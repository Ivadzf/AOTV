package com.aotv.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.plugins.InstalledPlugin
import com.aotv.app.data.plugins.PluginLastRun
import com.aotv.app.data.plugins.PluginRepo
import com.aotv.app.data.plugins.PluginStore
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.LocalIsTv
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Tela de Plugins (scrapers JS do Nuvio), no layout do JOTV v1.0.16: resumo "Neste perfil",
 * busca e filtros por repositório/estado/tipo, status por plugin na lista (o diagnóstico da
 * v1.0.3), menus por provedor e por repositório, e Preferências e backup (timeout,
 * simultâneos, cache, backup/restore). Instala repositórios por URL de manifest, liga/desliga
 * cada plugin, atualiza, remove e edita as configurações do onSettings().
 *
 * O campo de instalar já vem com o repo do Davi preenchido; nada é instalado sem o toque em Instalar.
 */
@Composable
fun PluginsScreen(
    onBack: () -> Unit,
    onAddons: () -> Unit,
    onLive: () -> Unit,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf(false) }
    var pastedBackup by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var selectedRepo by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf<Pair<PluginRepo, InstalledPlugin>?>(null) }
    var sourceTest by remember { mutableStateOf<Pair<String, String>?>(null) }
    var repo by remember { mutableStateOf<PluginRepo?>(null) }
    var remove by remember { mutableStateOf<PluginRepo?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(viewModel.backup()) } ?: error("Arquivo não disponível") } }
            Toast.makeText(context, if (result.isSuccess) "Backup salvo sem senhas e sessões." else "Não foi possível salvar o backup.", Toast.LENGTH_LONG).show()
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val data = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val output = StringBuilder(); val buffer = CharArray(8192)
                    while (true) { val n = reader.read(buffer); if (n < 0) break; require(output.length + n <= 2 * 1024 * 1024); output.append(buffer, 0, n) }
                    output.toString()
                } ?: error("Arquivo não disponível")
            } }
            data.onSuccess(viewModel::restore).onFailure { Toast.makeText(context, "Não foi possível ler este backup.", Toast.LENGTH_LONG).show() }
        }
    }
    val rows = remember(state.repos, state.plugins, state.lastRun, query, filter, selectedRepo, selectedType) {
        val normalized = java.text.Normalizer.normalize(query.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        state.repos.flatMap { r -> state.plugins.filter { it.repoUrl.equals(r.url, true) }.map { r to it } }.filter { (r, p) ->
            (selectedRepo.isBlank() || r.url == selectedRepo) && (selectedType.isBlank() || selectedType in p.supportedTypes) &&
                (filter != "enabled" || p.enabled && r.enabled) && (filter != "disabled" || !p.enabled || !r.enabled) &&
                (filter != "error" || run {
                    val s = state.lastRun[p.id]?.status
                    s == "timeout" || s == "error"
                }) &&
                (normalized.isBlank() || normalized in java.text.Normalizer.normalize("${p.name} ${r.name}".lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), ""))
        }
    }
    val total = state.plugins.size
    val active = if (!state.globalEnabled) 0 else state.repos.filter { it.enabled }.sumOf { r -> state.plugins.count { it.repoUrl.equals(r.url, true) && it.enabled } }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pagePadding(), vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!LocalIsTv.current) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary) }
            PageHeader("Plugins", Modifier.weight(1f), sub = "Instale repositórios e escolha quais provedores buscam suas fontes.")
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = pagePadding()), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── Resumo "Neste perfil" ──
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Neste perfil", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("$active de $total provedores ativos", color = TextPrimary, fontSize = 17.sp)
                    Text("${state.repos.size} repositórios. A ordem dos repositórios e provedores define a prioridade das fontes.", color = TextSecondary, fontSize = 14.sp)
                    AotvButton(if (state.globalEnabled) "Pausar plugins" else "Ativar plugins", { viewModel.setGlobalEnabled(!state.globalEnabled) }, compact = true)
                    Text("Outras fontes", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    AotvButton("Addons Stremio", onAddons, compact = true, icon = Icons.Default.Extension)
                    AotvButton("Canais ao vivo", onLive, compact = true, icon = Icons.Default.LiveTv)
                    Text("Os provedores são executados neste aparelho. A disponibilidade das fontes depende de cada provedor.", color = TextSecondary, fontSize = 14.sp)
                    Text("Senhas e sessões ficam neste perfil e fora do backup.", color = TextSecondary, fontSize = 13.sp)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChipPill("Adicionar por link", false, { input = true }, icon = Icons.Default.Add, busy = state.installing) }
                    item { FilterChipPill("Atualizar todos", false, viewModel::refreshAll, icon = Icons.Default.Refresh, busy = state.refreshing.isNotEmpty()) }
                    item { FilterChipPill("Preferências e backup", false, { choice = "options" }, icon = Icons.Default.Tune) }
                }
            }
            if (state.installing || state.refreshing.isNotEmpty()) item {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Accent)
                    Text(if (state.installing) "Instalando…" else "Carregando plugins…", color = TextSecondary)
                }
            }
            state.message?.let { item { Text(it, color = TextSecondary, modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(14.dp)) } }
            state.error?.let { item { Text(it, color = TextPrimary, modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(14.dp)) } }
            state.networkTest?.let { item { Text(it, color = TextSecondary, modifier = Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(14.dp)) } }
            item {
                OutlinedTextField(query, { query = it.take(100) }, singleLine = true, label = { Text("Buscar provedor ou repositório") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChipPill(state.repos.firstOrNull { it.url == selectedRepo }?.name ?: "Todos os repositórios", selectedRepo.isNotBlank(), { choice = "repos" }) }
                    item { FilterChipPill(when (filter) { "enabled" -> "Ativados"; "disabled" -> "Desativados"; "error" -> "Com erro"; else -> "Todos os estados" }, filter != "all", { choice = "states" }) }
                    item { FilterChipPill(if (selectedType.isBlank()) "Filmes e séries" else if (selectedType == "movie") "Filmes" else "Séries", selectedType.isNotBlank(), { choice = "types" }) }
                    if (query.isNotBlank() || selectedRepo.isNotBlank() || selectedType.isNotBlank() || filter != "all") item { FilterChipPill("Limpar filtros", false, { query = ""; selectedRepo = ""; selectedType = ""; filter = "all" }, icon = Icons.Default.Close) }
                }
            }
            if (state.repos.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Escolha seus provedores", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Cole o link de um repositório Nuvio, addon Stremio ou arquivo JavaScript para começar. Os provedores novos começam ativados.", color = TextSecondary, fontSize = 16.sp)
                }
            } else if (rows.isEmpty()) item { Text("Nenhum provedor neste filtro. Use Limpar filtros para ver todos.", color = TextSecondary, modifier = Modifier.padding(16.dp)) }
            items(rows, key = { (_, p) -> p.id }) { (r, p) ->
                var focused by remember { mutableStateOf(false) }
                val lastRun = state.lastRun[p.id]
                val activeRow = p.enabled && r.enabled && state.globalEnabled
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (activeRow) AccentSoft else BackgroundCard)
                    .border(if (focused) 2.dp else 0.dp, if (focused) TextPrimary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                    .onFocusChanged { focused = it.isFocused }.clickable { provider = r to p }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.Extension, null, tint = TextSecondary, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(r.name + " · " + p.supportedTypes.joinToString(" · ") { if (it == "movie") "Filmes" else if (it == "tv" || it == "series") "Séries" else it }, color = TextSecondary, fontSize = 13.sp)
                        Text(formatLastRun(lastRun), color = TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (p.id in state.testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Accent)
                    } else {
                        Text(if (!state.globalEnabled || !r.enabled) "Pausado" else if (p.enabled) "Ativado" else "Desativado", color = if (activeRow) Accent else TextSecondary, fontSize = 13.sp)
                    }
                }
            }
            if (state.repos.isNotEmpty()) item { AotvButton("Gerenciar repositórios", { choice = "manage" }, compact = true, icon = Icons.Default.Layers) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (input) PluginInputDialog("Adicionar plugin ou addon", "Link de repositório, manifest.json ou arquivo .js", "Instalar", state.installing, PluginStore.DEFAULT_REPO_URL, { input = false }) { value ->
        input = false
        keyboard?.hide()
        viewModel.install(value)
    }
    if (pastedBackup) PluginInputDialog("Restaurar plugins", "JSON do backup do AOTV ou do JOTV", "Restaurar", false, "", { pastedBackup = false }, multiline = true) { value -> pastedBackup = false; viewModel.restore(value) }
    val settingsPlugin = state.settingsPlugin
    if (settingsPlugin != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeSettings() },
            title = { Text("Configurações — ${settingsPlugin.name}") },
            text = {
                when {
                    state.settingsLoading -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.settingsDescriptors.isEmpty() -> {
                        Text("Esse plugin não tem configurações.")
                    }
                    else -> {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            state.settingsDescriptors.forEach { d ->
                                when (d.type) {
                                    "header" -> Text(
                                        d.label, fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp, color = TextPrimary,
                                    )
                                    "select" -> {
                                        Text(d.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        val current = state.settingsValues[d.key]?.toString()
                                        d.options.forEach { opt ->
                                            Row(
                                                Modifier.fillMaxWidth().clickable {
                                                    viewModel.setSettingValue(d.key, opt.value)
                                                },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = current == opt.value,
                                                    onClick = { viewModel.setSettingValue(d.key, opt.value) },
                                                )
                                                Text(opt.label, modifier = Modifier.padding(start = 4.dp))
                                            }
                                        }
                                    }
                                    "toggle" -> {
                                        val checked = state.settingsValues[d.key] as? Boolean
                                            ?: (d.defaultValue as? Boolean ?: false)
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(d.label, modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = checked,
                                                onCheckedChange = { viewModel.setSettingValue(d.key, it) },
                                            )
                                        }
                                    }
                                    "text" -> {
                                        Text(d.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        OutlinedTextField(
                                            value = state.settingsValues[d.key]?.toString().orEmpty(),
                                            onValueChange = { viewModel.setSettingValue(d.key, it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveSettings() }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeSettings() }) { Text("Cancelar") }
            },
        )
    }
    provider?.let { (r, p) ->
        val lastRun = state.lastRun[p.id]
        PluginMenu(p.name, listOf(
            PluginMenuItem(if (p.enabled) "Desativar provedor" else "Ativar provedor") { viewModel.togglePlugin(p.id) },
            PluginMenuItem("Configurar") { viewModel.openSettings(p) },
            PluginMenuItem("Testar carregamento") { viewModel.testPlugin(p.id) },
            PluginMenuItem("Testar fontes para um título") { sourceTest = p.id to p.name },
            PluginMenuItem("Subir prioridade") { viewModel.movePlugin(p.id, -1) },
            PluginMenuItem("Descer prioridade") { viewModel.movePlugin(p.id, 1) },
            PluginMenuItem("Gerenciar ${r.name}") { repo = r },
            PluginMenuItem(
                "Versão ${p.version.ifBlank { "não informada" }}",
                p.supportedTypes.joinToString(" · ") { if (it == "movie") "Filmes" else "Séries" },
                enabled = false,
            ),
            PluginMenuItem(lastRun?.let { formatLastRun(it) } ?: "Ainda não executado.", lastRun?.let { "${it.millis} ms" }.orEmpty(), enabled = false),
        )) { provider = null }
    }
    sourceTest?.let { (id, title) -> PluginSourceTestDialog(title, { sourceTest = null }) { tmdb, series, season, episode ->
        sourceTest = null
        viewModel.testSources(id, tmdb, series, season, episode)
    } }
    repo?.let { r ->
        val count = state.plugins.count { it.repoUrl.equals(r.url, true) }
        PluginMenu(r.name, listOf(
            PluginMenuItem("Atualizar repositório") { viewModel.refreshRepo(r.url) },
            PluginMenuItem(if (r.enabled) "Pausar repositório" else "Retomar repositório") { viewModel.toggleRepo(r.url) },
            PluginMenuItem("Ativar todos os provedores disponíveis") { viewModel.toggleAllPlugins(r.url, true) },
            PluginMenuItem("Desativar todos os provedores") { viewModel.toggleAllPlugins(r.url, false) },
            PluginMenuItem("Subir prioridade do repositório") { viewModel.moveRepo(r.url, -1) },
            PluginMenuItem("Descer prioridade do repositório") { viewModel.moveRepo(r.url, 1) },
            PluginMenuItem("Remover repositório") { remove = r },
            PluginMenuItem(r.url, "$count plugin(s)", enabled = false),
        )) { repo = null }
    }
    remove?.let { r -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remover ${r.name}?") }, text = { Text("Os provedores e as configurações deste repositório serão removidos deste perfil.") }, confirmButton = { TextButton(onClick = { viewModel.removeRepo(r.url); remove = null }) { Text("Remover") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancelar") } }, containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary) }
    choice?.let { menu ->
        val items = when (menu) {
            "repos" -> listOf(PluginMenuItem("Todos", selected = selectedRepo.isBlank()) { selectedRepo = "" }) + state.repos.map { r -> PluginMenuItem(r.name, selected = selectedRepo == r.url) { selectedRepo = r.url } }
            "manage" -> state.repos.map { r -> PluginMenuItem(r.name, "${state.plugins.count { it.repoUrl.equals(r.url, true) }} provedores") { repo = r } }
            "states" -> listOf("all" to "Todos", "enabled" to "Ativados", "disabled" to "Desativados", "error" to "Com erro").map { (key, title) -> PluginMenuItem(title, selected = filter == key) { filter = key } }
            "types" -> listOf("" to "Todos", "movie" to "Filmes", "tv" to "Séries").map { (key, title) -> PluginMenuItem(title, selected = selectedType == key) { selectedType = key } }
            "timeout" -> listOf(30, 45, 60, 90, 120, 180).map { n -> PluginMenuItem("$n segundos", selected = state.timeoutSec == n) { viewModel.setTimeoutSec(n) } }
            "concurrency" -> listOf(32, 10, 2, 1).map { n -> PluginMenuItem(if (n == 32) "Buscar em paralelo" else if (n == 10) "Dez por vez" else if (n == 1) "Um de cada vez" else "Até dois ao mesmo tempo", if (n == 32) "As fontes aparecem conforme cada provedor responde." else if (n == 10) "O padrão do AOTV." else "", selected = state.concurrency == n) { viewModel.setConcurrency(n) } }
            else -> listOf(
                PluginMenuItem(if (state.globalEnabled) "Pausar todos os plugins" else "Ativar plugins") { viewModel.setGlobalEnabled(!state.globalEnabled) },
                PluginMenuItem(if (state.autoUpdate) "Desativar atualização automática" else "Ativar atualização automática", "Conferir a lista a cada 6 horas.") { viewModel.setAutoUpdate(!state.autoUpdate) },
                PluginMenuItem("Tempo máximo por provedor", "${state.timeoutSec} segundos") { choice = "timeout" },
                PluginMenuItem("Provedores simultâneos", state.concurrency.toString()) { choice = "concurrency" },
                PluginMenuItem("Limpar cache dos provedores", "Cookies e sessões dos provedores serão apagados.") { viewModel.clearCache() },
                PluginMenuItem("Limpar cookies dos plugins") { viewModel.clearCookies() },
                PluginMenuItem("Testar rede", "O fetch do motor busca o manifest do repositório.") { viewModel.testNetwork() },
                PluginMenuItem("Exportar backup sem senhas") { createBackup.launch("AOTV-plugins.json") },
                PluginMenuItem("Importar arquivo de backup") { importBackup.launch(arrayOf("application/json", "text/plain")) },
                PluginMenuItem("Colar backup JSON") { pastedBackup = true },
                PluginMenuItem("Gerenciar repositórios") { choice = "manage" },
            )
        }
        val current = menu
        PluginMenu(when (menu) { "repos", "manage" -> "Repositórios"; "states" -> "Estado do provedor"; "types" -> "Tipo de conteúdo"; "timeout" -> "Tempo por provedor"; "concurrency" -> "Execução simultânea"; else -> "Preferências e backup" }, items.map { item -> item.copy(action = item.action?.let { action -> { if (choice == current) choice = null; action() } }) }) { choice = null }
    }
}

