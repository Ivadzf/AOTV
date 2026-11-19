package com.aotv.app.ui.movies

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.TextTertiary
import com.aotv.app.ui.tv.TvScaled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Ambilight no player (Lote 24 item 8, v1.34.0) — brilho ambiente ao redor
// do vídeo com as cores dominantes do quadro atual.
//
// ── POR QUE NÃO HÁ getBitmap DO VÍDEO ─────────────────────────────────────
// O PlayerView do app usa o padrão do Media3: SurfaceView (nenhum
// setSurfaceType no código — conferido). SurfaceView NÃO expõe getBitmap:
// o quadro vai direto p/ a Surface do compositor e o app não o lê. Trocar
// p/ TextureView só p/ amostrar custaria desempenho e bateria no player
// inteiro, então a amostragem usa o que o projeto JÁ tem:
//   1. ScrubThumbnailer.frameAt(url, pos) — MediaMetadataRetriever no
//      stream (mesmo mecanismo da prévia ao arrastar a barra). Funciona em
//      fonte HTTP progressiva (MP4); HLS, torrent e DRM falham calado.
//   2. Fallback: as cores do pôster/backdrop do título (Coil), quando o
//      frame não vem — o brilho continua existindo, só não acompanha a cena.
// O painel diz isso com honestidade ("segue o pôster quando a fonte não
// deixa ler o quadro").
//
// ── CUSTO ─────────────────────────────────────────────────────────────────
// Amostra no máximo 1x a cada 1,5 s (não os ~500 ms do brief: cada
// getFrameAtTime numa fonte HTTP progressiva pode buscar na rede e 2 Hz
// roubaria banda e bateria do próprio vídeo) e num bitmap pequeno (o
// frameAt já devolve com 160 px de largura; o pôster é reduzido p/ 32 px
// antes de contar as cores). Só amostra tocando e com o toggle ligado; o
// loop morre ao sair do player ou ao desligar. Default DESLIGADO.
//
// ── ONDE O BRILHO APARECE ─────────────────────────────────────────────────
// Atrás do PlayerView, com o fundo dele transparente enquanto ligado: o
// brilho aparece nas barras do letterbox (FIT/Bands) e some quando o vídeo
// preenche a tela (Zoom) — não há "ao redor" p/ mostrar em tela cheia sem
// encolher o vídeo, e encolher o vídeo p/ caber brilho não foi pedido.
//
// ── PERSISTÊNCIA (contrato de prefs) ──────────────────────────────────────
// Chaves com sufixo _v1, por perfil, via rawPref/writeRawPref (sem tocar
// em PlatformPrefs.kt — mesmo padrão do NightModeAudio.kt):
//   pref_player_ambilight_v1 (Boolean, default false)
//   pref_player_ambilight_intensity_v1 (String com o id, default "medium")
// O acesso passa por [PlayerAmbilightSettings] (o coordenador liga a
// implementação real na junção; [InMemoryAmbilightSettings] é o fallback).

/** Intervalo entre amostras do quadro (ms). Ver o motivo no doc do arquivo. */
const val AMBILIGHT_SAMPLE_INTERVAL_MS = 1_500L

/** Chave da pref do toggle do Ambilight (Boolean, por perfil). */
const val PREF_AMBILIGHT_ENABLED_V1 = "pref_player_ambilight_v1"

/** Chave da pref da intensidade do Ambilight (String com o id, por perfil). */
const val PREF_AMBILIGHT_INTENSITY_V1 = "pref_player_ambilight_intensity_v1"

/** Intensidade do brilho (controla opacidade e extensão do gradiente). */
enum class AmbilightIntensity(val id: String, val label: String, val alpha: Float, val spread: Float) {
    LOW("low", "Baixa", 0.35f, 0.55f),
    MEDIUM("medium", "Média", 0.55f, 0.75f),
    HIGH("high", "Alta", 0.75f, 0.95f);

