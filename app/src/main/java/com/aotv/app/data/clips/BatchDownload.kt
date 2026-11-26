package com.aotv.app.data.clips

import android.content.Context
import android.net.Uri
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.dao.DownloadDao
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.data.remote.StremioStream
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Download em lote + download inteligente (Lote 1 pós-v1.45.0, v1.46.0
 * — Frente B, itens 2, 3 e 4).
 *
 * - Lote (item 2): vários episódios (ou a temporada inteira, via
 *   [wantSeason]) numa fila SEQUENCIAL — um download por vez, com
 *   progresso por episódio ([batchProgress], chave = id da entidade).
 *   Cada episódio baixa pela MESMA maquinaria do vídeo inteiro
 *   ([ClipDownloader.downloadFull]: HLS com todos os segmentos, MP4
 *   direto, ordem de variantes, cookies, AES-128, pasta SAF) — nada de
 *   novo é baixado por outro caminho. O .mp4 pelo DownloadManager
 *   continua existindo p/ o toque manual na folha de fontes; o lote
 *   usa o caminho do ClipDownloader p/ HLS e MP4 terem UMA fila só.
 * - Inteligente (item 3): DESLIGADO por padrão (a pref `smartDownload`
 *   já nasce false — ver PlatformPrefs). Quando o usuário marca um
 *   episódio como assistido no diálogo de opções, [noteEpisodeWatched]
 *   apaga o arquivo do episódio assistido (SÓ se ele for um download
 *   inteligente, id ":smart" — download manual ":full" ou do
 *   DownloadManager NUNCA é apagado por aqui, sem confirmação) e deixa
 *   o próximo episódio como [pendingSmart]. O próximo só entra na fila
 *   quando a fonte dele é conhecida — ao abrir a folha de fontes dele
 *   ([maybeAutoDownload], chamado pela tela de detalhes) — porque a
 *   resolução de fontes vive na MovieDetailViewModel, fora desta
 *   frente. Sem worker novo: o gatilho é só "ao assistir/marcar
 *   assistido" (o app JÁ tem WorkManager p/ gravações/lembretes em
 *   WorkScheduler, mas nada ali é periódico p/ downloads — e um worker
 *   periódico não resolveria fonte nenhuma sem a tela de detalhes).
 * - Episódio com fonte escolhida (item 4): [requestEpisodeDownload]
 *   marca o episódio em "modo download"; a folha de fontes, ao tocar
 *   no download de uma fonte, consome o pedido
 *   ([consumeDownloadModeRequest]) e baixa daquela fonte — .mp4 vai
 *   pelo caminho antigo (DownloadManager), HLS/outro vai por aqui.
 *
 * O gerente sobrevive à saída da tela de detalhes: tem escopo próprio
 * ([CoroutineScope] com [SupervisorJob] + [Dispatchers.IO], mesmo
 * padrão do escopo do DownloadRepository) e é singleton por
 * applicationContext ([get]) — a tela de detalhes e a de Downloads
 * falam com a MESMA instância (e a mesma fila). Sem isso, o download
 * começado no detalhe morreria ao voltar p/ a lista (o ViewModel da
 * tela de detalhes some com ela, e o da tela de Downloads é outra
 * instância). As entidades vão p/ o DAO, então a tela de Downloads
 * mostra o download mesmo sem o gerente estar observado.
 */

/** Um episódio da fila de lote (manual ou inteligente). */
data class BatchEpisodeRequest(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterUrl: String,
    val season: Int,
    val episode: Int,
    /** URL da fonte escolhida/resolvida (master HLS + headers, ou MP4 direto). */
    val streamUrl: String,
    /** true = download inteligente (id ":smart"); false = manual (id ":full"). */
    val smart: Boolean = false,
) {
    /** "T1:E3" — o mesmo formato do [DownloadEntity.lastEpisode]. */
    val lastEpisode: String get() = ClipDownload.episodeLabel(season, episode)

    /** Id da entidade — igual ao do vídeo inteiro, com ":smart" no inteligente. */
    val id: String get() =
        if (smart) ClipDownload.smartId(tmdbId, type, lastEpisode)
        else ClipDownload.fullId(tmdbId, type, lastEpisode)
}

/** Episódios de uma série/temporada, p/ a fileira da aba de Downloads. */
data class SeasonDownloadGroup(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String,
    val season: Int,
    /** Episódios ordenados por número (o [DownloadEntity.lastEpisode] manda). */
    val episodes: List<DownloadEntity>,
)

