package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Moldura com âncora p/ as telas de VPN e Watch Party (Lote 10, v1.20.0).
 *
 * Mesmo mecanismo do `SettingsPageScaffold` (privado em SettingsPages.kt):
 * a seção registra a posição ao compor e, com a âncora pedida pela busca
 * profunda, a tela rola até ela e destaca o título. As telas de VPN e
 * Watch Party têm scaffold próprio como Trackers/Backup, então a moldura
 * vive aqui em vez de lá.
 */

/** Posições (px dentro do conteúdo rolável) das seções com âncora da página aberta. */
private val LocalVpnPartyAnchorPositions = staticCompositionLocalOf<MutableMap<String, Int>?> { null }

/** Âncora pedida pela busca profunda (null = abertura normal, sem destaque). */
private val LocalVpnPartyHighlightAnchor = staticCompositionLocalOf<String?> { null }

@Composable
fun VpnPartyScaffold(
    title: String,
    sub: String?,
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pad = pagePadding()
    val scrollState = rememberScrollState()
    val anchorPositions = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(highlightAnchor, highlightAnchor?.let { anchorPositions[it] }) {
        val y = highlightAnchor?.let { anchorPositions[it] } ?: return@LaunchedEffect
        scrollState.scrollTo(maxOf(0, y - 8))
    }
    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            PageHeader(title, Modifier.weight(1f), sub = sub)
        }
        CompositionLocalProvider(
            LocalVpnPartyAnchorPositions provides anchorPositions,
            LocalVpnPartyHighlightAnchor provides highlightAnchor,
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scrollState)
                    .padding(horizontal = pad).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * Rótulo de seção com âncora (mesmo mecanismo do `SettingsSection` de
 * SettingsPages.kt): registra a posição p/ a busca profunda rolar até
 * aqui e ganha fundo de destaque quando é a âncora pedida.
 */
@Composable
fun VpnPartySection(text: String, anchor: String? = null) {
    val positions = LocalVpnPartyAnchorPositions.current
    val highlighted = anchor != null && anchor == LocalVpnPartyHighlightAnchor.current
    com.aotv.app.ui.common.SettingsSection(
        text,
        modifier = Modifier
            .then(
                if (anchor != null && positions != null) Modifier.onGloballyPositioned { coords ->
                    positions[anchor] = coords.positionInParent().y.roundToInt()
                } else Modifier
            )
            .then(
                if (highlighted) Modifier.background(AccentSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                else Modifier
            ),
    )
}
