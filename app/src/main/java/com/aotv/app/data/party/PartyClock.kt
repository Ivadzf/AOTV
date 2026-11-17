package com.aotv.app.data.party

/**
 * Relógio da sala (Lote 19, v1.29.0) — mede o quanto o relógio do outro
 * lado está adiantado/atrasado em relação ao daqui, p/ o [PartySyncEngine]
 * saber onde o anfitrião está DE VERDADE quando o STATE dele chega.
 *
 * Método (NTP simplificado, sem servidor): ping-pong.
 * - A manda PING com `t0` = relógio de A no envio.
 * - B responde PONG com `t1` = relógio de B ao receber o PING.
 * - A recebe o PONG com `t3` = relógio de A na chegada.
 * - offset(B − A) = t1 − (t0 + t3) / 2; ida-e-volta = t3 − t0.
 *
 * O offset é a mediana das últimas amostras (tira o ruído de um pacote
 * lento). Amostra com ida-e-volta acima de 1 s é descartada (rede ruim
 * não pode virar relógio). Sem amostra, offset = 0 (relógios iguais).
 * Pura (sem Android): o teste do Lote 19 confere a conta em Python.
 */
class PartyClock {
    /** Ida-e-volta máxima aceita p/ uma amostra (ms). Acima disso, descarta. */
    companion object {
        const val MAX_RTT_MS = 1000L
        const val MAX_SAMPLES = 8
    }

    /** Uma amostra de ping-pong (offset + ida-e-volta). */
    data class ClockSample(val offsetMs: Long, val rttMs: Long)

    private val samples = ArrayDeque<ClockSample>()

    /**
     * Registra um ping-pong e devolve a amostra, ou null quando a
     * ida-e-volta passou de [MAX_RTT_MS] (amostra descartada).
     * `t0` = envio do PING (relógio local), `t1` = recepção no outro lado
     * (relógio remoto, veio no PONG), `t3` = chegada do PONG (relógio local).
     */
    fun recordPingPong(t0: Long, t1: Long, t3: Long): ClockSample? {
        val rtt = t3 - t0
        if (rtt < 0L || rtt > MAX_RTT_MS) return null
        val sample = ClockSample(offsetMs = t1 - (t0 + t3) / 2, rttMs = rtt)
        samples.addLast(sample)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
        return sample
    }

    /** Quantas amostras válidas existem agora. */
    val sampleCount: Int get() = samples.size

    /**
     * Offset mediano do relógio remoto em relação ao local (ms): positivo
     * = o relógio do outro lado está adiantado. 0 sem amostra.
     */
    val offsetMs: Long get() {
        if (samples.isEmpty()) return 0L
        val sorted = samples.map { it.offsetMs }.sorted()
        return sorted[sorted.size / 2]
    }

    /** Ida-e-volta mediana (ms). 0 sem amostra. */
    val rttMs: Long get() {
        if (samples.isEmpty()) return 0L
        val sorted = samples.map { it.rttMs }.sorted()
        return sorted[sorted.size / 2]
    }

    /** Converte um instante do relógio remoto p/ o relógio local. */
    fun remoteToLocal(remoteMs: Long): Long = remoteMs - offsetMs

    /** Converte um instante do relógio local p/ o relógio remoto. */
    fun localToRemote(localMs: Long): Long = localMs + offsetMs

    /** Limpa as amostras (saiu da sala / trocou de anfitrião). */
    fun reset() {
        samples.clear()
    }
}
