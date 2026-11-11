package com.aotv.app.ui.movies

import android.view.KeyEvent
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
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaptionOff
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
import com.aotv.app.data.local.SubLangs
import com.aotv.app.data.torrent.TorrentMagnet
import com.aotv.app.data.torrent.Torrents
import com.aotv.app.ui.common.ImmersiveFullscreen
import com.aotv.app.ui.common.KeepScreenOn
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

import com.aotv.app.data.remote.SourceFacts
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
private fun mediaSourceFactory(context: android.content.Context): DefaultMediaSourceFactory {
    // OkHttpDataSource usa o DNS-over-HTTPS Cloudflare (AppDns.mediaClient). O torrent local tem cliente próprio.
    val calls = object : okhttp3.Call.Factory {
        override fun newCall(request: okhttp3.Request) =
            (if (request.url.host == "127.0.0.1") localMediaClient else com.aotv.app.data.remote.AppDns.mediaClient).newCall(request)
    }
    val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(calls)
        .setDefaultRequestProperties(STREAM_HEADERS)
    // DefaultDataSource delega file://, content:// e asset:// para as fontes locais.
    return DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http))
}

/** Nome curto da fonte para avisos ("FrostStream · Servidor Neon · Dublado"). */
private fun extName(src: MoviePlayerSource, title: String) =
    parseSourceLabel(src.label, src.addon, sourceQualityRank(src.label), title)
        .let { listOf(it.addon, it.server, it.language) }.filter { it.isNotBlank() }.joinToString(" · ")

// Torrent entrega o começo só depois de achar pares e baixar as primeiras peças, então espera bem mais.
private val localMediaClient by lazy {
    com.aotv.app.data.remote.AppDns.mediaClient.newBuilder().readTimeout(150, java.util.concurrent.TimeUnit.SECONDS).build()
}