/** Downloads separados em fileiras de episódios + o resto (filmes, avulsos). */
data class GroupedDownloads(
    val groups: List<SeasonDownloadGroup>,
    val singles: List<DownloadEntity>,
)

/** Próximo episódio p/ o download inteligente (ainda sem fonte resolvida). */
data class PendingSmartRequest(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterUrl: String,
    val season: Int,
    val episode: Int,
)

object BatchDownload {

    /** "T1:E3" → (1, 3). null = não é rótulo de episódio. */
    fun parseEpisodeLabel(lastEpisode: String): Pair<Int, Int>? {
        val match = Regex("^T(\\d+):E(\\d+)$").matchEntire(lastEpisode.trim()) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: return null
        val episode = match.groupValues[2].toIntOrNull() ?: return null
        return season to episode
    }

    /**
     * Próximo episódio depois de (season, episode): o seguinte na mesma
     * temporada quando o catálogo local o conhece; senão o E1 da próxima
     * temporada (a próxima temporada pode não existir — o pedido
     * pendente só é cumprido quando o usuário abrir a folha daquele
     * episódio, então nada é baixado de uma temporada inexistente).
     */
    fun nextEpisodeAfter(season: Int, episode: Int, seasonEpisodes: List<Int>): Pair<Int, Int>? {
        if (seasonEpisodes.isEmpty()) return null
        val sorted = seasonEpisodes.sorted()
        val nextSame = sorted.firstOrNull { it > episode }
        if (nextSame != null) return season to nextSame
        if (episode >= (sorted.maxOrNull() ?: episode)) return (season + 1) to 1
        return null
    }

    /**
     * Agrupa os downloads de episódios por série → temporada (item 2):
     * só entra no grupo quem tem [DownloadEntity.lastEpisode] no formato
     * "T1:E3" e tmdbId > 0. Filmes e o resto ficam em [GroupedDownloads.singles],
     * na ordem que chegaram. Grupos ordenados por série (tmdbId) e
     * temporada; episódios dentro do grupo, por número.
     */
    fun groupDownloadsBySeason(entities: List<DownloadEntity>): GroupedDownloads {
        val grouped = LinkedHashMap<Pair<Int, Int>, MutableList<DownloadEntity>>()
        val singles = ArrayList<DownloadEntity>()
        for (entity in entities) {
            val parsed = parseEpisodeLabel(entity.lastEpisode)
            if (entity.tmdbId > 0 && parsed != null) {
                val key = entity.tmdbId to parsed.first
                val bucket = grouped.getOrPut(key) { ArrayList() }
                bucket += entity
            } else {
                singles += entity
            }
        }
        val groups = grouped.entries.map { (key, eps) ->
            val first = eps.first()
            val sorted = eps.sortedBy { e -> parseEpisodeLabel(e.lastEpisode)?.second ?: 0 }
            SeasonDownloadGroup(
                tmdbId = key.first,
                title = first.title,
                posterUrl = first.posterUrl,
                season = key.second,
                episodes = sorted,
            )
        }.sortedWith(compareBy({ it.tmdbId }, { it.season }))
        return GroupedDownloads(groups = groups, singles = singles)
    }

    /**
     * A fonte serve p/ o download do vídeo inteiro (HLS ou MP4 direto)?
     * É a mesma classificação do [ClipDownloader.downloadFull] — a
     * folha de fontes só oferece o download manual p/ .mp4, mas o lote
     * e o inteligente aceitam HLS também (o player toca a mesma URL).
     * Torrent, arquivo local e embed/página não servem.
     */
    fun isDownloadableFullSource(streamUrl: String): Boolean =
        when (ClipDownload.classifyFullSource(streamUrl)) {
            ClipDownload.FullEligibility.Hls -> true
            ClipDownload.FullEligibility.Mp4 -> true
            is ClipDownload.FullEligibility.Unsupported -> false
        }
}

