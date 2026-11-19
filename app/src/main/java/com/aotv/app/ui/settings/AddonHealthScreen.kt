package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.diagnostics.AddonCallLogEntry
import com.aotv.app.data.diagnostics.AddonHealthEntry
import com.aotv.app.data.diagnostics.AddonHealthHistory
import com.aotv.app.data.diagnostics.AddonHealthProbe
import com.aotv.app.data.diagnostics.AddonHealthStatus
import com.aotv.app.data.diagnostics.AddonKind
import com.aotv.app.data.diagnostics.LatencySample
import com.aotv.app.data.diagnostics.classifyLatency
import com.aotv.app.data.diagnostics.statusColor
import com.aotv.app.data.diagnostics.statusLabel
import com.aotv.app.data.kodi.KodiAddonStore
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.plugins.PluginStore
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Addon health check (Lote 25, v1.35.0 — Frente B, item 6; ideia do MKCinema,
 * reimplementada — o MK testa cada addon e explica o que está lento).
 *
 * Mede a latência do GET do manifest de cada addon Stremio instalado (verde
 * rápido, âmbar acima de 2 s, vermelho com erro + último erro) e mostra
 * também os providers Nuvio (contagem + estado do manifest local) e os
 * addons Kodi instalados (estado). Addon Studio = o detalhe por addon
 * (URL do manifest, histórico de latência, último erro). Addon Lab = o log
 * das últimas chamadas de addon, com botão "Repetir" p/ refazer o último
 * fetch de manifest. O histórico (20 por addon) e o log (50) ficam numa
 * pref global em JSON, com teto — nada cresce sem limite.
 */

/** Estado da tela de saúde dos addons. */
data class AddonHealthUiState(
    val stremio: List<AddonHealthEntry> = emptyList(),
    val nuvio: List<AddonHealthEntry> = emptyList(),
    val kodi: List<AddonHealthEntry> = emptyList(),
    val checking: Boolean = false,
    val log: List<AddonCallLogEntry> = emptyList(),
    /** Chave do addon Stremio aberto no detalhe (Addon Studio); null = nenhum. */
    val selectedKey: String? = null,
)

