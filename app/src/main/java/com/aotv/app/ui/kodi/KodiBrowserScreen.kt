package com.aotv.app.ui.kodi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.kodi.KodiPluginRunner
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Navegação de um addon Kodi (v1.2, Fase 2).
 *
 * Mostra o menu do addon e navega nas pastas; ao tocar um item jogável
 * o plugin resolve e a URL vai p/ o player do app (onPlay). O botão
 * voltar desempilha um nível; na raiz, volta p/ a tela anterior.
 * A URL resolvida nunca aparece aqui — ela vai direto ao ExoPlayer.
 */
@Composable
fun KodiBrowserScreen(
    onBack: () -> Unit,
    onPlay: (url: String, title: String, addonId: String) -> Unit,
    viewModel: KodiBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.playEvents.collect { onPlay(it.url, it.title, viewModel.addonId) }
    }
    BackHandler(enabled = state.canGoBack) { viewModel.goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .topInsetsPadding()
            .padding(horizontal = pagePadding(), vertical = 8.dp),
    ) {
        IconButton(
            onClick = { if (!viewModel.goBack()) onBack() },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
        }
        PageHeader(
            title = state.title.ifBlank { state.addonName },
            sub = state.addonName,
        )

        when {
            state.loading -> {
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Accent)
                Spacer(Modifier.height(12.dp))
                Text(
                    state.loadingLabel,
                    color = TextTertiary, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    "A primeira abertura demora alguns segundos (o Python e o addon iniciam).",
                    color = TextTertiary, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                )
            }
            state.error != null -> {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Erro: ${state.error}",
                    color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BackgroundCard)
                        .padding(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.retry() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tentar novamente")
                }
            }
            state.empty -> {
                Spacer(Modifier.height(16.dp))
                Text(
                    "O addon não devolveu itens para esta ação.",
                    color = TextSecondary, fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Algumas ações abrem diálogos do addon que ainda não existem no app (ex.: Configurações).",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                if (state.diag.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Diagnóstico (manda o print):",
                        color = TextTertiary, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.diag,
                        color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundCard)
                            .padding(12.dp),
                    )
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.url + it.label }) { item ->
                        KodiBrowserRow(
                            item = item,
                            enabled = !state.loading,
                            onClick = { viewModel.openItem(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KodiBrowserRow(
    item: KodiPluginRunner.KodiItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = item.thumbUrl
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundCard),
            )
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundCard),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.isFolder) Icons.Default.Folder else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = TextPrimary, fontSize = 15.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = item.plot
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    color = TextSecondary, fontSize = 13.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