    companion object {
        fun fromId(id: String): AmbilightIntensity =
            entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

/** Estado do Ambilight (toggle + intensidade). */
data class AmbilightState(
    val enabled: Boolean = false,
    val intensity: AmbilightIntensity = AmbilightIntensity.MEDIUM,
)

/** Lê o estado guardado (defaults quando nunca foi salvo). */
fun loadAmbilightState(prefs: PlatformPrefs): AmbilightState {
    // Junção (Lote 24): lê as vars tipadas do PlatformPrefs (mesmas chaves
    // de antes) p/ o fluxo das Configurações acompanhar o toggle do player.
    val enabled = prefs.playerAmbilight
    val intensity = AmbilightIntensity.fromId(prefs.playerAmbilightIntensity)
    return AmbilightState(enabled, intensity)
}

/** Salva o estado (por perfil). */
fun saveAmbilightState(prefs: PlatformPrefs, state: AmbilightState) {
    prefs.playerAmbilight = state.enabled
    prefs.playerAmbilightIntensity = state.intensity.id
}

/**
 * Acesso às prefs do Ambilight p/ a tela e as Configurações (contrato de
 * junção: o coordenador liga a implementação real; a de memória é fallback).
 */
interface PlayerAmbilightSettings {
    val enabledFlow: StateFlow<Boolean>
    val intensityFlow: StateFlow<AmbilightIntensity>
    fun setEnabled(value: Boolean)
    fun setIntensity(value: AmbilightIntensity)
}

/** Implementação em memória de [PlayerAmbilightSettings] (fallback sem prefs). */
class InMemoryAmbilightSettings(initial: AmbilightState = AmbilightState()) : PlayerAmbilightSettings {
    private val enabledState = MutableStateFlow(initial.enabled)
    private val intensityState = MutableStateFlow(initial.intensity)
    override val enabledFlow: StateFlow<Boolean> = enabledState
    override val intensityFlow: StateFlow<AmbilightIntensity> = intensityState
    override fun setEnabled(value: Boolean) { enabledState.value = value }
    override fun setIntensity(value: AmbilightIntensity) { intensityState.value = value }
}

/**
 * Opacidade efetiva do brilho. Com o modo sensorial ligado o pico é
 * cortado: Alta vira a opacidade da Baixa e Média vira a da Baixa também
 * (o sensorial não deixa brilho forte piscando na tela — ver
 * SensoryFriendly.kt).
 */
fun effectiveAmbilightAlpha(intensity: AmbilightIntensity, sensoryOn: Boolean): Float =
    if (sensoryOn) AmbilightIntensity.LOW.alpha else intensity.alpha

/**
 * Cores dominantes de um bitmap (pura, testável sem aparelho): reduz p/
 * 32x32, conta os pixels em baldes de 32 níveis por canal e devolve as
 * [maxColors] cores mais frequentes, sem repetir o mesmo balde. Lista
 * vazia = bitmap inválido. Pretos quase puros (bordas de letterbox) são
 * ignorados, senão o brilho seria sempre preto.
 */
fun dominantColors(bitmap: Bitmap, maxColors: Int = 3): List<Color> {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return emptyList()
    val small = runCatching { Bitmap.createScaledBitmap(bitmap, 32, 32, true) }.getOrNull() ?: return emptyList()
    val counts = linkedMapOf<Int, Int>()
    val stepX = 32
    val stepY = 32
    for (y in 0 until stepY step 2) {
        for (x in 0 until stepX step 2) {
            val px = small.getPixel(x, y)
            val r = android.graphics.Color.red(px)
            val g = android.graphics.Color.green(px)
            val b = android.graphics.Color.blue(px)
            if (r < 12 && g < 12 && b < 12) continue
            val key = (r / 32 shl 16) or (g / 32 shl 8) or (b / 32)
            counts[key] = (counts[key] ?: 0) + 1
        }
    }
    if (small !== bitmap) runCatching { small.recycle() }
    // O balde guarda r/32 (0..7): a cor volta como o CENTRO do balde
    // (balde*32+16), não o índice cru — sem isso o brilho sairia preto.
    return counts.entries.sortedByDescending { it.value }.take(maxColors).map { entry ->
        val key = entry.key
        Color(
            red = (((key shr 16) and 0xFF) * 32 + 16) / 255f,
            green = (((key shr 8) and 0xFF) * 32 + 16) / 255f,
            blue = ((key and 0xFF) * 32 + 16) / 255f,
        )
    }
}

/**
 * Amostrador do Ambilight. Um loop só, no escopo que recebe: a cada
 * [AMBILIGHT_SAMPLE_INTERVAL_MS] tenta o quadro atual via
 * ScrubThumbnailer.frameAt e cai p/ o pôster quando o frame não vem
 * (HLS/torrent/DRM). Só amostra tocando; parado, espera o próximo ciclo
 * sem gastar rede. [stop] cancela o loop (ao desligar, trocar de fonte
 * ou sair do player). Erro de rede ou de decode nunca derruba o player:
 * a amostra falha calada e as cores anteriores continuam valendo.
 */
class AmbilightSampler {
    private var job: Job? = null
    private var posterUrl: String = ""
    private var posterBitmap: Bitmap? = null

    fun start(
        scope: CoroutineScope,
        isPlaying: () -> Boolean,
        positionMs: () -> Long,
        streamUrl: () -> String?,
        posterUrl: () -> String,
        onColors: (List<Color>) -> Unit,
    ) {
        stop()
        job = scope.launch {
            while (isActive) {
                if (isPlaying()) {
                    val colors = runCatching { sampleOnce(streamUrl(), positionMs(), posterUrl()) }.getOrNull()
                    if (!colors.isNullOrEmpty()) onColors(colors)
                }
                delay(AMBILIGHT_SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun sampleOnce(url: String?, position: Long, poster: String): List<Color> =
        withContext(Dispatchers.IO) {
            val frame = if (!url.isNullOrBlank()) ScrubThumbnailer.frameAt(url, position) else null
            if (frame != null) return@withContext dominantColors(frame)
            if (poster.isBlank()) return@withContext emptyList()
            if (poster != posterUrl) {
                posterUrl = poster
                posterBitmap?.recycle()
                posterBitmap = null
                posterBitmap = loadPosterBitmap(poster)
            }
            val bmp = posterBitmap ?: return@withContext emptyList()
            dominantColors(bmp)
        }

    private suspend fun loadPosterBitmap(url: String): Bitmap? {
        return runCatching {
            val context = appContextForAmbilight ?: return null
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = loader.execute(request)
            if (result is SuccessResult) (result.drawable as? BitmapDrawable)?.bitmap else null
        }.getOrNull()
    }

    companion object {
        /** Contexto da app, ligado pela tela 1x (o sampler não guarda Activity). */
        var appContextForAmbilight: Context? = null
    }
}

/**
 * Brilho atrás do vídeo: gradiente radial com as cores amostradas, do
 * centro p/ transparente nas bordas. A extensão segue a intensidade
 * (o raio é a maior dimensão da tela vezes [AmbilightIntensity.spread]).
 * Sem cores, não desenha nada.
 */
@Composable
fun AmbilightGlow(
    colors: List<Color>,
    intensity: AmbilightIntensity,
    sensoryOn: Boolean,
    modifier: Modifier = Modifier,
) {
    if (colors.isEmpty()) return
    val alpha = effectiveAmbilightAlpha(intensity, sensoryOn)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val radius = with(density) { maxOf(maxWidth, maxHeight).toPx() } * intensity.spread * 1.6f
        val stops = colors.take(3).map { it.copy(alpha = alpha) } + Color.Transparent
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(colors = stops, radius = radius),
            ),
        )
    }
}

/** Painel do Ambilight (toggle + intensidade + aviso honesto sobre a fonte das cores). */
@Composable
fun AmbilightPanel(
    state: AmbilightState,
    sensoryOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onIntensity: (AmbilightIntensity) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled { AmbilightPanelBody(state, sensoryOn, onToggle, onIntensity, onDismiss, true) }
    else AmbilightPanelBody(state, sensoryOn, onToggle, onIntensity, onDismiss, false)
}

@Composable
private fun AmbilightPanelBody(
    state: AmbilightState,
    sensoryOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onIntensity: (AmbilightIntensity) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    SidePanel(
        title = "Ambilight",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        sub = "Brilho ambiente com as cores do vídeo",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onToggle(!state.enabled) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ambilight", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        if (state.enabled) "Ligado — acompanha as cores do vídeo" else "Desligado",
                        color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = { v -> onToggle(v) })
            }
            Eyebrow("Intensidade", Modifier.padding(top = 10.dp, start = 4.dp))
            AmbilightIntensity.entries.forEach { level ->
                val capped = sensoryOn && level != AmbilightIntensity.LOW
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (state.intensity == level) AccentSoft else Color.White.copy(alpha = 0.05f))
                        .focusProperties { canFocus = false }
                        .clickable { onIntensity(level) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(level.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            if (capped) "Limitada à Baixa pelo modo sensorial" else "Opacidade e extensão do brilho",
                            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    if (state.intensity == level) {
                        Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text(
                "O brilho aparece nas faixas pretas do vídeo e ao redor dele. " +
                    "A cor vem do quadro atual quando a fonte deixa ler (MP4); " +
                    "em HLS, torrent ou DRM, segue as cores do pôster. Amostra " +
                    "1x a cada 1,5 s p/ não gastar bateria.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
            )
        }
    }
}
