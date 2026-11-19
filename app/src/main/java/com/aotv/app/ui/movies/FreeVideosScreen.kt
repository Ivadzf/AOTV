package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.FreeCatalog
import com.aotv.app.data.remote.FreeCatalogsRepository
import com.aotv.app.data.remote.FreeVideoItem
import com.aotv.app.data.remote.freeCatalogOf
import com.aotv.app.data.remote.freeVideoDurationLabel
import com.aotv.app.ui.common.AotvEmptyState
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Lote 25 (v1.35.0) — Frente D, item 11: vídeos grátis ───────────────────
// Pseudo-catálogos opcionais (CCC, Blender, PeerTube) no Descobrir. O
// catálogo escolhido é por perfil ([PlatformPrefs.freeCatalog]). Falha
// graciosa: sem resposta da fonte, a tela mostra o estado vazio — nunca
// crash. O toque toca o link direto (mp4/HLS); o CCC resolve a gravação
// na hora do toque (a lista não traz o link).

@HiltViewModel
class FreeVideosViewModel @Inject constructor(
    private val catalogs: FreeCatalogsRepository,
    private val prefs: PlatformPrefs,
) : ViewModel() {

    data class UiState(
        val catalog: FreeCatalog = FreeCatalog.CCC,
        val items: List<FreeVideoItem> = emptyList(),
        val loading: Boolean = true,
        val resolvingId: String? = null,
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState(catalog = freeCatalogOf(prefs.freeCatalog)))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectCatalog(catalog: FreeCatalog) {
        if (catalog == _uiState.value.catalog) return
        prefs.freeCatalog = catalog.id
        _uiState.update { it.copy(catalog = catalog) }
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        val catalog = _uiState.value.catalog
        _uiState.update { it.copy(loading = true, items = emptyList(), message = null) }
        loadJob = viewModelScope.launch {
            val items = catalogs.list(catalog)
            _uiState.update {
                it.copy(
                    loading = false,
                    items = items,
                    message = if (items.isEmpty()) "A fonte não respondeu agora. Tente outro catálogo ou mais tarde." else null,
                )
            }
        }
    }

    /** Link direto do item (resolve o CCC na hora). null = sem vídeo tocável. */
    suspend fun resolve(item: FreeVideoItem): String? {
        _uiState.update { it.copy(resolvingId = item.id) }
        val url = catalogs.resolveStream(item)
        _uiState.update { it.copy(resolvingId = null, message = if (url == null) "Não deu para abrir este vídeo." else it.message) }
        return url
    }
}

/** Tela "Vídeos grátis": catálogo no topo, lista de vídeos; o toque toca. */
@Composable
fun FreeVideosScreen(
    onPlayUrl: (String, String) -> Unit,
    onBack: () -> Unit,
    viewModel: FreeVideosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Background)) {
        StreamingTopBar(onBack = onBack, title = "Vídeos grátis", sub = "CCC · Blender · PeerTube — sem conta")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FreeCatalog.values().forEach { catalog ->
                FilterChipPill(catalog.label, catalog == state.catalog, onClick = { viewModel.selectCatalog(catalog) })
            }
        }
        state.message?.let { message ->
            if (state.items.isEmpty() && !state.loading) {
                AotvEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Nada por aqui",
                    message = message,
                    action = {
                        androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                            Text("Tentar novamente", color = Accent, fontWeight = FontWeight.Bold)
                        }
                    },
                )
            } else {
                Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { item -> "${item.source}:${item.id}" }) { item ->
                FreeVideoRow(
                    item = item,
                    resolving = state.resolvingId == item.id,
                    onClick = {
                        scope.launch {
                            val url = viewModel.resolve(item)
                            if (url != null) onPlayUrl(url, item.title)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FreeVideoRow(item: FreeVideoItem, resolving: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.thumbUrl.isNotBlank()) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)).background(Background),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val meta = listOf(item.source, item.subtitle, freeVideoDurationLabel(item.durationSec))
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (resolving) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}
