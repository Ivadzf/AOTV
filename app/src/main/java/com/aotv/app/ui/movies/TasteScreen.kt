// ─── ui/movies/TasteScreen.kt (Lote 21, v1.31.0) ────────────────────────────
// "O que molda suas recomendações": a explicação do motor de recomendações
// (regras + sementes + sinais de gosto com remoção). Tudo local, sem IA.

package com.aotv.app.ui.movies

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.SoraFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Diálogo "O que molda suas recomendações": regras do motor, títulos-semente
 * e os sinais de gosto do perfil (👍/👎/⭐) com remoção. Aberto da grade
 * "Ver tudo" dos recomendados.
 */
@Composable
fun TasteDialog(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "O que molda suas recomendações",
                fontFamily = SoraFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp, color = TextPrimary,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Como as recomendações são montadas — tudo no aparelho, sem conta e sem IA.",
                    fontFamily = ManropeFontFamily, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(12.dp))
                TASTE_RULES.forEach { rule ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("•  ", color = Accent, fontFamily = ManropeFontFamily, fontSize = 13.sp)
                        Text(rule, color = TextPrimary, fontFamily = ManropeFontFamily, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                if (state.tasteSeedTitles.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sementes desta lista",
                        color = Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        fontFamily = ManropeFontFamily, letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.tasteSeedTitles.joinToString(" · "),
                        color = TextSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
                TasteGroup(
                    title = "👍 Gostei (${state.tasteLikesCount})",
                    entries = state.tasteEntries.filter { it.isLike },
                    emptyText = "Nenhum título com 👍 ainda — marque no detalhe do título.",
                    onRemove = { entry -> tasteIdOf(entry.key)?.let { viewModel.toggleTasteLike(it, tasteTypeOf(entry.key)) } },
                )
                TasteGroup(
                    title = "👎 Não gostei (${state.tasteDislikesCount})",
                    entries = state.tasteEntries.filter { it.isDislike },
                    emptyText = "Nenhum título com 👎 — eles somem das recomendações.",
                    onRemove = { entry -> tasteIdOf(entry.key)?.let { viewModel.toggleTasteDislike(it, tasteTypeOf(entry.key)) } },
                )
                TasteGroup(
                    title = "⭐ Favoritos do gosto (${state.tasteFavoritesCount})",
                    entries = state.tasteEntries.filter { it.isFavorite },
                    emptyText = "Nenhum favorito ainda — marque ⭐ no detalhe do título.",
                    onRemove = { entry -> tasteIdOf(entry.key)?.let { viewModel.toggleTasteFavorite(it, tasteTypeOf(entry.key)) } },
                )
                if (state.tasteFavoritesCount in 1 until TASTE_MIN_FAVORITES) {
                    Text(
                        "Faltam ${TASTE_MIN_FAVORITES - state.tasteFavoritesCount} favorito(s) p/ os favoritos virarem sementes das recomendações.",
                        color = TextTertiary, fontFamily = ManropeFontFamily, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendi", color = Accent, fontFamily = ManropeFontFamily) }
        },
    )
}

@Composable
private fun TasteGroup(
    title: String,
    entries: List<TasteEntry>,
    emptyText: String,
    onRemove: (TasteEntry) -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily,
    )
    Spacer(Modifier.height(4.dp))
    if (entries.isEmpty()) {
        Text(emptyText, color = TextTertiary, fontFamily = ManropeFontFamily, fontSize = 13.sp, lineHeight = 19.sp)
    } else {
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.title, color = TextSecondary, fontFamily = ManropeFontFamily,
                    fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(entry) }) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Close, contentDescription = "Remover ${entry.title}",
                        tint = TextTertiary,
                    )
                }
            }
        }
    }
}
