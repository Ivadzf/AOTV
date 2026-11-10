package com.aotv.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Toca o TRAILER com o player oficial do YouTube (IFrame API, lib android-youtube-player).
 * Recebe VÁRIAS chaves (melhor primeiro): se uma bloquear embed (erro 152/150), tenta a próxima.
 */
@Composable
fun TrailerYoutubeOverlay(keys: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    val idx = remember(keys) { mutableIntStateOf(0) }
    val view = remember(keys) {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            val opts = IFramePlayerOptions.Builder().controls(1).rel(0).build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    keys.getOrNull(0)?.let { youTubePlayer.loadVideo(it, 0f) }
                }
                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    // 152/150 = embed desabilitado pelo dono → tenta o próximo trailer.
                    if (error == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ||
                        error == PlayerConstants.PlayerError.HTML_5_PLAYER
                    ) {
                        val next = idx.intValue + 1
                        if (next < keys.size) {
                            idx.intValue = next
                            youTubePlayer.loadVideo(keys[next], 0f)
                        }
                    }
                }
            }, opts)
        }
    }
    DisposableEffect(view) { onDispose { view.release() } }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)).clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, "Fechar", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
