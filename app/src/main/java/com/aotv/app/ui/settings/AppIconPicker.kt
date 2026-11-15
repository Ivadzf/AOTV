package com.aotv.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.R
import com.aotv.app.data.local.AppIconVariants
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * Diálogo de escolha do ícone do app (Lote 11, v1.21.0).
 *
 * Mostra as 7 variantes com a prévia fiel (fundo colorido + o wordmark do
 * ícone atual). [onPick] devolve false quando a troca falhou (o diálogo
 * mostra o erro e não fecha). O Padrão é o ícone atual, sem alias.
 */
@Composable
fun AppIconPickerDialog(
    currentId: String,
    onPick: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ícone do app", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "O ícone muda na tela inicial em alguns segundos. Alguns launchers só atualizam depois de reabrir o app.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
                )
                AppIconVariants.VARIANTS.forEach { variant ->
                    val selected = variant.id == currentId
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (onPick(variant.id)) onDismiss() else error = true
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoxPreview(variant.backgroundColor)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            variant.label + if (variant.id == AppIconVariants.DEFAULT_ID) " (atual)" else "",
                            color = TextPrimary, fontSize = 16.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Icon(Icons.Default.CheckCircle, "Selecionado", tint = Accent)
                    }
                }
                if (error) Text(
                    "Não consegui trocar o ícone neste aparelho. Tente de novo.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Accent) } },
    )
}

/** Prévia do ícone: fundo da variante + o wordmark do ícone atual por cima. */
@Composable
private fun BoxPreview(backgroundColor: Long) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(backgroundColor)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}
