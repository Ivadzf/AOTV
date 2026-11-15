package com.aotv.app.ui.settings

import android.content.Context
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.DeviceFacts
import com.aotv.app.data.local.CrashLog
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.PosterInfo
import com.aotv.app.data.local.TuningSuggestion
import com.aotv.app.data.local.bufferLabel
import com.aotv.app.data.local.measureDeviceFacts
import com.aotv.app.data.local.ramGb
import com.aotv.app.data.local.suggestTuning
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

/**
 * Diagnóstico (Lote 11, v1.21.0 — ideia do fork KE).
 *
 * Aparelho, addons/fontes, memória e tempos de quadro — tudo MEDIDO na hora
 * (nada inventado: sem dado, a linha diz que não mediu). Os tempos de quadro
 * são medidos com o Choreographer enquanto a tela está aberta (média, p95 e
 * quantos quadros passaram de 33 ms); antes de 10 amostras, a tela diz que
 * a medição está em andamento. A sugestão de ajuste é a mesma da primeira
 * abertura ([suggestTuning]): buffer pelo tamanho da RAM e "Só Wi-Fi" quando
 * a rede medida é móvel. Aplicar nunca é automático — é o botão desta tela.
 *
 * A taxa da tela é só informada: o player acompanha a taxa da tela sozinho
 * e não há ajuste de frame-rate no app, então ela não vira sugestão.
 * Na TV a tela funciona igual (mesma moldura das telas de VPN/Watch Party);
 * o amostrador de quadros mede a interface da TV como a do celular.
 *
 * Último crash (Lote 13, v1.23.0 — Frente 3, Bug 1 da TV): o [CrashLog]
 * grava stack trace + aparelho/Android/hora num arquivo interno quando o
 * app fecha sozinho, e esta tela mostra ele — a causa do crash da Início
 * da TV só é confirmada quando o log aparecer aqui. Sem registro, a seção
 * diz que não houve crash. O botão "Limpar registro" apaga o arquivo.
 */

/** Estatísticas dos quadros medidos desde que a tela abriu. */
data class FrameStats(val samples: Int, val avgMs: Double, val p95Ms: Double, val slowFrames: Int)

/**
 * Amostrador de tempos de quadro via Choreographer. Só roda enquanto a tela
 * está aberta (start/stop no DisposableEffect). Guarda no máximo os últimos
 * 240 intervalos; [stats] devolve null antes de 10 amostras.
 */
class FrameTimeSampler {
    private val intervals = ArrayDeque<Long>()
    private var lastNanos = 0L
    private var running = false
    private val callback: Choreographer.FrameCallback = Choreographer.FrameCallback { t ->
        if (lastNanos != 0L) {
            val dt = (t - lastNanos) / 1_000_000
            if (dt in 1..1000) {
                intervals.addLast(dt)
                while (intervals.size > 240) intervals.removeFirst()
            }
        }
        lastNanos = t
        if (running) Choreographer.getInstance().postFrameCallback(callback)
    }

