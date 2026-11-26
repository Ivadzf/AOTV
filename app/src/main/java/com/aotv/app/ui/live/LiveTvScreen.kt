package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.filled.Tune
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
import kotlinx.coroutines.withContext

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
    // Lote 2 item 7 (v1.47.0): estilo da aba Ao vivo — "aotv" ou "bestcine".
    val liveStyle by prefs.liveStyleFlow.collectAsStateWithLifecycle()
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
    // Lote 23 item 14 (JOTV 1.0.22 §4.6): filtro por fonte de canal — estado
    // local da tela (sem pref). A fonte é a lista M3U/conta Xtream de origem
    // (LiveChannel.sourceId); o nome vem do JSON de fontes do repo ("aotv_live",
    // mesmas chaves do readSources — sem 2ª instância do repositório).
    var sourceFilter by remember { mutableStateOf("") }
    // Lote 2 item 2 (v1.47.0): seletor de addons da TV (chip "Addons…" na fileira de fontes).
    var addonPickerOpen by remember { mutableStateOf(false) }
    if (addonPickerOpen) TvAddonPickerDialog(
        options = v.tvAddons,
        onToggle = { id, visible -> viewModel.setTvAddonVisible(id, visible) },
        onSelectAll = viewModel::showAllTvAddons,
        onDismiss = { addonPickerOpen = false },
    )
    // Bônus Lote 23 — lockout do PIN: até quando o PIN travou, p/ recompor o
    // diálogo quando a 5ª falha trava no meio da tentativa.
    var pinLockedUntilMs by remember { mutableStateOf(0L) }
    val sourceNames: Map<String, String> = remember(context) {
        val out = LinkedHashMap<String, String>()
        runCatching {
            val a = org.json.JSONArray(
                context.getSharedPreferences("aotv_live", android.content.Context.MODE_PRIVATE)
                    .getString("sources", "[]")
            )
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isNotBlank()) out[id] = o.optString("name").ifBlank { id }
            }
        }
        out
    }
    /** Chave do filtro: a fonte do canal; cada addon de TV é uma fonte própria ("addon-tv:<id>") — os chips viram abas por addon (Lote 2, item 2). */
    fun sourceKey(c: com.aotv.app.data.live.LiveChannel): String = c.sourceId
    fun sourceLabel(key: String): String =
        if (key.startsWith("addon-tv:")) v.addonNames[key.removePrefix("addon-tv:")] ?: key.removePrefix("addon-tv:")
        else sourceNames[key] ?: key
    // Canais da fonte escolhida (a lista já vem filtrada por favoritos/grupo/
    // busca na ViewModel — aqui só soma a fonte).
    val shownRows = if (sourceFilter.isBlank()) v.rows
        else v.rows.filter { sourceKey(it.channel) == sourceFilter }
    val sourceOptions = remember(v.groupedRows) {
        v.groupedRows.values.flatten().map { it.channel }.distinctBy { it.id }
            .groupBy(::sourceKey).map { (k, cs) -> k to cs.size }
    }
    // Lote 2 item 2: o filtro de fonte some junto com o addon oculto — volta p/ "Todos".
    LaunchedEffect(sourceOptions) {
        if (sourceFilter.isNotBlank() && sourceOptions.none { it.first == sourceFilter }) sourceFilter = ""
    }

    /** Tocar um canal respeitando o cadeado do grupo (só no celular). */
    fun playChannel(channel: com.aotv.app.data.live.LiveChannel) {
        if (!isTv && prefs.isLiveGroupLocked(channel.group) && channel.group !in unlockedGroups) {
            pendingUnlockGroup = channel.group
            pendingUnlockRemovesLock = false
            pendingPlayChannel = channel.id
        } else if (com.aotv.app.data.remote.AddonTvCatalogs.isAddonTvChannelId(channel.id)) {
            // Canal de TV de addon (Lote 15, Bloco 2): resolve o stream
            // (/stream/tv/{id}.json) antes de abrir o player; sem stream, o
            // erro real aparece na tela e não navega.
            viewModel.playAddonChannel(channel.id) { onPlay(it) }
        } else {
            onPlay(channel.id)
        }
    }

    /** Tocar pelo id (cartões do hub e volta do PIN): acha o canal na lista e usa [playChannel]. */
    fun playChannelById(id: String) {
        val channel = v.rows.firstOrNull { it.channel.id == id }?.channel
            ?: v.groupedRows.values.flatten().firstOrNull { it.channel.id == id }?.channel
        if (channel != null) playChannel(channel) else onPlay(id)
    }

    pendingUnlockGroup?.let { group ->
        // Bônus Lote 23 — lockout do PIN parental (5 falhas = 5 min, ver
        // PlatformPrefs.recordParentalPinFailure): travado, mostra o tempo
        // restante e NÃO aceita tentativa, nem com o PIN certo.
        val lockedUntil = maxOf(prefs.parentalLockoutUntilMs, pinLockedUntilMs)
        if (System.currentTimeMillis() < lockedUntil) {
            val mins = ((lockedUntil - System.currentTimeMillis() + 59_999) / 60_000).coerceAtLeast(1)
            AlertDialog(
                onDismissRequest = { pendingUnlockGroup = null; pendingPlayChannel = null },
                containerColor = BackgroundCard,
                title = { Text("PIN travado", color = TextPrimary) },
                text = { Text("PIN travado — tente de novo em $mins min.", color = TextSecondary, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = { pendingUnlockGroup = null; pendingPlayChannel = null }) {
                        Text("Entendi", color = Accent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        } else {
            com.aotv.app.ui.settings.VerifyPinDialog(
                title = "PIN dos pais",
                onConfirm = { pin ->
                    if (prefs.isParentalLockedOut) {
                        pinLockedUntilMs = prefs.parentalLockoutUntilMs
                        false
                    } else if (prefs.checkParentalPin(pin)) {
                        prefs.resetParentalPinFailures()
                        if (pendingUnlockRemovesLock) prefs.unlockLiveGroup(group)
                        else unlockedGroups = unlockedGroups + group
                        pendingPlayChannel?.let { playChannelById(it) }
                        pendingPlayChannel = null
                        pendingUnlockGroup = null
                        true
                    } else {
                        prefs.recordParentalPinFailure()
                        pinLockedUntilMs = prefs.parentalLockoutUntilMs
                        false
                    }
                },
                onDismiss = { pendingUnlockGroup = null; pendingPlayChannel = null },
            )
        }
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
                    (v.loading || v.addonLoading) && v.total == 0 -> "Carregando canais…"
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
                placeholder = { Text("Nome do canal, grupo ou programa") },
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

        // Lote 23 item 14 (JOTV 1.0.22 §4.6): filtro por fonte de canal, no
        // padrão de chips do Descobrir (um por fonte presente + "Todos", com
        // contagem). Combina com favoritos, grupo e busca. Só aparece com
        // mais de uma fonte — e não no Guia (a grade tem ViewModel própria).
        if (!guide && sourceOptions.size > 1) {
            Text("Fonte dos canais", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = pad, end = pad, top = 4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { com.aotv.app.ui.common.FilterChipPill("Todos ${v.total}", sourceFilter.isBlank(), { sourceFilter = "" }) }
                items(sourceOptions, key = { it.first }) { (key, n) ->
                    com.aotv.app.ui.common.FilterChipPill("${sourceLabel(key)} $n", sourceFilter == key, { sourceFilter = key })
                }
                // Lote 2 item 2 (v1.47.0): seletor de addons — abre o diálogo
                // p/ escolher quais addons aparecem na aba.
                if (v.tvAddons.isNotEmpty()) {
                    item {
                        com.aotv.app.ui.common.FilterChipPill(
                            "Addons…", false, { addonPickerOpen = true },
                            icon = Icons.Default.Tune,
                        )
                    }
                }
            }
        }

        // Lote 8 (v1.18.0): seção de esportes ao vivo (Frente 2) — depois das
        // pílulas de filtro, só na vista de canais. Sem linhas, não desenha nada.
        // Lote 21 (v1.31.0): a classificação do EPG NÃO roda na composição
        // (thread principal) — produceState + Dispatchers.Default, com a
        // classificação em cache (SportsScores). Antes, cada recomposição
        // revarria o dicionário de 607 times p/ cada canal na Main (ANR).
        if (!guide && sportsShow) {
            val liveSportsRows by produceState(
                initialValue = emptyList<Pair<com.aotv.app.data.live.LiveChannel, com.aotv.app.data.live.LiveProgram?>>(),
                key1 = shownRows, key2 = v.epg, key3 = v.now,
            ) {
                value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    liveSportsNow(shownRows.map { it.channel }, v.epg, v.now)
                }
            }
            SportsSection(
                rows = liveSportsRows,
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

        // Lote 15 (v1.25.0, Bloco 2): resolvendo o stream do canal de addon.
        if (v.addonResolvingId != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(BackgroundCard2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Resolvendo o stream do canal…", color = TextSecondary, fontSize = 13.sp)
            }
        }

        v.error?.let {
            Text(it, color = DangerText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
        }

        // Lote 15 (v1.25.0, Bloco 2): erro ao ler os addons ou ao resolver o
        // stream do canal de addon.
        v.addonError?.let {
            Text(it, color = DangerText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
        }
        // Lote 16 (v1.26.0): diagnóstico dos catálogos de TV — quando um addon
        // ligado responde mas não rende catálogo de TV, mostra os types que
        // ele declara (p/ entender por que o canal não aparece). Vazio =
        // nenhum addon respondeu (o erro acima já cobre).
        if (v.addonDiag.isNotEmpty()) {
            Text(
                v.addonDiag.joinToString(" · ") { d ->
                    "${d.addonName}: ${if (d.declaredTypes.isEmpty()) "sem catálogo" else d.declaredTypes.joinToString(", ")}"
                },
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp),
            )
        }
        v.addonResolveError?.let {
            Text(it, color = DangerText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
        }

        // Lote 2 item 7 (v1.47.0): estilo BestCine — grade de canais com o
        // programa atual + barra de progresso e categorias no topo (variante
        // celular da barra lateral; na TV segue o padrão AOTV). Vem antes do
        // estado vazio p/ os chips ficarem visíveis mesmo sem resultado.
        if (!guide && !isTv && liveStyle == "bestcine") {
            BestCineLiveGrid(
                rows = shownRows,
                groups = v.groups,
                filter = v.filter,
                favoriteCount = v.favoriteCount,
                total = v.total,
                now = v.now,
                showLogos = v.showLogos,
                onSelectFilter = viewModel::setFilter,
                onPlay = { row -> playChannel(row.channel) },
                onToggleFavorite = { row -> viewModel.toggleFavorite(row.channel.id) },
            )
            return@Column
        }

        if (shownRows.isEmpty()) {
            Text(
                if (v.loading) "Carregando canais…"
                else if (sourceFilter.isNotBlank()) "Nenhum canal nesta fonte."
                else "Nenhum canal aqui.",
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
        // Lote 23 item 14: com filtro de fonte ativo, o hub (sem filtro)
        // não vale — segue a grade plana com as linhas filtradas.
        if (!isTv && !guide && v.filter == LiveTvViewModel.ALL && v.query.isBlank() && sourceFilter.isBlank()) {
            LiveGroupCards(
                groupedRows = v.groupedRows,
                favorites = groupFavorites,
                now = v.now,
                // Lote 15 (v1.25.0, Bloco 2): o toque no cartão passa por
                // playChannelById, que resolve o stream do canal de addon
                // antes de abrir o player (o onPlay cru não resolveria).
                onPlay = { id -> playChannelById(id) },
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
            items(shownRows, key = { it.channel.id }) { row ->
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

/**
 * Seletor de addons da TV (Lote 2, item 2, v1.47.0): lista os addons com
 * catálogo de TV e deixa ligar/desligar cada um na aba. Sem seleção = todos
 * (a pref guarda null). A contagem é de canais visíveis do addon.
 */
@Composable
private fun TvAddonPickerDialog(
    options: List<LiveTvViewModel.TvAddonOption>,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Addons na TV", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Só os addons ligados aparecem na aba Ao vivo.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(options, key = { it.id }) { opt ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(opt.id, !opt.visible) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.LiveTv,
                                null,
                                tint = if (opt.visible) Accent else TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(opt.name, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${opt.channelCount} ${if (opt.channelCount == 1) "canal" else "canais"}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            if (opt.visible) Icon(Icons.Default.Check, "Visível", tint = Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Pronto", color = Accent) } },
        dismissButton = { TextButton(onClick = onSelectAll) { Text("Mostrar todos", color = TextSecondary) } },
    )
}

/**
 * Grade estilo BestCine (Lote 2, item 7, v1.47.0): categorias no topo
 * (variante celular da barra lateral — no print de referência do Davi são
 * os chips Todos / Favoritos / Anime / Comédia…) e grade de canais com a
 * logo, o programa atual, a barra de progresso e os horários. O zapping
 * continua no player ([LivePlayerScreen.step], mesma lógica dos dois
 * estilos); o toque no cartão abre o canal pela mesma [playChannel].
 */
@Composable
private fun ColumnScope.BestCineLiveGrid(
    rows: List<LiveRow>,
    groups: List<Pair<String, Int>>,
    filter: String,
    favoriteCount: Int,
    total: Int,
    now: Long,
    showLogos: Boolean,
    onSelectFilter: (String) -> Unit,
    onPlay: (LiveRow) -> Unit,
    onToggleFavorite: (LiveRow) -> Unit,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    LazyRow(
        contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { com.aotv.app.ui.common.FilterChipPill("Todos $total", filter == LiveTvViewModel.ALL, { onSelectFilter(LiveTvViewModel.ALL) }) }
        item {
            com.aotv.app.ui.common.FilterChipPill(
                "Favoritos $favoriteCount", filter == LiveTvViewModel.FAV, { onSelectFilter(LiveTvViewModel.FAV) },
                icon = Icons.Default.Star,
            )
        }
        items(groups, key = { it.first }) { (name, n) ->
            val key = "g:$name"
            com.aotv.app.ui.common.FilterChipPill("$name $n", filter == key, { onSelectFilter(key) })
        }
    }
    if (rows.isEmpty()) {
        Text(
            "Nenhum canal aqui.",
            color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(pad),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows, key = { it.channel.id }) { row ->
            BestCineChannelCard(
                row = row,
                now = now,
                showLogos = showLogos,
                onPlay = { onPlay(row) },
                onToggleFavorite = { onToggleFavorite(row) },
            )
        }
    }
}

/** Cartão de canal da grade BestCine: logo, nome, programa atual, progresso e horários. */
@Composable
private fun BestCineChannelCard(
    row: LiveRow,
    now: Long,
    showLogos: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val channel = row.channel
    val program = row.now
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard2)
            .clickable { onPlay() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            if (showLogos && channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    channel.name, color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).background(FillSubtle)
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (row.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    if (row.favorite) "Desfavoritar" else "Favoritar",
                    tint = if (row.favorite) Gold else TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            channel.name, color = TextPrimary, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            program?.title ?: "Sem programação",
            color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (program != null) {
            LinearProgressIndicator(
                progress = { program.progress(now) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                color = Accent,
                trackColor = FillSubtle,
            )
            Text(
                "${hhmm(program.start)} – ${hhmm(program.stop)}",
                color = TextTertiary, fontSize = 11.sp,
            )
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
                // Lote 15 (v1.25.0, Bloco 2): canal de addon não tem EPG —
                // mostra o estado honesto, sem inventar programa.
                val suffix = if (com.aotv.app.data.remote.AddonTvCatalogs.isAddonTvChannelId(c.id)) " · Sem guia" else ""
                Text(groupText + suffix, color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
