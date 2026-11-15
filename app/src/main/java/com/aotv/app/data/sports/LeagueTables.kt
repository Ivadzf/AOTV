package com.aotv.app.data.sports

import android.content.Context
import com.aotv.app.data.live.LiveChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tabelas de ligas + League Networks + páginas de jogo (Lote 13, v1.23.0).
 *
 * FONTE — ESPN pública, SEM chave (verificado por curl em 2026-11-15):
 * - Placar do dia: `site.api.espn.com/apis/site/v2/sports/soccer/{liga}/scoreboard`
 *   (+ `?dates=YYYYMMDD`) → HTTP 200 (bra.1, eng.1, esp.1, ita.1, ger.1,
 *   fra.1, por.1, ned.1, uefa.champions). Jogos com placar, relógio e o
 *   campo `form` ("DWLWL") do time — os últimos 5 vêm DA FONTE, sem
 *   chamada extra.
 * - Página do jogo: `.../summary?event={id}` → HTTP 200. Traz placar,
 *   gols por minuto (`details[]` com `scoringPlay`), estatísticas do jogo
 *   (`boxscore.teams[].statistics`), escalações (`rosters[]` com
 *   `formation` + `roster[]` com `position`/`jersey`), a TABELA COMPLETA
 *   (`standings.groups[0].standings.entries[]`, 20 times) e os últimos 5
 *   de cada time (`lastFiveGames[]` com `gameResult`).
 * - A tabela NÃO vem de endpoint próprio: `.../standings` dá **404**
 *   ("Event ID 'null' not found") p/ futebol. Por isso a tabela é lida do
 *   `summary` de um jogo qualquer da liga (o placar do dia dá o id).
 * - A Core API (`sports.core.api.espn.com/.../standings`) devolve só um
 *   `$ref` e, seguido, 0 itens — NÃO usada.
 *
 * TheSportsDB v1 NÃO é fonte de tabela aqui (verificado em 2026-11-15):
 * a chave "3" está **morta (403 permanente)** e a chave de teste "123"
 * **trunca o `lookuptable` aos 5 primeiros** (e o `all_leagues` a 10
 * ligas; 4480/4481/4482 voltam corpo vazio). Tabela de 5 linhas não é
 * tabela. Nenhuma chave da TheSportsDB é embutida.
 *
 * REGRA DE DADO HONESTO (como [SportsScores.kt]): sem resposta da fonte,
 * estado vazio honesto — nunca dado inventado. Sem jogo no placar do dia,
 * o repositório tenta os 7 dias anteriores p/ achar um id de evento; fora
 * de temporada (sem jogo em 7 dias), a tabela volta null e a tela avisa
 * que não há dados. Zonas (vagas/rebaixamento) são só a cor da posição
 * seguindo o formato publicado da liga (ver [SportsLeague]): a posição e
 * os pontos são dados reais; a faixa colorida é apresentação.
 *
 * Cache em disco (mesmo padrão do LiveTvRepository): tabela 6 h, placar
 * do dia 2 min (placar ao vivo muda), placar de dia passado 6 h, detalhe
 * do jogo 5 min. Sem rede, vale o cache salvo (com a hora da atualização
 * na tela); sem cache, estado vazio.
 */

/** Normaliza nome p/ casamento (minúsculo, sem acento, sem pontuação). */
fun normalizeSportsName(s: String): String {
    val noAccent = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return noAccent.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim()
        .replace(Regex("\\s+"), " ")
}

/** Resultado de um jogo p/ os "últimos 5": vitória, empate ou derrota. */
enum class FormResult { W, D, L }

/** Faixa da tabela: vagas continentais ou rebaixamento (só cor + rótulo). */
enum class TableZone { CONTINENTAL, RELEGATION }

