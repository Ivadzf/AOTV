package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Segmentos puláveis (resumo/abertura/encerramento/pós-créditos) — v1.8.1,
 * portado da JOTV 1.0.17 (SkipSegmentsService.kt, commit d4a275eb).
 *
 * Busca em 3 fontes em paralelo e mescla (primeiro não-nulo por tipo):
 * IntroDB e SkipDB (sem chave) + Anime-Skip (GraphQL, só séries, SÓ quando
 * o usuário digitou um X-Client-ID nas Configurações — "" = desligado).
 * NUNCA embutir client ID no código: o ID de demonstração do JOTV não foi copiado.
 * Segmentos < 3s são descartados; cache em memória por imdb+temporada+episódio.
 * Falha de qualquer serviço retorna vazio e nunca quebra o player.
 *
 * v1.13.0 (refinamento, JOTV 1.0.19 §3.4): o merge passa a respeitar a duração
 * do arquivo (proxy da versão do arquivo — cortes diferentes têm durações
 * diferentes; segmento que começa depois do fim do arquivo é de outro corte e
 * sai; fim além da duração é aparado) e dá prioridade à cena pós-créditos
 * quando os intervalos se sobrepõem (o encerramento é aparado até o início
 * dela, para nunca pular por cima de uma cena ainda não vista). Resumo,
 * abertura e encerramento mantêm o comportamento da v1.8.1. Título, episódio
 * e duração já chegavam aqui (título e episódio alimentam o Anime-Skip e a
 * chave do cache; duração ia só na consulta ao SkipDB) — nenhum outro sinal
 * de "versão do arquivo" chega ao serviço: o rótulo/addon da fonte não é
 * passado ao get(), então a duração é o único discriminador de versão.
 */
data class SkipSegment(val start: Long, val end: Long)

data class SkipSegments(
    val recap: SkipSegment? = null,
    val intro: SkipSegment? = null,
    val outro: SkipSegment? = null,
    val postCredits: SkipSegment? = null,
)

