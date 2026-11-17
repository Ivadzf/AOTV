package com.aotv.app.data.party

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

/** O que a tela mostra sobre a sincronização (relógio + catch-up + decisão). */
data class PartySyncInfo(
    val clockOffsetMs: Long = 0L,
    val rttMs: Long = 0L,
    val catchingUp: Boolean = false,
    val lastDecision: String = "",
    val peersConnected: Int = 0,
)

/** Oferta p/ quem entrou com o filme já andando (só p/ convidado). */
data class PartyLateJoinOffer(
    val hostPositionMs: Long,
    val title: String,
)

/**
 * Watch Party real (Lote 19, v1.29.0) — orquestrador da sala.
 *
 * Junta as peças: sessão ([WatchPartyStore]), sinalização
 * ([PartyLanServer]/[PartyLanClient]/[ManualSdp]), WebRTC
 * ([PartyCallManager]), relógio ([PartyClock]), motor de sync
 * ([PartySyncEngine]), voz ([PartySpeakingMeter]) e o player
 * ([PartyPlayer]).
 *
 * Regras da sala (o que o código garante):
 * - O ANFITRIÃO é a verdade: só o STATE dele move o player dos outros.
 *   Convidado nunca manda STATE (só pede pausa, via PAUSE_REQUEST).
 * - Mensagens (chat, reação, SPEAKING, STATE) só andam no canal de dados
 *   com o anfitrião; o anfitrião retransmite p/ todos (menos quem enviou).
 *   Voz e vídeo são malha P2P (cada par tem seu PeerConnection).
 * - O relógio é medido por ping-pong (5 amostras ao conectar + 1 a cada
 *   30 s); sem ele, o STATE chegaria com o atraso da rede embutido.
 * - "Catch up" a 1,5× vale p/ atraso de 0,5–2 s ([PartySyncEngine]) e p/
 *   quem entrou atrasado (modo: do início ou 10 min atrás, a 1,5×, até
 *   alcançar a sala — depois volta sozinho p/ 1× e segue o anfitrião).
 * - Lobby: a sala abre em LOBBY; o anfitrião toca "Começar o filme" e a
 *   fase vira PLAYING. Quando o filme acaba (player do anfitrião no fim),
 *   a fase vira POST_FILM e o chat continua aberto (conversa pós-filme).
 * - Nada é persistido: chat, reações e membros vivem na sessão. Sair da
 *   sala apaga tudo (e a sessão some, como no Lote 10).
 * - Sem TURN configurado e P2P falhando, a conexão falha com o motivo
 *   honesto (não finge que conectou).
 */
