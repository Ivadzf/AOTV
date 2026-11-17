package com.aotv.app.data.party

/**
 * Player que a Watch Party comanda (Lote 19, v1.29.0).
 *
 * Interface fina p/ o [PartyRuntime] não conhecer o ExoPlayer nem a tela:
 * quem implementa é a tela do player, delegando p/ o player atual (ver
 * MoviePlayerScreen — `s.currentPlayer()`). Duração desconhecida
 * (ao vivo / sem metadados) é 0.
 */
interface PartyPlayer {
    /** Posição atual (ms). */
    fun positionMs(): Long

    /** true quando está tocando (não pausado). */
    fun isPlaying(): Boolean

    /** Duração total (ms). 0 quando desconhecida. */
    fun durationMs(): Long

    /** Pula p/ a posição (ms). */
    fun seekTo(positionMs: Long)

    /** Toca (true) ou pausa (false). */
    fun setPlaying(playing: Boolean)

    /** Velocidade de reprodução (1f = normal, 1.5f = catch-up). */
    fun setSpeed(rate: Float)

    /** Velocidade "normal" do usuário (a que o catch-up restaura). */
    fun normalSpeed(): Float
}
