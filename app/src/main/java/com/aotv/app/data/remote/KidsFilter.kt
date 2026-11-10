package com.aotv.app.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

// Perfil infantil. Mesma regra do kidsOk do webOS. Passa o que é animação, família ou infantil
// e não é terror, suspense, crime ou guerra.
private val KIDS_IN = setOf(16, 10751, 10762)
private val KIDS_OUT = setOf(27, 53, 80, 10752)

fun kidsOk(genreIds: Collection<Int>): Boolean =
    genreIds.any { it in KIDS_IN } && genreIds.none { it in KIDS_OUT }

/**
 * Idade da classificação. BR ("L", "10", "12"...) ou a dos EUA, que o certBR usa quando não há a
 * brasileira (filme G 0, PG 10, PG-13 13, R 17, NC-17 18; série TV-Y e TV-G 0, TV-Y7 7, TV-PG 10,
 * TV-14 14, TV-MA 18). null quando não dá pra saber.
 */
fun certAge(cert: String): Int? {
    val c = cert.trim().uppercase()
    c.toIntOrNull()?.let { return it }
    return when (c) {
        "L", "AL", "G", "TV-Y", "TV-G" -> 0
        "TV-Y7" -> 7
        "PG", "TV-PG" -> 10
        "PG-13" -> 13
        "TV-14" -> 14
        "R" -> 17
        "NC-17", "TV-MA" -> 18
        else -> null
    }
}

/**
 * Título fora do perfil infantil. Falha fechado. Bloqueia adulto, gênero que não passa no
 * [kidsOk] e classificação acima de 12. Sem classificação nenhuma só passa animação infantil ou
 * de família (16 junto com 10762 ou 10751).
 */
fun kidsBlocked(genreIds: Collection<Int>, cert: String, adult: Boolean = false): Boolean {
    if (adult || !kidsOk(genreIds)) return true
    val age = certAge(cert) ?: return !(16 in genreIds && (10762 in genreIds || 10751 in genreIds))
    return age > 12
}

// Listas do TMDB que levam título. "cast" e "crew" com gênero são a filmografia de uma pessoa
// (o elenco do filme não tem gênero e passa intacto, igual aos vídeos dentro de "results").
private val LIST_KEYS = setOf("results", "cast", "crew", "parts", "movie_results", "tv_results")

/**
 * Tira das listas do TMDB o que não é para criança. Vale para qualquer tela (Início, busca,
 * Descobrir, filmografia, relacionados, coleção, modo TV) porque age na resposta. Item sem
 * gênero só sai quando é pessoa ou título, nunca vídeo ou elenco.
 */
fun filterKidsJson(body: String): String {
    val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return body
    walk(root)
    return root.toString()
}

private fun walk(e: JsonElement) {
    when {
        e.isJsonObject -> e.asJsonObject.entrySet().forEach { (k, v) ->
            if (k in LIST_KEYS && v.isJsonArray) filterArray(v.asJsonArray)
            walk(v)
        }
        e.isJsonArray -> e.asJsonArray.forEach { walk(it) }
    }
}

private fun filterArray(arr: JsonArray) {
    val it = arr.iterator()
    while (it.hasNext()) {
        val o = it.next().takeIf { it.isJsonObject }?.asJsonObject ?: continue
        if (!isTitleOrPerson(o)) continue
        val adult = o.get("adult")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        if (adult || !kidsOk(genresOf(o))) it.remove()
    }
}

private fun isTitleOrPerson(o: JsonObject) =
    o.has("genre_ids") || o.get("media_type")?.takeIf { it.isJsonPrimitive }?.asString == "person"

private fun genresOf(o: JsonObject): List<Int> =
    o.getAsJsonArray("genre_ids")?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty()

/** Aplica o [filterKidsJson] nas respostas do TMDB enquanto o perfil ativo for infantil. */
class KidsFilterInterceptor(private val isKids: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val resp = chain.proceed(chain.request())
        if (!isKids() || !resp.isSuccessful) return resp
        val body = resp.body ?: return resp
        val type = body.contentType()
        val text = body.string()
        return resp.newBuilder().body(filterKidsJson(text).toResponseBody(type)).build()
    }
}
