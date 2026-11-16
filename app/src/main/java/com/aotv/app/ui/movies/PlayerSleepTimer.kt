package com.aotv.app.ui.movies

// Sleep timer do player (Lote 9 item 4, v1.19.0): parar a reprodução após
// 15/30/45/60/90 min ou no fim do episódio. SEM work-runtime: um Job com
// delay no escopo da sessão do player (morre junto com o player). Escolha
// por sessão — sem pref persistente. O "fim do episódio" não usa timer:
// o fim da reprodução só não avança nem volta, fica parado no último
// quadro (ver o efeito de playbackEnded em MoviePlayerScreen).
//
// Lote 16 item 6 (v1.26.0) — "voltar liga legendas": quando o timer
// pausa, o estado da legenda é capturado ([SleepWakeState]); ao voltar
// a tocar, o coordenador reativa a legenda se ela estava ligada (ver
// [captureSleepWakeState] / [sleepShouldRestoreSubtitles] e a API de
// plug abaixo). "Esperar pausa no diálogo": NÃO implementado — ver
// [SLEEP_WAIT_DIALOGUE_SUPPORTED].
//
// ── API DE PLUG (p/ o coordenador, MoviePlayerScreen) ────────────────
//   // Na sessão, junto de sleepMinutes/sleepAtEpisodeEnd:
//   var sleepWakeState by run { mutableStateOf<SleepWakeState?>(null) }
//   // No Job do timer, ANTES de pausar:
//   sleepWakeState = captureSleepWakeState(subtitleOn, selectedSubUrl)
//   currentPlayer().playWhenReady = false
//   // Ao voltar a tocar (play manual depois da pausa do timer, ou o
//   // botão "voltar" do cartão): se sleepShouldRestoreSubtitles(state)
//   // for true, reativa a legenda como ela estava (faixa embutida por
//   // pickAutoSub/enableTextGroup, ou externa por selectedSubUrl) e
//   // zera sleepWakeState = null (só vale 1x, p/ a pausa do timer).

/** Opções do timer, em minutos. */
val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90)

/** Rótulo do estado atual do timer p/ o painel do player. */
internal fun sleepTimerLabel(minutes: Int?, atEpisodeEnd: Boolean): String = when {
    atEpisodeEnd -> "Pausa no fim do episódio"
    minutes != null -> "Pausa em $minutes min"
    else -> "Desligado"
}

/** Rótulo da opção "pausar no fim do episódio" (o timer já existe na sessão — ver startSleepAtEpisodeEnd). */
internal fun sleepAfterEpisodeLabel(): String = "No fim do episódio"

/** Quanto falta p/ o timer disparar ("faltam 12 min"), ou null se já passou. */
internal fun sleepRemainingLabel(deadlineMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    val left = deadlineMs - nowMs
    if (left <= 0) return null
    val min = (left + 59_000L) / 60_000L
    return if (min >= 2) "faltam $min min" else "falta menos de 1 min"
}

/**
 * Estado da legenda no momento em que o sleep timer pausa — p/ reativar
 * ao voltar. [externalSubUrl] é a legenda externa em uso (null = faixa
 * embutida ou nenhuma); sem ele, a restauração religa a faixa embutida
 * que o `pickAutoSub` escolheria (a do idioma do perfil).
 */
data class SleepWakeState(
    val subtitlesWereOn: Boolean,
    val externalSubUrl: String? = null,
)

/** Captura o estado da legenda antes da pausa do timer. */
fun captureSleepWakeState(subtitleOn: Boolean, externalSubUrl: String? = null): SleepWakeState =
    SleepWakeState(subtitleOn, externalSubUrl)

/** true quando, ao voltar, a legenda deve ser reativada (ela estava ligada na pausa). */
fun sleepShouldRestoreSubtitles(wake: SleepWakeState?): Boolean =
    wake?.subtitlesWereOn == true

/**
 * "Esperar pausa no diálogo": NÃO suportado (Lote 16 item 6 — gap
 * explícito, sem fingir que funciona).
 *
 * O único caminho no Android seria o `Visualizer` na sessão de áudio
 * (amplitude p/ detectar silêncio), mas: (1) ele exige a permissão
 * RECORD_AUDIO concedida — hoje ela só é pedida no fluxo da busca por
 * voz (VoiceSearch), nunca no player; pedir uma permissão de microfone
 * no meio do filme p/ um timer seria pedir acesso sem relação com o
 * que o usuário está fazendo; (2) o `Visualizer` falha em vários
 * aparelhos (sessão sem efeito disponível) e (3) amplitude NÃO é
 * diálogo: cena quieta de música ou pausa entre frases dispararia a
 * pausa no meio da fala, e cena barulhenta sem fala nunca pausaria.
 * Sem um detector de fala de verdade (sem backend de terceiro e sem
 * modelo local no app), a pausa-no-diálogo ficaria errada na maioria
 * das vezes — por isso o timer entrega só minutos + fim do episódio
 * + restaurar legendas, e esta flag fica em false p/ a UI não oferecer
 * a opção.
 */
const val SLEEP_WAIT_DIALOGUE_SUPPORTED = false

/** Motivo da indisponibilidade (p/ a UI, se um dia mostrar a opção cinza). */
fun sleepWaitDialogueUnavailableReason(): String =
    "Pausar no silêncio entre falas precisaria ouvir o áudio do filme " +
        "(permissão de microfone no player) e ainda confundiria música " +
        "baixa com pausa de diálogo — sem detector de fala no app, a " +
        "opção não existe."
