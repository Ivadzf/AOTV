package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Barra de sincronia do som (.pl-sync), fixa embaixo com o filme tocando. Segurar um botão
 * acelera. Em tela larga o valor fica no meio dos botões, em tela estreita fica acima deles.
 */
@Composable
internal fun SyncBar(
    delaySec: Double,
    onShift: (Double) -> Unit,
    onBack5: () -> Unit,
    onZero: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(12.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE60C0C10))
            .focusProperties { canFocus = false }
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val wide = maxWidth >= 600.dp
        val value = @Composable { m: Modifier ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(syncValue(delaySec), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter)
                Text(audioDelayLabel(delaySec), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Sincronia do som", Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF4EA1FF))
            if (!wide) value(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SyncKey("−10 s", -10.0, onShift, Modifier.weight(1f))
                SyncKey("−1 s", -1.0, onShift, Modifier.weight(1f))
                SyncKey("−0,05 s", -0.05, onShift, Modifier.weight(1f))
                if (wide) value(Modifier.weight(2f))
                SyncKey("+0,05 s", 0.05, onShift, Modifier.weight(1f))
                SyncKey("+1 s", 1.0, onShift, Modifier.weight(1f))
                SyncKey("+10 s", 10.0, onShift, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SyncAction("Voltar 5 s e conferir", Modifier.weight(1.4f), onBack5)
                SyncAction("Zerar", Modifier.weight(1f), onZero)
                SyncAction("Fechar", Modifier.weight(1f), onClose)
            }
            Text(
                "Voz antes da boca mexer, aumente (+). Voz depois, diminua (−). Segure para ir mais rápido.",
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Botão de passo da sincronia. Toque dá um passo, segurar repete e acelera. */
@Composable
private fun SyncKey(label: String, base: Double, onShift: (Double) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val shift by rememberUpdatedState(onShift)
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.14f))
            .pointerInput(base) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        shift(base)
                        delay(400)
                        var rep = 1
                        while (true) { shift(syncStep(base, rep++)); delay(90) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SyncAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp)).clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
