package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows acompanhados nos trackers (Lote 13, v1.23.0): auto-add do calendário.
 *
 * Junta as séries da watchlist de shows do Trakt ([TraktService.getShowWatchlist])
 * com as de status `watching`/`plantowatch` do Simkl ([SimklService.getTrackedShows]),
 * sem duplicar (o TMDB decide) e sem tocar na Minha Lista local: a série do
 * tracker NÃO é salva no aparelho — ela só entra no conjunto do calendário e
 * das fileiras "Novos episódios"/"Próximos" enquanto o tracker a devolver.
 * Sem tracker conectado, a lista volta vazia (o calendário segue só com o local).
 * Sem id do TMDB, sem entrada — nada é inventado.
 *
 * Cache em memória com TTL de 6h (igual ao [SeriesDetailCache]): a Início e a
 * Biblioteca compartilham a mesma busca, e abrir a Biblioteca 2x seguidas
 * não refaz nada. [force] ignora o cache (botão "Atualizar" do calendário).
 */
@Singleton
class TrackerShowsCache @Inject constructor(
    private val trakt: TraktService,
    private val simkl: SimklService,
) {
    private val lock = Mutex()
    private var cached: List<Triple<Int, String, String>> = emptyList()
    private var cachedAt = 0L

    /** Séries dos trackers: (tmdbId, título, pôster vazio — o chamador resolve pelo TMDB). */
    suspend fun get(force: Boolean = false): List<Triple<Int, String, String>> {
        val now = System.currentTimeMillis()
        if (!force && cachedAt != 0L && now - cachedAt < TTL_MS) return cached
        val fresh = withContext(Dispatchers.IO) { fetch() }
        lock.withLock {
            cached = fresh
            cachedAt = System.currentTimeMillis()
        }
        return fresh
    }

    private suspend fun fetch(): List<Triple<Int, String, String>> {
        val out = LinkedHashMap<Int, Triple<Int, String, String>>()
        if (trakt.isConnected) {
            trakt.getShowWatchlist().getOrNull().orEmpty().forEach { entry ->
                val tmdb = entry.show?.ids?.tmdb ?: return@forEach
                if (tmdb > 0) out.putIfAbsent(tmdb, Triple(tmdb, entry.show?.title.orEmpty(), ""))
            }
        }
        simkl.getTrackedShows().getOrNull().orEmpty().forEach { show ->
            out.putIfAbsent(show.tmdbId, Triple(show.tmdbId, show.title, ""))
        }
        return out.values.toList()
    }

    companion object {
        /** TTL 6h, igual ao SeriesDetailCache. */
        const val TTL_MS = 6L * 3_600_000L
    }
}
