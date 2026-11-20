package com.aotv.app.data.clips

/**
 * Re-basificar timestamps MPEG-TS — Lote VPN (v1.40.0).
 *
 * POR QUE: o clipe .ts é a concatenação dos segmentos HLS do trecho, e
 * cada segmento traz os timestamps (PTS/DTS/PCR) da origem — no ALYA
 * eles começam longe de 0 e/ou saltam entre segmentos (descontinuidade).
 * O player externo lia a duração e o tempo desses timestamps crus: o
 * trecho de ~1:00 aparecia com 2:05, começava em 1:57 e o relógio
 * voltava ~30 s a cada ~5 s, enquanto a imagem seguia normal e em
 * ordem. Re-basificar desloca todos os timestamps p/ uma linha do
 * tempo contínua começando em ~0, sem tocar no resto do pacote.
 *
 * O QUE FAZ (pacote a pacote, 188 bytes):
 * - PAT/PMT e pacotes sem timestamp passam INTACTOS (só se reescreve o
 *   que é timestamp; o sync byte, o PID e o resto do header não mudam).
 * - PCR: só existe em pacote COM adaptation field (`adaptation_field_
 *   control` 2 ou 3) com o flag PCR (bit 0x10 do byte de flags) — 33
 *   bits de base em 90 kHz + 9 bits de extensão em 27 MHz. Só a BASE é
 *   deslocada; a extensão não muda (ela é o resto dentro do tick de
 *   90 kHz). Pacote sem adaptation field não tem PCR — nada a fazer.
 * - PTS/DTS: só em pacote com PUSI cujo payload começa com `00 00 01`
 *   (início de PES) e cujo stream_id é de áudio/vídeo (0xC0–0xDF,
 *   0xE0–0xEF, 0xBD) — outros stream_ids não têm o campo de flags
 *   PTS/DTS no mesmo lugar. PTS (5 bytes) e, quando houver, DTS
 *   (5 bytes) são reescritos com os marker bits preservados.
 * - PTS/DTS/PCR são 33 bits com WRAPAROUND: toda soma é mod 2^33
 *   (8.589.934.592 ticks de 90 kHz ≈ 26,5 h) — um timestamp perto do
 *   fim do ciclo que cruza o 0 continua válido depois do deslocamento.
 * - Pacote sem sync byte (0x47) ou com tamanho restante < 188 (resto
 *   final do segmento) passa intacto — sem tentar ressincronizar.
 *
 * COMO O OFFSET É CALCULADO (pelo chamador, um por segmento — ver
 * [ClipDownloader.downloadVariantAttempt]): o offset do segmento é
 * `(início desejado − base do segmento) mod 2^33`, onde a base é o
 * primeiro PTS ou PCR do segmento e o início desejado é a duração
 * acumulada dos segmentos anteriores (em ticks de 90 kHz, da duração
 * #EXTINF da playlist). Se os timestamps já forem contínuos desde 0,
 * a base do 1º segmento é ~0 e o offset é ~0 — o fix vira no-op. Se a
 * playlist tem descontinuidade (o segmento recomeça os timestamps),
 * cada segmento é medido separadamente e cai no seu lugar certo.
 *
 * NÃO MEXIDO: clipes fMP4 (.mp4) — os tempos vivem nos boxes moof, não
 * em timestamps TS; o caminho fMP4 não passa por aqui.
 *
 * Lote dos 3 fixes (v1.41.0) — POR QUE A v1.40.0 NÃO FUNCIONOU: o
 * `firstTimestamp90k` da v1.40.0 varria os pacotes em passos fixos de 188
 * bytes a partir do byte 0 e devolvia null quando nenhum pacote trazia
 * timestamp — e null virava offset 0, ou seja, o segmento ia cru p/ o
 * arquivo (o no-op silencioso). Qualquer segmento fora do layout "188
 * bytes alinhado no byte 0" cai nesse caso: pacotes M2TS de 192 bytes
 * (4 bytes de TP_extra_header antes de cada pacote), pacotes de 204
 * bytes (16 bytes de paridade RS depois de cada pacote), prefixo antes
 * do primeiro pacote, ou bytes que nem TS são (ex.: fMP4 sem
 * #EXT-X-MAP). O teste da v1.40.0 (arquivo com a mesma linha do tempo
 * da v1.39.0: duração 2:05, gerenciador marcando 5 s) é exatamente o
 * que esse no-op produz. Por isso esta versão DETECTA o layout
 * ([detectPacketLayout]) antes de ler qualquer timestamp e REEMPACOTA
 * o segmento p/ TS limpo de 188 bytes ([rebase]) — mesmo com offset 0.
 */
