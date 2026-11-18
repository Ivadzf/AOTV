// ─── ui/movies/Taste.kt (Lote 21, v1.31.0) ───────────────────────────────────
// "Picked for you" explicável: 👍/👎 por título + favoritos do gosto
// ("tastePick") + a tela "O que molda suas recomendações".
//
// Tudo no aparelho, sem IA e sem rede: as recomendações nascem do
// histórico (os títulos assistidos viram sementes p/ o TMDB) e dos sinais
// do usuário — 👍 e favoritos viram sementes extras, 👎 some da lista.
// Nada aqui inventa nota nem título: sem semente, sem recomendação.

package com.aotv.app.ui.movies

/** Uma chave de título no CSV de gosto: "tmdbId:TIPO" (ex.: "123:MOVIE"). */
fun tasteKey(tmdbId: Int, type: String): String = "$tmdbId:${type.uppercase()}"

/** Lê um CSV "tmdbId:TIPO,tmdbId:TIPO" → conjunto de chaves (ignora lixo). */
fun parseTasteCsv(csv: String): Set<String> =
    csv.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val parts = entry.split(":")
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val type = parts.getOrNull(1)?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "$id:$type"
        }.toSet()

/** Escreve um conjunto de chaves → CSV "tmdbId:TIPO,..." (ordem estável). */
fun tasteCsvOf(keys: Set<String>): String = keys.sorted().joinToString(",")

/** Soma o tmdbId de uma chave "tmdbId:TIPO" (null sem id válido). */
fun tasteIdOf(key: String): Int? = key.substringBefore(":").toIntOrNull()

/** Tipo ("MOVIE"/"SERIES") de uma chave "tmdbId:TIPO" ("" sem tipo). */
fun tasteTypeOf(key: String): String = key.substringAfter(":", "")

/**
 * Regras do motor de recomendações, em PT-BR, p/ a tela "O que molda suas
 * recomendações" — o usuário vê exatamente o que o app usa, sem caixa-preta.
 */
val TASTE_RULES: List<String> = listOf(
    "As recomendações nascem do que você assistiu: os títulos recentes do histórico viram sementes p/ o TMDB.",
    "Um título recomendado por várias sementes sobe na lista — gostar de dois filmes que apontam p/ o mesmo título é sinal forte.",
    "👍 e favoritos (3 ou mais) viram sementes também — o que você marcou como favorito pesa nas próximas recomendações.",
    "👎 tira o título das recomendações — ele não volta a aparecer na fileira.",
    "O que você já viu (ou está assistindo) nunca é recomendado de novo.",
    "Gênero pesa mais que diretor, diretor mais que elenco — e nada sai do aparelho: sem conta, sem nuvem, sem IA.",
)

/** Quantos favoritos faltam p/ o tastePick valer (mínimo 3). */
const val TASTE_MIN_FAVORITES = 3

/** Um sinal de gosto com título resolvido, p/ a tela de explicação. */
data class TasteEntry(val key: String, val title: String, val kind: String) {
    /** "like" | "dislike" | "favorite". */
    val isLike: Boolean get() = kind == "like"
    val isDislike: Boolean get() = kind == "dislike"
    val isFavorite: Boolean get() = kind == "favorite"
}
