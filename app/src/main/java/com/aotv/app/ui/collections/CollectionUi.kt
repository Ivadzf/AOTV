package com.aotv.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.data.collections.CollectionFolder
import com.aotv.app.data.collections.CollectionSource
import com.aotv.app.data.collections.MediaCollection
import com.aotv.app.data.collections.TmdbSourceType
import com.aotv.app.ui.theme.*

/** Fileira da Início com as pastas da coleção. */
@Composable
fun CollectionFolderRow(collection: MediaCollection, title: String, eyebrow: String? = "Coleção", onFolder: (CollectionFolder) -> Unit) {
    val pad = com.aotv.app.ui.common.pagePadding()
    Column {
        com.aotv.app.ui.common.SectionHeader(title = title, eyebrow = eyebrow)
        LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(collection.folders, key = { it.id }) { f -> FolderTile(f, onClick = { onFolder(f) }) }
        }
    }
}

/** Largura e proporção do cartão da pasta pelo formato escolhido (pôster, paisagem ou quadrado). */
private fun tileSize(shape: String) = when (shape) {
    "landscape" -> 220.dp to 16f / 9f
    "square" -> 150.dp to 1f
    else -> 128.dp to 2f / 3f
}

/**
 * Cartão da pasta. Com GIF ligado ele anima (o carregador de imagem do app lê GIF). Sem imagem,
 * mostra o emoji, e sem emoji a inicial do nome.
 */
@Composable
fun FolderTile(folder: CollectionFolder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (w, ratio) = tileSize(folder.shape)
    val image = folder.focusGifUrl?.takeIf { folder.focusGifEnabled && it.isNotBlank() } ?: folder.coverImageUrl?.takeIf { it.isNotBlank() }
    Column(modifier.width(w)) {
        if (image != null) {
            com.aotv.app.ui.common.MediaPosterCard(
                title = folder.title, imageUrl = image, onClick = onClick,
                modifier = Modifier.fillMaxWidth(), aspectRatio = ratio, cleanPoster = true,
            )
        } else {
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.fillMaxWidth().aspectRatio(ratio).clip(shape)
                    .background(if (focused) AccentSoft else Surface2)
                    .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                val emoji = folder.coverEmoji?.takeIf { it.isNotBlank() }
                if (emoji != null) Text(emoji, fontSize = 44.sp)
                else Text(folder.title.take(1).uppercase(), color = TextSecondary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (!folder.hideTitle) com.aotv.app.ui.common.PosterCaption(folder.title)
    }
}

/** "1 pasta", "3 pastas". */
fun countOf(n: Int, one: String, many: String): String = "$n " + if (n == 1) one else many

/** Nome da fonte para aba, fileira e editor. */
fun sourceLabel(s: CollectionSource, catalogName: (CollectionSource) -> String? = { null }): String {
    s.title?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        s.isTrakt -> "Lista do Trakt"
        s.isAddon -> (catalogName(s) ?: s.catalogId.orEmpty()) + (s.genre?.let { " · $it" } ?: "")
        else -> tmdbTypeLabel(s.tmdbType)
    }
}

fun tmdbTypeLabel(t: TmdbSourceType?): String = when (t) {
    TmdbSourceType.LIST -> "Lista do TMDB"
    TmdbSourceType.COLLECTION -> "Franquia"
    TmdbSourceType.COMPANY -> "Estúdio"
    TmdbSourceType.NETWORK -> "Canal"
    TmdbSourceType.DISCOVER -> "Descobrir"
    TmdbSourceType.PERSON -> "Ator ou atriz"
    TmdbSourceType.DIRECTOR -> "Direção"
    null -> "Fonte do TMDB"
}

/** Linha de baixo da fonte no editor (de onde vem e o tipo). */
fun sourceKind(s: CollectionSource): String = when {
    s.isTrakt -> "Trakt, não carrega nesta versão"
    s.isAddon -> "Addon ${s.addonId.orEmpty()} · " + if (s.type == "series") "Séries" else "Filmes"
    else -> "TMDB · " + tmdbTypeLabel(s.tmdbType) + if (s.tmdbType == TmdbSourceType.NETWORK || s.tv) " · Séries" else " · Filmes"
}

@Composable
internal fun CollectionCard(highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2)
            .then(if (highlight) Modifier.border(1.dp, Accent.copy(alpha = .5f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun collectionSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary,
    uncheckedTrackColor = BackgroundCard2,
    uncheckedBorderColor = Border,
)

/** Etiqueta pequena (Fixada no topo, Trakt e afins). */
@Composable
internal fun CollectionTag(text: String, color: Color = Accent) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
