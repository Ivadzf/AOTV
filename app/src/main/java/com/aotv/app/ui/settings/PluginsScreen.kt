package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.plugins.InstalledPlugin
import com.aotv.app.data.plugins.PluginStore
import com.aotv.app.ui.common.GlassCircleButton
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * Tela de Plugins (scrapers JS do Nuvio). Instala repositórios por URL de
 * manifest, liga/desliga cada plugin, atualiza, remove, edita as
 * configurações do onSettings() e ajusta o timeout das execuções.
 *
 * O campo de instalar já vem com o repo do Davi preenchido; nada é instalado
 * sem o toque em Instalar.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onBack: () -> Unit,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(PluginStore.DEFAULT_REPO_URL) }
    val keyboard = LocalSoftwareKeyboardController.current
    val enabledCount = state.plugins.count { it.enabled }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .topInsetsPadding(),
    ) {
        // ── Topo ──
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pagePadding(), top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            PageHeader(
                "Plugins", Modifier.weight(1f),
                sub = if (state.plugins.isEmpty()) "Nenhum instalado"
                else "${state.plugins.size} instalados · $enabledCount ativos",
            )
            GlassCircleButton(
                Icons.Default.Refresh, { viewModel.refreshAll() },
                contentDescription = "Atualizar plugins",
                busy = state.refreshing.isNotEmpty(),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = pagePadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Instalar repositório ──
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Instalar repositório", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Cole a URL do manifest.json de um repositório de plugins do Nuvio. Os plugins passam a aparecer nas fontes dos filmes e episódios.",
                        color = TextSecondary, fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; if (state.error != null || state.installedNow != null) viewModel.clearError() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…/manifest.json") },
                        singleLine = true,
                    )
                    Button(
                        onClick = { keyboard?.hide(); viewModel.install(input) },
                        enabled = !state.installing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.installing) "Instalando…" else "Instalar")
                    }
                    state.error?.let { Text(it, color = com.aotv.app.ui.theme.Danger, fontSize = 13.sp) }
                    state.installedNow?.let { Text(it, color = Accent, fontSize = 13.sp) }
                }
            }

            // ── Timeout e cookies ──
            item {
                var sliderValue by remember(state.timeoutSec) { mutableStateOf(state.timeoutSec.toFloat()) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Timeout dos plugins: ${state.timeoutSec}s", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Tempo máximo de cada execução. Providers lentos (ex.: o PoW do Byse) precisam de mais tempo; 60s é o padrão do Nuvio.",
                        color = TextSecondary, fontSize = 13.sp,
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.setTimeoutSec(sliderValue.toInt()) },
                        valueRange = 30f..180f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { viewModel.clearCookies() }) {
                        Text("Limpar cookies dos plugins")
                    }
                }
            }

            // ── Repositórios ──
            if (state.repos.isNotEmpty()) {
                item {
                    com.aotv.app.ui.common.SettingsSection("Repositórios")
                }
                items(state.repos, key = { it.url }) { repo ->
                    val count = state.plugins.count { it.repoUrl.equals(repo.url, true) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(repo.name.ifBlank { repo.url }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                (if (repo.version.isNotBlank()) "v${repo.version} · " else "") + "$count plugin(s)",
                                color = TextSecondary, fontSize = 13.sp,
                            )
                        }
                        IconButton(onClick = { viewModel.refreshRepo(repo.url) }) {
                            if (repo.url in state.refreshing) CircularProgressIndicator(Modifier.size(20.dp))
                            else Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.removeRepo(repo.url) }) {
                            Icon(Icons.Default.Delete, "Remover", tint = TextSecondary)
                        }
                    }
                }
            }

            // ── Plugins ──
            if (state.plugins.isNotEmpty()) {
                item {
                    com.aotv.app.ui.common.SettingsSection("Plugins instalados")
                }
                items(state.plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        onToggle = { viewModel.togglePlugin(plugin.id) },
                        onSettings = { viewModel.openSettings(plugin) },
                        onRemove = { viewModel.removePlugin(plugin.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ── Diálogo de configurações do plugin (onSettings) ──
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
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Extension, null, tint = Accent, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(plugin.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                (if (plugin.version.isNotBlank()) "v${plugin.version} · " else "") +
                    plugin.supportedTypes.joinToString(" + ") { if (it == "movie") "filmes" else "séries" },
                color = TextSecondary, fontSize = 13.sp,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, "Configurações", tint = TextSecondary)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, "Remover", tint = TextSecondary)
        }
        Switch(checked = plugin.enabled, onCheckedChange = { onToggle() })
    }
}