@Singleton
class PartyRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PlatformPrefs,
    private val store: WatchPartyStore,
    private val callManager: PartyCallManager,
) {
    companion object {
        /** Id do par no modo manual (só existe 1 par: anfitrião↔convidado). */
        const val MANUAL_PEER_ID = "manual-peer"

        /** Quantos pings de relógio ao conectar (depois, 1 a cada 30 s). */
        const val CLOCK_PINGS_ON_CONNECT = 5

        /** Intervalo do re-ping do relógio (ms). */
        const val CLOCK_REPING_MS = 30_000L

        /** A cada quanto o anfitrião reenvia o STATE tocando (ms). */
        const val STATE_BROADCAST_MS = 500L

        /** A cada quanto a sala lê a caixa de sinalização (ms). */
        const val SIGNAL_POLL_MS = 1_000L

        /** Falhas seguidas de poll até declarar o anfitrião perdido. */
        const val MAX_POLL_FAILURES = 5

        /** Posição a partir da qual entrar vira "entrou atrasado" (ms). */
        const val LATE_JOIN_AFTER_MS = 60_000L

        /** "10 min atrás" do catch-up de quem entrou atrasado (ms). */
        const val CATCH_UP_BACK_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Id deste aparelho nesta sala (8 hex, sorteado por processo). */
    val peerId: String = (1..8).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

    /** Nome de quem está neste aparelho (nome do perfil ativo). */
    val localName: String get() = store.localName

    // ─── Estado p/ a tela ──────────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(PartySyncState.IDLE)
    val connectionState: StateFlow<PartySyncState> = _connectionState.asStateFlow()

    private val _peers = MutableStateFlow<List<PartyPeer>>(emptyList())
    val peers: StateFlow<List<PartyPeer>> = _peers.asStateFlow()

    private val _chat = MutableStateFlow<List<PartyChatMessage>>(emptyList())
    val chat: StateFlow<List<PartyChatMessage>> = _chat.asStateFlow()

    private val _reactions = MutableStateFlow<List<PartyReactionEvent>>(emptyList())
    val reactions: StateFlow<List<PartyReactionEvent>> = _reactions.asStateFlow()

    private val _pauseRequests = MutableStateFlow<List<PartyPauseRequest>>(emptyList())
    val pauseRequests: StateFlow<List<PartyPauseRequest>> = _pauseRequests.asStateFlow()

    private val _lobbyPhase = MutableStateFlow(PartyLobbyPhase.LOBBY)
    val lobbyPhase: StateFlow<PartyLobbyPhase> = _lobbyPhase.asStateFlow()

    private val _lanUrl = MutableStateFlow<String?>(null)
    val lanUrl: StateFlow<String?> = _lanUrl.asStateFlow()

    private val _syncInfo = MutableStateFlow(PartySyncInfo())
    val syncInfo: StateFlow<PartySyncInfo> = _syncInfo.asStateFlow()

    private val _lateJoin = MutableStateFlow<PartyLateJoinOffer?>(null)
    val lateJoin: StateFlow<PartyLateJoinOffer?> = _lateJoin.asStateFlow()

    private val _failureReason = MutableStateFlow<String?>(null)
    val failureReason: StateFlow<String?> = _failureReason.asStateFlow()

    /** Blob da oferta manual (anfitrião) — pronto p/ copiar. */
    private val _manualOffer = MutableStateFlow<String?>(null)
    val manualOffer: StateFlow<String?> = _manualOffer.asStateFlow()

    /** Blob da resposta manual (convidado) — pronto p/ copiar. */
    private val _manualAnswer = MutableStateFlow<String?>(null)
    val manualAnswer: StateFlow<String?> = _manualAnswer.asStateFlow()

    /** Título da sala (o que está tocando). */
    private val _partyTitle = MutableStateFlow("")
    val partyTitle: StateFlow<String> = _partyTitle.asStateFlow()

    /** Vídeo remoto por peerId (vem do [PartyCallManager]). */
    val remoteVideoTracks get() = callManager.remoteVideoTracks

    /** Contexto EGL p/ os tiles de vídeo (null antes do WebRTC ligar). */
    val eglContext get() = callManager.eglContext

    /** Vídeo local (a própria câmera). null sem câmera. */
    val localVideoTrack get() = callManager.localVideoTrack

    /** true quando este aparelho tem câmera. */
    fun hasCamera(): Boolean = callManager.hasCamera()

    /** Microfone permitido (runtime) neste aparelho. */
    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Câmera permitida (runtime) neste aparelho. */
    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    // ─── Estado interno ────────────────────────────────────────────────────
    private val clock = PartyClock()
    private val speakingMeter = PartySpeakingMeter()
    private var isHost = false
    private var seq = 0L
    private var hostPeerId: String? = null
    private var tmdbId = 0
    private var lanServer: PartyLanServer? = null
    private var lanBaseUrl: String? = null
    private var lanCode = ""
    private var manualMode = false
    private var pollJob: Job? = null
    private var playerJob: Job? = null
    private var speakingJob: Job? = null
    private var speakingDecayJob: Job? = null
    private var clockJob: Job? = null
    private var sinceSeq = 0L
    private var pollFailures = 0
    private var player: PartyPlayer? = null
    private var catchingUp = false
    private var catchUpFromStart = false
    private var lastHostState: PartyMessage? = null
    private var lateJoinShown = false
    private var pendingPings = HashMap<Long, Long>()
    private var pingCounter = 0L
    private val seenMessages = LinkedHashSet<String>()
    private val pendingIce = HashMap<String, MutableList<IceCandidate>>()
    private val lastSpeakingAt = HashMap<String, Long>()

    init {
        callManager.onDataMessage = { from, bytes -> handleDataMessage(from, bytes) }
        callManager.onDataChannelOpen = { peerId -> onPeerConnected(peerId) }
        callManager.onPeerConnectionState = { peerId, state ->
            if (state == PeerConnection.IceConnectionState.FAILED) onPeerFailed(peerId)
        }
        callManager.onIceCandidate = { peerId, candidate ->
            // Na rede local, o candidato viaja na hora pela caixa; no modo
            // manual ele fica guardado p/ o blob (ver localCandidates).
            if (!manualMode) {
                val base = lanBaseUrl
                if (base != null) {
                    scope.launch {
                        val payload = JSONObject()
                            .put("mid", candidate.sdpMid)
                            .put("idx", candidate.sdpMLineIndex)
                            .put("sdp", candidate.sdp)
                            .toString()
                        PartyLanClient.postSignal(
                            base, lanCode,
                            SignalEnvelope(from = this@PartyRuntime.peerId, to = peerId, kind = "ice", payload = payload),
                        )
                    }
                }
            }
        }
        callManager.onGatheringComplete = { peerId ->
            if (manualMode) buildManualBlob(peerId)
        }
    }

    // ─── Sessão: abrir / entrar / sair ─────────────────────────────────────

    /**
     * Abre a sala na rede local (anfitrião). Precisa de sessão criada
     * (código). false quando não deu (sem sessão, sem rede local ou sem
     * WebRTC) — o motivo fica em [failureReason].
     */
    fun startHosting(title: String, tmdbId: Int): Boolean {
        val session = store.session ?: run {
            fail("Crie uma sessão primeiro (o código é a senha da sala).")
            return false
        }
        if (session.role != PartyRole.HOST) {
            fail("Só o anfitrião abre a sala.")
            return false
        }
        stopConnection()
        isHost = true
        manualMode = false
        _partyTitle.value = title
        this.tmdbId = tmdbId
        hostPeerId = peerId
        val server = PartyLanServer(session.code, title, tmdbId, store.localName, peerId)
        val url = server.start() ?: run {
            fail("Sem rede local: o servidor da sala não abriu (sem Wi-Fi?).")
            return false
        }
        lanServer = server
        lanBaseUrl = url
        lanCode = session.code
        _lanUrl.value = url
        if (!callManager.ensureLocalMedia(prefs.partyVoiceEnabled, prefs.partyCameraEnabled)) {
            fail("O WebRTC não ligou neste aparelho.")
            return false
        }
        startSpeakingMeterIfAllowed()
        _lobbyPhase.value = PartyLobbyPhase.LOBBY
        _connectionState.value = PartySyncState.LOBBY
        upsertSelfPeer()
        startPolling()
        startPlayerLoop()
        startSpeakingDecay()
        return true
    }

    /** Entra na sala de um anfitrião na rede local (convidado). */
    fun joinLan(hostPort: String, code: String) {
        scope.launch {
            stopConnection()
            isHost = false
            manualMode = false
            _connectionState.value = PartySyncState.CONNECTING
            val normalized = hostPort.trim().removePrefix("http://").trimEnd('/')
            val baseUrl = if (normalized.startsWith("http")) normalized else "http://$normalized"
            val info = PartyLanClient.fetchInfo(baseUrl, code).getOrElse { error ->
                fail(error.message ?: "Não consegui falar com o anfitrião.")
                return@launch
            }
            val hostId = info.optString("hostPeerId").takeIf { it.isNotBlank() } ?: run {
                fail("O anfitrião não respondeu com uma sala válida.")
                return@launch
            }
            hostPeerId = hostId
            lanBaseUrl = baseUrl
            lanCode = code
            _partyTitle.value = info.optString("title")
            tmdbId = info.optInt("tmdbId", 0)
            if (!callManager.ensureLocalMedia(prefs.partyVoiceEnabled, prefs.partyCameraEnabled)) {
                fail("O WebRTC não ligou neste aparelho.")
                return@launch
            }
            startSpeakingMeterIfAllowed()
            upsertSelfPeer()
            // Avisa o anfitrião que chegou (o nome dele aparece na lista).
            PartyLanClient.postSignal(
                baseUrl, code,
                SignalEnvelope(from = peerId, to = hostId, kind = "hello", payload = store.localName),
            )
            // O convidado oferece (e cria o canal de dados); o anfitrião responde.
            callManager.createPeer(hostId, withDataChannel = true)
            callManager.createOffer(hostId) { sdp ->
                if (sdp == null) {
                    fail("Não consegui criar a oferta WebRTC.")
                    return@createOffer
                }
                scope.launch {
                    PartyLanClient.postSignal(
                        baseUrl, code,
                        SignalEnvelope(from = peerId, to = hostId, kind = "offer", payload = sdp.description),
                    )
                }
            }
            // Malha: oferece também p/ os convidados que já estão na sala.
            val peersArr = info.optJSONArray("peers")
            if (peersArr != null) {
                for (i in 0 until peersArr.length()) {
                    val other = peersArr.optJSONObject(i)?.optString("peerId").orEmpty()
                    if (other.isBlank() || other == peerId || other == hostId) continue
                    callManager.createPeer(other, withDataChannel = false)
                    callManager.createOffer(other) { sdp ->
                        if (sdp == null) return@createOffer
                        scope.launch {
                            PartyLanClient.postSignal(
                                baseUrl, code,
                                SignalEnvelope(from = peerId, to = other, kind = "offer", payload = sdp.description),
                            )
                        }
                    }
                }
            }
            _connectionState.value = PartySyncState.CONNECTING
            startPolling()
            startPlayerLoop()
            startSpeakingDecay()
        }
    }

    // ─── Sinalização manual (sem servidor nenhum) ──────────────────────────

    /** Anfitrião: cria a oferta manual (o blob aparece em [manualOffer]). */
    fun createManualOffer(): Boolean {
        val session = store.session ?: run {
            fail("Crie uma sessão primeiro (o código é a senha da sala).")
            return false
        }
        if (session.role != PartyRole.HOST) {
            fail("Só o anfitrião cria a oferta.")
            return false
        }
        stopConnection()
        isHost = true
        manualMode = true
        _partyTitle.value = ""
        if (!callManager.ensureLocalMedia(prefs.partyVoiceEnabled, prefs.partyCameraEnabled)) {
            fail("O WebRTC não ligou neste aparelho.")
            return false
        }
        startSpeakingMeterIfAllowed()
        upsertSelfPeer()
        _connectionState.value = PartySyncState.CONNECTING
        callManager.createPeer(MANUAL_PEER_ID, withDataChannel = true)
        callManager.createOffer(MANUAL_PEER_ID) { sdp ->
            if (sdp == null) fail("Não consegui criar a oferta WebRTC.")
        }
        // Se o gathering demorar, monta o blob com o que já reuniu (8 s).
        scope.launch {
            delay(8_000)
            if (manualMode && _manualOffer.value == null) buildManualBlob(MANUAL_PEER_ID)
        }
        startPlayerLoop()
        startSpeakingDecay()
        return true
    }

    /** Convidado: entra com a oferta manual do anfitrião (a resposta vai p/ [manualAnswer]). */
    fun joinWithManualOffer(blob: String): Boolean {
        val data = ManualSdp.decode(blob) ?: run {
            fail("Oferta inválida: confira se copiou o texto inteiro.")
            return false
        }
        if (data.kind != "offer") {
            fail("Isto não é uma oferta (é uma resposta).")
            return false
        }
        stopConnection()
        isHost = false
        manualMode = true
        hostPeerId = MANUAL_PEER_ID
        if (!callManager.ensureLocalMedia(prefs.partyVoiceEnabled, prefs.partyCameraEnabled)) {
            fail("O WebRTC não ligou neste aparelho.")
            return false
        }
        startSpeakingMeterIfAllowed()
        upsertSelfPeer()
        _connectionState.value = PartySyncState.CONNECTING
        callManager.createPeer(MANUAL_PEER_ID, withDataChannel = false)
        val offer = SessionDescription(SessionDescription.Type.OFFER, data.sdp)
        callManager.createAnswer(MANUAL_PEER_ID, offer) { sdp ->
            if (sdp == null) fail("Não consegui criar a resposta WebRTC.")
        }
        data.candidates.forEach { c ->
            callManager.addIceCandidate(MANUAL_PEER_ID, IceCandidate(c.mid, c.idx, c.sdp))
        }
        scope.launch {
            delay(8_000)
            if (manualMode && _manualAnswer.value == null) buildManualBlob(MANUAL_PEER_ID)
        }
        startPlayerLoop()
        startSpeakingDecay()
        return true
    }

    /** Anfitrião: completa a troca manual com a resposta do convidado. */
    fun completeManualAnswer(blob: String): Boolean {
        val data = ManualSdp.decode(blob) ?: run {
            fail("Resposta inválida: confira se copiou o texto inteiro.")
            return false
        }
        if (data.kind != "answer") {
            fail("Isto não é uma resposta (é uma oferta).")
            return false
        }
        val answer = SessionDescription(SessionDescription.Type.ANSWER, data.sdp)
        callManager.setRemoteDescription(MANUAL_PEER_ID, answer) { ok ->
            if (!ok) fail("Não consegui aplicar a resposta WebRTC.")
        }
        data.candidates.forEach { c ->
            callManager.addIceCandidate(MANUAL_PEER_ID, IceCandidate(c.mid, c.idx, c.sdp))
        }
        return true
    }

    /** Monta o blob manual (oferta ou resposta) com o SDP + ICE reunidos. */
    private fun buildManualBlob(peerId: String) {
        val local = callManager.localDescription(peerId) ?: return
        val kind = if (local.type == SessionDescription.Type.OFFER) "offer" else "answer"
        val candidates = callManager.localCandidates(peerId).map { c ->
            ManualIceCandidate(c.sdpMid, c.sdpMLineIndex, c.sdp)
        }
        val blob = ManualSdp.encode(kind, local.description, candidates) ?: return
        if (isHost) _manualOffer.value = blob else _manualAnswer.value = blob
    }

    // ─── Sala: lobby, chat, reações, pausa, pós-filme ─────────────────────

    /** Anfitrião: "Começar o filme" — a fase vira PLAYING p/ todo mundo. */
    fun startFilm() {
        if (!isHost) return
        _lobbyPhase.value = PartyLobbyPhase.PLAYING
        broadcast(lobbyMessage(PartyLobbyPhase.PLAYING))
        broadcastStateNow()
    }

    /** Anfitrião (ou automático, no fim do player dele): abre a conversa pós-filme. */
    fun endFilm() {
        if (!isHost) return
        if (_lobbyPhase.value == PartyLobbyPhase.POST_FILM) return
        _lobbyPhase.value = PartyLobbyPhase.POST_FILM
        broadcast(lobbyMessage(PartyLobbyPhase.POST_FILM))
        addSystemChat("O filme acabou — a conversa pós-filme está aberta.")
    }

    /** Manda uma mensagem no chat da sala (máx. 500 caracteres). */
    fun sendChat(rawText: String) {
        val text = rawText.trim().take(500)
        if (text.isBlank()) return
        val msg = baseMessage(PartyMessageType.CHAT).copy(text = text)
        addChat(msg)
        sendToHostOrBroadcast(msg)
    }

    /** Manda uma reação (só os emojis de [PARTY_REACTIONS]). */
    fun sendReaction(emoji: String) {
        if (emoji !in PARTY_REACTIONS) return
        val msg = baseMessage(PartyMessageType.REACTION).copy(emoji = emoji)
        addReaction(msg)
        sendToHostOrBroadcast(msg)
    }

    /** Convidado: "pede p/ pausar" (o anfitrião aprova ou nega). */
    fun requestPause(reason: String) {
        if (isHost) return
        val host = hostPeerId ?: return
        val msg = baseMessage(PartyMessageType.PAUSE_REQUEST)
        val withId = msg.copy(requestId = "$peerId-${msg.seq}", reason = reason.trim().take(120))
        callManager.sendData(host, PartyMessage.encode(withId))
    }

    /** Anfitrião: responde a um pedido de pausa (aprovar = pausa p/ todos). */
    fun respondPause(requestId: String, approved: Boolean) {
        val req = _pauseRequests.value.firstOrNull { it.id == requestId } ?: return
        _pauseRequests.update { list -> list.filterNot { it.id == requestId } }
        val reply = baseMessage(PartyMessageType.PAUSE_REPLY)
            .copy(requestId = requestId, approved = approved)
        callManager.sendData(req.peerId, PartyMessage.encode(reply))
        if (approved) {
            player?.setPlaying(false)
            broadcastStateNow()
            addSystemChat("O anfitrião aprovou a pausa pedida por ${req.name}.")
        }
    }

    // ─── Quem entrou atrasado ──────────────────────────────────────────────

    /** "Entrar no ponto da sala" (pula p/ onde o anfitrião está). */
    fun joinNow() {
        val state = lastHostState ?: return
        player?.seekTo(state.positionMs)
        player?.setPlaying(state.playing)
        catchingUp = false
        catchUpFromStart = false
        _lateJoin.value = null
    }

    /** "Ver do início a 1,5×" (alcança a sala sozinho). */
    fun catchUpFromStart() {
        player?.seekTo(0L)
        player?.setSpeed(PartySyncEngine.CATCH_UP_RATE)
        player?.setPlaying(true)
        catchingUp = true
        catchUpFromStart = true
        _lateJoin.value = null
    }

    /** "10 min atrás a 1,5×" (alcança a sala sozinho, do ponto recente). */
    fun catchUpFromTenMinAgo() {
        val state = lastHostState ?: return
        player?.seekTo((state.positionMs - CATCH_UP_BACK_MS).coerceAtLeast(0L))
        player?.setSpeed(PartySyncEngine.CATCH_UP_RATE)
        player?.setPlaying(true)
        catchingUp = true
        catchUpFromStart = true
        _lateJoin.value = null
    }

    // ─── Voz e câmera ──────────────────────────────────────────────────────

    /** Liga/desliga o microfone (persiste no perfil). */
    fun setVoiceEnabled(enabled: Boolean) {
        prefs.partyVoiceEnabled = enabled
        callManager.setVoiceEnabled(enabled)
        if (enabled) startSpeakingMeterIfAllowed() else speakingMeter.stop()
        broadcastHello()
    }

    /**
     * Liga/desliga a câmera (persiste no perfil). false quando pediu p/
     * ligar e não deu (sem câmera ou sem permissão — a tela pede antes).
     */
    fun setCameraEnabled(enabled: Boolean): Boolean {
        val ok = callManager.setCameraEnabled(enabled)
        if (!ok && enabled) return false
        prefs.partyCameraEnabled = enabled
        broadcastHello()
        return true
    }

    /** Liga/desliga o "catch up" automático (persiste no perfil). */
    fun setCatchUpEnabled(enabled: Boolean) {
        prefs.partyCatchUpEnabled = enabled
    }

    /** Salva o relay TURN do usuário (vazio = sem relay). Vale na próxima conexão. */
    fun saveTurn(url: String, user: String, pass: String) {
        prefs.partyTurnUrl = url.trim()
        prefs.partyTurnUsername = user.trim()
        prefs.partyTurnPassword = pass
    }

    // ─── Player ────────────────────────────────────────────────────────────

    /** Liga o player da sala (a tela do player chama ao abrir com sessão ativa). */
    fun bindPlayer(player: PartyPlayer, title: String, tmdbId: Int) {
        this.player = player
        _partyTitle.value = title
        this.tmdbId = tmdbId
        if (isHost) broadcast(lobbyMessage(_lobbyPhase.value))
    }

    /** Desliga o player da sala (a tela do player chama ao fechar). */
    fun unbindPlayer() {
        player = null
        catchingUp = false
        catchUpFromStart = false
    }

    /** Sai da sala (fecha a conexão E a sessão, como no Lote 10). */
    fun leaveParty() {
        val msg = baseMessage(PartyMessageType.PEER_LEAVE)
        runCatching { callManager.broadcastData(PartyMessage.encode(msg)) }
        stopConnection()
        store.leaveSession()
    }

    // ─── Internos: conexão ─────────────────────────────────────────────────

    private fun fail(reason: String) {
        _failureReason.value = reason
        _connectionState.value = PartySyncState.FAILED
    }

    fun clearFailure() {
        _failureReason.value = null
        if (_connectionState.value == PartySyncState.FAILED) _connectionState.value = PartySyncState.IDLE
    }

    private fun stopConnection() {
        pollJob?.cancel()
        playerJob?.cancel()
        speakingJob?.cancel()
        speakingDecayJob?.cancel()
        clockJob?.cancel()
        speakingMeter.stop()
        callManager.closeAll()
        runCatching { lanServer?.stop() }
        lanServer = null
        lanBaseUrl = null
        lanCode = ""
        manualMode = false
        hostPeerId = null
        sinceSeq = 0L
        pollFailures = 0
        catchingUp = false
        catchUpFromStart = false
        lastHostState = null
        lateJoinShown = false
        pendingPings.clear()
        pendingIce.clear()
        seenMessages.clear()
        clock.reset()
        _peers.value = emptyList()
        _chat.value = emptyList()
        _reactions.value = emptyList()
        _pauseRequests.value = emptyList()
        _lobbyPhase.value = PartyLobbyPhase.LOBBY
        _lanUrl.value = null
        _lateJoin.value = null
        _manualOffer.value = null
        _manualAnswer.value = null
        _syncInfo.value = PartySyncInfo()
        if (_connectionState.value != PartySyncState.IDLE) _connectionState.value = PartySyncState.IDLE
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val base = lanBaseUrl
                if (base == null) break
                val result = PartyLanClient.pollSignals(base, lanCode, peerId, sinceSeq)
                result.onSuccess { envs ->
                    pollFailures = 0
                    envs.forEach { env ->
                        sinceSeq = maxOf(sinceSeq, env.seq)
                        handleSignal(env)
                    }
                }.onFailure {
                    pollFailures++
                    if (!isHost && pollFailures >= MAX_POLL_FAILURES) {
                        fail("Perdi o anfitrião (a rede caiu ou a sala fechou).")
                        stopConnection()
                    }
                }
                delay(SIGNAL_POLL_MS)
            }
        }
    }

    private fun handleSignal(env: SignalEnvelope) {
        when (env.kind) {
            "hello" -> {
                upsertPeer(env.from, env.payload, isHost = false, connected = false)
                if (isHost) broadcastPeerJoin(env.from, env.payload)
            }
            "offer" -> {
                val from = env.from
                upsertPeer(from, "", isHost = false, connected = false)
                if (callManager.iceState(from) == null) callManager.createPeer(from, withDataChannel = false)
                val offer = SessionDescription(SessionDescription.Type.OFFER, env.payload)
                callManager.createAnswer(from, offer) { answer ->
                    if (answer == null) return@createAnswer
                    scope.launch {
                        val base = lanBaseUrl ?: return@launch
                        PartyLanClient.postSignal(
                            base, lanCode,
                            SignalEnvelope(from = peerId, to = from, kind = "answer", payload = answer.description),
                        )
                    }
                }
                if (isHost) broadcastPeerJoin(from, "")
            }
            "answer" -> {
                val from = env.from
                val answer = SessionDescription(SessionDescription.Type.ANSWER, env.payload)
                callManager.setRemoteDescription(from, answer) { ok ->
                    if (ok) flushPendingIce(from)
                }
            }
            "ice" -> {
                val from = env.from
                val candidate = runCatching {
                    val o = JSONObject(env.payload)
                    IceCandidate(
                        o.optString("mid").takeIf { it.isNotBlank() },
                        o.optInt("idx", 0),
                        o.optString("sdp"),
                    )
                }.getOrNull() ?: return
                if (candidate.sdp.isBlank()) return
                // Candidato antes da descrição remota: guarda p/ depois.
                val pc = callManager.iceState(from)
                if (pc == null) {
                    pendingIce.getOrPut(from) { ArrayList() }.add(candidate)
                } else {
                    val added = callManager.addIceCandidate(from, candidate)
                    if (!added) pendingIce.getOrPut(from) { ArrayList() }.add(candidate)
                }
            }
        }
    }

    private fun flushPendingIce(peerId: String) {
        val list = pendingIce.remove(peerId) ?: return
        list.forEach { callManager.addIceCandidate(peerId, it) }
    }

    private fun onPeerConnected(peerId: String) {
        upsertPeer(peerId, "", isHost = peerId == hostPeerId, connected = true)
        sendHelloTo(peerId)
        if (_connectionState.value == PartySyncState.LOBBY ||
            _connectionState.value == PartySyncState.CONNECTING
        ) {
            _connectionState.value = PartySyncState.CONNECTED
        }
        if (!isHost && peerId == hostPeerId) startClockSync()
        updateSyncInfo()
    }

    private fun onPeerFailed(peerId: String) {
        if (!isHost && peerId == hostPeerId) {
            val hasTurn = prefs.partyTurnUrl.isNotBlank()
            fail(
                if (hasTurn) "A conexão direta com o anfitrião falhou (mesmo com o relay configurado)."
                else "A conexão direta com o anfitrião falhou e não há relay (TURN) configurado — " +
                    "a sala só funciona na mesma rede Wi-Fi ou com um TURN seu nas Configurações.",
            )
            return
        }
        upsertPeer(peerId, "", isHost = false, connected = false)
    }

    private fun startClockSync() {
        clockJob?.cancel()
        clockJob = scope.launch {
            repeat(CLOCK_PINGS_ON_CONNECT) {
                sendPing()
                delay(1_000)
            }
            while (isActive) {
                delay(CLOCK_REPING_MS)
                sendPing()
            }
        }
    }

    private fun sendPing() {
        val host = hostPeerId ?: return
        val id = ++pingCounter
        pendingPings[id] = System.currentTimeMillis()
        val msg = baseMessage(PartyMessageType.PING).copy(pingId = id, sentAtMs = System.currentTimeMillis())
        callManager.sendData(host, PartyMessage.encode(msg))
    }

    // ─── Internos: mensagens do canal de dados ─────────────────────────────

    private fun handleDataMessage(fromPeerId: String, bytes: ByteArray) {
        val msg = PartyMessage.decode(bytes) ?: return
        val key = "${msg.from}:${msg.seq}"
        if (!seenMessages.add(key)) return
        if (seenMessages.size > 400) seenMessages.clear()
        when (msg.type) {
            PartyMessageType.HELLO -> {
                upsertPeer(msg.from, msg.fromName, isHost = msg.isHost, connected = true)
                if (msg.from != peerId) updatePeerFlags(msg.from, msg.voiceOn, msg.cameraOn)
            }
            PartyMessageType.PING -> {
                if (isHost) {
                    val pong = baseMessage(PartyMessageType.PONG)
                        .copy(pingId = msg.pingId, remoteAtMs = System.currentTimeMillis())
                    callManager.sendData(msg.from, PartyMessage.encode(pong))
                }
            }
            PartyMessageType.PONG -> {
                val t0 = pendingPings.remove(msg.pingId) ?: return
                val sample = clock.recordPingPong(t0, msg.remoteAtMs, System.currentTimeMillis())
                if (sample != null) updateSyncInfo()
            }
            PartyMessageType.STATE -> {
                if (isHost) return // o anfitrião não aceita STATE de convidado
                if (msg.from != hostPeerId) return
                lastHostState = msg
                maybeOfferLateJoin(msg)
            }
            PartyMessageType.CHAT -> {
                addChat(msg)
                if (isHost) relayExcept(msg, msg.from)
            }
            PartyMessageType.REACTION -> {
                addReaction(msg)
                if (isHost) relayExcept(msg, msg.from)
            }
            PartyMessageType.SPEAKING -> {
                updatePeerSpeaking(msg.from, msg.level)
                if (isHost) relayExcept(msg, msg.from)
            }
            PartyMessageType.PAUSE_REQUEST -> {
                if (!isHost) return
                _pauseRequests.update { list ->
                    val req = PartyPauseRequest(
                        id = msg.requestId, peerId = msg.from, name = msg.fromName,
                        reason = msg.reason, atMs = System.currentTimeMillis(),
                    )
                    if (list.any { it.id == req.id }) list else (list + req).takeLast(5)
                }
            }
            PartyMessageType.PAUSE_REPLY -> {
                if (isHost) return
                addSystemChat(
                    if (msg.approved) "O anfitrião aprovou a pausa."
                    else "O anfitrião não aprovou a pausa.",
                )
            }
            PartyMessageType.PEER_JOIN -> {
                val newPeerId = msg.text
                if (newPeerId.isBlank() || newPeerId == peerId) return
                upsertPeer(newPeerId, msg.fromName, isHost = false, connected = false)
                // Malha (só na rede local): oferece p/ o recém-chegado (só mídia).
                if (!manualMode && lanBaseUrl != null && callManager.iceState(newPeerId) == null) {
                    callManager.createPeer(newPeerId, withDataChannel = false)
                    callManager.createOffer(newPeerId) { sdp ->
                        if (sdp == null) return@createOffer
                        scope.launch {
                            val base = lanBaseUrl ?: return@launch
                            PartyLanClient.postSignal(
                                base, lanCode,
                                SignalEnvelope(from = peerId, to = newPeerId, kind = "offer", payload = sdp.description),
                            )
                        }
                    }
                }
            }
            PartyMessageType.PEER_LEAVE -> {
                removePeer(msg.from)
                callManager.closePeer(msg.from)
            }
            PartyMessageType.LOBBY -> {
                _lobbyPhase.value = msg.phase
                if (msg.title.isNotBlank()) _partyTitle.value = msg.title
                if (msg.phase == PartyLobbyPhase.POST_FILM) {
                    addSystemChat("O filme acabou — a conversa pós-filme está aberta.")
                }
            }
            PartyMessageType.TITLE -> {
                if (msg.title.isNotBlank()) _partyTitle.value = msg.title
                tmdbId = msg.tmdbId
            }
        }
    }

    private fun maybeOfferLateJoin(state: PartyMessage) {
        if (lateJoinShown) return
        if (state.positionMs <= LATE_JOIN_AFTER_MS) return
        val p = player ?: return
        if (p.positionMs() >= 5_000L) return
        lateJoinShown = true
        _lateJoin.value = PartyLateJoinOffer(hostPositionMs = state.positionMs, title = state.title)
    }

    // ─── Internos: player (sync) ───────────────────────────────────────────

    private fun startPlayerLoop() {
        playerJob?.cancel()
        playerJob = scope.launch {
            var lastPlaying: Boolean? = null
            var lastPos = -1L
            var lastBroadcastAt = 0L
            var lastSyncInfoAt = 0L
            while (isActive) {
                val p = player
                if (p != null) {
                    val now = System.currentTimeMillis()
                    val pos = runCatching { p.positionMs() }.getOrDefault(0L)
                    val playing = runCatching { p.isPlaying() }.getOrDefault(false)
                    if (isHost) {
                        val posJumped = lastPos < 0 || abs(pos - lastPos) > 1_000L
                        if (playing != lastPlaying || posJumped ||
                            (playing && now - lastBroadcastAt >= STATE_BROADCAST_MS)
                        ) {
                            broadcastState(pos, playing)
                            lastBroadcastAt = now
                        }
                        val dur = runCatching { p.durationMs() }.getOrDefault(0L)
                        if (dur > 0 && pos >= dur - 2_000L && !playing &&
                            _lobbyPhase.value == PartyLobbyPhase.PLAYING
                        ) {
                            endFilm()
                        }
                    } else {
                        val state = lastHostState
                        if (state != null && _lobbyPhase.value == PartyLobbyPhase.PLAYING) {
                            val decision = PartySyncEngine.decide(
                                localPositionMs = pos,
                                localPlaying = playing,
                                remotePositionMs = state.positionMs,
                                remotePlaying = state.playing,
                                remoteSentAtMs = state.sentAtMs,
                                clockOffsetMs = clock.offsetMs,
                                nowMs = now,
                                catchingUp = catchingUp,
                            )
                            applyDecision(p, decision)
                            if (now - lastSyncInfoAt >= 500L) {
                                _syncInfo.update {
                                    it.copy(
                                        clockOffsetMs = clock.offsetMs,
                                        rttMs = clock.rttMs,
                                        catchingUp = catchingUp,
                                        lastDecision = describeDecision(decision),
                                        peersConnected = _peers.value.count { peer -> peer.connected },
                                    )
                                }
                                lastSyncInfoAt = now
                            }
                        }
                    }
                    lastPlaying = playing
                    lastPos = pos
                }
                delay(250)
            }
        }
    }

    private fun applyDecision(p: PartyPlayer, decision: PartySyncDecision) {
        if (catchUpFromStart) {
            // Alcançando a sala do início: só o play/pause do anfitrião vale;
            // o seek do motor é ignorado (aqui quem manda é o 1,5×).
            decision.setPlaying?.let { p.setPlaying(it) }
            if (decision.setRate == 1f) {
                p.setSpeed(p.normalSpeed())
                catchingUp = false
                catchUpFromStart = false
            }
            return
        }
        if (!prefs.partyCatchUpEnabled && decision.setRate == PartySyncEngine.CATCH_UP_RATE) {
            // Catch-up desligado no perfil: pula em vez de acelerar.
            decision.seekToMs?.let { p.seekTo(it) }
            if (decision.seekToMs == null) {
                val state = lastHostState ?: return
                p.seekTo(state.positionMs)
            }
            decision.setPlaying?.let { p.setPlaying(it) }
            return
        }
        decision.setPlaying?.let { p.setPlaying(it) }
        decision.seekToMs?.let { p.seekTo(it) }
        decision.setRate?.let { rate ->
            p.setSpeed(rate)
            catchingUp = rate == PartySyncEngine.CATCH_UP_RATE
        }
    }

    private fun describeDecision(d: PartySyncDecision): String = when {
        d.isNone -> "sincronizado"
        d.seekToMs != null -> "pulando p/ o ponto da sala"
        d.setRate == PartySyncEngine.CATCH_UP_RATE -> "alcançando a sala a 1,5×"
        d.setPlaying == true -> "tocando junto"
        d.setPlaying == false -> "pausado junto"
        else -> "sincronizando"
    }

    private fun broadcastState(pos: Long, playing: Boolean) {
        val msg = baseMessage(PartyMessageType.STATE).copy(
            positionMs = pos,
            playing = playing,
            rate = 1f,
            title = _partyTitle.value,
            tmdbId = tmdbId,
        )
        callManager.broadcastData(PartyMessage.encode(msg))
    }

    private fun broadcastStateNow() {
        val p = player ?: return
        broadcastState(
            runCatching { p.positionMs() }.getOrDefault(0L),
            runCatching { p.isPlaying() }.getOrDefault(false),
        )
    }

    private fun broadcast(msg: PartyMessage) {
        callManager.broadcastData(PartyMessage.encode(msg))
    }

    private fun sendToHostOrBroadcast(msg: PartyMessage) {
        if (isHost) {
            broadcast(msg)
        } else {
            val host = hostPeerId ?: return
            callManager.sendData(host, PartyMessage.encode(msg))
        }
    }

    private fun relayExcept(msg: PartyMessage, exceptPeerId: String) {
        val bytes = PartyMessage.encode(msg)
        _peers.value.forEach { peer ->
            if (peer.peerId != exceptPeerId && peer.peerId != this.peerId && peer.connected) {
                callManager.sendData(peer.peerId, bytes)
            }
        }
    }

    private fun lobbyMessage(phase: PartyLobbyPhase): PartyMessage =
        baseMessage(PartyMessageType.LOBBY).copy(
            phase = phase,
            title = _partyTitle.value,
            tmdbId = tmdbId,
        )

    private fun broadcastHello() {
        val msg = baseMessage(PartyMessageType.HELLO)
            .copy(isHost = isHost, voiceOn = prefs.partyVoiceEnabled, cameraOn = prefs.partyCameraEnabled)
        broadcast(msg)
    }

    private fun sendHelloTo(peerId: String) {
        val msg = baseMessage(PartyMessageType.HELLO)
            .copy(isHost = isHost, voiceOn = prefs.partyVoiceEnabled, cameraOn = prefs.partyCameraEnabled)
        callManager.sendData(peerId, PartyMessage.encode(msg))
    }

    private fun broadcastPeerJoin(newPeerId: String, name: String) {
        val msg = baseMessage(PartyMessageType.PEER_JOIN).copy(text = newPeerId, fromName = name)
        broadcast(msg)
    }

    // ─── Internos: membros, chat, voz ──────────────────────────────────────

    private fun baseMessage(type: PartyMessageType): PartyMessage = PartyMessage(
        type = type,
        from = peerId,
        fromName = store.localName,
        seq = msgSeq(),
        sentAtMs = System.currentTimeMillis(),
    )

    private fun msgSeq(): Long = ++seq

    private fun upsertSelfPeer() {
        val self = PartyPeer(
            peerId = peerId,
            name = store.localName,
            isHost = isHost,
            isSelf = true,
            voiceOn = prefs.partyVoiceEnabled,
            cameraOn = prefs.partyCameraEnabled,
            connected = true,
        )
        _peers.update { list -> listOf(self) + list.filterNot { it.peerId == peerId } }
    }

    private fun upsertPeer(peerId: String, name: String, isHost: Boolean, connected: Boolean) {
        if (peerId.isBlank()) return
        _peers.update { list ->
            val existing = list.firstOrNull { it.peerId == peerId }
            val updated = if (existing != null) {
                existing.copy(
                    name = name.ifBlank { existing.name },
                    isHost = isHost || existing.isHost,
                    connected = connected || existing.connected,
                )
            } else {
                PartyPeer(peerId = peerId, name = name.ifBlank { "Convidado" }, isHost = isHost, connected = connected)
            }
            listOf(updated) + list.filterNot { it.peerId == peerId }
        }
    }

    private fun updatePeerFlags(peerId: String, voiceOn: Boolean, cameraOn: Boolean) {
        _peers.update { list ->
            list.map { if (it.peerId == peerId) it.copy(voiceOn = voiceOn, cameraOn = cameraOn) else it }
        }
    }

    private fun updatePeerSpeaking(peerId: String, level: Int) {
        lastSpeakingAt[peerId] = System.currentTimeMillis()
        _peers.update { list ->
            list.map { if (it.peerId == peerId) it.copy(speakingLevel = level.coerceIn(0, 100)) else it }
        }
    }

    private fun removePeer(peerId: String) {
        _peers.update { list -> list.filterNot { it.peerId == peerId } }
        lastSpeakingAt.remove(peerId)
    }

    private fun addChat(msg: PartyMessage) {
        val text = msg.text.trim()
        if (text.isBlank()) return
        val item = PartyChatMessage(
            id = "${msg.from}:${msg.seq}",
            peerId = msg.from,
            name = msg.fromName.ifBlank { "Convidado" },
            text = text.take(500),
            atMs = msg.sentAtMs,
        )
        _chat.update { list -> (list + item).takeLast(100) }
    }

    private fun addSystemChat(text: String) {
        val item = PartyChatMessage(
            id = "sys:${System.currentTimeMillis()}",
            peerId = "",
            name = "Sala",
            text = text,
            atMs = System.currentTimeMillis(),
        )
        _chat.update { list -> (list + item).takeLast(100) }
    }

    private fun addReaction(msg: PartyMessage) {
        if (msg.emoji !in PARTY_REACTIONS) return
        val item = PartyReactionEvent(
            id = "${msg.from}:${msg.seq}",
            peerId = msg.from,
            name = msg.fromName.ifBlank { "Convidado" },
            emoji = msg.emoji,
            atMs = msg.sentAtMs,
        )
        _reactions.update { list -> (list + item).takeLast(20) }
    }

    private fun startSpeakingMeterIfAllowed() {
        speakingJob?.cancel()
        if (!prefs.partyVoiceEnabled || !hasMicPermission()) return
        if (!speakingMeter.start()) return
        speakingJob = scope.launch {
            var lastSentAt = 0L
            var lastSentSpeaking = false
            speakingMeter.level.collect { level ->
                // O próprio anel usa o nível direto; os outros recebem 2×/s.
                _peers.update { list ->
                    list.map { if (it.peerId == peerId) it.copy(speakingLevel = level) else it }
                }
                val now = System.currentTimeMillis()
                val speaking = level >= PARTY_SPEAKING_THRESHOLD
                if (now - lastSentAt >= 500L || speaking != lastSentSpeaking) {
                    lastSentAt = now
                    lastSentSpeaking = speaking
                    val msg = baseMessage(PartyMessageType.SPEAKING).copy(level = level)
                    sendToHostOrBroadcast(msg)
                }
            }
        }
    }

    private fun startSpeakingDecay() {
        speakingDecayJob?.cancel()
        speakingDecayJob = scope.launch {
            while (isActive) {
                delay(2_000)
                val now = System.currentTimeMillis()
                val stale = lastSpeakingAt.filterValues { now - it > 2_000L }.keys
                if (stale.isNotEmpty()) {
                    _peers.update { list ->
                        list.map { if (it.peerId in stale) it.copy(speakingLevel = 0) else it }
                    }
                    stale.forEach { lastSpeakingAt.remove(it) }
                }
            }
        }
    }

    private fun updateSyncInfo() {
        _syncInfo.update {
            it.copy(
                clockOffsetMs = clock.offsetMs,
                rttMs = clock.rttMs,
                catchingUp = catchingUp,
                peersConnected = _peers.value.count { peer -> peer.connected },
            )
        }
    }
}
