package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.ui.theme.*

/**
 * Tela "Downloads" do modo Filmes & Séries.
 * Lista os arquivos .mp4 baixados (DownloadRepository). Itens "done" tocam o
 * arquivo local pelo mesmo player; "downloading" mostram progresso; "failed" erro.
 * Long-press / botão lixeira remove o download.
 */
@Composable
fun DownloadsScreen(
    onPlayLocal: (DownloadEntity) -> Unit,
) {
    val vm: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val list by vm.downloads.collectAsStateWithLifecycle()

    // Progresso (0..100) por id — atualizado em loop enquanto houver download em andamento.
    var progress by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(list) {
        vm.refresh()
        while (list.any { it.status == "downloading" }) {
            progress = vm.queryProgress()
            vm.refresh() // vira done/failed quando termina (Flow re-dispara este efeito)
            kotlinx.coroutines.delay(1000)
        }
    }

    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    pendingDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remover download?") },
            text = { Text(d.title) },
            confirmButton = {
                TextButton(onClick = { vm.delete(d); pendingDelete = null }) {
                    Text("Remover", color = LiveRed)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        com.aotv.app.ui.common.PageHeader(
            "Downloads",
            sub = if (list.isEmpty()) "Para assistir sem internet" else "${list.size} no aparelho",
        )

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.aotv.app.ui.common.AotvEmptyState(
                    icon = Icons.Default.Download,
                    title = "Nenhum download",
                    message = "Toque no ícone de download em uma fonte .mp4 do filme ou episódio para baixá-lo e assistir offline aqui.",
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 8.dp) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { d ->
                DownloadRow(
                    d = d,
                    percent = progress[d.id] ?: 0,
                    onClick = { if (d.status == "done") onPlayLocal(d) },
                    onDelete = { pendingDelete = d },
                )
            }
        }
    }
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = calculateTopPadding() + other.calculateTopPadding(),
    end = calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
)

@Composable
private fun DownloadRow(
    d: DownloadEntity,
    percent: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = d.posterUrl,
            contentDescription = d.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
        )
        Column(Modifier.weight(1f)) {
            Text(
                d.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                maxLines = 2,
            )
            val sub = when (d.status) {
                "done"        -> if (d.lastEpisode.isNotBlank()) d.lastEpisode else "Pronto p/ assistir"
                "downloading" -> if (percent > 0) "Baixando… $percent%" else "Baixando…"
                else          -> "Falha no download"
            }
            val subColor = when (d.status) {
                "done" -> Ok
                "downloading" -> Accent
                else -> DangerText
            }
            Text(
                sub,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = subColor,
                modifier = Modifier.padding(top = 3.dp),
            )
            // Barra de progresso (só enquanto baixa).
            if (d.status == "downloading") {
                LinearProgressIndicator(
                    progress = { if (percent > 0) percent / 100f else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
        if (d.status == "done") {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, "Assistir", tint = TextOnLight, modifier = Modifier.size(22.dp))
            }
        } else if (d.status == "downloading") {
            // Anel determinado quando há %, indeterminado enquanto o total é desconhecido.
            if (percent > 0) {
                CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Accent)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Accent)
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Delete, "Remover", tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
