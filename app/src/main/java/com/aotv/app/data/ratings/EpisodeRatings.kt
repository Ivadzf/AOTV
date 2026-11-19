// ─── data/ratings/EpisodeRatings.kt (Lote 24 item 12, v1.34.0) ─────────────
// Fontes da nota dos episódios: TMDB (a de hoje), Trakt, OMDb e TVmaze.
// Configuração "Nota dos episódios" com DUAS opções — média das fontes que
// têm nota p/ o episódio, ou UMA única fonte (sem fallback p/ outra fonte
// quando a escolhida não tem nota).
//
// CONTRATO DE PREFS (a tela de Configurações é de outra frente — o
// coordenador liga estas prefs ao PlatformPrefs na junção):
// - episode_rating_mode_v1 (String, por perfil): "average" (default) ou
//   "tmdb" | "trakt" | "omdb" | "tvmaze".
// - omdb_api_key (String, global, vazio por padrão): chave GRATUITA do
//   USUÁRIO (omdbapi.com, 1000 req/dia). Nunca embutir chave no código.
//   Enquanto o coordenador não liga, [InMemoryEpisodeRatingSettings]
//   guarda os valores só na sessão — o modo default ("average") já vale.
//
// O MDBList NÃO entra aqui: o parâmetro `m` dele só aceita movie|show, sem
// nota por episódio (lição registrada no Lote 21).

package com.aotv.app.data.ratings

import com.aotv.app.data.remote.OmdbService
import com.aotv.app.data.remote.TraktService
import com.aotv.app.data.remote.TvMazeService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Chave da pref do modo da nota (por perfil). Valores em [EPISODE_RATING_MODES]. */
const val PREF_EPISODE_RATING_MODE = "episode_rating_mode_v1"

/** Chave da pref da chave do OMDb (global, do usuário). Vazia = fonte desligada. */
const val PREF_OMDB_API_KEY = "omdb_api_key"

/** Média das fontes que têm nota p/ o episódio (default). */
const val MODE_AVERAGE = "average"

/** Só o TMDB (o comportamento de hoje). */
const val MODE_TMDB = "tmdb"

/** Só o Trakt (exige a conexão Trakt). */
const val MODE_TRAKT = "trakt"

/** Só o OMDb (exige a chave do usuário). */
const val MODE_OMDB = "omdb"

/** Só o TVmaze (grátis, sem chave). */
const val MODE_TVMAZE = "tvmaze"

/** Modos válidos de [PREF_EPISODE_RATING_MODE]. */
val EPISODE_RATING_MODES = setOf(MODE_AVERAGE, MODE_TMDB, MODE_TRAKT, MODE_OMDB, MODE_TVMAZE)

/** Timeout por fonte ao resolver a nota (8 s): uma fonte lenta nunca segura o navegador. */
const val EPISODE_RATING_SOURCE_TIMEOUT_MS = 8_000L

/**
 * Acesso às prefs da nota dos episódios. A implementação real (ligada ao
 * PlatformPrefs) é do coordenador na junção; até lá vale a de memória.
 */
interface EpisodeRatingSettings {
    /** Modo atual ("average" por padrão). */
    val modeFlow: StateFlow<String>

    /** Chave do OMDb do usuário ("" = sem chave). */
    val omdbKeyFlow: StateFlow<String>

    fun setMode(v: String)
    fun setOmdbKey(v: String)
}

/** Fallback em memória (só a sessão): modo "average", sem chave do OMDb. */
class InMemoryEpisodeRatingSettings : EpisodeRatingSettings {
    private val _modeFlow = MutableStateFlow(MODE_AVERAGE)
    override val modeFlow: StateFlow<String> = _modeFlow.asStateFlow()

    private val _omdbKeyFlow = MutableStateFlow("")
    override val omdbKeyFlow: StateFlow<String> = _omdbKeyFlow.asStateFlow()

    override fun setMode(v: String) {
        _modeFlow.value = if (v in EPISODE_RATING_MODES) v else MODE_AVERAGE
    }

    override fun setOmdbKey(v: String) {
        _omdbKeyFlow.value = v.trim()
    }
}

/**
 * Ligação p/ o Hilt (junção do Lote 24): a implementação real, sobre o
 * PlatformPrefs (ver data/local/Lote24SettingsWiring.kt). A de memória
 * ([InMemoryEpisodeRatingSettings]) fica só como fallback p/ testes.
 */
@Module
@InstallIn(SingletonComponent::class)
object EpisodeRatingsModule {
    @Provides
    @Singleton
    fun provideEpisodeRatingSettings(
        prefs: com.aotv.app.data.local.PlatformPrefs,
    ): EpisodeRatingSettings = com.aotv.app.data.local.PlatformPrefsEpisodeRatingSettings(prefs)
}

/** Uma fonte de nota de episódio (todas na escala 0–10). */
enum class EpisodeRatingSource(val id: String, val label: String) {
    TMDB(MODE_TMDB, "TMDB"),
    TRAKT(MODE_TRAKT, "Trakt"),
    OMDB(MODE_OMDB, "OMDb"),
    TVMAZE(MODE_TVMAZE, "TVmaze");

