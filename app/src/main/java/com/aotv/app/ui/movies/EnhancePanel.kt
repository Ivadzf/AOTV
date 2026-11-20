package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.TextOnLight
import com.aotv.app.ui.theme.TextTertiary
import com.aotv.app.ui.tv.TvScaled

// Painel "Melhorar imagem" (Lote mpv 2, v1.37.0) — 3 abas (Upscaler,
// Filtros, Desempenho), mesmo SidePanel dos outros paineis do player.
//
// SO-MPV: sem o Libmpv o painel abre DESABILITADO, com explicacao curta
// (o chip continua visivel p/ o usuario descobrir o recurso). Nada aqui
// toca no ExoPlayer: os controles so chamam onStateChange e a sessao so
// aplica no mpv (ver PlayerSession.applyEnhanceEffects).
//
// D-pad: as linhas sao clicaveis (foco do Compose) e os sliders guardam o
// foco padrao — a sessao deixa as teclas passarem (ver o guarda
// panelKind == "enhance" no onKeyEvent do player, como os paineis de
// equalizador e modo noturno). Voltar fecha o painel.

/** Abas do painel. */
private const val ENHANCE_TAB_UPSCALER = 0
private const val ENHANCE_TAB_FILTERS = 1
private const val ENHANCE_TAB_PERF = 2

@Composable
fun EnhancePanel(
    state: EnhanceState,
    enabled: Boolean,
    perf: MpvPerf?,
    effectivePreset: String,
    effectiveQuality: String,
    watchdogCap: String,
    /** Força medida do hardware (Lote mpv 3): "" = ainda não medida, "weak" ou "strong". */
    hwClass: String,
    /** Modo da detecção de hardware: "auto", "weak" ou "strong". */
    hwMode: String,
    benchRunning: Boolean,
    benchResult: String?,
    videoW: Int,
    videoH: Int,
    videoFps: Double?,
    television: Boolean,
    onStateChange: (EnhanceState) -> Unit,
    onTestBench: () -> Unit,
    onComparePress: () -> Unit,
    onCompareRelease: () -> Unit,
    onOpenColor: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (television) {
        TvScaled {
            EnhancePanelBody(
                state = state,
                enabled = enabled,
                perf = perf,
                effectivePreset = effectivePreset,
                effectiveQuality = effectiveQuality,
                watchdogCap = watchdogCap,
                hwClass = hwClass,
                hwMode = hwMode,
                benchRunning = benchRunning,
                benchResult = benchResult,
                videoW = videoW,
                videoH = videoH,
                videoFps = videoFps,
                television = true,
                onStateChange = onStateChange,
                onTestBench = onTestBench,
                onComparePress = onComparePress,
                onCompareRelease = onCompareRelease,
                onOpenColor = onOpenColor,
                onDismiss = onDismiss,
            )
        }
    } else {
        EnhancePanelBody(
            state = state,
            enabled = enabled,
            perf = perf,
            effectivePreset = effectivePreset,
            effectiveQuality = effectiveQuality,
            watchdogCap = watchdogCap,
            hwClass = hwClass,
            hwMode = hwMode,
            benchRunning = benchRunning,
            benchResult = benchResult,
            videoW = videoW,
            videoH = videoH,
            videoFps = videoFps,
            television = false,
            onStateChange = onStateChange,
            onTestBench = onTestBench,
            onComparePress = onComparePress,
            onCompareRelease = onCompareRelease,
            onOpenColor = onOpenColor,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun EnhancePanelBody(
    state: EnhanceState,
    enabled: Boolean,
    perf: MpvPerf?,
    effectivePreset: String,
    effectiveQuality: String,
    watchdogCap: String,
    hwClass: String,
    hwMode: String,
    benchRunning: Boolean,
    benchResult: String?,
    videoW: Int,
    videoH: Int,
    videoFps: Double?,
    television: Boolean,
    onStateChange: (EnhanceState) -> Unit,
    onTestBench: () -> Unit,
    onComparePress: () -> Unit,
    onCompareRelease: () -> Unit,
    onOpenColor: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(ENHANCE_TAB_UPSCALER) }
    var advanced by rememberSaveable { mutableIntStateOf(0) }
    SidePanel(
        title = "Melhorar imagem",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        sub = "Só com o motor Libmpv — upscaler, filtros e desempenho",
    ) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!enabled) {
                item {
                    Text(
                        "Este painel só funciona com o motor Libmpv. Troque o motor em " +
                            "Configurações → Reprodução → Motor do player e reabra o vídeo. " +
                            "No ExoPlayer, a correção de cor continua no painel Cor.",
                        color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnhanceTabButton("Upscaler", tab == ENHANCE_TAB_UPSCALER, Modifier.weight(1f)) { tab = ENHANCE_TAB_UPSCALER }
                    EnhanceTabButton("Filtros", tab == ENHANCE_TAB_FILTERS, Modifier.weight(1f)) { tab = ENHANCE_TAB_FILTERS }
                    EnhanceTabButton("Desempenho", tab == ENHANCE_TAB_PERF, Modifier.weight(1f)) { tab = ENHANCE_TAB_PERF }
                }
            }
            when (tab) {
                ENHANCE_TAB_UPSCALER -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EnhanceTabButton("Predefinição", advanced == 0, Modifier.weight(1f)) { advanced = 0 }
                            EnhanceTabButton("Avançado", advanced == 1, Modifier.weight(1f)) { advanced = 1 }
                        }
                    }
                    if (advanced == 0) {
                        enhancePresetItems(
                            state = state,
                            enabled = enabled,
                            onStateChange = onStateChange,
                        )
                    } else {
                        enhanceAdvancedItems(
                            state = state,
                            enabled = enabled,
                            onStateChange = onStateChange,
                        )
                    }
                }
                ENHANCE_TAB_FILTERS -> {
                    enhanceFilterItems(
                        state = state,
                        enabled = enabled,
                        onStateChange = onStateChange,
                        onOpenColor = onOpenColor,
                    )
                }
                ENHANCE_TAB_PERF -> {
                    enhancePerfItems(
                        state = state,
                        enabled = enabled,
                        perf = perf,
                        effectivePreset = effectivePreset,
                        effectiveQuality = effectiveQuality,
                        watchdogCap = watchdogCap,
                        hwClass = hwClass,
                        hwMode = hwMode,
                        benchRunning = benchRunning,
                        benchResult = benchResult,
                        videoW = videoW,
                        videoH = videoH,
                        videoFps = videoFps,
                        onStateChange = onStateChange,
                        onTestBench = onTestBench,
                        onComparePress = onComparePress,
                        onCompareRelease = onCompareRelease,
                    )
                }
            }
        }
    }
}

