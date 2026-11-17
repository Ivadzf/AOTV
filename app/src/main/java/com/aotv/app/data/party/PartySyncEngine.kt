package com.aotv.app.data.party

/**
 * Motor de sincronização da Watch Party (Lote 19, v1.29.0) — decide o que
 * o player local tem que fazer quando chega um STATE do anfitrião.
 *
 * O anfitrião é a verdade: a posição dele no instante do envio +
 * o tempo que passou desde então (pelo relógio corrigido, ver
 * [PartyClock]) é o alvo. A decisão é pura (sem Android): o teste do
 * Lote 19 confere a tabela em Python, com os mesmos números.
 *
 * Tabela (diferença = alvo − posição local):
 * - |diferença| <= 500 ms: nada (é jitter de rede, não dessincronia).
 *   Se estava alcançando a sala, volta p/ a velocidade normal.
 * - Atrás de 500 ms a 2 s: "catch up" a 1,5× (ganha 0,5 s por segundo
 *   tocado, sem pulo — o espectador mal percebe).
 * - Atrás de mais de 2 s, ou na frente de mais de 500 ms: seek direto
 *   p/ o alvo (pulo curto p/ trás ou longo p/ frente).
 * - Tocando/pausado: o do anfitrião sempre vence (pausou lá, pausa aqui).
 *
 * Quem entrou atrasado (o filme já vai longe) NÃO usa esta tabela:
 * escolhe "entrar no ponto da sala" (seek) ou "ver do início a 1,5×"
 * (ver [PartyRuntime] — o catch-up longo é um modo, não uma decisão).
 */

/** O que o player local deve fazer com um STATE do anfitrião. */
data class PartySyncDecision(
    /** null = não muda o play/pause; senão, o estado do anfitrião. */
    val setPlaying: Boolean? = null,
    /** null = não pula; senão, a posição p/ onde ir (ms). */
    val seekToMs: Long? = null,
    /** null = não muda a velocidade; senão, a velocidade p/ usar. */
    val setRate: Float? = null,
) {
    companion object {
        /** Nada a fazer (dentro da tolerância). */
        val NONE = PartySyncDecision()
    }

    /** true quando a decisão não muda nada no player. */
    val isNone: Boolean get() = setPlaying == null && seekToMs == null && setRate == null
}

object PartySyncEngine {
    /** Diferença que ainda é jitter e não dessincronia (ms). */
    const val IGNORE_BELOW_MS = 500L

    /** Atrás até aqui, alcança a 1,5× em vez de pular (ms). */
    const val CATCH_UP_BELOW_MS = 2000L

    /** Velocidade do "catch up" (1,5×, como no MKCinema). */
    const val CATCH_UP_RATE = 1.5f

    /**
     * Decide o que fazer com um STATE do anfitrião.
     *
     * @param localPositionMs posição do player local agora (ms).
     * @param localPlaying true quando o player local está tocando.
     * @param remotePositionMs posição do anfitrião no instante do envio.
     * @param remotePlaying true quando o anfitrião estava tocando no envio.
     * @param remoteSentAtMs relógio do anfitrião no envio (ms desde epoch).
     * @param clockOffsetMs offset do relógio do anfitrião (ver [PartyClock]).
     * @param nowMs relógio local agora (ms desde epoch).
     * @param catchingUp true quando já está alcançando a sala a 1,5×.
     */
    fun decide(
        localPositionMs: Long,
        localPlaying: Boolean,
        remotePositionMs: Long,
        remotePlaying: Boolean,
        remoteSentAtMs: Long,
        clockOffsetMs: Long,
        nowMs: Long,
        catchingUp: Boolean,
    ): PartySyncDecision {
        // Onde o anfitrião está AGORA: posição do envio + o que tocou desde
        // então (só conta se ele estava tocando; parado, a posição é fixa).
        // remoteSentAtMs está no relógio DELE: o instante do envio no
        // relógio DAQUI é remoteSentAtMs − offset (ver PartyClock), então
        // o tempo passado é now − (remoteSentAtMs − offset).
        val elapsedMs = (nowMs + clockOffsetMs - remoteSentAtMs).coerceAtLeast(0L)
        val targetMs = if (remotePlaying) remotePositionMs + elapsedMs else remotePositionMs
        val diffMs = targetMs - localPositionMs

        var setPlaying: Boolean? = null
        var seekToMs: Long? = null
        var setRate: Float? = null

        // Play/pause do anfitrião sempre vence.
        if (remotePlaying != localPlaying) setPlaying = remotePlaying

        when {
            diffMs in -IGNORE_BELOW_MS..IGNORE_BELOW_MS -> {
                // Dentro da tolerância: nada. Se estava alcançando, volta ao normal.
                if (catchingUp) setRate = 1f
            }
            diffMs in (IGNORE_BELOW_MS + 1)..CATCH_UP_BELOW_MS -> {
                // Atrás pouco: alcança a 1,5×, sem pulo.
                setRate = CATCH_UP_RATE
            }
            else -> {
                // Atrás muito, ou na frente: pula direto p/ o alvo.
                seekToMs = targetMs.coerceAtLeast(0L)
                if (catchingUp) setRate = 1f
            }
        }
        return PartySyncDecision(setPlaying = setPlaying, seekToMs = seekToMs, setRate = setRate)
    }
}
