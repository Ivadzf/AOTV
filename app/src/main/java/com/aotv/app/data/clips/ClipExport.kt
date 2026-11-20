package com.aotv.app.data.clips

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AppUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Exportar um trecho (clipe) já baixado — Lote mpv 4 — Frente 2 (v1.39.0).
 *
 * O clipe mora na pasta do app (`getExternalFilesDir(DIRECTORY_MOVIES)`,
 * ou numa URI SAF quando já foi movido p/ a pasta de Downloads). Exportar
 * é TIRAR o arquivo de lá, sem mover o original:
 * - [shareClip]: abre o share sheet do sistema (ACTION_SEND) com o
 *   arquivo. Arquivo local vai p/ uma cópia em `cacheDir/clip-share/`
 *   antes, porque o FileProvider só expõe o cache (`cache-path "."` no
 *   `file_paths.xml` — a pasta de filmes externa não está exposta) e
 *   `getUriForFile` recusaria o arquivo original. URI SAF (`content://`)
 *   já é conteúdo e vai direta, com permissão de leitura concedida.
 * - [copyClipToFolder]: COPIA (não move) p/ a pasta pública de Downloads
 *   escolhida em Configurações ([PlatformPrefs.downloadFolderUri]) — o
 *   original continua em Downloads. Sem pasta escolhida, devolve
 *   [ClipExportResult.NoFolderConfigured] em vez de inventar um destino.
 *
 * Tudo devolve [ClipExportResult] em vez de lançar: erro vira mensagem
 * (toast), nunca crash. O nome do arquivo vem da URI (DISPLAY_NAME no
 * SAF, último segmento no `file://`); sem ele, usa o título do clipe.
 */

/** Resultado de uma exportação de trecho (o `when` que lê isto cobre os 4 ramos). */
sealed class ClipExportResult {
    /** O share sheet abriu (o usuário escolhe o destino no sistema). */
    object Shared : ClipExportResult()
    /** Cópia concluída na pasta de Downloads escolhida. */
    object CopiedToFolder : ClipExportResult()
    /** Sem pasta de Downloads escolhida — nada foi copiado. */
    object NoFolderConfigured : ClipExportResult()
    /** Não deu para exportar; [message] é o texto mostrado ao usuário. */
    data class Failed(val message: String) : ClipExportResult()
}