/**
 * Uma liga com tabela. [espnSlug] é o slug da ESPN (`bra.1`, `eng.1`...).
 * [topSpots]/[topLabel] = vagas diretas p/ a competição continental
 * (só as diretas — pré-Libertadores e playoffs NÃO são marcados);
 * [relegationSpots] = últimos colocados que caem (só os diretos).
 * [networkKeywords] = nomes de canais/emissoras que passam a liga no
 * Brasil, p/ a League Networks (casamento com as playlists do usuário).
 * Só ligas de pontos corridos (tabela única) — mata-mata e fase de grupos
 * (Champions, Libertadores) ficam p/ outra frente (tabela por grupo).
 */
data class SportsLeague(
    val id: String,
    val name: String,
    val espnSlug: String,
    val topSpots: Int,
    val topLabel: String,
    val relegationSpots: Int,
    val relegationLabel: String = "Rebaixamento",
    val networkKeywords: List<String>,
)

/** Ligas com tabela (8, todas de pontos corridos; slugs ESPN confirmados). */
val SPORTS_LEAGUES = listOf(
    SportsLeague(
        "bra1", "Brasileirão Série A", "bra.1", 4, "Libertadores", 4,
        networkKeywords = listOf("premiere", "sportv", "globo", "record", "cazetv", "amazon", "prime video", "band"),
    ),
    SportsLeague(
        "eng1", "Premier League", "eng.1", 4, "Champions League", 3,
        networkKeywords = listOf("espn", "disney"),
    ),
    SportsLeague(
        "esp1", "La Liga", "esp.1", 4, "Champions League", 3,
        networkKeywords = listOf("espn", "disney"),
    ),
    SportsLeague(
        "ita1", "Serie A (Itália)", "ita.1", 4, "Champions League", 3,
        networkKeywords = listOf("espn", "disney", "dazn"),
    ),
    SportsLeague(
        "ger1", "Bundesliga", "ger.1", 4, "Champions League", 2,
        networkKeywords = listOf("espn", "disney", "dazn", "sky"),
    ),
    SportsLeague(
        "fra1", "Ligue 1", "fra.1", 3, "Champions League", 2,
        networkKeywords = listOf("dazn", "espn"),
    ),
    SportsLeague(
        "por1", "Primeira Liga", "por.1", 2, "Champions League", 2,
        networkKeywords = listOf("espn", "sport tv", "sporttv", "dazn"),
    ),
    SportsLeague(
        "ned1", "Eredivisie", "ned.1", 2, "Champions League", 2,
        networkKeywords = listOf("espn", "ziggo"),
    ),
)

/** Uma linha da tabela. [goalsFor]/[goalsAgainst] são null quando a fonte não traz (a ESPN traz só o saldo). */
data class TableRow(
    val rank: Int,
    val team: String,
    val logo: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int?,
    val goalsAgainst: Int?,
    val goalDiff: Int,
    val points: Int,
    /** Últimos 5 da fonte (campo `form` do placar); vazio quando a fonte não trouxe. */
    val form: List<FormResult> = emptyList(),
    val zone: TableZone? = null,
)

/** Tabela de uma liga, com a hora da atualização (p/ mostrar "atualizada às HH:mm"). */
data class LeagueTable(
    val league: SportsLeague,
    val rows: List<TableRow>,
    val updatedAtMs: Long,
)

/** Estado do jogo (o `status.type.name` da ESPN). */
enum class GameStatus { SCHEDULED, LIVE, HALFTIME, FULL_TIME, POSTPONED, OTHER }

/** Um jogo do placar da ESPN. [homeScore]/[awayScore] null antes de começar. */
data class LeagueGame(
    val eventId: String,
    val leagueId: String,
    val homeTeam: String,
    val homeLogo: String,
    val homeScore: Int?,
    val awayTeam: String,
    val awayLogo: String,
    val awayScore: Int?,
    val status: GameStatus,
    /** Relógio/rótulo da fonte ("45'", "FT", "Intervalo"...). */
    val clock: String,
    val dateMs: Long,
    val venue: String = "",
) {
    val isLive get() = status == GameStatus.LIVE || status == GameStatus.HALFTIME
    val isFinished get() = status == GameStatus.FULL_TIME
    /** "Time A 2 x 1 Time B" p/ o placar fixado — null antes de começar. */
    val scoreText: String? get() =
        if (homeScore != null && awayScore != null) "$homeScore x $awayScore" else null
}

