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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveTvRepository
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.sports.ChannelFix
import com.aotv.app.data.sports.EspnSportsRepository
import com.aotv.app.data.sports.FormResult
import com.aotv.app.data.sports.GameDetail
import com.aotv.app.data.sports.GameStatus
import com.aotv.app.data.sports.LeagueGame
import com.aotv.app.data.sports.LeagueTable
import com.aotv.app.data.sports.SPORTS_LEAGUES
import com.aotv.app.data.sports.SportsLeague
import com.aotv.app.data.sports.TableZone
import com.aotv.app.data.sports.fixScopeLabel
import com.aotv.app.data.sports.leagueChannels
import com.aotv.app.data.sports.normalizeSportsName
import com.aotv.app.data.sports.resolveFixedChannel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import com.aotv.app.data.sports.FixScope
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border

/** "dd/MM HH:mm" no fuso do aparelho. */
private fun sportsDateTime(ms: Long): String =
    java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(ms))

/** "HH:mm" no fuso do aparelho. */
private fun sportsHhmm(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(ms))

/** "HH:mm" da hora da atualização da tabela. */
private fun sportsUpdatedAt(ms: Long): String = if (ms <= 0L) "" else sportsHhmm(ms)

// ── ViewModels ─────────────────────────────────────────────────────────────

/**
 * Tabelas de ligas (Lote 13, v1.23.0). Estado único (sem `combine` de
 * vários flows): a tabela e os jogos vêm do repositório ESPN, os canais
 * do LiveTvRepository e os fixes das prefs do perfil.
 */