@HiltViewModel
class AddonHealthViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val pluginStore: PluginStore,
    private val kodiStore: KodiAddonStore,
) : ViewModel() {
    // Declarado ANTES do init (lição do crash v1.34.0): o init lança
    // coroutine que escreve neste campo.
    private val _ui = MutableStateFlow(AddonHealthUiState())
    val ui: StateFlow<AddonHealthUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = AddonHealthUiState(
                stremio = buildStremio(),
                nuvio = buildNuvio(),
                kodi = buildKodi(),
                log = AddonHealthHistory.log(prefs),
            )
        }
    }

    /** Histórico de latência de um addon (mais recente primeiro) p/ o Studio. */
    fun historyFor(key: String): List<LatencySample> = AddonHealthHistory.samplesFor(prefs, key)

    /** Abre/fecha o detalhe de um addon Stremio (Addon Studio). */
    fun selectAddon(key: String?) {
        _ui.update { it.copy(selectedKey = key) }
    }

    /** Verifica todos os addons Stremio em paralelo (o resto é local, só relê). */
    fun checkAll() {
        if (_ui.value.checking) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(checking = true) }
            val checked = coroutineScope {
                _ui.value.stremio.map { entry -> async { checkStremio(entry) } }.awaitAll()
            }
            _ui.update {
                it.copy(
                    stremio = checked,
                    nuvio = buildNuvio(),
                    kodi = buildKodi(),
                    checking = false,
                    log = AddonHealthHistory.log(prefs),
                )
            }
        }
    }

    /** Refaz o fetch do manifest de um addon Stremio (botão "Repetir" do Lab). */
    fun repeatProbe(key: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = _ui.value.stremio.firstOrNull { it.key == key } ?: return@launch
            val checked = checkStremio(entry.copy(url = url))
            _ui.update { state ->
                state.copy(
                    stremio = state.stremio.map { if (it.key == key) checked else it },
                    log = AddonHealthHistory.log(prefs),
                )
            }
        }
    }

    /** Apaga o log do Addon Lab (o histórico de latência continua). */
    fun clearLog() {
        AddonHealthHistory.clearLog(prefs)
        _ui.update { it.copy(log = emptyList()) }
    }

    private suspend fun checkStremio(entry: AddonHealthEntry): AddonHealthEntry {
        if (!entryEnabled(entry)) return entry.copy(status = AddonHealthStatus.DISABLED)
        val now = System.currentTimeMillis()
        val (latency, error) = AddonHealthProbe.probeManifest(entry.url)
        val status = if (error != null) AddonHealthStatus.ERROR else classifyLatency(latency)
        AddonHealthHistory.record(
            prefs, entry.key,
            LatencySample(now, latency ?: 0L, error == null),
            AddonCallLogEntry(now, entry.key, entry.url, latency, error == null, error),
        )
        return entry.copy(status = status, latencyMs = latency, lastError = error, lastCheckedAt = now)
    }

    /** Addons Stremio instalados, com o estado da última verificação (ou "não verificado"). */
    private fun buildStremio(): List<AddonHealthEntry> {
        val cachedNames = prefs.addonMetaFlow.value
        return prefs.addons.value.map { addon ->
            val base = addon.url.trim().removeSuffix("/manifest.json").removeSuffix("/")
            val name = addon.id.ifBlank {
                cachedNames[base]?.name?.takeIf { it.isNotBlank() }
                    ?: runCatching { java.net.URI(addon.url).host }.getOrNull()
                    ?: addon.url
            }
            val last = AddonHealthHistory.lastSample(prefs, addon.url)
            val status = when {
                !addon.enabled -> AddonHealthStatus.DISABLED
                last == null -> AddonHealthStatus.UNKNOWN
                !last.ok -> AddonHealthStatus.ERROR
                else -> classifyLatency(last.ms)
            }
            AddonHealthEntry(
                key = addon.url,
                name = name,
                url = addon.url,
                kind = AddonKind.STREMIO,
                status = status,
                latencyMs = last?.ms,
                lastCheckedAt = last?.at ?: 0L,
            )
        }
    }

    /**
     * Providers Nuvio por repositório: contagem + estado do manifest LOCAL
     * (o código .js de cada provider está no aparelho?). Sem chamada de rede:
     * o manifest local é o que foi baixado na instalação do repo.
     */
    private fun buildNuvio(): List<AddonHealthEntry> {
        val plugins = pluginStore.plugins
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return pluginStore.repos.map { repo ->
            val mine = plugins.filter { it.repoUrl.equals(repo.url, true) }
            val missing = mine.filter { pluginStore.readCode(it.id) == null }
            val status = when {
                mine.isEmpty() -> AddonHealthStatus.LOCAL
                missing.isNotEmpty() -> AddonHealthStatus.ERROR
                else -> AddonHealthStatus.LOCAL
            }
            AddonHealthEntry(
                key = repo.url,
                name = repo.name.ifBlank { repo.url },
                url = repo.url,
                kind = AddonKind.NUVIO,
                status = status,
                lastError = missing.firstOrNull()?.let { "código local de ${it.id} ausente" },
                lastCheckedAt = repo.updatedAt,
                detail = "${mine.size} provider(s) · manifest local de ${dateFmt.format(Date(repo.updatedAt))}",
            )
        }
    }

    /** Addons Kodi instalados (estado local: habilitado ou desativado). */
    private fun buildKodi(): List<AddonHealthEntry> {
        return runCatching { kodiStore.listAddons() }.getOrDefault(emptyList()).map { addon ->
            AddonHealthEntry(
                key = "kodi:${addon.id}",
                name = addon.name.ifBlank { addon.id },
                url = addon.dir.path,
                kind = AddonKind.KODI,
                status = if (addon.enabled) AddonHealthStatus.OK else AddonHealthStatus.DISABLED,
                detail = "v${addon.version}",
            )
        }
    }

    private fun entryEnabled(entry: AddonHealthEntry): Boolean =
        prefs.addons.value.firstOrNull { it.url == entry.key }?.enabled ?: true
}

