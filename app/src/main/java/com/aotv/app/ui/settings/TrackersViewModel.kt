package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.SimklService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado da tela "Trackers e Sincronização" (só Simkl na v1.13.1). */
data class TrackersUiState(
    val profileName: String = "",
    val clientId: String = "",
    val connectedUser: String? = null,
    val expiresAt: Long = 0L,
    val pin: SimklService.DeviceCode? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * ViewModel da tela de trackers (v1.13.1, Frente B — Simkl, device flow V2).
 *
 * O client ID vive na pref existente [PlatformPrefs.simklClientId] (por
 * perfil, vazio por padrão) e a sessão em [PlatformPrefs.simklSession].
 * O polling do PIN roda aqui; cancelar o diálogo cancela o job.
 */
@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val simkl: SimklService,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        TrackersUiState(
            profileName = prefs.profile.name,
            clientId = prefs.simklClientId,
            connectedUser = simkl.user(),
            expiresAt = simkl.expiresAt(),
        ),
    )
    val ui: StateFlow<TrackersUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null

    fun clearMessage() = _ui.update { it.copy(message = null, error = null) }

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
}
