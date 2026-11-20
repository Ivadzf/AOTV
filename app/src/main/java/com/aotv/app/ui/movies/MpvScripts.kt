package com.aotv.app.ui.movies

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

/**
 * Scripts Lua do mpv (Lote mpv 4, v1.39.0 — Frente 3; instalação de dentro
 * do app no Lote de correções, v1.42.0 — Frente 2).
 *
 * O mpv carrega sozinho os scripts de `<config-dir>/scripts/` no init — e
 * o config-dir do app já é `filesDir/mpv` (ver MpvPlayer). Nada aqui precisa
 * passar script por comando: basta o arquivo `.lua` estar na pasta antes do
 * próximo init do Libmpv. Um script instalado de dentro do app cai no
 * mesmo caminho que os 2 exemplos, então carrega na próxima reprodução
 * com o Libmpv — sem acesso por fora à pasta interna do app.
 *
 * Só Lua: o AAR `io.github.abdallahmehiz:mpv-android-lib:0.1.12` (mpv 0.41)
 * traz o interpretador Lua habilitado (`ytdl_hook.lua` embutido) e NÃO traz
 * mujs — scripts `.js` não carregam. Scripts do usuário nunca são
 * sobrescritos: [ensureExamples] só copia os 2 exemplos quando a pasta
 * está vazia, e [installScript] recusa duplicado sem confirmação
 * (sobrescrever é só por [installScriptOverwrite]).
 *
 * Ligar/desligar por script é por renomeação: o desligado vira
 * `<nome>.lua.disabled` e o mpv só carrega arquivos que terminam em
 * `.lua` — sem pref nova e sem tocar no conteúdo do script.
 */
object MpvScripts {
    /** Pasta de scripts do mpv: `filesDir/mpv/scripts`. */
    fun scriptsDir(context: Context): File = File(context.filesDir, "mpv/scripts")

    /** Sufixo do script desligado (o mpv ignora: não termina em `.lua`). */
    const val DISABLED_SUFFIX = ".disabled"

    /** Tamanho máximo de um script instalável (1 MB — script Lua é texto curto). */
    const val MAX_SCRIPT_BYTES = 1_048_576L

    /** Um script na pasta: `enabled = false` é o `<nome>.lua.disabled`. */
    data class ScriptInfo(val name: String, val enabled: Boolean, val file: File)

    /** Resultado da instalação de um script. */
    sealed class InstallResult {
        /** Instalado com este nome de arquivo. */
        data class Installed(val name: String) : InstallResult()
        /** O nome veio vazio (sem nome, só `.lua`, ou só separadores). */
        object InvalidName : InstallResult()
        /** O arquivo não termina em `.lua`. */
        object InvalidExtension : InstallResult()
        /** O arquivo passa de [MAX_SCRIPT_BYTES]. */
        object TooLarge : InstallResult()
        /** Já existe um script (ligado ou desligado) com este nome. */
        data class Duplicate(val name: String) : InstallResult()
        /** Falha de leitura/escrita (mensagem p/ mostrar ao usuário). */
        data class Failed(val message: String) : InstallResult()
    }

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

    /**
     * Lista os scripts instalados (ligados e desligados), ordenados por
     * nome. Arquivos que não são `.lua` nem `.lua.disabled` são ignorados
     * (o mpv também os ignoraria).
     */
    fun listScripts(context: Context): List<ScriptInfo> {
        val dir = scriptsDir(context)
        val files = dir.listFiles() ?: return emptyList()
        val out = ArrayList<ScriptInfo>()
        for (file in files) {
            if (!file.isFile) continue
            if (file.name.endsWith(".lua", ignoreCase = true)) {
                out.add(ScriptInfo(file.name, true, file))
            } else if (file.name.endsWith(".lua" + DISABLED_SUFFIX, ignoreCase = true)) {
                val base = file.name.dropLast(DISABLED_SUFFIX.length)
                out.add(ScriptInfo(base, false, file))
            }
        }
        out.sortBy { it.name.lowercase() }
        return out
    }

    /**
     * Nome de exibição do arquivo escolhido no seletor do sistema
     * (DISPLAY_NAME do SAF; "" quando o provedor não devolve nome).
     * Igual ao padrão do VpnScreen p/ perfis importados por SAF.
     */
    fun displayName(context: Context, uri: Uri): String {
        val fromQuery = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return fromQuery ?: ""
    }

