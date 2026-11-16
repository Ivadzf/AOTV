package com.aotv.app.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveTvRepository
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.sports.ChannelFix
import com.aotv.app.data.sports.EspnSportsRepository
import com.aotv.app.data.sports.GameStatus
import com.aotv.app.data.sports.LeagueGame
import com.aotv.app.data.sports.SPORTS_LEAGUES
import com.aotv.app.data.sports.SportsLeague
import com.aotv.app.data.sports.leagueChannels
import com.aotv.app.data.sports.normalizeSportsName
import com.aotv.app.data.sports.resolveFixedChannel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Aba de Esportes (Lote 15, v1.25.0 — Bloco 1; outros esportes no Lote 16,
 * v1.26.0): 6ª aba da pílula, SÓ no celular. Na TV, esportes ficam dentro
 * do hub do Ao vivo, como hoje.
 *
 * COBERTURA HONESTA: o app conhece 13 ligas da ESPN ([SPORTS_LEAGUES]) —
 * 8 de futebol + NFL, NBA, NHL, MLB e MLS (Lote 16; slugs confirmados ao
 * vivo em 2026-11-16). Sem prometer centenas de ligas que o app não
 * conhece. O seletor mostra "N de 13 ligas ligadas" e o total disponível
 * é 13. Todos os jogos vêm do scoreboard da ESPN
 * ([EspnSportsRepository.gamesOn]); sem resposta da fonte, estado vazio —
 * nunca placar, período, liga ou canal inventado. O filtro por esporte é
 * montado a partir dos dados ([SportsLeague.sportLabel]): Futebol,
 * Futebol Americano, Basquete, Hóquei e Beisebol.
 * Tabelas SÓ p/ o futebol: o summary das ligas americanas não traz tabela
 * de verdade (só fragmento de divisão; MLS sem `standings`) — ver
 * [EspnSportsRepository.leagueTable].
 *
 * "Meus esportes" = SÓ ligas/times seguidos; começa vazio (nenhum default).
 * "Seguindo" alimenta os alertas do WorkManager ([GameScoreWorker]) na
 * medida do que ele cobre hoje: ele vigia o guia de TV (EPG), não a ESPN —
 * a tela diz isso, sem prometer alerta de jogo da ESPN.
 */

/** Um dia em ms (seletor de data: -3..+3 ao redor de hoje). */
private const val SPORTS_TAB_DAY_MS = 24L * 60 * 60 * 1000

/** "dd/MM HH:mm" no fuso do aparelho. */
private fun sportsTabDateTime(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))

/** Rótulo do dia no seletor: Ontem / Hoje / Amanhã, senão "sex 13". */
fun sportsTabDateLabel(offset: Int, nowMs: Long = System.currentTimeMillis()): String {
    if (offset == -1) return "Ontem"
    if (offset == 0) return "Hoje"
    if (offset == 1) return "Amanhã"
    return SimpleDateFormat("EEE d", Locale("pt", "BR")).format(Date(nowMs + offset * SPORTS_TAB_DAY_MS))
}

/** Minuto do relógio da ESPN ("45'", "45'+2'", "90+3'") — null sem minuto ("Intervalo", "FT"). SÓ p/ o relógio do futebol; fora dele, sem uso. */
fun sportsClockMinute(clock: String): Int? {
    val m = Regex("""^(\d{1,3})""").find(clock.trim()) ?: return null
    return m.groupValues[1].toIntOrNull()
}

/**
 * Progresso estimado do jogo ao vivo (minuto/90, SÓ futebol — a ESPN não
 * informa o total). null sem minuto no relógio (a barra some, sem chutar).
 * NÃO se aplica a NFL/NBA/NHL/MLB (períodos, não minutos): quem chama
 * filtra por [SportsLeague.espnSport] == "soccer" antes.
 */
fun sportsLiveProgress(clock: String): Float? {
    val minute = sportsClockMinute(clock) ?: return null
    return (minute / 90f).coerceIn(0f, 1f)
}

/** Esporte de um jogo da ESPN — o rótulo em PT-BR da liga dele. */
fun sportsGameSport(league: SportsLeague): String = league.sportLabel

