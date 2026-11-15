package com.aotv.app.ui.movies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Danger
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

// ── Painéis de opções (Lote 14, v1.24.0) ─────────────────────────────────────
// Long-press no cartão do Continuar assistindo e no episódio abrem um menu
// com as ações daquele item — o mesmo desenho do JOTV v1.0.20, reimplementado
// aqui (nenhum código dele): "Opções do título" e "Opções do episódio".
// O painel é um Dialog comum: toca fora ou volta e ele fecha; cada ação
// fecha o painel antes de rodar (a ação de remover abre a confirmação que
// já existia, não remove direto).

/** Uma linha do painel: ícone + rótulo, largura toda, toca e fecha. */
@Composable
private fun OptionsRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (destructive) Danger else TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (destructive) Danger else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ManropeFontFamily,
        )
    }
}

/** Moldura do painel: título + subtítulo + ações. */
@Composable
private fun OptionsPanel(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = BackgroundCard) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = ManropeFontFamily,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                content()
            }
        }
    }
}

/**
 * Painel do cartão do Continuar assistindo (segurar o card).
 * "Marcar como assistido" só aparece p/ filme/série — canal ao vivo não tem
 * assistido ([LibraryRepository.markContinueWatched] também recusa LIVE).
 * Um título no Continuar está em andamento por construção (o terminado já
 * saiu da fileira sozinho), então aqui a ação é sempre "Marcar", sem toggle.
 */
@Composable
fun TitleOptionsDialog(
    item: MovieItem,
    showMarkWatched: Boolean,
    onDismiss: () -> Unit,
    onGoToDetails: () -> Unit,
    onMarkWatched: () -> Unit,
    onRemove: () -> Unit,
) {
    OptionsPanel(title = "Opções do título", subtitle = item.title, onDismiss = onDismiss) {
        OptionsRow("Ir para detalhes", Icons.Default.Info) { onDismiss(); onGoToDetails() }
        if (showMarkWatched) {
            OptionsRow("Marcar como assistido", Icons.Default.Check) { onDismiss(); onMarkWatched() }
        }
        OptionsRow("Remover", Icons.Default.Delete, destructive = true) { onDismiss(); onRemove() }
    }
}

/**
 * Painel do episódio (segurar o card no detalhe da série).
 * O toggle é honesto: episódio já visto vira "Desmarcar como assistido".
 * "Marcar anteriores" só aparece quando há episódio anterior na temporada;
 * "Ver fontes" abre o painel de fontes do episódio (o mesmo que tocar o card).
 */
@Composable
fun EpisodeOptionsDialog(
    episodeLabel: String,
    watched: Boolean,
    hasPrevious: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onMarkPrevious: () -> Unit,
    onMarkSeason: () -> Unit,
    onViewSources: () -> Unit,
) {
    OptionsPanel(title = "Opções do episódio", subtitle = episodeLabel, onDismiss = onDismiss) {
        OptionsRow(
            if (watched) "Desmarcar como assistido" else "Marcar como assistido",
            if (watched) Icons.Default.Close else Icons.Default.Check,
        ) { onDismiss(); onToggleWatched() }
        if (hasPrevious) {
            OptionsRow("Marcar anteriores como assistidos", Icons.Default.SkipPrevious) { onDismiss(); onMarkPrevious() }
        }
        OptionsRow("Marcar temporada como assistida", Icons.Default.DoneAll) { onDismiss(); onMarkSeason() }
        OptionsRow("Ver fontes", Icons.Default.List) { onDismiss(); onViewSources() }
    }
}
