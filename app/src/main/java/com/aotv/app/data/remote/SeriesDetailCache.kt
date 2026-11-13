package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.dto.TmdbSeriesDetailDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache em memória do detalhe de série do TMDB (Lote 2, v1.10.0).
 *
 * O detalhe (`tv/{id}`) é a ÚNICA chamada que traz `next_episode_to_air` e
 * `last_episode_to_air` — e dois lugares precisam dele para as MESMAS séries:
 * a fileira "Novos episódios" da Início ([com.aotv.app.ui.movies.MoviesHomeViewModel])
 * e o Calendário da Biblioteca ([com.aotv.app.ui.movies.MinhaListaViewModel]).
 * Sem cache, abrir a Início e depois a Biblioteca buscava cada série 2x.
 *
 * Regras:
 * - 1 entrada por tmdbId, válida por 6h (air date não muda a cada minuto e o
 *   TMDB atualiza a grade 1x ao dia; errar por horas não é grave aqui).
 * - Falha de rede NÃO é cacheada (a próxima leitura tenta de novo).
 * - Sem chave TMDB, devolve null sem chamar a rede.
 * - Só o detalhe "magro" (`append = ""`): os dois campos de episódio vêm no
 *   corpo padrão do `tv/{id}`, não precisam de append.
 * - É cache de sessão: morrer com o processo é esperado; nada aqui é persistido.
 */
@Singleton
class SeriesDetailCache @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) {
    private data class Cached(val detail: TmdbSeriesDetailDto, val atMs: Long)

    companion object {
        /** Validade de uma entrada do cache (air dates mudam no ritmo da grade). */
        const val TTL_MS = 6 * 60 * 60 * 1000L
    }

    private val lock = Any()
    private val cache = HashMap<Int, Cached>()

    /** Detalhe da série, do cache quando válido, senão do TMDB. null sem chave ou em falha. */
    suspend fun get(tmdbId: Int): TmdbSeriesDetailDto? {
        if (tmdbId <= 0) return null
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val hit = cache[tmdbId]
            if (hit != null && now - hit.atMs < TTL_MS) return hit.detail
        }
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return null
        val detail = runCatching { tmdb.getSeriesDetail(tmdbId, append = "", apiKey = apiKey) }.getOrNull()
            ?: return null
        synchronized(lock) { cache[tmdbId] = Cached(detail, System.currentTimeMillis()) }
        return detail
    }

    /** Esvazia o cache (ex.: troca de perfil — cada perfil tem suas séries, e o detalhe em si
     * não muda por perfil, mas o esvaziamento evita segurar dados de outro perfil na memória). */
    fun clear() {
        synchronized(lock) { cache.clear() }
    }
}
