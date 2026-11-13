package com.aotv.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aotv.app.data.repository.ContinueArtwork
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.theme.*

/** Cartão horizontal de episódio com altura suficiente para a fonte escolhida no celular. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueCard(
    item: MovieItem,
    onClick: () -> Unit,
    artwork: ContinueArtwork? = null,
    progress: Float? = null,
    remaining: Int? = null,
    episode: String = "",
    onLongClick: (() -> Unit)? = null,
    /** Modo compacto (Fase B): card mais estreito. */
    compact: Boolean = false,
) {
    val width = continueCardWidth(compact)
    val font = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val height = maxOf(width.value * 9 / 16, 156f + 76f * (font - 1f)).dp
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    var failed by remember(artwork?.imageUrl) { mutableStateOf(false) }
    val image = if (failed) artwork?.posterUrl.orEmpty().ifBlank { item.posterUrl }
        else artwork?.imageUrl.orEmpty().ifBlank { item.backdropUrl }.ifBlank { item.posterUrl }
    Box(Modifier.width(width).height(height).clip(shape).background(BackgroundCard)
        .onFocusChanged { focused = it.isFocused }
        .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
        .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)) {
        AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .12f), .25f to Color.Transparent, 1f to Color.Black.copy(alpha = .94f),
        )))
        val pill = when {
            remaining != null && remaining > 0 -> "Faltam $remaining min"
            episode.isNotBlank() && (progress ?: 0f) <= 0f -> "Próximo episódio"
            else -> "Continuar assistindo"
        }
        Text(pill, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (episode.isNotBlank()) Text(episode.replace(':', ' '), color = TextSecondary, fontSize = 12.sp,
                lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(item.title.ifBlank { artwork?.title.orEmpty() }, color = Color.White, fontSize = 17.sp,
                lineHeight = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!artwork?.episodeName.isNullOrBlank()) Text(artwork!!.episodeName, color = TextSecondary,
                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.Center).size(44.dp).background(Color.Black.copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .24f))) {
            if (progress != null) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
    }
}
