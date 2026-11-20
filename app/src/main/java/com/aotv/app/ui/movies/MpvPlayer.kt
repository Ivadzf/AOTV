package com.aotv.app.ui.movies

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
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

    /**
     * Renderizador escolhido no painel Melhorar imagem (Lote mpv 2):
     * "gpu" (padrao) ou "gpu-next" (os dois existem no mpv 0.41 do AAR).
     * Vale no init e na (re)anexacao da superficie; trocar em reproducao
     * reinicia o VO (ver [applyVo]).
     */
    var preferredVo: String = "gpu"
        private set

    /** Shaders ja copiados p/ filesDir (ver [shaderDir]). */
    private var shadersInstalled = false

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
            mpv.setOptionString("vo", preferredVo)
            // Decodificação por hardware no Android (MediaCodec); se o
            // decoder de hardware falhar, o mpv cai p/ software sozinho.
            mpv.setOptionString("hwdec", "mediacodec")
            // Correcao A (Lote mpv 2, v1.37.0) — video engasgando: o video
            // ficava lento em partes apos alguns segundos, como perda de FPS
            // em jogo, sem travar tudo. Causa mais provavel: o padrao do mpv
            // (video-sync=audio) apresenta os quadros no relogio do audio;
            // num painel de 60 Hz, conteudo de 24 fps cai na cadencia 3:2
            // (quadros repetidos de forma desigual) e a correcao de deriva
            // audio-video aparece como "lentidao em partes". Com
            // video-sync=display-resample o mpv trava a apresentacao no
            // vsync da tela e reajusta a velocidade do audio (o display-fps
            // fica na deteccao automatica do mpv, padrao 0 = detectar).
            // Candidatos REJEITADOS, com motivo:
            // - hwdec=mediacodec-copy: COPIA cada quadro decodificado p/ a
            //   CPU antes de subir p/ a textura — num SoC fraco de TV satura
            //   a thread do codec (medido em 0,78x tempo real num Amlogic
            //   de 4x Cortex-A55) e reproduz o proprio sintoma relatado.
            //   O mediacodec zero-copy atual ja e o caminho certo.
            // - vo=gpu-next: existe no mpv 0.41, mas troca o pipeline de
            //   shaders inteiro (libplacebo) e ja quebrou com mediacodec em
            //   alguns aparelhos — fica como ESCOLHA no painel, nao padrao.
            // - demuxer-max-bytes / demuxer-readahead-secs: sao p/ engasgo
            //   de REDE (pausa p/ cache); o sintoma e de ritmo de quadro,
            //   nao de buffer — e o isBuffering ja cobre a pausa p/ cache.
            // A prova final e o teste do Davi na TV/celular (ver o reporte).
            mpv.setOptionString("video-sync", "display-resample")
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
        }.isSuccess
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
                mpv.setPropertyString("vo", preferredVo)
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
                TrackGroup("mpv-$id", format),
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

    // ── Melhorar imagem (Lote mpv 2, v1.37.0) ─────────────────────────────

    /**
     * Troca o renderizador ("gpu" ou "gpu-next"). Guarda a escolha p/ o
     * init e p/ a proxima superficie; com o VO ja ativo, troca na hora
     * (o mpv reinicia o VO sozinho). Valor desconhecido volta p/ "gpu".
     * Best-effort: se a troca falhar, o video continua no VO atual.
     */
    fun applyVo(vo: String) {
        val clean = if (vo == "gpu-next") "gpu-next" else "gpu"
        preferredVo = clean
        if (!initialized || destroyed) return
        runCatching { mpv.setPropertyString("vo", clean) }
    }

    /**
     * Copia os shaders empacotados (assets/shaders/) p/ filesDir/mpv-shaders
     * na primeira utilizacao e devolve o diretorio. SO os arquivos da
     * allowlist ([ENHANCE_SHADER_ALLOWLIST]) sao copiados — nunca caminho
     * arbitrario (o painel so manda ids do catalogo). null = a copia falhou
     * (o upscaler degrada p/ os escaladores built-in; os filtros continuam).
     */
    fun shaderDir(): File? {
        val dir = File(appContext.filesDir, "mpv-shaders").apply { mkdirs() }
        if (shadersInstalled) return dir.takeIf { it.isDirectory }
        var ok = true
        for (name in ENHANCE_SHADER_ALLOWLIST) {
            runCatching {
                val target = File(dir, name)
                val bytes = appContext.assets.open("shaders/$name").use { it.readBytes() }
                if (!target.exists() || target.length() != bytes.size.toLong()) target.writeBytes(bytes)
            }.onFailure { ok = false }
        }
        shadersInstalled = ok
        return dir.takeIf { ok }
    }

    /**
     * Aplica o estado do painel Melhorar imagem no mpv, sem reiniciar o
     * video. A cadeia de shaders vai por caminho absoluto em `glsl-shaders`
     * (separador ":" — o formato do libmpv no Android); etapa cujo arquivo
     * nao foi instalado e PULADA (o preset degrada p/ os escaladores
     * built-in em vez de fingir que o shader existe). A daltonizacao vai
     * por ultimo na cadeia (correcao depois da gradacao, como no ReShade);
     * sem upscaler, ela vai sozinha. O `video-sync` NAO e mexido aqui:
     * fica display-resample do init (correcao A) — o movimento suave so
     * liga/desliga a `interpolation`.
     */
    fun applyEnhance(state: EnhanceState, daltonism: DaltonismMode) {
        if (!initialized || destroyed) return
        runCatching {
            val dir = shaderDir()
            val shaderFiles = ArrayList<String>()
            if (dir != null) {
                for (id in resolveEnhanceChain(state)) {
                    val file = enhanceStepById(id)?.file ?: continue
                    val f = File(dir, file)
                    if (f.exists()) shaderFiles.add(f.absolutePath)
                }
                daltonizeShaderFile(daltonism)?.let { name ->
                    val f = File(dir, name)
                    if (f.exists()) shaderFiles.add(f.absolutePath)
                }
            }
            mpv.setPropertyString("glsl-shaders", shaderFiles.joinToString(":"))
            val deband = state.debandParts
            mpv.setPropertyBoolean("deband", state.deband)
            mpv.setPropertyInt("deband-iterations", deband.iter)
            mpv.setPropertyDouble("deband-threshold", deband.threshold.toDouble())
            mpv.setPropertyDouble("deband-range", deband.range.toDouble())
            mpv.setPropertyDouble("deband-grain", deband.grain.toDouble())
            mpv.setPropertyBoolean("interpolation", state.smoothMotion)
            if (state.toneMapping != "auto" && state.toneMapping in ENHANCE_TONE_MAPPINGS) {
                mpv.setPropertyString("tone-mapping", state.toneMapping)
            }
            mpv.setPropertyString("hdr-compute-peak", if (state.hdrPeak) "yes" else "auto")
            if (state.deinterlace in listOf("auto", "yes", "no")) {
                mpv.setPropertyString("deinterlace", state.deinterlace)
            }
            if (state.scale in ENHANCE_SCALERS) mpv.setPropertyString("scale", state.scale)
            if (state.cscale in ENHANCE_SCALERS) mpv.setPropertyString("cscale", state.cscale)
            if (state.dscale in ENHANCE_SCALERS) mpv.setPropertyString("dscale", state.dscale)
            // O mpv 0.41 nao tem opcao unica "antiring": sao tres (escala,
            // croma e reducao) e o slider unico aplica o mesmo valor nas tres.
            val antiring = state.antiring.coerceIn(0f, 1f).toDouble()
            mpv.setPropertyDouble("scale-antiring", antiring)
            mpv.setPropertyDouble("cscale-antiring", antiring)
            mpv.setPropertyDouble("dscale-antiring", antiring)
            mpv.setPropertyInt("gamma", state.gamma.toInt().coerceIn(-100, 100))
        }
    }

    /**
     * Custo por passada e contadores de quadros, lidos do proprio mpv (aba
     * Desempenho). `vo-passes` devolve um mapa categoria -> lista de
     * passadas, cada uma com `desc`, `avg`/`peak` em NANOSSEGUNDOS e
     * `samples`/`count` (ver o stats.lua embutido no mpv 0.41). O total e o
     * MAIOR total por categoria — as categorias se sobrepoem e soma-las
     * dobraria a conta. Qualquer falha de leitura devolve contadores zerados
     * e lista vazia (a aba mostra "sem dados"), nunca crasha.
     */
    fun readPerf(): MpvPerf {
        val dropped = runCatching { mpv.getPropertyInt("frame-drop-count") }.getOrNull() ?: 0
        val delayed = runCatching { mpv.getPropertyInt("vo-delayed-frame-count") }.getOrNull() ?: 0
        val mistimed = runCatching { mpv.getPropertyInt("mistimed-frame-count") }.getOrNull() ?: 0
        val fps = runCatching { mpv.getPropertyDouble("video-params/average-frame-rate") }.getOrNull()
            ?.takeIf { it > 0 }
        val passes = ArrayList<MpvPass>()
        var totalMs = 0.0
        runCatching {
            val json = mpv.getPropertyNode("vo-passes")?.toJson() ?: return@runCatching
            val root = org.json.JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val category = keys.next()
                val arr = root.optJSONArray(category) ?: continue
                var categoryMs = 0.0
                for (i in 0 until arr.length()) {
                    val pass = arr.optJSONObject(i) ?: continue
                    val avgMs = pass.optDouble("avg", 0.0) / 1_000_000.0
                    val peakMs = pass.optDouble("peak", 0.0) / 1_000_000.0
                    val desc = pass.optString("desc", "").takeIf { it.isNotBlank() } ?: continue
                    passes.add(MpvPass("$category — $desc", avgMs, peakMs))
                    categoryMs += avgMs
                }
                if (categoryMs > totalMs) totalMs = categoryMs
            }
        }
        return MpvPerf(passes, dropped.toLong(), delayed.toLong(), mistimed.toLong(), fps, totalMs)
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

/** Uma passada do VO do mpv (ver [MpvPlayer.readPerf]): tempos em ms por quadro. */
data class MpvPass(val desc: String, val avgMs: Double, val peakMs: Double)

/**
 * Custo e contadores do mpv (aba Desempenho). [totalAvgMs] e o maior total
 * por categoria de `vo-passes` (as categorias se sobrepoem). [fps] e o fps
 * do video, ou null sem video.
 */
data class MpvPerf(
    val passes: List<MpvPass>,
    val dropped: Long,
    val delayed: Long,
    val mistimed: Long,
    val fps: Double?,
    val totalAvgMs: Double,
)
