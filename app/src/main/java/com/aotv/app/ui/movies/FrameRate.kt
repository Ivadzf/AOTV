package com.aotv.app.ui.movies

import android.app.Activity
import android.media.MediaCodecInfo
import android.os.Build
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView

// Frame-rate matching + medidor de FPS + fallback Dolby Vision 7 → HDR10
// (Lote 16 item 4, v1.26.0). Pensado p/ a TV Philco do Davi (Android TV
// 14, 32-bit): a TV troca o modo de exibição p/ a taxa do vídeo e o
// filme para de dar aqueles pulos de 24 fps numa tela de 60 Hz.
//
// ── API DE PLUG (p/ o coordenador) ────────────────────────────────────────
//   // Quando o vídeo fica pronto (STATE_READY / onVideoSizeChanged):
//   val fps = suggestedFrameRate(player) // do videoFormat
//   if (fps != null) applyFrameRateToPlayerView(playerView, fps)
//   // Opcional, junto: applyWindowRefreshRate(activity, fps)
//   // Medidor (toggle nas infos técnicas):
//   val meter = remember { FpsMeter() }
//   LaunchedEffect(fpsMeterOn) { if (on) meter.start() else meter.stop() }
//   // Linha p/ o painel de infos: fpsDisplayLine(fpsDoStream, meter.currentFps)
//   // DV perfil 7 sem suporte no aparelho:
//   if (dvProfile7NeedsHdr10Fallback(player)) applyHdr10Fallback(player)
//   // Tudo com runCatching por dentro; nada aqui lança.
//
// ── LIMITES HONESTOS ──────────────────────────────────────────────────────
// - `Surface.setFrameRate` só vale p/ SurfaceView. O PlayerView do app
//   usa SurfaceView por padrão; se um dia virar TextureView, o retorno
//   é UNSUPPORTED_TEXTURE_VIEW (a TextureView não expõe a Surface p/
//   setFrameRate — o SurfaceTexture dela não tem essa API).
// - A troca de modo da TV pode não ser instantânea ("seamless" só em
//   API 31+); abaixo disso o pedido vai sem a estratégia seamless e a
//   TV pode piscar ao trocar. O resultado diz o que aconteceu.
// - O medidor mede o VSYNC DA TELA (Choreographer), não os quadros
//   decodificados do vídeo: ele diz a quantos Hz a tela está
//   desenhando, e o fps DO STREAM vem do videoFormat. Não há API
//   pública p/ contar quadros decodificados por segundo no Media3.
// - O fallback DV→HDR10 só existe quando o manifesto traz OUTRA faixa
//   de vídeo sem DV (o HLS/DASH costuma trazer a rendição HDR10 junto;
//   se só houver a faixa DV, não há p/ onde cair e applyHdr10Fallback
//   devolve false).

/** Resultado de aplicar o frame-rate na Surface. */
enum class FrameRateResult {
    /** Aplicado (API 31+: só troca se for sem corte; senão não troca). */
    APPLIED_SEAMLESS,
    /** Aplicado (API < 31: sem estratégia seamless — a TV pode piscar). */
    APPLIED,
    /** Sem Surface válida ainda (chamar de novo no STATE_READY). */
    NO_SURFACE,
    /** PlayerView com TextureView: sem API p/ setFrameRate. */
    UNSUPPORTED_TEXTURE_VIEW,
    /** fps inválido (<= 0 ou absurdo). */
    INVALID_FPS,
    /** A Surface recusou (exceção do framework). */
    FAILED,
}

/** Taxa do stream atual, do videoFormat (null = sem dado ainda). */
fun suggestedFrameRate(player: ExoPlayer): Float? {
    val fps = runCatching { player.videoFormat?.frameRate }.getOrNull() ?: return null
    return fps.takeIf { it > 0f && it <= 240f }
}