    fun start() {
        if (running) return
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    fun stats(): FrameStats? {
        if (intervals.size < 10) return null
        val sorted = intervals.sorted()
        val p95 = sorted[((sorted.size - 1) * 0.95).toInt()].toDouble()
        val slow = intervals.count { it > 33 }
        return FrameStats(intervals.size, intervals.average(), p95, slow)
    }
}

/** Estado da tela de diagnóstico. */
data class DiagnosticsUiState(
    val facts: DeviceFacts? = null,
    val addonsTotal: Int = 0,
    val addonsEnabled: Int = 0,
    val cachedTitles: Int = 0,
    val frameStats: FrameStats? = null,
    val suggestion: TuningSuggestion? = null,
    val currentBuffer: String = "normal",
    val currentWifiOnly: Boolean = false,
    val applied: Boolean = false,
    /** Registro do último crash (Lote 13, v1.23.0), ou null sem crash. */
    val lastCrash: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val posterInfo: PosterInfo,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _ui = MutableStateFlow(DiagnosticsUiState())
    val ui: StateFlow<DiagnosticsUiState> = _ui.asStateFlow()
    private val sampler = FrameTimeSampler()

    init {
        refresh()
    }

    /** Mede tudo de novo (aparelho, addons, fontes em cache e sugestão). */
    fun refresh() {
        val facts = measureDeviceFacts(appContext)
        val addons = prefs.addons.value
        _ui.value = _ui.value.copy(
            facts = facts,
            addonsTotal = addons.size,
            addonsEnabled = addons.count { it.enabled },
            cachedTitles = posterInfo.quality.value.size,
            currentBuffer = prefs.playerBuffer,
            currentWifiOnly = prefs.downloadsWifiOnly,
            suggestion = suggestTuning(facts, prefs.playerBuffer, prefs.downloadsWifiOnly),
            applied = false,
            lastCrash = CrashLog.read(appContext),
        )
    }

    /** Apaga o registro do último crash e atualiza a tela. */
    fun clearCrashLog() {
        CrashLog.clear(appContext)
        refresh()
    }

    fun startSampling() {
        sampler.start()
    }

    fun stopSampling() {
        sampler.stop()
    }

    /** Atualiza as estatísticas dos quadros com o que foi medido até agora. */
    fun refreshFrameStats() {
        sampler.stats()?.let { _ui.value = _ui.value.copy(frameStats = it) }
    }

    /** Aplica a sugestão (buffer e/ou "Só Wi-Fi") e mostra "aplicada". */
    fun applySuggestion() {
        val s = _ui.value.suggestion ?: return
        if (s.buffer != null) prefs.playerBuffer = s.buffer
        if (s.wifiOnly != null) prefs.downloadsWifiOnly = s.wifiOnly
        refresh()
        _ui.value = _ui.value.copy(applied = true)
    }
}

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        viewModel.startSampling()
        onDispose { viewModel.stopSampling() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.refreshFrameStats()
        }
    }
    VpnPartyScaffold("Diagnóstico", "Aparelho, fontes e quadros — tudo medido", onBack, highlightAnchor = highlightAnchor) {
        val facts = state.facts
        VpnPartySection("Aparelho", anchor = SettingsAnchors.DIAG_DEVICE)
        DiagCard {
            DiagRow("Aparelho", facts?.model ?: "não medido")
            DiagRow("Android", facts?.let { if (it.androidRelease.isNotBlank()) "Android ${it.androidRelease} (API ${it.androidSdk})" else "API ${it.androidSdk}" } ?: "não medido")
            DiagRow("Tipo", facts?.let { (if (it.isTv) "TV" else "Celular/tablet") + " · ${it.screenWidthDp} dp de largura" } ?: "não medido")
            DiagRow("Memória", facts?.let { "${ramGb(it.totalRamBytes)} GB total · ${ramGb(it.availRamBytes)} GB livres" } ?: "não medida")
            DiagRow("Taxa da tela", facts?.let { if (it.refreshRateHz > 0f) "${it.refreshRateHz.toInt()} Hz" else "não medida" } ?: "não medida")
            DiagRow("Rede", facts?.let { it.networkKind + if (it.meteredNetwork) " (medida)" else "" } ?: "não medida")
        }
        VpnPartySection("Addons e fontes", anchor = SettingsAnchors.DIAG_ADDONS)
        DiagCard {
            DiagRow("Addons Stremio", "${state.addonsEnabled} ativos de ${state.addonsTotal}")
            DiagRow("Fontes em cache", "${state.cachedTitles} títulos com resumo (de até 500)")
        }
        Text(
            "O resumo das fontes é o que os selos de qualidade do pôster mostram (4K, Remux, BluRay, WEB-DL, DV, HDR10+). Sem fonte lida, sem selo.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        VpnPartySection("Último crash", anchor = SettingsAnchors.DIAG_CRASH)
        if (state.lastCrash == null) {
            DiagCard {
                Text(
                    "Nenhum crash registrado desde que este registro foi instalado. Se o app fechar sozinho, volte aqui: o registro aparece nesta seção.",
                    fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp,
                )
            }
        } else {
            DiagCard {
                Text(
                    state.lastCrash.take(4000),
                    fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp,
                )
            }
            Text(
                "Só fica no aparelho: nada deste registro sai do app. Anote o começo do erro (a primeira linha depois de \"Thread:\") e mande p/ quem mantém o app.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            AotvButton("Limpar registro", { viewModel.clearCrashLog() }, tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
        VpnPartySection("Quadros", anchor = SettingsAnchors.DIAG_FRAMES)
        DiagCard {
            val stats = state.frameStats
            if (stats == null) {
                DiagRow("Tempos de quadro", "Medindo… role a tela por alguns segundos")
            } else {
                DiagRow("Média", "${msText(stats.avgMs)} ms por quadro")
                DiagRow("P95", "${msText(stats.p95Ms)} ms por quadro")
                DiagRow("Quadros lentos", "${stats.slowFrames} acima de 33 ms em ${stats.samples} medidos")
            }
        }
        VpnPartySection("Sugestão de ajuste", anchor = SettingsAnchors.DIAG_SUGGESTION)
        // Local val (não a prop delegada): o smart cast funciona aqui.
        val suggestion = if (dismissed) null else state.suggestion
        if (state.applied) {
            DiagCard {
                Text(
                    "Sugestão aplicada. O buffer vale a partir da próxima reprodução; o \"Só Wi-Fi\" já vale.",
                    fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp,
                )
            }
        } else if (suggestion != null) {
            DiagCard {
                suggestion.reasons.forEach { reason ->
                    Text(reason, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                }
                if (suggestion.buffer != null) DiagRow("Buffer", "${bufferLabel(state.currentBuffer)} → ${bufferLabel(suggestion.buffer)}")
                if (suggestion.wifiOnly != null) DiagRow("Só Wi-Fi", "Desligado → Ligado")
            }
            AotvButton("Aplicar sugestão", { viewModel.applySuggestion() }, tone = ButtonTone.PRIMARY, modifier = Modifier.fillMaxWidth())
            AotvButton("Manter padrão", { dismissed = true }, tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth())
        } else {
            DiagCard {
                Text(
                    "Nada a sugerir: o buffer e a rede já estão no perfil certo p/ este aparelho.",
                    fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp,
                )
            }
        }
        Text(
            "A taxa da tela é só informada: o player acompanha a taxa da tela sozinho e não há ajuste de frame-rate no app.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        AotvButton("Atualizar medições", { viewModel.refresh() }, tone = ButtonTone.SECONDARY, modifier = Modifier.fillMaxWidth())
    }
}

/** Milissegundos com 1 casa decimal, vírgula brasileira ("16,7"). */
private fun msText(ms: Double): String = String.format(Locale.US, "%.1f", ms).replace('.', ',')

/** Cartão simples de linhas (rótulo à esquerda, valor à direita). */
@Composable
private fun DiagCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(BackgroundCard, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