/** O jogo passa no filtro de busca (time, liga ou estádio, sem acento). */
fun sportsGameMatches(game: LeagueGame, league: SportsLeague, query: String): Boolean {
    val needle = normalizeSportsName(query.trim())
    if (needle.isBlank()) return true
    val hay = normalizeSportsName(game.homeTeam + " " + game.awayTeam + " " + league.name + " " + game.venue)
    return needle.split(" ").all { it in hay }
}

// ── ViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class SportsTabViewModel @Inject constructor(
    private val repo: EspnSportsRepository,
    private val liveRepo: LiveTvRepository,
    private val prefs: PlatformPrefs,
) : ViewModel() {
    enum class Section { MINE, ALL, SEARCH, FOLLOWING, NETWORKS }

    data class Ui(
        val section: Section = Section.MINE,
        val dateOffset: Int = 0,
        val games: Map<String, List<LeagueGame>> = emptyMap(),
        val loading: Boolean = false,
        val error: String? = null,
        val channels: List<LiveChannel> = emptyList(),
        val fixes: Map<String, ChannelFix> = emptyMap(),
        /** Ids das ligas ligadas no seletor (resolvido: vazio nas prefs = todas). */
        val enabledLeagueIds: List<String> = SPORTS_LEAGUES.map { it.id },
        val followedLeagues: List<String> = emptyList(),
        val followedTeams: List<String> = emptyList(),
        val query: String = "",
        val sport: String = "Todos",
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /** Ligas ligadas no seletor (só as que o app conhece — 13). */
    fun enabledLeagues(): List<SportsLeague> {
        val ids = prefs.sportsEnabledLeagueIds(SPORTS_LEAGUES.map { it.id })
        return SPORTS_LEAGUES.filter { it.id in ids }
    }

    init {
        viewModelScope.launch {
            liveRepo.state.collect { st ->
                _ui.update { it.copy(channels = liveRepo.displayChannels(st.channels)) }
            }
        }
        load()
    }

    fun selectSection(section: Section) {
        _ui.update { it.copy(section = section) }
    }

    fun selectDate(offset: Int) {
        if (offset == _ui.value.dateOffset) return
        _ui.update { it.copy(dateOffset = offset) }
        load()
    }

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
    }

    fun setSport(sport: String) {
        _ui.update { it.copy(sport = sport) }
    }

    fun refresh() = load()

    private fun load() {
        val offset = _ui.value.dateOffset
        val dayMs = System.currentTimeMillis() + offset * SPORTS_TAB_DAY_MS
        val leagues = enabledLeagues()
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val results = leagues.map { league ->
                async { league.id to repo.gamesOn(league, dayMs) }
            }.awaitAll()
            val games = results.toMap().filterValues { it.isNotEmpty() }
            _ui.update {
                it.copy(
                    games = games, loading = false,
                    error = if (games.isEmpty()) "Sem jogos nas ligas ligadas neste dia — a ESPN não respondeu ou não há jogos." else null,
                    fixes = prefs.sportsChannelFixes,
                    enabledLeagueIds = prefs.sportsEnabledLeagueIds(SPORTS_LEAGUES.map { it.id }),
                    followedLeagues = prefs.sportsFollowedLeagues,
                    followedTeams = prefs.sportsFollowedTeams,
                )
            }
        }
    }

    fun setLeagueEnabled(league: SportsLeague, enabled: Boolean) {
        prefs.setSportsLeagueEnabled(league.id, enabled, SPORTS_LEAGUES.map { it.id })
        load()
    }

    fun setAllLeaguesEnabled(enabled: Boolean) {
        prefs.sportsTabEnabledLeagues = if (enabled) null else emptyList()
        load()
    }

    fun toggleFollowLeague(leagueId: String) {
        prefs.toggleSportsFollowedLeague(leagueId)
        _ui.update { it.copy(followedLeagues = prefs.sportsFollowedLeagues) }
    }

    fun toggleFollowTeam(team: String) {
        prefs.toggleSportsFollowedTeam(team)
        _ui.update { it.copy(followedTeams = prefs.sportsFollowedTeams) }
    }

    fun saveFix(fix: ChannelFix) {
        prefs.setChannelFix(fix)
        _ui.update { it.copy(fixes = prefs.sportsChannelFixes) }
    }

    /** Canais da liga achados nas playlists do usuário (League Networks). */
    fun networkChannels(league: SportsLeague): List<LiveChannel> =
        leagueChannels(league, _ui.value.channels)

    /** Palavra da rede que casou com algum canal (p/ o escopo NETWORK do Fix Channel). */
    fun matchedNetworkKeyword(league: SportsLeague): String? {
        val channels = _ui.value.channels
        if (channels.isEmpty()) return null
        return league.networkKeywords.firstOrNull { keyword ->
            val k = normalizeSportsName(keyword)
            k.isNotBlank() && channels.any { c -> k in normalizeSportsName(c.name + " " + c.group) }
        }
    }

    /**
     * Canal p/ tocar um jogo: Fix Channel primeiro (jogo > time > rede),
     * senão o 1º canal da League Networks. null = nenhum canal achado —
     * a tela mostra "Não está nos seus canais", sem inventar.
     */
    fun resolveChannel(game: LeagueGame, league: SportsLeague): LiveChannel? {
        val channels = _ui.value.channels
        val network = matchedNetworkKeyword(league)
        val fix = resolveFixedChannel(
            prefs.sportsChannelFixes, game.eventId, network,
            listOf(game.homeTeam, game.awayTeam),
        )
        if (fix != null) {
            channels.firstOrNull { it.id == fix.channelId }?.let { return it }
        }
        return networkChannels(league).firstOrNull()
    }
}

