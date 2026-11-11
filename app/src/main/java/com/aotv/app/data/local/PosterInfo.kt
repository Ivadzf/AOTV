package com.aotv.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import com.aotv.app.data.remote.StremioAddonService
import com.aotv.app.data.remote.StremioStream
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Resumo das fontes dos addons de um título. [n] é quantas fontes vieram e [at] quando foi lido. */
data class SourceQuality(
    val q: Int, val hdr: Boolean, val dolbyVision: Boolean, val atmos: Boolean, val ptAudio: Boolean,
    val n: Int, val at: Long,
)

/** Posição de hoje (1 a 25) por id do TMDB, separada em filmes e séries. */
data class DayRanks(val day: String, val movie: Map<Int, Int>, val series: Map<Int, Int>)

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

fun posterKey(type: MediaType, tmdbId: Int) = (if (type == MediaType.SERIES) "series:" else "movie:") + tmdbId
fun posterKey(item: MovieItem) = if (item.tmdbId > 0) posterKey(item.type, item.tmdbId) else ""

/** Data local em yyyy-MM-dd, [daysAgo] dias atrás. */
fun dayString(daysAgo: Int = 0): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** Os 25 primeiros ids distintos viram posição 1 a 25. */
fun rankMap(ids: List<Int>): Map<Int, Int> {
    val out = LinkedHashMap<Int, Int>()
    for (id in ids) {
        if (out.size >= 25) break
        if (id > 0 && id !in out) out[id] = out.size + 1
    }
    return out
}

/** Melhor resolução, HDR (Dolby Vision também é HDR), Dolby e áudio em português da lista. */
fun summarize(list: List<StremioStream>, now: Long) = SourceQuality(
    q = list.maxOfOrNull { it.qualityRank } ?: 0,
    hdr = list.any { it.hdr || it.dolbyVision },
    dolbyVision = list.any { it.dolbyVision },
    atmos = list.any { it.atmos },
    ptAudio = list.any { it.ptAudio },
    n = list.size,
    at = now,
)

/** Resumo vale uma semana no cartão. */
fun SourceQuality.fresh(now: Long) = now - at < 7 * DAY

/** Lido de novo depois de um dia, ou de 6 horas quando não veio fonte nenhuma. */
fun SourceQuality?.stale(now: Long) = this == null || now - at > (if (n > 0) DAY else 6 * HOUR)

/**
 * O que o pôster mostra além da arte, igual aos cartões do webOS. Ranking de hoje do TMDB, resumo
 * das fontes dos addons (resolução, HDR, Dolby e dublado) e a cor tirada de cada pôster. Fica num
 * arquivo de preferências só dele, porque estes mapas pesariam em cada gravação do principal.
 */
