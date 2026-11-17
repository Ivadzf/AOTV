package com.aotv.app.data.party

import org.json.JSONObject

/**
 * Watch Party real (Lote 19, v1.29.0) — protocolo de mensagens.
 *
 * Tudo que viaja entre os participantes vai por aqui, como JSON, num
 * canal de dados WebRTC (DTLS — criptografado pelo próprio WebRTC) ou,
 * antes de o canal abrir, pela caixa de sinalização local (ver
 * [PartySignaling]). Nenhuma mensagem sai do aparelho sem passar por um
 * destes dois canais; não há servidor de terceiro no caminho.
 *
 * Tipos:
 * - HELLO: "cheguei" (nome, voz/câmera ligadas, anfitrião ou não).
 * - PING/PONG: medir o relógio (ver [PartyClock]).
 * - STATE: posição + tocando + relógio de envio (o anfitrião é a verdade).
 * - CHAT / REACTION: conversa e reações da sala.
 * - SPEAKING: nível do microfone (0–100) p/ o anel de quem fala.
 * - PAUSE_REQUEST / PAUSE_REPLY: "pedir p/ pausar" (convidado → anfitrião).
 * - PEER_JOIN / PEER_LEAVE: alguém entrou/saiu (o anfitrião retransmite).
 * - LOBBY: fase da sala (sala de espera / tocando / conversa pós-filme).
 * - TITLE: o que está tocando (título + id TMDB).
 *
 * O anfitrião é o centro da sala: quem não é anfitrião só fala COM o
 * anfitrião, e o anfitrião retransmite p/ todo mundo (ver [PartyRuntime]).
 * Assim o chat, as reações e o estado chegam a todos sem precisar de
 * canal de dados entre cada par de convidados (só voz/vídeo é malha).
 */

/** Fase da sala (lobby → tocando → conversa pós-filme). */
enum class PartyLobbyPhase { LOBBY, PLAYING, POST_FILM }

/** Tipos de mensagem do protocolo (campo `type` do JSON). */
enum class PartyMessageType { HELLO, PING, PONG, STATE, CHAT, REACTION, SPEAKING, PAUSE_REQUEST, PAUSE_REPLY, PEER_JOIN, PEER_LEAVE, LOBBY, TITLE }

/** Emojis de reação permitidos no chat (fixos — nada digitado vira reação). */
val PARTY_REACTIONS = listOf("❤️", "😂", "😮", "👏", "😢", "🔥")

/** Nível de voz a partir do qual o anel de "falando" acende (0–100). */
const val PARTY_SPEAKING_THRESHOLD = 25

/**
 * Uma mensagem do protocolo. Campos opcionais só existem no tipo que os
 * usa; [fromJson] ignora o que não conhece (compat. p/ frente).
 */