    companion object {
        fun fromId(id: String): EpisodeRatingSource? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Nota resolvida de um episódio. [value] 0.0 = sem nota em nenhuma fonte
 * (o cinza neutro do Rating Map, como hoje). [label] é a origem p/ exibir
 * no card ("Trakt" ou "média de 3 fontes"); null quando não há nota.
 */
data class ResolvedEpisodeRating(
    val value: Double,
    val label: String?,
    val sources: List<EpisodeRatingSource>,
) {
    val hasRating: Boolean get() = value > 0

    companion object {
        fun none(): ResolvedEpisodeRating = ResolvedEpisodeRating(0.0, null, emptyList())
    }
}

/** Rótulo da origem da nota p/ o Rating Map ("Fonte da nota: ..."). */
fun episodeRatingOriginCaption(mode: String): String = when (mode) {
    MODE_TMDB -> "Fonte da nota: TMDB"
    MODE_TRAKT -> "Fonte da nota: Trakt"
    MODE_OMDB -> "Fonte da nota: OMDb"
    MODE_TVMAZE -> "Fonte da nota: TVmaze"
    else -> "Fonte da nota: média das fontes"
}

/**
 * Resolve a nota de um episódio no modo configurado.
 *
 * - Média: soma SÓ as fontes que têm nota p/ o episódio e divide pela
 *   quantidade delas. Se só uma tiver nota, a média é ela. Se nenhuma
 *   tiver, fica "sem nota". A falha de uma fonte nunca quebra as outras.
 * - Fonte única: usa só a fonte escolhida; sem nota nela, fica "sem nota"
 *   (NÃO cai p/ outra fonte).
 * - O TMDB nunca vai à rede aqui: [tmdbRating] é o `vote_average` que o
 *   navegador já carregou — a nota do TMDB é a base e chega primeiro.
 * - [imdbId] é o IMDb id da série (quando o detalhe já o resolveu): o
 *   TVmaze prefere o lookup por IMDb, que não erra a série; sem ele, busca
 *   por título. [originalTitle] é o título original (fallback do OMDb e
 *   do TVmaze quando o título exibido, PT-BR, não casa — mesmo padrão do
 *   MDBList no detalhe).
 *
 * Nunca lança (falha de rede/HTTP/timeout vira "sem nota" naquela fonte).
 */
@Singleton
class EpisodeRatingsResolver @Inject constructor(
    private val settings: EpisodeRatingSettings,
    private val trakt: TraktService,
    private val omdb: OmdbService,
    private val tvMaze: TvMazeService,
) {
    val modeFlow: StateFlow<String> get() = settings.modeFlow

    suspend fun resolve(
        tmdbId: Int,
        season: Int,
        episode: Int,
        title: String,
        imdbId: String?,
        tmdbRating: Double,
        originalTitle: String = "",
    ): ResolvedEpisodeRating {
        val mode = settings.modeFlow.value
        if (mode == MODE_TMDB) {
            val value = tmdbRating.takeIf { it > 0 } ?: return ResolvedEpisodeRating.none()
            return ResolvedEpisodeRating(
                value = value,
                label = EpisodeRatingSource.TMDB.label,
                sources = listOf(EpisodeRatingSource.TMDB),
            )
        }
        val single = EpisodeRatingSource.fromId(mode)
        if (single != null) {
            val value = timedSource { sourceRating(single, tmdbId, season, episode, title, imdbId, tmdbRating, originalTitle) }
                ?: return ResolvedEpisodeRating.none()
            return ResolvedEpisodeRating(value, single.label, listOf(single))
        }
        // Modo desconhecido cai na média (o default) — nunca inventa modo.
        return coroutineScope {
            val tmdbValue = tmdbRating.takeIf { it > 0 }
            val traktDeferred = async { timedSource { traktRating(tmdbId, season, episode) } }
            val omdbDeferred = async { timedSource { omdb.getEpisodeRating(title, season, episode, originalTitle) } }
            val tvMazeDeferred = async { timedSource { tvMaze.getEpisodeRating(season, episode, title, imdbId, originalTitle) } }
            val found = ArrayList<Pair<EpisodeRatingSource, Double>>()
            if (tmdbValue != null) found += EpisodeRatingSource.TMDB to tmdbValue
            traktDeferred.await()?.let { found += EpisodeRatingSource.TRAKT to it }
            omdbDeferred.await()?.let { found += EpisodeRatingSource.OMDB to it }
            tvMazeDeferred.await()?.let { found += EpisodeRatingSource.TVMAZE to it }
            if (found.isEmpty()) return@coroutineScope ResolvedEpisodeRating.none()
            val average = found.sumOf { it.second } / found.size
            val label = if (found.size == 1) found[0].first.label else "média de ${found.size} fontes"
            ResolvedEpisodeRating(average, label, found.map { it.first })
        }
    }

    /** Trakt só entra conectado (decisão do lote) — sem login, sem nota do Trakt. */
    private suspend fun traktRating(tmdbId: Int, season: Int, episode: Int): Double? {
        if (!trakt.isConnected) return null
        return trakt.getEpisodeRating(tmdbId, season, episode)
    }

    private suspend fun sourceRating(
        source: EpisodeRatingSource,
        tmdbId: Int,
        season: Int,
        episode: Int,
        title: String,
        imdbId: String?,
        tmdbRating: Double,
        originalTitle: String = "",
    ): Double? = when (source) {
        EpisodeRatingSource.TMDB -> tmdbRating.takeIf { it > 0 }
        EpisodeRatingSource.TRAKT -> traktRating(tmdbId, season, episode)
        EpisodeRatingSource.OMDB -> omdb.getEpisodeRating(title, season, episode, originalTitle)
        EpisodeRatingSource.TVMAZE -> tvMaze.getEpisodeRating(season, episode, title, imdbId, originalTitle)
    }

    /**
     * Uma fonte com timeout curto: estourar o tempo vira "sem nota" naquela
     * fonte, nunca erro. Exceção (rede/HTTP) também vira null — o
     * CancellationException da coroutine passa direto (ensureActive).
     */
    private suspend fun <T> timedSource(block: suspend () -> T?): T? {
        return try {
            withTimeoutOrNull(EPISODE_RATING_SOURCE_TIMEOUT_MS) { block() }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }
}