@Singleton
class PosterInfo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PlatformPrefs,
    private val tmdb: TmdbApiService,
    private val stremio: StremioAddonService,
) {
    private val store = context.getSharedPreferences("aotv_cards", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _ranks = MutableStateFlow<DayRanks?>(null)
    val ranks: StateFlow<DayRanks?> = _ranks.asStateFlow()
    private val _quality = MutableStateFlow<Map<String, SourceQuality>>(emptyMap())
    val quality: StateFlow<Map<String, SourceQuality>> = _quality.asStateFlow()

    // Tudo abaixo só muda dentro de synchronized(lock).
    private var addonSig = ""
    private val tints = LinkedHashMap<String, Int>()
    private val queue = ArrayDeque<MovieItem>()
    private val running = HashSet<String>()
    private var ranksLoading = false
    private var saveQualityJob: Job? = null
    private var saveTintJob: Job? = null

    init {
        // Leitura fora da thread principal. Até acabar, o cartão sai sem selo e sem cor salva.
        scope.launch { load() }
    }

    // ── Ranking de hoje ──────────────────────────────────────────────────────

    fun rankOf(r: DayRanks?, item: MovieItem): Int =
        if (r == null || item.tmdbId <= 0 || r.day != dayString()) 0
        else (if (item.type == MediaType.SERIES) r.series else r.movie)[item.tmdbId] ?: 0

    /** Busca os 25 primeiros de hoje (filmes e séries) quando o salvo é de outro dia. */
    fun loadRanks() {
        val day = dayString()
        synchronized(lock) {
            if (_ranks.value?.day == day || ranksLoading) return
            ranksLoading = true
        }
        scope.launch {
            try {
                val key = prefs.tmdbApiKey
                if (key.isBlank()) return@launch
                val movie = top { p -> tmdb.getTrendingMoviesDay(page = p, apiKey = key).results.map { it.id } }
                val series = top { p -> tmdb.getTrendingTvDay(page = p, apiKey = key).results.map { it.id } }
                if (movie.isEmpty() && series.isEmpty()) return@launch
                _ranks.value = DayRanks(day, movie, series)
                fun obj(m: Map<Int, Int>) = JSONObject().apply { m.forEach { (id, n) -> put(id.toString(), n) } }
                store.edit().putString(KEY_RANKS, JSONObject().put("day", day).put("movie", obj(movie)).put("series", obj(series)).toString()).apply()
            } finally {
                synchronized(lock) { ranksLoading = false }
            }
        }
    }

    private suspend fun top(page: suspend (Int) -> List<Int>): Map<Int, Int> = coroutineScope {
        rankMap(listOf(1, 2).map { p -> async { runCatching { page(p) }.getOrDefault(emptyList()) } }.awaitAll().flatten())
    }

    // ── Resumo das fontes ────────────────────────────────────────────────────

    /** Guarda o resumo das fontes de um título. Lista vazia (rede fora) não apaga um que tinha fonte. */
    fun remember(type: MediaType, tmdbId: Int, list: List<StremioStream>) {
        if (tmdbId <= 0) return
        checkAddons()
        val key = posterKey(type, tmdbId)
        val now = System.currentTimeMillis()
        _quality.update { m ->
            val old = m[key]
            if (list.isEmpty() && old != null && old.n > 0 && old.fresh(now)) m else m + (key to summarize(list, now))
        }
        synchronized(lock) {
            if (saveQualityJob?.isActive != true) saveQualityJob = scope.launch { delay(1500); saveQuality() }
        }
    }

    /**
     * Cartão que ficou parado na tela. Entra na fila de leitura das fontes quando o resumo está
     * velho. Fila de 10 (sai o mais antigo) e uma leitura por vez (v1.0.9 — eram duas; cada
     * leitura dispara 1 requisição por addon e competia com os downloads dos pôsteres na
     * internet lenta). Rede medida (dados móveis) fica de fora, o detalhe ainda grava.
     */
    fun want(item: MovieItem) {
        val key = posterKey(item)
        if (key.isEmpty() || metered()) return
        checkAddons()
        if (!_quality.value[key].stale(System.currentTimeMillis())) return
        synchronized(lock) {
            if (key in running || queue.any { posterKey(it) == key }) return
            if (queue.size >= 10) queue.removeFirst()
            queue.addLast(item)
        }
        pump()
    }

    /** Cartão saiu da tela antes da vez dele. */
    fun unwant(item: MovieItem) {
        val key = posterKey(item)
        synchronized(lock) { queue.removeAll { posterKey(it) == key } }
    }

    private fun pump() {
        while (true) {
            val item = synchronized(lock) {
                if (running.size >= 1) return
                queue.removeFirstOrNull()?.also { running += posterKey(it) } ?: return
            }
            scope.launch {
                runCatching { fetch(item) }
                synchronized(lock) { running -= posterKey(item) }
                pump()
            }
        }
    }

    private suspend fun fetch(item: MovieItem) {
        val addons = prefs.stremioAddonList()
        if (addons.isEmpty()) return
        val series = item.type == MediaType.SERIES
        val key = prefs.tmdbApiKey
        val imdb = item.embedId.takeIf { it.startsWith("tt") } ?: if (key.isBlank()) "" else runCatching {
            (if (series) tmdb.getSeriesExternalIds(item.tmdbId, key) else tmdb.getMovieExternalIds(item.tmdbId, key)).imdbId
        }.getOrNull()?.takeIf { it.startsWith("tt") }.orEmpty()
        // Série usa o primeiro episódio como amostra do que os addons têm.
        val ep = if (series) 1 else null
        val list = stremio.getStreamsFrom(addons, if (series) "series" else "movie", imdb, ep, ep, item.tmdbId)
        remember(item.type, item.tmdbId, list)
    }

    /** Lista de addons diferente zera os resumos, que falavam das fontes dos addons antigos. */
    private fun checkAddons() {
        val sig = prefs.stremioAddonList().joinToString("|").hashCode().toString()
        val changed = synchronized(lock) {
            val was = addonSig
            addonSig = sig
            was.isNotEmpty() && was != sig
        }
        if (changed) _quality.value = emptyMap()
    }

    private fun metered() = runCatching {
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(false)

    private fun saveQuality() {
        val items = JSONObject()
        // Os 500 mais recentes.
        _quality.value.entries.sortedByDescending { it.value.at }.take(500).forEach { (k, q) ->
            items.put(
                k, JSONObject().put("q", q.q).put("h", q.hdr).put("d", q.dolbyVision).put("a", q.atmos)
                    .put("p", q.ptAudio).put("n", q.n).put("t", q.at),
            )
        }
        val sig = synchronized(lock) { addonSig }
        store.edit().putString(KEY_QUALITY, JSONObject().put("sig", sig).put("items", items).toString()).apply()
    }

    // ── Cor do pôster ────────────────────────────────────────────────────────

    fun tint(key: String): Int? = synchronized(lock) { tints[key] }

    fun putTint(key: String, hue: Int) {
        synchronized(lock) {
            tints[key] = hue
            if (saveTintJob?.isActive != true) saveTintJob = scope.launch { delay(2000); saveTints() }
        }
    }

    private fun saveTints() {
        val o = JSONObject()
        synchronized(lock) {
            while (tints.size > 800) tints.remove(tints.keys.first())
            tints.forEach { (k, v) -> o.put(k, v) }
        }
        store.edit().putString(KEY_TINTS, o.toString()).apply()
    }

    // ── Leitura do que ficou salvo ───────────────────────────────────────────

    private fun load() {
        runCatching {
            val o = JSONObject(store.getString(KEY_RANKS, null) ?: return@runCatching)
            fun map(k: String) = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associate { it.toInt() to m.getInt(it) } }.orEmpty()
            _ranks.compareAndSet(null, DayRanks(o.getString("day"), map("movie"), map("series")))
        }
        runCatching {
            val o = JSONObject(store.getString(KEY_TINTS, null) ?: return@runCatching)
            synchronized(lock) { o.keys().forEach { k -> if (k !in tints) tints[k] = o.getInt(k) } }
        }
        runCatching {
            val o = JSONObject(store.getString(KEY_QUALITY, null) ?: return@runCatching)
            val sig = prefs.stremioAddonList().joinToString("|").hashCode().toString()
            if (o.optString("sig") != sig) return@runCatching
            val items = o.getJSONObject("items")
            val saved = items.keys().asSequence().associateWith { k ->
                val v = items.getJSONObject(k)
                SourceQuality(
                    v.optInt("q"), v.optBoolean("h"), v.optBoolean("d"), v.optBoolean("a"), v.optBoolean("p"),
                    v.optInt("n"), v.optLong("t"),
                )
            }
            synchronized(lock) { if (addonSig.isEmpty()) addonSig = sig }
            // O que chegou enquanto lia vale mais que o salvo.
            _quality.update { saved + it }
        }
    }

    private companion object {
        const val KEY_RANKS = "rank_day_v1"
        const val KEY_TINTS = "poster_tint_v1"
        const val KEY_QUALITY = "avail_quality_v1"
    }
}
