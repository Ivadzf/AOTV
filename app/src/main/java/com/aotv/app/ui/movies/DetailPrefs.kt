package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Estilo do card de episódio (Fase B): horizontal com arte (o atual) ou empilhado com foco em texto. */
enum class EpisodeCardStyle {
    HORIZONTAL, STACKED;

    companion object {
        fun fromId(id: String): EpisodeCardStyle = if (id == "stacked") STACKED else HORIZONTAL
    }
}

/**
 * Preferências da página de detalhes (Fase B). Lê os fluxos do PlatformPrefs,
 * então mudar nas Configurações aplica na hora no detalhe, mesmo com ele na pilha.
 */
@HiltViewModel
class DetailPrefsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
) : ViewModel() {
    val cinematic: StateFlow<Boolean> = prefs.detailCinematicFlow
    val posterTransition: StateFlow<Boolean> = prefs.detailPosterTransitionFlow
    val iconActions: StateFlow<Boolean> = prefs.detailIconActionsFlow
    val tabs: StateFlow<Boolean> = prefs.detailTabsFlow
    val episodeStyle: StateFlow<String> = prefs.detailEpisodeStyleFlow
    /** Notas dos episódios visíveis nos cards (Lote 2 item 5; padrão: mostrar). */
    val episodeRatings: StateFlow<Boolean> = prefs.detailEpisodeRatingsFlow
}
