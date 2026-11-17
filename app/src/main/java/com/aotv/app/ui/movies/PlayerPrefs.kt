package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import com.aotv.app.data.local.DEFAULT_PLAYER_BUTTONS
import com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.SubLangs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Rótulo em pt-BR de um botão do player, pelo id canônico. */
fun playerButtonLabel(id: String): String = when (id) {
    "subs" -> "Legendas"
    "audio" -> "Áudio"
    "sources" -> "Fontes"
    "episodes" -> "Episódios"
    "prev" -> "Anterior"
    "next" -> "Próximo"
    "screen" -> "Tela"
    "speed" -> "Velocidade"
    "pin" -> "Fixar fonte"
    "skip" -> "Pular abertura"
    "color" -> "Cor"
    "info" -> "Infos técnicas"
    else -> id
}

/** Rótulo em pt-BR de um botão do topo do player ("Voltar" sempre aparece). */
fun playerTopActionLabel(id: String): String = when (id) {
    "cast" -> "Transmitir"
    "rotate" -> "Girar"
    "lock" -> "Travar"
    else -> id
}

/** Botões do player em vigor: os guardados, ou o padrão quando não há nada salvo. */
fun effectivePlayerButtons(saved: List<String>): List<String> =
    saved.ifEmpty { DEFAULT_PLAYER_BUTTONS }

/** Botões do topo do player em vigor: os guardados, ou o padrão quando não há nada salvo. */
fun effectivePlayerTopActions(saved: List<String>): List<String> =
    saved.ifEmpty { DEFAULT_PLAYER_TOP_ACTIONS }

/**
 * Preferências do player (Fase C). Lê os fluxos do PlatformPrefs, então mudar
 * nas Configurações aplica na hora no player, mesmo com ele na pilha.
 * Lista guardada vazia = padrão; guardar a lista igual ao padrão limpa o salvo.
 */
