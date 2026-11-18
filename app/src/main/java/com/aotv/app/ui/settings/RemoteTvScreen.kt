package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.aotv.app.data.local.ExternalPlayerStore
import com.aotv.app.data.local.GamepadAction
import com.aotv.app.data.local.GamepadCapture
import com.aotv.app.data.local.GamepadManager
import com.aotv.app.data.local.GamepadPresets
import com.aotv.app.data.remote.PlaybackHandoff
import com.aotv.app.data.remote.RemoteActionRegistry
import com.aotv.app.data.remote.RemoteControlClient
import com.aotv.app.data.remote.RemoteControlHost
import com.aotv.app.data.remote.RemoteControlServer
import com.aotv.app.data.remote.RemoteKeysPayload
import com.aotv.app.data.remote.RemoteLog
import com.aotv.app.data.remote.RemoteNav
import com.aotv.app.data.remote.RemotePlaybackState
import com.aotv.app.data.remote.SendToTv
import com.aotv.app.data.remote.SendToTvDiscovery
import com.aotv.app.data.remote.SendToTvSession
import com.aotv.app.data.remote.SendToTvSessionManager
import com.aotv.app.data.remote.TvDevice
import com.aotv.app.data.remote.TvTargetKind
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.SettingsGroup
import com.aotv.app.ui.common.SettingsRow
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Página "Controle remoto e TV" (Lote 22, v1.32.0) — os 6 itens do lote
 * que vivem nas Configurações:
 *
 * 1. Servidor do controle remoto (QR + PIN, log ao vivo, botões da tela
 *    atual, limpar cache) e cliente (enviar chaves de contas, links de
 *    addons e handoff p/ o outro aparelho).
 * 2. Send to TV: descobrir as TVs do Wi-Fi (Chromecast por DIAL, DLNA por
 *    SSDP/AVTransport, AirPlay listado mas SEM vídeo — FairPlay) e enviar
 *    o que está tocando, com Pause/Stop na notificação.
 * 3. Instalar addon por pareamento: a URL chega pelo servidor (item 1) e
 *    instala pelo KodiAddonStore — sem digitar URL no controle.
 * 4. Watch Next no launcher da Google TV (TvContract) — só na TV.
 * 5. Gamepads: presets por marca + mapeamento manual de botões.
 * 6. Player externo: padrão por tipo, por título e progresso ao voltar.
 *
 * O servidor vive só enquanto a página está aberta (mesmo desenho do
 * backup por Wi-Fi): ao sair da página ele desliga. Tráfego só na LAN.
 */