/** Um gol (ou evento de gol) da página do jogo: minuto, time e autor. */
data class GameGoal(
    val minute: String,
    val team: String,
    val scorer: String,
    val ownGoal: Boolean = false,
    val penalty: Boolean = false,
)

/** Uma estatística do jogo (valor dos dois times, como a fonte traz). */
data class GameStat(val label: String, val home: String, val away: String)

/** Um jogador da escalação. [position] é a sigla da fonte (G/D/M/F). */
data class LineupPlayer(
    val name: String,
    val jersey: String,
    val position: String,
    val starter: Boolean,
)

/** Escalação de um time: formação + titulares + reservas (só quando a fonte traz). */
data class TeamLineup(
    val team: String,
    val logo: String,
    val formation: String,
    val starters: List<LineupPlayer>,
    val bench: List<LineupPlayer>,
)

/** Página do jogo: placar, gols, estatísticas, escalações e emissoras. */
data class GameDetail(
    val game: LeagueGame,
    val goals: List<GameGoal>,
    val stats: List<GameStat>,
    val homeLineup: TeamLineup?,
    val awayLineup: TeamLineup?,
    /** Emissoras da fonte (`broadcasts[].media`), só p/ mostrar — nunca inventadas. */
    val broadcasts: List<String>,
)

/**
 * League Networks (item 2 da análise PlusIPTV): canais da liga achados
 * NAS PRÓPRIAS PLAYLISTS do usuário. Casa o nome/grupo do canal com as
 * palavras da liga ([SportsLeague.networkKeywords]), sem acento. Sem
 * canal, a lista volta vazia — a tela avisa, sem inventar canal.
 */
fun leagueChannels(league: SportsLeague, channels: List<LiveChannel>): List<LiveChannel> {
    val keywords = league.networkKeywords.map { normalizeSportsName(it) }.filter { it.isNotBlank() }
    if (keywords.isEmpty()) return emptyList()
    return channels.filter { c ->
        val hay = normalizeSportsName(c.name + " " + c.group)
        keywords.any { k -> k in hay }
    }
}

/** Construtores de URL + parsers puros da ESPN (sem rede — testáveis por marcadores). */
object EspnSportsApi {
    const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"

    fun scoreboardUrl(slug: String, dateYmd: String): String =
        "$BASE/$slug/scoreboard?dates=$dateYmd"

    fun summaryUrl(slug: String, eventId: String): String =
        "$BASE/$slug/summary?event=$eventId"

