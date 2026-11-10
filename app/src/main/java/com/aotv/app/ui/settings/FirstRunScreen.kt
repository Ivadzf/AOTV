package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.ui.common.LocalIsTv
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.SettingsRow
import com.aotv.app.ui.common.SettingsSection
import com.aotv.app.ui.common.currentDeviceLayout
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FirstRunViewModel @Inject constructor(private val prefs: PlatformPrefs) : ViewModel() {
    val initialName: String get() = prefs.profiles.value.first().name
    val initialMode: PlatformMode get() = prefs.platformMode

    /** Grava nome e plataforma escolhidos e marca a primeira abertura como feita. */
    fun confirm(name: String, mode: PlatformMode) {
        val p = prefs.profiles.value.first()
        val clean = name.trim().take(20)
        if (clean.isNotBlank() && clean != p.name) prefs.updateProfile(p.copy(name = clean))
        prefs.platformMode = mode
        prefs.onboarded = true
    }
}

/**
 * Primeira abertura do app: nome do perfil + confirmar a plataforma detectada. Só aparece uma
 * vez (onboarded=false). O Automático já vem selecionado, então na prática é só confirmar.
 */
@Composable
fun FirstRunScreen(onDone: () -> Unit, vm: FirstRunViewModel = hiltViewModel()) {
    val tv = LocalIsTv.current
    var name by remember { mutableStateOf(vm.initialName) }
    var mode by remember { mutableStateOf(vm.initialMode) }
    var naming by remember { mutableStateOf(false) }
    val autoLabel = currentDeviceLayout(PlatformMode.AUTO).label
    val pad = pagePadding()

    if (naming) {
        com.aotv.app.ui.common.TvTextPrompt(
            title = "Como podemos te chamar?",
            initial = name,
            okLabel = "Continuar",
            onDone = { name = it.take(20); naming = false },
            onDismiss = { naming = false },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding().verticalScroll(rememberScrollState())) {
        PageHeader("Bem-vindo ao AOTV", Modifier.padding(horizontal = pad, vertical = 10.dp), sub = "Só dessa vez: seu nome e o aparelho")
        Column(
            Modifier.fillMaxSize().padding(horizontal = pad).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSection("Seu nome")
            if (tv) SettingsRow(
                icon = Icons.Default.Edit, title = name.ifBlank { "Sem nome" }, sub = "Toque para editar",
                onClick = { naming = true },
            ) else OutlinedTextField(
                value = name, onValueChange = { name = it.take(20) },
                label = { Text("Nome do perfil") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                ),
            )

            SettingsSection("Aparelho")
            Text(
                "O automático reconhece TV, tablet e celular sozinho. Só muda se detectar errado.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
            FirstRunPlatformOption(
                selected = mode == PlatformMode.AUTO, title = "Automático", subtitle = "Detectamos: $autoLabel",
                icon = Icons.Default.Devices, onClick = { mode = PlatformMode.AUTO },
            )
            FirstRunPlatformOption(
                selected = mode == PlatformMode.TV, title = "Forçar TV", subtitle = "Layout de controle remoto com menu lateral",
                icon = Icons.Default.Tv, onClick = { mode = PlatformMode.TV },
            )
            FirstRunPlatformOption(
                selected = mode == PlatformMode.PHONE, title = "Celular ou tablet", subtitle = "Layout de toque adaptado ao espaço disponível",
                icon = Icons.Default.PhoneAndroid, onClick = { mode = PlatformMode.PHONE },
            )

            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            com.aotv.app.ui.common.AotvButton(
                "Continuar", { vm.confirm(name, mode); onDone() },
                tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FirstRunPlatformOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = title,
        sub = subtitle,
        iconTint = if (selected) Accent else TextSecondary,
        modifier = if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier,
        trailing = if (selected) ({ Icon(Icons.Default.CheckCircle, "Selecionado", tint = Accent) }) else null,
        onClick = onClick,
    )
}
