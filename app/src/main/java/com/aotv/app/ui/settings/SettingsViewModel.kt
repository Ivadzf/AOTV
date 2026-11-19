package com.aotv.app.ui.settings

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.recordings.RecordingStore
import com.aotv.app.data.recordings.ScheduledRecording
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progresso do download manual da atualização (Lote 21, v1.31.0). */
data class UpdateDownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
    /** 0..1 (0 quando o total é desconhecido). */
    val fraction: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (fraction * 100).toInt()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val platformPrefs: PlatformPrefs,
    private val healthChecker: com.aotv.app.data.remote.SourceHealthChecker,
    private val trakt: com.aotv.app.data.remote.TraktService,
    private val history: com.aotv.app.data.local.dao.MovieHistoryDao,
    private val recordings: RecordingStore,
    private val updateService: com.aotv.app.data.remote.AppUpdateService,
    @ApplicationContext private val appContext: Context,
    /** Addons Kodi (Lote 22, item 3): instalar por pareamento local. */
    private val kodiAddonStore: com.aotv.app.data.kodi.KodiAddonStore,
    /** Histórico p/ o handoff recebido (Lote 22, item 1). */
    private val library: com.aotv.app.data.repository.LibraryRepository,
) : ViewModel() {

    /** Prefs (p/ a página "Controle remoto e TV" montar servidor/cliente). */
    val prefs: PlatformPrefs get() = platformPrefs

    // ── Trakt (v1.17.0: Client ID/Secret do usuário, por perfil, na tela de Trackers) ──
    val traktConfigured = trakt.configured
    private val _traktUser = MutableStateFlow(trakt.user())
    /** Conta conectada no perfil ativo. null sem login. */
    val traktUser: StateFlow<String?> = _traktUser.asStateFlow()
    /** Conta conectada neste perfil (tem token), mesmo sem nome. */
    val traktConnected: Boolean get() = trakt.isConnected
    /** Código do login em andamento. null fora do login. */
    private val _traktCode = MutableStateFlow<com.aotv.app.data.remote.TraktService.DeviceCode?>(null)
    val traktCode: StateFlow<com.aotv.app.data.remote.TraktService.DeviceCode?> = _traktCode.asStateFlow()
    /** Chamada de rede do Trakt em andamento (conectar ou sincronizar). Trava o segundo clique. */
    private val _traktBusy = MutableStateFlow<String?>(null)
    val traktBusy: StateFlow<String?> = _traktBusy.asStateFlow()
    private var loginJob: kotlinx.coroutines.Job? = null

    fun traktConnect() {
        if (_traktBusy.value != null) return
        _traktBusy.value = "Pedindo código…"
        loginJob = viewModelScope.launch {
            val code = trakt.startLogin()
            if (code == null) { _traktBusy.value = null; return@launch }
            _traktCode.value = code
            _traktBusy.value = "Aguardando confirmação…"
            val name = trakt.waitLogin(code)
            _traktCode.value = null
            _traktBusy.value = null
            if (name != null) _traktUser.value = name
        }
    }

    fun traktCancel() { loginJob?.cancel(); _traktCode.value = null; _traktBusy.value = null }

    /**
     * "Sincronizar agora" (Acompanhamento). Chama o sync de verdade do
     * TraktService (v1.17.0, Frente A: suspend fun sync(): Boolean) e
     * relê a conta conectada quando dá certo. Trava o segundo clique
     * enquanto sincroniza, como o traktConnect(). Se a conta não tem
     * nome (user() null), mantém o valor atual em vez de apagar — nulo
     * aqui significaria "desconectado" na tela.
     */
    fun traktSync() {
        if (_traktBusy.value != null) return
        _traktBusy.value = "Sincronizando…"
        viewModelScope.launch {
            val ok = runCatching { trakt.sync() }.getOrDefault(false)
            if (ok) trakt.user()?.let { _traktUser.value = it }
            _traktBusy.value = null
        }
    }

    fun traktLogout() { trakt.logout(); _traktUser.value = null }

    /** Limpar Continuar assistindo: apaga as posições salvas do perfil ativo. Minha Lista continua. */
    fun clearContinueWatching() {
        viewModelScope.launch { history.clear(profileId.value) }
    }

    // ── Status das fontes (health-check) ──
    private val _sourceStatus = MutableStateFlow<List<com.aotv.app.data.remote.SourceStatus>>(emptyList())
    val sourceStatus: StateFlow<List<com.aotv.app.data.remote.SourceStatus>> = _sourceStatus.asStateFlow()

    private val _checkingHealth = MutableStateFlow(false)
    val checkingHealth: StateFlow<Boolean> = _checkingHealth.asStateFlow()

    fun checkSourcesHealth() {
        if (_checkingHealth.value) return
        viewModelScope.launch {
            _checkingHealth.value = true
            _sourceStatus.value = runCatching { healthChecker.checkAll() }.getOrDefault(emptyList())
            _checkingHealth.value = false
        }
    }

    // ── Downloads e dados móveis (Lote 5, v1.14.0) — do aparelho ──────────
    // Os fluxos vivem no PlatformPrefs (fonte única); aqui só o atalho + setter.
    val downloadsWifiOnly: StateFlow<Boolean> = platformPrefs.downloadsWifiOnlyFlow
    fun setDownloadsWifiOnly(v: Boolean) { platformPrefs.downloadsWifiOnly = v }

    val downloadFolderUri: StateFlow<String> = platformPrefs.downloadFolderUriFlow
    val downloadFolderName: StateFlow<String> = platformPrefs.downloadFolderNameFlow
    fun setDownloadFolder(uri: String, name: String) {
        platformPrefs.downloadFolderUri = uri
        platformPrefs.downloadFolderName = name
    }
    fun clearDownloadFolder() {
        platformPrefs.downloadFolderUri = ""
        platformPrefs.downloadFolderName = ""
    }

    val mobileDataToday: StateFlow<Long> = platformPrefs.mobileDataTodayFlow
    val mobileDataMonth: StateFlow<Long> = platformPrefs.mobileDataMonthFlow
    val mobileDataLimitMb: StateFlow<Long> = platformPrefs.mobileDataLimitMbFlow
    val mobileDataLimitReached: StateFlow<Boolean> = platformPrefs.mobileDataLimitReachedFlow
    fun setMobileDataLimitMb(v: Long) { platformPrefs.mobileDataLimitMb = v }
    fun refreshMobileData() { platformPrefs.refreshMobileDataCounters() }

    // ── Torrent/Downloads (Lote 18, v1.28.0) ──
    // Teto de espaço temporário do torrent + medição por tipo (ver PlatformPrefs).
    val mobileStreamingMonth: StateFlow<Long> = platformPrefs.mobileStreamingMonthFlow
    val mobileTorrentMonth: StateFlow<Long> = platformPrefs.mobileTorrentMonthFlow
    val torrentTempLimitMb: StateFlow<Int> = platformPrefs.torrentTempLimitMbFlow
    fun setTorrentTempLimitMb(v: Int) { platformPrefs.torrentTempLimitMb = v }
    val torrentDiscardWatched: StateFlow<Boolean> = platformPrefs.torrentDiscardWatchedFlow
    fun setTorrentDiscardWatched(v: Boolean) { platformPrefs.torrentDiscardWatched = v }

    // ── Esportes, Gravações e Calendário (Lote 8, v1.18.0) ───────────────
    // Os fluxos vivem no PlatformPrefs (fonte única); aqui só o atalho + setter.
    val sportsShow: StateFlow<Boolean> = platformPrefs.sportsShowFlow
    fun setSportsShow(v: Boolean) { platformPrefs.sportsShow = v }

    val sportsHideScores: StateFlow<Boolean> = platformPrefs.sportsHideScoresFlow
    fun setSportsHideScores(v: Boolean) { platformPrefs.sportsHideScores = v }

    val sportsScoreDelaySec: StateFlow<Int> = platformPrefs.sportsScoreDelaySecFlow
    fun setSportsScoreDelaySec(v: Int) { platformPrefs.sportsScoreDelaySec = v }

    val sportsLiveScores: StateFlow<Boolean> = platformPrefs.sportsLiveScoresFlow
    fun setSportsLiveScores(v: Boolean) { platformPrefs.sportsLiveScores = v }

    val sportsGameAlerts: StateFlow<Boolean> = platformPrefs.sportsGameAlertsFlow
    fun setSportsGameAlerts(v: Boolean) { platformPrefs.sportsGameAlerts = v }

    val calendarHideSpoilers: StateFlow<Boolean> = platformPrefs.calendarHideSpoilersFlow
    fun setCalendarHideSpoilers(v: Boolean) { platformPrefs.calendarHideSpoilers = v }

    // Lote 13 (v1.23.0): auto-add do calendário via Trakt/Simkl.
    val calendarAutoAddTrackers: StateFlow<Boolean> = platformPrefs.calendarAutoAddTrackersFlow
    fun setCalendarAutoAddTrackers(v: Boolean) { platformPrefs.calendarAutoAddTrackers = v }

    // ── Lote 23 (v1.33.0): biblioteca, perfil infantil, legendas, sync ──
    val statsPeriod: StateFlow<String> = platformPrefs.statsPeriodFlow
    fun setStatsPeriod(v: String) { platformPrefs.statsPeriod = v }

    val myListOrder: StateFlow<String> = platformPrefs.myListOrderFlow
    fun setMyListOrder(v: String) { platformPrefs.myListOrder = v }

    val blockedGenres: StateFlow<Set<Int>> = platformPrefs.blockedGenresFlow
    fun setBlockedGenres(v: Set<Int>) { platformPrefs.blockedGenres = v }

    val kidsAgeLimit: StateFlow<Int> = platformPrefs.kidsAgeLimitFlow
    fun setKidsAgeLimit(v: Int) { platformPrefs.kidsAgeLimit = v }

    val forcedSubsAuto: StateFlow<Boolean> = platformPrefs.forcedSubsAutoFlow
    fun setForcedSubsAuto(v: Boolean) { platformPrefs.forcedSubsAuto = v }

    val simklSyncEnabled: StateFlow<Boolean> = platformPrefs.simklSyncEnabledFlow
    fun setSimklSyncEnabled(v: Boolean) { platformPrefs.simklSyncEnabled = v }
    val simklLastPullMs: StateFlow<Long> = platformPrefs.simklLastPullMsFlow

    val continuousSyncEnabled: StateFlow<Boolean> = platformPrefs.continuousSyncEnabledFlow
    fun setContinuousSyncEnabled(v: Boolean) { platformPrefs.continuousSyncEnabled = v }
    val continuousSyncLastMs: StateFlow<Long> = platformPrefs.continuousSyncLastMsFlow
    /** Roda o sync contínuo agora (sobe o backup p/ o Drive, se configurado). */
    fun syncNow(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val r = com.aotv.app.data.sync.ContinuousSync.syncNow(appContext)
            onDone(r.getOrDefault("Falha ao sincronizar"))
        }
    }

    val recordStartEarlyMin: StateFlow<Int> = platformPrefs.recordStartEarlyMinFlow
    fun setRecordStartEarlyMin(v: Int) { platformPrefs.recordStartEarlyMin = v }

    val recordRunLateMin: StateFlow<Int> = platformPrefs.recordRunLateMinFlow
    fun setRecordRunLateMin(v: Int) { platformPrefs.recordRunLateMin = v }

    val recordKeepEpisodes: StateFlow<Int> = platformPrefs.recordKeepEpisodesFlow
    fun setRecordKeepEpisodes(v: Int) { platformPrefs.recordKeepEpisodes = v }

    val recordLowSpaceMb: StateFlow<Int> = platformPrefs.recordLowSpaceMbFlow
    fun setRecordLowSpaceMb(v: Int) { platformPrefs.recordLowSpaceMb = v }

    val recordNotifications: StateFlow<Boolean> = platformPrefs.recordNotificationsFlow
    fun setRecordNotifications(v: Boolean) { platformPrefs.recordNotifications = v }

    /** Gravar local e mover p/ a pasta de destino ao concluir (Lote 20, v1.30.0). */
    val recordMoveToDestination: StateFlow<Boolean> = platformPrefs.recordMoveToDestinationFlow
    fun setRecordMoveToDestination(v: Boolean) { platformPrefs.recordMoveToDestination = v }

    val recordingsFolderUri: StateFlow<String> = platformPrefs.recordingsFolderUriFlow
    val recordingsFolderName: StateFlow<String> = platformPrefs.recordingsFolderNameFlow
    fun setRecordingsFolder(uri: String, name: String) {
        platformPrefs.recordingsFolderUri = uri
        platformPrefs.recordingsFolderName = name
    }
    fun clearRecordingsFolder() {
        platformPrefs.recordingsFolderUri = ""
        platformPrefs.recordingsFolderName = ""
    }

    /** Gravações agendadas do aparelho (o serviço atualiza enquanto a tela está aberta). */
    val recordingsList: StateFlow<List<ScheduledRecording>> = recordings.recordingsFlow
    fun refreshRecordings() { recordings.refresh() }
    fun cancelRecording(id: String) { recordings.cancel(id) }
    fun removeRecording(id: String) { recordings.remove(id) }

    /** Espaço livre no aparelho em MB (StatFs do armazenamento interno). */
    fun recordingsFreeMb(): Long = runCatching {
        StatFs(appContext.filesDir.path).availableBytes / (1024L * 1024L)
    }.getOrDefault(0L)

    // ── Perfil e reprodução (valem para o perfil ativo) ──
    val profiles = platformPrefs.profiles
    val profileId = platformPrefs.profileId

    val hasParentalPin: Boolean get() = platformPrefs.hasParentalPin
    fun setParentalPin(pin: String) = platformPrefs.setParentalPin(pin)
    fun checkParentalPin(pin: String): Boolean = platformPrefs.checkParentalPin(pin)

    // Lote 11 (v1.21.0): grupos de canais trancados com o PIN dos pais.
    val liveLockedGroups: StateFlow<List<String>> = platformPrefs.liveLockedGroupsFlow
    fun unlockLiveGroup(group: String) = platformPrefs.unlockLiveGroup(group)

    private val _audio = MutableStateFlow(platformPrefs.audioPref)
    val audioPref: StateFlow<String> = _audio.asStateFlow()
    fun setAudio(v: String) { platformPrefs.audioPref = v; _audio.value = v }

    private val _maxQuality = MutableStateFlow(platformPrefs.maxQuality)
    val maxQuality: StateFlow<Int> = _maxQuality.asStateFlow()
    fun setMaxQuality(v: Int) { platformPrefs.maxQuality = v; _maxQuality.value = v }

    private val _heroTrailer = MutableStateFlow(platformPrefs.heroTrailer)
    val heroTrailer: StateFlow<Boolean> = _heroTrailer.asStateFlow()
    fun setHeroTrailer(v: Boolean) { platformPrefs.heroTrailer = v; _heroTrailer.value = v }

    // Autoplay do trailer no detalhe (Lote 21, v1.31.0): toca no topo do
    // detalhe depois de alguns segundos; só no Wi-Fi por padrão.
    private val _trailerAutoplay = MutableStateFlow(platformPrefs.trailerAutoplay)
    val trailerAutoplay: StateFlow<Boolean> = _trailerAutoplay.asStateFlow()
    fun setTrailerAutoplay(v: Boolean) { platformPrefs.trailerAutoplay = v; _trailerAutoplay.value = v }
    private val _trailerAutoplayWifiOnly = MutableStateFlow(platformPrefs.trailerAutoplayWifiOnly)
    val trailerAutoplayWifiOnly: StateFlow<Boolean> = _trailerAutoplayWifiOnly.asStateFlow()
    fun setTrailerAutoplayWifiOnly(v: Boolean) { platformPrefs.trailerAutoplayWifiOnly = v; _trailerAutoplayWifiOnly.value = v }

    // ── Página de detalhes (Fase B, por perfil) ──
    // Os fluxos vivem no PlatformPrefs (fonte única); aqui só o atalho + setter.
    val detailCinematic: StateFlow<Boolean> = platformPrefs.detailCinematicFlow
    fun setDetailCinematic(v: Boolean) { platformPrefs.detailCinematic = v }

    val detailPosterTransition: StateFlow<Boolean> = platformPrefs.detailPosterTransitionFlow
    fun setDetailPosterTransition(v: Boolean) { platformPrefs.detailPosterTransition = v }

    val detailIconActions: StateFlow<Boolean> = platformPrefs.detailIconActionsFlow
    fun setDetailIconActions(v: Boolean) { platformPrefs.detailIconActions = v }

    val detailTabs: StateFlow<Boolean> = platformPrefs.detailTabsFlow
    fun setDetailTabs(v: Boolean) { platformPrefs.detailTabs = v }

    val detailEpisodeStyle: StateFlow<String> = platformPrefs.detailEpisodeStyleFlow
    fun setDetailEpisodeStyle(v: String) { platformPrefs.detailEpisodeStyle = v }

    val detailEpisodeRatings: StateFlow<Boolean> = platformPrefs.detailEpisodeRatingsFlow
    fun setDetailEpisodeRatings(v: Boolean) { platformPrefs.detailEpisodeRatings = v }

    // Lote 11 (v1.21.0): blur anti-spoiler nos episódios não assistidos.
    val detailSpoilerBlur: StateFlow<Boolean> = platformPrefs.detailSpoilerBlurFlow
    fun setDetailSpoilerBlur(v: Boolean) { platformPrefs.detailSpoilerBlur = v }

    // ── Aparência (Lote 3, v1.11.0; do aparelho, como o tema) ──
    // Os fluxos vivem no PlatformPrefs (fonte única); aqui só o atalho + setter.
    val panelBlur: StateFlow<Boolean> = platformPrefs.panelBlurFlow
    fun setPanelBlur(v: Boolean) { platformPrefs.panelBlur = v }

    // ── Ícone do app (Lote 11, v1.21.0) — do aparelho ─────────────────────
    // A troca (liga/desliga os activity-alias) mora em AppIconVariants; aqui
    // só o estado p/ a tela e a gravação da pref depois que a troca dá certo.
    private val _appIconVariant = MutableStateFlow(platformPrefs.appIconVariant)
    /** Id da variante de ícone ativa ("default" = o ícone atual). */
    val appIconVariant: StateFlow<String> = _appIconVariant.asStateFlow()

    /** Troca o ícone. false = o PackageManager recusou (a pref não muda). */
    fun setAppIconVariant(id: String): Boolean {
        if (!com.aotv.app.data.local.AppIconVariants.applyVariant(appContext, id)) return false
        platformPrefs.appIconVariant = id
        _appIconVariant.value = id
        return true
    }

    // Lote 3 item 6 (v1.11.0): título em idioma original na página de detalhes.
    val detailOriginalTitle: StateFlow<Boolean> = platformPrefs.detailOriginalTitleFlow
    fun setDetailOriginalTitle(v: Boolean) { platformPrefs.detailOriginalTitle = v }

    // Lote 8 — Tela de detalhe (v1.18.0): nota agregada, avaliações do Trakt, frases/ficha.
    val detailShowAggregateRating: StateFlow<Boolean> = platformPrefs.detailShowAggregateRatingFlow
    fun setDetailShowAggregateRating(v: Boolean) { platformPrefs.detailShowAggregateRating = v }

    // Lote 12 — Frente 3 (Notas, v1.22.0): fonte da nota exibida de filmes
    // e séries ("auto" | "mdblist" | "tmdb"). Episódios seguem sempre TMDB.
    val ratingsSource: StateFlow<String> = platformPrefs.ratingsSourceFlow
    fun setRatingsSource(v: String) { platformPrefs.ratingsSource = v }

    // ── Atualizador dentro do app (Lote 12 — Frente 3, v1.22.0; Lote 18, v1.28.0) ──
    // Consulta a release mais recente do repo público no GitHub (sem login).
    // Sem releases publicadas, o estado é NoReleases — nunca "atualizado".
    // Lote 18: feed updates.json com SHA-256 + APK da ABI do aparelho,
    // download em 2º plano (WorkManager) e "What's new" após atualizar.
    val installedAppVersion: String get() = updateService.installedVersionName
    val appUpdateCheckEnabled: StateFlow<Boolean> = platformPrefs.appUpdateCheckEnabledFlow
    fun setAppUpdateCheckEnabled(v: Boolean) { platformPrefs.appUpdateCheckEnabled = v }

    val appUpdateBackgroundEnabled: StateFlow<Boolean> = platformPrefs.appUpdateBackgroundEnabledFlow
    fun setAppUpdateBackgroundEnabled(v: Boolean) { platformPrefs.appUpdateBackgroundEnabled = v }

    /** Tag da versão cujo APK já foi baixado e conferido em 2º plano ("" = nada pronto). */
    val updateReadyTag: StateFlow<String> = platformPrefs.appUpdateReadyTagFlow
    /** O APK pronto teve o SHA-256 conferido (false = só o tamanho). */
    val updateReadyHashVerified: StateFlow<Boolean> = platformPrefs.appUpdateReadyHashVerifiedFlow

    private val _updateState = MutableStateFlow<com.aotv.app.data.remote.UpdateCheckResult?>(null)
    /** Último resultado da checagem. null = ainda não checado nesta sessão. */
    val updateState: StateFlow<com.aotv.app.data.remote.UpdateCheckResult?> = _updateState.asStateFlow()
    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading.asStateFlow()
    /** Progresso do download manual (Lote 21, v1.31.0): null = sem download em curso. */
    private val _updateDownloadProgress = MutableStateFlow<UpdateDownloadProgress?>(null)
    val updateDownloadProgress: StateFlow<UpdateDownloadProgress?> = _updateDownloadProgress.asStateFlow()
    private val _updateDownloadError = MutableStateFlow<String?>(null)
    val updateDownloadError: StateFlow<String?> = _updateDownloadError.asStateFlow()
    private val _updateNeedsPermission = MutableStateFlow(false)
    /** O sistema ainda não permite instalar apps desconhecidos do AOTV. */
    val updateNeedsPermission: StateFlow<Boolean> = _updateNeedsPermission.asStateFlow()
    private val _updateHashVerified = MutableStateFlow<Boolean?>(null)
    /** O download manual teve o SHA-256 conferido (null = nada baixado nesta sessão). */
    val updateHashVerified: StateFlow<Boolean?> = _updateHashVerified.asStateFlow()

    /** A versão disponível já está baixada e conferida em 2º plano. */
    fun isUpdateReady(tag: String): Boolean =
        tag.isNotBlank() && platformPrefs.appUpdateReadyTag == tag &&
            updateService.cachedApkFile(appContext) != null

    fun checkForUpdates() {
        viewModelScope.launch {
            val result = updateService.checkLatest()
            _updateState.value = result
            val release = (result as? com.aotv.app.data.remote.UpdateCheckResult.Available)?.release
            if (release != null) {
                // Tag nova invalida o pronto anterior (o arquivo é outro).
                if (platformPrefs.appUpdateReadyTag.isNotBlank() && platformPrefs.appUpdateReadyTag != release.tag) {
                    platformPrefs.appUpdateReadyTag = ""
                }
                // 2º plano ligado e nada pronto p/ esta tag: baixa sozinho.
                if (platformPrefs.appUpdateBackgroundEnabled && platformPrefs.appUpdateReadyTag != release.tag) {
                    com.aotv.app.data.recordings.WorkScheduler.scheduleUpdateDownload(
                        appContext, release, platformPrefs.downloadsWifiOnly,
                    )
                }
            }
        }
    }

    /** Checa sozinho em todo cold start, quando a checagem automática está ligada (Lote 22, v1.32.0: sem gate de 24 h). */
    fun maybeAutoCheckForUpdates() {
        if (updateService.shouldAutoCheck()) checkForUpdates()
    }

    /**
     * Baixa o APK da release oficial e pede a instalação (ou a permissão).
     * Se o APK já está pronto (2º plano), instala direto, sem baixar de novo.
     */
    fun downloadAndInstallUpdate() {
        if (_updateDownloading.value) return
        val release = (_updateState.value as? com.aotv.app.data.remote.UpdateCheckResult.Available)?.release ?: return
        if (isUpdateReady(release.tag)) {
            updateService.cachedApkFile(appContext)?.let { file ->
                when (val req = updateService.installApk(appContext, file)) {
                    is com.aotv.app.data.remote.InstallRequest.Ready -> appContext.startActivity(req.intent)
                    com.aotv.app.data.remote.InstallRequest.NeedsPermission -> _updateNeedsPermission.value = true
                }
            }
            return
        }
        viewModelScope.launch {
            _updateDownloading.value = true
            _updateDownloadError.value = null
            _updateDownloadProgress.value = null
            updateService.downloadApk(appContext, release) { downloaded, total ->
                _updateDownloadProgress.value = UpdateDownloadProgress(downloaded, total)
            }
                .onSuccess { downloaded ->
                    _updateHashVerified.value = downloaded.hashVerified
                    _updateDownloadProgress.value = null
                    platformPrefs.appUpdateReadyTag = release.tag
                    platformPrefs.appUpdateReadyHashVerified = downloaded.hashVerified
                    when (val req = updateService.installApk(appContext, downloaded.file)) {
                        is com.aotv.app.data.remote.InstallRequest.Ready -> appContext.startActivity(req.intent)
                        com.aotv.app.data.remote.InstallRequest.NeedsPermission -> _updateNeedsPermission.value = true
                    }
                }
                .onFailure { e -> _updateDownloadError.value = e.message ?: e.javaClass.simpleName }
            _updateDownloading.value = false
        }
    }

    // ── "What's new" após atualizar (Lote 18, v1.28.0) ───────────────────
    // O changelog da versão nova é guardado quando a checagem a encontra
    // (feed ou corpo da release); quem atualiza por sideload usa o texto
    // embutido no app. Na 1ª abertura com a versão nova, o diálogo aparece
    // e a bolinha fica no avatar até o usuário ver (dismiss).
    private val _whatsNewText = MutableStateFlow<String?>(null)
    /** Texto do "What's new" a mostrar (null = nada a mostrar). */
    val whatsNewText: StateFlow<String?> = _whatsNewText.asStateFlow()
    private val _whatsNewDotVisible = MutableStateFlow(false)
    /** Bolinha no avatar: há novidade não vista da versão instalada. */
    val whatsNewDotVisible: StateFlow<Boolean> = _whatsNewDotVisible.asStateFlow()

    /** Há changelog p/ a versão instalada (guardado ou embutido). */
    fun hasWhatsNew(): Boolean = whatsNewTextForInstalled().isNotBlank()

    private fun whatsNewTextForInstalled(): String {
        val installed = installedAppVersion
        if (platformPrefs.whatsNewVersion == installed && platformPrefs.whatsNewChangelog.isNotBlank()) {
            return platformPrefs.whatsNewChangelog
        }
        return com.aotv.app.data.remote.embeddedChangelogFor(installed)
    }

    /** 1ª abertura com a versão nova: mostra o "What's new" (se houver texto). */
    fun checkWhatsNewOnStart() {
        val installed = installedAppVersion
        if (platformPrefs.appLastSeenVersion == installed) {
            // Mesma versão, mas a bolinha pode estar pendente de antes.
            _whatsNewDotVisible.value = platformPrefs.whatsNewSeenVersion != installed && hasWhatsNew()
            return
        }
        platformPrefs.appLastSeenVersion = installed
        val text = whatsNewTextForInstalled()
        if (text.isNotBlank() && platformPrefs.whatsNewSeenVersion != installed) {
            _whatsNewText.value = text
            _whatsNewDotVisible.value = true
        }
    }

    /** Reabre o "What's new" da versão instalada (linha nas Configurações). */
    fun showWhatsNew() {
        val text = whatsNewTextForInstalled()
        if (text.isNotBlank()) _whatsNewText.value = text
    }

    /** Fecha o diálogo e apaga a bolinha do avatar. */
    fun dismissWhatsNew() {
        _whatsNewText.value = null
        _whatsNewDotVisible.value = false
        platformPrefs.whatsNewSeenVersion = installedAppVersion
    }

    fun openUnknownSourcesSettings() {
        appContext.startActivity(updateService.unknownSourcesSettingsIntent(appContext))
    }

    fun dismissUpdatePermission() { _updateNeedsPermission.value = false }

    val detailShowTraktReviews: StateFlow<Boolean> = platformPrefs.detailShowTraktReviewsFlow
    fun setDetailShowTraktReviews(v: Boolean) { platformPrefs.detailShowTraktReviews = v }

    val detailShowQuotes: StateFlow<Boolean> = platformPrefs.detailShowQuotesFlow
    fun setDetailShowQuotes(v: Boolean) { platformPrefs.detailShowQuotes = v }

    // ── Lote 8 — Live TV (v1.18.0, por perfil; o formato Xtream é global) ──
    val liveGuideDaysAhead: StateFlow<Int> = platformPrefs.liveGuideDaysAheadFlow
    fun setLiveGuideDaysAhead(v: Int) { platformPrefs.liveGuideDaysAhead = v }

    val liveGuideHoursBack: StateFlow<Int> = platformPrefs.liveGuideHoursBackFlow
    fun setLiveGuideHoursBack(v: Int) { platformPrefs.liveGuideHoursBack = v }

    val liveRefreshListsHours: StateFlow<Int> = platformPrefs.liveRefreshListsHoursFlow
    fun setLiveRefreshListsHours(v: Int) { platformPrefs.liveRefreshListsHours = v }

    val liveRefreshEpgHours: StateFlow<Int> = platformPrefs.liveRefreshEpgHoursFlow
    fun setLiveRefreshEpgHours(v: Int) { platformPrefs.liveRefreshEpgHours = v }

    val liveShowLogos: StateFlow<Boolean> = platformPrefs.liveShowLogosFlow
    fun setLiveShowLogos(v: Boolean) { platformPrefs.liveShowLogos = v }

    val liveShowNames: StateFlow<Boolean> = platformPrefs.liveShowNamesFlow
    fun setLiveShowNames(v: Boolean) { platformPrefs.liveShowNames = v }

    val liveShowNumbers: StateFlow<Boolean> = platformPrefs.liveShowNumbersFlow
    fun setLiveShowNumbers(v: Boolean) { platformPrefs.liveShowNumbers = v }

    val liveCleanGroupNames: StateFlow<Boolean> = platformPrefs.liveCleanGroupNamesFlow
    fun setLiveCleanGroupNames(v: Boolean) { platformPrefs.liveCleanGroupNames = v }

    val liveXtreamFormat: StateFlow<String> = platformPrefs.liveXtreamFormatFlow
    fun setLiveXtreamFormat(v: String) { platformPrefs.liveXtreamFormat = v }

    val liveBackupChannel: StateFlow<Boolean> = platformPrefs.liveBackupChannelFlow
    fun setLiveBackupChannel(v: Boolean) { platformPrefs.liveBackupChannel = v }

    val liveInContinue: StateFlow<Boolean> = platformPrefs.liveInContinueFlow
    fun setLiveInContinue(v: Boolean) { platformPrefs.liveInContinue = v }

    val liveRememberAudio: StateFlow<Boolean> = platformPrefs.liveRememberAudioFlow
    fun setLiveRememberAudio(v: Boolean) { platformPrefs.liveRememberAudio = v }

    val liveCatchupHls: StateFlow<Boolean> = platformPrefs.liveCatchupHlsFlow
    fun setLiveCatchupHls(v: Boolean) { platformPrefs.liveCatchupHls = v }

    // ── Modo aleatório (Lote 2, v1.10.0) ───────────────────────────────────
    val randomIncludeContinue: StateFlow<Boolean> = platformPrefs.randomIncludeContinueFlow
    fun setRandomIncludeContinue(v: Boolean) { platformPrefs.randomIncludeContinue = v }

    val randomIncludeWatchlist: StateFlow<Boolean> = platformPrefs.randomIncludeWatchlistFlow
    fun setRandomIncludeWatchlist(v: Boolean) { platformPrefs.randomIncludeWatchlist = v }

    val randomUnwatchedOnly: StateFlow<Boolean> = platformPrefs.randomUnwatchedOnlyFlow
    fun setRandomUnwatchedOnly(v: Boolean) { platformPrefs.randomUnwatchedOnly = v }

    // ── Exibição da Início (Fase B, frente compacta; por perfil) ──
    /** Pôsteres, densidade, selos, herói, fundo e sublinhado da Início. */
    val homeDisplay: StateFlow<com.aotv.app.data.local.HomeDisplayPrefs> = platformPrefs.homeDisplayFlow
    fun setPosterSize(v: com.aotv.app.data.local.PosterSize) =
        platformPrefs.updateHomeDisplay { it.copy(posterSize = v) }
    fun setPosterRadius(v: Int) = platformPrefs.updateHomeDisplay { it.copy(posterRadiusDp = v) }
    fun setHomeDensity(v: com.aotv.app.data.local.HomeDensity) =
        platformPrefs.updateHomeDisplay { it.copy(density = v) }
    fun setShowPosterBadges(v: Boolean) = platformPrefs.updateHomeDisplay { it.copy(showPosterBadges = v) }
    /** Selos de qualidade/fonte (Lote 11, v1.21.0): LIGADOS por padrão (decisão do Davi). */
    fun setShowQualityBadges(v: Boolean) = platformPrefs.updateHomeDisplay { it.copy(showQualityBadges = v) }
    fun setHeroStyle(v: com.aotv.app.data.local.HeroStyle) =
        platformPrefs.updateHomeDisplay { it.copy(heroStyle = v) }
    fun setDynamicBackground(v: Boolean) = platformPrefs.updateHomeDisplay { it.copy(dynamicBackground = v) }
    fun setSectionUnderline(v: Boolean) = platformPrefs.updateHomeDisplay { it.copy(sectionUnderline = v) }

    // Legenda do perfil. Ligada ao abrir e o idioma em que ela liga sozinha.
    private val _subOn = MutableStateFlow(platformPrefs.subtitleOn)
    val subOn: StateFlow<Boolean> = _subOn.asStateFlow()
    fun setSubOn(v: Boolean) { platformPrefs.subtitleOn = v; _subOn.value = v }

    private val _subLang = MutableStateFlow(platformPrefs.subLang)
    val subLang: StateFlow<String> = _subLang.asStateFlow()
    fun setSubLang(v: String) { platformPrefs.subLang = v; _subLang.value = v }

    private val _platformMode = MutableStateFlow(platformPrefs.platformMode)
    val platformMode: StateFlow<PlatformMode> = _platformMode.asStateFlow()

    private val _customTmdbApiKey = MutableStateFlow(platformPrefs.customTmdbApiKey)
    val customTmdbApiKey: StateFlow<String> = _customTmdbApiKey.asStateFlow()

    /** Alguma chave TMDB em uso (pessoal ou padrão do app). */
    private val _hasTmdbKey = MutableStateFlow(platformPrefs.tmdbApiKey.isNotBlank())
    val hasTmdbKey: StateFlow<Boolean> = _hasTmdbKey.asStateFlow()

    /** Addons instalados, reativo: instalar pela Loja já atualiza a contagem aqui sozinho. */
    val addons: StateFlow<List<com.aotv.app.data.local.AddonEntry>> = platformPrefs.addons

    fun setPlatformMode(mode: PlatformMode) {
        platformPrefs.platformMode = mode
        _platformMode.value = mode
    }

    fun setTmdbApiKey(key: String) {
        platformPrefs.customTmdbApiKey = key
        _customTmdbApiKey.value = platformPrefs.customTmdbApiKey
        _hasTmdbKey.value = platformPrefs.tmdbApiKey.isNotBlank()
    }

    // ── Lote 22 (v1.32.0) — Controle remoto, Send to TV, Watch Next,
    // gamepad e player externo ────────────────────────────────────────────
    // Os fluxos vivem no PlatformPrefs (fonte única); aqui só o atalho + setter.
    val watchNextEnabled: StateFlow<Boolean> = platformPrefs.watchNextEnabledFlow
    fun setWatchNextEnabled(v: Boolean) {
        platformPrefs.watchNextEnabled = v
        if (!v) viewModelScope.launch { com.aotv.app.data.remote.WatchNextSync.clear(appContext) }
    }

    val gamepadPreset: StateFlow<String> = platformPrefs.gamepadPresetFlow
    fun setGamepadPreset(v: String) { platformPrefs.gamepadPreset = v }
    val gamepadMappingJson: StateFlow<String> = platformPrefs.gamepadMappingJsonFlow
    fun setGamepadMappingJson(v: String) { platformPrefs.gamepadMappingJson = v }

    val externalPlayerDefaultMovie: StateFlow<String> = platformPrefs.externalPlayerDefaultMovieFlow
    fun setExternalPlayerDefaultMovie(v: String) { platformPrefs.externalPlayerDefaultMovie = v }
    val externalPlayerDefaultSeries: StateFlow<String> = platformPrefs.externalPlayerDefaultSeriesFlow
    fun setExternalPlayerDefaultSeries(v: String) { platformPrefs.externalPlayerDefaultSeries = v }
    val externalPlayerByTitle: StateFlow<String> = platformPrefs.externalPlayerByTitleFlow
    fun setExternalPlayerByTitle(v: String) { platformPrefs.externalPlayerByTitle = v }
    val externalPlayerAsk: StateFlow<Boolean> = platformPrefs.externalPlayerAskFlow
    fun setExternalPlayerAsk(v: Boolean) { platformPrefs.externalPlayerAsk = v }

    /** Instala um addon Kodi por URL (ZIP direto instala na hora; índice
     * volta NeedsChoice p/ a UI escolher — aqui vira mensagem). Roda em IO. */
    suspend fun installKodiAddonByUrl(url: String): Result<String> {
        val resolved = kodiAddonStore.resolveInstallUrl(url)
        return resolved.map { result ->
            when (result) {
                is com.aotv.app.data.kodi.KodiAddonStore.UrlResolution.Installed ->
                    "Addon ${result.addon.name} instalado."
                is com.aotv.app.data.kodi.KodiAddonStore.UrlResolution.NeedsChoice ->
                    "O link é um repositório com ${result.index.addons.size} addons — escolha um na tela de Addons Kodi."
            }
        }
    }

    /** Salva o progresso de um handoff recebido (vira Continuar assistindo). */
    fun saveHandoffProgress(handoff: com.aotv.app.data.remote.PlaybackHandoff) {
        viewModelScope.launch {
            runCatching {
                library.saveProgress(
                    tmdbId = handoff.tmdbId, type = handoff.type, title = handoff.title,
                    posterUrl = handoff.posterUrl, positionMs = handoff.positionMs,
                    durationMs = handoff.durationMs, lastEpisode = handoff.lastEpisode,
                    lastSourceUrl = handoff.sourceUrl,
                )
            }
        }
    }

    /** Sincroniza o Watch Next do launcher agora (sem throttle). Devolve
     * quantos itens foram escritos no canal. Itens de ao vivo ficam fora. */
    suspend fun syncWatchNextNow(): Int {
        val pid = platformPrefs.profileId.value
        val items = history.getAll(pid).first()
            .filter { it.type != com.aotv.app.ui.live.LivePlayerViewModel.LIVE_TYPE }
            .map { com.aotv.app.data.remote.WatchNextSync.toItem(it) }
        com.aotv.app.data.remote.WatchNextSync.sync(appContext, platformPrefs, items)
        return items.size
    }
}