object TsRebase {

    /** Tamanho de um pacote MPEG-TS. */
    const val PACKET_SIZE = 188

    /** Módulo dos timestamps de 33 bits (2^33 ticks de 90 kHz). Literal:
     *  `const val` não aceita `shl` (não é expressão constante). */
    const val TIMESTAMP_MODULUS = 8589934592L

    /** Ticks de 90 kHz por milissegundo (p/ converter duração #EXTINF). */
    const val TICKS_PER_MS = 90L

    /**
     * Layout físico dos pacotes TS dentro do segmento: a partir de qual
     * byte começa o primeiro pacote ([startOffset]) e a distância entre
     * pacotes ([stride]).
     * - (0, 188): TS padrão — o caso comum.
     * - (4, 192): M2TS — 4 bytes de TP_extra_header antes de cada
     *   pacote de 188 bytes.
     * - (0, 204): TS com paridade Reed-Solomon — 16 bytes de paridade
     *   depois de cada pacote de 188 bytes.
     * - (G, 188): G bytes de prefixo antes do primeiro pacote.
     * [packetStart] é o byte 0x47 do pacote [index]; [packetCount] é
     * quantos pacotes inteiros cabem em [size] bytes.
     */
    data class PacketLayout(val startOffset: Int, val stride: Int) {
        fun packetStart(index: Int): Int = startOffset + index * stride
        /** Pacotes TS inteiros (188 bytes) que cabem em [size] bytes — o
         *  último pacote só precisa dos 188 bytes dele, não do slot
         *  inteiro (ex.: M2TS (4, 192) num segmento de 1920 bytes tem
         *  10 pacotes, não 9). */
        fun packetCount(size: Int): Int {
            val remaining = size - startOffset - PACKET_SIZE
            return if (remaining < 0) 0 else remaining / stride + 1
        }
    }

    /**
     * Detecta o layout dos pacotes no segmento procurando o byte de sync
     * (0x47) repetido: testa os strides 188, 192 e 204 (nesta ordem — o
     * padrão primeiro) e, p/ cada um, o menor startOffset (até 4096)
     * com 0x47 em 8 pacotes seguidos (ou menos, se o segmento tiver
     * menos de 8 pacotes no total). O nº de confirmações é o do
     * SEGMENTO inteiro, não o que resta a partir do candidato — senão
     * um start perto do fim passaria com 1 só confirmação e o layout
     * sairia errado. Null = nenhum layout consistente (ex.: bytes que
     * nem TS são) — o chamador cai no layout padrão como melhor esforço.
     */
    fun detectPacketLayout(bytes: ByteArray): PacketLayout? {
        for (stride in intArrayOf(PACKET_SIZE, 192, 204)) {
            val need = minOf(8, bytes.size / stride)
            if (need == 0) continue
            val maxStart = minOf(4096, bytes.size)
            var start = 0
            while (start < maxStart) {
                // O candidato precisa ter `need` pacotes inteiros à frente.
                if (start + (need - 1) * stride < bytes.size) {
                    var ok = true
                    var k = 0
                    while (k < need) {
                        if (bytes[start + k * stride] != 0x47.toByte()) {
                            ok = false
                            break
                        }
                        k++
                    }
                    if (ok) return PacketLayout(start, stride)
                }
                start++
            }
        }
        return null
    }

