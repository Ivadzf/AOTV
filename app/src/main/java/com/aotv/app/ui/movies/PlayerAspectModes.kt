package com.aotv.app.ui.movies

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.media3.ui.AspectRatioFrameLayout
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.TextOnLight
import com.aotv.app.ui.theme.TextTertiary
import com.aotv.app.ui.tv.TvScaled

// Modos de aspecto do player (Lote 24 item 7, v1.34.0).
//
// ── O QUE CADA MODO FAZ ───────────────────────────────────────────────────
// - ORIGINAL: o vídeo como veio, sem corte (RESIZE_MODE_FIT do Media3).
//   É o default e o modo da sessão quando o usuário nunca tocou no seletor.
// - ZOOM: preenche a tela cortando as bordas (RESIZE_MODE_ZOOM). Não é o
//   "Zoom" do modo de tela legado (índice 3, que soma ZOOM + escala 1,33x):
//   aqui é só o preenchimento do Media3, sem ampliação extra.
// - BANDS: mantém as faixas pretas do letterbox (FIT) E reserva uma faixa
//   inferior livre p/ a legenda não cobrir o vídeo. A reserva é um padding
//   inferior no PlayerView ([ASPECT_SUBTITLE_BAND_DP]): a área do vídeo
//   encolhe e a legenda externa (que já fica nos 32 dp inferiores da tela)
//   passa a cair dentro da faixa reservada, fora da imagem.
//
// ── PERSISTÊNCIA ──────────────────────────────────────────────────────────
// Por SESSÃO, não por perfil: o modo volta p/ Original a cada reprodução
// (decisão do brief). Por isso NÃO há chave de pref p/ o aspecto — nada
// aqui toca em PlatformPrefs.kt e o coordenador não precisa ligar nada.
//
// ── API DE PLUG (p/ o coordenador, MoviePlayerScreen) ─────────────────────
//   // Campo da sessão: var aspectMode by mutableStateOf(AspectMode.ORIGINAL)
//   // No update do AndroidView, no lugar do resizeMode direto:
//   //   it.resizeMode = resolveAspectResizeMode(aspectMode, effectiveResizeMode(it.width, it.height))
//   //   val bandPx = if (aspectMode == AspectMode.BANDS) aspectSubtitleBandPx(it.context) else 0
//   //   if (it.paddingBottom != bandPx) it.setPadding(0, 0, 0, bandPx)
//   // O Zoom legado (scale 1,33x) só vale com aspectMode == ORIGINAL, senão
//   // os dois zooms somariam. Painel: AspectModePanel(mode, onModeChange,
//   // onDismiss, television) via openDpadPanel("aspect"); chip "Aspecto"
//   // (id "aspect", fora do catálogo canônico, como "color").

/** Altura da faixa reservada p/ a legenda no modo Bands, em dp. */
const val ASPECT_SUBTITLE_BAND_DP = 96

/** Modo de aspecto do player (3 modos do seletor). */
enum class AspectMode(val id: String, val label: String, val sub: String) {
    ORIGINAL("original", "Original", "O vídeo como veio, sem corte"),
    ZOOM("zoom", "Zoom", "Preenche a tela cortando as bordas"),
    BANDS("bands", "Bands", "Faixas pretas + faixa livre p/ a legenda");

    companion object {
        fun fromId(id: String): AspectMode =
            entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

/**
 * ResizeMode efetivo p/ o PlayerView. O modo Original NÃO decide nada:
 * devolve o [legacyMode] (o do modo de tela legado — Automático/Ajustar/
 * Preencher/Zoom/Esticar), então quem nunca tocou no seletor continua
 * exatamente como antes. Zoom força ZOOM, Bands força FIT (o vídeo
 * encaixado + a faixa reservada vem do padding, ver [aspectSubtitleBandPx]).
 */
fun resolveAspectResizeMode(mode: AspectMode, legacyMode: Int): Int = when (mode) {
    AspectMode.ORIGINAL -> legacyMode
    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    AspectMode.BANDS -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

/** Faixa inferior reservada no modo Bands, em px, p/ a densidade do aparelho. */
fun aspectSubtitleBandPx(context: Context): Int =
    (ASPECT_SUBTITLE_BAND_DP * context.resources.displayMetrics.density).toInt()

/** Painel do seletor de aspecto (mesmo SidePanel dos outros painéis do player). */
@Composable
fun AspectModePanel(
    mode: AspectMode,
    onModeChange: (AspectMode) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled { AspectModePanelBody(mode, onModeChange, onDismiss, true) }
    else AspectModePanelBody(mode, onModeChange, onDismiss, false)
}

@Composable
private fun AspectModePanelBody(
    mode: AspectMode,
    onModeChange: (AspectMode) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    SidePanel(
        title = "Aspecto",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        sub = "Vale nesta reprodução — ao sair, volta p/ Original",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Modo de aspecto", Modifier.padding(top = 10.dp, start = 4.dp))
            AspectMode.entries.forEach { m ->
                AspectModeRow(
                    label = m.label,
                    sub = m.sub,
                    active = mode == m,
                    focused = false,
                    onClick = { onModeChange(m) },
                )
            }
            Text(
                "Original mostra o vídeo inteiro. Zoom preenche a tela e corta " +
                    "as bordas. Bands mantém as faixas pretas e deixa uma faixa " +
                    "livre embaixo p/ a legenda não cobrir a imagem — o modo " +
                    "pensado p/ quem lê legenda.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
            )
        }
    }
}

/** Linha do seletor (mesmo desenho de fundo/borda das linhas do painel de cor). */
@Composable
private fun AspectModeRow(
    label: String,
    sub: String,
    active: Boolean,
    focused: Boolean,
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
        if (active) {
            Icon(
                Icons.Default.Check,
                null,
                tint = if (focused) TextOnLight else Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
