package com.aotv.app.ui.movies

import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aotv.app.data.kodi.kodiProxyHostnameVerifier
import com.aotv.app.data.local.ExternalPlayerLaunch
import com.aotv.app.data.local.ExternalPlayerStore
import com.aotv.app.data.local.SubLangs
import com.aotv.app.data.party.PartyPlayer
import com.aotv.app.data.torrent.TorrentMagnet
import com.aotv.app.data.torrent.Torrents
import com.aotv.app.ui.common.ImmersiveFullscreen
import com.aotv.app.ui.common.KeepScreenOn
import com.aotv.app.ui.common.PostPlayRatingDialog
import com.aotv.app.ui.settings.WatchPartyViewModel
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToLong

import com.aotv.app.data.live.LiveSource
import com.aotv.app.data.live.LiveTvRepository
import com.aotv.app.data.live.VodItem
import com.aotv.app.data.remote.SourceFacts
import com.aotv.app.data.remote.bestIndex
import com.aotv.app.data.remote.durationMismatch
import com.aotv.app.data.remote.heightLabel
import com.aotv.app.data.remote.langOf
import com.aotv.app.data.remote.nextIndex
import com.aotv.app.data.remote.sameMirror

/** Uma fonte do filme/episódio p/ o seletor dentro do player (trocar fonte mantendo o tempo). */
data class MoviePlayerSource(
    val label: String,
    val url: String,
    /** Addon de origem (nome do manifest), mostrado no seletor e no player. */
    val addon: String = "",
    val stream: com.aotv.app.data.remote.StremioStream? = null,
)

/** Um resultado do On Demand Xtream na folha de fontes (Lote 12 — Frente 2). */
data class OnDemandHit(val item: VodItem, val isSeries: Boolean)

/** Nome amigável de uma faixa de áudio (label do stream → idioma → "Faixa N"). */
@OptIn(UnstableApi::class)
private fun audioTrackLabel(group: androidx.media3.common.Tracks.Group, index: Int): String {
    val f = runCatching { group.mediaTrackGroup.getFormat(0) }.getOrNull()
    val lang = f?.language?.lowercase()
    val byLang = when {
        lang == null -> null
        lang.startsWith("pt") -> "Português"
        lang.startsWith("en") -> "Inglês"
        lang.startsWith("es") -> "Espanhol"
        else -> lang.uppercase()
    }
    return f?.label ?: byLang ?: "Faixa ${index + 1}"
}

/** Fonte de mídia com DNS da Cloudflare, Referer e arquivos locais (downloads). */
@OptIn(UnstableApi::class)
/** Cliente do ExoPlayer com o kodiProxyHostnameVerifier (ver KodiLocalProxy; só o player usa). */
private val kodiMediaClient by lazy {
    com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
        .hostnameVerifier(kodiProxyHostnameVerifier)
        .build()
}

private fun mediaSourceFactory(
    context: android.content.Context,
    /** Headers extras p/ esta fonte (ex.: os do sufixo Kodi `|...`). */
    extraHeaders: Map<String, String> = emptyMap(),
): DefaultMediaSourceFactory {
    // OkHttpDataSource usa o DNS-over-HTTPS Cloudflare (AppDns.mediaClient). O torrent local tem cliente próprio.
    // v1.2.7: o player usa o kodiMediaClient (mesmo DNS/headers, só o hostname
    // check relaxado p/ *.null-null.shop — ver kodiProxyHostnameVerifier).
    val calls = object : okhttp3.Call.Factory {
        override fun newCall(request: okhttp3.Request) =
            (if (request.url.host == "127.0.0.1") localMediaClient else kodiMediaClient).newCall(request)
    }
    // Medição de streaming em dados móveis (Lote 18 item 7, v1.28.0): o
    // TransferListener conta os bytes de rede do ExoPlayer (o servidor
    // local do torrent, 127.0.0.1, é ignorado aqui — o torrent é contado
    // pelo TorrentStreamer). Arquivo local não passa por aqui.
    val http = com.aotv.app.data.local.StreamingDataMeter(context.applicationContext)
        .meteredFactory(
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(calls)
                .setDefaultRequestProperties(STREAM_HEADERS + extraHeaders),
        )
    // DefaultDataSource delega file://, content:// e asset:// para as fontes locais.
    return DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http))
}

/**
 * URL estilo Kodi `base|Header=Valor&Outro=Valor` → (base limpa, headers).
 *
 * v1.2.6 — o Vulgata resolve a TV com `|Accept-Language=...` no fim
 * (convenção Kodi: headers a enviar no fetch). O ExoPlayer não
 * entende o `|`: a URI chegaria malformada e o fetch falharia. Sem
 * `|`, devolve a URL intacta e mapa vazio. Chaves e valores do
 * sufixo vêm URL-encoded (ex.: `pt-BR%2Cpt%3Bq%3D0.9`).
 */
internal fun splitKodiStreamHeaders(url: String): Pair<String, Map<String, String>> {
    val bar = url.indexOf('|')
    if (bar < 0) return url to emptyMap()
    val base = url.substring(0, bar)
    val headers = LinkedHashMap<String, String>()
    for (pair in url.substring(bar + 1).split('&')) {
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val k = runCatching {
            java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8")
        }.getOrDefault(pair.substring(0, eq))
        val v = runCatching {
            java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
        }.getOrDefault(pair.substring(eq + 1))
        if (k.isNotBlank()) headers[k] = v
    }
    return base to headers
}

/** Nome curto da fonte para avisos ("FrostStream · Servidor Neon · Dublado"). */
private fun extName(src: MoviePlayerSource, title: String) =
    parseSourceLabel(src.label, src.addon, sourceQualityRank(src.label), title)
        .let { listOf(it.addon, it.server, it.language) }.filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Erro curto do ExoPlayer p/ o diagnóstico (v1.2.7): código + mensagem +
 * causa raiz, p/ distinguir TLS de 404 de URL inválida de timeout na
 * tela de erro do player. Limitado a 400 caracteres p/ caber na tela.
 */
private fun shortPlayerError(error: androidx.media3.common.PlaybackException): String {
    val root = generateSequence(error.cause) { it.cause }.lastOrNull()
    val rootTxt = root?.let { "${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
    return buildString {
        append(error.errorCodeName)
        error.message?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
        if (rootTxt.isNotBlank() && rootTxt != error.message) append("\n$rootTxt")
    }.take(400)
}

// Torrent entrega o começo só depois de achar pares e baixar as primeiras peças, então espera bem mais.
private val localMediaClient by lazy {
    com.aotv.app.data.remote.AppDns.mediaClient.newBuilder().readTimeout(150, java.util.concurrent.TimeUnit.SECONDS).build()
}

/** Magnet vira o endereço local do motor P2P na hora de tocar, a fonte guardada continua sendo o magnet. */
private fun mediaItemFor(context: android.content.Context, url: String): MediaItem {
    // v1.2.6 — tira o sufixo Kodi `|Header=...` (ver
    // splitKodiStreamHeaders): a URI p/ o ExoPlayer tem que ser
    // limpa; os headers vão p/ a factory (ver o call site).
    val (clean, _) = splitKodiStreamHeaders(url)
    val play = if (TorrentMagnet.isMagnet(clean)) Torrents.get(context).playUrl(clean) else clean
    val b = MediaItem.Builder().setUri(play)
    if (play.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    return b.build()
}

@OptIn(UnstableApi::class)
@Composable
fun MoviePlayerScreen(
    embedUrl: String,
    title: String,
    onBack: () -> Unit,
    tmdbId: Int = 0,
    type: String = "MOVIE",
    posterUrl: String = "",
    lastEpisode: String = "",
    startPositionMs: Long = 0,
    backdropUrl: String = "",
    season: Int = 0,
    episode: Int = 0,
    episodeTotal: Int = 0,
    sources: List<MoviePlayerSource> = emptyList(),
    /** Diagnóstico do play Kodi (resolveu= + http:) — v1.2.7: aparece na
     *  tela de erro quando o ExoPlayer não carrega o stream resolvido. */
    kodiDiag: String = "",
    /** Fonte travada ao abrir (guarda de duração confirmada, v1.21.0): o
     *  player não auto-troca por duração nem por lentidão nesta fonte. */
    startLocked: Boolean = false,
    television: Boolean = com.aotv.app.ui.common.LocalIsTv.current,
    progressVm: PlayerProgressViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    episodeVm: EpisodePlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    /** Prefs do player (Fase C): botões, tap-seek, boost, mini player, "A seguir". */
    playerPrefsVm: PlayerPrefsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    /** Watch Party (Lote 19, v1.29.0): a sala comanda este player quando há sessão ativa. */
    partyVm: WatchPartyViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    /** playId da rota (p/ o mini player reabrir esta mesma sessão ao expandir). */
    playId: Int = -1,
    /** Sessão minimizada a reutilizar (expandir do mini player, em vez de criar outra). */
    existingSession: PlayerSession? = null,
) {
    ImmersiveFullscreen()
    KeepScreenOn()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Motor do player (Lote 4, v1.12.0): lidos ANTES do remember da sessão
    // porque o decoder entra no construtor (só vale na criação do player)
    // e o player padrão decide o auto-abrir abaixo.
    val playerEngine by playerPrefsVm.engine.collectAsState()
    val playerDecoder by playerPrefsVm.decoder.collectAsState()
    // Buffer do player (Lote 5 item 1, v1.14.0): lido ANTES do remember da
    // sessão, como o decoder — o LoadControl entra no construtor do ExoPlayer.
    val playerBuffer by playerPrefsVm.buffer.collectAsState()
    // Lote 9 — Player parte 3 (v1.19.0): AutoSync, pular abertura automático,
    // cartão "A seguir" e equalizador (prefs por perfil, ver PlayerPrefsViewModel).
    val playerAutoSync by playerPrefsVm.autoSync.collectAsState()
    val playerAutoSkip by playerPrefsVm.autoSkip.collectAsState()
    val playerUpNext by playerPrefsVm.upNext.collectAsState()
    val playerEqEnabled by playerPrefsVm.equalizer.collectAsState()
    val playerEqPreset by playerPrefsVm.eqPreset.collectAsState()
    val playerEqBands by playerPrefsVm.eqBands.collectAsState()
    // Lote 16 item 4: sincronizar a taxa de quadros (pref, ligada por padrão).
    val playerFrameRate by playerPrefsVm.frameRate.collectAsState()
    // Estado e regra do player moram na sessão. A função de tela ficou pequena e o Android volta
    // a compilar o código (antes passava do limite de instruções e rodava interpretado).
    val forceTv = television
    val s = remember {
        // Expandir do mini player: reusa a sessão (mesmo ExoPlayer, mesma posição),
        // em vez de criar outro player do zero.
        existingSession ?: PlayerSession(
            context, scope, progressVm, episodeVm,
            embedUrl, title, tmdbId, type, posterUrl, lastEpisode, startPositionMs,
            backdropUrl, season, episode, episodeTotal, sources,
            forceTv = forceTv, kodiDiag = kodiDiag,
            decoderMode = DecoderMode.fromId(playerDecoder),
            bufferProfile = BufferProfile.fromId(playerBuffer),
            startLocked = startLocked,
        )
    }
    // A sessão voltou para a tela cheia: ela passa a ser da tela de novo e o
    // mini player some. No SideEffect (não no remember): limpar o estado do
    // mini aqui dentro da composição escreveria um estado que a navegação
    // está lendo no mesmo passe.
    if (existingSession != null) {
        SideEffect {
            existingSession.minimizedByUser = false
            MiniPlayerState.clearStored()
        }
    }
    // Watch Party (Lote 19, v1.29.0): com sessão ativa, a sala comanda
    // este player (posição/play/pause/velocidade) e mostra o overlay.
    // O adaptador delega p/ o player atual da sessão (ExoPlayer ou cast).
    // ── Player externo com progresso (Lote 22, item 6) ──────────────────
    // O lançador fica na tela (não na sessão): ao voltar do player externo,
    // a posição vem do resultado (MX Player devolve `position`) ou do tempo
    // decorrido, é salva no Continuar assistindo e a tela volta p/ o
    // detalhe. Enquanto o externo está aberto, a tela fica pausada atrás
    // dele — NÃO volta antes, senão o lançador morre e o retorno se perde.
    var externalLaunch by remember { mutableStateOf<ExternalPlayerLaunch?>(null) }
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val launch = externalLaunch ?: return@rememberLauncherForActivityResult
        externalLaunch = null
        s.externalResultPending = false
        val position = ExternalPlayerStore.positionFromResult(result.data, launch)
        progressVm.save(
            tmdbId = launch.tmdbId, type = launch.type, title = launch.title,
            posterUrl = launch.posterUrl, positionMs = position, durationMs = launch.durationMs,
            lastEpisode = launch.lastEpisode, sourceUrl = launch.sourceUrl,
        )
        s.toastMsg = "Progresso salvo no Continuar assistindo."
        onBack()
    }
    SideEffect {
        s.externalLauncher = { intent, launch ->
            externalLaunch = launch
            externalPlayerLauncher.launch(intent)
        }
    }
    // Controle remoto (Lote 22, item 1): com o player aberto, o celular
    // manda play/pause e pulo de 10 s e fecha o player. O servidor chama
    // de thread própria — tudo é postado p/ a main (o ExoPlayer é de UI).
    // Ao sair do player as ações somem do controle (onDispose).
    DisposableEffect(s) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val registry = com.aotv.app.data.remote.RemoteActionRegistry
        registry.register(
            com.aotv.app.data.remote.RemoteAction(
                name = "play_pause", description = "Play/Pause",
                run = {
                    main.post {
                        runCatching {
                            val p = s.currentPlayer()
                            p.playWhenReady = !p.playWhenReady
                        }
                    }
                    "Play/Pause alternado."
                },
            ),
        )
        registry.register(
            com.aotv.app.data.remote.RemoteAction(
                name = "avancar_10s", description = "Avançar 10 s",
                run = {
                    main.post {
                        runCatching {
                            val p = s.currentPlayer()
                            val target = p.currentPosition + 10_000
                            // Duração desconhecida (ao vivo/sem metadado) não
                            // trava o avanço: só limita quando a duração é > 0.
                            p.seekTo(if (p.duration > 0) target.coerceAtMost(p.duration) else target)
                        }
                    }
                    "Avançou 10 s."
                },
            ),
        )
        registry.register(
            com.aotv.app.data.remote.RemoteAction(
                name = "voltar_10s", description = "Voltar 10 s",
                run = {
                    main.post {
                        runCatching {
                            val p = s.currentPlayer()
                            p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                        }
                    }
                    "Voltou 10 s."
                },
            ),
        )
        registry.register(
            com.aotv.app.data.remote.RemoteAction(
                name = "parar", description = "Fechar o player",
                run = {
                    main.post { onBack() }
                    "Fechando o player."
                },
            ),
        )
        onDispose {
            registry.unregister("play_pause")
            registry.unregister("avancar_10s")
            registry.unregister("voltar_10s")
            registry.unregister("parar")
        }
    }
    val partyUiState by partyVm.ui.collectAsState()
    val partyPlayer = remember(s) {
        object : PartyPlayer {
            override fun positionMs(): Long =
                runCatching { s.currentPlayer().currentPosition }.getOrDefault(0L)

            override fun isPlaying(): Boolean =
                runCatching { s.currentPlayer().playWhenReady }.getOrDefault(false)

            override fun durationMs(): Long =
                runCatching { s.currentPlayer().duration }.getOrDefault(0L)
                    .takeIf { it > 0L } ?: 0L

            override fun seekTo(positionMs: Long) {
                runCatching { s.currentPlayer().seekTo(positionMs) }
            }

            override fun setPlaying(playing: Boolean) {
                runCatching { s.currentPlayer().playWhenReady = playing }
            }

            override fun setSpeed(rate: Float) {
                runCatching { s.currentPlayer().setPlaybackSpeed(rate) }
            }

            override fun normalSpeed(): Float = s.speeds.getOrElse(s.speedIdx) { 1f }
        }
    }
    LaunchedEffect(partyUiState.session) {
        val partySession = partyUiState.session
        if (partySession != null) {
            partyVm.runtime.bindPlayer(partyPlayer, title, tmdbId)
        } else {
            partyVm.runtime.unbindPlayer()
        }
    }
    DisposableEffect(Unit) {
        onDispose { partyVm.runtime.unbindPlayer() }
    }
    // Prefs do player (Fase C): a tela lê os fluxos e entrega à sessão, que aplica
    // (mesmo padrão do `s.onBack = onBack` abaixo). Mudar nas Configurações aplica
    // na hora, mesmo com o player aberto.
    val playerButtons by playerPrefsVm.buttons.collectAsState()
    val playerTopActionsPref by playerPrefsVm.topActions.collectAsState()
    val playerTapSeek by playerPrefsVm.tapSeek.collectAsState()
    val playerSwipeSeek by playerPrefsVm.swipeSeek.collectAsState()
    val playerVolBoost by playerPrefsVm.volBoost.collectAsState()
    val playerVolBoostLevel by playerPrefsVm.volBoostLevel.collectAsState()
    val playerMini by playerPrefsVm.mini.collectAsState()
    val playerAutoNext by playerPrefsVm.autoNext.collectAsState()
    val playerAutoNextDelay by playerPrefsVm.autoNextDelay.collectAsState()
    val playerAutoSwitch by playerPrefsVm.autoSwitch.collectAsState()
    val playerSourceTimeout by playerPrefsVm.sourceTimeout.collectAsState()
    // Lote 19 item 7 (v1.29.0): subir a qualidade sozinho (e) e debrid só com arquivo pronto (f).
    val playerAutoUpgrade by playerPrefsVm.autoUpgrade.collectAsState()
    val playerDebridReadyOnly by playerPrefsVm.debridReadyOnly.collectAsState()
    val playerAnimeSkipClientId by playerPrefsVm.animeSkipClientId.collectAsState()
    // Idioma preferido de áudio (Lote 4 item 7, v1.12.0): pref por perfil, código SubLangs.
    val playerAudioLang by playerPrefsVm.audioLang.collectAsState()
    /** Blur/vidro fosco nos painéis do player (Lote 4): mesma pref do detalhe. */
    val panelBlurOn by playerPrefsVm.panelBlur.collectAsState()
    // Correção de cor do vídeo (v1.9.0, Lote 1 item 1): fluxos do
    // PlayerPrefsViewModel (contrato de prefs no relatório da v1.9.0).
    val playerColorBrightness by playerPrefsVm.colorBrightness.collectAsState()
    val playerColorContrast by playerPrefsVm.colorContrast.collectAsState()
    val playerColorSaturation by playerPrefsVm.colorSaturation.collectAsState()
    val playerColorHue by playerPrefsVm.colorHue.collectAsState()
    val playerColorPreset by playerPrefsVm.colorPreset.collectAsState()
    val playerColorDaltonism by playerPrefsVm.colorDaltonism.collectAsState()
    // Modo privado (Lote 2, item 7 — v1.10.0): o chip "Privado" do player lê
    // daqui e a sessão avisa de volta por onPrivateModeToggle.
    val playerPrivateMode by playerPrefsVm.privateMode.collectAsState()
    val playerSpeed by playerPrefsVm.speed.collectAsState()
    // Lote 12 — Frente 2: modo de tela padrão e vista da folha (prefs por perfil).
    val playerScreenMode by playerPrefsVm.screenMode.collectAsState()
    val playerSourcesRanked by playerPrefsVm.sourcesRanked.collectAsState()
    // Notas dos episódios (Lote 2 item 5): a mesma pref do detalhe — o painel
    // de episódios do player esconde o "★ nota" junto com os cards.
    val showEpisodeRatings by playerPrefsVm.episodeRatings.collectAsState()
    s.showEpisodeRatings = showEpisodeRatings
    // Lote 12 — Frente 2: vista da folha (pref) e modo de tela (o chip
    // salva de volta por onScreenModeChange; o LaunchedEffect aplica o modo
    // do perfil ao abrir, como a velocidade faz com playerSpeed).
    s.sourcesRanked = playerSourcesRanked
    s.onSourcesRankedChange = { playerPrefsVm.setSourcesRanked(it) }
    s.onScreenModeChange = { playerPrefsVm.setScreenMode(it) }
    LaunchedEffect(playerScreenMode) { s.screenModeIdx = playerScreenMode }
    // On Demand da folha: repo da TV ao vivo (só lê as fontes Xtream já
    // configuradas; nada de rede até abrir a folha — ver loadOnDemand).
    val liveRepo = remember { LiveTvRepository(context, com.aotv.app.data.local.PlatformPrefs(context)) }
    s.onDemandRepo = liveRepo
    s.panelBlurOn = panelBlurOn
    s.playerButtonOrder = playerButtons
    s.playerTopActions = playerTopActionsPref
    s.routePlayId = playId
    s.tapSeekEnabled = playerTapSeek
    s.swipeSeekEnabled = playerSwipeSeek
    s.volBoostEnabled = playerVolBoost
    s.volBoostLevel = playerVolBoostLevel
    s.miniEnabled = playerMini
    s.autoNextEnabled = playerAutoNext
    s.autoNextDelaySec = playerAutoNextDelay
    // Lote 9 — Player parte 3 (v1.19.0).
    s.autoSyncEnabled = playerAutoSync
    s.autoSkipEnabled = playerAutoSkip
    s.upNextEnabled = playerUpNext
    s.eqEnabled = playerEqEnabled
    s.eqPreset = playerEqPreset
    s.eqBandsCsv = playerEqBands
    // Equalizador: o painel salva de volta por onEqSave/onEqToggle (preset,
    // bandas e liga/desliga), como o filtro de cor salva por onColorFilterSave.
    s.onEqSave = { preset, bands ->
        playerPrefsVm.setEqPreset(preset)
        playerPrefsVm.setEqBands(bands)
    }
    s.onEqToggle = { playerPrefsVm.setEqualizer(it) }
    // (Re)aplica o equalizador quando a pref muda (o LaunchedEffect do
    // Volume Boost faz o mesmo p/ o LoudnessEnhancer; a 1ª aplicação é no
    // onAudioSessionIdChanged, quando a sessão de áudio fica pronta).
    LaunchedEffect(playerEqEnabled, playerEqPreset, playerEqBands) { s.applyEqualizer() }
    // Lote 16 item 4: taxa de quadros (pref; desligar volta a taxa padrão da tela).
    LaunchedEffect(playerFrameRate) { s.frameRateEnabled = playerFrameRate; s.applyFrameRate() }
    // v1.8.1: troca automática de fonte (toggle + tempo) e client ID do Anime-Skip.
    s.autoSwitchEnabled = playerAutoSwitch
    s.sourceTimeoutSec = playerSourceTimeout
    // Lote 19 item 7 (v1.29.0): subir a qualidade sozinho (e) e debrid só com arquivo pronto (f).
    s.autoUpgradeEnabled = playerAutoUpgrade
    s.debridReadyOnly = playerDebridReadyOnly
    s.animeSkipClientId = playerAnimeSkipClientId
    // Idioma preferido de áudio (Lote 4 item 7, v1.12.0): a tela lê o fluxo e
    // entrega à sessão, como as outras prefs do player.
    s.audioLangPref = playerAudioLang
    // Correção de cor do vídeo (v1.9.0, Lote 1 item 1): a tela lê os fluxos e
    // entrega à sessão, como as outras prefs do player. O painel salva de
    // volta por [PlayerSession.onColorFilterSave].
    s.colorBrightness = playerColorBrightness
    s.colorContrast = playerColorContrast
    s.colorSaturation = playerColorSaturation
    s.colorHue = playerColorHue
    s.colorPreset = playerColorPreset
    s.colorDaltonism = DaltonismMode.fromId(playerColorDaltonism)
    s.onColorFilterSave = { ns ->
        playerPrefsVm.setColorBrightness(ns.brightness)
        playerPrefsVm.setColorContrast(ns.contrast)
        playerPrefsVm.setColorSaturation(ns.saturation)
        playerPrefsVm.setColorHue(ns.hueDegrees)
        playerPrefsVm.setColorPreset(ns.preset)
        playerPrefsVm.setColorDaltonism(ns.daltonism.id)
    }
    // Modo noturno + Clear Dialogue (Lote 16 item 1): estado por título,
    // carregado ao abrir e ao trocar de episódio; a sessão aplica no
    // ExoPlayer quando a sessão de áudio fica pronta ou o estado muda.
    val nightPrefs = remember { com.aotv.app.data.local.PlatformPrefs(context) }
    LaunchedEffect(tmdbId, type, s.curSeason, s.curEpisode) {
        val key = if (type == "SERIES") nightModeTitleKeyEpisode(tmdbId.toLong(), s.curSeason, s.curEpisode)
        else nightModeTitleKeyMovie(tmdbId.toLong())
        s.nightModeTitleKey = key
        s.nightModeState = loadNightModeState(nightPrefs, key)
        s.applyNightMode()
    }
    LaunchedEffect(s.nightModeState) { s.applyNightMode() }
    // Lote 24 — Frente B: Ambilight + modo sensorial (prefs por perfil).
    // Carregados 1x ao abrir o player; os toggles dos painéis salvam de
    // volta pelos setters da sessão. O contexto da app vai p/ o sampler
    // (o fallback do pôster precisa do Coil sem guardar Activity).
    LaunchedEffect(Unit) {
        AmbilightSampler.appContextForAmbilight = context.applicationContext
        s.ambilightState = loadAmbilightState(nightPrefs)
        s.sensoryOn = loadSensoryFriendlyState(nightPrefs).enabled
        s.applyNightMode()
    }
    s.onAmbilightSave = { saveAmbilightState(nightPrefs, it) }
    s.onSensorySave = { saveSensoryFriendlyState(nightPrefs, SensoryFriendlyState(it)) }
    DisposableEffect(s) { onDispose { s.nightController.release(); s.fpsMeter.stop() } }
    // Modo privado (Lote 2, item 7 — v1.10.0): a tela lê o fluxo e entrega à
    // sessão; o chip salva de volta por onPrivateModeToggle. Mudar no Perfil
    // aplica na hora, mesmo com o player aberto.
    s.privateMode = playerPrivateMode
    s.onPrivateModeToggle = { playerPrefsVm.setPrivateMode(it) }
    // v1.9.0: velocidade salva do perfil — aplica ao abrir e quando muda nas
    // Configurações (o ExoPlayer novo começa em 1x; a troca de episódio mantém
    // a velocidade sozinha porque o player é o mesmo).
    LaunchedEffect(playerSpeed) {
        s.speedIdx = PLAYBACK_SPEEDS.indexOf(playerSpeed).takeIf { it >= 0 } ?: PLAYBACK_SPEEDS.indexOf(1f)
        runCatching { s.currentPlayer().setPlaybackSpeed(playerSpeed) }
    }
    // Idioma preferido de áudio (Lote 4 item 7, v1.12.0): o ExoPlayer nasce
    // com o padrão ("pt") e a pref do perfil é aplicada aqui — e reaplicada
    // quando muda nas Configurações com o player aberto. Escolha à mão no
    // vídeo atual não é desfeita (ver audioPicked em applyPreferredAudioLanguage).
    LaunchedEffect(playerAudioLang) {
        s.audioLangPref = playerAudioLang
        s.applyPreferredAudioLanguage()
        s.pickAutoAudio()
    }
    // Motor do player (Lote 4 item 1, v1.12.0): pref "Player externo" —
    // quando a fonte já resolveu e começou no interno, abre 1x no player
    // externo e volta p/ o detalhe. Só no celular: na TV o chip "Player
    // externo" faz isso à mão. Se abrir falhar (sem player instalado),
    // fica no interno e o toast explica — não volta sozinho.
    LaunchedEffect(playerEngine, s.readyUrl) {
        if (playerEngine != "external" || s.isTv || s.casting || s.externalOpened) return@LaunchedEffect
        if (s.readyUrl == null) return@LaunchedEffect
        s.externalOpened = true
        runCatching { s.exoPlayer.playWhenReady = false }
        if (s.openExternalPlayer() && !s.externalResultPending) onBack()
    }
    SideEffect { s.isTv = forceTv }
    s.onBack = onBack
    // Fase 2 (Kodi): o xbmc.Player reflete o ExoPlayer de verdade — o addon
    // acompanha o playback (isPlaying/getTime/seek/pause) via KodiPlayerBridge.
    DisposableEffect(s) {
        com.aotv.app.data.kodi.KodiPlayerBridge.attach(s.exoPlayer, embedUrl, title)
        onDispose { com.aotv.app.data.kodi.KodiPlayerBridge.detach() }
    }
    LaunchedEffect(s.activeUrl, s.displayTitle) {
        com.aotv.app.data.kodi.KodiPlayerBridge.update(s.activeUrl, s.displayTitle)
    }
    PlayerSourceEffects(s)
    PlayerPlaybackEffects(s)
    // Avaliação pós-filme (v1.17.0): o diálogo abre quando o título passa
    // de 90% e a avaliação está ligada (o pedido vem de progressVm).
    val ratingReq by progressVm.ratingRequest.collectAsState()
    ratingReq?.let { req ->
        PostPlayRatingDialog(
            movieTitle = title,
            onRate = { progressVm.sendRating(req, title, null, it) },
            onDismiss = { progressVm.dismissRating() },
        )
    }
    PlayerScreenContent(s, partyVm)
}

