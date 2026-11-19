package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.SoraFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tour de onboarding (Lote 25, v1.35.0, item 4 — ideia do MKCinema,
 * reimplementada com texto próprio).
 *
 * 5 capítulos curtos, pulável a qualquer momento: boas-vindas, Início,
 * busca, player, perfis. Mostra na primeira abertura após a v1.35.0
 * (pref global `onboarding_tour_seen_v1`, fora de SCOPED_KEYS) e reabre
 * em Configurações → Aparência → "Tour de boas-vindas" (não existe página
 * "Sobre" nas Configurações — a Aparência é onde vive a versão instalada).
 */

// ── Pedido de reabertura ────────────────────────────────────────────────────

/** Pede o tour a partir de outro lugar (paleta, Configurações). A Início observa e abre. */
object OnboardingTourRequest {
    private val _show = MutableStateFlow(false)
    val show: StateFlow<Boolean> = _show.asStateFlow()
    fun request() { _show.value = true }
    fun consume() { _show.value = false }
}

// ── Capítulos ───────────────────────────────────────────────────────────────

/** Um capítulo do tour: título curto + 2 frases no máximo. */
data class TourChapter(val title: String, val body: String)

/** Os 5 capítulos, na ordem em que aparecem. */
val ONBOARDING_TOUR_CHAPTERS = listOf(
    TourChapter(
        "Bem-vindo ao AOTV",
        "Filmes, séries, TV ao vivo e esportes num só lugar — tudo no aparelho, " +
            "sem conta obrigatória. Este tour tem 5 passos curtos e dá p/ pular a qualquer momento.",
    ),
    TourChapter(
        "Início",
        "O destaque no topo e as fileiras embaixo. Segure um card do Continuar assistindo " +
            "p/ marcar como assistido ou tirar da lista, e personalize as fileiras em Configurações → Layout.",
    ),
    TourChapter(
        "Busca",
        "A busca é ao vivo: os resultados aparecem enquanto você digita. Dá p/ buscar por voz " +
            "no microfone, ver as buscas recentes e mostrar a busca p/ outro aparelho pelo QR ao lado do campo.",
    ),
    TourChapter(
        "Player",
        "Toque p/ pausar, arraste p/ buscar, e use os botões p/ trocar de fonte, legenda e áudio. " +
            "O assistente (ícone na Início e na busca) entende \"pausa\", \"volta 30 s\" e \"legendas off\".",
    ),
    TourChapter(
        "Perfis",
        "Cada pessoa tem o próprio Continuar assistindo, Minha Lista e ajustes. Troque de perfil " +
            "no avatar da pílula inferior — o perfil infantil esconde conteúdo e pede PIN.",
    ),
)

/**
 * Overlay do tour: um cartão por capítulo, com avançar/pular e as bolinhas
 * de progresso. `onDone` marca o tour como visto (o host grava a pref).
 */
@Composable
fun OnboardingTourDialog(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val chapter = ONBOARDING_TOUR_CHAPTERS[page]
    val last = page == ONBOARDING_TOUR_CHAPTERS.lastIndex

    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundCard)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Passo ${page + 1} de ${ONBOARDING_TOUR_CHAPTERS.size}",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ManropeFontFamily,
                letterSpacing = 1.2.sp,
            )
            Text(
                chapter.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = SoraFontFamily,
                letterSpacing = (-0.4).sp,
            )
            Text(
                chapter.body,
                color = TextSecondary,
                fontSize = 15.sp,
                fontFamily = ManropeFontFamily,
                lineHeight = 21.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ONBOARDING_TOUR_CHAPTERS.forEachIndexed { index, _ ->
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(if (index == page) 16.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (index == page) Color.White else Color.White.copy(alpha = 0.45f)),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDone) { Text("Pular tour", color = TextSecondary) }
                Button(
                    onClick = { if (last) onDone() else page += 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                ) {
                    Text(if (last) "Começar" else "Próximo", fontSize = 14.sp)
                    if (!last) {
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (!last) {
                Text(
                    "Dá p/ rever depois em Configurações → Aparência → Tour de boas-vindas.",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                )
            }
        }
    }
}
