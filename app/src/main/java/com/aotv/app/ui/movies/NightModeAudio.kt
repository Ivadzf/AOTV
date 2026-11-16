package com.aotv.app.ui.movies

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.aotv.app.data.local.PlatformPrefs

// Modo noturno + Clear Dialogue (Lote 16 item 1, v1.26.0).
//
// ── API DE PLUG (p/ o coordenador) ────────────────────────────────────────
//   val night = remember { NightModeController(prefs) }
//   // Estado por título/canal (carrega ao abrir o player):
//   val state = loadNightModeState(prefs, nightModeTitleKeyMovie(tmdbId))
//   // (re)aplica quando a sessão de áudio fica pronta E quando o estado muda:
//   //   override fun onAudioSessionIdChanged(id: Int) { ...; night.apply(id, channelCount, state) }
//   //   LaunchedEffect(state) { night.apply(player.audioSessionId, channelCount, state) }
//   // Ao fechar o player: night.release()
//   // Painel: NightModePanel(state, night.drcEngine, night.drcSupported,
//   //   night.clearSupported, onNightToggle = {...}, onClearToggle = {...},
//   //   onIntensity = {...}, onDismiss = {...}) — ver NightModePanel.kt.
//   // Salvar: saveNightModeState(prefs, titleKey, state) a cada mudança.
//   // Passthrough: night.passthrough = <true quando o bitstream estiver
//   //   ligado>; com ele ligado, apply() não processa nada e o painel
//   //   bloqueia os toggles com a explicação (ver blockedReason).
//
// ── COMO FUNCIONA ─────────────────────────────────────────────────────────
// Mesmo gancho do Volume Boost e do equalizador: efeitos `audiofx` na
// sessão de áudio do ExoPlayer (`onAudioSessionIdChanged`). Nada de
// renderer custom nem AudioProcessor de PCM — por isso NÃO foi preciso
// tocar em PlayerEngine.kt: o processamento vive na sessão, não no sink.
//
// - MODO NOTURNO (DRC): compressão de faixa dinâmica. API 28+ usa
//   `DynamicsProcessing` com 1 banda de compressor (0–20 kHz): o que
//   passa do limiar é comprimido (ratio até 8:1) e o conjunto ganha
//   postGain, então sussurros sobem e explosões não estouram. Abaixo
//   da API 28 NÃO há compressor no framework: o fallback é o
//   `LoudnessEnhancer` com ganho moderado — HONESTAMENTE NÃO é
//   compressão (só levanta tudo junto, inclusive o ruído, e pode
//   distorcer nos picos); o painel diz isso quando o fallback está em
//   uso. Aparelho que recusa os dois efeitos: DRC indisponível.
// - CLEAR DIALOGUE: equalização p/ voz — reforço de médios (1–4 kHz,
//   onde está a inteligibilidade da fala) + corte de graves (< 200 Hz,
//   onde mora o ronco de música/efeitos). Usa o próprio `Equalizer` do
//   Android, no mesmo caminho do equalizador do Lote 9, mas numa
//   instância separada (o framework encadeia os efeitos da sessão; o
//   EQ do usuário continua valendo junto). As bandas e os ganhos vêm
//   das frequências centrais DO APARELHO — ver [clearDialogueBandGains].
//
// ── PASSTHROUGH ──────────────────────────────────────────────────────────
// Com passthrough (bitstream Dolby/DTS) o áudio codificado vai direto
// p/ o receiver e NÃO passa pelos efeitos da sessão: DRC e Clear
// Dialogue não têm como processar nada. O app HOJE não tem modo
// passthrough (o AudioSink padrão decodifica p/ PCM), então o flag
// nasce desligado; quando o passthrough existir, o coordenador liga
// `night.passthrough = true` e tudo abaixo vira no-op com explicação.
// [audioIsBitstreamEncoded] só diz se a faixa ATUAL é Dolby/DTS
// codificada — NÃO prova passthrough (o ExoPlayer decodifica p/ PCM
// por padrão) — é só um indício p/ a UI, nunca um bloqueio automático.
//
// ── PERSISTÊNCIA ─────────────────────────────────────────────────────────
// Por título (filme/episódio) e por canal (ao vivo), por perfil, via
// os acessores genéricos de PlatformPrefs (`rawPref`/`writeRawPref` —
// sem tocar em PlatformPrefs.kt). Chaves dinâmicas `night_mode_*`:
// não entram em SCOPED_KEYS, então sobrevivem à remoção de um perfil
// (lixo pequeno, sem dado sensível — aceito e documentado aqui).

