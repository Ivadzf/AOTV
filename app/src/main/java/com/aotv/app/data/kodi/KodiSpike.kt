package com.aotv.app.data.kodi

import android.content.Context
import android.net.Uri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
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
 * O ZIP do Vulgata é PROPRIETÁRIO: nunca vai ao repo, nunca é embutido
 * como asset. O spike instala por ZIP escolhido pelo usuário (SAF) em
 * filesDir/kodi/addons/<id>/.
 */
@Singleton
class KodiSpike @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class InstalledKodiAddon(val id: String, val version: String, val dir: File)

    data class LoadTestResult(
        val pythonVersion: String,
        val stage: String,
        val stubsOk: Boolean,
        val soLoaded: Boolean,
        val firstError: String?,
        /** Repr do dict devolvido pelo Python (p/ exibir na tela). */
        val raw: String,
    )

    val addonsDir: File
        get() = File(context.filesDir, "kodi/addons")

    /** Vulgata já instalado por ZIP (null = ainda não instalado). */
    fun installedVulgata(): InstalledKodiAddon? {
        val dir = File(addonsDir, "plugin.video.vulgata")
        val xml = File(dir, "addon.xml")
        if (!xml.isFile) return null
        return parseAddonXml(dir, xml)?.copy(dir = dir)
    }

    /**
     * Instala um addon de ZIP (SAF): extrai, acha o addon.xml (na raiz ou
     * num único subdiretório, como o ZIP do Vulgata) e move para
     * filesDir/kodi/addons/<id>/, intacto (o loader do Vulgata verifica o
     * SHA256 do .so — nada pode ser alterado).
     */
    suspend fun installZip(uri: Uri): Result<InstalledKodiAddon> = withContext(Dispatchers.IO) {
        runCatching {
            val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
            val tmpDir = File(tmpRoot, "install-${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                // Copia o ZIP do SAF para um arquivo temporário.
                val tmpZip = File(tmpDir, "addon.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmpZip.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Não consegui abrir o ZIP escolhido.")
                // Valida pelo conteúdo (magic PK), não pelo MIME/extensão:
                // o seletor aceita qualquer arquivo (o filtro application/zip
                // cinzentava o Vulgata no celular do Davi) e só aqui se prova
                // que é ZIP de verdade.
                val magic = ByteArray(4)
                FileInputStream(tmpZip).use { it.read(magic) }
                require(
                    magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
                        magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
                        magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x07, 0x08)),
                ) { "O arquivo escolhido não é um ZIP válido." }
                // Extrai com proteção contra zip-slip.
                val extractDir = File(tmpDir, "extracted").apply { mkdirs() }
                ZipInputStream(FileInputStream(tmpZip)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(extractDir, entry.name)
                        require(out.canonicalPath.startsWith(extractDir.canonicalPath)) {
                            "Entrada inválida no ZIP: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output -> zip.copyTo(output) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                // Acha o addon.xml (na raiz ou num único subdiretório).
                val addonDir = findAddonDir(extractDir)
                    ?: error("addon.xml não encontrado dentro do ZIP.")
                val xml = File(addonDir, "addon.xml")
                val parsed = parseAddonXml(addonDir, xml)
                    ?: error("addon.xml sem id/versão.")
                // Move para o destino final (substitui instalação anterior).
                val dest = File(addonsDir, parsed.id).apply {
                    if (exists()) deleteRecursively()
                    parentFile?.mkdirs()
                }
                require(addonDir.renameTo(dest)) { "Não consegui mover o addon para ${dest.path}." }
                parsed.copy(dir = dest)
            } finally {
                tmpDir.deleteRecursively()
            }
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
                raw = "",
            )
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            val res = py.getModule("aotv_spike")
                .callAttr("run_load_test", addon.dir.absolutePath)
            LoadTestResult(
                pythonVersion = dictStr(res, "python_version"),
                stage = dictStr(res, "stage"),
                stubsOk = dictBool(res, "stubs_ok"),
                soLoaded = dictBool(res, "so_loaded"),
                firstError = dictStr(res, "first_error").ifBlank { null },
                raw = res.toString(),
            )
        } catch (e: Exception) {
            LoadTestResult(
                pythonVersion = "", stage = "kotlin_error", stubsOk = false,
                soLoaded = false,
                firstError = "${e.javaClass.simpleName}: ${e.message}",
                raw = "",
            )
        }
    }

    private fun dictStr(dict: com.chaquo.python.PyObject, key: String): String =
        runCatching { dict.callAttr("__getitem__", key).toString() }.getOrDefault("")

    private fun dictBool(dict: com.chaquo.python.PyObject, key: String): Boolean =
        runCatching { dict.callAttr("__getitem__", key).toBoolean() }.getOrDefault(false)

    private fun findAddonDir(root: File): File? {
        if (File(root, "addon.xml").isFile) return root
        val subdirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (subdirs.size == 1 && File(subdirs[0], "addon.xml").isFile) return subdirs[0]
        return null
    }

    private fun parseAddonXml(dir: File, xml: File): InstalledKodiAddon? {
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(xml.inputStream(), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "addon") {
                    val id = parser.getAttributeValue(null, "id").orEmpty()
                    val version = parser.getAttributeValue(null, "version").orEmpty()
                    if (id.isNotBlank()) return InstalledKodiAddon(id, version, dir)
                    return null
                }
                event = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
