package com.aotv.app.ui.lote24

import com.aotv.app.domain.model.MovieItem

/**
 * Lote 24 (v1.34.0), item 6 — Peak Cinema: modo "melhores cenas" por
 * gênero/humor.
 *
 * Sem backend novo: regras locais sobre os catálogos que já existem
 * (em alta + recomendados da Home). Cada preset é um gênero TMDB + humor +
 * nota mínima; a curadoria filtra por gênero e nota e ordena por nota
 * (desempate por número de votos). Os ids de gênero são os do TMDB
 * (28 ação, 35 comédia, 27 terror, 10749 romance, 878 ficção, 18 drama).
 */
data class PeakPreset(
    val id: String,
    val label: String,
    val genreIds: Set<Int>,
    val minRating: Double,
) {
    companion object {
        fun fromId(id: String): PeakPreset? = PEAK_CINEMA_PRESETS.firstOrNull { it.id == id }
    }
}

/** Presets de gênero + humor, na ordem da fileira. */
val PEAK_CINEMA_PRESETS: List<PeakPreset> = listOf(
    PeakPreset("acao_adrenalina", "Ação + adrenalina", setOf(28), 7.0),
    PeakPreset("comedia_leve", "Comédia + leve", setOf(35), 6.5),
    PeakPreset("terror_suspense", "Terror + suspense", setOf(27), 6.5),
    PeakPreset("romance_aconchego", "Romance + aconchego", setOf(10749), 6.5),
    PeakPreset("ficcao_mente", "Ficção + mente", setOf(878), 7.0),
    PeakPreset("drama_emocao", "Drama + emoção", setOf(18), 7.5),
)

/**
 * Curadoria local: títulos do preset (gênero + nota mínima), por nota
 * (desempate por votos), no máximo [max]. Sem título no corte, volta lista
 * vazia — a fileira some em vez de mostrar coisa fora do humor.
 */
fun pickPeakTitles(
    items: List<MovieItem>,
    preset: PeakPreset,
    max: Int = 10,
): List<MovieItem> {
    val seen = HashSet<Int>()
    return items
        .filter { item -> item.tmdbId !in seen && seen.add(item.tmdbId) }
        .filter { item -> item.genreIds.any { it in preset.genreIds } && item.rating >= preset.minRating }
        .sortedWith(compareByDescending<MovieItem> { it.rating }.thenByDescending { it.voteCount })
        .take(max)
}
