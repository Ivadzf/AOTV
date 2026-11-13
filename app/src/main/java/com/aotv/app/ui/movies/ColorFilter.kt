package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbMatrix
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.*
import com.aotv.app.ui.tv.TvScaled

// Correção de cor do vídeo (v1.9.0, Lote 1 item 1) — SÓ pro vídeo, nada na
// interface (decisão do Davi, 2026-11-13). Os 4 sliders + presets +
// correção p/ daltonismo viram efeitos do Media3 aplicados no ExoPlayer
// com setVideoEffects — ao vivo, sem reiniciar o vídeo. Não funciona com
// DRM (os streams do AOTV não são DRM — nada a fazer aqui).

/** Nº de linhas focáveis do painel (4 sliders + Redefinir + 4 presets + 4 modos). */
const val COLOR_FILTER_ROWS = 13

/** Nº de linhas que são sliders (as 4 primeiras). */
const val COLOR_FILTER_SLIDERS = 4

/** Preset guardado quando o usuário mexe num slider à mão. */
const val COLOR_PRESET_CUSTOM = "custom"

/** Modo de correção p/ daltonismo — os 3 modos do Android + desligado. */
enum class DaltonismMode(val id: String, val label: String, val sub: String) {
    OFF("off", "Desligado", "Sem correção de cor"),
    DEUTERANOMALY("deuteranomalia", "Deuteranomalia", "Verde-vermelho · o mais comum"),
    PROTANOMALY("protanomalia", "Protanomalia", "Vermelho-verde"),
    TRITANOMALY("tritanomalia", "Tritanomalia", "Azul-amarelo · o mais raro");

