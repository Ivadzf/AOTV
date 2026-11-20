package com.aotv.app.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.tim.basevpn.connection.VpnServiceConnection
import com.tim.basevpn.state.ConnectionState as TimConnectionState
import com.tim.openvpn.configuration.OpenVPNConfig
import com.tim.openvpn.service.OpenVPNService
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ligar automático da VPN (Lote VPN, v1.40.0) — ponto de entrada Hilt p/
 * o [VpnAutoConnect] rodar fora de um componente Hilt (ver
 * `ContinuousSync`, mesmo padrão).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VpnAutoConnectEntryPoint {
    fun vpnStore(): VpnProfileStore
}

/**
 * Ligar automático ao abrir o app (Lote VPN, v1.40.0). Chamado pelo
 * `AotvApplication.onCreate` (sem travar a abertura: roda em 2º plano).
 * Só tenta quando o toggle está ligado E há perfil ativo. Se o sistema
 * ainda não autorizou a VPN, o `VpnService.prepare()` devolve o Intent
 * de consentimento — sem Activity p/ lançar na abertura, a tentativa
 * fica pendente e a tela da VPN mostra o botão Conectar (o usuário
 * autoriza lá, com um toque).
 */
object VpnAutoConnect {
    fun maybeConnectOnOpen(appContext: Context) {
        runCatching {
            val entry = EntryPointAccessors.fromApplication(
                appContext.applicationContext, VpnAutoConnectEntryPoint::class.java,
            )
            entry.vpnStore().tryAutoConnect()
        }
    }
}

/**
 * Conexão VPN de verdade (Lote VPN, v1.40.0) — orquestra os dois motores.
 *
 * Os motores trazem o PRÓPRIO `VpnService` declarado no manifesto do AAR
 * (merge automático; APIs conferidas no artefato 1.0.20260102 / 1.1.3
 * com `javap` antes de escrever este arquivo):
 * - WireGuard: [GoBackend] + [Tunnel]; o backend sobe o túnel pelo
 *   `GoBackend$VpnService` dele e aplica `setBlocking(true)` no Builder
 *   (kill switch enquanto conectado) e `addAllowedApplication` p/ o
 *   escopo APP. O config vai inteiro via [Config.parse] do texto bruto
 *   do perfil ([VpnProfile.rawConfig]) — Address/DNS/AllowedIPs/
 *   Endpoint/PublicKey/PresharedKey/PersistentKeepalive.
 * - OpenVPN: `OpenVPNService` (tim06, processo `:openvpn`, foreground
 *   service `specialUse` com notificação própria); o config bruto vai
 *   pelo extra `CONFIGURATION_KEY` de
 *   `OpenVPNService.Companion.startService` (o `configuration` do
 *   [OpenVPNConfig] — sem passar pelo `OpenVPNConfigParser` da lib,
 *   que quebra sem o bloco `<tls-crypt>`). O estado chega pelo
 *   [VpnServiceConnection] (callback de [TimConnectionState]) + um
 *   watchdog de 25 s com `isConnected()` caso o callback não dispare.
 *
 * Consentimento: `VpnService.prepare()` — se devolver um Intent, a UI
 * lança a activity de consentimento do sistema e só conecta depois da
 * autorização; sem isso o `setState`/startService falha.
 *
 * Credenciais OpenVPN (`auth-user-pass`): o serviço tim06 1.1.3 não tem
 * canal p/ usuário/senha — o motor ovpn3 aceita `auth-user-pass <arquivo>`
 * (usuário na linha 1, senha na linha 2). O manager grava as credenciais
 * guardadas num arquivo privado (`filesDir/vpn-creds-<id>.txt`, mesmo UID
 * do processo `:openvpn`) e reescreve a diretiva p/ apontar p/ ele; o
 * arquivo é apagado ao desconectar. Nunca vai p/ log.
 *
 * Cipher OpenVPN3: o core rejeita cifra CBC no canal de dados (ex.:
 * `AES-128-CBC` → loop de reconexão). O manager remove as diretivas
 * `cipher`/`data-ciphers`/`keysize`/`connect-retry` do texto e anexa a
 * lista moderna (`CHACHA20-POLY1305:AES-256-GCM:AES-128-GCM`).
 *
 * Kill switch: no WireGuard o backend aplica `setBlocking(true)` (o
 * tráfego fora do túnel é bloqueado enquanto conectado); no OpenVPN o
 * serviço tim06 não expõe `setBlocking` — o bloqueio total depende do
 * "Sempre ligada + Bloquear conexões sem VPN" nas configurações do
 * sistema, que a tela abre (o app não pode ligar sozinho). A UI deixa
 * isso explícito.
 *
 * NADA aqui é provado na VM (sem runtime Android): o túnel só valida no
 * celular do Davi, com um perfil real .ovpn/.conf.
 */
