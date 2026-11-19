package com.aotv.app.ui.movies

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Ainda assistindo?" (Lote 24 item 10, v1.34.0) — após 3 episódios
// avançados AUTOMATICAMENTE sem nenhuma interação do usuário, o player
// PAUSA e pergunta se ele ainda está assistindo.
//
// ── REGRA ─────────────────────────────────────────────────────────────────
// - Só contam avanços AUTOMÁTICOS (a contagem do "A seguir" que chega a
//   zero e troca de episódio sozinha, ou o pulo p/ a cena pós-créditos
//   feito pelo avanço automático). O avanço manual (botão "Próximo", chip,
//   tecla de próximo, cartão "Assistir agora") NÃO conta — e, como toda
//   interação, ZERA o contador.
// - Qualquer interação zera o contador: toque na tela, tecla/D-pad,
//   gesto de volume/brilho/busca, play/pause, busca ±10 s, abrir qualquer
//   painel do player e cancelar o cartão "A seguir". Os ganchos vivem na
//   sessão ([PlayerSession.noteUserInteraction]) e são chamados nos pontos
//   de interação do MoviePlayerScreen — ver o doc da classe.
// - Ao completar o limiar, o avanço NÃO acontece: o player pausa e mostra
//   o diálogo. "Continuar" avança p/ o episódio que estava pendente e zera
//   o contador; "Parar" sai do player. Sem pref: o limiar é fixo em 3 e
//   não há ajuste nas Configurações (decisão do brief).
// - O contador é da sessão (não persiste): fechar o player zera tudo.
//
// ── API DE PLUG (p/ o coordenador, MoviePlayerScreen) ─────────────────────
//   // Campo da sessão: val stillWatching = StillWatchingTracker()
//   //   var stillWatchingAsk by mutableStateOf(false)
//   //   var stillWatchingPendingSeason by mutableStateOf(false)
//   // No efeito da contagem regressiva, depois de showNextPrompt = false:
//   //   if (stillWatching.noteAutoAdvance()) {
//   //       stillWatchingPendingSeason = promptIsSeason
//   //       stillWatchingAsk = true
//   //       runCatching { currentPlayer().playWhenReady = false }
//   //       return@LaunchedEffect
//   //   }
//   // Diálogo perto do NextEpisodeCard:
//   //   if (stillWatchingAsk) StillWatchingDialog(STILL_WATCHING_THRESHOLD,
//   //       onContinue = { stillWatchingAsk = false; stillWatching.reset()
//   //           if (stillWatchingPendingSeason) goToNextSeason() else playNextEpisode() },
//   //       onStop = { stillWatchingAsk = false; stillWatching.reset(); onBack() })

/** Nº de avanços automáticos seguidos sem interação até perguntar. */
const val STILL_WATCHING_THRESHOLD = 3

/**
 * Contador de avanços automáticos sem interação (puro, sem Compose —
 * dá p/ testar sem aparelho). O contador só cresce em [noteAutoAdvance]
 * e só zera em [noteUserInteraction] ou [reset].
 */
class StillWatchingTracker(private val threshold: Int = STILL_WATCHING_THRESHOLD) {
    /** Avanços automáticos seguidos desde a última interação. */
    var autoCount: Int = 0
        private set

    /**
     * Registra um avanço automático. Devolve true quando o limiar foi
     * atingido (é hora de pausar e perguntar) — e zera o contador, p/ o
     * próximo ciclo começar do zero depois da resposta do usuário.
     */
    fun noteAutoAdvance(): Boolean {
        autoCount += 1
        if (autoCount < threshold) return false
        autoCount = 0
        return true
    }

    /** Qualquer interação do usuário: zera o contador. */
    fun noteUserInteraction() {
        autoCount = 0
    }

    /** Zera o contador (ao responder o diálogo ou ao trocar de título). */
    fun reset() {
        autoCount = 0
    }
}

/**
 * Diálogo "Ainda assistindo?". "Continuar" segue a maratona (avança p/ o
 * episódio pendente); "Parar" sai do player. Voltar ou tocar fora é o
 * mesmo que "Parar" — o diálogo não pode ser dispensado em silêncio com
 * o vídeo parado no fim do episódio.
 */
@Composable
fun StillWatchingDialog(
    count: Int = STILL_WATCHING_THRESHOLD,
    onContinue: () -> Unit,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStop,
        title = { Text("Ainda assistindo?", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Você já assistiu a $count episódios seguidos sem tocar no controle. " +
                    "Quer continuar assistindo?",
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Continuar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onStop) {
                Text("Parar")
            }
        },
    )
}