/** Acesso ao DAO fora de um componente Hilt (mesmo padrão do ClipDownloader). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BatchDownloadEntryPoint {
    fun downloadDao(): DownloadDao
}

class BatchDownloadManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao: DownloadDao by lazy {
        EntryPointAccessors.fromApplication(appContext, BatchDownloadEntryPoint::class.java).downloadDao()
    }
    private val prefs: PlatformPrefs by lazy { PlatformPrefs(appContext) }
    private val clipDownloader: ClipDownloader by lazy { ClipDownloader(appContext) }

    /** Escopo próprio (app): o download sobrevive à saída da tela de detalhes. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Progresso (0..100) por id de episódio do lote — a tela de Downloads mescla com o do DownloadManager. */
    private val _batchProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val batchProgress: StateFlow<Map<String, Int>> = _batchProgress.asStateFlow()

    /** Mensagem curta p/ toast (null = nada pendente). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A tela chama depois de mostrar a mensagem, p/ não repetir no retorno. */
    fun consumeMessage() {
        _message.value = null
    }

    /** Fila sequencial: um episódio por vez (o ClipDownloader não é paralelo). */
    private val queue = ArrayDeque<BatchEpisodeRequest>()
    private var processingJob: Job? = null

    /** Próximo episódio do download inteligente, aguardando a fonte (ver o KDoc do arquivo). */
    private val _pendingSmart = MutableStateFlow<PendingSmartRequest?>(null)
    val pendingSmart: StateFlow<PendingSmartRequest?> = _pendingSmart.asStateFlow()

    /** Episódios da temporada inteira aguardando a fonte (item 2 — ver [wantSeason]). */
    private val wantedEpisodes = mutableSetOf<Triple<Int, Int, Int>>()

    /** Episódio em "modo download" (item 4): a folha de fontes baixa dele. */
    private val _downloadModeRequest = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    val downloadModeRequest: StateFlow<Triple<Int, Int, Int>?> = _downloadModeRequest.asStateFlow()

    /**
     * Enfileira UM episódio com a fonte já resolvida (item 4 e o
     * cumprimento do inteligente/[wantSeason]). Ignora duplicado (já
     * baixado, baixando ou na fila). Devolve false quando não enfileirou
     * (duplicado, inteligente desligado ou sem Wi-Fi no modo inteligente).
     */
    fun enqueueEpisode(request: BatchEpisodeRequest): Boolean {
        if (request.streamUrl.isBlank()) {
            _message.value = "Sem fonte para baixar este episódio."
            return false
        }
        if (request.smart) {
            if (!prefs.smartDownload) {
                _message.value = "O download inteligente está desligado (Configurações → Downloads)."
                return false
            }
            if (prefs.smartDownloadWifiOnly && !MobileDataUsage.isWifiActive(appContext)) {
                _message.value = "Download inteligente só em Wi-Fi: conecte no Wi-Fi para baixar o próximo episódio."
                return false
            }
        }
        synchronized(queue) {
            if (queue.any { it.id == request.id }) {
                _message.value = "Este episódio já está na fila de downloads."
                return false
            }
            if (_batchProgress.value.containsKey(request.id)) {
                _message.value = "Este episódio já está baixando."
                return false
            }
            queue.addLast(request)
        }
        startProcessingIfIdle()
        return true
    }

    /** Enfileira vários episódios de uma vez (item 2 — a fila é sequencial, um por vez). */
    fun enqueueBatch(requests: List<BatchEpisodeRequest>) {
        var added = 0
        for (request in requests) {
            if (enqueueEpisode(request)) added++
        }
        if (added > 0) {
            _message.value = if (added == 1) "1 episódio na fila de downloads." else "$added episódios na fila de downloads."
        }
    }

    /**
     * "Baixar temporada inteira" (item 2): marca os episódios lançados
     * da temporada como QUERIDOS — sem fonte ainda. Cada um baixa
     * sozinho quando a fonte dele for conhecida, isto é, quando o
     * usuário abrir a folha de fontes daquele episódio (ver
     * [maybeAutoDownload]): a resolução de fontes vive na
     * MovieDetailViewModel e esta frente não pode resolvê-las em lote.
     * Episódio já baixado não entra (o DAO diz, na hora do cumprimento).
     */
    fun wantSeason(
        tmdbId: Int,
        type: String,
        title: String,
        posterUrl: String,
        season: Int,
        episodes: List<Int>,
    ) {
        if (episodes.isEmpty()) return
        synchronized(wantedEpisodes) {
            for (episode in episodes) {
                wantedEpisodes.add(Triple(tmdbId, season, episode))
            }
        }
        _message.value = "Temporada na fila: cada episódio baixa quando a fonte dele for resolvida (abra a folha de fontes do episódio)."
    }

    /**
     * "Baixar episódio" (item 4): marca o episódio em modo download e
     * abre a folha de fontes — a fonte escolhida lá é que alimenta o
     * download (ver [consumeDownloadModeRequest]).
     */
    fun requestEpisodeDownload(tmdbId: Int, season: Int, episode: Int) {
        _downloadModeRequest.value = Triple(tmdbId, season, episode)
    }

    /**
     * A folha de fontes consome o pedido de download (item 4): true só
     * quando o pedido é deste episódio — aí o download sai da fonte
     * tocada; false = abrir a folha foi só "Ver fontes" (o download
     * segue o caminho antigo, .mp4 pelo DownloadManager).
     */
    fun consumeDownloadModeRequest(tmdbId: Int, season: Int, episode: Int): Boolean {
        val wanted = _downloadModeRequest.value
        if (wanted == null) return false
        val wantedTmdbId = wanted.first
        val wantedSeason = wanted.second
        val wantedEpisode = wanted.third
        if (wantedTmdbId != tmdbId || wantedSeason != season || wantedEpisode != episode) return false
        _downloadModeRequest.value = null
        return true
    }

    /**
     * Episódio marcado como assistido (item 3): com o inteligente
     * DESLIGADO, não faz nada. Ligado: apaga o arquivo do episódio
     * assistido (SÓ download inteligente — ver [deleteSmartDownload])
     * e deixa o próximo episódio como pendente ([pendingSmart]), p/
     * baixar quando a fonte dele for conhecida ([maybeAutoDownload]).
     * [seasonEpisodes] são os números que o catálogo local conhece.
     */
    fun noteEpisodeWatched(
        tmdbId: Int,
        type: String,
        title: String,
        posterUrl: String,
        season: Int,
        episode: Int,
        seasonEpisodes: List<Int>,
    ) {
        if (!prefs.smartDownload) return
        scope.launch {
            deleteSmartDownload(tmdbId, type, ClipDownload.episodeLabel(season, episode))
            val next = BatchDownload.nextEpisodeAfter(season, episode, seasonEpisodes) ?: return@launch
            val nextSeason = next.first
            val nextEpisode = next.second
            _pendingSmart.value = PendingSmartRequest(tmdbId, type, title, posterUrl, nextSeason, nextEpisode)
        }
    }

    /**
     * A folha de fontes do episódio abriu (itens 2 e 3): se o episódio
     * é o pendente do inteligente ou um querido da temporada, baixa da
     * MELHOR fonte baixável da lista (HLS ou MP4 — a lista já vem
     * ordenada pelas prefs do usuário; torrent/embed não servem e são
     * pulados). Sem fonte baixável, o pedido continua pendente e nada
     * é baixado — o usuário ainda pode baixar manual por "Baixar episódio".
     */
    fun maybeAutoDownload(
        tmdbId: Int,
        type: String,
        title: String,
        posterUrl: String,
        season: Int,
        episode: Int,
        streams: List<StremioStream>,
    ) {
        val pending = _pendingSmart.value
        val isPendingSmart = pending != null &&
            pending.tmdbId == tmdbId && pending.season == season && pending.episode == episode
        val isWanted = synchronized(wantedEpisodes) {
            wantedEpisodes.contains(Triple(tmdbId, season, episode))
        }
        if (!isPendingSmart && !isWanted) return
        val smart = isPendingSmart
        val best = streams.firstOrNull { stream -> BatchDownload.isDownloadableFullSource(stream.url) } ?: return
        if (isPendingSmart) _pendingSmart.value = null
        if (isWanted) {
            synchronized(wantedEpisodes) { wantedEpisodes.remove(Triple(tmdbId, season, episode)) }
        }
        enqueueEpisode(
            BatchEpisodeRequest(
                tmdbId = tmdbId, type = type, title = title, posterUrl = posterUrl,
                season = season, episode = episode, streamUrl = best.url, smart = smart,
            ),
        )
    }

    /**
     * Retoma um download do lote que falhou ("Retomar" na tela de
     * Downloads): re-download do zero a partir da fonte guardada na
     * entidade — o "Retomar" normal ([DownloadRepository.resumeDownload])
     * jogaria a URL HLS no DownloadManager, que baixaria a playlist
     * .m3u8 como se fosse .mp4. null = a entidade não tem fonte.
     */
    fun retry(entity: DownloadEntity): Boolean {
        if (entity.sourceUrl.isBlank()) return false
        val parsed = BatchDownload.parseEpisodeLabel(entity.lastEpisode) ?: return false
        val parsedSeason = parsed.first
        val parsedEpisode = parsed.second
        return enqueueEpisode(
            BatchEpisodeRequest(
                tmdbId = entity.tmdbId, type = entity.type, title = entity.title,
                posterUrl = entity.posterUrl, season = parsedSeason, episode = parsedEpisode,
                streamUrl = entity.sourceUrl, smart = ClipDownload.isSmartId(entity.id),
            ),
        )
    }

    /** Apaga o download INTELIGENTE de um episódio (item 3 — a guarda). */
    private suspend fun deleteSmartDownload(tmdbId: Int, type: String, lastEpisode: String) {
        val smartId = ClipDownload.smartId(tmdbId, type, lastEpisode)
        val entity = dao.get(smartId) ?: return
        // Guarda: só apaga o que é ":smart" e já terminou — download
        // manual (":full"), do DownloadManager ou em andamento nunca
        // passa por aqui (o id já filtra, e o status protege o andamento).
        if (!ClipDownload.isSmartId(entity.id)) return
        if (entity.status != "done") return
        deleteLocalFile(entity.localUri)
        dao.delete(entity)
    }

    /** Apaga o arquivo local (file:// por File, content:// pelo ContentResolver — mesmo padrão do repo). */
    private fun deleteLocalFile(localUri: String) {
        if (localUri.isBlank()) return
        runCatching {
            if (localUri.startsWith("content://")) {
                appContext.contentResolver.delete(Uri.parse(localUri), null, null)
            } else {
                Uri.parse(localUri).path?.let { File(it).delete() }
            }
        }
    }

    /** Liga o processador da fila quando ele não está rodando. */
    private fun startProcessingIfIdle() {
        synchronized(queue) {
            val running = processingJob
            if (running != null && running.isActive) return
            processingJob = scope.launch { processQueue() }
        }
    }

    /** Processa a fila, um episódio por vez, até esvaziar. */
    private suspend fun processQueue() {
        while (true) {
            val request = synchronized(queue) {
                if (queue.isEmpty()) null else queue.removeFirst()
            } ?: break
            downloadOne(request)
        }
    }

    /** Baixa UM episódio pelo [ClipDownloader.downloadFull] e reporta o progresso. */
    private suspend fun downloadOne(request: BatchEpisodeRequest) {
        // Já baixado por outro caminho (o toque manual na folha, por
        // exemplo): não baixa de novo — o DAO é a fonte da verdade.
        val existing = dao.get(request.id)
        if (existing != null && existing.status == "done") {
            _message.value = "Este episódio já foi baixado."
            return
        }
        _batchProgress.value = _batchProgress.value + (request.id to 0)
        val result = clipDownloader.downloadFull(
            tmdbId = request.tmdbId,
            type = request.type,
            title = request.title,
            posterUrl = request.posterUrl,
            lastEpisode = request.lastEpisode,
            streamUrl = request.streamUrl,
            smart = request.smart,
            onProgress = { downloaded, total ->
                if (total > 0) {
                    val percent = ((downloaded * 100) / total).coerceIn(0, 100)
                    val current = _batchProgress.value[request.id]
                    if (current != percent) {
                        _batchProgress.value = _batchProgress.value + (request.id to percent)
                    }
                }
            },
        )
        _batchProgress.value = _batchProgress.value - request.id
        _message.value = when (result) {
            is ClipResult.Done -> "Episódio salvo em Downloads: ${request.lastEpisode}"
            is ClipResult.Failed -> "Não deu para baixar ${request.lastEpisode}: ${result.message}"
            is ClipResult.Unsupported -> result.reason
            ClipResult.BlockedWifi -> "“Só Wi-Fi” ligado: conecte no Wi-Fi para baixar ${request.lastEpisode}."
        }
    }

    companion object {
        @Volatile
        private var instance: BatchDownloadManager? = null

        /** Singleton por applicationContext (ver o KDoc do arquivo). */
        fun get(context: Context): BatchDownloadManager {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                val doubleChecked = instance
                if (doubleChecked != null) return doubleChecked
                val created = BatchDownloadManager(context.applicationContext)
                instance = created
                return created
            }
        }
    }
}