@Composable
fun RemoteTvScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = viewModel.prefs

    // ── Servidor (item 1) ─────────────────────────────────────────────
    // O backup servido é o instantâneo da hora em que o servidor ligou
    // (o host lê este estado a cada pedido — o lambda captura o estado,
    // não o valor da 1ª composição).
    var backupJson by remember { mutableStateOf("") }
    val host = remember(viewModel) {
        object : RemoteControlHost {
            override fun currentScreen(): JSONObject = JSONObject()
                .put("route", RemoteNav.currentRoute)
                .put("title", RemotePlaybackState.current?.title ?: "")

            override fun clearCache(): String {
                runCatching {
                    val loader = context.imageLoader
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                val tmp = java.io.File(context.filesDir, "kodi/addons-tmp")
                val removed = runCatching { tmp.deleteRecursively() }.getOrDefault(false)
                return "Cache de imagens limpo" + if (removed) " e temporários apagados." else "."
            }

            override fun onKeysReceived(payload: RemoteKeysPayload): String {
                val applied = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                payload.keys.forEach { (kind, value) ->
                    if (value.isBlank()) return@forEach
                    when (kind) {
                        "tmdb" -> { prefs.customTmdbApiKey = value; applied += "TMDB" }
                        "trakt_client_id" -> { prefs.traktClientId = value; applied += "Trakt" }
                        "simkl" -> { prefs.simklClientId = value; applied += "Simkl" }
                        "mdblist" -> { prefs.mdbListApiKey = value; applied += "MDBList" }
                        "tvdb" -> { prefs.tvdbApiKey = value; applied += "TVDB" }
                        "subdl" -> { prefs.subDlApiKey = value; applied += "SubDL" }
                        // Debrid: as chaves ficam no armazenamento próprio do
                        // DebridService, sem pref — não vêm por aqui.
                        "debrid_alldebrid" -> skipped += "debrid (fica no aparelho)"
                        else -> skipped += kind
                    }
                }
                return when {
                    applied.isEmpty() && skipped.isEmpty() -> "Nenhuma chave para aplicar."
                    applied.isEmpty() -> "Nada aplicado. Ignorado: ${skipped.joinToString()}."
                    else -> "Aplicado: ${applied.joinToString()}." +
                        if (skipped.isNotEmpty()) " Ignorado: ${skipped.joinToString()}." else ""
                }
            }

            override fun onAddonReceived(url: String): String =
                kotlinx.coroutines.runBlocking {
                    viewModel.installKodiAddonByUrl(url).getOrElse { "Falha: ${it.message}" }
                }

            override fun backupJson(): String = backupJson

            override fun onHandoffReceived(handoff: PlaybackHandoff): String {
                viewModel.saveHandoffProgress(handoff)
                return "\"${handoff.title}\" recebido — está no Continuar assistindo."
            }
        }
    }
    val server = remember { RemoteControlServer(host) }
    val serverOnState = remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    var serverMsg by remember { mutableStateOf("") }
    var serverLog by remember { mutableStateOf<List<String>>(emptyList()) }
    // O log é o anel do servidor (método+caminho+status — PIN e corpos
    // nunca entram nele); atualizado a cada 2 s com o servidor ligado.
    LaunchedEffect(serverOnState.value) {
        while (serverOnState.value) {
            serverLog = RemoteLog.tail()
            delay(2000)
        }
    }
    // Sair da página desliga o servidor (precedente do backup por Wi-Fi).
    DisposableEffect(Unit) {
        onDispose {
            if (serverOnState.value) {
                server.stop()
                serverOnState.value = false
            }
        }
    }
    fun startServer() {
        scope.launch {
            serverMsg = "Preparando backup…"
            backupJson = backupViewModel.buildBackupJson().getOrNull().orEmpty()
            val url = server.start()
            if (url != null) {
                serverUrl = url
                serverOnState.value = true
                serverMsg = "Servidor ligado. Mostre o QR no outro aparelho."
            } else {
                serverMsg = "Não foi possível ligar o servidor nesta rede."
            }
        }
    }
    fun stopServer() {
        server.stop()
        serverOnState.value = false
        serverUrl = ""
        serverMsg = "Servidor desligado."
    }

    // ── Cliente (item 1) ──────────────────────────────────────────────
    var clientHost by remember { mutableStateOf("") }
    var clientPort by remember { mutableStateOf("") }
    var clientPin by remember { mutableStateOf("") }
    var clientOk by remember { mutableStateOf(false) }
    var clientMsg by remember { mutableStateOf("") }
    var keyTmdb by remember { mutableStateOf("") }
    var keyTrakt by remember { mutableStateOf("") }
    var keySimkl by remember { mutableStateOf("") }
    var keyMdblist by remember { mutableStateOf("") }
    var keyTvdb by remember { mutableStateOf("") }
    var keySubdl by remember { mutableStateOf("") }
    var addonUrl by remember { mutableStateOf("") }
    var fetchedHandoff by remember { mutableStateOf<PlaybackHandoff?>(null) }
    fun clientPortInt(): Int = clientPort.toIntOrNull() ?: 0
    fun sendKeys() {
        val keys = mapOf(
            "tmdb" to keyTmdb.trim(), "trakt_client_id" to keyTrakt.trim(),
            "simkl" to keySimkl.trim(), "mdblist" to keyMdblist.trim(),
            "tvdb" to keyTvdb.trim(), "subdl" to keySubdl.trim(),
        ).filterValues { it.isNotBlank() }
        if (keys.isEmpty()) {
            clientMsg = "Preencha ao menos uma chave."
            return
        }
        scope.launch {
            clientMsg = "Enviando chaves…"
            clientMsg = RemoteControlClient.sendKeys(
                clientHost.trim(), clientPortInt(), clientPin.trim(),
                RemoteKeysPayload(keys = keys, links = emptyList()),
            ).getOrElse { "Falha: ${it.message}" }
        }
    }
    fun sendAddon() {
        if (addonUrl.isBlank()) {
            clientMsg = "Cole a URL do addon primeiro."
            return
        }
        scope.launch {
            clientMsg = "Enviando addon…"
            clientMsg = RemoteControlClient.sendAddon(
                clientHost.trim(), clientPortInt(), clientPin.trim(), addonUrl.trim(),
            ).getOrElse { "Falha: ${it.message}" }
        }
    }

    // ── Send to TV (item 2) ───────────────────────────────────────────
    var devices by remember { mutableStateOf<List<TvDevice>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }
    var tvMsg by remember { mutableStateOf("") }
    var activeSession by remember { mutableStateOf<SendToTvSession?>(SendToTvSessionManager.active) }
    // O que está tocando é lido na hora de enviar (o RemotePlaybackState
    // muda fora da composição); o estado só alimenta a nota na tela e é
    // atualizado junto com a procura de TVs.
    var nowPlaying by remember { mutableStateOf(RemotePlaybackState.current) }
    fun sendToTv(device: TvDevice) {
        val playing = RemotePlaybackState.current
        if (playing == null || playing.sourceUrl.isBlank()) {
            tvMsg = "Nada tocando agora — abra um título e volte aqui."
            return
        }
        scope.launch {
            tvMsg = "Enviando \"${playing.title}\" p/ ${device.name}…"
            val result = SendToTv.connectAndPlay(device, playing.sourceUrl, playing.title, playing.positionMs)
            result.onSuccess { session ->
                SendToTvSessionManager.set(session)
                activeSession = session
                tvMsg = "Tocando na ${device.name}. Pause/Stop na notificação."
            }.onFailure { tvMsg = "Falha: ${it.message}" }
        }
    }

    // ── Watch Next (item 4) ───────────────────────────────────────────
    val watchNextEnabled by viewModel.watchNextEnabled.collectAsStateWithLifecycle()
    var watchNextMsg by remember { mutableStateOf("") }

    // ── Gamepad (item 5) ──────────────────────────────────────────────
    val gamepadManager = remember { GamepadManager(prefs) }
    var gamepads by remember { mutableStateOf(gamepadManager.connectedGamepads()) }
    val gamepadPreset by viewModel.gamepadPreset.collectAsStateWithLifecycle()
    var capturingAction by remember { mutableStateOf<GamepadAction?>(null) }
    val capturedKey by GamepadCapture.captured.collectAsStateWithLifecycle()
    var mappingVersion by remember { mutableStateOf(0) }
    val manualMapping = remember(mappingVersion) { gamepadManager.manualMapping() }
    LaunchedEffect(capturedKey) {
        val keyCode = capturedKey ?: return@LaunchedEffect
        val action = capturingAction ?: return@LaunchedEffect
        gamepadManager.setManualMapping(action, keyCode)
        mappingVersion++
        capturingAction = null
        GamepadCapture.consume()
    }

    // ── Player externo (item 6) ────────────────────────────────────────
    val externalStore = remember { ExternalPlayerStore(prefs) }
    val installedPlayers = remember { externalStore.installedPlayers(context) }
    val extDefaultMovie by viewModel.externalPlayerDefaultMovie.collectAsStateWithLifecycle()
    val extDefaultSeries by viewModel.externalPlayerDefaultSeries.collectAsStateWithLifecycle()
    val extAsk by viewModel.externalPlayerAsk.collectAsStateWithLifecycle()
    val extByTitleJson by viewModel.externalPlayerByTitle.collectAsStateWithLifecycle()
    val extByTitle = remember(extByTitleJson) { externalStore.playerForTitleMap() }

    VpnPartyScaffold("Controle remoto e TV", "Celular, TV e gamepad — tudo local", onBack, highlightAnchor) {
        // ── Servidor ──
        VpnPartySection("Servidor do controle remoto", anchor = SettingsAnchors.REMOTE_SERVER)
        RemoteTvCard {
            Text(
                "O aparelho vira um controle remoto por QR: o celular mostra a tela como lista, aperta os botões e limpa o cache — e recebe chaves, addons e o filme que está tocando. Só na rede local, com PIN de 4 dígitos. Desliga ao sair desta página.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (serverOnState.value) {
                    AotvButton("Desligar servidor", onClick = { stopServer() }, tone = ButtonTone.DANGER)
                } else {
                    AotvButton("Ligar servidor", onClick = { startServer() }, tone = ButtonTone.PRIMARY)
                }
                Spacer(Modifier.width(10.dp))
                if (serverMsg.isNotBlank()) Text(serverMsg, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            if (serverOnState.value && serverUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteQrCode(serverUrl, Modifier.size(160.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Aponte a câmera do outro aparelho p/ o QR.", fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Endereço: $serverUrl", fontSize = 13.sp, color = TextSecondary)
                        Text("PIN: ${server.pin} (digite no outro aparelho)", fontSize = 13.sp, color = TextSecondary)
                        Text("O PIN NÃO vai no QR — quem escaneia ainda precisa dele.", fontSize = 13.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Botões desta tela", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                val actions = RemoteActionRegistry.snapshot()
                if (actions.isEmpty()) {
                    Text("Nenhum botão registrado nesta tela.", fontSize = 13.sp, color = TextSecondary)
                } else {
                    actions.forEach { action ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(action.description.ifBlank { action.name }, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            AotvButton("Apertar", onClick = { serverMsg = RemoteActionRegistry.press(action.name) ?: "Sem resposta." }, compact = true)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Log ao vivo", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { serverLog = RemoteLog.tail() }) { Icon(Icons.Default.Refresh, "Atualizar log", tint = TextSecondary) }
                    AotvButton("Limpar cache", onClick = { serverMsg = host.clearCache() }, compact = true)
                }
                if (serverLog.isEmpty()) {
                    Text("Sem pedidos ainda.", fontSize = 13.sp, color = TextSecondary)
                } else {
                    serverLog.takeLast(10).forEach { line ->
                        Text(line, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        // ── Cliente ──
        VpnPartySection("Enviar p/ outro aparelho", anchor = SettingsAnchors.REMOTE_CLIENT)
        RemoteTvCard {
            Text(
                "Conecte no servidor do outro aparelho (TV ou celular) e mande as chaves de contas, o link do addon e o filme que está tocando — sem digitar com o controle.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    clientHost, { clientHost = it; clientOk = false },
                    label = { Text("Endereço (IP)") }, singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    clientPort, { clientPort = it.filter { c -> c.isDigit() }.take(5); clientOk = false },
                    label = { Text("Porta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    clientPin, { clientPin = it.filter { c -> c.isDigit() }.take(4); clientOk = false },
                    label = { Text("PIN") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AotvButton("Conectar", onClick = {
                    scope.launch {
                        clientMsg = "Conectando…"
                        val ok = RemoteControlClient.ping(clientHost.trim(), clientPortInt(), clientPin.trim())
                            .getOrDefault(false)
                        clientOk = ok
                        clientMsg = if (ok) "Conectado." else "Não conectou — confira endereço, porta e PIN."
                    }
                })
                Spacer(Modifier.width(10.dp))
                if (clientMsg.isNotBlank()) Text(clientMsg, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            if (clientOk) {
                Spacer(Modifier.height(12.dp))
                Text("Chaves de contas", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedTextField(keyTmdb, { keyTmdb = it }, label = { Text("TMDB") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(keyTrakt, { keyTrakt = it }, label = { Text("Trakt (client ID)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(keySimkl, { keySimkl = it }, label = { Text("Simkl (client ID)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(keyMdblist, { keyMdblist = it }, label = { Text("MDBList") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(keyTvdb, { keyTvdb = it }, label = { Text("TVDB") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(keySubdl, { keySubdl = it }, label = { Text("SubDL") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                AotvButton("Enviar chaves", onClick = { sendKeys() })
                Spacer(Modifier.height(12.dp))
                Text("Addon Kodi", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedTextField(addonUrl, { addonUrl = it }, label = { Text("URL do ZIP ou do repositório") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                AotvButton("Enviar addon", onClick = { sendAddon() })
                Spacer(Modifier.height(12.dp))
                Text("Take it with you", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Busca o filme que está tocando no outro aparelho e salva no Continuar assistindo daqui — ou manda o que está tocando aqui p/ lá.",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    AotvButton("Buscar da TV", onClick = {
                        scope.launch {
                            clientMsg = "Buscando…"
                            val result = RemoteControlClient.fetchHandoff(clientHost.trim(), clientPortInt(), clientPin.trim())
                            result.onSuccess { handoff ->
                                fetchedHandoff = handoff
                                clientMsg = "\"${handoff.title}\" está tocando lá."
                            }.onFailure { clientMsg = "Falha: ${it.message}" }
                        }
                    })
                    Spacer(Modifier.width(8.dp))
                    AotvButton("Enviar o meu", onClick = {
                        val mine = RemotePlaybackState.current
                        if (mine == null || mine.sourceUrl.isBlank()) {
                            clientMsg = "Nada tocando aqui agora."
                            return@AotvButton
                        }
                        scope.launch {
                            clientMsg = RemoteControlClient.sendHandoff(clientHost.trim(), clientPortInt(), clientPin.trim(), mine)
                                .getOrElse { "Falha: ${it.message}" }
                        }
                    })
                }
                val fetched = fetchedHandoff
                if (fetched != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "\"${fetched.title}\" · ${fetched.positionMs / 1000}s de ${fetched.durationMs / 1000}s",
                            fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f),
                        )
                        AotvButton("Salvar no Continuar", onClick = {
                            viewModel.saveHandoffProgress(fetched)
                            clientMsg = "Salvo no Continuar assistindo."
                        }, compact = true)
                    }
                }
            }
        }

        // ── Send to TV ──
        VpnPartySection("Enviar p/ a TV", anchor = SettingsAnchors.REMOTE_TV)
        RemoteTvCard {
            Text(
                "Lista as TVs do Wi-Fi e manda o que está tocando p/ lá. Chromecast por DIAL e DLNA por AVTransport — sem o Google Cast SDK. AirPlay aparece na lista, mas vídeo por AirPlay não funciona: o AirPlay 2 exige FairPlay (só aparelhos certificados MFi) e não há biblioteca aberta p/ enviar vídeo.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AotvButton(
                    "Procurar TVs", onClick = {
                        nowPlaying = RemotePlaybackState.current
                        scope.launch {
                            discovering = true
                            tvMsg = "Procurando na rede…"
                            devices = SendToTvDiscovery.discover(context)
                            discovering = false
                            tvMsg = if (devices.isEmpty()) "Nenhuma TV encontrada no Wi-Fi." else "${devices.size} aparelho(s) encontrado(s)."
                        }
                    },
                    busy = discovering, busyLabel = "Procurando…",
                )
                Spacer(Modifier.width(10.dp))
                if (tvMsg.isNotBlank()) Text(tvMsg, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                devices.forEach { device ->
                    val kindLabel = when (device.kind) {
                        TvTargetKind.CHROMECAST -> "Chromecast"
                        TvTargetKind.DLNA -> "DLNA"
                        TvTargetKind.AIRPLAY -> "AirPlay"
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(device.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                "$kindLabel · ${device.host}" + if (device.model.isNotBlank()) " · ${device.model}" else "",
                                fontSize = 13.sp, color = TextSecondary,
                            )
                            if (!device.videoSupported && device.unsupportedReason.isNotBlank()) {
                                Text(device.unsupportedReason, fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        if (device.videoSupported) {
                            AotvButton("Enviar", onClick = { sendToTv(device) }, compact = true)
                        }
                    }
                }
            }
            val session = activeSession
            // Lê p/ val local antes do teste: smart cast não funciona em
            // propriedade delegada (`nowPlaying` é `by remember`).
            val playing = nowPlaying
            if (session != null) {
                Spacer(Modifier.height(12.dp))
                Text("Tocando na TV", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "\"${session.title}\" · ${session.device.name}",
                    fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    AotvButton("Pausar", onClick = {
                        scope.launch {
                            session.pause().onFailure { tvMsg = "Falha: ${it.message}" }
                        }
                    }, compact = true)
                    Spacer(Modifier.width(8.dp))
                    AotvButton("Continuar", onClick = {
                        scope.launch {
                            session.resume().onFailure { tvMsg = "Falha: ${it.message}" }
                        }
                    }, compact = true)
                    Spacer(Modifier.width(8.dp))
                    AotvButton("Parar", onClick = {
                        scope.launch {
                            session.stop()
                            SendToTvSessionManager.clear()
                            activeSession = null
                            tvMsg = "Parado."
                        }
                    }, tone = ButtonTone.DANGER, compact = true)
                }
                Text(
                    "Pause/Stop também estão na notificação do celular.",
                    fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp),
                )
            } else if (playing != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tocando agora: \"${playing.title}\". Procure a TV acima p/ enviar.",
                    fontSize = 13.sp, color = TextSecondary,
                )
            }
        }

        // ── Watch Next ──
        VpnPartySection("Watch Next no launcher", anchor = SettingsAnchors.REMOTE_WATCHNEXT)
        SettingsGroup {
            SettingsRow(
                icon = Icons.Default.Tv,
                title = "Watch Next no launcher",
                sub = if (watchNextEnabled) "Ligado: o Continuar assistindo vira canal na tela inicial da Google TV"
                    else "Desligado: nada é publicado no launcher",
                trailing = { Switch(checked = watchNextEnabled, onCheckedChange = { viewModel.setWatchNextEnabled(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setWatchNextEnabled(!watchNextEnabled) },
                grouped = true,
            )
        }
        RemoteTvCard {
            Text(
                "Só vale na TV: o canal aparece na tela inicial do Google TV (\"Continuar assistindo\") com os títulos do Continuar. Tocar num deles abre o detalhe no app. Sem a pref ligada, o canal é apagado. A sincronização é automática quando o Continuar muda.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AotvButton("Sincronizar agora", onClick = {
                    scope.launch {
                        watchNextMsg = "Sincronizando…"
                        watchNextMsg = runCatching {
                            val n = viewModel.syncWatchNextNow()
                            "Sincronizados $n título(s) no launcher."
                        }.getOrElse { "Falha: ${it.message}" }
                    }
                })
                Spacer(Modifier.width(10.dp))
                if (watchNextMsg.isNotBlank()) Text(watchNextMsg, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            }
        }

        // ── Gamepad ──
        VpnPartySection("Gamepad", anchor = SettingsAnchors.REMOTE_GAMEPAD)
        RemoteTvCard {
            Text(
                "O controle fala em botões próprios (A, B, X, Y…); o app traduz p/ DPAD e teclas de mídia — por isso vale em todas as telas. O botão PS/Home é do sistema e não chega ao app. Os analógicos não são mapeados (o DPAD cobre a navegação).",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Conectados agora", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { gamepads = gamepadManager.connectedGamepads() }) {
                    Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary)
                }
            }
            if (gamepads.isEmpty()) {
                Text("Nenhum gamepad conectado.", fontSize = 13.sp, color = TextSecondary)
            } else {
                gamepads.forEach { device ->
                    Text(
                        "• ${device.name} (preset: ${gamepadManager.presetFor(device).label})",
                        fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Preset", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            val presetOptions = listOf("auto" to "Automático (detecta pela marca)") +
                GamepadPresets.ALL.map { it.id to it.label }
            presetOptions.forEach { (id, label) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .selectable(selected = gamepadPreset == id, onClick = { viewModel.setGamepadPreset(id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = gamepadPreset == id, onClick = null)
                    Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Mapeamento manual", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Toque em \"Mapear\" e aperte o botão do gamepad. Um botão só pode ser uma ação — mapear tira ele da ação anterior.",
                fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp),
            )
            val capturing = capturingAction
            if (capturing != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aperte um botão do gamepad p/ \"${GamepadManager.actionLabel(capturing)}\"…",
                        fontSize = 14.sp, color = Accent, modifier = Modifier.weight(1f),
                    )
                    AotvButton("Cancelar", onClick = {
                        GamepadCapture.stop()
                        capturingAction = null
                    }, compact = true)
                }
            }
            GamepadAction.entries.forEach { action ->
                val mapped = manualMapping[action]
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(GamepadManager.actionLabel(action), fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        mapped?.let { GamepadManager.keyCodeLabel(it) } ?: "—",
                        fontSize = 13.sp, color = TextSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    AotvButton("Mapear", onClick = {
                        capturingAction = action
                        GamepadCapture.start()
                    }, compact = true)
                }
            }
            Spacer(Modifier.height(10.dp))
            AotvButton("Limpar mapeamento manual", onClick = {
                gamepadManager.clearAllManualMapping()
                mappingVersion++
            }, tone = ButtonTone.DANGER)
        }

        // ── Player externo ──
        VpnPartySection("Player externo", anchor = SettingsAnchors.REMOTE_EXTERNAL)
        RemoteTvCard {
            Text(
                "Escolhe o player por título ou por tipo. Ao voltar do player externo, o progresso é salvo no Continuar assistindo — o MX Player devolve a posição; os outros estimam pelo tempo decorrido.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            if (installedPlayers.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Nenhum player de vídeo instalado (VLC, MX Player…).", fontSize = 13.sp, color = TextSecondary)
            } else {
                Spacer(Modifier.height(12.dp))
                Text("Padrão p/ filmes", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                ExternalPlayerChoice(
                    current = extDefaultMovie,
                    players = installedPlayers,
                    onPick = { viewModel.setExternalPlayerDefaultMovie(it) },
                )
                Spacer(Modifier.height(12.dp))
                Text("Padrão p/ séries", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                ExternalPlayerChoice(
                    current = extDefaultSeries,
                    players = installedPlayers,
                    onPick = { viewModel.setExternalPlayerDefaultSeries(it) },
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Perguntar sempre",
                    sub = if (extAsk) "Ligado: sem padrão salvo, o seletor aparece toda vez"
                        else "Desligado: sem padrão salvo, abre direto no primeiro player instalado",
                    trailing = { Switch(checked = extAsk, onCheckedChange = { viewModel.setExternalPlayerAsk(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                    onClick = { viewModel.setExternalPlayerAsk(!extAsk) },
                    grouped = true,
                )
            }
            if (extByTitle.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Por título", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                extByTitle.forEach { (key, packageName) ->
                    val tmdbId = key.substringBefore(':').toIntOrNull() ?: 0
                    val type = key.substringAfter(':', "")
                    val playerLabel = installedPlayers.firstOrNull { it.packageName == packageName }?.label ?: packageName
                    val typeLabel = when (type) {
                        "MOVIE" -> "Filme"
                        "TV_SERIES" -> "Série"
                        else -> type
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$typeLabel #$tmdbId → $playerLabel",
                            fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            externalStore.clearPlayerForTitle(tmdbId, type)
                        }) { Icon(Icons.Default.Delete, "Remover", tint = TextSecondary) }
                    }
                }
            }
        }
    }
}

/** Bloco com fundo de cartão (mesmo desenho do `SettingsCard` privado de SettingsPages.kt). */
@Composable
private fun RemoteTvCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(16.dp),
        content = content,
    )
}

/** Escolha de player padrão p/ um tipo (filme ou série): "Perguntar sempre" + instalados. */
@Composable
private fun ExternalPlayerChoice(
    current: String,
    players: List<com.aotv.app.data.local.ExternalPlayerApp>,
    onPick: (String) -> Unit,
) {
    val options = listOf("" to "Perguntar sempre") + players.map { it.packageName to it.label }
    options.forEach { (packageName, label) ->
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp)
                .selectable(selected = current == packageName, onClick = { onPick(packageName) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = current == packageName, onClick = null)
            Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** QR do endereço do servidor (mesmo desenho do QR do backup por Wi-Fi). */
@Composable
private fun RemoteQrCode(text: String, modifier: Modifier) {
    val img = remember(text) {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
        )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(img, "Código QR do servidor do controle remoto", modifier, contentScale = ContentScale.FillBounds)
}
