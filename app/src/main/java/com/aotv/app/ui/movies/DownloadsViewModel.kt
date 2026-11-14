package com.aotv.app.ui.movies

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
    private val prefs: PlatformPrefs,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> =
        repo.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    }

    /** Progresso atual (0..100) por id dos downloads em andamento. */
    suspend fun queryProgress(): Map<String, Int> = repo.progress()

    fun delete(entity: DownloadEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }
}
