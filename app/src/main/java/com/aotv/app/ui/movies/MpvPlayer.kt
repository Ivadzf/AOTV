package com.aotv.app.ui.movies

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaTrackGroup
import androidx.media3.common.Tracks
import com.aotv.app.data.torrent.TorrentMagnet
import com.aotv.app.data.torrent.Torrents
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import java.io.File

/**
 * Libmpv como segundo motor interno do player (Lote mpv 1, v1.36.0).
 *
 * AAR pré-compilado `io.github.abdallahmehiz:mpv-android-lib:0.1.12`
 * (Maven Central, MIT) — traz libmpv.so + libplayer.so p/ arm64-v8a E
 * armeabi-v7a (a TV do Davi é 32 bits), então o APK continua universal.
 * SEM NDK no CI, SEM compilar código nativo: só a dependência no gradle.
 *
 * Ciclo de vida (mesmo padrão do BaseMPVView do AAR): create → opções →
 * init → attachSurface no surfaceCreated → loadfile. Sem superfície, o
 * load fica pendente e dispara quando ela aparece. O mpv renderiza no
 * próprio contexto OpenGL (vo=gpu), ligado ao ciclo da SurfaceView.
 *
 * Tudo aqui é best-effort com runCatching: qualquer falha de init/load
 * devolve o controle p/ a sessão, que cai p/ o ExoPlayer (fallback) sem
 * crash — ver [MpvListener.onInitFailed] e o `mpvFallback` da sessão.
 *
 * Faixas de áudio/legenda: lidas das propriedades escalares
 * `track-list/N/...` do mpv e convertidas p/ `Tracks.Group` do Media3,
 * para a UI existente (painéis de legenda/áudio, pickAutoSub,
 * pickAutoAudio, forced/SDH) funcionar sem mudar — só a APLICAÇÃO da
 * escolha muda (aid/sid no mpv em vez de trackSelectionParameters).
 */
