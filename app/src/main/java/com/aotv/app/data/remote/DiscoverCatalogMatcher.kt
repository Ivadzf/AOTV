package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conversor addon→TMDB p/ o Descobrir, p/ os títulos do catálogo que não trazem id
 * `tmdb:` nem `tt...` (o [AddonMetaResolver] já resolve esses dois). Casa por
 * título+ano na busca do TMDB: o título normalizado tem que ser igual e, quando os dois
 * lados trazem ano, a diferença é de no máximo 1 (ano de festival vs. de estreia).
 *
 * Título sem par NÃO entra no resultado: o Descobrir mostra o aviso honesto
 * ("Os títulos desse catálogo não existem no TMDB, o que é comum em catálogo de anime")
 * em vez de exibir o título como se fosse do TMDB. Nunca se inventa dado.
 */
@Singleton
class DiscoverCatalogMatcher @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) {
    /**
     * Tenta casar UM título do catálogo com o TMDB. Null = sem par. Só olha a 1ª página
     * da busca; o TMDB ordena por relevância, então o par, quando existe, vem no topo.
     */
    suspend fun matchByTitle(meta: AddonMeta, series: Boolean): MovieItem? {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || meta.name.isBlank()) return null
        val want = normalizeTitle(meta.name)
        if (want.isBlank()) return null
        val metaYear = meta.year.take(4).toIntOrNull()
        return runCatching {
            tmdb.search(query = meta.name, apiKey = apiKey).results
                .filterNot { it.adult }
                .filter { dto ->
                    val title = dto.title ?: dto.name
                    val year = (dto.releaseDate ?: dto.firstAirDate).orEmpty().take(4).toIntOrNull()
                    !title.isNullOrBlank() && titlesMatch(want, normalizeTitle(title)) &&
                        yearMatches(metaYear, year)
                }
                .firstOrNull()
                ?.toMovieItem()
                ?.copy(type = if (series) MediaType.SERIES else MediaType.MOVIE)
                ?.takeIf { it.posterUrl.isNotBlank() && it.title.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        /**
         * Normaliza p/ comparar: minúsculas, sem acento, sem pontuação e sem o artigo
         * inicial ("the", "o", "a", "os", "as", "um", "uma") — "The Batman" casa com
         * "Batman (2022)". Ano entre parênteses no nome do catálogo sai também.
         */
        fun normalizeTitle(raw: String): String {
            val noYear = raw.replace(Regex("""\(\d{4}\)"""), " ")
            val folded = java.text.Normalizer.normalize(noYear, java.text.Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")
            val cleaned = folded.lowercase()
                .replace(Regex("""[^a-z0-9 ]"""), " ")
                .split(" ").filter { it.isNotBlank() }
            val withoutArticle = cleaned.dropWhile { it in ARTICLES }
            return withoutArticle.joinToString(" ")
        }

        private val ARTICLES = setOf("the", "o", "a", "os", "as", "um", "uma", "el", "la", "le", "der", "die", "das")

        /** Igualdade exata do título normalizado, ou um contém o outro com 3+ palavras. */
        fun titlesMatch(a: String, b: String): Boolean {
            if (a.isBlank() || b.isBlank()) return false
            if (a == b) return true
            val aw = a.split(" ")
            val bw = b.split(" ")
            val shorter = if (aw.size <= bw.size) aw else bw
            val longer = if (aw.size <= bw.size) bw else aw
            return shorter.size >= 3 && shorter.all { it in longer }
        }

        /** Ano casa quando falta um dos lados ou a diferença é de no máximo 1. */
        fun yearMatches(metaYear: Int?, tmdbYear: Int?): Boolean {
            if (metaYear == null || tmdbYear == null) return true
            return kotlin.math.abs(metaYear - tmdbYear) <= 1
        }
    }
}
