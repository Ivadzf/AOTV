package com.aotv.app.ui.movies

// Sleep timer do player (Lote 9 item 4, v1.19.0): parar a reprodução após
// 15/30/45/60/90 min ou no fim do episódio. SEM work-runtime: um Job com
// delay no escopo da sessão do player (morre junto com o player). Escolha
// por sessão — sem pref persistente. O "fim do episódio" não usa timer:
// o fim da reprodução só não avança nem volta, fica parado no último
// quadro (ver o efeito de playbackEnded em MoviePlayerScreen).

/** Opções do timer, em minutos. */
val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90)

/** Rótulo do estado atual do timer p/ o painel do player. */
internal fun sleepTimerLabel(minutes: Int?, atEpisodeEnd: Boolean): String = when {
    atEpisodeEnd -> "Pausa no fim do episódio"
    minutes != null -> "Pausa em $minutes min"
    else -> "Desligado"
}

/** Quanto falta p/ o timer disparar ("faltam 12 min"), ou null se já passou. */
internal fun sleepRemainingLabel(deadlineMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    val left = deadlineMs - nowMs
    if (left <= 0) return null
    val min = (left + 59_000L) / 60_000L
    return if (min >= 2) "faltam $min min" else "falta menos de 1 min"
}
