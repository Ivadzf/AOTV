package com.aotv.app.ui.kodi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.kodi.KodiAddonStore
import com.aotv.app.data.kodi.KodiPluginRunner
import com.aotv.app.data.kodi.KodiSettingsStore
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Tela "Addons Kodi" (v1.4, Fase 4).
 *
 * Gerencia os addons Kodi instalados: instala por ZIP (SAF) ou por
 * URL (download direto do ZIP, igual ao Kodi original), lista,
 * liga/desliga, abre o addon, edita os settings e mostra o
 * diagnóstico (dependências, service.py). Remover também está aqui.
 *
 * O Vulgata é Proprietary — nunca é embutido: entra sempre por
 * ZIP/URL escolhido pelo usuário, intacto.
 */
@Composable
fun KodiAddonsScreen(
    onBack: () -> Unit,
    onOpenAddon: (String) -> Unit = {},
    onOpenSpike: () -> Unit = {},
    viewModel: KodiAddonsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.installZip(it) } }
    var showUrlDialog by remember { mutableStateOf(false) }
    var menuAddon by remember { mutableStateOf<KodiAddonStore.KodiAddon?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .topInsetsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = pagePadding(), vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            PageHeader(
                "Addons Kodi",
                Modifier.weight(1f),
                sub = "Instale por ZIP ou URL, ative cada addon e ajuste as configurações.",
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = pagePadding()),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton(
                        "Instalar por ZIP",
                        { pickZip.launch(arrayOf("*/*")) },
                        compact = true,
                        icon = Icons.Default.Add,
                        busy = state.installing,
                        busyLabel = "Instalando…",
                    )
                    AotvButton(
                        "Instalar por URL",
                        { showUrlDialog = true },
                        compact = true,
                        icon = Icons.Default.Add,
                        busy = state.installingUrl,
                        busyLabel = "Baixando…",
                    )
                }
            }
            if (state.installing || state.installingUrl) item {
                Row(
                    Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Accent)
                    Text(
                        if (state.installing) "Instalando…" else "Baixando o ZIP…",
                        color = TextSecondary,
                    )
                }
            }
            state.message?.let { item { Text(it, color = TextSecondary, modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(14.dp)) } }
            state.error?.let { item { Text(it, color = TextPrimary, modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(14.dp)) } }
            if (state.addons.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Nenhum addon Kodi instalado", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Instale um ZIP pelo botão acima — o Vulgata e o BrazucaPlay entram por aqui. " +
                                "Por URL, cole o link direto do ZIP do addon.",
                            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                        )
                    }
                }
            }
            items(state.addons, key = { it.id }) { addon ->
                KodiAddonRow(
                    addon = addon,
                    displayName = viewModel.displayName(addon),
                    onClick = { menuAddon = addon },
                    onToggle = { viewModel.toggleEnabled(addon, it) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showUrlDialog) KodiInstallUrlDialog(
        busy = state.installingUrl,
        onClose = { showUrlDialog = false },
        onInstall = { url -> showUrlDialog = false; viewModel.installFromUrl(url) },
    )
    menuAddon?.let { addon ->
        KodiAddonMenuDialog(
            addon = addon,
            displayName = viewModel.displayName(addon),
            onClose = { menuAddon = null },
            onOpen = { menuAddon = null; onOpenAddon(addon.id) },
            onSettings = { menuAddon = null; viewModel.openSettings(addon) },
            onDiagnostics = { menuAddon = null; viewModel.openDiagnostics(addon) },
            onRemove = { menuAddon = null; viewModel.askRemove(addon) },
        )
    }
    val settingsAddon = state.settingsAddon
    if (settingsAddon != null) {
        KodiAddonSettingsDialog(
            addon = settingsAddon,
            displayName = viewModel.displayName(settingsAddon),
            defs = state.settingsDefs,
            values = state.settingsValues,
            isForced = { id -> viewModel.isForced(settingsAddon.id, id) },
            runningAction = state.runningAction,
            saving = state.settingsSaving,
            onValueChange = viewModel::setSettingsValue,
            onRunAction = { action -> viewModel.runAction(settingsAddon, action) },
            onSave = { viewModel.saveSettings() },
            onClose = { viewModel.closeSettings() },
        )
    }
    val diagAddon = state.diagAddon
    if (diagAddon != null) {
        KodiAddonDiagnosticsDialog(
            addon = diagAddon,
            displayName = viewModel.displayName(diagAddon),
            loading = state.diagLoading,
            issues = state.diagIssues,
            hasService = state.diagHasService,
            serviceRunning = state.diagServiceRunning,
            onOpenSpike = { viewModel.closeDiagnostics(); onOpenSpike() },
            onClose = { viewModel.closeDiagnostics() },
        )
    }
    state.removeAddon?.let { addon ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRemove() },
            title = { Text("Remover ${viewModel.displayName(addon)}?") },
            text = { Text("O addon e os dados dele (configurações, favoritos, cache) serão apagados deste aparelho.") },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(addon) }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRemove() }) { Text("Cancelar") }
            },
            containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
        )
    }
}

