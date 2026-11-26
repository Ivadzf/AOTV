package com.aotv.app.ui.movies

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.clips.BatchDownloadManager
import com.aotv.app.data.clips.ClipDownload
import com.aotv.app.data.clips.ClipDownloader
import com.aotv.app.data.clips.ClipExportResult
import com.aotv.app.data.clips.ClipExporter
import com.aotv.app.data.clips.ClipResult
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.data.repository.DownloadRepository
import com.aotv.app.data.repository.OrphanFile
import com.aotv.app.data.torrent.Torrents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Um trecho (clipe) baixado, como entrada da lista de Downloads
 * (Lote 18 item 10 — SLOT p/ o subagente de clipes).
 *
 * O subagente de clipes é quem produz estas entradas (título + intervalo,
 * ex.: "Filme (12:34–15:20)"): ele preenche [DownloadsViewModel.clipEntries]
 * (via [setClipEntries]) com os clipes salvos, e a tela os renderiza na
 * mesma lista dos downloads — completos na aba Completos, incompletos na
 * aba Restos — sem reestruturar a tela. Até lá, a lista é vazia e nada
 * muda na tela. [status] usa os mesmos valores dos downloads ("done",
 * "downloading", "failed", "queued"); [localUri] vazio = sem arquivo.
 */