    /**
     * Primeiro PTS ou PCR do segmento (ticks de 90 kHz), ou null se
     * nenhum pacote trouxer timestamp. É a "base" do segmento p/ o
     * cálculo do offset — ver o KDoc do arquivo. Lote dos 3 fixes
     * (v1.41.0): o scan respeita o layout detectado ([detectPacketLayout])
     * — antes ele assumia 188 bytes a partir do byte 0 e devolvia null
     * (offset 0, segmento cru) p/ qualquer segmento fora desse layout.
     */
    fun firstTimestamp90k(bytes: ByteArray): Long? {
        val layout = detectPacketLayout(bytes) ?: PacketLayout(0, PACKET_SIZE)
        var index = 0
        val count = layout.packetCount(bytes.size)
        while (index < count) {
            val at = layout.packetStart(index)
            if (at + PACKET_SIZE > bytes.size) break
            val packet = bytes.copyOfRange(at, at + PACKET_SIZE)
            firstTimestampInPacket(packet)?.let { return it }
            index++
        }
        return null
    }

    /**
     * Offset p/ um segmento: desloca a base dele p/ [targetStart90k]
     * (a duração acumulada dos segmentos anteriores, em ticks de 90
     * kHz). Base null (segmento sem timestamp) = 0 — passa intacto.
     * O resultado é sempre [0, 2^33): o wraparound é tratado aqui, não
     * no chamador.
     */
    fun offsetForSegment(base90k: Long?, targetStart90k: Long): Long {
        if (base90k == null) return 0L
        return mod33(targetStart90k - base90k)
    }

    /** Soma mod 2^33 (trata o wraparound dos 33 bits, inclusive negativo). */
    fun mod33(value: Long): Long {
        val m = value % TIMESTAMP_MODULUS
        return if (m < 0) m + TIMESTAMP_MODULUS else m
    }

    /**
     * Devolve uma CÓPIA do segmento com PTS/DTS/PCR deslocados de
     * [offset90k] (mod 2^33). Offset 0 devolve cópia idêntica — o
     * chamador pode pular a chamada, mas chamar com 0 é seguro.
     * Não altera PAT/PMT, pacotes sem timestamp nem o resto final.
     *
     * Lote dos 3 fixes (v1.41.0): o segmento é NORMALIZADO p/ TS limpo
     * de 188 bytes quando o layout detectado não é o padrão — o
     * TP_extra_header (192 bytes), a paridade RS (204 bytes), o prefixo
     * e o resto parcial são descartados. Isso vale MESMO com offset 0:
     * sem normalizar, um segmento M2TS com offset 0 iria cru p/ o
     * arquivo e misturaria strides com os demais segmentos. Layout
     * padrão + offset 0 continua devolvendo cópia idêntica (o resto
     * final parcial é preservado, como antes).
     */
    fun rebase(bytes: ByteArray, offset90k: Long): ByteArray {
        val layout = detectPacketLayout(bytes) ?: PacketLayout(0, PACKET_SIZE)
        if (layout.startOffset == 0 && layout.stride == PACKET_SIZE) {
            val out = bytes.copyOf()
            if (offset90k == 0L) return out
            var pos = 0
            while (pos + PACKET_SIZE <= out.size) {
                rebasePacket(out, pos, offset90k)
                pos += PACKET_SIZE
            }
            return out
        }
        val count = layout.packetCount(bytes.size)
        val out = ByteArray(count * PACKET_SIZE)
        var index = 0
        while (index < count) {
            val at = layout.packetStart(index)
            bytes.copyInto(out, index * PACKET_SIZE, at, at + PACKET_SIZE)
            if (offset90k != 0L) rebasePacket(out, index * PACKET_SIZE, offset90k)
            index++
        }
        return out
    }

    /** Primeiro timestamp (PTS, senão PCR) de UM pacote, ou null. */
    private fun firstTimestampInPacket(packet: ByteArray): Long? {
        if (packet[0] != 0x47.toByte()) return null
        // PTS vem antes do PCR na ordem do pacote? Não — o PCR mora no
        // adaptation field (header) e o PTS no payload. Tanto faz qual se
        // lê primeiro: os dois estão no mesmo relógio de 90 kHz. Lê o
        // PTS primeiro porque é o que o player usa p/ a linha do tempo.
        pesTimestamps(packet)?.let { (pts, _) -> return pts }
        return pcrInPacket(packet)
    }

