package com.aotv.app.data.kodi

import android.content.Context
import android.net.Uri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spike da Fase 0 (v1.1): prova de carga do Vulgata sob o Chaquopy.
 *
 * O que ele testa, nesta ordem:
 * 1. O Python do Chaquopy inicia (Python 3.11).
 * 2. Os stubs xbmc* (app/src/main/python/) importam.
 * 3. O loader do Vulgata roda o main.py e tenta importar o .so
 *    (resources/lib/android_arm64/addon_core.so).
 *
 * Resultado esperado neste estágio: FALHAR no dlopen com
 * "libkodi.so não encontrado" — o .so tem DT_NEEDED libkodi.so e ainda não
 * há shim (o shim precisa de NDK no CI e vem depois, se o teste confirmar
 * que ele é o fator). Se o teste falhar com esse erro, o diagnóstico do
 * plano (§2.4) está confirmado e o erro exato do linker fica registrado.
 *
 * Spike4: o shim já está no APK e os símbolos Py* resolvem, mas o teste
 * continua falhando com "Protected execution failed" — o loader do Vulgata
 * engole a causa real. O driver agora captura o erro BRUTO antes do
 * main.py: CDLL do libkodi.so (por nome e pelo caminho absoluto) e carga
 * direta do addon_core.so (ver aotv_spike.py).
 *
 * O ZIP do Vulgata é PROPRIETÁRIO: nunca vai ao repo, nunca é embutido
 * como asset. O spike instala por ZIP escolhido pelo usuário (SAF) em
 * filesDir/kodi/addons/<id>/.
 *
 * Fase 1: a instalação/parse de addon.xml vive no KodiAddonStore (loja
 * de produção); o spike delega para ele para não duplicar o código.
 */
@Singleton
class KodiSpike @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addonStore: KodiAddonStore,
) {

    data class InstalledKodiAddon(val id: String, val version: String, val dir: File)

    data class LoadTestResult(
        val pythonVersion: String,
        val stage: String,
        val stubsOk: Boolean,
        val soLoaded: Boolean,
        val firstError: String?,
        /** Erro bruto da carga direta do addon_core.so (spike4). */
        val directSo: String?,
        /** Erro bruto do CDLL do libkodi.so (spike4). */
        val cdllLibkodi: String?,
        /** Resumo das chamadas xbmc* (spike6): funções distintas + últimas. */
        val xbmcCalls: String?,
        /** Repr do dict devolvido pelo Python (p/ exibir na tela). */
        val raw: String,
    )

    val addonsDir: File
        get() = addonStore.addonsDir

    /** Vulgata já instalado por ZIP (null = ainda não instalado). */
    fun installedVulgata(): InstalledKodiAddon? {
        val addon = addonStore.getAddon("plugin.video.vulgata") ?: return null
        return InstalledKodiAddon(addon.id, addon.version, addon.dir)
    }

    /**
     * Instala um addon de ZIP (SAF) — delega ao KodiAddonStore.
     * Extrai, acha o addon.xml (na raiz ou num único subdiretório, como o
     * ZIP do Vulgata) e move para filesDir/kodi/addons/<id>/, intacto (o
     * loader do Vulgata verifica o SHA256 do .so — nada pode ser alterado).
     */
    suspend fun installZip(uri: Uri): Result<InstalledKodiAddon> = withContext(Dispatchers.IO) {
        addonStore.installZip(uri).map { addon ->
            InstalledKodiAddon(addon.id, addon.version, addon.dir)
        }
    }

    /**
     * Roda o teste de carga (aotv_spike.run_load_test) numa thread de fundo.
     * O Python.start() é sob demanda e só na primeira vez.
     */
    suspend fun runLoadTest(): LoadTestResult = withContext(Dispatchers.Default) {
        val addon = installedVulgata()
            ?: return@withContext LoadTestResult(
                pythonVersion = "", stage = "not_installed", stubsOk = false,
                soLoaded = false,
                firstError = "Vulgata não instalado — instale o ZIP primeiro.",
                directSo = null, cdllLibkodi = null, xbmcCalls = null,
                raw = "",
            )
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            val res = py.getModule("aotv_spike")
                .callAttr(
                    "run_load_test", addon.dir.absolutePath,
                    context.applicationInfo.nativeLibraryDir,
                )
            LoadTestResult(
                pythonVersion = dictStr(res, "python_version"),
                stage = dictStr(res, "stage"),
                stubsOk = dictBool(res, "stubs_ok"),
                soLoaded = dictBool(res, "so_loaded"),
                firstError = dictStr(res, "first_error").ifBlank { null },
                directSo = dictStr(res, "direct_so").ifBlank { null },
                cdllLibkodi = dictStr(res, "cdll_libkodi").ifBlank { null },
                xbmcCalls = dictStr(res, "xbmc_calls").ifBlank { null },
                raw = res.toString(),
            )
        } catch (e: Exception) {
            LoadTestResult(
                pythonVersion = "", stage = "kotlin_error", stubsOk = false,
                soLoaded = false,
                firstError = "${e.javaClass.simpleName}: ${e.message}",
                directSo = null, cdllLibkodi = null, xbmcCalls = null,
                raw = "",
            )
        }
    }

    private fun dictStr(dict: com.chaquo.python.PyObject, key: String): String =
        runCatching { dict.callAttr("__getitem__", key).toString() }.getOrDefault("")

    private fun dictBool(dict: com.chaquo.python.PyObject, key: String): Boolean =
        runCatching { dict.callAttr("__getitem__", key).toBoolean() }.getOrDefault(false)
}
