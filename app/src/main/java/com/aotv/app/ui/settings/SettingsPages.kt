package com.aotv.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.DEFAULT_PLAYER_BUTTONS
import com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.recordings.RecordingService
import com.aotv.app.data.recordings.RecordingStore
import com.aotv.app.data.recordings.RecordingThumbs
import com.aotv.app.data.recordings.ScheduledRecording
import com.aotv.app.data.remote.AppUpdateService
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.SubLangs
import com.aotv.app.ui.movies.MpvScripts
import com.aotv.app.ui.movies.PlayerPrefsViewModel
import com.aotv.app.ui.movies.SUB_BG_OPACITIES
import com.aotv.app.ui.movies.SUB_COLORS
import com.aotv.app.ui.movies.SUB_FONTS
import com.aotv.app.ui.movies.StreamPrefsViewModel
import com.aotv.app.ui.movies.effectivePlayerButtons
import com.aotv.app.ui.movies.effectivePlayerTopActions
import com.aotv.app.ui.movies.playerButtonLabel
import com.aotv.app.ui.movies.playerTopActionLabel
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.Danger
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.Ok
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
// DEPENDÊNCIA (outra frente da Fase A): ThemeSelector() vive em ui/theme/ThemePicker.kt.
// Se o arquivo ainda não existe, esta importação é o único ponto a integrar.
import com.aotv.app.ui.theme.CustomThemeEditor
import com.aotv.app.ui.theme.ThemeSelector
import kotlin.math.roundToInt

/** Ids das páginas — são os segmentos da rota `movies_settings/{page}`. */
object SettingsPageIds {
    const val Layout = "layout"
    const val Detalhe = "detalhe"
    const val Streams = "streams"
    const val Conteudo = "conteudo"
    const val Integracoes = "integracoes"
    const val Acompanhamento = "acompanhamento"
    const val Aparencia = "aparencia"
    const val Reproducao = "reproducao"
    /** Motor do player + tudo de mpv (remodel v1.44.0, Frente 2):
     * saiu de Reprodução p/ aba própria. NÃO está em [kidsHidden],
     * igual a Reprodução, de onde veio. */
    const val Mpv = "mpv"
    const val Downloads = "downloads"
    const val Backup = "backup"
    const val Trackers = "trackers"
    const val Vpn = "vpn"
    const val WatchParty = "watchparty"
    const val SocialHub = "social"
    const val Diagnostico = "diagnostico"
    /** Controle remoto + Send to TV + Watch Next + gamepad + player externo (Lote 22, v1.32.0). */
    const val RemoteTv = "remotetv"

    /** Páginas escondidas no perfil infantil (igual à tela única antiga). */
    val kidsHidden = setOf(Layout, Conteudo, Integracoes, Aparencia, Backup, Trackers, Vpn, WatchParty, SocialHub, Diagnostico, RemoteTv)

    fun title(id: String): String = when (id) {
        Layout -> "Layout"
        Detalhe -> "Página de detalhes"
        Streams -> "Streams"
        Conteudo -> "Conteúdo & Descoberta"
        Integracoes -> "Integrações"
        Acompanhamento -> "Acompanhamento"
        Aparencia -> "Aparência"
        Reproducao -> "Reprodução"
        Mpv -> "MPV"
        Downloads -> "Downloads"
        Backup -> "Backup e restauração"
        Trackers -> "Trackers e Sincronização"
        Vpn -> "VPN"
        WatchParty -> "Watch Party"
        SocialHub -> "Social Hub"
        Diagnostico -> "Diagnóstico"
        RemoteTv -> "Controle remoto e TV"
        else -> "Configurações"
    }
}

/**
 * Despacha a página `movies_settings/{page}`. O conteúdo é o mesmo da tela única antiga,
 * só reorganizado — nenhuma setting nova além do seletor de tema (Aparência, Fase A).
 */
@Composable
fun SettingsPageScreen(
    pageId: String,
    highlightAnchor: String? = null,
    onOpenAddons: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    onOpenKodiAddons: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenHomeCustomize: () -> Unit = {},
    onOpenDebrid: () -> Unit = {},
    onOpenMediaServers: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenLive: () -> Unit = {},
    onOpenLiveChannels: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenWatchParty: () -> Unit = {},
    // Lote 25 (v1.35.0) — Frente B: "Report a problem" + saúde dos addons.
    onOpenReportProblem: () -> Unit = {},
    onOpenAddonHealth: () -> Unit = {},
    onBack: () -> Unit,
    onPlatformChanged: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val pid by viewModel.profileId.collectAsStateWithLifecycle()
    val me = profiles.firstOrNull { it.id == pid } ?: profiles.first()
    if (me.kids && pageId in SettingsPageIds.kidsHidden) {
        LockedPage(SettingsPageIds.title(pageId), onBack)
        return
    }
    when (pageId) {
        SettingsPageIds.Layout -> LayoutPage(onOpenHomeCustomize, onOpenCollections, onBack, viewModel, highlightAnchor)
        SettingsPageIds.Detalhe -> DetalhePage(onBack, viewModel, highlightAnchor)
        SettingsPageIds.Streams -> StreamsPage(onBack, highlightAnchor = highlightAnchor)
        SettingsPageIds.Conteudo -> ConteudoPage(onOpenAddons, onOpenPlugins, onOpenKodiAddons, onOpenDebrid, onOpenMediaServers, onOpenAddonHealth, onBack, viewModel, highlightAnchor)
        SettingsPageIds.Integracoes -> IntegracoesPage(onOpenAccounts, onOpenLive, onBack, viewModel, highlightAnchor, onOpenLiveChannels)
        SettingsPageIds.Acompanhamento -> AcompanhamentoPage(me.name, me.kids, onOpenProfiles, onBack, viewModel, highlightAnchor)
        SettingsPageIds.Aparencia -> AparenciaPage(onBack, onPlatformChanged, viewModel, highlightAnchor, onOpenReportProblem = onOpenReportProblem)
        SettingsPageIds.Reproducao -> ReproducaoPage(onBack, viewModel, highlightAnchor = highlightAnchor)
        SettingsPageIds.Mpv -> MpvPage(onBack, highlightAnchor = highlightAnchor)
        SettingsPageIds.Downloads -> DownloadsPage(onBack, viewModel, highlightAnchor)
        SettingsPageIds.Backup -> BackupScreen(onBack, highlightAnchor = highlightAnchor)
        SettingsPageIds.Trackers -> TrackersScreen(onBack)
        SettingsPageIds.Vpn -> VpnScreen(onBack, highlightAnchor)
        SettingsPageIds.WatchParty -> WatchPartyScreen(onBack, highlightAnchor)
        SettingsPageIds.SocialHub -> SocialHubScreen(onBack, highlightAnchor, onOpenWatchParty = onOpenWatchParty)
        SettingsPageIds.Diagnostico -> DiagnosticsScreen(onBack, highlightAnchor, onOpenReportProblem = onOpenReportProblem, onOpenAddonHealth = onOpenAddonHealth)
        SettingsPageIds.RemoteTv -> RemoteTvScreen(onBack, highlightAnchor)
        else -> PlaceholderPage(
            title = "Configurações",
            sub = null,
            note = "Essa página não existe. Volte e escolha outra.",
            onBack = onBack,
        )
    }
}

/** Posições (px dentro do conteúdo rolável) das seções com âncora da página aberta. */
private val LocalSettingsAnchorPositions = staticCompositionLocalOf<MutableMap<String, Int>?> { null }

/** Âncora pedida pela busca profunda (null = abertura normal, sem destaque). */
private val LocalSettingsHighlightAnchor = staticCompositionLocalOf<String?> { null }

/**
 * Moldura comum das páginas: voltar + título + coluna rolável.
 * Com [highlightAnchor], rola até a seção da âncora quando ela registra a
 * posição e destaca o título da seção (busca profunda, Lote 6).
 */
@Composable
private fun SettingsPageScaffold(
    title: String,
    sub: String?,
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    val scrollState = rememberScrollState()
    val anchorPositions = remember { mutableStateMapOf<String, Int>() }
    // A seção registra a posição ao compor; quando a âncora pedida aparece,
    // rola até ela (uma vez por âncora — a posição não muda com a rolagem).
    LaunchedEffect(highlightAnchor, highlightAnchor?.let { anchorPositions[it] }) {
        val y = highlightAnchor?.let { anchorPositions[it] } ?: return@LaunchedEffect
        scrollState.scrollTo(maxOf(0, y - 8))
    }
    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Na TV quem volta é o Voltar do controle. O botão sai do caminho do foco.
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(title, Modifier.weight(1f), sub = sub)
        }
        CompositionLocalProvider(
            LocalSettingsAnchorPositions provides anchorPositions,
            LocalSettingsHighlightAnchor provides highlightAnchor,
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scrollState)
                    .padding(horizontal = pad).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** Página travada no perfil infantil (o hub já esconde a entrada; isto é a trava de rota). */