/** Efeitos de carga (fontes, legendas, logo, cast). */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerSourceEffects(s: PlayerSession) = with(s) {
    LaunchedEffect(tmdbId, curSeason) {
        // Re-resolve a contagem de episódios da temporada ATUAL (muda ao trocar de temporada).
        if (type == "SERIES" && curSeason > 0) {
            epTotal = episodeVm.seasonEpisodeCount(tmdbId, curSeason)
        }
    }
    LaunchedEffect(tmdbId, type) {
        if (type == "SERIES") numSeasons = episodeVm.seasonCount(tmdbId)
    }
    LaunchedEffect(tmdbId, type) { if (type != "SERIES") runtimeMin = episodeVm.movieRuntime(tmdbId) }
    LaunchedEffect(tmdbId, type) {
        // Logo: TMDB (PT-BR) principal; metahub como fallback.
        val tmdbLogo = runCatching { episodeVm.tmdbLogoUrl(tmdbId, type) }.getOrDefault("")
        if (tmdbLogo.isNotBlank()) logoUrl = tmdbLogo
        val imdb = episodeVm.imdbId(tmdbId, type) ?: return@LaunchedEffect
        if (logoUrl.isBlank()) logoUrl = "https://images.metahub.space/logo/medium/$imdb/img"
        metaBg = "https://images.metahub.space/background/medium/$imdb/img"
    }

    // Pular trechos (v1.8.1): segmentos IntroDB/SkipDB/Anime-Skip por IMDb id.
    // Filmes também têm segmentos (resumo/abertura/encerramento) — sem restrição
    // a séries, exceto o Anime-Skip (só séries, e só com client ID digitado).
    LaunchedEffect(tmdbId, curSeason, curEpisode) {
        skipSegments = com.aotv.app.data.remote.SkipSegments()
        val imdb = runCatching { episodeVm.imdbId(tmdbId, type) }.getOrNull() ?: return@LaunchedEffect
        val durSec = exoPlayer.duration.takeIf { it > 0 }?.div(1000.0) ?: 0.0
        // Lote 16 item 2: a IntroDB filtrada (confiança ≥ 0,5, ≥ 1 envio) é a
        // fonte principal — UM caminho por título (evita buscar 2x). O
        // serviço antigo (IntroDB sem filtro + SkipDB + Anime-Skip) só entra
        // quando a IntroDB não conhece o título (sem segmentos E sem no_*).
        val introDb = s.loadIntroDb(imdb, type == "SERIES", curSeason, curEpisode)
        if (introDb != null && (introDb.hasAny() || introDb.noIntro || introDb.noRecap || introDb.noOutro)) {
            skipSegments = s.introDbToSkipSegments(introDb)
        } else {
            skipSegments = runCatching {
                skipSegmentsService.get(imdb, title, type == "SERIES", curSeason, curEpisode, durSec, animeSkipClientId)
            }.getOrDefault(com.aotv.app.data.remote.SkipSegments())
        }
    }

    // Resume de SÉRIE: as fontes recebidas vêm do estado de FILME (vazias p/ série). Carrega
    // as fontes Stremio do episódio e toca a salva (ou a melhor) — corrige "não carrega a fonte".
    LaunchedEffect(Unit) {
        if (type == "SERIES" && episode > 0 && currentSources.none { it.url == activeUrl }) {
            // Sem preferência ainda nesta sessão (abriu fresco pelo detalhe) → herda o addon
            // da última vez que essa série tocou, pra não perguntar nos episódios seguintes.
            // Última fonte escolhida À MÃO neste título (Lote 4 item 6): addon +
            // qualidade valem antes do addon herdado do progresso; sem ela, o
            // comportamento atual (lastAddon) continua.
            progressVm.lastSourcePick(tmdbId, type)?.let {
                preferredAddon = it.addon
                preferredQuality = it.qualityRank
            } ?: run { if (preferredAddon.isBlank()) preferredAddon = progressVm.lastAddon(tmdbId, type) }
            // Fontes PRÉ-CARREGADAS no meio do episódio anterior (Lote 4 item 5):
            // sem rede nenhuma quando o cache da sessão ainda vale (15 min).
            val eps = episodeVm.cachedEpisodeSources(tmdbId, curSeason, episode)
                ?: episodeVm.stremioEpisodeSources(tmdbId, curSeason, episode)
            if (eps.isNotEmpty()) {
                currentSources = eps
                val matchIdx = eps.indexOfFirst { it.url == activeUrl }
                if (matchIdx >= 0) {
                    currentSourceIdx = matchIdx
                    currentSourceLabel = eps[matchIdx].label
                    preferredAddon = eps[matchIdx].addon
                    preferredQuality = sourceQualityRank(eps[matchIdx].label)
                } else {
                    val i = pickPreferredSource(eps, preferredAddon, preferredQuality)
                    val best = eps[i]
                    currentSourceIdx = i
                    currentSourceLabel = best.label
                    activeUrl = best.url; streamUrl = best.url
                }
            } else if (streamUrl == null) {
                // Episódio aberto sem URL e nenhum addon devolveu fonte.
                sourceFailed = true
            }
        }
    }

    // Legendas externas do episódio ou filme atual. Episódio novo solta a legenda externa do
    // anterior (ela não serve no outro) e devolve a escolha para o automático.
    LaunchedEffect(tmdbId, type, curSeason, curEpisode) {
        selectedSubUrl = null
        subPicked = false
        // Lote 16 item 3: a 2ª legenda vale p/ o vídeo atual — trocar de
        // episódio/título desliga.
        secondarySubUrl = null
        secondaryCues = emptyList()
        dualSubState = DualSubState()
        // Lote 16 item 6: o estado de "voltar" do sleep timer vale 1x p/ a
        // pausa do timer — trocar de episódio/título zera.
        sleepWakeState = null
        sleepPaused = false
        // Idioma de áudio (Lote 4 item 7): episódio novo devolve a faixa para o
        // automático, como a legenda — a escolha à mão valia p/ o vídeo anterior.
        audioPicked = false
        externalSubs = runCatching { episodeVm.subtitlesFor(tmdbId, type, curSeason, curEpisode) }.getOrDefault(emptyList())
        pickAutoSub()
    }
    LaunchedEffect(selectedSubUrl) {
        externalCues = emptyList()
        val u = selectedSubUrl ?: return@LaunchedEffect
        externalCues = episodeVm.subtitleCues(u)
        if (externalCues.isEmpty() && selectedSubUrl == u) toastMsg = "Não deu para carregar essa legenda"
    }
    // 2ª legenda (Lote 16 item 3): mesmo caminho da 1ª externa.
    LaunchedEffect(secondarySubUrl) {
        secondaryCues = emptyList()
        val u = secondarySubUrl ?: return@LaunchedEffect
        secondaryCues = episodeVm.subtitleCues(u)
        if (secondaryCues.isEmpty() && secondarySubUrl == u) toastMsg = "Não deu para carregar a 2ª legenda"
    }
    // Legenda externa é desenhada pelo próprio player (ver externalCues), então trocar de
    // legenda não recarrega o vídeo.
    LaunchedEffect(streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        playVia = ""
        var play = url
        if (TorrentMagnet.isMagnet(url)) {
            // Com conta de debrid, o torrent vira link direto. Sem cache no serviço, toca por P2P.
            val service = episodeVm.debridLabel()
            val r = if (service != null) {
                switching = true
                debridStatus = "$service · preparando o link"
                episodeVm.debridResolve(url, curSeason, curEpisode)
            } else null
            debridStatus = null
            if (streamUrl != url) return@LaunchedEffect
            if (r is com.aotv.app.data.debrid.DebridResolve.Ok) {
                play = r.url; playVia = "debrid"
            } else {
                if (r is com.aotv.app.data.debrid.DebridResolve.NotCached && episodeVm.debridHideUncached() && !userLockedSource && nextMovieSource()) {
                    toastMsg = "$service não tem essa fonte pronta. Tentando a próxima."
                    return@LaunchedEffect
                }
                playVia = "p2p"
                when (r) {
                    is com.aotv.app.data.debrid.DebridResolve.NotCached -> toastMsg = "$service não tem esse arquivo pronto. Tocando por P2P."
                    is com.aotv.app.data.debrid.DebridResolve.Failed -> toastMsg = "${r.message}. Tocando por P2P."
                    else -> Unit
                }
            }
        }
        try {
            // v1.2.6 — headers do sufixo Kodi `|...` (ex.: Accept-Language
            // da TV do Vulgata) vão p/ a factory; a URI limpa vai p/ o item.
            val (cleanPlay, playHeaders) = splitKodiStreamHeaders(play)
            // v1.2.8 — stream Kodi (diag presente) passa pelo proxy local
            // (127.0.0.1): o ExoPlayer nunca vê o hostname punycode com
            // underscores, que o OkHttp rejeita no parse (STD 3 ASCII).
            // O proxy busca o upstream (java.net.URL, sem validação
            // STD3) com os headers do sufixo Kodi e repassa Range/206.
            // Só Kodi — Nuvio JS e torrent seguem o caminho normal.
            val (playUrl, factoryHeaders) = if (kodiDiag.isNotBlank()) {
                com.aotv.app.data.kodi.KodiLocalProxy.proxyUrl(cleanPlay, playHeaders)
            } else {
                cleanPlay to playHeaders
            }
            // Correção de cor (v1.9.0): o setVideoEffects precisa ser chamado
            // pelo menos 1x antes do prepare; depois os sliders reaplicam ao
            // vivo (LaunchedEffect junto do Volume Boost, abaixo).
            exoPlayer.setVideoEffects(buildColorEffects(colorState))
            exoPlayer.setMediaSource(mediaSourceFactory(context, factoryHeaders).createMediaSource(mediaItemFor(context, playUrl)))
            exoPlayer.prepare()
            if (!didSeek && pendingSeekMs > 0) { exoPlayer.seekTo(pendingSeekMs); didSeek = true }
            // Transmitindo: mantém o player local MUDO (o Cast recebe a fonte em efeito próprio).
            if (casting) exoPlayer.playWhenReady = false
        } catch (_: Exception) {}
    }

    // Listener: play/pause + buffering + duração + faixas (áudio/legenda)
    DisposableEffect(exoPlayer) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L); movieStarted = true
                    // Lote 17 item 1: tocou de verdade — o host ganha um acerto
                    // na saúde aprendida (1x por URL nesta sessão).
                    streamUrl?.let { noteSourceReady(it) }
                    readyUrl = streamUrl
                    switching = false // novo episódio no ar, tira o loading da troca
                    refreshTechInfo()
                    // Frame-rate matching (Lote 16 item 4) + medidor de FPS.
                    applyFrameRate()
                    fpsMeter.start()
                    maybeHdr10Fallback()
                }
                if (state == Player.STATE_ENDED) playbackEnded = true
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                movieErrored = true // fonte direta falhou → tenta a próxima
                playerError = shortPlayerError(error) // v1.2.7: vai p/ a tela de erro
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.length > 0 }
                textGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.length > 0 }
                // Faixas novas (episódio ou fonte trocados). Com a legenda ligada no perfil, liga a
                // do idioma escolhido. Escolha à mão no vídeo atual não é desfeita.
                pickAutoSub()
                // Lote 16 item 3: com a legenda desligada e o áudio estrangeiro,
                // mostra a faixa forced (falas em outra língua dentro do filme).
                maybeShowForcedSub()
                // Áudio no idioma preferido do perfil (Lote 4 item 7) + infos
                // técnicas do stream novo (Lote 4 item 4).
                pickAutoAudio()
                refreshTechInfo()
            }
            // Resolução real do que está tocando (ex "1080p") — mostrada junto da fonte.
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                videoQualityLabel = videoResolutionLabel(size.width, size.height)
                // Lote 12 — Frente 2: o modo "Automático" compara com a tela.
                videoWidth = size.width
                videoHeight = size.height
                refreshTechInfo()
                // Frame-rate matching (Lote 16 item 4): a resolução mudou,
                // o fps do stream pode ter mudado junto.
                applyFrameRate()
            }
            // Sessão de áudio (re)criada: (re)aplica o Volume Boost nela.
            override fun onAudioSessionIdChanged(audioSessionId: Int) { applyVolBoost(); applyEqualizer(); applyNightMode() }
        }
        exoPlayer.addListener(l); onDispose { exoPlayer.removeListener(l) }
    }

    // Volume Boost (Fase C): (re)aplica quando o toggle ou o nível muda nas
    // Configurações. A 1ª aplicação acontece quando a sessão de áudio fica
    // pronta (onAudioSessionIdChanged, no listener acima).
    LaunchedEffect(volBoostEnabled, volBoostLevel) { applyVolBoost() }

    // Correção de cor (v1.9.0 + Lote 16 item 8): o debounce segura os ticks
    // do slider (200 ms) e preset/daltonismo aplicam na hora — sem reiniciar
    // o vídeo. O runCatching (em applyColorEffects) protege a reprodução se
    // o renderer não aceitar os efeitos.
    var prevColorState by remember { mutableStateOf(colorState) }
    LaunchedEffect(colorBrightness, colorContrast, colorSaturation, colorHue, colorDaltonism) {
        onColorStateChanged(colorState, prevColorState)
        prevColorState = colorState
    }

    DisposableEffect(castPlayer, streamUrl) {
        val cp = castPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : androidx.media3.cast.SessionAvailabilityListener {
            // Só alterna o flag; a fonte é enviada pelo efeito abaixo (LaunchedEffect casting).
            override fun onCastSessionAvailable() { casting = true }
            override fun onCastSessionUnavailable() {
                // Volta pro local no ponto onde a TV estava.
                val pos = runCatching { cp.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
                casting = false
                exoPlayer.seekTo(pos); exoPlayer.playWhenReady = true
            }
        }
        cp.setSessionAvailabilityListener(listener)
        onDispose { runCatching { cp.setSessionAvailabilityListener(null) } }
    }
    DisposableEffect(Unit) { onDispose { runCatching { castPlayer?.release() } } }

    // Envia a fonte atual pro Chromecast (ao iniciar cast OU ao trocar de fonte/episódio),
    // começando do ponto onde o local estava, e silencia o player local.
    LaunchedEffect(casting, streamUrl) {
        val cp = castPlayer ?: return@LaunchedEffect
        val src = streamUrl
        // Torrent só vai pro Chromecast pelo link do debrid. O motor P2P roda dentro deste aparelho.
        val url = if (TorrentMagnet.isMagnet(src)) src?.let { episodeVm.debridNow(it, curSeason, curEpisode) } else src
        if (casting && TorrentMagnet.isMagnet(src) && url == null) {
            toastMsg = "Fonte P2P não vai pro Chromecast. Escolha outra fonte ou pare de transmitir."
            return@LaunchedEffect
        }
        if (casting && url != null) {
            val start = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                .let { if (it > 0) it else pendingSeekMs }.coerceAtLeast(0L)
            val cb = MediaItem.Builder().setUri(url)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(displayTitle).build())
            cb.setMimeType(if (url.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            cp.setMediaItem(cb.build(), start)
            cp.playWhenReady = true
            exoPlayer.playWhenReady = false
        }
    }

    // O som só se reajusta quando a pessoa para de mexer na sincronia (450 ms).
    LaunchedEffect(audioDelay) {
        delay(450)
        extSync?.setDelay(audioDelay)
        if (extUrl != null) saveExt()
        else {
            // Áudio próprio (Lote 14 item 3 + Lote 17 item 10): atraso positivo vai
            // p/ o processador de PCM; adianto (−) atrasa o vídeo pelo mesmo valor.
            ownAudioDelayProcessor.setDelayMs((audioDelay.coerceAtLeast(0.0) * 1000).roundToLong())
            videoDelayState.setDelayMs((-audioDelay.coerceAtMost(0.0) * 1000).roundToLong())
            if (audioDelay != 0.0 || progressVm.ownAudioDelay(extKey) != null) saveOwnAudio()
        }
    }
    // Lote 14 item 4 — folha de fontes estável: anota cada leva de fontes e,
    // sem fonte nova há 1,5 s, fotografa o ranking uma única vez. O efeito
    // recomeça a cada lista nova, então o prazo só vence com a busca quieta.
    LaunchedEffect(currentSources) {
        noteSourcesArrived()
        delay(1500)
        markSourcesSettled()
    }
    // Lote 14 item 4 — quem focou numa fonte da folha nunca tem a ordem mudada sozinha:
    // o foco do D-pad marca a fonte como tocada (o toque marca no onPick da ação).
    LaunchedEffect(sourcePanelOpen, panelKind, sourcePanelIdx) {
        if (!sourcePanelOpen || panelKind != "sources") return@LaunchedEffect
        val url = sourceActions.getOrNull(sourcePanelIdx)?.stream?.url
        if (!url.isNullOrBlank()) markSourceTouched(url)
    }
    // Hear it (Lote 19 item 3, v1.29.0): transmitindo, o som da outra fonte NÃO
    // desliga — vira o áudio do celular em outro idioma, seguindo a posição da
    // TV (CastPlayer). O volume da TV é silenciado pelo sync e devolvido ao
    // parar de transmitir (ver ExtAudioSync.reanchor). Ao parar, volta a
    // seguir o player local. Sem som de outra fonte, nada muda.
    LaunchedEffect(casting) {
        val cp = castPlayer
        if (casting && cp != null) {
            if (extSync != null) {
                extSync?.reanchor(cp)
                toastMsg = "Ouvindo no celular em outro idioma — a TV mostra só a imagem. Ajuste fino em Ajustar sincronia."
            }
        } else if (!casting) {
            extSync?.reanchor(exoPlayer)
        }
    }
    // Ao voltar ao título, religa o som da mesma fonte com o mesmo ajuste. As fontes podem
    // chegar aos poucos (série), por isso tenta de novo a cada lista nova. Vale também
    // transmitindo: o setExt usa o player atual (CastPlayer) como referência.
    LaunchedEffect(readyUrl, currentSources, casting) {
        if (extRestored || extUrl != null || readyUrl == null) return@LaunchedEffect
        val saved = progressVm.extAudio(extKey) ?: run { extRestored = true; return@LaunchedEffect }
        val s = currentSources.firstOrNull {
            it.url != streamUrl && extAudioBlock(it.url).isEmpty() && sourceSig(it, title) == saved.sig
        } ?: return@LaunchedEffect
        extRestored = true
        audioDelay = saved.delaySec
        setExt(s) { ok ->
            if (ok) toastMsg = "Som de ${extName(s, title)} de volta" +
                if (audioDelay != 0.0) " (${audioDelayLabel(audioDelay).lowercase()})" else ""
        }
    }

    // Some o HUD de gesto após 800ms parado
    LaunchedEffect(gestureHud) { if (gestureHud != null) { delay(800); gestureHud = null } }
    // Some o indicador de busca do gesto ("−10s" / "+10s") após 800ms parado
    LaunchedEffect(seekHud) { if (seekHud != null) { delay(800); seekHud = null } }

}

/** Efeitos de reprodução (progresso, troca automática de fonte, fim do vídeo). */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerPlaybackEffects(s: PlayerSession) = with(s) {
    LaunchedEffect(streamUrl) {
        var sinceSave = 0L
        while (streamUrl != null) {
            val p = currentPlayer()
            position = p.currentPosition.coerceAtLeast(0L)
            duration = p.duration.coerceAtLeast(0L).let { if (it > 0) it else duration }
            isPlaying = p.isPlaying
            sinceSave += 500
            if (sinceSave >= 10_000 && position > 0) {
                val curEp = if (type == "SERIES" && curEpisode > 0) "T$curSeason:E$curEpisode" else lastEpisode
                progressVm.save(tmdbId, type, title, posterUrl, position, duration, curEp, streamUrl ?: activeUrl, preferredAddon)
                sinceSave = 0
            }
            // Pular abertura/encerramento automático (Lote 9 item 2): só
            // quando o toggle está ligado e a reprodução entrou no segmento
            // andando (não por pulo manual — ver maybeAutoSkip).
            maybeAutoSkip()
            // Lote 16 item 2: aviso de pós-créditos (1x por episódio).
            maybePostCreditsNotice()
            // VISTO ao passar de 90% da duração (série) — 1x por episódio.
            if (type == "SERIES" && curEpisode > 0 && duration > 0 &&
                position >= duration * 0.9 && markedWatchedEp != curEpisode
            ) {
                progressVm.markWatched(tmdbId, curSeason, curEpisode)
                markedWatchedEp = curEpisode
            }
            // Filme passou de 90% — 1x por título; abre o pedido de nota.
            if (type != "SERIES" && duration > 0 &&
                position >= duration * 0.9 && !markedWatchedMovie
            ) {
                progressVm.markMovieWatched(tmdbId)
                markedWatchedMovie = true
            }
            // AUTO-PLAY: aviso de próximo episódio só no ÚLTIMO 1 MINUTO (antes aparecia a 90%,
            // cedo demais em episódios longos). Lote 23 item 11: o aviso é
            // MANUAL aqui (sem contagem, sem avançar sozinho) — a contagem só
            // arma no fim real (ver LaunchedEffect(playbackEnded)).
            if (upNextEnabled && hasNextEpisode && duration > 60_000 && position >= duration - 60_000 && nextPromptEp != curEpisode) {
                nextPromptEp = curEpisode; promptIsSeason = false; nextCountdown = autoNextDelaySec; nextCountdownArmed = false; showNextPrompt = true
            }
            // PRÉ-CARREGA o próximo (ou a próxima temporada) na METADE do episódio atual. Quando
            // o usuário aperta "Próximo", as fontes já estão na memória e a troca é instantânea.
            // Modo privado (Lote 4 item 5): nada de trabalho especulativo ligado ao que se
            // assiste — o preload em si não grava progresso, mas no privado ele nem dispara.
            if (!privateMode && duration > 0 && position >= duration / 2 && prefetchedEp != curEpisode) {
                prefetchedEp = curEpisode
                when {
                    hasNextEpisode -> episodeVm.prefetchEpisode(tmdbId, curSeason, curEpisode + 1)
                    hasNextSeason -> episodeVm.prefetchEpisode(tmdbId, curSeason + 1, 1)
                }
            }
            // Último episódio da temporada com temporada seguinte → oferece a PRÓXIMA TEMPORADA
            // já nos créditos, não só quando o arquivo termina (muita fonte corta antes do fim).
            // Lote 23 item 11: manual aqui também (sem contagem).
            if (upNextEnabled && !hasNextEpisode && hasNextSeason && duration > 60_000 &&
                position >= duration - 60_000 && nextPromptEp != curEpisode
            ) {
                nextPromptEp = curEpisode; promptIsSeason = true; nextCountdown = autoNextDelaySec; nextCountdownArmed = false; showNextPrompt = true
            }
            delay(500)
        }
    }
    // Trakt. Avisa quando começa a tocar e quando pausa, e "stop" ao sair (logo abaixo).
    LaunchedEffect(isPlaying, curEpisode) {
        progressVm.scrobble(if (isPlaying) "start" else "pause", tmdbId, type, curSeason, curEpisode, position, duration)
    }
    // Salva ao sair da tela do player. Usa `position` (último poll do currentPlayer, já
    // cast-aware) — NÃO exoPlayer.currentPosition: no onDispose o player já foi liberado
    // (ordem LIFO → release roda antes) e no Cast o player local está congelado.
    // Ao entrar, trava em paisagem; ao sair, volta ao automático.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            val pos = position.coerceAtLeast(0L)
            // Salva o episódio ATUAL (após próximo/anterior), não o que abriu a tela.
            val curEp = if (type == "SERIES" && curEpisode > 0) "T$curSeason:E$curEpisode" else lastEpisode
            if (pos > 0) progressVm.save(tmdbId, type, title, posterUrl, pos, duration, curEp, streamUrl ?: activeUrl, preferredAddon)
            progressVm.scrobble("stop", tmdbId, type, curSeason, curEpisode, pos, duration)
            // Ao sair do player, volta ao automático (caso o usuário tenha travado paisagem).
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }
    // Auto-hide dos controles
    LaunchedEffect(controlsVisible, isPlaying, streamUrl, chipFocused) {
        if (controlsVisible && !chipFocused && (isPlaying || streamUrl == null)) { delay(4500); controlsVisible = false }
    }

    // Pausa em background + SALVA o progresso no ON_STOP (app minimizado/morto pelo SO) — senão
    // perde até 10s (ou tudo, se o onDispose não rodar). Cobre o "perde a reprodução do seriado".
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPlayingOnStop = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    wasPlayingOnStop = exoPlayer.playWhenReady
                    exoPlayer.playWhenReady = false
                    val pos = runCatching { currentPlayer().currentPosition }.getOrDefault(position).coerceAtLeast(0L)
                    val curEp = if (type == "SERIES" && curEpisode > 0) "T$curSeason:E$curEpisode" else lastEpisode
                    if (pos > 0) progressVm.save(tmdbId, type, title, posterUrl, pos, duration, curEp, streamUrl ?: activeUrl, preferredAddon)
                }
                // Só retoma sozinho se já estava tocando ao sair, e sem Cast ativo (senão duplica
                // o áudio — a TV já está tocando e o player local tomaria o play também).
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (wasPlayingOnStop && !casting) exoPlayer.playWhenReady = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Minimizado p/ o mini player: a sessão (e o ExoPlayer) sobrevive à
            // saída da tela — liberar aqui mataria o áudio. O fechar do mini
            // player ou a volta à tela cheia libera depois (ver releaseAll).
            if (!minimizedByUser) releaseAll()
        }
    }

    // Registra o gatilho de PiP enquanto houver fonte tocando no ExoPlayer.
    DisposableEffect(streamUrl) {
        PipState.enter = if (streamUrl != null) ({ enterPip() }) else null
        onDispose { PipState.enter = null }
    }
    // Lote 24 item 8: amostragem do Ambilight (só com o toggle ligado e fonte
    // ativa; o loop morre ao desligar, trocar de fonte ou sair do player).
    DisposableEffect(ambilightState.enabled, streamUrl) {
        if (ambilightState.enabled && streamUrl != null) {
            ambilightSampler.start(
                scope,
                isPlaying = { isPlaying },
                positionMs = { position },
                streamUrl = { streamUrl },
                posterUrl = { posterUrl.ifBlank { backdropUrl } },
                onColors = { ambilightColors = it },
            )
        }
        onDispose { ambilightSampler.stop() }
    }
    // Contagem regressiva do prompt — 0 = avança automaticamente (próximo ep OU próxima temporada).
    // Com o "A seguir" automático DESLIGADO (Fase C), o cartão aparece mas a
    // contagem não anda: só avança no botão "Assistir".
    // Lote 23 item 11 (JOTV 1.0.22 §4.3): a contagem só anda com
    // nextCountdownArmed — no último minuto o cartão é manual e este efeito
    // volta sem contar (a chave nextCountdownArmed o reinicia no fim real).
    LaunchedEffect(showNextPrompt, nextCountdownArmed) {
        if (!showNextPrompt) return@LaunchedEffect
        if (!autoNextEnabled) return@LaunchedEffect
        if (!nextCountdownArmed) return@LaunchedEffect
        while (nextCountdown > 0) { delay(1000); nextCountdown -= 1 }
        // NÃO zerar nextCountdownArmed aqui: ele é chave deste efeito e
        // mudá-lo cancelaria o avanço abaixo. O reset está em
        // playNextEpisode/goToNextSeason, chamados logo abaixo.
        showNextPrompt = false
        // Lote 24 item 10: o avanço automático conta; 3 seguidos sem nenhuma
        // interação do usuário pausam e perguntam antes de avançar (o avanço
        // em si NÃO acontece aqui — o "Continuar" do diálogo avança depois).
        if (stillWatching.noteAutoAdvance()) {
            stillWatchingPendingSeason = promptIsSeason
            stillWatchingAsk = true
            runCatching { currentPlayer().playWhenReady = false }
            return@LaunchedEffect
        }
        // v1.13.0 (JOTV 1.0.19 §3.4): o avanço automático preserva a cena
        // pós-créditos ainda não vista — em vez de trocar de episódio, vai
        // para o início dela. Se já estiver dentro da cena, só esconde o
        // cartão; o fim da reprodução mostra o cartão de novo. Avanço à mão
        // (botão "Próximo", chip, "Pular pós-créditos") não muda: é escolha
        // do usuário e avança direto.
        val pc = skipSegments.postCredits
        if (pc != null && position < pc.end) {
            if (position < pc.start) {
                currentPlayer().seekTo(pc.start)
                position = pc.start
                toastMsg = "Cena pós-créditos"
            }
            return@LaunchedEffect
        }
        if (promptIsSeason) goToNextSeason() else playNextEpisode()
    }
    // Fim da reprodução: série com próximo → mostra o card "Próximo episódio" (BOTÃO + contagem),
    // não avança/sai sozinho. Filme ou último episódio → salva e volta ao detalhe.
    LaunchedEffect(playbackEnded) {
        if (!playbackEnded) return@LaunchedEffect
        playbackEnded = false
        saveNow()
        // Sleep timer "no fim do episódio" (Lote 9 item 4): não avança nem
        // volta — fica parado no último quadro, com o timer desligado.
        if (sleepAtEpisodeEnd) {
            sleepAtEpisodeEnd = false
            sleepMinutes = null
            return@LaunchedEffect
        }
        when {
            // Tem próximo episódio na temporada → card "Próximo episódio".
            // Lote 23 item 11: o fim real ARMA a contagem (antes o cartão do
            // último minuto já contava e avançava nos créditos).
            type == "SERIES" && hasNextEpisode && upNextEnabled -> { promptIsSeason = false; nextPromptEp = curEpisode; nextCountdown = autoNextDelaySec; nextCountdownArmed = true; showNextPrompt = true }
            // Acabou a temporada e existe outra → card "Próxima temporada".
            type == "SERIES" && hasNextSeason && upNextEnabled -> { promptIsSeason = true; nextCountdown = autoNextDelaySec; nextCountdownArmed = true; showNextPrompt = true }
            // "A seguir" desligado e há próximo: fica parado no fim (o chip
            // "Próximo" e o botão Voltar continuam valendo).
            type == "SERIES" && (hasNextEpisode || hasNextSeason) -> Unit
            else -> onBack() // filme ou fim da série
        }
    }
    LaunchedEffect(type, tmdbId, curSeason, curEpisode) {
        if (type != "SERIES" || tmdbId <= 0 || curSeason <= 0 || curEpisode <= 0) return@LaunchedEffect
        // Última fonte escolhida À MÃO neste título (Lote 4 item 6): mesma
        // precedência do efeito de resumo — addon + qualidade antes do lastAddon.
        progressVm.lastSourcePick(tmdbId, type)?.let {
            preferredAddon = it.addon
            preferredQuality = it.qualityRank
        } ?: run { if (preferredAddon.isBlank()) preferredAddon = progressVm.lastAddon(tmdbId, type) }
        fun merge(novas: List<MoviePlayerSource>) {
            if (novas.isEmpty()) return
            val tocando = currentSources.getOrNull(currentSourceIdx)?.url
            // A ordem passa a ser a do EPISÓDIO (ordem do gerenciador de addons), a mesma que
            // a tela de detalhe mostra. Antes as novas iam no fim e o seletor do player ficava
            // numa ordem diferente da lista que o usuário tinha acabado de ver.
            val sobras = currentSources.filterNot { atual -> novas.any { it.url == atual.url } }
            val lista = novas + sobras
            if (lista.map { it.url } == currentSources.map { it.url }) return
            currentSources = lista
            // Reancora: a fonte em reprodução continua sendo a "atual", senão o próximo
            // fallback pularia a partir do lugar errado.
            currentSourceIdx = lista.indexOfFirst { it.url == tocando }.coerceAtLeast(0)
            // Episódio aberto sem URL. Toca a primeira fonte que chegar, sem esperar todos os addons.
            if (streamUrl == null) applyMovieSource(pickPreferredSource(lista, preferredAddon, preferredQuality))
        }
        episodeVm.cachedEpisodeSources(tmdbId, curSeason, curEpisode)?.let { merge(it); return@LaunchedEffect }
        runCatching {
            episodeVm.stremioEpisodeSourcesProgressive(tmdbId, curSeason, curEpisode) { merge(it) }
        }
    }

    LaunchedEffect(Unit) {
        if (startPositionMs > 0) return@LaunchedEffect
        val epKey = if (type == "SERIES" && episode > 0) "T$season:E$episode" else ""
        val salvo = progressVm.savedPosition(tmdbId, type, epKey)
        if (salvo <= 10_000) return@LaunchedEffect
        // Nada tocou ainda (senão seria o usuário já assistindo, não abertura).
        if (didSeek || position > 0) return@LaunchedEffect
        pendingSeekMs = salvo
        // Se o ExoPlayer já preparou a mídia, aplica na hora; senão o prepare usa o pendingSeekMs.
        runCatching { if (streamUrl != null) { exoPlayer.seekTo(salvo); didSeek = true } }
    }

    // Erro de reprodução (fonte direta) → pula pra próxima; sem próxima → marca indisponível.
    // Fonte travada à mão (userLockedSource) NUNCA auto-troca — o usuário mandou manter, mas o
    // erro precisa aparecer (sourceFailed), senão o player fica preso sem explicação.
    // v1.8.1: com a troca automática DESLIGADA (autoSwitchEnabled), também espera sem trocar.
    LaunchedEffect(movieErrored) {
        if (!movieErrored) return@LaunchedEffect
        movieErrored = false
        if (userLockedSource || !autoSwitchEnabled) sourceFailed = true
        else if (!nextMovieSource()) sourceFailed = true
    }
    // Fonte que não inicia. Os espelhos do mesmo arquivo às vezes demoram 15 s para entregar o
    // começo, e qual deles fica lento muda a cada pedido. Havendo outro espelho igual (mesmo
    // addon, idioma e resolução), a espera cai para 5 s e troca por ele. Sem espelho, espera o
    // tempo configurado (sourceTimeoutSec) e pula pra próxima fonte.
    LaunchedEffect(streamUrl) {
        movieStarted = false
        val u = streamUrl ?: return@LaunchedEffect
        triedUrls += u
        if (TorrentMagnet.isMagnet(u)) {
            // Espera o debrid decidir entre link direto e P2P antes de contar o tempo.
            var w = 0L
            while (playVia == "" && streamUrl == u && w < 40_000) { delay(250); w += 250 }
            if (movieStarted || streamUrl != u) return@LaunchedEffect
        }
        if (TorrentMagnet.isMagnet(u) && playVia != "debrid") {
            // P2P precisa achar pares e baixar o começo. Desiste aos 45 s só se nunca apareceu par nem
            // metadado (torrent morto). Achou alguém, espera até 2 min, pares caem e voltam no começo.
            var waited = 0L
            var alive = false
            while (waited < 120_000) {
                delay(5_000); waited += 5_000
                if (movieStarted || streamUrl != u) return@LaunchedEffect
                val st = Torrents.ifStarted()?.status(u)
                if (st != null && (st.peers > 0 || st.hasMetadata)) alive = true
                if (waited >= 45_000 && !alive) break
            }
            if (movieStarted || streamUrl != u) return@LaunchedEffect
            if (userLockedSource || !autoSwitchEnabled) sourceFailed = true
            else if (!nextMovieSource(slow = true)) sourceFailed = true
            return@LaunchedEffect
        }
        val hasMirror = !userLockedSource && sameMirror(facts(), currentSourceIdx, triedUrls) >= 0
        kotlinx.coroutines.delay(if (hasMirror) 5_000 else sourceTimeoutSec * 1_000L)
        if (movieStarted || streamUrl != u) return@LaunchedEffect
        if (userLockedSource || !autoSwitchEnabled) { noteTimeout(); sourceFailed = true; return@LaunchedEffect }
        val m = sameMirror(facts(), currentSourceIdx, triedUrls)
        if (m >= 0) {
            toastMsg = "Servidor lento. Tentando outro espelho da mesma fonte."
            applyMovieSource(m)
        } else if (!nextMovieSource(slow = true)) { noteTimeout(); sourceFailed = true }
    }

    // Vídeo carregou. (1) Filme com duração que não bate com o TMDB é outro filme de nome
    // parecido, pula pra próxima. (2) 4 s depois testa as outras fontes do mesmo idioma e, se
    // achar uma pelo menos 25% mais alta, troca uma vez sem perder o ponto. Fonte escolhida à
    // mão ou fixada não troca.
    LaunchedEffect(readyUrl) {
        val u = readyUrl ?: return@LaunchedEffect
        if (u != streamUrl) return@LaunchedEffect
        val sec = exoPlayer.duration.takeIf { it > 0 }?.div(1000.0) ?: 0.0
        if (type != "SERIES" && !userLockedSource && durationMismatch(runtimeMin, sec)) {
            wrongUrls += u
            val f = facts()
            // Lote 19 item 7 (f): a troca por filme errado também é automática —
            // pula os torrents que o gate do debrid veta, sem tocar no triedUrls.
            var n = nextIndex(f, triedUrls, emptySet())
            while (n >= 0 && debridBlocksAutoSwitch(f[n].url)) n = nextIndex(f, triedUrls + f[n].url, emptySet())
            if (n >= 0 && !f[n].wrongTitle) {
                toastMsg = "Essa fonte era de outro filme com nome parecido. Tentando a próxima."
                applyMovieSource(n)
                return@LaunchedEffect
            }
        }
        // Lote 19 item 7 (e): com "Subir a qualidade sozinho" desligado, o
        // upgrade 1x não roda — o player fica na qualidade que começou.
        if (upgradeDone || userLockedSource || !autoUpgradeEnabled) return@LaunchedEffect
        delay(4_000)
        val cur = currentSources.getOrNull(currentSourceIdx) ?: return@LaunchedEffect
        val lang = langOf(cur.label)
        fun consider(): Boolean {
            if (upgradeDone || userLockedSource || !autoUpgradeEnabled || casting || streamUrl != u) return true
            val curH = exoPlayer.videoSize.height
            if (curH <= 0) return false
            var best = -1
            var bestH = Math.round(curH * 1.25f)
            facts().forEachIndexed { i, f ->
                val h = f.probe?.height ?: 0
                if (i == currentSourceIdx || f.dead || f.wrongTitle || f.url in triedUrls || h == 0 || f.lang != lang) return@forEachIndexed
                // Lote 19 item 7 (b+e): o upgrade respeita a qualidade máxima do
                // perfil (não sobe p/ 4K com teto de 720p) e o gate do debrid (f).
                if (maxQuality > 0 && f.rank > maxQuality) return@forEachIndexed
                if (debridBlocksAutoSwitch(f.url)) return@forEachIndexed
                if (h >= bestH && f.probe!!.ms < 6_000) { best = i; bestH = h }
            }
            if (best < 0) return false
            upgradeDone = true
            toastMsg = "Trocando para ${heightLabel(bestH)} sem perder o ponto"
            applyMovieSource(best)
            return true
        }
        if (consider()) return@LaunchedEffect
        val todo = currentSources.filter {
            it.url != u && it.url !in triedUrls && it.url.startsWith("http") &&
                langOf(it.label) == lang && episodeVm.probeCached(it.url) == null
        }.take(8)
        for (pair in todo.chunked(2)) {
            kotlinx.coroutines.coroutineScope {
                pair.map { async { episodeVm.probe(it.url) } }.forEach { it.await() }
            }
            if (consider()) return@LaunchedEffect
        }
    }
    // Buffering TRAVADO: fonte conectou (READY) mas empaca sem avançar (stall infinito,
    // sem erro) → pula pra próxima. Cobre o caso "1ª fonte abre e fica no preto carregando".
    // Tempo = sourceTimeoutSec (v1.8.1); P2P continua 30 s.
    LaunchedEffect(buffering, streamUrl) {
        if (!buffering || streamUrl == null) return@LaunchedEffect
        val u = streamUrl
        val p2p = TorrentMagnet.isMagnet(u) && playVia != "debrid"
        var startPos = position
        while (true) {
            kotlinx.coroutines.delay(if (p2p) 30_000 else sourceTimeoutSec * 1_000L)
            if (!buffering || streamUrl != u || position > startPos + 500) return@LaunchedEffect
            // P2P que ainda recebe dado num ritmo razoável segue esperando em vez de trocar.
            if (p2p && (Torrents.ifStarted()?.status(u)?.downloadBps ?: 0) > 64_000) { startPos = position; continue }
            // Ainda travando na mesma fonte e sem progredir → troca (ou avisa, se travada à mão
            // ou com a troca automática desligada — v1.8.1).
            val bufferingMsg = "Timeout: o stream travou no buffering por ${sourceTimeoutSec}s (sem erro do player)"
            if (userLockedSource || !autoSwitchEnabled) { noteTimeout(bufferingMsg); sourceFailed = true }
            else if (!nextMovieSource(slow = true)) { noteTimeout(bufferingMsg); sourceFailed = true }
            return@LaunchedEffect
        }
    }
    // Todas as fontes falharam → avisa e volta ao detalhe (não deixa preso no preto).
    // v1.2.7 (Kodi): com diag, NÃO volta sozinho — o Davi precisa ler e mandar
    // o print do diagnóstico na tela de erro; ele volta pelo botão "Voltar".
    // v1.13.0 (JOTV 1.0.19 §3.7): com "Tentar novamente"/"Escolher outra fonte"
    // na tela, NÃO volta sozinho — 2,5 s não dá tempo de ler e tocar. Sem ação
    // possível no overlay (nenhuma fonte), volta como antes.
    LaunchedEffect(sourceFailed) {
        if (!sourceFailed) return@LaunchedEffect
        if (kodiDiag.isNotBlank()) return@LaunchedEffect
        if (sourceFailedHasActions) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        onBack()
    }

}

