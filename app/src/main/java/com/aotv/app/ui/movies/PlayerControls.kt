package com.aotv.app.ui.movies

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay
import com.aotv.app.ui.tv.r

// Na TV, título e controles seguem a composição inferior do player do webOS.
// O celular usa controles por toque que se adaptam à altura e à fonte do sistema.

/** Chip da barra de baixo (Zoom, Velocidade, Legendas, Áudio, Fontes), navegável por D-pad na TV.
 * [id] é o id canônico da pref `playerButtons` (ver [DEFAULT_PLAYER_BUTTONS] em
 * PlatformPrefs): "subs", "audio", "sources", "episodes", "prev", "next", "screen",
 * "speed", "pin", "skip". O chip principal (play/pause, [main]) não entra na pref
 * e fica sempre em primeiro. */
data class PlayerChipItem(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val value: String = "",
    val main: Boolean = false,
    val id: String = "",
    val onRun: () -> Unit,
)

/**
 * Ordena os chips do player pela pref `playerButtons` (Fase C).
 * Lista vazia = ordem padrão (a que o player já montava). Ids da pref que não
 * existem no contexto atual (ex.: "prev" sem episódio anterior) são ignorados.
 * Um id canônico que está FORA da lista guardada foi desligado nas
 * Configurações e NÃO aparece; só vai para o fim um chip NOVO, cujo id nem
 * existe no catálogo da pref (função nova não pode sumir só porque o usuário
 * salvou a ordem antes dela existir). O chip principal (play/pause) fica
 * sempre em primeiro.
 */
internal fun orderPlayerChips(chips: List<PlayerChipItem>, order: List<String>): List<PlayerChipItem> {
    val main = chips.filter { it.main }
    val rest = chips.filter { !it.main }
    if (order.isEmpty()) return chips
    val known = com.aotv.app.data.local.DEFAULT_PLAYER_BUTTONS.toSet()
    val ordered = order.flatMap { wanted -> rest.filter { it.id == wanted } }
    val leftovers = rest.filter { chip -> chip.id !in known && chip.id !in order.toSet() }
    return main + ordered + leftovers
}

/**
 * Botões do topo do player pela pref `playerTopActions` (Fase C).
 * Lista vazia = padrão ([DEFAULT_PLAYER_TOP_ACTIONS]). Ids desconhecidos são
 * ignorados. "Voltar" não entra aqui: sempre aparece, por último.
 */
internal fun orderTopActions(order: List<String>): List<String> {
    if (order.isEmpty()) return com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS
    val known = com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS.toSet()
    return order.filter { it in known }.distinct()
}

/**
 * Recolhe/expande a barra de botões do player (Lote 12 — Frente 2, v1.22.0,
 * igual ao Nuvio: barra recolhida + ">" p/ expandir).
 *
 * Recolhido (só no celular): o botão principal + os 5 primeiros botões da
 * ordem configurada + o chevron ">" ([id] "expand"). Expandido: todos os
 * botões + o chevron "<" ([id] "collapse") p/ recolher de novo. Na TV não
 * recolhe (o D-pad precisa de todos os botões na mesma fileira) e com 5 ou
 * menos botões não há o que recolher. Os chevrons NÃO entram na lista
 * canônica [DEFAULT_PLAYER_BUTTONS] nem na pref de ordem: são estado da
 * sessão, criados aqui depois da ordenação.
 */
