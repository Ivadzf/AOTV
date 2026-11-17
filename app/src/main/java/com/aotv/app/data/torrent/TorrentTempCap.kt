package com.aotv.app.data.torrent

/**
 * Teto de espaço temporário do torrent + descarte do já assistido
 * (Lote 18 item 2, v1.28.0 — fecha o gap do Lote 5).
 *
 * Por que NÃO é um "tamanho de cache": o libtorrent 2.x tirou o cache de
 * disco (I/O por mmap) e o `SettingsPack` do libtorrent4j 2.1.0-39 não
 * tem `cache_size` (conferido no jar: só `maxQueuedDiskBytes`, que é
 * fila de escrita, não cache). O que este arquivo faz, por outro ângulo:
 *
 * 1. TETO (32–256 MB, padrão 128 MB, pref `torrent_temp_limit_mb`): o
 *    motor só guarda em `cacheDir/torrents` até esse teto. A janela de
 *    pré-download à frente da reprodução também encolhe com o teto
 *    (teto/4, mín. 4 MB, máx. 24 MB) — o motor não sai baixando o
 *    arquivo inteiro à frente.
 * 2. DESCARTE do já assistido (pref `torrent_discard_watched`, padrão
 *    ligado): quando o diretório do torrent passa do teto, as peças
 *    totalmente atrás da posição assistida têm os blocos liberados no
 *    disco com `fallocate(PUNCH_HOLE)` (best-effort: se o sistema de
 *    arquivos não suportar, nada quebra — o teto só não é cumprido
 *    naquele aparelho) e a prioridade vira IGNORE, p/ o motor não
 *    pedi-las de novo. Se o usuário voltar nelas (seek p/ trás), o
 *    motor marca a peça p/ baixar de novo e roda `forceRecheck()` 1x
 *    p/ o libtorrent reconhecer que a peça sumiu — ver
 *    [TorrentStreamer]. Sem o descarte ligado, o teto NÃO é cumprido
 *    (não há como apagar peça sem rebaixar) e o app só registra.
 * 3. APAGAR TUDO AO PARAR: sair do player (`stopAll`) ou 45 s ocioso
 *    remove o torrent da sessão com DELETE_FILES + apaga o diretório
 *    (comportamento do Lote 5, mantido).
 * 4. SEM SEED: ao completar o arquivo escolhido o torrent é pausado
 *    (não fica compartilhando o arquivo completo com o player aberto);
 *    ao parar, tudo é apagado (item 3). Durante o streaming o upload
 *    é o do protocolo (pares retribuem) — sem ele o download trava.
 *
 * Tudo aqui é lógica pura (sem Android, sem libtorrent) p/ ser testável.
 */
object TorrentTempCap {

    /** Chave da pref do teto, em MB (global, do aparelho). */
    const val KEY_TEMP_LIMIT_MB = "torrent_temp_limit_mb"

    /** Chave da pref do descarte do já assistido (global, do aparelho). */
    const val KEY_DISCARD_WATCHED = "torrent_discard_watched"

    /** Menor teto que a tela oferece. */
    const val MIN_LIMIT_MB = 32

    /** Maior teto que a tela oferece. */
    const val MAX_LIMIT_MB = 256

    /** Teto padrão (128 MB). */
    const val DEFAULT_LIMIT_MB = 128

    /** Teto padrão em bytes. */
    const val DEFAULT_TEMP_LIMIT_BYTES = DEFAULT_LIMIT_MB * 1024L * 1024L

    /** Janela máxima de pré-download à frente da reprodução (24 MB). */
    const val MAX_WINDOW_BYTES = 24L * 1024 * 1024

    /** Janela mínima de pré-download (4 MB — menos que isso trava toda hora). */
    const val MIN_WINDOW_BYTES = 4L * 1024 * 1024

    /** Teto fora da faixa volta p/ dentro dela (32–256 MB). */
    fun clampLimitMb(mb: Int): Int = mb.coerceIn(MIN_LIMIT_MB, MAX_LIMIT_MB)

    /** Quanto o diretório passou do teto (0 = dentro do teto). */
    fun overBy(dirBytes: Long, capBytes: Long): Long = (dirBytes - capBytes).coerceAtLeast(0L)

    /** Janela de pré-download p/ um teto (teto/4, entre 4 MB e 24 MB). */
    fun windowBytes(capBytes: Long): Long =
        (capBytes / 4).coerceIn(MIN_WINDOW_BYTES, MAX_WINDOW_BYTES)

    /**
     * Peças totalmente assistidas e dentro do arquivo escolhido: a peça
     * `p` só entra quando o fim dela está atrás do byte assistido
     * (`watchedThroughPiece` é a peça ONDE está o byte assistido — ela
     * ainda está sendo vista e fica fora). Peça fora de
     * [firstPiece, lastPiece] nunca entra.
     */
    fun discardablePieces(firstPiece: Int, lastPiece: Int, watchedThroughPiece: Int): List<Int> =
        (firstPiece until watchedThroughPiece).filter { it <= lastPiece }

    /**
     * Faixa da peça dentro do ARQUIVO escolhido, em bytes relativos ao
     * início do arquivo: (início, fim-exclusivo). Null quando a peça não
     * toca o arquivo (peça de outro arquivo do torrent, ou faixa vazia).
     * Uma peça pode cruzar a fronteira entre dois arquivos do torrent —
     * aqui só vai a parte que cai dentro deste arquivo.
     */
    fun pieceFileRange(piece: Int, fileOffset: Long, pieceLength: Int, fileSize: Long): Pair<Long, Long>? {
        val pieceStart = piece.toLong() * pieceLength
        val pieceEnd = pieceStart + pieceLength
        val start = maxOf(pieceStart, fileOffset) - fileOffset
        val end = minOf(pieceEnd, fileOffset + fileSize) - fileOffset
        if (start < 0L || end <= start || start >= fileSize) return null
        return start to end
    }
}