private data class PluginMenuItem(val label: String, val description: String = "", val selected: Boolean = false, val enabled: Boolean = true, val action: (() -> Unit)? = null)

@Composable
private fun PluginMenu(title: String, items: List<PluginMenuItem>, closeOnSelect: Boolean = true, onClose: () -> Unit) {
    val initialFocus = remember { FocusRequester() }
    val first = items.indexOfFirst { it.enabled && it.action != null }
    androidx.compose.runtime.LaunchedEffect(title) { kotlinx.coroutines.delay(100); if (first >= 0) runCatching { initialFocus.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.9f).widthIn(max = 760.dp).fillMaxHeight(.85f).background(BackgroundCard, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar", tint = TextPrimary) } }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items.size) { index ->
                    val item = items[index]
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (item.selected) AccentSoft else Surface2)
                        .border(if (focused) 2.dp else 0.dp, if (focused) TextPrimary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                        .onFocusChanged { focused = it.isFocused }.then(if (index == first) Modifier.focusRequester(initialFocus) else Modifier)
                        .then(if (item.enabled && item.action != null) Modifier.clickable { if (closeOnSelect) onClose(); item.action.invoke() } else Modifier).padding(16.dp)) {
                        Text(item.label + if (item.selected) " · Selecionado" else "", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        if (item.description.isNotBlank()) Text(item.description, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginInputDialog(title: String, label: String, ok: String, busy: Boolean, initial: String, onClose: () -> Unit, multiline: Boolean = false, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = { OutlinedTextField(value, { value = it.take(if (multiline) 2 * 1024 * 1024 else 8192) }, label = { Text(label) }, singleLine = !multiline, maxLines = if (multiline) 8 else 1) },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = !busy && value.isNotBlank()) { Text(ok) } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }, containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary)
}

@Composable
private fun PluginSourceTestDialog(title: String, onClose: () -> Unit, onTest: (Int, Boolean, Int?, Int?) -> Unit) {
    var tmdb by remember { mutableStateOf("") }
    var series by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf("1") }
    var episode by remember { mutableStateOf("1") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Testar $title") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Informe o identificador do título no TMDB. O teste não ativa o provedor.")
            OutlinedTextField(tmdb, { tmdb = it.filter(Char::isDigit).take(9) }, label = { Text("Identificador TMDB") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(series, { series = it }); Text("Série") }
            if (series) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(season, { season = it.filter(Char::isDigit).take(4) }, label = { Text("Temporada") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(episode, { episode = it.filter(Char::isDigit).take(4) }, label = { Text("Episódio") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }, confirmButton = { TextButton(enabled = (tmdb.toIntOrNull() ?: 0) > 0 && (!series || (season.toIntOrNull() ?: 0) > 0 && (episode.toIntOrNull() ?: 0) > 0), onClick = { onTest(tmdb.toInt(), series, season.toIntOrNull(), episode.toIntOrNull()) }) { Text("Testar fontes") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }, containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary)
}

/** Linha curta do último resultado ("Último teste: 3 streams · fetch: 200 · 2s · há 2 min"). */
private fun formatLastRun(run: PluginLastRun?): String = when {
    run == null -> "Nunca testado"
    run.status == "ok" -> "Último teste: ${run.streams} stream(s)" +
        run.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
        " · ${formatAgo(run.at)}"
    run.status == "empty" -> "Último teste: nenhum stream" +
        run.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
        " · ${formatAgo(run.at)}"
    run.status == "timeout" -> "O plugin excedeu o tempo de execução (${run.millis / 1000}s) · ${formatAgo(run.at)}"
    else -> "Erro: ${run.detail.ifBlank { "falha" }} · ${formatAgo(run.at)}"
}

private fun formatAgo(at: Long): String {
    val min = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "agora"
        min < 60 -> "há $min min"
        else -> "há ${min / 60} h"
    }
}
