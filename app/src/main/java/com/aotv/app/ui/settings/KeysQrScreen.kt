package com.aotv.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.remote.KeysQrServer
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Chaves pelo celular via QR (v1.45.0, item 9 — ideia do Notflix,
 * reimplementada).
 *
 * O controle remoto por LAN (Lote 22) já deixava colar chaves pelo
 * celular, mas exigia a tela de controle remoto inteira. Esta é a versão
 * mínima e dedicada: QR nas Configurações → Integrações; o celular abre
 * uma página servida pelo próprio app ([KeysQrServer]) e o Davi digita a
 * chave TMDB, o Trakt Client ID e a URL da IntroDB no teclado do celular
 * — sem digitar nada no controle remoto/teclado da TV.
 *
 * O servidor liga sozinho ao abrir a página e desliga ao sair (precedente
 * do backup por Wi-Fi). O PIN NÃO vai no QR — quem escaneia ainda precisa
 * digitá-lo no formulário do celular.
 */
@Composable
fun KeysQrScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs = viewModel.prefs
    val scope = rememberCoroutineScope()
    val server = remember { KeysQrServer(prefs) }
    var serverOn by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    var serverMsg by remember { mutableStateOf("") }
    val introDbUrl by prefs.introDbBaseUrlFlow.collectAsStateWithLifecycle()
    val traktSet = prefs.traktClientId.isNotBlank()
    val tmdbConfigured = prefs.customTmdbApiKey.isNotBlank()

    fun startServer() {
        scope.launch {
            val url = server.start()
            if (url != null) {
                serverUrl = url
                serverOn = true
                serverMsg = "Servidor ligado. Mostre o QR no celular."
            } else {
                serverMsg = "Não foi possível ligar o servidor nesta rede."
            }
        }
    }

    fun stopServer() {
        server.stop()
        serverOn = false
        serverUrl = ""
        serverMsg = "Servidor desligado."
    }

    // Liga sozinho ao abrir; sair da página desliga o servidor.
    LaunchedEffect(Unit) { startServer() }
    DisposableEffect(Unit) {
        onDispose { if (serverOn) server.stop() }
    }

    VpnPartyScaffold("Chaves pelo celular", "TMDB, Trakt e IntroDB no teclado do celular", onBack, highlightAnchor) {
        VpnPartySection("Servidor de chaves", anchor = SettingsAnchors.KEYS_QR)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "O celular abre uma página servida pelo próprio app (só na rede local, com PIN de 4 dígitos) e digita as chaves lá — sem digitar no controle remoto. O servidor desliga ao sair desta página.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (serverOn) {
                    AotvButton("Desligar servidor", onClick = { stopServer() }, tone = ButtonTone.DANGER)
                } else {
                    AotvButton("Ligar servidor", onClick = { startServer() }, tone = ButtonTone.PRIMARY)
                }
                Spacer(Modifier.width(10.dp))
                if (serverMsg.isNotBlank()) Text(serverMsg, fontSize = 13.sp, color = TextSecondary)
            }
            if (serverOn && serverUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(serverUrl, Modifier.size(160.dp), "Código QR da página de chaves")
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Aponte a câmera do celular p/ o QR.", fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Endereço: $serverUrl", fontSize = 13.sp, color = TextSecondary)
                        Text("PIN: ${server.pin} (digite no celular)", fontSize = 13.sp, color = TextSecondary)
                        Text("O PIN NÃO vai no QR — quem escaneia ainda precisa dele.", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("O que já está configurado", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Chave TMDB: ${if (tmdbConfigured) "configurada" else "não configurada"}", fontSize = 13.sp, color = TextSecondary)
            Text("Trakt Client ID: ${if (traktSet) "configurado" else "não configurado"}", fontSize = 13.sp, color = TextSecondary)
            Text("URL da IntroDB: $introDbUrl", fontSize = 13.sp, color = TextSecondary)
        }
    }
}
