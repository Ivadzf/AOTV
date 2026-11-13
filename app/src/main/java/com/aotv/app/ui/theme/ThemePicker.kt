package com.aotv.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** ViewModel do seletor de tema: lê a paleta salva e grava a escolha. */
@HiltViewModel
class ThemePickerViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
) : ViewModel() {
    /** Id da paleta ativa. */
    val paletteId = prefs.themePalette

    /** Grava a paleta escolhida (id desconhecido vira Padrão na leitura). */
    fun selectPalette(id: String) {
        prefs.themePaletteId = AotvPalette.fromId(id).id
    }

    /** As 6 cores editáveis do tema personalizado, que avisam quem observa. */
    val customTheme = prefs.customThemeFlow

    /** Troca uma das 6 cores editáveis (só aplica com o Personalizado selecionado, mas grava sempre). */
    fun setCustomColor(key: String, argb: Int) {
        prefs.updateCustomThemeColor(key, argb)
    }

    /** Volta as 6 cores editáveis p/ as do tema Padrão. */
    fun resetCustomTheme() {
        prefs.resetCustomTheme()
    }
}

/**
 * Seletor de tema (Fase A, Frente 3). Lista as paletas do [AotvPalette.all];
 * tocar numa grava a escolha no [PlatformPrefs] e o app inteiro recompõe com
 * a paleta nova na hora. Chamado pela página Aparência das Configurações.
 */
@Composable
fun ThemeSelector(
    viewModel: ThemePickerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val selectedId by viewModel.paletteId.collectAsStateWithLifecycle()
    // A fileira "Personalizado" mostra as cores EDITADAS (não as do padrão):
    // a entrada "custom" de AotvPalette.all nasce dos padrões, então aqui ela
    // é trocada pela paleta derivada das cores salvas.
    val customColors by viewModel.customTheme.collectAsStateWithLifecycle()
    val palettes = AotvPalette.all.map { if (it.id == "custom") AotvPalette.fromId("custom", customColors) else it }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        palettes.forEach { palette ->
            ThemePaletteRow(
                palette = palette,
                selected = palette.id == selectedId,
                onClick = { viewModel.selectPalette(palette.id) },
            )
        }
    }
}

@Composable
private fun ThemePaletteRow(
    palette: AotvPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Border, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Amostra: destaque, secundário e fundo da paleta.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(palette.accent))
            Box(Modifier.size(22.dp).clip(CircleShape).background(palette.accentSecondary))
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(palette.background)
                    .border(1.dp, Border, CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(palette.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (palette.isLight) "Tema claro" else "Tema escuro",
                color = TextSecondary, fontSize = 12.sp,
            )
        }
        if (selected) Icon(Icons.Default.Check, "Tema atual", tint = Accent, modifier = Modifier.size(22.dp))
    }
}

// ── Tema personalizado (Lote 3, v1.11.0) ─────────────────────────────────────
// Presets do editor: as cores de destaque dos 8 temas + branco e dois fundos
// escuros. Não há seletor de cor (color picker) no projeto e nenhuma
// dependência nova foi trazida: preset + campo hexadecimal cobre a edição.
private val CUSTOM_COLOR_PRESETS = listOf(
    0xFF00C2B8.toInt(), 0xFF9E1B1B.toInt(), 0xFF1E88E5.toInt(), 0xFF7E57C2.toInt(),
    0xFF2E9E6B.toInt(), 0xFFF59E0B.toInt(), 0xFFEC407A.toInt(), 0xFFF5F7F8.toInt(),
    0xFF191919.toInt(), 0xFF0D0D0D.toInt(),
)

/**
 * Editor do tema personalizado (Configurações → Aparência, junto ao seletor).
 * Uma linha por cor editável (presets + campo hexadecimal); gravar aqui já
 * atualiza o app na hora quando o Personalizado está selecionado, porque a
 * MainActivity observa as cores salvas. "Restaurar" volta às cores do Padrão.
 */
@Composable
fun CustomThemeEditor(
    viewModel: ThemePickerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val custom by viewModel.customTheme.collectAsStateWithLifecycle()
    val selectedId by viewModel.paletteId.collectAsStateWithLifecycle()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (selectedId == "custom") "Editando o tema Personalizado — a mudança aparece na hora."
            else "Estas cores só valem quando o tema Personalizado está selecionado.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        CustomThemeColors.CUSTOM_THEME_KEYS.forEach { key ->
            CustomColorRow(
                label = CustomThemeColors.label(key),
                argb = custom.color(key),
                onColorChange = { viewModel.setCustomColor(key, it) },
            )
        }
        TextButton(onClick = { viewModel.resetCustomTheme() }) {
            Text("Restaurar cores do Padrão", color = Accent, fontSize = 14.sp)
        }
    }
}

/** Uma cor editável: amostra, nome, campo hexadecimal e presets. */
@Composable
private fun CustomColorRow(
    label: String,
    argb: Int,
    onColorChange: (Int) -> Unit,
) {
    val savedHex = formatHexColor(argb)
    // O rascunho volta ao valor salvo quando ele muda por fora (preset ou Restaurar).
    var draft by remember(savedHex) { mutableStateOf(savedHex) }
    val parsed = parseHexColor(draft)
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color(argb))
                    .border(1.dp, Border, CircleShape),
            )
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = draft,
                onValueChange = { raw ->
                    // Só entra dígito hexadecimal (no máximo 6); com 6 dígitos, aplica na hora.
                    val clean = raw.trim().removePrefix("#").take(6)
                        .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                    draft = if (clean.length == 6) "#${clean.uppercase()}" else clean
                    parseHexColor(draft)?.let { onColorChange(it) }
                },
                singleLine = true,
                isError = draft.length >= 6 && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.width(110.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CUSTOM_COLOR_PRESETS.forEach { preset ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(preset))
                        .border(if (preset == argb) 2.dp else 1.dp, if (preset == argb) Accent else Border, CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { onColorChange(preset) }),
                )
            }
        }
    }
}