/** Alvo do diálogo Fix Channel na aba: o jogo + a liga dele. */
private data class SportsFixTarget(val league: SportsLeague, val game: LeagueGame)

// ── Tela: aba de Esportes ──────────────────────────────────────────────────

/**
 * Aba de Esportes (celular). Chips do topo: Meus esportes / Todos os
 * eventos / Buscar / Seguindo / Redes. Seletor de data (-3..+3 dias),
 * filtro por esporte (montado a partir dos dados), "Mostrando N ligas" +
 * seletor persistido. Ordem das seções: Ao vivo primeiro, depois os
 * próximos jogos do dia (e os encerrados por último).
 */
@Composable
fun SportsTabScreen(
    onOpenGame: (String, String) -> Unit,
    onPlayChannel: (String) -> Unit,
    viewModel: SportsTabViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var showLeaguePicker by remember { mutableStateOf(false) }
    var fixTarget by remember { mutableStateOf<SportsFixTarget?>(null) }
    // `ui` é propriedade delegada (by): smart cast não funciona nela —
    // ler para vals locais antes dos testes (build #248).
    val gamesByLeague = ui.games
    val followedLeagues = ui.followedLeagues
    val followedTeams = ui.followedTeams
    val allGames = remember(gamesByLeague) {
        gamesByLeague.entries.flatMap { (leagueId, games) ->
            val league = SPORTS_LEAGUES.firstOrNull { it.id == leagueId } ?: return@flatMap emptyList()
            games.map { league to it }
        }
    }
    val sportsPresent = remember(allGames) {
        allGames.map { sportsGameSport(it.first) }.distinct()
    }
    val sportFiltered = remember(allGames, ui.sport) {
        if (ui.sport == "Todos") allGames
        else allGames.filter { sportsGameSport(it.first) == ui.sport }
    }
    val liveGames = remember(sportFiltered) {
        sportFiltered.filter { it.second.isLive }.sortedBy { it.second.dateMs }
    }
    val upcomingGames = remember(sportFiltered) {
        sportFiltered.filter { it.second.status == GameStatus.SCHEDULED }.sortedBy { it.second.dateMs }
    }
    val finishedGames = remember(sportFiltered) {
        sportFiltered.filter { it.second.isFinished }.sortedBy { it.second.dateMs }
    }
    val mineGames = remember(sportFiltered, followedLeagues, followedTeams) {
        sportFiltered.filter { (league, game) ->
            league.id in followedLeagues || game.homeTeam in followedTeams || game.awayTeam in followedTeams
        }.sortedBy { it.second.dateMs }
    }
    val query = ui.query
    val searchGames = remember(sportFiltered, query) {
        sportFiltered.filter { (league, game) -> sportsGameMatches(game, league, query) }
            .sortedBy { it.second.dateMs }
    }
    val enabledLeagues = remember(ui.enabledLeagueIds) {
        SPORTS_LEAGUES.filter { it.id in ui.enabledLeagueIds }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(end = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.aotv.app.ui.common.PageHeader(
                "Esportes", Modifier.weight(1f),
                sub = "Placar e tabelas — dados da ESPN",
                trailing = {
                    IconButton(onClick = { viewModel.refresh() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary)
                    }
                },
            )
        }
        // Chips do topo
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val section = ui.section
            item(key = "chip-mine") {
                com.aotv.app.ui.common.FilterChipPill(
                    "Meus esportes", section == SportsTabViewModel.Section.MINE,
                    { viewModel.selectSection(SportsTabViewModel.Section.MINE) },
                )
            }
            item(key = "chip-all") {
                com.aotv.app.ui.common.FilterChipPill(
                    "Todos os eventos", section == SportsTabViewModel.Section.ALL,
                    { viewModel.selectSection(SportsTabViewModel.Section.ALL) },
                )
            }
            item(key = "chip-search") {
                com.aotv.app.ui.common.FilterChipPill(
                    "Buscar", section == SportsTabViewModel.Section.SEARCH,
                    { viewModel.selectSection(SportsTabViewModel.Section.SEARCH) },
                    icon = Icons.Default.Search,
                )
            }
            item(key = "chip-following") {
                com.aotv.app.ui.common.FilterChipPill(
                    "Seguindo", section == SportsTabViewModel.Section.FOLLOWING,
                    { viewModel.selectSection(SportsTabViewModel.Section.FOLLOWING) },
                )
            }
            item(key = "chip-networks") {
                com.aotv.app.ui.common.FilterChipPill(
                    "Redes", section == SportsTabViewModel.Section.NETWORKS,
                    { viewModel.selectSection(SportsTabViewModel.Section.NETWORKS) },
                )
            }
        }
        // Seletor de data: ontem / hoje / amanhã + dias ao redor
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((-3..3).toList(), key = { "day:$it" }) { offset ->
                com.aotv.app.ui.common.FilterChipPill(
                    sportsTabDateLabel(offset), ui.dateOffset == offset,
                    { viewModel.selectDate(offset) },
                )
            }
        }
        // Filtro por esporte (montado a partir dos dados — Futebol,
        // Futebol Americano, Basquete, Hóquei, Beisebol, conforme os jogos do dia)
        if (sportsPresent.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "sport-all") {
                    com.aotv.app.ui.common.FilterChipPill(
                        "Todos", ui.sport == "Todos", { viewModel.setSport("Todos") },
                    )
                }
                items(sportsPresent, key = { "sport:$it" }) { sport ->
                    com.aotv.app.ui.common.FilterChipPill(
                        sport, ui.sport == sport, { viewModel.setSport(sport) },
                    )
                }
            }
        }
        // "Mostrando N ligas" + seletor persistido
        Row(
            Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Mostrando ${enabledLeagues.size} de ${SPORTS_LEAGUES.size} ligas",
                color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showLeaguePicker = true }) {
                Text("Escolher ligas ›", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        when (ui.section) {
            SportsTabViewModel.Section.MINE -> SportsTabGamesList(
                games = mineGames,
                loading = ui.loading,
                error = ui.error,
                emptyTitle = "Nada seguido ainda",
                emptyMessage = "Siga ligas ou times p/ ver só os seus jogos aqui — toque na estrela de um time ou siga uma liga.",
                emptyActionLabel = "Escolher ligas",
                onEmptyAction = { showLeaguePicker = true },
                followedTeams = followedTeams,
                viewModel = viewModel,
                onOpenGame = onOpenGame,
                onPlayChannel = onPlayChannel,
                onFixChannel = { league, game -> fixTarget = SportsFixTarget(league, game) },
                pad = pad,
            )
            SportsTabViewModel.Section.ALL -> SportsTabGamesList(
                games = liveGames + upcomingGames + finishedGames,
                loading = ui.loading,
                error = ui.error,
                emptyTitle = "Sem jogos neste dia",
                emptyMessage = "Nenhum jogo nas ligas ligadas neste dia.",
                emptyActionLabel = null,
                onEmptyAction = null,
                followedTeams = followedTeams,
                viewModel = viewModel,
                onOpenGame = onOpenGame,
                onPlayChannel = onPlayChannel,
                onFixChannel = { league, game -> fixTarget = SportsFixTarget(league, game) },
                pad = pad,
            )
            SportsTabViewModel.Section.SEARCH -> Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query, onValueChange = { viewModel.setQuery(it) }, singleLine = true,
                    placeholder = { Text("Buscar time, liga ou jogo") },
                    leadingIcon = { Icon(Icons.Default.Search, "Buscar", tint = TextTertiary) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Text(
                    "Busca nos jogos do dia (time, liga ou estádio).",
                    color = TextTertiary, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 2.dp),
                )
                SportsTabGamesList(
                    games = searchGames,
                    loading = ui.loading,
                    error = ui.error,
                    emptyTitle = if (query.isBlank()) "Busque um jogo" else "Nada encontrado",
                    emptyMessage = if (query.isBlank()) "Digite um time, uma liga ou um estádio."
                    else "Nenhum jogo do dia combina com \"$query\".",
                    emptyActionLabel = null,
                    onEmptyAction = null,
                    followedTeams = followedTeams,
                    viewModel = viewModel,
                    onOpenGame = onOpenGame,
                    onPlayChannel = onPlayChannel,
                    onFixChannel = { league, game -> fixTarget = SportsFixTarget(league, game) },
                    pad = pad,
                )
            }
            SportsTabViewModel.Section.FOLLOWING -> SportsTabFollowingList(
                followedLeagues = followedLeagues,
                followedTeams = followedTeams,
                viewModel = viewModel,
                pad = pad,
            )
            SportsTabViewModel.Section.NETWORKS -> SportsTabNetworksList(
                leagues = enabledLeagues,
                viewModel = viewModel,
                onPlayChannel = onPlayChannel,
                pad = pad,
            )
        }
    }
    if (showLeaguePicker) {
        SportsLeaguePickerDialog(
            enabledIds = ui.enabledLeagueIds,
            onToggle = { league, enabled -> viewModel.setLeagueEnabled(league, enabled) },
            onToggleAll = { viewModel.setAllLeaguesEnabled(it) },
            onDismiss = { showLeaguePicker = false },
        )
    }
    val target = fixTarget
    if (target != null) {
        FixChannelDialog(
            game = target.game,
            networkKeyword = viewModel.matchedNetworkKeyword(target.league),
            channels = ui.channels,
            onSave = { fix ->
                viewModel.saveFix(fix)
                fixTarget = null
            },
            onDismiss = { fixTarget = null },
        )
    }
}