/** Tela do player. Vídeo, camadas por cima e painel. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreenContent(s: PlayerSession, partyVm: WatchPartyViewModel) = with(s) {
    val tapInteraction = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    // Root é dono do foco (D-pad), inclusive enquanto a fonte ainda está sendo resolvida.
    LaunchedEffect(activeUrl, streamUrl) { runCatching { focusRequester.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Lote 24 item 10: qualquer tecla é interação — zera o contador
                // do "Ainda assistindo?" (antes de qualquer desvio do when).
                noteUserInteraction()
                val kc = ev.nativeKeyEvent.keyCode
                // TELA TRAVADA (item 4): D-pad/teclado não mexe em nada — só BACK/ESC
                // sai do player e OK/CENTER mostra o cadeado. O Voltar nunca trava.
                if (locked) {
                    return@onKeyEvent when (kc) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { lockHintVisible = !lockHintVisible; true }
                        else -> true
                    }
                }
                // Barra de sincronia do som. Setas mudam o atraso e segurar acelera.
                // Áudio próprio (Lote 14 item 3): só atrasa (0 a +10 s) — o passo
                // negativo diminui o atraso até zero, nunca abaixo dele.
                if (syncBarOpen) {
                    val r = ev.nativeKeyEvent.repeatCount
                    fun shift(base: Double) {
                        audioDelay = if (extUrl != null) addDelay(audioDelay, syncStep(base, r))
                        else addDelay(audioDelay, syncStep(base, r)).coerceIn(OWN_AUDIO_DELAY_MIN_SEC, OWN_AUDIO_DELAY_MAX_SEC)
                    }
                    when (kc) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> shift(-0.05)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> shift(0.05)
                        KeyEvent.KEYCODE_DPAD_UP -> shift(1.0)
                        KeyEvent.KEYCODE_DPAD_DOWN -> shift(-1.0)
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> shift(10.0)
                        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> shift(-10.0)
                        KeyEvent.KEYCODE_PROG_YELLOW -> seekBy(-5_000)
                        KeyEvent.KEYCODE_PROG_RED -> audioDelay = 0.0
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> togglePlay()
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            syncBarOpen = false; if (extUrl != null) saveExt() else saveOwnAudio()
                        }
                    }
                    controlsVisible = false
                    return@onKeyEvent true
                }
                // Teclas coloridas como no webOS. Vermelho Fontes, verde Legendas, amarelo Áudio.
                // A mesma cor de novo fecha o painel.
                val colorKind = when (kc) {
                    KeyEvent.KEYCODE_PROG_RED -> "sources"
                    KeyEvent.KEYCODE_PROG_GREEN -> "subs"
                    KeyEvent.KEYCODE_PROG_YELLOW -> "audio"
                    else -> null
                }
                if (colorKind != null) {
                    val list = when (colorKind) { "subs" -> subActions; "audio" -> audioActions; else -> sourceActions }
                    when {
                        list.isEmpty() -> toastMsg = if (colorKind == "subs") "Sem legendas para este vídeo" else "Nada para trocar neste vídeo"
                        sourcePanelOpen && panelKind == colorKind -> sourcePanelOpen = false
                        else -> openDpadPanel(colorKind)
                    }
                    return@onKeyEvent true
                }
                // Painel de Fontes/Episódios aberto: ↑↓ navega, OK executa, ←/Back fecha.
                if (sourcePanelOpen) {
                    // Painel de COR (v1.9.0): navegação própria — ↑↓ muda o foco
                    // (sliders, Redefinir, presets, daltonismo), ←→ ajusta o
                    // slider com foco, OK aplica a linha (preset/modo/Redefinir).
                    // Sem esta guarda, o OK executaria uma ação de fonte.
                    if (panelKind == "color") {
                        return@onKeyEvent when (kc) {
                            KeyEvent.KEYCODE_DPAD_UP -> { colorFocusIdx = (colorFocusIdx - 1 + COLOR_FILTER_ROWS) % COLOR_FILTER_ROWS; true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { colorFocusIdx = (colorFocusIdx + 1) % COLOR_FILTER_ROWS; true }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val delta = if (kc == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                                if (colorFocusIdx < COLOR_FILTER_SLIDERS) {
                                    applyColorState(adjustColorState(colorState, colorFocusIdx, delta)); true
                                } else if (colorFocusIdx == COLOR_FILTER_SLIDERS) {
                                    sourcePanelOpen = false; true
                                } else true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                applyColorState(activateColorRow(colorState, colorFocusIdx)); true
                            }
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { sourcePanelOpen = false; true }
                            else -> true
                        }
                    }
                    // Painel de LEGENDAS (Lote 16 item 3): ←→ ajusta o atraso da
                    // 1ª legenda ao vivo (passo fino 0,1 s); ↑↓/OK continuam
                    // navegando a lista de ações (guarda genérica abaixo).
                    if (panelKind == "subs" && (kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        adjustSubDelay(if (kc == KeyEvent.KEYCODE_DPAD_RIGHT) DELAY_STEP_FINE_MS else -DELAY_STEP_FINE_MS)
                        return@onKeyEvent true
                    }
                    // Painel de INFOS TÉCNICAS (Lote 4 item 4): só mostra dados — ↑↓/OK
                    // não executam nada e ←/Back fecha. Sem esta guarda, o OK
                    // executaria uma ação de fonte (o panelActions cai em sourceActions).
                    if (panelKind == "info") {
                        return@onKeyEvent when (kc) {
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { sourcePanelOpen = false; true }
                            else -> true
                        }
                    }
                    // Painel do EQUALIZADOR (Lote 9 item 3): sliders e presets
                    // com foco próprio — ←→ ajusta o slider com foco, o resto
                    // passa p/ o composável (não navega a lista de ações).
                    if (panelKind == "eq") {
                        return@onKeyEvent when (kc) {
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { sourcePanelOpen = false; true }
                            else -> false
                        }
                    }
                    // Painel do MODO NOTURNO (Lote 16 item 1): mesmo padrão do
                    // equalizador — o painel tem foco próprio (linhas clicáveis).
                    if (panelKind == "night") {
                        return@onKeyEvent when (kc) {
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { sourcePanelOpen = false; true }
                            else -> false
                        }
                    }
                    // Fileira de chips de filtro do painel de fontes (Lote 23
                    // item 13): os chips entram no anel de navegação junto
                    // com a lista — ↑ na 1ª linha entra nos chips, ↑ de novo
                    // vai p/ a última linha, ↓ nos chips volta p/ a 1ª linha,
                    // ↓ na última entra nos chips, ←→ troca de chip, OK
                    // aplica o filtro e continua nos chips.
                    val chips = if (panelKind == "sources") sourceFilterChips else emptyList()
                    fun currentChipIdx(): Int = chips.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
                    val n = panelActions.size
                    return@onKeyEvent when (kc) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            when {
                                sourceChipCursor >= 0 -> { sourceChipCursor = -1; if (n > 0) sourcePanelIdx = n - 1; true }
                                chips.isNotEmpty() && sourcePanelIdx == 0 -> { sourceChipCursor = currentChipIdx(); true }
                                n > 0 -> { sourcePanelIdx = (sourcePanelIdx - 1 + n) % n; true }
                                else -> true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            when {
                                sourceChipCursor >= 0 -> { sourceChipCursor = -1; sourcePanelIdx = 0; true }
                                chips.isNotEmpty() && n > 0 && sourcePanelIdx == n - 1 -> { sourceChipCursor = currentChipIdx(); true }
                                n > 0 -> { sourcePanelIdx = (sourcePanelIdx + 1) % n; true }
                                else -> true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (sourceChipCursor >= 0) { chips.getOrNull(sourceChipCursor)?.onClick?.invoke(); true }
                            else { panelActions.getOrNull(sourcePanelIdx)?.let { it.onRun(); if (!it.keepOpen) sourcePanelOpen = false }; true }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (sourceChipCursor >= 0) {
                                val step = if (kc == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                                sourceChipCursor = (sourceChipCursor + step + chips.size) % chips.size
                            } else if (panelKind == "episodes" && sourcePanelIdx == 0 && numSeasons > 1) {
                                val step = if (kc == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                                selectPanelSeason((panelSeason + step - 1 + numSeasons) % numSeasons + 1)
                            } else if (kc == KeyEvent.KEYCODE_DPAD_LEFT) sourcePanelOpen = false
                            true
                        }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { sourcePanelOpen = false; true }
                        else -> true
                    }
                }
                // Fonte ainda sendo resolvida: só CIMA abre o painel de fontes e BACK volta.
                if (streamUrl == null) {
                    return@onKeyEvent when (kc) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (sourceActions.size > 1) { panelKind = "sources"; sourcePanelIdx = 0; sourcePanelOpen = true; true } else false
                        }
                        else -> false
                    }
                }
                // Barra de chips focada (D-pad): ←→ navega, OK executa, ↑/Back sai.
                if (chipFocused) {
                    val n = tvChips.size
                    return@onKeyEvent when (kc) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { if (n > 0) chipIdx = (chipIdx - 1 + n) % n; controlsVisible = true; true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { if (n > 0) chipIdx = (chipIdx + 1) % n; controlsVisible = true; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { tvChips.getOrNull(chipIdx)?.onRun?.invoke(); controlsVisible = true; true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { chipFocused = false; true }
                        else -> true
                    }
                }
                when (kc) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { togglePlay(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(10_000); true }
                    // CIMA abre o painel de Fontes + Episódios (D-pad navegável).
                    KeyEvent.KEYCODE_DPAD_UP -> { if (sourceActions.size > 1) { panelKind = "sources"; sourcePanelIdx = 0; sourcePanelOpen = true }; true }
                    // BAIXO foca a barra de chips (Zoom/Velocidade/Legendas/Áudio/Fontes).
                    KeyEvent.KEYCODE_DPAD_DOWN -> { controlsVisible = true; if (tvChips.isNotEmpty()) { chipFocused = true; chipIdx = 0 }; true }
                    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> { playNextEpisode(); true }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> { playPrevEpisode(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onBack(); true }
                    else -> { controlsVisible = true; false }
                }
            }
            .clickable(interactionSource = tapInteraction, indication = null) {
                // Lote 24 item 10: qualquer toque é interação (mesmo travado).
                noteUserInteraction()
                if (locked) lockHintVisible = !lockHintVisible
                else controlsVisible = !controlsVisible
            }
            // Gestos (celular): metade direita = volume, metade esquerda = brilho. (off se travado)
            .then(
                if (streamUrl != null && !locked) Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { noteUserInteraction() },
                        onDragEnd = { gestureHud = null },
                        onVerticalDrag = { change, dragAmount ->
                            val frac = -dragAmount / size.height.toFloat()
                            if (change.position.x > size.width / 2f) changeVolume(frac)
                            else changeBrightness(frac)
                            change.consume()
                        },
                    )
                } else Modifier
            )
            // Gesto de deslizar p/ buscar ±10 s (item 5): horizontal sobre o vídeo, um
            // passo a cada 8% da largura arrastada. Detector separado do vertical
            // (cada um só dispara depois do limiar na sua direção, como os dois
            // detectores já separados da barra de progresso). Off se travado ou com
            // o tap-seek desligado; o toque p/ mostrar/esconder controles não muda.
            .then(
                if (streamUrl != null && !locked && swipeSeekEnabled) Modifier.pointerInput(Unit) {
                    var accum = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accum = 0f; noteUserInteraction() },
                        onDragEnd = { accum = 0f },
                        onDragCancel = { accum = 0f },
                    ) { _, dragAmount ->
                        accum += dragAmount
                        val step = size.width * 0.08f
                        while (accum >= step) { accum -= step; swipeSeekStep(true) }
                        while (accum <= -step) { accum += step; swipeSeekStep(false) }
                    }
                } else Modifier
            ),
    ) {
        when {
            // ── Player custom (ExoPlayer) ──
            streamUrl != null -> {
                // Ambilight (Lote 24 item 8): brilho atrás do vídeo, com as
                // cores amostradas. O fundo do PlayerView fica transparente
                // enquanto ligado, p/ o brilho aparecer nas barras do letterbox.
                if (ambilightState.enabled && ambilightColors.isNotEmpty()) {
                    AmbilightGlow(
                        colors = ambilightColors,
                        intensity = ambilightState.intensity,
                        sensoryOn = sensoryOn,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // controles custom (Compose)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    },
                    update = {
                        setPlayerViewRef(it)
                        applyFrameRate()
                        // Lote 24 item 7: o modo de aspecto decide o resizeMode
                        // (Original devolve o do modo de tela legado). Troca
                        // imediata, sem reiniciar o vídeo.
                        it.resizeMode = resolveAspectResizeMode(aspectMode, effectiveResizeMode(it.width, it.height))
                        // Zoom legado (índice 3) amplia 33% — só vale no modo
                        // Original, senão os dois zooms somariam.
                        val legacyZoom = aspectMode == AspectMode.ORIGINAL && screenModeIdx == 3
                        it.scaleX = if (legacyZoom) 1.33f else 1f
                        it.scaleY = if (legacyZoom) 1.33f else 1f
                        // Bands: reserva a faixa inferior p/ a legenda não cobrir o vídeo.
                        val bandPx = if (aspectMode == AspectMode.BANDS) aspectSubtitleBandPx(it.context) else 0
                        if (it.paddingBottom != bandPx) it.setPadding(0, 0, 0, bandPx)
                        // Ambilight: fundo transparente p/ o brilho aparecer nas barras.
                        it.setBackgroundColor(if (ambilightState.enabled) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
                        // Legenda embutida segue o tamanho e o fundo escolhidos.
                        it.subtitleView?.apply {
                            setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subSize)
                            // Sem os estilos embutidos, o tamanho e o fundo escolhidos valem
                            // também para a legenda embutida (antes só valiam para a externa).
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            // Estilo da legenda (v1.9.0): cor, fundo com opacidade,
                            // contorno e fonte — ver buildCaptionStyle em SubtitleStyle.kt.
                            setStyle(buildCaptionStyle(subColor, subBg, subBgOpacity, subOutline, subFont))
                        }
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )

                // Legenda externa desenhada aqui, com o atraso escolhido.
                if (selectedSubUrl != null && externalCues.isNotEmpty()) {
                    ExternalSubtitle(externalCues, exoPlayer, (subDelay * 1000).toLong(), subSize, subBg, isTv, controlsVisible || syncBarOpen, subColor, subFont, subBgOpacity)
                }
                // Segunda legenda no topo (Lote 16 item 3): só vale com arquivo
                // externo (o ExoPlayer renderiza 1 faixa embutida por vez).
                if (secondarySubUrl != null && secondaryCues.isNotEmpty()) {
                    SecondarySubtitle(secondaryCues, exoPlayer, secondaryDelayMs(), subSize, subBg, isTv, controlsVisible || syncBarOpen, subColor, subFont, subBgOpacity)
                }

                // Transmitindo p/ Chromecast: a mídia roda na TV; celular vira controle.
                if (casting) CastingOverlay(title)

                // Watch Party (Lote 19, v1.29.0): botão + painel da sala,
                // só com sessão ativa (o overlay some sozinho sem sessão).
                PartyPlayerOverlay(
                    viewModel = partyVm,
                    modifier = Modifier.align(Alignment.TopEnd),
                )

                // Pular trechos (v1.8.1): aparece quando há um segmento ativo na posição
                // atual (resumo/abertura/encerramento/pós-créditos dos serviços, ou o
                // marcador de abertura aprendido como fallback). Fica na tela mesmo com os
                // controles escondidos (tipo Netflix).
                val skipTarget = activeSkipTarget()
                AnimatedVisibility(
                    visible = skipTarget != null && !inPip && !locked,
                    enter = sensoryEnterTransition(sensoryOn), exit = sensoryExitTransition(sensoryOn),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    SkipIntroButton(raised = controlsVisible, label = skipTarget?.label ?: "Pular abertura") {
                        skipTarget?.let { t ->
                            // Lote 24 item 10: pular trecho à mão é interação.
                            noteUserInteraction()
                            if (t.goNext) {
                                if (hasNextEpisode) playNextEpisode() else goToNextSeason()
                            } else {
                                currentPlayer().seekTo(t.targetMs)
                                position = t.targetMs
                                if (t.learnedMark) introSkippedEp = curEpisode
                                // Lote 16 item 2: pulo manual da ABERTURA de série
                                // conta p/ o auto-skip aprendido (3 pulos = oferta opt-in).
                                if (t.label == "Pular abertura") noteManualIntroSkip()
                            }
                        }
                    }
                }

                // Aviso curto no rodapé (ex "Abertura marcada").
                if (toastMsg.isNotBlank()) PlayerToast(toastMsg) { toastMsg = "" }

                // HUD de gesto: volume / brilho
                gestureHud?.let { (label, frac) -> GestureHud(label, frac) }

                // HUD do gesto de busca ("−10s" / "+10s")
                seekHud?.let { SeekHud(it) }

                // TELA TRAVADA: destravar e voltar ficam SEMPRE visíveis (o bloqueio
                // nunca pode esconder a saída do player nem o desbloqueio).
                if (locked) {
                    Row(
                        Modifier.align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                            .padding(top = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CtlButton(Icons.Default.LockOpen, "Destravar a tela", 48.dp, onClick = {
                            locked = false; lockHintVisible = false; controlsVisible = true
                        })
                        CtlButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", 48.dp, onClick = onBack)
                    }
                }

                // TELA TRAVADA: cadeado central aparece ao tocar; clicar destrava.
                if (locked && lockHintVisible) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CtlButton(Icons.Default.Lock, "Destravar", 60.dp, big = true, onClick = {
                            locked = false; lockHintVisible = false; controlsVisible = true
                        })
                    }
                }

                val p2pText = rememberP2pStatus(if (playVia == "p2p") streamUrl else null, buffering || switching)
                val p2pStatus = debridStatus ?: p2pText
                if (buffering || switching) {
                    PlayerLoadingOverlay(
                        bannerUrl = metaBg.ifBlank { backdropUrl }, logoUrl = logoUrl, posterUrl = posterUrl, title = title,
                        episodeLine = if (type == "SERIES" && curEpisode > 0) "T$curSeason · E$curEpisode" else null,
                        status = p2pStatus,
                    )
                }

                // Som de outra fonte carregando. A imagem espera parada.
                if (extHeld && !buffering && !switching) ExtAudioLoading()

                // AUTO-PLAY: card "próximo episódio em Ns" (canto inferior-direito).
                // Lote 23 item 11: no último minuto o card é manual
                // (autoAdvance = false → "Assistir agora" sem contagem); a
                // contagem só aparece depois do fim real (nextCountdownArmed).
                if (showNextPrompt && !inPip && !locked) {
                    NextEpisodeCard(
                        isSeason = promptIsSeason,
                        line = if (promptIsSeason) "Temporada ${curSeason + 1}" else "T$curSeason · E${curEpisode + 1}",
                        countdown = nextCountdown,
                        autoAdvance = autoNextEnabled && nextCountdownArmed,
                        onPlay = { noteUserInteraction(); if (promptIsSeason) goToNextSeason() else playNextEpisode() },
                        onCancel = { noteUserInteraction(); nextCountdownArmed = false; showNextPrompt = false },
                    )
                }

                // "Ainda assistindo?" (Lote 24 item 10): 3 avanços automáticos
                // sem interação pausaram o player — o diálogo decide o destino.
                if (stillWatchingAsk && !inPip && !locked) {
                    StillWatchingDialog(
                        count = STILL_WATCHING_THRESHOLD,
                        onContinue = {
                            stillWatchingAsk = false
                            stillWatching.reset()
                            if (stillWatchingPendingSeason) goToNextSeason() else playNextEpisode()
                        },
                        onStop = {
                            stillWatchingAsk = false
                            stillWatching.reset()
                            onBack()
                        },
                    )
                }

                AnimatedVisibility(controlsVisible && !inPip && !syncBarOpen, enter = sensoryEnterTransition(sensoryOn), exit = sensoryExitTransition(sensoryOn)) {
                    CustomPlayerControls(
                        title = displayTitle,
                        clock = clockLine(System.currentTimeMillis(), position, duration, speeds[speedIdx]),
                        // Info da fonte no player: ADDON · fonte · qualidade detectada.
                        subtitle = remember(currentSourceIdx, currentSourceLabel, currentSources, title) {
                            val src = currentSources.getOrNull(currentSourceIdx)
                            if (src != null) {
                                val info = releaseInfo(src.stream ?: com.aotv.app.data.remote.StremioStream(src.label, src.url, src.addon), title)
                                listOf(src.addon, info.server).filter { it.isNotBlank() }.distinct().joinToString("  ·  ")
                            } else
                            currentSourceLabel
                        },
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        onToggle = ::togglePlay,
                        onSeekBack = { seekBy(-10_000) },
                        onSeekFwd = { seekBy(10_000) },
                        onSeekTo = { ms ->
                            val from = position
                            currentPlayer().seekTo(ms); position = ms; controlsVisible = true
                            // Pulo pra frente no começo do episódio = abertura. Grava e reusa depois.
                            if (type == "SERIES" && progressVm.learnIntro(tmdbId, from, ms)) {
                                introMark = from to ms
                                introSkippedEp = curEpisode
                                toastMsg = "Abertura marcada. Nos próximos episódios aparece o botão Pular abertura."
                            }
                        },
                        videoUrl = streamUrl ?: activeUrl,
                        // Barra de chips por D-pad: realçada na TV OU sempre que estiver focada
                        // (controle em device comum). Só volta pros chips de toque fora do modo chip.
                        isTv = isTv,
                        tvChips = tvChips,
                        tvChipIdx = if (chipFocused) chipIdx else -1,
                        resolution = videoQualityLabel,
                        formatTags = currentSources.getOrNull(currentSourceIdx)?.let { source ->
                            releaseInfo(source.stream ?: com.aotv.app.data.remote.StremioStream(source.label, source.url, source.addon), title).tags
                        }.orEmpty(),
                        playbackInfo = listOf(
                            if (extUrl != null) "Som de outra fonte" else audioGroups.indexOfFirst { it.isSelected }
                                .takeIf { it >= 0 }?.let { audioTrackLabel(audioGroups[it], it) }
                                .orEmpty().takeUnless { it.equals("UND", true) }.orEmpty(),
                            screenModes[screenModeIdx].first, playbackSpeedText(speeds[speedIdx]),
                        ).filter { it.isNotBlank() }.joinToString("  ·  "),
                        buffered = currentPlayer().bufferedPosition,
                        tapSeek = tapSeekEnabled,
                        panelBlur = panelBlurOn,
                        // Lote 16 item 5: divisões da barra (intro/recap/créditos da IntroDB).
                        markers = progressMarkers,
                    )
                }

                // Topo-direito: círculos pequenos (Transmitir · Girar · Travar · Voltar) — estilo print.
                AnimatedVisibility(
                    controlsVisible && !inPip, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    PlayerTopActions(
                        isTv = isTv,
                        visibleActions = orderTopActions(playerTopActions),
                        // Mini player (Fase C): só no celular e com a pref ligada. Minimizar
                        // guarda a sessão e volta p/ a navegação (o áudio continua na barra).
                        onMinimize = if (miniEnabled && !isTv) ({ MiniPlayerState.minimize(s, routePlayId); onBack() }) else null,
                        onRotate = {
                            val cur = activity?.requestedOrientation
                            activity?.requestedOrientation =
                                if (cur == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                                else
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onLock = { locked = true; controlsVisible = false; sourcePanelOpen = false; syncBarOpen = false; lockHintVisible = false },
                        onBack = onBack,
                    )
                }
            }

            // ── Fonte ainda sendo resolvida nos addons ──
            else -> {
                CircularProgressIndicator(color = Accent, strokeWidth = 4.dp, modifier = Modifier.align(Alignment.Center))

                AnimatedVisibility(controlsVisible && !inPip, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CtlButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", 48.dp, onClick = onBack)
                        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Todas as fontes falharam: aviso central (o efeito acima volta ao detalhe em ~2.5s,
        // exceto no play Kodi — v1.2.7 — que fica na tela p/ o Davi ler o diagnóstico).
        // v1.13.0 (JOTV 1.0.19 §3.7): na falha da troca automática, o overlay
        // oferece "Tentar novamente" (mesma fonte) e "Escolher outra fonte"
        // (abre o painel de fontes, sem redesenhá-lo). "Tentar novamente" só
        // aparece quando há uma fonte atual para retentar; "Escolher outra
        // fonte" só quando há mais de uma fonte. Sem nenhuma das duas, o
        // overlay fica só com "Voltar" e a volta automática de 2,5 s continua.
        if (sourceFailed) SourceFailedOverlay(
            diag = kodiDiag + proxyDiagLine(), playerError = playerError, onBack = onBack,
            onRetry = if (currentSources.getOrNull(currentSourceIdx) != null) ({ retryCurrentSource() }) else null,
            onChooseSource = if (currentSources.size > 1) ({ sourceFailed = false; openDpadPanel("sources") }) else null,
        )

        // Barra de sincronia do som (Lote 14 item 3): renderiza também p/ o áudio
        // próprio, sem som de outra fonte — aí ajusta o atraso do PCM (só atrasa).
        if (syncBarOpen && !inPip && !locked) {
            SyncBar(
                delaySec = audioDelay,
                onShift = {
                    audioDelay = if (extUrl != null) addDelay(audioDelay, it)
                    else addDelay(audioDelay, it).coerceIn(OWN_AUDIO_DELAY_MIN_SEC, OWN_AUDIO_DELAY_MAX_SEC)
                },
                onBack5 = { seekBy(-5_000); controlsVisible = false },
                onZero = { audioDelay = 0.0 },
                onClose = { syncBarOpen = false; if (extUrl != null) saveExt() else saveOwnAudio() },
                ownAudio = extUrl == null,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ── Seletor de FONTE (dentro do player) — troca mantendo o tempo ──
        // Travado, o painel não abre (o onLock já o fecha; a guarda cobre o caso
        // de ele estar aberto por outro caminho quando a tela trava).
        // O painel de INFOS TÉCNICAS tem composable próprio ([TechInfoPanel]),
        // como o de COR ([ColorFilterPanel]) — não cabe na lista de ações.
        if (sourcePanelOpen && !locked && panelKind == "eq") {
            EqualizerPanel(
                enabled = eqEnabled,
                preset = eqPreset,
                bands = eqBands,
                equalizer = equalizer,
                onToggle = { onEqToggle(it) },
                onPreset = { onEqSave(it, eqBandsCsv) },
                onBand = { i, db -> onEqBand(i, db) },
                onReset = { onEqReset() },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "info") {
            // Stats do torrent ao vivo (Lote 5 item 2): enquanto o painel de
            // infos está aberto, re-lê seeds/peers/velocidade/buffer a cada
            // 2 s. Fora do painel não há polling — o status só existe p/ mostrar.
            LaunchedEffect(streamUrl, playVia) {
                while (true) { refreshTorrentStats(); delay(2_000) }
            }
            TechInfoPanel(
                info = techInfo,
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
                torrent = torrentStats,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "color") {
            ColorFilterPanel(
                state = colorState,
                focusIdx = colorFocusIdx,
                onStateChange = ::applyColorState,
                onSliderFinished = { applyColorNow() },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "night") {
            NightModePanel(
                state = nightModeState,
                drcEngine = nightController.drcEngine,
                drcSupported = nightController.drcSupported,
                clearSupported = nightController.clearSupported,
                onNightToggle = ::setNightOn,
                onClearToggle = ::setClearDialogue,
                onIntensity = ::setNightIntensity,
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "aspect") {
            // Lote 24 item 7: seletor de aspecto (Original/Zoom/Bands, por sessão).
            AspectModePanel(
                mode = aspectMode,
                onModeChange = { aspectMode = it },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "ambilight") {
            // Lote 24 item 8: Ambilight (toggle + intensidade, por perfil).
            AmbilightPanel(
                state = ambilightState,
                sensoryOn = sensoryOn,
                onToggle = { setAmbilightEnabled(it) },
                onIntensity = { setAmbilightIntensity(it) },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "sensory") {
            // Lote 24 item 9: modo sensorial (toggle único, por perfil).
            SensoryFriendlyPanel(
                enabled = sensoryOn,
                onToggle = { setSensoryOn(it) },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked && panelKind == "xray") {
            // X-Ray (Lote 21, v1.31.0): corte, elenco, história, curva de
            // intensidade (da legenda ativa) e marcadores de pular trechos.
            XRayPanel(
                title = title,
                loading = xrayLoading,
                cast = xrayCast,
                director = xrayDirector,
                summary = xraySummary,
                releaseLabel = currentSourceLabel,
                fileDurationMs = duration,
                tmdbRuntimeMin = runtimeMin,
                buckets = intensityBuckets(externalCues, duration),
                hasSubtitles = selectedSubUrl != null && externalCues.isNotEmpty(),
                markers = listOfNotNull(
                    skipSegments.recap?.let { XRayMarker("Recapitulação", it.start * 1_000, it.end * 1_000) },
                    skipSegments.intro?.let { XRayMarker("Abertura", it.start * 1_000, it.end * 1_000) },
                    skipSegments.outro?.let { XRayMarker("Encerramento", it.start * 1_000, it.end * 1_000) },
                    skipSegments.postCredits?.let { XRayMarker("Pós-créditos", it.start * 1_000, it.end * 1_000) },
                ),
                positionMs = position,
                onSeek = { ms -> currentPlayer().seekTo(ms); position = ms },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
            )
        } else if (sourcePanelOpen && !locked) {
            PlayerActionPanel(
                title = panelTitle,
                showCount = panelKind == "sources",
                actions = panelActions,
                focusIdx = sourcePanelIdx,
                pinned = pinnedSource,
                movieTitle = title,
                onPick = { act -> act.onRun(); if (!act.keepOpen) sourcePanelOpen = false },
                onDismiss = { sourcePanelOpen = false },
                television = isTv,
                // Lote 4: painel de fontes (e demais painéis) em vidro fosco.
                frosted = panelBlurOn,
                subtitleMode = panelKind == "subs",
                // Lote 15 item 2 + Lote 23 item 13 — chips de filtro no topo
                // da folha de Fontes (Todos + provider + qualidade). Na TV a
                // fileira entra na navegação D-pad (ver sourceChipCursor e o
                // onKeyEvent do painel): ↑ na 1ª linha entra nos chips.
                filterChips = if (panelKind == "sources") sourceFilterChips else emptyList(),
                chipCursor = sourceChipCursor,
            )
        }

        // Baixar trecho (Lote 18 item 10 — CLIPES, v1.28.0): diálogo de
        // início/fim. Não é painel de ação (não entra no panelKind): abre por
        // cima do player, travado não abre.
        if (showClipDialog && !locked) {
            ClipDownloadDialog(
                title = title,
                positionMs = position,
                durationMs = duration,
                onDismiss = { showClipDialog = false },
                onDownload = { startMs, endMs -> startClipDownload(startMs, endMs) },
            )
        }
    }
}

/** Indicador do gesto de busca ("−10s" / "+10s"), no centro da tela. Some sozinho em 800 ms. */
@Composable
private fun BoxScope.SeekHud(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 24.dp, vertical = 12.dp))
    }
}

