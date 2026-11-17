// ─── data/remote/AppUpdateService.kt (Lote 12 — Frente 3, v1.22.0; Lote 18, v1.28.0) ──
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
//
// Lote 18 (v1.28.0) — SHA-256 no feed + APK da ABI do aparelho + 2º plano:
// - Feed `updates.json` na raiz do repo público
//   (https://raw.githubusercontent.com/Ivadzf/AOTV/main/updates.json):
//   versão, changelog e, por ABI (arm64-v8a / armeabi-v7a / universal),
//   url + sha256 + size. O app lê o feed PRIMEIRO; se o feed falhar
//   (404, sem rede, JSON inválido) ou estiver desatualizado (versão menor
//   que a instalada), cai p/ a API de releases, como antes.
// - A API de releases do GitHub NÃO devolve sha256 dos assets (confirmado
//   ao vivo: o JSON só traz name/size/browser_download_url). Por isso o
//   hash vem do feed; como 2º caminho, o corpo da release (notas) pode
//   trazer linhas `SHA-256 (arm64-v8a): <hex>` — [parseSha256FromBody].
//   Sem hash em nenhum dos dois, o download confere só o tamanho (como
//   no Lote 15) e a tela diz isso com honestidade — nunca finge que
//   conferiu o hash.
// - O APK baixado é o da ABI do aparelho ([pickAbi] sobre
//   Build.SUPPORTED_ABIS): `AOTV-vX.Y.Z-arm64-v8a.apk` em vez do
//   universal de ~76 MB. A release de hoje só traz o universal — nesse
//   caso o universal continua sendo o baixado; o per-ABI passa a valer
//   quando a release trouxer o asset com o sufixo da ABI (o workflow do
//   CI é do Davi; nada aqui mexe nele).
// - O hash é conferido ANTES de pedir a instalação ([downloadApk]):
//   divergente = arquivo apagado + erro, sem instalar nada.
// - `updates.json` precisa ser atualizado a cada release (versão + hash
//   do APK novo, tirado do asset publicado). Sem isso, o feed fica
//   desatualizado e o app usa a API de releases (só tamanho).
// - O changelog da versão nova vem do feed ou do corpo da release e é
//   guardado nas prefs p/ o "What's new" (ver SettingsViewModel).

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

/**
 * Release do GitHub com APK. [apkUrl] é SEMPRE o browser_download_url do asset .apk.
 * [sha256] é o hash do APK ("", quando o feed e o corpo da release não
 * trouxeram — nesse caso só o tamanho é conferido). [changelog] é o texto
 * das notas da release (feed ou corpo), guardado p/ o "What's new".
 * [abi] é a ABI do asset ("arm64-v8a", "armeabi-v7a" ou "" = universal).
 */
data class AppRelease(
    val tag: String,
    val name: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val publishedAt: String,
    val sha256: String = "",
    val changelog: String = "",
    val abi: String = "",
)

/** APK baixado e conferido. [hashVerified] = o SHA-256 do feed foi conferido (false = só o tamanho). */
data class ApkDownload(
    val file: File,
    val hashVerified: Boolean,
    val sha256: String,
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
 * Prefere o asset da ABI do aparelho ([supportedAbis], ex. Build.SUPPORTED_ABIS);
 * sem asset da ABI, usa o universal. O SHA-256, quando presente, vem do corpo
 * da release (linhas `SHA-256 (abi): <hex>`); a API de releases não o devolve.
 */
fun pickApkAsset(json: JSONObject, supportedAbis: Array<String> = Build.SUPPORTED_ABIS): AppRelease? {
    val tag = json.optString("tag_name", "").trim()
    if (tag.isBlank()) return null
    val body = json.optString("body", "")
    val assets = json.optJSONArray("assets") ?: return null
    val wantedAbi = pickAbi(supportedAbis)
    var universal: AppRelease? = null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val url = asset.optString("browser_download_url", "")
        if (!isApkAssetUrl(url)) continue
        val abi = assetAbi(asset.optString("name", ""))
        val release = AppRelease(
            tag = tag,
            name = json.optString("name", tag),
            apkUrl = url,
            sizeBytes = asset.optLong("size", 0L),
            publishedAt = json.optString("published_at", ""),
            sha256 = parseSha256FromBody(body, abi),
            changelog = body,
            abi = abi,
        )
        if (abi.isNotEmpty() && abi == wantedAbi) return release
        if (abi.isEmpty() && universal == null) universal = release
    }
    return universal
}

