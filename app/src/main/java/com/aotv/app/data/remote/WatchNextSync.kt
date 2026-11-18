package com.aotv.app.data.remote

import android.app.PendingIntent
import android.app.UiModeManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.tv.TvContract
import android.os.Build
import com.aotv.app.MainActivity
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.entity.MovieHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Watch Next no launcher / Continue Watching na tela inicial do Google TV
 * (Lote 22, v1.32.0, item 4).
 *
 * O "Continuar assistindo" vira o canal de recomendações do launcher:
 * cada item em andamento vira um programa em
 * [TvContract.WatchNextPrograms] (API 26+; abaixo disso, não faz nada).
 * O launcher da TV mostra o canal sozinho quando há programas — sem app
 * de sistema e sem permissão especial: o TvProvider deixa o app escrever
 * as PRÓPRIAS linhas (as que carregam o seu pacote); mexer nas linhas de
 * outro app é que exigiria a permissão de EPG.
 *
 * - Título, pôster (URL do TMDB), duração, posição e último acesso vão
 *   nos campos do programa; série leva temporada/episódio (de "T1:E3").
 * - O toque no programa abre o app no detalhe do título (intent explícito
 *   p/ a MainActivity com os extras, consumido via [WatchNextDeepLink]).
 * - Sincroniza quando o Continuar muda (MoviesHomeViewModel) e quando a
 *   página "Controle remoto e TV" pede ("Sincronizar agora"). Itens que
 *   saíram do Continuar são apagados do canal. Desligar o toggle apaga tudo.
 * - Só roda na TV (UiMode TELEVISION) e com a pref ligada. No celular o
 *   TvProvider nem sempre existe — tudo sob runCatching, sem crash.
 */
data class WatchNextItem(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastEpisode: String,
    val lastWatchedAt: Long,
)

/** Deep link do Watch Next: a MainActivity oferece, a navegação consome. */
object WatchNextDeepLink {
    data class Target(val tmdbId: Int, val type: String)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    fun offer(tmdbId: Int, type: String) {
        if (tmdbId > 0) _target.value = Target(tmdbId, type)
    }

    fun consume() {
        _target.value = null
    }
}

object WatchNextSync {
    /** Extras que a MainActivity lê do intent do programa. */
    const val EXTRA_TMDB_ID = "watch_next_tmdb_id"
    const val EXTRA_TYPE = "watch_next_type"

    /** Id interno do programa no TvProvider (estável por título). */
    fun providerId(tmdbId: Int, type: String): String = "aotv:$tmdbId:$type"

    fun toItem(entity: MovieHistoryEntity): WatchNextItem = WatchNextItem(
        tmdbId = entity.tmdbId,
        type = entity.type,
        title = entity.title,
        posterUrl = entity.posterUrl,
        positionMs = entity.positionMs,
        durationMs = entity.durationMs,
        lastEpisode = entity.lastEpisode,
        lastWatchedAt = entity.lastWatchedAt,
    )