/** Magnet vira o endereço local do motor P2P na hora de tocar, a fonte guardada continua sendo o magnet. */
private fun mediaItemFor(context: android.content.Context, url: String): MediaItem {
    val play = if (TorrentMagnet.isMagnet(url)) Torrents.get(context).playUrl(url) else url
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
    television: Boolean = com.aotv.app.ui.common.LocalIsTv.current,
    progressVm: PlayerProgressViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    episodeVm: EpisodePlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    ImmersiveFullscreen()
    KeepScreenOn()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Estado e regra do player moram na sessão. A função de tela ficou pequena e o Android volta
    // a compilar o código (antes passava do limite de instruções e rodava interpretado).
    val forceTv = television
    val s = remember {
        PlayerSession(
            context, scope, progressVm, episodeVm,
            embedUrl, title, tmdbId, type, posterUrl, lastEpisode, startPositionMs,
            backdropUrl, season, episode, episodeTotal, sources,
            forceTv = forceTv,
        )
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
    PlayerScreenContent(s)
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

    // Resume de SÉRIE: as fontes recebidas vêm do estado de FILME (vazias p/ série). Carrega
    // as fontes Stremio do episódio e toca a salva (ou a melhor) — corrige "não carrega a fonte".
    LaunchedEffect(Unit) {
        if (type == "SERIES" && episode > 0 && currentSources.none { it.url == activeUrl }) {
            // Sem preferência ainda nesta sessão (abriu fresco pelo detalhe) → herda o addon
            // da última vez que essa série tocou, pra não perguntar nos episódios seguintes.
            if (preferredAddon.isBlank()) preferredAddon = progressVm.lastAddon(tmdbId, type)
            val eps = episodeVm.stremioEpisodeSources(tmdbId, curSeason, episode)
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
        externalSubs = runCatching { episodeVm.subtitlesFor(tmdbId, type, curSeason, curEpisode) }.getOrDefault(emptyList())
        pickAutoSub()
    }
    LaunchedEffect(selectedSubUrl) {
        externalCues = emptyList()
        val u = selectedSubUrl ?: return@LaunchedEffect
        externalCues = episodeVm.subtitleCues(u)
        if (externalCues.isEmpty() && selectedSubUrl == u) toastMsg = "Não deu para carregar essa legenda"
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
            exoPlayer.setMediaSource(mediaSourceFactory(context).createMediaSource(mediaItemFor(context, play)))
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
                    readyUrl = streamUrl
                    switching = false // novo episódio no ar, tira o loading da troca
                }
                if (state == Player.STATE_ENDED) playbackEnded = true
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                movieErrored = true // fonte direta falhou → tenta a próxima
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.length > 0 }
                textGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.length > 0 }
                // Faixas novas (episódio ou fonte trocados). Com a legenda ligada no perfil, liga a
                // do idioma escolhido. Escolha à mão no vídeo atual não é desfeita.
                pickAutoSub()
            }
            // Resolução real do que está tocando (ex "1080p") — mostrada junto da fonte.
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                videoQualityLabel = videoResolutionLabel(size.width, size.height)
            }
        }
        exoPlayer.addListener(l); onDispose { exoPlayer.removeListener(l) }
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
    }
    // Transmitindo, o som vai pela TV. Desliga o da outra fonte.
    LaunchedEffect(casting) { if (casting) dropExt() }
    // Ao voltar ao título, religa o som da mesma fonte com o mesmo ajuste. As fontes podem
    // chegar aos poucos (série), por isso tenta de novo a cada lista nova.
    LaunchedEffect(readyUrl, currentSources) {
        if (extRestored || extUrl != null || readyUrl == null || casting) return@LaunchedEffect
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
            // VISTO ao passar de 90% da duração (série) — 1x por episódio.
            if (type == "SERIES" && curEpisode > 0 && duration > 0 &&
                position >= duration * 0.9 && markedWatchedEp != curEpisode
            ) {
                progressVm.markWatched(tmdbId, curSeason, curEpisode)
                markedWatchedEp = curEpisode
            }
            // AUTO-PLAY: aviso de próximo episódio só no ÚLTIMO 1 MINUTO (antes aparecia a 90%,
            // cedo demais em episódios longos).
            if (hasNextEpisode && duration > 60_000 && position >= duration - 60_000 && nextPromptEp != curEpisode) {
                nextPromptEp = curEpisode; promptIsSeason = false; nextCountdown = 10; showNextPrompt = true
            }
            // PRÉ-CARREGA o próximo (ou a próxima temporada) na METADE do episódio atual. Quando
            // o usuário aperta "Próximo", as fontes já estão na memória e a troca é instantânea.
            if (duration > 0 && position >= duration / 2 && prefetchedEp != curEpisode) {
                prefetchedEp = curEpisode
                when {
                    hasNextEpisode -> episodeVm.prefetchEpisode(tmdbId, curSeason, curEpisode + 1)
                    hasNextSeason -> episodeVm.prefetchEpisode(tmdbId, curSeason + 1, 1)
                }
            }
            // Último episódio da temporada com temporada seguinte → oferece a PRÓXIMA TEMPORADA
            // já nos créditos, não só quando o arquivo termina (muita fonte corta antes do fim).
            if (!hasNextEpisode && hasNextSeason && duration > 60_000 &&
                position >= duration - 60_000 && nextPromptEp != curEpisode
            ) {
                nextPromptEp = curEpisode; promptIsSeason = true; nextCountdown = 15; showNextPrompt = true
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
            extSync?.release(); exoPlayer.release(); mediaSession.release(); ScrubThumbnailer.release()
            // Saiu do player, o aparelho para de baixar e de compartilhar o torrent.
            Torrents.ifStarted()?.let { e -> Thread { e.stopAll() }.start() }
        }
    }

    // Registra o gatilho de PiP enquanto houver fonte tocando no ExoPlayer.
    DisposableEffect(streamUrl) {
        PipState.enter = if (streamUrl != null) ({ enterPip() }) else null
        onDispose { PipState.enter = null }
    }
    // Contagem regressiva do prompt — 0 = avança automaticamente (próximo ep OU próxima temporada).
    LaunchedEffect(showNextPrompt) {
        if (!showNextPrompt) return@LaunchedEffect
        while (nextCountdown > 0) { delay(1000); nextCountdown -= 1 }
        showNextPrompt = false
        if (promptIsSeason) goToNextSeason() else playNextEpisode()
    }
    // Fim da reprodução: série com próximo → mostra o card "Próximo episódio" (BOTÃO + contagem),
    // não avança/sai sozinho. Filme ou último episódio → salva e volta ao detalhe.
    LaunchedEffect(playbackEnded) {
        if (!playbackEnded) return@LaunchedEffect
        playbackEnded = false
        saveNow()
        when {
            // Tem próximo episódio na temporada → card "Próximo episódio".
            type == "SERIES" && hasNextEpisode -> { promptIsSeason = false; nextPromptEp = curEpisode; nextCountdown = 10; showNextPrompt = true }
            // Acabou a temporada e existe outra → card "Próxima temporada".
            type == "SERIES" && hasNextSeason -> { promptIsSeason = true; nextCountdown = 10; showNextPrompt = true }
            else -> onBack() // filme ou fim da série
        }
    }
    LaunchedEffect(type, tmdbId, curSeason, curEpisode) {
        if (type != "SERIES" || tmdbId <= 0 || curSeason <= 0 || curEpisode <= 0) return@LaunchedEffect
        if (preferredAddon.isBlank()) preferredAddon = progressVm.lastAddon(tmdbId, type)
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
    LaunchedEffect(movieErrored) {
        if (!movieErrored) return@LaunchedEffect
        movieErrored = false
        if (userLockedSource) sourceFailed = true
        else if (!nextMovieSource()) sourceFailed = true
    }
    // Fonte que não inicia. Os espelhos do mesmo arquivo às vezes demoram 15 s para entregar o
    // começo, e qual deles fica lento muda a cada pedido. Havendo outro espelho igual (mesmo
    // addon, idioma e resolução), a espera cai para 5 s e troca por ele. Sem espelho, 12 s e
    // pula pra próxima fonte.
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
            if (userLockedSource) sourceFailed = true
            else if (!nextMovieSource(slow = true)) sourceFailed = true
            return@LaunchedEffect
        }
        val hasMirror = !userLockedSource && sameMirror(facts(), currentSourceIdx, triedUrls) >= 0
        kotlinx.coroutines.delay(if (hasMirror) 5_000 else 12_000)
        if (movieStarted || streamUrl != u) return@LaunchedEffect
        if (userLockedSource) { sourceFailed = true; return@LaunchedEffect }
        val m = sameMirror(facts(), currentSourceIdx, triedUrls)
        if (m >= 0) {
            toastMsg = "Servidor lento. Tentando outro espelho da mesma fonte."
            applyMovieSource(m)
        } else if (!nextMovieSource(slow = true)) sourceFailed = true
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
            val n = nextIndex(f, triedUrls, emptySet())
            if (n >= 0 && !f[n].wrongTitle) {
                toastMsg = "Essa fonte era de outro filme com nome parecido. Tentando a próxima."
                applyMovieSource(n)
                return@LaunchedEffect
            }
        }
        if (upgradeDone || userLockedSource) return@LaunchedEffect
        delay(4_000)
        val cur = currentSources.getOrNull(currentSourceIdx) ?: return@LaunchedEffect
        val lang = langOf(cur.label)
        fun consider(): Boolean {
            if (upgradeDone || userLockedSource || casting || streamUrl != u) return true
            val curH = exoPlayer.videoSize.height
            if (curH <= 0) return false
            var best = -1
            var bestH = Math.round(curH * 1.25f)
            facts().forEachIndexed { i, f ->
                val h = f.probe?.height ?: 0
                if (i == currentSourceIdx || f.dead || f.wrongTitle || f.url in triedUrls || h == 0 || f.lang != lang) return@forEachIndexed
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
    // Buffering TRAVADO: fonte conectou (READY) mas empaca >12s sem avançar (stall infinito,
    // sem erro) → pula pra próxima. Cobre o caso "1ª fonte abre e fica no preto carregando".
    LaunchedEffect(buffering, streamUrl) {
        if (!buffering || streamUrl == null) return@LaunchedEffect
        val u = streamUrl
        val p2p = TorrentMagnet.isMagnet(u) && playVia != "debrid"
        var startPos = position
        while (true) {
            kotlinx.coroutines.delay(if (p2p) 30_000 else 12_000)
            if (!buffering || streamUrl != u || position > startPos + 500) return@LaunchedEffect
            // P2P que ainda recebe dado num ritmo razoável segue esperando em vez de trocar.
            if (p2p && (Torrents.ifStarted()?.status(u)?.downloadBps ?: 0) > 64_000) { startPos = position; continue }
            // Ainda travando na mesma fonte e sem progredir → troca (ou avisa, se travada à mão).
            if (userLockedSource) sourceFailed = true
            else if (!nextMovieSource(slow = true)) sourceFailed = true
            return@LaunchedEffect
        }
    }
    // Todas as fontes falharam → avisa e volta ao detalhe (não deixa preso no preto).
    LaunchedEffect(sourceFailed) {
        if (!sourceFailed) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        onBack()
    }

}

/** Tela do player. Vídeo, camadas por cima e painel. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreenContent(s: PlayerSession) = with(s) {
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
                val kc = ev.nativeKeyEvent.keyCode
                // Barra de sincronia do som. Setas mudam o atraso e segurar acelera.
                if (syncBarOpen) {
                    val r = ev.nativeKeyEvent.repeatCount
                    fun shift(base: Double) { audioDelay = addDelay(audioDelay, syncStep(base, r)) }
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
                            syncBarOpen = false; saveExt()
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
                    val n = panelActions.size
                    return@onKeyEvent when (kc) {
                        KeyEvent.KEYCODE_DPAD_UP -> { if (n > 0) sourcePanelIdx = (sourcePanelIdx - 1 + n) % n; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { if (n > 0) sourcePanelIdx = (sourcePanelIdx + 1) % n; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            panelActions.getOrNull(sourcePanelIdx)?.let { it.onRun(); if (!it.keepOpen) sourcePanelOpen = false }; true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (panelKind == "episodes" && sourcePanelIdx == 0 && numSeasons > 1) {
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
                if (locked) lockHintVisible = !lockHintVisible
                else controlsVisible = !controlsVisible
            }
            // Gestos (celular): metade direita = volume, metade esquerda = brilho. (off se travado)
            .then(
                if (streamUrl != null && !locked) Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { gestureHud = null },
                        onVerticalDrag = { change, dragAmount ->
                            val frac = -dragAmount / size.height.toFloat()
                            if (change.position.x > size.width / 2f) changeVolume(frac)
                            else changeBrightness(frac)
                            change.consume()
                        },
                    )
                } else Modifier
            ),
    ) {
        when {
            // ── Player custom (ExoPlayer) ──
            streamUrl != null -> {
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
                        it.resizeMode = screenModes[screenModeIdx].second
                        it.scaleX = if (screenModeIdx == 2) 1.33f else 1f
                        it.scaleY = if (screenModeIdx == 2) 1.33f else 1f
                        // Legenda embutida segue o tamanho e o fundo escolhidos.
                        it.subtitleView?.apply {
                            setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subSize)
                            setStyle(androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                if (subBg) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                if (subBg) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK, null,
                            ))
                        }
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )

                // Legenda externa desenhada aqui, com o atraso escolhido.
                if (selectedSubUrl != null && externalCues.isNotEmpty()) {
                    ExternalSubtitle(externalCues, exoPlayer, (subDelay * 1000).toLong(), subSize, subBg, isTv, controlsVisible || syncBarOpen)
                }

                // Transmitindo p/ Chromecast: a mídia roda na TV; celular vira controle.
                if (casting) CastingOverlay(title)

                // PULAR ABERTURA: aparece só quando a série tem marcador aprendido e a posição
                // está dentro dele. Fica na tela mesmo com os controles escondidos (tipo Netflix).
                val mark = introMark
                val inIntro = mark != null && type == "SERIES" && introSkippedEp != curEpisode &&
                    position >= (mark.first - 2_000L) && position < (mark.second - 1_000L)
                AnimatedVisibility(
                    visible = inIntro && !inPip && !locked,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    SkipIntroButton(raised = controlsVisible) {
                        mark?.let { m ->
                            currentPlayer().seekTo(m.second)
                            position = m.second
                            introSkippedEp = curEpisode
                        }
                    }
                }

                // Aviso curto no rodapé (ex "Abertura marcada").
                if (toastMsg.isNotBlank()) PlayerToast(toastMsg) { toastMsg = "" }

                // HUD de gesto: volume / brilho
                gestureHud?.let { (label, frac) -> GestureHud(label, frac) }

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
                if (showNextPrompt && !inPip && !locked) {
                    NextEpisodeCard(
                        isSeason = promptIsSeason,
                        line = if (promptIsSeason) "Temporada ${curSeason + 1}" else "T$curSeason · E${curEpisode + 1}",
                        countdown = nextCountdown,
                        onPlay = { if (promptIsSeason) goToNextSeason() else playNextEpisode() },
                        onCancel = { showNextPrompt = false },
                    )
                }

                AnimatedVisibility(controlsVisible && !inPip && !syncBarOpen, enter = fadeIn(), exit = fadeOut()) {
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

                    )
                }

                // Topo-direito: círculos pequenos (Transmitir · Girar · Travar · Voltar) — estilo print.
                AnimatedVisibility(
                    controlsVisible && !inPip, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    PlayerTopActions(
                        isTv = isTv,
                        onCast = {
                            runCatching {
                                val themed = android.view.ContextThemeWrapper(
                                    context, androidx.appcompat.R.style.Theme_AppCompat_DayNight
                                )
                                val selector = androidx.mediarouter.media.MediaRouteSelector.Builder()
                                    .addControlCategory(
                                        com.google.android.gms.cast.CastMediaControlIntent.categoryForCast(
                                            com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                                        )
                                    ).build()
                                androidx.mediarouter.app.MediaRouteChooserDialog(themed).apply {
                                    routeSelector = selector
                                }.show()
                            }
                        },
                        onRotate = {
                            val cur = activity?.requestedOrientation
                            activity?.requestedOrientation =
                                if (cur == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                                else
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onLock = { locked = true; controlsVisible = false },
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

        // Todas as fontes falharam: aviso central (o efeito acima volta ao detalhe em ~2.5s).
        if (sourceFailed) SourceFailedOverlay()

        if (syncBarOpen && extUrl != null && !inPip) {
            SyncBar(
                delaySec = audioDelay,
                onShift = { audioDelay = addDelay(audioDelay, it) },
                onBack5 = { seekBy(-5_000); controlsVisible = false },
                onZero = { audioDelay = 0.0 },
                onClose = { syncBarOpen = false; saveExt() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ── Seletor de FONTE (dentro do player) — troca mantendo o tempo ──
        if (sourcePanelOpen) {
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
            )
        }
    }
}

/**
 * Estado e regra de negócio de uma sessão do player. Antes tudo isso morava dentro do
 * composable e cada lambda capturava dezenas de estados, o que estourava o tamanho do método.
 */
@OptIn(UnstableApi::class)
internal class PlayerSession(
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
    // Velocidade de reprodução (chip "1x") — cicla 1x/1.25x/1.5x/2x.
    val speeds get() = PLAYBACK_SPEEDS
    var speedIdx by run { mutableIntStateOf(PLAYBACK_SPEEDS.indexOf(1f)) }
    var didSeek by run { mutableStateOf(false) }
    // Posição a retomar (continuar OU ao trocar de fonte mantém o tempo)
    var pendingSeekMs by run { mutableLongStateOf(startPositionMs) }
    // Seletor de fonte — lista MUTÁVEL (recarregada a cada troca de episódio).
    private var panelOpenState by mutableStateOf(false)
    var sourcePanelOpen: Boolean
        get() = panelOpenState
        set(v) { if (v != panelOpenState) sourcePanelIdx = 0; panelOpenState = v }
    var sourcePanelIdx by mutableIntStateOf(0) // foco D-pad no painel, zera ao abrir ou fechar
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
    var userLockedSource by run { mutableStateOf(false) }
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
    // Auto-play próximo episódio: prompt aparece a ~90% (antes dos créditos), conta 10s e avança.
    var nextPromptEp by run { mutableIntStateOf(-1) }
    var showNextPrompt by run { mutableStateOf(false) }
    var nextCountdown by run { mutableIntStateOf(10) }
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
    var toastMsg by run { mutableStateOf("") }          // aviso curto (ex "Abertura marcada")
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

    // Som de outra fonte. [extSync] segue o player principal, [audioDelay] em segundos.
    var extSync by run { mutableStateOf<ExtAudioSync?>(null) }
    var extUrl by run { mutableStateOf<String?>(null) }
    var extHeld by run { mutableStateOf(false) }
    var audioDelay by run { mutableDoubleStateOf(0.0) }
    var syncBarOpen by run { mutableStateOf(false) }
    var extRestored by run { mutableStateOf(false) }
    val extKey get() = "$type:$tmdbId"
    // Transmitindo p/ Chromecast (mídia roda na TV; celular vira controle)
    var casting by run { mutableStateOf(false) }
    // Overlay de gesto (volume/brilho) — null = escondido
    var gestureHud by run { mutableStateOf<Pair<String, Float>?>(null) }
    // Modos de tela (resizeMode do PlayerView)
    val screenModes = run {
        listOf(
            "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Preencher" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            "Esticar" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
    }
    var screenModeIdx by run { mutableIntStateOf(0) }
    // Tela travada (ignora toques até destravar)
    var locked by run { mutableStateOf(false) }
    var lockHintVisible by run { mutableStateOf(false) }

    val exoPlayer = run {
        // Foco de áudio do sistema: pausa sozinho numa ligação ou quando outro app toca som, e
        // para ao tirar o fone (sem isso o filme seguia tocando mudo pro alto-falante).
        val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()
        try {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory(context))
                .setAudioAttributes(audioAttrs, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    playWhenReady = true
                    // Legenda: começa desligada e pickAutoSub liga a do idioma do perfil quando
                    // ele deixou a legenda ligada (subWanted).
                    // Áudio: prefere português quando o arquivo trouxer mais de uma faixa.
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitleOn)
                        .setPreferredAudioLanguage("pt")
                        .build()
                }
        } catch (_: Exception) {
            ExoPlayer.Builder(context).setAudioAttributes(audioAttrs, true).setHandleAudioBecomingNoisy(true)
                .build().apply { playWhenReady = true }
        }
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

    // Seleciona uma faixa de ÁUDIO específica (índice no audioGroups)
    fun selectAudio(index: Int) {
        val g = audioGroups.getOrNull(index) ?: return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
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
    fun pickAutoSub() {
        if (!subWanted || subPicked) return
        val pick = SubLangs.choose(textGroups.map { it.getTrackFormat(0).language }, externalSubs.map { it.lang }, subLang) ?: return
        if (pick.embedded) {
            selectedSubUrl = null
            enableTextGroup(textGroups[pick.index])
            subtitleOn = true
        } else {
            if (subtitleOn) disableText()
            subtitleOn = false
            selectedSubUrl = externalSubs[pick.index].url
        }
    }
    // Escolha à mão. Vale para o vídeo atual e vira a preferência do perfil (ligada ou não), para
    // o próximo episódio e a próxima sessão abrirem do mesmo jeito.
    fun pickSub(g: androidx.media3.common.Tracks.Group?, ext: com.aotv.app.data.remote.ExternalSub?) {
        subPicked = true
        when {
            g != null -> { selectedSubUrl = null; enableTextGroup(g); subtitleOn = true }
            ext != null -> { if (subtitleOn) disableText(); subtitleOn = false; selectedSubUrl = ext.url }
            else -> { disableText(); subtitleOn = false; selectedSubUrl = null }
        }
        subWanted = g != null || ext != null
        progressVm.subtitleOn = subWanted
    }

    // ── Som de outra fonte ──
    fun saveExt() {
        val src = currentSources.firstOrNull { it.url == extUrl }
        progressVm.saveExtAudio(extKey, src?.let { sourceSig(it, title) }, audioDelay)
    }
    fun dropExt() {
        extSync?.release()
        extSync = null; extUrl = null; extHeld = false; syncBarOpen = false
    }
    /** Liga o som de [src] com a imagem atual. [onDone] diz se deu certo. */
    fun setExt(src: MoviePlayerSource, onDone: (Boolean) -> Unit) {
        dropExt()
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
            exoPlayer, ext, scope, audioDelay,
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
    fun seekBy(deltaMs: Long) {
        val p = currentPlayer()
        val d = if (duration > 0) duration else p.duration.coerceAtLeast(0L)
        val target = (p.currentPosition + deltaMs).coerceIn(0L, if (d > 0) d else Long.MAX_VALUE)
        p.seekTo(target); position = target; controlsVisible = true
    }
    fun togglePlay() { currentPlayer().let { it.playWhenReady = !it.playWhenReady }; controlsVisible = true }

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
    fun playNextEpisode() { showNextPrompt = false; if (hasNextEpisode) goToEpisode(curEpisode + 1) }
    // Próxima TEMPORADA: episódio 1 da temporada seguinte (mesma fonte quando existir).
    fun goToNextSeason() {
        if (type != "SERIES" || !hasNextSeason) return
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

    // Próxima FONTE (auto-fallback). Ainda não tentada, fugindo de outro filme, de fonte morta
    // e do addon que acabou de falhar. Retorna false se não sobrou nenhuma.
    // [slow] = troca por lentidão. Aí só vale fonte que não seja de outro filme, senão é melhor
    // seguir esperando a atual do que abrir o filme errado.
    fun nextMovieSource(slow: Boolean = false): Boolean {
        currentSources.getOrNull(currentSourceIdx)?.addon?.takeIf { it.isNotBlank() }?.let { failedAddons += it }
        val f = facts()
        val next = nextIndex(f, triedUrls, failedAddons)
        if (next < 0) return false
        if (slow && f[next].wrongTitle && f.getOrNull(currentSourceIdx)?.wrongTitle == false) return true
        applyMovieSource(next)
        return true
    }
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

    // Painel D-pad separado por tipo: "sources" (fontes+episódios), "subs" (legendas), "audio".
    val sourceActions: List<TvPlayerAction> get() {
        val playing = currentSources.getOrNull(currentSourceIdx)?.url
        val groups = groupReleases(currentSources.map { it.stream ?: com.aotv.app.data.remote.StremioStream(it.label, it.url, it.addon,
            qualityRank = com.aotv.app.data.remote.qualityRankOf(it.label)) })
            .sortedByDescending { g -> g.members.any { it.url == playing } }
        return buildList {
            groups.take(sourceLimit).forEach { group ->
                val active = group.members.any { it.url == playing }
                val src = group.members.firstOrNull { it.url == playing } ?: group.lead
                add(TvPlayerAction(src.label, true, active, addon = src.addon, stream = src,
                    providers = group.members.map { it.addon }.distinct()) {
                    if (!active) applyMovieSource(currentSources.indexOfFirst { it.url == src.url }, manual = true)
                })
            }
            if (groups.size > sourceLimit) add(TvPlayerAction("Mostrar mais", false, false,
                sub = "${groups.size - sourceLimit} versões", keepOpen = true) { sourceLimit += 30 })
        }
    }
    val subActions: List<TvPlayerAction> get() = buildList {
        if (textGroups.isNotEmpty() || externalSubs.isNotEmpty()) {
            add(TvPlayerAction("Desativada", false, selectedSubUrl == null && !subtitleOn) { pickSub(null, null) })
            // Cada faixa do vídeo com o nome do idioma, a do idioma do perfil primeiro.
            textGroups.sortedBy { SubLangs.rank(it.getTrackFormat(0).language, subLang) }.forEachIndexed { i, g ->
                val f = g.getTrackFormat(0)
                val name = f.label?.takeIf { it.isNotBlank() } ?: SubLangs.label(f.language)
                add(TvPlayerAction(name, false, subtitleOn && g.isSelected, header = if (i == 0) "No vídeo" else "") { pickSub(g, null) })
            }
            externalSubs.forEachIndexed { i, sub ->
                add(TvPlayerAction(sub.label, false, selectedSubUrl == sub.url, header = if (i == 0) "Legendas externas" else "") { pickSub(null, sub) })
            }
            // Ajustes no fim da lista, sempre na mesma ordem, para nada pular de lugar ao tocar.
            add(TvPlayerAction("Tamanho", false, false, sub = subSizeLabel(subSize), header = "Ajustes", keepOpen = true) {
                subSize = nextSubSize(subSize); progressVm.subSize = subSize
            })
            add(TvPlayerAction("Fundo", false, false, sub = if (subBg) "Com fundo" else "Sem fundo", keepOpen = true) {
                subBg = !subBg; progressVm.subBg = subBg
            })
            val onlyExt = if (selectedSubUrl == null) "  ·  vale para legenda externa" else ""
            add(TvPlayerAction("Atrasar 0,5 s", false, false, sub = subDelayLabel(subDelay) + onlyExt, keepOpen = true) { subDelay = addDelay(subDelay, 0.5) })
            add(TvPlayerAction("Adiantar 0,5 s", false, false, sub = "Use quando a legenda aparece depois da fala", keepOpen = true) { subDelay = addDelay(subDelay, -0.5) })
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
                header = if (i == 0) "Som de outra fonte (a imagem continua a atual)" else "", stream = src.stream,
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
        if (ext != null) add(TvPlayerAction(
            "Ajustar sincronia", false, false, header = "Sincronia do som",
            sub = audioDelayLabel(audioDelay) + "  ·  o filme continua tocando",
        ) { syncBarOpen = true; controlsVisible = false })
    }
    val speedActions get() = speeds.mapIndexed { i, speed ->
        TvPlayerAction(playbackSpeedText(speed), false, i == speedIdx,
            sub = if (speed == 1f) "Velocidade normal" else if (speed < 1f) "Mais devagar" else "Mais rápido") {
            speedIdx = i
            runCatching { currentPlayer().setPlaybackSpeed(speed) }
        }
    }
    val screenActions get() = screenModes.mapIndexed { i, mode ->
        val descriptions = listOf("Mantém a proporção do vídeo", "Ocupa a tela cortando as bordas", "Aproxima a imagem em 33%", "Ocupa a tela alterando a proporção")
        TvPlayerAction(mode.first, false, i == screenModeIdx, sub = descriptions[i]) { screenModeIdx = i }
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
                    ep.rating.takeIf { it > 0 }?.let { "★ " + com.aotv.app.ui.common.ratingText(it) },
                    if (future) "Em breve" else if (current) "Tocando agora" else null).filterNotNull().joinToString(" · "),
                image = ep.stillUrl.ifBlank { backdropUrl }.ifBlank { posterUrl }, detail = ep.overview,
                episode = true, disabled = future, keepOpen = future) {
                if (!future && !current) goToEpisode(ep.episodeNumber, panelSeason)
            })
        }
    }

    val panelActions get() = when (panelKind) {
        "subs" -> subActions
        "audio" -> audioActions
        "speed" -> speedActions
        "screen" -> screenActions
        "episodes" -> episodeActions
        else -> sourceActions
    }
    val panelTitle get() = when (panelKind) {
        "subs" -> "Legendas"
        "audio" -> "Áudio"
        "speed" -> "Velocidade"
        "screen" -> "Tela"
        "episodes" -> "Episódios"
        else -> "Fontes"
    }

    fun openDpadPanel(kind: String) {
        panelKind = kind
        chipFocused = false
        sourcePanelIdx = 0
        sourcePanelOpen = true
        if (kind == "sources") sourceLimit = 30
        if (kind == "episodes") selectPanelSeason(curSeason.coerceAtLeast(1))
    }
    val tvChips: List<PlayerChipItem> get() = buildList {
        add(PlayerChipItem(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pausar" else "Assistir", main = true) { togglePlay() })
        if (subActions.isNotEmpty()) add(PlayerChipItem(WebPlayerIcons.Subtitles, "Legendas", active = subtitleOn || selectedSubUrl != null) { openDpadPanel("subs") })
        if (audioGroups.size > 1 || currentSources.size > 1)
            add(PlayerChipItem(WebPlayerIcons.Audio, if (extUrl != null) "Som de outra fonte" else "Áudio", active = extUrl != null) { openDpadPanel("audio") })
        if (currentSources.size > 1) add(PlayerChipItem(WebPlayerIcons.Sources, "Fontes") { openDpadPanel("sources") })
        if (type == "SERIES") {
            add(PlayerChipItem(WebPlayerIcons.Episodes, "Episódios") { openDpadPanel("episodes") })
            if (curEpisode > 1) add(PlayerChipItem(WebPlayerIcons.Previous, "Anterior") { playPrevEpisode() })
            if (hasNextEpisode || hasNextSeason) add(PlayerChipItem(WebPlayerIcons.Next, "Próximo") {
                if (hasNextEpisode) playNextEpisode() else goToNextSeason()
            })
        }
        add(PlayerChipItem(WebPlayerIcons.Screen, "Tela ${screenModes[screenModeIdx].first.lowercase()}", active = screenModeIdx > 0) { openDpadPanel("screen") })
        val speed = speeds[speedIdx]
        add(PlayerChipItem(WebPlayerIcons.Speed, "Velocidade ${playbackSpeedText(speed)}", active = speed != 1f,
            value = if (speed != 1f) playbackSpeedText(speed) else "") { openDpadPanel("speed") })
        if (currentSources.size > 1) add(PlayerChipItem(WebPlayerIcons.Pin, if (pinnedSource) "Fonte fixa" else "Fixar fonte", active = pinnedSource) {
            pinnedSource = !pinnedSource
            userLockedSource = pinnedSource
            toastMsg = if (pinnedSource) "Fonte fixada. O player não troca sozinho." else "Fonte solta. Volta a trocar quando a atual falhar."
        })
        introMark?.let { (_, end) ->
            if (type == "SERIES" && introSkippedEp != curEpisode && position < end - 1_000L)
                add(PlayerChipItem(WebPlayerIcons.Skip, "Pular abertura") {
                    currentPlayer().seekTo(end); position = end; introSkippedEp = curEpisode
                })
        }
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