/** Aplica o frame-rate na Surface do PlayerView (SurfaceView). */
fun applyFrameRateToPlayerView(playerView: PlayerView, fps: Float): FrameRateResult {
    val view: View = runCatching { playerView.videoSurfaceView }.getOrNull() ?: return FrameRateResult.NO_SURFACE
    return when (view) {
        is SurfaceView -> applyFrameRateToSurface(view.holder.surface, fps)
        is TextureView -> FrameRateResult.UNSUPPORTED_TEXTURE_VIEW
        else -> FrameRateResult.NO_SURFACE
    }
}

/** Aplica o frame-rate numa Surface (a do SurfaceView do PlayerView). */
fun applyFrameRateToSurface(surface: Surface, fps: Float): FrameRateResult {
    if (fps <= 0f || fps > 240f) return FrameRateResult.INVALID_FPS
    if (!runCatching { surface.isValid }.getOrDefault(false)) return FrameRateResult.NO_SURFACE
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            surface.setFrameRate(
                fps,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
            )
            FrameRateResult.APPLIED_SEAMLESS
        } else {
            @Suppress("DEPRECATION")
            surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            FrameRateResult.APPLIED
        }
    }.getOrDefault(FrameRateResult.FAILED)
}

/**
 * Pede a taxa de atualização da JANELA p/ o fps do vídeo (a TV troca o
 * modo de exibição). Complementa o setFrameRate da Surface. Devolve
 * false sem activity ou quando o framework recusa.
 */
fun applyWindowRefreshRate(activity: Activity?, fps: Float): Boolean {
    val act = activity ?: return false
    if (fps <= 0f || fps > 240f) return false
    return runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val window = act.window ?: return false
        val attrs = window.attributes
        attrs.preferredRefreshRate = fps
        window.attributes = attrs
        true
    }.getOrDefault(false)
}

/**
 * Medidor de FPS da tela (Choreographer): média móvel dos últimos 30
 * quadros de vsync. Mede a TELA, não o vídeo (ver o doc do arquivo).
 * Só vale na thread principal (onde o Choreographer vive).
 */
class FpsMeter {
    private val frameTimes = ArrayDeque<Long>()
    private var running = false

