package com.aotv.app.ui.common

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * Trailer no NOSSO ExoPlayer (stream extraído do YouTube). Toca até trailers com embed bloqueado.
 *
 * Vai dentro de um [Dialog] de propósito. Renderizado inline ele ficava ABAIXO da barra de
 * navegação da tela que o abriu; no Dialog cobre a tela inteira. Enquanto aberto o app libera
 * a rotação e esconde as barras do sistema, e devolve tudo ao fechar.
 *
 * [onOpenDetail] e [onNext] são opcionais: quando vêm, aparecem os botões de abrir a página do
 * título e de pular pro próximo trailer da fileira.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrailerExoOverlay(
    videoUrl: String,
    audioUrl: String?,
    onClose: () -> Unit,
    title: String = "",
    onOpenDetail: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val exo = rememberTrailerExo(videoUrl, audioUrl)

    // Tela cheia de verdade: trava em paisagem e esconde status bar e barra de navegação
    // enquanto o trailer está aberto. Ao fechar, devolve o estado anterior.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }

    BackHandler(onBack = onClose)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // ocupa a tela toda, inclusive sobre a barra de navegação
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Título do que está tocando (canto superior esquerdo).
            if (title.isNotBlank()) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 20.dp, end = 160.dp),
                )
            }

            // Ações: ver o título, próximo trailer, fechar.
            Row(
                Modifier.align(Alignment.TopEnd).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOpenDetail != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { onOpenDetail() }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Text("Ver título", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (onNext != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { onNext() }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Text("Próximo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, "Fechar", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Sobe a cadeia de ContextWrapper até achar a Activity (o LocalContext nem sempre é ela). */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** ExoPlayer do trailer (stream do YouTube), solto ao sair da tela. Usado pelo overlay e pelo destaque. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun rememberTrailerExo(videoUrl: String, audioUrl: String?): ExoPlayer {
    val context = LocalContext.current
    val exo = remember(videoUrl, audioUrl) {
        val f = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
        // Start rápido: o padrão do ExoPlayer segura 2,5s de buffer antes do primeiro frame.
        // Baixando pra 0,5s o trailer começa quase na hora e segue enchendo o buffer depois.
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 500, 2_000)
            .build()
        // DefaultMediaSourceFactory detecta HLS (.m3u8, ex: Piped) e mp4 automaticamente.
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(f))
            .setLoadControl(load)
            .build().apply {
                if (audioUrl.isNullOrBlank()) {
                    val item = MediaItem.Builder().setUri(videoUrl)
                    if (videoUrl.substringBefore('?').endsWith(".m3u8", true) || videoUrl.contains(".m3u8")) {
                        item.setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                    setMediaItem(item.build())
                } else {
                    // NewPipe DASH: vídeo-only + áudio-only (mp4) → mescla.
                    val v = ProgressiveMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(videoUrl))
                    val a = ProgressiveMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(audioUrl))
                    setMediaSource(MergingMediaSource(v, a))
                }
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.w("TrailerExo", "falha ao tocar trailer: ${error.errorCodeName} ${error.message}")
                    }
                })
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }
    return exo
}

/**
 * Trailer sem som e sem controles por cima do fundo do destaque da Início. Corta para preencher
 * a área. [onEnd] avisa quando acaba ou falha, para o destaque voltar à imagem.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MutedTrailer(videoUrl: String, audioUrl: String?, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val exo = rememberTrailerExo(videoUrl, audioUrl)
    var started by remember(exo) { mutableStateOf(false) }
    val view = remember(exo) { arrayOfNulls<android.view.TextureView>(1) }
    // Recorte central feito na matriz da TextureView. A view tem o tamanho exato da área e o
    // vídeo é ampliado até cobrir tudo. Antes a view era mais larga que a tela e o Android
    // não desenhava a parte de fora, sobrava faixa escura.
    fun cover() {
        val tv = view[0] ?: return
        val vs = exo.videoSize
        if (vs.width == 0 || vs.height == 0 || tv.width == 0 || tv.height == 0) return
        val vw = vs.width * vs.pixelWidthHeightRatio
        val vh = vs.height.toFloat()
        val k = maxOf(tv.width / vw, tv.height / vh)
        tv.setTransform(android.graphics.Matrix().apply {
            setScale(vw * k / tv.width, vh * k / tv.height, tv.width / 2f, tv.height / 2f)
        })
    }
    DisposableEffect(exo) {
        exo.volume = 0f
        val l = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() { started = true }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = cover()
            override fun onPlaybackStateChanged(state: Int) { if (state == androidx.media3.common.Player.STATE_ENDED) onEnd() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) = onEnd()
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    // TextureView porque aceita transparência. Fica invisível até o primeiro quadro, assim o fundo
    // não pisca preto enquanto carrega.
    AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).also { tv ->
                view[0] = tv
                exo.setVideoTextureView(tv)
                tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cover() }
            }
        },
        modifier = modifier.clipToBounds().graphicsLayer { alpha = if (started) 1f else 0f },
    )
}
