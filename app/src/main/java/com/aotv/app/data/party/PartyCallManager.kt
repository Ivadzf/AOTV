package com.aotv.app.data.party

import android.content.Context
import android.content.pm.PackageManager
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebRTC da Watch Party (Lote 19, v1.29.0) — voz, vídeo e canal de dados.
 *
 * TODA a API `org.webrtc` do app vive neste arquivo (e só aqui): se a
 * versão do WebRTC mudar, o estrago é local.
 *
 * Biblioteca: `io.github.webrtc-sdk:android:150.7871.04` (ver
 * app/build.gradle.kts) — o `org.webrtc:google-webrtc` antigo NÃO está
 * mais no Maven Central; o webrtc-sdk é o sucessor mantido, mesmo pacote
 * `org.webrtc.*`, BSD-3. O canal de dados é DTLS (criptografado pelo
 * próprio WebRTC); não há servidor no meio da mídia.
 *
 * Topologia: malha p/ mídia (cada par tem seu PeerConnection com áudio +
 * vídeo) e estrela p/ mensagens (só os canais com o anfitrião carregam
 * chat/estado — o anfitrião retransmite; ver [PartyRuntime]). No modo
 * manual (SDP copiado e colado, sem servidor), a mídia só existe entre
 * anfitrião e cada convidado — a troca manual por par não escala.
 *
 * ICE: STUN público sem credencial (`stun:stun.l.google.com:19302`, só
 * descoberta de NAT) + TURN OPCIONAL configurado pelo usuário nas
 * Configurações (nunca embutido — sem ele, P2P que falha por NAT
 * simétrico falha de verdade, ver [PartyRuntime]).
 */
