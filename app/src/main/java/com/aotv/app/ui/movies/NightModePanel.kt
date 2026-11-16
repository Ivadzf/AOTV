package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.*
import com.aotv.app.ui.tv.TvScaled

// Painel do modo noturno + Clear Dialogue (Lote 16 item 1, v1.26.0).
//
// Painel puramente apresentacional: o estado vem do coordenador (ver o
// doc de NightModeAudio.kt) e cada mudança volta por callback — o painel
// não lê pref nem toca no player. Com passthrough ligado, os toggles
// ficam desligados e o painel explica o motivo (o som vai direto p/ o
// receiver em bitstream e não passa pelo processamento).

/** Painel do modo noturno no player (mesmo SidePanel dos outros painéis). */
@Composable
fun NightModePanel(
    state: NightModeState,
    drcEngine: NightDrcEngine,
    drcSupported: Boolean,
    clearSupported: Boolean,
    onNightToggle: (Boolean) -> Unit,
    onClearToggle: (Boolean) -> Unit,
    onIntensity: (Int) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled {
        NightModePanelBody(state, drcEngine, drcSupported, clearSupported, onNightToggle, onClearToggle, onIntensity, onDismiss, true)
    } else NightModePanelBody(state, drcEngine, drcSupported, clearSupported, onNightToggle, onClearToggle, onIntensity, onDismiss, false)
}

@Composable
private fun NightModePanelBody(
    state: NightModeState,
    drcEngine: NightDrcEngine,
    drcSupported: Boolean,
    clearSupported: Boolean,
    onNightToggle: (Boolean) -> Unit,
    onClearToggle: (Boolean) -> Unit,
    onIntensity: (Int) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    val blocked = state.passthrough
    val blockedText = "Passthrough ligado: o áudio vai direto p/ o receiver em " +
        "bitstream (Dolby/DTS) e não passa pelo processamento — modo " +
        "noturno e Clear Dialogue ficam desligados."
    SidePanel(
        title = "Modo noturno",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        titleSize = 26.sp,
        dismissLabel = "Fechar",
        sub = "Som mais baixo sem perder a fala — vale neste player",
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (blocked) {
                item {
                    Text(
                        blockedText,
                        color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }
            }
            item {
                NightToggleRow(
                    label = if (state.nightOn) "Modo noturno ligado" else "Modo noturno desligado",
                    sub = when {
                        blocked -> "Bloqueado pelo passthrough"
                        !drcSupported -> "Compressão indisponível neste aparelho"
                        drcEngine == NightDrcEngine.LOUDNESS_FALLBACK ->
                            "Ganho simples — sem compressão de verdade neste aparelho"
                        else -> "Comprime o som: sussurros sobem, explosões não estouram"
                    },
                    checked = state.nightOn,
                    enabled = !blocked && drcSupported,
                    onToggle = onNightToggle,
                )
            }
            item {
                NightToggleRow(
                    label = if (state.clearDialogueOn) "Clear Dialogue ligado" else "Clear Dialogue desligado",
                    sub = when {
                        blocked -> "Bloqueado pelo passthrough"
                        !clearSupported -> "Equalizador de voz indisponível neste aparelho"
                        else -> "Reforça as vozes (1–4 kHz) e corta o ronco grave"
                    },
                    checked = state.clearDialogueOn,
                    enabled = !blocked && clearSupported,
                    onToggle = onClearToggle,
                )
            }
            item { Eyebrow("Intensidade", Modifier.padding(top = 10.dp, start = 4.dp)) }
            item {
                NightIntensityRow(
                    value = state.intensity,
                    enabled = !blocked && (state.nightOn || state.clearDialogueOn),
                    onChange = onIntensity,
                )
            }
            item {
                Text(
                    "O modo noturno comprime a faixa dinâmica (API 28+: DynamicsProcessing; " +
                        "abaixo disso, só um ganho simples). O Clear Dialogue é equalização " +
                        "na sessão de áudio do player — não vale p/ Cast nem player externo. " +
                        "A escolha fica salva por filme/episódio ou por canal.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** Linha de liga/desliga (mesmo desenho da do equalizador). */
@Composable
private fun NightToggleRow(
    label: String,
    sub: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle, enabled = enabled,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/** Slider de intensidade 0..100 (só vale com algo ligado). */
@Composable
private fun NightIntensityRow(
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Intensidade", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$value%", color = TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
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
