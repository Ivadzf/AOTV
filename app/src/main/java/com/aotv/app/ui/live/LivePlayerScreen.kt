package com.aotv.app.ui.live

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay

/**
 * Player de canal ao vivo. Toca HLS ou TS com os cabeçalhos que a lista pede, mostra o que está
 * passando e troca de canal pela lista lateral ou pelas setas.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(channelId: String, onBack: () -> Unit, viewModel: LivePlayerViewModel = hiltViewModel()) {
    com.aotv.app.ui.common.ImmersiveFullscreen()
    com.aotv.app.ui.common.KeepScreenOn()
    val context = LocalContext.current
    val st by viewModel.state.collectAsStateWithLifecycle()
    var currentId by remember { mutableStateOf(channelId) }
    val channel = st.channels.firstOrNull { it.id == currentId }
    // A lista lateral segue o mesmo grupo do canal aberto (favoritos entram juntos).
    val siblings = remember(st.channels, channel?.group) { st.channels.filter { it.group == channel?.group }.ifEmpty { st.channels } }
    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var retries by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val activity = context as? android.app.Activity
    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    // Ao sair, solta o player e volta a tela para o automático (caso tenha travado em paisagem).
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { failed = false; retries = 0 }
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun play(c: LiveChannel) {
        failed = false
        buffering = true
        val http = OkHttpDataSource.Factory(com.aotv.app.data.remote.AppDns.mediaClient)
            .setDefaultRequestProperties(c.headers)
            .setUserAgent(c.headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android) AOTV")
        val item = MediaItem.Builder().setUri(c.url).apply {
            val path = c.url.substringBefore('?').lowercase()
            if (path.endsWith(".m3u8") || path.endsWith(".m3u")) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
        player.prepare()
        viewModel.watched(c.id)
    }

    LaunchedEffect(channel?.id) { channel?.let { play(it) } }
    // Canal caiu. Tenta de novo duas vezes antes de mostrar o aviso.
    LaunchedEffect(failed) {
        if (failed && retries < 2 && channel != null) {
            delay(2_500); retries++; play(channel)
        }
    }
    LaunchedEffect(overlay, panel) { if (overlay && !panel) { delay(5_000); overlay = false } }

    fun step(delta: Int) {
        if (siblings.isEmpty()) return
        val i = siblings.indexOfFirst { it.id == currentId }
        currentId = siblings[((if (i < 0) 0 else i) + delta + siblings.size) % siblings.size].id
        overlay = true
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { overlay = !overlay; if (!overlay) panel = false },
    ) {
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

        if (buffering && !failed) CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(44.dp))

        if (failed && retries >= 2) Column(
            Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC0B0F17)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Canal fora do ar agora", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("A fonte não respondeu. Tente de novo ou mude de canal.", color = TextSecondary, fontSize = 14.sp)
            com.aotv.app.ui.common.AotvButton("Tentar de novo", { retries = 0; channel?.let { play(it) } }, icon = Icons.Default.Refresh, compact = true)
        }

        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Topo com o canal.
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                    if (!channel?.logo.isNullOrBlank()) AsyncImage(channel!!.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel?.name ?: "Canal", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!channel?.group.isNullOrBlank()) Text(LiveTvViewModel.groupLabel(channel!!), color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val fav = channel != null && channel.id in st.favorites
                    IconButton(onClick = { channel?.let { viewModel.toggleFavorite(it.id) } }, modifier = Modifier.size(48.dp)) {
                        Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, if (fav) "Tirar dos favoritos" else "Favoritar", tint = if (fav) Gold else Color.White)
                    }
                    IconButton(onClick = {
                        val cur = activity?.requestedOrientation
                        activity?.requestedOrientation =
                            if (cur == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        overlay = true
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ScreenRotation, "Girar a tela", tint = Color.White)
                    }
                    IconButton(onClick = { panel = !panel; overlay = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.List, "Lista de canais", tint = Color.White)
                    }
                }
                // Base com agora e a seguir, e as setas de canal.
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val (n, nx) = channel?.let { viewModel.nowNext(it.id, now) } ?: (null to null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "AO VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(LiveRed).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                            Text(n?.title ?: "Sem guia para este canal", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (n != null) {
                            com.aotv.app.ui.common.ProgressBar(n.progress(now), Modifier.fillMaxWidth(0.7f), height = 3.dp)
                            Text("${hhmm(n.start)} até ${hhmm(n.stop)}", color = TextSecondary, fontSize = 12.sp)
                        }
                        nx?.let { Text("A seguir ${hhmm(it.start)} ${it.title}", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    IconButton(onClick = { step(-1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Canal anterior", tint = Color.White) }
                    IconButton(onClick = { step(1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Próximo canal", tint = Color.White) }
                }
            }
        }

        AnimatedVisibility(panel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) {
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { siblings.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) } }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.8f).background(Color(0xEE080B12))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(siblings, key = { _, c -> c.id }) { _, c ->
                    val active = c.id == currentId
                    val (n, _) = viewModel.nowNext(c.id, now)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (active) AccentSoft else Color.White.copy(alpha = .05f))
                            .clickable { currentId = c.id; panel = false }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(width = 48.dp, height = 34.dp), contentAlignment = Alignment.Center) {
                            if (c.logo.isNotBlank()) AsyncImage(c.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            else Text(c.name.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = if (active) Accent else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            n?.let { Text(it.title, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}
