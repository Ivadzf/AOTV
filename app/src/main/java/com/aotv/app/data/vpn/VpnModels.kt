package com.aotv.app.data.vpn

import android.content.Intent
import org.json.JSONObject

/**
 * VPN (Lote 10, v1.20.0; conexão no Lote VPN, v1.40.0) — modelos.
 *
 * O AOTV só GERENCIA perfis trazidos pelo usuário (arquivo .conf do
 * WireGuard ou .ovpn do OpenVPN, importado por SAF). Nada de servidor
 * próprio, nada embutido: o perfil é do provedor do usuário.
 *
 * ── CONEXÃO (Lote VPN, v1.40.0) ──────────────────────────────────────────
 * O túnel existe de verdade, com os motores das dependências do gradle:
 * WireGuard `com.wireguard.android:tunnel:1.0.20260102` (Maven Central,
 * AAR com as .so embutidas, sem NDK no CI) e OpenVPN
 * `io.github.tim06:openvpn:1.1.3` (Maven Central, fork Apache-2.0 do
 * ics-openvpn com o motor OpenVPN3). Os dois motores trazem o PRÓPRIO
 * `VpnService` declarado no manifesto do AAR (merge automático) — o
 * WireGuard usa `GoBackend$VpnService` e o OpenVPN usa
 * `com.tim.openvpn.service.OpenVPNService` (processo `:openvpn`); o app
 * NÃO declara um terceiro `VpnService`, que ficaria sem túnel e poderia
 * conflitar com os dois. O que o app faz é orquestrar (ver
 * [VpnConnectionManager]): `VpnService.prepare()` p/ o consentimento do
 * usuário, `GoBackend.setState(UP/DOWN)` p/ WireGuard e
 * `OpenVPNService.Companion.startService/stopService` p/ OpenVPN, com o
 * estado espelhado em [VpnProfileStore].
 * Escopos "só HTTP" e "HTTP+HTTPS" da fork PlusIPTV NÃO existem no
 * Android: um `VpnService` tunela o aparelho inteiro ou só os apps da
 * allowlist (`addAllowedApplication`) — não dá p/ filtrar por
 * protocolo. Por isso o escopo aqui é só "app" (só o AOTV) ou "device"
 * (todo o aparelho).
 *
 * ── ONDE FICA CADA COISA (contrato pré-existente, ver SCOPED_KEYS) ────
 * [VpnProfile] guarda metadados + segredos juntos no JSON de
 * `vpn_profiles_v1` (nome, tipo, escopo, servidor + chave privada/senha/
 * certificados em "secrets" + o texto bruto do config em "rawConfig").
 * A chave inteira está em
 * [com.aotv.app.data.backup.AotvBackup.SECRET_SCOPED_KEYS]: só entra no
 * backup/sync com o toggle de segredos ligado (mesma regra do Trakt/
 * Simkl). O escopo ("app"/"device") vive DENTRO de cada perfil — não há
 * pref `vpn_scope` separada. `vpn_active_profile_v1` é o id do perfil
 * ativo e `vpn_auto_connect_v1` é o ligar automático (vale desde a
 * v1.40.0 — ver [VpnAutoConnect]). A UI nunca mostra o valor de um
 * segredo, só se ele existe.
 */

/** Tipo do perfil de VPN (vem do arquivo importado). */
enum class VpnType { WIREGUARD, OPENVPN }

/**
 * Escopo do túnel quando ele existir. APP = só o tráfego do AOTV passa
 * pela VPN (allowlist do `VpnService`); DEVICE = todo o aparelho.
 * Os escopos "só HTTP"/"HTTP+HTTPS" da fork não existem no Android.
 */
enum class VpnScope { APP, DEVICE }

/**
 * Estado da conexão (Lote VPN, v1.40.0). FAILED guarda o motivo legível
 * em [VpnProfileStore.lastErrorFlow] — nunca um "conectado" inventado.
 */
enum class VpnConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, FAILED }

/**
 * Resultado de uma tentativa de conexão ([VpnProfileStore.connectAttempt]).
 * [NeedsConsent] carrega o Intent do `VpnService.prepare()` — a UI lança
 * a activity de consentimento do sistema e só conecta depois da
 * autorização (ver [VpnConnectionManager]).
 */
sealed class VpnConnectOutcome {
    /** O sistema ainda não autorizou a VPN — lançar [intent] e tentar de novo. */
    data class NeedsConsent(val intent: Intent) : VpnConnectOutcome()

    /** Tentativa despachada (o estado vai p/ CONNECTING e depois CONNECTED/FAILED). */
    data object Started : VpnConnectOutcome()

    /** Não deu nem p/ tentar — [message] é o que a tela mostra. */
    data class Failed(val message: String) : VpnConnectOutcome()

    /** Nenhum perfil ativo p/ conectar. */
    data object NoProfile : VpnConnectOutcome()

    /** O perfil pede usuário/senha do OpenVPN e eles não estão guardados. */
    data object NeedsCredentials : VpnConnectOutcome()
}

/** Coordenada Maven do motor WireGuard. */
const val VPN_WIREGUARD_MAVEN = "com.wireguard.android:tunnel:1.0.20260102"

