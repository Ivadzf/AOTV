package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.mediaservers.DiscoveredPlexServer
import com.aotv.app.data.mediaservers.MediaItem
import com.aotv.app.data.mediaservers.MediaLibrary
import com.aotv.app.data.mediaservers.MediaServerAccount
import com.aotv.app.data.mediaservers.MediaServerKind
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Servidores de mídia (Lote 10, v1.20.0). Conecta o Jellyfin, Emby ou Plex
 * do usuário (URL + credenciais dele, nada embutido), testa a conexão e
 * navega nas bibliotecas: filmes tocam direto, séries abrem temporadas e
 * episódios. Tudo pelo player existente (ExoPlayer).
 */
@Composable
fun MediaServersScreen(
    onBack: () -> Unit,
    onPlay: (url: String, title: String, type: String, posterUrl: String, season: Int, episode: Int) -> Unit,
    viewModel: MediaServersViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    val scope = rememberCoroutineScope()

    // ── Formulário de adição ──────────────────────────────────────────────
    var kind by remember { mutableStateOf(MediaServerKind.JELLYFIN) }
    var base by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var plexToken by remember { mutableStateOf("") }
    var plexBase by remember { mutableStateOf("") }

    // ── Navegação das bibliotecas ─────────────────────────────────────────
    var openAcc by remember { mutableStateOf<MediaServerAccount?>(null) }
    var libraries by remember { mutableStateOf<List<MediaLibrary>>(emptyList()) }
    var openLib by remember { mutableStateOf<MediaLibrary?>(null) }
    var libItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var openSeries by remember { mutableStateOf<MediaItem?>(null) }
    var seasons by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var openSeason by remember { mutableStateOf<MediaItem?>(null) }
    var episodes by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var browseBusy by remember { mutableStateOf<String?>(null) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    fun clearBrowse() {
        openLib = null
        libItems = emptyList()
        openSeries = null
        seasons = emptyList()
        openSeason = null
        episodes = emptyList()
        searchQuery = ""
        searchResults = emptyList()
        browseError = null
    }

    fun openAccount(acc: MediaServerAccount) {
        if (openAcc?.id == acc.id) {
            openAcc = null
            libraries = emptyList()
            clearBrowse()
            return
        }
        openAcc = acc
        clearBrowse()
        browseBusy = "libs"
        browseError = null
        scope.launch {
            val r = viewModel.libraries(acc)
            browseBusy = null
            r.onSuccess { libraries = it }
            r.onFailure { browseError = it.message }
        }
    }

    fun openLibrary(acc: MediaServerAccount, lib: MediaLibrary) {
        if (openLib?.id == lib.id) {
            openLib = null
            libItems = emptyList()
            openSeries = null
            seasons = emptyList()
            openSeason = null
            episodes = emptyList()
            return
        }
        openLib = lib
        openSeries = null
        seasons = emptyList()
        openSeason = null
        episodes = emptyList()
        browseBusy = "items"
        browseError = null
        scope.launch {
            val r = viewModel.libraryItems(acc, lib)
            browseBusy = null
            r.onSuccess { libItems = it }
            r.onFailure { browseError = it.message }
        }
    }

    fun openSeriesDetail(acc: MediaServerAccount, series: MediaItem) {
        if (openSeries?.id == series.id) {
            openSeries = null
            seasons = emptyList()
            openSeason = null
            episodes = emptyList()
            return
        }
        openSeries = series
        openSeason = null
        episodes = emptyList()
        browseBusy = "seasons"
        browseError = null
        scope.launch {
            val r = viewModel.seasons(acc, series)
            browseBusy = null
            r.onSuccess { seasons = it }
            r.onFailure { browseError = it.message }
        }
    }

    fun openSeasonEpisodes(acc: MediaServerAccount, series: MediaItem, season: MediaItem) {
        if (openSeason?.id == season.id) {
            openSeason = null
            episodes = emptyList()
            return
        }
        openSeason = season
        browseBusy = "episodes"
        browseError = null
        scope.launch {
            val r = viewModel.episodes(acc, series, season)
            browseBusy = null
            r.onSuccess { episodes = it }
            r.onFailure { browseError = it.message }
        }
    }

    fun playItem(acc: MediaServerAccount, item: MediaItem) {
        if (playingId != null) return
        playingId = item.id
        browseError = null
        scope.launch {
            val url = viewModel.streamUrl(acc, item)
            playingId = null
            if (url.isBlank()) {
                browseError = "O servidor não devolveu o vídeo desse item"
            } else {
                onPlay(
                    url, item.title,
                    if (item.type == "episode") "SERIES" else "MOVIE",
                    item.posterUrl, item.seasonNumber, item.episodeNumber,
                )
            }
        }
    }

    fun togglePlayed(acc: MediaServerAccount, item: MediaItem, inSearch: Boolean) {
        scope.launch {
            val ok = viewModel.setPlayed(acc, item, !item.played)
            if (!ok) {
                browseError = "O servidor não aceitou marcar como visto"
                return@launch
            }
            val updated = item.copy(played = !item.played)
            if (inSearch) {
                searchResults = searchResults.map { if (it.id == item.id) updated else it }
            } else if (openSeason != null) {
                episodes = episodes.map { if (it.id == item.id) updated else it }
            } else {
                libItems = libItems.map { if (it.id == item.id) updated else it }
            }
        }
    }

    fun doSearch(acc: MediaServerAccount) {
        if (searchQuery.isBlank()) return
        browseBusy = "search"
        browseError = null
        scope.launch {
            val r = viewModel.search(acc, searchQuery)
            browseBusy = null
            r.onSuccess { searchResults = it }
            r.onFailure { browseError = it.message }
        }
    }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(
                "Servidores de mídia", Modifier.weight(1f),
                sub = if (st.accounts.isEmpty()) "Nenhum servidor conectado" else "${st.accounts.size} conectado(s)",
            )
        }

        val summary: @Composable () -> Unit = {
            com.aotv.app.ui.common.SummaryCard {
                Text("Sua coleção, no app", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                com.aotv.app.ui.common.SummaryRow("Servidores", if (st.accounts.isEmpty()) "Nenhum" else st.accounts.joinToString(" · ") { it.kind.short })
                com.aotv.app.ui.common.SummaryRow("Na Biblioteca", if (st.showInLibrary) "Mostra o atalho" else "Escondido", TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Jellyfin, Emby e Plex do seu servidor, com a sua conta. O endereço e a senha ficam só neste aparelho e nunca entram no backup.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                st.message?.let { m -> item { ServerNotice(m, ok = true, onClose = viewModel::clearMessage) } }
                st.error?.let { e -> item { ServerNotice(e, ok = false, onClose = viewModel::clearMessage) } }
                browseError?.let { e -> item { ServerNotice(e, ok = false, onClose = { browseError = null }) } }

                item { com.aotv.app.ui.common.SettingsSection("Adicionar servidor") }
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaServerKind.entries.forEach { k ->
                                com.aotv.app.ui.common.FilterChipPill(k.label, kind == k, { kind = k })
                            }
                        }
                        if (kind == MediaServerKind.PLEX) {
                            OutlinedTextField(
                                value = plexToken, onValueChange = { plexToken = it.trim() }, singleLine = true,
                                label = { Text("Token do Plex (plex.tv)") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                colors = serverFieldColors(),
                            )
                            Text(
                                "O token fica em plex.tv → sua conta → Token. O app só usa ele para achar o seu servidor.",
                                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.aotv.app.ui.common.AotvButton(
                                    "Descobrir servidores", { viewModel.discoverPlex(plexToken) },
                                    tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                                    busy = st.busy == "add", busyLabel = "Procurando…",
                                    enabled = plexToken.length >= 8, compact = true,
                                )
                            }
                            OutlinedTextField(
                                value = plexBase, onValueChange = { plexBase = it }, singleLine = true,
                                label = { Text("Ou o endereço do servidor (ex.: http://192.168.1.10:32400)") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                colors = serverFieldColors(),
                            )
                            com.aotv.app.ui.common.AotvButton(
                                "Conectar no endereço", { viewModel.connectPlexManual(plexToken, plexBase) },
                                tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                                busy = st.busy == "add", busyLabel = "Conectando…",
                                enabled = plexToken.length >= 8 && plexBase.isNotBlank(), compact = true,
                            )
                        } else {
                            OutlinedTextField(
                                value = base, onValueChange = { base = it }, singleLine = true,
                                label = { Text("Endereço do servidor (ex.: http://192.168.1.10:8096)") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                colors = serverFieldColors(),
                            )
                            OutlinedTextField(
                                value = user, onValueChange = { user = it }, singleLine = true,
                                label = { Text("Usuário") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                colors = serverFieldColors(),
                            )
                            OutlinedTextField(
                                value = pass, onValueChange = { pass = it }, singleLine = true,
                                label = { Text("Senha") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                colors = serverFieldColors(),
                            )
                            com.aotv.app.ui.common.AotvButton(
                                "Conectar", { viewModel.connectJellyfin(kind, base, user, pass) },
                                tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                                busy = st.busy == "add", busyLabel = "Conectando…",
                                enabled = base.isNotBlank() && user.isNotBlank(), compact = true,
                            )
                        }
                    }
                }

                if (st.plexServers.isNotEmpty()) {
                    item { com.aotv.app.ui.common.SettingsSection("Servidores encontrados") }
                    items(st.plexServers, key = { it.machineId + it.uri }) { s ->
                        DiscoveredServerRow(
                            server = s,
                            busy = st.busy == "add",
                            onConnect = { viewModel.connectPlexDiscovered(plexToken, s) },
                        )
                    }
                }

                if (st.accounts.isNotEmpty()) {
                    item { com.aotv.app.ui.common.SettingsSection("Ajustes") }
                    item {
                        com.aotv.app.ui.common.SettingsRow(
                            icon = Icons.Default.Dns,
                            iconTint = Accent,
                            title = "Mostrar na Biblioteca",
                            sub = "Desligado, some o atalho dos servidores da aba Biblioteca",
                            trailing = { Switch(checked = st.showInLibrary, onCheckedChange = viewModel::setShowInLibrary, colors = serverSwitch()) },
                            onClick = { viewModel.setShowInLibrary(!st.showInLibrary) },
                        )
                    }

                    item { com.aotv.app.ui.common.SettingsSection("Seus servidores") }
                    items(st.accounts, key = { it.id }) { acc ->
                        ServerCard(
                            account = acc,
                            open = openAcc?.id == acc.id,
                            busy = st.busy == acc.id,
                            onOpen = { openAccount(acc) },
                            onTest = { viewModel.testConnection(acc) },
                            onRemove = { viewModel.remove(acc) },
                        )
                    }

                    openAcc?.let { acc ->
                        item { com.aotv.app.ui.common.SettingsSection("Bibliotecas de ${acc.label}") }
                        if (browseBusy == "libs") {
                            item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); Text("Carregando bibliotecas…", color = TextTertiary, fontSize = 13.sp) } }
                        }
                        if (libraries.isEmpty() && browseBusy == null) {
                            item { Text("Nenhuma biblioteca de filmes ou séries nesse servidor.", color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp) }
                        }
                        items(libraries, key = { it.id }) { lib ->
                            LibraryRow(
                                library = lib,
                                open = openLib?.id == lib.id,
                                onOpen = { openLibrary(acc, lib) },
                            )
                        }

                        openLib?.let { lib ->
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true,
                                        label = { Text("Buscar em ${acc.label}") },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                                        modifier = Modifier.weight(1f),
                                        colors = serverFieldColors(),
                                    )
                                    IconButton(onClick = { doSearch(acc) }, enabled = searchQuery.isNotBlank(), modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.Search, "Buscar", tint = Accent)
                                    }
                                }
                            }
                            if (browseBusy == "items") {
                                item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); Text("Carregando itens…", color = TextTertiary, fontSize = 13.sp) } }
                            }
                            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                                item { com.aotv.app.ui.common.SettingsSection("Resultados da busca") }
                                items(searchResults, key = { it.id }) { item ->
                                    ServerItemRow(
                                        item = item,
                                        playing = playingId == item.id,
                                        onPlay = { playItem(acc, item) },
                                        onOpenSeries = { openSeriesDetail(acc, item) },
                                        onTogglePlayed = { togglePlayed(acc, item, true) },
                                    )
                                }
                            }
                            items(libItems, key = { it.id }) { item ->
                                ServerItemRow(
                                    item = item,
                                    playing = playingId == item.id,
                                    onPlay = { playItem(acc, item) },
                                    onOpenSeries = { openSeriesDetail(acc, item) },
                                    onTogglePlayed = { togglePlayed(acc, item, false) },
                                )
                            }

                            openSeries?.let { series ->
                                item { com.aotv.app.ui.common.SettingsSection("Temporadas de ${series.title}") }
                                if (browseBusy == "seasons") {
                                    item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); Text("Carregando temporadas…", color = TextTertiary, fontSize = 13.sp) } }
                                }
                                items(seasons, key = { it.id }) { season ->
                                    LibraryRow(
                                        library = MediaLibrary(season.id, acc.id, seasonTitle(season), "series"),
                                        open = openSeason?.id == season.id,
                                        onOpen = { openSeasonEpisodes(acc, series, season) },
                                    )
                                }
                                openSeason?.let {
                                    item { com.aotv.app.ui.common.SettingsSection("Episódios") }
                                    if (browseBusy == "episodes") {
                                        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)); Text("Carregando episódios…", color = TextTertiary, fontSize = 13.sp) } }
                                    }
                                    if (episodes.isEmpty() && browseBusy == null) {
                                        item { Text("Nenhum episódio nessa temporada.", color = TextTertiary, fontSize = 13.sp) }
                                    }
                                    items(episodes, key = { e -> e.id }) { ep ->
                                        ServerItemRow(
                                            item = ep,
                                            playing = playingId == ep.id,
                                            onPlay = { playItem(acc, ep) },
                                            onOpenSeries = {},
                                            onTogglePlayed = { togglePlayed(acc, ep, false) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                below?.let { b -> item { Spacer(Modifier.height(8.dp)); b() } }
            }
        }
    }
}

