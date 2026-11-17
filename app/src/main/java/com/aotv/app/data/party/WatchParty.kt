package com.aotv.app.data.party

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Watch Party (Lote 10, v1.20.0; sync real no Lote 19, v1.29.0) — modelos,
 * código e deep link.
 *
 * Ideia portada do Tuvora (deep links `tuvora.co/party/` e
 * `tuvora://party/?code=`): uma sessão compartilhada por código, com
 * playback sincronizado (play/pause/seek). Reimplementada, sem copiar
 * código da fork (GPL-3.0).
 *
 * ── SINCRONIZAÇÃO (Lote 19) ─────────────────────────────────────────────
 * O play/pause/seek sincronizado agora existe, via WebRTC P2P (ver
 * [PartyRuntime]): o anfitrião é a verdade (posição + relógio com
 * offsets por ping), os convidados seguem com "catch up" a 1,5× ou seek.
 * A sinalização NÃO usa backend de terceiro: ou é a rede local (o
 * anfitrião liga um servidor HTTP efêmero no próprio aparelho — ver
 * [PartyLanServer]) ou é a troca manual de SDP (copiar e colar — ver
 * [ManualSdp]). O que continua sem existir: servidor de sinalização
 * hospedado, conta na nuvem e relay TURN embutido — o TURN é opcional e
 * configurado pelo usuário (ver [PARTY_TURN_*] em PlatformPrefs). Sem
 * TURN e com o P2P falhando, a conexão falha com o motivo honesto.
 * Nenhum participante é simulado: sem conexão, a sessão só mostra quem
 * está NESTE aparelho (ver [WatchPartyStore.members]).
 */

/** Papel de quem está na sessão. */
enum class PartyRole { HOST, GUEST }

/** Uma sessão local da Watch Party: código + papel + quando foi criada (ms desde epoch). */
data class PartySession(
    val code: String,
    val role: PartyRole,
    val createdAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("code", code)
        .put("role", role.name)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject?): PartySession? {
            if (o == null) return null
            val code = o.optString("code").takeIf { WatchParty.isValidCode(it) } ?: return null
            val role = runCatching { PartyRole.valueOf(o.optString("role")) }.getOrDefault(PartyRole.HOST)
            return PartySession(code, role, o.optLong("createdAt", 0L))
        }
    }
}

/** Alguém na sessão. Sem conexão, só existe o participante local ([isSelf]). */
data class PartyMember(val name: String, val isSelf: Boolean)

/**
 * Motivo da sincronização estar indisponível QUANDO a conexão direta
 * falha e não há relay configurado (mostrado na tela, sem inventar).
 * Com a sala conectada, a sincronização funciona (ver [PartyRuntime]).
 */
const val PARTY_SYNC_UNAVAILABLE_REASON =
    "Sincronização indisponível: a conexão direta falhou e não há relay " +
        "(TURN) configurado. A sala funciona na mesma rede Wi-Fi ou com " +
        "um TURN seu nas Configurações — sem servidor de terceiro no meio."

/** STUN público sem credencial (só descoberta de NAT — não é backend). */
const val PARTY_STUN_URL = "stun:stun.l.google.com:19302"

/** Estado da sincronização (vive no [PartyRuntime], não na sessão). */
enum class PartySyncState {
    /** Sem sala aberta. */
    IDLE,
    /** Sala aberta esperando gente (anfitrião na rede local). */
    LOBBY,
    /** Trocando SDP/ICE com o outro lado. */
    CONNECTING,
    /** Canal de dados aberto com pelo menos 1 participante. */
    CONNECTED,
    /** A conexão falhou (o motivo está em PartyRuntime.failureReason). */
    FAILED,
}

object WatchParty {
    /** Esquema do deep link de convite (ver AndroidManifest.xml). */
    const val SCHEME = "aotv"

    /** Host do deep link de convite. */
    const val HOST = "party"

    /** Tamanho do código da sessão. */
    const val CODE_LENGTH = 6

    /**
     * Alfabeto do código: sem 0/O, 1/I/L (não confundem ao ditar) e sem
     * vogais demais juntas de propósito — o código é sorteado, não é palavra.
     */
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** Gera um código novo de 6 caracteres. */
    fun generateCode(random: java.util.Random = java.util.Random()): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    /** Código válido: 6 caracteres do alfabeto (ignora caixa e espaços). */
    fun isValidCode(raw: String?): Boolean {
        if (raw == null) return false
        val clean = raw.trim().uppercase()
        return clean.length == CODE_LENGTH && clean.all { it in CODE_ALPHABET }
    }

    /** Normaliza o código digitado/colado (caixa alta, sem espaços). */
    fun normalizeCode(raw: String): String = raw.trim().uppercase()

    /** Link de convite da sessão (abre o app pelo deep link). */
    fun buildInviteLink(code: String): String = "$SCHEME://$HOST/?code=$code"

    /** Texto do convite (p/ copiar ou compartilhar). */
    fun buildInviteText(code: String): String =
        "Assista comigo no AOTV! Abra o link e entre com o código $code: ${buildInviteLink(code)}"

    /**
     * Extrai o código de um deep link `aotv://party/?code=XXXXXX`.
     * null quando não é um convite válido (outro host, sem `code` ou código
     * fora do formato). A tela usa isto p/ pré-preencher a entrada.
     */
    fun parseDeepLink(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        val code = uri.getQueryParameter("code") ?: return null
        return normalizeCode(code).takeIf { isValidCode(it) }
    }
}

/**
 * Pedido de navegação p/ a tela da Watch Party vindo de deep link (Lote 10,
 * v1.20.0). A MainActivity oferece o código (onCreate/onNewIntent) e o
 * MoviesNavigation consome e navega p/ `movies_settings/watchparty`. O código
 * em si a tela lê do intent — aqui só viaja o gatilho da navegação.
 */
object PartyDeepLink {
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()
    fun offer(code: String) { _code.value = code }
    fun consume() { _code.value = null }
}
