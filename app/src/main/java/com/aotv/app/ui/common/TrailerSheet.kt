package com.aotv.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.BorderSubtle
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.SoraFontFamily
import com.aotv.app.ui.theme.TextPrimary

/**
 * Folha expansível do trailer (Lote 2 item 9h): entre o mini-player do topo e
 * a tela cheia em paisagem. Recebe o ExoPlayer JÁ CRIADO (o detalhe iça um
 * só p/ mini, folha e tela cheia) — expandir/minimizar move a MESMA
 * instância, sem recomeçar o vídeo. O PlayerView é recriado a cada estado,
 * mas o player (e a posição) sobrevive.
 */
@Composable
fun TrailerSheet(
    exo: ExoPlayer,
    title: String,
    onExpandFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val scrimSrc = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = scrimSrc, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val cardSrc = remember { MutableInteractionSource() }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(BackgroundCard)
                .border(1.dp, Border, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clickable(interactionSource = cardSrc, indication = null, onClick = {})
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 14.dp)
                .navigationBarsPadding(),
        ) {
            // Grabber
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderSubtle),
            )
            Spacer(Modifier.height(12.dp))
            // Título + fechar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, "Fechar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            // Vídeo 16:9 — o mesmo player do mini e da tela cheia.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
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
            }
            Spacer(Modifier.height(12.dp))
            // Ações: tela cheia / minimizar (PiP) — o fechar já está no topo.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrailerSheetAction(
                    icon = Icons.Default.Fullscreen,
                    label = "Tela cheia",
                    onClick = onExpandFullscreen,
                    modifier = Modifier.weight(1f),
                )
                TrailerSheetAction(
                    icon = Icons.Default.PictureInPictureAlt,
                    label = "Minimizar",
                    onClick = onMinimize,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TrailerSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label, color = Color.White, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = ManropeFontFamily,
        )
    }
}