    companion object {
        fun fromId(id: String): DaltonismMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** Predefinição do filtro (valores dos 4 sliders). */
enum class ColorPreset(
    val id: String,
    val label: String,
    val sub: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val hueDegrees: Float,
) {
    PADRAO("padrao", "Padrão", "Sem ajuste", 0f, 1f, 1f, 0f),
    VIVO("vivo", "Vivo", "Mais cor e contraste", 0.05f, 1.15f, 1.4f, 0f),
    CINEMA("cinema", "Cinema", "Mais escuro e menos saturado", -0.05f, 1.1f, 0.85f, 0f),
    PB("pb", "P&B", "Sem cor (preto e branco)", 0f, 1f, 0f, 0f);

    companion object {
        fun byId(id: String): ColorPreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Estado do filtro. Faixas tipo VLC: brilho −1..1, contraste 0..2,
 * saturação 0..2, matiz −180..180°.
 */
data class ColorFilterState(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hueDegrees: Float = 0f,
    val preset: String = ColorPreset.PADRAO.id,
    val daltonism: DaltonismMode = DaltonismMode.OFF,
) {
    /** true = o vídeo está sem nenhum ajuste (o chip "Cor" fica apagado). */
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 1f && saturation == 1f &&
            hueDegrees == 0f && daltonism == DaltonismMode.OFF
}

// ── Daltonização (correção p/ daltonismo) ────────────────────────────────────
// CUIDADO CENTRAL — correção NÃO é simulação. As matrizes famosas de
// daltonismo (Machado et al.) SIMULAM como uma pessoa daltônica enxerga:
// aplicá-las pioraria a visão de quem tem daltonismo. O que entra aqui é
// DALTONIZAÇÃO: o algoritmo do daltonize.org simula a deficiência só para
// calcular o que se perde (erro = original − simulado) e DESLOCA esse erro
// para os canais que a pessoa ainda distingue — o resultado final é uma
// CORREÇÃO, não uma simulação.
//
// O algoritmo é todo linear (RGB→LMS, simulação em LMS, LMS→RGB, erro em
// RGB, deslocamento do erro), então a cadeia inteira colapsa numa única
// matriz 3x3 em RGB por tipo — calculada fora do app e conferida em Python
// (ver test_v190_color.py, que recalcula as 3 matrizes a partir do pipeline
// publicado e compara com estas constantes). Fonte: algoritmo LMS de
// daltonização do daltonize.org (http://www.daltonize.org/2010/05/lms-daltonization-algorithm.html),
// via o port p/ ReShade (crosire/reshade-shaders, Daltonize.fx):
//   RGB→LMS: L = 17.8824R + 43.5161G + 4.11935B; M = 3.45565R + 27.1554G + 3.86714B;
//            S = 0.0299566R + 0.184309G + 1.46709B
//   Simulação em LMS: protanopia L' = 2.02344M − 2.52581S; deuteranopia
//            M' = 0.494207L + 1.24827S; tritanopia S' = −0.395913L + 0.801109M
//   Erro em RGB: err = original − simulado; correção = original +
//            (0, 0.7·errR + errG, 0.7·errR + errB) — o erro do canal perdido
//            vai p/ os outros dois com peso 0.7.
// Os modos têm os nomes do Android (Deuteranomalia etc.), mas as matrizes
// são as da correção completa (dicromata), como no daltonize.org — sem
// escala de severidade parcial.
//
// Verificado em Python: branco e cinza passam intactos pelas 3 matrizes, e
// pares que se confundem (vermelho-verde) ficam MAIS distinguíveis para
// quem simula a deficiência depois da correção (protanopia 1.10→1.25,
// deuteranopia 0.59→1.02). Caveats conhecidos, p/ o teste no celular:
// a tritanopia tem coeficientes grandes (3.37) — o algoritmo empurra forte
// o canal azul — e a literatura registra que ela pode gerar novas
// confusões (IRJET v7 i5, 2020); se um modo parecer pior que desligado,
// o Davi desliga no painel. A daltonização troca um pouco de fidelidade
// nos outros pares (ex.: vermelho-azul cai levemente) para ganhar no eixo
// que se confunde — é o compromisso do próprio algoritmo.

/** Matrizes de daltonização 3x3 em RGB (linhas R, G, B), uma por modo. */
internal val DALTONIZATION_RGB: Map<DaltonismMode, Array<FloatArray>> = mapOf(
    // Protanomalia (matriz do algoritmo p/ protanopia).
    DaltonismMode.PROTANOMALY to arrayOf(
        floatArrayOf(1.000000f, 0.000000f, 0.000000f),
        floatArrayOf(0.508949f, 0.491054f, 0.000001f),
        floatArrayOf(0.617327f, -0.617323f, 1.000001f),
    ),
    // Deuteranomalia (matriz do algoritmo p/ deuteranopia).
    DaltonismMode.DEUTERANOMALY to arrayOf(
        floatArrayOf(1.000000f, 0.000000f, 0.000000f),
        floatArrayOf(0.202325f, 0.797674f, 0.000000f),
        floatArrayOf(0.517411f, -0.517413f, 1.000000f),
    ),
    // Tritanomalia (matriz do algoritmo p/ tritanopia — coeficientes
    // grandes no azul, ver o caveat no comentário acima).
    DaltonismMode.TRITANOMALY to arrayOf(
        floatArrayOf(1.000000f, 0.000000f, 0.000000f),
        floatArrayOf(-0.138536f, 1.138538f, 0.000000f),
        floatArrayOf(3.365585f, -3.365629f, 0.999995f),
    ),
)

/**
 * Converte uma 3x3 (linhas R/G/B, out = M·in) p/ a 4x4 do [RgbMatrix].
 * O Media3 sobe a matriz com glUniformMatrix4fv(transpose=false), ou
 * seja, column-major: o array é [coluna 0, coluna 1, coluna 2, translação]
 * — a 4ª coluna (índices 12..14) é a translação, como na matriz do
 * [Brightness] (brilho nos índices 12..14). Se o daltonismo parecer
 * transposto/trocado no teste do celular, é aqui que se inverte.
 */
internal fun rgb3x3ToMatrix4x4(m: Array<FloatArray>): FloatArray = floatArrayOf(
    m[0][0], m[1][0], m[2][0], 0f,
    m[0][1], m[1][1], m[2][1], 0f,
    m[0][2], m[1][2], m[2][2], 0f,
    0f, 0f, 0f, 1f,
)

/** [RgbMatrix] é interface (só [RgbMatrix.getMatrix] é abstrato): a matriz de daltonização entra por aqui. */
@OptIn(UnstableApi::class)
private class DaltonizationEffect(private val matrix4x4: FloatArray) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix4x4
}

/**
 * Constrói a lista de efeitos do Media3 p/ o estado. Mapeamentos
 * (a API do Media3 não usa as faixas do VLC):
 * - brilho −1..1 → [Brightness] direto;
 * - contraste 0..2 → [Contrast] −1..1 (0 = sem mudança, ou seja, slider − 1);
 * - saturação 0..2 → [HslAdjustment] −100..100 ((slider − 1) × 100);
 * - matiz −180..180° → [HslAdjustment.adjustHue] direto.
 * Efeitos identidade são pulados (menos trabalho p/ a GPU). A daltonização
 * vai por último na cadeia, como recomenda o ReShade (correção depois da
 * gradação). Lista vazia = vídeo sem ajuste (o setVideoEffects ainda é
 * chamado, 1x antes do prepare — ver MoviePlayerScreen).
 */
@OptIn(UnstableApi::class)
fun buildColorEffects(state: ColorFilterState): List<Effect> = buildList {
    if (state.brightness != 0f) add(Brightness(state.brightness.coerceIn(-1f, 1f)))
    if (state.contrast != 1f) add(Contrast((state.contrast - 1f).coerceIn(-1f, 1f)))
    if (state.hueDegrees != 0f || state.saturation != 1f) {
        add(
            HslAdjustment.Builder()
                .adjustHue(state.hueDegrees.coerceIn(-180f, 180f))
                .adjustSaturation(((state.saturation - 1f) * 100f).coerceIn(-100f, 100f))
                .build()
        )
    }
    if (state.daltonism != DaltonismMode.OFF) {
        val rgb = DALTONIZATION_RGB[state.daltonism] ?: return@buildList
        add(DaltonizationEffect(rgb3x3ToMatrix4x4(rgb)))
    }
}

/** Ajusta o slider da linha [rowIdx] (0..3) em [delta] passos (D-pad ←/→). */
fun adjustColorState(state: ColorFilterState, rowIdx: Int, delta: Int): ColorFilterState {
    val d = delta.toFloat()
    return when (rowIdx) {
        0 -> state.copy(brightness = (state.brightness + d * 0.05f).coerceIn(-1f, 1f), preset = COLOR_PRESET_CUSTOM)
        1 -> state.copy(contrast = (state.contrast + d * 0.05f).coerceIn(0f, 2f), preset = COLOR_PRESET_CUSTOM)
        2 -> state.copy(saturation = (state.saturation + d * 0.05f).coerceIn(0f, 2f), preset = COLOR_PRESET_CUSTOM)
        3 -> state.copy(hueDegrees = (state.hueDegrees + d * 5f).coerceIn(-180f, 180f), preset = COLOR_PRESET_CUSTOM)
        else -> state
    }
}

/** Aplica a linha [rowIdx] com OK (D-pad): Redefinir, preset ou modo de daltonismo. Sliders não mudam no OK. */
fun activateColorRow(state: ColorFilterState, rowIdx: Int): ColorFilterState = when (rowIdx) {
    COLOR_FILTER_SLIDERS -> ColorFilterState()
    in (COLOR_FILTER_SLIDERS + 1)..(COLOR_FILTER_SLIDERS + 4) -> {
        val preset = ColorPreset.entries[rowIdx - COLOR_FILTER_SLIDERS - 1]
        state.copy(
            brightness = preset.brightness, contrast = preset.contrast,
            saturation = preset.saturation, hueDegrees = preset.hueDegrees, preset = preset.id,
        )
    }
    in (COLOR_FILTER_SLIDERS + 5)..(COLOR_FILTER_SLIDERS + 8) -> {
        state.copy(daltonism = DaltonismMode.entries[rowIdx - COLOR_FILTER_SLIDERS - 5])
    }
    else -> state
}

/** Índice do item da LazyColumn p/ o foco [focusIdx] (os cabeçalhos não são focáveis). */
internal fun colorFocusItemIndex(focusIdx: Int): Int = when {
    focusIdx < COLOR_FILTER_SLIDERS -> focusIdx + 1
    focusIdx == COLOR_FILTER_SLIDERS -> 5
    focusIdx <= COLOR_FILTER_SLIDERS + 4 -> focusIdx + 2
    else -> focusIdx + 3
}

private fun fmt2(v: Float): String = "%.2f".format(java.util.Locale.ROOT, v).replace('.', ',')
private fun fmtHue(v: Float): String = "%.0f°".format(java.util.Locale.ROOT, v)

/** Painel lateral do player com os 4 sliders, presets e daltonismo (mesmo SidePanel dos outros painéis). */
@Composable
internal fun ColorFilterPanel(
    state: ColorFilterState,
    focusIdx: Int,
    onStateChange: (ColorFilterState) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled { ColorFilterPanelBody(state, focusIdx, onStateChange, onDismiss, true) }
    else ColorFilterPanelBody(state, focusIdx, onStateChange, onDismiss, false)
}

@Composable
private fun ColorFilterPanelBody(
    state: ColorFilterState,
    focusIdx: Int,
    onStateChange: (ColorFilterState) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    SidePanel(
        title = "Cor do vídeo",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        sub = "Vale só para o vídeo — a interface não muda",
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(focusIdx) {
            listState.animateScrollToItem(colorFocusItemIndex(focusIdx.coerceIn(0, COLOR_FILTER_ROWS - 1)))
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Eyebrow("Ajustes", Modifier.padding(top = 10.dp, start = 4.dp)) }
            item {
                ColorSliderRow("Brilho", fmt2(state.brightness), state.brightness, -1f..1f, focused = focusIdx == 0) {
                    onStateChange(state.copy(brightness = it, preset = COLOR_PRESET_CUSTOM))
                }
            }
            item {
                ColorSliderRow("Contraste", fmt2(state.contrast), state.contrast, 0f..2f, focused = focusIdx == 1) {
                    onStateChange(state.copy(contrast = it, preset = COLOR_PRESET_CUSTOM))
                }
            }
            item {
                ColorSliderRow("Saturação", fmt2(state.saturation), state.saturation, 0f..2f, focused = focusIdx == 2) {
                    onStateChange(state.copy(saturation = it, preset = COLOR_PRESET_CUSTOM))
                }
            }
            item {
                ColorSliderRow("Matiz", fmtHue(state.hueDegrees), state.hueDegrees, -180f..180f, focused = focusIdx == 3) {
                    onStateChange(state.copy(hueDegrees = it, preset = COLOR_PRESET_CUSTOM))
                }
            }
            item {
                ColorActionRow("Redefinir", "Volta brilho, contraste, saturação, matiz e daltonismo ao padrão",
                    active = false, focused = focusIdx == 4, icon = Icons.Default.Refresh) {
                    onStateChange(ColorFilterState())
                }
            }
            item { Eyebrow("Predefinições", Modifier.padding(top = 10.dp, start = 4.dp)) }
            ColorPreset.entries.forEachIndexed { i, preset ->
                item {
                    ColorActionRow(preset.label, preset.sub, active = state.preset == preset.id,
                        focused = focusIdx == COLOR_FILTER_SLIDERS + 1 + i) {
                        onStateChange(
                            state.copy(
                                brightness = preset.brightness, contrast = preset.contrast,
                                saturation = preset.saturation, hueDegrees = preset.hueDegrees, preset = preset.id,
                            )
                        )
                    }
                }
            }
            item { Eyebrow("Correção p/ daltonismo", Modifier.padding(top = 10.dp, start = 4.dp)) }
            DaltonismMode.entries.forEachIndexed { i, mode ->
                item {
                    ColorActionRow(mode.label, mode.sub, active = state.daltonism == mode,
                        focused = focusIdx == COLOR_FILTER_SLIDERS + 5 + i) {
                        onStateChange(state.copy(daltonism = mode))
                    }
                }
            }
            item {
                Text(
                    "A correção remapeia as cores que se confundem para tons distinguíveis (daltonização). " +
                        "Não é uma simulação de como uma pessoa daltônica enxerga.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** Linha com slider (mesmo desenho de fundo/foco das linhas do painel de fontes). */
@Composable
private fun ColorSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    focused: Boolean,
    onChange: (Float) -> Unit,
) {
    val bg = if (focused) Color.White else Color.White.copy(alpha = 0.05f)
    val fg = if (focused) TextOnLight else Color.White
    val sub = if (focused) Color(0xFF444444) else TextTertiary
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .focusProperties { canFocus = false }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(valueText, color = sub, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = if (focused) Color(0xFFBBBBBB) else Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
        )
    }
}

/** Linha de ação do painel (preset, modo de daltonismo, Redefinir). */
@Composable
private fun ColorActionRow(
    label: String,
    sub: String,
    active: Boolean,
    focused: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = when {
        focused -> Color.White
        active -> AccentSoft
        else -> Color.White.copy(alpha = 0.05f)
    }
    val fg = if (focused) TextOnLight else Color.White
    val subColor = if (focused) Color(0xFF444444) else TextTertiary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .then(if (active && !focused) Modifier.border(1.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)) else Modifier)
            .focusProperties { canFocus = false }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, color = subColor, fontSize = 13.sp, lineHeight = 18.sp)
        }
        val trailingIcon = icon ?: if (active) Icons.Default.Check else null
        if (trailingIcon != null) {
            Icon(
                trailingIcon,
                null,
                tint = if (focused) TextOnLight else if (active) Accent else TextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