/**
 * Estado e regra de negócio de uma sessão do player. Antes tudo isso morava dentro do
 * composable e cada lambda capturava dezenas de estados, o que estourava o tamanho do método.
 */
@OptIn(UnstableApi::class)
class PlayerSession(
    val context: android.content.Context,
    val scope: kotlinx.coroutines.CoroutineScope,
    val progressVm: PlayerProgressViewModel,
    val episodeVm: EpisodePlayerViewModel,
    val embedUrl: String,
    val title: String,
    val tmdbId: Int,
    val type: String,
    val posterUrl: String,
    val lastEpisode: String,
    val startPositionMs: Long,
    val backdropUrl: String,
    val season: Int,
    val episode: Int,
    val episodeTotal: Int,
    val sources: List<MoviePlayerSource>,
    forceTv: Boolean = false,
    /** Diagnóstico do play Kodi (resolveu= + http:) — v1.2.7. */
    val kodiDiag: String = "",
    /** Decoder de vídeo (pref `pref_player_decoder`, Lote 4 — v1.12.0).
     *  Só vale na criação do player: a sessão cria o ExoPlayer 1x. */
    val decoderMode: DecoderMode = DecoderMode.HW,
    /** Perfil de buffer do ExoPlayer (pref `pref_player_buffer`, Lote 5 item 1 — v1.14.0).
     *  Como o decoder, só vale na criação do player: o LoadControl entra no Builder. */
    val bufferProfile: BufferProfile = BufferProfile.NORMAL,
    /** Fonte travada ao abrir (guarda de duração confirmada, v1.21.0). */
    val startLocked: Boolean = false,
) {
    /** Volta para a tela anterior. Atualizado a cada composição. */
    var onBack: () -> Unit = {}

    // A navegação define o modo de entrada também para os controles do player.
    var isTv by mutableStateOf(forceTv)
    // Fonte ATIVA (mutável) — trocada pelo seletor dentro do player.
    // Tudo toca no ExoPlayer. streamUrl null quer dizer que a fonte ainda está sendo resolvida
    // (episódio aberto sem URL, o player busca nos addons).
    var activeUrl by run { mutableStateOf(embedUrl) }
    var streamUrl by run { mutableStateOf(embedUrl.ifBlank { null }) }
    // Abre LIMPO (sem controles) — mostra logo + animação de carregamento; controles só ao mexer.
    var controlsVisible by run { mutableStateOf(false) }
    // Velocidade de reprodução (chip "1x") — presets 0,5x a 2x (PLAYBACK_SPEEDS),
    // salva no perfil e aplicada ao abrir.
    val speeds get() = PLAYBACK_SPEEDS
    var speedIdx by run { mutableIntStateOf(PLAYBACK_SPEEDS.indexOf(1f)) }
    var didSeek by run { mutableStateOf(false) }
    // Posição a retomar (continuar OU ao trocar de fonte mantém o tempo)
    var pendingSeekMs by run { mutableLongStateOf(startPositionMs) }
    // Seletor de fonte — lista MUTÁVEL (recarregada a cada troca de episódio).
    private var panelOpenState by mutableStateOf(false)
    var sourcePanelOpen: Boolean
        get() = panelOpenState
        set(v) { if (v != panelOpenState) { sourcePanelIdx = 0; sourceChipCursor = -1 }; panelOpenState = v }
    var sourcePanelIdx by mutableIntStateOf(0) // foco D-pad no painel, zera ao abrir ou fechar
    /** Cursor D-pad na fileira de chips de filtro do painel de fontes
     * (Lote 23 item 13): -1 = foco na lista; >= 0 = índice do chip. */
    var sourceChipCursor by mutableIntStateOf(-1)
    // Barra de chips (Zoom/Velocidade/Legendas/Áudio/Fontes) navegável por D-pad na TV.
    var chipFocused by run { mutableStateOf(false) }
    var chipIdx by run { mutableIntStateOf(0) }
    // Tipo do painel D-pad aberto: "sources" | "subs" | "audio".
    var panelKind by run { mutableStateOf("sources") }
    var panelSeason by mutableIntStateOf(season.coerceAtLeast(1))
    var panelEpisodes by mutableStateOf<List<com.aotv.app.domain.model.EpisodeItem>>(emptyList())
    var panelEpisodesLoading by mutableStateOf(false)
    private var episodePanelJob: kotlinx.coroutines.Job? = null
    private var sourceLimit by mutableIntStateOf(30)
    var currentSources by run { mutableStateOf(sources) }
    var currentSourceLabel by run { mutableStateOf(sources.firstOrNull { it.url == embedUrl }?.label ?: "Fonte") }
    // Preferência de fonte que ATRAVESSA episódio e temporada.
    // O label muda a cada episódio (tamanho do arquivo, servidor sorteado), então casar por
    // label falhava sempre e o player caía na fonte 0 — era por isso que a fonte escolhida
    // ou fixada não acompanhava o próximo episódio. A identidade estável é addon + qualidade.
    var preferredAddon by run {
        mutableStateOf(sources.firstOrNull { it.url == embedUrl }?.addon.orEmpty())
    }
    var preferredQuality by run {
        mutableIntStateOf(sourceQualityRank(sources.firstOrNull { it.url == embedUrl }?.label.orEmpty()))
    }
    // Índice da fonte ativa (identidade estável — labels/urls podem repetir).
    var currentSourceIdx by run { mutableIntStateOf(sources.indexOfFirst { it.url == embedUrl }.coerceAtLeast(0)) }
    // Fonte escolhida À MÃO pelo usuário → NÃO auto-troca (mesmo lenta). Ele que decide.
    // startLocked (v1.21.0): a guarda de duração já foi confirmada no detalhe.
    var userLockedSource by run { mutableStateOf(startLocked) }
    // FIXAR FONTE (escolha explícita do usuário no chip). Diferente do userLockedSource, que
    // é implícito e cai a cada episódio novo. Fixado, o auto-fallback NUNCA troca de fonte,
    // nem por lentidão, nem por erro, e a trava atravessa a troca de episódio/temporada.
    var pinnedSource by run { mutableStateOf(false) }
    // Resolução real em reprodução (ex "1080p") — exibida junto do addon/fonte no player.
    var videoQualityLabel by run { mutableStateOf("") }
    // Episódio + TEMPORADA atuais (séries) — mudam ao avançar ep / próxima temporada.
    var curEpisode by run { mutableIntStateOf(episode) }
    var curSeason by run { mutableIntStateOf(season) }
    // Total de episódios (pode chegar 0 no "Continuar") — resolve pelo TMDB p/ liberar o "próximo".
    var epTotal by run { mutableIntStateOf(episodeTotal) }
    // Nº de temporadas (p/ oferecer "Próxima Temporada" no fim da última). 0 = desconhecido.
    var numSeasons by run { mutableIntStateOf(0) }
    val hasNextEpisode get() = type == "SERIES" && curEpisode in 1 until epTotal
    // Tem PRÓXIMA TEMPORADA? (fim do último episódio da temporada e existe temporada seguinte).
    val hasNextSeason get() = type == "SERIES" && numSeasons > 0 && curSeason in 1 until numSeasons
    // Auto-play próximo episódio: prompt aparece no ÚLTIMO MINUTO, MANUAL
    // (sem contagem); a contagem só arma no fim real do vídeo (Lote 23 item 11).
    var nextPromptEp by run { mutableIntStateOf(-1) }
    var showNextPrompt by run { mutableStateOf(false) }
    var nextCountdown by run { mutableIntStateOf(10) }
    // Lote 23 item 11 (JOTV 1.0.22 §4.3): true = a contagem vale — só depois
    // do fim real do vídeo. No último minuto o cartão é manual (false) e o
    // episódio seguinte NÃO começa sozinho no meio dos créditos.
    var nextCountdownArmed by run { mutableStateOf(false) }
    // true = o card de aviso é de PRÓXIMA TEMPORADA (não próximo episódio).
    var promptIsSeason by run { mutableStateOf(false) }
    // true quando o ExoPlayer chega ao fim (STATE_ENDED) — dispara próximo ep. ou fecha o player.
    var playbackEnded by run { mutableStateOf(false) }
    // ABERTURA: (inicioMs, fimMs) aprendidos da série. Não há API pública de intro fora do
    // AniSkip (só anime), então o marcador vem do pulo manual do usuário no 1º episódio.
    var introMark by run {
        mutableStateOf(if (type == "SERIES") progressVm.introMarker(tmdbId) else null)
    }
    var introSkippedEp by run { mutableIntStateOf(-1) } // já pulou nesse episódio
    // Pular trechos (v1.8.1): segmentos IntroDB/SkipDB/Anime-Skip do título atual.
    var skipSegments by run { mutableStateOf(com.aotv.app.data.remote.SkipSegments()) }
    // X-Ray (Lote 21, v1.31.0): elenco/diretor + resumo da Wikipédia do título.
    // Carregado ao abrir o painel (1x por título/episódio); marcadores e curva
    // de intensidade vêm do estado que o player já tem (skipSegments/externalCues).
    var xrayLoading by run { mutableStateOf(false) }
    var xrayCast by run { mutableStateOf<List<com.aotv.app.domain.model.CastMember>>(emptyList()) }
    var xrayDirector by run { mutableStateOf<String?>(null) }
    var xraySummary by run { mutableStateOf<String?>(null) }
    val skipSegmentsService = com.aotv.app.data.remote.SkipSegmentsService()
    /** Última posição vista pelo poll (p/ detectar entrada natural no segmento). */
    private var lastSkipCheckPos by run { mutableLongStateOf(0L) }
    /** Episódio em que o auto-skip já pulou + alvo (1x por segmento por episódio). */
    private var autoSkippedEp by run { mutableIntStateOf(-1) }
    private var autoSkippedTarget by run { mutableLongStateOf(-1L) }
    // Troca automática de fonte (v1.8.1): toggle + tempo do watchdog (segundos).
    var autoSwitchEnabled by run { mutableStateOf(true) }
    var sourceTimeoutSec by run { mutableIntStateOf(12) }
    // Lote 19 item 7 (v1.29.0) — Opções de Reprodução do JOTV: subir a
    // qualidade sozinho (e) e debrid só com arquivo pronto (f). A tela
    // entrega as prefs aqui (ver MoviePlayerScreen, junto ao autoSwitch).
    var autoUpgradeEnabled by run { mutableStateOf(true) }
    var debridReadyOnly by run { mutableStateOf(true) }
    /** Client ID do Anime-Skip digitado pelo usuário ("" = desligado). */
    var animeSkipClientId by run { mutableStateOf("") }
    /** Alvo do botão de pular trechos: rótulo, alvo em ms, ir p/ o próximo episódio. */
    data class SkipTarget(val label: String, val targetMs: Long, val goNext: Boolean = false, val learnedMark: Boolean = false)
    /**
     * Trecho pulável ativo na posição atual, ou null. Ordem: resumo, abertura,
     * encerramento (vira "Ir para pós-créditos" quando há pós-créditos),
     * pós-créditos (vai p/ o próximo episódio, se houver). O marcador de
     * abertura aprendido continua como fallback quando não há segmento de
     * abertura dos serviços. Filmes também têm segmentos — sem restrição a séries.
     */
    fun activeSkipTarget(): SkipTarget? {
        val pos = position
        val segs = skipSegments
        fun inside(s: com.aotv.app.data.remote.SkipSegment?): Boolean =
            s != null && pos >= s.start - 2_000L && pos < s.end - 1_000L
        if (inside(segs.recap)) return SkipTarget("Pular resumo", segs.recap!!.end)
        if (inside(segs.intro)) return SkipTarget("Pular abertura", segs.intro!!.end)
        if (inside(segs.outro)) {
            val pc = segs.postCredits
            return if (pc != null) SkipTarget("Ir para pós-créditos", pc.start)
            else SkipTarget("Pular encerramento", segs.outro!!.end)
        }
        if (inside(segs.postCredits)) {
            return SkipTarget("Pular pós-créditos", segs.postCredits!!.end, goNext = hasNextEpisode || hasNextSeason)
        }
        val mark = introMark
        if (type == "SERIES" && mark != null && introSkippedEp != curEpisode &&
            pos >= mark.first - 2_000L && pos < mark.second - 1_000L) {
            return SkipTarget("Pular abertura", mark.second, learnedMark = true)
        }
        return null
    }
    /**
     * Pular abertura/encerramento automático (Lote 9 item 2): com o toggle
     * ligado, pula sozinho ao ENTRAR no segmento de intro ou de
     * encerramento durante a reprodução normal. Só dispara quando a posição
     * avançou andando (menos de 2 s desde o último poll): pulo manual p/
     * dentro da abertura não dispara, e voltar a abertura à mão não é
     * desfeito. 1x por segmento por episódio. O encerramento vai p/ o início
     * da cena pós-créditos quando ela existe (mesma regra do botão manual).
     */
    fun maybeAutoSkip() {
        if (!autoSkipEnabled || !isPlaying) return
        val pos = position
        val prev = lastSkipCheckPos
        lastSkipCheckPos = pos
        if (pos - prev !in 0..2_000) return
        val segs = skipSegments
        val intro = segs.intro
        val outro = segs.outro
        val target: Long
        val label: String
        when {
            intro != null && prev <= intro.start && pos >= intro.start && pos < intro.end -> {
                target = intro.end; label = "Abertura pulada automaticamente"
            }
            outro != null && prev <= outro.start && pos >= outro.start && pos < outro.end -> {
                target = segs.postCredits?.start ?: outro.end; label = "Encerramento pulado automaticamente"
            }
            else -> return
        }
        if (autoSkippedEp == curEpisode && autoSkippedTarget == target) return
        autoSkippedEp = curEpisode
        autoSkippedTarget = target
        currentPlayer().seekTo(target)
        position = target
        toastMsg = label
    }
    var toastMsg by run { mutableStateOf("") }          // aviso curto (ex "Abertura marcada")
    // LOTE 18 item 10 — CLIPES (v1.28.0): diálogo "Baixar trecho" + download do
    // trecho HLS. O downloader resolve o DAO por EntryPoint (a sessão é criada
    // à mão, fora do Hilt) — ver ClipDownloader.
    var showClipDialog by run { mutableStateOf(false) }
    private val clipDownloader by lazy { com.aotv.app.data.clips.ClipDownloader(context) }

    /** Toque no chip "Baixar trecho": HLS abre o seletor; o resto avisa o motivo, sem falhar em silêncio. */
    fun onClipChipTap() {
        val url = streamUrl
        if (url.isNullOrBlank()) {
            toastMsg = "A fonte ainda está carregando."
            return
        }
        if (duration <= 0) {
            toastMsg = "Ao vivo não tem trecho para baixar."
            return
        }
        when (val eligibility = com.aotv.app.data.clips.ClipDownload.classifyClipSource(url)) {
            com.aotv.app.data.clips.ClipDownload.ClipEligibility.Hls -> showClipDialog = true
            is com.aotv.app.data.clips.ClipDownload.ClipEligibility.Unsupported -> toastMsg = eligibility.reason
        }
    }

    /** Baixa o trecho escolhido (HLS): fecha o diálogo, roda em IO e avisa o resultado por toast. O clipe entra nos Downloads como DownloadEntity (ver ClipDownloader). */
    fun startClipDownload(startMs: Long, endMs: Long) {
        val url = streamUrl ?: return
        showClipDialog = false
        scope.launch {
            val result = clipDownloader.downloadClip(tmdbId, type, title, posterUrl, lastEpisode, url, startMs, endMs)
            toastMsg = when (result) {
                is com.aotv.app.data.clips.ClipResult.Done ->
                    "Trecho salvo em Downloads: ${result.displayTitle}"
                is com.aotv.app.data.clips.ClipResult.Failed ->
                    "Não deu para baixar o trecho: ${result.message}"
                is com.aotv.app.data.clips.ClipResult.Unsupported -> result.reason
                com.aotv.app.data.clips.ClipResult.BlockedWifi ->
                    "“Só Wi-Fi” ligado: conecte no Wi-Fi para baixar o trecho."
            }
        }
    }
    // Trocando de episódio/temporada: mostra o loading NA HORA, sem ficar preso na cena anterior.
    var switching by run { mutableStateOf(false) }
    // Torrent tocando por onde. "" ainda decidindo, "debrid" link direto do serviço, "p2p" motor do aparelho.
    var playVia by run { mutableStateOf("") }
    // Texto do carregando enquanto o debrid prepara o link.
    var debridStatus by run { mutableStateOf<String?>(null) }
    // Episódio cujo SUCESSOR já foi pré-carregado (1x por episódio).
    var prefetchedEp by run { mutableIntStateOf(-1) }
    // Auto-fallback de FONTE (filme): fonte direta com erro OU que não inicia → tenta a próxima.
    var movieStarted by run { mutableStateOf(false) }
    var movieErrored by run { mutableStateOf(false) }
    // Erro do ExoPlayer na fonte atual (código + mensagem + causa raiz) — v1.2.7: vai p/
    // a tela de erro junto com o diag do Kodi, p/ distinguir TLS de 404 de timeout.
    // Nulo quando a falha foi por timeout (sem exceção) — aí o noteTimeout() registra.
    var playerError by run { mutableStateOf<String?>(null) }
    /** Marca falha por timeout (stream não iniciou/travou) sem sobrescrever um erro real do player. */
    fun noteTimeout(msg: String = "Timeout: o stream não iniciou em ${sourceTimeoutSec}s (sem erro do player)") {
        if (playerError == null) playerError = msg
    }
    /** v1.2.8: linha do proxy p/ o diag da tela de erro ("" se o proxy
     *  nunca buscou — o diag original já cobre o resto). */
    fun proxyDiagLine(): String = com.aotv.app.data.kodi.KodiLocalProxy.lastDiag()
    // Nenhuma fonte tocável (todas falharam/mortas) → overlay + volta ao detalhe (não trava no preto).
    var sourceFailed by run { mutableStateOf(false) }
    // URL que chegou em STATE_READY por último. Dispara a checagem de duração e a subida de qualidade.
    var readyUrl by run { mutableStateOf<String?>(null) }
    // Fontes já tentadas, addons que falharam e fontes que o player viu serem de outro filme.
    // Zeram a cada episódio novo.
    val triedUrls = run { mutableSetOf<String>() }
    val failedAddons = run { mutableSetOf<String>() }
    val wrongUrls = run { mutableSetOf<String>() }
    // A troca automática para qualidade melhor acontece uma vez por vídeo.
    var upgradeDone by run { mutableStateOf(false) }
    var runtimeMin by run { mutableIntStateOf(0) }
    // Título com episódio (séries): "Nome · T1 E3" — mostrado no player.
    val displayTitle get() = if (type == "SERIES" && curEpisode > 0) "$title · T$curSeason E$curEpisode" else title

    // Logo + background (metahub, por IMDB) — tela de loading mostra o banner + a LOGO no lugar do poster.
    var logoUrl by run { mutableStateOf("") }
    var metaBg by run { mutableStateOf("") }
    // Estado de reprodução p/ o player custom
    var isPlaying by run { mutableStateOf(true) }
    var buffering by run { mutableStateOf(false) }
    var duration by run { mutableLongStateOf(0L) }
    var position by run { mutableLongStateOf(0L) }
    // Faixas de áudio/legenda do vídeo atual
    var audioGroups by run { mutableStateOf<List<androidx.media3.common.Tracks.Group>>(emptyList()) }
    var textGroups by run { mutableStateOf<List<androidx.media3.common.Tracks.Group>>(emptyList()) }
    // Faixa de legenda do vídeo ligada agora. Quem decide se a legenda abre ligada é subWanted.
    var subtitleOn by run { mutableStateOf(false) }
    // Preferência do perfil (Ajustes, Legenda ao abrir), lembrada entre episódios e sessões, e o
    // idioma em que a legenda liga sozinha. subPicked marca escolha à mão no vídeo atual.
    var subWanted by run { mutableStateOf(progressVm.subtitleOn) }
    val subLang = progressVm.subLang
    var subPicked by run { mutableStateOf(false) }
    // Idioma preferido de ÁUDIO do perfil (Lote 4 item 7, v1.12.0 — pref
    // `pref_player_audio_lang`, código SubLangs). A tela entrega aqui a cada
    // composição. [audioPicked] marca escolha À MÃO no vídeo atual: ela vale
    // e o automático não troca no meio da reprodução; zera a cada episódio
    // novo, como o subPicked.
    var audioLangPref by run { mutableStateOf(SubLangs.DEFAULT) }
    var audioPicked by run { mutableStateOf(false) }
    // Infos técnicas do vídeo (Lote 4 item 4, v1.12.0): dados do stream atual
    // p/ o painel "Informações de reprodução". Refeito a cada troca de faixa,
    // de resolução e quando o vídeo fica pronto — e ao abrir o painel.
    var techInfo by run { mutableStateOf<TechInfo?>(null) }
    /** Re-lê os dados do stream atual p/ o painel de infos técnicas. Não trava o player. */
    fun refreshTechInfo() {
        techInfo = runCatching { collectTechInfo(exoPlayer, context, casting) }.getOrNull()
            ?.copy(displayFps = displayFpsLine())
    }
    // Stats do torrent P2P (Lote 5 item 2, v1.14.0): seeds, conexões,
    // velocidade e buffer do ExoPlayer p/ a seção TORRENT do painel de
    // infos técnicas. Re-lido a cada 2 s enquanto o painel está aberto
    // (ver o LaunchedEffect junto do TechInfoPanel). Null = fonte atual
    // não é P2P, P2P resolvido pelo debrid ou motor sem o torrent — aí a
    // seção some do painel, sem inventar valor.
    var torrentStats by run { mutableStateOf<TorrentStats?>(null) }
    /** Re-lê os stats do torrent da fonte atual. Não trava o player. */
    fun refreshTorrentStats() {
        val u = streamUrl
        torrentStats = if (u != null && TorrentMagnet.isMagnet(u) && playVia != "debrid") {
            val st = runCatching { Torrents.ifStarted()?.status(u) }.getOrNull()
            val buffered = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
            st?.let { TorrentStats(seeds = it.seeds, peers = it.peers, downloadBps = it.downloadBps, bufferedMs = buffered) }
        } else {
            null
        }
    }
    // Legendas externas (OpenSubtitles e addons). Ligam sozinhas quando são a melhor no idioma.
    var externalSubs by run { mutableStateOf<List<com.aotv.app.data.remote.ExternalSub>>(emptyList()) }
    var selectedSubUrl by run { mutableStateOf<String?>(null) }
    // Falas da legenda externa escolhida. O player desenha a fala por cima do vídeo, o que deixa
    // atrasar ou adiantar sem recarregar nada.
    var externalCues by run { mutableStateOf<List<SubCue>>(emptyList()) }
    // Atraso da legenda em segundos, só para o vídeo atual. Tamanho e fundo ficam salvos.
    var subDelay by run { mutableDoubleStateOf(0.0) }
    var subSize by run { mutableFloatStateOf(progressVm.subSize) }
    var subBg by run { mutableStateOf(progressVm.subBg) }
    // Personalização de legenda (v1.9.0): cor, opacidade do fundo, contorno e fonte.
    // Como tamanho e fundo, ficam salvos no perfil e valem para a próxima sessão.
    var subColor by run { mutableIntStateOf(progressVm.subColor) }
    var subBgOpacity by run { mutableFloatStateOf(progressVm.subBgOpacity) }
    var subOutline by run { mutableStateOf(progressVm.subOutline) }
    var subFont by run { mutableStateOf(progressVm.subFont) }

    // Som de outra fonte. [extSync] segue o player principal, [audioDelay] em segundos.
    var extSync by run { mutableStateOf<ExtAudioSync?>(null) }
    var extUrl by run { mutableStateOf<String?>(null) }
    var extHeld by run { mutableStateOf(false) }
    // Sincronia do áudio próprio (Lote 14 item 3, v1.24.0): [audioDelay] é o
    // valor exibido nos dois casos — com som de outra fonte ele é o atraso
    // do som externo, sem ele é o atraso do áudio próprio (salvo por
    // título, como o ExtAudioPick; 0 = sincronia original). O processador
    // que atrasa o PCM vive na sessão p/ o player ser criado com ele.
    val ownAudioDelayProcessor = AudioDelayProcessor()
    /** Atraso do vídeo em ms (Lote 17 item 10): adiantar o som = atrasar o vídeo. */
    val videoDelayState = VideoDelayState()
    var audioDelay by run { mutableDoubleStateOf(progressVm.ownAudioDelay(extKey)?.delaySec ?: 0.0) }
    var syncBarOpen by run { mutableStateOf(false) }
    var extRestored by run { mutableStateOf(false) }
    val extKey get() = "$type:$tmdbId"
    // Transmitindo p/ Chromecast (mídia roda na TV; celular vira controle)
    var casting by run { mutableStateOf(false) }
    // Overlay de gesto (volume/brilho) — null = escondido
    var gestureHud by run { mutableStateOf<Pair<String, Float>?>(null) }
    // Modos de tela (resizeMode do PlayerView). O índice 0 é o "Automático"
    // (Lote 12 — Frente 2, v1.22.0, padrão): compara a proporção do vídeo
    // com a da tela e escolhe FIT ou ZOOM (ver [effectiveResizeMode]) — o
    // resizeMode guardado aqui p/ ele é só o fallback FIT. Os outros são os
    // de antes, deslocados de 1: 1 Ajustar, 2 Preencher, 3 Zoom (+33%),
    // 4 Esticar. O modo é persistido por perfil (pref
    // `pref_player_screen_mode`) e a tela abre nele — antes zerava a cada
    // reprodução. [onScreenModeChange] salva a cada troca.
    val screenModes = run {
        listOf(
            "Automático" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Preencher" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            "Esticar" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
    }
    var screenModeIdx by run { mutableIntStateOf(0) }
    /** Salva o modo de tela no perfil (ligado pela tela a [PlayerPrefsViewModel.setScreenMode]). */
    var onScreenModeChange: (Int) -> Unit = {}
    /** Proporção real do vídeo (de onVideoSizeChanged): o Automático compara com a da tela. 0 = ainda sem medir. */
    var videoWidth by run { mutableIntStateOf(0) }
    var videoHeight by run { mutableIntStateOf(0) }

    /**
     * Modo de tela efetivo p/ o PlayerView (Lote 12 — Frente 2).
     * Automático (idx 0): proporções próximas (até 10% de diferença) →
     * ZOOM, preenche a tela cortando no máximo ~10% das bordas; proporções
     * distantes → FIT, mostra o vídeo inteiro com barras. Sem medida do
     * vídeo ou da tela, FIT (nunca corta no escuro).
     */
    fun effectiveResizeMode(viewW: Int, viewH: Int): Int {
        if (screenModeIdx != 0) return screenModes[screenModeIdx].second
        if (videoWidth <= 0 || videoHeight <= 0 || viewW <= 0 || viewH <= 0) {
            return AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val videoAR = videoWidth.toFloat() / videoHeight
        val screenAR = viewW.toFloat() / viewH
        val ratio = videoAR / screenAR
        return if (ratio in 0.9f..1.1f) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    // Tela travada (ignora toques até destravar)
    var locked by run { mutableStateOf(false) }
    var lockHintVisible by run { mutableStateOf(false) }

    // ── Lote 24 — Frente B (v1.34.0) ─────────────────────────────────────
    // Item 7: modo de aspecto da sessão (Original/Zoom/Bands). Só sessão:
    // default Original, sem pref persistente — ver PlayerAspectModes.kt.
    var aspectMode by run { mutableStateOf(AspectMode.ORIGINAL) }
    // Item 8: Ambilight (estado por perfil, ver PlayerAmbilight.kt) + cores
    // amostradas (vazias = sem brilho até a 1ª amostra).
    var ambilightState by run { mutableStateOf(AmbilightState()) }
    var ambilightColors by run { mutableStateOf<List<Color>>(emptyList()) }
    /** Amostrador do Ambilight (loop 1,5 s; ver PlayerAmbilight.kt). */
    val ambilightSampler = AmbilightSampler()
    // Item 9: modo sensorial (pref por perfil, ver SensoryFriendly.kt).
    var sensoryOn by run { mutableStateOf(false) }
    // Item 10: "Ainda assistindo?" — contador de avanços automáticos sem
    // interação (ver StillWatching.kt). O diálogo e o destino pendente vivem aqui.
    val stillWatching = StillWatchingTracker()
    var stillWatchingAsk by run { mutableStateOf(false) }
    var stillWatchingPendingSeason by run { mutableStateOf(false) }
    /** Qualquer interação do usuário zera o contador do "Ainda assistindo?". */
    fun noteUserInteraction() { stillWatching.noteUserInteraction() }
    /** Salva o estado do Ambilight no perfil (ligado pela tela). */
    var onAmbilightSave: (AmbilightState) -> Unit = {}
    /** Salva o modo sensorial no perfil (ligado pela tela). */
    var onSensorySave: (Boolean) -> Unit = {}
    fun setAmbilightEnabled(v: Boolean) { ambilightState = ambilightState.copy(enabled = v); onAmbilightSave(ambilightState) }
    fun setAmbilightIntensity(v: AmbilightIntensity) { ambilightState = ambilightState.copy(intensity = v); onAmbilightSave(ambilightState) }
    fun setSensoryOn(v: Boolean) { sensoryOn = v; onSensorySave(v); applyNightMode() }

    // ── Prefs do player (Fase C) ─────────────────────────────────────────
    // A tela lê os fluxos do PlayerPrefsViewModel e entrega aqui (ver
    // MoviePlayerScreen). São mutableState para a UI reagir na hora.
    /** Ordem dos botões do player (pref `playerButtons`). Vazio = padrão. */
    var playerButtonOrder by run { mutableStateOf<List<String>>(emptyList()) }
    /** Botões do topo (pref `playerTopActions`). Vazio = padrão. */
    var playerTopActions by run { mutableStateOf<List<String>>(emptyList()) }
    /** Tocar na barra busca (pref `playerTapSeek`). */
    var tapSeekEnabled by run { mutableStateOf(true) }
    /** Notas dos episódios no painel (pref `pref_detail_episode_ratings`, Lote 2 item 5). */
    var showEpisodeRatings by run { mutableStateOf(true) }
    /** Blur/vidro fosco nos painéis do player (pref `pref_panel_blur`, Lote 4 correção A).
     * Campo da sessão porque a PlayerScreenContent é outra função e não enxerga o
     * `val` local da MoviePlayerScreen (mesmo padrão do showEpisodeRatings, Lote 2). */
    var panelBlurOn by run { mutableStateOf(true) }
    /** Deslizar na horizontal sobre o vídeo busca ±10 s (item 5). Por enquanto segue
     * o toggle tap-seek (pref `playerTapSeek`); a pref dedicada
     * `pref_player_swipe_seek` (a ser criada pelo coordenador — ver relatório
     * v1.9.0 itens 4/5) desacopla os dois quando existir. */
    var swipeSeekEnabled by run { mutableStateOf(true) }
    /** Indicador do gesto de busca ("−10s" / "+10s") — null = escondido. */
    var seekHud by run { mutableStateOf<String?>(null) }
    /** Volume Boost ligado (pref `playerVolBoost`). */
    var volBoostEnabled by run { mutableStateOf(false) }
    /** Nível do boost como multiplicador 1,0–2,0 (pref `playerVolBoostLevel`). */
    var volBoostLevel by run { mutableFloatStateOf(1.5f) }
    /** Mini player ligado (pref `playerMini`): mostra o botão de minimizar. */
    var miniEnabled by run { mutableStateOf(true) }
    /** "A seguir" automático ligado (pref `playerAutoNext`). */
    var autoNextEnabled by run { mutableStateOf(true) }
    /** Contagem do "A seguir" em segundos (pref `playerAutoNextDelay`). */
    var autoNextDelaySec by run { mutableIntStateOf(10) }
    // ── Lote 9 — Player parte 3 (v1.19.0) ───────────────────────────────
    /** Sincronização automática da legenda ligada (pref `pref_player_autosync`). */
    var autoSyncEnabled by run { mutableStateOf(false) }
    /** Pular abertura/encerramento automático ligado (pref `pref_player_auto_skip`). */
    var autoSkipEnabled by run { mutableStateOf(false) }
    /** Cartão "A seguir" no fim do episódio (pref `pref_player_up_next`). */
    var upNextEnabled by run { mutableStateOf(true) }
    /** Equalizador ligado (pref `pref_player_equalizer`). */
    var eqEnabled by run { mutableStateOf(false) }
    /** Preset do equalizador (pref `pref_player_eq_preset`; "flat" = tudo em 0 dB). */
    var eqPreset by run { mutableStateOf(EQ_PRESET_FLAT) }
    /** Bandas do equalizador em CSV (pref `pref_player_eq_bands`). */
    var eqBandsCsv by run { mutableStateOf("") }
    /** A tela liga ao PlayerPrefsViewModel (salva preset e bandas do equalizador). */
    var onEqSave: (String, String) -> Unit = { _, _ -> }
    /** A tela liga ao PlayerPrefsViewModel (liga/desliga o equalizador). */
    var onEqToggle: (Boolean) -> Unit = {}
    // ── Lote 12 — Frente 2 (Player e fontes, v1.22.0) ─────────────────────
    /** Barra de botões expandida nesta sessão (recolhida = 5 primeiros + ">"). Por sessão, sem pref. */
    var chipsExpanded by run { mutableStateOf(false) }
    /** Folha de fontes em ranking (melhor → pior) nesta sessão; espelha a pref `pref_player_sources_ranked`. */
    var sourcesRanked by run { mutableStateOf(false) }
    // ── Lote 14 item 4 — folha de fontes estável durante a busca (v1.24.0) ──
    /** Ordem congelada da folha (URLs na ordem em que apareceram). Novas entram no fim. */
    var sourceSheetOrder by run { mutableStateOf<List<String>>(emptyList()) }
    /** Chave do título/episódio da ordem congelada; trocar de episódio zera a ordem. */
    var sourceSheetOrderKey by run { mutableStateOf("") }
    /** Busca assentada: sem fonte nova há 1,5 s (ver o efeito em PlayerSourceEffects). */
    var sourcesSettled by run { mutableStateOf(false) }
    /** Ranking fotografado no assentamento (URLs, melhor → pior); null = ainda não assentou. */
    var sourceSettledOrder by run { mutableStateOf<List<String>?>(null) }
    /** URLs que o usuário tocou/focou na folha — nunca mudam de lugar sozinhas. */
    var sourceTouchedUrls by run { mutableStateOf<Set<String>>(emptySet()) }
    /**
     * Filtro de provider da folha de fontes (Lote 15 item 2 — v1.25.0, estilo Nuvio):
     * "" = Todos; senão o nome do addon. Por sessão; trocar de episódio zera (ver
     * [noteSourcesArrived]). O filtro só ESCONDE — a ordem continua a congelada.
     */
    var sourceProviderFilter by run { mutableStateOf("") }
    /** Filtro de qualidade da folha de fontes (Lote 23 item 13, JOTV 1.0.22
     * §4.5): "" = Todas; senão "4K", "1080p", "720p" ou "480p". Combina com o
     * filtro de provider — os dois só ESCONDEM, a ordem continua a congelada. */
    var sourceQualityFilter by run { mutableStateOf("") }
    /** Salva a vista da folha no perfil (ligado pela tela a [PlayerPrefsViewModel.setSourcesRanked]). */
    var onSourcesRankedChange: (Boolean) -> Unit = {}
    // ── Lote 17 — saúde aprendida + downgrade adaptativo (v1.27.0) ───────
    /** Prefs do perfil (leitura direta, mesmo padrão de LiveTvScreen). */
    private val prefs by lazy { com.aotv.app.data.local.PlatformPrefs(context.applicationContext) }
    /** Qualidade máxima do perfil (leitura pública p/ o auto-upgrade em PlayerSourceEffects, que está fora da classe — o `prefs` é privado). */
    val maxQuality: Int get() = prefs.maxQuality
    /** URLs que já ganharam um acerto nesta sessão (1x cada). */
    private val healthRecordedUrls = mutableSetOf<String>()
    /** Oferta "Mudar para 720p?" ativa (fonte 1080p parou 3x em 1 minuto). */
    var showDowngradeOffer by run { mutableStateOf(false) }

    /** Tocou de verdade: o host ganha um acerto (1x por URL nesta sessão). */
    fun noteSourceReady(url: String) {
        if (url.isBlank() || !healthRecordedUrls.add(url)) return
        val ms = episodeVm.probeCached(url)?.ms ?: 0
        prefs.sourceHealth = com.aotv.app.data.remote.recordSourceSuccess(
            prefs.sourceHealth, url, ms, System.currentTimeMillis(),
        )
    }

    /** Falhou (não respondeu / erro): o host ganha uma falha. */
    fun noteSourceFailed(url: String) {
        if (url.isBlank()) return
        prefs.sourceHealth = com.aotv.app.data.remote.recordSourceFailure(
            prefs.sourceHealth, url, System.currentTimeMillis(),
        )
    }

    /** Travou no meio da reprodução: conta a travada e avalia o downgrade. */
    fun noteSourceStall(url: String) {
        if (url.isBlank()) return
        val now = System.currentTimeMillis()
        val health = com.aotv.app.data.remote.recordSourceStall(prefs.sourceHealth, url, now)
        prefs.sourceHealth = health
        val rank = facts().firstOrNull { it.url == url }?.rank ?: 0
        if (rank >= 1080 && com.aotv.app.data.remote.shouldOfferDowngrade(health[com.aotv.app.data.remote.sourceHost(url)], now)) {
            showDowngradeOffer = true
        }
    }

    fun dismissDowngradeOffer() { showDowngradeOffer = false }

    /**
     * "Tentar a próxima fonte" (Lote 17 item 1): a próxima do ranking ainda
     * não tentada — retoma DE ONDE PAROU, porque [applyMovieSource] mantém
     * a posição. false = acabou a lista.
     */
    fun tryNextSource(): Boolean {
        val f = facts()
        val rankedOrder = rankedSourceOrder()
        val next = rankedOrder.firstOrNull { idx ->
            val s = f.getOrNull(idx) ?: return@firstOrNull false
            s.url !in triedUrls && !s.dead && !s.wrongTitle
        } ?: rankedOrder.firstOrNull { idx -> (f.getOrNull(idx)?.url ?: "") !in triedUrls }
        if (next == null) return false
        applyMovieSource(next)
        return true
    }

    /** Downgrade adaptativo: a melhor fonte até 720p ainda não tentada. */
    fun switchToLowerQuality(maxRank: Int = 720): Boolean {
        val f = facts()
        val next = rankedSourceOrder().firstOrNull { idx ->
            val s = f.getOrNull(idx) ?: return@firstOrNull false
            s.rank in 1..maxRank && s.url !in triedUrls && !s.dead && !s.wrongTitle
        } ?: return false
        showDowngradeOffer = false
        applyMovieSource(next)
        return true
    }
    /** Repo da TV ao vivo p/ o On Demand da folha (ligado pela tela; null = sem repo). */
    var onDemandRepo: LiveTvRepository? = null
    /** Fonte Xtream usada no On Demand da folha (a conta já configurada). */
    var onDemandSource: LiveSource? = null
    var onDemandLoading by run { mutableStateOf(false) }
    /** Resultados do On Demand com este título (filmes + séries), na ordem da API. */
    var onDemandItems by run { mutableStateOf<List<OnDemandHit>>(emptyList()) }
    /** Erro REAL da busca no On Demand (null = sem erro ou sem conta). */
    var onDemandError: String? by run { mutableStateOf<String?>(null) }
    /** Nome da conta Xtream do On Demand (""). Sem conta, a seção mostra o vazio honesto. */
    var onDemandSourceName by run { mutableStateOf("") }
    /** Título p/ o qual o On Demand já foi buscado ("tipo:título") — não busca 2x. */
    var onDemandLoadedFor by run { mutableStateOf("") }
    /** true entre minimizar e expandir/fechar: o dispose da tela NÃO libera o player. */
    var minimizedByUser by run { mutableStateOf(false) }
    /** playId da rota que abriu esta sessão (p/ o mini player reabrir a mesma). */
    var routePlayId by run { mutableIntStateOf(-1) }
    /** O aparelho não aceitou o LoudnessEnhancer (aviso 1x, ver [applyVolBoost]). */
    var volBoostUnavailable by run { mutableStateOf(false) }

    // ── Correção de cor do vídeo (v1.9.0, Lote 1 item 1) ────────────────
    // Vale SÓ p/ o vídeo (decisão do Davi): brilho/contraste/saturação/
    // matiz + presets + correção p/ daltonismo, por perfil (prefs
    // `pref_player_color_*` — contrato no relatório da v1.9.0).
    // O painel do player edita aqui e salva por [onColorFilterSave].
    var colorBrightness by run { mutableFloatStateOf(0f) } // -1..1
    var colorContrast by run { mutableFloatStateOf(1f) } // 0..2
    var colorSaturation by run { mutableFloatStateOf(1f) } // 0..2
    var colorHue by run { mutableFloatStateOf(0f) } // -180..180°
    var colorPreset by run { mutableStateOf(ColorPreset.PADRAO.id) }
    var colorDaltonism by run { mutableStateOf(DaltonismMode.OFF) }
    /** Foco D-pad no painel de cor (0..12: 4 sliders, Redefinir, 4 presets, 4 modos). */
    var colorFocusIdx by run { mutableIntStateOf(0) }
    /** Estado atual do filtro, p/ construir os efeitos e p/ o painel. */
    val colorState get() = ColorFilterState(colorBrightness, colorContrast, colorSaturation, colorHue, colorPreset, colorDaltonism)
    /** O painel chama p/ salvar (a tela liga ao PlayerPrefsViewModel). */
    var onColorFilterSave: (ColorFilterState) -> Unit = {}
    // ── Modo privado (Lote 2, item 7 — v1.10.0) ────────────────────────────
    // O chip "Privado" liga/desliga aqui; o bloqueio em si mora nos ViewModels
    // (PlayerProgressViewModel.save/markWatched/scrobble e
    // MovieDetailViewModel.recordPlayback/markEpisodeWatched) — a sessão só
    // espelha o estado p/ o chip e avisa quem salva a pref do perfil.
    var privateMode by run { mutableStateOf(false) }
    /** A tela liga ao PlayerPrefsViewModel (salva a pref do perfil). */
    var onPrivateModeToggle: (Boolean) -> Unit = {}
    /** Liga/desliga o modo privado e avisa com um toast. */
    fun togglePrivateMode() {
        privateMode = !privateMode
        onPrivateModeToggle(privateMode)
        toastMsg = if (privateMode) "Modo privado ligado — nada será salvo no histórico"
        else "Modo privado desligado — o progresso volta a ser salvo"
    }
    /** Aplica um estado novo na sessão e salva (painel e D-pad usam este). */
    fun applyColorState(ns: ColorFilterState) {
        colorBrightness = ns.brightness; colorContrast = ns.contrast
        colorSaturation = ns.saturation; colorHue = ns.hueDegrees
        colorPreset = ns.preset; colorDaltonism = ns.daltonism
        onColorFilterSave(ns)
    }
    /** (Re)aplica os efeitos no ExoPlayer sem reiniciar o vídeo. */
    fun applyColorEffects() {
        runCatching { exoPlayer.setVideoEffects(buildColorEffects(colorState)) }
    }

    // ── Debounce dos sliders de cor (Lote 16 item 8 — fix do Lote 1) ────────
    // Antes cada tick do slider chamava setVideoEffects e travava o vídeo;
    // agora o slider espera 200 ms parado e preset/daltonismo aplicam na hora.
    private val colorDebouncer = ColorFilterDebouncer(scope) { applyColorEffects() }
    fun onColorStateChanged(newState: ColorFilterState, oldState: ColorFilterState) =
        colorDebouncer.onStateChanged(newState, oldState)
    fun applyColorNow() = colorDebouncer.applyNow()

    // ── Modo noturno + Clear Dialogue (Lote 16 item 1) ─────────────────────
    // Controller criado com as prefs do app; o estado é carregado por título
    // na tela (LaunchedEffect) e a sessão só aplica no ExoPlayer. O app não
    // tem passthrough hoje (a flag nasce desligada; ver NightModeAudio.kt).
    val nightController: NightModeController by lazy {
        NightModeController(com.aotv.app.data.local.PlatformPrefs(context))
    }
    var nightModeState by run { mutableStateOf(NightModeState()) }
    var nightModeTitleKey by run { mutableStateOf("") }
    /** (Re)aplica o modo noturno/Clear Dialogue na sessão de áudio atual.
     * Com o sensorial ligado, o DRC é forçado (ver [sensoryEffectiveNightState]). */
    fun applyNightMode() {
        val id = runCatching { exoPlayer.audioSessionId }.getOrDefault(0)
        val ch = runCatching { exoPlayer.audioFormat?.channelCount }.getOrNull() ?: 2
        nightController.apply(id, ch, sensoryEffectiveNightState(nightModeState, sensoryOn))
    }
    /** Salva o estado do título atual (chamado ao trocar um toggle). */
    fun saveNightMode() {
        saveNightModeState(com.aotv.app.data.local.PlatformPrefs(context), nightModeTitleKey, nightModeState)
    }
    fun setNightOn(v: Boolean) { nightModeState = nightModeState.copy(nightOn = v); applyNightMode(); saveNightMode() }
    fun setClearDialogue(v: Boolean) { nightModeState = nightModeState.copy(clearDialogueOn = v); applyNightMode(); saveNightMode() }
    fun setNightIntensity(v: Int) { nightModeState = nightModeState.copy(intensity = v); applyNightMode(); saveNightMode() }

    // ── Frame-rate matching (Lote 16 item 4) ───────────────────────────────
    // A taxa do stream vai p/ a Surface do PlayerView (só troca sem corte)
    // e p/ a janela; o medidor é da TELA (Choreographer), não do vídeo.
    val fpsMeter = FpsMeter()
    private var playerViewRef: PlayerView? = null
    private var lastAppliedFps: Float = 0f
    /** Sincronizar a taxa de atualização com o fps do stream (pref, ligada por padrão). */
    var frameRateEnabled by run { mutableStateOf(true) }
    /** Guarda a Surface do player (o update do AndroidView chama a cada recomposição). */
    fun setPlayerViewRef(pv: PlayerView?) {
        if (pv !== playerViewRef) { playerViewRef = pv; lastAppliedFps = 0f }
    }
    fun applyFrameRate() {
        if (!frameRateEnabled) return
        val fps = suggestedFrameRate(exoPlayer) ?: return
        if (fps == lastAppliedFps) return
        lastAppliedFps = fps
        playerViewRef?.let { applyFrameRateToPlayerView(it, fps) }
        runCatching { applyWindowRefreshRate(activity, fps) }
    }
    /** DV perfil 7 sem suporte no aparelho: tenta a faixa HDR10 (1x por fonte). */
    private var dvFallbackTriedFor: String? = null
    fun maybeHdr10Fallback() {
        val url = streamUrl ?: return
        if (dvFallbackTriedFor == url) return
        dvFallbackTriedFor = url
        if (dvProfile7NeedsHdr10Fallback(exoPlayer)) {
            if (applyHdr10Fallback(exoPlayer)) toastMsg = "Dolby Vision sem suporte neste aparelho — usando HDR10"
        }
    }
    /** Linha do medidor p/ as infos técnicas (null = sem dado ainda). */
    fun displayFpsLine(): String? = fpsDisplayLine(
        runCatching { exoPlayer.videoFormat?.frameRate }.getOrNull(),
        fpsMeter.currentFps,
    )

    // ── Pular trechos via IntroDB filtrada (Lote 16 item 2) ─────────────────
    // Segmentos filtrados por confiança do título atual (o modelo antigo
    // `skipSegments` continua alimentando o botão e o auto-skip de sempre).
    var introDbSegments by run { mutableStateOf<com.aotv.app.data.remote.IntroDbSegments?>(null) }
    private val introDbService = com.aotv.app.data.remote.IntroDbService()
    /** Marcadores da barra de progresso (intro + créditos + capítulos — sem capítulos ainda). */
    val progressMarkers: List<ProgressMarker>
        get() = introDbSegments?.let { buildProgressMarkers(it, emptyList(), duration) } ?: emptyList()
    /** Aviso de pós-créditos mostrado 1x por episódio (evita repetir o toast a cada poll). */
    private var postCreditsNoticedEp by run { mutableIntStateOf(-1) }
    fun maybePostCreditsNotice() {
        val segs = introDbSegments ?: return
        if (type != "SERIES" || postCreditsNoticedEp == curEpisode) return
        postCreditsNotice(segs, position)?.let {
            toastMsg = it
            postCreditsNoticedEp = curEpisode
        }
    }
    /** Busca a IntroDB filtrada (fonte principal, 1x por título) e guarda o resultado. */
    suspend fun loadIntroDb(imdb: String, isSeries: Boolean, season: Int, episode: Int): com.aotv.app.data.remote.IntroDbSegments? {
        postCreditsNoticedEp = -1
        val segs = runCatching { introDbService.get(imdb, isSeries, season, episode) }.getOrNull()
        introDbSegments = segs
        return segs
    }
    /** Mapeia os segmentos filtrados p/ o modelo antigo (botão + auto-skip). */
    fun introDbToSkipSegments(segs: com.aotv.app.data.remote.IntroDbSegments): com.aotv.app.data.remote.SkipSegments =
        com.aotv.app.data.remote.SkipSegments(
            recap = segs.recap?.let { com.aotv.app.data.remote.SkipSegment(it.startMs, it.endMs) },
            intro = segs.intro?.let { com.aotv.app.data.remote.SkipSegment(it.startMs, it.endMs) },
            outro = segs.outro?.let { com.aotv.app.data.remote.SkipSegment(it.startMs, it.endMs) },
            postCredits = segs.postCredits?.let { com.aotv.app.data.remote.SkipSegment(it.startMs, it.endMs) },
        )
    /** Pulo manual de abertura de série (p/ o auto-skip aprendido, 3 pulos = oferta opt-in). */
    fun noteManualIntroSkip() {
        if (type != "SERIES") return
        val store = LearnedAutoSkipStore.fromContext(context)
        store.noteManualSkip(learnedSkipKey(type, tmdbId))
        if (store.shouldOfferAutoSkip(learnedSkipKey(type, tmdbId))) {
            toastMsg = "Você pula a abertura sempre — quer pular automático? (Configurações → Player)"
        }
    }

    // ── Legendas nível 2 (Lote 16 item 3) ────────────────────────────────────
    /** 2ª legenda (topo): URL externa + falas + faixa. Null = sem 2ª legenda. */
    var secondarySubUrl by run { mutableStateOf<String?>(null) }
    var secondaryCues by run { mutableStateOf<List<SubCue>>(emptyList()) }
    var dualSubState by run { mutableStateOf(DualSubState()) }
    /** Chave do título p/ lembrar faixa exata e delay por faixa. */
    val subTitleKey: String get() = subTrackTitleKey(type, tmdbId)
    /** Delay (ms) da 2ª legenda, persistido por faixa. */
    fun secondaryDelayMs(): Long {
        val track = dualSubState.secondary ?: return 0L
        return delayFor(dualSubState, track)
    }

    // ── Sleep timer inteligente (Lote 16 item 6) ─────────────────────────────
    /** Estado das legendas capturado ao pausar pelo timer (p/ reativar ao voltar). */
    var sleepWakeState: SleepWakeState? = null
    /** true = a pausa atual veio do sleep timer (o Voltar comum não restaura legenda). */
    var sleepPaused by run { mutableStateOf(false) }

    val exoPlayer = run {
        // Foco de áudio do sistema: pausa sozinho numa ligação ou quando outro app toca som, e
        // para ao tirar o fone (sem isso o filme seguia tocando mudo pro alto-falante).
        val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()
        try {
            ExoPlayer.Builder(context)
                .setRenderersFactory(decoderRenderersFactory(context, decoderMode, ownAudioDelayProcessor, videoDelayState))
                .setLoadControl(bufferLoadControl(bufferProfile))
                .setMediaSourceFactory(mediaSourceFactory(context))
                .setAudioAttributes(audioAttrs, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    playWhenReady = true
                    // Legenda: começa desligada e pickAutoSub liga a do idioma do perfil quando
                    // ele deixou a legenda ligada (subWanted).
                    // Áudio: o padrão aqui é o da pref nova (`pref_player_audio_lang`,
                    // "pob" → "pt"); a pref de verdade é aplicada logo depois da
                    // criação pelo LaunchedEffect (a tela entrega o valor aqui
                    // depois do remember) — ver applyPreferredAudioLanguage.
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitleOn)
                        .setPreferredAudioLanguage(SubLangs.of(SubLangs.DEFAULT)?.family ?: "pt")
                        .build()
                }
        } catch (_: Exception) {
            ExoPlayer.Builder(context).setRenderersFactory(decoderRenderersFactory(context, decoderMode, ownAudioDelayProcessor, videoDelayState))
                .setLoadControl(bufferLoadControl(bufferProfile))
                .setAudioAttributes(audioAttrs, true).setHandleAudioBecomingNoisy(true)
                .build().apply { playWhenReady = true }
        }
    }.also {
        // Sincronia do áudio próprio (Lote 14 item 3 + Lote 17 item 10): o atraso
        // salvo vale desde o 1º quadro, sem esperar o efeito debounced de 450 ms.
        ownAudioDelayProcessor.setDelayMs((audioDelay.coerceAtLeast(0.0) * 1000).roundToLong())
        videoDelayState.setDelayMs((-audioDelay.coerceAtMost(0.0) * 1000).roundToLong())
    }
    // Controle pela tela bloqueada, fone Bluetooth e Google Assistant. Só o player LOCAL —
    // o Cast tem a própria sessão separada via Google Cast framework.
    val mediaSession = androidx.media3.session.MediaSession.Builder(context, exoPlayer).build()

    // ── CAST: ao conectar uma sessão Chromecast, transfere a reprodução p/ a TV ──
    val castContext = run {
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(context) }.getOrNull()
    }
    val castPlayer = run { castContext?.let { androidx.media3.cast.CastPlayer(it) } }
    // Player ATIVO: quando transmitindo, os controles (play/pause/seek) e o progresso
    // operam no Chromecast (castPlayer), não no player local — senão o áudio sai dobrado
    // (celular + TV) e o app não controla a TV.
    fun currentPlayer(): androidx.media3.common.Player =
        if (casting && castPlayer != null) castPlayer else exoPlayer

    // ── Player externo (Lote 4 item 1 — v1.12.0) ──────────────────────────
    /** true depois que o \"Player externo\" automático já disparou 1x nesta sessão. */
    var externalOpened by run { mutableStateOf(false) }

    /** Escolha de player externo por título/tipo (Lote 22, item 6). */
    val externalPlayerStore: com.aotv.app.data.local.ExternalPlayerStore by lazy {
        com.aotv.app.data.local.ExternalPlayerStore(progressVm.platformPrefs)
    }

    /** Lançador do player externo, registrado pela tela (ActivityResultLauncher).
     * Sem ele, a abertura é fogo-e-esquece como antes (sem progresso ao voltar). */
    var externalLauncher: ((android.content.Intent, com.aotv.app.data.local.ExternalPlayerLaunch) -> Unit)? = null

    /** true enquanto a abertura externa aguarda o retorno (a tela NÃO volta
     * p/ o detalhe ainda — o retorno salva o progresso e volta depois). */
    var externalResultPending by run { mutableStateOf(false) }

    /**
     * Abre a fonte atual num player externo instalado (VLC, MX Player…).
     * Devolve true quando o player abriu. Mesma guarda do Cast:
     * torrent só sai do app pelo link do debrid (o motor P2P mora aqui);
     * transmitindo p/ TV, não abre fora (senão toca nos dois lugares).
     * A URL que vai é a fonte atual limpa do sufixo Kodi `|...` (headers
     * não viajam no intent — ver [openInExternalPlayer]).
     *
     * Lote 22 (v1.32.0, item 6): o pacote vem da escolha por título/tipo
     * ([ExternalPlayerStore.resolvePlayer]); sem escolha e com um só player
     * instalado, abre direto nele; sem escolha e com vários, mostra o
     * seletor. Com o lançador da tela, o retorno salva o progresso no
     * Continuar assistindo (posição do MX Player, ou tempo decorrido) e a
     * tela volta p/ o detalhe depois — sem o lançador, volta na hora como
     * antes (fogo-e-esquece, sem progresso).
     */
    fun openExternalPlayer(): Boolean {
        if (casting) {
            toastMsg = "Pare de transmitir antes de abrir num player externo."
            return false
        }
        val src = streamUrl ?: activeUrl
        val url = if (TorrentMagnet.isMagnet(src)) {
            src?.let { episodeVm.debridNow(it, curSeason, curEpisode) }
        } else {
            src
        }
        if (url.isNullOrBlank()) {
            toastMsg = "Fonte P2P sem link pronto. Escolha outra fonte ou espere o debrid."
            return false
        }
        val clean = splitKodiStreamHeaders(url).first
        val installed = externalPlayerStore.installedPlayers(context)
        // Resolução do pacote (Lote 22, item 6): por título, depois por
        // tipo; sem escolha, um só instalado abre direto; com vários, o
        // seletor aparece quando "Perguntar sempre" está ligado, senão
        // abre no primeiro instalado (lista ordenada por nome).
        val resolvedPackage = externalPlayerStore.resolvePlayer(tmdbId, type)
            ?: installed.takeIf { it.size == 1 }?.first()?.packageName
            ?: if (!progressVm.platformPrefs.externalPlayerAsk) installed.firstOrNull()?.packageName else null
        val baseIntent = externalPlayerIntent(context, clean, displayTitle, resolvedPackage)
        if (baseIntent == null) {
            toastMsg = "Esse link não abre num player externo."
            return false
        }
        val intent = if (resolvedPackage == null) {
            android.content.Intent.createChooser(baseIntent, "Abrir com")
        } else {
            baseIntent
        }
        val launcher = externalLauncher
        if (launcher != null) {
            val launch = com.aotv.app.data.local.ExternalPlayerLaunch(
                tmdbId = tmdbId, type = type, title = displayTitle, posterUrl = posterUrl,
                lastEpisode = lastEpisode, sourceUrl = clean,
                startPositionMs = exoPlayer.currentPosition,
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
            )
            return runCatching {
                externalResultPending = true
                launcher(intent, launch)
                true
            }.onFailure {
                externalResultPending = false
                toastMsg = "Não foi possível abrir no player externo."
            }.getOrDefault(false)
        }
        val err = openInExternalPlayer(context, clean, displayTitle, resolvedPackage)
        if (err != null) {
            toastMsg = err
            return false
        }
        return true
    }

    // Seleciona uma faixa de ÁUDIO específica (índice no audioGroups)
    fun selectAudio(index: Int) {
        // Escolha À MÃO (Lote 4 item 7): vale p/ o vídeo atual e o automático
        // não volta por cima dela no meio da reprodução.
        audioPicked = true
        val g = audioGroups.getOrNull(index) ?: return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
    }

    // Aplica o idioma de áudio preferido do perfil no ExoPlayer. O seletor de
    // faixas do Media3 casa o código ISO 639-1 ("pt"), então a pref (código
    // SubLangs, ex. "pob") entra pela família. Não mexe quando a pessoa já
    // escolheu a faixa à mão no vídeo atual (audioPicked).
    fun applyPreferredAudioLanguage() {
        if (audioPicked) return
        val lang = SubLangs.of(audioLangPref)?.family ?: "pt"
        runCatching {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(lang)
                .build()
        }
    }

    // Escolhe a faixa de áudio no idioma preferido quando as faixas chegam
    // (episódio/fonte novos). O setPreferredAudioLanguage nem sempre casa
    // código 639-2 ("por") com faixa 639-1 ("pt"); aqui o casamento é pelo
    // SubLangs.match (exato, depois família), igual ao pickAutoSub. Escolha
    // à mão no vídeo atual manda; sem faixa no idioma, fica a atual.
    fun pickAutoAudio() {
        if (audioPicked || audioGroups.isEmpty()) return
        val langs = audioGroups.map { it.getTrackFormat(0).language }
        val best = langs.indices.maxByOrNull { SubLangs.match(langs[it], audioLangPref) } ?: return
        if (SubLangs.match(langs[best], audioLangPref) == 0) return
        val g = audioGroups[best]
        if (g.isSelected) return
        runCatching {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
                .build()
        }
    }
    fun enableTextGroup(g: androidx.media3.common.Tracks.Group) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
    }
    fun disableText() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            .build()
    }
    // Legenda que liga sozinha quando o perfil deixou a legenda ligada. A ordem está em
    // SubLangs.choose (faixa do vídeo e externa no idioma do perfil, depois o idioma vizinho).
    // Escolha à mão no vídeo atual manda, e idioma que a pessoa não escolheu não liga.
    /** Id da faixa embutida p/ memória/delay (Lote 16 item 3). */
    fun embeddedTrackId(g: androidx.media3.common.Tracks.Group): SubTrackId {
        val f = g.getTrackFormat(0)
        return SubTrackId(SubSourceKind.EMBEDDED, f.language.orEmpty(), f.label.orEmpty(), f.label?.takeIf { it.isNotBlank() } ?: SubLangs.label(f.language))
    }
    /** Id da faixa externa p/ memória/delay (Lote 16 item 3). */
    fun externalTrackId(e: com.aotv.app.data.remote.ExternalSub): SubTrackId =
        SubTrackId(SubSourceKind.EXTERNAL, e.lang, e.label.substringAfter(" · ", "").ifBlank { "OpenSubtitles" }, SubLangs.label(e.lang))
    /** Devolve o atraso salvo da 1ª legenda (Lote 16 item 3). */
    fun restorePrimaryDelay() {
        val t = dualSubState.primary ?: return
        subDelay = SubDelayStore.fromContext(context).delayFor(t) / 1000.0
    }
    /** Salva o atraso da 1ª legenda por faixa (Lote 16 item 3). */
    fun persistPrimaryDelay() {
        val t = dualSubState.primary ?: return
        val ms = (subDelay * 1000).toLong()
        dualSubState = withDelay(dualSubState, t, ms)
        SubDelayStore.fromContext(context).setDelay(t, ms)
    }
    /** Ajuste fino do atraso da 1ª legenda ao vivo (Lote 16 item 3 — D-pad ←→ no painel de legendas). */
    fun adjustSubDelay(stepMs: Long) {
        subDelay = addDelay(subDelay, stepMs / 1000.0)
        persistPrimaryDelay()
    }
    /** Forced subs (Lote 16 item 3 + Lote 23 item 12, JOTV 1.0.22 §4.4): com
     * a legenda completa DESLIGADA (não ligou sozinha nem à mão) e o áudio
     * estrangeiro, mostra a faixa forced (falas em outra língua dentro do
     * filme) — só com a pref `pref_forced_subs` ligada (padrão). Ela nunca
     * toma o lugar da completa: ligar uma faixa completa troca o override
     * do tipo texto no ExoPlayer (ver [enableTextGroup]) e a forçada sai.
     * A escolha explícita do usuário (subPicked) nunca é desfeita. */
    fun maybeShowForcedSub() {
        if (!prefs.forcedSubsAuto) return
        if (subtitleOn || selectedSubUrl != null || subPicked) return
        val audioLang = audioGroups.firstOrNull { it.isSelected }?.getTrackFormat(0)?.language
        if (!isForeignAudio(audioLang, subLang)) return
        val forced = pickForcedTrack(textGroups.map { it.getTrackFormat(0) }, subLang, audioLang) ?: return
        val g = textGroups.firstOrNull { it.getTrackFormat(0) == forced } ?: return
        enableTextGroup(g)
        subtitleOn = true
    }

    fun pickAutoSub() {
        if (!subWanted || subPicked) return
        val prefs = com.aotv.app.data.local.PlatformPrefs(context)
        // Lote 16 item 3 — "Foreign audio only": com a pref ligada, a legenda
        // só abre sozinha quando o áudio é estrangeiro p/ o idioma do perfil.
        val audioLang = audioGroups.firstOrNull { it.isSelected }?.getTrackFormat(0)?.language
        if (prefs.rawPref("sub_foreign_only") == true && !isForeignAudio(audioLang, subLang)) return
        // Lote 16 item 3 — a faixa exata lembrada deste título vale antes do automático.
        val memory = SubTrackMemory.fromContext(context)
        val available = textGroups.map { embeddedTrackId(it) } + externalSubs.map { externalTrackId(it) }
        memory.resolve(subTitleKey, available)?.let { wanted ->
            when (wanted.kind) {
                SubSourceKind.EMBEDDED -> {
                    val g = textGroups.firstOrNull { SubLangs.match(it.getTrackFormat(0).language, wanted.lang) == 2 }
                    if (g != null) {
                        selectedSubUrl = null; enableTextGroup(g); subtitleOn = true
                        dualSubState = dualSubState.copy(primary = wanted)
                        restorePrimaryDelay()
                        return
                    }
                }
                SubSourceKind.EXTERNAL -> {
                    val ext = externalSubs.firstOrNull { it.lang == wanted.lang }
                    if (ext != null) {
                        if (subtitleOn) disableText(); subtitleOn = false; selectedSubUrl = ext.url
                        dualSubState = dualSubState.copy(primary = wanted)
                        restorePrimaryDelay()
                        return
                    }
                }
            }
        }
        // Lote 16 item 3 — ocultar SDH: tira as faixas de surdos da escolha automática.
        val embGroups = if (prefs.rawPref("sub_hide_sdh") == true) textGroups.filter { !isSdhTrack(it.getTrackFormat(0)) } else textGroups
        val pick = SubLangs.choose(embGroups.map { it.getTrackFormat(0).language }, externalSubs.map { it.lang }, subLang) ?: return
        if (pick.embedded) {
            selectedSubUrl = null
            val g = embGroups[pick.index]
            enableTextGroup(g)
            subtitleOn = true
            dualSubState = dualSubState.copy(primary = embeddedTrackId(g))
            restorePrimaryDelay()
        } else {
            if (subtitleOn) disableText()
            subtitleOn = false
            val ext = externalSubs[pick.index]
            selectedSubUrl = ext.url
            dualSubState = dualSubState.copy(primary = externalTrackId(ext))
            restorePrimaryDelay()
        }
    }
    // Escolha à mão. Vale para o vídeo atual e vira a preferência do perfil (ligada ou não), para
    // o próximo episódio e a próxima sessão abrirem do mesmo jeito.
    fun pickSub(g: androidx.media3.common.Tracks.Group?, ext: com.aotv.app.data.remote.ExternalSub?) {
        subPicked = true
        // Lote 16 item 3: lembrar a faixa exata deste título (vale no próximo episódio/sessão).
        val memory = SubTrackMemory.fromContext(context)
        when {
            g != null -> {
                selectedSubUrl = null; enableTextGroup(g); subtitleOn = true
                val track = embeddedTrackId(g)
                memory.remember(subTitleKey, track)
                dualSubState = dualSubState.copy(primary = track)
                restorePrimaryDelay()
            }
            ext != null -> {
                if (subtitleOn) disableText(); subtitleOn = false; selectedSubUrl = ext.url
                val track = externalTrackId(ext)
                memory.remember(subTitleKey, track)
                dualSubState = dualSubState.copy(primary = track)
                restorePrimaryDelay()
            }
            else -> { disableText(); subtitleOn = false; selectedSubUrl = null }
        }
        subWanted = g != null || ext != null
        progressVm.subtitleOn = subWanted
    }

    // ── Sincronização automática da legenda (Lote 9 item 1) ─────────────
    /** Estado da medição (a tela mostra "Medindo…" na ação). */
    var autoSyncState by run { mutableStateOf(AutoSyncState.IDLE) }
    private var autoSyncJob: kotlinx.coroutines.Job? = null

    /**
     * Mede o atraso da legenda externa contra a embutida (ver
     * PlayerAutoSync.kt) e aplica o ajuste medido. Nada é inventado: sem
     * legenda externa carregada, sem faixa embutida, sem vídeo tocando ou
     * sem casamento suficiente, NADA muda e o ajuste manual continua
     * valendo. A faixa embutida é ligada só durante a medição e devolvida
     * ao estado anterior depois (e o `subPicked` é restaurado, p/ o
     * automático não trocar a legenda no meio da medição).
     */
    fun runAutoSync() {
        if (autoSyncJob != null) return
        if (selectedSubUrl == null || externalCues.isEmpty()) {
            toastMsg = "Escolha uma legenda externa para sincronizar"
            return
        }
        if (textGroups.isEmpty()) {
            toastMsg = "Este vídeo não tem legenda embutida para comparar"
            return
        }
        if (!isPlaying) {
            toastMsg = "Dê play no vídeo para medir a sincronização"
            return
        }
        autoSyncJob = scope.launch {
            autoSyncState = AutoSyncState.MEASURING
            toastMsg = "Medindo a sincronização — deixe o vídeo tocar"
            val wasEmbeddedOn = subtitleOn
            val wasSubPicked = subPicked
            subPicked = true
            val group = textGroups.sortedBy { SubLangs.rank(it.getTrackFormat(0).language, subLang) }.firstOrNull()
            if (group != null) enableTextGroup(group)
            subtitleOn = true
            val embedded = sampleEmbeddedCues(exoPlayer)
            if (!wasEmbeddedOn) {
                disableText()
                subtitleOn = false
            }
            subPicked = wasSubPicked
            val est = estimateSubDelay(embedded, externalCues)
            autoSyncState = AutoSyncState.IDLE
            autoSyncJob = null
            when {
                est == null -> toastMsg = "Não deu para medir — as falas não bateram. O ajuste manual continua valendo."
                kotlin.math.abs(est.adjustmentSec) < 0.5 -> toastMsg = "A legenda já está sincronizada (diferença medida de ${est.medianOffsetMs} ms em ${est.matchedCues} falas)"
                else -> {
                    subDelay = est.adjustmentSec
                    toastMsg = "Legenda sincronizada: ${subDelayLabel(est.adjustmentSec)} (medido em ${est.matchedCues} falas)"
                }
            }
        }
    }

    // ── Som de outra fonte ──
    fun saveExt() {
        val src = currentSources.firstOrNull { it.url == extUrl }
        progressVm.saveExtAudio(extKey, src?.let { sourceSig(it, title) }, audioDelay)
    }
    /** Grava o atraso do áudio próprio do título (só o atraso; 0.0 apaga o registro). */
    fun saveOwnAudio() {
        progressVm.saveOwnAudioDelay(extKey, audioDelay)
    }
    /**
     * Volta [audioDelay] p/ o atraso do áudio próprio salvo do título
     * (Lote 14 item 3). Chamado quando o som de outra fonte sai: sem
     * isso, o atraso do som externo ficaria valendo p/ o áudio próprio.
     */
    fun restoreOwnAudioDelay() {
        audioDelay = progressVm.ownAudioDelay(extKey)?.delaySec ?: 0.0
    }
    fun dropExt(restoreOwn: Boolean = true) {
        extSync?.release()
        extSync = null; extUrl = null; extHeld = false; syncBarOpen = false
        if (restoreOwn) restoreOwnAudioDelay()
    }
    /** Liga o som de [src] com a imagem atual. [onDone] diz se deu certo. A referência
     *  de posição é o player atual (local ou CastPlayer, no Hear it) — ver [currentPlayer]. */
    fun setExt(src: MoviePlayerSource, onDone: (Boolean) -> Unit) {
        dropExt(restoreOwn = false)
        val ext = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory(context)).build().apply {
            // Só o som. Sem decodificar imagem nem legenda.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .setPreferredAudioLanguage("pt")
                .build()
            setMediaItem(mediaItemFor(context, src.url))
        }
        extUrl = src.url
        extSync = ExtAudioSync(
            currentPlayer(), ext, scope, audioDelay,
            onHold = { extHeld = it },
            onReady = { if (extUrl == src.url) { saveExt(); onDone(true) } },
            onError = { msg ->
                if (extUrl == src.url) { extSync = null; dropExt(); toastMsg = msg; onDone(false) }
            },
        )
    }
    // ── Gestos de volume (direita) e brilho (esquerda) — só celular (toque) ──
    val audioManager = run { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val activity = run { context as? android.app.Activity }
    val maxVol = run { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var volLevel by run {
        mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    var brightLevel by run {
        mutableFloatStateOf((activity?.window?.attributes?.screenBrightness ?: 0.5f).let { if (it < 0f) 0.5f else it })
    }
    fun changeVolume(deltaFrac: Float) {
        volLevel = (volLevel + deltaFrac).coerceIn(0f, 1f)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (volLevel * maxVol).toInt(), 0)
        gestureHud = "Volume" to volLevel
    }
    fun changeBrightness(deltaFrac: Float) {
        val a = activity ?: return
        brightLevel = (brightLevel + deltaFrac).coerceIn(0.02f, 1f)
        a.window.attributes = a.window.attributes.apply { screenBrightness = brightLevel }
        gestureHud = "Brilho" to brightLevel
    }
    // Poll de posição + salva progresso a cada ~10s (Continuar assistindo)
    var markedWatchedEp by run { mutableIntStateOf(-1) }
    // Avaliação pós-filme (v1.17.0): o gatilho de filme dispara 1x por título.
    var markedWatchedMovie by run { mutableStateOf(false) }
    fun seekBy(deltaMs: Long, showControls: Boolean = true) {
        // Lote 24 item 10: buscar é interação (botão ±10 s ou gesto).
        noteUserInteraction()
        val p = currentPlayer()
        val d = if (duration > 0) duration else p.duration.coerceAtLeast(0L)
        val target = (p.currentPosition + deltaMs).coerceIn(0L, if (d > 0) d else Long.MAX_VALUE)
        p.seekTo(target); position = target
        if (showControls) controlsVisible = true
    }
    /** Um passo do gesto de deslizar p/ buscar (item 5): ±10 s, o mesmo passo dos
     * botões ±10 s. Respeita o bloqueio e o toggle tap-seek; NÃO abre os
     * controles (só o indicador [seekHud]) p/ o gesto não piscar a UI. */
    fun swipeSeekStep(forward: Boolean) {
        if (locked || !swipeSeekEnabled) return
        seekBy(if (forward) 10_000 else -10_000, showControls = false)
        seekHud = if (forward) "+10s" else "−10s"
    }
    fun togglePlay() {
        // Lote 24 item 10: play/pause é interação.
        noteUserInteraction()
        currentPlayer().let { it.playWhenReady = !it.playWhenReady }
        controlsVisible = true
        // Sleep timer (Lote 16 item 6): ao voltar a tocar depois da pausa do
        // timer, reativa a legenda como estava (1x — o pause/resume normal
        // não restaura nada).
        if (sleepPaused && currentPlayer().playWhenReady) {
            sleepPaused = false
            val wake = sleepWakeState
            sleepWakeState = null
            if (sleepShouldRestoreSubtitles(wake)) {
                if (wake?.externalSubUrl != null) {
                    selectedSubUrl = wake.externalSubUrl
                } else {
                    pickAutoSub()
                }
            }
        }
    }

    // ── Sleep timer (Lote 9 item 4) ──────────────────────────────────────
    // Job com delay no escopo da sessão (sem work-runtime): morre junto
    // com o player. Escolha por sessão, sem pref persistente.
    /** Minutos do timer atual (null = desligado). */
    var sleepMinutes by run { mutableStateOf<Int?>(null) }
    /** true = pausar no fim do episódio (sem timer de minutos). */
    var sleepAtEpisodeEnd by run { mutableStateOf(false) }
    /** Quando o timer dispara (ms desde epoch). 0 = desligado. */
    var sleepDeadlineMs by run { mutableLongStateOf(0L) }
    private var sleepJob: kotlinx.coroutines.Job? = null

    /** Liga o timer p/ pausar em [minutes] min. */
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepMinutes = minutes
        sleepAtEpisodeEnd = false
        sleepDeadlineMs = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            sleepMinutes = null
            sleepDeadlineMs = 0L
            // Lote 16 item 6: guarda o estado da legenda p/ reativar quando o
            // usuário voltar a tocar (só vale 1x, p/ a pausa do timer).
            sleepWakeState = captureSleepWakeState(subtitleOn, selectedSubUrl)
            sleepPaused = true
            currentPlayer().playWhenReady = false
            toastMsg = "Sleep timer: reprodução pausada"
        }
        toastMsg = "Sleep timer: pausa em $minutes min"
    }

    /** Liga o "pausar no fim do episódio" (o fim não avança nem volta). */
    fun startSleepAtEpisodeEnd() {
        cancelSleepTimer()
        sleepAtEpisodeEnd = true
        toastMsg = "Sleep timer: pausa no fim do episódio"
    }

    /** Desliga o timer (qualquer modo). */
    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepMinutes = null
        sleepAtEpisodeEnd = false
        sleepDeadlineMs = 0L
    }

    // Picture-in-Picture: mini tela pra continuar assistindo em outros apps (API 26+).
    fun enterPip() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val act = activity ?: return
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .build()
        runCatching { act.enterPictureInPictureMode(params) }
    }
    val inPip get() = PipState.inPip.value

    // ── Volume Boost (Fase C) ────────────────────────────────────────────
    // LoudnessEnhancer na sessão de áudio do ExoPlayer: amplifica o som do
    // APP sem mexer no volume do sistema (o gesto de volume continua usando o
    // AudioManager, como antes). Sem renderer nem audio sink custom: o efeito
    // é do Android, aplicado na sessão que o ExoPlayer já cria. O ganho vem
    // de volBoostGainMb (PlayerExtras): 1,5x ≈ +3,5 dB, 2,0x ≈ +6 dB.
    // A sessão de áudio do ExoPlayer é estável por player, então basta
    // (re)aplicar quando a pref muda ou quando a sessão de áudio muda
    // (onAudioSessionIdChanged). Aparelho sem suporte: avisa 1x e segue sem boost.
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    fun applyVolBoost() {
        val id = runCatching { exoPlayer.audioSessionId }.getOrDefault(0)
        if (!volBoostEnabled || volBoostLevel <= 1f || id == 0) {
            runCatching { loudnessEnhancer?.enabled = false }
            return
        }
        val enhancer = loudnessEnhancer ?: runCatching {
            android.media.audiofx.LoudnessEnhancer(id)
        }.getOrNull()
        if (enhancer == null) {
            if (!volBoostUnavailable) {
                volBoostUnavailable = true
                toastMsg = "Volume Boost não suportado neste aparelho"
            }
            return
        }
        loudnessEnhancer = enhancer
        runCatching {
            enhancer.setTargetGain(volBoostGainMb(volBoostLevel))
            enhancer.enabled = true
        }
    }
    fun releaseVolBoost() {
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }

    // ── Equalizador (Lote 9 item 3) ──────────────────────────────────────
    // Mesmo gancho do Volume Boost: o efeito vive na sessão de áudio do
    // ExoPlayer e é (re)criado quando ela fica pronta
    // (onAudioSessionIdChanged) ou quando a pref muda (LaunchedEffect na
    // tela). Ao fechar o player, releaseEqualizer() libera o efeito.
    var equalizer: PlayerEqualizer? by run { mutableStateOf<PlayerEqualizer?>(null) }
    /** O aparelho não aceitou o Equalizer (aviso 1x, ver [applyEqualizer]). */
    var eqUnavailable by run { mutableStateOf(false) }

    /** Bandas atuais em dB (da pref, ou tudo em 0 quando a pref está vazia/inválida). */
    val eqBands: List<Int> get() {
        val n = equalizer?.bandCount?.takeIf { it > 0 } ?: EQ_BANDS
        return parseEqBandsCsv(eqBandsCsv, n) ?: List(n) { 0 }
    }

    /** (Re)aplica o equalizador na sessão de áudio atual. */
    fun applyEqualizer() {
        val id = runCatching { exoPlayer.audioSessionId }.getOrDefault(0)
        if (!eqEnabled || id == 0) {
            runCatching { equalizer?.release() }
            equalizer = null
            return
        }
        val eq = equalizer ?: runCatching { PlayerEqualizer(id) }.getOrNull()
        if (eq == null || !eq.supported) {
            if (!eqUnavailable) {
                eqUnavailable = true
                toastMsg = "Equalizador não suportado neste aparelho"
            }
            return
        }
        equalizer = eq
        if (eqPreset != EQ_PRESET_FLAT && eq.applyPreset(eqPreset)) return
        eq.applyBands(eqBands)
    }

    /** Libera o Equalizer (ao fechar o player). */
    fun releaseEqualizer() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    /** Muda uma banda e salva (sai do preset: vira "flat" customizado). */
    fun onEqBand(index: Int, db: Int) {
        val cur = eqBands.toMutableList()
        if (index !in cur.indices) return
        cur[index] = db.coerceIn(-15, 15)
        onEqSave(EQ_PRESET_FLAT, eqBandsCsv(cur))
    }

    /** Volta tudo em 0 dB e salva. */
    fun onEqReset() { onEqSave(EQ_PRESET_FLAT, eqBandsCsv(List(eqBands.size) { 0 })) }

    /** Libera tudo do player (ExoPlayer, sessão de mídia, boost, proxy, torrent).
     * O dispose da tela chama isso, EXCETO ao minimizar (ver [minimizedByUser]);
     * o fechar do mini player chama direto. */
    fun releaseAll() {
        extSync?.release()
        releaseVolBoost()
        releaseEqualizer()
        cancelSleepTimer()
        exoPlayer.release()
        mediaSession.release()
        ScrubThumbnailer.release()
        // v1.2.8: para o proxy local do Kodi (a porta e os tokens morrem com o player).
        com.aotv.app.data.kodi.KodiLocalProxy.stop()
        // Saiu do player, o aparelho para de baixar e de compartilhar o torrent.
        Torrents.ifStarted()?.let { e -> Thread { e.stopAll() }.start() }
    }
    // Salva o progresso ATUAL na hora (antes de trocar fonte/episódio e ao terminar) — evita
    // perder a posição do filme/série quando muda a fonte ou fecha.
    fun saveNow() {
        val pos = position.coerceAtLeast(0L)
        if (pos > 0) {
            val curEp = if (type == "SERIES" && curEpisode > 0) "T$curSeason:E$curEpisode" else lastEpisode
            progressVm.save(tmdbId, type, title, posterUrl, pos, duration, curEp, streamUrl ?: activeUrl, preferredAddon)
        }
    }
    // Troca de episódio (séries) mantendo a MESMA fonte. Recarrega a lista de fontes
    // do novo episódio e atualiza o seletor — antes ficava preso na fonte do anterior.
    // Coloca no ar a lista de fontes de um episódio já resolvido. Mantém a MESMA fonte (label)
    // do episódio anterior quando ela existe na lista nova; senão pega a de melhor qualidade.
    fun applyEpisodeSources(list: List<MoviePlayerSource>, idx: Int, targetEp: Int, targetSeason: Int) {
        val chosen = list.getOrNull(idx) ?: return
        currentSources = list
        currentSourceIdx = idx
        currentSourceLabel = chosen.label
        curSeason = targetSeason
        curEpisode = targetEp
        prefetchedEp = -1                   // libera o prefetch do sucessor deste episódio
        triedUrls.clear(); failedAddons.clear(); wrongUrls.clear(); upgradeDone = false
        // Auto-skip (Lote 9 item 2): episódio novo, medição nova — o pulo
        // de 1x por segmento vale p/ cada episódio.
        lastSkipCheckPos = 0L
        autoSkippedEp = -1
        autoSkippedTarget = -1L
        // Episódio novo desliga o som de outra fonte e a sincronia da legenda. O som salvo
        // volta sozinho quando a mesma fonte aparecer no episódio novo.
        dropExt(); extRestored = false; subDelay = 0.0
        didSeek = true; pendingSeekMs = 0   // novo episódio começa do início
        activeUrl = chosen.url
        streamUrl = chosen.url
        // switching continua true até o ExoPlayer chegar em STATE_READY (sem piscada da cena velha).
    }

    fun goToEpisode(target: Int, targetSeason: Int = curSeason) {
        if (type != "SERIES" || target < 1 || targetSeason < 1 || switching) return
        // Salva o progresso do episódio ATUAL antes de trocar (senão perde a posição ao avançar).
        val savePos = position.coerceAtLeast(0L)
        if (savePos > 0) {
            val curEp = if (curEpisode > 0) "T$curSeason:E$curEpisode" else lastEpisode
            progressVm.save(tmdbId, type, title, posterUrl, savePos, duration, curEp, streamUrl ?: activeUrl, preferredAddon)
        }
        userLockedSource = pinnedSource // novo episódio destrava, a não ser que esteja fixada
        controlsVisible = true
        switching = true         // feedback imediato na tela
        // 0) PRÉ-CARREGADO no meio do episódio anterior → troca sem rede nenhuma.
        val cached = episodeVm.cachedEpisodeSources(tmdbId, targetSeason, target)
        if (cached != null) {
            applyEpisodeSources(cached, cached.let { pickPreferredSource(it, preferredAddon, preferredQuality) }, target, targetSeason)
            return
        }
        val season = targetSeason
        scope.launch {
            // 1) Streams Stremio do novo episódio, TOCANDO NO PRIMEIRO ADDON QUE RESPONDER.
            var started = false
            episodeVm.stremioEpisodeSourcesProgressive(tmdbId, season, target) { partial ->
                if (partial.isEmpty()) return@stremioEpisodeSourcesProgressive
                if (!started) {
                    started = true
                    applyEpisodeSources(partial, partial.let { pickPreferredSource(it, preferredAddon, preferredQuality) }, target, season)
                } else {
                    // Já está tocando. Os addons que chegaram depois só entram no seletor.
                    val playingUrl = currentSources.getOrNull(currentSourceIdx)?.url
                    currentSources = partial
                    currentSourceIdx = partial.indexOfFirst { it.url == playingUrl }.coerceAtLeast(0)
                }
            }
            // Nenhum addon devolveu o episódio novo.
            if (!started) { switching = false; sourceFailed = true }
        }
    }
    fun playNextEpisode() { nextCountdownArmed = false; showNextPrompt = false; if (hasNextEpisode) goToEpisode(curEpisode + 1) }
    // Próxima TEMPORADA: episódio 1 da temporada seguinte (mesma fonte quando existir).
    fun goToNextSeason() {
        if (type != "SERIES" || !hasNextSeason) return
        nextCountdownArmed = false
        showNextPrompt = false
        goToEpisode(1, curSeason + 1)
    }
    // Aplica a fonte no índice dado (identidade por ÍNDICE, não por label — labels se repetem,
    // e resolver por label causava troca infinita / duas fontes "ativas" ao mesmo tempo).
    // manual=true: usuário escolheu no seletor → trava o auto-fallback (não pula, "mantém").
    fun applyMovieSource(idx: Int, manual: Boolean = false) {
        val src = currentSources.getOrNull(idx) ?: return
        saveNow() // salva a posição da fonte atual antes de trocar
        userLockedSource = manual || pinnedSource
        // Escolha à mão vira a preferência levada pros próximos episódios. Troca automática
        // (fallback por erro) NÃO mexe nisso, senão uma falha pontual sequestraria a escolha.
        if (manual) {
            preferredAddon = src.addon
            preferredQuality = sourceQualityRank(src.label)
            // Última fonte do título (Lote 4 item 6): grava addon + qualidade
            // (nunca a URL). O gravar já é bloqueado no modo privado e com a
            // pref desligada — ver PlayerProgressViewModel.saveLastSourcePick.
            progressVm.saveLastSourcePick(tmdbId, type, src.addon, sourceQualityRank(src.label))
        }
        // O painel fecha só por quem escolheu (onPick e OK). Troca automática (corrida de espelhos,
        // fonte lenta) não fecha o painel que o controle remoto está usando.
        // A imagem passou a ser a fonte que dava o som. Volta o som da própria fonte.
        if (src.url == extUrl) dropExt()
        currentSourceIdx = idx
        currentSourceLabel = src.label
        // Posição a manter na nova fonte. Se o player ainda NÃO tocou nada (troca automática
        // logo na abertura), currentPosition é 0 — nesse caso PRESERVA o pendingSeekMs original
        // (o "continuar assistindo"). Sem isso a troca de fonte zerava o progresso salvo.
        val live = runCatching { exoPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
        val keep = maxOf(live, position.coerceAtLeast(0L))
        pendingSeekMs = if (keep > 0) keep else pendingSeekMs
        didSeek = false
        activeUrl = src.url
        streamUrl = src.url // ExoPlayer re-prepara e faz o seek
    }
    // Troca de fonte DENTRO do player (usuário no seletor) — MANUAL → trava auto-fallback.
    fun selectMovieSource(src: MoviePlayerSource) = applyMovieSource(currentSources.indexOf(src), manual = true)

    // Fontes com o que se sabe delas (teste do detalhe, outro filme visto aqui).
    fun facts() = currentSources.map { s ->
        val p = episodeVm.probeCached(s.url)
        SourceFacts(
            s.url, s.addon, s.label, sourceQualityRank(s.label), p,
            wrongTitle = s.url in wrongUrls || (p != null && durationMismatch(runtimeMin, p.sec)),
            debrid = if (episodeVm.debridCached(s.url) == true) 2 else 0,
        )
    }

    /**
     * Ordem em ranking de TODAS as fontes, da melhor p/ pior (Lote 17 —
     * v1.27.0). O MESMO score do detalhe ([rankSources]): resolução, tempo
     * até o 1º byte, saúde aprendida, idioma, debrid, CAM/sample — com o
     * desempate final por URL, então a ordem é determinística (nada de
     * "parece sorteio"). A folha NÃO esconde fonte aqui (sem filtro de
     * idioma nem de codec): quem está no player pode escolher qualquer uma
     * à mão; os filtros valem na lista do detalhe. O #1 daqui é o mesmo #1
     * que o "Assistir" escolhe.
     */
    fun rankedSourceOrder(): List<Int> {
        val f = facts()
        if (f.isEmpty()) return emptyList()
        val item = progressVm.lastSourcePick(tmdbId, type)
        return com.aotv.app.data.remote.rankSources(
            facts = f,
            health = prefs.sourceHealth,
            preset = com.aotv.app.data.remote.SourcePreset.of(prefs.sourcePreset),
            strategy = com.aotv.app.data.remote.SourceStrategy.of(prefs.sourceStrategy),
            langPref = prefs.audioPref,
            maxQuality = prefs.maxQuality,
            manualOrder = prefs.sourceManualOrder,
            lastPick = if (prefs.sourceRememberPick) item else null,
            originalFirst = prefs.sourceOriginalFirst,
            runtimeMin = runtimeMin,
        ).map { it.index }
    }

    /** Score com motivo por URL, p/ a folha mostrar "#1 · 84 — 1080p · rápido". */
    fun rankedByUrl(): Map<String, com.aotv.app.data.remote.RankedSource> {
        val f = facts()
        if (f.isEmpty()) return emptyMap()
        return com.aotv.app.data.remote.rankSources(
            facts = f,
            health = prefs.sourceHealth,
            preset = com.aotv.app.data.remote.SourcePreset.of(prefs.sourcePreset),
            strategy = com.aotv.app.data.remote.SourceStrategy.of(prefs.sourceStrategy),
            langPref = prefs.audioPref,
            maxQuality = prefs.maxQuality,
            manualOrder = prefs.sourceManualOrder,
            lastPick = if (prefs.sourceRememberPick) progressVm.lastSourcePick(tmdbId, type) else null,
            originalFirst = prefs.sourceOriginalFirst,
            runtimeMin = runtimeMin,
        ).associateBy { it.fact.url }
    }

    // ── Lote 14 item 4 — folha de fontes estável durante a busca (v1.24.0) ──
    // Por que precisou de fix (NÃO estava certa): o `merge()` das séries
    // põe as fontes novas NA FRENTE (`novas + sobras`) e o `rankedSourceOrder()`
    // é recalculado a cada recomposição a partir dos `facts()` — cada probe
    // que completa (ou addon que responde) reordenava a folha no meio da
    // busca, exatamente o que o JOTV tirou na 1.0.20 (análise §4.3).
    /** Chave da ordem congelada da folha: título + episódio atual. */
    fun sourceOrderKey() = "$type:$tmdbId:$curSeason:$curEpisode"

    /**
     * Anota as fontes que chegaram: a ordem congelada só cresce no fim —
     * o que já apareceu fica onde está. Trocar de episódio zera tudo
     * (ordem, assentamento e toques). Chamar a cada lista nova (o efeito
     * em PlayerSourceEffects faz isso).
     */
    fun noteSourcesArrived() {
        val key = sourceOrderKey()
        val urls = currentSources.map { it.url }
        if (key != sourceSheetOrderKey) {
            sourceSheetOrderKey = key
            sourceSheetOrder = urls
            sourcesSettled = false
            sourceSettledOrder = null
            sourceTouchedUrls = emptySet()
            // Lote 15 item 2: trocar de episódio zera o filtro de provider junto.
            sourceProviderFilter = ""
            return
        }
        val known = sourceSheetOrder.toSet()
        val newcomers = urls.filter { it !in known }
        if (newcomers.isNotEmpty()) {
            sourceSheetOrder = sourceSheetOrder + newcomers
            // Leva nova: o ranking fotografado não vale mais — fotografa de novo no próximo assentamento.
            sourcesSettled = false
            sourceSettledOrder = null
        }
    }

    /**
     * Assentou (sem fonte nova há 1,5 s): fotografa o ranking uma única
     * vez por leva. O ranking continua sendo o [rankedSourceOrder] — só
     * não é recalculado a cada probe/addon que chega.
     */
    fun markSourcesSettled() {
        if (sourceSettledOrder != null) return
        sourceSettledOrder = rankedSourceOrder().mapNotNull { currentSources.getOrNull(it)?.url }
        sourcesSettled = true
        // Lote 19 item 7 (f): a lista assentou — pede a consulta de cache só de
        // leitura p/ os magnets, p/ a troca automática saber o que já está pronto.
        refreshDebridCacheForAutoSwitch()
    }

    /** Marca a fonte como tocada/focada pelo usuário na folha. */
    fun markSourceTouched(url: String) {
        if (url.isBlank() || url in sourceTouchedUrls) return
        sourceTouchedUrls = sourceTouchedUrls + url
    }

    /**
     * Ordem de exibição da folha (índices em [currentSources]).
     * Antes de assentar: ordem de chegada (congelada + novas no fim).
     * Depois de assentar: o ranking fotografado, com as fontes que
     * chegaram depois no fim. Quem o usuário tocou/focou fica na posição
     * em que apareceu, mesmo depois do ranking. Identidade por URL (o
     * mesmo pressuposto do `merge()`: a URL identifica a fonte).
     */
    fun stableSourceOrder(): List<Int> {
        val urls = currentSources.map { it.url }
        if (urls.isEmpty()) return emptyList()
        val frozen = sourceSheetOrder.filter { it in urls }
        val newcomers = urls.filter { it !in frozen }
        val shown = frozen + newcomers
        val snap = sourceSettledOrder
        val base = if (snap == null) shown
        else snap.filter { it in urls } + newcomers.filter { it !in snap }
        val touched = sourceTouchedUrls.filter { it in urls }
        if (touched.isEmpty()) return base.map { urls.indexOf(it) }
        val rest = base.filter { it !in touched }.toMutableList()
        touched.sortedBy { frozen.indexOf(it) }.forEach { url ->
            rest.add(frozen.indexOf(url).coerceIn(0, rest.size), url)
        }
        return rest.map { urls.indexOf(it) }
    }

    /** Palavras do título p/ casar com o On Demand (minúsculas, sem pontuação). */
    private fun vodTitleWords(t: String): List<String> =
        t.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }

    /** O item do On Demand é este título? Todas as palavras do título mais curto estão no mais longo. */
    internal fun vodMatches(title: String, itemTitle: String): Boolean {
        val a = vodTitleWords(title)
        val b = vodTitleWords(itemTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        val (short, long) = if (a.size <= b.size) a to b else b to a
        return short.all { it in long }
    }

    /**
     * Busca o título no On Demand Xtream p/ a folha de fontes (Lote 12 —
     * Frente 2). Só com a conta Xtream já configurada; sem conta, a seção
     * fica no vazio honesto (ver [sourceActions]). Busca 1x por título.
     * Filmes primeiro, depois séries, no máximo 6 — sem inventar resultado.
     */
    fun loadOnDemand() {
        val repo = onDemandRepo ?: return
        val key = "$type:$title"
        if (onDemandLoadedFor == key || onDemandLoading) return
        onDemandLoadedFor = key
        onDemandLoading = true
        onDemandError = null
        scope.launch {
            runCatching {
                val source = repo.xtreamSources().firstOrNull()
                if (source == null) {
                    onDemandSource = null
                    onDemandSourceName = ""
                    onDemandItems = emptyList()
                } else {
                    onDemandSource = source
                    onDemandSourceName = source.name
                    val movies = repo.fetchOnDemand(source, false)?.second.orEmpty()
                    val series = repo.fetchOnDemand(source, true)?.second.orEmpty()
                    val hits = (movies.map { OnDemandHit(it, false) } + series.map { OnDemandHit(it, true) })
                        .filter { vodMatches(title, it.item.title) }
                        .take(6)
                    onDemandItems = hits
                }
            }.onFailure {
                onDemandError = it.message?.takeIf { m -> m.isNotBlank() } ?: "Não deu para carregar o On Demand"
                onDemandItems = emptyList()
            }
            onDemandLoading = false
        }
    }

    /**
     * Toca um resultado do On Demand pelo player existente (Lote 12 —
     * Frente 2): vira uma fonte "On Demand" na lista e aplica como escolha
     * manual (mantém o tempo). Série toca o 1º episódio da API — a folha
     * não tem como escolher episódio; o detalhe completo fica no On Demand
     * da aba Ao vivo. Sem URL, o toast explica — sem inventar stream.
     */
    fun playOnDemand(hit: OnDemandHit) {
        val repo = onDemandRepo
        val source = onDemandSource
        if (repo == null || source == null) {
            toastMsg = "On Demand indisponível — sem conta Xtream configurada"
            return
        }
        if (!hit.isSeries) {
            val url = repo.vodStreamUrl(source, hit.item)
            currentSources = currentSources + MoviePlayerSource("On Demand · ${hit.item.title}", url, addon = "On Demand")
            applyMovieSource(currentSources.lastIndex, manual = true)
            toastMsg = "Tocando versão On Demand: ${hit.item.title}"
            return
        }
        scope.launch {
            val url = runCatching {
                val detail = repo.fetchSeriesDetail(source, hit.item.id)
                val ep = detail?.episodes?.firstOrNull { it.id.isNotBlank() } ?: return@runCatching null
                repo.episodeStreamUrl(source, ep)
            }.getOrNull()
            if (url.isNullOrBlank()) {
                toastMsg = "Não deu para abrir esta série no On Demand"
                return@launch
            }
            currentSources = currentSources + MoviePlayerSource("On Demand · ${hit.item.title}", url, addon = "On Demand")
            applyMovieSource(currentSources.lastIndex, manual = true)
            toastMsg = "Tocando versão On Demand: ${hit.item.title} (1º episódio)"
        }
    }

    /**
     * Lote 19 item 7 (f) — "Debrid só com arquivo pronto": com a pref ligada,
     * a troca AUTOMÁTICA veta um torrent que o debrid ativo ainda não tem
     * pronto, p/ a conta não começar downloads sozinha. Só vale com conta de
     * debrid ativa (sem conta, o magnet vai p/ P2P e o gate não se aplica) e
     * só p/ magnets: link direto nunca passa pelo debrid. "Não se sabe"
     * (nunca consultado, ou Real-Debrid/AllDebrid — que só descobrem o cache
     * resolvendo, ver DebridService.check) conta como não pronto. A escolha
     * à mão e o "Tentar a próxima fonte" NÃO passam por aqui: aí foi o
     * usuário quem escolheu, e o resolve na hora de tocar vale.
     */
    fun debridBlocksAutoSwitch(url: String): Boolean {
        if (!debridReadyOnly) return false
        if (!TorrentMagnet.isMagnet(url)) return false
        if (episodeVm.debridLabel() == null) return false
        return episodeVm.debridCached(url) != true
    }

    /** Magnets cuja consulta de cache (só leitura) já foi pedida nesta sessão. */
    private var debridCacheCheckedUrls = emptySet<String>()

    /**
     * Lote 19 item 7 (f): com o gate ligado, pede a consulta de cache só de
     * leitura (TorBox/Premiumize) p/ os magnets da lista assentada, p/ a
     * troca automática saber o que já está pronto sem resolver nada na conta.
     * Só os magnets novos de cada leva; sem magnet, sem conta ou com o gate
     * desligado, não faz nada. RD/AD não respondem sem resolver e ficam como
     * "não se sabe" (o gate veta na automática — ver [debridBlocksAutoSwitch]).
     */
    fun refreshDebridCacheForAutoSwitch() {
        if (!debridReadyOnly) return
        val fresh = currentSources.map { it.url }
            .filter { TorrentMagnet.isMagnet(it) && it !in debridCacheCheckedUrls }
        if (fresh.isEmpty()) return
        debridCacheCheckedUrls += fresh
        episodeVm.debridCheckCache(fresh, curSeason, curEpisode)
    }

    // Próxima FONTE (auto-fallback). Ainda não tentada, fugindo de outro filme, de fonte morta
    // e do addon que acabou de falhar. Retorna false se não sobrou nenhuma.
    // [slow] = troca por lentidão. Aí só vale fonte que não seja de outro filme, senão é melhor
    // seguir esperando a atual do que abrir o filme errado.
    fun nextMovieSource(slow: Boolean = false): Boolean {
        // Failover (Lote 17 item 1): a fonte atual falhou — o host ganha uma
        // falha na saúde aprendida; se foi travada (slow), conta a travada
        // p/ o downgrade adaptativo. A próxima vem do RANKING (não da ordem
        // de chegada): primeiro as vivas, do título certo e de addon que não
        // acabou de falhar; depois qualquer uma não tentada. Retoma a posição
        // via applyMovieSource (ver tryNextSource).
        currentSources.getOrNull(currentSourceIdx)?.let { cur ->
            noteSourceFailed(cur.url)
            if (slow) noteSourceStall(cur.url)
            if (cur.addon.isNotBlank()) failedAddons += cur.addon
        }
        val f = facts()
        val rankedOrder = rankedSourceOrder()
        // Lote 19 item 7 (f): o gate do debrid vale nas 3 passagens — a troca
        // automática nunca abre torrent que o debrid ainda não tem pronto. Sem
        // candidato que passe, retorna false e a tela de falha oferece a ação manual.
        val next = rankedOrder.firstOrNull { idx ->
            val s = f.getOrNull(idx) ?: return@firstOrNull false
            s.url !in triedUrls && !s.dead && !s.wrongTitle && s.addon !in failedAddons && !debridBlocksAutoSwitch(s.url)
        } ?: rankedOrder.firstOrNull { idx ->
            val s = f.getOrNull(idx) ?: return@firstOrNull false
            s.url !in triedUrls && !s.wrongTitle && !debridBlocksAutoSwitch(s.url)
        } ?: rankedOrder.firstOrNull { idx ->
            val u = f.getOrNull(idx)?.url ?: ""
            u !in triedUrls && !debridBlocksAutoSwitch(u)
        }
        if (next == null) return false
        if (slow && f[next].wrongTitle && f.getOrNull(currentSourceIdx)?.wrongTitle == false) return true
        applyMovieSource(next)
        return true
    }
    /** v1.13.0 (JOTV 1.0.19 §3.7): "Tentar novamente" na falha da troca
     *  automática — tenta de novo a fonte atual, que pode responder na 2ª
     *  vez, sem perder o ponto. O streamUrl vai a null antes de reaplicar:
     *  o efeito que prepara a mídia é keyado por streamUrl, e reaplicar a
     *  mesma URL não o redispararia. Sem fonte atual, não faz nada. */
    fun retryCurrentSource() {
        if (currentSources.getOrNull(currentSourceIdx) == null) return
        sourceFailed = false
        playerError = null
        movieStarted = false
        movieErrored = false
        streamUrl?.let { triedUrls.remove(it) }
        streamUrl = null
        applyMovieSource(currentSourceIdx)
    }
    /** v1.13.0: o overlay de falha tem algo a oferecer (tentar a mesma fonte
     *  ou escolher outra). Sem nada disso, a volta automática de 2,5 s continua. */
    val sourceFailedHasActions get() =
        currentSources.getOrNull(currentSourceIdx) != null || currentSources.size > 1
    /**
     * SÉRIE: completa a lista de fontes com as do EPISÓDIO atual.
     *
     * A tela de detalhe monta `playerSources` a partir das fontes do FILME, que numa série é
     * lista vazia. Resultado: ao abrir pelo "Continuar", o player ficava com uma fonte só e,
     * quando ela caía, não tinha pra onde ir e perdia o ponto.
     * Aqui as fontes do episódio entram sem encostar no que já está tocando.
     */
    /**
     * Retoma o tempo salvo quando quem abriu o player NÃO informou posição.
     *
     * Só o botão principal do detalhe passava startPositionMs. Clicar direto numa fonte da
     * lista, ou no dublado/legendado, abria do zero mesmo com progresso salvo. Aqui o próprio
     * player busca no histórico, então todo caminho de abertura passa a retomar.
     */
    // Episódio anterior (séries)
    fun playPrevEpisode() { if (type == "SERIES" && curEpisode > 1) goToEpisode(curEpisode - 1) }

    /**
     * Providers (addons) presentes na folha, na ordem em que apareceram — alimenta os
     * chips de filtro (Lote 15 item 2). Sem addon (fonte própria), fora da lista.
     */
    val sourceProviders: List<String> get() =
        stableSourceOrder().mapNotNull { currentSources.getOrNull(it)?.addon?.takeIf { a -> a.isNotBlank() } }.distinct()

    /** Rank de qualidade de uma fonte: o do stream, ou o do rótulo quando o stream não traz (Lote 23 item 13). */
    private fun sourceQualityRankOf(src: MoviePlayerSource): Int =
        src.stream?.qualityRank?.takeIf { it > 0 } ?: sourceQualityRank(src.label)

    /** O rank passa no filtro de qualidade do painel de fontes ("" = Todas).
     * 1440p cai em 1080p; sem rank conhecido, só "Todas" mostra. */
    fun matchesQualityFilter(rank: Int): Boolean = when (sourceQualityFilter) {
        "4K" -> rank >= 2160
        "1080p" -> rank in 1080..2159
        "720p" -> rank in 720..1079
        "480p" -> rank in 1..719
        else -> true
    }

    /** Chips de filtro do painel de fontes (Lote 23 item 13): "Todos" (zera os
     * dois filtros) + um por provider presente + um por qualidade. A mesma
     * lista alimenta o celular (toque) e a TV (D-pad, ver [sourceChipCursor]). */
    val sourceFilterChips: List<SourceFilterChip> get() = buildList {
        add(SourceFilterChip("Todos", sourceProviderFilter.isBlank() && sourceQualityFilter.isBlank(), key = "all", onClick = {
            sourceProviderFilter = ""; sourceQualityFilter = ""; sourcePanelIdx = 0
        }))
        sourceProviders.forEach { provider ->
            add(SourceFilterChip(provider, sourceProviderFilter.equals(provider, ignoreCase = true), key = "prov:$provider", onClick = {
                sourceProviderFilter = provider; sourcePanelIdx = 0
            }))
        }
        listOf("4K", "1080p", "720p", "480p").forEach { q ->
            add(SourceFilterChip(q, sourceQualityFilter == q, key = "q:$q", onClick = {
                sourceQualityFilter = q; sourcePanelIdx = 0
            }))
        }
    }

    /** "T2 E3" p/ os cards da folha quando for série (Lote 15 item 2). "" em filme. */
    private val episodeCode: String get() =
        if (type == "SERIES" && curSeason > 0 && curEpisode > 0) "T$curSeason E$curEpisode" else ""

    // Painel D-pad separado por tipo: "sources" (fontes+episódios), "subs" (legendas), "audio".
    val sourceActions: List<TvPlayerAction> get() {
        val playing = currentSources.getOrNull(currentSourceIdx)?.url
        // Lote 15 item 2 — filtro de provider (chips "Todos + addon"): só esconde o
        // que não é do provider; a ordem continua a congelada ([stableSourceOrder]).
        val providerFilter = sourceProviderFilter
        fun matchesFilter(addon: String): Boolean =
            providerFilter.isBlank() || addon.equals(providerFilter, ignoreCase = true)
        // Lote 14 item 4: os grupos seguem a ordem congelada da folha (ver
        // [stableSourceOrder]) — a ordem de chegada dos addons não reordena
        // nada. O `sortedByDescending` abaixo só move o grupo da fonte que
        // está TOCANDO p/ o topo, e só quando a fonte em reprodução muda
        // (escolha do usuário ou troca automática), nunca pela busca.
        val groups = groupReleases(stableSourceOrder().map { idx -> currentSources[idx] }.map { it.stream ?: com.aotv.app.data.remote.StremioStream(it.label, it.url, it.addon,
            qualityRank = com.aotv.app.data.remote.qualityRankOf(it.label)) })
            .sortedByDescending { g -> g.members.any { it.url == playing } }
        return buildList {
            // Lote 12 — Frente 2: alterna a vista da folha entre agrupada
            // por addon e ranking único da melhor p/ pior. Salva no perfil.
            add(TvPlayerAction(
                if (sourcesRanked) "Vista: ranking (melhor → pior)" else "Vista: agrupada por addon",
                false, sourcesRanked, header = "Fontes",
                sub = "Toque para alternar a vista da folha", keepOpen = true,
            ) {
                sourcesRanked = !sourcesRanked
                onSourcesRankedChange(sourcesRanked)
            })
            // Lote 17 item 1 — "Tentar a próxima fonte": a próxima do ranking
            // ainda não tentada, retomando a posição (ver tryNextSource).
            // Só aparece quando há outra fonte p/ tentar.
            if (currentSources.size > 1) add(TvPlayerAction(
                "Tentar a próxima fonte", false, false, header = "Fontes",
                sub = "Próxima do ranking, sem perder o ponto", keepOpen = true,
            ) {
                if (!tryNextSource()) toastMsg = "Não há outra fonte para tentar"
            })
            // Lote 17 item 2 — downgrade adaptativo: a fonte 1080p parou 3x
            // em 1 minuto. Oferece, não impõe: "Mudar para 720p?" ou manter.
            if (showDowngradeOffer) {
                add(TvPlayerAction(
                    "Mudar para 720p?", false, false, header = "Fonte instável",
                    sub = "A fonte 1080p parou 3 vezes em 1 minuto", keepOpen = true,
                ) {
                    if (!switchToLowerQuality()) { dismissDowngradeOffer(); toastMsg = "Não há fonte 720p para trocar" }
                })
                add(TvPlayerAction(
                    "Manter 1080p", false, false, header = "Fonte instável",
                    sub = "Continuar nesta fonte", keepOpen = true,
                ) { dismissDowngradeOffer() })
            }
            if (sourcesRanked) {
                // Ranking: lista única de TODAS as streams, da melhor p/
                // pior (mesmo escore do bestSourceIndex — ver
                // [rankedSourceOrder]). A #1 leva o "Melhor fonte" do
                // detalhe; a que está tocando segue marcada como ativa.
                // Lote 14 item 4: a ordem é a estável ([stableSourceOrder]) —
                // o ranking vale uma única vez, no assentamento da busca.
                // Lote 15 item 2: o filtro de provider esconde o resto; o
                // ranking (#1, #2…) vale DENTRO do que está visível.
                val order = stableSourceOrder().filter { idx ->
                    val src = currentSources.getOrNull(idx)
                    src != null && matchesFilter(src.addon) && matchesQualityFilter(sourceQualityRankOf(src))
                }
                // Score com motivo (Lote 17): o mesmo do detalhe.
                val scores = rankedByUrl()
                order.take(sourceLimit).forEachIndexed { rank, idx ->
                    val src = currentSources.getOrNull(idx) ?: return@forEachIndexed
                    val stream = src.stream ?: com.aotv.app.data.remote.StremioStream(src.label, src.url, src.addon,
                        qualityRank = com.aotv.app.data.remote.qualityRankOf(src.label))
                    // "Melhor fonte" só na vista sem filtro: filtrado, o #1 é
                    // o melhor DAQUELE provider/qualidade, não o melhor da folha.
                    val best = rank == 0 && providerFilter.isBlank() && sourceQualityFilter.isBlank()
                    // O card já mostra o addon no título — o sub não repete.
                    // T/E entra no card quando for série (Lote 15 item 2).
                    val scored = scores[src.url]
                    add(TvPlayerAction(src.label, true, src.url == playing, addon = src.addon, stream = stream,
                        sub = "#${rank + 1}" + (scored?.let { " · ${it.score}" }.orEmpty()) +
                            (if (best) " · Melhor fonte" else "") +
                            (scored?.reason?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()) +
                            (if (episodeCode.isNotBlank()) " · $episodeCode" else ""),
                        header = if (rank == 0) "Ranking da melhor para a pior" else "") {
                        markSourceTouched(src.url)
                        if (src.url != playing) applyMovieSource(idx, manual = true)
                    })
                }
                if (order.size > sourceLimit) add(TvPlayerAction("Mostrar mais", false, false,
                    sub = "${order.size - sourceLimit} fontes", keepOpen = true) { sourceLimit += 30 })
            } else {
                // Lote 15 item 2 + Lote 23 item 13: filtrado, só os grupos com
                // alguma fonte do provider E da qualidade (o filtro de
                // qualidade vale por membro, como o de provider).
                val shownGroups = if (providerFilter.isBlank() && sourceQualityFilter.isBlank()) groups
                    else groups.filter { g -> g.members.any { matchesFilter(it.addon) && matchesQualityFilter(it.qualityRank.takeIf { q -> q > 0 } ?: sourceQualityRank(it.label)) } }
                shownGroups.take(sourceLimit).forEach { group ->
                    val active = group.members.any { it.url == playing }
                    val src = group.members.firstOrNull { it.url == playing } ?: group.lead
                    add(TvPlayerAction(src.label, true, active, addon = src.addon, stream = src,
                        sub = episodeCode,
                        providers = group.members.map { it.addon }.distinct()) {
                        markSourceTouched(src.url)
                        if (!active) applyMovieSource(currentSources.indexOfFirst { it.url == src.url }, manual = true)
                    })
                }
                if (shownGroups.size > sourceLimit) add(TvPlayerAction("Mostrar mais", false, false,
                    sub = "${shownGroups.size - sourceLimit} versões", keepOpen = true) { sourceLimit += 30 })
            }
            // ── On Demand Xtream na folha (Lote 12 — Frente 2) ─────────
            // Mesma conta Xtream já configurada; sem conta ou sem resultado,
            // estado vazio honesto — nunca inventa resultado.
            if (onDemandLoading) {
                add(TvPlayerAction("Buscando no On Demand…", false, false, header = "On Demand Xtream",
                    sub = onDemandSourceName, disabled = true, keepOpen = true) {})
            } else if (onDemandSource == null) {
                add(TvPlayerAction("Sem conta Xtream configurada", false, false, header = "On Demand Xtream",
                    sub = "Adicione uma conta em Fontes de canais para ver o On Demand aqui", disabled = true, keepOpen = true) {})
            } else if (onDemandError != null) {
                add(TvPlayerAction("On Demand indisponível", false, false, header = "On Demand Xtream",
                    sub = onDemandError.orEmpty(), keepOpen = true) { onDemandLoadedFor = ""; loadOnDemand() })
            } else if (onDemandItems.isEmpty()) {
                add(TvPlayerAction("Nada no On Demand com este título", false, false, header = "On Demand Xtream",
                    sub = "Conta: $onDemandSourceName", disabled = true, keepOpen = true) {})
            } else {
                onDemandItems.forEachIndexed { i, hit ->
                    val detail = hit.item.year.takeIf { it.isNotBlank() }
                        ?: hit.item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
                    add(TvPlayerAction(hit.item.title, true, false, addon = "On Demand",
                        header = if (i == 0) "On Demand Xtream" else "",
                        sub = (if (hit.isSeries) "Série · 1º episódio" else "Filme") +
                            (detail?.let { " · $it" } ?: "") + " · $onDemandSourceName",
                        stream = com.aotv.app.data.remote.StremioStream(hit.item.title, "", "On Demand")) {
                        playOnDemand(hit)
                    })
                }
            }
        }
    }
    val subActions: List<TvPlayerAction> get() = buildList {
        if (textGroups.isNotEmpty() || externalSubs.isNotEmpty()) {
            add(TvPlayerAction("Desativada", false, selectedSubUrl == null && !subtitleOn) { pickSub(null, null) })
            // Lote 15 item 8 — uma linha por idioma, com o selo de contagem real
            // (como o seletor do Nuvio: "Inglês 8"). Agrupa por idioma; tocar
            // escolhe a 1ª opção do grupo (a melhor no ranking, que já vem
            // ordenado pelo idioma do perfil). A contagem só conta o que
            // existe de fato. Caveat: a 2ª opção do mesmo idioma (ex.: 2º
            // arquivo externo do addon) não tem mais linha própria.
            val embeddedByLang = textGroups
                .sortedBy { SubLangs.rank(it.getTrackFormat(0).language, subLang) }
                .groupBy { SubLangs.of(it.getTrackFormat(0).language)?.key ?: it.getTrackFormat(0).language.orEmpty() }
            embeddedByLang.values.forEachIndexed { i, group ->
                // Lote 23 item 12 (JOTV 1.0.22 §4.4): a faixa forçada do idioma
                // ganha linha própria "(forçada)" — dá p/ escolher à mão; o
                // automático nunca a impõe no lugar da completa.
                val fullTracks = group.filter { !isForcedTrack(it.getTrackFormat(0)) }
                val forcedTracks = group.filter { isForcedTrack(it.getTrackFormat(0)) }
                val g = fullTracks.firstOrNull() ?: group.first()
                val f = g.getTrackFormat(0)
                val name = f.label?.takeIf { it.isNotBlank() } ?: SubLangs.label(f.language)
                val displayName = if (fullTracks.isEmpty()) "$name (forçada)" else name
                val mainActive = subtitleOn && (if (fullTracks.isNotEmpty()) fullTracks.any { it.isSelected } else group.any { it.isSelected })
                add(TvPlayerAction(displayName, false, mainActive,
                    header = if (i == 0) "No vídeo" else "", count = group.size,
                    langKey = SubLangs.of(f.language)?.key ?: f.language.orEmpty(),
                    source = "No vídeo") { pickSub(g, null) })
                if (fullTracks.isNotEmpty() && forcedTracks.isNotEmpty()) {
                    val fg = forcedTracks.first()
                    val ff = fg.getTrackFormat(0)
                    val forcedName = (ff.label?.takeIf { it.isNotBlank() } ?: SubLangs.label(ff.language)) + " (forçada)"
                    add(TvPlayerAction(forcedName, false, subtitleOn && forcedTracks.any { it.isSelected },
                        count = forcedTracks.size,
                        langKey = SubLangs.of(ff.language)?.key ?: ff.language.orEmpty(),
                        source = "No vídeo") { pickSub(fg, null) })
                }
            }
            // Lote 17 item 14 — uma ação por legenda: o painel duas-colunas agrupa
            // por idioma na coluna "Idiomas" e mostra selo + ID por entrada.
            externalSubs.forEachIndexed { i, sub ->
                val source = sub.source.ifBlank { sub.label.substringAfter(" · ", "").ifBlank { "OpenSubtitles" } }
                add(TvPlayerAction(SubLangs.label(sub.lang), false, sub.url == selectedSubUrl,
                    header = if (i == 0) "Legendas externas" else "",
                    langKey = SubLangs.of(sub.lang)?.key ?: sub.lang,
                    source = source, entryId = sub.id, detail = sub.detail) { pickSub(null, sub) })
            }
            // Ajustes no fim da lista, sempre na mesma ordem, para nada pular de lugar ao tocar.
            add(TvPlayerAction("Tamanho", false, false, sub = subSizeLabel(subSize), header = "Ajustes", keepOpen = true) {
                subSize = nextSubSize(subSize); progressVm.subSize = subSize
            })
            add(TvPlayerAction("Fundo", false, false, sub = if (subBg) "Com fundo" else "Sem fundo", keepOpen = true) {
                subBg = !subBg; progressVm.subBg = subBg
            })
            // Personalização de legenda (v1.9.0): cor, fonte, opacidade do fundo e
            // contorno. Vale na hora para a embutida e para a externa, e fica salva
            // no perfil (mesmo caminho do tamanho e do fundo).
            add(TvPlayerAction("Cor", false, false, sub = subColorLabel(subColor), keepOpen = true) {
                subColor = nextSubColor(subColor); progressVm.subColor = subColor
            })
            add(TvPlayerAction("Fonte", false, false, sub = subFontLabel(subFont), keepOpen = true) {
                subFont = nextSubFont(subFont); progressVm.subFont = subFont
            })
            add(TvPlayerAction("Opacidade do fundo", false, false, sub = subBgOpacityLabel(subBgOpacity), keepOpen = true) {
                subBgOpacity = nextSubBgOpacity(subBgOpacity); progressVm.subBgOpacity = subBgOpacity
            })
            add(TvPlayerAction("Contorno", false, false, sub = if (subOutline) "Com contorno" else "Sem contorno", keepOpen = true) {
                subOutline = !subOutline; progressVm.subOutline = subOutline
            })
            val onlyExt = if (selectedSubUrl == null) "  ·  vale para legenda externa" else ""
            add(TvPlayerAction("Atrasar 0,5 s", false, false, sub = subDelayLabel(subDelay) + onlyExt, keepOpen = true) { subDelay = addDelay(subDelay, 0.5); persistPrimaryDelay() })
            add(TvPlayerAction("Adiantar 0,5 s", false, false, sub = "Use quando a legenda aparece depois da fala", keepOpen = true) { subDelay = addDelay(subDelay, -0.5); persistPrimaryDelay() })
            // Sincronização automática (Lote 9 item 1): mede o atraso da
            // legenda externa contra a embutida (ver runAutoSync). Só com a
            // pref ligada e uma legenda externa carregada; sem medição
            // possível, nada muda e o ajuste manual continua valendo.
            if (autoSyncEnabled && selectedSubUrl != null && externalCues.isNotEmpty()) {
                add(TvPlayerAction("Sincronizar automaticamente", false, false,
                    sub = if (autoSyncState == AutoSyncState.MEASURING) "Medindo — deixe o vídeo tocar…"
                    else "Mede o atraso contra a legenda do vídeo", keepOpen = true) { runAutoSync() })
            }
            // Baixa de novo a legenda externa selecionada pelo mesmo caminho do carregamento
            // inicial (episodeVm.subtitleCues). Só aparece com legenda externa selecionada:
            // o LaunchedEffect de selectedSubUrl só dispara quando a URL muda, então o
            // recarregamento é feito aqui, sem trocar a seleção.
            if (selectedSubUrl != null) add(TvPlayerAction("Recarregar legendas", false, false,
                sub = "Baixa de novo a legenda selecionada", keepOpen = true) {
                scope.launch {
                    val u = selectedSubUrl ?: return@launch
                    val cues = episodeVm.subtitleCues(u)
                    if (cues.isNotEmpty()) externalCues = cues
                    else toastMsg = "Não deu para recarregar essa legenda"
                }
            })
            // Segunda legenda no topo (Lote 16 item 3): outra externa (o
            // ExoPlayer renderiza 1 faixa embutida por vez). A 1ª não pode
            // ser a 2ª; o atraso é próprio da faixa (passo fino 0,1 s) e
            // fica salvo por faixa.
            if (externalSubs.isNotEmpty()) {
                add(TvPlayerAction("Sem 2ª legenda", false, secondarySubUrl == null, header = "Segunda legenda (topo)") {
                    secondarySubUrl = null
                    secondaryCues = emptyList()
                    dualSubState = dualSubState.copy(secondary = null)
                })
                externalSubs.forEach { sub ->
                    if (sub.url == selectedSubUrl) return@forEach
                    val track = externalTrackId(sub)
                    add(TvPlayerAction(SubLangs.label(sub.lang), false, sub.url == secondarySubUrl,
                        sub = sub.label.substringAfter(" · ", "").ifBlank { "OpenSubtitles" }) {
                        secondarySubUrl = sub.url
                        val store = SubDelayStore.fromContext(context)
                        dualSubState = withDelay(dualSubState.copy(secondary = track), track, store.delayFor(track))
                    })
                }
                if (secondarySubUrl != null) {
                    add(TvPlayerAction("Atrasar 2ª legenda 0,1 s", false, false, sub = "Atraso atual: ${secondaryDelayMs()} ms", keepOpen = true) {
                        dualSubState.secondary?.let { t ->
                            val next = adjustDelayMs(secondaryDelayMs(), DELAY_STEP_FINE_MS)
                            dualSubState = withDelay(dualSubState, t, next)
                            SubDelayStore.fromContext(context).setDelay(t, next)
                        }
                    })
                    add(TvPlayerAction("Adiantar 2ª legenda 0,1 s", false, false, sub = "Atraso próprio desta faixa", keepOpen = true) {
                        dualSubState.secondary?.let { t ->
                            val next = adjustDelayMs(secondaryDelayMs(), -DELAY_STEP_FINE_MS)
                            dualSubState = withDelay(dualSubState, t, next)
                            SubDelayStore.fromContext(context).setDelay(t, next)
                        }
                    })
                }
            }
        }
    }
    // Faixas da fonte atual e, abaixo, o som de outra fonte. Serve para ver a imagem boa de uma
    // versão legendada ouvindo a dublagem de outra.
    val audioActions: List<TvPlayerAction> get() = buildList {
        val ext = extUrl
        fun backToOwn() { val had = ext != null; dropExt(); if (had) saveExt() }
        if (audioGroups.size > 1) audioGroups.forEachIndexed { i, g ->
            add(TvPlayerAction(audioTrackLabel(g, i), false, ext == null && g.isSelected, header = if (i == 0) "Da fonte atual" else "") {
                backToOwn(); selectAudio(i)
            })
        } else add(TvPlayerAction("Som da própria fonte", false, ext == null, header = "Da fonte atual") { backToOwn() })
        // Dublado primeiro, o uso comum é imagem legendada com o som dublado de outra.
        val others = currentSources.filterIndexed { i, _ -> i != currentSourceIdx }
            .sortedByDescending { langOf(it.label) == "dub" }
        others.forEachIndexed { i, src ->
            val why = extAudioBlock(src.url)
            val sub = when {
                why.isNotBlank() -> why
                ext == src.url -> if (extHeld) "Carregando o som…" else "Tocando o som desta fonte"
                src.url in wrongUrls -> "Parece ser outro filme"
                else -> ""
            }
            add(TvPlayerAction(
                src.label, true, ext == src.url, addon = src.addon, sub = sub, disabled = why.isNotBlank(), keepOpen = true,
                // Hear it (Lote 19 item 3): transmitindo, o som da outra fonte sai
                // no celular em outro idioma e a TV mostra só a imagem.
                header = if (i == 0) (if (casting) "Ouvir no celular em outro idioma (a TV mostra só a imagem; o som segue a posição da TV, aproximada)" else "Som de outra fonte (a imagem continua a atual)") else "", stream = src.stream,
            ) {
                if (why.isNotBlank()) { toastMsg = why; return@TvPlayerAction }
                if (ext == src.url) return@TvPlayerAction
                audioDelay = 0.0
                toastMsg = "Carregando o som de ${extName(src, title)}…"
                setExt(src) { ok ->
                    if (ok) toastMsg = "Som de ${extName(src, title)}. Se a voz não bater com a boca, use Ajustar sincronia."
                }
            })
        }
        // Sincronia do som (Lote 14 item 3): a ação existe também sem som de
        // outra fonte — aí ela abre a barra p/ o áudio próprio (só atrasa).
        add(TvPlayerAction(
            "Ajustar sincronia", false, false, header = "Sincronia do som",
            sub = if (ext != null && casting) audioDelayLabel(audioDelay) + "  ·  o som do celular segue a TV (posição aproximada; ajuste fino aqui)"
            else if (ext != null) audioDelayLabel(audioDelay) + "  ·  o filme continua tocando"
            else audioDelayLabel(audioDelay) + "  ·  áudio da própria fonte (− adianta o som · + atrasa o som)",
        ) { syncBarOpen = true; controlsVisible = false })
    }
    val speedActions get() = speeds.mapIndexed { i, speed ->
        TvPlayerAction(playbackSpeedText(speed), false, i == speedIdx,
            sub = if (speed == 1f) "Velocidade normal" else if (speed < 1f) "Mais devagar" else "Mais rápido") {
            speedIdx = i
            // Salva no perfil: a próxima sessão abre na mesma velocidade.
            progressVm.playerSpeed = speed
            runCatching { currentPlayer().setPlaybackSpeed(speed) }
        }
    }
    val screenActions get() = screenModes.mapIndexed { i, mode ->
        val descriptions = listOf(
            "Ajusta sozinho: preenche quando o vídeo tem a proporção da tela, ajusta quando não tem",
            "Mantém a proporção do vídeo", "Ocupa a tela cortando as bordas", "Aproxima a imagem em 33%", "Ocupa a tela alterando a proporção")
        TvPlayerAction(mode.first, false, i == screenModeIdx, sub = descriptions[i]) {
            screenModeIdx = i
            // Lote 12 — Frente 2: o modo fica salvo no perfil e vale na próxima reprodução.
            onScreenModeChange(i)
        }
    }

    fun selectPanelSeason(target: Int) {
        if (target < 1 || (numSeasons > 0 && target > numSeasons)) return
        if (target == panelSeason && panelEpisodesLoading) return
        episodePanelJob?.cancel()
        panelSeason = target
        panelEpisodes = emptyList()
        panelEpisodesLoading = true
        sourcePanelIdx = 0
        episodePanelJob = scope.launch {
            val episodes = episodeVm.seasonEpisodes(tmdbId, target)
            coroutineContext.ensureActive()
            if (panelSeason == target) { panelEpisodes = episodes; panelEpisodesLoading = false }
        }
    }

    val episodeActions: List<TvPlayerAction> get() = buildList {
        add(TvPlayerAction("Temporada $panelSeason", false, false, keepOpen = true,
            seasonTabs = (1..numSeasons.coerceAtLeast(panelSeason)).toList(), selectedSeason = panelSeason,
            onSeasonPick = ::selectPanelSeason) {
            selectPanelSeason(panelSeason % numSeasons.coerceAtLeast(1) + 1)
        })
        if (panelEpisodesLoading) add(TvPlayerAction("Carregando episódios…", false, false, keepOpen = true) {})
        else if (panelEpisodes.isEmpty()) add(TvPlayerAction("Não foi possível carregar a temporada", false, false,
            sub = "Selecione para tentar novamente", keepOpen = true) { selectPanelSeason(panelSeason) })
        else panelEpisodes.forEach { ep ->
            val current = panelSeason == curSeason && ep.episodeNumber == curEpisode
            val future = ep.airDate.isNotBlank() && ep.airDate > com.aotv.app.data.local.dayString()
            add(TvPlayerAction(ep.name.ifBlank { "Episódio ${ep.episodeNumber}" }, false, current,
                sub = listOf("T$panelSeason E${ep.episodeNumber}", ep.runtimeMin.takeIf { it > 0 }?.let { "$it min" },
                    // (Lote 2 item 5) a nota some aqui também quando desligada.
                    ep.rating.takeIf { it > 0 && showEpisodeRatings }?.let { "★ " + com.aotv.app.ui.common.ratingText(it) },
                    if (future) "Em breve" else if (current) "Tocando agora" else null).filterNotNull().joinToString(" · "),
                image = ep.stillUrl.ifBlank { backdropUrl }.ifBlank { posterUrl }, detail = ep.overview,
                episode = true, disabled = future, keepOpen = future) {
                if (!future && !current) goToEpisode(ep.episodeNumber, panelSeason)
            })
        }
    }

    /** Ações do painel "Sleep timer" (escolha por sessão). */
    val sleepActions: List<TvPlayerAction> get() = buildList {
        add(TvPlayerAction("Desligado", false, sleepMinutes == null && !sleepAtEpisodeEnd,
            sub = "Sem timer") { cancelSleepTimer() })
        SLEEP_TIMER_MINUTES.forEach { m ->
            add(TvPlayerAction("$m min", false, sleepMinutes == m,
                sub = if (sleepMinutes == m) sleepRemainingLabel(sleepDeadlineMs) ?: "Pausa em $m minutos"
                else "Pausa em $m minutos", keepOpen = true) { startSleepTimer(m) })
        }
        add(TvPlayerAction("No fim do episódio", false, sleepAtEpisodeEnd,
            sub = "Pausa quando o episódio terminar") { startSleepAtEpisodeEnd() })
    }

    val panelActions get() = when (panelKind) {
        "subs" -> subActions
        "audio" -> audioActions
        "speed" -> speedActions
        "screen" -> screenActions
        "episodes" -> episodeActions
        "sleep" -> sleepActions
        else -> sourceActions
    }
    val panelTitle get() = when (panelKind) {
        "subs" -> "Legendas"
        "audio" -> "Áudio"
        "speed" -> "Velocidade"
        "screen" -> "Tela"
        "episodes" -> "Episódios"
        "color" -> "Cor do vídeo"
        "info" -> "Informações de reprodução"
        "eq" -> "Equalizador"
        "night" -> "Modo noturno"
        "xray" -> "X-Ray"
        "sleep" -> "Sleep timer"
        else -> "Fontes"
    }

    fun openDpadPanel(kind: String) {
        // Lote 24 item 10: abrir qualquer painel é interação.
        noteUserInteraction()
        panelKind = kind
        chipFocused = false
        sourcePanelIdx = 0
        sourcePanelOpen = true
        if (kind == "sources") sourceLimit = 30
        if (kind == "color") colorFocusIdx = 0
        // Lote 12 — Frente 2: abrir a folha de fontes busca o título no On
        // Demand Xtream (1x por título; sem conta, a seção fica vazia).
        if (kind == "sources") loadOnDemand()
        // Infos técnicas (Lote 4 item 4): lê os dados do stream na hora de abrir.
        // Lote 5 item 2: junto, os stats do torrent (se a fonte for P2P).
        if (kind == "info") { refreshTechInfo(); refreshTorrentStats() }
        if (kind == "episodes") selectPanelSeason(curSeason.coerceAtLeast(1))
        // X-Ray (Lote 21, v1.31.0): carrega elenco/diretor + resumo ao abrir.
        if (kind == "xray") loadXRay()
    }

    /** Carrega os dados do X-Ray a cada abertura do painel (sem TMDB, só os marcadores/curva). */
    fun loadXRay() {
        if (xrayLoading) return
        xrayLoading = true
        scope.launch {
            val credits = episodeVm.xrayCredits(tmdbId, type)
            val summary = episodeVm.xrayTitleSummary(title)
            xrayCast = credits.cast
            xrayDirector = credits.director
            xraySummary = summary
            xrayLoading = false
        }
    }
    // Chips do player (celular E TV: a mesma lista alimenta os dois — o toque
    // usa como `actions`, a TV como `tvChips`). Cada chip tem o id canônico
    // da pref `playerButtons` e a lista final passa por [orderPlayerChips]
    // (Fase C): ordem escolhida, desligado some, chip novo vai p/ o fim.
    val tvChips: List<PlayerChipItem> get() {
        val ordered = orderPlayerChips(buildList {
        add(PlayerChipItem(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pausar" else "Assistir", main = true) { togglePlay() })
        if (subActions.isNotEmpty()) add(PlayerChipItem(WebPlayerIcons.Subtitles, "Legendas", active = subtitleOn || selectedSubUrl != null, id = "subs") { openDpadPanel("subs") })
        if (audioGroups.size > 1 || currentSources.size > 1)
            add(PlayerChipItem(WebPlayerIcons.Audio, if (extUrl != null) (if (casting) "Ouvir no celular" else "Som de outra fonte") else "Áudio", active = extUrl != null, id = "audio") { openDpadPanel("audio") })
        if (currentSources.size > 1) add(PlayerChipItem(WebPlayerIcons.Sources, "Fontes", id = "sources") { openDpadPanel("sources") })
        if (type == "SERIES") {
            add(PlayerChipItem(WebPlayerIcons.Episodes, "Episódios", id = "episodes") { openDpadPanel("episodes") })
            if (curEpisode > 1) add(PlayerChipItem(WebPlayerIcons.Previous, "Anterior", id = "prev") { noteUserInteraction(); playPrevEpisode() })
            if (hasNextEpisode || hasNextSeason) add(PlayerChipItem(WebPlayerIcons.Next, "Próximo", id = "next") {
                noteUserInteraction()
                if (hasNextEpisode) playNextEpisode() else goToNextSeason()
            })
        }
        add(PlayerChipItem(WebPlayerIcons.Screen, "Tela ${screenModes[screenModeIdx].first.lowercase()}", active = screenModeIdx > 0, id = "screen") { openDpadPanel("screen") })
        val speed = speeds[speedIdx]
        add(PlayerChipItem(WebPlayerIcons.Speed, "Velocidade ${playbackSpeedText(speed)}", active = speed != 1f,
            value = if (speed != 1f) playbackSpeedText(speed) else "", id = "speed") { openDpadPanel("speed") })
        // Correção de cor (v1.9.0): chip novo "color" — NÃO está na lista
        // canônica DEFAULT_PLAYER_BUTTONS (não mexida); o orderPlayerChips
        // põe ids fora do catálogo no fim, então ele aparece por último.
        add(PlayerChipItem(Icons.Default.Tune, "Cor", active = !colorState.isDefault, id = "color") { openDpadPanel("color") })
        // Infos técnicas do vídeo (Lote 4 item 4, v1.12.0): mesmo padrão do chip
        // "color" — id fora do catálogo, vai p/ o fim e NÃO pode ser desligado
        // nas Configurações (é um painel de diagnóstico, não um botão configurável).
        add(PlayerChipItem(Icons.Default.Info, "Infos técnicas", id = "info") { openDpadPanel("info") })
        // Equalizador (Lote 9 item 3, v1.19.0): mesmo padrão dos chips
        // "color"/"info" — id fora do catálogo, vai p/ o fim e NÃO pode ser
        // desligado nas Configurações (o liga/desliga dele é a pref).
        add(PlayerChipItem(Icons.Default.Equalizer, "Equalizador", active = eqEnabled, id = "eq") { openDpadPanel("eq") })
        // Modo noturno + Clear Dialogue (Lote 16 item 1): mesmo padrão dos
        // chips "color"/"eq" — id fora do catálogo, vai p/ o fim e NÃO pode
        // ser desligado nas Configurações (a escolha é por título, no painel).
        add(PlayerChipItem(Icons.Default.DarkMode, "Modo noturno", active = nightModeState.nightOn || nightModeState.clearDialogueOn, id = "night") { openDpadPanel("night") })
        // Lote 24 — Frente B (v1.34.0): mesmo padrão dos chips "color"/"night"
        // — ids fora do catálogo canônico, vão p/ o fim e NÃO podem ser
        // desligados nas Configurações (o liga/desliga é a pref, no painel).
        add(PlayerChipItem(Icons.Default.AspectRatio, "Aspecto ${aspectMode.label}", active = aspectMode != AspectMode.ORIGINAL, id = "aspect") { openDpadPanel("aspect") })
        add(PlayerChipItem(Icons.Default.BrightnessHigh, "Ambilight", active = ambilightState.enabled, id = "ambilight") { openDpadPanel("ambilight") })
        add(PlayerChipItem(Icons.Default.Spa, "Sensorial", active = sensoryOn, id = "sensory") { openDpadPanel("sensory") })
        // X-Ray (Lote 21, v1.31.0): mesmo padrão dos chips "color"/"info" — id
        // fora do catálogo canônico, vai p/ o fim e NÃO pode ser desligado nas
        // Configurações (é um painel de contexto, não um botão configurável).
        add(PlayerChipItem(Icons.Default.Science, "X-Ray", id = "xray") { openDpadPanel("xray") })
        // Sleep timer (Lote 9 item 4, v1.19.0): mesmo padrão — id fora do
        // catálogo, vai p/ o fim. Escolha por sessão, sem pref persistente.
        add(PlayerChipItem(Icons.Default.Bedtime, "Sleep timer", active = sleepMinutes != null || sleepAtEpisodeEnd, id = "sleep") { openDpadPanel("sleep") })
        // Modo privado (Lote 2, item 7 — v1.10.0): mesmo padrão do chip "color"
        // — id fora do catálogo, vai p/ o fim e NÃO pode ser desligado nas
        // Configurações (é um estado, não um botão configurável). Ligado, o
        // player não salva progresso nem marca visto (ver togglePrivateMode).
        add(PlayerChipItem(Icons.Default.VisibilityOff, if (privateMode) "Privado" else "Modo privado", active = privateMode, id = "private") { togglePrivateMode() })
        // Player externo (Lote 4 item 1 — v1.12.0): mesmo padrão dos chips
        // "color"/"private" — id fora do catálogo canônico, vai p/ o fim e
        // NÃO pode ser desligado nas Configurações. Abre a fonte atual no
        // seletor do sistema (VLC, MX Player…). Com a pref "Player externo"
        // ligada, nem precisa tocar aqui: abre sozinho ao resolver a fonte.
        add(PlayerChipItem(Icons.Default.OpenInNew, "Player externo", id = "external") { openExternalPlayer() })
        // Baixar trecho (Lote 18 item 10 — CLIPES, v1.28.0): mesmo padrão dos
        // chips "color"/"info" — id "clip" fora do catálogo canônico, vai p/
        // o fim e NÃO pode ser desligado nas Configurações. Só aparece com
        // fonte ativa e duração conhecida (ao vivo não tem trecho). HLS abre
        // o seletor de início/fim; MP4/torrent avisam o motivo no toque.
        if (streamUrl != null && duration > 0) add(PlayerChipItem(Icons.Default.ContentCut, "Baixar trecho", id = "clip") { onClipChipTap() })
        if (currentSources.size > 1) add(PlayerChipItem(WebPlayerIcons.Pin, if (pinnedSource) "Fonte fixa" else "Fixar fonte", active = pinnedSource, id = "pin") {
            pinnedSource = !pinnedSource
            userLockedSource = pinnedSource
            toastMsg = if (pinnedSource) "Fonte fixada. O player não troca sozinho." else "Fonte solta. Volta a trocar quando a atual falhar."
        })
        // Pular trechos (v1.8.1): rótulo e alvo dependem do segmento ativo na posição
        // atual (resumo/abertura/encerramento/pós-créditos); o marcador aprendido
        // continua como fallback da abertura. O id "skip" é o da config da Fase C.
        activeSkipTarget()?.let { target ->
            add(PlayerChipItem(WebPlayerIcons.Skip, target.label, id = "skip") {
                // Lote 24 item 10: pular trecho à mão é interação.
                noteUserInteraction()
                if (target.goNext) {
                    if (hasNextEpisode) playNextEpisode() else goToNextSeason()
                } else {
                    currentPlayer().seekTo(target.targetMs); position = target.targetMs
                    if (target.learnedMark) introSkippedEp = curEpisode
                }
            })
        }
        }, playerButtonOrder)
        // Lote 12 — Frente 2: barra recolhida no celular (5 primeiros + ">");
        // expandida mostra todos. Na TV não recolhe (o D-pad precisa de
        // todos os botões na mesma fileira) — ver [collapsePlayerChips].
        return collapsePlayerChips(ordered, chipsExpanded, isTv,
            onExpand = { chipsExpanded = true }, onCollapse = { chipsExpanded = false })
    }

}

/** Faixa de qualidade a partir do rótulo da fonte (2160 = 4K). 0 = não dá pra saber. */
internal fun sourceQualityRank(label: String): Int = com.aotv.app.data.remote.qualityRankOf(label)

/**
 * Índice da fonte que melhor corresponde à preferência do usuário numa lista NOVA
 * (outro episódio ou outra temporada).
 *
 * Casar por rótulo não funciona: o addon muda o texto a cada episódio. A identidade que se
 * mantém é o ADDON, e dentro dele a qualidade. Ordem de preferência: mesmo addon e mesma
 * qualidade; mesmo addon na melhor qualidade que tiver; qualquer um na mesma qualidade;
 * e por último o primeiro da lista, que já vem na prioridade do gerenciador de addons.
 */
internal fun pickPreferredSource(
    sources: List<MoviePlayerSource>,
    preferredAddon: String,
    preferredQuality: Int,
): Int {
    if (sources.isEmpty()) return 0
    if (preferredAddon.isNotBlank()) {
        val doAddon = sources.withIndex().filter { it.value.addon.equals(preferredAddon, ignoreCase = true) }
        if (doAddon.isNotEmpty()) {
            doAddon.firstOrNull { sourceQualityRank(it.value.label) == preferredQuality }?.let { return it.index }
            return doAddon.maxBy { sourceQualityRank(it.value.label) }.index
        }
    }
    if (preferredQuality > 0) {
        sources.indexOfFirst { sourceQualityRank(it.label) == preferredQuality }
            .takeIf { it >= 0 }?.let { return it }
    }
    return 0
}