/** Intensidade padrão do modo noturno (0..100). */
const val NIGHT_MODE_INTENSITY_DEFAULT = 50

/** Chaves de pref (sufixo do titleKey) — ver [nightModeTitleKeyMovie] etc. */
private const val KEY_NIGHT_ON = "night_mode_on_"
private const val KEY_NIGHT_CLEAR = "night_mode_clear_"
private const val KEY_NIGHT_INTENSITY = "night_mode_intensity_"

/** Estado do modo noturno p/ um título/canal. */
data class NightModeState(
    val nightOn: Boolean = false,
    val clearDialogueOn: Boolean = false,
    val intensity: Int = NIGHT_MODE_INTENSITY_DEFAULT,
    /** true = passthrough ligado: DRC e Clear Dialogue não processam nada. */
    val passthrough: Boolean = false,
)

/** Motor de DRC em uso (p/ o painel dizer o que está acontecendo de verdade). */
enum class NightDrcEngine(val label: String) {
    /** Compressor de verdade (DynamicsProcessing, API 28+). */
    DYNAMICS_PROCESSING("Compressão (DynamicsProcessing)"),
    /** Ganho simples — NÃO é compressão, ver o doc do arquivo. */
    LOUDNESS_FALLBACK("Ganho simples (sem compressão neste aparelho)"),
    /** Nenhum dos dois aceito pelo aparelho. */
    NONE("Indisponível neste aparelho"),
}

/** Chave de persistência p/ um filme (tmdbId). */
fun nightModeTitleKeyMovie(tmdbId: Long): String = "m$tmdbId"

/** Chave de persistência p/ um episódio. */
fun nightModeTitleKeyEpisode(tmdbId: Long, season: Int, episode: Int): String =
    "s${tmdbId}_t${season}e$episode"

/** Chave de persistência p/ um canal ao vivo. */
fun nightModeTitleKeyLive(channelId: String): String =
    "live_" + channelId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

/** Lê o estado guardado p/ [titleKey] (padrões quando nunca foi salvo). */
fun loadNightModeState(prefs: PlatformPrefs, titleKey: String): NightModeState {
    val on = (prefs.rawPref(KEY_NIGHT_ON + titleKey) as? Boolean) ?: false
    val clear = (prefs.rawPref(KEY_NIGHT_CLEAR + titleKey) as? Boolean) ?: false
    val intensity = when (val v = prefs.rawPref(KEY_NIGHT_INTENSITY + titleKey)) {
        is Int -> v
        is Long -> v.toInt()
        is Float -> v.toInt()
        else -> NIGHT_MODE_INTENSITY_DEFAULT
    }.coerceIn(0, 100)
    return NightModeState(on, clear, intensity)
}

/** Salva o estado p/ [titleKey] (por perfil; `passthrough` não é persistido — é do aparelho). */
fun saveNightModeState(prefs: PlatformPrefs, titleKey: String, state: NightModeState) {
    prefs.writeRawPref(KEY_NIGHT_ON + titleKey, state.nightOn)
    prefs.writeRawPref(KEY_NIGHT_CLEAR + titleKey, state.clearDialogueOn)
    prefs.writeRawPref(KEY_NIGHT_INTENSITY + titleKey, state.intensity.coerceIn(0, 100))
}

/**
 * Parâmetros do compressor p/ uma intensidade (0..100), em valores
 * diretos do `MbcBand` (dB/dBFS/ms). Intensidade maior = limiar mais
 * baixo + ratio maior + mais ganho de saída (o som baixo sobe mais).
 */
data class NightDrcParams(
    val thresholdDb: Float,
    val ratio: Float,
    val postGainDb: Float,
)

