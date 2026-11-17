package com.aotv.app.data.party

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sessão da Watch Party (Lote 10, v1.20.0; conexão real no Lote 19,
 * v1.29.0) — estado local.
 *
 * A sessão vive na pref `party_session_v1` (JSON de
 * [PartySession], por perfil): código + papel + criação. É local e
 * efêmera — sair da sala apaga o código (ver [PartyRuntime.leaveParty]).
 *
 * Participantes: sem conexão, [members] devolve SÓ quem está neste
 * aparelho (o nome do perfil ativo). Com a sala conectada, quem manda
 * nos membros é o [PartyRuntime] (a tela lê de lá). Nenhum participante
 * é inventado ou simulado.
 */
@Singleton
class WatchPartyStore @Inject constructor(
    private val prefs: PlatformPrefs,
) {
    private fun readSession(): PartySession? = runCatching {
        val raw = prefs.partySessionJson
        if (raw.isBlank()) null else PartySession.fromJson(JSONObject(raw))
    }.getOrDefault(null)

    private val _session = MutableStateFlow(readSession())
    /** Sessão atual (null = sem sessão), que avisa quem observa. */
    val sessionFlow: StateFlow<PartySession?> = _session.asStateFlow()

    /** Sessão atual, ou null sem sessão. */
    val session: PartySession? get() = _session.value

    /** Nome de quem está neste aparelho (o nome do perfil ativo). */
    val localName: String get() = prefs.profile.name.trim().ifBlank { "Você" }

    /** Quem está na sessão: só o participante local. Nunca inventa os outros. */
    fun members(): List<PartyMember> =
        if (_session.value == null) emptyList() else listOf(PartyMember(localName, isSelf = true))

    /** Cria uma sessão nova como anfitrião (gera o código). */
    fun createSession(): PartySession {
        val created = PartySession(WatchParty.generateCode(), PartyRole.HOST, System.currentTimeMillis())
        saveSession(created)
        return created
    }

    /**
     * Entra numa sessão com o código do convite. null quando o código é
     * inválido (formato errado — a tela mostra o erro real).
     */
    fun joinSession(rawCode: String): PartySession? {
        val code = WatchParty.normalizeCode(rawCode)
        if (!WatchParty.isValidCode(code)) return null
        val joined = PartySession(code, PartyRole.GUEST, System.currentTimeMillis())
        saveSession(joined)
        return joined
    }

    /** Sai da sessão (apaga o código local). */
    fun leaveSession() {
        saveSession(null)
    }

    /** Recarrega das prefs (troca de perfil). */
    fun refresh() {
        _session.value = readSession()
    }

    private fun saveSession(value: PartySession?) {
        prefs.partySessionJson = value?.toJson().toString().orEmpty()
        _session.value = value
    }
}
