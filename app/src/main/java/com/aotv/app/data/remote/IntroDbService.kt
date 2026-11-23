package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── IntroDB — segmentos puláveis com filtro de confiança (Lote 16, v1.26.0) ──
//
// API pública SEM chave, verificada ao vivo da VM em 2026-11-16:
//   série: GET https://api.introdb.app/segments?imdb_id={tt...}&season={s}&episode={e}
//   filme: GET https://api.introdb.app/segments?imdb_id={tt...}&is_movie=true
// Resposta 200 (exemplo real, tt0371746, 2026-11-16):
//   {"imdb_id":"tt0371746","media_type":"movie","is_movie":true,
//    "intro":{"start_sec":7512,"end_sec":7545,"start_ms":7512000,"end_ms":7545000,
//             "confidence":1,"submission_count":1,"updated_at":"..."},
//    "recap":null,
//    "outro":{"start_sec":7038,"end_sec":7540,...,"confidence":1,"submission_count":1},
//    "post_credits":{"start_sec":7540,"end_sec":7580,...},
//    "no_intro":false,"no_recap":false,"no_outro":false}
// E tt0903747 S01E01: intro null + "no_intro":true, outro presente
// (3431000–3500000, confidence 1, submission_count 1), "no_recap":true.
//
// DIFERENÇA p/ o SkipSegmentsService (v1.8.1): aquele mescla IntroDB +
// SkipDB + Anime-Skip SEM olhar confidence/submission_count e SEM respeitar
// os flags no_* — um segmento de confiança 0,1 com 1 envio virava botão de
// "Pular abertura". Este serviço é SÓ IntroDB, COM o filtro: segmento com
// confidence < MIN_CONFIDENCE (0,5) ou submission_count < MIN_SUBMISSIONS
// (0 envios = ninguém confirmou) é descartado, e um trecho confirmado como
// inexistente (no_intro/no_recap/no_outro = true) nunca gera botão — ver
// [IntroDbSegments]. Os dois serviços coexistem: o coordenador escolhe UM
// caminho por título (este, filtrado, é o recomendado); chamar os dois
// busca a IntroDB 2x por episódio — 1 GET pequeno a mais, sem outro custo
// (este serviço tem cache próprio de 15 min; o outro não compartilha).
//
// COMO PLUGAR (o coordenador integra no MoviePlayerScreen.kt):
//   val introDb = IntroDbService()
//   val segs = introDb.get(imdb, isSeries = type == "SERIES", season, episode)
//   // segs.intro / .recap / .outro / .postCredits já vêm filtrados;
//   // segs.noIntro / .noRecap / .noOutro dizem o que NÃO existe.
//   // Marcadores p/ a barra + alvo do botão: ver SkipSegments.kt
//   // (buildProgressMarkers, skipTargetAt, postCreditsNotice).
// Nunca lança exceção: sem rede, HTTP != 200 ou JSON inválido, devolve
// IntroDbSegments vazio (sem segmentos, sem flags) — o player segue sem
// botão de pular, como se a IntroDB não tivesse o título.

/** Um trecho da IntroDB que passou no filtro de confiança (em ms). */
data class IntroDbSegment(
    val startMs: Long,
    val endMs: Long,
    /** Confiança da comunidade, 0..1 (a API devolve 1 p/ 1 envio confirmado). */
    val confidence: Double,
    /** Quantos envios sustentam o trecho. */
    val submissionCount: Int,
)

/** Resposta filtrada da IntroDB p/ um título. Null = a API não tem o trecho
 *  (ou ele foi descartado pelo filtro). Os flags no_* são a confirmação da
 *  comunidade de que o trecho NÃO EXISTE — com eles em true, o player não
 *  mostra botão nem procura o trecho em outra fonte. */
data class IntroDbSegments(
    val imdbId: String,
    val isSeries: Boolean,
    val season: Int,
    val episode: Int,
    val recap: IntroDbSegment? = null,
    val intro: IntroDbSegment? = null,
    val outro: IntroDbSegment? = null,
    val postCredits: IntroDbSegment? = null,
    val noIntro: Boolean = false,
    val noRecap: Boolean = false,
    val noOutro: Boolean = false,
) {
    /** Algum trecho pulável sobreviveu ao filtro. */
    fun hasAny(): Boolean = recap != null || intro != null || outro != null || postCredits != null
}