internal fun collapsePlayerChips(
    chips: List<PlayerChipItem>,
    expanded: Boolean,
    isTv: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
): List<PlayerChipItem> {
    if (isTv) return chips
    val main = chips.filter { it.main }
    val rest = chips.filter { !it.main }
    if (rest.size <= 5) return chips
    return if (expanded) {
        chips + PlayerChipItem(Icons.Default.ChevronLeft, "Menos opções", id = "collapse") { onCollapse() }
    } else {
        main + rest.take(5) + PlayerChipItem(Icons.Default.ChevronRight, "Mais opções", id = "expand") { onExpand() }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Largura que os botões do topo direito ocupam, para o título não passar por baixo deles. */
internal fun topActionsWidth(isTv: Boolean): Dp = if (isTv) (48 * 2 + 8 + 28).dp else (48 * 4 + 8 * 3 + 28).dp

@Composable
internal fun CustomPlayerControls(
    title: String,
    clock: String,
    subtitle: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onToggle: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onSeekTo: (Long) -> Unit,
    videoUrl: String = "",
    isTv: Boolean = false,
    tvChips: List<PlayerChipItem> = emptyList(),
    tvChipIdx: Int = -1,
    resolution: String = "",
    formatTags: List<String> = emptyList(),
    playbackInfo: String = "",
    buffered: Long = 0,
    /** Tocar na barra busca (pref `playerTapSeek`). Desligado, só o arraste busca. */
    tapSeek: Boolean = true,
    /** Vidro fosco/blur nas folhas do player (pref `pref_panel_blur`, Lote 4). */
    panelBlur: Boolean = true,
    /** Divisões da barra (Lote 16 item 5): intro/recap/créditos da IntroDB. */
    markers: List<ProgressMarker> = emptyList(),
) {
    if (isTv) {
        TvPlayerControls(title, subtitle, clock, resolution, formatTags, playbackInfo, position, duration, buffered, onSeekTo, tvChips, tvChipIdx, markers = markers)
        return
    }
    TouchPlayerControls(title, subtitle, clock, resolution, formatTags, playbackInfo,
        isPlaying, position, duration, buffered, videoUrl, onToggle, onSeekBack, onSeekFwd, onSeekTo, tvChips, tvChipIdx, tapSeek = tapSeek, panelBlur = panelBlur, markers = markers)
}

/** Tempo, barra fina com bolinha e duração na mesma linha (.pl-bar-row). Toque ou arraste busca.
 * O toque já existia antes da Fase C: são dois `pointerInput` separados — o de
 * toque (`detectTapGestures`) e o de arraste (`detectHorizontalDragGestures`) — e
 * o de arraste só consome o gesto depois de passar do limiar, então um toque
 * rápido chega ao detector de toque sem conflito. [tapSeek] (pref
 * `playerTapSeek`) só liga/desliga o toque; desligado, só o arraste busca. */
@Composable
internal fun PlayerSeekRow(position: Long, duration: Long, onSeekTo: (Long) -> Unit, videoUrl: String, buffered: Long = 0, showTimes: Boolean = true, tapSeek: Boolean = true, markers: List<ProgressMarker> = emptyList()) {
    val total = if (duration > 0) duration else 0L
    val frac = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
    var barW by remember { mutableFloatStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubX by remember { mutableFloatStateOf(0f) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    // Prévia do frame ao arrastar, tipo YouTube. Só busca quando a posição muda de janela de
    // 1.5s (não a cada pixel). Mantém o último frame bom na tela em vez de apagar — tanto
    // enquanto o próximo carrega (sem isso piscava preto a cada novo arraste, mesmo já tendo
    // mostrado imagem antes) quanto se essa janela especificamente falhar (ScrubThumbnailer
    // explica por quê). Só um resultado de verdade substitui o que já estava lá.
    LaunchedEffect(scrubbing, scrubMs / 1500, videoUrl) {
        if (!scrubbing || total <= 0 || videoUrl.isBlank()) return@LaunchedEffect
        delay(120)
        ScrubThumbnailer.frameAt(videoUrl, scrubMs)?.let { preview = it }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { barW = it.width.toFloat().coerceAtLeast(1f) }
                .then(
                    if (tapSeek) Modifier.pointerInput(total) {
                        detectTapGestures { off -> if (total > 0) onSeekTo(((off.x / barW).coerceIn(0f, 1f) * total).toLong()) }
                    } else Modifier
                )
                .pointerInput(total) {
                    detectHorizontalDragGestures(
                        onDragStart = { scrubbing = true },
                        onDragEnd = { scrubbing = false },
                        onDragCancel = { scrubbing = false },
                    ) { change, _ ->
                        if (total <= 0) return@detectHorizontalDragGestures
                        val x = change.position.x.coerceIn(0f, barW)
                        scrubX = x
                        scrubMs = ((x / barW) * total).toLong()
                        onSeekTo(scrubMs)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f)))
            val buffer = if (total > 0) (buffered.toFloat() / total).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(buffer).height(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .35f)))
            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.requiredSize(12.dp).clip(CircleShape).background(Color.White))
            }
            // Lote 16 item 5: ticks de intro/recap/créditos (início e fim do
            // trecho, cor de destaque) — a barra fica dividida sem tocar no
            // gesto de busca.
            markers.forEach { m ->
                if (total > 0) {
                    val sFrac = (m.startMs.toFloat() / total).coerceIn(0f, 1f)
                    val eFrac = (m.endMs.toFloat() / total).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(sFrac), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.requiredWidth(2.dp).height(10.dp).background(Accent))
                    }
                    Box(Modifier.fillMaxWidth(eFrac), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.requiredWidth(2.dp).height(10.dp).background(Accent))
                    }
                }
            }
            if (scrubbing) ScrubPreview(
                bitmap = preview, timeMs = scrubMs, xPx = scrubX, barWidthPx = barW,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (showTimes) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimePill(formatTime(position))
            TimePill(if (total > 0) formatTime(total) else "AO VIVO")
        }
    }
}

/** Miniatura flutuante acima do ponto arrastado (.scrub-preview), tipo YouTube. */
@Composable
private fun ScrubPreview(bitmap: Bitmap?, timeMs: Long, xPx: Float, barWidthPx: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val widthDp = 96.dp
    val widthPx = with(density) { widthDp.toPx() }
    val minX = widthPx / 2
    val maxX = (barWidthPx - widthPx / 2).coerceAtLeast(minX)
    val centerX = xPx.coerceIn(minX, maxX)
    Column(
        modifier
            .offset { IntOffset((centerX - widthPx / 2).toInt(), with(density) { (-68).dp.roundToPx() }) }
            .width(widthDp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(4.dp))
        TimePill(formatTime(timeMs))
    }
}

