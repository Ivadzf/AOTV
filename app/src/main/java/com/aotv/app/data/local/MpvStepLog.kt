package com.aotv.app.data.local

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Últimos passos do libmpv (Lote mpv 3, v1.38.0).
 *
 * Por que existe: o CrashLog só instala um handler JVM
 * ([Thread.setDefaultUncaughtExceptionHandler]) — um crash NATIVO dentro do
 * libmpv (create/init/attachSurface via JNI) mata o processo antes do
 * handler rodar, então o Diagnóstico ficava vazio quando o app fechava na
 * 1ª abertura de uma fonte com o Libmpv (relato do Davi na v1.37.0). Sem
 * NDK no CI não dá p/ instalar um handler de sinal nativo a partir do
 * Kotlin — então o mpv registra cada passo num arquivo interno
 * (`filesDir/mpv-last-steps.txt`, no máximo 120 linhas, só texto, sem URL
 * nem credencial) e o [com.aotv.app.ui.settings.DiagnosticsScreen] mostra
 * o conteúdo. Se a sessão anterior não terminou com "destroy: ok", o
 * arquivo marca "[aviso] sessão anterior terminou sem encerramento limpo
 * (possível crash nativo)" — é uma EVIDÊNCIA de morte do processo, não
 * uma prova de crash no libmpv (o Android também pode ter matado o app).
 */
object MpvStepLog {
    const val FILE_NAME = "mpv-last-steps.txt"
    private const val MAX_LINES = 120
    private const val CLEAN_MARKER = "destroy: ok"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Abre uma sessão nova: se o arquivo existe e a última linha não é o
     * encerramento limpo, a sessão anterior morreu sem destruir o mpv.
     *
     * Hotfix v1.38.1: a comparação era `lines.last() != CLEAN_MARKER`, mas
     * o [append] grava TODA linha com o prefixo "[HH:mm:ss] " (inclusive o
     * marcador: "[12:34:25] destroy: ok") — a comparação NUNCA casava e o
     * aviso disparava em TODA sessão nova, mesmo com encerramento limpo.
     * O fim da linha é o que vale (`endsWith`).
     */
    @Synchronized
    fun beginSession(context: Context) {
        val lines = readLines(context)
        if (lines.isNotEmpty() && !lines.last().endsWith(CLEAN_MARKER)) {
            append(context, "[aviso] sessão anterior terminou sem encerramento limpo (possível crash nativo)")
        }
    }

    @Synchronized
    fun append(context: Context, step: String) {
        runCatching {
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val lines = (readLines(context) + "[$stamp] $step").takeLast(MAX_LINES)
            file(context).writeText(lines.joinToString("\n") + "\n")
        }
    }

    /** Último passo registrado ("destroy: ok" quando o encerramento foi limpo). */
    fun markCleanShutdown(context: Context) = append(context, CLEAN_MARKER)

    fun read(context: Context): String = runCatching { file(context).readText() }.getOrNull().orEmpty()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun readLines(context: Context): List<String> =
        runCatching { file(context).readLines() }.getOrNull().orEmpty().filter { it.isNotBlank() }
}