private fun seasonTitle(season: MediaItem): String =
    if (season.seasonNumber > 0) "Temporada ${season.seasonNumber}" else season.title

@Composable
private fun serverFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
)

@Composable
private fun serverSwitch() = SwitchDefaults.colors(
    checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BackgroundCard2, uncheckedBorderColor = Border,
)

@Composable
private fun ServerNotice(text: String, ok: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background((if (ok) Ok else DangerText).copy(alpha = .12f)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (ok) Ok else DangerText, modifier = Modifier.size(18.dp))
        Text(text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        TextButton(onClick = onClose) { Text("Ok", color = TextSecondary) }
    }
}

@Composable
private fun DiscoveredServerRow(server: DiscoveredPlexServer, busy: Boolean, onConnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Text("PX", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.weight(1f)) {
            Text(server.name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                server.uri + if (server.owned) " · seu servidor" else "",
                color = TextTertiary, fontSize = 13.sp, maxLines = 2,
            )
        }
        com.aotv.app.ui.common.AotvButton(
            "Conectar", onConnect,
            tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
            busy = busy, busyLabel = "Conectando…", compact = true,
        )
    }
}

@Composable
private fun ServerCard(
    account: MediaServerAccount,
    open: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .then(if (open) Modifier.border(1.dp, Accent.copy(alpha = .45f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AccentSoft),
                contentAlignment = Alignment.Center,
            ) { Text(account.kind.short, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                Text(account.label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    account.kind.label + " · " + account.baseUrl + if (account.username.isNotBlank()) " · ${account.username}" else "",
                    color = TextTertiary, fontSize = 13.sp, maxLines = 2,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.AotvButton(
                if (open) "Fechar" else "Ver bibliotecas", onOpen, compact = true,
            )
            com.aotv.app.ui.common.AotvButton(
                "Testar conexão", onTest,
                busy = busy, busyLabel = "Testando…", compact = true,
            )
            com.aotv.app.ui.common.AotvButton(
                "Remover", { confirmRemove = true }, compact = true,
            )
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        containerColor = BackgroundCard,
        title = { Text("Remover ${account.label}?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Text("O servidor sai deste aparelho. Para usar de novo, conecte outra vez.", color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = { onRemove(); confirmRemove = false }) { Text("Remover", color = DangerText) }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar", color = TextSecondary) } },
    )
}

@Composable
private fun LibraryRow(library: MediaLibrary, open: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2)
            .clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (library.type == "series") Icons.Default.Tv else Icons.Default.Movie,
            null, tint = Accent, modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(library.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                when (library.type) {
                    "movie" -> "Filmes"
                    "series" -> "Séries"
                    else -> "Filmes e séries"
                } + if (open) " · aberta" else "",
                color = TextTertiary, fontSize = 13.sp,
            )
        }
        Icon(Icons.Default.Refresh, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ServerItemRow(
    item: MediaItem,
    playing: Boolean,
    onPlay: () -> Unit,
    onOpenSeries: () -> Unit,
    onTogglePlayed: () -> Unit,
) {
    val isSeries = item.type == "series"
    val sub = buildString {
        if (item.type == "episode") {
            if (item.seasonNumber > 0) append("T${item.seasonNumber}")
            if (item.episodeNumber > 0) append("E${item.episodeNumber} · ")
        }
        if (item.year > 0) append(item.year.toString() + " · ")
        if (item.runtimeMin > 0) append("${item.runtimeMin} min · ")
        append(if (item.played) "Visto" else if (isSeries) "Série" else "Filme")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2)
            .clickable(onClick = if (isSeries) onOpenSeries else onPlay).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.posterUrl.isNotBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = "Pôster de ${item.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(Surface2),
            )
        } else {
            Box(
                Modifier.size(44.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSeries || item.type == "episode") Icons.Default.Tv else Icons.Default.Movie,
                    null, tint = TextTertiary, modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(sub, color = TextTertiary, fontSize = 13.sp, maxLines = 1)
            if (item.overview.isNotBlank()) {
                Text(item.overview, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
            }
        }
        if (playing) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            IconButton(onClick = onTogglePlayed, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (item.played) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    if (item.played) "Tirar o visto" else "Marcar como visto",
                    tint = if (item.played) Accent else TextTertiary,
                )
            }
            if (!isSeries) IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.PlayArrow, "Tocar", tint = Accent)
            }
        }
    }
}
