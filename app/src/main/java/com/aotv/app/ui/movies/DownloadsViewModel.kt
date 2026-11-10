package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> =
        repo.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Atualiza status (em andamento → done/failed) consultando o DownloadManager. */
    fun refresh() {
        viewModelScope.launch { repo.refreshStatuses() }
    }

    /** Progresso atual (0..100) por id dos downloads em andamento. */
    suspend fun queryProgress(): Map<String, Int> = repo.progress()

    fun delete(entity: DownloadEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }
}