    fun isTv(context: Context): Boolean {
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return manager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Sincroniza o canal com a lista atual do Continuar. Itens novos entram,
     * itens que mudaram de posição são atualizados, itens que saíram são
     * apagados. Sem TV ou com a pref desligada, apaga tudo e sai.
     */
    suspend fun sync(context: Context, prefs: PlatformPrefs, items: List<WatchNextItem>) =
        withContext(Dispatchers.IO) {
            runCatching { syncInternal(context, prefs, items) }
        }

    @Volatile private var lastSyncMs: Long = 0
    @Volatile private var lastSyncIds: Set<String> = emptySet()

    /**
     * Como [sync], mas com throttle: o Continuar emite a cada mudança de
     * histórico e o TvProvider não precisa ser reescrito a cada uma. Sincroniza
     * quando a lista de títulos mudou ou quando passou [minIntervalMs]
     * desde a última sincronização. A página de configurações chama [sync]
     * direto ("Sincronizar agora", sem throttle).
     */
    suspend fun syncThrottled(
        context: Context,
        prefs: PlatformPrefs,
        items: List<WatchNextItem>,
        minIntervalMs: Long = 60_000,
    ) {
        val ids = items.map { providerId(it.tmdbId, it.type) }.toSet()
        val now = System.currentTimeMillis()
        if (ids == lastSyncIds && now - lastSyncMs < minIntervalMs) return
        lastSyncIds = ids
        lastSyncMs = now
        sync(context, prefs, items)
    }

    /** Apaga todos os programas deste app no Watch Next. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        runCatching { deleteAll(context) }
    }

    private fun syncInternal(context: Context, prefs: PlatformPrefs, items: List<WatchNextItem>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isTv(context)) return
        if (!prefs.watchNextEnabled) {
            deleteAll(context)
            return
        }
        val resolver = context.contentResolver
        val uri = TvContract.WatchNextPrograms.CONTENT_URI
        val valid = items.filter { it.tmdbId > 0 && it.title.isNotBlank() }.take(30)
        val wantedIds = valid.map { providerId(it.tmdbId, it.type) }.toSet()
        // Linhas que já existem (id interno -> id da linha).
        val existing = mutableMapOf<String, Long>()
        runCatching {
            resolver.query(
                uri,
                arrayOf(
                    TvContract.WatchNextPrograms._ID,
                    TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                ),
                "${TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME} = ?",
                arrayOf(context.packageName),
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms._ID)
                val providerIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
                while (cursor.moveToNext()) {
                    if (idIdx >= 0 && providerIdx >= 0) {
                        existing[cursor.getString(providerIdx).orEmpty()] = cursor.getLong(idIdx)
                    }
                }
            }
        }
        for (item in valid) {
            val values = valuesFor(context, item)
            val rowId = existing[providerId(item.tmdbId, item.type)]
            if (rowId != null) {
                runCatching {
                    resolver.update(
                        uri, values,
                        "${TvContract.WatchNextPrograms._ID} = ?",
                        arrayOf(rowId.toString()),
                    )
                }
            } else {
                runCatching { resolver.insert(uri, values) }
            }
        }
        // Apaga o que saiu do Continuar.
        for ((providerIdValue, rowId) in existing) {
            if (providerIdValue !in wantedIds) {
                runCatching {
                    resolver.delete(
                        uri,
                        "${TvContract.WatchNextPrograms._ID} = ?",
                        arrayOf(rowId.toString()),
                    )
                }
            }
        }
    }

    private fun deleteAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.contentResolver.delete(
                TvContract.WatchNextPrograms.CONTENT_URI,
                "${TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME} = ?",
                arrayOf(context.packageName),
            )
        }
    }

    private fun valuesFor(context: Context, item: WatchNextItem): ContentValues {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TMDB_ID, item.tmdbId)
            putExtra(EXTRA_TYPE, item.type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // O requestCode distingue filme de série com o mesmo tmdbId: os
        // extras NÃO entram na igualdade do PendingIntent, então sem isso
        // um sobrescreveria o outro (FLAG_UPDATE_CURRENT).
        val requestCode = (item.tmdbId shl 1) or (if (isSeries(item.type)) 1 else 0)
        val pending = PendingIntent.getActivity(
            context, requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val values = ContentValues().apply {
            put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, providerId(item.tmdbId, item.type))
            put(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, context.packageName)
            put(TvContract.WatchNextPrograms.COLUMN_TITLE, item.title)
            put(TvContract.WatchNextPrograms.COLUMN_TYPE, watchNextType(item.type))
            put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.TYPE_CONTINUE)
            put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, item.durationMs)
            put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, item.positionMs)
            put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, item.lastWatchedAt)
            put(TvContract.WatchNextPrograms.COLUMN_TRANSIENT, 0)
            put(TvContract.WatchNextPrograms.COLUMN_INTENT, pending)
            if (item.posterUrl.isNotBlank()) {
                put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, item.posterUrl)
            }
            parseEpisode(item.lastEpisode)?.let { (season, episode) ->
                put(TvContract.WatchNextPrograms.COLUMN_SEASON_NUMBER, season)
                put(TvContract.WatchNextPrograms.COLUMN_EPISODE_NUMBER, episode)
                put(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, "T${season} E$episode")
            }
        }
        return values
    }

    /** O tipo do app é "MOVIE" ou "TV_SERIES" (nomes do MediaType). */
    private fun isSeries(type: String): Boolean =
        type.equals("TV_SERIES", ignoreCase = true) || type.equals("SERIES", ignoreCase = true)

    private fun watchNextType(type: String): Int =
        if (isSeries(type)) {
            TvContract.WatchNextPrograms.TYPE_TV_SERIES
        } else {
            TvContract.WatchNextPrograms.TYPE_MOVIE
        }

    /** "T1:E3" -> (1, 3). null sem episódio. */
    private fun parseEpisode(lastEpisode: String): Pair<Int, Int>? {
        val match = Regex("""T(\d+):E(\d+)""").find(lastEpisode.trim()) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: return null
        val episode = match.groupValues[2].toIntOrNull() ?: return null
        return season to episode
    }
}
