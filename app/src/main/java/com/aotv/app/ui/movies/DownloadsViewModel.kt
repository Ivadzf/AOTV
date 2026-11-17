package com.aotv.app.ui.movies

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) {
    /** O clipe como download comum, p/ tocar o arquivo local pelo mesmo player. */
    fun toDownloadEntity(): DownloadEntity = DownloadEntity(
        id = id,
        tmdbId = 0,
        type = "MOVIE",
        title = if (intervalLabel.isNotBlank()) "$title ($intervalLabel)" else title,
        posterUrl = posterUrl,
        sourceUrl = "",
        localUri = localUri,
        lastEpisode = "",
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
        )
    }

    /** Há um torrent baixando/tocando agora (p/ o aviso na tela — Lote 18 item 4). */
    fun isTorrentDownloading(): Boolean = Torrents.ifStarted()?.hasActiveDownload() == true

    /** Retoma um download parado ("failed"/"queued") — re-download do zero (ver [DownloadRepository.resumeDownload]). */
    fun resume(entity: DownloadEntity) {
        viewModelScope.launch {
            repo.resumeDownload(entity)
            refresh()
        }
    }

    /** Progresso atual (0..100) por id dos downloads em andamento. */
    suspend fun queryProgress(): Map<String, Int> = repo.progress()

    fun delete(entity: DownloadEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }
}