    /** "yyyyMMdd" em UTC (a ESPN trabalha em UTC). */
    fun ymd(ms: Long): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(ms))
    }

    /** ISO da ESPN ("2026-11-18T22:30Z", com ou sem segundos) → epoch ms; 0 quando não dá p/ ler. */
    fun parseDateMs(iso: String): Long {
        if (iso.isBlank()) return 0L
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'")) {
            val f = SimpleDateFormat(pattern, Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = runCatching { f.parse(iso)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    private fun statusOf(typeName: String): GameStatus = when (typeName) {
        "STATUS_SCHEDULED" -> GameStatus.SCHEDULED
        "STATUS_IN_PROGRESS" -> GameStatus.LIVE
        "STATUS_HALFTIME" -> GameStatus.HALFTIME
        "STATUS_FULL_TIME" -> GameStatus.FULL_TIME
        "STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_SUSPENDED" -> GameStatus.POSTPONED
        else -> GameStatus.OTHER
    }

    private fun logoOf(team: JSONObject): String {
        val direct = team.optString("logo")
        if (direct.isNotBlank()) return direct
        val logos = team.optJSONArray("logos") ?: return ""
        for (i in 0 until logos.length()) {
            val href = logos.optJSONObject(i)?.optString("href").orEmpty()
            if (href.isNotBlank()) return href
        }
        return ""
    }

    /**
     * Jogos do placar (`scoreboard`): um [LeagueGame] por evento. Evento sem
     * os dois times é descartado (nunca meio jogo inventado).
     */
    fun parseScoreboard(json: String, leagueId: String): List<LeagueGame> {
        val out = ArrayList<LeagueGame>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val events = root.optJSONArray("events") ?: return out
        for (ei in 0 until events.length()) {
            val e = events.optJSONObject(ei) ?: continue
            val eventId = e.optString("id")
            if (eventId.isBlank()) continue
            val dateMs = parseDateMs(e.optString("date"))
            val statusObj = e.optJSONObject("status")
            val type = statusObj?.optJSONObject("type")
            val status = statusOf(type?.optString("name").orEmpty())
            val clock = statusObj?.optString("displayClock")?.takeIf { it.isNotBlank() }
                ?: type?.optString("detail").orEmpty()
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val venue = e.optJSONObject("venue")?.optString("fullName").orEmpty()
                .ifBlank { comp.optJSONObject("venue")?.optString("fullName").orEmpty() }
            var home: JSONObject? = null
            var away: JSONObject? = null
            val competitors = comp.optJSONArray("competitors") ?: continue
            for (ci in 0 until competitors.length()) {
                val c = competitors.optJSONObject(ci) ?: continue
                when (c.optString("homeAway")) {
                    "home" -> home = c
                    "away" -> away = c
                }
            }
            val h = home ?: continue
            val a = away ?: continue
            val homeTeam = h.optJSONObject("team") ?: continue
            val awayTeam = a.optJSONObject("team") ?: continue
            out += LeagueGame(
                eventId = eventId, leagueId = leagueId,
                homeTeam = homeTeam.optString("displayName"),
                homeLogo = logoOf(homeTeam),
                homeScore = h.optString("score").toIntOrNull(),
                awayTeam = awayTeam.optString("displayName"),
                awayLogo = logoOf(awayTeam),
                awayScore = a.optString("score").toIntOrNull(),
                status = status, clock = clock, dateMs = dateMs, venue = venue,
            )
        }
        return out
    }

    /**
     * Últimos 5 por time, do campo `form` do placar ("DWLWL" — a fonte).
     * Chave = nome do time como a fonte traz. Time sem `form` não entra.
     */
    fun parseFormMap(json: String): Map<String, List<FormResult>> {
        val out = LinkedHashMap<String, List<FormResult>>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val events = root.optJSONArray("events") ?: return out
        for (ei in 0 until events.length()) {
            val e = events.optJSONObject(ei) ?: continue
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            for (ci in 0 until competitors.length()) {
                val c = competitors.optJSONObject(ci) ?: continue
                val team = c.optJSONObject("team")?.optString("displayName").orEmpty()
                val form = c.optString("form")
                if (team.isBlank() || form.isBlank()) continue
                val results = form.mapNotNull {
                    when (it) {
                        'W' -> FormResult.W
                        'D' -> FormResult.D
                        'L' -> FormResult.L
                        else -> null
                    }
                }.takeLast(5)
                if (results.isNotEmpty()) out[team] = results
            }
        }
        return out
    }

    /** Últimos 5 por time, do `lastFiveGames` do summary (só os 2 times do jogo). */
    fun parseLastFive(json: String): Map<String, List<FormResult>> {
        val out = LinkedHashMap<String, List<FormResult>>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val lastFive = root.optJSONArray("lastFiveGames") ?: return out
        for (i in 0 until lastFive.length()) {
            val entry = lastFive.optJSONObject(i) ?: continue
            val team = entry.optJSONObject("team")?.optString("displayName").orEmpty()
            if (team.isBlank()) continue
            val events = entry.optJSONArray("events") ?: continue
            val results = ArrayList<FormResult>()
            for (j in 0 until events.length()) {
                when (events.optJSONObject(j)?.optString("gameResult")) {
                    "W" -> results += FormResult.W
                    "D" -> results += FormResult.D
                    "L" -> results += FormResult.L
                }
            }
            if (results.isNotEmpty()) out[team] = results.takeLast(5)
        }
        return out
    }

    private fun statOf(stats: org.json.JSONArray?, name: String): Int? {
        if (stats == null) return null
        for (i in 0 until stats.length()) {
            val s = stats.optJSONObject(i) ?: continue
            if (s.optString("name") == name) return s.optDouble("value", Double.NaN).takeIf { !it.isNaN() }?.toInt()
        }
        return null
    }

    /**
     * Tabela a partir do `standings` do summary. null quando o summary não
     * traz tabela (jogo de copa, liga sem tabela). A zona vem da posição +
     * o formato da liga — nunca de dado inventado.
     */
    fun parseTable(json: String, league: SportsLeague, form: Map<String, List<FormResult>>, updatedAtMs: Long): LeagueTable? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val groups = root.optJSONObject("standings")?.optJSONArray("groups") ?: return null
        val first = groups.optJSONObject(0) ?: return null
        val entries = first.optJSONObject("standings")?.optJSONArray("entries") ?: return null
        val raw = ArrayList<TableRow>()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val team = e.optString("team")
            if (team.isBlank()) continue
            val stats = e.optJSONArray("stats")
            val rank = statOf(stats, "rank") ?: (i + 1)
            val played = statOf(stats, "gamesPlayed") ?: 0
            val wins = statOf(stats, "wins") ?: 0
            val draws = statOf(stats, "ties") ?: 0
            val losses = statOf(stats, "losses") ?: 0
            val gdRaw = statOf(stats, "pointDifferential") ?: 0
            val points = statOf(stats, "points") ?: 0
            raw += TableRow(
                rank = rank, team = team, logo = e.optString("logo"),
                played = played, wins = wins, draws = draws, losses = losses,
                goalsFor = statOf(stats, "goalsFor"),
                goalsAgainst = statOf(stats, "goalsAgainst"),
                goalDiff = gdRaw, points = points,
                form = form[team].orEmpty(),
            )
        }
        if (raw.isEmpty()) return null
        val sorted = raw.sortedBy { it.rank }
        val total = sorted.size
        val zoned = sorted.map { row ->
            val zone = when {
                row.rank <= league.topSpots -> TableZone.CONTINENTAL
                league.relegationSpots > 0 && row.rank > total - league.relegationSpots -> TableZone.RELEGATION
                else -> null
            }
            row.copy(zone = zone)
        }
        return LeagueTable(league = league, rows = zoned, updatedAtMs = updatedAtMs)
    }

    /** Estatísticas que valem na página do jogo (nome da ESPN → rótulo em PT). */
    private val GAME_STATS = listOf(
        "possessionPct" to "Posse de bola",
        "totalShots" to "Finalizações",
        "shotsOnTarget" to "No alvo",
        "wonCorners" to "Escanteios",
        "foulsCommitted" to "Faltas",
        "yellowCards" to "Amarelos",
        "redCards" to "Vermelhos",
        "offsides" to "Impedimentos",
        "saves" to "Defesas",
    )

    private fun statsOf(teamObj: JSONObject): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val stats = teamObj.optJSONArray("statistics") ?: return out
        for (i in 0 until stats.length()) {
            val s = stats.optJSONObject(i) ?: continue
            val name = s.optString("name")
            val value = s.optString("displayValue")
            if (name.isNotBlank() && value.isNotBlank()) out[name] = value
        }
        return out
    }

    private fun parseLineup(rosterObj: JSONObject): TeamLineup? {
        val team = rosterObj.optJSONObject("team") ?: return null
        val name = team.optString("displayName")
        if (name.isBlank()) return null
        val formation = rosterObj.optString("formation")
        val starters = ArrayList<LineupPlayer>()
        val bench = ArrayList<LineupPlayer>()
        val roster = rosterObj.optJSONArray("roster") ?: return TeamLineup(name, logoOf(team), formation, emptyList(), emptyList())
        for (i in 0 until roster.length()) {
            val p = roster.optJSONObject(i) ?: continue
            val athlete = p.optJSONObject("athlete") ?: continue
            val playerName = athlete.optString("displayName")
            if (playerName.isBlank()) continue
            val pos = p.optJSONObject("position")?.optString("abbreviation").orEmpty()
            val player = LineupPlayer(
                name = playerName,
                jersey = p.optString("jersey"),
                position = pos,
                starter = p.optBoolean("starter", false),
            )
            if (player.starter) starters += player else bench += player
        }
        // Titulares na ordem da posição (G → D → M → F); reservas na ordem da fonte.
        val rank = mapOf("G" to 0, "GK" to 0, "D" to 1, "DF" to 1, "M" to 2, "MF" to 2, "F" to 3, "FW" to 3)
        starters.sortWith(compareBy { rank[it.position] ?: 4 })
        return TeamLineup(name, logoOf(team), formation, starters, bench)
    }

    /**
     * Página do jogo a partir do summary. null quando o summary não traz o
     * jogo (id inválido, resposta vazia). Gols, estatísticas e escalações
     * só existem quando a fonte traz — jogo futuro volta com as seções
     * vazias, nunca com dado chutado.
     */
    fun parseGameDetail(json: String, leagueId: String): GameDetail? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val header = root.optJSONObject("header") ?: return null
        val comp = header.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val eventId = comp.optString("id")
        if (eventId.isBlank()) return null
        val statusObj = comp.optJSONObject("status")
        val type = statusObj?.optJSONObject("type")
        val status = statusOf(type?.optString("name").orEmpty())
        val clock = statusObj?.optString("displayClock")?.takeIf { it.isNotBlank() }
            ?: type?.optString("detail").orEmpty()
        val competitors = comp.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null
        var away: JSONObject? = null
        for (ci in 0 until competitors.length()) {
            val c = competitors.optJSONObject(ci) ?: continue
            when (c.optString("homeAway")) {
                "home" -> home = c
                "away" -> away = c
            }
        }
        val h = home ?: return null
        val a = away ?: return null
        val homeTeam = h.optJSONObject("team") ?: return null
        val awayTeam = a.optJSONObject("team") ?: return null
        val game = LeagueGame(
            eventId = eventId, leagueId = leagueId,
            homeTeam = homeTeam.optString("displayName"),
            homeLogo = logoOf(homeTeam),
            homeScore = h.optString("score").toIntOrNull(),
            awayTeam = awayTeam.optString("displayName"),
            awayLogo = logoOf(awayTeam),
            awayScore = a.optString("score").toIntOrNull(),
            status = status, clock = clock,
            dateMs = parseDateMs(comp.optString("date")),
            venue = root.optJSONObject("gameInfo")?.optJSONObject("venue")?.optString("fullName").orEmpty(),
        )
        val goals = ArrayList<GameGoal>()
        val details = comp.optJSONArray("details") ?: org.json.JSONArray()
        for (i in 0 until details.length()) {
            val d = details.optJSONObject(i) ?: continue
            if (!d.optBoolean("scoringPlay", false)) continue
            val teamName = d.optJSONObject("team")?.optString("displayName").orEmpty()
            val scorer = d.optJSONArray("participants")?.optJSONObject(0)
                ?.optJSONObject("athlete")?.optString("displayName").orEmpty()
            val minute = d.optJSONObject("clock")?.optString("displayValue").orEmpty()
            if (teamName.isBlank() || minute.isBlank()) continue
            goals += GameGoal(
                minute = minute, team = teamName,
                scorer = scorer.ifBlank { teamName },
                ownGoal = d.optBoolean("ownGoal", false),
                penalty = d.optBoolean("penaltyKick", false),
            )
        }
        val boxscore = root.optJSONObject("boxscore")?.optJSONArray("teams")
        var homeStats = emptyMap<String, String>()
        var awayStats = emptyMap<String, String>()
        if (boxscore != null) {
            for (i in 0 until boxscore.length()) {
                val t = boxscore.optJSONObject(i) ?: continue
                val name = t.optJSONObject("team")?.optString("displayName").orEmpty()
                val stats = statsOf(t)
                if (name == game.homeTeam) homeStats = stats
                if (name == game.awayTeam) awayStats = stats
            }
        }
        val stats = GAME_STATS.mapNotNull { (espnName, label) ->
            val hv = homeStats[espnName]
            val av = awayStats[espnName]
            if (hv == null && av == null) null
            else GameStat(label, hv ?: "–", av ?: "–")
        }
        var homeLineup: TeamLineup? = null
        var awayLineup: TeamLineup? = null
        val rosters = root.optJSONArray("rosters")
        if (rosters != null) {
            for (i in 0 until rosters.length()) {
                val r = rosters.optJSONObject(i) ?: continue
                when (r.optString("homeAway")) {
                    "home" -> homeLineup = parseLineup(r)
                    "away" -> awayLineup = parseLineup(r)
                }
            }
        }
        val broadcasts = ArrayList<String>()
        val bc = root.optJSONArray("broadcasts")
        if (bc != null) {
            for (i in 0 until bc.length()) {
                val media = bc.optJSONObject(i)?.optJSONObject("media") ?: continue
                val name = media.optString("shortName").ifBlank { media.optString("name") }
                if (name.isNotBlank() && name !in broadcasts) broadcasts += name
            }
        }
        return GameDetail(game, goals, stats, homeLineup, awayLineup, broadcasts)
    }
}

