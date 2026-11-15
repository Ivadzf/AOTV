package com.aotv.app.ui.tv

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.aotv.app.domain.model.MovieItem

// ── Painéis de opções na TV (Lote 14, v1.24.0) ─────────────────────────────
// Segurar OK no cartão do Continuar (Início e Minha Lista) ou no episódio
// abre o mesmo painel do celular, no painel lateral da TV (TvSourcePanel:
// Dialog próprio, navegável por D-pad, Voltar fecha). O primeiro botão já
// nasce com o foco, p/ o controle não ficar sem nada selecionado.
// Textos iguais aos do celular (ver ui/movies/OptionsPanels.kt).

/** Primeiro botão do painel já focado ao abrir (D-pad). */
@Composable
private fun TvOptionsFirstFocus(content: @Composable (Modifier) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    content(Modifier.focusRequester(firstFocus))
}

/**
 * Painel do cartão do Continuar assistindo na TV (segurar OK).
 * "Remover" chama o [onRemove] do chamador — que abre a confirmação que já
 * existia (nunca remove direto). "Marcar como assistido" some p/ canal ao
 * vivo, igual ao celular.
 */
@Composable
fun TvTitleOptionsPanel(
    item: MovieItem,
    showMarkWatched: Boolean,
    onDismiss: () -> Unit,
    onGoToDetails: () -> Unit,
    onMarkWatched: () -> Unit,
    onRemove: () -> Unit,
) {
    TvSourcePanel(title = "Opções do título", sub = item.title, onDismiss = onDismiss) {
        TvOptionsFirstFocus { focusMod ->
            TvButton("Ir para detalhes", { onDismiss(); onGoToDetails() }, Modifier.fillMaxWidth().then(focusMod))
        }
        if (showMarkWatched) {
            TvButton("Marcar como assistido", { onDismiss(); onMarkWatched() }, Modifier.fillMaxWidth())
        }
        TvButton("Remover", { onDismiss(); onRemove() }, Modifier.fillMaxWidth(), primary = true)
    }
}

/**
 * Painel do episódio na TV (segurar OK no episódio).
 * "Ver fontes" abre o painel de fontes do episódio (o que segurar OK fazia
 * antes); o toggle é honesto: episódio já visto vira "Desmarcar".
 */
@Composable
fun TvEpisodeOptionsPanel(
    episodeLabel: String,
    watched: Boolean,
    hasPrevious: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onMarkPrevious: () -> Unit,
    onMarkSeason: () -> Unit,
    onViewSources: () -> Unit,
) {
    TvSourcePanel(title = "Opções do episódio", sub = episodeLabel, onDismiss = onDismiss) {
        TvOptionsFirstFocus { focusMod ->
            TvButton(
                if (watched) "Desmarcar como assistido" else "Marcar como assistido",
                { onDismiss(); onToggleWatched() }, Modifier.fillMaxWidth().then(focusMod),
            )
        }
        if (hasPrevious) {
            TvButton("Marcar anteriores como assistidos", { onDismiss(); onMarkPrevious() }, Modifier.fillMaxWidth())
        }
        TvButton("Marcar temporada como assistida", { onDismiss(); onMarkSeason() }, Modifier.fillMaxWidth())
        TvButton("Ver fontes", { onDismiss(); onViewSources() }, Modifier.fillMaxWidth())
    }
}