// ── Lista de jogos (Meus / Todos / Buscar) ───────────────────────────────

/**
 * Lista de jogos com cabeçalhos de seção: "Ao vivo agora" primeiro, depois
 * "Próximos jogos" e "Encerrados" por último. A lista chega já ordenada
 * (ao vivo → próximos → encerrados); o cabeçalho entra na transição.
 */
@Composable
private fun SportsTabGamesList(
    games: List<Pair<SportsLeague, LeagueGame>>,
    loading: Boolean,
    error: String?,
    emptyTitle: String,
    emptyMessage: String,
    emptyActionLabel: String?,
    onEmptyAction: (() -> Unit)?,
    followedTeams: List<String>,
    viewModel: SportsTabViewModel,
    onOpenGame: (String, String) -> Unit,
    onPlayChannel: (String) -> Unit,
    onFixChannel: (SportsLeague, LeagueGame) -> Unit,
    pad: androidx.compose.ui.unit.Dp,
) {
    if (loading && games.isEmpty()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        return
    }
    val gamesError = error
    if (games.isEmpty()) {
        if (gamesError != null) {
            Text(
                gamesError, color = TextSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 12.dp),
            )
        } else {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.SportsSoccer, emptyTitle, emptyMessage,
                action = if (emptyActionLabel != null && onEmptyAction != null) {
                    { com.aotv.app.ui.common.AotvButton(emptyActionLabel, onEmptyAction, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY) }
                } else null,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(games, key = { _, pair -> "game:${pair.second.eventId}" }) { index, (league, game) ->
            val header = when {
                game.isLive && index == 0 -> "Ao vivo agora"
                game.status == GameStatus.SCHEDULED && (index == 0 || games[index - 1].second.isLive) -> "Próximos jogos"
                game.isFinished && (index == 0 || !games[index - 1].second.isFinished) -> "Encerrados"
                else -> null
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (header != null) {
                    com.aotv.app.ui.common.SectionHeader(
                        title = header + if (header == "Ao vivo agora") " (${games.count { it.second.isLive }})" else "",
                        underline = true, compact = true,
                    )
                }
                SportsTabGameCard(
                    league = league, game = game, followedTeams = followedTeams,
                    viewModel = viewModel, onOpenGame = onOpenGame,
                    onPlayChannel = onPlayChannel, onFixChannel = onFixChannel,
                )
            }
        }
    }
}

