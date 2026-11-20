package com.aotv.app.data.vpn

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perfis de VPN (Lote 10, v1.20.0; conexão no Lote VPN, v1.40.0) — CRUD
 * sobre as prefs do perfil + estado da conexão.
 *
 * Contrato de chaves (pré-existente em SCOPED_KEYS / AotvBackup, esta
 * frente é dona de quem ESCREVE):
 * - `vpn_profiles_v1`: JSON array de [VpnProfile] (metadados + segredos
 *   embutidos em "secrets" + texto bruto do config em "rawConfig").
 *   Chave SECRETA (ver
 *   [com.aotv.app.data.backup.AotvBackup.SECRET_SCOPED_KEYS]): só viaja no
 *   backup/sync com o toggle de segredos ligado.
 * - `vpn_active_profile_v1`: id do perfil ativo ("" = nenhum).
 * - `vpn_auto_connect_v1`: ligar o perfil ativo ao abrir o app (vale
 *   desde a v1.40.0 — ver [VpnAutoConnect]).
 * O escopo ("app" = só o AOTV, "device" = todo o aparelho) vive DENTRO de
 * cada perfil; não há pref `vpn_scope` separada.
 *
 * Conexão: o túnel é do [VpnConnectionManager] (WireGuard via GoBackend,
 * OpenVPN via OpenVPNService do tim06). [connectionStateFlow] espelha o
 * estado real — CONNECTING/CONNECTED/DISCONNECTING/FAILED — e
 * [connectAttempt] despacha a conexão (ou o consentimento do sistema).
 * Nada aqui finge que o túnel subiu.
 */
