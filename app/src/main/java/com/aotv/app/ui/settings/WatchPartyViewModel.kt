package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.party.PARTY_SYNC_UNAVAILABLE_REASON
import com.aotv.app.data.party.PartyLateJoinOffer
import com.aotv.app.data.party.PartyLobbyPhase
import com.aotv.app.data.party.PartyMember
import com.aotv.app.data.party.PartyPauseRequest
import com.aotv.app.data.party.PartyPeer
import com.aotv.app.data.party.PartyReactionEvent
import com.aotv.app.data.party.PartyRole
import com.aotv.app.data.party.PartyChatMessage
import com.aotv.app.data.party.PartySession
import com.aotv.app.data.party.PartySyncInfo
import com.aotv.app.data.party.PartySyncState
import com.aotv.app.data.party.PartyRuntime
import com.aotv.app.data.party.WatchParty
import com.aotv.app.data.party.WatchPartyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado da tela "Watch Party" (Lote 10, v1.20.0; sala real no Lote 19, v1.29.0). */
data class WatchPartyUiState(
    val session: PartySession? = null,
    val localName: String = "",
    val members: List<PartyMember> = emptyList(),
    /** Código que chegou por deep link e ainda não foi usado (pré-preenche a entrada). */
    val inviteCode: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class WatchPartyViewModel @Inject constructor(
    private val store: WatchPartyStore,
    private val prefs: PlatformPrefs,
    val runtime: PartyRuntime,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        WatchPartyUiState(
            session = store.sessionFlow.value,
            localName = store.localName,
            members = store.members(),
        ),
    )
    val ui: StateFlow<WatchPartyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.sessionFlow.collect { session ->
                _ui.update { it.copy(session = session, members = currentMembers()) }
            }
        }
        viewModelScope.launch {
            runtime.peers.collect {
                _ui.update { state -> state.copy(members = currentMembers()) }
            }
        }
        viewModelScope.launch {
            runtime.failureReason.collect { reason ->
                if (reason != null) _ui.update { it.copy(error = reason) }
            }
        }
    }

    /** Membros p/ a tela: da sala conectada, ou só o local sem conexão. */
    private fun currentMembers(): List<PartyMember> {
        val peers = runtime.peers.value
        if (peers.isEmpty()) return store.members()
        return peers.map { PartyMember(it.name, it.isSelf) }
    }

    // ─── Estado da sala (vem do runtime) ─────────────────────────────────
    val partyConnectionState: StateFlow<PartySyncState> = runtime.connectionState
    val partyPeers: StateFlow<List<PartyPeer>> = runtime.peers
    val partyChat: StateFlow<List<PartyChatMessage>> = runtime.chat
    val partyReactions: StateFlow<List<PartyReactionEvent>> = runtime.reactions
    val partyPauseRequests: StateFlow<List<PartyPauseRequest>> = runtime.pauseRequests
    val partyLobbyPhase: StateFlow<PartyLobbyPhase> = runtime.lobbyPhase
    val partySyncInfo: StateFlow<PartySyncInfo> = runtime.syncInfo
    val partyLateJoin: StateFlow<PartyLateJoinOffer?> = runtime.lateJoin
    val partyLanUrl: StateFlow<String?> = runtime.lanUrl
    val partyTitle: StateFlow<String> = runtime.partyTitle
    val manualOffer: StateFlow<String?> = runtime.manualOffer
    val manualAnswer: StateFlow<String?> = runtime.manualAnswer

    /** Estado da sincronização (ao vivo, do runtime). */
    val syncState: PartySyncState get() = runtime.connectionState.value

    /** Motivo da sincronização estar indisponível quando a conexão falha. */
    val syncUnavailableReason: String get() = PARTY_SYNC_UNAVAILABLE_REASON

    /** true quando quem está neste aparelho é o anfitrião da sessão. */
    val isHost: Boolean get() = store.session?.role == PartyRole.HOST

    /** Link de convite da sessão atual ("" sem sessão). */
    fun inviteLink(): String = _ui.value.session?.let { WatchParty.buildInviteLink(it.code) }.orEmpty()

    /** Texto do convite da sessão atual ("" sem sessão). */
    fun inviteText(): String = _ui.value.session?.let { WatchParty.buildInviteText(it.code) }.orEmpty()

    /** Cria uma sessão nova como anfitrião (gera o código). */
    fun createSession() {
        val created = store.createSession()
        _ui.update {
            it.copy(
                message = "Sessão criada. Compartilhe o código ${created.code} com quem vai assistir junto.",
                error = null,
            )
        }
    }

    /** Entra numa sessão com o código digitado/colado. */
    fun joinSession(rawCode: String) {
        val joined = store.joinSession(rawCode)
        if (joined == null) {
            _ui.update {
                it.copy(
                    error = "Código inválido: esperado 6 caracteres (letras e números, sem 0/O/1/I/L).",
                    message = null,
                )
            }
            return
        }
        _ui.update {
            it.copy(
                inviteCode = null,
                message = "Você entrou na sessão ${joined.code}. Abra a sala na rede local ou use a troca manual p/ conectar com o anfitrião.",
                error = null,
            )
        }
    }

    /** Sai da sessão atual (fecha a conexão e apaga o código local). */
    fun leaveSession() {
        runtime.leaveParty()
        _ui.update { it.copy(message = "Você saiu da sessão.", error = null) }
    }

    /**
     * Código que chegou por deep link (`aotv://party/?code=`). Pré-preenche
     * a entrada; entrar continua sendo um toque do usuário — o link sozinho
     * nunca entra na sessão.
     */
    fun onInviteReceived(code: String) {
        if (_ui.value.session != null) return
        _ui.update {
            it.copy(
                inviteCode = code,
                message = "Convite recebido (código $code). Toque em Entrar para participar.",
                error = null,
            )
        }
    }

    /** Limpa a mensagem/erro da tela. */
    fun clearMessage() {
        runtime.clearFailure()
        _ui.update { it.copy(message = null, error = null) }
    }

    // ─── Sala: abrir / entrar (rede local e manual) ───────────────────────

    /** Anfitrião: abre a sala na rede local (o título vem do player depois). */
    fun startHosting(title: String) {
        viewModelScope.launch {
            val ok = runtime.startHosting(title, tmdbId = 0)
            _ui.update {
                it.copy(
                    message = if (ok) "Sala aberta na rede local. Quem está no mesmo Wi-Fi entra com o endereço abaixo."
                    else null,
                    error = if (ok) null else runtime.failureReason.value,
                )
            }
        }
    }

    /** Convidado: entra na sala do anfitrião na rede local. */
    fun joinLan(hostPort: String, code: String) {
        val cleanCode = WatchParty.normalizeCode(code)
        if (!WatchParty.isValidCode(cleanCode)) {
            _ui.update { it.copy(error = "Código inválido: esperado 6 caracteres.", message = null) }
            return
        }
        if (hostPort.isBlank()) {
            _ui.update { it.copy(error = "Digite o endereço do anfitrião (ex.: 192.168.1.10:12345).", message = null) }
            return
        }
        runtime.joinLan(hostPort, cleanCode)
        _ui.update { it.copy(message = "Conectando com o anfitrião…", error = null) }
    }

    /** Anfitrião: cria a oferta manual (o blob aparece p/ copiar). */
    fun createManualOffer() {
        val ok = runtime.createManualOffer()
        _ui.update {
            it.copy(
                message = if (ok) "Oferta criada. Copie o texto e mande p/ o convidado."
                else null,
                error = if (ok) null else runtime.failureReason.value,
            )
        }
    }

    /** Convidado: entra com a oferta manual do anfitrião. */
    fun joinWithManualOffer(blob: String) {
        val ok = runtime.joinWithManualOffer(blob)
        _ui.update {
            it.copy(
                message = if (ok) "Resposta criada. Copie o texto e mande de volta p/ o anfitrião."
                else null,
                error = if (ok) null else runtime.failureReason.value,
            )
        }
    }

    /** Anfitrião: completa a troca manual com a resposta do convidado. */
    fun completeManualAnswer(blob: String) {
        val ok = runtime.completeManualAnswer(blob)
        _ui.update {
            it.copy(
                message = if (ok) "Resposta aplicada. A conexão está sendo estabelecida…"
                else null,
                error = if (ok) null else runtime.failureReason.value,
            )
        }
    }

    // ─── Sala: lobby, chat, reações, pausa ────────────────────────────────

    /** Anfitrião: "Começar o filme" (a fase vira PLAYING p/ todo mundo). */
    fun startFilm() {
        runtime.startFilm()
        _ui.update { it.copy(message = "O filme começou p/ todo mundo.", error = null) }
    }

    /** Anfitrião: abre a conversa pós-filme. */
    fun endFilm() {
        runtime.endFilm()
    }

    /** Manda uma mensagem no chat da sala. */
    fun sendChat(text: String) {
        runtime.sendChat(text)
    }

    /** Manda uma reação p/ a sala. */
    fun sendReaction(emoji: String) {
        runtime.sendReaction(emoji)
    }

    /** Convidado: pede p/ pausar. */
    fun requestPause(reason: String) {
        runtime.requestPause(reason)
        _ui.update { it.copy(message = "Pedido de pausa enviado ao anfitrião.", error = null) }
    }

    /** Anfitrião: responde a um pedido de pausa. */
    fun respondPause(requestId: String, approved: Boolean) {
        runtime.respondPause(requestId, approved)
    }

    /** Quem entrou atrasado: "Entrar no ponto da sala". */
    fun joinNow() {
        runtime.joinNow()
    }

    /** Quem entrou atrasado: "Ver do início a 1,5×". */
    fun catchUpFromStart() {
        runtime.catchUpFromStart()
    }

    /** Quem entrou atrasado: "10 min atrás a 1,5×". */
    fun catchUpFromTenMinAgo() {
        runtime.catchUpFromTenMinAgo()
    }

    /** Sai da sala (fecha a conexão e apaga o código). */
    fun leaveParty() {
        leaveSession()
    }

    // ─── Voz, câmera e relay ──────────────────────────────────────────────

    private val _voiceEnabled = MutableStateFlow(prefs.partyVoiceEnabled)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _cameraEnabled = MutableStateFlow(prefs.partyCameraEnabled)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled.asStateFlow()

    private val _catchUpEnabled = MutableStateFlow(prefs.partyCatchUpEnabled)
    val catchUpEnabled: StateFlow<Boolean> = _catchUpEnabled.asStateFlow()

    /** Liga/desliga o microfone da sala. */
    fun setVoiceEnabled(enabled: Boolean) {
        _voiceEnabled.value = enabled
        runtime.setVoiceEnabled(enabled)
    }

    /** Liga/desliga a câmera da sala. false quando não deu p/ ligar. */
    fun setCameraEnabled(enabled: Boolean): Boolean {
        if (enabled) {
            val ok = runtime.setCameraEnabled(true)
            if (!ok) {
                _ui.update {
                    it.copy(
                        error = "Não consegui ligar a câmera (sem câmera, sem permissão ou ocupada).",
                        message = null,
                    )
                }
                return false
            }
        } else {
            runtime.setCameraEnabled(false)
        }
        _cameraEnabled.value = enabled
        return true
    }

    /** Liga/desliga o "catch up" automático a 1,5×. */
    fun setCatchUpEnabled(enabled: Boolean) {
        _catchUpEnabled.value = enabled
        runtime.setCatchUpEnabled(enabled)
    }

    private val _turnUrl = MutableStateFlow(prefs.partyTurnUrl)
    val turnUrl: StateFlow<String> = _turnUrl.asStateFlow()

    private val _turnUsername = MutableStateFlow(prefs.partyTurnUsername)
    val turnUsername: StateFlow<String> = _turnUsername.asStateFlow()

    private val _turnPassword = MutableStateFlow(prefs.partyTurnPassword)
    val turnPassword: StateFlow<String> = _turnPassword.asStateFlow()

    /** Salva o relay TURN do usuário (vale na próxima conexão). */
    fun saveTurn(url: String, user: String, pass: String) {
        runtime.saveTurn(url, user, pass)
        _turnUrl.value = url.trim()
        _turnUsername.value = user.trim()
        _turnPassword.value = pass
        _ui.update {
            it.copy(
                message = if (url.isBlank()) "Relay removido (a sala só funciona em P2P direto)."
                else "Relay salvo. Vale na próxima conexão.",
                error = null,
            )
        }
    }
}
