package com.aotv.app.ui.common

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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aotv.app.data.remote.YoutubeTrailer

/**
 * Mini-player do trailer (Lote 21, v1.31.0): o trailer toca embutido no topo
 * do detalhe (autoplay após alguns segundos) em vez de abrir o overlay de
 * tela cheia. Com [pip], vira um quadro flutuante no canto inferior — o
 * MESMO player, sem reiniciar: o composable não desmonta ao minimizar, só
 * muda de posição/tamanho (o chamador troca o Modifier). Expandir abre o
 * overlay de tela cheia; fechar encerra o trailer desta sessão.
 * Mesmo ExoPlayer do overlay ([rememberTrailerExo]), com som — diferente do
 * [MutedTrailer] mudo do destaque da Início.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrailerMiniPlayer(
    stream: YoutubeTrailer.TrailerStream,
    pip: Boolean,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit,
    onMinimize: (() -> Unit)? = null,
    onClose: () -> Unit,
    // Lote 2 item 9h — player compartilhado (o detalhe iça um só): com ele, o
    // player próprio nasce ocioso (URL em branco) e não toca nada.
    sharedExo: ExoPlayer? = null,
) {
    val ownedExo = rememberTrailerExo(if (sharedExo != null) "" else stream.videoUrl, if (sharedExo != null) null else stream.audioUrl)
    val exo = sharedExo ?: ownedExo
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black),
    ) {
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
        Row(
            Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!pip && onMinimize != null) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)).clickable { onMinimize() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PictureInPictureAlt, "Minimizar", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable { onExpand() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Fullscreen, "Tela cheia", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, "Fechar trailer", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