class ClipExporter(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: PlatformPrefs by lazy { PlatformPrefs(appContext) }

    /**
     * Abre o share sheet do sistema com o arquivo do trecho. O original
     * não é movido nem apagado — a cópia p/ o cache (só no `file://`) é
     * descartável e o sistema limpa o cache quando precisa.
     */
    suspend fun shareClip(localUri: String, fallbackTitle: String): ClipExportResult =
        withContext(Dispatchers.IO) {
            if (localUri.isBlank()) {
                return@withContext ClipExportResult.Failed("O trecho ainda não tem arquivo para compartilhar.")
            }
            val fileName = exportFileName(localUri, fallbackTitle)
            val shareUri = resolveShareUri(localUri, fileName)
                ?: return@withContext ClipExportResult.Failed("Arquivo do trecho não encontrado.")
            var launchFailed = false
            withContext(Dispatchers.Main) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mimeFor(fileName)
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    appContext.startActivity(Intent.createChooser(send, "Compartilhar trecho"))
                }.onFailure { launchFailed = true }
            }
            if (launchFailed) {
                ClipExportResult.Failed("Não deu para abrir o compartilhamento.")
            } else {
                ClipExportResult.Shared
            }
        }

    /**
     * Copia o trecho p/ a pasta de Downloads escolhida (SAF) — mesmo
     * padrão do `moveToDownloadFolder` do trecho, mas sem apagar o
     * original depois da cópia. Sem pasta escolhida, devolve
     * [ClipExportResult.NoFolderConfigured].
     */
    suspend fun copyClipToFolder(localUri: String, fallbackTitle: String): ClipExportResult =
        withContext(Dispatchers.IO) {
            if (localUri.isBlank()) {
                return@withContext ClipExportResult.Failed("O trecho ainda não tem arquivo para exportar.")
            }
            val treeUriStr = prefs.downloadFolderUri
            if (treeUriStr.isBlank()) return@withContext ClipExportResult.NoFolderConfigured
            val fileName = exportFileName(localUri, fallbackTitle)
            runCatching {
                val input = openClipStream(localUri)
                    ?: return@runCatching ClipExportResult.Failed("Arquivo do trecho não encontrado.")
                val treeUri = Uri.parse(treeUriStr)
                val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri),
                )
                val destDoc = DocumentsContract.createDocument(
                    appContext.contentResolver, rootDoc, mimeFor(fileName), fileName,
                ) ?: return@runCatching ClipExportResult.Failed(
                    "Não deu para criar o arquivo na pasta de Downloads.",
                )
                input.use { source ->
                    appContext.contentResolver.openOutputStream(destDoc)?.use { output ->
                        source.copyTo(output)
                    } ?: return@runCatching ClipExportResult.Failed(
                        "Não deu para escrever na pasta de Downloads.",
                    )
                }
                ClipExportResult.CopiedToFolder
            }.getOrElse {
                ClipExportResult.Failed(
                    "Não deu para exportar o trecho para a pasta de Downloads.",
                )
            }
        }

    /**
     * URI p/ compartilhar: `content://` (SAF) vai direta; `file://` é
     * copiado p/ `cacheDir/clip-share/` e servido pelo FileProvider
     * (o `file_paths.xml` só expõe o cache — o arquivo original, na pasta
     * externa de filmes, seria recusado pelo `getUriForFile`).
     */
    private fun resolveShareUri(localUri: String, fileName: String): Uri? {
        if (localUri.startsWith("content://")) {
            return runCatching { Uri.parse(localUri) }.getOrNull()
        }
        val path = runCatching { Uri.parse(localUri).path }.getOrNull() ?: return null
        val src = File(path)
        if (!src.isFile) return null
        val shareDir = File(appContext.cacheDir, "clip-share").apply { mkdirs() }
        val dest = File(shareDir, fileName)
        return runCatching {
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            FileProvider.getUriForFile(appContext, AppUpdateService.FILE_PROVIDER_AUTHORITY, dest)
        }.getOrNull()
    }

    /** Abre o arquivo do trecho p/ leitura, seja `file://` ou `content://` (SAF). */
    private fun openClipStream(localUri: String): InputStream? = runCatching {
        if (localUri.startsWith("content://")) {
            appContext.contentResolver.openInputStream(Uri.parse(localUri))
        } else {
            val path = Uri.parse(localUri).path ?: return@runCatching null
            val file = File(path)
            if (file.isFile) file.inputStream() else null
        }
    }.getOrNull()

    /** Nome do arquivo p/ exportar: o da URI quando existe, senão o título sanitizado. */
    private fun exportFileName(localUri: String, fallbackTitle: String): String {
        displayNameFromUri(localUri)?.takeIf { it.isNotBlank() }?.let { return it }
        val extension = when {
            localUri.endsWith(".mp4", ignoreCase = true) -> "mp4"
            localUri.endsWith(".ts", ignoreCase = true) -> "ts"
            else -> "mp4"
        }
        val safe = fallbackTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(50).trim('_')
            .ifBlank { "trecho" }
        return "$safe.$extension"
    }

    /** Nome de exibição da URI: DISPLAY_NAME no SAF, último segmento no `file://`. */
    private fun displayNameFromUri(localUri: String): String? = runCatching {
        if (localUri.startsWith("content://")) {
            appContext.contentResolver.query(
                Uri.parse(localUri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } else {
            Uri.parse(localUri).lastPathSegment?.substringAfterLast('/')
        }
    }.getOrNull()

    /** Mime do trecho pelo nome do arquivo (.ts = MPEG-TS, .mp4 = MP4). */
    private fun mimeFor(fileName: String): String = when {
        fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
        fileName.endsWith(".ts", ignoreCase = true) -> "video/mp2t"
        else -> "video/*"
    }
}
