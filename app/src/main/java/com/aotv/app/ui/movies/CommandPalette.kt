package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Paleta de comandos estilo Ctrl-K (Lote 25, v1.35.0, item 2 — ideia do
 * MKCinema, reimplementada).
 *
 * Overlay (Dialog) por cima da tela atual — NÃO é rota nova. Lista ações
 * (ir p/ telas, alternar ajustes, abrir o Continuar, buscar) com filtro por
 * texto; navegável por toque e por D-pad (as fileiras são clicáveis, e
 * clicável é focável). Abre pelo botão na Início e pela busca. Sem atalho
 * de teclado Ctrl-K no celular (não há teclado físico); o botão é a entrada.
 */

// ── Pedido de abertura ──────────────────────────────────────────────────────

/** Abrir/fechar a paleta (o diálogo vive na navegação, por cima do NavHost). */
object CommandPaletteRequest {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    fun open() { _open.value = true }
    fun close() { _open.value = false }
}

// ── Ação ────────────────────────────────────────────────────────────────────

/**
 * Uma ação da paleta. `onRun` executa e o diálogo fecha (o host fecha ao
 * receber o clique — ver [CommandPaletteDialog]).
 */
data class PaletteAction(
    val id: String,
    val title: String,
    val sub: String,
    val keywords: String = "",
    /** Estado atual de um ajuste (ex.: "Ligado"), null p/ navegação. */
    val stateLabel: String? = null,
    val onRun: () -> Unit,
)

/** O filtro casa em título, subtítulo e palavras-chave (sem acento importa pouco: compara em minúsculas). */
private fun paletteMatches(action: PaletteAction, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isBlank()) return true
    return action.title.lowercase().contains(q)
        || action.sub.lowercase().contains(q)
        || action.keywords.lowercase().contains(q)
}

/**
 * Diálogo da paleta: campo de filtro + lista de ações filtradas.
 * Tocar (ou Enter no D-pad) executa a ação e fecha.
 */
@Composable
fun CommandPaletteDialog(
    actions: List<PaletteAction>,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(filter, actions) {
        actions.filter { action -> paletteMatches(action, filter) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundCard)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text("Digite um comando ou ação…", color = TextTertiary, fontSize = 15.sp, fontFamily = ManropeFontFamily) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    shown.firstOrNull()?.let { action -> onDismiss(); action.onRun() }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (shown.isEmpty()) {
                Text(
                    "Nada com esse filtro.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { action -> action.id }) { action ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = { onDismiss(); action.onRun() })
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    action.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = ManropeFontFamily,
                                )
                                Text(
                                    action.sub,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = ManropeFontFamily,
                                )
                            }
                            if (action.stateLabel != null) {
                                Text(
                                    action.stateLabel,
                                    color = Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ManropeFontFamily,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) }
            }
        }
    }
}