class MpvPlayer(
    context: Context,
    private val listener: MpvListener,
) {
    interface MpvListener {
        /** Arquivo carregado e tocando (equivale ao STATE_READY do ExoPlayer). */
        fun onFileLoaded()
        /** Fim do arquivo. [eofReached] = terminou de verdade; false = erro ao abrir. */
        fun onEndFile(eofReached: Boolean)
        /** O libmpv não iniciou neste aparelho (falta .so, init falhou...). */
        fun onInitFailed()
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mpv = MPV()

    private var initialized = false
    private var initFailed = false
    private var surfaceAttached = false
    private var destroyed = false

    /** Load pedido antes do init/superfície — dispara quando ambos existirem. */
    private var pendingLoad: PendingLoad? = null

    data class PendingLoad(
        val url: String,
        val headers: Map<String, String>,
        val seekMs: Long,
        val speed: Float,
        val startPaused: Boolean,
        val audioLang: String,
        val subLang: String,
    )

    init {
        mpv.addObserver(object : MPV.EventObserver {
            override fun eventProperty(property: String) = Unit
            override fun eventProperty(property: String, value: Long) = Unit
            override fun eventProperty(property: String, value: Boolean) = Unit
            override fun eventProperty(property: String, value: String) = Unit
            override fun eventProperty(property: String, value: Double) = Unit
            override fun eventProperty(property: String, value: MPVNode) = Unit
            override fun event(eventId: Int, data: MPVNode) {
                // Chega na thread de eventos do mpv — tudo p/ a Main.
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> mainHandler.post {
                        if (!destroyed) listener.onFileLoaded()
                    }
                    MPV.mpvEvent.MPV_EVENT_END_FILE -> mainHandler.post {
                        if (destroyed) return@post
                        val eof = runCatching { mpv.getPropertyBoolean("eof-reached") }.getOrNull() == true
                        listener.onEndFile(eof)
                    }
                }
            }
        })
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────

    /** create + opções de init + init. Idempotente; falha → onInitFailed. */
    fun initIfNeeded() {
        if (initialized || initFailed || destroyed) return
        val ok = runCatching {
            val configDir = File(appContext.filesDir, "mpv").apply { mkdirs() }
            val cacheDir = File(appContext.cacheDir, "mpv").apply { mkdirs() }
            mpv.create(appContext)
            mpv.setOptionString("config", "yes")
            mpv.setOptionString("config-dir", configDir.absolutePath)
            mpv.setOptionString("gpu-shader-cache-dir", cacheDir.absolutePath)
            mpv.setOptionString("icc-cache-dir", cacheDir.absolutePath)
            // Renderizador em contexto próprio (vo=gpu, contexto EGL do mpv).
            mpv.setOptionString("vo", "gpu")
            // Decodificação por hardware no Android (MediaCodec); se o
            // decoder de hardware falhar, o mpv cai p/ software sozinho.
            mpv.setOptionString("hwdec", "mediacodec")
            mpv.setOptionString("force-window", "no")
            // Precisa ficar idle 1x p/ o loadfile funcionar antes da superfície.
            mpv.setOptionString("idle", "once")
            // O app escolhe as faixas sozinho (pickAutoSub/pickAutoAudio) —
            // o mpv não pré-seleciona legenda nenhuma.
            mpv.setOptionString("sub-auto", "no")
            // Sem yt-dlp: as fontes já chegam resolvidas pelos addons.
            mpv.setOptionString("ytdl", "no")
            mpv.init()
            initialized = true
        }.getOrDefault(false)
        if (!ok) {
            initFailed = true
            mainHandler.post { listener.onInitFailed() }
        }
    }

    /** SurfaceView pronta: inicia (se preciso), anexa e dispara load pendente. */
    fun onSurfaceCreated(surface: Surface) {
        if (destroyed) return
        initIfNeeded()
        if (!initialized) return
        runCatching {
            mpv.attachSurface(surface)
            mpv.setOptionString("force-window", "yes")
            surfaceAttached = true
            val pending = pendingLoad
            if (pending != null) {
                pendingLoad = null
                doLoad(pending)
            } else {
                mpv.setPropertyString("vo", "gpu")
            }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        if (!initialized || destroyed) return
        runCatching { mpv.setPropertyString("android-surface-size", "${width}x$height") }
    }

    /** SurfaceView destruída: solta o VO antes do detach (padrão do AAR). */
    fun onSurfaceDestroyed() {
        if (!initialized) return
        runCatching {
            mpv.setPropertyString("vo", "null")
            mpv.setPropertyString("force-window", "no")
            mpv.detachSurface()
            surfaceAttached = false
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pendingLoad = null
        runCatching {
            if (surfaceAttached) {
                mpv.setPropertyString("vo", "null")
                mpv.detachSurface()
                surfaceAttached = false
            }
        }
        runCatching { mpv.destroy() }
    }

    // ── Carregar ─────────────────────────────────────────────────────────

    /**
     * Toca [url] (já limpa: sem sufixo Kodi `|...`, magnet já resolvido
     * p/ a URL local do P2P). Headers vão p/ `http-header-fields` antes
     * do loadfile — é o que os links de addon exigem (Referer/UA).
     * Sem init/superfície, fica pendente. [seekMs] aplica no FILE_LOADED.
     */
    fun load(
        url: String,
        headers: Map<String, String>,
        seekMs: Long,
        speed: Float,
        startPaused: Boolean,
        audioLang: String,
        subLang: String,
    ) {
        val req = PendingLoad(url, headers, seekMs, speed, startPaused, audioLang, subLang)
        if (!initialized || !surfaceAttached) {
            pendingLoad = req
            initIfNeeded()
            return
        }
        doLoad(req)
    }

    private fun doLoad(req: PendingLoad) {
        runCatching {
            val fields = (STREAM_HEADERS + req.headers).entries
                .joinToString(",") { (k, v) -> "$k: ${escapeMpvListValue(v)}" }
            mpv.setOptionString("http-header-fields", fields)
            if (req.audioLang.isNotBlank()) mpv.setOptionString("alang", req.audioLang)
            if (req.subLang.isNotBlank()) mpv.setOptionString("slang", req.subLang)
            mpv.setPropertyDouble("speed", req.speed.toDouble())
            mpv.command("loadfile", req.url)
            if (req.startPaused) mpv.setPropertyBoolean("pause", true)
        }
    }

    /** Seek guardado p/ depois do FILE_LOADED (o loadfile ainda não aceita posição). */
    fun seekAfterLoad(seekMs: Long) {
        if (seekMs <= 0L) return
        runCatching { mpv.command("seek", (seekMs / 1000.0).toString(), "absolute") }
    }

    // ── Controles ────────────────────────────────────────────────────────

    fun positionMs(): Long =
        runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull()
            ?.let { (it * 1000).toLong().coerceAtLeast(0L) } ?: 0L

    fun durationMs(): Long =
        runCatching { mpv.getPropertyDouble("duration") }.getOrNull()
            ?.let { (it * 1000).toLong().coerceAtLeast(0L) } ?: 0L

    fun isPlaying(): Boolean {
        if (!initialized || destroyed) return false
        val paused = runCatching { mpv.getPropertyBoolean("pause") }.getOrNull() == true
        val idle = runCatching { mpv.getPropertyBoolean("core-idle") }.getOrNull() == true
        return !paused && !idle
    }

    fun isBuffering(): Boolean =
        runCatching { mpv.getPropertyBoolean("paused-for-cache") }.getOrNull() == true

    fun setPlaying(playing: Boolean) {
        runCatching { mpv.setPropertyBoolean("pause", !playing) }
    }

    fun seekTo(ms: Long) {
        runCatching { mpv.command("seek", (ms / 1000.0).toString(), "absolute") }
    }

    fun setSpeed(rate: Float) {
        runCatching { mpv.setPropertyDouble("speed", rate.toDouble()) }
    }

    // ── Faixas (áudio/legenda) ───────────────────────────────────────────

    /** Faixa de áudio do mpv (id da track-list) ou null = nenhuma. */
    fun setAudioTrackId(id: Int?) {
        if (id == null) return
        runCatching { mpv.setPropertyInt("aid", id) }
    }

    /** Faixa de legenda do mpv (id da track-list); null = desligar. */
    fun setSubTrackId(id: Int?) {
        runCatching {
            if (id == null) mpv.setPropertyString("sid", "no")
            else mpv.setPropertyInt("sid", id)
        }
    }

    /**
     * Lê a track-list do mpv e devolve (áudio, legenda) como Groups do
     * Media3 — mesmo formato que o listener do ExoPlayer entrega, p/ a
     * UI e a lógica de escolha automática não saberem qual motor está
     * tocando. O id do mpv vai no [Format.id] (é o que selectAudio /
     * enableTextGroup usam p/ aplicar a escolha).
     */
    fun readTracks(): Pair<List<Tracks.Group>, List<Tracks.Group>> {
        val audio = ArrayList<Tracks.Group>()
        val text = ArrayList<Tracks.Group>()
        val count = runCatching { mpv.getPropertyInt("track-list/count") }.getOrNull() ?: 0
        for (i in 0 until count) {
            val type = runCatching { mpv.getPropertyString("track-list/$i/type") }.getOrNull() ?: continue
            if (type != "audio" && type != "sub") continue
            val id = runCatching { mpv.getPropertyInt("track-list/$i/id") }.getOrNull() ?: continue
            val lang = runCatching { mpv.getPropertyString("track-list/$i/lang") }.getOrNull()
            val title = runCatching { mpv.getPropertyString("track-list/$i/title") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            val codec = runCatching { mpv.getPropertyString("track-list/$i/codec") }.getOrNull()
            val selected = runCatching { mpv.getPropertyBoolean("track-list/$i/selected") }.getOrNull() == true
            val forced = runCatching { mpv.getPropertyBoolean("track-list/$i/forced") }.getOrNull() == true
            val isDefault = runCatching { mpv.getPropertyBoolean("track-list/$i/default") }.getOrNull() == true
            var flags = 0
            if (forced) flags = flags or C.SELECTION_FLAG_FORCED
            if (isDefault) flags = flags or C.SELECTION_FLAG_DEFAULT
            val format = Format.Builder()
                .setId(id.toString())
                .setLanguage(lang)
                .setLabel(title)
                .setSampleMimeType(mimeForCodec(codec, type))
                .setSelectionFlags(flags)
                .build()
            val group = Tracks.Group(
                MediaTrackGroup("mpv-$id", format),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(selected),
            )
            if (type == "audio") audio.add(group) else text.add(group)
        }
        return audio to text
    }

    private fun mimeForCodec(codec: String?, type: String): String? {
        val c = codec?.lowercase() ?: return null
        return when (type) {
            "audio" -> when (c) {
                "aac" -> "audio/mp4a-latm"
                "ac3" -> "audio/ac3"
                "eac3" -> "audio/eac3"
                "dts" -> "audio/vnd.dts"
                "truehd" -> "audio/true-hd"
                "opus" -> "audio/opus"
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "vorbis" -> "audio/vorbis"
                "pcm_s16le", "pcm_s24le", "pcm_s32le" -> "audio/raw"
                else -> null
            }
            else -> when (c) {
                "subrip" -> "application/x-subrip"
                "ass", "ssa" -> "text/x-ssa"
                "mov_text" -> "application/mp4"
                "webvtt" -> "text/vtt"
                "hdmv_pgs_subtitle" -> "application/pgs"
                "dvd_subtitle" -> "application/vobsub"
                else -> null
            }
        }
    }

    // ── Estilo (cor + legenda) ───────────────────────────────────────────

    /** Sliders de cor do perfil (Lote 1, v1.9.0): os 4 que o mpv tem como propriedade. Preset e daltonismo ficam p/ o Lote mpv 2 (são cadeias de efeitos do ExoPlayer). */
    fun applyColor(state: ColorFilterState) {
        runCatching {
            mpv.setPropertyDouble("brightness", (state.brightness * 100).toDouble().coerceIn(-100.0, 100.0))
            mpv.setPropertyDouble("contrast", ((state.contrast - 1f) * 100).toDouble().coerceIn(-100.0, 100.0))
            mpv.setPropertyDouble("saturation", ((state.saturation - 1f) * 100).toDouble().coerceIn(-100.0, 100.0))
            mpv.setPropertyDouble("hue", state.hueDegrees.toDouble().coerceIn(-180.0, 180.0))
        }
    }

    /** Estilo da legenda do perfil (tamanho, cor, fundo, contorno, fonte). */
    fun applySubStyle(
        sizeScale: Float,
        textColorArgb: Int,
        withBg: Boolean,
        bgOpacity: Float,
        outline: Boolean,
        font: String,
    ) {
        runCatching {
            mpv.setOptionString("sub-font-size", (55 * sizeScale).toInt().coerceIn(10, 200).toString())
            mpv.setOptionString("sub-color", "#%08X".format(textColorArgb))
            val bgAlpha = (bgOpacity.coerceIn(0f, 1f) * 255).toInt()
            mpv.setOptionString(
                "sub-back-color",
                if (withBg) "#%02X000000".format(bgAlpha) else "#00000000",
            )
            mpv.setOptionString("sub-border-size", if (outline) "3" else "0")
            if (font.isNotBlank() && font != "default") mpv.setOptionString("sub-font", font)
        }
    }

    // ── Infos técnicas ───────────────────────────────────────────────────

    /** Dados do stream p/ o painel técnico (mesmo TechInfo do ExoPlayer). */
    fun techInfo(casting: Boolean): TechInfo {
        val w = runCatching { mpv.getPropertyInt("video-params/w") }.getOrNull()
        val h = runCatching { mpv.getPropertyInt("video-params/h") }.getOrNull()
        val fps = runCatching { mpv.getPropertyDouble("video-params/average-frame-rate") }.getOrNull()
        val vcodec = runCatching { mpv.getPropertyString("video-params/codec") }.getOrNull()
        val acodec = runCatching { mpv.getPropertyString("audio-params/codec") }.getOrNull()
        val vbr = runCatching { mpv.getPropertyInt("video-bitrate") }.getOrNull()
            ?.takeIf { it > 0 }?.div(1000)
        val battery = runCatching { readBatteryStatus(appContext) }.getOrNull()
        return TechInfo(
            engine = "Libmpv",
            videoCodec = vcodec,
            videoResolution = if (w != null && h != null && w > 0 && h > 0) "${w}x$h" else null,
            videoFps = fps?.takeIf { it > 0 }?.let { "%.3f".format(it).trimEnd('0').trimEnd('.') },
            videoBitrateKbps = vbr,
            audioCodec = acodec,
            audioOutput = collectAudioOutput(appContext, casting),
            batteryLevelPercent = battery?.levelPercent,
            batteryCharging = battery?.isCharging,
        )
    }

    /** Posição até onde há dados em cache (p/ a barra de progresso). */
    fun bufferedPositionMs(): Long {
        val ahead = runCatching { mpv.getPropertyDouble("demuxer-cache-time") }.getOrNull() ?: 0.0
        return (positionMs() + (ahead * 1000).toLong()).coerceAtLeast(0L)
    }

    /** FPS do stream (p/ a linha "24 fps · tela 60 Hz" das infos técnicas). */
    fun videoFps(): Float? =
        runCatching { mpv.getPropertyDouble("video-params/average-frame-rate") }.getOrNull()
            ?.takeIf { it > 0 }?.toFloat()

    /** Largura/altura do vídeo p/ o selo de qualidade e o modo Automático. */
    fun videoSize(): Pair<Int, Int> {
        val w = runCatching { mpv.getPropertyInt("video-params/w") }.getOrNull() ?: 0
        val h = runCatching { mpv.getPropertyInt("video-params/h") }.getOrNull() ?: 0
        return w to h
    }

    companion object {
        /** Escapa valor p/ lista do mpv separada por vírgula (\, e \\). */
        internal fun escapeMpvListValue(v: String): String =
            v.replace("\\", "\\\\").replace(",", "\\,")

        /** MediaItem só p/ reaproveitar o MIME (.m3u8) na hora de resolver a URL — o mpv toca a URL direta. */
        internal fun playUrlFor(context: Context, url: String): String {
            val (clean, _) = splitKodiStreamHeaders(url)
            return if (TorrentMagnet.isMagnet(clean)) Torrents.get(context).playUrl(clean) else clean
        }
    }
}
