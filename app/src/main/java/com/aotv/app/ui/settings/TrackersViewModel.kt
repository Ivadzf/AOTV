package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AniListService
import com.aotv.app.data.remote.MDBListService
import com.aotv.app.data.remote.SimklService
import com.aotv.app.data.remote.TraktService
import com.aotv.app.data.remote.TvdbService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado da tela "Trackers e Sincronização" (Simkl na v1.13.1; Trakt na v1.17.0; MDBList, AniList e TheTVDB na v1.17.0). */
data class TrackersUiState(
    val profileName: String = "",
    val clientId: String = "",
    val connectedUser: String? = null,
    val expiresAt: Long = 0L,
    val pin: SimklService.DeviceCode? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    // Trakt (v1.17.0, mesmo padrão do Simkl).
    val traktClientId: String = "",
    val traktClientSecret: String = "",
    val traktUser: String? = null,
    val traktEnabled: Boolean = false,
    val traktPin: TraktService.DeviceCode? = null,
    val traktBusy: Boolean = false,
    val traktMessage: String? = null,
    val traktError: String? = null,
    // MDBList (v1.17.0, mesmo padrão do Trakt: chave do usuário + toggle).
    val mdbApiKey: String = "",
    val mdbEnabled: Boolean = false,
    val mdbSources: Set<String> = emptySet(),
    val mdbMessage: String? = null,
    val mdbError: String? = null,
    // AniList (v1.17.0, OAuth por deep link aotv://anilist/callback).
    val aniClientId: String = "",
    val aniConnected: Boolean = false,
    val aniEnabled: Boolean = false,
    val aniOAuthUrl: String = "",
    val aniMessage: String? = null,
    val aniError: String? = null,
    // TheTVDB (v1.17.0, mesmo padrão do Trakt: chave do usuário + toggle).
    val tvdbApiKey: String = "",
    val tvdbEnabled: Boolean = false,
    val tvdbOrdering: String = "official",
    val tvdbMessage: String? = null,
    val tvdbError: String? = null,
    // Avaliação pós-filme (v1.17.0): pedir nota ao fim do título e enviar aos trackers.
    val postPlayRatingEnabled: Boolean = true,
)

/**
 * ViewModel da tela de trackers (Simkl v1.13.1, device flow V2; Trakt v1.17.0;
 * MDBList, AniList e TheTVDB v1.17.0).
 *
 * O client ID do Simkl vive na pref existente [PlatformPrefs.simklClientId]
 * (por perfil, vazio por padrão) e a sessão em [PlatformPrefs.simklSession].
 * O Trakt vive no [TraktService] (client ID + client secret do usuário, por
 * perfil; toggle de ativação em [TraktService.isEnabled]). O polling do PIN
 * roda aqui; cancelar o diálogo cancela o job. MDBList, AniList e TheTVDB
 * seguem o mesmo padrão: chave/client ID do usuário (nunca embutida),
 * toggle de ativação e estado lido do serviço.
 */
