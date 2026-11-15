package com.aotv.app.data.vpn

/**
 * Parser dos arquivos de perfil de VPN (Lote 10, v1.20.0).
 *
 * Kotlin puro, sem dependência Android — só lê texto. Aceita:
 * - WireGuard (.conf): seções `[Interface]` e `[Peer]`.
 * - OpenVPN (.ovpn): diretivas `remote`, `proto`, `auth-user-pass` e os
 *   blocos embutidos `<ca>`, `<cert>`, `<key>`.
 *
 * O parser valida o mínimo p/ o perfil ser usável depois (chave privada e
 * servidor presentes); o resto (DNS, AllowedIPs, cipher) é ignorado de
 * propósito — sem motor nativo, nada disso é aplicado ainda. Erro de
 * validação vira mensagem real p/ a tela, nunca perfil pela metade.
 * Nenhum segredo é logado: as mensagens de erro nunca incluem valores.
 */

/** Resultado da importação de um arquivo de perfil. */
sealed class VpnParseResult {
    /** Perfil válido (metadados + segredos separados, ver [VpnProfile]). */
    data class Ok(val profile: VpnProfile, val secrets: VpnSecrets) : VpnParseResult()

    /** Arquivo inválido — [message] é o que a tela mostra. */
    data class Error(val message: String) : VpnParseResult()
}

private val BASE64_CHARS = (('A'..'Z') + ('a'..'z') + ('0'..'9') + '+' + '/' + '=').toSet()

/** Chave do WireGuard: sempre 44 caracteres base64. */
private fun isWireGuardKey(value: String): Boolean =
    value.length == 44 && value.all { it in BASE64_CHARS }

/** Nome de exibição a partir do nome do arquivo (sem extensão, no máximo 40 caracteres). */
fun sanitizeProfileName(fileName: String): String {
    val base = fileName.substringBeforeLast('.').trim()
        .replace(Regex("[\\r\\n]"), " ").trim()
    return base.take(40).ifBlank { "Perfil VPN" }
}

/**
 * Importa o conteúdo de um arquivo de perfil. [fileName] é só p/ o nome
 * de exibição (o SAF nem sempre devolve extensão confiável, então o
 * formato é detectado pelo CONTEÚDO, não pela extensão).
 */
fun parseVpnConfig(text: String, fileName: String): VpnParseResult {
    if (text.toByteArray(Charsets.UTF_8).size > VPN_MAX_CONFIG_BYTES) {
        return VpnParseResult.Error("Arquivo grande demais (limite de 256 KB).")
    }
    val clean = text.trim()
    if (clean.isEmpty()) return VpnParseResult.Error("Arquivo vazio.")
    val lower = clean.lowercase()
    return when {
        "[interface]" in lower -> parseWireGuard(clean, fileName)
        lower.lines().any { it.trimStart().startsWith("remote ") || it.trim() == "client" } -> parseOpenVpn(clean, fileName)
        else -> VpnParseResult.Error(
            "Formato não reconhecido: esperado um .conf do WireGuard ou um .ovpn do OpenVPN.",
        )
    }
}

/** Lê o .conf do WireGuard ([Interface] + [Peer]). */
private fun parseWireGuard(text: String, fileName: String): VpnParseResult {
    var section = ""
    val iface = mutableMapOf<String, String>()
    val peer = mutableMapOf<String, String>()
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length - 1).trim().lowercase()
            continue
        }
        val eq = line.indexOf('=')
        if (eq < 0) continue
        val key = line.substring(0, eq).trim().lowercase()
        val value = line.substring(eq + 1).trim()
        when (section) {
            "interface" -> iface[key] = value
            "peer" -> peer[key] = value
        }
    }
    val privateKey = iface["privatekey"].orEmpty()
    if (privateKey.isBlank()) return VpnParseResult.Error("Falta a PrivateKey na seção [Interface].")
    if (!isWireGuardKey(privateKey)) {
        return VpnParseResult.Error("PrivateKey inválida (esperava 44 caracteres base64).")
    }
    val publicKey = peer["publickey"].orEmpty()
    if (publicKey.isBlank()) return VpnParseResult.Error("Falta a PublicKey na seção [Peer].")
    if (!isWireGuardKey(publicKey)) {
        return VpnParseResult.Error("PublicKey inválida (esperava 44 caracteres base64).")
    }
    val endpoint = peer["endpoint"].orEmpty()
    if (endpoint.isBlank()) return VpnParseResult.Error("Falta o Endpoint na seção [Peer].")
    val port = endpoint.substringAfterLast(':', "")
    val portNum = port.toIntOrNull()
    if (port.isEmpty() || portNum == null || portNum !in 1..65535) {
        return VpnParseResult.Error("Endpoint inválido (esperava host:porta).")
    }
    val profile = VpnProfile(
        id = "",
        name = sanitizeProfileName(fileName),
        type = VpnType.WIREGUARD,
        endpoint = endpoint,
        createdAt = System.currentTimeMillis(),
    )
    val secrets = VpnSecrets(
        privateKey = privateKey,
        presharedKey = peer["presharedkey"].orEmpty(),
    )
    return VpnParseResult.Ok(profile, secrets)
}

/** Lê o .ovpn do OpenVPN (diretivas + blocos embutidos). */
private fun parseOpenVpn(text: String, fileName: String): VpnParseResult {
    var remoteHost = ""
    var remotePort = "1194"
    var remoteProto = ""
    var proto = ""
    var needsCredentials = false
    val blocks = mutableMapOf<String, String>()
    var currentBlock: String? = null
    val blockLines = StringBuilder()
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.startsWith("<") && line.endsWith(">") && !line.startsWith("</")) {
            currentBlock = line.substring(1, line.length - 1).trim().lowercase()
            blockLines.clear()
            continue
        }
        if (line.startsWith("</") && line.endsWith(">")) {
            val name = currentBlock
            if (name != null) blocks[name] = blockLines.toString().trim()
            currentBlock = null
            continue
        }
        if (currentBlock != null) {
            blockLines.append(raw).append('\n')
            continue
        }
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
        val parts = line.split(Regex("\\s+"))
        when (parts[0].lowercase()) {
            "remote" -> {
                if (remoteHost.isEmpty() && parts.size >= 2) {
                    remoteHost = parts[1]
                    if (parts.size >= 3) remotePort = parts[2]
                    if (parts.size >= 4) remoteProto = parts[3]
                }
            }
            "proto" -> if (parts.size >= 2) proto = parts[1]
            "auth-user-pass" -> needsCredentials = true
        }
    }
    if (remoteHost.isBlank()) {
        return VpnParseResult.Error("Falta o servidor (diretiva 'remote') no arquivo .ovpn.")
    }
    val protoLabel = (remoteProto.ifBlank { proto }).lowercase()
    val endpoint = if (protoLabel.isBlank()) "$remoteHost:$remotePort" else "$remoteHost:$remotePort ($protoLabel)"
    val profile = VpnProfile(
        id = "",
        name = sanitizeProfileName(fileName),
        type = VpnType.OPENVPN,
        endpoint = endpoint,
        needsCredentials = needsCredentials,
        createdAt = System.currentTimeMillis(),
    )
    val secrets = VpnSecrets(
        caCert = blocks["ca"].orEmpty(),
        cert = blocks["cert"].orEmpty(),
        key = blocks["key"].orEmpty(),
    )
    return VpnParseResult.Ok(profile, secrets)
}
