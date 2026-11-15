package com.aotv.app.ui.movies

import android.media.audiofx.Equalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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

// Equalizador do player (Lote 9 item 3, v1.19.0).
//
// `android.media.audiofx.Equalizer` na sessão de áudio do ExoPlayer (o
// mesmo gancho do Volume Boost: `onAudioSessionIdChanged`). Sem dependência
// nova: é API do framework. Só vale com o player aberto; ao fechar, o
// Equalizer é liberado junto com o resto da sessão (ver `releaseAll`).
//
// PRESETS: os do próprio aparelho (`getPresetName` / `numberOfPresets`) —
// NENHUM valor inventado. A pref `pref_player_eq_preset` guarda o NOME do
// preset; "flat" é o especial "tudo em 0 dB" (sem preset do aparelho).
// Se o nome guardado não existir neste aparelho, cai p/ flat.
//
// BANDAS: 5 personalizáveis, em dB inteiros, guardadas em
// `pref_player_eq_bands` como CSV ("0,3,-2,0,0"). O nº de bandas e a
// faixa de ganho vêm do aparelho (`numberOfBands`, `bandLevelRange` —
// em geral 5 bandas e ±15 dB); aparelho com menos bandas mostra só as
// que tem. Mover um slider sai do preset e volta p/ "flat" customizado.
//
// NÃO vale p/ Cast nem player externo: o efeito vive na sessão de áudio
// do ExoPlayer local. Aparelho sem suporte: o painel avisa e segue sem EQ.

/** Preset "tudo em 0 dB" (valor da pref `pref_player_eq_preset`). */
const val EQ_PRESET_FLAT = "flat"

/** Nº de bandas que o painel oferece (o aparelho pode ter menos — ver [PlayerEqualizer.bandCount]). */
const val EQ_BANDS = 5

/** "0,3,-2,0,0" → [0, 3, -2, 0, 0] em dB. Null quando não bate com [bandCount] ou tem valor inválido/fora de ±15 dB. */
internal fun parseEqBandsCsv(csv: String, bandCount: Int): List<Int>? {
    if (csv.isBlank() || bandCount <= 0) return null
    val parts = csv.split(",")
    if (parts.size != bandCount) return null
    val out = parts.map { it.trim().toIntOrNull() ?: return null }
    if (out.any { it < -15 || it > 15 }) return null
    return out
}

/** [0, 3, -2, 0, 0] → "0,3,-2,0,0" (o que vai p/ a pref). */
internal fun eqBandsCsv(levels: List<Int>): String = levels.joinToString(",")

/** "+3 dB", "0 dB", "−2 dB". */
internal fun eqBandLabel(db: Int): String = when {
    db > 0 -> "+$db dB"
    db < 0 -> "−${-db} dB"
    else -> "0 dB"
}

/** "60 Hz", "230 Hz", "1 kHz", "2,3 kHz" (frequência central da banda, do aparelho). */
internal fun eqBandFreqLabel(hz: Int): String =
    if (hz >= 1000) String.format(java.util.Locale("pt", "BR"), "%.1f kHz", hz / 1000.0)
        .replace(",0 kHz", " kHz")
    else "$hz Hz"

/** Equalizer do Android na sessão de áudio dada. [supported] false quando o aparelho não aceitou. */
class PlayerEqualizer(sessionId: Int) {
    private val eq: Equalizer? = runCatching { Equalizer(0, sessionId) }.getOrNull()

    /** true quando o efeito foi criado e está usável. */
    val supported: Boolean get() = eq != null

    /** Nº de bandas do aparelho (em geral 5). 0 sem suporte. */
    val bandCount: Int get() = runCatching { eq?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)

    /** Faixa de ganho em dB (em geral -15..15), do `bandLevelRange` do aparelho. */
    val levelRangeDb: IntRange get() {
        val r = runCatching { eq?.bandLevelRange }.getOrNull() ?: return -15..15
        return (r[0] / 100)..(r[1] / 100)
    }

    /** Frequência central de cada banda, em Hz (do aparelho). */
    val centerFreqHz: List<Int> get() =
        (0 until bandCount).map { runCatching { eq?.getCenterFreq(it.toShort()) ?: 0 }.getOrDefault(0) }

    /** Nomes dos presets do aparelho (ex.: Normal, Pop, Rock, Jazz, Clássico). */
    val presets: List<String> get() {
        val n = runCatching { eq?.numberOfPresets?.toInt() ?: 0 }.getOrDefault(0)
        return (0 until n).mapNotNull { runCatching { eq?.getPresetName(it.toShort()) }.getOrNull() }
            .filter { it.isNotBlank() }
    }

