package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.sports.liveSportsNow
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.movies.SportsSection
import com.aotv.app.ui.theme.*

/** Aba "Ao vivo". Canais das listas e contas, com o que está passando agora e a seguir. */
@Composable
fun LiveTvScreen(
    onPlay: (String) -> Unit,
    onSources: () -> Unit,
    /** Rota de catch-up pronta (ver [livePlayerCatchupRoute]); sem ele, passado com catch-up cai no ao vivo. */
    onPlayCatchup: ((String) -> Unit)? = null,
    /** Abre a Biblioteca, onde vive o calendário que já existe (Lote 9, hub). */
    onOpenCalendar: () -> Unit = {},
    /** Abre o On Demand (rota "live_ondemand", Frente 2 do Lote 9). */
    onOpenVod: () -> Unit = {},
    /** Abre o Multiview (Lote 12, v1.22.0): até 4 canais ao mesmo tempo. */
    onOpenMultiview: () -> Unit = {},
    /** Abre as tabelas de ligas (Lote 13, v1.23.0): placar, tabela e canais. */
    onOpenTables: () -> Unit = {},
    /** Buscar o título de um programa do guia nos addons (Lote 12, v1.22.0). */
    onSearchTitle: ((String) -> Unit)? = null,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val v by viewModel.view.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var searching by remember { mutableStateOf(false) }
    // Lote 7: lista de canais ou grade do guia (mesma ViewModel, mesmos filtros).
    var guide by remember { mutableStateOf(false) }
    // Lote 8 (v1.18.0): prefs de esportes (Frente 2) p/ a seção ao vivo.
    // LocalContext.current NÃO pode ir dentro do remember { } (não é contexto
    // @Composable — build #234): lê fora e passa como chave.
    val context = LocalContext.current
    val prefs = remember(context) { PlatformPrefs(context) }
    val sportsShow by prefs.sportsShowFlow.collectAsStateWithLifecycle()
    val sportsHideScores by prefs.sportsHideScoresFlow.collectAsStateWithLifecycle()
    val sportsScoreDelaySec by prefs.sportsScoreDelaySecFlow.collectAsStateWithLifecycle()
    val sportsLiveScores by prefs.sportsLiveScoresFlow.collectAsStateWithLifecycle()
    // Lote 9 (v1.19.0): hub da Live TV — só no celular. Na TV (LocalIsTv,
    // ligado pelo ProvideTvMode da navegação) mantém a renderização atual,
    // para não quebrar o D-pad. Grupos favoritos do hub (a estrela no cartão).
    val isTv = com.aotv.app.ui.common.LocalIsTv.current
    val groupFavorites by prefs.liveGroupFavoritesFlow.collectAsStateWithLifecycle()
    // PIN parental por grupo (Lote 11, v1.21.0): grupos trancados com o PIN
    // dos pais (o mesmo PIN global). O desbloqueio vale só p/ a sessão:
    // fechar o app tranca de novo. Na TV não há cadeado nem gate — a tela
    // segue como antes (documentado no relatório do lote).
    val lockedGroups by prefs.liveLockedGroupsFlow.collectAsStateWithLifecycle()
    var unlockedGroups by remember { mutableStateOf(setOf<String>()) }
    // Grupo esperando o PIN: abrir (liberação da sessão) ou destrancar
    // (tirar da lista — só via cadeado ou Configurações). pendingPlayChannel
    // = canal que tentou tocar num grupo trancado (grade plana).
    var pendingUnlockGroup by remember { mutableStateOf<String?>(null) }
    var pendingUnlockRemovesLock by remember { mutableStateOf(false) }
    var pendingPlayChannel by remember { mutableStateOf<String?>(null) }
    // Grupo esperando a CRIAÇÃO do PIN (trancar sem PIN definido ainda).
    var pendingLockGroup by remember { mutableStateOf<String?>(null) }
    var showSetPinForLock by remember { mutableStateOf(false) }

    /** Tocar um canal respeitando o cadeado do grupo (só no celular). */
    fun playChannel(channel: com.aotv.app.data.live.LiveChannel) {
        if (!isTv && prefs.isLiveGroupLocked(channel.group) && channel.group !in unlockedGroups) {
            pendingUnlockGroup = channel.group
            pendingUnlockRemovesLock = false
            pendingPlayChannel = channel.id
        } else {
            onPlay(channel.id)
        }
    }

    pendingUnlockGroup?.let { group ->
        com.aotv.app.ui.settings.VerifyPinDialog(
            title = "PIN dos pais",
            onConfirm = { pin ->
                if (prefs.checkParentalPin(pin)) {
                    if (pendingUnlockRemovesLock) prefs.unlockLiveGroup(group)
                    else unlockedGroups = unlockedGroups + group
                    pendingPlayChannel?.let { onPlay(it) }
                    pendingPlayChannel = null
                    pendingUnlockGroup = null
                    true
                } else {
                    false
                }
            },
            onDismiss = { pendingUnlockGroup = null; pendingPlayChannel = null },
        )
    }
    if (showSetPinForLock) {
        com.aotv.app.ui.settings.SetPinDialog(
            title = "Criar PIN dos pais",
            onSet = { pin ->
                prefs.setParentalPin(pin)
                pendingLockGroup?.let { prefs.toggleLiveGroupLocked(it) }
                pendingLockGroup = null
                showSetPinForLock = false
            },
            onDismiss = { pendingLockGroup = null; showSetPinForLock = false },
        )
    }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.aotv.app.ui.common.PageHeader(
                "Ao vivo", Modifier.weight(1f),
                sub = when {
                    !v.hasSources -> "Listas M3U e contas Xtream"
                    v.loading && v.total == 0 -> "Carregando canais…"
                    v.epgLoading -> "${v.total} canais · carregando o guia…"
                    else -> "${v.total} canais"
                },
            )
            if (v.hasSources) {
                com.aotv.app.ui.common.GlassCircleButton(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    { searching = !searching; if (!searching) viewModel.setQuery("") },
                    contentDescription = if (searching) "Fechar busca" else "Buscar canal",
                )
                com.aotv.app.ui.common.GlassCircleButton(
                    Icons.Default.Refresh, viewModel::reload,
                    contentDescription = if (v.loading) "Atualizando canais" else "Atualizar canais", busy = v.loading,
                )
            }
            com.aotv.app.ui.common.GlassCircleButton(Icons.Default.Settings, onSources, contentDescription = "Fontes de canais")
        }

        // Lote 9 (v1.19.0): barra superior do hub (só no celular). Na TV a
        // tela segue como antes, sem esta barra.
        if (!isTv) {
            LiveHubTopBar(
                guide = guide,
                onSearch = { searching = !searching; if (!searching) viewModel.setQuery("") },
                onGuide = { guide = !guide },
                onCalendar = onOpenCalendar,
                onVod = onOpenVod,
                onSports = { guide = false },
                onSettings = onSources,
            )
        }

        if (!v.hasSources) {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.LiveTv, "Nenhuma fonte de canais",
                "Adicione uma lista M3U ou uma conta Xtream para ver os canais ao vivo aqui, com o guia de programação.",
                action = { com.aotv.app.ui.common.AotvButton("Adicionar fonte", onSources, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY) },
            )
            return@Column
        }

        if (searching) {
            OutlinedTextField(
                value = v.query, onValueChange = viewModel::setQuery, singleLine = true,
                placeholder = { Text("Nome do canal ou grupo") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                ),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { com.aotv.app.ui.common.FilterChipPill("Canais", !guide, { guide = false }, icon = Icons.Default.List) }
            item { com.aotv.app.ui.common.FilterChipPill("Guia", guide, { guide = true }, icon = Icons.Default.GridView) }
            // Lote 12 (v1.22.0): Multiview — até 4 canais ao mesmo tempo (só no celular; na TV a tela avisa).
            item { com.aotv.app.ui.common.FilterChipPill("Multiview", false, onOpenMultiview, icon = Icons.Default.Dashboard) }
            // Lote 13 (v1.23.0): tabelas de ligas + League Networks + páginas de jogo.
            item { com.aotv.app.ui.common.FilterChipPill("Tabelas", false, onOpenTables, icon = Icons.Default.TableChart) }
            item { com.aotv.app.ui.common.FilterChipPill("Todos ${v.total}", v.filter == LiveTvViewModel.ALL, { viewModel.setFilter(LiveTvViewModel.ALL) }) }
            if (v.favoriteCount > 0) item {
                com.aotv.app.ui.common.FilterChipPill("Favoritos ${v.favoriteCount}", v.filter == LiveTvViewModel.FAV, { viewModel.setFilter(LiveTvViewModel.FAV) }, icon = Icons.Default.Star)
            }
            if (v.recentCount > 0) item {
                com.aotv.app.ui.common.FilterChipPill("Recentes ${v.recentCount}", v.filter == LiveTvViewModel.RECENT, { viewModel.setFilter(LiveTvViewModel.RECENT) })
            }
            if (v.groups.size > 1) items(v.groups, key = { "g:" + it.first }) { (g, n) ->
                com.aotv.app.ui.common.FilterChipPill("$g $n", v.filter == "g:$g", { viewModel.setFilter("g:$g") })
            }
        }

        // Lote 8 (v1.18.0): seção de esportes ao vivo (Frente 2) — depois das
        // pílulas de filtro, só na vista de canais. Sem linhas, não desenha nada.
        if (!guide && sportsShow) {
            SportsSection(
                rows = liveSportsNow(v.rows.map { it.channel }, v.epg, v.now),
                now = v.now,
                hideScores = sportsHideScores,
                scoreDelaySec = sportsScoreDelaySec,
                liveScores = sportsLiveScores,
                onPlay = onPlay,
            )
        }

        // Lote 8 (v1.18.0): banner de progresso visível durante a atualização
        // (manual ou automática ao abrir) — nunca toast genérico.
        if (v.loading) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(BackgroundCard2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Atualizando canais e guia…", color = TextSecondary, fontSize = 13.sp)
            }
        }

        v.error?.let {
            Text(it, color = DangerText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
        }

        if (v.rows.isEmpty()) {
            Text(
                if (v.loading) "Carregando canais…" else "Nenhum canal aqui.",
                color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(pad),
            )
            return@Column
        }
        if (guide) {
            LiveGuideScreen(onPlay = onPlay, onPlayCatchup = onPlayCatchup, onSearchTitle = onSearchTitle)
            return@Column
        }
        // Lote 9 (v1.19.0): no celular e na vista padrão (sem filtro nem
        // busca), os canais vêm em cartões por grupo (o hub); na TV, com
        // filtro ou com busca, segue a grade plana de antes.
        if (!isTv && !guide && v.filter == LiveTvViewModel.ALL && v.query.isBlank()) {
            LiveGroupCards(
                groupedRows = v.groupedRows,
                favorites = groupFavorites,
                now = v.now,
                onPlay = onPlay,
                onFavoriteChannel = { viewModel.toggleFavorite(it) },
                onToggleGroupFavorite = { prefs.toggleLiveGroupFavorite(it) },
                showLogos = v.showLogos, showNames = v.showNames,
                showNumbers = v.showNumbers, cleanGroups = v.cleanGroupNames,
                // PIN por grupo (Lote 11): cadeado no cartão + PIN ao abrir
                // um grupo trancado. Trancar sem PIN pede p/ criar o PIN
                // primeiro; destrancar pede o PIN.
                lockedGroups = lockedGroups,
                unlockedGroups = unlockedGroups,
                onToggleGroupLock = { group ->
                    if (prefs.isLiveGroupLocked(group)) {
                        pendingUnlockGroup = group
                        pendingUnlockRemovesLock = true
                    } else if (!prefs.hasParentalPin) {
                        pendingLockGroup = group
                        showSetPinForLock = true
                    } else {
                        prefs.toggleLiveGroupLocked(group)
                    }
                },
                onRequestUnlock = { group ->
                    pendingUnlockGroup = group
                    pendingUnlockRemovesLock = false
                },
            )
            return@Column
        }
        // Celular em uma coluna, tela larga em mais de uma.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(v.rows, key = { it.channel.id }) { row ->
                ChannelRow(
                    // PIN por grupo (Lote 11): tocar um canal de grupo
                    // trancado pede o PIN (só no celular; na TV, sem gate).
                    row, v.now, onClick = { playChannel(row.channel) }, onFavorite = { viewModel.toggleFavorite(row.channel.id) },
                    showLogos = v.showLogos, showNames = v.showNames, showNumbers = v.showNumbers, cleanGroups = v.cleanGroupNames,
                )
            }
        }
    }
}