@Singleton
class VpnProfileStore @Inject constructor(
    private val prefs: PlatformPrefs,
    private val manager: VpnConnectionManager,
) {
    private fun readProfiles(): List<VpnProfile> = runCatching {
        val arr = JSONArray(prefs.vpnProfilesJson)
        (0 until arr.length()).mapNotNull { VpnProfile.fromJson(arr.optJSONObject(it)) }
    }.getOrDefault(emptyList())

    private val _profiles = MutableStateFlow(readProfiles())
    /** Perfis de VPN do perfil ativo, que avisam quem observa. */
    val profilesFlow: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow(prefs.vpnActiveProfileId)
    /** Id do perfil de VPN ativo ("" = nenhum), que avisa quem observa. */
    val activeProfileIdFlow: StateFlow<String> = _activeProfileId.asStateFlow()

    private val _autoConnect = MutableStateFlow(prefs.vpnAutoConnect)
    /** Ligar o perfil ativo ao abrir o app, que avisa quem observa. */
    val autoConnectFlow: StateFlow<Boolean> = _autoConnect.asStateFlow()

    /** Estado da conexão (do [VpnConnectionManager] — CONNECTING/CONNECTED/DISCONNECTING/FAILED). */
    val connectionStateFlow: StateFlow<VpnConnectionState> = manager.stateFlow

    /** Estado atual da conexão (atalho p/ o valor do [connectionStateFlow]). */
    val connectionState: VpnConnectionState get() = manager.stateFlow.value

    /** Motivo legível da última falha (null = sem falha recente). */
    val lastErrorFlow: StateFlow<String?> = manager.lastErrorFlow

    /** Servidor do perfil conectado ("" = sem túnel). */
    val connectedEndpointFlow: StateFlow<String> = manager.connectedEndpointFlow

    /** Intent de consentimento do sistema aguardando a UI (null = nenhum). */
    val pendingConsentFlow: StateFlow<android.content.Intent?> = manager.pendingConsentFlow

    /** Perfil ativo, ou null sem perfil ativo (ou id órfão). */
    val activeProfile: VpnProfile? get() = _profiles.value.firstOrNull { it.id == _activeProfileId.value }

    /** O perfil tem algum segredo guardado (só p/ mostrar "chave configurada" — o valor nunca sai daqui). */
    fun hasSecrets(id: String): Boolean =
        _profiles.value.firstOrNull { it.id == id }?.secrets?.hasAny == true

    /** Segredos de um perfil (só p/ a frente que ligar a conexão — a UI nunca chama isto). */
    fun secretsFor(id: String): VpnSecrets =
        _profiles.value.firstOrNull { it.id == id }?.secrets ?: VpnSecrets()

    /** Recarrega das prefs (troca de perfil). */
    fun refresh() {
        _profiles.value = readProfiles()
        _activeProfileId.value = prefs.vpnActiveProfileId
        _autoConnect.value = prefs.vpnAutoConnect
    }

    /** Importa um arquivo já lido. Erro de parse volta como [VpnParseResult.Error] sem gravar nada. */
    fun importConfig(text: String, fileName: String): VpnParseResult {
        val parsed = parseVpnConfig(text, fileName)
        if (parsed !is VpnParseResult.Ok) return parsed
        val id = "vpn" + java.lang.Long.toString(System.currentTimeMillis(), 36)
        val profile = parsed.profile.copy(id = id, secrets = parsed.secrets)
        saveProfiles(readProfiles() + profile)
        if (_activeProfileId.value.isBlank()) setActiveProfile(id)
        return VpnParseResult.Ok(profile, parsed.secrets)
    }

    /** Renomeia o perfil (nome vazio = sem mudança). */
    fun renameProfile(id: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isBlank()) return
        saveProfiles(readProfiles().map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** Troca o escopo de um perfil ("app" = só o AOTV, "device" = todo o aparelho). */
    fun setScope(id: String, scope: VpnScope) {
        saveProfiles(readProfiles().map { if (it.id == id) it.copy(scope = scope) else it })
    }

    /** Ativa um perfil (null = nenhum ativo). */
    fun setActiveProfile(id: String?) {
        val clean = id?.takeIf { target -> readProfiles().any { it.id == target } }.orEmpty()
        prefs.vpnActiveProfileId = clean
        _activeProfileId.value = clean
    }

    /** Liga/desliga o conectar automático do perfil ativo ao abrir o app (vale desde a v1.40.0). */
    fun setAutoConnect(on: Boolean) {
        prefs.vpnAutoConnect = on
        _autoConnect.value = on
    }

    /** Grava usuário/senha do OpenVPN (`auth-user-pass`). Senha vazia = só troca o usuário. */
    fun updateCredentials(id: String, username: String, password: String) {
        saveProfiles(readProfiles().map {
            if (it.id != id) return@map it
            val current = it.secrets
            it.copy(
                secrets = current.copy(
                    username = username.trim(),
                    password = if (password.isBlank()) current.password else password,
                ),
            )
        })
    }

    /** Remove o perfil; se era o ativo, desativa. */
    fun removeProfile(id: String) {
        saveProfiles(readProfiles().filterNot { it.id == id })
        if (_activeProfileId.value == id) setActiveProfile(null)
    }

    /**
     * Tentativa de conexão (Lote VPN, v1.40.0) — despacha o túnel do
     * perfil ativo pelo [VpnConnectionManager]. [VpnConnectOutcome.Started]
     * = tentativa rodando (o estado chega pelo [connectionStateFlow]);
     * [VpnConnectOutcome.NeedsConsent] = o sistema ainda não autorizou —
     * a UI lança o Intent e chama de novo. Nada aqui finge que o túnel
     * subiu: sem perfil, sem credenciais ou sem o arquivo completo, volta
     * o motivo real sem tentar nada.
     */
    fun connectAttempt(): VpnConnectOutcome {
        val profile = activeProfile ?: return VpnConnectOutcome.NoProfile
        return manager.connect(profile)
    }

    /** Desconecta o túnel (sem efeito se já desconectado). */
    fun disconnect() {
        manager.disconnect()
    }

    /** Limpa o Intent de consentimento pendente (a UI já lançou ou desistiu). */
    fun clearPendingConsent() {
        manager.clearPendingConsent()
    }

    /**
     * Ligar automático ao abrir o app: só tenta quando o toggle está
     * ligado E há perfil ativo E não há túnel. Se o sistema ainda não
     * autorizou, a tentativa fica pendente (consentimento na tela da VPN).
     */
    fun tryAutoConnect() {
        if (!prefs.vpnAutoConnect) return
        if (manager.stateFlow.value != VpnConnectionState.DISCONNECTED) return
        val profile = activeProfile ?: return
        manager.connect(profile)
    }

    private fun saveProfiles(list: List<VpnProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.vpnProfilesJson = arr.toString()
        _profiles.value = list
    }
}