    /** Aplica o preset pelo NOME (do aparelho). "flat" = tudo em 0 dB. false quando o nome não existe aqui. */
    fun applyPreset(name: String): Boolean {
        val e = eq ?: return false
        if (name == EQ_PRESET_FLAT) {
            applyBands(List(bandCount) { 0 })
            return true
        }
        val idx = presets.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (idx < 0) return false
        return runCatching {
            e.usePreset(idx.toShort())
            e.enabled = true
            true
        }.getOrDefault(false)
    }

    /** Aplica os níveis por banda, em dB (limitados à faixa do aparelho). */
    fun applyBands(dbLevels: List<Int>) {
        val e = eq ?: return
        val range = levelRangeDb
        runCatching {
            dbLevels.forEachIndexed { i, db ->
                if (i < bandCount) e.setBandLevel(i.toShort(), (db.coerceIn(range) * 100).toShort())
            }
            e.enabled = true
        }
    }

    /** Libera o efeito (ao fechar o player). */
    fun release() {
        runCatching { eq?.release() }
    }
}

/** Painel do equalizador no player (mesmo SidePanel dos outros painéis). */
@Composable
internal fun EqualizerPanel(
    enabled: Boolean,
    preset: String,
    bands: List<Int>,
    equalizer: PlayerEqualizer?,
    onToggle: (Boolean) -> Unit,
    onPreset: (String) -> Unit,
    onBand: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled {
        EqualizerPanelBody(enabled, preset, bands, equalizer, onToggle, onPreset, onBand, onReset, onDismiss, true)
    } else EqualizerPanelBody(enabled, preset, bands, equalizer, onToggle, onPreset, onBand, onReset, onDismiss, false)
}

@Composable
private fun EqualizerPanelBody(
    enabled: Boolean,
    preset: String,
    bands: List<Int>,
    equalizer: PlayerEqualizer?,
    onToggle: (Boolean) -> Unit,
    onPreset: (String) -> Unit,
    onBand: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    SidePanel(
        title = "Equalizador",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        titleSize = 26.sp,
        dismissLabel = "Fechar",
        sub = "Só vale neste player — ao fechar, desliga junto",
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                EqToggleRow(enabled = enabled, onToggle = onToggle)
            }
            when {
                equalizer == null || !equalizer.supported -> {
                    item {
                        Text(
                            if (enabled) "Equalizador indisponível neste aparelho ou sem sessão de áudio ainda — tente quando o vídeo estiver tocando."
                            else "Ligue o equalizador para ajustar o som.",
                            color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                        )
                    }
                }
                !enabled -> {
                    item {
                        Text(
                            "Desligado — o som volta ao original. Os ajustes ficam salvos p/ a próxima vez.",
                            color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                        )
                    }
                }
                else -> {
                    item { Eyebrow("Predefinições do aparelho", Modifier.padding(top = 10.dp, start = 4.dp)) }
                    item {
                        EqActionRow("Plano", "Tudo em 0 dB", active = preset == EQ_PRESET_FLAT) { onPreset(EQ_PRESET_FLAT) }
                    }
                    equalizer.presets.forEach { name ->
                        item {
                            EqActionRow(name, "Preset do aparelho", active = preset.equals(name, ignoreCase = true)) { onPreset(name) }
                        }
                    }
                    item { Eyebrow("Bandas (dB)", Modifier.padding(top = 10.dp, start = 4.dp)) }
                    val freqs = equalizer.centerFreqHz
                    val range = equalizer.levelRangeDb
                    bands.forEachIndexed { i, db ->
                        item {
                            EqSliderRow(
                                label = eqBandFreqLabel(freqs.getOrNull(i) ?: 0),
                                valueText = eqBandLabel(db),
                                value = db.toFloat(),
                                range = range.first.toFloat()..range.last.toFloat(),
                            ) { onBand(i, it.toInt()) }
                        }
                    }
                    item {
                        EqActionRow("Redefinir", "Volta tudo em 0 dB", active = false, icon = Icons.Default.Refresh) { onReset() }
                    }
                    item {
                        Text(
                            "Os presets e as frequências das bandas são os do aparelho — mudam de um modelo p/ o outro. Não vale p/ Cast nem player externo.",
                            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Linha do liga/desliga do equalizador. */
@Composable
private fun EqToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (enabled) "Equalizador ligado" else "Equalizador desligado",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(
            checked = enabled, onCheckedChange = onToggle,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/** Linha de preset / Redefinir (mesmo desenho das linhas do painel de cor). */
@Composable
private fun EqActionRow(
    label: String,
    sub: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = TextTertiary, fontSize = 13.sp)
        }
        if (active) androidx.compose.material3.Icon(
            androidx.compose.material.icons.filled.Check, "Ativo", tint = Accent, modifier = Modifier.size(20.dp),
        )
    }
}

/** Linha com slider de uma banda (mesmo desenho do painel de cor). */
@Composable
private fun EqSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(valueText, color = TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = ((range.endInclusive - range.start).toInt() - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
