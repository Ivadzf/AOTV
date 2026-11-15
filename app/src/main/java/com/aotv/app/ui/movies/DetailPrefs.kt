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
    /** Título em idioma original na página de detalhes (Lote 3 item 6; padrão: desligado). */
    val originalTitle: StateFlow<Boolean> = prefs.detailOriginalTitleFlow
    /** Nota agregada (MDBList) na página de detalhes (Lote 8, v1.18.0; padrão: mostrar). */
    val showAggregateRating: StateFlow<Boolean> = prefs.detailShowAggregateRatingFlow
    /** Fonte da nota exibida de filmes e séries (Lote 12 — Frente 3, v1.22.0): "auto" | "mdblist" | "tmdb". Episódios seguem sempre TMDB. */
    val ratingsSource: StateFlow<String> = prefs.ratingsSourceFlow
    /** Avaliações do Trakt na página de detalhes (Lote 8, v1.18.0; padrão: mostrar). */
    val showTraktReviews: StateFlow<Boolean> = prefs.detailShowTraktReviewsFlow
    /** Frases e ficha (Wikiquote/Wikipédia) na página de detalhes (Lote 8, v1.18.0; padrão: mostrar). */
    val showQuotes: StateFlow<Boolean> = prefs.detailShowQuotesFlow
    /** Blur real atrás das folhas do detalhe (Lote 3 item 4; padrão: ligado). */
    val panelBlur: StateFlow<Boolean> = prefs.panelBlurFlow
    /** Guarda de duração (Lote 11, v1.21.0; padrão: ligada). */
    val durationGuard: StateFlow<Boolean> = prefs.durationGuardFlow
    /** Blur anti-spoiler nos episódios não assistidos (Lote 11, v1.21.0; padrão: desligado). */
    val spoilerBlur: StateFlow<Boolean> = prefs.detailSpoilerBlurFlow
    /** Esconder título/episódio até ir ao ar (Lote 8; também vale no detalhe, p/ episódios futuros). */
    val calendarHideSpoilers: StateFlow<Boolean> = prefs.calendarHideSpoilersFlow
}