data class PartyMessage(
    val type: PartyMessageType,
    /** Id do aparelho que enviou (8 hex, gerado por sessão — não é o nome). */
    val from: String,
    /** Nome de exibição de quem enviou (nome do perfil). */
    val fromName: String = "",
    /** Nº de sequência de quem enviou (p/ descartar duplicata na retransmissão). */
    val seq: Long = 0L,
    /** Relógio de quem enviou no envio (ms desde epoch). */
    val sentAtMs: Long = 0L,
    // ── HELLO / PEER_JOIN ──
    val isHost: Boolean = false,
    val voiceOn: Boolean = false,
    val cameraOn: Boolean = false,
    // ── PING / PONG ──
    val pingId: Long = 0L,
    /** Relógio do outro lado ao receber o PING (só no PONG). */
    val remoteAtMs: Long = 0L,
    // ── STATE ──
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val rate: Float = 1f,
    val title: String = "",
    val tmdbId: Int = 0,
    // ── CHAT / REACTION ──
    val text: String = "",
    val emoji: String = "",
    // ── SPEAKING ──
    val level: Int = 0,
    // ── PAUSE_REQUEST / PAUSE_REPLY ──
    val requestId: String = "",
    val reason: String = "",
    val approved: Boolean = false,
    // ── LOBBY ──
    val phase: PartyLobbyPhase = PartyLobbyPhase.LOBBY,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("type", type.name)
        .put("from", from)
        .put("fromName", fromName)
        .put("seq", seq)
        .put("sentAtMs", sentAtMs)
        .put("isHost", isHost)
        .put("voiceOn", voiceOn)
        .put("cameraOn", cameraOn)
        .put("pingId", pingId)
        .put("remoteAtMs", remoteAtMs)
        .put("positionMs", positionMs)
        .put("playing", playing)
        .put("rate", rate.toDouble())
        .put("title", title)
        .put("tmdbId", tmdbId)
        .put("text", text)
        .put("emoji", emoji)
        .put("level", level)
        .put("requestId", requestId)
        .put("reason", reason)
        .put("approved", approved)
        .put("phase", phase.name)

    companion object {
        /** null quando o JSON não é uma mensagem válida (tipo desconhecido, sem `from`). */
        fun fromJson(raw: String): PartyMessage? = runCatching {
            val o = JSONObject(raw)
            if (o.optInt("v", 1) != 1) return null
            val type = runCatching { PartyMessageType.valueOf(o.optString("type")) }.getOrNull() ?: return null
            val from = o.optString("from").takeIf { it.isNotBlank() } ?: return null
            PartyMessage(
                type = type,
                from = from,
                fromName = o.optString("fromName"),
                seq = o.optLong("seq", 0L),
                sentAtMs = o.optLong("sentAtMs", 0L),
                isHost = o.optBoolean("isHost", false),
                voiceOn = o.optBoolean("voiceOn", false),
                cameraOn = o.optBoolean("cameraOn", false),
                pingId = o.optLong("pingId", 0L),
                remoteAtMs = o.optLong("remoteAtMs", 0L),
                positionMs = o.optLong("positionMs", 0L),
                playing = o.optBoolean("playing", false),
                rate = o.optDouble("rate", 1.0).toFloat(),
                title = o.optString("title"),
                tmdbId = o.optInt("tmdbId", 0),
                text = o.optString("text"),
                emoji = o.optString("emoji"),
                level = o.optInt("level", 0),
                requestId = o.optString("requestId"),
                reason = o.optString("reason"),
                approved = o.optBoolean("approved", false),
                phase = runCatching { PartyLobbyPhase.valueOf(o.optString("phase")) }
                    .getOrDefault(PartyLobbyPhase.LOBBY),
            )
        }.getOrNull()

        /** Texto cru p/ o canal de dados (JSON, UTF-8). */
        fun encode(message: PartyMessage): ByteArray =
            message.toJson().toString().toByteArray(Charsets.UTF_8)

        /** Decodifica o que chegou no canal de dados. null = lixo, descartado. */
        fun decode(bytes: ByteArray): PartyMessage? =
            runCatching { fromJson(String(bytes, Charsets.UTF_8)) }.getOrNull()
    }
}

/** Alguém na sala (o anfitrião + os convidados conectados). */
data class PartyPeer(
    val peerId: String,
    val name: String,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    val voiceOn: Boolean = false,
    val cameraOn: Boolean = false,
    /** Nível de voz agora (0–100); >= [PARTY_SPEAKING_THRESHOLD] = falando. */
    val speakingLevel: Int = 0,
    /** true quando o canal de dados com ele está aberto. */
    val connected: Boolean = false,
) {
    /** true quando o anel de "falando" deve acender neste participante. */
    val isSpeaking: Boolean get() = speakingLevel >= PARTY_SPEAKING_THRESHOLD
}

/** Uma mensagem do chat da sala (só vive na sessão — sai da sala, some). */
data class PartyChatMessage(
    val id: String,
    val peerId: String,
    val name: String,
    val text: String,
    val atMs: Long = 0L,
)

/** Uma reação (emoji flutuante). Só as 20 mais recentes são guardadas. */
data class PartyReactionEvent(
    val id: String,
    val peerId: String,
    val name: String,
    val emoji: String,
    val atMs: Long = 0L,
)

/** Um pedido de "pausa, por favor" esperando a resposta do anfitrião. */
data class PartyPauseRequest(
    val id: String,
    val peerId: String,
    val name: String,
    val reason: String,
    val atMs: Long = 0L,
)