/** Coordenada Maven do motor OpenVPN. */
const val VPN_OPENVPN_MAVEN = "io.github.tim06:openvpn:1.1.3"

/** Tamanho máximo de um arquivo de perfil importado (256 KB — .conf/.ovpn são texto curto). */
const val VPN_MAX_CONFIG_BYTES = 256 * 1024

/**
 * Perfil de VPN — metadados + segredos no mesmo JSON (`vpn_profiles_v1`,
 * chave secreta no backup/sync). [endpoint] é o servidor como aparece no
 * arquivo ("host:porta" ou "host:porta (udp)"). [needsCredentials] é true
 * quando o .ovpn pede usuário e senha (`auth-user-pass`) e o arquivo não
 * os traz — aí a tela pede p/ digitar. [secrets] guarda chave privada,
 * senha e certificados (nunca vai p/ log nem p/ tela). [rawConfig] é o
 * texto bruto do arquivo importado (Lote VPN, v1.40.0): o túnel precisa
 * do config completo — o WireGuard precisa de Address/DNS/AllowedIPs/
 * Endpoint/PublicKey/PresharedKey/PersistentKeepalive e o OpenVPN do
 * .ovpn inteiro + certs inline — e o parser do Lote 10 não guardava
 * esses campos. Fica DENTRO da mesma chave secreta (`vpn_profiles_v1`),
 * então não muda nenhuma regra de backup/sync; perfis importados antes
 * da v1.40.0 têm [rawConfig] vazio e precisam ser importados de novo
 * p/ conectar. [createdAt] é ms desde epoch.
 */
data class VpnProfile(
    val id: String,
    val name: String,
    val type: VpnType,
    val scope: VpnScope = VpnScope.APP,
    val endpoint: String = "",
    val needsCredentials: Boolean = false,
    val secrets: VpnSecrets = VpnSecrets(),
    val rawConfig: String = "",
    val createdAt: Long = 0L,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("type", type.name)
            .put("scope", scope.name)
            .put("endpoint", endpoint)
            .put("needsCredentials", needsCredentials)
            .put("createdAt", createdAt)
        if (secrets.hasAny) o.put("secrets", secrets.toJson())
        if (rawConfig.isNotBlank()) o.put("rawConfig", rawConfig)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject?): VpnProfile? {
            if (o == null) return null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
            val type = runCatching { VpnType.valueOf(o.optString("type")) }.getOrDefault(VpnType.WIREGUARD)
            val scope = runCatching { VpnScope.valueOf(o.optString("scope")) }.getOrDefault(VpnScope.APP)
            return VpnProfile(
                id = id,
                name = name,
                type = type,
                scope = scope,
                endpoint = o.optString("endpoint", ""),
                needsCredentials = o.optBoolean("needsCredentials", false),
                secrets = VpnSecrets.fromJson(o.optJSONObject("secrets")),
                rawConfig = o.optString("rawConfig", ""),
                createdAt = o.optLong("createdAt", 0L),
            )
        }
    }
}

/**
 * Segredos de um perfil de VPN. Tudo opcional e vazio por padrão; só os
 * campos não vazios são persistidos (ver [VpnProfileStore]). NUNCA vai p/
 * log, p/ tela ou p/ backup sem o toggle de segredos.
 */
data class VpnSecrets(
    /** Chave privada do WireGuard ([Interface] PrivateKey). */
    val privateKey: String = "",
    /** Chave pré-compartilhada do WireGuard ([Peer] PresharedKey). */
    val presharedKey: String = "",
    /** Usuário do OpenVPN (`auth-user-pass`). */
    val username: String = "",
    /** Senha do OpenVPN (`auth-user-pass`). */
    val password: String = "",
    /** Certificado da CA embutido no .ovpn (`<ca>`). */
    val caCert: String = "",
    /** Certificado do cliente embutido no .ovpn (`<cert>`). */
    val cert: String = "",
    /** Chave do cliente embutida no .ovpn (`<key>`). */
    val key: String = "",
) {
    /** Algum segredo preenchido (p/ a UI mostrar "chave configurada" sem mostrar o valor). */
    val hasAny: Boolean get() = privateKey.isNotBlank() || presharedKey.isNotBlank() ||
        username.isNotBlank() || password.isNotBlank() ||
        caCert.isNotBlank() || cert.isNotBlank() || key.isNotBlank()

    fun toJson(): JSONObject {
        val o = JSONObject()
        if (privateKey.isNotBlank()) o.put("privateKey", privateKey)
        if (presharedKey.isNotBlank()) o.put("presharedKey", presharedKey)
        if (username.isNotBlank()) o.put("username", username)
        if (password.isNotBlank()) o.put("password", password)
        if (caCert.isNotBlank()) o.put("caCert", caCert)
        if (cert.isNotBlank()) o.put("cert", cert)
        if (key.isNotBlank()) o.put("key", key)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject?): VpnSecrets {
            if (o == null) return VpnSecrets()
            return VpnSecrets(
                privateKey = o.optString("privateKey", ""),
                presharedKey = o.optString("presharedKey", ""),
                username = o.optString("username", ""),
                password = o.optString("password", ""),
                caCert = o.optString("caCert", ""),
                cert = o.optString("cert", ""),
                key = o.optString("key", ""),
            )
        }
    }
}
