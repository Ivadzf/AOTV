package com.aotv.app.ui.movies

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.rememberReducedMotion
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay

// Camadas que aparecem por cima do vídeo. Cada uma só desenha, a regra fica no player.

/** Legenda externa desenhada pelo app, com o atraso escolhido (.pl-subs e .pl-sub-line). */
@Composable
internal fun BoxScope.ExternalSubtitle(
    cues: List<SubCue>,
    player: androidx.media3.common.Player,
    delayMs: Long,
    sizeScale: Float,
    withBg: Boolean,
    isTv: Boolean,
    raised: Boolean,
) {
    var subPos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(cues) { while (true) { subPos = player.currentPosition; delay(100) } }
    val line = cueAt(cues, subPos, delayMs)
    if (line.isEmpty()) return
    Text(
        line, color = Color.White, textAlign = TextAlign.Center,
        fontSize = (if (isTv) 24 else 18).sp * sizeScale, lineHeight = (if (isTv) 30 else 23).sp * sizeScale,
        fontWeight = FontWeight.SemiBold,
        style = androidx.compose.ui.text.TextStyle(
            shadow = if (withBg) null else androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f),
        ),
        modifier = Modifier.align(Alignment.BottomCenter)
            .padding(start = 24.dp, end = 24.dp, bottom = if (raised) 190.dp else 32.dp)
            .then(if (withBg) Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.62f)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Transmitindo para o Chromecast. A mídia roda na TV e o celular vira controle. */
@Composable
internal fun CastingOverlay(title: String) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.Cast, null, tint = Accent, modifier = Modifier.size(64.dp))
            Text("Transmitindo para a TV", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter)
            Text(title, color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Botão Pular abertura (.pl-skip). Branco com texto escuro. */
@Composable
internal fun SkipIntroButton(raised: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(end = 20.dp, bottom = if (raised) 150.dp else 34.dp)
            .heightIn(min = 48.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.SkipNext, null, tint = TextOnLight, modifier = Modifier.size(20.dp))
        Text("Pular abertura", color = TextOnLight, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Aviso curto embaixo (#toast). Some sozinho em 4 s. */
@Composable
internal fun BoxScope.PlayerToast(msg: String, onDone: () -> Unit) {
    LaunchedEffect(msg) { delay(4_000); onDone() }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 160.dp, start = 24.dp, end = 24.dp)
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF71E1E1E))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(msg, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

/** Indicador de volume e brilho durante o gesto. */
@Composable
internal fun GestureHud(label: String, frac: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (label == "Volume") Icons.Default.VolumeUp else Icons.Default.BrightnessHigh,
                null, tint = Color.White, modifier = Modifier.size(28.dp),
            )
            Text("$label ${(frac * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.width(120.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(3.dp)).background(Accent))
            }
        }
    }
}

/**
 * Carregando (.pl-loading). Arte do título ao fundo, logo respirando no meio e o spinner.
 * Com animação reduzida a logo fica parada.
 */
@Composable
internal fun PlayerLoadingOverlay(
    bannerUrl: String,
    logoUrl: String,
    posterUrl: String,
    title: String,
    episodeLine: String?,
    status: String? = null,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bannerUrl.isNotBlank()) {
            coil.compose.AsyncImage(
                model = bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.35f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.88f)))))
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val reduced = rememberReducedMotion()
            val pulse = rememberInfiniteTransition(label = "loadPulse")
            val logoScale = if (reduced) 1f else pulse.animateFloat(
                1f, 1.03f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "logoScale",
            ).value
            val logoAlpha = if (reduced) 1f else pulse.animateFloat(
                0.82f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "logoAlpha",
            ).value
            // Logo no lugar do pôster. Sem logo vai o pôster, sem pôster vai o nome.
            if (logoUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = logoUrl, contentDescription = title,
                    modifier = Modifier.widthIn(max = 340.dp).heightIn(max = 130.dp).scale(logoScale).alpha(logoAlpha),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else if (posterUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = posterUrl, contentDescription = title,
                    modifier = Modifier.height(180.dp).clip(RoundedCornerShape(14.dp)).scale(logoScale).alpha(logoAlpha),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter, maxLines = 2, textAlign = TextAlign.Center)
            }
            if (episodeLine != null) Text(episodeLine, color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            CircularProgressIndicator(color = Accent, trackColor = Color.White.copy(alpha = 0.14f), strokeWidth = 4.dp)
            if (status != null) Text(status, color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/** Situação do torrent enquanto carrega (conexões e velocidade). Null quando a fonte não é P2P. */
@Composable
internal fun rememberP2pStatus(url: String?, loading: Boolean): String? {
    var text by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(url, loading) {
        if (!loading || !com.aotv.app.data.torrent.TorrentMagnet.isMagnet(url)) { text = null; return@LaunchedEffect }
        while (true) {
            val st = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.aotv.app.data.torrent.Torrents.ifStarted()?.status(url)
            }
            text = when {
                st == null || (!st.hasMetadata && st.peers == 0) -> "P2P · procurando quem compartilha"
                !st.hasMetadata -> "P2P · ${st.peers} conexões · lendo o torrent"
                else -> "P2P · ${st.peers} conexões · ${speedLabel(st.downloadBps)}"
            }
            delay(1_000)
        }
    }
    return text
}

private fun speedLabel(bps: Int): String =
    if (bps >= 1_000_000) String.format(java.util.Locale("pt", "BR"), "%.1f MB/s", bps / 1_000_000.0)
    else "${bps / 1_000} KB/s"

/** Som de outra fonte carregando. A imagem espera parada. */
@Composable
internal fun BoxScope.ExtAudioLoading() {
    Column(
        Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
        Text("Carregando o som…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Cartão de próximo episódio ou próxima temporada (.pl-next), com contagem. */
@Composable
internal fun NextEpisodeCard(isSeason: Boolean, line: String, countdown: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
    // Foco automático no botão principal — senão o D-pad fica no player por trás e OK pausa o
    // vídeo em vez de confirmar (mesmo padrão do TvExitDialog).
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.widthIn(max = 340.dp).shadow(20.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .background(Color(0xF5141414)).border(1.dp, Border, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Eyebrow(if (isSeason) "Próxima temporada" else "Próximo episódio")
            Text(line, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.aotv.app.ui.common.AotvButton(
                    "Assistir agora (${countdown}s)", onPlay, Modifier.weight(1f).focusRequester(playFocus),
                    tone = com.aotv.app.ui.common.ButtonTone.PRIMARY, compact = true,
                )
                com.aotv.app.ui.common.AotvButton("Cancelar", onCancel, compact = true)
            }
        }
    }
}

/** Nenhuma fonte serviu (.pl-fail). O player volta ao detalhe sozinho. */
@Composable
internal fun SourceFailedOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = Danger, modifier = Modifier.size(56.dp))
            Text("Fonte indisponível", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter)
            Text("Não foi possível carregar este título agora.", color = TextSecondary, fontSize = 15.sp)
        }
    }
}
