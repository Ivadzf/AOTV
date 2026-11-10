package com.aotv.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aotv.app.R
import com.aotv.app.data.local.Profile
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.rememberReducedMotion
import com.aotv.app.ui.theme.*
import com.aotv.app.ui.tv.r
import com.aotv.app.ui.tv.rs

/** Item do menu lateral da TV. [avatar] troca o ícone pelo avatar do perfil. */
class TvMenuItem(
    val route: String,
    val label: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
    val avatar: Profile? = null,
)

val TvMenuClosed: androidx.compose.ui.unit.Dp
    @Composable @ReadOnlyComposable get() = 6.5.r

/** Cápsula flutuante do webOS, com seleção que acompanha o foco do controle remoto. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSideMenu(
    main: List<TvMenuItem>,
    bottom: List<TvMenuItem>,
    currentRoute: String?,
    requesters: Map<String, FocusRequester>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val progress = animateFloatAsState(if (open) 1f else 0f, tween(if (reduced) 0 else 220), label = "menuReveal")
    val dockWidth by animateDpAsState(if (open) 19.5.r else 4.75.r, tween(if (reduced) 0 else 220), label = "menuWidth")
    val entries = main + bottom
    val active = entries.firstOrNull { it.route == currentRoute } ?: main.first()
    var focusedRoute by remember { mutableStateOf<String?>(null) }
    val pillRoute = if (open) focusedRoute ?: active.route else active.route
    val index = entries.indexOfFirst { it.route == pillRoute }.coerceAtLeast(0)
    val sectionGap = if (bottom.isEmpty()) 0f else 1.26f
    val dockHeight = (1.4f + entries.size * 4.4f + sectionGap).r
    val pillY by animateDpAsState((.95f + index * 4.4f + if (index >= main.size) sectionGap else 0f).r,
        tween(if (reduced) 0 else 250), label = "menuSelection")
    val labelSlide = .6.r
    Box(Modifier.fillMaxHeight().width(if (open) 21.25.r else TvMenuClosed)) {
        Row(Modifier.align(Alignment.TopStart).padding(top = 2.4.r, start = 1.6.r)
            .wrapContentWidth(Alignment.Start, unbounded = true).width(20.r).height(3.6.r), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.aotv_nav_logo), "AOTV", Modifier.size(3.25.r))
            Text("AOTV", color = TextPrimary, fontSize = 1.7.rs, fontWeight = FontWeight.Bold, fontFamily = DisplayFont,
                letterSpacing = (-.068).rs, modifier = Modifier.padding(start = 1.r).graphicsLayer {
                    alpha = progress.value; translationX = -labelSlide.toPx() * (1f - progress.value)
                })
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = .875.r).width(dockWidth).height(dockHeight)
            .clip(RoundedCornerShape(2.6.r)).background(Color(0xF7191919))
            .then(Modifier.border(.06.r, Color.White.copy(alpha = .09f), RoundedCornerShape(2.6.r)))) {
            Box(Modifier.offset(x = .4.r, y = pillY).width(dockWidth - .8.r).height(3.9.r)
                .background(Color(0xFFF5F5F5), RoundedCornerShape(2.r)))
            Column(Modifier.width(dockWidth).padding(vertical = .7.r)
                .onFocusChanged { onOpenChange(it.hasFocus) }
                .focusProperties { enter = { requesters[active.route] ?: FocusRequester.Default } }.focusGroup()) {
                main.forEach { item ->
                    TvMenuRow(item, item.route == currentRoute, item.route == pillRoute, progress, requesters[item.route]) { focusedRoute = item.route }
                }
                if (bottom.isNotEmpty()) {
                    Box(Modifier.padding(top = .6.r, bottom = .6.r).padding(horizontal = .4.r)
                        .fillMaxWidth().height(.06.r).background(Color.White.copy(alpha = .08f)))
                    bottom.forEach { item ->
                        TvMenuRow(item, item.route == currentRoute, item.route == pillRoute, progress, requesters[item.route]) { focusedRoute = item.route }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMenuRow(
    item: TvMenuItem,
    active: Boolean,
    selectedPill: Boolean,
    progress: State<Float>,
    requester: FocusRequester?,
    onFocused: () -> Unit,
) {
    val labelSlide = .6.r
    val fg = if (selectedPill) TextOnLight else if (active) TextSecondary else TextTertiary
    Box(Modifier.padding(horizontal = .4.r, vertical = .25.r).fillMaxWidth().height(3.9.r)
        .clip(RoundedCornerShape(2.r))
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onFocusChanged { if (it.isFocused) onFocused() }
        .semantics { contentDescription = item.label; selected = active }
        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab, onClick = item.onClick),
        contentAlignment = Alignment.CenterStart) {
        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(18.7.r).padding(start = 1.075.r),
            verticalAlignment = Alignment.CenterVertically) {
            if (item.avatar != null) Box(Modifier.clearAndSetSemantics {}) {
                com.aotv.app.ui.settings.ProfileAvatar(item.avatar, 1.8.r)
            } else item.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(1.75.r)) }
            Text(item.label, color = fg, fontSize = 1.35.rs, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFont,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 1.3.r, end = .8.r).clearAndSetSemantics {}.graphicsLayer {
                    alpha = progress.value; translationX = -labelSlide.toPx() * (1f - progress.value)
                })
        }
    }
}

/** "Sair do AOTV?" na Início, como o webOS. O foco começa em Ficar. */
@Composable
fun TvExitDialog(onExit: () -> Unit, onStay: () -> Unit) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    Dialog(onDismissRequest = onStay) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(BackgroundCard).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Sair do AOTV?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AotvButton("Ficar", onStay, Modifier.focusRequester(stay), icon = Icons.Default.Check, tone = ButtonTone.PRIMARY)
                AotvButton("Sair", onExit, tone = ButtonTone.DANGER)
            }
        }
    }
}
