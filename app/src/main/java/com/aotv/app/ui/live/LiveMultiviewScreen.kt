package com.aotv.app.ui.live

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.ui.theme.*

/** Rota do Multiview (Lote 12, v1.22.0). */
const val LIVE_MULTIVIEW_ROUTE = "live_multiview"

/**
 * Multiview: até 4 canais ao mesmo tempo (Lote 12, v1.22.0).
 *
 * Cada célula tem o seu próprio ExoPlayer; SÓ a célula em foco tem áudio
 * (as outras ficam mudas). Tocar na célula dá o foco; o botão de expandir
 * abre o player cheio daquele canal. O seletor de layout (1, 2 ou 4) fica
 * guardado por perfil (`live_multiview_layout`).
 *
 * Celular primeiro: na TV o Multiview não entra (o D-pad não dá conta de 4
 * players) — a tela avisa e oferece abrir um canal só, no player único.
 * Sem dependência nova: só ExoPlayer, que o app já usa no player ao vivo.
 */
@Composable
fun LiveMultiviewScreen(
    onBack: () -> Unit,
    onExpand: (String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val v by viewModel.view.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { com.aotv.app.data.local.PlatformPrefs(context) }
    val layout by prefs.liveMultiviewLayoutFlow.collectAsStateWithLifecycle()
    val isTv = com.aotv.app.ui.common.LocalIsTv.current

    Column(Modifier.fillMaxSize().background(Background).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            Text("Multiview", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (!isTv) {
                com.aotv.app.ui.common.FilterChipPill("1", layout == 1, { prefs.liveMultiviewLayout = 1 })
                com.aotv.app.ui.common.FilterChipPill("2", layout == 2, { prefs.liveMultiviewLayout = 2 })
                com.aotv.app.ui.common.FilterChipPill("4", layout == 4, { prefs.liveMultiviewLayout = 4 })
            }
        }

        if (v.rows.isEmpty()) {
            Text(
                if (v.loading) "Carregando canais…" else "Nenhum canal aqui. Adicione uma fonte em Configurações → TV ao vivo.",
                color = TextTertiary, fontSize = 14.sp,
                modifier = Modifier.padding(com.aotv.app.ui.common.pagePadding()),
            )
            return@Column
        }

        if (isTv) {
            // TV (Lote 12, v1.22.0): Multiview não entra no D-pad — player
            // único, com o aviso honesto. O primeiro canal abre no player.
            Column(
                Modifier.fillMaxSize().padding(com.aotv.app.ui.common.pagePadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "O Multiview é só no celular.",
                    color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Ver até 4 canais ao mesmo tempo precisa de toque — na TV, abra um canal de cada vez no player.",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
                v.rows.firstOrNull()?.let { row ->
                    com.aotv.app.ui.common.AotvButton(
                        "Assistir ${row.channel.name}", { onExpand(row.channel.id) },
                        tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                    )
                }
            }
            return@Column
        }

        MultiviewGrid(
            rows = v.rows,
            layout = layout,
            onExpand = onExpand,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Grade 1/2/4 com os canais das células (só no celular). */
@Composable
private fun MultiviewGrid(
    rows: List<LiveRow>,
    layout: Int,
    onExpand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Canais das células, na ordem da lista; trocar o layout completa as
    // células que faltam com os próximos canais (sem repetir).
    var cells by remember { mutableStateOf<List<String>>(emptyList()) }
    var focused by remember { mutableIntStateOf(0) }
    var pickerCell by remember { mutableIntStateOf(-1) }
    val byId = remember(rows) { rows.associate { it.channel.id to it } }

    LaunchedEffect(rows, layout) {
        val want = layout.coerceIn(1, 4)
        val kept = cells.filter { it in byId }.take(want)
        val filled = kept + rows.map { it.channel.id }.filterNot { it in kept }.take(want - kept.size)
        if (filled != cells) cells = filled
        if (focused >= want) focused = 0
    }

    fun cell(i: Int): @Composable (Modifier) -> Unit = { cellModifier ->
        val id = cells.getOrNull(i)
        val row = id?.let { byId[it] }
        MultiviewCell(
            channel = row?.channel,
            nowTitle = row?.now?.title,
            focused = focused == i,
            onFocus = { focused = i },
            onExpand = { row?.let { onExpand(it.channel.id) } },
            onPick = { pickerCell = i },
            modifier = cellModifier,
        )
    }

    Column(modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (layout) {
            2 -> {
                Box(Modifier.weight(1f).fillMaxWidth()) { cell(0)(Modifier.fillMaxSize()) }
                Box(Modifier.weight(1f).fillMaxWidth()) { cell(1)(Modifier.fillMaxSize()) }
            }
            4 -> {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { cell(0)(Modifier.fillMaxSize()) }
                    Box(Modifier.weight(1f).fillMaxHeight()) { cell(1)(Modifier.fillMaxSize()) }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { cell(2)(Modifier.fillMaxSize()) }
                    Box(Modifier.weight(1f).fillMaxHeight()) { cell(3)(Modifier.fillMaxSize()) }
                }
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth()) { cell(0)(Modifier.fillMaxSize()) }
        }
    }

    // Trocar o canal da célula: lista com busca (só canais visíveis).
    if (pickerCell >= 0) {
        var q by remember { mutableStateOf("") }
        val needle = q.trim().lowercase()
        val list = remember(rows, needle) {
            if (needle.isEmpty()) rows
            else rows.filter { it.channel.name.lowercase().contains(needle) || it.channel.group.lowercase().contains(needle) }
        }
        AlertDialog(
            onDismissRequest = { pickerCell = -1 },
            containerColor = BackgroundCard,
            title = { Text("Trocar o canal", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        placeholder = { Text("Buscar canal") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                        ),
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list, key = { it.channel.id }) { row ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val idx = pickerCell
                                        cells = cells.toMutableList().also { l ->
                                            while (l.size <= idx) l += ""
                                            l[idx] = row.channel.id
                                        }
                                        focused = idx
                                        pickerCell = -1
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (row.channel.logo.isNotBlank()) {
                                    AsyncImage(row.channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 40.dp, height = 28.dp))
                                }
                                Text(row.channel.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerCell = -1 }) { Text("Fechar", color = TextSecondary) } },
        )
    }
}

/** Uma célula: o seu próprio ExoPlayer, mudo salvo quando está em foco. */
@OptIn(UnstableApi::class)
@Composable
private fun MultiviewCell(
    channel: LiveChannel?,
    nowTitle: String?,
    focused: Boolean,
    onFocus: () -> Unit,
    onExpand: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) failed = false
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // Toca o canal da célula; muda o canal, troca a fonte.
    LaunchedEffect(channel?.id) {
        failed = false
        channel?.let { playMultiview(player, it) }
    }
    // Áudio SÓ da célula em foco — as outras ficam mudas.
    LaunchedEffect(focused) { player.volume = if (focused) 1f else 0f }

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(14.dp)) else Modifier)
            .clickable { onFocus() },
    ) {
        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sem canal", color = TextTertiary, fontSize = 14.sp)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Canal fora do ar agora", color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            // Faixa com o canal: tocar dá o foco; os botões trocam e expandem.
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 32.dp, height = 22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        channel.name, color = Color.White, fontSize = 13.sp,
                        fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    nowTitle?.let {
                        Text(it, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (focused) Text("COM ÁUDIO", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = onPick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.List, "Trocar o canal", tint = Color.White)
                }
                IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInFull, "Abrir em tela cheia", tint = Color.White)
                }
            }
        }
    }
}

/** Toca um canal no player da célula (mesmo padrão do player ao vivo, sem catch-up). */
@OptIn(UnstableApi::class)
private fun playMultiview(player: ExoPlayer, c: LiveChannel) {
    val http = OkHttpDataSource.Factory(com.aotv.app.data.remote.AppDns.mediaClient)
        .setDefaultRequestProperties(c.headers)
        .setUserAgent(c.headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android) AOTV")
    val item = MediaItem.Builder().setUri(c.url).apply {
        val path = c.url.substringBefore('?').lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".m3u")) setMimeType(MimeTypes.APPLICATION_M3U8)
    }.build()
    player.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
    player.prepare()
}
