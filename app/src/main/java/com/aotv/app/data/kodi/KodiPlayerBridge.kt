package com.aotv.app.data.kodi

import androidx.media3.common.Player
import java.lang.ref.WeakReference

/**
 * Ponte de playback Kodi <-> ExoPlayer (v1.2, Fase 2).
 *
 * O xbmc.Player do Python lê daqui (via Chaquopy: `from
 * com.aotv.app.data.kodi import KodiPlayerBridge`) para refletir o estado
 * REAL do ExoPlayer — isPlaying, getTime/getTotalTime, seekTime e pause
 * operam no player do app, não num estado neutro. Sem player anexado
 * (nada tocando), tudo devolve o valor neutro (parado, tempo 0).
 *
 * `object` + `@JvmStatic` de propósito: o Python importa a classe e chama
 * os métodos estáticos sem precisar de instância Hilt. A referência ao
 * player é fraca (nunca segura o ExoPlayer vivo) e todos os getters do
 * ExoPlayer usados aqui são thread-safe (o Python chama desta ponte a
 * partir da thread do dispatch).
 *
 * Ciclo de vida: MoviePlayerScreen anexa o ExoPlayer ao abrir e desanexa
 * ao sair; a cada troca de fonte/episódio atualiza arquivo + título.
 * Cast (Chromecast) não passa por aqui — a ponte reflete o player local.
 */
object KodiPlayerBridge {

    @Volatile
    private var playerRef: WeakReference<Player>? = null

    /** Arquivo em reprodução (URL do stream) — vazio quando nada toca. */
    @Volatile
    @JvmStatic
    var playingFile: String = ""
        private set

    /** Título em reprodução (p/ Player.Title / VideoPlayer.Title). */
    @Volatile
    var playingTitle: String = ""
        private set

    /** Anexa o player do app (chamado na abertura do player). */
    @JvmStatic
    fun attach(player: Player, file: String, title: String) {
        playerRef = WeakReference(player)
        playingFile = file
        playingTitle = title
    }

    /** Atualiza arquivo + título (troca de fonte/episódio, sem re-anexar). */
    @JvmStatic
    fun update(file: String, title: String) {
        playingFile = file
        playingTitle = title
    }

    /** Desanexa (saída do player) — o estado volta ao neutro. */
    @JvmStatic
    fun detach() {
        playerRef = null
        playingFile = ""
        playingTitle = ""
    }

    @JvmStatic
    fun isPlaying(): Boolean = playerRef?.get()?.isPlaying == true

    @JvmStatic
    fun isPlayingVideo(): Boolean = isPlaying()

    @JvmStatic
    fun getTitle(): String = playingTitle

    /** Posição atual em segundos (0 sem player). */
    @JvmStatic
    fun getTime(): Double =
        ((playerRef?.get()?.currentPosition ?: 0L) / 1000.0).coerceAtLeast(0.0)

    /** Duração total em segundos (0 sem player ou ao vivo sem duração). */
    @JvmStatic
    fun getTotalTime(): Double {
        val d = playerRef?.get()?.duration ?: 0L
        return if (d <= 0L) 0.0 else d / 1000.0
    }

    @JvmStatic
    fun seekTime(seconds: Double) {
        playerRef?.get()?.seekTo((seconds * 1000).toLong().coerceAtLeast(0L))
    }

    @JvmStatic
    fun pause() {
        playerRef?.get()?.pause()
    }

    @JvmStatic
    fun play() {
        playerRef?.get()?.play()
    }

    @JvmStatic
    fun stop() {
        playerRef?.get()?.stop()
        detach()
    }
}