/** 0 → −12 dB/2:1/+3 dB · 50 → −21 dB/5:1/+6 dB · 100 → −30 dB/8:1/+9 dB (linear). */
fun nightDrcParams(intensity: Int): NightDrcParams {
    val t = intensity.coerceIn(0, 100) / 100f
    return NightDrcParams(
        thresholdDb = -12f - 18f * t,
        ratio = 2f + 6f * t,
        postGainDb = 3f + 6f * t,
    )
}

/**
 * Compressor do modo noturno numa sessão de áudio.
 *
 * API 28+: `DynamicsProcessing` com 1 banda (0–20 kHz), ataque 5 ms,
 * release 250 ms, joelho 6 dB, sem noise gate (limiar −90 dBFS) e sem
 * expansor. O limiter fica DESLIGADO de propósito: a configuração
 * padrão do limiter no `Config.Builder` não é documentada e um teto
 * errado achataria o som; o compressor já segura os picos no limiar.
 * Abaixo da API 28 (ou aparelho que recusa o DynamicsProcessing):
 * `LoudnessEnhancer` com ganho por intensidade — ganho simples, NÃO
 * compressão (ver o doc do arquivo).
 *
 * [channelCount] vem do formato de áudio atual; se ele mudar (troca
 * de faixa), o [NightModeController] recria o efeito.
 */
class NightModeDrc(sessionId: Int, channelCount: Int = 2) {
    private var dynamics: DynamicsProcessing? = null
    private var loudness: LoudnessEnhancer? = null
    private var lastIntensity = NIGHT_MODE_INTENSITY_DEFAULT

    /** Motor em uso (NONE quando o aparelho recusou os dois efeitos). */
    val engine: NightDrcEngine
    /** true quando algum motor aceitou (o painel usa p/ habilitar o toggle). */
    val supported: Boolean get() = engine != NightDrcEngine.NONE

    init {
        engine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = runCatching { createDynamics(sessionId, channelCount) }.getOrNull()
            if (dp != null) {
                dynamics = dp
                NightDrcEngine.DYNAMICS_PROCESSING
            } else {
                val le = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
                if (le != null) {
                    loudness = le
                    NightDrcEngine.LOUDNESS_FALLBACK
                } else NightDrcEngine.NONE
            }
        } else {
            val le = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
            if (le != null) {
                loudness = le
                NightDrcEngine.LOUDNESS_FALLBACK
            } else NightDrcEngine.NONE
        }
    }

    private fun createDynamics(sessionId: Int, channelCount: Int): DynamicsProcessing {
        val channels = channelCount.coerceIn(1, 8)
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            channels,
            /* preEqInUse = */ false, /* preEqBandCount = */ 0,
            /* mbcInUse = */ true, /* mbcBandCount = */ 1,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ false,
        ).build()
        return DynamicsProcessing(0, sessionId, cfg)
    }

    /** Banda do compressor p/ a intensidade (1 banda cobrindo 0–20 kHz). */
    private fun nightBand(intensity: Int): DynamicsProcessing.MbcBand {
        val p = nightDrcParams(intensity)
        return DynamicsProcessing.MbcBand(
            /* enabled = */ true,
            /* cutoffFrequency = */ 20000f,
            /* attackTime = */ 5f,
            /* releaseTime = */ 250f,
            /* ratio = */ p.ratio,
            /* threshold = */ p.thresholdDb,
            /* kneeWidth = */ 6f,
            /* noiseGateThreshold = */ -90f,
            /* expanderRatio = */ 1f,
            /* preGain = */ 0f,
            /* postGain = */ p.postGainDb,
        )
    }

    /** Aplica a intensidade atual ao motor (sem ligar/desligar o efeito). */
    fun setIntensity(intensity: Int) {
        lastIntensity = intensity.coerceIn(0, 100)
        val dp = dynamics
        if (dp != null) {
            runCatching { dp.setMbcBandAllChannelsTo(0, nightBand(lastIntensity)) }
            return
        }
        val le = loudness ?: return
        // Ganho simples: +3..+9 dB conforme a intensidade (mB = dB × 100).
        runCatching { le.setTargetGain(((3f + 6f * lastIntensity / 100f) * 100f).toInt()) }
    }

    /** Liga/desliga o efeito (a intensidade vigente é aplicada ao ligar). */
    fun setEnabled(enabled: Boolean) {
        val dp = dynamics
        if (dp != null) {
            runCatching {
                if (enabled) setIntensity(lastIntensity)
                dp.enabled = enabled
            }
            return
        }
        val le = loudness ?: return
        runCatching {
            if (enabled) setIntensity(lastIntensity)
            le.enabled = enabled
        }
    }

    /** Libera o efeito (ao trocar de sessão ou fechar o player). */
    fun release() {
        runCatching { dynamics?.release() }
        dynamics = null
        runCatching { loudness?.release() }
        loudness = null
    }
}

