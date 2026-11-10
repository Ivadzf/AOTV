package com.aotv.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.aotv.app.data.repository.ContinueArtwork
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.theme.*

/** Imagem do episódio com o progresso e as informações dentro do cartão, como no webOS. */
@Composable
fun TvContinueCard(
    item: MovieItem,
    onClick: () -> Unit,
    artwork: ContinueArtwork? = null,
    progress: Float? = null,
    remaining: Int? = null,
    episode: String = "",
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = tvFocusScale(focused, 1.04f)
    val shape = RoundedCornerShape(0.9.r)
    var failed by remember(artwork?.imageUrl) { mutableStateOf(false) }
    val image = if (!failed) artwork?.imageUrl.orEmpty().ifBlank { item.backdropUrl }.ifBlank { item.posterUrl }
        else artwork?.posterUrl.orEmpty().ifBlank { item.posterUrl }
    Box(
        Modifier.width(26.r).aspectRatio(16f / 9f).scale(scale)
            .shadow(if (focused) 1.2.r else 0.r, shape).clip(shape).background(BackgroundCard)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .tvClick(onClick, onLongClick)
            .then(if (focused) Modifier.border(0.22.r, Color.White, shape) else Modifier),
    ) {
        AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .08f), .3f to Color.Transparent, 1f to Color.Black.copy(alpha = .94f),
        )))
        val pill = when {
            remaining != null && remaining > 0 -> "Faltam $remaining min"
            episode.isNotBlank() && (progress ?: 0f) <= 0f -> "Próximo episódio"
            else -> "Continuar assistindo"
        }
        Text(
            pill, color = Color.White, fontSize = .85.rs, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(.9.r)
                .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(50)).padding(horizontal = .7.r, vertical = .35.r),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(1.1.r).padding(bottom = .3.r)) {
            if (episode.isNotBlank()) Text(episode, color = TextSecondary, fontSize = .9.rs, fontWeight = FontWeight.Bold)
            Text(
                item.title.ifBlank { artwork?.title.orEmpty() }, color = Color.White, fontFamily = Inter,
                fontSize = 1.2.rs, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!artwork?.episodeName.isNullOrBlank()) Text(
                artwork!!.episodeName, color = TextSecondary, fontSize = .9.rs,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = .25.r),
            )
        }
        if (focused) Icon(
            Icons.Default.PlayArrow, null, tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(3.r),
        )
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(.26.r).background(Color.White.copy(alpha = .2f))) {
            if (progress != null && progress > 0f) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
    }
}
