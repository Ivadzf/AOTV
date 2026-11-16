// ─── data/remote/AppUpdateService.kt (Lote 12 — Frente 3, v1.22.0) ────────
// Atualizador dentro do app, tipo o do Nuvio: consulta a release mais
// recente do repo PÚBLICO no GitHub e, havendo versão nova, baixa o APK da
// release e pede p/ instalar. SEM login (o artefato do GitHub Actions
// exige autenticação; a API de releases é pública). SEMPRE do asset
// oficial da release — nunca de outra URL.
//
// HONESTIDADE: sem releases publicadas ainda (404), o resultado é
// NoReleases ("Nenhuma release publicada ainda") — NUNCA "está atualizado".
// Sem rede, o erro real volta em Error, sem inventar estado.
// A primeira release do AOTV é a v1.22.0 (decisão do Davi, 2026-11-15).

package com.aotv.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aotv.app.BuildConfig
import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// ─── Modelos ────────────────────────────────────────────────────────────────

/** Release do GitHub com APK. [apkUrl] é SEMPRE o browser_download_url do asset .apk. */
data class AppRelease(
    val tag: String,
    val name: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val publishedAt: String,
)

/** Resultado da checagem de atualização. */
sealed interface UpdateCheckResult {
    /** Nenhuma release publicada ainda (404 ou lista vazia) — NÃO é "atualizado". */
    data object NoReleases : UpdateCheckResult
    /** A instalada já é a mais recente. */
    data class UpToDate(val latestTag: String) : UpdateCheckResult
    /** Há versão nova na release oficial. */
    data class Available(val release: AppRelease) : UpdateCheckResult
    /** Falha real (sem rede, GitHub fora, tag inválida): a mensagem é o erro. */
    data class Error(val message: String) : UpdateCheckResult
}

/** Resultado do pedido de instalação do APK baixado. */
sealed interface InstallRequest {
    /** Intent pronta p/ instalar (FileProvider + permissão concedida). */
    data class Ready(val intent: Intent) : InstallRequest
    /** O usuário precisa permitir "instalar apps desconhecidos" p/ o AOTV. */
    data object NeedsPermission : InstallRequest
}

// ─── Funções puras (comparação de versão e validação do asset) ─────────────

/**
 * Parse robusto da tag da release: `v` opcional, `1.22.0`, sufixos
 * (`-beta.1`, `+build`) ignorados. Partes que faltam viram 0 ("1.22" = 1.22.0).
 * null = tag sem nenhum número (não dá p/ comparar — nunca inventar).
 */
fun parseVersionTag(tag: String): Triple<Int, Int, Int>? {
    val clean = tag.trim().removePrefix("v").removePrefix("V")
        .substringBefore("-").substringBefore("+").trim()
    if (clean.isBlank()) return null
    val parts = clean.split(".")
    if (parts.size > 3 || parts.any { it.isBlank() || it.any { c -> !c.isDigit() } }) return null
    val nums = parts.map { it.toIntOrNull() ?: return null }
    return Triple(nums.getOrElse(0) { 0 }, nums.getOrElse(1) { 0 }, nums.getOrElse(2) { 0 })
}

/** Compara duas versões semânticas: <0 mais antiga, 0 igual, >0 mais nova. */
fun compareVersions(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
    if (a.first != b.first) return a.first.compareTo(b.first)
    if (a.second != b.second) return a.second.compareTo(b.second)
    return a.third.compareTo(b.third)
}

/**
 * Só o asset .apk da release OFICIAL do AOTV vale p/ baixar: URL de
 * download da release do repo público, terminando em `.apk`. Qualquer outra
 * URL (outro repo, outro host, outro arquivo) é recusada.
 */
fun isApkAssetUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.startsWith("https://github.com/ivadzf/aotv/releases/download/") &&
        lower.endsWith(".apk")
}

/**
 * Extrai a release do JSON de `GET /repos/Ivadzf/AOTV/releases/latest`.
 * null = sem asset .apk oficial (a release existe, mas não traz APK p/ instalar).
 */
fun pickApkAsset(json: JSONObject): AppRelease? {
    val tag = json.optString("tag_name", "").trim()
    if (tag.isBlank()) return null
    val assets = json.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val url = asset.optString("browser_download_url", "")
        if (!isApkAssetUrl(url)) continue
        return AppRelease(
            tag = tag,
            name = json.optString("name", tag),
            apkUrl = url,
            sizeBytes = asset.optLong("size", 0L),
            publishedAt = json.optString("published_at", ""),
        )
    }
    return null
}

// ─── Service ────────────────────────────────────────────────────────────────

