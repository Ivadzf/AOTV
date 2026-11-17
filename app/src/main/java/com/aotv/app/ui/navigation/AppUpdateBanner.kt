// ─── ui/navigation/AppUpdateBanner.kt (Lote 15 — Bloco 4, item 7, v1.25.0) ───
// Banner de atualização estilo Nuvio ("vX.Y.Z • NN MB — Atualização
// disponível", botão Atualizar + X p/ dispensar), no topo ao abrir o app.
// O tamanho só aparece quando a release informa (sizeBytes > 0) — nunca
// inventado. O botão dispara o fluxo existente de download/instalação
// (SettingsViewModel.downloadAndInstallUpdate); o X dispensa nesta sessão.
// Vidro igual ao da pílula (GlassBg + borda sutil, toggle `pref_panel_blur`).

package com.aotv.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BorderSubtle
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.GlassBg
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * Banner no topo: "Atualização disponível" + "vX.Y.Z • NN MB" (sem tamanho
 * quando a release não informa) + botão Atualizar + X. [downloading]
 * trava o botão em "Baixando…"; [downloadError] mostra a falha real.
 * Lote 18 (v1.28.0): [ready] = o APK já foi baixado e conferido em 2º
 * plano — o título vira "Atualização pronta para instalar" e o botão
 * "Instalar" (sem baixar de novo).
 */
@Composable
fun AppUpdateBanner(
    tag: String,
    sizeBytes: Long,
    downloading: Boolean,
    downloadError: String?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    /** Vidro fosco (Lote 4): toggle `pref_panel_blur`. */
    frosted: Boolean = false,
    /** APK já baixado em 2º plano, pronto p/ instalar. */
    ready: Boolean = false,
) {
    val sizeText = if (sizeBytes > 0) " • " + MobileDataUsage.formatBytes(sizeBytes) else ""
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(if (frosted) GlassBg.copy(alpha = 0.62f) else GlassBg)
            .border(1.dp, BorderSubtle, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Download, null, tint = Accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (ready) "Atualização pronta para instalar" else "Atualização disponível", color = TextPrimary,
                    fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$tag$sizeText", color = TextSecondary,
                    fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text(if (downloading) "Baixando…" else if (ready) "Instalar" else "Atualizar", fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Dispensar", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        if (downloadError != null) Text(
            "Falha no download: $downloadError", color = DangerText,
            fontSize = 12.sp, lineHeight = 16.sp,
        )
    }
}