    private val callback: Choreographer.FrameCallback = Choreographer.FrameCallback { nowNs ->
        if (!running) return@FrameCallback
        frameTimes.addLast(nowNs)
        while (frameTimes.size > 30) frameTimes.removeFirst()
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /** FPS médio da tela nos últimos quadros, ou null sem medição ainda. */
    val currentFps: Float?
        get() {
            if (frameTimes.size < 2) return null
            val first = frameTimes.first()
            val last = frameTimes.last()
            val spanNs = last - first
            if (spanNs <= 0) return null
            return ((frameTimes.size - 1) * 1_000_000_000f / spanNs)
        }

    /** Começa a medir (chamar 1x; sem efeito se já estiver medindo). */
    fun start() {
        if (running) return
        running = true
        frameTimes.clear()
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /** Para de medir e limpa a janela. */
    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
        frameTimes.clear()
    }
}

/** Linha p/ as infos técnicas: "24 fps · tela 60 Hz" (só o que existe aparece). */
fun fpsDisplayLine(streamFps: Float?, displayFps: Float?): String? {
    val stream = streamFps?.takeIf { it > 0f }?.let { "%.3f".format(it).trimEnd('0').trimEnd('.') + " fps" }
    val display = displayFps?.takeIf { it > 0f }?.let { "tela ${"%.0f".format(it)} Hz" }
    return listOfNotNull(stream, display).joinToString(" · ").takeIf { it.isNotBlank() }
}

// ── Dolby Vision perfil 7 → HDR10 ─────────────────────────────────────────

/** "dvhe.07.06" / "dvh1.07" / "dvav.07" → 7. Null quando não é DV. */
fun dolbyVisionProfileFromCodecs(codecs: String?): Int? {
    val c = codecs?.lowercase() ?: return null
    val m = Regex("(?:dvhe|dvh1|dvav|dva1|dvh2|dvh3)\\.(\\d{2})").find(c) ?: return null
    return m.groupValues[1].toIntOrNull()
}

/** true quando a string de codecs é Dolby Vision (qualquer perfil). */
fun isDolbyVisionCodecs(codecs: String?): Boolean = dolbyVisionProfileFromCodecs(codecs) != null

/**
 * Perfil DV (nº) → constante do `CodecProfileLevel`. Mapeamento do
 * `MediaCodecUtil` do ExoPlayer (conferido: "07" → DvheDtb,
 * "06" → DvheDth). Perfis fora da tabela: null = sem como verificar.
 */
private fun dvProfileConstant(profile: Int): Int? = when (profile) {
    0 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPen
    1 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe
    2 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDen
    3 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr
    4 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn
    5 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt
    6 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDth
    7 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb
    8 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt
    9 -> MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe
    else -> null
}

/**
 * true quando o aparelho tem decoder Dolby Vision p/ o [profile].
 * Sem decoder DV ou sem o perfil na lista: false (não suportado).
 */
fun deviceSupportsDolbyVisionProfile(profile: Int): Boolean {
    val constant = dvProfileConstant(profile) ?: return false
    return runCatching {
        MediaCodecSelector.DEFAULT.getDecoderInfos("video/dolby-vision", false, false).any { info ->
            // Media3 1.6.0: MediaCodecInfo NÃO tem getCapabilitiesForType — os
            // níveis do perfil vêm de getProfileLevels() (já é do mime type
            // da consulta, "video/dolby-vision"; vazio sem capabilities).
            info.getProfileLevels().any { it.profile == constant }
        }
    }.getOrDefault(false)
}

/**
 * true quando o vídeo atual é DV perfil 7 E o aparelho não o suporta:
 * aí vale tentar o fallback p/ HDR10. Qualquer outro caso: false.
 */
@OptIn(UnstableApi::class)
fun dvProfile7NeedsHdr10Fallback(player: ExoPlayer): Boolean {
    val format = runCatching { player.videoFormat }.getOrNull() ?: return false
    if (dolbyVisionProfileFromCodecs(format.codecs) != 7) return false
    return !deviceSupportsDolbyVisionProfile(7)
}

/**
 * Procura uma faixa de vídeo SEM Dolby Vision no player (prefere HDR10
 * — colorTransfer ST2084 — e aceita SDR). Devolve (grupo, índice da
 * faixa) ou null quando só existe a faixa DV. Pura sobre os tracks.
 */
@OptIn(UnstableApi::class)
fun findHdr10FallbackTrack(player: ExoPlayer): Pair<Tracks.Group, Int>? {
    val tracks = runCatching { player.currentTracks }.getOrNull() ?: return null
    var sdrFallback: Pair<Tracks.Group, Int>? = null
    for (gi in 0 until tracks.groups.size) {
        val group = tracks.groups[gi]
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (ti in 0 until group.length) {
            val format: Format = group.getTrackFormat(ti)
            if (isDolbyVisionCodecs(format.codecs)) continue
            val candidate = group to ti
            if (format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084) return candidate
            if (sdrFallback == null) sdrFallback = candidate
        }
    }
    return sdrFallback
}

/**
 * Força a faixa HDR10 (ou SDR) quando o vídeo é DV perfil 7 sem
 * suporte no aparelho. Devolve false quando não há faixa alternativa
 * (só existe DV) ou quando o ExoPlayer recusa a troca.
 */
@OptIn(UnstableApi::class)
fun applyHdr10Fallback(player: ExoPlayer): Boolean {
    val fallback = findHdr10FallbackTrack(player) ?: return false
    val group = fallback.first
    val track = fallback.second
    return runCatching {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track))
            .build()
        true
    }.getOrDefault(false)
}