/**
 * Ganhos por banda do Clear Dialogue, em dB, a partir das frequências
 * centrais DO APARELHO: +boost nas bandas de 1–4 kHz (voz),
 * −corte nas bandas abaixo de 200 Hz (ronco de música/efeitos),
 * 0 dB no resto. Sem banda em 1–4 kHz, o boost vai p/ a banda mais
 * próxima de 2 kHz. Boost +3..+8 dB e corte −3..−6 dB conforme a
 * intensidade (0..100). Pura (sem efeito): dá p/ testar sem aparelho.
 */
fun clearDialogueBandGains(centerFreqHz: List<Int>, intensity: Int): List<Int> {
    val t = intensity.coerceIn(0, 100)
    val boost = 3 + (5 * t) / 100
    val cut = -(3 + (3 * t) / 100)
    val gains = centerFreqHz.map { hz ->
        when {
            hz in 1000..4000 -> boost
            hz in 1..199 -> cut
            else -> 0
        }
    }
    if (gains.none { it == boost } && centerFreqHz.isNotEmpty()) {
        val nearest = centerFreqHz.indices.minByOrNull { kotlin.math.abs(centerFreqHz[it] - 2000) } ?: return gains
        return gains.mapIndexed { i, g -> if (i == nearest) boost else g }
    }
    return gains
}

/**
 * Equalizador de voz do Clear Dialogue — instância própria de
 * `Equalizer` na sessão (separada do equalizador do usuário; o
 * framework encadeia os efeitos). Sem suporte no aparelho, [supported]
 * é false e [apply] não faz nada.
 */
class ClearDialogueEq(sessionId: Int) {
    private val eq: Equalizer? = runCatching { Equalizer(0, sessionId) }.getOrNull()

    /** true quando o efeito foi criado e está usável. */
    val supported: Boolean get() = eq != null

    /** Nº de bandas do aparelho (em geral 5). 0 sem suporte. */
    val bandCount: Int get() = runCatching { eq?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)

    /** Frequência central de cada banda, em Hz (do aparelho). */
    val centerFreqHz: List<Int> get() =
        (0 until bandCount).map { runCatching { eq?.getCenterFreq(it.toShort()) ?: 0 }.getOrDefault(0) }

    /** Faixa de ganho em dB (em geral −15..15), do `bandLevelRange` do aparelho. */
    private val levelRangeDb: IntRange get() {
        val r = runCatching { eq?.bandLevelRange }.getOrNull() ?: return -15..15
        return (r[0] / 100)..(r[1] / 100)
    }

    /** Aplica o reforço de voz p/ a intensidade (0..100). */
    fun apply(intensity: Int) {
        val e = eq ?: return
        val range = levelRangeDb
        runCatching {
            clearDialogueBandGains(centerFreqHz, intensity).forEachIndexed { i, db ->
                if (i < bandCount) e.setBandLevel(i.toShort(), (db.coerceIn(range) * 100).toShort())
            }
            e.enabled = true
        }
    }

    /** Desliga o efeito (volta o som ao original; os ganhos ficam p/ a próxima vez). */
    fun clear() {
        runCatching { eq?.enabled = false }
    }

    /** Libera o efeito (ao trocar de sessão ou fechar o player). */
    fun release() {
        runCatching { eq?.release() }
    }
}