@Composable
private fun KodiAddonRow(
    addon: KodiAddonStore.KodiAddon,
    displayName: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Default.Extension, null, tint = TextSecondary, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${addon.id} · v${addon.version.ifBlank { "?" }}",
                color = TextSecondary, fontSize = 13.sp,
            )
            Text(
                if (addon.enabled) "Ativado" else "Desativado",
                color = if (addon.enabled) Accent else TextSecondary, fontSize = 13.sp,
            )
        }
        Switch(checked = addon.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun KodiInstallUrlDialog(
    busy: Boolean,
    onClose: () -> Unit,
    onInstall: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Instalar por URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cole o link direto do ZIP do addon (ex.: https://…/plugin.video.x.zip). Índices de repositório (addons.xml) não são suportados.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(2048) },
                    label = { Text("URL do ZIP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInstall(url.trim()) },
                enabled = !busy && (url.trim().startsWith("http://") || url.trim().startsWith("https://")),
            ) { Text("Instalar") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancelar") }
        },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

@Composable
private fun KodiAddonMenuDialog(
    addon: KodiAddonStore.KodiAddon,
    displayName: String,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    onRemove: () -> Unit,
) {
    val isVideo = addon.extensionPoints.any { (point, _) -> point == "xbmc.python.pluginsource" }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isVideo && addon.enabled) KodiMenuRow("Abrir", "Navegar no catálogo do addon", onOpen)
                KodiMenuRow("Configurações", "Ajustar as opções do addon", onSettings)
                KodiMenuRow("Diagnóstico", "Dependências e serviço em segundo plano", onDiagnostics)
                KodiMenuRow("Remover", "Apagar o addon e os dados dele", onRemove)
                Spacer(Modifier.height(8.dp))
                Text("${addon.id} · v${addon.version.ifBlank { "?" }}", color = TextTertiary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Fechar") }
        },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

@Composable
private fun KodiMenuRow(label: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (description.isNotBlank()) Text(description, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun KodiAddonSettingsDialog(
    addon: KodiAddonStore.KodiAddon,
    displayName: String,
    defs: List<KodiSettingsStore.SettingDefinition>,
    values: Map<String, String>,
    isForced: (String) -> Boolean,
    runningAction: String?,
    saving: Boolean,
    onValueChange: (String, String) -> Unit,
    onRunAction: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Configurações — $displayName") },
        text = {
            if (defs.isEmpty()) {
                Text("Esse addon não tem configurações.")
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    defs.filter { it.visible != "false" }.forEach { d ->
                        val label = KodiPluginRunner.cleanKodiText(d.label.ifBlank { d.id })
                        val forced = isForced(d.id)
                        when (d.type) {
                            "lsep" -> Text(
                                label, color = TextTertiary, fontSize = 13.sp,
                            )
                            "bool" -> {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(label, modifier = Modifier.weight(1f), color = TextPrimary, fontSize = 14.sp)
                                    Switch(
                                        checked = values[d.id] == "true",
                                        onCheckedChange = { onValueChange(d.id, if (it) "true" else "false") },
                                        enabled = !forced,
                                    )
                                }
                                if (forced) Text(
                                    "Desligado por decisão — não pode ser alterado.",
                                    color = TextTertiary, fontSize = 12.sp,
                                )
                            }
                            "enum" -> {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                val options = d.values.split("|")
                                val current = values[d.id].orEmpty()
                                options.forEachIndexed { idx, opt ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable(enabled = !forced) {
                                            onValueChange(d.id, idx.toString())
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = current == idx.toString(),
                                            onClick = { onValueChange(d.id, idx.toString()) },
                                            enabled = !forced,
                                        )
                                        Text(
                                            KodiPluginRunner.cleanKodiText(opt),
                                            modifier = Modifier.padding(start = 4.dp),
                                            color = TextPrimary, fontSize = 14.sp,
                                        )
                                    }
                                }
                                if (forced) Text(
                                    "Desligado por decisão — não pode ser alterado.",
                                    color = TextTertiary, fontSize = 12.sp,
                                )
                            }
                            "action" -> {
                                AotvButton(
                                    label.ifBlank { "Executar" },
                                    { onRunAction(d.action) },
                                    compact = true,
                                    busy = runningAction != null,
                                    busyLabel = "Executando…",
                                    enabled = d.action.isNotBlank(),
                                )
                            }
                            else -> {
                                // text, number e o resto: campo de texto.
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                OutlinedTextField(
                                    value = values[d.id].orEmpty(),
                                    onValueChange = { onValueChange(d.id, it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (d.type == "number") KeyboardType.Number else KeyboardType.Text,
                                    ),
                                    enabled = !forced,
                                )
                                if (forced) Text(
                                    "Desligado por decisão — não pode ser alterado.",
                                    color = TextTertiary, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !saving) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancelar") }
        },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

@Composable
private fun KodiAddonDiagnosticsDialog(
    addon: KodiAddonStore.KodiAddon,
    displayName: String,
    loading: Boolean,
    issues: List<KodiAddonStore.DependencyIssue>,
    hasService: Boolean,
    serviceRunning: Boolean,
    onOpenSpike: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Diagnóstico — $displayName") },
        text = {
            if (loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("ID: ${addon.id}", color = TextPrimary, fontSize = 14.sp)
                    Text("Versão: ${addon.version.ifBlank { "?" }}", color = TextPrimary, fontSize = 14.sp)
                    Text(
                        "Extensões: ${addon.extensionPoints.joinToString(", ") { it.first }.ifBlank { "—" }}",
                        color = TextPrimary, fontSize = 14.sp,
                    )
                    Text("Dependências:", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (issues.isEmpty()) {
                        Text("Todas as dependências satisfeitas.", color = TextSecondary, fontSize = 14.sp)
                    } else {
                        issues.forEach { issue ->
                            Text(
                                "• ${issue.dependency.addon}: ${issue.detail}",
                                color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp,
                            )
                        }
                    }
                    Text("Serviço em segundo plano:", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Tem service.py: ${if (hasService) "sim" else "não"}",
                        color = TextSecondary, fontSize = 14.sp,
                    )
                    Text(
                        "Serviço rodando: ${if (serviceRunning) "sim" else "não"}",
                        color = TextSecondary, fontSize = 14.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSpike) { Text("Diagnóstico (spike)") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Fechar") }
        },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}