    /** PCR (base de 33 bits, 90 kHz) do pacote, ou null se não houver. */
    private fun pcrInPacket(packet: ByteArray): Long? {
        val adaptation = adaptationField(packet) ?: return null
        val (start, length) = adaptation
        // PCR precisa de 7 bytes de campo (1 de flags + 6 de PCR).
        if (length < 7) return null
        val flags = packet[start].toInt() and 0xFF
        if (flags and 0x10 == 0) return null // sem flag PCR
        // PCR: 6 bytes a partir de start+1 — base de 33 bits nos 5
        // primeiros bytes + bit 7 do 6º; os 9 bits de extensão ficam.
        if (start + 7 > PACKET_SIZE) return null
        val b0 = packet[start + 1].toLong() and 0xFF
        val b1 = packet[start + 2].toLong() and 0xFF
        val b2 = packet[start + 3].toLong() and 0xFF
        val b3 = packet[start + 4].toLong() and 0xFF
        val b4 = packet[start + 5].toLong() and 0xFF
        return (b0 shl 25) or (b1 shl 17) or (b2 shl 9) or (b3 shl 1) or (b4 shr 7)
    }

    /**
     * (índice do byte de flags, tamanho) do adaptation field, ou null se
     * o pacote não tiver adaptation field. O byte de comprimento é o
     * índice 4; o de flags é o 5; o PCR (quando há) começa no 6.
     */
    private fun adaptationField(packet: ByteArray): Pair<Int, Int>? {
        val afc = (packet[3].toInt() shr 4) and 0x3
        if (afc == 2 || afc == 3) {
            val length = packet[4].toInt() and 0xFF
            // Comprimento tem que caber no pacote (1 byte de tamanho +
            // o campo em si); senão o pacote está corrompido — ignora.
            if (4 + 1 + length > PACKET_SIZE) return null
            return 5 to length
        }
        return null
    }

    /**
     * (PTS, DTS?) do payload PES do pacote, ou null se não for início de
     * PES de áudio/vídeo com PTS. DTS é null quando o header só traz PTS.
     */
    private fun pesTimestamps(packet: ByteArray): Pair<Long, Long?>? {
        val payloadStart = payloadStart(packet) ?: return null
        // Precisa de pelo menos o prefixo + stream_id + tamanho + flags.
        if (payloadStart + 9 > PACKET_SIZE) return null
        val p = payloadStart
        if (packet[p] != 0.toByte() || packet[p + 1] != 0.toByte() ||
            packet[p + 2] != 1.toByte()
        ) return null // não é início de PES
        val streamId = packet[p + 3].toInt() and 0xFF
        // Só áudio/vídeo/private-stream-1 têm PTS/DTS neste layout —
        // outros stream_ids (0xBC, 0xBE, 0xBF, 0xF0–0xF2, 0xF8, 0xFF)
        // não têm o campo de flags no mesmo lugar.
        val hasPtsLayout = streamId in 0xC0..0xDF || streamId in 0xE0..0xEF || streamId == 0xBD
        if (!hasPtsLayout) return null
        val ptsDtsFlags = (packet[p + 7].toInt() shr 6) and 0x3
        if (ptsDtsFlags == 0) return null
        if (p + 9 + 5 > PACKET_SIZE) return null
        val pts = decodeTimestamp(packet, p + 9)
        val dts = if (ptsDtsFlags == 3 && p + 14 + 5 <= PACKET_SIZE) {
            decodeTimestamp(packet, p + 14)
        } else {
            null
        }
        return pts to dts
    }

    /** Índice do 1º byte do payload, ou null se o pacote não tiver payload. */
    private fun payloadStart(packet: ByteArray): Int? {
        val afc = (packet[3].toInt() shr 4) and 0x3
        if (afc == 2) return null // só adaptation field, sem payload
        return if (afc == 3) {
            val length = packet[4].toInt() and 0xFF
            if (4 + 1 + length > PACKET_SIZE) null else 4 + 1 + length
        } else {
            4
        }
    }