/** Mimes de áudio codificado em bitstream (Dolby/DTS) — p/ o indício de passthrough. */
private val BITSTREAM_AUDIO_MIMES = setOf(
    "audio/ac3", "audio/eac3", "audio/vnd.dts", "audio/vnd.dts.hd",
    "audio/vnd.dts.hd;profile=ma", "audio/truehd", "audio/vnd.dolby.truehd",
)

/**
 * true quando a faixa de áudio ATUAL do player é Dolby/DTS codificada.
 * NÃO prova passthrough — o ExoPlayer decodifica p/ PCM por padrão —
 * só um indício p/ a UI ("o áudio atual é Dolby/DTS"). Null = sem faixa.
 */
fun audioIsBitstreamEncoded(player: androidx.media3.exoplayer.ExoPlayer): Boolean? {
    val mime = runCatching { player.audioFormat?.sampleMimeType }.getOrNull() ?: return null
    return mime.lowercase() in BITSTREAM_AUDIO_MIMES
}

/**
 * Dono dos efeitos do modo noturno numa sessão de player. Uma
 * instância por tela de player (filme/série OU ao vivo); o coordenador
 * cria no `remember`, aplica em `onAudioSessionIdChanged` + quando o
 * estado muda, e libera no dispose.
 *
 * Com [passthrough] ligado, [apply] não cria nem liga efeito nenhum e
 * [blockedReason] explica — os toggles do painel ficam desligados.
 */
class NightModeController(private val prefs: PlatformPrefs) {
    /** true = passthrough (bitstream) ligado: DRC/boost não processam nada. */
    var passthrough: Boolean = false

    private var drc: NightModeDrc? = null
    private var clearEq: ClearDialogueEq? = null
    private var lastSessionId: Int = 0
    private var lastChannelCount: Int = 0

    /** Motor de DRC da sessão atual (NONE sem sessão aplicada ainda). */
    val drcEngine: NightDrcEngine get() = drc?.engine ?: NightDrcEngine.NONE

    /** true quando o aparelho aceitou o compressor (ou o fallback de ganho). */
    val drcSupported: Boolean get() = drc?.supported ?: false

    /** true quando o aparelho aceitou o Equalizer de voz. */
    val clearSupported: Boolean get() = clearEq?.supported ?: false

    /**
     * Explicação do bloqueio quando o passthrough impede o processamento,
     * ou null quando nada impede. O painel mostra quando não-nulo.
     */
    fun blockedReason(state: NightModeState): String? {
        if (!state.passthrough && !passthrough) return null
        return "Passthrough ligado: o áudio vai direto p/ o receiver em " +
            "bitstream (Dolby/DTS) e não passa pelo processamento — " +
            "modo noturno e Clear Dialogue ficam desligados."
    }

    /**
     * (Re)aplica o estado na sessão de áudio [sessionId]. Sessão 0 =
     * sem áudio ainda: libera os efeitos e espera a próxima chamada.
     * Recria os efeitos quando a sessão ou o nº de canais muda.
     */
    fun apply(sessionId: Int, channelCount: Int, state: NightModeState) {
        if (sessionId == 0) {
            release()
            return
        }
        if (sessionId != lastSessionId || channelCount != lastChannelCount) {
            release()
            lastSessionId = sessionId
            lastChannelCount = channelCount
        }
        val blocked = state.passthrough || passthrough
        // Modo noturno (DRC).
        if (state.nightOn && !blocked) {
            val d = drc ?: NightModeDrc(sessionId, channelCount).also { drc = it }
            d.setEnabled(true)
            d.setIntensity(state.intensity)
        } else {
            drc?.setEnabled(false)
        }
        // Clear Dialogue (EQ de voz).
        if (state.clearDialogueOn && !blocked) {
            val e = clearEq ?: ClearDialogueEq(sessionId).also { clearEq = it }
            e.apply(state.intensity)
        } else {
            clearEq?.clear()
        }
    }

    /** Libera os efeitos (ao fechar o player). */
    fun release() {
        runCatching { drc?.release() }
        drc = null
        runCatching { clearEq?.release() }
        clearEq = null
        lastSessionId = 0
        lastChannelCount = 0
    }
}