    /**
     * Higieniza o nome do arquivo escolhido: tira diretórios, espaços nas
     * pontas e rejeita o que não é `.lua`. Devolve o nome limpo ou null —
     * null com o nome vazio é [InstallResult.InvalidName], null com outra
     * extensão é [InstallResult.InvalidExtension] (ver [validateName]).
     */
    fun sanitizeFileName(raw: String): String? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (base.isEmpty()) return null
        if (!base.endsWith(".lua", ignoreCase = true)) return null
        if (base.length <= ".lua".length) return null
        // O mpv casa a extensão em minúsculo: "Script.LUA" vira
        // "Script.lua" p/ carregar no init.
        return base.dropLast(".lua".length) + ".lua"
    }

    /** Diz por que [sanitizeFileName] recusou o nome (p/ a mensagem certa). */
    fun validateName(raw: String): InstallResult? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (base.isEmpty()) return InstallResult.InvalidName
        // A extensão vem antes do tamanho: "x.js" é extensão errada, não
        // nome vazio — só ".lua" (sem nome antes da extensão) é InvalidName.
        if (!base.endsWith(".lua", ignoreCase = true)) return InstallResult.InvalidExtension
        if (base.length <= ".lua".length) return InstallResult.InvalidName
        return null
    }

    /**
     * Instala um `.lua` escolhido no seletor do sistema na pasta de
     * scripts. Bloqueante (chamar em Dispatchers.IO): lê o [input] com
     * limite de [MAX_SCRIPT_BYTES] e copia p/ a pasta. Recusa nome
     * vazio/duplicado e extensão errada — sem sobrescrever nada.
     * Devolve [InstallResult.Duplicate] quando já existe um script com o
     * mesmo nome (ligado ou desligado); aí a tela confirma antes de
     * chamar [installScriptOverwrite].
     */
    fun installScript(context: Context, displayName: String, input: InputStream): InstallResult {
        return installScriptInternal(context, displayName, input, overwrite = false)
    }

    /**
     * Igual a [installScript], mas substitui o script existente com o
     * mesmo nome (ligado ou desligado). Só chamar depois da confirmação
     * do usuário na tela.
     */
    fun installScriptOverwrite(context: Context, displayName: String, input: InputStream): InstallResult {
        return installScriptInternal(context, displayName, input, overwrite = true)
    }

    private fun installScriptInternal(
        context: Context,
        displayName: String,
        input: InputStream,
        overwrite: Boolean,
    ): InstallResult {
        val invalid = validateName(displayName)
        if (invalid != null) return invalid
        val name = sanitizeFileName(displayName) ?: return InstallResult.InvalidName
        val dir = scriptsDir(context)
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, name)
        val disabled = File(dir, name + DISABLED_SUFFIX)
        if (!overwrite && (dest.exists() || disabled.exists())) {
            return InstallResult.Duplicate(name)
        }
        val temp = File(dir, "$name.tmp")
        return try {
            var total = 0L
            temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_SCRIPT_BYTES) {
                        return InstallResult.TooLarge
                    }
                    output.write(buffer, 0, n)
                }
            }
            if (overwrite) {
                dest.delete()
                disabled.delete()
            }
            if (!temp.renameTo(dest)) {
                dest.delete()
                if (!temp.renameTo(dest)) return InstallResult.Failed("Não foi possível salvar o script")
            }
            InstallResult.Installed(name)
        } catch (e: Exception) {
            temp.delete()
            InstallResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Não foi possível salvar o script")
        } finally {
            if (temp.exists() && !temp.delete()) {
                temp.deleteOnExit()
            }
        }
    }

    /**
     * Liga/desliga um script: desligar renomeia p/ `<nome>.lua.disabled`
     * (o mpv não carrega — só entra `.lua` no init), ligar desfaz.
     * Vale a partir da próxima reprodução com o Libmpv. Devolve false
     * quando o arquivo sumiu ou o destino já existe.
     */
    fun setScriptEnabled(context: Context, info: ScriptInfo, enabled: Boolean): Boolean {
        if (info.enabled == enabled) return true
        val dir = scriptsDir(context)
        return if (enabled) {
            val dest = File(dir, info.name)
            if (dest.exists()) false else info.file.renameTo(dest)
        } else {
            val dest = File(dir, info.name + DISABLED_SUFFIX)
            if (dest.exists()) false else info.file.renameTo(dest)
        }
    }

    /** Exclui o script da pasta (ligado ou desligado). Devolve false se não apagou. */
    fun deleteScript(context: Context, info: ScriptInfo): Boolean {
        val file = info.file
        if (!file.exists()) return false
        return file.delete()
    }

    /**
     * Os scripts que o mpv vai carregar no próximo init: os `.lua`
     * ligados da pasta (o mpv lê `<config-dir>/scripts/` com
     * `load-scripts=yes`; ver o init em MpvPlayer). Desligados
     * (`.lua.disabled`) e outros arquivos não entram.
     */
    fun loadableScripts(context: Context): List<String> {
        return listScripts(context).filter { it.enabled }.map { it.name }
    }
}
