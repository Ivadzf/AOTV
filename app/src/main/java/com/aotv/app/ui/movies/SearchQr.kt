package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aotv.app.ui.settings.QrCode
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * QR da busca (Lote 25, v1.35.0, item 3 — ideia do Sombra, reimplementada).
 *
 * O QR carrega a query atual como TEXTO puro (não é link): outro aparelho
 * ou a TV lê o texto e busca o mesmo termo. Reusa o [QrCode] que já existe
 * (ZXing, sem suavizar) — sem dependência nova.
 *
 * Sem query, o diálogo nem abre (o botão só aparece com texto no campo).
 */
@Composable
fun SearchQrDialog(
    query: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Buscar em outro aparelho",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ManropeFontFamily,
            )
            Text(
                "Aponte a câmera p/ ler a busca:",
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.padding(top = 6.dp),
            )
            QrCode(
                query,
                Modifier.size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp),
                "Código QR com a busca \"$query\"",
            )
            Text(
                query,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ManropeFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
                Text("Fechar", color = TextSecondary)
            }
        }
    }
}