/** Card de um jogo como no print do Nuvio+IPTV: liga + estádio, selo LIVE, logos, placar, período e barra de progresso — só com dados reais. */
@Composable
private fun SportsTabGameCard(
    league: SportsLeague,
    game: LeagueGame,
    followedTeams: List<String>,
    viewModel: SportsTabViewModel,
    onOpenGame: (String, String) -> Unit,
    onPlayChannel: (String) -> Unit,
    onFixChannel: (SportsLeague, LeagueGame) -> Unit,
) {
    val resolved = viewModel.resolveChannel(game, league)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
            .clickable { onOpenGame(league.id, game.eventId) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (game.isLive) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("AO VIVO", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(league.name, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (game.venue.isNotBlank()) {
                Text(game.venue, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        val statusText = when {
            game.isFinished -> "Fim de jogo"
            game.status == GameStatus.SCHEDULED -> sportsTabDateTime(game.dateMs)
            game.clock.isNotBlank() -> game.clock
            else -> ""
        }
        if (statusText.isNotBlank()) {
            Text(statusText, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        SportsTabTeamRow(
            team = game.homeTeam, logo = game.homeLogo, score = game.homeScore,
            followed = game.homeTeam in followedTeams,
            onToggleFollow = { viewModel.toggleFollowTeam(game.homeTeam) },
        )
        SportsTabTeamRow(
            team = game.awayTeam, logo = game.awayLogo, score = game.awayScore,
            followed = game.awayTeam in followedTeams,
            onToggleFollow = { viewModel.toggleFollowTeam(game.awayTeam) },
        )
        // Barra de progresso: minuto do relógio / 90 — SÓ futebol. NFL/NBA/
        // NHL/MLB têm períodos, não minuto: sem barra (o relógio da ESPN,
        // "Q3 8:12" etc., já aparece como texto acima).
        val progress = if (game.isLive && league.espnSport == "soccer") sportsLiveProgress(game.clock) else null
        if (progress != null) {
            com.aotv.app.ui.common.ProgressBar(progress, Modifier.fillMaxWidth(), height = 4.dp, fillColor = Accent)
        }
        if (resolved != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                com.aotv.app.ui.common.AotvButton(
                    "Assistir: ${resolved.name}", { onPlayChannel(resolved.id) },
                    icon = Icons.Default.PlayArrow, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY, compact = true,
                )
                TextButton(onClick = { onFixChannel(league, game) }) {
                    Text("Corrigir canal", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            TextButton(onClick = { onFixChannel(league, game) }) {
                Text("Não está nos seus canais — corrigir canal", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SportsTabTeamRow(
    team: String,
    logo: String,
    score: Int?,
    followed: Boolean,
    onToggleFollow: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onToggleFollow, modifier = Modifier.size(32.dp)) {
            Icon(
                if (followed) Icons.Default.Star else Icons.Default.StarBorder,
                if (followed) "Deixar de seguir $team" else "Seguir $team",
                tint = if (followed) Accent else TextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (logo.isNotBlank()) AsyncImage(logo, team, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
        Text(team, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (score != null) Text(score.toString(), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ── Seguindo ───────────────────────────────────────────────────────────────

/** Ligas e times seguidos + o aviso honesto sobre o alcance dos alertas. */
@Composable
private fun SportsTabFollowingList(
    followedLeagues: List<String>,
    followedTeams: List<String>,
    viewModel: SportsTabViewModel,
    pad: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "follow-leagues-header") {
            com.aotv.app.ui.common.SectionHeader(title = "Ligas seguidas", underline = true, compact = true)
        }
        if (followedLeagues.isEmpty()) {
            item(key = "follow-leagues-empty") {
                Text("Nenhuma liga seguida ainda — siga uma liga no seletor de ligas.", color = TextTertiary, fontSize = 14.sp)
            }
        } else {
            items(followedLeagues, key = { "follow-league:$it" }) { leagueId ->
                val league = SPORTS_LEAGUES.firstOrNull { it.id == leagueId }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        league?.name ?: leagueId, color = TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.toggleFollowLeague(leagueId) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, "Deixar de seguir", tint = TextTertiary)
                    }
                }
            }
        }
        item(key = "follow-teams-header") {
            com.aotv.app.ui.common.SectionHeader(title = "Times seguidos", underline = true, compact = true)
        }
        if (followedTeams.isEmpty()) {
            item(key = "follow-teams-empty") {
                Text("Nenhum time seguido ainda — toque na estrela de um time num jogo.", color = TextTertiary, fontSize = 14.sp)
            }
        } else {
            items(followedTeams, key = { "follow-team:$it" }) { team ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        team, color = TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.toggleFollowTeam(team) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, "Deixar de seguir", tint = TextTertiary)
                    }
                }
            }
        }
        item(key = "follow-alerts-note") {
            Text(
                "Alertas: o vigia de fim de jogo e placar (a cada 15 min) hoje cobre os jogos do guia de TV — os jogos da ESPN ainda não disparam alerta.",
                color = TextTertiary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

// ── Redes ──────────────────────────────────────────────────────────────────

/** League Networks por liga: canal de cada liga, ou "sem canal mapeado". */
@Composable
private fun SportsTabNetworksList(
    leagues: List<SportsLeague>,
    viewModel: SportsTabViewModel,
    onPlayChannel: (String) -> Unit,
    pad: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leagues.isEmpty()) {
            item(key = "networks-none") {
                Text("Nenhuma liga ligada — ligue ao menos uma no seletor de ligas.", color = TextTertiary, fontSize = 14.sp)
            }
        }
        leagues.forEach { league ->
            item(key = "net-league:${league.id}") {
                com.aotv.app.ui.common.SectionHeader(title = league.name, underline = true, compact = true)
            }
            val channels = viewModel.networkChannels(league)
            if (channels.isEmpty()) {
                item(key = "net-empty:${league.id}") {
                    Text("Sem canal mapeado — nenhum canal das suas listas passa esta liga.", color = TextTertiary, fontSize = 14.sp)
                }
            } else {
                items(channels, key = { "net:${it.id}" }) { channel ->
                    SportsTabNetworkRow(channel, onPlay = { onPlayChannel(channel.id) })
                }
            }
        }
    }
}

@Composable
private fun SportsTabNetworkRow(channel: LiveChannel, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 48.dp, height = 34.dp), contentAlignment = Alignment.Center) {
            if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Text(channel.name.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(channel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.group.isNotBlank()) Text(channel.group, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, "Assistir", tint = Accent, modifier = Modifier.size(20.dp))
            Text("Assistir", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Seletor de ligas ───────────────────────────────────────────────────────

/**
 * "Escolher ligas": as ligas que o app REALMENTE conhece (13, da ESPN), com
 * busca dentro do seletor e ligar/desligar em massa. Persistido em
 * PlatformPrefs (`sports_tab_leagues_v1` — null = todas ligadas).
 */
@Composable
private fun SportsLeaguePickerDialog(
    enabledIds: List<String>,
    onToggle: (SportsLeague, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val needle = normalizeSportsName(query.trim())
    val filtered = remember(needle) {
        if (needle.isBlank()) SPORTS_LEAGUES
        else SPORTS_LEAGUES.filter { normalizeSportsName(it.name).contains(needle) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.aotv.app.ui.theme.BackgroundCard,
        title = { Text("Escolher ligas", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { onToggleAll(true) }) {
                        Text("Ligar todas", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onToggleAll(false) }) {
                        Text("Desligar todas", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Buscar liga") },
                    leadingIcon = { Icon(Icons.Default.Search, "Buscar", tint = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Text(
                    "${enabledIds.size} de ${SPORTS_LEAGUES.size} ligas ligadas · ${SPORTS_LEAGUES.size} ligas disponíveis",
                    color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { league ->
                        val enabled = league.id in enabledIds
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onToggle(league, !enabled) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Checkbox(
                                checked = enabled,
                                onCheckedChange = { checked -> onToggle(league, checked) },
                            )
                            Text(league.name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "no-league") {
                            Text(
                                "Nenhuma liga com esse nome — o app só conhece ${SPORTS_LEAGUES.size} ligas da ESPN.",
                                color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = Accent, fontWeight = FontWeight.Bold) }
        },
    )
}
