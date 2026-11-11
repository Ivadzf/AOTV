package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val platformPrefs: PlatformPrefs,
    private val healthChecker: com.aotv.app.data.remote.SourceHealthChecker,
    private val trakt: com.aotv.app.data.remote.TraktService,
    private val history: com.aotv.app.data.local.dao.MovieHistoryDao,
) : ViewModel() {

    // ── Trakt (só aparece com as chaves no local.properties) ──
    val traktConfigured = trakt.configured
    private val _traktUser = MutableStateFlow(trakt.user())
    /** Conta conectada no perfil ativo. null sem login. */
    val traktUser: StateFlow<String?> = _traktUser.asStateFlow()
    /** Código do login em andamento. null fora do login. */
    private val _traktCode = MutableStateFlow<com.aotv.app.data.remote.TraktService.DeviceCode?>(null)
    val traktCode: StateFlow<com.aotv.app.data.remote.TraktService.DeviceCode?> = _traktCode.asStateFlow()
    /** Chamada de rede do Trakt em andamento (conectar ou sincronizar). Trava o segundo clique. */
    private val _traktBusy = MutableStateFlow<String?>(null)
    val traktBusy: StateFlow<String?> = _traktBusy.asStateFlow()
    private var loginJob: kotlinx.coroutines.Job? = null

    fun traktConnect() {
        if (_traktBusy.value != null) return
        _traktBusy.value = "Pedindo código…"
        loginJob = viewModelScope.launch {
            val code = trakt.startLogin()
            if (code == null) { _traktBusy.value = null; return@launch }
            _traktCode.value = code
            _traktBusy.value = "Aguardando confirmação…"
            val name = trakt.waitLogin(code)
            _traktCode.value = null
            _traktBusy.value = null
            if (name != null) _traktUser.value = name
        }
    }

    fun traktCancel() { loginJob?.cancel(); _traktCode.value = null; _traktBusy.value = null }

    fun traktSync() {
        if (_traktBusy.value != null) return
        _traktBusy.value = "Salvando…"
        viewModelScope.launch { trakt.sync(); _traktBusy.value = null }
    }

    fun traktLogout() { trakt.logout(); _traktUser.value = null }

    /** Limpar Continuar assistindo: apaga as posições salvas do perfil ativo. Minha Lista continua. */
    fun clearContinueWatching() {
        viewModelScope.launch { history.clear(profileId.value) }
    }

    // ── Status das fontes (health-check) ──
    private val _sourceStatus = MutableStateFlow<List<com.aotv.app.data.remote.SourceStatus>>(emptyList())
    val sourceStatus: StateFlow<List<com.aotv.app.data.remote.SourceStatus>> = _sourceStatus.asStateFlow()

    private val _checkingHealth = MutableStateFlow(false)
    val checkingHealth: StateFlow<Boolean> = _checkingHealth.asStateFlow()

    fun checkSourcesHealth() {
        if (_checkingHealth.value) return
        viewModelScope.launch {
            _checkingHealth.value = true
            _sourceStatus.value = runCatching { healthChecker.checkAll() }.getOrDefault(emptyList())
            _checkingHealth.value = false
        }
    }

    // ── Perfil e reprodução (valem para o perfil ativo) ──
    val profiles = platformPrefs.profiles
    val profileId = platformPrefs.profileId

    val hasParentalPin: Boolean get() = platformPrefs.hasParentalPin
    fun setParentalPin(pin: String) = platformPrefs.setParentalPin(pin)

    private val _audio = MutableStateFlow(platformPrefs.audioPref)
    val audioPref: StateFlow<String> = _audio.asStateFlow()
    fun setAudio(v: String) { platformPrefs.audioPref = v; _audio.value = v }

    private val _maxQuality = MutableStateFlow(platformPrefs.maxQuality)
    val maxQuality: StateFlow<Int> = _maxQuality.asStateFlow()
    fun setMaxQuality(v: Int) { platformPrefs.maxQuality = v; _maxQuality.value = v }

    private val _heroTrailer = MutableStateFlow(platformPrefs.heroTrailer)
    val heroTrailer: StateFlow<Boolean> = _heroTrailer.asStateFlow()
    fun setHeroTrailer(v: Boolean) { platformPrefs.heroTrailer = v; _heroTrailer.value = v }

    // Legenda do perfil. Ligada ao abrir e o idioma em que ela liga sozinha.
    private val _subOn = MutableStateFlow(platformPrefs.subtitleOn)
    val subOn: StateFlow<Boolean> = _subOn.asStateFlow()
    fun setSubOn(v: Boolean) { platformPrefs.subtitleOn = v; _subOn.value = v }

    private val _subLang = MutableStateFlow(platformPrefs.subLang)
    val subLang: StateFlow<String> = _subLang.asStateFlow()
    fun setSubLang(v: String) { platformPrefs.subLang = v; _subLang.value = v }

    private val _platformMode = MutableStateFlow(platformPrefs.platformMode)
    val platformMode: StateFlow<PlatformMode> = _platformMode.asStateFlow()

    private val _customTmdbApiKey = MutableStateFlow(platformPrefs.customTmdbApiKey)
    val customTmdbApiKey: StateFlow<String> = _customTmdbApiKey.asStateFlow()

    /** Alguma chave TMDB em uso (pessoal ou padrão do app). */
    private val _hasTmdbKey = MutableStateFlow(platformPrefs.tmdbApiKey.isNotBlank())
    val hasTmdbKey: StateFlow<Boolean> = _hasTmdbKey.asStateFlow()

    /** Addons instalados, reativo: instalar pela Loja já atualiza a contagem aqui sozinho. */
    val addons: StateFlow<List<com.aotv.app.data.local.AddonEntry>> = platformPrefs.addons

    fun setPlatformMode(mode: PlatformMode) {
        platformPrefs.platformMode = mode
        _platformMode.value = mode
    }

    fun setTmdbApiKey(key: String) {
        platformPrefs.customTmdbApiKey = key
        _customTmdbApiKey.value = platformPrefs.customTmdbApiKey
        _hasTmdbKey.value = platformPrefs.tmdbApiKey.isNotBlank()
    }
}
