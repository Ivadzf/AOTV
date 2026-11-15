package com.aotv.app.data.local

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro do último crash (Lote 13, v1.23.0 — Frente 3, Bug 1 da TV).
 *
 * A Início da TV crashava toda vez que os pôsteres terminavam de carregar,
 * e a causa era chutada (matiz do pôster? memória?). Este handler grava o
 * stack trace real + aparelho/Android/hora num arquivo interno, e a página
 * de Diagnóstico mostra ele — no próximo crash a causa é CONFIRMADA pelo
 * log, não adivinhada. Sem telemetria externa: só arquivo local, nada sai
 * do aparelho, e o botão "Limpar registro" do Diagnóstico apaga ele.
 *
 * Instalado no [com.aotv.app.AotvApplication.onCreate]. Depois de gravar,
 * o crash segue o fluxo normal (o handler anterior é chamado), então o
 * comportamento do sistema não muda — só fica o registro para trás.
 */
object CrashLog {
    const val FILE_NAME = "aotv-last-crash.txt"

    /** Teto do arquivo: o stack trace é cortado aqui para não crescer sem limite. */
    private const val MAX_CHARS = 32768

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
            }
        }
    }

    /** Conteúdo do registro, ou null quando nunca houve crash registrado. */
    fun read(context: Context): String? = runCatching {
        context.filesDir.resolve(FILE_NAME).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Apaga o registro (botão "Limpar registro" do Diagnóstico). */
    fun clear(context: Context) {
        runCatching { context.filesDir.resolve(FILE_NAME).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = runCatching {
            "${com.aotv.app.BuildConfig.VERSION_NAME} (${com.aotv.app.BuildConfig.VERSION_CODE})"
        }.getOrNull() ?: "?"
        val header = "AOTV $version — crash em $whenText\n" +
            "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Thread: ${thread.name}\n\n"
        val trace = android.util.Log.getStackTraceString(throwable)
        context.filesDir.resolve(FILE_NAME).writeText((header + trace).take(MAX_CHARS))
    }
}