@Singleton
class IntroDbService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private data class Cached(val at: Long, val value: IntroDbSegments)

    private val cache = ConcurrentHashMap<String, Cached>()

    init {
        // Troca de perfil sem restart (v1.45.0, item 1): o cache é por
        // título, não por perfil — mas a resposta pode carregar o limite
        // de confiança do perfil anterior; limpar é barato (15 min).
        com.aotv.app.data.local.ProfileEpoch.registerInvalidator("introdb") { cache.clear() }
    }

    /**
     * Segmentos filtrados da IntroDB p/ o título. Cache em memória por
     * [CACHE_TTL_MS] (curto: a comunidade edita os trechos; 15 min evita
     * repetir a chamada a cada troca de episódio sem servir dado velho por
     * muito tempo). Respostas vazias COM flags no_* também entram no cache
     * (a confirmação de "não tem intro" é informação); falha de rede não.
     */
    suspend fun get(imdb: String, isSeries: Boolean, season: Int, episode: Int): IntroDbSegments {
        val empty = IntroDbSegments(imdb, isSeries, season, episode)
        if (imdb.isBlank()) return empty
        val key = "$imdb|${if (isSeries) "$season:$episode" else "movie"}"
        val now = System.currentTimeMillis()
        val hit = cache[key]
        if (hit != null && now - hit.at < CACHE_TTL_MS) return hit.value
        val url = if (isSeries) seriesUrl(imdb, season, episode) else movieUrl(imdb)
        val fetched = runCatching {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else resp.body?.string()?.let { parseResponse(imdb, isSeries, season, episode, JSONObject(it)) }
                }
            }
        }.getOrNull() ?: return empty
        cache[key] = Cached(now, fetched)
        return fetched
    }

    companion object {
        /**
         * URL base da IntroDB (v1.45.0, item 4): era fixa no código; agora
         * vem da pref `pref_introdb_base_url_v1` (a tela de chaves pelo
         * celular também grava aqui). O app define no arranque
         * (`MainActivity`) e quando a pref muda. Padrão = API pública.
         */
        @Volatile
        var baseUrl: String = "https://api.introdb.app"

        /** Confiança mínima p/ o trecho valer: abaixo de 0,5 a comunidade
         *  discorda mais do que concorda — o botão pularia p/ o lugar errado. */
        const val MIN_CONFIDENCE = 0.5

        /** Envios mínimos: 0 envios = trecho sem nenhuma confirmação. */
        const val MIN_SUBMISSIONS = 1

        /** Trecho com menos de 3 s é ruído (mesmo corte do SkipSegmentsService). */
        const val MIN_SEGMENT_MS = 3000L

        /** Cache em memória: 15 min (ver [get]). */
        const val CACHE_TTL_MS = 15 * 60 * 1000L

        fun seriesUrl(imdb: String, season: Int, episode: Int): String =
            "$baseUrl/segments?imdb_id=$imdb&season=$season&episode=$episode"

        fun movieUrl(imdb: String): String =
            "$baseUrl/segments?imdb_id=$imdb&is_movie=true"

        /**
         * Um trecho da resposta, já filtrado. Aceita start_ms/end_ms (o que
         * a API devolve) e start_sec/end_sec (fallback). Null quando o JSON
         * não tem o trecho, quando ele é curto demais, quando a confiança
         * está abaixo de [MIN_CONFIDENCE] ou quando ninguém o confirmou
         * (submission_count < [MIN_SUBMISSIONS]).
         */
        fun parseSegment(obj: JSONObject?): IntroDbSegment? {
            if (obj == null) return null
            val startMs: Long
            val endMs: Long
            if (obj.has("start_ms") && obj.has("end_ms")) {
                startMs = obj.optDouble("start_ms").toLong()
                endMs = obj.optDouble("end_ms").toLong()
            } else if (obj.has("start_sec") && obj.has("end_sec")) {
                startMs = (obj.optDouble("start_sec") * 1000.0).toLong()
                endMs = (obj.optDouble("end_sec") * 1000.0).toLong()
            } else {
                return null
            }
            if (endMs - startMs < MIN_SEGMENT_MS) return null
            val confidence = obj.optDouble("confidence", 0.0)
            if (confidence < MIN_CONFIDENCE) return null
            val submissions = obj.optInt("submission_count", 0)
            if (submissions < MIN_SUBMISSIONS) return null
            return IntroDbSegment(maxOf(0L, startMs), endMs, confidence, submissions)
        }

        /** Resposta inteira, já filtrada. Campos ausentes viram null/false. */
        fun parseResponse(imdb: String, isSeries: Boolean, season: Int, episode: Int, json: JSONObject): IntroDbSegments =
            IntroDbSegments(
                imdbId = imdb,
                isSeries = isSeries,
                season = season,
                episode = episode,
                recap = parseSegment(json.optJSONObject("recap")),
                intro = parseSegment(json.optJSONObject("intro")),
                outro = parseSegment(json.optJSONObject("outro")),
                postCredits = parseSegment(json.optJSONObject("post_credits")),
                noIntro = json.optBoolean("no_intro", false),
                noRecap = json.optBoolean("no_recap", false),
                noOutro = json.optBoolean("no_outro", false),
            )
    }
}
