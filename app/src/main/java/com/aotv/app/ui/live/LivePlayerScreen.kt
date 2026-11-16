package com.aotv.app.ui.live

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import android.widget.Toast
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.recordings.RecordingService
import com.aotv.app.data.recordings.RecordingStore
import com.aotv.app.data.recordings.ScheduledRecording
import com.aotv.app.ui.movies.ColorFilterDebouncer
import com.aotv.app.ui.movies.ColorFilterPanel
import com.aotv.app.ui.movies.ColorFilterState
import com.aotv.app.ui.movies.ColorPreset
import com.aotv.app.ui.movies.DaltonismMode
import com.aotv.app.ui.movies.RecordProgramDialog
import com.aotv.app.ui.movies.buildColorEffects
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Player de canal ao vivo. Toca HLS ou TS com os cabeçalhos que a lista pede, mostra o que está
 * passando e troca de canal pela lista lateral ou pelas setas.
 *
 * Lote 8 (v1.18.0): canal reserva (outro canal com o mesmo nome de outra fonte, 1 tentativa),
 * erro real na tela, faixa de áudio lembrada por canal e registro no Continuar assistindo.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    channelId: String,
    onBack: () -> Unit,
    viewModel: LivePlayerViewModel = hiltViewModel(),
    // Catch-up (v1.17.0): início e fim do programa, em epoch segundos; 0 = ao vivo normal.
    catchupStart: Long = 0L,
    catchupStop: Long = 0L,
    // Lote 12 (v1.22.0): abrir o guia a partir do player — voltar do guia
    // retorna ao player (a rota do guia fica por cima na pilha). Sem ele,
    // o botão do guia não aparece.
    onOpenGuide: (String) -> Unit = {},
) {
    com.aotv.app.ui.common.ImmersiveFullscreen()
    com.aotv.app.ui.common.KeepScreenOn()
    val context = LocalContext.current
    val st by viewModel.state.collectAsStateWithLifecycle()
    var currentId by remember { mutableStateOf(channelId) }
    val channel = st.channels.firstOrNull { it.id == currentId }
    // O listener do player é criado uma vez: lê o canal por aqui, sempre atual.
    val latestChannelState = rememberUpdatedState(channel)
    // A lista lateral segue o mesmo grupo do canal aberto (favoritos entram juntos).
    val siblings = remember(st.channels, channel?.group) { st.channels.filter { it.group == channel?.group }.ifEmpty { st.channels } }
    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var retries by remember { mutableIntStateOf(0) }
    // Lote 8 (v1.18.0): canal reserva em uso (1 tentativa), detalhe do erro
    // real do player, seletor de faixa de áudio e a quem a faixa lembrada
    // já foi aplicada nesta sessão.
    var backupId by remember { mutableStateOf<String?>(null) }
    var failDetail by remember { mutableStateOf<String?>(null) }
    var showAudioPicker by remember { mutableStateOf(false) }
    // Lote 16 item 8: painel de cor no ao vivo (botão "Cor" nos controles).
    var showColorPanel by remember { mutableStateOf(false) }
    var audioAppliedFor by remember { mutableStateOf<String?>(null) }
    // Lote 12 — Frente 2: gravar o programa atual (ou o de catch-up).
    var showRecordDialog by remember { mutableStateOf(false) }
    val recordStore = remember { RecordingStore(context) }
    val recordPrefs = remember { PlatformPrefs(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    // Catch-up: programa passado vira uma sessão com início/fim; sem URL de catch-up,
    // cai para o ao vivo e avisa. "Voltar ao vivo" desliga o modo na sessão.
    val isCatchup = catchupStart > 0L
    val catchupDurationMs = if (isCatchup) (catchupStop - catchupStart) * 1000L else 0L
    var showCatchupUnavailable by remember { mutableStateOf(false) }
    var forceLive by remember { mutableStateOf(false) }
    val inCatchup = isCatchup && !forceLive
    var positionMs by remember { mutableLongStateOf(0L) }
    // Lote 16 (v1.26.0): filtro de cor na TV ao vivo — o MESMO
    // ColorFilterState/buildColorEffects do player de filmes, com as
    // MESMAS prefs por perfil (pref_player_color_*, via rawPref) e o
    // MESMO debounce (ColorFilterDebouncer). O painel abre pelo botão
    // "Cor" nos controles (abaixo) e salva nas mesmas prefs do perfil.
    var liveColorState by remember {
        mutableStateOf(
            ColorFilterState(
                brightness = (recordPrefs.rawPref("pref_player_color_brightness") as? Float) ?: 0f,
                contrast = (recordPrefs.rawPref("pref_player_color_contrast") as? Float) ?: 1f,
                saturation = (recordPrefs.rawPref("pref_player_color_saturation") as? Float) ?: 1f,
                hueDegrees = (recordPrefs.rawPref("pref_player_color_hue") as? Float) ?: 0f,
                preset = (recordPrefs.rawPref("pref_player_color_preset") as? String) ?: ColorPreset.PADRAO.id,
                daltonism = DaltonismMode.fromId((recordPrefs.rawPref("pref_player_color_daltonism") as? String) ?: "off"),
            )
        )
    }
    var prevLiveColorState by remember { mutableStateOf(liveColorState) }

    val activity = context as? android.app.Activity
    val player = remember {
        // Buffer da pref `pref_player_buffer` NÃO vale aqui (Lote 5 item 1,
        // v1.14.0): a TV ao vivo tem o próprio player, sem LoadControl
        // configurado — o ajuste só vale p/ filmes e episódios.
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    // Duração do programa p/ o slider de catch-up (o player ainda não sabe: usa o programa).
    val durationMs = player.duration.takeIf { it > 0 } ?: catchupDurationMs
    // Posição do slider de catch-up, atualizada 1x por segundo.
    LaunchedEffect(inCatchup) {
        while (inCatchup) {
            positionMs = player.currentPosition
            delay(1_000)
        }
    }
    // Ao entrar, trava em paisagem; ao sair, solta o player e volta a tela para o automático.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            player.release()
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }
    /**
     * Aplica a faixa de áudio lembrada deste canal (Lote 8, v1.18.0): procura,
     * entre as faixas de áudio do stream, a que tem o idioma (ou rótulo)
     * guardado e trava a seleção nela via trackSelectionParameters. Sem faixa
     * igual, deixa o padrão do ExoPlayer. [tracks] vem do listener quando há;
     * sem ele, lê do player.
     */
    fun applyAudioTrack(lang: String, tracks: Tracks? = null) {
        val current = tracks ?: player.currentTracks
        var foundGroup: Tracks.Group? = null
        var foundTrack = -1
        for (gi in 0 until current.groups.size) {
            val g = current.groups[gi]
            if (g.type != C.TRACK_TYPE_AUDIO) continue
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val key = f.language?.takeIf { it.isNotBlank() } ?: f.label.orEmpty()
                if (key.isNotBlank() && key.equals(lang, ignoreCase = true)) {
                    foundGroup = g
                    foundTrack = ti
                    break
                }
            }
            if (foundGroup != null) break
        }
        val group = foundGroup ?: return
        if (foundTrack < 0) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, foundTrack))
            .build()
    }

    /** Aplica a faixa lembrada do canal atual, uma vez por canal (ver [applyAudioTrack]). */
    fun applySavedAudioIfNeeded(tracks: Tracks? = null) {
        val c = latestChannelState.value ?: return
        if (audioAppliedFor == c.id) return
        val saved = viewModel.savedAudio(c.id)
        if (saved == null) {
            audioAppliedFor = c.id
            return
        }
        val current = tracks ?: player.currentTracks
        if (current.groups.isEmpty()) return
        applyAudioTrack(saved, current)
        audioAppliedFor = c.id
    }

    /** Faixas de áudio do stream atual, p/ o seletor (chave → rótulo). */
    fun audioTrackKeys(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val current = player.currentTracks
        for (gi in 0 until current.groups.size) {
            val g = current.groups[gi]
            if (g.type != C.TRACK_TYPE_AUDIO) continue
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val key = f.language?.takeIf { it.isNotBlank() } ?: f.label.orEmpty()
                val label = f.label?.takeIf { it.isNotBlank() }
                    ?: f.language?.takeIf { it.isNotBlank() }
                    ?: "Faixa ${out.size + 1}"
                out += (key.ifBlank { label }) to label
            }
        }
        return out
    }

    /** (Re)aplica o filtro de cor no player ao vivo, sem reiniciar o vídeo. */
    fun applyLiveColorEffects() {
        runCatching { player.setVideoEffects(buildColorEffects(liveColorState)) }
    }

    /** Salva o estado do filtro nas prefs do perfil (as mesmas do player de filmes). */
    fun saveLiveColorState(ns: ColorFilterState) {
        recordPrefs.writeRawPref("pref_player_color_brightness", ns.brightness)
        recordPrefs.writeRawPref("pref_player_color_contrast", ns.contrast)
        recordPrefs.writeRawPref("pref_player_color_saturation", ns.saturation)
        recordPrefs.writeRawPref("pref_player_color_hue", ns.hueDegrees)
        recordPrefs.writeRawPref("pref_player_color_preset", ns.preset)
        recordPrefs.writeRawPref("pref_player_color_daltonism", ns.daltonism.id)
    }

    /** Novo estado vindo do painel: salva; a aplicação vai pelo LaunchedEffect com debounce. */
    fun applyLiveColorState(ns: ColorFilterState) {
        liveColorState = ns
        saveLiveColorState(ns)
    }

    val liveColorScope = rememberCoroutineScope()
    val liveColorDebouncer = remember {
        ColorFilterDebouncer(liveColorScope) { applyLiveColorEffects() }
    }

    /** Ao soltar o slider do painel: aplica o filtro na hora (cancela o debounce pendente). */
    fun onLiveColorSliderFinished() {
        liveColorDebouncer.applyNow()
    }

    // Slider mexeu: espera ~200 ms sem mudança p/ aplicar (mesmo fix do
    // player de filmes — Lote 16 item 8); preset/daltonismo aplicam na hora.
    LaunchedEffect(liveColorState) {
        liveColorDebouncer.onStateChanged(liveColorState, prevLiveColorState)
        prevLiveColorState = liveColorState
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { failed = false; retries = 0; applySavedAudioIfNeeded() }
            }
            override fun onPlayerError(error: PlaybackException) {
                failed = true
                failDetail = error.message?.takeIf { it.isNotBlank() }
            }
            override fun onTracksChanged(tracks: Tracks) { applySavedAudioIfNeeded(tracks) }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun play(c: LiveChannel) {
        failed = false
        failDetail = null
        buffering = true
        audioAppliedFor = null
        // Catch-up: resolve a URL da gravação; sem ela, toca o ao vivo e avisa.
        val targetUrl = if (inCatchup) {
            val resolved = viewModel.getCatchupUrl(c, catchupStart, catchupStop)
            if (resolved == null) {
                showCatchupUnavailable = true
                c.url
            } else resolved
        } else c.url
        val http = OkHttpDataSource.Factory(com.aotv.app.data.remote.AppDns.mediaClient)
            .setDefaultRequestProperties(c.headers)
            .setUserAgent(c.headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android) AOTV")
        val item = MediaItem.Builder().setUri(targetUrl).apply {
            val path = targetUrl.substringBefore('?').lowercase()
            if (path.endsWith(".m3u8") || path.endsWith(".m3u")) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        // Lote 16 (v1.26.0): filtro de cor no ao vivo — 1x antes do
        // prepare, como no player de filmes (setVideoEffects aceito:
        // o player ao vivo é um ExoPlayer comum).
        player.setVideoEffects(buildColorEffects(liveColorState))
        player.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
        player.prepare()
        // Lote 8 (v1.18.0): registra no Continuar assistindo (o mesmo
        // histórico dos filmes e séries), com o programa atual — respeita a
        // pref e o modo privado (nada é gravado no privado).
        val (currentProgram, _) = viewModel.nowNext(c.id, System.currentTimeMillis())
        viewModel.recordLiveContinue(c, currentProgram)
        viewModel.watched(c.id)
    }

    // ── Lote 12 — Frente 2: gravar o programa atual ─────────────────────
    // Ao vivo: o programa do guia agora. Em catch-up: o programa da janela
    // de catch-up (o título e os horários vêm da janela, não do "agora").
    // Sem programa no guia, não há o que gravar — o botão avisa, sem inventar.
    val recordProgram = channel?.let { c ->
        if (inCatchup) viewModel.nowNext(c.id, catchupStart * 1000L).first
        else viewModel.nowNext(c.id, now).first
    }
    val recordStartMs = if (inCatchup) catchupStart * 1000L else recordProgram?.start ?: 0L
    val recordStopMs = if (inCatchup) catchupStop * 1000L else recordProgram?.stop ?: 0L
    val recordTitle = recordProgram?.title?.takeIf { it.isNotBlank() }

    fun onRecordClick() {
        val c = channel ?: return
        if (recordTitle == null || recordStartMs <= 0L || recordStopMs <= recordStartMs) {
            Toast.makeText(context, "Sem programa no guia para gravar agora", Toast.LENGTH_LONG).show()
            return
        }
        // Uma gravação por vez (padrão do Lote 8): o serviço recusa a 2ª.
        if (recordStore.list().any { it.status == ScheduledRecording.STATUS_RECORDING }) {
            Toast.makeText(context, "Já há uma gravação em andamento", Toast.LENGTH_LONG).show()
            return
        }
        // Aviso de espaço (padrão do Lote 8): o serviço pára abaixo do limite.
        val freeMb = runCatching {
            android.os.StatFs(context.filesDir.path).availableBytes / (1024L * 1024L)
        }.getOrDefault(0L)
        val limitMb = recordPrefs.recordLowSpaceMb
        if (limitMb > 0 && freeMb < limitMb) {
            Toast.makeText(
                context,
                "Espaço livre abaixo de $limitMb MB ($freeMb MB livres) — libere espaço antes de gravar",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        showRecordDialog = true
    }

    // Lote 13 — Frente 1: placar fixado na tela cheia (item 19) — o placar
    // do jogo (só quando o texto do EPG traz um) fica visível mesmo com os
    // controles escondidos; sem placar no texto, sem chip na tela.
    val pinned = channel?.let { viewModel.pinnedScore(it.id, now) }

    LaunchedEffect(channel?.id) { channel?.let { play(it) } }
    // Canal caiu. Tenta de novo duas vezes; depois, com o canal reserva
    // ligado, tenta UMA vez outro canal com o mesmo nome de outra fonte
    // (Lote 8, v1.18.0); se ele também falhar, mostra o erro real.
    LaunchedEffect(failed) {
        if (!failed || channel == null) return@LaunchedEffect
        if (retries < 2) {
            delay(2_500); retries++; play(channel)
        } else if (backupId == null && viewModel.backupEnabled) {
            val backup = viewModel.backupChannel(channel)
            if (backup != null) {
                backupId = backup.id
                retries = 0
                currentId = backup.id
            }
        }
    }
    LaunchedEffect(overlay, panel) { if (overlay && !panel) { delay(5_000); overlay = false } }

    fun step(delta: Int) {
        if (siblings.isEmpty()) return
        val i = siblings.indexOfFirst { it.id == currentId }
        backupId = null
        currentId = siblings[((if (i < 0) 0 else i) + delta + siblings.size) % siblings.size].id
        overlay = true
    }

    // Erro real só quando não há mais o que tentar: com reserva disponível,
    // a troca acontece sozinha e a tela não pisca o erro no meio.
    val backupAvailable = channel != null && backupId == null && viewModel.backupEnabled &&
        viewModel.backupChannel(channel) != null
    val showError = failed && retries >= 2 && !backupAvailable
    // Lote 12 (v1.22.0): swipe vertical troca de canal (p/ cima = próximo,
    // p/ baixo = anterior). O `step` fecha sobre `siblings`, que muda com o
    // grupo — o rememberUpdatedState lê sempre a versão atual.
    val latestStep = rememberUpdatedState({ d: Int -> step(d) })

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, amount -> total += amount },
                    onDragEnd = {
                        if (total < -120f) latestStep.value(1)
                        else if (total > 120f) latestStep.value(-1)
                    },
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { overlay = !overlay; if (!overlay) panel = false },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && !failed) CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(44.dp))

        // Lote 13 (v1.23.0, item 19): placar fixado — fica na tela mesmo
        // com os controles escondidos (fora do AnimatedVisibility do
        // overlay). Só aparece quando o EPG traz o placar (ver
        // LivePlayerViewModel.pinnedScore); sem placar, sem chip.
        if (pinned != null) {
            Column(
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(pinned.first, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Placar: ${pinned.second}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (showError) Column(
            Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC0B0F17)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Canal fora do ar agora", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                failDetail ?: "Não foi possível tocar este canal. Tente de novo ou mude de canal.",
                color = TextSecondary, fontSize = 14.sp,
            )
            if (backupId != null) Text("O canal reserva de outra fonte também não tocou.", color = TextSecondary, fontSize = 14.sp)
            com.aotv.app.ui.common.AotvButton("Tentar de novo", { retries = 0; channel?.let { play(it) } }, icon = Icons.Default.Refresh, compact = true)
        }

        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Topo com o canal.
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                    if (!channel?.logo.isNullOrBlank()) AsyncImage(channel!!.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel?.name ?: "Canal", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!channel?.group.isNullOrBlank()) Text(LiveTvViewModel.groupLabel(channel!!), color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val fav = channel != null && channel.id in st.favorites
                    IconButton(onClick = { channel?.let { viewModel.toggleFavorite(it.id) } }, modifier = Modifier.size(48.dp)) {
                        Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, if (fav) "Tirar dos favoritos" else "Favoritar", tint = if (fav) Gold else Color.White)
                    }
                    IconButton(onClick = {
                        val cur = activity?.requestedOrientation
                        activity?.requestedOrientation =
                            if (cur == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        overlay = true
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ScreenRotation, "Girar a tela", tint = Color.White)
                    }
                    IconButton(onClick = { panel = !panel; overlay = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.List, "Lista de canais", tint = Color.White)
                    }
                    // Lote 12 (v1.22.0): guia a partir do player — voltar do
                    // guia retorna a este player (rota por cima na pilha).
                    IconButton(onClick = { channel?.let { onOpenGuide(it.id) } }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.GridView, "Guia de programação", tint = Color.White)
                    }
                }
                // Base com agora e a seguir, e as setas de canal.
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val (n, nx) = channel?.let { viewModel.nowNext(it.id, now) } ?: (null to null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (inCatchup) "CATCH-UP" else "AO VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AccentSecondary).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                            Text(n?.title ?: "Sem guia para este canal", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (n != null) {
                            com.aotv.app.ui.common.ProgressBar(n.progress(now), Modifier.fillMaxWidth(0.7f), height = 3.dp)
                            Text("${hhmm(n.start)} até ${hhmm(n.stop)}", color = TextSecondary, fontSize = 12.sp)
                        }
                        nx?.let { Text("A seguir ${hhmm(it.start)} ${it.title}", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (inCatchup) CatchupControls(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeek = { player.seekTo(it) },
                            onBackToLive = { forceLive = true; channel?.let { play(it) } },
                        )
                    }
                    IconButton(onClick = { step(-1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Canal anterior", tint = Color.White) }
                    IconButton(onClick = { step(1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Próximo canal", tint = Color.White) }
                    // Lote 8 (v1.18.0): faixa de áudio — a escolha fica guardada
                    // neste canal e volta sozinha na próxima vez.
                    TextButton(onClick = { showAudioPicker = true }) {
                        Text("Áudio", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    // Lote 16 item 8: filtro de cor no ao vivo (mesmo painel
                    // do player de filmes, mesmas prefs do perfil).
                    TextButton(onClick = { showColorPanel = true }) {
                        Text("Cor", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    // Lote 12 — Frente 2: gravar o programa atual (em
                    // catch-up, grava o programa em catch-up). Só com o app
                    // aberto — o diálogo avisa (ver RecordProgramDialog).
                    TextButton(onClick = { onRecordClick() }) {
                        Text("Gravar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(panel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) {
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { siblings.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) } }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.8f).background(Color(0xEE080B12))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(siblings, key = { _, c -> c.id }) { _, c ->
                    val active = c.id == currentId
                    val (n, _) = viewModel.nowNext(c.id, now)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (active) AccentSoft else Color.White.copy(alpha = .05f))
                            .clickable { backupId = null; currentId = c.id; panel = false }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(width = 48.dp, height = 34.dp), contentAlignment = Alignment.Center) {
                            if (c.logo.isNotBlank()) AsyncImage(c.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            else Text(c.name.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = if (active) Accent else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            n?.let { Text(it.title, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }

        // Catch-up sem URL: caiu para o ao vivo e avisa.
        // (catch-up indisponível: sem URL de gravação, toca o ao vivo.)
        if (showCatchupUnavailable) Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp, start = 16.dp, end = 16.dp),
            action = { TextButton(onClick = { showCatchupUnavailable = false }) { Text("Ok", color = Color.White) } },
        ) { Text("Catch-up indisponível para este programa — tocando ao vivo") }

        // Seletor de faixa de áudio (Lote 8, v1.18.0): a escolha fica guardada
        // neste canal (ver LiveAudioPick) e volta sozinha na próxima vez.
        if (showAudioPicker) {
            val tracks = audioTrackKeys()
            val saved = channel?.let { viewModel.savedAudio(it.id) }
            AlertDialog(
                onDismissRequest = { showAudioPicker = false },
                containerColor = BackgroundCard,
                title = { Text("Faixa de áudio", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (tracks.isEmpty()) {
                            Text("Nenhuma faixa de áudio disponível neste canal.", color = TextSecondary, fontSize = 14.sp)
                        } else {
                            Text("A escolha fica guardada neste canal.", color = TextTertiary, fontSize = 13.sp)
                            tracks.forEach { (key, label) ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            channel?.let { c ->
                                                applyAudioTrack(key)
                                                viewModel.saveAudio(c.id, key)
                                            }
                                            showAudioPicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label, color = if (saved == key) Accent else TextPrimary,
                                        fontSize = 15.sp, fontWeight = if (saved == key) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (saved == key) Text("Atual", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAudioPicker = false }) { Text("Fechar", color = TextSecondary) } },
            )
        }

        // Filtro de cor (Lote 16 item 8): o mesmo painel do player de filmes,
        // com o mesmo estado/debounce; salva nas mesmas prefs do perfil.
        if (showColorPanel) {
            ColorFilterPanel(
                state = liveColorState,
                focusIdx = 0,
                onStateChange = ::applyLiveColorState,
                onSliderFinished = ::onLiveColorSliderFinished,
                onDismiss = { showColorPanel = false },
                television = com.aotv.app.ui.common.LocalIsTv.current,
            )
        }

        // Lote 12 — Frente 2: diálogo de gravar (reuso do Lote 8). Em
        // catch-up, grava pela URL de catch-up (o ao vivo não tem o programa
        // passado); ao vivo, pelo canal. A janela é início−2min/fim+5min
        // (prefs do perfil); o programa atual já está na janela, então o
        // serviço começa na hora. Só com o app aberto — o diálogo avisa.
        if (showRecordDialog && channel != null && recordTitle != null && recordStartMs > 0L && recordStopMs > recordStartMs) {
            val c = channel
            RecordProgramDialog(
                channelId = c.id,
                channelName = c.name,
                channelUrl = if (inCatchup) viewModel.getCatchupUrl(c, catchupStart, catchupStop) ?: c.url else c.url,
                channelHeaders = c.headers,
                programTitle = recordTitle,
                startMs = recordStartMs,
                stopMs = recordStopMs,
                onDismiss = { showRecordDialog = false },
                onScheduled = { rec ->
                    showRecordDialog = false
                    RecordingService.start(context, rec.id)
                    Toast.makeText(context, "Gravação iniciada: ${rec.programTitle}", Toast.LENGTH_LONG).show()
                },
            )
        }
    }
}

/** Controles do modo catch-up: selo, voltar ao vivo e slider mm:ss. */
@Composable
private fun CatchupControls(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onBackToLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Selo CATCH-UP + voltar ao vivo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "CATCH-UP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onBackToLive) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = "Ao vivo",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text("Ao vivo", color = MaterialTheme.colorScheme.primary)
            }
        }

        // Slider de progresso com timestamp mm:ss
        val positionSec = positionMs / 1000
        val durationSec = if (durationMs > 0) durationMs / 1000 else 1
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatCatchupTime(positionSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.width(44.dp),
            )
            Slider(
                value = positionSec.toFloat(),
                onValueChange = { onSeek((it * 1000).toLong()) },
                valueRange = 0f..durationSec.toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                text = formatCatchupTime(durationSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Segundos em mm:ss. */
private fun formatCatchupTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