@HiltViewModel
class SportsTablesViewModel @Inject constructor(
    private val repo: EspnSportsRepository,
    private val liveRepo: LiveTvRepository,
    private val prefs: PlatformPrefs,
) : ViewModel() {
    data class Ui(
        val league: SportsLeague = SPORTS_LEAGUES[0],
        val table: LeagueTable? = null,
        val tableLoading: Boolean = false,
        val tableError: String? = null,
        val games: List<LeagueGame> = emptyList(),
        val gamesLoading: Boolean = false,
        val channels: List<LiveChannel> = emptyList(),
        val fixes: Map<String, ChannelFix> = emptyMap(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            liveRepo.state.collect { st ->
                _ui.update { it.copy(channels = liveRepo.displayChannels(st.channels)) }
            }
        }
        load()
    }

    fun selectLeague(league: SportsLeague) {
        if (league.id == _ui.value.league.id) return
        _ui.update { it.copy(league = league, table = null, games = emptyList()) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        val league = _ui.value.league
        viewModelScope.launch {
            _ui.update { it.copy(tableLoading = true, tableError = null, gamesLoading = true) }
            val table = repo.leagueTable(league)
            val games = repo.gamesOn(league, System.currentTimeMillis())
            _ui.update {
                it.copy(
                    table = table, tableLoading = false,
                    tableError = if (table == null) "Sem dados da tabela agora — a ESPN não respondeu ou a temporada ainda não começou." else null,
                    games = games, gamesLoading = false,
                    fixes = prefs.sportsChannelFixes,
                )
            }
        }
    }

    fun saveFix(fix: ChannelFix) {
        prefs.setChannelFix(fix)
        _ui.update { it.copy(fixes = prefs.sportsChannelFixes) }
    }

    fun removeFix(mapKey: String) {
        prefs.removeChannelFix(mapKey)
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
     * a tela avisa, sem inventar.
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

/** Página de um jogo (Lote 13, v1.23.0): detalhe ESPN + Fix Channel. */
@HiltViewModel
class SportsGameViewModel @Inject constructor(
    private val repo: EspnSportsRepository,
    private val liveRepo: LiveTvRepository,
    private val prefs: PlatformPrefs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val leagueId: String = savedStateHandle.get<String>("league").orEmpty()
    val eventId: String = savedStateHandle.get<String>("eventId").orEmpty()
    val league: SportsLeague? = SPORTS_LEAGUES.firstOrNull { it.id == leagueId }

    data class Ui(
        val detail: GameDetail? = null,
        val table: LeagueTable? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val channels: List<LiveChannel> = emptyList(),
        val fixes: Map<String, ChannelFix> = emptyMap(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            liveRepo.state.collect { st ->
                _ui.update { it.copy(channels = liveRepo.displayChannels(st.channels)) }
            }
        }
        load()
    }

    fun refresh() = load()

    private fun load() {
        val league = league ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val detail = repo.gameDetail(league, eventId)
            // Lote 15 (v1.25.0, Bloco 1): a tabela da liga na página do jogo
            // (mesma fonte do Lote 13 — summary da ESPN; null fora de
            // temporada, e aí a seção some, sem inventar).
            val table = repo.leagueTable(league)
            _ui.update {
                it.copy(
                    detail = detail, table = table, loading = false,
                    error = if (detail == null) "Sem dados deste jogo agora — a ESPN não respondeu." else null,
                    fixes = prefs.sportsChannelFixes,
                )
            }
        }
    }

    fun saveFix(fix: ChannelFix) {
        prefs.setChannelFix(fix)
        _ui.update { it.copy(fixes = prefs.sportsChannelFixes) }
    }

    fun networkChannels(league: SportsLeague): List<LiveChannel> =
        leagueChannels(league, _ui.value.channels)

    fun matchedNetworkKeyword(league: SportsLeague): String? {
        val channels = _ui.value.channels
        if (channels.isEmpty()) return null
        return league.networkKeywords.firstOrNull { keyword ->
            val k = normalizeSportsName(keyword)
            k.isNotBlank() && channels.any { c -> k in normalizeSportsName(c.name + " " + c.group) }
        }
    }

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

// ── Tela: tabelas ──────────────────────────────────────────────────────────

/**
 * Tabelas de ligas + League Networks (Lote 13, v1.23.0).
 *
 * Seções: ligas (chips), jogos de hoje (placar da ESPN), tabela (posição,
 * últimos 5, vagas, rebaixamento), canais da liga (League Networks — só
 * canais das playlists do usuário) e canais corrigidos (Fix Channel).
 * Sem dado da fonte, estado vazio honesto — nunca número inventado.
 */
@Composable
fun SportsTablesScreen(
    onBack: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onOpenGame: (String, String) -> Unit,
    viewModel: SportsTablesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    Column(Modifier.fillMaxSize().background(Background).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(end = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(
                "Tabelas de ligas", Modifier.weight(1f),
                sub = "Placar, tabela e canais — dados da ESPN",
                trailing = {
                    IconButton(onClick = { viewModel.refresh() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary)
                    }
                },
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Lote 16 (v1.26.0): só as ligas com tabela (as 8 de futebol) — as
            // 5 novas (NFL, NBA, NHL, MLB, MLS) não têm tabela na ESPN
            // (o summary só traz o fragmento da divisão dos 2 times do jogo).
            items(SPORTS_LEAGUES.filter { it.hasTable }, key = { it.id }) { league ->
                com.aotv.app.ui.common.FilterChipPill(
                    league.name, ui.league.id == league.id,
                    { viewModel.selectLeague(league) }, icon = Icons.Default.TableChart,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Jogos de hoje
            item(key = "games-header") {
                com.aotv.app.ui.common.SectionHeader(title = "Jogos de hoje", underline = true, compact = true)
            }
            if (ui.gamesLoading) {
                item(key = "games-loading") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            } else if (ui.games.isEmpty()) {
                item(key = "games-empty") {
                    Text(
                        "Nenhum jogo desta liga hoje.",
                        color = TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(ui.games, key = { "game:${it.eventId}" }) { game ->
                    GameCard(game, onClick = { onOpenGame(game.leagueId, game.eventId) })
                }
            }
            // Tabela
            item(key = "table-header") {
                com.aotv.app.ui.common.SectionHeader(
                    title = "Tabela",
                    underline = true, compact = true,
                    action = {
                        val at = ui.table?.updatedAtMs ?: 0L
                        if (at > 0L) Text("Atualizada às ${sportsUpdatedAt(at)}", color = TextTertiary, fontSize = 12.sp)
                    },
                )
            }
            // `ui` é propriedade delegada (by): smart cast não funciona nela —
            // ler para vals locais antes da cadeia (build #248).
            val tableError = ui.tableError
            val table = ui.table
            if (ui.tableLoading) {
                item(key = "table-loading") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            } else if (tableError != null) {
                item(key = "table-error") {
                    Text(tableError, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            } else if (table != null) {
                item(key = "table-body") { LeagueTableCard(table) }
            }
            // League Networks
            item(key = "networks-header") {
                com.aotv.app.ui.common.SectionHeader(title = "Canais desta liga", underline = true, compact = true)
            }
            val networks = viewModel.networkChannels(ui.league)
            if (networks.isEmpty()) {
                item(key = "networks-empty") {
                    Text(
                        "Nenhum canal desta liga nas suas listas.",
                        color = TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(networks, key = { "net:${it.id}" }) { channel ->
                    NetworkChannelRow(channel, onPlay = { onPlayChannel(channel.id) })
                }
            }
            // Fix Channel — canais corrigidos
            if (ui.fixes.isNotEmpty()) {
                item(key = "fixes-header") {
                    com.aotv.app.ui.common.SectionHeader(title = "Canais corrigidos", underline = true, compact = true)
                }
                val sortedFixes = ui.fixes.entries.sortedByDescending { it.value.at }
                items(sortedFixes, key = { "fix:${it.key}" }) { (mapKey, fix) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(fixScopeLabel(fix), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("→ ${fix.channelName}", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.removeFix(mapKey) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Delete, "Remover correção", tint = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

/** Card de um jogo: times, placar ou hora, selo de estado. */
@Composable
private fun GameCard(game: LeagueGame, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
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
            Text(
                when {
                    game.isFinished -> "Fim de jogo"
                    game.status == GameStatus.SCHEDULED -> sportsDateTime(game.dateMs)
                    game.clock.isNotBlank() -> game.clock
                    else -> ""
                },
                color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            if (game.venue.isNotBlank()) {
                Text(game.venue, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeamScoreCell(game.homeTeam, game.homeLogo, game.homeScore, Modifier.weight(1f))
            Text("x", color = TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            TeamScoreCell(game.awayTeam, game.awayLogo, game.awayScore, Modifier.weight(1f), alignEnd = true)
        }
    }
}

@Composable
private fun TeamScoreCell(team: String, logo: String, score: Int?, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = if (alignEnd) Alignment.End else Alignment.Start),
    ) {
        if (!alignEnd && logo.isNotBlank()) AsyncImage(logo, team, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
        Text(team, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, modifier = Modifier.weight(1f, fill = false))
        if (score != null) Text(score.toString(), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        if (alignEnd && logo.isNotBlank()) AsyncImage(logo, team, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
    }
}

/** Linha de canal da League Networks. */
@Composable
private fun NetworkChannelRow(channel: LiveChannel, onPlay: () -> Unit) {
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

/** Tabela: cabeçalho + linhas com zona colorida e últimos 5.
 * Lote 20 (v1.30.0): mais de um grupo (Libertadores) vira uma seção por
 * grupo, com o nome no cabeçalho; grupo único (o caso de todas as outras
 * ligas) renderiza como antes, sem cabeçalho. A legenda só mostra a zona
 * que a liga tem de verdade (sem "Rebaixamento" p/ quem não rebaixa). */
@Composable
private fun LeagueTableCard(table: LeagueTable) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        table.groups.forEach { group ->
            if (table.groups.size > 1) {
                Text(
                    group.name.ifBlank { "Tabela" },
                    color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("", modifier = Modifier.width(4.dp))
                Text("#", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Text("Time", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TableNumHeader("P")
                TableNumHeader("V")
                TableNumHeader("E")
                TableNumHeader("D")
                TableNumHeader("SG")
                TableNumHeader("Pts", bold = true)
                Text("Últimos 5", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
            }
            group.rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(
                        Modifier.size(width = 4.dp, height = 28.dp).clip(RoundedCornerShape(2.dp))
                            .background(
                                when (row.zone) {
                                    TableZone.CONTINENTAL -> Accent
                                    TableZone.RELEGATION -> Color(0xFFE5484D)
                                    null -> Color.Transparent
                                },
                            ),
                    )
                    Text(row.rank.toString(), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (row.logo.isNotBlank()) AsyncImage(row.logo, row.team, contentScale = ContentScale.Fit, modifier = Modifier.size(20.dp))
                        Text(row.team, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TableNumCell(row.played.toString())
                    TableNumCell(row.wins.toString())
                    TableNumCell(row.draws.toString())
                    TableNumCell(row.losses.toString())
                    TableNumCell(if (row.goalDiff > 0) "+${row.goalDiff}" else row.goalDiff.toString())
                    TableNumCell(row.points.toString(), bold = true)
                    Row(Modifier.width(72.dp), horizontalArrangement = Arrangement.spacedBy(3.dp, alignment = Alignment.CenterHorizontally)) {
                        row.form.forEach { result ->
                            Box(
                                Modifier.size(11.dp).clip(CircleShape).background(
                                    when (result) {
                                        FormResult.W -> Color(0xFF3DDC84)
                                        FormResult.D -> Color(0xFF8A93A6)
                                        FormResult.L -> Color(0xFFE5484D)
                                    },
                                ),
                            )
                        }
                        if (row.form.isEmpty()) Text("–", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }
        }
        if (table.league.topSpots > 0 && table.league.topLabel.isNotBlank() ||
            table.league.relegationSpots > 0
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
                if (table.league.topSpots > 0 && table.league.topLabel.isNotBlank()) ZoneLegend(Accent, table.league.topLabel)
                if (table.league.relegationSpots > 0) ZoneLegend(Color(0xFFE5484D), table.league.relegationLabel)
            }
        }
    }
}

@Composable
private fun TableNumHeader(text: String, bold: Boolean = false) {
    Text(text, color = TextTertiary, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
}

@Composable
private fun TableNumCell(text: String, bold: Boolean = false) {
    Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
}

@Composable
private fun ZoneLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(width = 10.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = TextTertiary, fontSize = 12.sp)
    }
}

// ── Tela: página do jogo ───────────────────────────────────────────────────

/**
 * Página do jogo (Lote 13, v1.23.0): placar, gols por minuto, estatísticas,
 * escalações, emissoras — tudo da ESPN, só quando a fonte traz. Sem dado,
 * a seção some (nunca inventada). "Assistir" toca o canal resolvido
 * (Fix Channel > League Networks); "Corrigir canal" abre o diálogo.
 */
@Composable
fun SportsGameScreen(
    onBack: () -> Unit,
    onPlayChannel: (String) -> Unit,
    viewModel: SportsGameViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val league = viewModel.league
    val pad = com.aotv.app.ui.common.pagePadding()
    var showFixDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Background).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(end = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(
                league?.name ?: "Jogo", Modifier.weight(1f),
                sub = "Dados da ESPN",
                trailing = {
                    IconButton(onClick = { viewModel.refresh() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary)
                    }
                },
            )
        }
        if (league == null) {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.SportsSoccer, "Liga desconhecida",
                "Esta liga não está na lista de tabelas.",
            )
            return@Column
        }
        if (ui.loading && ui.detail == null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            return@Column
        }
        // `ui` é propriedade delegada (by): smart cast não funciona nela (build #248).
        val error = ui.error
        if (error != null && ui.detail == null) {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.SportsSoccer, "Sem dados do jogo",
                error,
                action = { com.aotv.app.ui.common.AotvButton("Tentar de novo", { viewModel.refresh() }, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY) },
            )
            return@Column
        }
        val detail = ui.detail ?: return@Column
        val game = detail.game
        val resolved = viewModel.resolveChannel(game, league)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "score") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (game.isLive) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("AO VIVO", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(
                            when {
                                game.isFinished -> "Fim de jogo"
                                game.status == GameStatus.SCHEDULED -> sportsDateTime(game.dateMs)
                                game.clock.isNotBlank() -> game.clock
                                else -> ""
                            },
                            color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        GameTeamBig(game.homeTeam, game.homeLogo, game.homeScore)
                        Text("x", color = TextTertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        GameTeamBig(game.awayTeam, game.awayLogo, game.awayScore)
                    }
                    if (game.venue.isNotBlank()) Text(game.venue, color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (resolved != null) {
                            com.aotv.app.ui.common.AotvButton(
                                "Assistir: ${resolved.name}", { onPlayChannel(resolved.id) },
                                icon = Icons.Default.PlayArrow, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY, compact = true,
                            )
                        } else {
                            Text("Nenhum canal desta liga nas suas listas.", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        com.aotv.app.ui.common.AotvButton(
                            "Corrigir canal", { showFixDialog = true },
                            icon = Icons.Default.Edit, compact = true,
                        )
                    }
                }
            }
            // Lote 20 (v1.30.0): placar por período — só os períodos que o
            // jogo alcançou; jogo agendado não tem a seção.
            if (detail.periodScores.isNotEmpty()) {
                item(key = "periods-header") {
                    com.aotv.app.ui.common.SectionHeader(title = "Placar por período", underline = true, compact = true)
                }
                item(key = "periods") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        detail.periodScores.forEach { period ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(period.label, color = TextSecondary, fontSize = 13.sp)
                                Text(period.text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (detail.goals.isNotEmpty()) {
                item(key = "goals-header") {
                    com.aotv.app.ui.common.SectionHeader(title = "Gols", underline = true, compact = true)
                }
                items(detail.goals, key = { "goal:${it.minute}:${it.scorer}" }) { goal ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(goal.minute, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                goal.scorer + if (goal.ownGoal) " (contra)" else if (goal.penalty) " (pênalti)" else "",
                                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(goal.team, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (detail.stats.isNotEmpty()) {
                item(key = "stats-header") {
                    com.aotv.app.ui.common.SectionHeader(title = "Estatísticas", underline = true, compact = true)
                }
                item(key = "stats-body") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(detail.game.homeTeam, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(detail.game.awayTeam, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                        detail.stats.forEach { stat ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stat.home, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                                    Text(stat.label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                    Text(stat.away, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                                }
                                val hv = stat.home.trimEnd('%').toFloatOrNull()
                                val av = stat.away.trimEnd('%').toFloatOrNull()
                                if (hv != null && av != null && hv + av > 0f) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        com.aotv.app.ui.common.ProgressBar(
                                            hv / (hv + av), Modifier.weight(1f), height = 4.dp, fillColor = Accent,
                                        )
                                        Box(Modifier.width(8.dp))
                                        com.aotv.app.ui.common.ProgressBar(
                                            av / (hv + av), Modifier.weight(1f), height = 4.dp, fillColor = TextTertiary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // `ui` é propriedade delegada (by): smart cast não funciona nela (build #248).
            val table = ui.table
            if (table != null) {
                item(key = "game-table-header") {
                    com.aotv.app.ui.common.SectionHeader(title = "Tabela — ${table.league.name}", underline = true, compact = true)
                }
                item(key = "game-table-body") { LeagueTableCard(table) }
            }
            val homeLineup = detail.homeLineup
            val awayLineup = detail.awayLineup
            if (homeLineup != null || awayLineup != null) {
                item(key = "lineups-header") {
                    com.aotv.app.ui.common.SectionHeader(title = "Escalações", underline = true, compact = true)
                }
                if (homeLineup != null) item(key = "lineup-home") { LineupCard(homeLineup) }
                if (awayLineup != null) item(key = "lineup-away") { LineupCard(awayLineup) }
            }
            if (detail.broadcasts.isNotEmpty()) {
                item(key = "broadcasts") {
                    Text(
                        "Transmissão: ${detail.broadcasts.joinToString(", ")}",
                        color = TextTertiary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        if (showFixDialog) {
            val network = viewModel.matchedNetworkKeyword(league)
            FixChannelDialog(
                game = game,
                networkKeyword = network,
                channels = ui.channels,
                onSave = { fix ->
                    viewModel.saveFix(fix)
                    showFixDialog = false
                },
                onDismiss = { showFixDialog = false },
            )
        }
    }
}

@Composable
private fun GameTeamBig(team: String, logo: String, score: Int?) {
    Column(
        Modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (logo.isNotBlank()) AsyncImage(logo, team, contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
        Text(team, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (score != null) Text(score.toString(), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Escalação de um time: formação, titulares por posição e reservas. */
@Composable
private fun LineupCard(lineup: com.aotv.app.data.sports.TeamLineup) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lineup.logo.isNotBlank()) AsyncImage(lineup.logo, lineup.team, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
            Text(lineup.team, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (lineup.formation.isNotBlank()) Text(lineup.formation, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        lineup.starters.forEach { player ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(player.jersey.ifBlank { "–" }, color = TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                Text(player.name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (player.position.isNotBlank()) Text(player.position, color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (lineup.bench.isNotEmpty()) {
            Text("Reservas", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            lineup.bench.forEach { player ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(player.jersey.ifBlank { "–" }, color = TextTertiary, fontSize = 13.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                    Text(player.name, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (player.position.isNotBlank()) Text(player.position, color = TextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Diálogo: Fix Channel ───────────────────────────────────────────────────

// Lote 15 (v1.25.0 — Bloco 1): o diálogo virou `internal` p/ a aba de
// Esportes (SportsTabScreen.kt) reutilizar o mesmo — sem duplicar.
// (A extração p/ arquivo próprio foi revertida: o teste do Lote 13 (B9)
// cobra o diálogo neste arquivo.)
@Composable
internal fun FixChannelDialog(
    game: LeagueGame,
    networkKeyword: String?,
    channels: List<LiveChannel>,
    onSave: (ChannelFix) -> Unit,
    onDismiss: () -> Unit,
) {
    var scope by remember { mutableStateOf(FixScope.GAME) }
    var teamChoice by remember { mutableStateOf(game.homeTeam) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LiveChannel?>(null) }
    val needle = query.trim().lowercase()
    val filtered = remember(channels, needle) {
        if (needle.isBlank()) channels
        else channels.filter { it.name.lowercase().contains(needle) || it.group.lowercase().contains(needle) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Corrigir canal", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vale para:", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                FixScopeOption("Só este jogo", scope == FixScope.GAME) { scope = FixScope.GAME }
                FixScopeOption("Todos os jogos do ${game.homeTeam}", scope == FixScope.TEAM && teamChoice == game.homeTeam) {
                    scope = FixScope.TEAM
                    teamChoice = game.homeTeam
                }
                FixScopeOption("Todos os jogos do ${game.awayTeam}", scope == FixScope.TEAM && teamChoice == game.awayTeam) {
                    scope = FixScope.TEAM
                    teamChoice = game.awayTeam
                }
                if (networkKeyword != null) {
                    val label = networkKeyword.replaceFirstChar { it.uppercase() }
                    FixScopeOption("Todos os jogos da $label", scope == FixScope.NETWORK) { scope = FixScope.NETWORK }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Buscar canal") },
                    leadingIcon = { Icon(Icons.Default.Search, "Buscar", tint = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { channel ->
                        val isSelected = selected?.id == channel.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Accent.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selected = channel }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(width = 40.dp, height = 28.dp), contentAlignment = Alignment.Center) {
                                if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                else Text(channel.name.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.ExtraBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, color = if (isSelected) Accent else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (channel.group.isNotBlank()) Text(channel.group, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "no-channel") {
                            Text("Nenhum canal com esse nome nas suas listas.", color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val channel = selected ?: return@TextButton
                    val key = when (scope) {
                        FixScope.GAME -> game.eventId
                        FixScope.TEAM -> teamChoice
                        FixScope.NETWORK -> networkKeyword.orEmpty()
                    }
                    if (key.isBlank()) return@TextButton
                    onSave(ChannelFix(scope, key, channel.id, channel.name, System.currentTimeMillis()))
                },
                enabled = selected != null,
            ) { Text("Salvar correção", color = Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
        },
    )
}

@Composable
private fun FixScopeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = TextTertiary),
        )
        Text(label, color = TextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