// ── Aba Upscaler — Predefinicao ─────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.enhancePresetItems(
    state: EnhanceState,
    enabled: Boolean,
    onStateChange: (EnhanceState) -> Unit,
) {
    item { Eyebrow("Predefinição", Modifier.padding(top = 10.dp, start = 4.dp)) }
    EnhancePreset.entries.forEach { preset ->
        item {
            EnhanceRow(
                label = preset.label,
                sub = preset.sub,
                active = state.preset == preset.id,
                enabled = enabled,
                onClick = { onStateChange(applyEnhancePresetDefaults(state.copy(preset = preset.id, chainJson = ""))) },
            )
        }
    }
    val pinsChain = state.presetEnum.pinsChain
    item { Eyebrow("Qualidade", Modifier.padding(top = 10.dp, start = 4.dp)) }
    EnhanceQuality.entries.forEach { quality ->
        item {
            EnhanceRow(
                label = quality.label,
                sub = if (pinsChain) "Este preset fixa a própria cadeia" else if (quality == EnhanceQuality.FAST) "Variantes S — mais rápido" else "Variantes M/L — mais pesado",
                active = state.quality == quality.id,
                enabled = enabled && !pinsChain,
                onClick = { onStateChange(state.copy(quality = quality.id)) },
            )
        }
    }
    item { Eyebrow("Saída", Modifier.padding(top = 10.dp, start = 4.dp)) }
    EnhanceOutput.entries.forEach { output ->
        item {
            EnhanceRow(
                label = output.label,
                sub = when (output) {
                    EnhanceOutput.FIT -> "Reduz entre as ampliações, p/ caber na tela"
                    EnhanceOutput.X2 -> "Só a 1ª ampliação — sai 2x do vídeo"
                    EnhanceOutput.X4 -> "As duas ampliações inteiras — sai 4x do vídeo"
                },
                active = state.output == output.id,
                enabled = enabled && !pinsChain,
                onClick = { onStateChange(state.copy(output = output.id)) },
            )
        }
    }
    item {
        Text(
            "Auto escolhe pela placa, pelo vídeo e pela tela, cabendo em 60% do " +
                "tempo de um quadro. O vigia desce a qualidade se o aparelho " +
                "perder quadros — sem mexer na sua escolha.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
    }
}

// ── Aba Upscaler — Avancado ─────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.enhanceAdvancedItems(
    state: EnhanceState,
    enabled: Boolean,
    onStateChange: (EnhanceState) -> Unit,
) {
    // A cadeia em edicao: a propria do usuario, ou a do preset como ponto
    // de partida (a 1a edicao materializa a cadeia do preset na pref).
    val editing = state.customChain
        ?: EnhanceChain(enhancePresetChain(state.presetEnum, state.qualityEnum), 0)
    fun save(chain: EnhanceChain) {
        onStateChange(state.copy(chainJson = encodeEnhanceChain(chain)))
    }
    val conflict = enhanceChainConflict(editing.steps)
    val costMs = estimateChainCostMs(editing.steps, 1920, 1080)
    item { Eyebrow("Cadeia — ${editing.steps.size} etapas", Modifier.padding(top = 10.dp, start = 4.dp)) }
    if (editing.steps.isEmpty()) {
        item {
            Text(
                "Nenhuma etapa — este preset não usa shaders. Escolha um preset " +
                    "Anime4K na aba Predefinição ou adicione etapas abaixo.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
        }
    }
    editing.steps.forEachIndexed { idx, stepId ->
        val step = enhanceStepById(stepId) ?: return@forEachIndexed
        item {
            EnhanceChainRow(
                number = idx + 1,
                step = step,
                enabled = enabled,
                onMove = { delta ->
                    val to = idx + delta
                    if (to !in editing.steps.indices) return@EnhanceChainRow
                    val next = editing.steps.toMutableList()
                    next[idx] = next[to].also { next[to] = next[idx] }
                    save(editing.copy(steps = next))
                },
                onRemove = { save(editing.copy(steps = editing.steps - stepId)) },
                onSize = { size ->
                    val sibling = enhanceStepSiblingSize(stepId, size) ?: return@EnhanceChainRow
                    val next = editing.steps.toMutableList()
                    next[idx] = sibling
                    save(editing.copy(steps = next))
                },
            )
        }
    }
    item {
        EnhanceRow(
            label = "Nitidez: " + when (editing.sharp) {
                1 -> "Suave"
                2 -> "Forte"
                else -> "Desligada"
            },
            sub = "Suave usa Deblur_Original, Forte usa Deblur_DoG no fim da cadeia",
            active = editing.sharp > 0,
            enabled = enabled,
            onClick = { save(editing.copy(sharp = (editing.sharp + 1) % 3)) },
        )
    }
    if (conflict != null) {
        item {
            Text(
                conflict,
                color = Color(0xFFFF9E9E), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
    item {
        Text(
            "Custo estimado da cadeia: %.1f ms por quadro (a 1080p). O custo real " +
                "vem do mpv — ver a aba Desempenho.".format(java.util.Locale.ROOT, costMs),
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        )
    }
    if (state.chainJson.isNotBlank()) {
        item {
            EnhanceRow(
                label = "Voltar ao preset",
                sub = "Descarta a cadeia própria e volta a usar a do preset",
                active = false,
                enabled = enabled,
                onClick = { onStateChange(state.copy(chainJson = "")) },
            )
        }
    }
    item { Eyebrow("Adicionar etapa", Modifier.padding(top = 10.dp, start = 4.dp)) }
    EnhanceStepGroup.entries.forEach { group ->
        item { Eyebrow(group.label, Modifier.padding(top = 6.dp, start = 4.dp)) }
        ENHANCE_STEPS.filter { it.group == group }.forEach { step ->
            val inChain = step.id in editing.steps
            item {
                EnhanceRow(
                    label = step.label,
                    sub = step.sub + if (inChain) " — já está na cadeia" else "",
                    active = inChain,
                    enabled = enabled && !inChain,
                    onClick = { save(editing.copy(steps = editing.steps + step.id)) },
                )
            }
        }
    }
}

// ── Aba Filtros ─────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.enhanceFilterItems(
    state: EnhanceState,
    enabled: Boolean,
    onStateChange: (EnhanceState) -> Unit,
    onOpenColor: () -> Unit,
) {
    val deband = state.debandParts
    item { Eyebrow("Cor", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        EnhanceRow(
            label = "Brilho, contraste, saturação e matiz",
            sub = "Ficam no painel Cor — os 4 sliders já valem no mpv",
            active = false,
            enabled = enabled,
            onClick = onOpenColor,
        )
    }
    item {
        EnhanceSliderRow(
            label = "Gama",
            valueText = "%.0f".format(java.util.Locale.ROOT, state.gamma),
            value = state.gamma,
            range = -100f..100f,
            enabled = enabled,
            onChange = { onStateChange(state.copy(gamma = it)) },
        )
    }
    item { Eyebrow("Debanding", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        EnhanceRow(
            label = "Debanding",
            sub = "Tira as faixas de cor em degradês (céu, parede)",
            active = state.deband,
            enabled = enabled,
            onClick = { onStateChange(state.copy(deband = !state.deband)) },
        )
    }
    item {
        EnhanceSliderRow(
            label = "Iterações",
            valueText = deband.iter.toString(),
            value = deband.iter.toFloat(),
            range = 1f..8f,
            enabled = enabled && state.deband,
            onChange = { onStateChange(state.copy(debandCfg = encodeDebandCfg(deband.copy(iter = it.toInt())))) },
        )
    }
    item {
        EnhanceSliderRow(
            label = "Limiar",
            valueText = "%.0f".format(java.util.Locale.ROOT, deband.threshold),
            value = deband.threshold,
            range = 0f..200f,
            enabled = enabled && state.deband,
            onChange = { onStateChange(state.copy(debandCfg = encodeDebandCfg(deband.copy(threshold = it)))) },
        )
    }
    item {
        EnhanceSliderRow(
            label = "Alcance",
            valueText = "%.0f".format(java.util.Locale.ROOT, deband.range),
            value = deband.range,
            range = 1f..64f,
            enabled = enabled && state.deband,
            onChange = { onStateChange(state.copy(debandCfg = encodeDebandCfg(deband.copy(range = it)))) },
        )
    }
    item {
        EnhanceSliderRow(
            label = "Grão",
            valueText = "%.0f".format(java.util.Locale.ROOT, deband.grain),
            value = deband.grain,
            range = 0f..100f,
            enabled = enabled && state.deband,
            onChange = { onStateChange(state.copy(debandCfg = encodeDebandCfg(deband.copy(grain = it)))) },
        )
    }
    item { Eyebrow("Movimento e HDR", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        EnhanceRow(
            label = "Movimento suave",
            sub = "Cria quadros entre os do arquivo, no ritmo da tela — o estilo está na linha abaixo",
            active = state.smoothMotion,
            enabled = enabled,
            onClick = { onStateChange(state.copy(smoothMotion = !state.smoothMotion)) },
        )
    }
    item {
        // Lote mpv 3 (v1.38.0): estilo da interpolação (tscale do mpv) —
        // só vale com o Movimento suave ligado.
        val interpLabel = ENHANCE_INTERP_PRESETS.firstOrNull { it.first == state.interpPreset }?.second ?: "Padrão"
        EnhanceRow(
            label = "Estilo da interpolação: $interpLabel",
            sub = "Leve é barato (oversample); Suave e Cinema são mais suaves e mais pesados — só vale com o Movimento suave ligado",
            active = state.smoothMotion && state.interpPreset != "off",
            enabled = enabled,
            onClick = {
                val ids = ENHANCE_INTERP_PRESETS.map { it.first }
                val next = ids[(ids.indexOf(state.interpPreset) + 1) % ids.size]
                onStateChange(state.copy(interpPreset = next))
            },
        )
    }
    item {
        EnhanceRow(
            label = "Tone-mapping: " + state.toneMapping,
            sub = "HDR em tela comum — auto usa o padrão do mpv",
            active = state.toneMapping != "auto",
            enabled = enabled,
            onClick = {
                val next = ENHANCE_TONE_MAPPINGS[(ENHANCE_TONE_MAPPINGS.indexOf(state.toneMapping) + 1) % ENHANCE_TONE_MAPPINGS.size]
                onStateChange(state.copy(toneMapping = next))
            },
        )
    }
    item {
        EnhanceRow(
            label = "Calcular o pico do HDR",
            sub = "Mede o brilho máximo do vídeo p/ o tone-mapping (hdr-compute-peak)",
            active = state.hdrPeak,
            enabled = enabled,
            onClick = { onStateChange(state.copy(hdrPeak = !state.hdrPeak)) },
        )
    }
    item {
        EnhanceRow(
            label = "Entrelaçado: " + when (state.deinterlace) {
                "yes" -> "Ligado"
                "no" -> "Desligado"
                else -> "Automático"
            },
            sub = "Automático decide pelos sinais do vídeo",
            active = state.deinterlace != "no",
            enabled = enabled,
            onClick = {
                val next = when (state.deinterlace) {
                    "auto" -> "yes"
                    "yes" -> "no"
                    else -> "auto"
                }
                onStateChange(state.copy(deinterlace = next))
            },
        )
    }
    item { Eyebrow("Escaladores", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        EnhanceRow(
            label = "Ampliação: " + state.scale,
            sub = "Escalador da imagem (scale)",
            active = state.scale != "ewa_lanczos",
            enabled = enabled,
            onClick = {
                val next = ENHANCE_SCALERS[(ENHANCE_SCALERS.indexOf(state.scale) + 1) % ENHANCE_SCALERS.size]
                onStateChange(state.copy(scale = next))
            },
        )
    }
    item {
        EnhanceRow(
            label = "Croma: " + state.cscale,
            sub = "Escalador da cor (cscale)",
            active = state.cscale != "spline36",
            enabled = enabled,
            onClick = {
                val next = ENHANCE_SCALERS[(ENHANCE_SCALERS.indexOf(state.cscale) + 1) % ENHANCE_SCALERS.size]
                onStateChange(state.copy(cscale = next))
            },
        )
    }
    item {
        EnhanceRow(
            label = "Redução: " + state.dscale,
            sub = "Escalador da redução p/ a tela (dscale)",
            active = state.dscale != "mitchell",
            enabled = enabled,
            onClick = {
                val next = ENHANCE_SCALERS[(ENHANCE_SCALERS.indexOf(state.dscale) + 1) % ENHANCE_SCALERS.size]
                onStateChange(state.copy(dscale = next))
            },
        )
    }
    item {
        EnhanceSliderRow(
            label = "Antiring",
            valueText = "%.2f".format(java.util.Locale.ROOT, state.antiring).replace('.', ','),
            value = state.antiring,
            range = 0f..1f,
            enabled = enabled,
            onChange = { onStateChange(state.copy(antiring = it)) },
        )
    }
    item {
        Text(
            "O antiring vale p/ os três escaladores juntos (o mpv não tem um " +
                "antiring único). Retrô / pixel art usa nearest nos três.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
    }
}

// ── Aba Desempenho ──────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.enhancePerfItems(
    state: EnhanceState,
    enabled: Boolean,
    perf: MpvPerf?,
    effectivePreset: String,
    effectiveQuality: String,
    watchdogCap: String,
    hwClass: String,
    hwMode: String,
    benchRunning: Boolean,
    benchResult: String?,
    videoW: Int,
    videoH: Int,
    videoFps: Double?,
    onStateChange: (EnhanceState) -> Unit,
    onTestBench: () -> Unit,
    onComparePress: () -> Unit,
    onCompareRelease: () -> Unit,
) {
    val chainSteps = resolveEnhanceChain(state)
    val estMs = estimateChainCostMs(chainSteps, videoW, videoH)
    val budgetMs = autoBudgetMs(videoFps)
    val chainText = if (chainSteps.isEmpty()) "nenhum shader" else chainSteps.joinToString(", ")
    val budgetText = budgetMs?.let { " · orçamento (60% do quadro): %.1f ms".format(java.util.Locale.ROOT, it) } ?: ""
    item { Eyebrow("Renderizador", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        EnhanceRow(
            label = "Saída de vídeo: " + state.vo,
            sub = if (state.vo == "gpu-next") "gpu-next — mais moderno, pode pesar em placas fracas" else "gpu — o padrão, testado neste app",
            active = state.vo == "gpu-next",
            enabled = enabled,
            onClick = {
                val next = ENHANCE_VOS[(ENHANCE_VOS.indexOf(state.vo) + 1) % ENHANCE_VOS.size]
                onStateChange(state.copy(vo = next))
            },
        )
    }
    item { Eyebrow("Custo por quadro", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        Text(
            "Cadeia em uso: $chainText\n" +
                "Custo estimado: %.1f ms por quadro".format(java.util.Locale.ROOT, estMs) + budgetText,
            color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        )
    }
    if (perf != null) {
        item {
            Text(
                "Medido no mpv — total: %.2f ms por quadro".format(java.util.Locale.ROOT, perf.totalAvgMs) +
                    "\nQuadros perdidos: ${perf.dropped} · atrasados: ${perf.delayed} · fora de hora: ${perf.mistimed}",
                color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
        perf.passes.forEach { pass ->
            item {
                Text(
                    "${pass.desc}: %.2f ms".format(java.util.Locale.ROOT, pass.avgMs),
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }
        }
    } else {
        item {
            Text(
                "Sem dados do mpv ainda — abra o painel com o vídeo tocando.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
    item { Eyebrow("Auto e vigia", Modifier.padding(top = 10.dp, start = 4.dp)) }
    item {
        Text(
            "Em uso: ${EnhancePreset.byId(effectivePreset).label} (${EnhanceQuality.byId(effectiveQuality).label})" +
                if (watchdogCap.isNotBlank()) "\nTeto do vigia: ${EnhancePreset.byId(watchdogCap).label} — a placa aguentou até aí" else "\nTeto do vigia: nenhum ainda",
            color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        )
    }
    item {
        // Lote mpv 3: força do aparelho — medida na 1ª vez que o mpv é usado
        // (ou pelo "Testar a minha placa") ou forçada nas Configurações.
        val hwText = when {
            hwMode == "weak" -> "Fraca (forçada nas Configurações) — o Auto fica no preset mais leve"
            hwMode == "strong" -> "Forte (forçada nas Configurações)"
            hwClass == "weak" -> "Fraca (medida) — o Auto fica no preset mais leve"
            hwClass == "strong" -> "Forte (medida)"
            else -> "Ainda não medida — mede sozinha na 1ª vez que o mpv é usado, ou toque em \"Testar a minha placa\""
        }
        Text(
            "Força do aparelho: $hwText",
            color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        )
    }
    item {
        Button(
            onClick = onTestBench,
            enabled = enabled && !benchRunning,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = TextOnLight),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (benchRunning) "Medindo… (4 s)" else "Testar a minha placa", fontWeight = FontWeight.Bold)
        }
    }
    if (benchResult != null) {
        item {
            Text(
                benchResult,
                color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
    item {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) AccentSoft else Color.White.copy(alpha = 0.05f))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            onComparePress()
                            tryAwaitRelease()
                            onCompareRelease()
                        },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Comparar — segure p/ ver sem efeito",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
    item {
        Text(
            "Comparar desliga tudo enquanto o botão está pressionado e volta " +
                "ao sair o dedo. Testar a minha placa mede ~4 s com a cadeia " +
                "atual e diz qual preset o aparelho sustenta.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
    }
}

// ── Pecas ───────────────────────────────────────────────────────────────────

@Composable
private fun EnhanceTabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) Color.White else Color.White.copy(alpha = 0.05f)
    val fg = if (selected) TextOnLight else Color.White
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Linha de acao do painel (preset, toggle, seletor de opcao). */
@Composable
private fun EnhanceRow(
    label: String,
    sub: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.03f)
        active -> AccentSoft
        else -> Color.White.copy(alpha = 0.05f)
    }
    val fg = if (!enabled) TextTertiary else Color.White
    val subColor = TextTertiary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .then(if (active && enabled) Modifier.border(1.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, color = subColor, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (active) {
            Icon(Icons.Default.Check, null, tint = if (enabled) Accent else TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Linha com slider (mesmo desenho das linhas do painel de cor). */
@Composable
private fun EnhanceSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val fg = if (enabled) Color.White else TextTertiary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(valueText, color = TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Linha numerada da cadeia do modo Avancado (sobe/desce, tamanho da rede, tira). */
@Composable
private fun EnhanceChainRow(
    number: Int,
    step: EnhanceStep,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onSize: (String) -> Unit,
) {
    val size = enhanceStepSize(step.id)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$number.", color = TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(step.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${step.group.label} · ${"%.1f".format(java.util.Locale.ROOT, step.costMs)} ms", color = TextTertiary, fontSize = 13.sp, maxLines = 1)
        }
        if (step.networkSizes) {
            val nextSize = when (size) {
                "S" -> "M"
                "M" -> "L"
                else -> "S"
            }
            Text(
                size,
                color = TextOnLight, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Accent)
                    .clickable(enabled = enabled) { onSize(nextSize) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        IconButton(onClick = { onMove(-1) }, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ArrowUpward, "Subir", tint = Color.White)
        }
        IconButton(onClick = { onMove(1) }, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ArrowDownward, "Descer", tint = Color.White)
        }
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, "Tirar", tint = Color.White)
        }
    }
}