@Singleton
class AppUpdateService @Inject constructor(
    private val prefs: PlatformPrefs,
    client: OkHttpClient,
) {
    companion object {
        const val REPO = "Ivadzf/AOTV"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
        const val FILE_PROVIDER_AUTHORITY = "com.aotv.app.fileprovider"
        private const val APK_FILE_NAME = "aotv-update.apk"
        /** Intervalo mínimo entre checagens automáticas (24 h). */
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }

    private val http = client.newBuilder().cache(null).build()

    /** Versão instalada (nome, ex. "1.22.0"). */
    val installedVersionName: String get() = BuildConfig.VERSION_NAME

    /** Checagem automática ligada (global, padrão: ligada). */
    val autoCheckEnabled: Boolean get() = prefs.appUpdateCheckEnabled

    /** Já passou o intervalo p/ checar de novo sozinho (24 h desde a última). */
    fun shouldAutoCheck(nowMs: Long = System.currentTimeMillis()): Boolean =
        autoCheckEnabled && nowMs - prefs.appUpdateLastCheck >= AUTO_CHECK_INTERVAL_MS

    /**
     * Consulta a release mais recente. 404 = nenhuma release publicada ainda
     * ([UpdateCheckResult.NoReleases]); erro de rede vira [UpdateCheckResult.Error]
     * com a mensagem real. A comparação é por versionName semântico — a tag
     * não carrega versionCode, então não há como comparar por código.
     */
    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AOTV/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        val resp = runCatching { http.newCall(req).execute() }.getOrElse { e ->
            return@withContext UpdateCheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
        resp.use {
            if (it.code == 404) return@withContext UpdateCheckResult.NoReleases
            if (!it.isSuccessful) {
                return@withContext UpdateCheckResult.Error("GitHub respondeu HTTP ${it.code}")
            }
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) return@withContext UpdateCheckResult.Error("Resposta vazia do GitHub")
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext UpdateCheckResult.Error("Resposta inválida do GitHub")
            prefs.appUpdateLastCheck = System.currentTimeMillis()
            val release = pickApkAsset(json) ?: return@withContext UpdateCheckResult.NoReleases
            val latest = parseVersionTag(release.tag)
                ?: return@withContext UpdateCheckResult.Error("Tag da release inválida: ${release.tag}")
            val installed = parseVersionTag(installedVersionName)
                ?: return@withContext UpdateCheckResult.Error("Versão instalada inválida: $installedVersionName")
            if (compareVersions(latest, installed) > 0) UpdateCheckResult.Available(release)
            else UpdateCheckResult.UpToDate(release.tag)
        }
    }

    /**
     * Baixa o APK da release p/ o cache do app. Só aceita o asset oficial
     * ([isApkAssetUrl]); qualquer outra URL é recusada antes de baixar.
     * Confere o tamanho baixado contra o informado pela release (Lote 15,
     * v1.25.0 — Bloco 4, item 7): tamanho diferente = download incompleto
     * (o parcial é apagado e o erro volta, sem instalar nada). Sem tamanho
     * informado (0), só vale o "não vazio" de antes. SHA-256 não é
     * conferido: a release não traz arquivo de checksums.
     */
    suspend fun downloadApk(context: Context, release: AppRelease): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(isApkAssetUrl(release.apkUrl)) { "URL do APK fora da release oficial" }
            val req = Request.Builder()
                .url(release.apkUrl)
                .header("User-Agent", "AOTV/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            val outFile = File(context.cacheDir, APK_FILE_NAME)
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download falhou: HTTP ${resp.code}")
                val body = resp.body ?: error("Download falhou: resposta vazia")
                outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (!outFile.exists() || outFile.length() <= 0) error("Download falhou: arquivo vazio")
            val downloadedBytes = outFile.length()
            if (release.sizeBytes > 0 && downloadedBytes != release.sizeBytes) {
                outFile.delete()
                error("Download incompleto: $downloadedBytes de ${release.sizeBytes} bytes")
            }
            outFile
        }
    }

    /**
     * Monta o pedido de instalação do APK baixado (FileProvider). Se o
     * aparelho ainda não permite instalar apps desconhecidos do AOTV, volta
     * [InstallRequest.NeedsPermission] — a tela mostra como habilitar
     * (Configurações do sistema → Instalar apps desconhecidos → AOTV).
     */
    fun installApk(context: Context, apkFile: File): InstallRequest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return InstallRequest.NeedsPermission
        }
        val uri: Uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return InstallRequest.Ready(intent)
    }

    /** Intent p/ a tela do sistema onde se permite instalar apps desconhecidos. */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
