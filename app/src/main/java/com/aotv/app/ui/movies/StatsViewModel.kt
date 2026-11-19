// ─── ui/movies/StatsViewModel.kt (Lote 23, v1.33.0) ─────────────────────────
// ViewModel da tela de Estatísticas: lê o histórico do perfil ativo
// (MovieHistoryDao.statsRows + WatchedEpisodeDao), agrega em StatsRecap e
// exporta o recap como PNG (MediaStore, Pictures/AOTV) p/ compartilhar.
// Tudo local: nada sai do aparelho.

package com.aotv.app.ui.movies

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.dao.MovieHistoryDao
import com.aotv.app.data.local.dao.WatchedEpisodeDao
import com.aotv.app.data.stats.StatsSummary
import com.aotv.app.data.stats.computeStats
import com.aotv.app.data.stats.statsPeriodLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val historyDao: MovieHistoryDao,
    private val watchedDao: WatchedEpisodeDao,
    private val prefs: PlatformPrefs,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class StatsUiState(
        val summary: StatsSummary? = null,
        val period: String = "30d",
        val profileName: String = "",
        val loading: Boolean = true,
        val exporting: Boolean = false,
        val exportMessage: String? = null,
        val exportedUri: Uri? = null,
    )

    private val _uiState = MutableStateFlow(StatsUiState(period = prefs.statsPeriod))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Troca o período (grava a pref) e recarrega as estatísticas. */
    fun setPeriod(period: String) {
        prefs.statsPeriod = period
        load()
    }

    fun clearMessage() = _uiState.update { it.copy(exportMessage = null) }

    /** A tela já abriu o compartilhamento: limpa o Uri p/ não reabrir. */
    fun consumeExportedUri() = _uiState.update { it.copy(exportedUri = null) }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val period = prefs.statsPeriod
            val profileId = prefs.profileId.value
            val profileName = prefs.profile.name
            val rows = withContext(Dispatchers.IO) { historyDao.statsRows(profileId) }
            val episodes = withContext(Dispatchers.IO) { watchedDao.getAllForProfile(profileId) }
            val summary = computeStats(rows, episodes, period)
            _uiState.update {
                it.copy(summary = summary, period = period, profileName = profileName, loading = false)
            }
        }
    }

    /**
     * Desenha o recap em Bitmap e salva como PNG em Pictures/AOTV
     * (MediaStore — sem FileProvider e sem permissão de escrita no
     * Android 10+). O Uri salvo vai p/ o estado e a tela abre o
     * compartilhamento. Sem histórico no período, exporta mesmo assim
     * (o recap mostra os zeros com "Sem dados neste período").
     */
    fun exportRecap() {
        val summary = _uiState.value.summary ?: return
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, exportMessage = null, exportedUri = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = drawRecapBitmap(summary, statsPeriodLabel(_uiState.value.period), _uiState.value.profileName)
                    val uri = savePng(appContext, bitmap)
                        ?: throw IllegalStateException("Não foi possível salvar a imagem.")
                    bitmap.recycle()
                    uri
                }
            }
            result.onSuccess { uri ->
                _uiState.update { it.copy(exporting = false, exportedUri = uri, exportMessage = "Recap salvo em Pictures/AOTV.") }
            }.onFailure { e ->
                _uiState.update { it.copy(exporting = false, exportMessage = e.message ?: "Não foi possível exportar o recap.") }
            }
        }
    }

    private fun savePng(context: Context, bitmap: Bitmap): Uri? {
        val name = "aotv-recap-${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            // No Android 9 e anteriores o RELATIVE_PATH é ignorado e o
            // arquivo cai no local padrão de imagens (sem erro).
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AOTV")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return uri
    }
}
