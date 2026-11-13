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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AotvPalette.all.forEach { palette ->
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
