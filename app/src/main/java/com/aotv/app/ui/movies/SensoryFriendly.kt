package com.aotv.app.ui.movies

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.TextTertiary
import com.aotv.app.ui.tv.TvScaled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Modo sensorial (Lote 24 item 9, v1.34.0) — um toggle único que suaviza
// estouros e pisca-pisca.
//
// ── O QUE ELE FAZ (3 frentes, sem efeito novo inventado) ──────────────────
// (a) ÁUDIO — comprime a dinâmica: REUSA o compressor do modo noturno
// (NightModeController / NightModeDrc, DynamicsProcessing na sessão de
// áudio). Com o sensorial ligado, o estado efetivo aplicado é o do
// usuário com o DRC forçado ([sensoryEffectiveNightState]): nightOn =
// true e intensidade mínima de 80. NÃO cria uma 2ª instância de
// DynamicsProcessing na mesma sessão (o framework não garante o encadeamento
// de dois compressores iguais) e respeita o bloqueio de passthrough do
// NightModeController (com bitstream não há o que comprimir — ver
// NightModeAudio.kt). Aparelho sem DynamicsProcessing cai no fallback de
// ganho do modo noturno, com a mesma honestidade de lá.
// (b) VÍDEO/UI — reduz pisca-pisca e transições bruscas: as animações de
// entrada/saída dos controles e do botão de pular trecho viram instantâneas
// ([sensoryEnterTransition]/[sensoryExitTransition] com tween 0) e o
// Ambilight tem o pico de brilho cortado ([effectiveAmbilightAlpha]).
// (c) HERÓI — o auto-rotate do herói da Início não avança enquanto o modo
// estiver ligado ([heroAutoRotateAllowed]; a rotação é o item 2 do Lote 24,
// Frente A, e o call-site faz AND do flag dela com este — ver
// MoviesHomeScreen.kt).
//
// ── PERSISTÊNCIA (contrato de prefs) ──────────────────────────────────────
// Chave com sufixo _v1, por perfil, via rawPref/writeRawPref (sem tocar
// em PlatformPrefs.kt — mesmo padrão do NightModeAudio.kt):
//   pref_player_sensory_friendly_v1 (Boolean, default false)
// O acesso passa por [PlayerSensorySettings] (o coordenador liga a
// implementação real na junção; [InMemorySensorySettings] é o fallback).

/** Chave da pref do modo sensorial (Boolean, por perfil). */
const val PREF_SENSORY_FRIENDLY_V1 = "pref_player_sensory_friendly_v1"

/** Intensidade mínima do DRC quando o sensorial força o compressor (0..100). */
const val SENSORY_DRC_INTENSITY = 80

/** Estado do modo sensorial (só o toggle). */
data class SensoryFriendlyState(val enabled: Boolean = false)

/** Lê o estado guardado (default desligado quando nunca foi salvo). */
fun loadSensoryFriendlyState(prefs: PlatformPrefs): SensoryFriendlyState =
    // Junção (Lote 24): var tipada do PlatformPrefs (mesma chave de antes)
    // p/ o fluxo das Configurações acompanhar o toggle do player.
    SensoryFriendlyState(prefs.playerSensoryFriendly)

/** Salva o estado (por perfil). */
fun saveSensoryFriendlyState(prefs: PlatformPrefs, state: SensoryFriendlyState) {
    prefs.playerSensoryFriendly = state.enabled
}

/**
 * Acesso à pref do modo sensorial p/ a tela e as Configurações (contrato
 * de junção: o coordenador liga a implementação real; a de memória é fallback).
 */
interface PlayerSensorySettings {
    val enabledFlow: StateFlow<Boolean>
    fun setEnabled(value: Boolean)
}

/** Implementação em memória de [PlayerSensorySettings] (fallback sem prefs). */
class InMemorySensorySettings(initial: Boolean = false) : PlayerSensorySettings {
    private val enabledState = MutableStateFlow(initial)
    override val enabledFlow: StateFlow<Boolean> = enabledState
    override fun setEnabled(value: Boolean) { enabledState.value = value }
}

/**
 * Estado de áudio efetivo com o sensorial ligado: o DRC do modo noturno é
 * forçado (nightOn = true, intensidade mínima [SENSORY_DRC_INTENSITY]) e o
 * Clear Dialogue do usuário é preservado como está. Desligado, devolve o
 * estado do usuário sem tocar em nada.
 */
fun sensoryEffectiveNightState(base: NightModeState, sensoryOn: Boolean): NightModeState {
    if (!sensoryOn) return base
    return base.copy(nightOn = true, intensity = maxOf(base.intensity, SENSORY_DRC_INTENSITY))
}

/**
 * Transição de entrada dos controles/painéis com o sensorial ligado:
 * instantânea (tween 0), sem fade que pisca. Desligado, o fade normal.
 */
fun sensoryEnterTransition(sensoryOn: Boolean): EnterTransition =
    if (sensoryOn) fadeIn(animationSpec = tween(0)) else fadeIn()

/** Transição de saída, igual à de entrada (instantânea com o sensorial ligado). */
fun sensoryExitTransition(sensoryOn: Boolean): ExitTransition =
    if (sensoryOn) fadeOut(animationSpec = tween(0)) else fadeOut()

/**
 * true quando o herói da Início pode auto-rotacionar. Com o sensorial
 * ligado, não rotaciona (o usuário troca de destaque à mão).
 */
fun heroAutoRotateAllowed(sensoryOn: Boolean): Boolean = !sensoryOn

/** Painel do modo sensorial (toggle único + explicação do que ele muda). */
@Composable
fun SensoryFriendlyPanel(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled { SensoryFriendlyPanelBody(enabled, onToggle, onDismiss, true) }
    else SensoryFriendlyPanelBody(enabled, onToggle, onDismiss, false)
}

@Composable
private fun SensoryFriendlyPanelBody(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    SidePanel(
        title = "Modo sensorial",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        sub = "Suaviza estouros de volume e pisca-pisca",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onToggle(!enabled) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Modo sensorial", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        if (enabled) "Ligado — som comprimido e transições suaves" else "Desligado",
                        color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { v -> onToggle(v) })
            }
            Text(
                "Com o modo ligado: o áudio passa por compressão (sussurros sobem, " +
                    "explosões não estouram — o mesmo compressor do modo noturno, " +
                    "com intensidade mínima de 80); os controles e o botão de pular " +
                    "trecho aparecem e somem sem fade; o Ambilight fica limitado " +
                    "ao brilho Baixo; e o herói da Início para de trocar sozinho. " +
                    "Com passthrough (bitstream p/ o receiver), a compressão não " +
                    "se aplica — o som vai direto e não passa pelo processamento.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 8.dp),
            )
        }
    }
}
