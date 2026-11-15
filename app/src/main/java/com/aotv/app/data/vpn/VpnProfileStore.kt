package com.aotv.app.data.vpn

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perfis de VPN (Lote 10, v1.20.0) — CRUD sobre as prefs do perfil.
 *
 * Contrato de chaves (pré-existente em SCOPED_KEYS / AotvBackup, esta
 * frente é dona de quem ESCREVE):
 * - `vpn_profiles_v1`: JSON array de [VpnProfile] (metadados + segredos
 *   embutidos em "secrets"). Chave SECRETA (ver
 *   [com.aotv.app.data.backup.AotvBackup.SECRET_SCOPED_KEYS]): só viaja no
 *   backup/sync com o toggle de segredos ligado.
 * - `vpn_active_profile_v1`: id do perfil ativo ("" = nenhum).
 * - `vpn_auto_connect_v1`: ligar o perfil ativo ao abrir o app (só vale
 *   quando a conexão existir — hoje não faz nada sozinho).
 * O escopo ("app" = só o AOTV, "device" = todo o aparelho) vive DENTRO de
 * cada perfil; não há pref `vpn_scope` separada.
 *
 * Conexão: NÃO há motor nativo nesta versão (ver [VPN_BLOCKED_REASON] em
 * VpnModels.kt). [connectionState] é sempre DESCONECTADO e [connectAttempt]
 * não tenta nada — só devolve o motivo real p/ a tela mostrar. Nada aqui
 * finge que o túnel subiu.
 */
@Singleton
class VpnProfileStore @Inject constructor(
    private val prefs: PlatformPrefs,
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
    /** Ligar o perfil ativo ao abrir o app, que avisa quem observa. Hoje sem efeito (sem motor de VPN). */
    val autoConnectFlow: StateFlow<Boolean> = _autoConnect.asStateFlow()

    /** Perfil ativo, ou null sem perfil ativo (ou id órfão). */
    val activeProfile: VpnProfile? get() = _profiles.value.firstOrNull { it.id == _activeProfileId.value }

    /**
     * Estado da conexão. Sem motor nativo, é sempre DESCONECTADO — a tela
     * mostra [VPN_BLOCKED_REASON] junto, nunca um "conectado" inventado.
     */
    val connectionState: VpnConnectionState get() = VpnConnectionState.DISCONNECTED

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

    /** Liga/desliga o conectar automático do perfil ativo ao abrir o app (só vale com o motor de VPN). */
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
     * Tentativa de conexão — BLOQUEADA (sem motor nativo). Não abre túnel
     * nenhum: devolve o motivo real p/ a tela mostrar. Quando a frente do
     * motor existir, é aqui que o `VpnService` entra (ver VpnModels.kt).
     */
    fun connectAttempt(): String = VPN_BLOCKED_REASON

    private fun saveProfiles(list: List<VpnProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.vpnProfilesJson = arr.toString()
        _profiles.value = list
    }
}