@Singleton
class PartyCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PlatformPrefs,
) {
    companion object {
        /** Nome do canal de dados da sala (um por par anfitrião↔convidado). */
        const val DATA_CHANNEL_LABEL = "aotv-party"

        /** Id do stream de mídia local (o mesmo em todos os pares). */
        const val STREAM_ID = "aotv-party"

        /** Captura da câmera (a TV mostra o celular como câmera). */
        const val CAMERA_WIDTH = 640
        const val CAMERA_HEIGHT = 480
        const val CAMERA_FPS = 30
    }

    // ─── Callbacks p/ o runtime ────────────────────────────────────────────
    /** Chegou uma mensagem no canal de dados de um par. */
    var onDataMessage: ((peerId: String, bytes: ByteArray) -> Unit)? = null

    /** O vídeo remoto de um par chegou (null = saiu). */
    var onRemoteVideo: ((peerId: String, videoTrack: VideoTrack?) -> Unit)? = null

    /** Novo candidato ICE local (p/ enviar pela sinalização). */
    var onIceCandidate: ((peerId: String, candidate: IceCandidate) -> Unit)? = null

    /** Gathering de ICE completou (o blob manual pode ser montado). */
    var onGatheringComplete: ((peerId: String) -> Unit)? = null

    /** Canal de dados com um par abriu (a sala está ligada de verdade). */
    var onDataChannelOpen: ((peerId: String) -> Unit)? = null

    /** Estado ICE de um par mudou (CONNECTED / FAILED / ...). */
    var onPeerConnectionState: ((peerId: String, state: PeerConnection.IceConnectionState) -> Unit)? = null

    // ─── Estado ────────────────────────────────────────────────────────────
    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())

    /** Vídeo remoto por peerId (p/ os tiles da tela). */
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    /** Contexto EGL p/ os renderers de vídeo (null antes de ligar a fábrica). */
    val eglContext: EglBase.Context? get() = eglBase?.eglBaseContext

    /** Vídeo local (a própria câmera), p/ o tile "você". null sem câmera. */
    val localVideoTrack: VideoTrack? get() = videoTrack

    /** Último erro da câmera ("" = nenhum). Ex.: câmera em uso por outro app. */
    var lastCameraError: String? = null
        private set

    /** true quando este aparelho tem alguma câmera (frontal ou traseira). */
    fun hasCamera(): Boolean = runCatching {
        Camera2Enumerator(context).deviceNames.isNotEmpty() ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }.getOrDefault(false)

    /** Servidores ICE: STUN público + TURN do usuário (se configurado). */
    fun iceServers(): List<PeerConnection.IceServer> {
        val servers = ArrayList<PeerConnection.IceServer>()
        servers.add(PeerConnection.IceServer.builder(PARTY_STUN_URL).createIceServer())
        val turnUrl = prefs.partyTurnUrl.trim()
        if (turnUrl.isNotBlank()) {
            val builder = PeerConnection.IceServer.builder(turnUrl)
            val user = prefs.partyTurnUsername.trim()
            val pass = prefs.partyTurnPassword
            if (user.isNotBlank()) builder.setUsername(user)
            if (pass.isNotBlank()) builder.setPassword(pass)
            servers.add(builder.createIceServer())
        }
        return servers
    }

    // ─── Fábrica e mídia local ─────────────────────────────────────────────
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var factoryReady = false
    private var cameraCapturing = false
    private var cameraOn = false
    private var voiceOn = true

    private val cameraEvents = object : CameraVideoCapturer.CameraEventsHandler {
        override fun onCameraError(errorDescription: String) {
            lastCameraError = errorDescription
        }

        override fun onCameraDisconnected() {}
        override fun onCameraFreezed(freezeDescription: String) {}
        override fun onCameraOpening() {}
        override fun onFirstFrameAvailable() {}
        override fun onCameraClosed() {}
    }

    /** Inicializa a fábrica WebRTC (1x por processo). false quando falhou. */
    fun ensureFactory(): Boolean {
        if (factoryReady) return true
        return runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
            )
            val egl = EglBase.create() ?: return false
            eglBase = egl
            val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
            val adm = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
            val created = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            factory = created
            factoryReady = created != null
            factoryReady
        }.getOrDefault(false)
    }

    /**
     * Liga a mídia local (microfone sempre; câmera só se `cameraOn` e o
     * aparelho tiver câmera). false quando a fábrica não ligou.
     */
    fun ensureLocalMedia(voiceOn: Boolean, cameraOn: Boolean): Boolean {
        if (!ensureFactory()) return false
        this.voiceOn = voiceOn
        this.cameraOn = cameraOn
        val f = factory ?: return false
        if (audioTrack == null) {
            val source = f.createAudioSource(MediaConstraints())
            audioSource = source
            audioTrack = f.createAudioTrack("party-audio", source)
        }
        audioTrack?.setEnabled(voiceOn)
        if (cameraOn) startCamera() else stopCamera()
        return true
    }

    /** Liga/desliga o microfone (a faixa continua existindo, só muda). */
    fun setVoiceEnabled(enabled: Boolean) {
        voiceOn = enabled
        audioTrack?.setEnabled(enabled)
    }

    /**
     * Liga/desliga a câmera. Devolve false quando pediu p/ ligar e não deu
     * (sem câmera, sem permissão ou câmera ocupada — ver [lastCameraError]).
     * Desligar só congela a faixa (não destrói o capturador).
     */
    fun setCameraEnabled(enabled: Boolean): Boolean {
        cameraOn = enabled
        if (enabled) {
            if (!ensureFactory()) return false
            videoTrack?.setEnabled(true)
            return startCamera()
        }
        videoTrack?.setEnabled(false)
        stopCamera()
        return true
    }

    /** true quando a câmera está ligada e capturando agora. */
    val isCameraCapturing: Boolean get() = cameraCapturing

    private fun startCamera(): Boolean {
        if (cameraCapturing) return true
        if (!hasCamera()) return false
        val f = factory ?: return false
        val egl = eglBase ?: return false
        return runCatching {
            if (videoSource == null) {
                val enumerator = Camera2Enumerator(context)
                val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                    ?: enumerator.deviceNames.firstOrNull()
                    ?: return false
                val source = f.createVideoSource(false)
                videoSource = source
                val helper = SurfaceTextureHelper.create("PartyCapture", egl.eglBaseContext)
                surfaceTextureHelper = helper
                val cap = enumerator.createCapturer(front, cameraEvents) ?: return false
                capturer = cap
                cap.initialize(helper, context, source.capturerObserver)
                videoTrack = f.createVideoTrack("party-video", source)
            }
            videoTrack?.setEnabled(true)
            capturer?.startCapture(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FPS)
            cameraCapturing = true
            // A faixa de vídeo entra nos pares que já existem (e nos novos, em createPeer).
            val track = videoTrack ?: return true
            peers.values.forEach { conn ->
                if (conn.videoAdded) return@forEach
                runCatching { conn.pc.addTrack(track, listOf(STREAM_ID)) }
                conn.videoAdded = true
            }
            true
        }.getOrDefault(false)
    }

    private fun stopCamera() {
        if (!cameraCapturing) return
        runCatching { capturer?.stopCapture() }
        cameraCapturing = false
    }

    // ─── Pares (PeerConnection) ────────────────────────────────────────────
    private class PeerConn(
        val pc: PeerConnection,
        var dataChannel: DataChannel? = null,
        val candidates: MutableList<IceCandidate> = ArrayList(),
        var gatheringComplete: Boolean = false,
        var videoAdded: Boolean = false,
    )

    private val peers = LinkedHashMap<String, PeerConn>()

    /** Candidatos ICE locais já reunidos de um par (p/ o blob manual). */
    fun localCandidates(peerId: String): List<IceCandidate> =
        peers[peerId]?.candidates?.toList().orEmpty()

    /** true quando o gathering de ICE de um par completou. */
    fun isGatheringComplete(peerId: String): Boolean =
        peers[peerId]?.gatheringComplete == true

    /** Descrição local atual de um par (p/ o blob manual). */
    fun localDescription(peerId: String): SessionDescription? =
        peers[peerId]?.pc?.localDescription

    /** Estado ICE atual de um par (null sem par). */
    fun iceState(peerId: String): PeerConnection.IceConnectionState? =
        peers[peerId]?.pc?.iceConnectionState()

    /** Cria o PeerConnection com um par (adiciona as faixas locais). null sem fábrica. */
    fun createPeer(peerId: String, withDataChannel: Boolean): PeerConnection? {
        if (!ensureFactory()) return null
        val f = factory ?: return null
        val config = PeerConnection.RTCConfiguration(iceServers())
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        val pc = f.createPeerConnection(config, PeerObserver(peerId)) ?: return null
        val conn = PeerConn(pc = pc)
        peers[peerId] = conn
        audioTrack?.let { track ->
            runCatching { pc.addTrack(track, listOf(STREAM_ID)) }
        }
        val video = videoTrack
        if (video != null && cameraOn && cameraCapturing) {
            runCatching { pc.addTrack(video, listOf(STREAM_ID)) }
            conn.videoAdded = true
        }
        if (withDataChannel) {
            val init = DataChannel.Init()
            init.ordered = true
            val dc = pc.createDataChannel(DATA_CHANNEL_LABEL, init)
            conn.dataChannel = dc
            dc?.registerObserver(DataObserver(peerId))
        }
        return pc
    }

    /** Cria a oferta SDP de um par (e já define como descrição local). */
    fun createOffer(peerId: String, onCreated: (SessionDescription?) -> Unit) {
        val conn = peers[peerId] ?: run { onCreated(null); return }
        conn.pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                conn.pc.setLocalDescription(
                    sdpObserver(onSet = { onCreated(sdp) }, onFailure = { onCreated(null) }),
                    sdp,
                )
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) { onCreated(null) }
            override fun onSetFailure(error: String) { onCreated(null) }
        }, MediaConstraints())
    }

    /** Responde a uma oferta SDP (define a remota, cria a resposta, define a local). */
    fun createAnswer(peerId: String, offer: SessionDescription, onCreated: (SessionDescription?) -> Unit) {
        val conn = peers[peerId] ?: run { onCreated(null); return }
        conn.pc.setRemoteDescription(
            sdpObserver(
                onSet = {
                    conn.pc.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription) {
                            conn.pc.setLocalDescription(
                                sdpObserver(onSet = { onCreated(sdp) }, onFailure = { onCreated(null) }),
                                sdp,
                            )
                        }

                        override fun onSetSuccess() {}
                        override fun onCreateFailure(error: String) { onCreated(null) }
                        override fun onSetFailure(error: String) { onCreated(null) }
                    }, MediaConstraints())
                },
                onFailure = { onCreated(null) },
            ),
            offer,
        )
    }

    /** Define a descrição remota (oferta ou resposta) de um par. */
    fun setRemoteDescription(peerId: String, sdp: SessionDescription, onDone: (Boolean) -> Unit) {
        val conn = peers[peerId] ?: run { onDone(false); return }
        conn.pc.setRemoteDescription(
            sdpObserver(onSet = { onDone(true) }, onFailure = { onDone(false) }),
            sdp,
        )
    }

    /** Adiciona um candidato ICE remoto a um par (false sem par / PC fechado). */
    fun addIceCandidate(peerId: String, candidate: IceCandidate): Boolean {
        val conn = peers[peerId] ?: return false
        return runCatching { conn.pc.addIceCandidate(candidate) }.getOrDefault(false)
    }

    /** Envia bytes no canal de dados de um par. false sem canal aberto. */
    fun sendData(peerId: String, bytes: ByteArray): Boolean {
        val dc = peers[peerId]?.dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        return runCatching { dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false)) }.getOrDefault(false)
    }

    /** Envia bytes p/ todos os pares com canal aberto. */
    fun broadcastData(bytes: ByteArray) {
        peers.keys.toList().forEach { sendData(it, bytes) }
    }

    /** true quando o canal de dados com um par está aberto. */
    fun isDataChannelOpen(peerId: String): Boolean =
        peers[peerId]?.dataChannel?.state() == DataChannel.State.OPEN

    /** Fecha o par (canal + PeerConnection) e tira o vídeo remoto dele. */
    fun closePeer(peerId: String) {
        val conn = peers.remove(peerId) ?: return
        runCatching { conn.dataChannel?.dispose() }
        runCatching { conn.pc.close() }
        runCatching { conn.pc.dispose() }
        _remoteVideoTracks.update { it - peerId }
        onRemoteVideo?.invoke(peerId, null)
    }

    /** Fecha todos os pares (a mídia local continua p/ a próxima sala). */
    fun closeAll() {
        peers.keys.toList().forEach { closePeer(it) }
    }

    /** Desliga tudo (só ao sair do app — a fábrica não reabre no processo). */
    fun dispose() {
        closeAll()
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { eglBase?.release() }
        capturer = null
        videoSource = null
        audioSource = null
        videoTrack = null
        audioTrack = null
        surfaceTextureHelper = null
        factory = null
        eglBase = null
        factoryReady = false
        cameraCapturing = false
    }

    // ─── Observadores ──────────────────────────────────────────────────────
    private fun sdpObserver(
        onSet: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
    ): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() { onSet?.invoke() }
        override fun onCreateFailure(error: String) { onFailure?.invoke(error) }
        override fun onSetFailure(error: String) { onFailure?.invoke(error) }
    }

    private inner class PeerObserver(private val peerId: String) : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            onPeerConnectionState?.invoke(peerId, newState)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                peers[peerId]?.gatheringComplete = true
                onGatheringComplete?.invoke(peerId)
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            peers[peerId]?.candidates?.add(candidate)
            onIceCandidate?.invoke(peerId, candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {
            val track = stream.videoTracks.firstOrNull() ?: return
            _remoteVideoTracks.update { it + (peerId to track) }
            onRemoteVideo?.invoke(peerId, track)
        }

        override fun onRemoveStream(stream: MediaStream) {
            _remoteVideoTracks.update { it - peerId }
            onRemoteVideo?.invoke(peerId, null)
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            peers[peerId]?.dataChannel = dataChannel
            dataChannel.registerObserver(DataObserver(peerId))
        }

        override fun onRenegotiationNeeded() {}

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            val track = receiver.track()
            if (track is VideoTrack) {
                _remoteVideoTracks.update { it + (peerId to track) }
                onRemoteVideo?.invoke(peerId, track)
            }
            // Áudio remoto toca sozinho pelo WebRTC (não precisa de sink).
        }
    }

    private inner class DataObserver(private val peerId: String) : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}

        override fun onStateChange() {
            val dc = peers[peerId]?.dataChannel ?: return
            if (dc.state() == DataChannel.State.OPEN) onDataChannelOpen?.invoke(peerId)
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            onDataMessage?.invoke(peerId, bytes)
        }
    }
}