@Composable
private fun LockedPage(title: String, onBack: () -> Unit) {
    SettingsPageScaffold(title, null, onBack) {
        SettingsCard {
            Text(
                "Disponível fora do perfil infantil. Troque de perfil para mexer aqui.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
        }
    }
}

/** Página sem conteúdo existente ainda — nota honesta, sem toggles inventados. */
@Composable
private fun PlaceholderPage(title: String, sub: String?, note: String, onBack: () -> Unit) {
    SettingsPageScaffold(title, sub, onBack) {
        SettingsCard {
            Text(note, fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
        }
    }
}

/**
 * Rótulo de seção das páginas. Com [anchor], registra a posição no conteúdo
 * rolável p/ a busca profunda rolar até aqui; quando é a âncora pedida, o
 * título ganha fundo de destaque.
 */
@Composable
private fun SettingsSection(text: String, anchor: String? = null) {
    val positions = LocalSettingsAnchorPositions.current
    val highlighted = anchor != null && anchor == LocalSettingsHighlightAnchor.current
    com.aotv.app.ui.common.SettingsSection(
        text,
        modifier = Modifier
            .then(
                if (anchor != null && positions != null) Modifier.onGloballyPositioned { coords ->
                    positions[anchor] = coords.positionInParent().y.roundToInt()
                } else Modifier
            )
            .then(
                if (highlighted) Modifier.background(AccentSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                else Modifier
            ),
    )
}

/** Bloco com fundo de cartão para agrupar controles que não são uma linha só. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(16.dp),
        content = content,
    )
}

/**
 * Gerenciador de scripts Lua do mpv (Lote de correções, v1.42.0 — Frente 2):
 * instala um `.lua` do celular pelo seletor de arquivos do sistema (SAF,
 * mesmo padrão do perfil VPN e do backup), lista os instalados com
 * ligar/desligar e excluir — tudo de dentro do app, sem acesso por fora à
 * pasta interna (`filesDir/mpv/scripts`).
 *
 * O launcher é registrado no escopo deste @Composable (nunca dentro de
 * `remember {}`) e a cópia roda em Dispatchers.IO com null check no
 * `openInputStream`. O script instalado entra no mesmo caminho dos
 * exemplos: o mpv carrega os `.lua` da pasta no próximo init com
 * `load-scripts=yes` (ver o init em MpvPlayer) — vale a partir da
 * próxima reprodução com o Libmpv. Desligar renomeia p/
 * `<nome>.lua.disabled`, que o mpv ignora.
 */
@Composable
private fun MpvScriptsManager() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scripts by remember { mutableStateOf(emptyList<MpvScripts.ScriptInfo>()) }
    var refreshTick by remember { mutableStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var pendingOverwrite by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var scriptToDelete by remember { mutableStateOf<MpvScripts.ScriptInfo?>(null) }

    fun refreshScripts() {
        refreshTick++
    }
    LaunchedEffect(refreshTick) {
        scripts = MpvScripts.listScripts(context)
    }

    // Seletor de arquivos do sistema (SAF): "*/*" porque nem todo
    // gerenciador devolve MIME de .lua — a extensão é validada em
    // MpvScripts.installScript, que rejeita o que não é .lua.
    val pickLuaScript = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        installing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val displayName = MpvScripts.displayName(context, uri)
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    MpvScripts.InstallResult.Failed("Não foi possível abrir o arquivo escolhido")
                } else {
                    input.use { stream -> MpvScripts.installScript(context, displayName, stream) }
                }
            }
            installing = false
            when (result) {
                is MpvScripts.InstallResult.Installed -> {
                    Toast.makeText(context, "Script ${result.name} instalado — vale na próxima reprodução com o Libmpv", Toast.LENGTH_LONG).show()
                    refreshScripts()
                }
                is MpvScripts.InstallResult.Duplicate -> {
                    pendingOverwrite = uri to result.name
                }
                MpvScripts.InstallResult.InvalidName -> {
                    Toast.makeText(context, "Nome de arquivo inválido — escolha um arquivo .lua com nome", Toast.LENGTH_LONG).show()
                }
                MpvScripts.InstallResult.InvalidExtension -> {
                    Toast.makeText(context, "Só arquivos .lua — o arquivo escolhido não é um script Lua", Toast.LENGTH_LONG).show()
                }
                MpvScripts.InstallResult.TooLarge -> {
                    Toast.makeText(context, "Script muito grande (limite de 1 MB)", Toast.LENGTH_LONG).show()
                }
                is MpvScripts.InstallResult.Failed -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Duplicado: substituir só com confirmação.
    val pending = pendingOverwrite
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            containerColor = BackgroundCard,
            title = { Text("Substituir script?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Já existe um script chamado ${pending.second}. Substituir pelo arquivo escolhido?",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingOverwrite = null
                    scope.launch {
                        val overwritten = withContext(Dispatchers.IO) {
                            val input = context.contentResolver.openInputStream(pending.first)
                            if (input == null) {
                                MpvScripts.InstallResult.Failed("Não foi possível abrir o arquivo escolhido")
                            } else {
                                input.use { stream -> MpvScripts.installScriptOverwrite(context, pending.second, stream) }
                            }
                        }
                        if (overwritten is MpvScripts.InstallResult.Installed) {
                            Toast.makeText(context, "Script ${overwritten.name} substituído — vale na próxima reprodução com o Libmpv", Toast.LENGTH_LONG).show()
                            refreshScripts()
                        } else if (overwritten is MpvScripts.InstallResult.Failed) {
                            Toast.makeText(context, overwritten.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Substituir", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite = null }) { Text("Cancelar", color = TextSecondary) }
            },
        )
    }

    // Excluir: confirma antes de apagar.
    val toDelete = scriptToDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            containerColor = BackgroundCard,
            title = { Text("Excluir script?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Excluir ${toDelete.name} da pasta de scripts? O script some na próxima reprodução com o Libmpv.",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scriptToDelete = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) { MpvScripts.deleteScript(context, toDelete) }
                        if (deleted) {
                            Toast.makeText(context, "Script ${toDelete.name} excluído", Toast.LENGTH_SHORT).show()
                            refreshScripts()
                        } else {
                            Toast.makeText(context, "Não foi possível excluir ${toDelete.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Excluir", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) { Text("Cancelar", color = TextSecondary) }
            },
        )
    }

    SettingsCard {
        Text(
            "Scripts Lua instalados",
            fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Instale um arquivo .lua do celular — ele carrega na próxima reprodução com o Libmpv. Desligar deixa o script na pasta sem carregar.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { pickLuaScript.launch(arrayOf("*/*")) }, enabled = !installing) {
            Text("Instalar script .lua", color = Accent, fontWeight = FontWeight.Bold)
        }
        if (scripts.isEmpty()) {
            Text(
                "Nenhum script — os 2 exemplos são copiados na 1ª reprodução com o Libmpv.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
        } else {
            scripts.forEach { script ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(script.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (!script.enabled) {
                            Text("Desligado — não carrega", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = script.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { MpvScripts.setScriptEnabled(context, script, enabled) }
                                if (ok) {
                                    refreshScripts()
                                } else {
                                    Toast.makeText(context, "Não foi possível ${if (enabled) "ligar" else "desligar"} ${script.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                    IconButton(onClick = { scriptToDelete = script }) {
                        Icon(Icons.Default.Delete, "Excluir ${script.name}", tint = Danger)
                    }
                }
            }
        }
    }
}

// ── Downloads (Lote 5, v1.14.0) ─────────────────────────────────────────────

/** Nome da pasta SAF p/ mostrar (DISPLAY_NAME da árvore; fallback p/ o último segmento da URI). */
private fun safFolderDisplayName(context: android.content.Context, treeUri: Uri): String {
    val fromQuery = runCatching {
        context.contentResolver.query(treeUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
    if (fromQuery != null) return fromQuery
    return Uri.decode(treeUri.lastPathSegment?.substringAfterLast('/') ?: "").ifBlank { "Pasta escolhida" }
}

/** Linha da gravação: canal, horário e status (Lote 8). */
private fun recordingSub(rec: ScheduledRecording): String {
    val day = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(rec.startMs))
    val until = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(rec.stopMs))
    // Lote 20 (v1.30.0): onde o arquivo está (local ou já movido p/ o destino).
    val where = when {
        rec.movedToDestination -> " · No destino"
        rec.status == ScheduledRecording.STATUS_DONE -> " · Local"
        else -> ""
    }
    return "${rec.channelName} · $day até $until · ${rec.statusLabel()}$where"
}

/**
 * Abre uma gravação p/ assistir (Lote 12 — Frente 2; local via FileProvider
 * no Lote 20, v1.30.0). A gravação em andamento toca o arquivo parcial (o
 * player abre o arquivo como está). Pasta SAF (já movida): o arquivo abre
 * no player do sistema pela URI de conteúdo (com a pasta da série, quando
 * houver). Pasta do app (local): o arquivo abre pela URI do FileProvider
 * (`files-path recordings/` — antes não havia FileProvider p/ ele e o
 * toast só explicava). Tocar dentro do app a partir daqui precisa do
 * callback de navegação p/ o player, fora desta frente.
 */
private fun playRecording(
    context: android.content.Context,
    rec: ScheduledRecording,
    folderUri: String,
    store: RecordingStore,
) {
    if (rec.fileName.isBlank()) {
        Toast.makeText(context, "A gravação ainda não começou — não há arquivo para assistir", Toast.LENGTH_LONG).show()
        return
    }
    val uri: Uri? = if (rec.movedToDestination) {
        runCatching {
            var dir = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return@runCatching null
            val folder = store.folderNameFor(rec)
            if (folder.isNotBlank()) dir = dir.findFile(folder) ?: return@runCatching null
            dir.findFile(rec.fileName)?.uri
        }.getOrNull()
    } else {
        val file = RecordingService.localFileFor(context, rec)
        if (file == null) null else runCatching {
            FileProvider.getUriForFile(context, AppUpdateService.FILE_PROVIDER_AUTHORITY, file)
        }.getOrNull()
    }
    if (uri == null) {
        Toast.makeText(context, "Arquivo da gravação não encontrado", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Assistir gravação")) }
        .onFailure { Toast.makeText(context, "Nenhum player instalado para abrir este arquivo", Toast.LENGTH_LONG).show() }
}

/**
 * Página "Downloads": "Só Wi-Fi", pasta de download (SAF) e dados móveis.
 * A pasta é do aparelho (não do perfil); os contadores mostram só o que o
 * app mediu de fato (downloads .mp4 — ver MobileDataUsage). O limite é só
 * um aviso na tela: nada é bloqueado nem pausado ao atingi-lo.
 */
@Composable
private fun DownloadsPage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    highlightAnchor: String? = null,
) {
    val context = LocalContext.current
    val wifiOnly by viewModel.downloadsWifiOnly.collectAsStateWithLifecycle()
    val folderUri by viewModel.downloadFolderUri.collectAsStateWithLifecycle()
    val folderName by viewModel.downloadFolderName.collectAsStateWithLifecycle()
    val today by viewModel.mobileDataToday.collectAsStateWithLifecycle()
    val month by viewModel.mobileDataMonth.collectAsStateWithLifecycle()
    val limitMb by viewModel.mobileDataLimitMb.collectAsStateWithLifecycle()
    val limitReached by viewModel.mobileDataLimitReached.collectAsStateWithLifecycle()
    // Lote 18 (v1.28.0): streaming/torrent medidos, separados por tipo + teto do torrent.
    val streamingMonth by viewModel.mobileStreamingMonth.collectAsStateWithLifecycle()
    val torrentMonth by viewModel.mobileTorrentMonth.collectAsStateWithLifecycle()
    val torrentTempLimitMb by viewModel.torrentTempLimitMb.collectAsStateWithLifecycle()
    val torrentDiscardWatched by viewModel.torrentDiscardWatched.collectAsStateWithLifecycle()
    // Gravações (Lote 8): pasta, janelas, retenção, espaço e a lista.
    val recFolderUri by viewModel.recordingsFolderUri.collectAsStateWithLifecycle()
    val recFolderName by viewModel.recordingsFolderName.collectAsStateWithLifecycle()
    val recStartEarly by viewModel.recordStartEarlyMin.collectAsStateWithLifecycle()
    val recRunLate by viewModel.recordRunLateMin.collectAsStateWithLifecycle()
    val recKeep by viewModel.recordKeepEpisodes.collectAsStateWithLifecycle()
    val recLowSpace by viewModel.recordLowSpaceMb.collectAsStateWithLifecycle()
    val recNotifications by viewModel.recordNotifications.collectAsStateWithLifecycle()
    val recMoveToDestination by viewModel.recordMoveToDestination.collectAsStateWithLifecycle()
    val recordings by viewModel.recordingsList.collectAsStateWithLifecycle()
    // Lote 12 — Frente 2: gravações vistas (pref por perfil) e assistir.
    val recPrefs = remember { PlatformPrefs(context) }
    val recStore = remember { RecordingStore(context) }
    val moveScope = rememberCoroutineScope()
    val seenRecordings by recPrefs.recordingsSeenFlow.collectAsStateWithLifecycle()
    var recFreeMb by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { viewModel.refreshMobileData(); viewModel.refreshRecordings(); recFreeMb = viewModel.recordingsFreeMb() }

    // A pasta escolhida ainda tem permissão persistida? O backup restaura a
    // URI como texto, mas a permissão NÃO viaja — sem ela, pede p/ escolher de novo.
    val folderHasAccess = remember(folderUri) {
        folderUri.isBlank() || runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == folderUri && it.isReadPermission && it.isWritePermission
            }
        }.getOrDefault(false)
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setDownloadFolder(uri.toString(), safFolderDisplayName(context, uri))
        Toast.makeText(context, "Pasta de download alterada. Os próximos downloads concluídos vão para lá.", Toast.LENGTH_LONG).show()
    }

    // A pasta de gravações ainda tem permissão persistida? Mesmo caso da de
    // downloads: o backup restaura a URI como texto, mas a permissão NÃO viaja.
    val recFolderHasAccess = remember(recFolderUri) {
        recFolderUri.isBlank() || runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == recFolderUri && it.isReadPermission && it.isWritePermission
            }
        }.getOrDefault(false)
    }

    val pickRecordingsFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setRecordingsFolder(uri.toString(), safFolderDisplayName(context, uri))
        Toast.makeText(context, "Pasta de gravações alterada. As próximas gravações vão para lá.", Toast.LENGTH_LONG).show()
    }

    SettingsPageScaffold("Downloads", "Só Wi-Fi, pasta e dados móveis", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Dados móveis", anchor = SettingsAnchors.DL_DATA)
        SettingsCard {
            Text("Hoje: ${MobileDataUsage.formatBytes(today)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Este mês: ${MobileDataUsage.formatBytes(month)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            // Lote 18 (v1.28.0): streaming e torrent medidos, separados por tipo.
            Text("Streaming este mês: ${MobileDataUsage.formatBytes(streamingMonth)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text("Torrent este mês: ${MobileDataUsage.formatBytes(torrentMonth)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Conta só o que o app mediu em dados móveis: downloads .mp4, streaming do player e torrents — separados acima. Pôsteres e chamadas de API não entram na conta.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (limitReached) {
            SettingsCard {
                Text(
                    "Limite mensal atingido (${MobileDataUsage.formatBytes(month)} este mês). É só um aviso: nada é bloqueado nem pausado.",
                    fontSize = 14.sp, color = DangerText, lineHeight = 20.sp,
                )
            }
        }
        SettingsSection("Limite mensal", anchor = SettingsAnchors.DL_LIMIT)
        SettingsCard {
            PrefRow(
                "Avisar quando chegar a",
                listOf(0L to "Sem limite", 512L to "512 MB", 1024L to "1 GB", 2048L to "2 GB", 5120L to "5 GB", 10240L to "10 GB"),
                limitMb, viewModel::setMobileDataLimitMb,
            )
            Text(
                "É só um aviso na tela de Downloads: nada é bloqueado nem pausado ao atingir o limite.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsSection("Downloads", anchor = SettingsAnchors.DL_MAIN)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.WifiOff,
                title = "Só Wi-Fi",
                sub = if (wifiOnly) "Ligado: em dados móveis os downloads ficam na fila aguardando o Wi-Fi"
                    else "Desligado: downloads baixam em qualquer rede, como antes",
                trailing = { Switch(checked = wifiOnly, onCheckedChange = { viewModel.setDownloadsWifiOnly(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDownloadsWifiOnly(!wifiOnly) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Folder,
                title = "Pasta de download",
                sub = when {
                    folderUri.isBlank() -> "Pasta do app (padrão). Toque para escolher outra pasta."
                    !folderHasAccess -> "\"$folderName\" sem acesso — toque para escolher de novo."
                    else -> "\"$folderName\". Os downloads concluídos vão para lá."
                },
                onClick = { pickFolder.launch(null) },
                grouped = true,
                divider = true,
            )
        }
        if (folderUri.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.clearDownloadFolder() }) {
                    Text("Voltar para a pasta do app", color = Accent)
                }
            }
        }
        // Torrent — espaço temporário (Lote 18 item 2, v1.28.0): teto de
        // 32–256 MB + descarte do já assistido. Não é um ajuste do cache
        // interno do motor (o libtorrent 2.x não expõe esse ajuste — ver TorrentTempCap).
        SettingsSection("Torrent (espaço temporário)", anchor = SettingsAnchors.DL_TORRENT)
        SettingsCard {
            PrefRow(
                "Teto do espaço temporário",
                listOf(32 to "32 MB", 64 to "64 MB", 128 to "128 MB", 256 to "256 MB"),
                torrentTempLimitMb, viewModel::setTorrentTempLimitMb,
            )
            Text(
                "O torrent baixa em arquivos temporários e apaga tudo ao parar. O teto limita o quanto fica no disco enquanto assiste.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Storage,
                title = "Descartar o que já foi assistido",
                sub = if (torrentDiscardWatched) "Ligado: ao passar do teto, o app apaga do disco as peças já assistidas (volta a baixar se você voltar nelas)"
                    else "Desligado: sem descarte o teto não é cumprido — o arquivo temporário cresce até o fim da reprodução",
                trailing = { Switch(checked = torrentDiscardWatched, onCheckedChange = { viewModel.setTorrentDiscardWatched(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setTorrentDiscardWatched(!torrentDiscardWatched) },
                grouped = true,
            )
        }
        // Gravações (Lote 8, v1.18.0): pasta SAF, janelas, retenção, espaço e a lista.
        SettingsSection("Gravações", anchor = SettingsAnchors.DL_RECORD)
        SettingsCard {
            Text("Espaço livre no aparelho: ${MobileDataUsage.formatBytes(recFreeMb * 1024L * 1024L)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "A gravação pára e avisa quando o espaço livre ficar abaixo de $recLowSpace MB. O espaço é do armazenamento interno (StatFs); com pasta externa, a gravação vai para lá, mas o limite olha o aparelho.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Folder,
                title = "Pasta de gravações",
                sub = when {
                    recFolderUri.isBlank() -> "Pasta do app (padrão). Toque para escolher outra pasta."
                    !recFolderHasAccess -> "\"$recFolderName\" sem acesso — toque para escolher de novo."
                    recMoveToDestination -> "\"$recFolderName\". Grava local e move p/ lá ao concluir."
                    else -> "\"$recFolderName\". Com o mover desligado, as gravações ficam na pasta do app."
                },
                onClick = { pickRecordingsFolder.launch(null) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Notificações de gravação",
                sub = if (recNotifications) "Avisa início, fim e falha (com o erro real)"
                    else "Sem aviso: a gravação acontece, mas não avisa",
                trailing = { Switch(checked = recNotifications, onCheckedChange = { viewModel.setRecordNotifications(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setRecordNotifications(!recNotifications) },
                grouped = true,
                divider = true,
            )
            // Lote 20 (v1.30.0): grava local e move ao concluir — a gravação
            // direta em rede/USB via SAF é lenta e falha no meio.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.MoveToInbox,
                title = "Mover p/ a pasta ao concluir",
                sub = if (recMoveToDestination) "Grava na pasta do app e move p/ o destino quando termina (rede/USB)"
                    else "Desligado: a gravação fica na pasta do app, mesmo com destino escolhido",
                trailing = { Switch(checked = recMoveToDestination, onCheckedChange = { viewModel.setRecordMoveToDestination(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setRecordMoveToDestination(!recMoveToDestination) },
                grouped = true,
                divider = true,
            )
        }
        if (recFolderUri.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.clearRecordingsFolder() }) {
                    Text("Voltar para a pasta do app", color = Accent)
                }
            }
        }
        SettingsCard {
            PrefRow(
                "Começar a gravar antes",
                listOf(0 to "Na hora", 2 to "2 min", 5 to "5 min", 10 to "10 min"),
                recStartEarly, viewModel::setRecordStartEarlyMin,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Continuar gravando depois",
                listOf(0 to "Na hora", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"),
                recRunLate, viewModel::setRecordRunLateMin,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Manter gravações",
                listOf(1 to "1", 3 to "3", 5 to "5", 10 to "10", 20 to "20"),
                recKeep, viewModel::setRecordKeepEpisodes,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Avisar quando o espaço ficar abaixo de",
                listOf(256 to "256 MB", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB"),
                recLowSpace, viewModel::setRecordLowSpaceMb,
            )
            Text(
                "Acima do nº a manter, a gravação mais antiga é apagada. Com o app fechado, a gravação só começa quando o app abre — não há agendador em segundo plano.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (recordings.isEmpty()) {
            SettingsCard {
                Text(
                    "Nenhuma gravação agendada. Agende a partir de um jogo na fileira \"Próximos\" ou de um programa no guia.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
                )
            }
        } else {
            // Lote 12 — Frente 2: gravações de séries agrupadas na pasta da
            // série; as outras ficam em "Gravações". A em andamento aparece
            // como tocável (toca o arquivo parcial).
            val grouped = recordings.groupBy { it.seriesTitle.trim() }
            val orderedGroups = grouped.entries.sortedBy { (series, _) -> if (series.isBlank()) 1 else 0 }
            orderedGroups.forEachIndexed { groupIndex, (series, items) ->
                if (groupIndex > 0) Spacer(Modifier.height(12.dp))
                SettingsCard {
                    Text(
                        if (series.isBlank()) "Gravações" else series,
                        color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    items.forEachIndexed { index, rec ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        // Lote 20 (v1.30.0): miniatura do .ts (frame de 5 s).
                        var thumbFile by remember(rec.id, rec.status) { mutableStateOf<File?>(null) }
                        LaunchedEffect(rec.id, rec.status) {
                            thumbFile = withContext(Dispatchers.IO) {
                                RecordingThumbs.ensureThumbnail(context, rec, recFolderUri)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            thumbFile?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = "Miniatura de ${rec.programTitle}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(rec.programTitle, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    recordingSub(rec) + if (rec.id in seenRecordings) " · Vista" else "",
                                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                                )
                            }
                        }
                        if (rec.status == ScheduledRecording.STATUS_FAILED && rec.error.isNotBlank()) {
                            Text("Erro: ${rec.error}", color = DangerText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (rec.status == ScheduledRecording.STATUS_RECORDING || rec.status == ScheduledRecording.STATUS_DONE) {
                                TextButton(onClick = { playRecording(context, rec, recFolderUri, recStore) }) {
                                    Text(if (rec.status == ScheduledRecording.STATUS_RECORDING) "Assistir (gravando)" else "Assistir", color = Accent)
                                }
                                TextButton(onClick = { recPrefs.setRecordingSeen(rec.id, rec.id !in seenRecordings) }) {
                                    Text(if (rec.id in seenRecordings) "Vista ✓" else "Marcar como vista", color = Accent)
                                }
                            }
                            // Lote 20 (v1.30.0): mover manual p/ o destino
                            // quando a gravação ficou local (mover desligado
                            // ou falha ao mover no fim).
                            if (rec.status == ScheduledRecording.STATUS_DONE && !rec.movedToDestination && recFolderUri.isNotBlank()) {
                                TextButton(onClick = {
                                    moveScope.launch(Dispatchers.IO) {
                                        val result = RecordingService.moveNow(context, rec.id)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                result.fold(
                                                    onSuccess = { "Gravação movida p/ o destino" },
                                                    onFailure = { e -> e.message ?: "Não consegui mover" },
                                                ),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }) {
                                    Text("Mover p/ destino", color = Accent)
                                }
                            }
                            when (rec.status) {
                                ScheduledRecording.STATUS_SCHEDULED -> TextButton(onClick = { viewModel.cancelRecording(rec.id) }) { Text("Cancelar", color = Accent) }
                                ScheduledRecording.STATUS_DONE, ScheduledRecording.STATUS_FAILED, ScheduledRecording.STATUS_CANCELLED ->
                                    TextButton(onClick = { viewModel.removeRecording(rec.id) }) { Text("Apagar", color = Accent) }
                            }
                        }
                    }
                }
            }
        }
        SettingsSection("O que entra na conta", anchor = SettingsAnchors.DL_COUNTED)
        SettingsCard {
            Text(
                "Downloads .mp4: bytes medidos pelo DownloadManager enquanto a rede é móvel. " +
                    "Streaming do player, torrents, pôsteres e chamadas de API não entram — medir o player exigiria mexer no motor do player e fica para o próximo lote. " +
                    "Um download concluído com o app fechado entra na conta quando o app volta a observá-lo, com o total real, atribuído à rede daquele momento.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
        }
    }
}

// ── Layout ───────────────────────────────────────────────────────────────────

@Composable
private fun LayoutPage(
    onOpenHomeCustomize: () -> Unit,
    onOpenCollections: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    highlightAnchor: String? = null,
) {
    val display by viewModel.homeDisplay.collectAsStateWithLifecycle()
    // Lote 24 (v1.34.0): herói (item 2), abas (item 4), MK Channel e Peak
    // Cinema (itens 5 e 6). As pastas (item 3) vivem no Personalizar Início.
    val heroAutoRotate by viewModel.heroAutoRotate.collectAsStateWithLifecycle()
    val heroPreviewOnFocus by viewModel.heroPreviewOnFocus.collectAsStateWithLifecycle()
    val hiddenTabs by viewModel.bottomTabsHidden.collectAsStateWithLifecycle()
    val defaultTab by viewModel.bottomTabDefault.collectAsStateWithLifecycle()
    val mkSource by viewModel.mkChannelSource.collectAsStateWithLifecycle()
    val peakEnabled by viewModel.peakCinemaEnabled.collectAsStateWithLifecycle()
    val peakMood by viewModel.peakCinemaMood.collectAsStateWithLifecycle()
    SettingsPageScaffold("Layout", "Personalizar Início e Coleções", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Início", anchor = SettingsAnchors.LAYOUT_HOME)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Dashboard,
                iconTint = Accent,
                title = "Personalizar Início",
                sub = "Liga, desliga, ordena e renomeia as fileiras. Escolhe o destaque e o catálogo AOTV.",
                onClick = onOpenHomeCustomize,
                grouped = true,
            )
            // Coleções saem primeiro no celular. A TV ganha a tela dela na etapa seguinte.
            if (!com.aotv.app.ui.common.LocalIsTv.current) com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ViewModule,
                iconTint = Accent,
                title = "Coleções",
                sub = "Pastas de estúdios, streamings, franquias e catálogos na Início. Importa e exporta no formato do Nuvio.",
                onClick = onOpenCollections,
                grouped = true,
                divider = true,
            )
        }

        // Lote 24 (v1.34.0), itens 2, 5 e 6: herói auto-rotativo, MK Channel
        // e Peak Cinema. A rotação "Automática" é ligada no celular e só em
        // TV que não for fraca; com o modo sensorial ligado, o herói não
        // rotaciona sozinho mesmo com a rotação ligada.
        SettingsCard {
            PrefRow(
                "Rotação do herói",
                listOf("auto" to "Automática", "on" to "Sempre ligada", "off" to "Desligada"),
                heroAutoRotate, viewModel::setHeroAutoRotate,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Prévia de cena ao focar",
                sub = if (heroPreviewOnFocus) "Ao parar no destaque, o trailer começa em mudo depois de alguns segundos"
                    else "Desligada: o destaque não mostra prévia sozinho",
                trailing = { Switch(checked = heroPreviewOnFocus, onCheckedChange = { viewModel.setHeroPreviewOnFocus(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setHeroPreviewOnFocus(!heroPreviewOnFocus) },
                grouped = true,
            )
        }
        SettingsCard {
            PrefRow(
                "Fonte do MK Channel",
                listOf("up_next" to "Up next", "watchlist" to "Minha Lista", "marathon" to "Maratona"),
                mkSource, viewModel::setMkChannelSource,
            )
            Text(
                "A fileira \"liga e já tem algo tocando\", logo depois do herói. Up next usa o próximo do Continuar assistindo; Maratona, o próximo episódio da série em andamento.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Star,
                title = "Peak Cinema",
                sub = if (peakEnabled) "Fileira \"melhores cenas\" por humor, logo depois do MK Channel"
                    else "Desligado: a fileira não aparece na Início",
                trailing = { Switch(checked = peakEnabled, onCheckedChange = { viewModel.setPeakCinemaEnabled(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setPeakCinemaEnabled(!peakEnabled) },
                grouped = true,
            )
        }
        if (peakEnabled) SettingsCard {
            PrefRow(
                "Humor do Peak Cinema",
                com.aotv.app.ui.lote24.PEAK_CINEMA_PRESETS.map { it.id to it.label },
                peakMood, viewModel::setPeakCinemaMood,
            )
        }

        // Lote 24 (v1.34.0), item 4: esconder/mostrar abas da pílula e a aba
        // padrão ao abrir. A Início nunca some (se todas ficarem escondidas,
        // ela volta sozinha). A aba padrão escondida cai p/ a Início.
        SettingsSection("Navegação", anchor = SettingsAnchors.LAYOUT_NAV)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.lote24.BottomTab24.entries.forEachIndexed { index, tab ->
                val hidden = tab.id in hiddenTabs
                com.aotv.app.ui.common.SettingsRow(
                    icon = when (tab) {
                        com.aotv.app.ui.lote24.BottomTab24.HOME -> Icons.Default.Home
                        com.aotv.app.ui.lote24.BottomTab24.LIVE -> Icons.Default.LiveTv
                        com.aotv.app.ui.lote24.BottomTab24.SEARCH -> Icons.Default.Search
                        com.aotv.app.ui.lote24.BottomTab24.LIBRARY -> Icons.Default.BookmarkBorder
                        com.aotv.app.ui.lote24.BottomTab24.SPORTS -> Icons.Default.SportsSoccer
                        com.aotv.app.ui.lote24.BottomTab24.PROFILE -> Icons.Default.AccountCircle
                    },
                    title = "Mostrar aba ${tab.label}",
                    sub = if (hidden) "Escondida da pílula" else "Visível na pílula",
                    trailing = { Switch(checked = !hidden, onCheckedChange = { viewModel.setTabHidden(tab.id, !it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                    onClick = { viewModel.setTabHidden(tab.id, hidden) },
                    grouped = true,
                    divider = index < com.aotv.app.ui.lote24.BottomTab24.entries.size - 1,
                )
            }
        }
        SettingsCard {
            PrefRow(
                "Aba ao abrir o app",
                com.aotv.app.ui.lote24.BottomTab24.entries.map { it.id to it.label },
                defaultTab, viewModel::setBottomTabDefault,
            )
            Text(
                "A aba em que o app abre. Se ela estiver escondida, abre na Início.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Pôsteres e densidade (Fase B, frente compacta). O padrão é o compacto:
        // pôster pequeno, fileiras apertadas, sem selos nem etiqueta no pôster.
        // Muda na hora, sem reabrir o app. Vale para o perfil ativo.
        SettingsSection("Pôsteres", anchor = SettingsAnchors.LAYOUT_POSTERS)
        SettingsCard {
            PrefRow(
                "Tamanho do pôster",
                listOf(
                    com.aotv.app.data.local.PosterSize.SMALL to "Pequeno",
                    com.aotv.app.data.local.PosterSize.MEDIUM to "Médio",
                    com.aotv.app.data.local.PosterSize.LARGE to "Grande",
                ),
                display.posterSize, viewModel::setPosterSize,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Raio dos cantos",
                listOf(8 to "Pequeno", 14 to "Médio", 20 to "Grande"),
                display.posterRadiusDp, viewModel::setPosterRadius,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Densidade",
                listOf(
                    com.aotv.app.data.local.HomeDensity.COMPACT to "Compacta",
                    com.aotv.app.data.local.HomeDensity.COMFORTABLE to "Confortável",
                ),
                display.density, viewModel::setHomeDensity,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Mostrar selos nos pôsteres",
                sub = if (display.showPosterBadges) "Etiqueta do topo visível (ranking, estreia, idioma)"
                    else "Sem etiqueta no topo. Liga para ver a etiqueta de novo",
                // A linha liga e desliga. O Switch fica fora do caminho do controle.
                trailing = { Switch(checked = display.showPosterBadges, onCheckedChange = { viewModel.setShowPosterBadges(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setShowPosterBadges(!display.showPosterBadges) },
                grouped = true,
            )
            // Selos de qualidade/fonte (Lote 11, v1.21.0): 4K, Remux, BluRay,
            // WEB-DL, DV, HDR10+ e DUB no canto do pôster. LIGADOS por padrão
            // (decisão do Davi) — diferente da etiqueta acima (Fase B,
            // desligada por padrão). Só aparecem quando o resumo das fontes
            // do título existe: sem fonte lida, sem selo inventado.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Selos de qualidade e fonte",
                sub = if (display.showQualityBadges) "4K, Remux, BluRay, WEB-DL, DV, HDR10+ e DUB no canto do pôster"
                    else "Sem selos de qualidade no pôster. Liga para ver de novo",
                trailing = { Switch(checked = display.showQualityBadges, onCheckedChange = { viewModel.setShowQualityBadges(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setShowQualityBadges(!display.showQualityBadges) },
                grouped = true,
                divider = true,
            )
        }

        // Destaque do topo.
        SettingsSection("Destaque", anchor = SettingsAnchors.LAYOUT_HERO)
        SettingsCard {
            PrefRow(
                "Estilo do herói",
                listOf(
                    com.aotv.app.data.local.HeroStyle.FULL_BLEED to "Tela cheia",
                    com.aotv.app.data.local.HeroStyle.CARD to "Cartão",
                ),
                display.heroStyle, viewModel::setHeroStyle,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Fundo dinâmico",
                sub = if (display.dynamicBackground) "A Início ganha um gradiente com as cores do destaque"
                    else "Fundo sólido de sempre. Liga para tingir com as cores do destaque",
                trailing = { Switch(checked = display.dynamicBackground, onCheckedChange = { viewModel.setDynamicBackground(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDynamicBackground(!display.dynamicBackground) },
                grouped = true,
            )
        }

        // Títulos das seções.
        SettingsSection("Títulos das seções", anchor = SettingsAnchors.LAYOUT_TITLES)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.CheckCircle,
                title = "Sublinhado de destaque",
                sub = if (display.sectionUnderline) "Barra na cor do tema abaixo do título de cada seção"
                    else "Sem barra abaixo dos títulos das seções",
                trailing = { Switch(checked = display.sectionUnderline, onCheckedChange = { viewModel.setSectionUnderline(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSectionUnderline(!display.sectionUnderline) },
                grouped = true,
            )
        }

        // Continuar assistindo novo + Modo aleatório (Lote 2, v1.10.0). O que o
        // sorteio pode pegar: séries do Continuar assistindo e/ou da Minha Lista
        // do perfil ativo. Filmes nunca entram (só episódios de série).
        SettingsSection("Continuar assistindo e Modo aleatório", anchor = SettingsAnchors.LAYOUT_RANDOM)
        val randomContinue by viewModel.randomIncludeContinue.collectAsStateWithLifecycle()
        val randomWatchlist by viewModel.randomIncludeWatchlist.collectAsStateWithLifecycle()
        val randomUnwatched by viewModel.randomUnwatchedOnly.collectAsStateWithLifecycle()
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.History,
                title = "Sortear do Continuar assistindo",
                sub = if (randomContinue) "Séries em andamento entram no sorteio"
                    else "Séries em andamento ficam fora do sorteio",
                trailing = { Switch(checked = randomContinue, onCheckedChange = { viewModel.setRandomIncludeContinue(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setRandomIncludeContinue(!randomContinue) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.BookmarkBorder,
                title = "Sortear da Minha Lista",
                sub = if (randomWatchlist) "Séries salvas entram no sorteio"
                    else "Séries salvas ficam fora do sorteio",
                trailing = { Switch(checked = randomWatchlist, onCheckedChange = { viewModel.setRandomIncludeWatchlist(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setRandomIncludeWatchlist(!randomWatchlist) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VisibilityOff,
                title = "Só episódios não vistos",
                sub = if (randomUnwatched) "Se tudo já foi visto, o sorteio avisa em vez de tocar um repetido"
                    else "Um episódio já visto pode sair no sorteio (o app tenta evitá-lo primeiro)",
                trailing = { Switch(checked = randomUnwatched, onCheckedChange = { viewModel.setRandomUnwatchedOnly(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setRandomUnwatchedOnly(!randomUnwatched) },
                grouped = true,
                divider = true,
            )
        }

        // Esportes e Calendário (Lote 8, v1.18.0). Os placares vêm SÓ do texto
        // do EPG — sem placar no texto, sem placar na tela.
        SettingsSection("Esportes e Calendário", anchor = SettingsAnchors.LAYOUT_SPORTS)
        val sportsShow by viewModel.sportsShow.collectAsStateWithLifecycle()
        val sportsLiveScores by viewModel.sportsLiveScores.collectAsStateWithLifecycle()
        val sportsHideScores by viewModel.sportsHideScores.collectAsStateWithLifecycle()
        val sportsScoreDelay by viewModel.sportsScoreDelaySec.collectAsStateWithLifecycle()
        val sportsAlerts by viewModel.sportsGameAlerts.collectAsStateWithLifecycle()
        val hideSpoilers by viewModel.calendarHideSpoilers.collectAsStateWithLifecycle()
        val autoAddTrackers by viewModel.calendarAutoAddTrackers.collectAsStateWithLifecycle()
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SportsSoccer,
                title = "Mostrar esportes",
                sub = if (sportsShow) "Seção de esportes no Ao vivo e jogos na fileira \"Próximos\""
                    else "Sem seção de esportes nem jogos na Início",
                trailing = { Switch(checked = sportsShow, onCheckedChange = { viewModel.setSportsShow(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSportsShow(!sportsShow) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.LiveTv,
                title = "Placares ao vivo nos canais",
                sub = if (sportsLiveScores) "O placar do EPG aparece no canal de esporte"
                    else "Canais de esporte sem placar",
                trailing = { Switch(checked = sportsLiveScores, onCheckedChange = { viewModel.setSportsLiveScores(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSportsLiveScores(!sportsLiveScores) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VisibilityOff,
                title = "Esconder placares",
                sub = if (sportsHideScores) "Nenhum placar aparece (anti-spoiler)"
                    else "O placar aparece quando o guia traz no texto",
                trailing = { Switch(checked = sportsHideScores, onCheckedChange = { viewModel.setSportsHideScores(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSportsHideScores(!sportsHideScores) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Alertas de jogo",
                sub = if (sportsAlerts) "Avisa quando um jogo começa, mesmo com o app fechado"
                    else "Sem aviso quando o jogo começa",
                trailing = { Switch(checked = sportsAlerts, onCheckedChange = { viewModel.setSportsGameAlerts(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSportsGameAlerts(!sportsAlerts) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.CalendarMonth,
                title = "Esconder spoilers do calendário",
                sub = if (hideSpoilers) "O episódio aparece sem T/E até ir ao ar"
                    else "T/E do episódio visível na fileira \"Próximos\"",
                trailing = { Switch(checked = hideSpoilers, onCheckedChange = { viewModel.setCalendarHideSpoilers(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setCalendarHideSpoilers(!hideSpoilers) },
                grouped = true,
                divider = true,
            )
            // Auto-add do calendário (Lote 13, v1.23.0): séries do Trakt/Simkl
            // entram no calendário sozinhas, sem estar na Minha Lista.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Sync,
                title = "Séries do Trakt/Simkl no calendário",
                sub = if (autoAddTrackers) "Séries acompanhadas no Trakt/Simkl entram no calendário sozinhas"
                    else "Só Minha Lista e Continuar assistindo entram no calendário",
                trailing = { Switch(checked = autoAddTrackers, onCheckedChange = { viewModel.setCalendarAutoAddTrackers(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setCalendarAutoAddTrackers(!autoAddTrackers) },
                grouped = true,
                divider = true,
            )
        }
        SettingsCard {
            PrefRow(
                "Atraso do placar (anti-spoiler)",
                listOf(0 to "Na hora", 15 to "15 s", 30 to "30 s", 60 to "1 min", 120 to "2 min"),
                sportsScoreDelay, viewModel::setSportsScoreDelaySec,
            )
            Text(
                "O placar só aparece esse tempo depois do início do jogo — e só quando o guia traz o placar no texto. Sem placar no texto, o jogo aparece sem resultado.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ── Página de detalhes (Fase B) ─────────────────────────────────────────────

@Composable
private fun DetalhePage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    highlightAnchor: String? = null,
) {
    val cinematic by viewModel.detailCinematic.collectAsStateWithLifecycle()
    val posterTransition by viewModel.detailPosterTransition.collectAsStateWithLifecycle()
    val iconActions by viewModel.detailIconActions.collectAsStateWithLifecycle()
    val tabs by viewModel.detailTabs.collectAsStateWithLifecycle()
    val episodeStyle by viewModel.detailEpisodeStyle.collectAsStateWithLifecycle()
    val episodeRatings by viewModel.detailEpisodeRatings.collectAsStateWithLifecycle()
    // Blur anti-spoiler (Lote 11, v1.21.0): episódios não assistidos sem arte/título/sinopse.
    val spoilerBlur by viewModel.detailSpoilerBlur.collectAsStateWithLifecycle()
    val originalTitle by viewModel.detailOriginalTitle.collectAsStateWithLifecycle()
    val showAggregateRating by viewModel.detailShowAggregateRating.collectAsStateWithLifecycle()
    // Fonte da nota exibida (Lote 12 — Frente 3, v1.22.0).
    val ratingsSource by viewModel.ratingsSource.collectAsStateWithLifecycle()
    // Lote 24 (v1.34.0), item 12: fonte da nota dos EPISÓDIOS + chave do OMDb.
    val episodeRatingMode by viewModel.episodeRatingMode.collectAsStateWithLifecycle()
    val omdbKey by viewModel.omdbApiKey.collectAsStateWithLifecycle()
    var omdbDraft by remember(omdbKey) { mutableStateOf(omdbKey) }
    var omdbVisible by remember(omdbKey) { mutableStateOf(false) }
    val showTraktReviews by viewModel.detailShowTraktReviews.collectAsStateWithLifecycle()
    val showQuotes by viewModel.detailShowQuotes.collectAsStateWithLifecycle()
    SettingsPageScaffold("Página de detalhes", "Fundo, abas e ações do título", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Fundo e entrada", anchor = SettingsAnchors.DETAIL_BG)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Fundo cinematográfico",
                sub = "Arte desfocada em tela cheia atrás da página. Desligado, fica o backdrop no topo.",
                trailing = { Switch(checked = cinematic, onCheckedChange = { viewModel.setDetailCinematic(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailCinematic(!cinematic) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Transição do pôster",
                sub = "O pôster cresce e aparece ao abrir os detalhes",
                trailing = { Switch(checked = posterTransition, onCheckedChange = { viewModel.setDetailPosterTransition(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailPosterTransition(!posterTransition) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Ações e organização", anchor = SettingsAnchors.DETAIL_ACTIONS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Dashboard,
                title = "Ações em ícones",
                sub = "Minha Lista e Trailer viram ícones com rótulo abaixo do Reproduzir, em vez dos botões em vidro",
                trailing = { Switch(checked = iconActions, onCheckedChange = { viewModel.setDetailIconActions(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailIconActions(!iconActions) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ViewModule,
                title = "Layout de abas",
                sub = "Episódios, Elenco e Mais como isto em abas. Desligado, tudo numa rolagem só.",
                trailing = { Switch(checked = tabs, onCheckedChange = { viewModel.setDetailTabs(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailTabs(!tabs) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Título original", anchor = SettingsAnchors.DETAIL_ORIGINAL)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Translate,
                title = "Título em idioma original",
                sub = if (originalTitle) "Mostra o título original do TMDB numa linha abaixo do título traduzido na página de detalhes"
                    else "Só o título traduzido. Ligado, mostra também o original (ex.: o nome em inglês) abaixo dele",
                trailing = { Switch(checked = originalTitle, onCheckedChange = { viewModel.setDetailOriginalTitle(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailOriginalTitle(!originalTitle) },
                grouped = true,
            )
        }
        SettingsSection("Episódios", anchor = SettingsAnchors.DETAIL_EPISODES)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Star,
                title = "Mostrar notas dos episódios",
                sub = if (episodeRatings) "A nota do TMDB aparece nos cards de episódio, no mapa de notas e no painel de episódios do player"
                    else "Sem notas: os cards mostram só data e duração, e o mapa de notas fica oculto",
                trailing = { Switch(checked = episodeRatings, onCheckedChange = { viewModel.setDetailEpisodeRatings(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailEpisodeRatings(!episodeRatings) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VisibilityOff,
                title = "Blur anti-spoiler",
                sub = if (spoilerBlur) "Episódios não assistidos ficam com a arte borrada e sem título nem sinopse (abaixo do Android 12, véu escuro no lugar do blur)"
                    else "Desligado: os episódios mostram arte, título e sinopse normalmente",
                trailing = { Switch(checked = spoilerBlur, onCheckedChange = { viewModel.setDetailSpoilerBlur(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailSpoilerBlur(!spoilerBlur) },
                grouped = true,
            )
        }
        SettingsCard {
            PrefRow(
                "Estilo do card de episódio",
                listOf("horizontal" to "Horizontal com arte", "stacked" to "Empilhado com texto"),
                episodeStyle, viewModel::setDetailEpisodeStyle,
            )
        }
        SettingsSection("Nota agregada", anchor = SettingsAnchors.DETAIL_RATINGS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Star,
                title = "Mostrar nota agregada",
                sub = if (showAggregateRating) "Nota de 0 a 100 com as notas por fonte (MDBList). Sem fonte com nota, a seção não aparece"
                    else "Sem nota agregada na página de detalhes",
                trailing = { Switch(checked = showAggregateRating, onCheckedChange = { viewModel.setDetailShowAggregateRating(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailShowAggregateRating(!showAggregateRating) },
                grouped = true,
            )
        }
        // Lote 12 — Frente 3 (Notas, v1.22.0): fonte da nota exibida de
        // filmes e séries. O MDBList não tem nota por episódio, por isso a
        // nota DO EPISÓDIO segue a pref do Lote 24 abaixo (item 12). Sem nota
        // MDBList p/ o título, o TMDB assume como fallback — nunca se inventa nota.
        SettingsCard {
            PrefRow(
                "Fonte da nota exibida",
                listOf(
                    "auto" to "Automática (MDBList quando conectado)",
                    "mdblist" to "MDBList",
                    "tmdb" to "TMDB",
                ),
                ratingsSource, viewModel::setRatingsSource,
            )
            Text(
                "A nota de filmes e séries na página de detalhes. Automática usa a nota agregada do MDBList quando há chave configurada e ele está ligado; sem chave, ou sem nota p/ o título, vale a do TMDB.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        // Lote 24 (v1.34.0), item 12: fonte da nota dos EPISÓDIOS. A média
        // soma só as fontes que têm nota p/ o episódio; sem nota em nenhuma,
        // fica "sem nota". Fonte única não cai p/ outra fonte quando a
        // escolhida não tem nota. A ordenação e as cores do navegador de
        // episódios usam a nota resultante daqui.
        SettingsCard {
            PrefRow(
                "Nota dos episódios",
                listOf(
                    "average" to "Média das fontes com nota",
                    "tmdb" to "Só TMDB",
                    "trakt" to "Só Trakt",
                    "omdb" to "Só OMDb (IMDb)",
                    "tvmaze" to "Só TVmaze",
                ),
                episodeRatingMode, viewModel::setEpisodeRatingMode,
            )
            Text(
                "De onde vem a nota do episódio (0–10) nos cards, no mapa de notas e no painel do player. Trakt só vale conectado; OMDb só com a chave abaixo (grátis, 1000 consultas/dia); TVmaze é grátis e sem chave. A origem aparece no card (ex.: \"8,4 · média de 3 fontes\").",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsCard {
            Text(
                if (omdbKey.isBlank()) "Sem chave do OMDb: a fonte OMDb fica fora da média e o modo \"Só OMDb\" fica sem nota. A chave é sua (grátis em omdbapi.com) e fica só neste aparelho."
                    else "Chave do OMDb configurada. A fonte entra na média e no modo \"Só OMDb\".",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                    OutlinedTextField(
                        value = omdbDraft,
                        onValueChange = { omdbDraft = it },
                        label = { Text("Sua chave do OMDb") },
                        placeholder = { Text("Cole sua chave aqui", color = TextTertiary) },
                        visualTransformation = if (omdbVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = gate,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Border,
                            focusedContainerColor = Surface2,
                            unfocusedContainerColor = Surface2,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Accent,
                            unfocusedLabelColor = TextTertiary,
                            cursorColor = Accent,
                        ),
                    )
                }
                IconButton(
                    onClick = { omdbVisible = !omdbVisible }, enabled = omdbDraft.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (omdbVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (omdbVisible) "Ocultar chave do OMDb" else "Mostrar chave do OMDb",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.aotv.app.ui.common.AotvButton(
                    "Salvar chave", onClick = {
                        viewModel.setOmdbApiKey(omdbDraft)
                        omdbDraft = viewModel.omdbApiKey.value
                        omdbVisible = false
                    },
                    enabled = omdbDraft.isNotBlank() && omdbDraft.trim() != omdbKey,
                    compact = true, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                )
                if (omdbKey.isNotBlank()) com.aotv.app.ui.common.AotvButton(
                    "Remover chave", onClick = { viewModel.setOmdbApiKey(""); omdbDraft = ""; omdbVisible = false },
                    compact = true,
                )
            }
        }
        SettingsSection("O que estão dizendo", anchor = SettingsAnchors.DETAIL_REVIEWS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Comment,
                title = "Mostrar avaliações do Trakt",
                sub = if (showTraktReviews) "Avaliações do Trakt na página de detalhes, com abas Série e Episódio"
                    else "Sem avaliações do Trakt na página de detalhes",
                trailing = { Switch(checked = showTraktReviews, onCheckedChange = { viewModel.setDetailShowTraktReviews(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailShowTraktReviews(!showTraktReviews) },
                grouped = true,
            )
        }
        SettingsSection("Frases e ficha de produção", anchor = SettingsAnchors.DETAIL_QUOTES)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.FormatQuote,
                title = "Mostrar frases e ficha",
                sub = if (showQuotes) "Frases do Wikiquote e resumo da Wikipédia. Sem frase ou página, a seção não aparece"
                    else "Sem frases nem ficha na página de detalhes",
                trailing = { Switch(checked = showQuotes, onCheckedChange = { viewModel.setDetailShowQuotes(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailShowQuotes(!showQuotes) },
                grouped = true,
            )
        }
    }
}

// ── Streams (Fase C) ─────────────────────────────────────────────────────────

@Composable
private fun StreamsPage(
    onBack: () -> Unit,
    viewModel: StreamPrefsViewModel = hiltViewModel(),
    highlightAnchor: String? = null,
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val badgesSize by viewModel.badgesSize.collectAsStateWithLifecycle()
    val badgesCodec by viewModel.badgesCodec.collectAsStateWithLifecycle()
    val badgesLang by viewModel.badgesLang.collectAsStateWithLifecycle()
    val badgesPos by viewModel.badgesPos.collectAsStateWithLifecycle()
    val logo by viewModel.logo.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val logoBadgesEnabled by viewModel.badgePackEnabled.collectAsStateWithLifecycle()
    val packBadges by viewModel.packBadges.collectAsStateWithLifecycle()
    var showBadgePack by remember { mutableStateOf(false) }
    SettingsPageScaffold("Streams", "Pesquisa, emblemas, logo e fontes fixadas", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Folha de fontes", anchor = SettingsAnchors.STREAMS_SHEET)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Search,
                title = "Barra de pesquisa nas fontes",
                sub = if (search) "Filtra as fontes digitando o nome do addon"
                    else "Sem barra de pesquisa. A folha mostra todas as fontes",
                trailing = { Switch(checked = search, onCheckedChange = { viewModel.setSearch(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSearch(!search) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Image,
                title = "Logo do addon",
                sub = if (logo) "Mostra o logo do addon ao lado do nome da fonte"
                    else "Só o nome da fonte, sem logo",
                trailing = { Switch(checked = logo, onCheckedChange = { viewModel.setLogo(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLogo(!logo) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Emblemas", anchor = SettingsAnchors.STREAMS_BADGES)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Mostrar tamanho",
                sub = "Emblema com o tamanho do arquivo (ex.: 2,4 GB), quando a fonte informa",
                trailing = { Switch(checked = badgesSize, onCheckedChange = { viewModel.setBadgesSize(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgesSize(!badgesSize) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Mostrar codec",
                sub = "Emblema com o codec (ex.: HEVC, AV1), quando a fonte informa",
                trailing = { Switch(checked = badgesCodec, onCheckedChange = { viewModel.setBadgesCodec(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgesCodec(!badgesCodec) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Mostrar idioma",
                sub = "Emblema Dublado ou Legendado em cada fonte",
                trailing = { Switch(checked = badgesLang, onCheckedChange = { viewModel.setBadgesLang(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgesLang(!badgesLang) },
                grouped = true,
                divider = true,
            )
        }
        SettingsCard {
            PrefRow(
                "Posição dos emblemas",
                listOf("stats" to "No canto", "pills" to "Na linha de pills"),
                badgesPos, viewModel::setBadgesPos,
            )
        }
        // ── Selos com logo (Lote 19 item 9, v1.29.0) ─────────────────────
        // Pack de selos em JSON por URL, com prévia por categoria. Sem pack,
        // a folha usa os selos de texto de sempre — o toggle só vale com pack.
        SettingsSection("Selos com logo", anchor = SettingsAnchors.STREAMS_LOGO_BADGES)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Image,
                title = "Selos com logo na folha de fontes",
                sub = if (logoBadgesEnabled) "Com pack carregado, os selos mostram o logo (4K, Dolby Vision, DTS-HD…); sem pack, ficam os selos de texto"
                    else "Só os selos de texto, como hoje",
                trailing = { Switch(checked = logoBadgesEnabled, onCheckedChange = { viewModel.setBadgePackEnabled(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgePackEnabled(!logoBadgesEnabled) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Link,
                title = "Pack de selos (URL)",
                sub = if (packBadges.isEmpty()) "Nenhum pack carregado — toque para colar a URL do pack"
                    else "${packBadges.size} selos carregados — toque para ver a prévia ou trocar a URL",
                onClick = { showBadgePack = true },
                grouped = true,
                divider = true,
            )
        }
        if (showBadgePack) BadgePackPreviewDialog(viewModel = viewModel, onDismiss = { showBadgePack = false })
        SettingsSection("Fontes fixadas", anchor = SettingsAnchors.STREAMS_PINNED)
        Text(
            "Para fixar uma fonte, toque no pin ao lado do nome dela na folha de fontes. As fixadas aparecem primeiro.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        if (pinned.isEmpty()) {
            SettingsCard {
                Text("Nenhuma fonte fixada", fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
            }
        } else {
            com.aotv.app.ui.common.SettingsGroup {
                pinned.forEachIndexed { i, name ->
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.PushPin,
                        title = name,
                        sub = "Aparece primeiro na folha de fontes",
                        trailing = { TextButton(onClick = { viewModel.unpin(name) }) { Text("Desfixar", color = DangerText) } },
                        grouped = true,
                        divider = i > 0,
                    )
                }
            }
        }
        // ── Ranking de fontes (Lote 17, v1.27.0) ────────────────────────
        // Score 0–100 com motivo, preset, estratégia, filtro de idioma e
        // ordem manual — o mesmo ranking do detalhe e da folha do player.
        val preset by viewModel.sourcePreset.collectAsStateWithLifecycle()
        val strategy by viewModel.sourceStrategy.collectAsStateWithLifecycle()
        val langFilter by viewModel.sourceLangFilter.collectAsStateWithLifecycle()
        val originalFirst by viewModel.sourceOriginalFirst.collectAsStateWithLifecycle()
        val manualOrder by viewModel.sourceManualOrder.collectAsStateWithLifecycle()
        val hideUnplayable by viewModel.sourceHideUnplayable.collectAsStateWithLifecycle()
        val rememberPick by viewModel.sourceRememberPick.collectAsStateWithLifecycle()
        val sourceFormat by viewModel.sourceFormat.collectAsStateWithLifecycle()
        SettingsSection("Ranking de fontes", anchor = SettingsAnchors.STREAMS_RANKING)
        Text(
            "Cada fonte ganha um score de 0 a 100 com o motivo (ex.: \"#1 · 84 — 1080p · rápido · 0 falhas\"). O mesmo score vale no detalhe e na folha do player.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        SettingsCard {
            PrefRow(
                "Preset",
                listOf("best" to "Melhor imagem", "balanced" to "Equilibrado", "fast" to "Início rápido"),
                preset, viewModel::setSourcePreset,
            )
            PrefRow(
                "Estratégia do ranking",
                listOf("quality" to "Qualidade", "speed" to "Rapidez", "reliability" to "Confiabilidade", "language" to "Idioma"),
                strategy, viewModel::setSourceStrategy,
            )
            PrefRow(
                "Idioma das fontes",
                listOf("" to "Todos", "dub" to "Dublado", "leg" to "Legendado", "original" to "Original"),
                langFilter, viewModel::setSourceLangFilter,
            )
            PrefRow(
                "Formato preferido",
                listOf("auto" to "Automático", "hls" to "HLS", "mp4" to "MP4"),
                sourceFormat, viewModel::setSourceFormat,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Translate,
                title = "Original primeiro",
                sub = if (originalFirst) "Fontes sem marca de dublado/legendado ganham pontos no ranking"
                    else "O áudio original não ganha ponto extra",
                trailing = { Switch(checked = originalFirst, onCheckedChange = { viewModel.setSourceOriginalFirst(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSourceOriginalFirst(!originalFirst) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Block,
                title = "Esconder o que o aparelho não toca",
                sub = if (hideUnplayable) "Fontes AV1/HEVC sem suporte neste aparelho não aparecem na lista"
                    else "Todas as fontes aparecem, mesmo sem suporte a codec",
                trailing = { Switch(checked = hideUnplayable, onCheckedChange = { viewModel.setSourceHideUnplayable(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSourceHideUnplayable(!hideUnplayable) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.History,
                title = "Lembrar minha escolha por título",
                sub = if (rememberPick) "A última fonte escolhida à mão ganha pontos no ranking daquele título"
                    else "A última escolha não influencia o ranking",
                trailing = { Switch(checked = rememberPick, onCheckedChange = { viewModel.setSourceRememberPick(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSourceRememberPick(!rememberPick) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Ordem manual das fontes", anchor = SettingsAnchors.STREAMS_RANKING)
        Text(
            "A ordem manual vale por cima do ranking. Use as setas para reordenar; um addon fora da lista segue só pelo score.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        if (manualOrder.isEmpty()) {
            SettingsCard {
                Text("Sem ordem manual — vale só o ranking.", fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
            }
        } else {
            com.aotv.app.ui.common.SettingsGroup {
                manualOrder.forEachIndexed { i, name ->
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.FormatListNumbered,
                        title = "${i + 1}. $name",
                        sub = "Ordem manual",
                        trailing = {
                            Row {
                                IconButton(onClick = { viewModel.moveManualSource(name, -1) }, enabled = i > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = TextSecondary)
                                }
                                IconButton(onClick = { viewModel.moveManualSource(name, 1) }, enabled = i < manualOrder.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Descer", tint = TextSecondary)
                                }
                                TextButton(onClick = { viewModel.removeManualSource(name) }) { Text("Tirar", color = DangerText) }
                            }
                        },
                        grouped = true,
                        divider = i > 0,
                    )
                }
            }
        }
        // Adicionar: um addon fixado que ainda não está na ordem manual.
        val addable = pinned.filter { it !in manualOrder }
        if (addable.isNotEmpty()) {
            com.aotv.app.ui.common.SettingsGroup {
                addable.forEachIndexed { i, name ->
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.Add,
                        title = "Adicionar $name",
                        sub = "Põe no fim da ordem manual",
                        trailing = { TextButton(onClick = { viewModel.addManualSource(name) }) { Text("Adicionar", color = Accent) } },
                        grouped = true,
                        divider = i > 0,
                    )
                }
            }
        }
    }
}

// ── Conteúdo & Descoberta ────────────────────────────────────────────────────

@Composable
private fun ConteudoPage(
    onOpenAddons: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenKodiAddons: () -> Unit,
    onOpenDebrid: () -> Unit,
    onOpenMediaServers: () -> Unit,
    // Lote 25 (v1.35.0) — Frente B: saúde dos addons.
    onOpenAddonHealth: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    highlightAnchor: String? = null,
) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val addonCount = addons.size
    val sourceStatus by viewModel.sourceStatus.collectAsStateWithLifecycle()
    val checkingHealth by viewModel.checkingHealth.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (sourceStatus.isEmpty()) viewModel.checkSourcesHealth() }
    // Lote 23: ordem da Minha Lista, período das Estatísticas e gêneros bloqueados.
    val myListOrder by viewModel.myListOrder.collectAsStateWithLifecycle()
    val statsPeriod by viewModel.statsPeriod.collectAsStateWithLifecycle()
    val blockedGenres by viewModel.blockedGenres.collectAsStateWithLifecycle()
    SettingsPageScaffold("Conteúdo & Descoberta", "Addons, plugins, fontes e Biblioteca", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Addons", anchor = SettingsAnchors.CONTENT_ADDONS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Extension,
                iconTint = Accent,
                title = "Gerenciar addons",
                sub = (if (addonCount == 0) "Nenhum instalado" else "$addonCount instalado(s)") +
                    ". As fontes vêm dos addons. Instale, ordene e ative cada um.",
                onClick = onOpenAddons,
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Code,
                iconTint = Accent,
                title = "Plugins do Nuvio",
                sub = "Scrapers JavaScript (ex.: os do seu repositório). Instale por manifest, ative cada um e ajuste o timeout.",
                onClick = onOpenPlugins,
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Extension,
                iconTint = Accent,
                title = "Addons Kodi",
                sub = "Instale addons Kodi por ZIP ou URL, ative cada um e ajuste as configurações.",
                onClick = onOpenKodiAddons,
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Bolt,
                iconTint = Accent,
                title = "Debrid",
                sub = "Real-Debrid, TorBox, Premiumize ou AllDebrid. Torrent toca na hora por link direto.",
                onClick = onOpenDebrid,
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Dns,
                iconTint = Accent,
                title = "Servidores de mídia",
                sub = "Jellyfin, Emby e Plex. Sua coleção no app, com a sua conta.",
                onClick = onOpenMediaServers,
                grouped = true,
                divider = true,
            )
            // Lote 25 (v1.35.0) — Frente B, item 6: saúde dos addons.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Speed,
                iconTint = Accent,
                title = "Saúde dos addons",
                sub = "Latência de cada manifest, o que está lento, Addon Studio e Addon Lab.",
                onClick = onOpenAddonHealth,
                grouped = true,
            )
        }

        // Status das fontes. Chama a rede, então gira e trava enquanto verifica.
        SettingsSection("Status das fontes", anchor = SettingsAnchors.CONTENT_STATUS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = if (checkingHealth) "Verificando…" else "Verificar fontes agora",
                sub = "Confere se o TMDB e os addons estão online (DNS Cloudflare)",
                busy = checkingHealth,
                trailing = null,
                onClick = { viewModel.checkSourcesHealth() },
                grouped = true,
            )
        }
        if (sourceStatus.isNotEmpty()) SettingsCard {
            sourceStatus.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(if (s.online) Ok else Danger))
                    Text(s.name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        if (s.online) "${s.latencyMs} ms" else s.detail,
                        color = if (s.online) TextTertiary else DangerText, fontSize = 13.sp, maxLines = 1,
                    )
                }
            }
        }

        // Biblioteca fica aqui dentro (decisão da Frente 2): o hub continua com as 8 páginas
        // da fork e a limpeza do Continuar assistindo mora junto do conteúdo.
        SettingsSection("Biblioteca", anchor = SettingsAnchors.CONTENT_LIBRARY)
        var confirmClear by remember { mutableStateOf(false) }
        val context = LocalContext.current
        com.aotv.app.ui.common.SettingsRow(
            icon = Icons.Default.History,
            title = "Limpar Continuar assistindo",
            sub = "Apaga posições salvas de filmes e séries. Minha Lista continua",
            onClick = { confirmClear = true },
        )
        if (confirmClear) AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = BackgroundCard,
            title = { Text("Limpar Continuar assistindo?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("As posições salvas somem. Minha Lista continua.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearContinueWatching()
                    confirmClear = false
                    Toast.makeText(context, "Histórico limpo", Toast.LENGTH_SHORT).show()
                }) { Text("Limpar", color = DangerText) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = TextSecondary) } },
        )
        // Lote 23 (v1.33.0): ordem da Minha Lista, período das Estatísticas
        // e gêneros bloqueados — tudo do perfil ativo.
        SettingsCard {
            PrefRow(
                "Minha Lista: ordem",
                listOf("recent" to "Recentes", "name" to "Nome", "unwatched" to "Não assistidos"),
                myListOrder, viewModel::setMyListOrder,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Estatísticas: período",
                listOf("7d" to "7 dias", "30d" to "30 dias", "90d" to "90 dias", "all" to "Tudo"),
                statsPeriod, viewModel::setStatsPeriod,
            )
            Text(
                "A tela Estatísticas fica na Biblioteca, junto dos Downloads.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("Gêneros bloqueados", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            // GENRE_SHORT (ui.common) tem ids de filme e de TV com o mesmo
            // rótulo (28 e 10759 = "Ação"): o chip bloqueia todos os ids do
            // rótulo de uma vez; o filtro vale p/ Início, Busca e Filmes.
            val genreGroups = remember {
                com.aotv.app.ui.common.GENRE_SHORT.entries
                    .groupBy({ it.value }, { it.key }).entries.toList()
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genreGroups.forEach { (label, ids) ->
                    val on = ids.all { it in blockedGenres }
                    com.aotv.app.ui.common.FilterChipPill(label, on, onClick = {
                        viewModel.setBlockedGenres(
                            if (on) blockedGenres - ids.toSet() else blockedGenres + ids,
                        )
                    })
                }
            }
            Text(
                if (blockedGenres.isEmpty()) "Nenhum gênero bloqueado."
                else "${blockedGenres.size} ${if (blockedGenres.size == 1) "gênero bloqueado" else "gêneros bloqueados"} — escondidos de Início, Busca e Filmes.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ── Integrações ──────────────────────────────────────────────────────────────

@Composable
private fun IntegracoesPage(
    onOpenAccounts: () -> Unit,
    onOpenLive: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    highlightAnchor: String? = null,
    // Lote 12 (v1.22.0): abre o gerenciador de canais.
    onOpenLiveChannels: () -> Unit = {},
) {
    // A chave TMDB saiu daqui (Lote 6, v1.15.0): agora mora em Acompanhamento,
    // junto dos trackers. Esta página fica só com contas e TV ao vivo.
    // Lote 8 (v1.18.0): opções da TV ao vivo, por perfil (o formato Xtream é global).
    val liveGuideDaysAhead by viewModel.liveGuideDaysAhead.collectAsStateWithLifecycle()
    val liveGuideHoursBack by viewModel.liveGuideHoursBack.collectAsStateWithLifecycle()
    val liveRefreshListsHours by viewModel.liveRefreshListsHours.collectAsStateWithLifecycle()
    val liveRefreshEpgHours by viewModel.liveRefreshEpgHours.collectAsStateWithLifecycle()
    val liveShowLogos by viewModel.liveShowLogos.collectAsStateWithLifecycle()
    val liveShowNames by viewModel.liveShowNames.collectAsStateWithLifecycle()
    val liveShowNumbers by viewModel.liveShowNumbers.collectAsStateWithLifecycle()
    val liveCleanGroupNames by viewModel.liveCleanGroupNames.collectAsStateWithLifecycle()
    val liveXtreamFormat by viewModel.liveXtreamFormat.collectAsStateWithLifecycle()
    val liveBackupChannel by viewModel.liveBackupChannel.collectAsStateWithLifecycle()
    val liveInContinue by viewModel.liveInContinue.collectAsStateWithLifecycle()
    val liveRememberAudio by viewModel.liveRememberAudio.collectAsStateWithLifecycle()
    val liveCatchupHls by viewModel.liveCatchupHls.collectAsStateWithLifecycle()
    // Lote 12 (v1.22.0): Multiview — a pref vive no PlatformPrefs direto
    // (o SettingsViewModel não ganhou setter novo nesta frente).
    val context = LocalContext.current
    val livePrefs = remember(context) { com.aotv.app.data.local.PlatformPrefs(context) }
    val liveMultiviewLayout by livePrefs.liveMultiviewLayoutFlow.collectAsStateWithLifecycle()
    SettingsPageScaffold("Integrações", "Contas e TV ao vivo", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Contas", anchor = SettingsAnchors.INTEGR_ACCOUNTS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.AccountCircle,
                iconTint = Accent,
                title = "Stremio e Nuvio",
                sub = "Entre na sua conta e traga addons, Minha Lista, progresso e debrid.",
                onClick = onOpenAccounts,
                grouped = true,
            )
        }
        SettingsSection("TV ao vivo", anchor = SettingsAnchors.INTEGR_LIVE)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.LiveTv,
                iconTint = Accent,
                title = "Canais ao vivo",
                sub = "Listas M3U e contas Xtream, com guia de programação.",
                onClick = onOpenLive,
                grouped = true,
            )
        }
        // Lote 8 (v1.18.0): guia, atualização, exibição, stream e reprodução —
        // tudo sob a mesma seção "TV ao vivo" (anchor INTEGR_LIVE), logo após
        // a entrada das fontes. O botão "Atualizar agora" fica nas telas Ao vivo
        // e Fontes (força listas + guia na hora, com banner de progresso).
        // Lote 12 (v1.22.0): Multiview e gerenciar canais — tudo sob a
        // seção "TV ao vivo" (anchor INTEGR_LIVE_MANAGE).
        SettingsSection("Multiview e canais", anchor = SettingsAnchors.INTEGR_LIVE_MANAGE)
        SettingsCard {
            PrefRow(
                "Multiview: canais na tela",
                listOf(1 to "1 canal", 2 to "2 canais", 4 to "4 canais"),
                liveMultiviewLayout, { livePrefs.liveMultiviewLayout = it },
            )
            Text(
                "Só no celular: na TV o Multiview não entra e a tela avisa. A célula em foco é a que tem áudio.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Edit,
                iconTint = Accent,
                title = "Gerenciar canais",
                sub = "Ocultar, reordenar, renomear, trocar a logo e associar o guia de cada canal.",
                onClick = onOpenLiveChannels,
                grouped = true,
            )
        }
        SettingsSection("Guia de programação")
        SettingsCard {
            PrefRow(
                "Dias à frente",
                listOf(1 to "1 dia", 2 to "2 dias", 3 to "3 dias", 5 to "5 dias", 7 to "7 dias"),
                liveGuideDaysAhead, viewModel::setLiveGuideDaysAhead,
            )
            PrefRow(
                "Horas para trás",
                listOf(0 to "Só agora", 1 to "1 h", 3 to "3 h", 6 to "6 h", 12 to "12 h", 24 to "24 h"),
                liveGuideHoursBack, viewModel::setLiveGuideHoursBack,
            )
            Text(
                "A grade continua mostrando 4 horas por vez; os botões ‹ 1 h / Agora / 1 h › respeitam estes limites. Sem guia, não há programação.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsSection("Atualização")
        SettingsCard {
            PrefRow(
                "Listas: atualizar a cada",
                listOf(6 to "6 h", 12 to "12 h", 24 to "24 h", 48 to "48 h", 168 to "7 dias"),
                liveRefreshListsHours, viewModel::setLiveRefreshListsHours,
            )
            PrefRow(
                "Guia: atualizar a cada",
                listOf(1 to "1 h", 6 to "6 h", 12 to "12 h", 24 to "24 h"),
                liveRefreshEpgHours, viewModel::setLiveRefreshEpgHours,
            )
            Text(
                "A atualização automática acontece ao abrir a aba Ao vivo. O botão de atualizar nas telas Ao vivo e Fontes força na hora.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsSection("Exibição dos canais")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Logos dos canais",
                sub = if (liveShowLogos) "Ligado: a logo aparece na fileira" else "Desligado: só o nome",
                trailing = { Switch(checked = liveShowLogos, onCheckedChange = { viewModel.setLiveShowLogos(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveShowLogos(!liveShowLogos) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Nomes dos canais",
                sub = if (liveShowNames) "Ligado: o nome aparece na fileira" else "Desligado: só o programa",
                trailing = { Switch(checked = liveShowNames, onCheckedChange = { viewModel.setLiveShowNames(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveShowNames(!liveShowNames) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Números dos canais",
                sub = if (liveShowNumbers) "Ligado: o número aparece antes do nome" else "Desligado: sem número",
                trailing = { Switch(checked = liveShowNumbers, onCheckedChange = { viewModel.setLiveShowNumbers(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveShowNumbers(!liveShowNumbers) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Limpar nomes de grupos",
                sub = if (liveCleanGroupNames) "Ligado: prefixos tipo \"BR | \" somem da exibição" else "Desligado: os nomes aparecem como vêm na lista",
                trailing = { Switch(checked = liveCleanGroupNames, onCheckedChange = { viewModel.setLiveCleanGroupNames(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveCleanGroupNames(!liveCleanGroupNames) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Stream e catch-up")
        SettingsCard {
            PrefRow(
                "Formato do stream Xtream",
                listOf("auto" to "Automático", "ts" to "TS (.ts)", "hls" to "HLS (.m3u8)"),
                liveXtreamFormat, viewModel::setLiveXtreamFormat,
            )
            Text(
                "Automático usa o formato que a conta libera. Se a conta não liberar o formato escolhido, volta ao automático e avisa na tela.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.History,
                title = "Catch-up em HLS",
                sub = if (liveCatchupHls) "Ligado: a gravação vem em .m3u8 em vez de .ts" else "Desligado: a gravação vem em .ts",
                trailing = { Switch(checked = liveCatchupHls, onCheckedChange = { viewModel.setLiveCatchupHls(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveCatchupHls(!liveCatchupHls) },
                grouped = true,
            )
        }
        SettingsSection("Reprodução ao vivo")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = "Canal reserva",
                sub = if (liveBackupChannel) "Ligado: se o canal falhar, tenta outro com o mesmo nome de outra fonte" else "Desligado: sem tentativa de reserva",
                trailing = { Switch(checked = liveBackupChannel, onCheckedChange = { viewModel.setLiveBackupChannel(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveBackupChannel(!liveBackupChannel) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.History,
                title = "Ao vivo no Continuar assistindo",
                sub = if (liveInContinue) "Ligado: o canal aparece no Continuar, com o programa atual" else "Desligado: nada ao vivo entra no Continuar",
                trailing = { Switch(checked = liveInContinue, onCheckedChange = { viewModel.setLiveInContinue(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveInContinue(!liveInContinue) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.History,
                title = "Lembrar faixa de áudio",
                sub = if (liveRememberAudio) "Ligado: a faixa escolhida volta sozinha neste canal" else "Desligado: sempre a faixa padrão",
                trailing = { Switch(checked = liveRememberAudio, onCheckedChange = { viewModel.setLiveRememberAudio(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLiveRememberAudio(!liveRememberAudio) },
                grouped = true,
                divider = true,
            )
        }
    }
}

// ── Acompanhamento ───────────────────────────────────────────────────────────

@Composable
private fun AcompanhamentoPage(
    profileName: String,
    isKids: Boolean,
    onOpenProfiles: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    highlightAnchor: String? = null,
) {
    var showSetPin by remember { mutableStateOf(false) }
    val hasPin = viewModel.hasParentalPin
    // Lote 23: limite de idade do perfil infantil (só aparece com infantil ativo).
    val kidsAgeLimit by viewModel.kidsAgeLimit.collectAsStateWithLifecycle()
    // Grupos trancados com o PIN (Lote 11, v1.21.0): gerenciar os cadeados
    // aqui (destrancar pede o PIN); trancar é no cartão do grupo, no Ao vivo.
    val lockedGroups by viewModel.liveLockedGroups.collectAsStateWithLifecycle()
    var showLockedGroups by remember { mutableStateOf(false) }
    var pendingRemoveLock by remember { mutableStateOf<String?>(null) }
    // Chave TMDB (Lote 6, v1.15.0): saiu de Integrações p/ cá, junto dos
    // trackers. Escondida no infantil — a chave continua atrás do PIN dos pais.
    val customTmdbKey by viewModel.customTmdbApiKey.collectAsStateWithLifecycle()
    val hasTmdbKey by viewModel.hasTmdbKey.collectAsStateWithLifecycle()
    var tmdbDraft by remember(customTmdbKey) { mutableStateOf(customTmdbKey) }
    var tmdbVisible by remember(customTmdbKey) { mutableStateOf(false) }
    SettingsPageScaffold("Acompanhamento", "Perfil, PIN dos pais, TMDB e Trakt", onBack, highlightAnchor = highlightAnchor) {
        // Perfil ativo. Tocar abre o "Quem está assistindo?" para trocar ou editar.
        // PIN dos pais. Só mexe quem já está fora do infantil (sair do infantil já pede
        // o PIN quando existe um, então chegar aqui com infantil ativo não rola).
        SettingsSection("Perfil", anchor = SettingsAnchors.TRACK_PROFILE)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Person,
                title = profileName,
                sub = (if (isKids) "Infantil" else "Perfil") + " · trocar ou editar perfis",
                onClick = onOpenProfiles,
                grouped = true,
            )
            // Remodel v1.44.0 (Frente 2): trocar de conta/perfil dentro
            // das Configurações, como no Nuvio — abre a mesma seleção
            // de perfil de sempre ("Quem está assistindo?"); a troca
            // reinicia o app no perfil escolhido (MainActivity observa
            // profileId e chama restartApp(picked = true)). A tela de
            // seleção continua como está, sem nada removido.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SwapHoriz,
                title = "Trocar de perfil/conta",
                sub = "Abre \"Quem está assistindo?\" para trocar de conta ou perfil",
                onClick = onOpenProfiles,
                grouped = true,
                divider = true,
            )
            if (!isKids) {
                com.aotv.app.ui.common.SettingsRow(
                    icon = Icons.Default.Lock,
                    title = if (hasPin) "Trocar PIN dos pais" else "Criar PIN dos pais",
                    sub = "Trava sair do infantil e abrir Addons, a chave do TMDB e o Aparelho com o infantil ativo",
                    onClick = { showSetPin = true },
                    grouped = true,
                    divider = true,
                )
                com.aotv.app.ui.common.SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Grupos trancados com PIN",
                    sub = if (lockedGroups.isEmpty()) "Nenhum grupo trancado. Toque no cadeado do cartão do grupo, na aba Ao vivo, para trancar"
                        else if (lockedGroups.size == 1) "1 grupo trancado — toque para gerenciar"
                        else "${lockedGroups.size} grupos trancados — toque para gerenciar",
                    onClick = { showLockedGroups = true },
                    grouped = true,
                    divider = true,
                )
            }
        }
        if (showSetPin) SetPinDialog(
            title = if (hasPin) "Trocar PIN dos pais" else "Criar PIN dos pais",
            onSet = { pin -> viewModel.setParentalPin(pin); showSetPin = false },
            onDismiss = { showSetPin = false },
        )
        // Lote 23 (v1.33.0): limite de idade do perfil infantil ativo.
        // 0 = só o filtro de conteúdo infantil, sem limite de idade.
        if (isKids) {
            SettingsCard {
                PrefRow(
                    "Limite de idade",
                    listOf(0 to "Só gênero", 7 to "7 anos", 10 to "10 anos", 12 to "12 anos", 14 to "14 anos", 16 to "16 anos", 18 to "18 anos"),
                    kidsAgeLimit, viewModel::setKidsAgeLimit,
                )
                Text(
                    "Só mostra títulos até essa idade. Com \"Só gênero\", vale só o filtro de conteúdo infantil.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (showLockedGroups) {
            AlertDialog(
                onDismissRequest = { showLockedGroups = false },
                title = { Text("Grupos trancados") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (lockedGroups.isEmpty()) {
                            Text(
                                "Nenhum grupo trancado. Na aba Ao vivo, toque no cadeado do cartão do grupo para trancar — abrir o grupo passa a pedir o PIN dos pais.",
                                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                            )
                        } else {
                            Text(
                                "Abrir um destes grupos pede o PIN dos pais. Destrancar também pede o PIN.",
                                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                            )
                            lockedGroups.forEach { group ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                                    Text(group, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { pendingRemoveLock = group }) {
                                        Text("Destrancar", color = Accent)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLockedGroups = false }) {
                        Text("Fechar", color = Accent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
        pendingRemoveLock?.let { group ->
            VerifyPinDialog(
                title = "PIN dos pais",
                onConfirm = { pin ->
                    if (viewModel.checkParentalPin(pin)) {
                        viewModel.unlockLiveGroup(group)
                        pendingRemoveLock = null
                        true
                    } else {
                        false
                    }
                },
                onDismiss = { pendingRemoveLock = null },
            )
        }

        // TMDB (veio de Integrações). A chave é do perfil ativo e libera o
        // catálogo, a busca e as fileiras; sem ela, vale a chave padrão do app.
        if (!isKids) {
            SettingsSection("TMDB", anchor = SettingsAnchors.TRACK_TMDB)
            Text(
                when {
                    customTmdbKey.isNotBlank() -> "Chave pessoal em uso. Você pode trocar a chave ou restaurar o padrão do app."
                    hasTmdbKey -> "Chave padrão do app em uso. Se preferir, informe sua própria chave abaixo."
                    else -> "Nenhuma chave configurada. Cole sua chave TMDB abaixo para liberar o catálogo, a busca e as fileiras."
                },
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                    OutlinedTextField(
                        value = tmdbDraft,
                        onValueChange = { tmdbDraft = it },
                        label = { Text("Sua chave TMDB (opcional)") },
                        placeholder = { Text("Cole sua chave aqui", color = TextTertiary) },
                        visualTransformation = if (tmdbVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = gate,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Border,
                            focusedContainerColor = Surface2,
                            unfocusedContainerColor = Surface2,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Accent,
                            unfocusedLabelColor = TextTertiary,
                            cursorColor = Accent,
                        ),
                    )
                }
                IconButton(
                    onClick = { tmdbVisible = !tmdbVisible }, enabled = tmdbDraft.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (tmdbVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (tmdbVisible) "Ocultar chave pessoal" else "Mostrar chave pessoal",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.aotv.app.ui.common.AotvButton(
                    "Salvar chave", onClick = {
                        viewModel.setTmdbApiKey(tmdbDraft)
                        tmdbDraft = viewModel.customTmdbApiKey.value
                        tmdbVisible = false
                    },
                    enabled = tmdbDraft.isNotBlank() && tmdbDraft.trim() != customTmdbKey,
                    compact = true, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                )
                if (customTmdbKey.isNotBlank()) com.aotv.app.ui.common.AotvButton(
                    "Usar chave padrão", onClick = { viewModel.setTmdbApiKey(""); tmdbDraft = ""; tmdbVisible = false },
                    compact = true,
                )
            }
        }

        if (viewModel.traktConfigured) {
            SettingsSection("Trakt", anchor = SettingsAnchors.TRACK_TRAKT)
            SettingsCard { TraktSection(viewModel) }
        }
    }
}

/**
 * Conta do Trakt do perfil ativo. Conectar mostra o código para digitar em trakt.tv/activate.
 * Botão de rede marca na hora, gira "Salvando…" e trava o segundo toque até a resposta.
 */
@Composable
private fun TraktSection(viewModel: SettingsViewModel) {
    val user by viewModel.traktUser.collectAsStateWithLifecycle()
    val busy by viewModel.traktBusy.collectAsStateWithLifecycle()
    val code by viewModel.traktCode.collectAsStateWithLifecycle()
    // Confirmar antes de desconectar (v1.13.0).
    var confirmLogout by remember { mutableStateOf(false) }
    Text(
        if (user == null) "Junta a Minha Lista, o ponto onde parou e os episódios vistos com o celular, a TV e o site."
        else "Conectado${if (user.isNullOrBlank()) "" else " como $user"}. Sincroniza ao abrir a Início.",
        fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BusyButton(
            label = if (user == null) "Conectar" else "Sincronizar agora",
            busyLabel = busy,
            onClick = { if (user == null) viewModel.traktConnect() else viewModel.traktSync() },
        )
        if (user != null && busy == null) {
            com.aotv.app.ui.common.AotvButton("Desconectar", { confirmLogout = true }, compact = true)
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        containerColor = BackgroundCard,
        title = { Text("Desconectar do Trakt?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Text("A conta sai deste perfil. Minha Lista e o progresso ficam no aparelho.", color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = { viewModel.traktLogout(); confirmLogout = false }) { Text("Desconectar", color = DangerText) }
        },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar", color = TextSecondary) } },
    )
    code?.let { c ->
        AlertDialog(
            onDismissRequest = viewModel::traktCancel,
            containerColor = BackgroundCard,
            title = { Text("Conectar ao Trakt", color = TextPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Abra no celular ou no computador", color = TextSecondary, fontSize = 13.sp)
                    Text(c.url.removePrefix("https://"), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Text("e digite o código", color = TextSecondary, fontSize = 13.sp)
                    Text(c.userCode, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                        Text("Aguardando confirmação…", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::traktCancel) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

/** Botão de chamada de rede. Com [busyLabel] gira, mostra o texto e não aceita outro toque. */
@Composable
private fun BusyButton(label: String, busyLabel: String?, onClick: () -> Unit) {
    com.aotv.app.ui.common.AotvButton(
        label, onClick, icon = Icons.Default.Sync, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
        busy = busyLabel != null, busyLabel = busyLabel ?: "", compact = true,
    )
}

// ── Aparência ────────────────────────────────────────────────────────────────

@Composable
private fun AparenciaPage(
    onBack: () -> Unit,
    onPlatformChanged: () -> Unit,
    viewModel: SettingsViewModel,
    highlightAnchor: String? = null,
    // Lote 25 (v1.35.0) — Frente B: "Report a problem" (na seção de versão/Sobre).
    onOpenReportProblem: () -> Unit = {},
) {
    val currentMode by viewModel.platformMode.collectAsStateWithLifecycle()
    // Lote 25 (v1.35.0), item 4 — Tour de boas-vindas: reabre os 5 capítulos.
    // (Não existe página "Sobre" — a versão instalada vive nesta página.)
    val tourContext = LocalContext.current
    val tourPrefs = remember(tourContext) { com.aotv.app.data.local.PlatformPrefs(tourContext) }
    var tourOpen by remember { mutableStateOf(false) }
    if (tourOpen) {
        com.aotv.app.ui.movies.OnboardingTourDialog(onDone = {
            tourOpen = false
            tourPrefs.onboardingTourSeenV1 = true
        })
    }
    val deviceLayout = com.aotv.app.ui.common.currentDeviceLayout(currentMode)
    // Blur nos painéis (Lote 3 item 4, v1.11.0; padrão: ligado).
    val panelBlur by viewModel.panelBlur.collectAsStateWithLifecycle()
    // Lote 24 (v1.34.0), item 1: preto puro por cima do tema escolhido.
    val homeAmoled by viewModel.homeAmoled.collectAsStateWithLifecycle()
    SettingsPageScaffold("Aparência", "Tema e aparelho", onBack, highlightAnchor = highlightAnchor) {
        SettingsSection("Tema", anchor = SettingsAnchors.APPEAR_THEME)
        // Única setting nova da Fase A. O composable vem de ui/theme/ThemePicker.kt
        // (outra frente); sem ele, esta é a única importação a integrar.
        ThemeSelector()
        // Lote 24 (v1.34.0), item 1: AMOLED puro por cima do tema escolhido.
        // Com o tema AMOLED selecionado, o toggle não muda nada (já é preto puro).
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Contrast,
                title = "Preto puro (AMOLED)",
                sub = if (homeAmoled) "Fundo #000000 e cartões quase pretos por cima do tema — economia de bateria em tela OLED"
                    else "Desligado: o tema usa os fundos dele",
                trailing = { Switch(checked = homeAmoled, onCheckedChange = { viewModel.setHomeAmoled(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setHomeAmoled(!homeAmoled) },
                grouped = true,
            )
        }
        // Tema personalizado (Lote 3 item 3, v1.11.0): as 6 cores editáveis.
        SettingsSection("Tema personalizado", anchor = SettingsAnchors.APPEAR_CUSTOM)
        CustomThemeEditor()
        SettingsSection("Painéis", anchor = SettingsAnchors.APPEAR_PANELS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Image,
                title = "Blur nos painéis",
                sub = "Desfoca o conteúdo atrás das folhas (detalhe e player) e deixa a pílula, o mini player e os cartões em vidro fosco. Abaixo do Android 12, um fundo escuro fosco no lugar do desfoque.",
                trailing = { Switch(checked = panelBlur, onCheckedChange = { viewModel.setPanelBlur(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setPanelBlur(!panelBlur) },
                grouped = true,
            )
        }
        // Ícone do app (Lote 11, v1.21.0): 7 variantes coloridas por
        // activity-alias. O Padrão é o ícone atual e não muda. Na TV o ícone
        // não aparece (o launcher da TV usa o banner, igual em todas as
        // cores), então a linha só abre o seletor no celular/tablet.
        SettingsSection("Ícone do app", anchor = SettingsAnchors.APPEAR_ICON)
        var showIconPicker by remember { mutableStateOf(false) }
        val appIconVariant by viewModel.appIconVariant.collectAsStateWithLifecycle()
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Image,
                title = "Ícone do app",
                sub = "Ícone atual: " + (com.aotv.app.data.local.AppIconVariants.byId(appIconVariant)?.label ?: "Padrão") + ". Toque para trocar a cor.",
                onClick = { showIconPicker = true },
                grouped = true,
            )
        }
        if (showIconPicker) AppIconPickerDialog(
            currentId = appIconVariant,
            onPick = { viewModel.setAppIconVariant(it) },
            onDismiss = { showIconPicker = false },
        )
        SettingsSection("Aparelho", anchor = SettingsAnchors.APPEAR_DEVICE)
        Text(
            "Layout atual ${deviceLayout.label}. O modo automático reconhece TV e adapta celular e tablet ao espaço disponível.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        com.aotv.app.ui.common.SettingsGroup {
            PlatformOption(
                selected = currentMode == PlatformMode.AUTO,
                title = "Automático",
                subtitle = "Reconhece TV, tablet e celular e acompanha o tamanho da janela",
                icon = Icons.Default.Devices,
                onClick = { viewModel.setPlatformMode(PlatformMode.AUTO); onPlatformChanged() },
                grouped = true,
            )
            PlatformOption(
                selected = currentMode == PlatformMode.TV,
                title = "Forçar TV",
                subtitle = "Layout de controle remoto com menu lateral",
                icon = Icons.Default.Tv,
                onClick = { viewModel.setPlatformMode(PlatformMode.TV); onPlatformChanged() },
                grouped = true,
                divider = true,
            )
            PlatformOption(
                selected = currentMode == PlatformMode.PHONE,
                title = "Celular ou tablet",
                subtitle = "Layout de toque adaptado ao espaço disponível",
                icon = Icons.Default.PhoneAndroid,
                onClick = { viewModel.setPlatformMode(PlatformMode.PHONE); onPlatformChanged() },
                grouped = true,
                divider = true,
            )
        }
        // Atualizações do app (Lote 12 — Frente 3, v1.22.0): checagem manual
        // + aviso discreto da release mais recente do repo público no GitHub
        // (sem login). Sem releases publicadas, mostra o estado honesto —
        // nunca afirma que está atualizado. Não bloqueia o uso.
        SettingsSection("Atualizações", anchor = SettingsAnchors.APPEAR_UPDATE)
        val updateState by viewModel.updateState.collectAsStateWithLifecycle()
        val updateDownloading by viewModel.updateDownloading.collectAsStateWithLifecycle()
        val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsStateWithLifecycle()
        val updateDownloadError by viewModel.updateDownloadError.collectAsStateWithLifecycle()
        val updateNeedsPermission by viewModel.updateNeedsPermission.collectAsStateWithLifecycle()
        val updateCheckEnabled by viewModel.appUpdateCheckEnabled.collectAsStateWithLifecycle()
        val updateBackgroundEnabled by viewModel.appUpdateBackgroundEnabled.collectAsStateWithLifecycle()
        val updateReadyTag by viewModel.updateReadyTag.collectAsStateWithLifecycle()
        val updateReadyHashVerified by viewModel.updateReadyHashVerified.collectAsStateWithLifecycle()
        val whatsNewText by viewModel.whatsNewText.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.maybeAutoCheckForUpdates() }
        val updateStatus = when (val s = updateState) {
            null -> "Ainda não verificado nesta sessão."
            com.aotv.app.data.remote.UpdateCheckResult.NoReleases -> "Nenhuma release publicada ainda."
            is com.aotv.app.data.remote.UpdateCheckResult.UpToDate -> "Você está na versão mais recente (${s.latestTag})."
            is com.aotv.app.data.remote.UpdateCheckResult.Available -> "Nova versão disponível: ${s.release.tag}."
            is com.aotv.app.data.remote.UpdateCheckResult.Error -> "Não foi possível verificar: ${s.message}"
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = "Versão instalada",
                sub = "AOTV ${viewModel.installedAppVersion}. $updateStatus",
                trailing = null,
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Sync,
                title = "Verificação automática",
                sub = if (updateCheckEnabled) "Avisa quando houver versão nova (checa no máximo 1 vez por dia)"
                    else "Só verifica quando você tocar em \"Verificar atualizações\"",
                trailing = { Switch(checked = updateCheckEnabled, onCheckedChange = { viewModel.setAppUpdateCheckEnabled(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setAppUpdateCheckEnabled(!updateCheckEnabled) },
                grouped = true,
                divider = true,
            )
            // Lote 18 (v1.28.0): download em 2º plano — com a opção ligada,
            // a versão nova baixa sozinha (WorkManager) e avisa quando está
            // pronta p/ instalar; respeita o "Só Wi-Fi" dos downloads.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Download,
                title = "Atualização em 2º plano",
                sub = if (updateBackgroundEnabled) "Baixa a versão nova sozinha e avisa quando estiver pronta"
                    else "Só baixa quando você tocar em \"Baixar e instalar\"",
                trailing = { Switch(checked = updateBackgroundEnabled, onCheckedChange = { viewModel.setAppUpdateBackgroundEnabled(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setAppUpdateBackgroundEnabled(!updateBackgroundEnabled) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = "Verificar atualizações",
                sub = "Consulta a release mais recente no GitHub",
                busy = updateDownloading,
                onClick = { viewModel.checkForUpdates() },
                grouped = true,
                divider = true,
            )
            val available = updateState as? com.aotv.app.data.remote.UpdateCheckResult.Available
            if (available != null) {
                val ready = viewModel.isUpdateReady(available.release.tag)
                val abiLabel = if (available.release.abi.isNotBlank()) "APK ${available.release.abi}" else "APK universal"
                val shaLabel = if (available.release.sha256.isNotBlank()) "SHA-256 conferido no feed" else "sem SHA-256 no feed — só o tamanho será conferido"
                val readyLabel = if (ready) {
                    if (updateReadyHashVerified) "Baixado em 2º plano • SHA-256 conferido ✓" else "Baixado em 2º plano • só o tamanho conferido"
                } else "$abiLabel • $shaLabel"
                com.aotv.app.ui.common.SettingsRow(
                    icon = Icons.Default.FiberManualRecord,
                    title = if (updateDownloading) {
                        val pct = updateDownloadProgress?.percent
                        if (pct != null) "Baixando atualização… $pct%" else "Baixando atualização…"
                    } else if (ready) "Instalar atualização pronta ${available.release.tag}" else "Baixar e instalar ${available.release.tag}",
                    sub = if (updateDownloading) {
                        // Lote 21 (v1.31.0): progresso real do download com
                        // retomada — "34 de 81 MB", sem fingir velocidade.
                        val prog = updateDownloadProgress
                        if (prog != null && prog.totalBytes > 0) {
                            "${MobileDataUsage.formatBytes(prog.downloadedBytes)} de ${MobileDataUsage.formatBytes(prog.totalBytes)} — se cair, retoma de onde parou."
                        } else "Baixando da release oficial…"
                    } else if (ready) readyLabel else "Baixa o $abiLabel da release oficial e pede p/ instalar. $shaLabel.",
                    onClick = { viewModel.downloadAndInstallUpdate() },
                    grouped = true,
                    divider = true,
                )
            }
            // Lote 18 (v1.28.0): "What's new" — reabre as novidades da
            // versão instalada (do feed/release ou embutidas no app).
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.NewReleases,
                title = "Novidades da versão",
                sub = if (viewModel.hasWhatsNew()) "O que mudou na v${viewModel.installedAppVersion}"
                    else "Sem notas salvas p/ esta versão",
                onClick = { viewModel.showWhatsNew() },
                grouped = true,
            )
            // Lote 25 (v1.35.0) — Frente B, item 5: "Report a problem".
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.BugReport,
                title = "Reportar um problema",
                sub = "Relato pré-preenchido com versão e aparelho, sem chaves, + teste de velocidade",
                onClick = onOpenReportProblem,
                grouped = true,
            )
            // Lote 25 (v1.35.0), item 4 — Tour de boas-vindas (Frente A):
            // reabre os 5 capítulos. Não há página "Sobre" — a reabertura
            // vive aqui, junto da versão instalada.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.AutoAwesome,
                title = "Tour de boas-vindas",
                sub = "Rever os 5 capítulos: boas-vindas, Início, busca, player e perfis",
                onClick = { tourOpen = true },
                grouped = true,
            )
        }
        // Diálogo "What's new" (Lote 18, v1.28.0): mostra o changelog da
        // versão instalada; fechar apaga a bolinha do avatar.
        val whatsNew = whatsNewText
        if (whatsNew != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissWhatsNew() },
                title = { Text("Novidades da v${viewModel.installedAppVersion}") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(whatsNew, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissWhatsNew() }) { Text("Entendi") }
                },
            )
        }
        if (updateDownloadError != null) {
            SettingsCard {
                Text(
                    "Falha no download: $updateDownloadError",
                    fontSize = 14.sp, color = DangerText, lineHeight = 20.sp,
                )
            }
        }
        if (updateNeedsPermission) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdatePermission() },
                title = { Text("Permitir instalação") },
                text = { Text("O Android ainda não permite que o AOTV instale apps. Ative \"Instalar apps desconhecidos\" p/ o AOTV nas configurações do sistema e tente de novo.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdatePermission(); viewModel.openUnknownSourcesSettings() }) {
                        Text("Abrir configurações")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdatePermission() }) { Text("Agora não") }
                },
            )
        }
    }
}

/** Opção de aparelho. Selecionada em turquesa com o check. Dentro de um grupo, sem borda própria. */
@Composable
private fun PlatformOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    grouped: Boolean = false,
    divider: Boolean = false,
) {
    com.aotv.app.ui.common.SettingsRow(
        icon = icon,
        title = title,
        sub = subtitle,
        iconTint = if (selected) Accent else TextSecondary,
        modifier = if (selected && !grouped) Modifier.border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier,
        trailing = if (selected) ({ Icon(Icons.Default.CheckCircle, "Selecionado", tint = Accent) }) else null,
        onClick = onClick,
        grouped = grouped,
        divider = divider,
    )
}

// ── Reprodução ───────────────────────────────────────────────────────────────

@Composable
private fun ReproducaoPage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    playerPrefs: PlayerPrefsViewModel = hiltViewModel(),
    highlightAnchor: String? = null,
) {
    val audio by viewModel.audioPref.collectAsStateWithLifecycle()
    val maxQ by viewModel.maxQuality.collectAsStateWithLifecycle()
    val heroTrailer by viewModel.heroTrailer.collectAsStateWithLifecycle()
    val trailerAutoplay by viewModel.trailerAutoplay.collectAsStateWithLifecycle()
    val trailerAutoplayWifiOnly by viewModel.trailerAutoplayWifiOnly.collectAsStateWithLifecycle()
    val subOn by viewModel.subOn.collectAsStateWithLifecycle()
    val subLang by viewModel.subLang.collectAsStateWithLifecycle()
    // Lote 23: legenda forçada automática (só as falas em outro idioma,
    // quando a legenda completa não liga — nunca toma o lugar dela).
    val forcedSubsAuto by viewModel.forcedSubsAuto.collectAsStateWithLifecycle()
    var pickLang by remember { mutableStateOf(false) }
    // Idioma preferido de ÁUDIO (Lote 4 item 7, v1.12.0) + reutilizar a última
    // fonte do título (item 6). A legenda já tinha o controle acima.
    val audioLang by playerPrefs.audioLang.collectAsStateWithLifecycle()
    var pickAudioLang by remember { mutableStateOf(false) }
    val reuseLastSource by playerPrefs.reuseLastSource.collectAsStateWithLifecycle()
    // Botões do player (Fase C). A lista guardada vazia é o padrão; aqui a
    // ordem em vigor, com os desligados no fim para religar sem perder o lugar.
    val savedButtons by playerPrefs.buttons.collectAsStateWithLifecycle()
    val effectiveButtons = effectivePlayerButtons(savedButtons)
    val disabledButtons = DEFAULT_PLAYER_BUTTONS.filterNot { it in effectiveButtons }
    val savedTopActions by playerPrefs.topActions.collectAsStateWithLifecycle()
    val effectiveTopActions = effectivePlayerTopActions(savedTopActions)
    val tapSeek by playerPrefs.tapSeek.collectAsStateWithLifecycle()
    val swipeSeek by playerPrefs.swipeSeek.collectAsStateWithLifecycle()
    val volBoost by playerPrefs.volBoost.collectAsStateWithLifecycle()
    val volBoostLevel by playerPrefs.volBoostLevel.collectAsStateWithLifecycle()
    val mini by playerPrefs.mini.collectAsStateWithLifecycle()
    val autoNext by playerPrefs.autoNext.collectAsStateWithLifecycle()
    val autoNextDelay by playerPrefs.autoNextDelay.collectAsStateWithLifecycle()
    val autoSwitch by playerPrefs.autoSwitch.collectAsStateWithLifecycle()
    // Lote mpv 3 (v1.38.0): "Procurar outra fonte no começo".
    val autoSwitchAtStart by playerPrefs.autoSwitchAtStart.collectAsStateWithLifecycle()
    val durationGuard by playerPrefs.durationGuard.collectAsStateWithLifecycle()
    val sourceTimeout by playerPrefs.sourceTimeout.collectAsStateWithLifecycle()
    // Lote 19 item 7 (v1.29.0) — Opções de Reprodução do JOTV: subir a
    // qualidade sozinho (e) e debrid só com arquivo pronto (f).
    val autoUpgrade by playerPrefs.autoUpgrade.collectAsStateWithLifecycle()
    val debridReadyOnly by playerPrefs.debridReadyOnly.collectAsStateWithLifecycle()
    // Lote 24 (v1.34.0), itens 8 e 9: Ambilight e modo sensorial.
    val ambilight by viewModel.playerAmbilight.collectAsStateWithLifecycle()
    val ambilightIntensity by viewModel.playerAmbilightIntensity.collectAsStateWithLifecycle()
    val sensoryFriendly by viewModel.playerSensoryFriendly.collectAsStateWithLifecycle()
    // Lote 25 (v1.35.0), itens 7–9 — Frente C: gate de dados móveis,
    // governador de bateria e saída de áudio (playerPrefs, não viewModel).
    val mobileDataGate by playerPrefs.mobileDataGate.collectAsStateWithLifecycle()
    val batteryGovernor by playerPrefs.batteryGovernor.collectAsStateWithLifecycle()
    val audioOutput by playerPrefs.audioOutput.collectAsStateWithLifecycle()
    val audioPassthrough by playerPrefs.audioPassthrough.collectAsStateWithLifecycle()
    val animeSkipId by playerPrefs.animeSkipClientId.collectAsStateWithLifecycle()
    // Personalização de legenda e velocidade padrão (v1.9.0).
    val subColor by playerPrefs.subColor.collectAsStateWithLifecycle()
    val subBgOpacity by playerPrefs.subBgOpacity.collectAsStateWithLifecycle()
    val subOutline by playerPrefs.subOutline.collectAsStateWithLifecycle()
    val subFont by playerPrefs.subFont.collectAsStateWithLifecycle()
    val playerSpeed by playerPrefs.speed.collectAsStateWithLifecycle()
    // Lote 12 — Frente 2: modo de tela padrão e vista da folha de fontes.
    val screenMode by playerPrefs.screenMode.collectAsStateWithLifecycle()
    val sourcesRanked by playerPrefs.sourcesRanked.collectAsStateWithLifecycle()
    // Lote 9 — Player parte 3 (v1.19.0): AutoSync, pular abertura automático,
    // cartão "A seguir" e equalizador.
    val autoSync by playerPrefs.autoSync.collectAsStateWithLifecycle()
    val autoSkip by playerPrefs.autoSkip.collectAsStateWithLifecycle()
    val upNext by playerPrefs.upNext.collectAsStateWithLifecycle()
    val eqEnabled by playerPrefs.equalizer.collectAsStateWithLifecycle()
    var animeSkipDraft by remember(animeSkipId) { mutableStateOf(animeSkipId) }
    // Lote 16 — Player parte 4 (v1.26.0): taxa de quadros, legendas nível 2 e SubDL.
    val frameRate by playerPrefs.frameRate.collectAsStateWithLifecycle()
    val subForeignOnly by playerPrefs.subForeignOnly.collectAsStateWithLifecycle()
    val subHideSdh by playerPrefs.subHideSdh.collectAsStateWithLifecycle()
    val subDlKey by playerPrefs.subDlApiKey.collectAsStateWithLifecycle()
    var subDlDraft by remember(subDlKey) { mutableStateOf(subDlKey) }
    // Lote 20 (v1.30.0) — olho p/ exibir/mascarar a chave do SubDL (mesmo
    // padrão da TMDB, logo acima).
    var subDlVisible by remember { mutableStateOf(false) }
    SettingsPageScaffold("Reprodução", "Áudio, qualidade, botões do player e legendas", onBack, highlightAnchor = highlightAnchor) {
        // Reprodução do perfil ativo. Ordena as fontes e limita a qualidade escolhida no Assistir.
        SettingsSection("Reprodução", anchor = SettingsAnchors.PLAY_MAIN)
        SettingsCard {
            PrefRow("Áudio preferido", listOf("any" to "Qualquer", "dub" to "Dublado", "leg" to "Legendado"), audio, viewModel::setAudio)
            Spacer(Modifier.height(14.dp))
            PrefRow("Qualidade máxima", listOf(0 to "Sem limite", 2160 to "4K", 1080 to "1080p", 720 to "720p"), maxQ, viewModel::setMaxQuality)
        }
        // Lote 25 (v1.35.0), itens 7–9 — Frente C: gate de dados móveis
        // ao dar play, governador de bateria e saída de áudio. O gate e
        // o governador são por perfil; a saída de áudio é do aparelho.
        SettingsSection("Dados móveis, bateria e áudio", anchor = SettingsAnchors.PLAY_MAIN)
        SettingsCard {
            PrefRow("Dados móveis ao dar play", listOf("ask" to "Perguntar sempre", "allow" to "Liberar sempre", "block" to "Só Wi-Fi"), mobileDataGate, playerPrefs::setMobileDataGate)
            Spacer(Modifier.height(14.dp))
            PrefRow("Saída de áudio", listOf("auto" to "Automática", "speakers" to "Alto-falantes", "receiver" to "Receiver (HDMI)"), audioOutput, playerPrefs::setAudioOutput)
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Bolt,
                title = "Governador de bateria",
                sub = if (batteryGovernor) "Com bateria ≤ 20% e sem carregar, sugere o modo economia (até 720p)"
                    else "Desligado: nunca sugere o modo economia",
                trailing = { Switch(checked = batteryGovernor, onCheckedChange = { playerPrefs.setBatteryGovernor(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setBatteryGovernor(!batteryGovernor) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VolumeUp,
                title = "Passthrough Dolby/DTS",
                sub = if (audioPassthrough) "Ligado: bitstream p/ o receiver (vale na próxima reprodução; modo noturno desligado)"
                    else "Desligado: o app decodifica p/ PCM",
                trailing = { Switch(checked = audioPassthrough, onCheckedChange = { playerPrefs.setAudioPassthrough(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAudioPassthrough(!audioPassthrough) },
                grouped = true,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Trailer no destaque",
                sub = "Toca sem som depois de 5 s parado num título da Início",
                // A linha liga e desliga. O Switch fica fora do caminho do controle.
                trailing = { Switch(checked = heroTrailer, onCheckedChange = { viewModel.setHeroTrailer(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setHeroTrailer(!heroTrailer) },
                grouped = true,
            )
            // Autoplay do trailer no detalhe (Lote 21, v1.31.0): toca no topo
            // do detalhe depois de alguns segundos, num mini-player (sem
            // abrir a tela cheia). Desligado, o trailer só toca no botão ▷.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Autoplay do trailer no detalhe",
                sub = if (trailerAutoplay) "Toca no topo do detalhe depois de alguns segundos" else "Desligado. O trailer só toca no botão ▷ Trailer",
                trailing = { Switch(checked = trailerAutoplay, onCheckedChange = { viewModel.setTrailerAutoplay(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setTrailerAutoplay(!trailerAutoplay) },
                grouped = true,
                divider = true,
            )
            // Só no Wi-Fi (padrão): no dados móveis o autoplay não dispara.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Wifi,
                title = "Autoplay do trailer só no Wi-Fi",
                sub = if (trailerAutoplayWifiOnly) "No dados móveis, o trailer só toca no botão ▷ Trailer" else "O autoplay vale também no dados móveis",
                trailing = { Switch(checked = trailerAutoplayWifiOnly, onCheckedChange = { viewModel.setTrailerAutoplayWifiOnly(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setTrailerAutoplayWifiOnly(!trailerAutoplayWifiOnly) },
                grouped = true,
                divider = true,
            )
            // Legenda do perfil. Ligada ao abrir e o idioma em que ela liga sozinha no player.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ClosedCaption,
                title = "Legenda ao abrir",
                sub = if (subOn) "Liga sozinha em ${SubLangs.label(subLang)} quando o título tiver" else "Desligada. Dá para ligar no player, em Legendas",
                trailing = { Switch(checked = subOn, onCheckedChange = { viewModel.setSubOn(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSubOn(!subOn) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Translate,
                title = "Idioma da legenda",
                sub = SubLangs.label(subLang) + ". Também aparece primeiro na lista do player",
                onClick = { pickLang = true },
                grouped = true,
                divider = true,
            )
            // Idioma preferido da faixa de ÁUDIO (Lote 4 item 7, v1.12.0): o
            // player escolhe a faixa nesse idioma ao abrir o vídeo. A escolha
            // à mão no player continua valendo p/ o vídeo atual.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VolumeUp,
                title = "Idioma do áudio",
                sub = SubLangs.label(audioLang) + ". O player escolhe a faixa de áudio nesse idioma quando o título tiver",
                onClick = { pickAudioLang = true },
                grouped = true,
                divider = true,
            )
            // Sincronização automática da legenda (Lote 9 item 1, v1.19.0):
            // mede o atraso da legenda externa contra a embutida (nunca
            // inventa atraso); o botão fica no painel de Legendas do player.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Sync,
                title = "Sincronização automática da legenda",
                sub = if (autoSync) "O botão \"Sincronizar automaticamente\" aparece no painel de Legendas do player"
                    else "Desligada. O ajuste manual ± do player continua valendo",
                trailing = { Switch(checked = autoSync, onCheckedChange = { playerPrefs.setAutoSync(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoSync(!autoSync) },
                grouped = true,
                divider = true,
            )
            // Legenda forçada automática (Lote 23, v1.33.0): quando a legenda
            // completa não liga, entra sozinha a faixa forçada (só as falas em
            // outro idioma); nunca toma o lugar da legenda completa.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ClosedCaption,
                title = "Legenda forçada automática",
                sub = if (forcedSubsAuto) "Ligada: sem legenda completa, entra sozinha a faixa forçada"
                    else "Desligada: a faixa forçada só entra pelo painel de Legendas",
                trailing = { Switch(checked = forcedSubsAuto, onCheckedChange = { viewModel.setForcedSubsAuto(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setForcedSubsAuto(!forcedSubsAuto) },
                grouped = true,
                divider = true,
            )
        }
        if (pickLang) SubLangDialog(subLang, onPick = { viewModel.setSubLang(it); pickLang = false }, onDismiss = { pickLang = false })
        if (pickAudioLang) SubLangDialog(audioLang, onPick = { playerPrefs.setAudioLang(it); pickAudioLang = false }, onDismiss = { pickAudioLang = false }, title = "Idioma do áudio")

        // Personalização de legenda (v1.9.0): cor, fonte, opacidade do fundo e
        // contorno. Vale para a legenda embutida e para a externa, e também dá
        // para mudar no player, em Legendas. Tamanho e fundo continuam no player.
        // Lote 24 (v1.34.0), itens 8 e 9: Ambilight e modo sensorial. O
        // Ambilight amostra as cores da cena (ou do pôster, quando a fonte
        // não permite amostrar) e pinta o fundo do player; em HLS, torrent
        // e DRM, ficam as cores do pôster. O modo sensorial suaviza estouros
        // de som e pisca-pisca e desliga a rotação do herói.
        SettingsSection("Ambilight e conforto", anchor = SettingsAnchors.PLAY_COMFORT)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Lightbulb,
                title = "Ambilight no player",
                sub = if (ambilight) "O fundo do player acompanha as cores da cena"
                    else "Desligado: o fundo do player é o de sempre",
                trailing = { Switch(checked = ambilight, onCheckedChange = { viewModel.setPlayerAmbilight(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setPlayerAmbilight(!ambilight) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Tune,
                title = "Modo sensorial",
                sub = if (sensoryFriendly) "Suaviza estouros de som e pisca-pisca (transições e Ambilight mais calmos)"
                    else "Desligado: som e transições no normal",
                trailing = { Switch(checked = sensoryFriendly, onCheckedChange = { viewModel.setPlayerSensoryFriendly(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setPlayerSensoryFriendly(!sensoryFriendly) },
                grouped = true,
            )
        }
        if (ambilight) SettingsCard {
            PrefRow(
                "Intensidade do Ambilight",
                listOf("low" to "Baixa", "medium" to "Média", "high" to "Alta"),
                ambilightIntensity, viewModel::setPlayerAmbilightIntensity,
            )
        }
        SettingsSection("Legendas", anchor = SettingsAnchors.PLAY_SUBS)
        SettingsCard {
            PrefRow("Cor da legenda", SUB_COLORS, subColor, playerPrefs::setSubColor)
            Spacer(Modifier.height(14.dp))
            PrefRow("Fonte da legenda", SUB_FONTS, subFont, playerPrefs::setSubFont)
            Spacer(Modifier.height(14.dp))
            PrefRow("Opacidade do fundo", SUB_BG_OPACITIES, subBgOpacity, playerPrefs::setSubBgOpacity)
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ClosedCaption,
                title = "Contorno da legenda",
                sub = if (subOutline) "Contorno preto ao redor do texto quando não há fundo"
                    else "Sem contorno. Sem fundo, o texto pode sumir em cena clara",
                trailing = { Switch(checked = subOutline, onCheckedChange = { playerPrefs.setSubOutline(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSubOutline(!subOutline) },
                grouped = true,
            )
        }
        // Legendas nível 2 (Lote 16 item 3, v1.26.0): só mostrar a legenda
        // quando o áudio é estrangeiro, esconder as faixas SDH/CC e o SubDL
        // como 2ª fonte (chave grátis do usuário — nunca embutida).
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Translate,
                title = "Só legendas em língua estrangeira",
                sub = if (subForeignOnly) "A legenda só abre sozinha quando o áudio não é do seu idioma"
                    else "A legenda abre sozinha sempre que o título tiver, no seu idioma",
                trailing = { Switch(checked = subForeignOnly, onCheckedChange = { playerPrefs.setSubForeignOnly(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSubForeignOnly(!subForeignOnly) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ClosedCaption,
                title = "Ocultar legendas SDH",
                sub = if (subHideSdh) "Faixas para surdos (com [sons] e [música]) não entram na escolha automática"
                    else "Faixas SDH/CC aparecem normalmente na escolha automática",
                trailing = { Switch(checked = subHideSdh, onCheckedChange = { playerPrefs.setSubHideSdh(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSubHideSdh(!subHideSdh) },
                grouped = true,
            )
        }
        Text(
            "SubDL é uma 2ª fonte de legendas (além do OpenSubtitles). A chave é grátis no site subdl.com — sem ela, o SubDL fica desligado.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                OutlinedTextField(
                    value = subDlDraft,
                    onValueChange = { subDlDraft = it },
                    label = { Text("Chave do SubDL (opcional)") },
                    placeholder = { Text("Cole sua chave aqui", color = TextTertiary) },
                    visualTransformation = if (subDlVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = gate,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextTertiary,
                        cursorColor = Accent,
                    ),
                )
            }
            IconButton(
                onClick = { subDlVisible = !subDlVisible }, enabled = subDlDraft.isNotEmpty(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (subDlVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (subDlVisible) "Ocultar chave do SubDL" else "Mostrar chave do SubDL",
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.aotv.app.ui.common.AotvButton(
                "Salvar chave", onClick = { playerPrefs.setSubDlApiKey(subDlDraft) },
                enabled = subDlDraft.trim() != subDlKey,
                compact = true, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
            )
            if (subDlKey.isNotBlank()) com.aotv.app.ui.common.AotvButton(
                "Desligar SubDL", onClick = { playerPrefs.setSubDlApiKey(""); subDlDraft = "" },
                compact = true,
            )
        }

        // Botões do player (Fase C). Sobe e desce reordena, o switch tira da
        // barra. Desligado, o botão some do player mas continua aqui p/ religar.
        SettingsSection("Botões do player", anchor = SettingsAnchors.PLAY_BUTTONS)
        SettingsCard {
            (effectiveButtons + disabledButtons).forEachIndexed { i, id ->
                val enabled = id in effectiveButtons
                val pos = effectiveButtons.indexOf(id)
                PlayerButtonRow(
                    label = playerButtonLabel(id),
                    enabled = enabled,
                    canUp = enabled && pos > 0,
                    canDown = enabled && pos >= 0 && pos < effectiveButtons.lastIndex,
                    onUp = { playerPrefs.moveButton(id, -1) },
                    onDown = { playerPrefs.moveButton(id, 1) },
                    onToggle = { playerPrefs.setButtonEnabled(id, it) },
                )
                if (i < DEFAULT_PLAYER_BUTTONS.lastIndex) Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { playerPrefs.resetButtons() }) { Text("Restaurar padrão", color = Accent) }
            }
        }
        SettingsSection("Botões do topo", anchor = SettingsAnchors.PLAY_TOP)
        Text(
            "O botão Voltar sempre aparece. Estes são os opcionais do topo do player.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        com.aotv.app.ui.common.SettingsGroup {
            listOf(
                "cast" to Icons.Default.Cast,
                "rotate" to Icons.Default.ScreenRotation,
                "lock" to Icons.Default.Lock,
            ).forEachIndexed { i, (id, icon) ->
                val on = id in effectiveTopActions
                com.aotv.app.ui.common.SettingsRow(
                    icon = icon,
                    title = playerTopActionLabel(id),
                    sub = when (id) {
                        "cast" -> "Manda o vídeo para a TV (Chromecast)"
                        "rotate" -> "Gira a tela do player"
                        else -> "Trava os controles para não tocar sem querer"
                    },
                    trailing = { Switch(checked = on, onCheckedChange = { playerPrefs.setTopActionEnabled(id, it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                    onClick = { playerPrefs.setTopActionEnabled(id, !on) },
                    grouped = true,
                    divider = i > 0,
                )
            }
        }
        SettingsSection("Reprodução no player", anchor = SettingsAnchors.PLAY_PLAYER)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.TouchApp,
                title = "Tocar na barra para buscar",
                sub = if (tapSeek) "Tocar na barra de progresso pula para aquele ponto"
                    else "Só arrastando a barra. Tocar nela não pula",
                trailing = { Switch(checked = tapSeek, onCheckedChange = { playerPrefs.setTapSeek(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setTapSeek(!tapSeek) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SwapHoriz,
                title = "Deslizar para avançar/retroceder",
                sub = if (swipeSeek) "Deslizar sobre o vídeo avança ou retrocede 10s"
                    else "Deslizar sobre o vídeo não busca",
                trailing = { Switch(checked = swipeSeek, onCheckedChange = { playerPrefs.setSwipeSeek(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSwipeSeek(!swipeSeek) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VolumeUp,
                title = "Volume Boost",
                sub = if (volBoost) "Deixa o volume passar de 100%"
                    else "Volume máximo é 100%",
                trailing = { Switch(checked = volBoost, onCheckedChange = { playerPrefs.setVolBoost(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setVolBoost(!volBoost) },
                grouped = true,
                divider = true,
            )
            // Taxa de quadros (Lote 16 item 4, v1.26.0): sincroniza a taxa de
            // atualização da tela com o fps do stream (24/25/30 fps sem
            // judder). Na TV vale o matchFrameRate; no celular, a janela.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Speed,
                title = "Sincronizar taxa de quadros",
                sub = if (frameRate) "A tela acompanha o fps do vídeo (24/25/30 fps sem engasgo)"
                    else "Desligado. A tela fica na taxa padrão do aparelho",
                trailing = { Switch(checked = frameRate, onCheckedChange = { playerPrefs.setFrameRate(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setFrameRate(!frameRate) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.PictureInPictureAlt,
                title = "Mini player",
                sub = if (mini) "Dá para minimizar o player e continuar navegando no app"
                    else "Sem mini player. Sair do player pausa o vídeo",
                trailing = { Switch(checked = mini, onCheckedChange = { playerPrefs.setMini(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setMini(!mini) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SkipNext,
                title = "'A seguir' automático",
                sub = if (autoNext) "Ao fim do episódio, avança sozinho para o próximo"
                    else "Ao fim do episódio, espera você escolher o próximo",
                trailing = { Switch(checked = autoNext, onCheckedChange = { playerPrefs.setAutoNext(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoNext(!autoNext) },
                grouped = true,
                divider = true,
            )
            // Cartão "A seguir" (Lote 9 item 2, v1.19.0): o cartão no fim do
            // episódio. Desligado, o episódio termina parado no último quadro
            // (o chip "Próximo" continua valendo); o avanço automático é o
            // "'A seguir' automático" acima.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SkipNext,
                title = "Mostrar cartão \"A seguir\"",
                sub = if (upNext) "No fim do episódio, mostra o cartão do próximo episódio"
                    else "No fim do episódio, não mostra o cartão — fica parado no último quadro",
                trailing = { Switch(checked = upNext, onCheckedChange = { playerPrefs.setUpNext(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setUpNext(!upNext) },
                grouped = true,
                divider = true,
            )
            // Lote 19 item 7 (c), v1.29.0 — título igual ao da tela REPRODUÇÃO
            // do JOTV. Desligado, o player fica na fonte escolhida e espera
            // por ela, mesmo se demorar (o aviso de timeout continua aparecendo).
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = "Trocar de fonte sozinho",
                sub = if (autoSwitch) "Se a fonte não iniciar ou travar, troca sozinha por outra"
                    else "Fica na fonte escolhida e espera por ela, mesmo se demorar",
                trailing = { Switch(checked = autoSwitch, onCheckedChange = { playerPrefs.setAutoSwitch(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoSwitch(!autoSwitch) },
                grouped = true,
                divider = true,
            )
            // Lote 19 item 7 (e), v1.29.0 — "Subir a qualidade sozinho": o
            // upgrade 1x do player (4 s depois de começar, troca uma vez p/
            // uma fonte pelo menos 25% melhor, sem perder o ponto). Desligado,
            // fica na qualidade que começou. Fonte escolhida à mão nunca troca.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.TrendingUp,
                title = "Subir a qualidade sozinho",
                sub = if (autoUpgrade) "Se acha uma fonte melhor logo no começo, troca uma vez sem perder o ponto"
                    else "Fica na qualidade que começou, mesmo se aparecer uma fonte melhor",
                trailing = { Switch(checked = autoUpgrade, onCheckedChange = { playerPrefs.setAutoUpgrade(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoUpgrade(!autoUpgrade) },
                grouped = true,
                divider = true,
            )
            // Lote mpv 3 (v1.38.0) — "Procurar outra fonte no começo": desliga
            // a procura automática ao abrir (a troca quando a fonte não
            // inicia + o upgrade 1x). Em fonte P2P/torrent as duas trocas
            // NUNCA rodam sozinhas, mesmo com este toggle ligado.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Search,
                title = "Procurar outra fonte no começo",
                sub = if (autoSwitchAtStart) "Ao abrir, procura outra fonte se a atual não iniciar ou se achar uma melhor"
                    else "Fica na fonte que começou — sem trocar sozinha no começo",
                trailing = { Switch(checked = autoSwitchAtStart, onCheckedChange = { playerPrefs.setAutoSwitchAtStart(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoSwitchAtStart(!autoSwitchAtStart) },
                grouped = true,
                divider = true,
            )
            // Lote 19 item 7 (f), v1.29.0 — "Debrid só com arquivo pronto": a
            // troca automática nunca abre torrent que o debrid ativo ainda não
            // tem pronto, p/ a conta não começar downloads sozinha. Sem conta
            // de debrid, não muda nada. A escolha à mão continua valendo.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Bolt,
                title = "Debrid só com arquivo pronto",
                sub = if (debridReadyOnly) "A troca automática nunca abre torrent que o debrid ainda não tem, para a conta não começar downloads sozinha"
                    else "A troca automática pode abrir torrent que o debrid ainda não tem — ele começa o download na conta",
                trailing = { Switch(checked = debridReadyOnly, onCheckedChange = { playerPrefs.setDebridReadyOnly(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setDebridReadyOnly(!debridReadyOnly) },
                grouped = true,
                divider = true,
            )
            // Guarda de duração (Lote 11, v1.21.0): antes de tocar um filme,
            // avisa quando o arquivo tem duração >10% diferente da do título
            // no TMDB (pode ser outro filme com o mesmo nome). Sem duração
            // conhecida de um dos lados, toca normal sem perguntar.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Timer,
                title = "Avisar quando a duração não bate",
                sub = if (durationGuard) "Antes de tocar, avisa se o arquivo tem duração muito diferente da do título"
                    else "Toca direto, mesmo se a duração do arquivo for muito diferente da do título",
                trailing = { Switch(checked = durationGuard, onCheckedChange = { playerPrefs.setDurationGuard(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setDurationGuard(!durationGuard) },
                grouped = true,
                divider = true,
            )
            // Reutilizar a última fonte do título (Lote 4 item 6, v1.12.0): o
            // player reabre o título na fonte escolhida à mão da última vez
            // (mesmo addon e qualidade), quando ela ainda existe na lista.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.History,
                title = "Reutilizar última fonte",
                sub = if (reuseLastSource) "Abre cada título na última fonte escolhida à mão nele"
                    else "Sempre abre na melhor fonte, sem lembrar a escolha anterior",
                trailing = { Switch(checked = reuseLastSource, onCheckedChange = { playerPrefs.setReuseLastSource(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setReuseLastSource(!reuseLastSource) },
                grouped = true,
                divider = true,
            )
            // Folha de fontes em ranking (Lote 12 — Frente 2): lista única da
            // melhor p/ pior em vez de agrupada por addon. Vale na próxima
            // vez que a folha abrir; o toggle da folha muda aqui também.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.FormatListNumbered,
                title = "Fontes em ranking",
                sub = if (sourcesRanked) "A folha de fontes lista da melhor para a pior (#1, #2, #3…)"
                    else "A folha de fontes agrupa as fontes por addon",
                trailing = { Switch(checked = sourcesRanked, onCheckedChange = { playerPrefs.setSourcesRanked(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSourcesRanked(!sourcesRanked) },
                grouped = true,
                divider = true,
            )
        }
        // Velocidade padrão (v1.9.0): o player abre nela; mudar no player salva aqui.
        SettingsCard {
            PrefRow(
                "Velocidade padrão",
                listOf(0.5f to "0,5x", 1f to "1x", 1.5f to "1,5x", 2f to "2x"),
                playerSpeed, playerPrefs::setSpeed,
            )
        }
        // Modo de tela padrão (Lote 12 — Frente 2): o player abre nele; mudar no chip "Tela" salva aqui.
        SettingsCard {
            PrefRow(
                "Modo de tela padrão",
                listOf(0 to "Automático", 1 to "Ajustar", 2 to "Preencher", 3 to "Zoom", 4 to "Esticar"),
                screenMode, playerPrefs::setScreenMode,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Automático: preenche a tela quando o vídeo tem a proporção dela e ajusta com barras quando não tem. Vale a partir da próxima reprodução.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
        }
        Column(Modifier.alpha(if (volBoost) 1f else 0.45f)) {
            SettingsCard {
                PrefRow(
                    "Nível do Volume Boost",
                    listOf(1.0f to "100%", 1.25f to "125%", 1.5f to "150%", 1.75f to "175%", 2.0f to "200%"),
                    volBoostLevel, { if (volBoost) playerPrefs.setVolBoostLevel(it) },
                )
                if (!volBoost) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ligue o Volume Boost para mudar o nível.", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Column(Modifier.alpha(if (autoNext) 1f else 0.45f)) {
            SettingsCard {
                PrefRow(
                    "Atraso do \"A seguir\"",
                    listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s"),
                    autoNextDelay, { if (autoNext) playerPrefs.setAutoNextDelay(it) },
                )
                if (!autoNext) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ligue o \"A seguir\" automático para mudar o atraso.", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Column(Modifier.alpha(if (autoSwitch) 1f else 0.45f)) {
            SettingsCard {
                // Lote 19 item 7 (d), v1.29.0 — título igual ao da tela REPRODUÇÃO
                // do JOTV. O padrão do AOTV continua 12 s (o da foto é 20 s).
                PrefRow(
                    "Espera antes de trocar",
                    listOf(8 to "8 s", 12 to "12 s", 20 to "20 s", 30 to "30 s", 45 to "45 s", 60 to "60 s"),
                    sourceTimeout, { if (autoSwitch) playerPrefs.setSourceTimeout(it) },
                )
                if (!autoSwitch) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ligue a troca automática de fonte para mudar o tempo.", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        // Equalizador (Lote 9 item 3, v1.19.0): presets do aparelho e 5
        // bandas em dB. Os presets e as bandas ficam no painel do player
        // (chip "Equalizador") — aqui só o liga/desliga, porque os presets
        // precisam da sessão de áudio do vídeo tocando p/ existir.
        SettingsSection("Equalizador", anchor = SettingsAnchors.PLAY_EQ)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Tune,
                title = "Equalizador",
                sub = if (eqEnabled) "Ligado — presets e bandas no painel do player (chip \"Equalizador\")"
                    else "Desligado. O som volta ao original",
                trailing = { Switch(checked = eqEnabled, onCheckedChange = { playerPrefs.setEqualizer(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setEqualizer(!eqEnabled) },
                grouped = true,
            )
        }
        Text(
            "O equalizador usa os presets do próprio aparelho (Normal, Pop, Rock, Jazz, Clássico…) e 5 bandas em dB. Só vale com o player aberto — ao fechar, desliga junto. Não vale para Cast nem player externo.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        SettingsSection("Pular trechos", anchor = SettingsAnchors.PLAY_SKIP)
        Text(
            "O player busca os trechos (resumo, abertura, encerramento e pós-créditos) no IntroDB e no SkipDB, sem chave. O Anime-Skip (só séries) precisa de um client ID seu — vazio, ele fica desligado.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        // Pular abertura automático (Lote 9 item 2, v1.19.0): pula a abertura
        // e o encerramento sozinho ao entrar no segmento (1x por episódio).
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SkipNext,
                title = "Pular abertura automaticamente",
                sub = if (autoSkip) "Ao entrar na abertura ou no encerramento, pula sozinho (1x por episódio)"
                    else "Desligado. O botão \"Pular abertura\" continua aparecendo na hora",
                trailing = { Switch(checked = autoSkip, onCheckedChange = { playerPrefs.setAutoSkip(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoSkip(!autoSkip) },
                grouped = true,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                OutlinedTextField(
                    value = animeSkipDraft,
                    onValueChange = { animeSkipDraft = it },
                    label = { Text("Client ID do Anime-Skip (opcional)") },
                    placeholder = { Text("Cole seu client ID aqui", color = TextTertiary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = gate,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextTertiary,
                        cursorColor = Accent,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.aotv.app.ui.common.AotvButton(
                "Salvar ID", onClick = { playerPrefs.setAnimeSkipClientId(animeSkipDraft) },
                enabled = animeSkipDraft.trim() != animeSkipId,
                compact = true, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
            )
            if (animeSkipId.isNotBlank()) com.aotv.app.ui.common.AotvButton(
                "Desligar Anime-Skip", onClick = { playerPrefs.setAnimeSkipClientId(""); animeSkipDraft = "" },
                compact = true,
            )
        }
    }
}

/**
 * Aba MPV (remodel v1.44.0, Frente 2): tudo de mpv que vivia em
 * Reprodução — motor (ExoPlayer/Libmpv), decoder, buffer, opções do
 * Libmpv (display-fps, video-sync, hwdec, cache, posição das legendas,
 * correção de tom), extras (pré-carregar, scripts Lua, IPC) e Melhorar
 * imagem (detecção de hardware do upscaling). Conteúdo MOVIDO de
 * ReproducaoPage, sem duplicar e sem renomear nenhuma pref: as mesmas
 * chaves (`pref_mpv_*` / `pref_enhance_*` via PlayerPrefsViewModel) e o
 * mesmo gerenciador de scripts Lua ([MpvScriptsManager]). Reprodução
 * fica com o resto (áudio, legendas, botões, velocidade etc.).
 */
@Composable
private fun MpvPage(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    playerPrefs: PlayerPrefsViewModel = hiltViewModel(),
) {
    // Motor do player (Lote 4, v1.12.0): player padrão e decoder.
    val playerEngine by playerPrefs.engine.collectAsStateWithLifecycle()
    val playerDecoder by playerPrefs.decoder.collectAsStateWithLifecycle()
    // Buffer do player (Lote 5 item 1, v1.14.0): perfil de buffer do ExoPlayer.
    val playerBuffer by playerPrefs.buffer.collectAsStateWithLifecycle()
    // Lote mpv 3 (v1.38.0) — Playback avançado: opções do mpv e detecção de hardware do upscaling.
    val mpvDisplayFps by playerPrefs.mpvDisplayFps.collectAsStateWithLifecycle()
    val mpvVideoSync by playerPrefs.mpvVideoSync.collectAsStateWithLifecycle()
    val mpvHwdec by playerPrefs.mpvHwdec.collectAsStateWithLifecycle()
    val mpvCacheSecs by playerPrefs.mpvCacheSecs.collectAsStateWithLifecycle()
    val mpvPitchCorrection by playerPrefs.mpvPitchCorrection.collectAsStateWithLifecycle()
    // Lote mpv 4 (v1.39.0) — Frente 3: pré-carregar o motor, scripts Lua e
    // servidor IPC do mpv.
    val mpvPreload by playerPrefs.mpvPreload.collectAsStateWithLifecycle()
    val mpvScriptsEnabled by playerPrefs.mpvScripts.collectAsStateWithLifecycle()
    val mpvIpcEnabled by playerPrefs.mpvIpc.collectAsStateWithLifecycle()
    val mpvSubPos by playerPrefs.mpvSubPos.collectAsStateWithLifecycle()
    val enhanceHwMode by playerPrefs.enhanceHwMode.collectAsStateWithLifecycle()
    val enhanceHwClass by playerPrefs.enhanceHwClass.collectAsStateWithLifecycle()
    SettingsPageScaffold("MPV", "Motor do player, opções do Libmpv, scripts Lua e Melhorar imagem", onBack, highlightAnchor = highlightAnchor) {
        // Motor do player (Lote 4, v1.12.0): player padrão e decoder de vídeo.
        // Buffer do player (Lote 5 item 1, v1.14.0): perfil de buffer do ExoPlayer.
        SettingsSection("Motor do player", anchor = SettingsAnchors.MPV_ENGINE)
        SettingsCard {
            PrefRow(
                "Player padrão",
                listOf("exoplayer" to "ExoPlayer (interno)", "libmpv" to "Libmpv (interno)", "external" to "Player externo (VLC, MX…)"),
                playerEngine, playerPrefs::setEngine,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Decoder de vídeo",
                listOf("hw" to "Hardware (HW)", "hw_fallback" to "Hardware com fallback"),
                playerDecoder, playerPrefs::setDecoder,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Buffer do player",
                listOf("low" to "Baixo", "normal" to "Normal", "high" to "Alto"),
                playerBuffer, playerPrefs::setBuffer,
            )
        }
        Text(
            "Buffer: Baixo guarda menos vídeo na frente (começa mais rápido, trava mais fácil se a internet oscilar), Normal é o padrão do ExoPlayer e Alto guarda até 3 minutos (aguenta internet instável, mas espera mais no início). Vale a partir da próxima reprodução. Não vale para TV ao vivo, player externo nem Cast.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Text(
            "Em fonte P2P, o painel \"Infos técnicas\" do player mostra seeds, conexões, velocidade de download e buffer no player, atualizados enquanto o painel está aberto.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Text(
            "Player externo abre o link da fonte no seletor do sistema quando ela resolve — ou na hora, pelo chip \"Player externo\" do player. Precisa de um player instalado; torrent sem link do debrid e fonte que só toca com header não abrem fora. Libmpv é experimental: se ele falhar ao iniciar, o player volta sozinho p/ o ExoPlayer e avisa; som de outra fonte, Volume Boost, equalizador e modo noturno ainda não valem com o Libmpv. Vale a partir da próxima reprodução.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Text(
            "Decoder: HW usa só o hardware do aparelho. Com fallback, se o decoder de hardware falhar ao iniciar, o player tenta outro decoder do aparelho antes de trocar de fonte. Não há decoder por software: ele exigiria a extensão FFmpeg do Media3, não incluída. O decoder vale a partir da próxima reprodução.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        // Opções do Libmpv (Lote mpv 3, v1.38.0): só valem com o Libmpv
        // ativo; valem a partir da próxima reprodução (ou na hora, com o
        // player na pilha). A taxa da tela fixa o display-fps quando a
        // detecção automática do mpv erra no painel do aparelho.
        SettingsSection("Opções do Libmpv", anchor = SettingsAnchors.MPV_OPTIONS)
        SettingsCard {
            PrefRow(
                "Taxa da tela do mpv",
                listOf(0f to "Automática", 24f to "24 Hz", 25f to "25 Hz", 30f to "30 Hz", 50f to "50 Hz", 60f to "60 Hz", 120f to "120 Hz"),
                mpvDisplayFps, playerPrefs::setMpvDisplayFps,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Sincronização de vídeo",
                listOf("display-resample" to "Display (resample)", "display-vdrop" to "Display (vdrop)", "audio" to "Áudio"),
                mpvVideoSync, playerPrefs::setMpvVideoSync,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Decodificação por hardware",
                listOf("mediacodec" to "Hardware (zero-copy)", "mediacodec-copy" to "Hardware (com cópia)", "no" to "Software"),
                mpvHwdec, playerPrefs::setMpvHwdec,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Cache do mpv",
                listOf(0 to "Padrão do mpv", 10 to "10 s", 30 to "30 s", 60 to "60 s", 120 to "120 s"),
                mpvCacheSecs, playerPrefs::setMpvCacheSecs,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Posição das legendas",
                listOf(100 to "Embaixo", 90 to "Um pouco acima", 80 to "Acima", 70 to "Mais acima", 50 to "Meio da tela"),
                mpvSubPos, playerPrefs::setMpvSubPos,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Speed,
                title = "Manter o tom da voz ao mudar a velocidade",
                sub = if (mpvPitchCorrection) "A voz não distorce quando a velocidade muda (mpv e ExoPlayer)"
                    else "A voz acompanha a velocidade (efeito \"voz de esquilo\")",
                trailing = { Switch(checked = mpvPitchCorrection, onCheckedChange = { playerPrefs.setMpvPitchCorrection(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setMpvPitchCorrection(!mpvPitchCorrection) },
                grouped = true,
            )
        }
        // Lote mpv 4 (v1.39.0) — Frente 3: extras do mpv (pré-carregar o
        // motor, scripts Lua e servidor IPC). Valem a partir da próxima
        // reprodução (o pré-carregar, ao abrir o próximo detalhe).
        SettingsSection("Extras do mpv", anchor = SettingsAnchors.MPV_EXTRAS)
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Bolt,
                title = "Pré-carregar o motor Libmpv",
                sub = if (mpvPreload) "Ligado: o motor já está quente ao abrir o player"
                    else "Desligado: o motor só inicia ao abrir o player (sem o gasto de memória antes)",
                trailing = { Switch(checked = mpvPreload, onCheckedChange = { playerPrefs.setMpvPreload(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setMpvPreload(!mpvPreload) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Code,
                title = "Scripts Lua do mpv",
                sub = if (mpvScriptsEnabled) "Ligado: carrega os .lua da pasta de scripts no próximo init do Libmpv"
                    else "Desligado: os .lua da pasta de scripts não carregam",
                trailing = { Switch(checked = mpvScriptsEnabled, onCheckedChange = { playerPrefs.setMpvScripts(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setMpvScripts(!mpvScriptsEnabled) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Dns,
                title = "Servidor IPC do mpv",
                sub = if (mpvIpcEnabled) "Ligado: controla o player por socket — só outro app no aparelho acessa"
                    else "Desligado por padrão: controla o player por socket — só outro app no aparelho acessa",
                trailing = { Switch(checked = mpvIpcEnabled, onCheckedChange = { playerPrefs.setMpvIpc(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setMpvIpc(!mpvIpcEnabled) },
                grouped = true,
            )
        }
        // Scripts Lua do mpv (Lote de correções, v1.42.0 — Frente 2):
        // instalar/ligar/desligar/excluir de dentro do app.
        MpvScriptsManager()
        Text(
            "yt-dlp não disponível nesta versão do motor (o AAR não traz o binário) — as fontes já chegam resolvidas pelos addons, então nada muda.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        // Melhorar imagem (Lote mpv 2, v1.37.0): a detecção de hardware
        // do upscaling vive aqui; o painel em si fica no player, com o
        // motor Libmpv (a busca apontava p/ Reprodução — agora p/ cá).
        SettingsSection("Melhorar imagem", anchor = SettingsAnchors.MPV_ENHANCE)
        SettingsCard {
            PrefRow(
                "Detecção de hardware do upscaling",
                listOf("auto" to "Automática", "weak" to "Fraca", "strong" to "Forte"),
                enhanceHwMode, playerPrefs::setEnhanceHwMode,
            )
        }
        Text(
            "Força do aparelho: " + when {
                enhanceHwMode == "weak" -> "Fraca (forçada aqui) — o Auto do upscaling fica no preset mais leve."
                enhanceHwMode == "strong" -> "Forte (forçada aqui)."
                enhanceHwClass == "weak" -> "Fraca (medida) — o Auto do upscaling fica no preset mais leve."
                enhanceHwClass == "strong" -> "Forte (medida)."
                else -> "Ainda não medida — mede sozinha na 1ª vez que o mpv é usado."
            },
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Text(
            "Taxa da tela: Automática deixa o mpv detectar; se o vídeo engasgar depois de alguns segundos, fixe a taxa do seu painel. Hardware com cópia copia cada quadro p/ a CPU e pode travar aparelho fraco — só use se o zero-copy falhar.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
    }
}

/** Linha de um botão do player: rótulo, subir/descer e liga/desliga. */
@Composable
private fun PlayerButtonRow(
    label: String,
    enabled: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (canUp) TextPrimary else TextTertiary)
        }
        IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Descer", tint = if (canDown) TextPrimary else TextTertiary)
        }
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.focusProperties { canFocus = false })
    }
}

/** Lista dos idiomas de legenda. Na TV o foco já começa no idioma atual. */
@Composable
private fun SubLangDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit, title: String = "Idioma da legenda") {
    val langs = SubLangs.all
    val start = langs.indexOfFirst { it.key == current }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val isTv = com.aotv.app.ui.common.LocalIsTv.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(langs, key = { _, l -> l.key }) { i, l ->
                    val fr = remember { FocusRequester() }
                    LangOption(l.label, l.key == current, Modifier.focusRequester(fr)) { onPick(l.key) }
                    // No celular o toque basta. Foco puxado ali acenderia a borda sem ninguém pedir.
                    if (isTv && i == start) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) } },
    )
}

@Composable
private fun LangOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (focused || selected) AccentSoft else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, "Selecionado", tint = Accent)
    }
}

/** Uma preferência de escolha única em pílulas. Salva na hora (é local, sem rede). */
@Composable
private fun <T> PrefRow(title: String, options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            com.aotv.app.ui.common.FilterChipPill(label, v == value, onClick = { onPick(v) })
        }
    }
}