@HiltViewModel
class PlayerPrefsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
) : ViewModel() {
    /** Botões do player guardados, na ordem (vazio = padrão). */
    val buttons: StateFlow<List<String>> = prefs.playerButtonsFlow

    /** Move um botão da lista em vigor ([delta] -1 sobe, 1 desce). Só vale para botão ligado. */
    fun moveButton(id: String, delta: Int) {
        val cur = effectivePlayerButtons(buttons.value).toMutableList()
        val i = cur.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in cur.indices) return
        cur[i] = cur[j].also { cur[j] = cur[i] }
        saveButtons(cur)
    }

    /** Liga ou desliga um botão. Ligar volta o botão na posição que ele tem no padrão. */
    fun setButtonEnabled(id: String, enabled: Boolean) {
        val cur = effectivePlayerButtons(buttons.value).toMutableList()
        if (enabled) {
            if (id !in cur) {
                val pos = cur.count { DEFAULT_PLAYER_BUTTONS.indexOf(it) < DEFAULT_PLAYER_BUTTONS.indexOf(id) }
                cur.add(pos, id)
            }
        } else {
            cur.remove(id)
        }
        saveButtons(cur)
    }

    /** Volta os botões do player para o padrão (limpa o salvo). */
    fun resetButtons() { prefs.playerButtons = emptyList() }

    private fun saveButtons(list: List<String>) {
        prefs.playerButtons = if (list == DEFAULT_PLAYER_BUTTONS) emptyList() else list
    }

    /** Botões do topo do player guardados (vazio = padrão). "Voltar" sempre aparece. */
    val topActions: StateFlow<List<String>> = prefs.playerTopActionsFlow

    /** Liga ou desliga um botão do topo. */
    fun setTopActionEnabled(id: String, enabled: Boolean) {
        val cur = effectivePlayerTopActions(topActions.value).toMutableList()
        if (enabled) {
            if (id !in cur) {
                val pos = cur.count { DEFAULT_PLAYER_TOP_ACTIONS.indexOf(it) < DEFAULT_PLAYER_TOP_ACTIONS.indexOf(id) }
                cur.add(pos, id)
            }
        } else {
            cur.remove(id)
        }
        prefs.playerTopActions = if (cur == DEFAULT_PLAYER_TOP_ACTIONS) emptyList() else cur
    }

    val tapSeek: StateFlow<Boolean> = prefs.playerTapSeekFlow
    fun setTapSeek(v: Boolean) { prefs.playerTapSeek = v }

    /** Sincronizar a taxa de atualização da tela com o fps do stream (Lote 16 item 4). */
    val frameRate: StateFlow<Boolean> = prefs.playerFrameRateFlow
    fun setFrameRate(v: Boolean) { prefs.playerFrameRate = v }

    /** "Foreign audio only" (Lote 16 item 3): a legenda só abre sozinha quando o áudio é estrangeiro. */
    val subForeignOnly: StateFlow<Boolean> = prefs.subForeignOnlyFlow
    fun setSubForeignOnly(v: Boolean) { prefs.subForeignOnly = v }

    /** Ocultar legendas SDH/CC na escolha automática (Lote 16 item 3). */
    val subHideSdh: StateFlow<Boolean> = prefs.subHideSdhFlow
    fun setSubHideSdh(v: Boolean) { prefs.subHideSdh = v }

    val swipeSeek: StateFlow<Boolean> = prefs.playerSwipeSeekFlow
    fun setSwipeSeek(v: Boolean) { prefs.playerSwipeSeek = v }

    val volBoost: StateFlow<Boolean> = prefs.playerVolBoostFlow
    fun setVolBoost(v: Boolean) { prefs.playerVolBoost = v }

    val volBoostLevel: StateFlow<Float> = prefs.playerVolBoostLevelFlow
    fun setVolBoostLevel(v: Float) { prefs.playerVolBoostLevel = v.coerceIn(1f, 2f) }

    val mini: StateFlow<Boolean> = prefs.playerMiniFlow
    fun setMini(v: Boolean) { prefs.playerMini = v }

    val autoNext: StateFlow<Boolean> = prefs.playerAutoNextFlow
    fun setAutoNext(v: Boolean) { prefs.playerAutoNext = v }

    val autoNextDelay: StateFlow<Int> = prefs.playerAutoNextDelayFlow
    fun setAutoNextDelay(v: Int) { prefs.playerAutoNextDelay = v }

    val autoSwitch: StateFlow<Boolean> = prefs.playerAutoSwitchFlow
    fun setAutoSwitch(v: Boolean) { prefs.playerAutoSwitch = v }

    /** Guarda de duração (Lote 11, v1.21.0): avisar antes de tocar um arquivo com duração >10% diferente da do título. */
    val durationGuard: StateFlow<Boolean> = prefs.durationGuardFlow
    fun setDurationGuard(v: Boolean) { prefs.durationGuard = v }

    /** Tempo do watchdog antes de trocar de fonte, em segundos (8/12/20/30/45/60). */
    val sourceTimeout: StateFlow<Int> = prefs.playerSourceTimeoutFlow
    fun setSourceTimeout(v: Int) {
        val allowed = listOf(8, 12, 20, 30, 45, 60)
        prefs.playerSourceTimeout = allowed.minByOrNull { kotlin.math.abs(it - v) } ?: 12
    }

    /**
     * Subir a qualidade sozinho (Lote 19 item 7e, v1.29.0): com o toggle
     * ligado, o player troca 1x p/ uma fonte melhor logo no começo, sem
     * perder o ponto; desligado, fica na qualidade que começou. Padrão ligado.
     */
    val autoUpgrade: StateFlow<Boolean> = prefs.playerAutoUpgradeFlow
    fun setAutoUpgrade(v: Boolean) { prefs.playerAutoUpgrade = v }

    /**
     * Debrid só com arquivo pronto (Lote 19 item 7f, v1.29.0): com o toggle
     * ligado, a troca automática nunca abre torrent que o debrid ativo ainda
     * não tem pronto, p/ a conta não começar downloads sozinha. Sem conta de
     * debrid ativa, não muda nada. Padrão ligado.
     */
    val debridReadyOnly: StateFlow<Boolean> = prefs.playerDebridReadyOnlyFlow
    fun setDebridReadyOnly(v: Boolean) { prefs.playerDebridReadyOnly = v }

    /** Client ID do Anime-Skip, digitado pelo usuário ("" = desligado). */
    val animeSkipClientId: StateFlow<String> = prefs.playerAnimeSkipClientIdFlow
    fun setAnimeSkipClientId(v: String) { prefs.playerAnimeSkipClientId = v.trim() }

    /** Chave grátis do SubDL, digitada pelo usuário ("" = SubDL desligado, Lote 16 item 3). */
    val subDlApiKey: StateFlow<String> = prefs.subDlApiKeyFlow
    fun setSubDlApiKey(v: String) { prefs.subDlApiKey = v.trim() }

    // ── Lote 4 — Player parte 2 (v1.12.0) ────────────────────────────────────
    // Idiomas preferidos de áudio/legenda + reutilizar a última fonte do título.

    /**
     * Idioma preferido da faixa de ÁUDIO (código SubLangs, ex. "pob", "eng";
     * padrão "pob"). O player usa a família ISO 639-1 dele no ExoPlayer e
     * escolhe a faixa nesse idioma ao abrir o vídeo. Valor desconhecido volta
     * p/ "pob" (mesmo comportamento de antes, que era "pt" fixo).
     */
    val audioLang: StateFlow<String> = prefs.playerAudioLangFlow
    fun setAudioLang(v: String) { prefs.playerAudioLang = v }

    /**
     * Idioma preferido da LEGENDA. A pref já existia (`pref_sub_lang`, por
     * perfil, no PlatformPrefs) e o pickAutoSub já a usava; aqui ela só ganha
     * o mesmo acesso do PlayerPrefsViewModel que o áudio, p/ a tela do player
     * e as Configurações lerem as duas no mesmo lugar. O fluxo local espelha
     * o valor guardado (mesmo padrão do SettingsViewModel).
     */
    private val _subLang = MutableStateFlow(prefs.subLang)
    val subLang: StateFlow<String> = _subLang
    fun setSubLang(v: String) {
        val clean = v.takeIf { SubLangs.of(it) != null } ?: SubLangs.DEFAULT
        prefs.subLang = clean; _subLang.value = clean
    }

    /** Reutilizar a última fonte escolhida à mão em cada título (padrão ligado). */
    val reuseLastSource: StateFlow<Boolean> = prefs.playerReuseLastSourceFlow
    fun setReuseLastSource(v: Boolean) { prefs.playerReuseLastSource = v }

    /** Modo privado do perfil (Lote 2, v1.10.0): assistir sem gravar nada. */
    val privateMode: StateFlow<Boolean> = prefs.privateModeFlow
    fun setPrivateMode(v: Boolean) { prefs.privateMode = v }

    /** Blur/vidro fosco nos painéis do player (Lote 4, v1.12.0): mesma pref `pref_panel_blur` do detalhe. */
    val panelBlur: StateFlow<Boolean> = prefs.panelBlurFlow

    /** Cor do texto da legenda (ARGB; branco por padrão). v1.9.0. */
    val subColor: StateFlow<Int> = prefs.subColorFlow
    fun setSubColor(v: Int) { prefs.subColor = v }

    /** Opacidade do fundo da legenda (0,5–0,9; 0,78 por padrão). v1.9.0. */
    val subBgOpacity: StateFlow<Float> = prefs.subBgOpacityFlow
    fun setSubBgOpacity(v: Float) { prefs.subBgOpacity = v }

    /** Contorno preto ao redor do texto quando não há fundo. v1.9.0. */
    val subOutline: StateFlow<Boolean> = prefs.subOutlineFlow
    fun setSubOutline(v: Boolean) { prefs.subOutline = v }

    /** Fonte da legenda ("default", "monospace", "serif" ou "sans"). v1.9.0. */
    val subFont: StateFlow<String> = prefs.subFontFlow
    fun setSubFont(v: String) { prefs.subFont = v }

    /** Velocidade de reprodução salva do perfil (0,5x–2x; 1x por padrão). v1.9.0. */
    val speed: StateFlow<Float> = prefs.playerSpeedFlow
    fun setSpeed(v: Float) { prefs.playerSpeed = v }

    /**
     * Notas dos episódios visíveis (Lote 2 item 5). Só LEITURA aqui: a pref é
     * do detalhe (`pref_detail_episode_ratings`, por perfil) e o controle de
     * verdade fica em Configurações → Página de detalhes → Episódios. O player
     * só consome, p/ esconder o "★ nota" do painel de episódios junto com os
     * cards do detalhe — uma fonte única, sem segunda pref p/ o mesmo ajuste.
     */
    val episodeRatings: StateFlow<Boolean> = prefs.detailEpisodeRatingsFlow

    /** Filtro de correção de cor do vídeo (Lote 1, v1.9.0). */
    val colorBrightness: StateFlow<Float> = prefs.playerColorBrightnessFlow
    fun setColorBrightness(v: Float) { prefs.playerColorBrightness = v.coerceIn(-1f, 1f) }

    val colorContrast: StateFlow<Float> = prefs.playerColorContrastFlow
    fun setColorContrast(v: Float) { prefs.playerColorContrast = v.coerceIn(0f, 2f) }

    val colorSaturation: StateFlow<Float> = prefs.playerColorSaturationFlow
    fun setColorSaturation(v: Float) { prefs.playerColorSaturation = v.coerceIn(0f, 2f) }

    val colorHue: StateFlow<Float> = prefs.playerColorHueFlow
    fun setColorHue(v: Float) { prefs.playerColorHue = v.coerceIn(-180f, 180f) }

    val colorPreset: StateFlow<String> = prefs.playerColorPresetFlow
    fun setColorPreset(v: String) { prefs.playerColorPreset = v }

    val colorDaltonism: StateFlow<String> = prefs.playerColorDaltonismFlow
    fun setColorDaltonism(v: String) { prefs.playerColorDaltonism = v }

    /** Player padrão do perfil (Lote 4, v1.12.0): "exoplayer" (interno) ou "external". */
    val engine: StateFlow<String> = prefs.playerEngineFlow
    fun setEngine(v: String) { prefs.playerEngine = if (v == "external") "external" else "exoplayer" }

    /** Decoder de vídeo do perfil (Lote 4, v1.12.0): "hw" ou "hw_fallback". */
    val decoder: StateFlow<String> = prefs.playerDecoderFlow
    fun setDecoder(v: String) { prefs.playerDecoder = if (v == "hw_fallback") "hw_fallback" else "hw" }

    /**
     * Perfil de buffer do ExoPlayer (Lote 5 item 1, v1.14.0): "low", "normal"
     * ou "high" (ver [BufferProfile] em PlayerEngine.kt). "normal" é o
     * padrão do ExoPlayer. Vale a partir da próxima reprodução (o LoadControl
     * entra na criação do player). Valor desconhecido volta p/ "normal".
     */
    val buffer: StateFlow<String> = prefs.playerBufferFlow
    fun setBuffer(v: String) { prefs.playerBuffer = v }

    // ── Lote 9 — Player parte 3 (v1.19.0) ───────────────────────────────────
    // AutoSync (mede o atraso contra a legenda embutida, nunca inventa),
    // pular abertura automático, cartão "A seguir" e equalizador. Ver
    // PlayerAutoSync.kt e PlayerEqualizer.kt.

    /** Sincronização automática da legenda (padrão desligada; o botão no painel de Legendas mede o atraso). */
    val autoSync: StateFlow<Boolean> = prefs.playerAutoSyncFlow
    fun setAutoSync(v: Boolean) { prefs.playerAutoSync = v }

    /** Pular abertura/encerramento automaticamente ao entrar no segmento (padrão desligado). */
    val autoSkip: StateFlow<Boolean> = prefs.playerAutoSkipFlow
    fun setAutoSkip(v: Boolean) { prefs.playerAutoSkip = v }

    /** Cartão "A seguir" no fim do episódio (padrão ligado; o avanço automático é outra pref). */
    val upNext: StateFlow<Boolean> = prefs.playerUpNextFlow
    fun setUpNext(v: Boolean) { prefs.playerUpNext = v }

    /** Equalizador ligado (padrão desligado; só vale com o player aberto). */
    val equalizer: StateFlow<Boolean> = prefs.playerEqualizerFlow
    fun setEqualizer(v: Boolean) { prefs.playerEqualizer = v }

    /** Preset do equalizador (nome do preset do aparelho; "flat" = tudo em 0 dB). */
    val eqPreset: StateFlow<String> = prefs.playerEqPresetFlow
    fun setEqPreset(v: String) { prefs.playerEqPreset = v }

    /** Bandas do equalizador em dB inteiros, CSV ("0,3,-2,0,0"); "" = tudo em 0 dB. */
    val eqBands: StateFlow<String> = prefs.playerEqBandsFlow
    fun setEqBands(v: String) { prefs.playerEqBands = v }

    // ── Lote 12 — Frente 2 (Player e fontes, v1.22.0) ───────────────────────

    /** Modo de tela padrão do player (0 = Automático, 1 = Ajustar, 2 = Preencher, 3 = Zoom, 4 = Esticar). */
    val screenMode: StateFlow<Int> = prefs.playerScreenModeFlow
    fun setScreenMode(v: Int) { prefs.playerScreenMode = v }

    /** Folha de fontes em ranking (melhor → pior) em vez de agrupada por addon (padrão desligado). */
    val sourcesRanked: StateFlow<Boolean> = prefs.playerSourcesRankedFlow
    fun setSourcesRanked(v: Boolean) { prefs.playerSourcesRanked = v }
}
