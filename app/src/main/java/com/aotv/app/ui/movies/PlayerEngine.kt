package com.aotv.app.ui.movies

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.google.android.gms.cast.CastMediaControlIntent

/**
 * Motor do player — Lote 4 (v1.12.0), itens 1–3: player externo, Cast e decoder.
 *
 * ── Libmpv: NÃO implementado nesta versão, de propósito ──────────────────
 * Um segundo player interno via Libmpv exigiria o mpv-android, que NÃO é
 * publicado como artefato Maven Central: a via oficial é o AAR do GitHub
 * Releases (ou JitPack `com.github.mpv-android:mpv-android`), um binário
 * externo com .so nativos por ABI (+~15–25 MB no APK), ciclo de vida
 * próprio de Surface (MPVLib) e build NDK — fora do que o CI atual
 * (arm64-only, sem NDK) compila com segurança. Também não há como testar
 * no celular do Davi sem um build real do CI. Por isso o Lote 4 entrega
 * só players EXTERNOS (item 1): o sistema escolhe o app instalado (VLC,
 * MX Player…). Se um dia o mpv entrar, ele vive aqui, ao lado do
 * [decoderRenderersFactory], como um segundo [DecoderMode]-like — nunca
 * espalhado pela tela do player.
 */

/**
 * Decoder de vídeo do ExoPlayer (pref `pref_player_decoder`, por perfil).
 *
 * O que cada modo faz DE VERDADE no app (Media3 1.6.0, sem extensão FFmpeg):
 * - [HW]: fábrica padrão. Decodificação por hardware (MediaCodec do
 *   aparelho). Se o codec de hardware falhar ao iniciar, o erro é fatal e
 *   o watchdog troca de fonte (v1.8.1).
 * - [HW_FALLBACK]: igual ao HW, mas com `setEnableDecoderFallback(true)`:
 *   se o decoder de hardware falhar ao iniciar, o ExoPlayer tenta OUTRO
 *   decoder de plataforma p/ o mesmo MIME (ex.: outro OMX) antes de
 *   desistir. NÃO é decodificação por software: não há fallback p/ CPU
 *   aqui, só p/ outro decoder de hardware do aparelho.
 *
 * NÃO há modo SW de verdade: decodificar por software exigiria a extensão
 * FFmpeg do Media3 (`media3-exoplayer-ffmpeg`), que NÃO está no
 * build.gradle.kts — ela é pesada (binários FFmpeg nativos por ABI, MBs
 * no APK, build NDK no CI) e nenhum modo inventado aqui mudaria nada sem
 * ela: `EXTENSION_RENDERER_MODE_PREFER` sem extensão instalada é no-op.
 * Por isso o controle só oferece os dois modos que mudam comportamento
 * de verdade; o SW fica como follow-up (avaliar a extensão FFmpeg).
 */
enum class DecoderMode(val id: String, val label: String) {
    HW("hw", "Hardware (HW)"),
    HW_FALLBACK("hw_fallback", "Hardware com fallback");

    companion object {
        fun fromId(id: String): DecoderMode =
            entries.firstOrNull { it.id == id } ?: HW
    }
}

/**
 * Fábrica de renderers do ExoPlayer respeitando a pref de decoder.
 * Sem extensão FFmpeg instalada, o modo de extensão fica OFF nos dois
 * modos (ligar PREFER seria fingir que existe decoder de software).
 * A pref só vale na CRIAÇÃO do player (a sessão cria o ExoPlayer 1x);
 * trocar de fonte/episódio reusa o mesmo player e não recria nada.
 * Mudar a pref em Configurações vale a partir da próxima reprodução.
 */
@OptIn(UnstableApi::class)
fun decoderRenderersFactory(context: Context, mode: DecoderMode): DefaultRenderersFactory =
    DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        .setEnableDecoderFallback(mode == DecoderMode.HW_FALLBACK)