@Singleton
class SkipSegmentsService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, SkipSegments>()

    private fun seg(a: Double, b: Double): SkipSegment? {
        if (!a.isFinite() || !b.isFinite() || b - a < 3000) return null
        return SkipSegment(maxOf(0L, a.toLong()), b.toLong())
    }

    private fun ms(o: JSONObject?): SkipSegment? {
        if (o == null) return null
        if (o.has("start_ms") && o.has("end_ms")) {
            return seg(o.optDouble("start_ms"), o.optDouble("end_ms"))
        }
        if (o.has("start_sec") && o.has("end_sec")) {
            return seg(o.optDouble("start_sec") * 1000.0, o.optDouble("end_sec") * 1000.0)
        }
        return null
    }

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap(), postBody: String? = null): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                    if (postBody != null) {
                        post(postBody.toRequestBody("application/json".toMediaType()))
                    }
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else resp.body?.string()?.let { JSONObject(it) }
                }
            }.getOrNull()
        }

    private suspend fun fromIntroDb(imdb: String, isSeries: Boolean, season: Int, episode: Int): SkipSegments? {
        if (imdb.isBlank()) return null
        val url = "https://api.introdb.app/segments?imdb_id=$imdb" +
            (if (isSeries) "&season=$season&episode=$episode" else "&is_movie=true")
        val j = getJson(url) ?: return null
        return SkipSegments(
            recap = ms(j.optJSONObject("recap")),
            intro = ms(j.optJSONObject("intro")),
            outro = ms(j.optJSONObject("outro")),
            postCredits = ms(j.optJSONObject("post_credits")),
        )
    }

    private suspend fun fromSkipDb(imdb: String, isSeries: Boolean, season: Int, episode: Int, durationSec: Double): SkipSegments? {
        if (imdb.isBlank()) return null
        val url = "https://api.skipdb.tv/api/segments?imdb_id=$imdb" +
            (if (isSeries) "&season=$season&episode=$episode" else "") +
            (if (durationSec > 0) "&duration=${durationSec.toLong()}" else "")
        val j = getJson(url) ?: return null
        val s = j.optJSONObject("segments") ?: return null
        return SkipSegments(
            recap = ms(s.optJSONObject("recap")),
            intro = ms(s.optJSONObject("intro")),
            outro = ms(s.optJSONObject("outro")),
        )
    }

    private fun norm(t: String): String = t.lowercase().replace(Regex("[^a-z0-9]"), "")

    private suspend fun fromAnimeSkip(title: String, isSeries: Boolean, episode: Int, durationSec: Double, clientId: String): SkipSegments? {
        if (!isSeries || title.isBlank() || episode <= 0 || clientId.isBlank()) return null
        val gql = "query(\$s:String!){searchShows(search:\$s,limit:5){name episodes{number timestamps{at type{name}}}}}"
        val body = JSONObject().put("query", gql).put("variables", JSONObject().put("s", title)).toString()
        val headers = mapOf("X-Client-ID" to clientId)
        val j = getJson("https://api.anime-skip.com/graphql", headers, body) ?: return null
        val shows = j.optJSONObject("data")?.optJSONArray("searchShows") ?: return null
        val want = norm(title)
        var show: JSONObject? = null
        for (i in 0 until shows.length()) {
            val s = shows.optJSONObject(i) ?: continue
            if (norm(s.optString("name")) == want) { show = s; break }
        }
        if (show == null) return null
        val eps = show.optJSONArray("episodes") ?: return null
        var epObj: JSONObject? = null
        for (i in 0 until eps.length()) {
            val e = eps.optJSONObject(i) ?: continue
            if (e.optInt("number") == episode) {
                if (epObj == null || (e.optJSONArray("timestamps")?.length() ?: 0) > (epObj.optJSONArray("timestamps")?.length() ?: 0)) {
                    epObj = e
                }
            }
        }
        val ts = epObj?.optJSONArray("timestamps") ?: return null
        if (ts.length() == 0) return null
        data class TsItem(val at: Double, val name: String)
        val items = mutableListOf<TsItem>()
        for (i in 0 until ts.length()) {
            val t = ts.optJSONObject(i) ?: continue
            items += TsItem(t.optDouble("at"), t.optJSONObject("type")?.optString("name").orEmpty().lowercase())
        }
        items.sortBy { it.at }
        var recap: SkipSegment? = null
        var intro: SkipSegment? = null
        var outro: SkipSegment? = null
        for (i in 0 until items.size) {
            val curr = items[i]
            val nextAt = items.getOrNull(i + 1)?.at ?: (curr.at + 90.0)
            val a = curr.at * 1000.0
            val b = nextAt * 1000.0
            if (curr.name.contains("recap")) recap = recap ?: seg(a, b)
            else if (curr.name.contains("intro") || curr.name.contains("opening")) intro = intro ?: seg(a, b)
            else if (curr.name.contains("credit") || curr.name.contains("ending")) outro = outro ?: seg(a, if (durationSec > 0) maxOf(b, durationSec * 1000.0) else b)
        }
        return SkipSegments(recap, intro, outro, null)
    }

    // v1.13.0: merge respeitando a duração do arquivo e a prioridade da cena
    // pós-créditos (ver o KDoc da classe). Sem duração conhecida (> 0), vale
    // só o primeiro-não-nulo por tipo, como na v1.8.1.
    private fun merge(list: List<SkipSegments?>, durationSec: Double = 0.0): SkipSegments {
        var recap: SkipSegment? = null
        var intro: SkipSegment? = null
        var outro: SkipSegment? = null
        var postCredits: SkipSegment? = null
        list.filterNotNull().forEach { s ->
            if (recap == null && s.recap != null) recap = s.recap
            if (intro == null && s.intro != null) intro = s.intro
            if (outro == null && s.outro != null) outro = s.outro
            if (postCredits == null && s.postCredits != null) postCredits = s.postCredits
        }
        // Duração do arquivo (quando conhecida): segmento que começa depois do
        // fim é de outro corte/versão e sai; fim além da duração é aparado; o
        // que sobrar com menos de 3s sai (mesmo corte do seg()).
        val durMs = if (durationSec > 0) (durationSec * 1000.0).toLong() else 0L
        fun within(s: SkipSegment?): SkipSegment? {
            if (s == null || durMs <= 0) return s
            if (s.start >= durMs) return null
            val end = minOf(s.end, durMs)
            return if (end - s.start < 3000) null else s.copy(end = end)
        }
        recap = within(recap)
        intro = within(intro)
        outro = within(outro)
        postCredits = within(postCredits)
        // Pós-créditos tem prioridade na sobreposição: o encerramento é
        // aparado até o início da cena; se não sobrar 3s de encerramento, ele
        // sai e só a cena fica (o botão vira "Ir para pós-créditos" no player).
        if (outro != null && postCredits != null && outro.end > postCredits.start) {
            outro = if (postCredits.start - outro.start < 3000) null
            else outro.copy(end = postCredits.start)
        }
        return SkipSegments(recap, intro, outro, postCredits)
    }

    suspend fun get(
        imdb: String,
        title: String,
        isSeries: Boolean,
        season: Int,
        episode: Int,
        durationSec: Double = 0.0,
        animeSkipClientId: String = "",
    ): SkipSegments = coroutineScope {
        val key = "$imdb|$title|${if (isSeries) "$season:$episode" else "movie"}|${(durationSec / 60).toLong()}"
        cache[key]?.let { return@coroutineScope it }
        val a = async { fromIntroDb(imdb, isSeries, season, episode) }
        val b = async { fromSkipDb(imdb, isSeries, season, episode, durationSec) }
        val c = async { fromAnimeSkip(title, isSeries, episode, durationSec, animeSkipClientId) }
        val res = merge(listOf(a.await(), b.await(), c.await()), durationSec)
        if (res.recap != null || res.intro != null || res.outro != null || res.postCredits != null) {
            cache[key] = res
        }
        res
    }
}
