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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.clips.ClipDownload
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.data.repository.OrphanFile
import com.aotv.app.ui.theme.*

/**
 * Tela "Downloads" do modo Filmes & Séries.
 * Lista os arquivos .mp4 baixados (DownloadRepository). Itens "done" tocam o
 * arquivo local pelo mesmo player; "downloading" mostram progresso; "failed" erro.
 * Long-press / botão lixeira remove o download.
 *
 * Abas (Lote 18 item 4, v1.28.0): "Completos" (downloads concluídos) e
 * "Restos" (downloads incompletos — baixando, na fila ou com falha — +
 * restos de arquivos .mp4 sem dono na pasta). "failed"/"queued" têm
 * "Retomar" (re-download do zero — ver DownloadRepository.resumeDownload).
 * Trechos (clipes, item 10) entram na mesma lista: a linha do clipe é a
 * mesma casca da linha de download (título + intervalo no subtítulo) —
 * ver [ClipDownloadRow] e o slot [DownloadsViewModel.clipEntries].
 * Com um torrent baixando, a tela avisa que sair do app interrompe o
 * download (o aviso de saída em si é a notificação de TorrentExitWarning).
 */
private enum class DownloadsTab { COMPLETED, LEFTOVERS }
@Composable
fun DownloadsScreen(
    onPlayLocal: (DownloadEntity) -> Unit,
) {
    val vm: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val list by vm.downloads.collectAsStateWithLifecycle()
    // "Só Wi-Fi" e fila (Lote 5, v1.14.0): downloads "queued" esperam o Wi-Fi.
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val queuedCount by vm.queuedCount.collectAsStateWithLifecycle()
    val mobileLimitReached by vm.mobileLimitReached.collectAsStateWithLifecycle()
    val mobileMonthBytes by vm.mobileMonthBytes.collectAsStateWithLifecycle()
    // Restos de arquivos + clipes (Lote 18 item 4 e item 10 — aba Restos / mesma lista).
    val orphans by vm.orphans.collectAsStateWithLifecycle()
    val clips by vm.clipEntries.collectAsStateWithLifecycle()
    // Progresso do vídeo inteiro (Lote mpv 4 — Frente 2) + mensagens
    // (exportação e vídeo inteiro chegam por toast).
    val fullProgress by vm.fullProgress.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let { text ->
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    // Abas Completos/Restos (Lote 18 item 4): a aba sobrevive à recomposição.
    var tab by remember { mutableStateOf(DownloadsTab.COMPLETED) }
    val completed = list.filter { it.status == "done" }
    val leftovers = list.filter { it.status != "done" }
    val completedClips = clips.filter { it.status == "done" }
    val leftoverClips = clips.filter { it.status != "done" }

    // Torrent baixando (Lote 18 item 4): avisa na tela enquanto houver um
    // torrent ativo — sair do app interrompe o download (o aviso de saída
    // em si é a notificação de TorrentExitWarning, no onStop da Activity).
    var torrentActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            torrentActive = vm.isTorrentDownloading()
            kotlinx.coroutines.delay(5_000)
        }
    }

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

    // Remover o trecho (Lote do remodel v1.44.0 — Frente 1): mesmo padrão
    // do `pendingDelete` acima — confirmação antes de apagar o arquivo e
    // a entrada do clipe (ver [DownloadsViewModel.deleteClip]).
    var pendingDeleteClip by remember { mutableStateOf<ClipDownloadEntry?>(null) }
    pendingDeleteClip?.let { clip ->
        AlertDialog(
            onDismissRequest = { pendingDeleteClip = null },
            title = { Text("Remover trecho?") },
            text = { Text(clip.title) },
            confirmButton = {
                TextButton(onClick = { vm.deleteClip(clip); pendingDeleteClip = null }) {
                    Text("Remover", color = LiveRed)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteClip = null }) { Text("Cancelar") } },
        )
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        com.aotv.app.ui.common.PageHeader(
            "Downloads",
            sub = if (list.isEmpty()) "Para assistir sem internet" else "${list.size} no aparelho",
        )

        // Aviso da fila "Só Wi-Fi" (Lote 5): downloads parados esperando o Wi-Fi.
        if (queuedCount > 0) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = com.aotv.app.ui.common.pagePadding()).padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.WifiOff, null, tint = Accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Aguardando Wi-Fi — $queuedCount na fila",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
                    )
                    Text(
                        if (wifiOnly) "Com \"Só Wi-Fi\" ligado, os downloads só começam em Wi-Fi. Desliga em Configurações → Downloads para baixar em dados móveis."
                        else "O \"Só Wi-Fi\" está desligado: toque em \"Tentar agora\" para começar os downloads.",
                        fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TextButton(onClick = { vm.refresh() }) { Text("Tentar agora", color = Accent) }
            }
        }

        // Aviso do limite de dados móveis (Lote 5): só aviso, nada é bloqueado.
        if (mobileLimitReached) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = com.aotv.app.ui.common.pagePadding()).padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Limite de dados móveis atingido (${MobileDataUsage.formatBytes(mobileMonthBytes)} este mês). É só um aviso: nada é bloqueado. Muda o limite em Configurações → Downloads.",
                    fontSize = 13.sp, color = DangerText, lineHeight = 18.sp,
                )
            }
        }

        // Aviso de torrent baixando (Lote 18 item 4): sair do app interrompe
        // o download e o arquivo temporário é apagado. Só aparece com um
        // torrent ativo — sem torrent, a tela não fala disso.
        if (torrentActive) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = com.aotv.app.ui.common.pagePadding()).padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Warning, null, tint = Accent, modifier = Modifier.size(22.dp))
                Text(
                    "Torrent baixando — sair do app interrompe o download e o arquivo temporário é apagado.",
                    fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp,
                )
            }
        }

        // Abas Completos/Restos (Lote 18 item 4).
        TabRow(
            selectedTabIndex = tab.ordinal,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = com.aotv.app.ui.common.pagePadding()).padding(top = 10.dp),
            containerColor = Background,
            contentColor = Accent,
        ) {
            Tab(
                selected = tab == DownloadsTab.COMPLETED,
                onClick = { tab = DownloadsTab.COMPLETED },
                text = { Text("Completos (${completed.size + completedClips.size})", color = TextPrimary, fontSize = 14.sp) },
            )
            Tab(
                selected = tab == DownloadsTab.LEFTOVERS,
                onClick = { tab = DownloadsTab.LEFTOVERS },
                text = { Text("Restos (${leftovers.size + leftoverClips.size + orphans.size})", color = TextPrimary, fontSize = 14.sp) },
            )
        }

        if (tab == DownloadsTab.COMPLETED && completed.isEmpty() && completedClips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.aotv.app.ui.common.AotvEmptyState(
                    icon = Icons.Default.Download,
                    title = "Nenhum download concluído",
                    message = "Toque no ícone de download em uma fonte .mp4 do filme ou episódio para baixá-lo e assistir offline aqui.",
                )
            }
            return@Column
        }

        if (tab == DownloadsTab.LEFTOVERS && leftovers.isEmpty() && leftoverClips.isEmpty() && orphans.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.aotv.app.ui.common.AotvEmptyState(
                    icon = Icons.Default.Download,
                    title = "Nada por aqui",
                    message = "Sem downloads incompletos nem restos de arquivos. Downloads com falha ou na fila aparecem aqui, com a opção de retomar.",
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 8.dp) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == DownloadsTab.COMPLETED) {
                items(completed, key = { it.id }) { d ->
                    DownloadRow(
                        d = d,
                        // O vídeo inteiro (":full:") não passa pelo
                        // DownloadManager: o progresso dele vem do ViewModel.
                        percent = progress[d.id] ?: fullProgress[d.id] ?: 0,
                        onClick = { if (d.status == "done") onPlayLocal(d) },
                        onDelete = { pendingDelete = d },
                        onResume = null,
                        // Lote do remodel v1.44.0 — Frente 1: Compartilhar
                        // e Salvar na pasta valem p/ TODO download
                        // concluído com arquivo local — não só p/ a entrada
                        // ":full:" (o card do Davi, "The Mentalist T6:E4",
                        // é um download NORMAL e ficava sem os botões
                        // porque a v1.43.0 só passava as ações quando
                        // `isFullId(d.id)`). Sem arquivo (em andamento) os
                        // botões somem em vez de quebrar.
                        onShare = if (d.status == "done" && d.localUri.isNotBlank()) ({ vm.shareDownload(d) }) else null,
                        onSaveToFolder = if (d.status == "done" && d.localUri.isNotBlank()) ({ vm.copyDownloadToFolder(d) }) else null,
                    )
                }
                items(completedClips, key = { "clip:${it.id}" }) { clip ->
                    ClipDownloadRow(
                        clip = clip,
                        onClick = { if (clip.localUri.isNotBlank()) onPlayLocal(clip.toDownloadEntity()) },
                        // Lixeira no card do trecho (Lote do remodel
                        // v1.44.0 — Frente 1): confirmação antes de apagar
                        // o arquivo e a entrada (ver `pendingDeleteClip`).
                        onDelete = { pendingDeleteClip = clip },
                        onShare = { vm.shareClip(clip) },
                        onSaveToFolder = { vm.copyClipToFolder(clip) },
                        onDownloadFull = { vm.downloadFullClip(clip) },
                        // Vídeo inteiro em andamento (Lote v1.42.0 — Frente 1):
                        // a % vive no ViewModel com a chave ":full:" do clipe
                        // — a linha mostra o progresso aqui, na aba Completos.
                        fullDownloadPercent = fullProgress[ClipDownload.fullId(clip.tmdbId, clip.type, clip.lastEpisode)],
                    )
                }
            } else {
                items(leftovers, key = { it.id }) { d ->
                    DownloadRow(
                        d = d,
                        percent = progress[d.id] ?: fullProgress[d.id] ?: 0,
                        onClick = { if (d.status == "done") onPlayLocal(d) },
                        onDelete = { pendingDelete = d },
                        // Retomar (Lote 18 item 4): "failed" e "queued" começam
                        // de novo; "downloading" já está andando.
                        onResume = if (d.status == "failed" || d.status == "queued") ({ vm.resume(d) }) else null,
                    )
                }
                items(leftoverClips, key = { "clip:${it.id}" }) { clip ->
                    ClipDownloadRow(
                        clip = clip,
                        onClick = null,
                        // Lixeira também na aba Restos (Lote do remodel
                        // v1.44.0 — Frente 1): o trecho incompleto também
                        // pode ser apagado (arquivo parcial + entrada).
                        onDelete = { pendingDeleteClip = clip },
                    )
                }
                // Restos de arquivos .mp4 sem dono na pasta (Lote 18 item 4).
                if (orphans.isNotEmpty()) {
                    item(key = "orphans-header") {
                        Text(
                            "Restos de arquivos (${orphans.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(orphans, key = { "orphan:${it.path}" }) { orphan ->
                        OrphanRow(
                            orphan = orphan,
                            onDelete = { vm.deleteOrphan(orphan.path) },
                        )
                    }
                }
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
    onResume: (() -> Unit)?,
    /** Lote do remodel v1.44.0 — Frente 1: TODO download concluído com arquivo local preenche [onShare] e [onSaveToFolder] (ver a chamada na aba Completos); antes (v1.43.0) só a entrada ":full:" preenchia. */
    onShare: (() -> Unit)? = null,
    onSaveToFolder: (() -> Unit)? = null,
) {
    val sub = when (d.status) {
        "done"        -> if (d.lastEpisode.isNotBlank()) d.lastEpisode else "Pronto p/ assistir"
        "downloading" -> if (percent > 0) "Baixando… $percent%" else "Baixando…"
        "queued"      -> "Na fila — aguardando Wi-Fi"
        else          -> "Falha no download"
    }
    val subColor = when (d.status) {
        "done" -> Ok
        "downloading" -> Accent
        "queued" -> Accent
        else -> DangerText
    }
    DownloadRowShell(
        posterUrl = d.posterUrl,
        title = d.title,
        subtitle = sub,
        subtitleColor = subColor,
        showProgress = d.status == "downloading",
        percent = percent,
        showPlay = d.status == "done",
        showDownloadingRing = d.status == "downloading",
        onClick = onClick,
        onResume = onResume,
        onDelete = onDelete,
        onShare = onShare,
        onSaveToFolder = onSaveToFolder,
    )
}

/**
 * Linha de um trecho (clipe) baixado (Lote 18 item 10): a MESMA casca da
 * linha de download — título + intervalo no subtítulo (ex.: "Filme
 * (12:34–15:20)"). O clipe concluído toca o arquivo local pelo mesmo
 * player; sem arquivo local, não há o que tocar. A lixeira ([onDelete])
 * apaga o arquivo e a entrada, com confirmação na tela (Lote do remodel
 * v1.44.0 — Frente 1, ver [DownloadsViewModel.deleteClip]). Lote mpv 4 — Frente 2 (v1.39.0): o clipe concluído ganha
 * "Compartilhar" (share sheet), "Salvar na pasta" (cópia p/ a pasta de
 * Downloads, sem mover o original) e "Vídeo inteiro" (baixa o vídeo
 * completo da fonte do clipe); sem fonte guardada, o "Vídeo inteiro"
 * diz o que falta em vez de falhar (ver [DownloadsViewModel.downloadFullClip]).
 * Lote v1.42.0 — Frente 1: com o vídeo inteiro baixando, a linha mostra
 * "Baixando vídeo inteiro… X%" com barra AQUI (a entrada ":full:" mora
 * na aba Restos — sem isso o toque parecia não fazer nada) e o botão
 * "Vídeo inteiro" some até terminar; Compartilhar e Salvar na pasta
 * continuam valendo, e o play do card não muda.
 */
@Composable
private fun ClipDownloadRow(
    clip: ClipDownloadEntry,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onShare: (() -> Unit)? = null,
    onSaveToFolder: (() -> Unit)? = null,
    onDownloadFull: (() -> Unit)? = null,
    /** % do vídeo inteiro em andamento (null = sem download completo — ver DownloadsScreen). */
    fullDownloadPercent: Int? = null,
) {
    val interval = clip.intervalLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    // Vídeo inteiro baixando: % lida p/ um val local (sem smart cast —
    // o parâmetro é nullable e o `when` abaixo não o estreita sozinho).
    val fullDownloading = fullDownloadPercent != null
    val fullPct = fullDownloadPercent ?: 0
    val sub = if (fullDownloading) {
        if (fullPct > 0) "Baixando vídeo inteiro… $fullPct%" else "Baixando vídeo inteiro…"
    } else when (clip.status) {
        "done"        -> "Trecho$interval — pronto p/ assistir"
        "downloading" -> if (clip.percent > 0) "Baixando trecho$interval… ${clip.percent}%" else "Baixando trecho$interval…"
        "queued"      -> "Trecho$interval — na fila"
        else          -> "Falha no trecho$interval"
    }
    val subColor = if (fullDownloading) Accent else when (clip.status) {
        "done" -> Ok
        "downloading" -> Accent
        "queued" -> Accent
        else -> DangerText
    }
    DownloadRowShell(
        posterUrl = clip.posterUrl,
        title = clip.title,
        subtitle = sub,
        subtitleColor = subColor,
        showProgress = clip.status == "downloading" || fullDownloading,
        percent = if (fullDownloading) fullPct else clip.percent,
        showPlay = clip.status == "done" && clip.localUri.isNotBlank(),
        showDownloadingRing = clip.status == "downloading",
        onClick = onClick,
        onResume = null,
        onDelete = onDelete,
        // Exportar só com arquivo local; o vídeo inteiro só com o clipe
        // concluído (a fonte dele é o que alimenta o download completo)
        // e sem outro download completo em andamento (o botão some e a
        // linha vira o progresso — toque duplo não recomeça nada).
        onShare = if (clip.status == "done" && clip.localUri.isNotBlank()) onShare else null,
        onSaveToFolder = if (clip.status == "done" && clip.localUri.isNotBlank()) onSaveToFolder else null,
        onDownloadFull = if (clip.status == "done" && !fullDownloading) onDownloadFull else null,
    )
}

/** Linha de um resto de arquivo .mp4 sem dono (Lote 18 item 4): só nome, tamanho e apagar. */
@Composable
private fun OrphanRow(
    orphan: OrphanFile,
    onDelete: () -> Unit,
) {
    DownloadRowShell(
        posterUrl = "",
        title = orphan.name,
        subtitle = "Resto de arquivo — ${MobileDataUsage.formatBytes(orphan.sizeBytes)}",
        subtitleColor = TextSecondary,
        showProgress = false,
        percent = 0,
        showPlay = false,
        showDownloadingRing = false,
        onClick = null,
        onResume = null,
        onDelete = onDelete,
    )
}

/**
 * Casca visual compartilhada das linhas da tela de Downloads (download,
 * clipe e resto de arquivo): pôster, título, subtítulo, barra de
 * progresso, play/anel, "Retomar" e lixeira. Cada tipo só preenche o que
 * tem — sem pôster, o quadrado vira o ícone de download. Lote mpv 4 —
 * Frente 2 (v1.39.0): a linha do clipe pode trazer mais três ações em
 * texto abaixo do subtítulo ([onShare], [onSaveToFolder] e
 * [onDownloadFull]); o resto passa null e nada muda p/ ele. Lote do
 * remodel v1.44.0 — Frente 1: o download concluído com arquivo local
 * também preenche [onShare] e [onSaveToFolder] (antes só o ":full:").
 */
@Composable
private fun DownloadRowShell(
    posterUrl: String,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    showProgress: Boolean,
    percent: Int,
    showPlay: Boolean,
    showDownloadingRing: Boolean,
    onClick: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onShare: (() -> Unit)? = null,
    onSaveToFolder: (() -> Unit)? = null,
    onDownloadFull: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (posterUrl.isNotBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
            )
        } else {
            Box(
                Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Download, null, tint = TextTertiary, modifier = Modifier.size(26.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                maxLines = 2,
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = subtitleColor,
                modifier = Modifier.padding(top = 3.dp),
            )
            // Barra de progresso (só enquanto baixa).
            if (showProgress) {
                LinearProgressIndicator(
                    progress = { if (percent > 0) percent / 100f else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Accent,
                    trackColor = FillTint.copy(alpha = 0.12f),
                )
            }
            // Ações de exportar (Lote mpv 4 — Frente 2): a linha do clipe
            // preenche Compartilhar/Salvar/Vídeo inteiro; o download
            // concluído com arquivo local preenche Compartilhar/Salvar
            // (Lote do remodel v1.44.0 — Frente 1) e o resto passa null.
            // Lote v1.42.0 — Frente 1: área de toque larga (16.dp x 8.dp):
            // os botões moram dentro do card clicável (o toque toca o
            // trecho) — um toque que errasse o texto por poucos dp caía
            // no card e reproduzia o vídeo em vez de baixar.
            if (onShare != null || onSaveToFolder != null || onDownloadFull != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (onShare != null) {
                        TextButton(onClick = onShare, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("Compartilhar", color = Accent, fontSize = 13.sp) }
                    }
                    if (onSaveToFolder != null) {
                        TextButton(onClick = onSaveToFolder, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("Salvar na pasta", color = Accent, fontSize = 13.sp) }
                    }
                    if (onDownloadFull != null) {
                        TextButton(onClick = onDownloadFull, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("Vídeo inteiro", color = Accent, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (showPlay) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(FocusSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, "Assistir", tint = OnFocusSurface, modifier = Modifier.size(22.dp))
            }
        } else if (showDownloadingRing) {
            // Anel determinado quando há %, indeterminado enquanto o total é desconhecido.
            if (percent > 0) {
                CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Accent)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Accent)
            }
        }
        // Retomar (Lote 18 item 4): só em "failed"/"queued".
        if (onResume != null) {
            TextButton(onClick = onResume) { Text("Retomar", color = Accent) }
        }
        if (onDelete != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, "Remover", tint = TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
