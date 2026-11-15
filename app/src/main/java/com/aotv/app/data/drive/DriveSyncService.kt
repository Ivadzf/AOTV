package com.aotv.app.data.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync do backup via Google Drive (Lote 10, v1.20.0).
 *
 * Reaproveita o formato do backup local (v1.13.0, `aotv-backup-v1`): quem
 * monta o JSON é o [BackupViewModel.buildBackupJson] e quem valida/restaura
 * é o [AotvBackup.parse]/`summarize` — este serviço só sobe e baixa o texto,
 * sem saber o que tem dentro. O upload respeita o toggle "Incluir chaves e
 * sessões" porque o JSON já vem montado com ele.
 *
 * OAuth: Google Sign-In com o escopo `drive.file` (só os arquivos que o
 * próprio app criou). O [clientId] é o Client ID OAuth do tipo "Aplicativo
 * da Web" do projeto do usuário no Google Cloud, guardado em
 * `PlatformPrefs.driveClientId` — VAZIO por padrão e NADA embutido: sem ele
 * a função fica desligada ([signInIntent] devolve null). O código de
 * autorização (`requestServerAuthCode`) amarra o login ao projeto e à tela
 * de consentimento dele; o token p/ a API do Drive vem da conta conectada
 * ([GoogleAccountCredential]) — nenhum segredo é guardado no app. Sem o
 * projeto configurado (API do Drive desativada, OAuth sem o escopo), o
 * login ou a primeira chamada falha e a tela mostra o erro real.
 *
 * Arquivo: pasta "AOTV" na raiz do Drive do usuário + `aotv-backup.json`
 * dentro dela (o escopo `drive.file` só enxerga o que o app criou).
 *
 * Conflito (padrão da fork ReSeq: nunca sobrescrever silenciosamente): o
 * upload compara o `modifiedTime` do arquivo remoto com o guardado no
 * último sync (`lastRemoteModifiedMs`). Arquivo remoto mais novo que o
 * último sync, ou arquivo remoto que nunca foi sincronizado, vira
 * [UploadResult.Conflict] — a tela pergunta antes de subir por cima.
 * Tudo roda em [Dispatchers.IO]; erro de rede/API vira mensagem legível
 * ([friendlyError]) com o erro real da API quando houver.
 *
 * NÃO testável na VM: OAuth real e Drive real exigem conta Google, projeto
 * no Cloud e Play Services — o primeiro teste real é no celular do Davi.
 */