data class ClipDownloadEntry(
    val id: String,
    val title: String,
    /** Intervalo do trecho, ex.: "12:34–15:20" (vai no subtítulo da linha). */
    val intervalLabel: String,
    val posterUrl: String = "",
    val status: String = "done",
    val percent: Int = 0,
    val localUri: String = "",
    /**
     * Fonte do clipe (Lote mpv 4 — Frente 2, v1.39.0): a URL master + headers
     * Kodi guardada na entidade ([DownloadEntity.sourceUrl]). É daqui que
     * sai o vídeo inteiro ([DownloadsViewModel.downloadFullClip]) — clipe
     * antigo sem ela não baixa o vídeo inteiro (avisa, sem crash).
     */
    val sourceUrl: String = "",
    val tmdbId: Int = 0,
    val type: String = "MOVIE",
    val lastEpisode: String = "",
) {
    /** O clipe como download comum, p/ tocar o arquivo local pelo mesmo player. */
    fun toDownloadEntity(): DownloadEntity = DownloadEntity(
        id = id,
        tmdbId = tmdbId,
        type = type,
        title = if (intervalLabel.isNotBlank()) "$title ($intervalLabel)" else title,
        posterUrl = posterUrl,
        sourceUrl = sourceUrl,
        localUri = localUri,
        lastEpisode = lastEpisode,
        downloadId = 0,
        status = status,
        createdAt = 0L,
    )
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
    private val prefs: PlatformPrefs,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // LOTE 18 item 10 — CLIPES: a lista principal EXCLUI os clipes (id com
    // ":clip:") — eles vão para [clipEntries] e a tela os renderiza como
    // ClipDownloadRow. Sem o filtro, o clipe apareceria duas vezes (linha
    // de download + linha de clipe).
    val downloads: StateFlow<List<DownloadEntity>> =
        repo.all().map { list -> list.filterNot { ":clip:" in it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Só Wi-Fi" ligado? (global, Lote 5, v1.14.0). */
    val wifiOnly: StateFlow<Boolean> = prefs.downloadsWifiOnlyFlow

    /** Quantos downloads estão na fila aguardando Wi-Fi. */
    val queuedCount: StateFlow<Int> = downloads.map { list ->
        list.count { it.status == "queued" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Rede atual é Wi-Fi? (p/ o aviso da fila). */
    private val _isWifi = MutableStateFlow(MobileDataUsage.isWifiActive(context))
    val isWifi: StateFlow<Boolean> = _isWifi.asStateFlow()

    /** Dados móveis medidos no mês (p/ o aviso de limite na tela). */
    val mobileMonthBytes: StateFlow<Long> = prefs.mobileDataMonthFlow

    /** Limite mensal de dados móveis atingido? */
    val mobileLimitReached: StateFlow<Boolean> = prefs.mobileDataLimitReachedFlow

    /** Atualiza status (em andamento → done/failed) consultando o DownloadManager. */
    fun refresh() {
        _isWifi.value = MobileDataUsage.isWifiActive(context)
        prefs.refreshMobileDataCounters()
        viewModelScope.launch { repo.refreshStatuses() }
        refreshOrphans()
    }

    /** Restos de arquivos .mp4 sem dono na pasta de downloads (Lote 18 item 4 — aba Restos). */
    private val _orphans = MutableStateFlow<List<OrphanFile>>(emptyList())
    val orphans: StateFlow<List<OrphanFile>> = _orphans.asStateFlow()

    /** Recarrega os restos de arquivos. */
    fun refreshOrphans() {
        viewModelScope.launch { _orphans.value = repo.findOrphans() }
    }

    /** Apaga um resto de arquivo e recarrega a lista. */
    fun deleteOrphan(path: String) {
        viewModelScope.launch {
            repo.deleteOrphan(path)
            _orphans.value = repo.findOrphans()
        }
    }

    /**
     * Trechos (clipes) baixados — SLOT p/ o subagente de clipes
     * (Lote 18 item 10): ele preenche com os clipes salvos e a tela os
     * mostra na mesma lista dos downloads. Vazio até lá.
     */
    private val _clipEntries = MutableStateFlow<List<ClipDownloadEntry>>(emptyList())
    val clipEntries: StateFlow<List<ClipDownloadEntry>> = _clipEntries.asStateFlow()

    /** Preenche a lista de clipes (chamado por quem guarda os clipes — ver [ClipDownloadEntry]). */
    fun setClipEntries(entries: List<ClipDownloadEntry>) {
        _clipEntries.value = entries
    }

    // LOTE 18 item 10 — CLIPES (âncora do subagente de clipes): preenche o
    // slot de clipes a partir do DAO — o ClipDownloader grava o clipe como
    // DownloadEntity com ":clip:" no id, e a tela separa completos (aba
    // Completos) de incompletos (aba Restos) pelo status, como os downloads.
    // O [setClipEntries] continua existindo p/ preenchimento manual.
    init {
        viewModelScope.launch {
            repo.all().collect { list ->
                _clipEntries.value = list
                    .filter { ":clip:" in it.id }
                    .map { it.toClipEntry() }
            }
        }
    }

    /** DownloadEntity de clipe → entrada da lista (título base + intervalo "12:34–15:20"). */
    private fun DownloadEntity.toClipEntry(): ClipDownloadEntry {
        val range = id.substringAfter(":clip:", "").split("-")
        val startMs = range.getOrNull(0)?.toLongOrNull() ?: 0L
        val endMs = range.getOrNull(1)?.toLongOrNull() ?: 0L
        val interval = if (endMs > startMs) {
            "${com.aotv.app.data.clips.ClipDownload.formatClipTime(startMs)}–${com.aotv.app.data.clips.ClipDownload.formatClipTime(endMs)}"
        } else ""
        return ClipDownloadEntry(
            id = id,
            title = title.substringBefore(" (").ifBlank { title },
            intervalLabel = interval,
            posterUrl = posterUrl,
            status = status,
            percent = 0,
            localUri = localUri,
            sourceUrl = sourceUrl,
            tmdbId = tmdbId,
            type = type,
            lastEpisode = lastEpisode,
        )
    }

    /** Há um torrent baixando/tocando agora (p/ o aviso na tela — Lote 18 item 4). */
    fun isTorrentDownloading(): Boolean = Torrents.ifStarted()?.hasActiveDownload() == true

    /** Retoma um download parado ("failed"/"queued") — re-download do zero (ver [DownloadRepository.resumeDownload]). */
    fun resume(entity: DownloadEntity) {
        viewModelScope.launch {
            // Lote 1 pós-v1.45.0 (v1.46.0 — Frente B, item 2): o lote e o
            // inteligente baixam por [ClipDownloader.downloadFull], NÃO
            // pelo DownloadManager — o "Retomar" normal jogaria a URL HLS
            // no DownloadManager, que baixaria a playlist .m3u8 como se
            // fosse .mp4. ":full" e ":smart" retomam pela fila do lote.
            if (ClipDownload.isFullId(entity.id) || ClipDownload.isSmartId(entity.id)) {
                if (!batchManager.retry(entity)) {
                    _message.value = "Não deu para retomar: a fonte deste download não foi guardada."
                }
                refresh()
                return@launch
            }
            repo.resumeDownload(entity)
            refresh()
        }
    }

    /** Progresso atual (0..100) por id dos downloads em andamento. */
    suspend fun queryProgress(): Map<String, Int> = repo.progress()

    fun delete(entity: DownloadEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }

    // LOTE mpv 4 — FRENTE 2 (v1.39.0): exportar o trecho e baixar o vídeo
    // inteiro. O downloader/exporter resolvem o DAO e as prefs por conta
    // própria (mesmo padrão do ClipDownloader no player); o progresso do
    // vídeo inteiro vive aqui porque o DownloadManager não o conhece
    // (downloadId 0, como o clipe).
    private val clipDownloader by lazy { ClipDownloader(context) }
    private val clipExporter by lazy { ClipExporter(context) }

    // LOTE 1 pós-v1.45.0 (v1.46.0 — Frente B, itens 2 e 3): download em
    // lote + inteligente. O gerente é singleton por applicationContext
    // (ver [BatchDownloadManager.get]) — a MESMA instância (e a mesma
    // fila) que a tela de detalhes usa p/ enfileirar episódios; sem
    // isso, o download começado no detalhe morreria ao voltar p/ cá.
    private val batchManager by lazy { BatchDownloadManager.get(context) }

    /** Progresso (0..100) por id dos episódios do lote — a tela mescla com o do DownloadManager e o do vídeo inteiro. */
    val batchProgress: StateFlow<Map<String, Int>> = batchManager.batchProgress

    /** Mensagem curta do lote/inteligente p/ toast — a tela consome como [message]. */
    val batchMessage: StateFlow<String?> = batchManager.message

    /** A tela chama depois de mostrar a mensagem do lote, p/ não repetir no retorno. */
    fun consumeBatchMessage() {
        batchManager.consumeMessage()
    }

    /** Download inteligente ligado? (desligado por padrão — ver PlatformPrefs). */
    val smartDownload: StateFlow<Boolean> = prefs.smartDownloadFlow

    /** Download inteligente só em Wi-Fi? (ligado por padrão). */
    val smartDownloadWifiOnly: StateFlow<Boolean> = prefs.smartDownloadWifiOnlyFlow

    /** Liga/desliga o download inteligente (o toggle mora na aba Downloads). */
    fun setSmartDownload(value: Boolean) {
        prefs.smartDownload = value
    }

    /** Liga/desliga o "só Wi-Fi" do download inteligente. */
    fun setSmartDownloadWifiOnly(value: Boolean) {
        prefs.smartDownloadWifiOnly = value
    }

    /** Progresso (0..100) do vídeo inteiro por id (":full") — a tela mescla com [queryProgress]. */
    private val _fullProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val fullProgress: StateFlow<Map<String, Int>> = _fullProgress.asStateFlow()

    /** Mensagem curta p/ a tela mostrar em toast (null = nada pendente). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A tela chama depois de mostrar a mensagem, p/ não repetir no retorno. */
    fun consumeMessage() {
        _message.value = null
    }

    /** Compartilha o trecho concluído no share sheet do sistema. */
    fun shareClip(entry: ClipDownloadEntry) {
        viewModelScope.launch {
            if (entry.localUri.isBlank()) {
                _message.value = "O trecho ainda não tem arquivo para compartilhar."
                return@launch
            }
            when (val result = clipExporter.shareClip(entry.localUri, entry.title)) {
                ClipExportResult.Shared -> { /* o share sheet abriu — nada a avisar */ }
                ClipExportResult.CopiedToFolder -> { /* não acontece no share */ }
                ClipExportResult.NoFolderConfigured -> { /* não acontece no share */ }
                is ClipExportResult.Failed -> _message.value = result.message
            }
        }
    }

    /** Copia o trecho concluído p/ a pasta de Downloads escolhida (sem mover o original). */
    fun copyClipToFolder(entry: ClipDownloadEntry) {
        viewModelScope.launch {
            if (entry.localUri.isBlank()) {
                _message.value = "O trecho ainda não tem arquivo para exportar."
                return@launch
            }
            when (val result = clipExporter.copyClipToFolder(entry.localUri, entry.title)) {
                ClipExportResult.Shared -> { /* não acontece na cópia */ }
                ClipExportResult.CopiedToFolder -> _message.value = "Trecho copiado para a pasta de Downloads."
                ClipExportResult.NoFolderConfigured ->
                    _message.value = "Escolha a pasta de Downloads em Configurações → Downloads para exportar o trecho."
                is ClipExportResult.Failed -> _message.value = result.message
            }
        }
    }

    /**
     * Compartilha um download concluído no share sheet do sistema (Lote
     * de correções v1.43.0 — Frente 1, generalizado na v1.44.0 — Frente 1:
     * antes só a entrada ":full:" chegava aqui; agora TODO download
     * concluído com arquivo local passa por aqui — ver DownloadsScreen).
     * Reutiliza o MESMO caminho do trecho ([ClipExporter.shareClip] —
     * FileProvider via cópia p/ o cache): o exporter é genérico
     * (localUri + título) e o arquivo do download é .mp4 ou .ts como o
     * do trecho — o mime vai como `video/mp4` (.mp4) ou `video/*` →
     * `*/*` (.ts). Sem arquivo local (download em andamento), avisa em
     * vez de quebrar.
     */
    fun shareDownload(entity: DownloadEntity) {
        viewModelScope.launch {
            if (entity.localUri.isBlank()) {
                _message.value = "Este download ainda não tem arquivo para compartilhar."
                return@launch
            }
            when (val result = clipExporter.shareClip(entity.localUri, entity.title)) {
                ClipExportResult.Shared -> { /* o share sheet abriu — nada a avisar */ }
                ClipExportResult.CopiedToFolder -> { /* não acontece no share */ }
                ClipExportResult.NoFolderConfigured -> { /* não acontece no share */ }
                is ClipExportResult.Failed -> _message.value = result.message
            }
        }
    }

    /**
     * Copia um download concluído (Lote de correções v1.43.0 — Frente 1,
     * generalizado na v1.44.0 — Frente 1: antes só a entrada ":full:")
     * p/ a pasta de Downloads escolhida (sem mover o original). Mesmo
     * caminho do trecho ([ClipExporter.copyClipToFolder] — cópia SAF): o
     * exporter é genérico (localUri + título). Sem arquivo local, avisa
     * em vez de quebrar; sem pasta escolhida, diz onde escolher.
     */
    fun copyDownloadToFolder(entity: DownloadEntity) {
        viewModelScope.launch {
            if (entity.localUri.isBlank()) {
                _message.value = "Este download ainda não tem arquivo para exportar."
                return@launch
            }
            when (val result = clipExporter.copyClipToFolder(entity.localUri, entity.title)) {
                ClipExportResult.Shared -> { /* não acontece na cópia */ }
                ClipExportResult.CopiedToFolder -> _message.value = "Copiado para a pasta de Downloads."
                ClipExportResult.NoFolderConfigured ->
                    _message.value = "Escolha a pasta de Downloads em Configurações → Downloads para exportar."
                is ClipExportResult.Failed -> _message.value = result.message
            }
        }
    }

    /**
     * Apaga um trecho (clipe) — Lote do remodel v1.44.0 — Frente 1: o
     * clipe vive como [DownloadEntity] comum com ":clip:" no id (ver
     * [ClipDownload]), então a remoção é o MESMO caminho do download
     * normal ([repo.delete]): cancela no DownloadManager (downloadId 0,
     * sem efeito), apaga o arquivo local (file:// por File, content://
     * pelo ContentResolver) e remove a linha do DAO — e o Flow de
     * [repo.all] tira a entrada da lista de clipes sozinho. A tela pede
     * confirmação antes (diálogo `pendingDeleteClip`, mesmo padrão do
     * `pendingDelete` dos downloads normais).
     */
    fun deleteClip(entry: ClipDownloadEntry) {
        viewModelScope.launch { repo.delete(entry.toDownloadEntity()) }
    }

    /**
     * Baixa o vídeo INTEIRO da fonte do clipe (HLS = todos os segmentos,
     * MP4 = arquivo direto — ver [ClipDownloader.downloadFull]). A entrada
     * ":full:" aparece na lista principal de Downloads com "(completo)" no
     * título; o progresso vem por [fullProgress] e a tela mescla com o do
     * DownloadManager. O progresso é marcado 0 NA HORA (antes do
     * primeiro segmento): a linha do clipe mostra "Baixando vídeo
     * inteiro…" na aba Completos — sem isso o toque parecia não fazer
     * nada, porque a entrada ":full:" mora na aba Restos. Toque duplo
     * não recomeça o download (a % já está na linha do clipe).
     *
     * Sem `sourceUrl` no clipe, NÃO re-resolve a fonte: o clipe só guarda
     * tmdbId/tipo/episódio — sem o addon nem o rótulo da fonte que o
     * gerou — e a resolução de fontes vive no player (lista de fontes +
     * preferência de addon/qualidade). A mensagem diz exatamente o que
     * falta e o caminho que funciona: cortar um trecho novo da mesma
     * fonte grava a URL e o "Vídeo inteiro" passa a valer.
     */
    fun downloadFullClip(entry: ClipDownloadEntry) {
        viewModelScope.launch {
            if (entry.sourceUrl.isBlank()) {
                _message.value = "Trecho antigo sem a fonte salva (sem URL nem addon de origem): corte um trecho novo da mesma fonte e toque em “Vídeo inteiro” nele."
                return@launch
            }
            val fullId = ClipDownload.fullId(entry.tmdbId, entry.type, entry.lastEpisode)
            // Já baixando (toque duplo): não recomeça — o progresso já
            // está na linha do clipe.
            if (_fullProgress.value.containsKey(fullId)) {
                _message.value = "O vídeo inteiro já está baixando."
                return@launch
            }
            // Progresso 0 NA HORA: a linha do clipe mostra "Baixando vídeo
            // inteiro…" antes do primeiro segmento (ver o KDoc acima).
            _fullProgress.value = _fullProgress.value + (fullId to 0)
            _message.value = "Baixando o vídeo inteiro…"
            val result = clipDownloader.downloadFull(
                tmdbId = entry.tmdbId,
                type = entry.type,
                title = entry.title,
                posterUrl = entry.posterUrl,
                lastEpisode = entry.lastEpisode,
                streamUrl = entry.sourceUrl,
                onProgress = { downloaded, total ->
                    if (total > 0) {
                        val percent = ((downloaded * 100) / total).coerceIn(0, 100)
                        // Só atualiza quando a % muda, p/ não recompor a lista a cada segmento.
                        if (_fullProgress.value[fullId] != percent) {
                            _fullProgress.value = _fullProgress.value + (fullId to percent)
                        }
                    }
                },
            )
            _fullProgress.value = _fullProgress.value - fullId
            _message.value = when (result) {
                is ClipResult.Done -> "Vídeo inteiro salvo em Downloads: ${result.displayTitle}"
                is ClipResult.Failed -> "Não deu para baixar o vídeo inteiro: ${result.message}"
                is ClipResult.Unsupported -> result.reason
                ClipResult.BlockedWifi -> "“Só Wi-Fi” ligado: conecte no Wi-Fi para baixar o vídeo inteiro."
            }
        }
    }
}