/**
 * Repositório da ESPN (Lote 13, v1.23.0): HTTP + cache em disco.
 * Tudo suspenso, tudo com `runCatching` — falha de rede volta null (ou o
 * cache salvo), nunca dado inventado. Sem dependência nova: OkHttp do
 * [com.aotv.app.data.remote.AppDns] + org.json, como o resto do app.
 */
@Singleton
class EspnSportsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "sports").apply { mkdirs() }
    private val client = com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private val tableTtlMs = 6 * 60 * 60 * 1000L
    private val todayTtlMs = 2 * 60 * 1000L
    private val detailTtlMs = 5 * 60 * 1000L

    private fun get(url: String): String? = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) AOTV")
            .header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun cacheFile(name: String): File = File(dir, name)

    private fun readCache(name: String, ttlMs: Long): Pair<String, Long>? = runCatching {
        val f = cacheFile(name)
        if (!f.exists()) return null
        val text = f.readText()
        if (text.isBlank()) return null
        val at = f.lastModified()
        if (System.currentTimeMillis() - at > ttlMs) return null
        text to at
    }.getOrNull()

    private fun readCacheStale(name: String): Pair<String, Long>? = runCatching {
        val f = cacheFile(name)
        if (!f.exists()) return null
        val text = f.readText()
        if (text.isBlank()) return null
        text to f.lastModified()
    }.getOrNull()

    private fun writeCache(name: String, text: String) = runCatching {
        cacheFile(name).writeText(text)
    }

    private suspend fun fetchScoreboard(slug: String, dateYmd: String, today: Boolean): String? =
        withContext(Dispatchers.IO) {
            val name = "scoreboard_${slug}_$dateYmd.json"
            val ttl = if (today) todayTtlMs else tableTtlMs
            readCache(name, ttl)?.first
                ?: get(EspnSportsApi.scoreboardUrl(slug, dateYmd))?.also { writeCache(name, it) }
                ?: readCacheStale(name)?.first
        }

    private suspend fun fetchSummary(slug: String, eventId: String): String? =
        withContext(Dispatchers.IO) {
            val name = "detail_$eventId.json"
            readCache(name, detailTtlMs)?.first
                ?: get(EspnSportsApi.summaryUrl(slug, eventId))?.also { writeCache(name, it) }
                ?: readCacheStale(name)?.first
        }

    /**
     * Jogos de um dia (placar do dia). Sem rede e sem cache, lista vazia —
     * nunca jogo inventado.
     */
    suspend fun gamesOn(league: SportsLeague, dateMs: Long): List<LeagueGame> {
        val ymd = EspnSportsApi.ymd(dateMs)
        val today = ymd == EspnSportsApi.ymd(System.currentTimeMillis())
        val json = fetchScoreboard(league.espnSlug, ymd, today) ?: return emptyList()
        return EspnSportsApi.parseScoreboard(json, league.id)
    }

    /**
     * Tabela da liga. Pega um id de evento do placar (hoje, senão os 7 dias
     * anteriores) e lê a tabela do summary dele. Os últimos 5 vêm dos
     * placares recentes (campo `form` da fonte) até cobrir os times da
     * tabela (no máximo 7 dias). Fora de temporada, null.
     */
    suspend fun leagueTable(league: SportsLeague): LeagueTable? {
        val cached = readCache("table_${league.espnSlug}.json", tableTtlMs)
        if (cached != null) {
            val table = parseCachedTable(cached.first, league, cached.second)
            if (table != null) return table
        }
        val now = System.currentTimeMillis()
        var eventId: String? = null
        var summaryJson: String? = null
        for (back in 0..7) {
            val dayMs = now - back * 24L * 60 * 60 * 1000
            val games = gamesOn(league, dayMs)
            val first = games.firstOrNull() ?: continue
            val summary = fetchSummary(league.espnSlug, first.eventId) ?: continue
            eventId = first.eventId
            summaryJson = summary
            break
        }
        if (eventId == null || summaryJson == null) {
            return readCacheStale("table_${league.espnSlug}.json")
                ?.let { parseCachedTable(it.first, league, it.second) }
        }
        // Últimos 5: o `form` dos placares recentes + o `lastFiveGames` do
        // summary (os 2 times do jogo). Sem `form` p/ um time, a linha fica
        // sem os 5 — nunca inventado.
        val form = LinkedHashMap<String, List<FormResult>>()
        form.putAll(EspnSportsApi.parseLastFive(summaryJson))
        val tableTeams = EspnSportsApi.parseTable(summaryJson, league, emptyMap(), now)?.rows?.map { it.team }.orEmpty()
        for (back in 0..7) {
            if (tableTeams.isNotEmpty() && tableTeams.all { it in form }) break
            val dayMs = now - back * 24L * 60 * 60 * 1000
            val ymd = EspnSportsApi.ymd(dayMs)
            val today = back == 0
            val raw = fetchScoreboard(league.espnSlug, ymd, today) ?: continue
            EspnSportsApi.parseFormMap(raw).forEach { (team, results) -> form.putIfAbsent(team, results) }
        }
        val table = EspnSportsApi.parseTable(summaryJson, league, form, now) ?: return null
        writeCache("table_${league.espnSlug}.json", summaryJson)
        return table
    }

    /** Tabela salva em disco (o summary cru + a hora do arquivo). */
    private fun parseCachedTable(json: String, league: SportsLeague, atMs: Long): LeagueTable? {
        // O cache guarda o summary cru; os últimos 5 voltam do próprio
        // summary (`lastFiveGames`) — os demais times ficam sem os 5 até a
        // próxima atualização com rede. Nunca inventado.
        val form = EspnSportsApi.parseLastFive(json)
        return EspnSportsApi.parseTable(json, league, form, atMs)
    }

    /** Página de um jogo (placar, gols, estatísticas, escalações). */
    suspend fun gameDetail(league: SportsLeague, eventId: String): GameDetail? {
        if (eventId.isBlank()) return null
        val json = fetchSummary(league.espnSlug, eventId) ?: return null
        return EspnSportsApi.parseGameDetail(json, league.id)
    }
}