    /** Decodifica um timestamp de 33 bits (5 bytes com marker bits). */
    private fun decodeTimestamp(bytes: ByteArray, at: Int): Long {
        val b0 = bytes[at].toLong() and 0xFF
        val b1 = bytes[at + 1].toLong() and 0xFF
        val b2 = bytes[at + 2].toLong() and 0xFF
        val b3 = bytes[at + 3].toLong() and 0xFF
        val b4 = bytes[at + 4].toLong() and 0xFF
        return ((b0 shr 1) and 0x07 shl 30) or (b1 shl 22) or
            ((b2 shr 1) and 0x7F shl 15) or (b3 shl 7) or ((b4 shr 1) and 0x7F)
    }

    /** Codifica um timestamp de 33 bits (5 bytes com marker bits). */
    private fun encodeTimestamp(bytes: ByteArray, at: Int, value: Long, prefix: Int) {
        val v = mod33(value)
        bytes[at] = ((prefix shl 4) or (((v shr 30) and 0x07) shl 1).toInt() or 1).toByte()
        bytes[at + 1] = ((v shr 22) and 0xFF).toByte()
        bytes[at + 2] = ((((v shr 15) and 0x7F) shl 1).toInt() or 1).toByte()
        bytes[at + 3] = ((v shr 7) and 0xFF).toByte()
        bytes[at + 4] = (((v and 0x7F) shl 1).toInt() or 1).toByte()
    }

    /** Reescreve PTS/DTS/PCR de UM pacote in-place (offset mod 2^33). */
    private fun rebasePacket(bytes: ByteArray, at: Int, offset90k: Long) {
        if (bytes[at] != 0x47.toByte()) return
        val packet = bytes.copyOfRange(at, at + PACKET_SIZE)
        // PCR (adaptation field) — reescreve a base, extensão intacta.
        // Só quando o pacote traz PCR válido (nunca inventa base 0).
        val adaptation = adaptationField(packet)
        if (adaptation != null) {
            val (start, length) = adaptation
            if (length >= 7) {
                val flags = packet[start].toInt() and 0xFF
                if (flags and 0x10 != 0 && start + 7 <= PACKET_SIZE) {
                    val pcr = pcrInPacket(packet)
                    if (pcr != null) {
                        val rebased = mod33(pcr + offset90k)
                        // Base de 33 bits nos bytes start+1..start+5; o bit 7
                        // do byte start+5 é o bit 0 da base — o resto do byte
                        // (6 bits reservados + bit 0 da extensão) não muda.
                        bytes[at + start + 1] = ((rebased shr 25) and 0xFF).toByte()
                        bytes[at + start + 2] = ((rebased shr 17) and 0xFF).toByte()
                        bytes[at + start + 3] = ((rebased shr 9) and 0xFF).toByte()
                        bytes[at + start + 4] = ((rebased shr 1) and 0xFF).toByte()
                        val keep = bytes[at + start + 5].toInt() and 0x7F
                        bytes[at + start + 5] = (keep or ((rebased and 1L) shl 7).toInt()).toByte()
                    }
                }
            }
        }
        // PTS/DTS (payload PES).
        val payloadStart = payloadStart(packet) ?: return
        val p = payloadStart
        if (p + 9 > PACKET_SIZE) return
        if (packet[p] != 0.toByte() || packet[p + 1] != 0.toByte() ||
            packet[p + 2] != 1.toByte()
        ) return
        val streamId = packet[p + 3].toInt() and 0xFF
        val hasPtsLayout = streamId in 0xC0..0xDF || streamId in 0xE0..0xEF || streamId == 0xBD
        if (!hasPtsLayout) return
        val ptsDtsFlags = (packet[p + 7].toInt() shr 6) and 0x3
        if (ptsDtsFlags == 0) return
        if (p + 9 + 5 > PACKET_SIZE) return
        val pts = decodeTimestamp(packet, p + 9)
        // Prefixo 0x2 = só PTS; 0x3 = PTS + DTS (preserva o prefixo original).
        encodeTimestamp(bytes, at + p + 9, pts + offset90k, if (ptsDtsFlags == 3) 0x3 else 0x2)
        if (ptsDtsFlags == 3 && p + 14 + 5 <= PACKET_SIZE) {
            val dts = decodeTimestamp(packet, p + 14)
            encodeTimestamp(bytes, at + p + 14, dts + offset90k, 0x1)
        }
    }
}
