package com.aotv.app.ui.movies

import android.content.Context
import java.io.File

/**
 * Scripts Lua do mpv (Lote mpv 4, v1.39.0 — Frente 3).
 *
 * O mpv carrega sozinho os scripts de `<config-dir>/scripts/` no init — e
 * o config-dir do app já é `filesDir/mpv` (ver MpvPlayer). Nada aqui precisa
 * passar script por comando: basta o arquivo `.lua` estar na pasta antes do
 * próximo init do Libmpv.
 *
 * Só Lua: o AAR `io.github.abdallahmehiz:mpv-android-lib:0.1.12` (mpv 0.41)
 * traz o interpretador Lua habilitado (`ytdl_hook.lua` embutido) e NÃO traz
 * mujs — scripts `.js` não carregam. Scripts do usuário nunca são
 * sobrescritos: [ensureExamples] só copia os 2 exemplos quando a pasta
 * está vazia.
 */
object MpvScripts {
    /** Pasta de scripts do mpv: `filesDir/mpv/scripts`. */
    fun scriptsDir(context: Context): File = File(context.filesDir, "mpv/scripts")

    /**
     * Copia os 2 scripts de exemplo (`assets/mpv-scripts/`) p/ a pasta de
     * scripts SÓ se ela estiver vazia. Nunca sobrescreve scripts do usuário
     * — se já há qualquer arquivo na pasta, não copia nada.
     */
    fun ensureExamples(context: Context) {
        val dir = scriptsDir(context)
        if (!dir.exists()) dir.mkdirs()
        val existing = dir.listFiles()
        if (existing != null && existing.isNotEmpty()) return
        copyAsset(context, "mpv-scripts/aotv-hello.lua", File(dir, "aotv-hello.lua"))
        copyAsset(context, "mpv-scripts/aotv-time.lua", File(dir, "aotv-time.lua"))
    }

    private fun copyAsset(context: Context, assetPath: String, dest: File) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