@Composable
internal fun ChannelRow(
    row: LiveRow, now: Long, onClick: () -> Unit, onFavorite: () -> Unit,
    // Lote 8 (v1.18.0): exibição de canais, por perfil (ver LiveTvViewModel.View).
    showLogos: Boolean = true, showNames: Boolean = true, showNumbers: Boolean = false, cleanGroups: Boolean = false,
) {
    val c = row.channel
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard2)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showLogos) {
            Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                if (c.logo.isNotBlank()) AsyncImage(c.logo, c.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                else Text(c.name.take(2).uppercase(), color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (showNames) {
                val displayName = if (showNumbers && c.number > 0) "${c.number} · ${c.name}" else c.name
                Text(displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val n = row.now
            if (n != null) {
                Text(n.title, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.aotv.app.ui.common.ProgressBar(n.progress(now), Modifier.fillMaxWidth(0.9f), height = 3.dp)
            } else if (c.group.isNotBlank()) {
                // Limpeza de prefixo ("BR | ") só na exibição, quando ligada.
                val groupText = if (cleanGroups) LiveTvViewModel.groupNames(c, true).joinToString(" · ") else LiveTvViewModel.groupLabel(c)
                Text(groupText, color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            row.next?.let { nx ->
                Text("A seguir ${hhmm(nx.start)} ${nx.title}", color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
                if (row.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                if (row.favorite) "Tirar ${c.name} dos favoritos" else "Favoritar ${c.name}",
                tint = if (row.favorite) Gold else TextTertiary,
            )
        }
    }
}

internal fun hhmm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(ms))
