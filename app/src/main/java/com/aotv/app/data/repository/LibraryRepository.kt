package com.aotv.app.data.repository

import com.aotv.app.data.local.dao.MovieHistoryDao
import com.aotv.app.data.local.dao.WatchedEpisodeDao
import com.aotv.app.data.local.dao.WatchlistDao
import com.aotv.app.data.local.entity.MovieHistoryEntity
import com.aotv.app.data.local.entity.WatchedEpisodeEntity
import com.aotv.app.data.local.entity.WatchlistEntity
import com.aotv.app.domain.model.MovieItem
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agrega os 3 DAOs da BIBLIOTECA do AOTV (Minha Lista, Continuar Assistindo, Episodios Vistos).
 * Expoe metodos convenientes baseados em [MovieItem] (dominio) para a UI consumir.
 * Tudo vale para o perfil ativo. As listas observadas trocam sozinhas quando o perfil muda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepository @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val movieHistoryDao: MovieHistoryDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val prefs: com.aotv.app.data.local.PlatformPrefs,
    private val db: com.aotv.app.data.local.AppDatabase,
) {
    private val pid get() = prefs.profileId.value

    /** Apaga Minha Lista, histórico e episódios vistos de um perfil removido (tudo ou nada). */
    suspend fun dropProfile(profileId: String) = db.withTransaction {
        watchlistDao.dropProfile(profileId)
        movieHistoryDao.clear(profileId)
        watchedEpisodeDao.dropProfile(profileId)
    }

    // ----- Minha Lista (Watchlist) -----

    /** Itens salvos na Minha Lista, mais recentes primeiro. Com [profileId] lê o perfil pedido,
     * em vez do ativo (usado pela sincronia do Trakt, que captura o perfil uma vez no início). */
    fun watchlist(profileId: String? = null): Flow<List<WatchlistEntity>> =
        if (profileId != null) watchlistDao.getAll(profileId) else prefs.profileId.flatMapLatest { watchlistDao.getAll(it) }

    /** Reativo: o item esta salvo na Minha Lista? */
    fun isSaved(tmdbId: Int, type: String): Flow<Boolean> =
        prefs.profileId.flatMapLatest { watchlistDao.isSaved(it, tmdbId, type) }

    /** Reativo: o item esta salvo na Minha Lista? (sobrecarga p/ MovieItem) */
    fun isSaved(item: MovieItem): Flow<Boolean> = isSaved(item.tmdbId, item.type.name)

    /** Salva um [MovieItem] na Minha Lista. Com [profileId], grava nesse perfil em vez do ativo. */
    suspend fun saveToList(item: MovieItem, profileId: String? = null) {
        watchlistDao.add(
            WatchlistEntity(
                profileId = profileId ?: pid,
                tmdbId = item.tmdbId,
                type = item.type.name,
                title = item.title,
                posterUrl = item.posterUrl,
                year = item.year,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Remove um [MovieItem] da Minha Lista. */
    suspend fun removeFromList(item: MovieItem) =
        watchlistDao.remove(pid, item.tmdbId, item.type.name)

    /** Remove da Minha Lista por id + tipo. */
    suspend fun removeFromList(tmdbId: Int, type: String) =
        watchlistDao.remove(pid, tmdbId, type)

    // ----- Continuar Assistindo / Reproducoes recentes (MovieHistory) -----

    /** Reproducoes recentes (max 30), mais recentes primeiro. */
    fun recentlyPlayed(): Flow<List<MovieHistoryEntity>> = prefs.profileId.flatMapLatest { movieHistoryDao.getAll(it) }

    /** Listas de uma vez (p/ o backup): Minha Lista, Continuar e episódios vistos. */
    suspend fun watchlistNow(profileId: String? = null): List<WatchlistEntity> = watchlist(profileId).first()

    suspend fun recentlyPlayedNow(): List<MovieHistoryEntity> = recentlyPlayed().first()

    suspend fun allWatchedEpisodes(profileId: String? = null): List<WatchedEpisodeEntity> =
        watchedEpisodeDao.getAllForProfile(profileId ?: pid)

    /** Progresso salvo de um item (posição/fonte/episódio), ou null. Com [profileId], lê desse
     * perfil em vez do ativo. */
    suspend fun getPlayback(tmdbId: Int, type: String, profileId: String? = null): MovieHistoryEntity? =
        movieHistoryDao.get(profileId ?: pid, tmdbId, type)

    /**
     * Registra uma reproducao (upsert). [lastEpisode] ex "T1:E3" para serie, vazio p/ filme.
     * [lastSourceUrl] = fonte usada (p/ retomar na mesma). Preserva valores antigos quando 0/vazio.
     */
    suspend fun recordPlayback(
        item: MovieItem,
        positionMs: Long = 0,
        durationMs: Long = 0,
        lastEpisode: String = "",
        lastSourceUrl: String = "",
        lastAddon: String = "",
    ) {
        // Assistir de novo libera o título que tinha sido removido do Continuar (v1.13.0).
        prefs.unhideContinue(item.tmdbId, item.type.name)
        val prev = movieHistoryDao.get(pid, item.tmdbId, item.type.name)
        movieHistoryDao.upsert(
            MovieHistoryEntity(
                profileId = pid,
                tmdbId = item.tmdbId,
                type = item.type.name,
                title = item.title.ifBlank { prev?.title ?: "" },
                posterUrl = item.posterUrl.ifBlank { prev?.posterUrl ?: "" },
                lastWatchedAt = System.currentTimeMillis(),
                positionMs = if (positionMs > 0) positionMs else prev?.positionMs ?: 0,
                durationMs = if (durationMs > 0) durationMs else prev?.durationMs ?: 0,
                lastEpisode = lastEpisode.ifBlank { prev?.lastEpisode ?: "" },
                lastSourceUrl = lastSourceUrl.ifBlank { prev?.lastSourceUrl ?: "" },
                lastAddon = lastAddon.ifBlank { prev?.lastAddon ?: "" },
            )
        )
    }

    /** Salva SÓ a posição/fonte (chamado pelo player periodicamente e ao sair). */
    suspend fun saveProgress(
        tmdbId: Int, type: String, title: String, posterUrl: String,
        positionMs: Long, durationMs: Long, lastEpisode: String, lastSourceUrl: String,
        lastAddon: String = "",
        profileId: String? = null,
    ) {
        val targetPid = profileId ?: pid
        // Assistir de novo libera o título que tinha sido removido do Continuar (v1.13.0).
        // Só vale p/ o perfil ativo: a sincronia (outro perfil) NÃO traz o título de volta.
        if (targetPid == pid) prefs.unhideContinue(tmdbId, type)
        val prev = movieHistoryDao.get(targetPid, tmdbId, type)
        movieHistoryDao.upsert(
            MovieHistoryEntity(
                profileId = targetPid,
                tmdbId = tmdbId,
                type = type,
                title = title.ifBlank { prev?.title ?: "" },
                posterUrl = posterUrl.ifBlank { prev?.posterUrl ?: "" },
                lastWatchedAt = System.currentTimeMillis(),
                positionMs = positionMs,
                durationMs = durationMs,
                lastEpisode = lastEpisode.ifBlank { prev?.lastEpisode ?: "" },
                lastSourceUrl = lastSourceUrl.ifBlank { prev?.lastSourceUrl ?: "" },
                lastAddon = lastAddon.ifBlank { prev?.lastAddon ?: "" },
            )
        )
    }

    /**
     * Progresso vindo de outra conta (Stremio, Nuvio). Só grava quando é mais novo que o do
     * aparelho, para a importação nunca apagar o que a pessoa assistiu aqui. Devolve se gravou.
     */
    suspend fun importPlayback(
        tmdbId: Int, type: String, title: String, posterUrl: String,
        positionMs: Long, durationMs: Long, lastEpisode: String, watchedAt: Long, profileId: String? = null,
    ): Boolean {
        val target = profileId ?: pid
        val prev = movieHistoryDao.get(target, tmdbId, type)
        if (prev != null && prev.lastWatchedAt >= watchedAt) return false
        movieHistoryDao.upsert(
            MovieHistoryEntity(
                profileId = target, tmdbId = tmdbId, type = type,
                title = title.ifBlank { prev?.title.orEmpty() }, posterUrl = posterUrl.ifBlank { prev?.posterUrl.orEmpty() },
                lastWatchedAt = watchedAt, positionMs = positionMs, durationMs = durationMs,
                lastEpisode = lastEpisode, lastSourceUrl = prev?.lastSourceUrl.orEmpty(), lastAddon = prev?.lastAddon.orEmpty(),
            )
        )
        return true
    }

    /** Item de outra conta na Minha Lista. Não mexe no que já estava salvo. Devolve se entrou. */
    suspend fun importToList(
        tmdbId: Int, type: String, title: String, posterUrl: String, year: String, addedAt: Long, profileId: String? = null,
    ): Boolean {
        val target = profileId ?: pid
        if (watchlistDao.isSaved(target, tmdbId, type).first()) return false
        watchlistDao.add(WatchlistEntity(target, tmdbId, type, title, posterUrl, year, addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
        return true
    }

    /** Remove UM item do "continuar assistindo". */
    suspend fun removePlayback(tmdbId: Int, type: String) = movieHistoryDao.delete(pid, tmdbId, type)

    /**
     * Remove um título do "continuar assistindo" PELO TIPO ARMAZENADO
     * (v1.21.0 — regressão do long-press no card): o tipo que chega do
     * card pode divergir do da linha (o MovieItem só tem MOVIE/SERIES, e
     * um canal ao vivo "LIVE" vira MOVIE no card). Esconde e apaga a linha
     * em TODOS os tipos armazenados; sem linha nenhuma, usa o tipo que
     * chegou como fallback (nunca deixa o item preso na lista).
     */
    suspend fun removePlaybackAnyType(tmdbId: Int, fallbackType: String) {
        val stored = runCatching { movieHistoryDao.typesFor(pid, tmdbId) }.getOrDefault(emptyList())
        val types = stored.ifEmpty { listOf(fallbackType) }
        types.forEach { t ->
            prefs.hideContinue(tmdbId, t)
            movieHistoryDao.delete(pid, tmdbId, t)
        }
    }

    /** Limpa todo o historico de reproducoes. */
    suspend fun clearHistory() = movieHistoryDao.clear(pid)

    // ----- Episodios vistos (WatchedEpisode) -----

    /** Episodios ja vistos de uma serie. */
    fun watchedEpisodes(seriesTmdbId: Int): Flow<List<WatchedEpisodeEntity>> =
        prefs.profileId.flatMapLatest { watchedEpisodeDao.getForSeries(it, seriesTmdbId) }

    /** Ids das séries com pelo menos um episódio visto (p/ a prateleira "Ainda não assistidos"). */
    fun watchedSeriesIds(): Flow<Set<Int>> =
        prefs.profileId.flatMapLatest { watchedEpisodeDao.watchedSeriesIds(it) }
            .map { it.toSet() }

    /** O perfil ativo já viu esse episódio? Com [profileId], confere nesse perfil em vez do ativo. */
    suspend fun isEpisodeWatched(seriesTmdbId: Int, season: Int, episode: Int, profileId: String? = null): Boolean =
        watchedEpisodeDao.isWatched(profileId ?: pid, seriesTmdbId, season, episode)

    /** Marca um episodio como visto. Com [profileId], grava nesse perfil em vez do ativo. */
    suspend fun markEpisodeWatched(seriesTmdbId: Int, season: Int, episode: Int, profileId: String? = null) {
        watchedEpisodeDao.markWatched(
            WatchedEpisodeEntity(
                profileId = profileId ?: pid,
                seriesTmdbId = seriesTmdbId,
                season = season,
                episode = episode,
                watchedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Marca VARIOS episodios da mesma temporada como vistos, tudo ou nada
     * (Lote 14, v1.24.0 — painel de opções do episódio: "anteriores" e
     * "temporada"). Só marca o que o chamador passou — em geral os números
     * que existem no catálogo local da tela de detalhe; nada é inventado.
     */
    suspend fun markEpisodesWatched(seriesTmdbId: Int, season: Int, episodes: List<Int>, profileId: String? = null) {
        if (episodes.isEmpty()) return
        val target = profileId ?: pid
        val now = System.currentTimeMillis()
        db.withTransaction {
            episodes.forEach { ep ->
                watchedEpisodeDao.markWatched(
                    WatchedEpisodeEntity(target, seriesTmdbId, season, ep, now)
                )
            }
        }
    }

    /**
     * "Marcar como assistido" no cartão do Continuar assistindo (Lote 14,
     * v1.24.0 — painel de opções do título). Reusa o mecanismo que já tira
     * um título terminado da fileira ([MoviesHomeViewModel.resolveHistoryItem]):
     * filme completa o progresso (posição = duração → some do Continuar);
     * série marca o episódio em andamento como visto E completa o progresso
     * (→ avança p/ o próximo episódio, ou some se era o último). Sem duração
     * conhecida não há progresso p/ completar: o filme sai do Continuar do
     * mesmo jeito (nada mais há p/ marcar) e a série só marca o episódio.
     * Ao vivo (type LIVE) não tem "assistido" — devolve false sem mexer.
     * Devolve se fez alguma coisa.
     */
    suspend fun markContinueWatched(tmdbId: Int): Boolean {
        val row = recentlyPlayedNow().firstOrNull { it.tmdbId == tmdbId } ?: return false
        if (row.type == "LIVE") return false
        if (row.type == "SERIES") {
            val m = Regex("T(\\d+):E(\\d+)").find(row.lastEpisode)
            val season = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episode = m?.groupValues?.getOrNull(2)?.toIntOrNull()
            if (season != null && episode != null && season > 0 && episode > 0) {
                markEpisodeWatched(row.tmdbId, season, episode)
            }
            if (row.durationMs > 0) {
                saveProgress(row.tmdbId, row.type, row.title, row.posterUrl,
                    positionMs = row.durationMs, durationMs = row.durationMs,
                    lastEpisode = row.lastEpisode, lastSourceUrl = row.lastSourceUrl, lastAddon = row.lastAddon)
                return true
            }
            return season != null && episode != null
        }
        // Filme (ou outro tipo com progresso): completa o progresso.
        if (row.durationMs > 0) {
            saveProgress(row.tmdbId, row.type, row.title, row.posterUrl,
                positionMs = row.durationMs, durationMs = row.durationMs,
                lastEpisode = row.lastEpisode, lastSourceUrl = row.lastSourceUrl, lastAddon = row.lastAddon)
            return true
        }
        removePlaybackAnyType(row.tmdbId, row.type)
        return true
    }

    /** Desmarca um episodio (remove de vistos). */
    suspend fun unmarkEpisodeWatched(seriesTmdbId: Int, season: Int, episode: Int) =
        watchedEpisodeDao.unmark(pid, seriesTmdbId, season, episode)
}