@Singleton
class DriveSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Metadados do arquivo remoto (o que basta p/ o conflito e o status). */
    data class RemoteFile(
        val id: String,
        val name: String,
        val modifiedTimeMs: Long,
        val sizeBytes: Long,
    )

    /** Resultado do upload: ou subiu ([Uploaded]) ou achou conflito. */
    sealed interface UploadResult {
        data class Uploaded(val fileId: String, val modifiedTimeMs: Long) : UploadResult
        data class Conflict(val remote: RemoteFile) : UploadResult
    }

    companion object {
        /** Pasta do AOTV na raiz do Drive do usuário. */
        const val FOLDER_NAME = "AOTV"

        /** Nome do arquivo de backup dentro da pasta. */
        const val FILE_NAME = "aotv-backup.json"

        /** MIME da pasta do Drive. */
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }

    // ── Login ───────────────────────────────────────────────────────────────

    /**
     * Intent do login Google com o escopo do Drive. Null quando não há
     * client ID configurado (função desligada — a tela explica o que falta).
     */
    fun signInIntent(clientId: String): Intent? {
        if (clientId.isBlank()) return null
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestServerAuthCode(clientId.trim())
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    /** Conta conectada (null = nunca conectou ou desconectou). */
    fun lastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /** Desconecta a conta Google (não apaga o arquivo do Drive). */
    fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
    }

    // ── Cliente do Drive ────────────────────────────────────────────────────

    private fun driveClient(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        return Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("AOTV")
            .build()
    }

    /** Id da pasta "AOTV" (cria se não existir). */
    private fun findOrCreateFolder(drive: Drive): String {
        val found = drive.files().list()
            .setQ("mimeType = '$FOLDER_MIME' and name = '$FOLDER_NAME' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id)")
            .setPageSize(10)
            .execute()
        val existing = found.files?.firstOrNull()?.id
        if (existing != null) return existing
        val created = drive.files().create(File().setName(FOLDER_NAME).setMimeType(FOLDER_MIME))
            .setFields("id")
            .execute()
        return created.id ?: error("O Drive não devolveu a pasta criada.")
    }

    /** Metadados do `aotv-backup.json` (null = ainda não existe no Drive). */
    private fun findRemoteFile(drive: Drive, folderId: String): RemoteFile? {
        val found = drive.files().list()
            .setQ("name = '$FILE_NAME' and '$folderId' in parents and trashed = false")
            .setSpaces("drive")
            .setFields("files(id,name,modifiedTime,size)")
            .setPageSize(10)
            .execute()
        val file = found.files?.firstOrNull() ?: return null
        return RemoteFile(
            id = file.id,
            name = file.name ?: FILE_NAME,
            modifiedTimeMs = file.modifiedTime?.value ?: 0L,
            sizeBytes = file.size ?: 0L,
        )
    }

    // ── Upload / download ───────────────────────────────────────────────────

    /**
     * Sobe o JSON do backup. [lastRemoteModifiedMs] é o `modifiedTime` do
     * arquivo remoto no último sync (null = nunca sincronizou). Sem [force],
     * arquivo remoto mais novo que o último sync (ou nunca sincronizado)
     * vira [UploadResult.Conflict] em vez de subir por cima.
     */
    suspend fun uploadBackup(
        account: GoogleSignInAccount,
        json: String,
        lastRemoteModifiedMs: Long?,
        force: Boolean,
    ): Result<UploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = json.toByteArray(Charsets.UTF_8)
            if (bytes.size > com.aotv.app.data.backup.AotvBackup.MAX_BACKUP_BYTES) {
                throw IllegalStateException("Backup grande demais (limite de 8 MB)")
            }
            val drive = driveClient(account)
            val folderId = findOrCreateFolder(drive)
            val remote = findRemoteFile(drive, folderId)
            if (remote != null && !force) {
                val changedSinceLastSync = lastRemoteModifiedMs == null ||
                    remote.modifiedTimeMs > lastRemoteModifiedMs
                if (changedSinceLastSync) return@runCatching UploadResult.Conflict(remote)
            }
            val content = ByteArrayContent("application/json", bytes)
            val saved = if (remote == null) {
                drive.files().create(
                    File().setName(FILE_NAME).setParents(listOf(folderId)),
                    content,
                ).setFields("id,modifiedTime").execute()
            } else {
                drive.files().update(remote.id, File(), content)
                    .setFields("id,modifiedTime").execute()
            }
            UploadResult.Uploaded(
                fileId = saved.id ?: remote?.id ?: error("O Drive não devolveu o arquivo enviado."),
                modifiedTimeMs = saved.modifiedTime?.value ?: System.currentTimeMillis(),
            )
        }.mapFailure()
    }

    /**
     * Baixa o backup do Drive. Devolve o texto + os metadados (a tela mostra
     * o resumo p/ confirmar antes de restaurar, igual ao arquivo local).
     */
    suspend fun downloadBackup(account: GoogleSignInAccount): Result<Pair<String, RemoteFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = driveClient(account)
                val folderId = findOrCreateFolder(drive)
                val remote = findRemoteFile(drive, folderId)
                    ?: throw IllegalStateException("Nenhum backup no Google Drive ainda.")
                val bytes = drive.files().get(remote.id).executeMediaAsInputStream().use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > com.aotv.app.data.backup.AotvBackup.MAX_BACKUP_BYTES) {
                            throw IllegalStateException("Backup grande demais (limite de 8 MB)")
                        }
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                String(bytes, Charsets.UTF_8) to remote
            }.mapFailure()
        }

    // ── Erros ───────────────────────────────────────────────────────────────

    /** Mensagem legível p/ a tela, com o erro real da API quando houver. */
    private fun friendlyError(e: Throwable): String {
        return when (e) {
            is GoogleJsonResponseException -> when (e.statusCode) {
                401 -> "Login do Google expirou: conecte a conta de novo."
                403 -> "Acesso ao Drive negado: ${e.details?.message ?: "confira se a API do Drive está ativa no projeto do Google Cloud."}"
                404 -> "Arquivo não encontrado no Drive."
                else -> "Google Drive (${e.statusCode}): ${e.details?.message ?: e.message}"
            }
            is UserRecoverableAuthException -> "O Google pediu autorização de novo: conecte a conta outra vez."
            is java.net.UnknownHostException -> "Sem conexão com a internet."
            is java.net.SocketTimeoutException -> "O Google Drive não respondeu a tempo: tente de novo."
            else -> e.message ?: "Não foi possível falar com o Google Drive."
        }
    }

    /** Troca a falha pelo erro legível, sem perder o tipo do sucesso. */
    private fun <T> Result<T>.mapFailure(): Result<T> =
        fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(friendlyError(it))) },
        )
}