@Composable
fun AddonHealthScreen(
    onBack: () -> Unit,
    viewModel: AddonHealthViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    VpnPartyScaffold("Saúde dos addons", "Latência de cada manifest — verde rápido, âmbar lento, vermelho com erro", onBack) {
        AotvButton(
            "Verificar agora",
            { viewModel.checkAll() },
            tone = ButtonTone.PRIMARY, modifier = Modifier.fillMaxWidth(),
            busy = state.checking, busyLabel = "Verificando…",
        )
        val slow = state.stremio.count { it.status == AddonHealthStatus.SLOW }
        val failed = state.stremio.count { it.status == AddonHealthStatus.ERROR }
        HealthCard {
            Text(
                "${state.stremio.size} addon(s) Stremio · ${state.nuvio.size} repositório(s) Nuvio · ${state.kodi.size} addon(s) Kodi" +
                    if (failed > 0 || slow > 0) " — $failed com erro, $slow lento(s)" else " — nada lento no momento",
                fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp,
            )
        }

        VpnPartySection("Addons Stremio")
        if (state.stremio.isEmpty()) {
            HealthCard {
                Text("Nenhum addon Stremio instalado. Instale pela Loja ou pelo gerenciador de addons.", fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            }
        } else {
            state.stremio.forEach { entry ->
                HealthRow(entry, onClick = { viewModel.selectAddon(entry.key) })
            }
        }
        // Addon Studio — detalhe do addon selecionado.
        val selected = state.selectedKey?.let { key -> state.stremio.firstOrNull { it.key == key } }
        if (selected != null) {
            VpnPartySection("Addon Studio — ${selected.name}")
            HealthCard {
                Text("Manifest: ${selected.url}", fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                val lastError = selected.lastError
                if (lastError != null) {
                    Text("Último erro: $lastError", fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                }
                val history = viewModel.historyFor(selected.key)
                if (history.isEmpty()) {
                    Text("Sem histórico ainda — toque em \"Verificar agora\".", fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                } else {
                    Text("Histórico de latência (mais recente primeiro):", fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                    history.take(AddonHealthHistory.MAX_SAMPLES_PER_ADDON).forEach { sample ->
                        Text(
                            "${fmtTime(sample.at)} — " + if (sample.ok) "${sample.ms} ms" else "erro",
                            fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp,
                        )
                    }
                }
            }
            AotvButton(
                "Repetir este addon",
                { viewModel.repeatProbe(selected.key, selected.url) },
                tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth(),
            )
            AotvButton("Fechar detalhe", { viewModel.selectAddon(null) }, tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth())
        }

        VpnPartySection("Providers Nuvio")
        if (state.nuvio.isEmpty()) {
            HealthCard {
                Text("Nenhum repositório Nuvio instalado.", fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            }
        } else {
            state.nuvio.forEach { entry -> HealthRow(entry, onClick = null) }
        }

        VpnPartySection("Addons Kodi")
        if (state.kodi.isEmpty()) {
            HealthCard {
                Text("Nenhum addon Kodi instalado.", fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            }
        } else {
            state.kodi.forEach { entry -> HealthRow(entry, onClick = null) }
        }

        // Addon Lab — log das últimas chamadas + replay.
        VpnPartySection("Addon Lab — log das chamadas")
        val lastLog = state.log.firstOrNull()
        val lastIsStremio = lastLog != null && state.stremio.any { it.key == lastLog.addonKey }
        if (state.log.isEmpty()) {
            HealthCard {
                Text("Nenhuma chamada registrada ainda — toque em \"Verificar agora\".", fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            }
        } else {
            state.log.take(10).forEach { call ->
                val isStremio = state.stremio.any { it.key == call.addonKey }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(if (call.ok) statusColor(AddonHealthStatus.OK) else statusColor(AddonHealthStatus.ERROR)))
                    Column(Modifier.weight(1f)) {
                        Text(call.addonKey, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                        Text(
                            "${fmtTime(call.at)} — " + if (call.ok) "${call.ms ?: 0} ms" else (call.error ?: "erro"),
                            color = TextTertiary, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                    if (isStremio) {
                        AotvButton(
                            "Repetir",
                            { viewModel.repeatProbe(call.addonKey, call.url) },
                            tone = ButtonTone.SECONDARY, compact = true,
                        )
                    }
                }
            }
        }
        if (lastLog != null && lastIsStremio) {
            AotvButton(
                "Repetir última chamada",
                { viewModel.repeatProbe(lastLog.addonKey, lastLog.url) },
                tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.log.isNotEmpty()) {
            AotvButton("Limpar log", { viewModel.clearLog() }, tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Uma linha da lista de saúde: ponto colorido + nome + latência/erro. */
@Composable
private fun HealthRow(entry: AddonHealthEntry, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .background(BackgroundCard, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(statusColor(entry.status)))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val secondLine = when {
                entry.detail.isNotBlank() && entry.kind != AddonKind.STREMIO -> entry.detail
                entry.lastError != null -> entry.lastError
                entry.latencyMs != null -> "${entry.latencyMs} ms"
                else -> entry.url
            }
            Text(secondLine, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Text(statusLabel(entry.status), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Cartão simples de texto (mesmo visual dos cartões do Diagnóstico). */
@Composable
private fun HealthCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(BackgroundCard, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** "19/11 17:10" p/ o histórico e o log. */
private fun fmtTime(at: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(at))