/** SHA-256 em hex de um arquivo (streaming, sem carregar tudo na memória). "" = não leu. */
fun sha256Hex(file: File): String {
    if (!file.exists() || file.length() <= 0) return ""
    return runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}

/** Um SHA-256 válido tem 64 dígitos hexadecimais (minúsculos ou não). */
fun isValidSha256(s: String): Boolean {
    val clean = s.trim()
    return clean.length == 64 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

/**
 * Extrai o SHA-256 do corpo da release (notas). Formatos aceitos, um por linha:
 * `SHA-256 (arm64-v8a): <hex>`, `SHA-256 arm64-v8a: <hex>`, `SHA256: <hex>`
 * (o último vale p/ o universal). [abi] "" = procura só o genérico; com ABI,
 * prefere a linha da ABI e cai p/ a genérica se não houver. "" = não achou.
 */
fun parseSha256FromBody(body: String, abi: String): String {
    if (body.isBlank()) return ""
    val hex = "([0-9a-fA-F]{64})"
    // 1º: a linha da ABI pedida (só ela — a genérica não vale aqui).
    if (abi.isNotBlank()) {
        val abiOnly = Regex(
            "(?im)^\\s*sha[-_ ]?256\\s*(?:\\(\\s*${Regex.escape(abi)}\\s*\\)|${Regex.escape(abi)})\\s*:\\s*$hex",
        ).find(body)
        if (abiOnly != null) return abiOnly.groupValues[1].lowercase()
    }
    // 2º: a linha genérica (vale p/ o universal; p/ ABI, só quando não há linha própria).
    val generic = Regex("(?im)^\\s*sha[-_ ]?256\\s*:\\s*$hex").find(body)
    if (generic != null) return generic.groupValues[1].lowercase()
    return ""
}

/** ABI de um nome de asset (`AOTV-v1.28.0-arm64-v8a.apk` → "arm64-v8a"; sem sufixo → "" = universal). */
fun assetAbi(fileName: String): String {
    val lower = fileName.trim().lowercase()
    return when {
        "-arm64-v8a." in lower -> "arm64-v8a"
        "-armeabi-v7a." in lower -> "armeabi-v7a"
        else -> ""
    }
}

/**
 * A ABI do aparelho p/ baixar: a primeira de [supportedAbis] que o feed
 * conhece. "" = nenhuma conhecida (o universal é o único que serve).
 */
fun pickAbi(supportedAbis: Array<String>): String {
    val known = setOf("arm64-v8a", "armeabi-v7a")
    return supportedAbis.firstOrNull { it in known } ?: ""
}

/**
 * Extrai a release do `updates.json` do repo (feed do Lote 18):
 * `{ "latest": { "version", "versionCode", "tag", "published_at",
 * "changelog", "assets": { "arm64-v8a"|"armeabi-v7a"|"universal":
 * { "url", "sha256", "size" } } } }`.
 * null = feed sem versão válida ou sem asset oficial p/ o aparelho.
 * Prefere o asset da ABI do aparelho; sem ele, o universal.
 */
fun parseUpdateFeed(json: JSONObject, supportedAbis: Array<String> = Build.SUPPORTED_ABIS): AppRelease? {
    val latest = json.optJSONObject("latest") ?: return null
    val version = latest.optString("version", "").trim()
    if (version.isBlank()) return null
    val tag = latest.optString("tag", "v$version").trim()
    val changelog = latest.optString("changelog", "")
    val publishedAt = latest.optString("published_at", "")
    val assets = latest.optJSONObject("assets") ?: return null
    val wantedAbi = pickAbi(supportedAbis)
    val order = listOfNotNull(wantedAbi.ifBlank { null }, "universal")
    for (abiKey in order) {
        val asset = assets.optJSONObject(abiKey) ?: continue
        val url = asset.optString("url", "").trim()
        if (!isApkAssetUrl(url)) continue
        val sha = asset.optString("sha256", "").trim().lowercase()
        return AppRelease(
            tag = tag,
            name = tag,
            apkUrl = url,
            sizeBytes = asset.optLong("size", 0L),
            publishedAt = publishedAt,
            sha256 = if (isValidSha256(sha)) sha else "",
            changelog = changelog,
            abi = if (abiKey == "universal") "" else abiKey,
        )
    }
    return null
}

/**
 * Changelog embutido no app p/ a versão instalada ("What's new" sem
 * depender do feed — vale p/ quem atualiza por sideload do APK das
 * Actions, sem passar pela checagem). Só cobre o que este lote entrega;
 * o changelog completo da versão vive nas notas da release / no feed.
 * "" = versão sem notas embutidas.
 */
fun embeddedChangelogFor(version: String): String = when (version.trim()) {
    "1.30.0" -> "Novidades do Lote 20: gravações com destino em rede/USB e jogos do time seguido; dicionário de esportes embutido; Multiview com faixa de placares e layouts com nome; logos de canais do tv-logos; catch-up Xtream e conta Xtream com telas em uso; tabelas da Champions e da Libertadores por grupo; fix do atualizador automático e do 'Testar chave' do MDBList; olho nas chaves de API; packs de selos no formato Fusion do Nuvio. O changelog completo da versão está nas notas da release no GitHub."
    "1.29.0" -> "Novidades do Lote 19:\n" +
        "• Watch Party: assista junto com sincronização real, voz, chat e reações.\n" +
        "• Social Hub: amigos, film night e couch match — tudo local, sem servidor; \"Assistir junto\" abre a Watch Party em 1 toque.\n" +
        "• \"Ver todos\" em cada fileira da Início: abre a grade com todos os itens.\n" +
        "• Fix do MDBList: notas voltam a aparecer no detalhe, com o motivo quando não há nota.\n" +
        "O changelog completo da versão está nas notas da release no GitHub."
    "1.28.0" -> "Novidades do Lote 18 (atualizador):\n" +
        "• Atualizador com SHA-256: o APK baixado tem o hash conferido antes de instalar; sem hash no feed, só o tamanho é conferido e a tela diz isso.\n" +
        "• APK da ABI do aparelho: baixa o APK arm64-v8a ou armeabi-v7a em vez do universal (~76 MB), quando a release trouxer o asset da ABI.\n" +
        "• Atualização em 2º plano: com a opção ligada, a versão nova baixa sozinha e avisa quando estiver pronta p/ instalar.\n" +
        "• Novidades após atualizar: esta tela mostra o que mudou; a bolinha no avatar some quando você a vê.\n" +
        "O changelog completo da versão está nas notas da release no GitHub."
    else -> ""
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
        /** Feed do Lote 18 (v1.28.0): versão + changelog + sha256/size por ABI. Arquivo na raiz do repo. */
        const val UPDATE_FEED_URL = "https://raw.githubusercontent.com/Ivadzf/AOTV/main/updates.json"
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

    /** Já passou o intervalo p/ checar de novo sozinho (24 h desde a última).
     * Lote 20 (v1.30.0): o gate é ignorado quando a versão instalada mudou
     * desde o último check — atualizar o app dispara o check no próximo
     * cold start, mesmo que a versão anterior tenha checado há pouco. */
    fun shouldAutoCheck(nowMs: Long = System.currentTimeMillis()): Boolean =
        autoCheckEnabled && (prefs.appUpdateLastCheckVersion != installedVersionName ||
            nowMs - prefs.appUpdateLastCheck >= AUTO_CHECK_INTERVAL_MS)

    /**
     * Consulta a release mais recente. Ordem (Lote 18, v1.28.0): 1º o feed
     * `updates.json` (traz sha256 + changelog + asset da ABI); se o feed
     * falhar (404, sem rede, JSON inválido) ou estiver desatualizado
     * (versão menor que a instalada), cai p/ a API de releases, como
     * antes. 404 na API = nenhuma release publicada ainda
     * ([UpdateCheckResult.NoReleases]); erro de rede vira [UpdateCheckResult.Error]
     * com a mensagem real. A comparação é por versionName semântico — a tag
     * não carrega versionCode, então não há como comparar por código.
     * Quando há versão nova (ou a instalada, vinda do feed), o changelog
     * é guardado nas prefs p/ o "What's new".
     */
    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installed = parseVersionTag(installedVersionName)
            ?: return@withContext UpdateCheckResult.Error("Versão instalada inválida: $installedVersionName")
        fetchFeedRelease()?.let { feedRelease ->
            val feedVersion = parseVersionTag(feedRelease.tag)
            if (feedVersion != null && compareVersions(feedVersion, installed) >= 0) {
                prefs.appUpdateLastCheck = System.currentTimeMillis()
                prefs.appUpdateLastCheckVersion = installedVersionName
                saveChangelog(feedRelease)
                return@withContext if (compareVersions(feedVersion, installed) > 0) {
                    UpdateCheckResult.Available(feedRelease)
                } else {
                    UpdateCheckResult.UpToDate(feedRelease.tag)
                }
            }
            // Feed desatualizado (versão menor que a instalada): a API de
            // releases decide — o feed pode estar sem a release nova.
        }
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
            prefs.appUpdateLastCheckVersion = installedVersionName
            val release = pickApkAsset(json) ?: return@withContext UpdateCheckResult.NoReleases
            val latest = parseVersionTag(release.tag)
                ?: return@withContext UpdateCheckResult.Error("Tag da release inválida: ${release.tag}")
            saveChangelog(release)
            if (compareVersions(latest, installed) > 0) UpdateCheckResult.Available(release)
            else UpdateCheckResult.UpToDate(release.tag)
        }
    }

    /**
     * Lê o feed `updates.json`. null = feed ausente, sem rede, inválido
     * ou sem asset oficial p/ o aparelho (o chamador cai p/ a API de
     * releases — nunca é erro p/ o usuário).
     */
    private fun fetchFeedRelease(): AppRelease? {
        val req = Request.Builder()
            .url(UPDATE_FEED_URL)
            .header("User-Agent", "AOTV/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        val resp = runCatching { http.newCall(req).execute() }.getOrNull() ?: return null
        resp.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return parseUpdateFeed(json)
        }
    }

    /** Guarda o changelog da release p/ o "What's new" (só quando há texto). */
    private fun saveChangelog(release: AppRelease) {
        val text = release.changelog.trim()
        if (text.isBlank()) return
        prefs.whatsNewVersion = release.tag.trim().removePrefix("v").removePrefix("V")
        prefs.whatsNewChangelog = text
    }

    /**
     * Baixa o APK da release p/ o cache do app. Só aceita o asset oficial
     * ([isApkAssetUrl]); qualquer outra URL é recusada antes de baixar.
     * Confere o tamanho baixado contra o informado pela release (Lote 15,
     * v1.25.0 — Bloco 4, item 7): tamanho diferente = download incompleto
     * (o parcial é apagado e o erro volta, sem instalar nada). Sem tamanho
     * informado (0), só vale o "não vazio" de antes.
     *
     * Lote 18 (v1.28.0): com SHA-256 no feed (ou no corpo da release), o
     * hash do arquivo baixado é conferido ANTES de pedir a instalação —
     * divergente = arquivo apagado + erro "SHA-256 não confere", sem
     * instalar nada. Sem hash ([AppRelease.sha256] vazio ou inválido), só
     * o tamanho é conferido e [ApkDownload.hashVerified] volta false — a
     * tela mostra isso com honestidade, sem fingir verificação.
     */
    suspend fun downloadApk(context: Context, release: AppRelease): Result<ApkDownload> = withContext(Dispatchers.IO) {
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
            val expectedSha = release.sha256.trim().lowercase()
            if (isValidSha256(expectedSha)) {
                val actualSha = sha256Hex(outFile)
                if (actualSha != expectedSha) {
                    outFile.delete()
                    error("SHA-256 não confere: o APK baixado não é o da release oficial")
                }
                ApkDownload(outFile, hashVerified = true, sha256 = expectedSha)
            } else {
                ApkDownload(outFile, hashVerified = false, sha256 = "")
            }
        }
    }

    /** APK já baixado e conferido no cache (download em 2º plano), se existir. null = nada pronto. */
    fun cachedApkFile(context: Context): File? {
        val file = File(context.cacheDir, APK_FILE_NAME)
        return if (file.exists() && file.length() > 0) file else null
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
