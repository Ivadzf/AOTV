package com.aotv.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.backup.AotvBackup
import com.aotv.app.data.backup.BackupWifiServer
import com.aotv.app.data.drive.DriveSyncService
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.Profile
import com.aotv.app.data.local.SCOPED_KEYS
import com.aotv.app.data.plugins.PluginStore
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.ui.theme.CustomThemeColors
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Backup e restauração (v1.13.0), no desenho do JOTV 1.0.19: arquivo .json
 * (SAF), colar JSON como texto e servidor Wi-Fi temporário na rede local.
 * Sem servidor externo — tudo fica entre os aparelhos do usuário.
 *
 * O backup é do PERFIL ATIVO: addons (ordem + ativação), repositórios e
 * plugins, Minha Lista, Continuar assistindo, episódios vistos, buscas
 * recentes, coleções, organização da Início e preferências (por perfil via
 * SCOPED_KEYS + globais). Limite de 8 MB nos dois sentidos.
 *
 * SEGREDOS: com o toggle "Incluir chaves e sessões" DESLIGADO (o padrão), o
 * backup sai sem tokens/senhas/chaves e a restauração avisa p/ configurar
 * de novo. Ligado, entram as sessões que vivem no PlatformPrefs (Trakt,
 * Simkl, MDBList, OMDb, AniList, WeTrakr, PublicMetaDB, Discord,
 * Jellyfin/Emby, client ID do Anime-Skip, chave TMDB, hash do PIN dos pais,
 * hashes de PIN dos perfis e configurações dos plugins). Debrid, Nuvio e
 * Stremio NUNCA entram: vivem em SharedPreferences próprias, sem caminho de
 * exportação/importação nesses serviços — restaurar exige reconectar as três.
 * NUNCA entram URLs de fonte (lastSourceUrl): links de addon trazem
 * token/assinatura. Nada de segredo vai p/ log.
 *
 * Sync via Google Drive (Lote 10, v1.20.0): o MESMO JSON do backup local
 * sobe e desce do Drive ([DriveSyncService]) — sem tocar no backup
 * local/Wi-Fi. O upload respeita o toggle "Incluir chaves e sessões"
 * porque o JSON já vem montado com ele. OAuth com client ID configurável,
 * vazio por padrão (sem ele a função fica desligada e a tela explica o
 * que falta). Conflito (padrão da fork ReSeq): se o arquivo do Drive for
 * mais novo que o último sync local, o upload trava e a tela pergunta
 * antes de subir por cima.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val library: LibraryRepository,
    private val plugins: PluginStore,
    private val drive: DriveSyncService,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** Restauração aguardando confirmação (resumo mostrado antes de aplicar). */
    data class PendingRestore(val summary: AotvBackup.Summary, val json: String, val source: String)

    /** Upload travado: o Drive tem um backup mais novo que o último sync. */
    data class DriveConflict(val remoteName: String, val remoteModifiedMs: Long)

    data class BackupUiState(
        val includeSecrets: Boolean = false,
        val busy: String? = null,
        val message: String? = null,
        val serverRunning: Boolean = false,
        val serverUrl: String? = null,
        val serverPin: String? = null,
        val pendingRestore: PendingRestore? = null,
        val driveClientId: String = "",
        val driveConnected: Boolean = false,
        val driveAccountEmail: String = "",
        val driveLastSyncAt: Long = 0L,
        val driveConflict: DriveConflict? = null,
        // Lote 23 (v1.33.0): sync contínuo p/ o Drive (automático + manual).
        val continuousSyncEnabled: Boolean = false,
        val continuousSyncLastMs: Long = 0L,
    )

    private val _uiState = MutableStateFlow(
        BackupUiState(
            includeSecrets = prefs.backupIncludeSecrets,
            driveClientId = prefs.driveClientId,
            driveConnected = prefs.driveAccountEmail.isNotBlank(),
            driveAccountEmail = prefs.driveAccountEmail,
            driveLastSyncAt = prefs.driveLastSyncAt,
            continuousSyncEnabled = prefs.continuousSyncEnabled,
            continuousSyncLastMs = prefs.continuousSyncLastMs,
        )
    )
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var wifiServer: BackupWifiServer? = null

    /** Metadados do arquivo baixado do Drive, p/ gravar o sync DEPOIS de restaurar (confirmar ou cancelar). */
    private var pendingDriveMeta: DriveSyncService.RemoteFile? = null

    fun setIncludeSecrets(v: Boolean) {
        prefs.backupIncludeSecrets = v
        _uiState.update { it.copy(includeSecrets = v, message = null) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    // ── Sync contínuo (Lote 23, v1.33.0) ───────────────────────────────────
    // Sobe o backup p/ o Drive sozinho (worker de 6 h + na abertura, ver
    // ContinuousSync); o toggle aqui liga/desliga e aplica o worker na hora.
    fun setContinuousSyncEnabled(v: Boolean) {
        prefs.continuousSyncEnabled = v
        com.aotv.app.data.sync.ContinuousSync.applyEnabled(appContext)
        _uiState.update { it.copy(continuousSyncEnabled = v, message = null) }
    }

    /** Sincroniza agora (mesmo caminho do worker) e mostra o resultado. */
    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = "Sincronizando…", message = null) }
            val result = com.aotv.app.data.sync.ContinuousSync.syncNow(appContext)
            _uiState.update {
                it.copy(
                    busy = null,
                    continuousSyncLastMs = prefs.continuousSyncLastMs,
                    driveLastSyncAt = prefs.driveLastSyncAt,
                    message = result.getOrDefault("Não foi possível sincronizar agora."),
                )
            }
        }
    }

    // ── Google Drive (Lote 10, v1.20.0) ────────────────────────────────────

    /** Guarda o Client ID do Google (vazio = sync desligado; limpar desconecta). */
    fun setDriveClientId(id: String) {
        prefs.driveClientId = id
        if (id.isBlank()) {
            drive.signOut()
            prefs.driveAccountEmail = ""
            _uiState.update { it.copy(driveClientId = "", driveConnected = false, driveAccountEmail = "", message = null) }
        } else {
            _uiState.update { it.copy(driveClientId = prefs.driveClientId, message = null) }
        }
    }

    /** Confere a sessão Google ao abrir a tela (o e-mail guardado pode estar velho). */
    fun refreshDriveSession(context: Context) {
        val account = drive.lastSignedInAccount()
        if (account == null) {
            if (prefs.driveAccountEmail.isNotBlank()) prefs.driveAccountEmail = ""
            _uiState.update { it.copy(driveConnected = false, driveAccountEmail = "") }
        } else {
            val email = account.email ?: prefs.driveAccountEmail
            if (email.isNotBlank()) prefs.driveAccountEmail = email
            _uiState.update { it.copy(driveConnected = true, driveAccountEmail = email) }
        }
    }

    /** Intent do login Google. Null = sem client ID configurado (função desligada). */
    fun driveSignInIntent(context: Context): Intent? = drive.signInIntent(prefs.driveClientId)

    /** Login concluído: guarda o e-mail e marca conectado (null = cancelado). */
    fun onDriveSignIn(account: GoogleSignInAccount?) {
        if (account == null) {
            _uiState.update { it.copy(message = "Login cancelado.") }
            return
        }
        val email = account.email ?: ""
        if (email.isNotBlank()) prefs.driveAccountEmail = email
        _uiState.update { it.copy(driveConnected = true, driveAccountEmail = email, message = "Conta Google conectada.") }
    }

    /** Falha no login (o erro real vem do Google Sign-In). */
    fun onDriveSignInError(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    /** Desconecta a conta Google (não apaga o arquivo do Drive). */
    fun driveSignOut() {
        drive.signOut()
        prefs.driveAccountEmail = ""
        _uiState.update { it.copy(driveConnected = false, driveAccountEmail = "", message = "Conta Google desconectada.") }
    }

    /**
     * Sobe o backup (o MESMO JSON do arquivo local) p/ o Drive. Sem [force],
     * arquivo remoto mais novo que o último sync trava em [DriveConflict] e
     * a tela pergunta antes de subir por cima.
     */
    fun uploadToDrive(force: Boolean = false) {
        viewModelScope.launch {
            val account = drive.lastSignedInAccount()
            if (account == null) {
                _uiState.update { it.copy(message = "Conecte a conta Google primeiro.") }
                return@launch
            }
            _uiState.update { it.copy(busy = "Enviando p/ o Google Drive…", message = null, driveConflict = null) }
            val built = buildBackupJson()
            val json = built.getOrNull()
            if (json == null) {
                _uiState.update { it.copy(busy = null, message = built.exceptionOrNull()?.message ?: "Não foi possível montar o backup.") }
                return@launch
            }
            val lastRemote = prefs.driveLastRemoteModifiedMs.takeIf { it > 0L }
            val result = withContext(Dispatchers.IO) { drive.uploadBackup(account, json, lastRemote, force) }
            result.onSuccess { outcome ->
                when (outcome) {
                    is DriveSyncService.UploadResult.Uploaded -> {
                        prefs.driveFileId = outcome.fileId
                        prefs.driveLastRemoteModifiedMs = outcome.modifiedTimeMs
                        prefs.driveLastSyncAt = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(busy = null, driveLastSyncAt = prefs.driveLastSyncAt, message = "Backup enviado p/ o Google Drive.")
                        }
                    }
                    is DriveSyncService.UploadResult.Conflict -> {
                        _uiState.update {
                            it.copy(
                                busy = null,
                                driveConflict = DriveConflict(outcome.remote.name, outcome.remote.modifiedTimeMs),
                                message = "O Drive tem um backup mais novo que o último sync.",
                            )
                        }
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(busy = null, message = e.message ?: "Não foi possível enviar p/ o Google Drive.") }
            }
        }
    }

    /** O usuário confirmou subir por cima do arquivo mais novo do Drive. */
    fun confirmDriveOverwrite() {
        _uiState.update { it.copy(driveConflict = null) }
        uploadToDrive(force = true)
    }

    fun dismissDriveConflict() = _uiState.update { it.copy(driveConflict = null) }

    /**
     * Baixa o backup do Drive e mostra o resumo p/ confirmar antes de
     * restaurar (igual ao arquivo local). O sync só é gravado se a
     * restauração for confirmada (ver [confirmRestore]).
     */
    fun downloadFromDrive() {
        viewModelScope.launch {
            val account = drive.lastSignedInAccount()
            if (account == null) {
                _uiState.update { it.copy(message = "Conecte a conta Google primeiro.") }
                return@launch
            }
            _uiState.update { it.copy(busy = "Baixando do Google Drive…", message = null) }
            val result = withContext(Dispatchers.IO) { drive.downloadBackup(account) }
            result.onSuccess { (text, meta) ->
                pendingDriveMeta = meta
                prepareRestore(text, "drive")
            }.onFailure { e ->
                _uiState.update { it.copy(busy = null, message = e.message ?: "Não foi possível baixar do Google Drive.") }
            }
        }
    }

    // ── Exportar ────────────────────────────────────────────────────────────

    /** Monta o JSON do backup do perfil ativo. Erro quando passa de 8 MB. */
    /**
     * Monta o JSON `aotv-backup-v1` — delega p/ [AotvBackup.buildBackupJson]
     * (Lote 23: antes havia uma cópia idêntica aqui; fonte única agora).
     */
    suspend fun buildBackupJson(): Result<String> =
        AotvBackup.buildBackupJson(prefs, library, plugins)

    // ── Restaurar (com resumo + confirmação) ─────────────────────────────────

    /** Valida o texto e mostra o resumo p/ confirmar antes de aplicar. */
    fun prepareRestore(text: String, source: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = "Lendo backup…", message = null) }
            val parsed = runCatching { withContext(Dispatchers.IO) { AotvBackup.parse(text) } }
            parsed.onSuccess { root ->
                _uiState.update {
                    it.copy(busy = null, pendingRestore = PendingRestore(AotvBackup.summarize(root), text, source))
                }
            }.onFailure { e ->
                _uiState.update { it.copy(busy = null, message = e.message ?: "Não foi possível ler este backup.") }
            }
        }
    }

    fun dismissRestore() {
        pendingDriveMeta = null
        _uiState.update { it.copy(pendingRestore = null) }
    }

    /** Aplica a restauração que estava aguardando confirmação. */
    fun confirmRestore() {
        val pending = _uiState.value.pendingRestore ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = "Restaurando…", pendingRestore = null) }
            val result = runCatching { withContext(Dispatchers.IO) { restoreInternal(pending.json) } }
            result.onSuccess {
                val meta = pendingDriveMeta
                if (meta != null) {
                    prefs.driveFileId = meta.id
                    prefs.driveLastRemoteModifiedMs = meta.modifiedTimeMs
                    prefs.driveLastSyncAt = System.currentTimeMillis()
                    _uiState.update { it.copy(driveLastSyncAt = prefs.driveLastSyncAt) }
                    pendingDriveMeta = null
                }
            }
            _uiState.update { it.copy(busy = null, message = result.getOrElse { e -> e.message ?: "Não foi possível restaurar este backup." }) }
        }
    }

    /**
     * Aplica o backup. Ordem: perfis → addons → prefs do perfil → globais →
     * trocar p/ o perfil do backup (recarrega os fluxos) → plugins (rede) →
     * lista, Continuar e vistos. O Continuar respeita a lista de ocultos do
     * perfil: o título removido não volta nem com backup antigo.
     */
    private suspend fun restoreInternal(text: String): String {
        val root = AotvBackup.parse(text)
        val includesSecrets = root.optBoolean("includesSecrets", false)
        // Perfis (o hash do PIN só vem quando o backup incluiu segredos).
        val profilesArr = root.optJSONArray("profiles")
        val profiles = (0 until (profilesArr?.length() ?: 0)).mapNotNull { i ->
            profilesArr?.optJSONObject(i)?.let { profileFromJson(it) }
        }
        if (profiles.isNotEmpty()) prefs.restoreProfiles(profiles)
        val targetProfileId = root.optJSONObject("profile")?.optString("id").orEmpty()
            .takeIf { id -> profiles.any { it.id == id } }
            ?: prefs.profileId.value
        // Addons (ordem + ativação; o setter avisa os fluxos e recarrega o layout).
        root.optJSONArray("addons")?.let { arr ->
            val entries = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (!url.startsWith("http")) null
                else com.aotv.app.data.local.AddonEntry(url, o.optBoolean("enabled", true), o.optString("id"))
            }
            prefs.addonEntries = entries
        }
        // Prefs do perfil (layout, coleções, buscas, ocultos, player, detalhe…).
        root.optJSONObject("profilePrefs")?.let { obj ->
            obj.keys().forEach { key ->
                if (!includesSecrets && key in AotvBackup.SECRET_SCOPED_KEYS) return@forEach
                AotvBackup.decodePrefValue(obj.optJSONObject(key))?.let { prefs.writeRawPref(key, it, targetProfileId) }
            }
        }
        // Globais: o cru vai direto; os que têm fluxo passam pelo setter p/ avisar.
        root.optJSONObject("globalPrefs")?.let { obj ->
            obj.keys().forEach { key ->
                if (!includesSecrets && key in AotvBackup.SECRET_GLOBAL_KEYS) return@forEach
                if (key !in AotvBackup.GLOBAL_KEYS && key !in AotvBackup.SECRET_GLOBAL_KEYS) return@forEach
                AotvBackup.decodePrefValue(obj.optJSONObject(key))?.let { prefs.writeRawGlobalPref(key, it) }
            }
            refreshGlobalFlows(obj)
        }
        // Troca p/ o perfil do backup: recarrega layout, coleções e prefs do perfil.
        prefs.selectProfile(targetProfileId)
        // Plugins: reinstala os repositórios (rede) e aplica habilitados/ordem.
        var pluginNote = ""
        root.optJSONObject("plugins")?.let { obj ->
            val r = plugins.restore(obj.toString())
            pluginNote = if (r.reposFail > 0) " Plugins: ${r.reposOk} repositório(s), ${r.reposFail} falha(s) — confira a rede e tente de novo na tela de Plugins."
            else if (r.reposOk > 0) " Plugins: ${r.reposOk} repositório(s), ${r.pluginsOk} plugin(s)."
            else ""
        }
        // Minha Lista (não mexe no que já estava salvo).
        root.optJSONArray("watchlist")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val o = arr.optJSONObject(i) ?: return@forEach
                val tmdbId = o.optInt("tmdbId")
                if (tmdbId <= 0) return@forEach
                library.importToList(
                    tmdbId, o.optString("type", "MOVIE"), o.optString("title"),
                    o.optString("posterUrl"), o.optString("year"), o.optLong("addedAt", 0L), targetProfileId,
                )
            }
        }
        // Continuar assistindo: pula o título oculto (removido pelo usuário).
        root.optJSONArray("continue")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val o = arr.optJSONObject(i) ?: return@forEach
                val tmdbId = o.optInt("tmdbId")
                val type = o.optString("type", "MOVIE")
                if (tmdbId <= 0 || prefs.isContinueHidden(tmdbId, type)) return@forEach
                library.importPlayback(
                    tmdbId, type, o.optString("title"), o.optString("posterUrl"),
                    o.optLong("positionMs", 0L), o.optLong("durationMs", 0L),
                    o.optString("lastEpisode"), o.optLong("lastWatchedAt", 0L), targetProfileId,
                )
            }
        }
        // Episódios vistos.
        root.optJSONArray("watched")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val o = arr.optJSONObject(i) ?: return@forEach
                val seriesId = o.optInt("seriesTmdbId")
                if (seriesId <= 0) return@forEach
                library.markEpisodeWatched(seriesId, o.optInt("season", 0), o.optInt("episode", 0), targetProfileId)
            }
        }
        return "Backup restaurado." +
            (if (!includesSecrets) " Configure novamente suas senhas e sessões." else "") +
            pluginNote
    }

    /**
     * Globais com fluxo (tema, blur, catálogo, plugins): o valor cru já foi
     * gravado; o setter reescreve o mesmo valor e avisa quem observa.
     */
    private fun refreshGlobalFlows(obj: JSONObject) {
        fun str(key: String): String? = AotvBackup.decodePrefValue(obj.optJSONObject(key)) as? String
        fun bool(key: String): Boolean? = AotvBackup.decodePrefValue(obj.optJSONObject(key)) as? Boolean
        fun int(key: String): Int? = when (val v = AotvBackup.decodePrefValue(obj.optJSONObject(key))) {
            is Int -> v
            is Long -> v.toInt()
            else -> null
        }
        str("theme_palette_v1")?.let { prefs.themePaletteId = it }
        str("custom_theme_v1")?.let { prefs.customTheme = CustomThemeColors.fromJson(it) }
        bool("pref_panel_blur")?.let { prefs.panelBlur = it }
        // Lote 11 (v1.21.0): a variante de ícone do backup vira o alias ativo
        // — a pref crua sozinha não trocaria o ícone da tela inicial. Se a
        // troca falhar, a pref volta p/ a variante ativa de fato (nunca diz
        // uma cor que não está no launcher).
        str("app_icon_variant")?.let { id ->
            if (!com.aotv.app.data.local.AppIconVariants.applyVariant(appContext, id)) {
                prefs.appIconVariant = com.aotv.app.data.local.AppIconVariants.currentVariantId(appContext)
            }
        }
        bool("aotv_catalog_on")?.let { prefs.catalogOn = it }
        int("plugin_timeout_sec")?.let { prefs.pluginTimeoutSec = it }
        int("plugin_concurrency")?.let { prefs.pluginConcurrency = it }
        bool("plugin_auto_update")?.let { prefs.pluginAutoUpdate = it }
        bool("plugin_global_enabled")?.let { prefs.pluginGlobalEnabled = it }
        // Lote 12 — Frente 3 (v1.22.0): checagem de atualização do app.
        // Long não tem helper aqui: o valor cru já foi restaurado pelo loop
        // genérico; o setter abaixo só sincroniza o fluxo em memória.
        bool("app_update_check_enabled")?.let { prefs.appUpdateCheckEnabled = it }
        (AotvBackup.decodePrefValue(obj.optJSONObject("app_update_last_check_v1")) as? Long)
            ?.let { prefs.appUpdateLastCheck = it }
    }

    private fun profileFromJson(o: JSONObject): Profile? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return Profile(
            id, o.optString("name", "Perfil"), o.optInt("color", 0), o.optBoolean("kids", false),
            o.optString("avatarUrl", ""), o.optString("avatarId", ""),
            o.optString("pinHash", ""), o.optBoolean("usePrimaryAddons", false),
        )
    }

    // ── Servidor Wi-Fi ──────────────────────────────────────────────────────

    /** Abre o servidor (monta o backup na hora) e mostra IP, porta, QR e PIN. */
    fun startServer() {
        if (wifiServer != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = "Montando backup…", message = null) }
            val built = buildBackupJson()
            val json = built.getOrNull()
            if (json == null) {
                _uiState.update { it.copy(busy = null, message = built.exceptionOrNull()?.message ?: "Não foi possível montar o backup.") }
                return@launch
            }
            // O upload que chega restaura neste aparelho (o PIN é a autorização).
            val server = BackupWifiServer(json) { text ->
                runBlocking { runCatching { restoreInternal(text) }.getOrElse { e -> e.message ?: "Não foi possível restaurar este backup." } }
            }
            val url = withContext(Dispatchers.IO) { runCatching { server.start() }.getOrNull() }
            if (url == null) {
                server.stop()
                _uiState.update { it.copy(busy = null, message = "Sem rede Wi-Fi para abrir o servidor.") }
                return@launch
            }
            wifiServer = server
            _uiState.update { it.copy(busy = null, serverRunning = true, serverUrl = url, serverPin = server.pin) }
        }
    }

    /** Fecha o servidor (ao sair da tela e no onCleared). */
    fun stopServer() {
        wifiServer?.stop()
        wifiServer = null
        _uiState.update { it.copy(serverRunning = false, serverUrl = null, serverPin = null) }
    }

    // ── Outro aparelho pelo Wi-Fi ───────────────────────────────────────────

    /** Traz o backup de outro aparelho (GET /backup) e mostra o resumo p/ confirmar. */
    fun fetchFrom(host: String, port: Int, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = "Buscando backup…", message = null) }
            val result = BackupWifiServer.fetchBackupFrom(host, port, pin)
            result.onSuccess { prepareRestore(it, "wifi") }
                .onFailure { e ->
                    _uiState.update { it.copy(busy = null, message = e.message ?: "Não foi possível buscar o backup.") }
                }
        }
    }

    /** Envia o backup p/ outro aparelho (POST /upload). */
    fun sendTo(host: String, port: Int, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = "Enviando backup…", message = null) }
            val built = buildBackupJson()
            val json = built.getOrNull()
            if (json == null) {
                _uiState.update { it.copy(busy = null, message = built.exceptionOrNull()?.message ?: "Não foi possível montar o backup.") }
                return@launch
            }
            val result = BackupWifiServer.sendBackupTo(host, port, pin, json)
            _uiState.update {
                it.copy(
                    busy = null,
                    message = result.getOrElse { e -> e.message ?: "Não foi possível enviar o backup." },
                )
            }
        }
    }

    override fun onCleared() {
        wifiServer?.stop()
        wifiServer = null
        super.onCleared()
    }
}