@Singleton
class VpnConnectionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(VpnConnectionState.DISCONNECTED)
    /** Estado da conexão, que avisa quem observa. */
    val stateFlow: StateFlow<VpnConnectionState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** Motivo legível da última falha (null = sem falha recente). */
    val lastErrorFlow: StateFlow<String?> = _lastError.asStateFlow()

    private val _connectedEndpoint = MutableStateFlow("")
    /** Servidor do perfil conectado ("" = sem túnel). */
    val connectedEndpointFlow: StateFlow<String> = _connectedEndpoint.asStateFlow()

    private val _pendingConsent = MutableStateFlow<Intent?>(null)
    /** Intent de consentimento do `VpnService.prepare()` aguardando a UI (null = nenhum). */
    val pendingConsentFlow: StateFlow<Intent?> = _pendingConsent.asStateFlow()

    private var goBackend: GoBackend? = null
    private var wgTunnel: Tunnel? = null
    private var ovpnConnection: VpnServiceConnection? = null
    private var ovpnWatchdog: Job? = null
    private var activeProfileId: String? = null
    private var activeType: VpnType? = null
    private var credsFile: File? = null

    /**
     * Conecta o perfil. Síncrono só até despachar: [VpnConnectOutcome.Started]
     * significa que a tentativa está rodando (CONNECTING → CONNECTED/FAILED
     * chega pelo [stateFlow]). [VpnConnectOutcome.NeedsConsent] carrega o
     * Intent do sistema — a UI lança e chama [connect] de novo.
     */
    fun connect(profile: VpnProfile): VpnConnectOutcome {
        if (_state.value == VpnConnectionState.CONNECTING ||
            _state.value == VpnConnectionState.CONNECTED
        ) {
            if (activeProfileId == profile.id) return VpnConnectOutcome.Started
        }
        if (profile.type == VpnType.OPENVPN && profile.needsCredentials &&
            (profile.secrets.username.isBlank() || profile.secrets.password.isBlank())
        ) {
            return VpnConnectOutcome.NeedsCredentials
        }
        if (profile.rawConfig.isBlank()) {
            return VpnConnectOutcome.Failed(
                "Este perfil foi importado antes da v1.40.0 e não guarda o arquivo " +
                    "completo — importe o .conf/.ovpn de novo para conectar.",
            )
        }
        val prepareIntent = runCatching { VpnService.prepare(appContext) }.getOrNull()
        if (prepareIntent != null) {
            _pendingConsent.value = prepareIntent
            activeProfileId = profile.id
            return VpnConnectOutcome.NeedsConsent(prepareIntent)
        }
        _pendingConsent.value = null
        _lastError.value = null
        _state.value = VpnConnectionState.CONNECTING
        _connectedEndpoint.value = profile.endpoint
        val switching = activeProfileId != null && activeProfileId != profile.id
        val previousType = activeType
        activeProfileId = profile.id
        activeType = profile.type
        scope.launch {
            // Troca de perfil com túnel ativo: derruba o antigo ANTES de
            // subir o novo (na mesma coroutine — o teardown é assíncrono e
            // um DOWN tardio derrubaria o túnel novo).
            if (switching) teardownCurrent(previousType)
            when (profile.type) {
                VpnType.WIREGUARD -> startWireGuard(profile)
                VpnType.OPENVPN -> startOpenVpn(profile)
            }
        }
        return VpnConnectOutcome.Started
    }

    /** Desconecta o túnel (sem efeito se já desconectado). */
    fun disconnect() {
        val type = activeType
        val wasActive = _state.value == VpnConnectionState.CONNECTED ||
            _state.value == VpnConnectionState.CONNECTING
        if (type == null && !wasActive) {
            _state.value = VpnConnectionState.DISCONNECTED
            _connectedEndpoint.value = ""
            return
        }
        _state.value = VpnConnectionState.DISCONNECTING
        scope.launch {
            teardownCurrent(type)
            activeProfileId = null
            activeType = null
            _connectedEndpoint.value = ""
            _state.value = VpnConnectionState.DISCONNECTED
        }
    }

    /**
     * Derruba o túnel atual e limpa os rastros (conexão OpenVPN,
     * watchdog, arquivo de credenciais, referência do túnel WireGuard).
     * Roda na coroutine do chamador — nunca em paralelo com um start.
     * [type] é o tipo do túnel a derrubar (o campo [activeType] já pode
     * ser o do próximo perfil, na troca).
     */
    private fun teardownCurrent(type: VpnType?) {
        ovpnWatchdog?.cancel()
        ovpnWatchdog = null
        runCatching {
            when (type) {
                VpnType.WIREGUARD -> {
                    val backend = goBackend
                    val tunnel = wgTunnel
                    if (backend != null && tunnel != null) {
                        backend.setState(tunnel, Tunnel.State.DOWN, null)
                    }
                }
                VpnType.OPENVPN -> {
                    val connection = ovpnConnection
                    ovpnConnection = null
                    runCatching { connection?.stop() }
                    runCatching { OpenVPNService.stopService(appContext) }
                    runCatching { connection?.stopServiceIfNeed(true) }
                }
                null -> Unit
            }
        }
        deleteCredsFile()
        wgTunnel = null
    }

    /** Limpa o Intent de consentimento pendente (a UI já lançou ou desistiu). */
    fun clearPendingConsent() {
        _pendingConsent.value = null
    }

    /** WireGuard: parse do config bruto + `GoBackend.setState(UP)`. */
    private fun startWireGuard(profile: VpnProfile) {
        val config = runCatching {
            Config.parse(ByteArrayInputStream(applyWgScope(profile).toByteArray(Charsets.UTF_8)))
        }.getOrElse { e ->
            fail("Config do WireGuard inválido: ${e.message ?: "erro de parse"}")
            return
        }
        val tunnel = object : Tunnel {
            override fun getName(): String = "aotv-vpn"
            override fun onStateChange(state: Tunnel.State) {
                when (state) {
                    Tunnel.State.UP -> {
                        _lastError.value = null
                        _connectedEndpoint.value = profile.endpoint
                        _state.value = VpnConnectionState.CONNECTED
                    }
                    Tunnel.State.DOWN -> {
                        if (_state.value != VpnConnectionState.DISCONNECTING &&
                            _state.value != VpnConnectionState.FAILED
                        ) {
                            _connectedEndpoint.value = ""
                            activeProfileId = null
                            activeType = null
                            _state.value = VpnConnectionState.DISCONNECTED
                        }
                    }
                    Tunnel.State.TOGGLE -> Unit
                }
            }
        }
        wgTunnel = tunnel
        val backend = goBackend ?: GoBackend(appContext).also { goBackend = it }
        runCatching {
            val result = backend.setState(tunnel, Tunnel.State.UP, config)
            if (result == Tunnel.State.UP) {
                _lastError.value = null
                _connectedEndpoint.value = profile.endpoint
                _state.value = VpnConnectionState.CONNECTED
            } else {
                fail("O motor WireGuard não subiu o túnel (estado: $result).")
            }
        }.onFailure { e ->
            if (e is BackendException) fail("WireGuard: ${e.message ?: "falha do motor"}")
            else fail("WireGuard: ${e.message ?: "falha ao subir o túnel"}")
        }
    }

    /** OpenVPN: config bruto via `OpenVPNService.startService` + escuta de estado. */
    private fun startOpenVpn(profile: VpnProfile) {
        val (host, port, proto) = parseOvpnRemote(profile.rawConfig)
        val configText = sanitizeOpenVpnConfig(profile)
        val type = if (proto == "tcp") "tcp-client" else "udp-client"
        // startService exige o array não-nulo (@NotNull no artefato 1.1.3):
        // vazio = sem allowlist (todo o aparelho); com o pacote = só o AOTV.
        val allowedApps: Array<String> = when (profile.scope) {
            VpnScope.APP -> arrayOf(appContext.packageName)
            VpnScope.DEVICE -> emptyArray()
        }
        val ovpnConfig = OpenVPNConfig(
            name = profile.name,
            host = host,
            port = port,
            type = type,
            cipher = "",
            auth = "",
            ca = "",
            key = "",
            cert = "",
            tlsCrypt = "",
            configuration = configText,
        )
        runCatching {
            OpenVPNService.startService(appContext, ovpnConfig, null, allowedApps)
        }.onFailure { e ->
            deleteCredsFile()
            fail("OpenVPN: ${e.message ?: "falha ao iniciar o serviço"}")
            return
        }
        val connection = object : VpnServiceConnection(
            appContext,
            OpenVPNService::class.java,
            { state: TimConnectionState -> onTimState(state, profile) },
            scope,
        ) {}
        ovpnConnection = connection
        runCatching { connection.attachListener() }
        // Watchdog: se o callback de estado não disparar, pergunta ao
        // serviço uma vez se já conectou (não inventa CONNECTED sem prova).
        ovpnWatchdog = scope.launch {
            delay(25_000L)
            if (_state.value == VpnConnectionState.CONNECTING && activeProfileId == profile.id) {
                val connected = runCatching { connection.isConnected() }.getOrDefault(false)
                if (connected) {
                    _lastError.value = null
                    _connectedEndpoint.value = profile.endpoint
                    _state.value = VpnConnectionState.CONNECTED
                }
            }
        }
    }

    /** Mapeia o estado do serviço tim06 p/ o estado do app. */
    private fun onTimState(state: TimConnectionState, profile: VpnProfile) {
        when (state) {
            TimConnectionState.CONNECTED -> {
                _lastError.value = null
                _connectedEndpoint.value = profile.endpoint
                _state.value = VpnConnectionState.CONNECTED
            }
            TimConnectionState.CONNECTING, TimConnectionState.READYFORCONNECT -> {
                if (_state.value == VpnConnectionState.DISCONNECTED) {
                    _state.value = VpnConnectionState.CONNECTING
                }
            }
            TimConnectionState.DISCONNECTING -> {
                _state.value = VpnConnectionState.DISCONNECTING
            }
            TimConnectionState.DISCONNECTED, TimConnectionState.IDLE -> {
                if (_state.value != VpnConnectionState.DISCONNECTING) {
                    _connectedEndpoint.value = ""
                    activeProfileId = null
                    activeType = null
                    _state.value = VpnConnectionState.DISCONNECTED
                }
            }
            TimConnectionState.PERMISSION_NOT_GRANTED -> {
                fail("O sistema não autorizou a VPN p/ este app.")
            }
        }
    }

    /** Marca a falha (sem nunca incluir valores do config) e limpa o túnel. */
    private fun fail(message: String) {
        ovpnWatchdog?.cancel()
        ovpnWatchdog = null
        deleteCredsFile()
        _lastError.value = message
        _connectedEndpoint.value = ""
        activeProfileId = null
        activeType = null
        wgTunnel = null
        _state.value = VpnConnectionState.FAILED
    }

    /**
     * Escopo WireGuard: no [VpnScope.APP], injeta
     * `IncludedApplications = com.aotv.app` na seção [Interface] (o
     * backend aplica `addAllowedApplication`); no [VpnScope.DEVICE] o
     * config vai como está (todo o aparelho).
     */
    internal fun applyWgScope(profile: VpnProfile): String {
        if (profile.scope != VpnScope.APP) return profile.rawConfig
        val lines = profile.rawConfig.lines()
        var inInterface = false
        var hasIncluded = false
        val out = ArrayList<String>(lines.size + 1)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (inInterface && !hasIncluded) {
                    out.add("IncludedApplications = ${appContext.packageName}")
                    hasIncluded = true
                }
                inInterface = trimmed.equals("[Interface]", ignoreCase = true)
            }
            if (inInterface && trimmed.startsWith("IncludedApplications", ignoreCase = true)) {
                hasIncluded = true
            }
            out.add(line)
        }
        if (inInterface && !hasIncluded) out.add("IncludedApplications = ${appContext.packageName}")
        return out.joinToString("\n")
    }

    /**
     * Extrai `remote <host> <porta> <proto>` do .ovpn (p/ o [OpenVPNConfig];
     * o texto bruto continua sendo o que o motor recebe). Padrões:
     * porta 1194, proto udp.
     */
    internal fun parseOvpnRemote(rawConfig: String): Triple<String, Int, String> {
        for (raw in rawConfig.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2 && parts[0].equals("remote", ignoreCase = true)) {
                val host = parts[1]
                val port = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 1194
                val proto = parts.getOrNull(3)?.lowercase()?.takeIf { it == "tcp" || it == "udp" } ?: "udp"
                return Triple(host, port, proto)
            }
        }
        return Triple("", 1194, "udp")
    }

    /**
     * Sanitiza o .ovpn p/ o motor OpenVPN3 (tim06 1.1.3): remove as
     * diretivas que o core rejeita ou que causam loop (`cipher`,
     * `data-ciphers`, `keysize`, `connect-retry`) e anexa a lista moderna
     * de cifras; reescreve `auth-user-pass` p/ o arquivo privado de
     * credenciais quando o perfil tem usuário/senha guardados. Todo o
     * resto (incl. blocos `<ca>`/`<cert>`/`<key>` inline) vai intacto.
     */
    internal fun sanitizeOpenVpnConfig(profile: VpnProfile): String {
        val credsPath = if (profile.needsCredentials &&
            profile.secrets.username.isNotBlank() && profile.secrets.password.isNotBlank()
        ) {
            writeCredsFile(profile)
        } else {
            null
        }
        val dropped = setOf("cipher", "data-ciphers", "keysize", "connect-retry")
        val out = ArrayList<String>()
        for (raw in profile.rawConfig.lines()) {
            val key = raw.trim().split(Regex("\\s+")).firstOrNull().orEmpty().lowercase()
            if (key in dropped) continue
            if (key == "auth-user-pass") {
                if (credsPath != null) out.add("auth-user-pass $credsPath")
                continue
            }
            out.add(raw)
        }
        out.add("cipher CHACHA20-POLY1305")
        out.add("data-ciphers CHACHA20-POLY1305:AES-256-GCM:AES-128-GCM")
        return out.joinToString("\n")
    }

    /** Grava usuário/senha num arquivo privado (apagado ao desconectar). */
    private fun writeCredsFile(profile: VpnProfile): String? {
        return runCatching {
            val file = File(appContext.filesDir, "vpn-creds-${profile.id}.txt")
            file.writeText("${profile.secrets.username}\n${profile.secrets.password}\n", Charsets.UTF_8)
            credsFile = file
            file.absolutePath
        }.getOrNull()
    }

    /** Apaga o arquivo de credenciais, se existir. */
    private fun deleteCredsFile() {
        val file = credsFile
        credsFile = null
        if (file != null) runCatching { file.delete() }
    }
}
