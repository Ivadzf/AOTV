package com.aotv.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.vpn.VpnConnectOutcome
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

/** Estado da tela "VPN" (Lote 10, v1.20.0; conexão no Lote VPN, v1.40.0). */
data class VpnUiState(
    val profiles: List<VpnProfile> = emptyList(),
    val activeProfileId: String = "",
    val autoConnect: Boolean = false,
    /** Estado da conexão (espelha o [VpnProfileStore.connectionStateFlow]). */
    val connectionState: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    /** Motivo legível da última falha (null = sem falha recente). */
    val lastError: String? = null,
    /** Servidor do perfil conectado ("" = sem túnel). */
    val connectedEndpoint: String = "",
    /** Intent de consentimento do sistema aguardando a UI lançar (null = nenhum). */
    val pendingConsent: Intent? = null,
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
        viewModelScope.launch {
            store.connectionStateFlow.collect { state ->
                _ui.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            store.lastErrorFlow.collect { error ->
                _ui.update { it.copy(lastError = error) }
            }
        }
        viewModelScope.launch {
            store.connectedEndpointFlow.collect { endpoint ->
                _ui.update { it.copy(connectedEndpoint = endpoint) }
            }
        }
        viewModelScope.launch {
            store.pendingConsentFlow.collect { intent ->
                _ui.update { it.copy(pendingConsent = intent) }
            }
        }
    }

    /** Estado da conexão — espelha o túnel (nunca um "conectado" inventado). */
    val connectionState: VpnConnectionState get() = store.connectionState

    /** O perfil tem chave/senha guardada (só p/ mostrar "chave configurada" — o valor nunca sai do store). */
    fun hasSecrets(id: String): Boolean = store.hasSecrets(id)

    /** Importa o conteúdo de um arquivo .conf/.ovpn já lido por SAF. */
    fun importConfig(text: String, fileName: String) {        when (val result = store.importConfig(text, fileName)) {
            is VpnParseResult.Ok -> _ui.update {
                it.copy(
                    message = "Perfil \"${result.profile.name}\" importado.",
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

    /** Ativa um perfil (o túnel sobe no Conectar — trocar o ativo não conecta sozinho). */
    fun setActiveProfile(id: String) {
        store.setActiveProfile(id)
        _ui.update { it.copy(message = null, error = null) }
    }

    /** Liga/desliga o conectar automático do perfil ativo ao abrir o app. */
    fun setAutoConnect(on: Boolean) {
        store.setAutoConnect(on)
        _ui.update {
            it.copy(
                message = if (on) "Ligar automático ativado — conecta o perfil ativo ao abrir o app." else null,
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
     * Tentativa de conexão (Lote VPN, v1.40.0) — despacha o túnel do perfil
     * ativo. Se o sistema ainda não autorizou a VPN, o [VpnUiState.pendingConsent]
     * carrega o Intent e a tela lança a activity de consentimento; o
     * [onConsentResult] tenta de novo depois da autorização.
     */
    fun connectAttempt() {
        when (val outcome = store.connectAttempt()) {
            is VpnConnectOutcome.Started -> _ui.update {
                it.copy(message = "Conectando…", error = null)
            }
            is VpnConnectOutcome.NeedsConsent -> _ui.update {
                it.copy(message = "Autorize a VPN na tela do sistema para conectar.", error = null)
            }
            is VpnConnectOutcome.Failed -> _ui.update {
                it.copy(error = outcome.message, message = null)
            }
            is VpnConnectOutcome.NoProfile -> _ui.update {
                it.copy(error = "Escolha um perfil acima para conectar.", message = null)
            }
            is VpnConnectOutcome.NeedsCredentials -> _ui.update {
                it.copy(error = "Este perfil precisa de usuário e senha — edite o perfil e preencha.", message = null)
            }
        }
    }

    /** Desconecta o túnel. */
    fun disconnect() {
        store.disconnect()
        _ui.update { it.copy(message = "Desconectando…", error = null) }
    }

    /**
     * Resultado da activity de consentimento do sistema. Autorizado =
     * tenta conectar de novo (o `VpnService.prepare()` agora devolve null);
     * negado = mostra o motivo sem tentar nada.
     */
    fun onConsentResult(granted: Boolean) {
        if (granted) {
            connectAttempt()
        } else {
            _ui.update {
                it.copy(error = "Permissão de VPN negada — sem ela o túnel não sobe.", message = null)
            }
        }
    }

    /** Limpa o Intent de consentimento pendente (a tela já lançou). */
    fun clearPendingConsent() {
        store.clearPendingConsent()
    }

    /** Abre as configurações de VPN do sistema (always-on + "Bloquear conexões sem VPN"). */
    fun openSystemVpnSettings(context: android.content.Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Limpa a mensagem/erro da tela. */
    fun clearMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }
}
