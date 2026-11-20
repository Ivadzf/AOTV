package com.aotv.app.ui.movies

import android.content.Context
import java.io.File

/**
 * yt-dlp no mpv (Lote mpv 4, v1.39.0 — Frente 3).
 *
 * INVIÁVEL nesta versão do motor: o AAR
 * `io.github.abdallahmehiz:mpv-android-lib:0.1.12` (mpv 0.41) NÃO traz o
 * binário yt-dlp, e o yt-dlp é um programa Python — embuti-lo exigiria
 * empacotar um interpretador Python + o script no APK (pesado, fora do
 * escopo deste lote). Por isso o init do MpvPlayer fixa `ytdl=no`: o mpv
 * nunca tenta resolver URL com yt-dlp, e nada muda — as fontes já chegam
 * resolvidas pelos addons, então o player recebe a URL direta.
 *
 * Ponto de integração p/ um lote futuro: se um dia o binário for embutido
 * em `filesDir/mpv/bin/yt-dlp` (executável), [binaryPath] passa a devolver
 * o caminho dele e o init pode ligar o `ytdl` apontando p/ ele. Até lá,
 * devolve null.
 */
object MpvYtdlp {
    /**
     * Caminho do binário yt-dlp embutido, ou null quando não há nenhum
     * (o caso hoje: o AAR não traz o binário).
     */
    fun binaryPath(context: Context): String? {
        val candidate = File(context.filesDir, "mpv/bin/yt-dlp")
        return if (candidate.isFile && candidate.canExecute()) candidate.absolutePath else null
    }
}
