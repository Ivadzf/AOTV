package com.aotv.app.data.debrid

/** Serviços de debrid aceitos. [device] diz se dá para entrar com código, sem colar chave. */
enum class DebridProvider(
    val id: String,
    val label: String,
    val short: String,
    val keyPage: String,
    val device: Boolean,
) {
    REAL_DEBRID("realdebrid", "Real-Debrid", "RD", "https://real-debrid.com/apitoken", true),
    TORBOX("torbox", "TorBox", "TB", "https://torbox.app/settings", true),
    PREMIUMIZE("premiumize", "Premiumize", "PM", "https://www.premiumize.me/account", false);

    companion object {
        fun of(id: String?): DebridProvider? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Conta conectada. [refresh], [clientId] e [clientSecret] só existem no login por código do
 * Real-Debrid, que dá um token de algumas horas. Chave colada não expira ([expiresAt] 0).
 */
data class DebridAccount(
    val provider: DebridProvider,
    val token: String,
    val user: String = "",
    /** Fim do premium em epoch ms. 0 quando o serviço não informou. */
    val premiumUntil: Long = 0L,
    val refresh: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val expiresAt: Long = 0L,
)

/** Código para entrar pelo site do serviço. */
data class DeviceCode(
    val provider: DebridProvider,
    val deviceCode: String,
    val userCode: String,
    /** Endereço que o QR e o botão abrem (já leva o código quando o serviço deixa). */
    val verifyUrl: String,
    val intervalSec: Int,
    val expiresAt: Long,
    /** Página curta para digitar o código à mão. */
    val manualUrl: String = verifyUrl,
)

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data class Done(val account: DebridAccount) : DevicePoll
    data object Expired : DevicePoll
    data class Failed(val message: String) : DevicePoll
}

/** Resultado de transformar um torrent em link direto. */
sealed interface DebridResolve {
    data class Ok(val url: String, val provider: DebridProvider) : DebridResolve
    /** O serviço não tem o arquivo pronto. Quem chama toca por P2P ou passa para outra fonte. */
    data object NotCached : DebridResolve
    data object NoAccount : DebridResolve
    data class Failed(val message: String) : DebridResolve
}

/** Ajustes do debrid (do aparelho, valem para todos os perfis). */
data class DebridSettings(
    /** Usa o debrid nas fontes de torrent. */
    val enabled: Boolean = true,
    /** Serviço usado quando há mais de um conectado. Vazio = o primeiro. */
    val preferred: String = "",
    /** Esconde o torrent que o serviço disse que não tem pronto (sem isso ele toca por P2P). */
    val hideUncached: Boolean = false,
)