/**
 * Tamanho do buffer do ExoPlayer (Lote 5 item 1, v1.14.0 — pref
 * `pref_player_buffer`, por perfil). Os 4 números são os do
 * [DefaultLoadControl.Builder.setBufferDurationsMs]: mínimo e máximo de
 * mídia guardada, quanto precisa ter p/ começar a tocar e quanto precisa
 * ter p/ retomar depois de travar.
 *
 * - [LOW]: 15 s / 30 s, começa com 1,5 s. Internet boa e aparelho com
 *   pouca memória; começa rápido, mas trava mais fácil se a rede oscilar.
 * - [NORMAL]: 50 s / 50 s, começa com 2,5 s, retoma com 5 s. É o PADRÃO
 *   do ExoPlayer (os mesmos números que ele usa sem LoadControl) — quem
 *   não mexer continua exatamente como antes.
 * - [HIGH]: 120 s / 180 s, começa com 5 s, retoma com 10 s. Internet
 *   instável: segura mais vídeo na frente, ao custo de mais memória e
 *   de esperar mais no início.
 *
 * Vale SÓ na criação do player (a sessão cria o ExoPlayer 1x, como o
 * decoder): mudar em Configurações vale a partir da próxima reprodução.
 * Não vale p/ TV ao vivo (LivePlayerScreen usa o próprio ExoPlayer
 * sem LoadControl), nem p/ player externo e Cast (não é o ExoPlayer do
 * app que toca), nem p/ o player de som de outra fonte (só áudio, sem
 * vídeo p/ guardar). Não há valor personalizado em ms: 3 presets cobrem
 * os casos e o campo numérico não valia a tela a mais.
 */
enum class BufferProfile(
    val id: String,
    val label: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
) {
    LOW("low", "Baixo", 15_000, 30_000, 1_500, 3_000),
    NORMAL("normal", "Normal", 50_000, 50_000, 2_500, 5_000),
    HIGH("high", "Alto", 120_000, 180_000, 5_000, 10_000);

    companion object {
        fun fromId(id: String): BufferProfile =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** LoadControl do ExoPlayer com o perfil de buffer escolhido. */
@OptIn(UnstableApi::class)
fun bufferLoadControl(profile: BufferProfile): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            profile.minBufferMs,
            profile.maxBufferMs,
            profile.bufferForPlaybackMs,
            profile.bufferForPlaybackAfterRebufferMs,
        )
        .build()

/** Seletor de rota do Cast (Default Media Receiver), compartilhado pelo botão do player. */
fun castRouteSelector(): androidx.mediarouter.media.MediaRouteSelector =
    androidx.mediarouter.media.MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
        ).build()

/** MIME p/ abrir o link num player externo (o seletor do sistema usa isso p/ filtrar apps). */
fun externalPlayerMime(url: String): String {
    val u = url.substringBefore('|').substringBefore('?').lowercase()
    return when {
        u.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
        u.endsWith(".mp4") || u.endsWith(".m4v") -> "video/mp4"
        u.endsWith(".mkv") -> "video/x-matroska"
        u.endsWith(".webm") -> "video/webm"
        u.endsWith(".mov") -> "video/quicktime"
        u.endsWith(".avi") -> "video/x-msvideo"
        else -> "video/*"
    }
}

/**
 * Abre [url] num player externo instalado (ACTION_VIEW + seletor do sistema).
 * Devolve null quando o intent foi disparado, ou a mensagem p/ o toast quando não dá:
 * link vazio, nenhum player instalado, ou o sistema recusou.
 *
 * Limites honestos (não dá p/ contornar pelo intent):
 * - Headers custom (ex.: o sufixo Kodi `|Header=...`) NÃO vão junto: o
 *   ACTION_VIEW só leva URL + MIME. Fonte que só toca com header no
 *   interno pode falhar no externo — aí é usar o ExoPlayer.
 * - A URL tem que ser direta (http/https). Magnet de torrent não abre
 *   fora do app: quem resolve é o debrid, antes de chamar aqui (ver
 *   [PlayerSession.openExternalPlayer]).
 * - Legenda externa, som de outra fonte e correção de cor ficam no app:
 *   o externo toca só o vídeo, com os próprios controles dele.
 */
fun openInExternalPlayer(context: Context, url: String, title: String): String? {
    if (url.isBlank()) return "Sem link para abrir no player externo."
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return "Esse link não abre num player externo."
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), externalPlayerMime(url))
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "Abrir com"))
        null
    } catch (_: android.content.ActivityNotFoundException) {
        "Nenhum player de vídeo instalado para abrir esse link."
    } catch (_: Exception) {
        "Não foi possível abrir no player externo."
    }
}