@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val simkl: SimklService,
    private val trakt: TraktService,
    private val mdbList: MDBListService,
    private val aniList: AniListService,
    private val tvdb: TvdbService,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        TrackersUiState(
            profileName = prefs.profile.name,
            clientId = prefs.simklClientId,
            connectedUser = simkl.user(),
            expiresAt = simkl.expiresAt(),
            traktClientId = trakt.clientId,
            traktClientSecret = trakt.clientSecret,
            traktUser = trakt.user(),
            traktEnabled = trakt.isEnabled,
            mdbApiKey = mdbList.apiKey,
            mdbEnabled = mdbList.isEnabled,
            mdbSources = mdbList.getEnabledSources(),
            aniClientId = aniList.clientId,
            aniConnected = aniList.isConnected,
            aniEnabled = aniList.isEnabled,
            aniOAuthUrl = runCatching { aniList.buildOAuthUrl() }.getOrNull().orEmpty(),
            tvdbApiKey = tvdb.apiKey,
            tvdbEnabled = tvdb.isEnabled,
            tvdbOrdering = tvdb.getOrdering(),
            postPlayRatingEnabled = prefs.postPlayRatingEnabled,
        ),
    )
    val ui: StateFlow<TrackersUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null
    private var traktPollJob: Job? = null

    fun clearMessage() = _ui.update { it.copy(message = null, error = null) }

    fun clearTraktMessage() = _ui.update { it.copy(traktMessage = null, traktError = null) }

    fun clearMdbMessage() = _ui.update { it.copy(mdbMessage = null, mdbError = null) }

    fun clearAniMessage() = _ui.update { it.copy(aniMessage = null, aniError = null) }

    fun clearTvdbMessage() = _ui.update { it.copy(tvdbMessage = null, tvdbError = null) }

    /** Grava o client ID digitado (só o usuário cola; nunca há valor embutido). */
    fun saveClientId(id: String) {
        prefs.simklClientId = id.trim()
        _ui.update {
            it.copy(
                clientId = prefs.simklClientId,
                message = if (prefs.simklClientId.isBlank()) "Client ID apagado — Simkl desligado neste perfil."
                else "Client ID salvo. Toque em Conectar para entrar com o PIN.",
                error = null,
            )
        }
        refreshStatus()
    }

    private fun refreshStatus() = _ui.update {
        it.copy(connectedUser = simkl.user(), expiresAt = simkl.expiresAt())
    }

    /**
     * Abre o diálogo do PIN. Sem client ID, mostra a instrução de cadastro
     * em vez de chamar o Simkl (sem ID nada é chamado). Com client ID,
     * chama o device flow V2: Started mostra o diálogo e aguarda a
     * confirmação; Failed exibe o erro real vindo do serviço, sem reescrever.
     */
    fun startConnect() {
        if (prefs.simklClientId.isBlank()) {
            _ui.update {
                it.copy(
                    error = "Falta o Client ID do Simkl. Crie o app em ${SimklService.DEVELOPER_URL} " +
                        "(tipo \"TV, devices & command line\") e cole a API Key no campo acima.",
                )
            }
            return
        }
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null, message = null) }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            when (val started = simkl.startLogin()) {
                is SimklService.LoginResult.Failed -> {
                    _ui.update { it.copy(busy = false, error = started.message) }
                }
                is SimklService.LoginResult.Started -> {
                    val code = started.code
                    _ui.update { it.copy(pin = code) }
                    when (val outcome = simkl.waitLogin(code)) {
                        is SimklService.LoginOutcome.Connected -> {
                            _ui.update { it.copy(pin = null, busy = false) }
                            refreshStatus()
                            _ui.update {
                                it.copy(message = if (outcome.name.isBlank()) "Simkl conectado neste perfil." else "Simkl conectado como ${outcome.name}.")
                            }
                        }
                        SimklService.LoginOutcome.Expired -> {
                            _ui.update {
                                it.copy(pin = null, busy = false, error = "O código expirou. Toque em Conectar para gerar um novo.")
                            }
                        }
                        SimklService.LoginOutcome.Denied -> {
                            _ui.update {
                                it.copy(pin = null, busy = false, error = "Você negou a conexão no Simkl. Toque em Conectar para tentar de novo.")
                            }
                        }
                        is SimklService.LoginOutcome.Failed -> {
                            _ui.update { it.copy(pin = null, busy = false, error = outcome.message) }
                        }
                    }
                }
            }
        }
    }

    /** Cancela a espera do PIN (fecha o diálogo e para o polling). */
    fun cancelConnect() {
        pollJob?.cancel()
        pollJob = null
        _ui.update { it.copy(pin = null, busy = false) }
    }

    /** Desconecta o Simkl deste perfil (apaga só a sessão; o client ID fica). */
    fun disconnect() {
        simkl.logout()
        refreshStatus()
        _ui.update { it.copy(message = "Simkl desconectado neste perfil. O Client ID continua salvo.") }
    }

    // ── Trakt (v1.17.0, espelho do Simkl) ───────────────────────────────────

    /** Grava o Client ID e o Client Secret digitados (só o usuário cola; nunca há valor embutido). */
    fun saveTraktCredentials(id: String, secret: String) {
        trakt.setClientId(id.trim())
        trakt.setClientSecret(secret.trim())
        _ui.update {
            it.copy(
                traktClientId = trakt.clientId,
                traktClientSecret = trakt.clientSecret,
                traktMessage = if (trakt.clientId.isBlank()) "Client ID apagado — Trakt desligado neste perfil."
                else "Dados do Trakt salvos. Toque em Conectar para entrar com o código.",
                traktError = null,
            )
        }
        refreshTraktStatus()
    }

    /** Liga/desliga o Trakt neste perfil (desligado, nada é marcado nem sincronizado). */
    fun setTraktEnabled(on: Boolean) {
        trakt.setEnabled(on)
        _ui.update { it.copy(traktEnabled = trakt.isEnabled) }
    }

    private fun refreshTraktStatus() = _ui.update {
        it.copy(traktUser = trakt.user(), traktEnabled = trakt.isEnabled)
    }

    /**
     * Abre o diálogo do código do Trakt. Sem client ID, mostra a instrução
     * de cadastro em vez de chamar o Trakt (sem ID nada é chamado). Com
     * client ID, pede o código ([TraktService.startLogin]) e aguarda a
     * confirmação ([TraktService.waitLogin]): o nome devolvido é a conta
     * conectada; null é código expirado ou conexão não confirmada — os
     * dois casos caem na mesma mensagem, sem inventar motivo.
     */
    fun startTraktConnect() {
        if (trakt.clientId.isBlank()) {
            _ui.update {
                it.copy(
                    traktError = "Falta o Client ID do Trakt. Crie o app em https://trakt.tv/oauth/applications " +
                        "e cole o Client ID no campo acima.",
                )
            }
            return
        }
        if (_ui.value.traktBusy) return
        _ui.update { it.copy(traktBusy = true, traktError = null, traktMessage = null) }
        traktPollJob?.cancel()
        traktPollJob = viewModelScope.launch {
            val code = trakt.startLogin()
            if (code == null) {
                _ui.update {
                    it.copy(
                        traktBusy = false,
                        traktError = "Não foi possível pedir o código ao Trakt. Confira a internet e o Client ID e tente de novo.",
                    )
                }
                return@launch
            }
            _ui.update { it.copy(traktPin = code) }
            val name = trakt.waitLogin(code)
            if (name != null) {
                _ui.update { it.copy(traktPin = null, traktBusy = false) }
                refreshTraktStatus()
                _ui.update {
                    it.copy(traktMessage = if (name.isBlank()) "Trakt conectado neste perfil." else "Trakt conectado como $name.")
                }
            } else {
                _ui.update {
                    it.copy(
                        traktPin = null,
                        traktBusy = false,
                        traktError = "O código expirou ou a conexão não foi confirmada. Toque em Conectar para gerar um novo.",
                    )
                }
            }
        }
    }

    /** Cancela a espera do código (fecha o diálogo e para o polling). */
    fun cancelTraktConnect() {
        traktPollJob?.cancel()
        traktPollJob = null
        _ui.update { it.copy(traktPin = null, traktBusy = false) }
    }

    /** Desconecta o Trakt deste perfil (apaga só a sessão; o Client ID fica). */
    fun disconnectTrakt() {
        trakt.logout()
        refreshTraktStatus()
        _ui.update { it.copy(traktMessage = "Trakt desconectado neste perfil. O Client ID continua salvo.") }
    }

    // ── MDBList (v1.17.0) ───────────────────────────────────────────────────
    // Só consome notas (read-only): a chave do usuário libera as listas
    // dinâmicas e as notas por fonte; nada é enviado de volta ao MDBList.

    /** Grava a chave de API digitada (só o usuário cola; nunca há valor embutido). */
    fun saveMdbApiKey(key: String) {
        mdbList.setApiKey(key.trim())
        _ui.update {
            it.copy(
                mdbApiKey = mdbList.apiKey,
                mdbEnabled = mdbList.isEnabled,
                mdbMessage = if (mdbList.apiKey.isBlank()) "Chave apagada — MDBList desligado neste perfil."
                else "Chave do MDBList salva.",
                mdbError = null,
            )
        }
    }

    /** Liga/desliga o MDBList neste perfil (desligado, as notas por fonte somem dos detalhes). */
    fun setMdbEnabled(on: Boolean) {
        mdbList.setEnabled(on)
        _ui.update { it.copy(mdbEnabled = mdbList.isEnabled) }
    }

    /** Liga/desliga uma fonte de nota (IMDb, TMDB, Trakt, Letterboxd, Rotten Tomatoes, Metacritic). */
    fun toggleMdbSource(source: String, enabled: Boolean) {
        val current = mdbList.getEnabledSources().toMutableSet()
        if (enabled) current.add(source) else current.remove(source)
        mdbList.setEnabledSources(current)
        _ui.update { it.copy(mdbSources = mdbList.getEnabledSources()) }
    }

    // ── AniList (v1.17.0) ───────────────────────────────────────────────────
    // Tracking de anime por OAuth: o botão "Autorizar" abre a página do
    // AniList no navegador e o retorno volta pelo deep link
    // aotv://anilist/callback (ver AndroidManifest.xml); o token recebido
    // é guardado pelo serviço ([AniListService.saveToken]).

    /** Grava o Client ID digitado (só o usuário cola; nunca há valor embutido). */
    fun saveAniClientId(id: String) {
        aniList.setClientId(id.trim())
        _ui.update {
            it.copy(
                aniClientId = aniList.clientId,
                aniConnected = aniList.isConnected,
                aniEnabled = aniList.isEnabled,
                aniOAuthUrl = runCatching { aniList.buildOAuthUrl() }.getOrNull().orEmpty(),
                aniMessage = if (aniList.clientId.isBlank()) "Client ID apagado — AniList desligado neste perfil."
                else "Client ID do AniList salvo. Toque em Autorizar para conectar.",
                aniError = null,
            )
        }
    }

    /** Liga/desliga o AniList neste perfil (desligado, nada é marcado). */
    fun setAniEnabled(on: Boolean) {
        aniList.setEnabled(on)
        _ui.update { it.copy(aniEnabled = aniList.isEnabled, aniConnected = aniList.isConnected) }
    }

    /** Guarda o token que voltou do deep link e marca como conectado. */
    fun onAniTokenReceived(token: String) {
        if (token.isBlank()) return
        aniList.saveToken(token.trim())
        _ui.update {
            it.copy(
                aniConnected = aniList.isConnected,
                aniEnabled = aniList.isEnabled,
                aniMessage = "AniList conectado neste perfil.",
                aniError = null,
            )
        }
    }

    /** Desconecta o AniList deste perfil (apaga só o token; o Client ID fica). */
    fun disconnectAni() {
        aniList.disconnect()
        _ui.update {
            it.copy(
                aniConnected = aniList.isConnected,
                aniEnabled = aniList.isEnabled,
                aniMessage = "AniList desconectado neste perfil. O Client ID continua salvo.",
            )
        }
    }

    // ── TheTVDB (v1.17.0) ───────────────────────────────────────────────────
    // Ordenação de episódios (oficial, DVD ou absoluta) e correspondência
    // com o TMDB p/ o tracking; a chave de API é do usuário, nunca embutida.

    /** Grava a chave de API digitada (só o usuário cola; nunca há valor embutido). */
    fun saveTvdbApiKey(key: String) {
        tvdb.setApiKey(key.trim())
        _ui.update {
            it.copy(
                tvdbApiKey = tvdb.apiKey,
                tvdbEnabled = tvdb.isEnabled,
                tvdbMessage = if (tvdb.apiKey.isBlank()) "Chave apagada — TheTVDB desligado neste perfil."
                else "Chave do TheTVDB salva.",
                tvdbError = null,
            )
        }
    }

    /** Liga/desliga o TheTVDB neste perfil (desligado, a ordenação volta à oficial do TMDB). */
    fun setTvdbEnabled(on: Boolean) {
        tvdb.setEnabled(on)
        _ui.update { it.copy(tvdbEnabled = tvdb.isEnabled) }
    }

    /** Troca a ordenação de episódios ("official", "dvd" ou "absolute"). */
    fun setTvdbOrdering(ordering: String) {
        tvdb.setOrdering(ordering)
        _ui.update { it.copy(tvdbOrdering = tvdb.getOrdering()) }
    }

    // ── Avaliação pós-filme (v1.17.0) ───────────────────────────────────────

    /** Liga/desliga o pedido de nota ao fim do título (por perfil; ligado por padrão). */
    fun setPostPlayRatingEnabled(on: Boolean) {
        prefs.postPlayRatingEnabled = on
        _ui.update { it.copy(postPlayRatingEnabled = prefs.postPlayRatingEnabled) }
    }
}
