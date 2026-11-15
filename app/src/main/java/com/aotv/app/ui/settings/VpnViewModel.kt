package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.vpn.VPN_BLOCKED_REASON
import com.aotv.app.data.vpn.VpnConnectionState
import com.aotv.app.data.vpn.VpnParseResult
import com.aotv.app.data.vpn.VpnProfile
import com.aotv.app.data.vpn.VpnProfileStore
import com.aotv.app.data.vpn.VpnScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado da tela "VPN" (Lote 10, v1.20.0). */
data class VpnUiState(
    val profiles: List<VpnProfile> = emptyList(),
    val activeProfileId: String = "",
    val autoConnect: Boolean = false,
    /** Perfil aberto no diálogo de edição (null = sem diálogo). */
    val editingProfile: VpnProfile? = null,
    /** Perfil aguardando confirmação de remoção (null = sem diálogo). */
    val confirmRemove: VpnProfile? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class VpnViewModel @Inject constructor(
    private val store: VpnProfileStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        VpnUiState(
            profiles = store.profilesFlow.value,
            activeProfileId = store.activeProfileIdFlow.value,
            autoConnect = store.autoConnectFlow.value,
        ),
    )
    val ui: StateFlow<VpnUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.profilesFlow.collect { list ->
                _ui.update { it.copy(profiles = list, activeProfileId = store.activeProfileIdFlow.value) }
            }
        }
        viewModelScope.launch {
            store.activeProfileIdFlow.collect { id ->
                _ui.update { it.copy(activeProfileId = id) }
            }
        }
        viewModelScope.launch {
            store.autoConnectFlow.collect { on ->
                _ui.update { it.copy(autoConnect = on) }
            }
        }
    }

    /** Estado da conexão — sempre DESCONECTADO (sem motor nativo, ver VpnModels.kt). */
    val connectionState: VpnConnectionState get() = store.connectionState

    /** O perfil tem chave/senha guardada (só p/ mostrar "chave configurada" — o valor nunca sai do store). */
    fun hasSecrets(id: String): Boolean = store.hasSecrets(id)

    /** Importa o conteúdo de um arquivo .conf/.ovpn já lido por SAF. */
    fun importConfig(text: String, fileName: String) {        when (val result = store.importConfig(text, fileName)) {
            is VpnParseResult.Ok -> _ui.update {
                it.copy(
                    message = "Perfil \"${result.profile.name}\" importado. Conexão indisponível nesta versão.",
                    error = null,
                )
            }
            is VpnParseResult.Error -> _ui.update { it.copy(error = result.message, message = null) }
        }
    }

    /** O arquivo escolhido não pôde ser lido (SAF) — erro real, sem inventar motivo. */
    fun importReadError() {
        _ui.update { it.copy(error = "Não foi possível ler este arquivo.", message = null) }
    }

    /** O arquivo escolhido passou do limite de 256 KB. */
    fun importTooLarge() {
        _ui.update { it.copy(error = "Arquivo grande demais (limite de 256 KB).", message = null) }
    }

    /** Ativa um perfil (o túnel só sobe quando o motor existir). */
    fun setActiveProfile(id: String) {
        store.setActiveProfile(id)
        _ui.update { it.copy(message = null, error = null) }
    }

    /** Liga/desliga o conectar automático do perfil ativo (só vale com o motor de VPN). */
    fun setAutoConnect(on: Boolean) {
        store.setAutoConnect(on)
        _ui.update {
            it.copy(
                message = if (on) "Ligar automático ativado — só vale quando a conexão existir." else null,
                error = null,
            )
        }
    }

    /** Troca o escopo de um perfil ("app" = só o AOTV, "device" = todo o aparelho). */
    fun setScope(id: String, scope: VpnScope) {
        store.setScope(id, scope)
    }

    /** Abre o diálogo de edição de um perfil. */
    fun startEdit(profile: VpnProfile) {
        _ui.update { it.copy(editingProfile = profile) }
    }

    /** Fecha o diálogo de edição sem gravar. */
    fun cancelEdit() {
        _ui.update { it.copy(editingProfile = null) }
    }

    /** Grava nome, escopo e (se o perfil pede) usuário/senha do diálogo de edição. */
    fun saveEdit(id: String, name: String, scope: VpnScope, username: String, password: String) {
        store.renameProfile(id, name)
        store.setScope(id, scope)
        val profile = store.profilesFlow.value.firstOrNull { it.id == id }
        if (profile?.needsCredentials == true) store.updateCredentials(id, username, password)
        _ui.update { it.copy(editingProfile = null, message = "Perfil atualizado.", error = null) }
    }

    /** Pede confirmação p/ remover um perfil. */
    fun askRemove(profile: VpnProfile) {
        _ui.update { it.copy(confirmRemove = profile) }
    }

    /** Cancela a remoção. */
    fun cancelRemove() {
        _ui.update { it.copy(confirmRemove = null) }
    }

    /** Remove o perfil confirmado (e os segredos dele). */
    fun confirmRemove() {
        val profile = _ui.value.confirmRemove ?: return
        store.removeProfile(profile.id)
        _ui.update { it.copy(confirmRemove = null, message = "Perfil \"${profile.name}\" removido.", error = null) }
    }

    /**
     * Tentativa de conexão — BLOQUEADA (sem motor nativo). Não abre túnel
     * nenhum: mostra o motivo real. Quando a frente do motor existir, é o
     * [VpnProfileStore.connectAttempt] que vira a conexão de verdade.
     */
    fun connectAttempt() {
        _ui.update { it.copy(error = VPN_BLOCKED_REASON, message = null) }
    }

    /** Limpa a mensagem/erro da tela. */
    fun clearMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }
}