/** Chips centralizados que rolam de lado quando não cabem. TV usa a lista com foco por D-pad. */
@Composable
private fun PlayerChipRow(tvMode: Boolean, tvChips: List<PlayerChipItem>, tvChipIdx: Int, chips: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tvMode) tvChips.forEachIndexed { i, chip ->
                PlayerChip(chip.icon, chip.label, focusedByDpad = i == tvChipIdx, onClick = chip.onRun)
            } else chips()
        }
    }
}

/** Pílula de tempo (.pl-time). */
@Composable
internal fun TimePill(text: String) {
    Text(
        text, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Botão redondo dos controles. Nunca menor que 48dp. */
@Composable
internal fun CtlButton(icon: ImageVector, desc: String, size: Dp, big: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size.coerceAtLeast(48.dp))
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Black.copy(alpha = if (big) 0.5f else 0.42f))
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (focused) TextOnLight else Color.White, modifier = Modifier.size(if (big) 44.dp else if (size >= 56.dp) 30.dp else 24.dp))
    }
}

/** Chip do player (.pl-chip). Ativo azul, foco branco com texto escuro. 48dp de altura. */
@Composable
internal fun PlayerChip(icon: ImageVector, label: String, active: Boolean = false, focusedByDpad: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val on = focused || focusedByDpad
    val bg = when { on -> Color.White; active -> AccentFill; else -> Color.White.copy(alpha = 0.14f) }
    val fg = if (on) TextOnLight else Color.White
    Row(
        Modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = 1)
    }
}

/** Botões do topo direito. Transmitir, Girar e Travar (só celular) e Voltar.
 * [visibleActions] vem da pref `playerTopActions` (ver [orderTopActions]):
 * só "cast"/"rotate"/"lock", na ordem escolhida; "Voltar" sempre aparece.
 * [onMinimize] é o botão de minimizar p/ o mini player (Fase C): só aparece
 * quando não é nulo (mini player ligado e celular); não entra na pref.
 * O "cast" é um CtlButton comum (fix do crash da v1.12.0): o [MediaRouteButton]
 * de verdade (AndroidView, Lote 4) crashava o app ao aparecer junto com os
 * controles no toque — confirmado pelo Davi ao desligar o "Transmitir". O botão
 * abre o diálogo do sistema (escolher a TV / desconectar) só quando tocado,
 * sob runCatching. NUNCA voltar o MediaRouteButton aqui. A sessão em si
 * continua a mesma de antes (SessionAvailabilityListener + LaunchedEffect em
 * MoviePlayerScreen): conectar manda a fonte atual p/ a TV e silencia o local;
 * desconectar volta p/ o celular no ponto onde a TV estava. */
@Composable
internal fun PlayerTopActions(
    isTv: Boolean,
    onRotate: () -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit,
    visibleActions: List<String> = com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS,
    onMinimize: (() -> Unit)? = null,
) {
    if (isTv) return
    Row(
        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)).padding(top = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onMinimize != null) CtlButton(Icons.Default.KeyboardArrowDown, "Minimizar", 48.dp, onClick = onMinimize)
        if ("cast" in visibleActions) {
            // Cast (v1.12.0 fix do crash no toque): o MediaRouteButton de verdade
            // (AndroidView) crashava o app ao aparecer junto com os controles no
            // toque — confirmado pelo Davi ao desligar o "Transmitir". Volta o
            // botão Compose comum, que abre o seletor de rota do sistema só
            // quando tocado (mesmo comportamento pré-Lote 4). O diálogo abre sob
            // runCatching: se o Cast não inicializar neste aparelho, mostra um
            // aviso em vez de fechar o app. A sessão em si continua a mesma
            // (SessionAvailabilityListener em MoviePlayerScreen).
            val context = LocalContext.current
            CtlButton(Icons.Default.Cast, "Transmitir", 48.dp, onClick = {
                val activity = context as? android.app.Activity ?: return@CtlButton
                runCatching {
                    androidx.mediarouter.app.MediaRouteChooserDialog(activity).apply {
                        routeSelector = castRouteSelector()
                        show()
                    }
                }.onFailure {
                    Toast.makeText(context, "Cast indisponível neste aparelho", Toast.LENGTH_SHORT).show()
                }
            })
        }
        // Girar e Travar só no celular, na TV o controle já faz.
        if (!isTv) {
            if ("rotate" in visibleActions) CtlButton(Icons.Default.ScreenRotation, "Girar a tela", 48.dp, onClick = onRotate)
            if ("lock" in visibleActions) CtlButton(Icons.Default.Lock, "Travar a tela", 48.dp, onClick = onLock)
        }
        CtlButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", 48.dp, onClick = onBack)
    }
}
