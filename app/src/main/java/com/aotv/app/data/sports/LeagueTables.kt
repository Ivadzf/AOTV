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
 * Tabelas de ligas + League Networks + páginas de jogo (Lote 13, v1.23.0;
 * outros esportes no Lote 16, v1.26.0).
 *
 * FONTE — ESPN pública, SEM chave (verificado por curl em 2026-11-15/16):
 * - Placar do dia: `site.api.espn.com/apis/site/v2/sports/{esporte}/{liga}/scoreboard`
 *   (+ `?dates=YYYYMMDD`) → HTTP 200. Futebol: `soccer/{bra.1, eng.1,
 *   esp.1, ita.1, ger.1, fra.1, por.1, ned.1}`. Americanos (Lote 16):
 *   `football/nfl` (14 eventos), `basketball/nba` (8), `hockey/nhl` (2),
 *   `baseball/mlb` (1), `soccer/usa.1` (MLS, 2) — contagens do scoreboard
 *   sem `dates` em 2026-11-16. Jogos com placar, relógio e (só futebol)
 *   o campo `form` ("DWLWL") do time — os últimos 5 vêm DA FONTE.
 * - Página do jogo: `.../summary?event={id}` → HTTP 200. Traz placar,
 *   gols por minuto (`details[]` com `scoringPlay`), estatísticas do jogo
 *   (`boxscore.teams[].statistics`), escalações (`rosters[]` com
 *   `formation` + `roster[]` com `position`/`jersey`), a TABELA COMPLETA
 *   (`standings.groups[0].standings.entries[]`, 20 times) e os últimos 5
 *   de cada time (`lastFiveGames[]` com `gameResult`) — TUDO ISSO SÓ P/
 *   O FUTEBOL.
 * - A tabela NÃO vem de endpoint próprio: `.../standings` dá **404**
 *   ("Event ID 'null' not found") p/ futebol. Por isso a tabela é lida do
 *   `summary` de um jogo qualquer da liga (o placar do dia dá o id).
 * - A Core API (`sports.core.api.espn.com/.../standings`) devolve só um
 *   `$ref` e, seguido, 0 itens — NÃO usada.
 *
 * TABELAS SÓ P/ O FUTEBOL (verificado ao vivo em 2026-11-16): o summary
 * das 5 ligas americanas NÃO traz tabela de verdade — traz só o fragmento
 * da divisão/conferência dos 2 times do jogo (NFL: 1 grupo de 4; NBA:
 * 2 grupos de 5; NHL: 2 grupos de 8; MLB: 2 grupos de 5; MLS: SEM
 * `standings`), e sem as estatísticas que a tabela usa (`points` e
 * `gamesPlayed` faltam em NBA/MLB; `goalsFor`/`goalsAgainst` em todas).
 * Uma "tabela" de 4 times com 0 pontos seria dado enganoso. Por isso
 * [SportsLeague.hasTable] = false nessas ligas e
 * [EspnSportsRepository.leagueTable] volta null p/ elas — a tela mostra
 * "sem dados", sem inventar tabela. Lote 18 (v1.28.0): as 72 ligas novas
 * também têm hasTable = false — copas e continentais são mata-mata /
 * multi-grupo, e as 2ªs divisões ficam fora da tabela por decisão do
 * lote (o summary delas não foi validado como tabela).
 *
 * RELÓGIO POR ESPORTE (Lote 16): o relógio é mostrado como a ESPN traz,
 * sem converter. Futebol: `displayClock` ("45'"). Americanos: o
 * `type.detail` ("Q3 8:12", "Top 5th") — o `displayClock` traz só o tempo
 * ("8:12") ou "0:00" (MLB não tem relógio). A barra de progresso
 * (minuto/90) só existe p/ o futebol; fora dele, sem barra.
 * STATUS: jogos americanos terminados vêm como `STATUS_FINAL` (não
 * `STATUS_FULL_TIME`) — mapeado p/ FULL_TIME; `STATUS_END_PERIOD`
 * (fim de quarto/período) é jogo ao vivo.
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
 * [espnSport] = segmento do esporte na ESPN (`soccer`, `football`,
 * `basketball`, `hockey`, `baseball`); [sportLabel] = nome do esporte em
 * PT-BR p/ o filtro da aba; [hasTable] = false quando o summary da ESPN
 * não traz tabela de verdade (ligas americanas, Lote 16).
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
    val espnSport: String = "soccer",
    val sportLabel: String = "Futebol",
    val hasTable: Boolean = true,
    /**
     * Tabela em vários grupos (Libertadores: 8 grupos de 4). O resumo da
     * ESPN traz só o grupo do jogo, então a tabela é montada com um
     * resumo por grupo ([EspnSportsRepository.leagueTableMultiGroup]).
     */
    val multiGroup: Boolean = false,
)

/** Ids das 13 ligas que existiam antes da v1.28.0 (Lote 18). Fallback do
 * "conhecido" em [com.aotv.app.data.local.PlatformPrefs.sportsEnabledLeagueIds]:
 * instalação anterior à v1.28.0 que salvou uma lista de ligas ligadas não
 * tem snapshot guardado — o conhecido é este conjunto, então só as ligas
 * novas entram ligadas; liga antiga desligada pelo usuário continua
 * desligada. */
val SPORTS_LEAGUE_IDS_BEFORE_V128 = listOf(
    "bra1", "eng1", "esp1", "ita1", "ger1", "fra1", "por1", "ned1",
    "nfl", "nba", "nhl", "mlb", "mls",
)

/** Ligas da aba Esportes (85: 8 de futebol com tabela + 77 sem tabela;
 * slugs ESPN confirmados ao vivo — Lote 18, v1.28.0). */
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
    // ── Outros esportes (Lote 16, v1.26.0): scoreboard SEM chave, verificado
    // ao vivo em 2026-11-16 (NFL 14, NBA 8, NHL 2, MLB 1, MLS 2 eventos).
    // Sem tabela: o summary só traz o fragmento da divisão (ver o doc no
    // topo do arquivo) — hasTable = false; sem rebaixamento nem vagas
    // continentais (topSpots/relegationSpots = 0, sem uso). Sem college
    // sports (fora de escopo do lote); WNBA entrou no Lote 18.
    SportsLeague(
        "nfl", "NFL", "nfl", 0, "", 0,
        networkKeywords = listOf("espn", "disney", "sportv", "redetv", "nfl network"),
        espnSport = "football", sportLabel = "Futebol Americano", hasTable = false,
    ),
    SportsLeague(
        "nba", "NBA", "nba", 0, "", 0,
        networkKeywords = listOf("espn", "disney", "sportv", "prime video", "amazon"),
        espnSport = "basketball", sportLabel = "Basquete", hasTable = false,
    ),
    SportsLeague(
        "nhl", "NHL", "nhl", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        espnSport = "hockey", sportLabel = "Hóquei", hasTable = false,
    ),
    SportsLeague(
        "mlb", "MLB", "mlb", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        espnSport = "baseball", sportLabel = "Beisebol", hasTable = false,
    ),
    SportsLeague(
        "mls", "MLS", "usa.1", 0, "", 0,
        networkKeywords = listOf("espn", "disney", "apple tv"),
        espnSport = "soccer", sportLabel = "Futebol", hasTable = false,
    ),
    // ── Lote 18 (v1.28.0): 1ªs divisões — scoreboard confirmado ao vivo
    // em 2026-11-16/17 (HTTP 200 + eventos). Sem tabela (hasTable = false):
    // a tabela só existe p/ as 8 de futebol acima, por decisão do lote.
    SportsLeague(
        "mex1", "Liga MX", "mex.1", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "arg1", "Campeonato Argentino", "arg.1", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "sco1", "Campeonato Escocês", "sco.1", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "aus1", "A-League (Austrália)", "aus.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "ksa1", "Liga Saudita", "ksa.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "tur1", "Campeonato Turco", "tur.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "rus1", "Liga Russa", "rus.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "bel1", "Liga Belga", "bel.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "aut1", "Campeonato Austríaco", "aut.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "gre1", "Campeonato Grego", "gre.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "chn1", "Campeonato Chinês", "chn.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "swe1", "Allsvenskan (Suécia)", "swe.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "den1", "Superliga (Dinamarca)", "den.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "nor1", "Eliteserien (Noruega)", "nor.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "chi1", "Campeonato Chileno", "chi.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "uru1", "Campeonato Uruguaio", "uru.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "col1", "Campeonato Colombiano", "col.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "per1", "Liga 1 (Peru)", "per.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "par1", "Campeonato Paraguaio", "par.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "ecu1", "LigaPro (Equador)", "ecu.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "ven1", "Liga FUTVE (Venezuela)", "ven.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "bol1", "División Profesional (Bolívia)", "bol.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "jpn1", "J1 League (Japão)", "jpn.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "hon1", "Liga Nacional (Honduras)", "hon.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "crc1", "Primeira División (Costa Rica)", "crc.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "gua1", "Liga Nacional (Guatemala)", "gua.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "slv1", "Primeira División (El Salvador)", "slv.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "ind1", "Indian Super League", "ind.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "rsa1", "Premier Soccer League (África do Sul)", "rsa.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    // ── Lote 18 (v1.28.0): 2ªs divisões (a ESPN não serve por.2 nem
    // outras 2ªs — só estas 12 devolveram eventos ao vivo; uru.2 ficou
    // fora: 0 eventos em 17 datas testadas). Sem tabela, como as 1ªs.
    SportsLeague(
        "eng2", "Championship (Inglaterra)", "eng.2", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "esp2", "La Liga 2", "esp.2", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "ger2", "2. Bundesliga", "ger.2", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "ita2", "Serie B (Itália)", "ita.2", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "fra2", "Ligue 2", "fra.2", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "ned2", "Eerste Divisie (Holanda)", "ned.2", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "bra2", "Brasileirão Série B", "bra.2", 0, "", 0,
        networkKeywords = listOf("premiere", "sportv", "globo", "record", "cazetv", "band"),
        hasTable = false,
    ),
    SportsLeague(
        "mex2", "Liga de Expansión (México)", "mex.2", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "arg2", "Primera Nacional (Argentina)", "arg.2", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "sco2", "Championship (Escócia)", "sco.2", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "ind2", "I-League (Índia)", "ind.2", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "usl1", "USL Championship", "usa.usl.1", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    // ── Lote 18 (v1.28.0): copas nacionais (mata-mata: hasTable = false).
    // col.copa ficou fora: 0 eventos em 17 datas testadas.
    SportsLeague(
        "facup", "FA Cup", "eng.fa", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "eflcup", "Copa da Liga Inglesa", "eng.league_cup", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "copadelrey", "Copa del Rey", "esp.copa_del_rey", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "dfbpokal", "DFB-Pokal", "ger.dfb_pokal", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "coppaitalia", "Coppa Italia", "ita.coppa_italia", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "coupedefrance", "Copa da França", "fra.coupe_de_france", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "knvbcup", "Copa da Holanda", "ned.cup", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "tacaportugal", "Taça de Portugal", "por.taca.portugal", 0, "", 0,
        networkKeywords = listOf("espn", "sport tv", "sporttv"),
        hasTable = false,
    ),
    SportsLeague(
        "copadobrasil", "Copa do Brasil", "bra.copa_do_brazil", 0, "", 0,
        networkKeywords = listOf("premiere", "sportv", "globo", "amazon", "prime video"),
        hasTable = false,
    ),
    SportsLeague(
        "copaargentina", "Copa Argentina", "arg.copa", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "usopencup", "US Open Cup", "usa.open", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "copachile", "Copa do Chile", "chi.copa_chi", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "scotcup", "Copa da Escócia", "sco.tennents", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "kingscup", "Copa do Rei Saudita", "ksa.kings.cup", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "copabolivia", "Copa da Bolívia", "bol.copa", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    // ── Lote 18 (v1.28.0): continentais (fase de grupos/mata-mata: sem
    // tabela, como decidido no Lote 13). fifa.cwc ficou fora:
    // o Mundial de Clubes não tem edição em 2026 (próxima: 2029) — 0
    // eventos em todas as datas testadas.
    // Lote 20 (v1.30.0): Champions/Europa/Conference VIRARAM tabela única
    // de 36 (formato novo da UEFA, verificado ao vivo: o resumo traz 1
    // grupo com 36 entradas); a Libertadores virou multi-grupo (8 grupos
    // de 4 — o resumo traz só o grupo do jogo, ver leagueTableMultiGroup).
    SportsLeague(
        "ucl", "Champions League", "uefa.champions", 0, "", 0,
        networkKeywords = listOf("tnt", "max", "space", "espn"),
        hasTable = true,
    ),
    SportsLeague(
        "uel", "Europa League", "uefa.europa", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = true,
    ),
    SportsLeague(
        "uecl", "Conference League", "uefa.europa.conf", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = true,
    ),
    SportsLeague(
        "libertadores", "Libertadores", "conmebol.libertadores", 0, "", 0,
        networkKeywords = listOf("espn", "disney", "paramount", "globo"),
        hasTable = true,
        multiGroup = true,
    ),
    SportsLeague(
        "sudamericana", "Sul-Americana", "conmebol.sudamericana", 0, "", 0,
        networkKeywords = listOf("espn", "disney", "paramount"),
        hasTable = false,
    ),
    SportsLeague(
        "concachampions", "Champions Cup (Concacaf)", "concacaf.champions", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "afcchampions", "Champions League (Ásia)", "afc.champions", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "cafchampions", "Champions League (África)", "caf.champions", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "leaguescup", "Leagues Cup", "concacaf.leagues.cup", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        hasTable = false,
    ),
    SportsLeague(
        "cafconfed", "Copa das Confederações (África)", "caf.confed", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "afccup", "AFC Cup", "afc.cup", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    SportsLeague(
        "concacentral", "Copa Centro-Americana", "concacaf.central.american.cup", 0, "", 0,
        networkKeywords = emptyList(),
        hasTable = false,
    ),
    // ── Lote 18 (v1.28.0): outros esportes. WNBA (mesmo mecanismo do
    // basquete), NBL, G League e UFL — todos com eventos ao vivo. CFL e
    // XFL ficaram fora: 0 eventos em todas as datas testadas (XFL nem
    // existe mais — fundida na UFL em 2024).
    SportsLeague(
        "wnba", "WNBA", "wnba", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        espnSport = "basketball", sportLabel = "Basquete", hasTable = false,
    ),
    SportsLeague(
        "nbl", "NBL (Austrália)", "nbl", 0, "", 0,
        networkKeywords = emptyList(),
        espnSport = "basketball", sportLabel = "Basquete", hasTable = false,
    ),
    SportsLeague(
        "gleague", "NBA G League", "nba-development", 0, "", 0,
        networkKeywords = listOf("espn", "disney"),
        espnSport = "basketball", sportLabel = "Basquete", hasTable = false,
    ),
    SportsLeague(
        "ufl", "UFL", "ufl", 0, "", 0,
        networkKeywords = emptyList(),
        espnSport = "football", sportLabel = "Futebol Americano", hasTable = false,
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

/** Um grupo da tabela (Libertadores: Grupo A–H; fase única: grupo sem nome). */
data class TableGroup(
    val name: String,
    val rows: List<TableRow>,
)

/** Tabela de uma liga, com a hora da atualização (p/ mostrar "atualizada às HH:mm").
 * [groups] é a fonte da verdade quando a liga tem mais de um grupo
 * (Lote 20, v1.30.0); [rows] é a soma dos grupos, p/ quem só precisa
 * da lista plana. */
data class LeagueTable(
    val league: SportsLeague,
    val rows: List<TableRow>,
    val updatedAtMs: Long,
    val groups: List<TableGroup> = listOf(TableGroup("", rows)),
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

/** Placar de um período ("1º tempo", "2º tempo", "Prorrogação"). */
data class PeriodScore(
    val label: String,
    val home: Int,
    val away: Int,
) {
    val text: String get() = "$home x $away"
}

/** Um destaque do jogo (artilheiro etc.): 1º da 1ª categoria de `leaders` por time. */
data class TopPerformer(
    val team: String,
    val player: String,
    /** Categoria da fonte ("Goals", "Assists"...). */
    val label: String,
    /** Valor da fonte, em PT quando dá p/ traduzir ("10 gols · 30 jogos"). */
    val value: String,
)

/** Um jogo recente de um time (do `lastFiveGames` do summary). */
data class RecentGame(
    val dateMs: Long,
    val opponent: String,
    val opponentLogo: String,
    val atHome: Boolean,
    val score: String,
    val result: FormResult,
)

/** Últimos jogos de um time (do `lastFiveGames` do summary). */
data class TeamRecentGames(
    val team: String,
    val games: List<RecentGame>,
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
    /** Placar por período (Lote 20, v1.30.0): só os períodos alcançados. */
    val periodScores: List<PeriodScore> = emptyList(),
    /** Destaques do jogo (Lote 2, v1.47.0): 1º de cada categoria por time. */
    val topPerformers: List<TopPerformer> = emptyList(),
    /** Últimos jogos dos dois times (Lote 2, v1.47.0): do `lastFiveGames`. */
    val lastFiveGames: List<TeamRecentGames> = emptyList(),
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
    private const val API_ROOT = "https://site.api.espn.com/apis/site/v2/sports"

    fun scoreboardUrl(sport: String, slug: String, dateYmd: String): String =
        "$API_ROOT/$sport/$slug/scoreboard?dates=$dateYmd"

    fun summaryUrl(sport: String, slug: String, eventId: String): String =
        "$API_ROOT/$sport/$slug/summary?event=$eventId"

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
        "STATUS_IN_PROGRESS", "STATUS_END_PERIOD" -> GameStatus.LIVE
        "STATUS_HALFTIME" -> GameStatus.HALFTIME
        // Americanos: jogo terminado é STATUS_FINAL (verificado ao vivo:
        // 13 jogos da NFL e 1 da MLB com esse nome em 2026-11-16).
        "STATUS_FULL_TIME", "STATUS_FINAL" -> GameStatus.FULL_TIME
        "STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_SUSPENDED", "STATUS_DELAYED" -> GameStatus.POSTPONED
        else -> GameStatus.OTHER
    }

    /**
     * Relógio a mostrar, como a ESPN traz — sem converter nem inventar.
     * Futebol: `displayClock` ("45'"). Demais esportes: o `type.detail`
     * ("Q3 8:12", "Top 5th") — o `displayClock` traz só o tempo restante
     * ("8:12") ou "0:00" (MLB não tem relógio). Jogo agendado/final não
     * usa o relógio (a tela mostra data/hora ou "Fim de jogo").
     */
    private fun clockOf(statusObj: JSONObject?, type: JSONObject?, sport: String): String {
        val detail = type?.optString("detail").orEmpty()
        val display = statusObj?.optString("displayClock").orEmpty()
        return if (sport == "soccer") {
            display.takeIf { it.isNotBlank() } ?: detail
        } else {
            detail.takeIf { it.isNotBlank() } ?: display
        }
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
    fun parseScoreboard(json: String, leagueId: String, sport: String = "soccer"): List<LeagueGame> {
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
            val clock = clockOf(statusObj, type, sport)
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
     * Grupos da tabela a partir do `standings` do summary. Lista vazia
     * quando o summary não traz tabela (jogo de mata-mata, liga sem
     * tabela). O resumo da ESPN traz SÓ o grupo do jogo (Libertadores:
     * 4 times) e sem nome — [groupName] é o nome do grupo vindo do
     * placar (`competitions[0].group.name`, ex. "Group H"); quando o
     * resumo traz mais de um grupo sem nome, vira "Grupo 2", "Grupo 3"...
     * A zona vem da posição + o formato da liga — nunca de dado inventado.
     */
    fun parseTableGroups(
        json: String,
        league: SportsLeague,
        form: Map<String, List<FormResult>>,
        updatedAtMs: Long,
        groupName: String = "",
    ): List<TableGroup> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val groups = root.optJSONObject("standings")?.optJSONArray("groups") ?: return emptyList()
        val out = ArrayList<TableGroup>()
        for (gi in 0 until groups.length()) {
            val group = groups.optJSONObject(gi) ?: continue
            val entries = group.optJSONObject("standings")?.optJSONArray("entries") ?: continue
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
            if (raw.isEmpty()) continue
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
            val name = when {
                groups.length() == 1 -> groupName
                groupName.isNotBlank() -> "$groupName (${gi + 1})"
                else -> "Grupo ${gi + 1}"
            }
            out += TableGroup(name = name, rows = zoned)
        }
        return out
    }

    /**
     * Tabela a partir do `standings` do summary. null quando o summary não
     * traz tabela (jogo de copa, liga sem tabela). A zona vem da posição +
     * o formato da liga — nunca de dado inventado.
     */
    fun parseTable(json: String, league: SportsLeague, form: Map<String, List<FormResult>>, updatedAtMs: Long): LeagueTable? {
        val groups = parseTableGroups(json, league, form, updatedAtMs)
        if (groups.isEmpty()) return null
        return LeagueTable(league = league, rows = groups.flatMap { it.rows }, groups = groups, updatedAtMs = updatedAtMs)
    }

    /**
     * Grupo → id do 1º evento do grupo, a partir do placar (o jogo carrega
     * `competitions[0].group.{name}`). Jogo de mata-mata não tem grupo e é
     * ignorado. Usado p/ montar a tabela multi-grupo (Libertadores): um
     * resumo por grupo, cada um com a tabela daquele grupo.
     */
    fun parseScoreboardGroups(json: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val events = root.optJSONArray("events") ?: return out
        for (ei in 0 until events.length()) {
            val e = events.optJSONObject(ei) ?: continue
            val eventId = e.optString("id")
            if (eventId.isBlank()) continue
            val name = e.optJSONArray("competitions")?.optJSONObject(0)
                ?.optJSONObject("group")?.optString("name").orEmpty()
            if (name.isBlank()) continue
            out.putIfAbsent(name, eventId)
        }
        return out
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
     * Placar por período a partir dos gols do resumo (Lote 20, v1.30.0).
     * 1º tempo = gols até 45' (incl. acréscimos); 2º = 46'–90';
     * prorrogação = acima de 90'. Só entram os períodos que o jogo
     * alcançou (pelo relógio/status) — jogo agendado volta vazio, nunca
     * 0 x 0 inventado. Um 0 x 0 num período alcançado é dado real (nenhum
     * gol naquele período), não invenção.
     */
    fun periodScoresOf(goals: List<GameGoal>, homeTeam: String, awayTeam: String, game: LeagueGame): List<PeriodScore> {
        if (game.status == GameStatus.SCHEDULED || game.status == GameStatus.POSTPONED) return emptyList()
        val clockMin = game.clock.trimStart().takeWhile { it.isDigit() }.toIntOrNull()
        fun minuteOf(minute: String): Int? = minute.trimStart().takeWhile { it.isDigit() }.toIntOrNull()
        fun count(period: IntRange, team: String): Int = goals.count { g ->
            val m = minuteOf(g.minute) ?: return@count false
            m in period && g.team == team
        }
        val out = ArrayList<PeriodScore>()
        out += PeriodScore("1º tempo", count(0..45, homeTeam), count(0..45, awayTeam))
        val secondReached = game.status == GameStatus.FULL_TIME ||
            (clockMin != null && clockMin >= 46) ||
            goals.any { (minuteOf(it.minute) ?: 0) > 45 }
        if (secondReached) out += PeriodScore("2º tempo", count(46..90, homeTeam), count(46..90, awayTeam))
        val etReached = (clockMin != null && clockMin > 90) ||
            goals.any { (minuteOf(it.minute) ?: 0) > 90 }
        if (etReached) out += PeriodScore("Prorrogação", count(91..200, homeTeam), count(91..200, awayTeam))
        return out
    }

    /**
     * Página do jogo a partir do summary. null quando o summary não traz o
     * jogo (id inválido, resposta vazia). Gols, estatísticas e escalações
     * só existem quando a fonte traz — jogo futuro volta com as seções
     * vazias, nunca com dado chutado.
     */
    fun parseGameDetail(json: String, leagueId: String, sport: String = "soccer"): GameDetail? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val header = root.optJSONObject("header") ?: return null
        val comp = header.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val eventId = comp.optString("id")
        if (eventId.isBlank()) return null
        val statusObj = comp.optJSONObject("status")
        val type = statusObj?.optJSONObject("type")
        val status = statusOf(type?.optString("name").orEmpty())
        val clock = clockOf(statusObj, type, sport)
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
        // Lote 2 (v1.47.0, item 8): destaques e últimos jogos, do mesmo summary.
        val topPerformers = parseTopPerformers(root)
        val lastFiveGames = parseLastFiveGames(root)
        return GameDetail(game, goals, stats, homeLineup, awayLineup, broadcasts, periodScoresOf(goals, game.homeTeam, game.awayTeam, game), topPerformers, lastFiveGames)
    }

    /**
     * Destaques do jogo (Lote 2, item 8): o 1º atleta da 1ª categoria de
     * `leaders` de cada time (futebol: o artilheiro). "Matches: 30, Goals:
     * 10" vira "10 gols · 30 jogos"; outra categoria mostra o valor cru da
     * fonte. Sem `leaders`, lista vazia — a seção some.
     */
    private fun parseTopPerformers(root: JSONObject): List<TopPerformer> {
        val out = ArrayList<TopPerformer>()
        val teams = root.optJSONArray("leaders") ?: return out
        for (i in 0 until teams.length()) {
            val t = teams.optJSONObject(i) ?: continue
            val teamName = t.optJSONObject("team")?.optString("displayName").orEmpty()
            val cats = t.optJSONArray("leaders") ?: continue
            val cat = cats.optJSONObject(0) ?: continue
            val first = cat.optJSONArray("leaders")?.optJSONObject(0) ?: continue
            val player = first.optJSONObject("athlete")?.optString("displayName").orEmpty()
            val raw = first.optString("displayValue").orEmpty()
            if (teamName.isBlank() || player.isBlank()) continue
            val label = cat.optString("displayName").ifBlank { cat.optString("name") }
            val value = if (cat.optString("name") == "goalsLeaders") {
                val goals = Regex("Goals: (\\d+)").find(raw)?.groupValues?.getOrNull(1)
                val matches = Regex("Matches: (\\d+)").find(raw)?.groupValues?.getOrNull(1)
                if (goals != null && matches != null) "$goals ${if (goals == "1") "gol" else "gols"} · $matches jogos"
                else raw
            } else raw
            out += TopPerformer(teamName, player, label, value)
        }
        return out
    }

    /**
     * Últimos jogos dos times (Lote 2, item 8): o `lastFiveGames` do
     * summary — data, adversário, placar e o resultado do ponto de vista
     * do time (`gameResult`: W/D/L). Sem o campo, lista vazia.
     */
    private fun parseLastFiveGames(root: JSONObject): List<TeamRecentGames> {
        val out = ArrayList<TeamRecentGames>()
        val teams = root.optJSONArray("lastFiveGames") ?: return out
        for (i in 0 until teams.length()) {
            val t = teams.optJSONObject(i) ?: continue
            val teamName = t.optJSONObject("team")?.optString("displayName").orEmpty()
            if (teamName.isBlank()) continue
            val games = ArrayList<RecentGame>()
            val events = t.optJSONArray("events") ?: org.json.JSONArray()
            for (j in 0 until events.length()) {
                val e = events.optJSONObject(j) ?: continue
                val result = when (e.optString("gameResult")) {
                    "W" -> FormResult.W
                    "L" -> FormResult.L
                    else -> FormResult.D
                }
                val opp = e.optJSONObject("opponent")
                games += RecentGame(
                    dateMs = parseDateMs(e.optString("gameDate")),
                    opponent = opp?.optString("displayName").orEmpty(),
                    opponentLogo = e.optString("opponentLogo").orEmpty(),
                    atHome = e.optString("atVs") == "vs",
                    score = e.optString("score").orEmpty(),
                    result = result,
                )
            }
            if (games.isNotEmpty()) out += TeamRecentGames(teamName, games)
        }
        return out
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

    private suspend fun fetchScoreboard(league: SportsLeague, dateYmd: String, today: Boolean): String? =
        withContext(Dispatchers.IO) {
            val name = "scoreboard_${league.espnSport}_${league.espnSlug}_$dateYmd.json"
            val ttl = if (today) todayTtlMs else tableTtlMs
            readCache(name, ttl)?.first
                ?: get(EspnSportsApi.scoreboardUrl(league.espnSport, league.espnSlug, dateYmd))?.also { writeCache(name, it) }
                ?: readCacheStale(name)?.first
        }

    private suspend fun fetchSummary(league: SportsLeague, eventId: String): String? =
        withContext(Dispatchers.IO) {
            val name = "detail_$eventId.json"
            readCache(name, detailTtlMs)?.first
                ?: get(EspnSportsApi.summaryUrl(league.espnSport, league.espnSlug, eventId))?.also { writeCache(name, it) }
                ?: readCacheStale(name)?.first
        }

    /**
     * Jogos de um dia (placar do dia). Sem rede e sem cache, lista vazia —
     * nunca jogo inventado.
     */
    suspend fun gamesOn(league: SportsLeague, dateMs: Long): List<LeagueGame> {
        val ymd = EspnSportsApi.ymd(dateMs)
        val today = ymd == EspnSportsApi.ymd(System.currentTimeMillis())
        val json = fetchScoreboard(league, ymd, today) ?: return emptyList()
        return EspnSportsApi.parseScoreboard(json, league.id, league.espnSport)
    }

    /**
     * Tabela da liga. Pega um id de evento do placar (hoje, senão os 7 dias
     * anteriores) e lê a tabela do summary dele. Os últimos 5 vêm dos
     * placares recentes (campo `form` da fonte) até cobrir os times da
     * tabela (no máximo 7 dias). Fora de temporada, null.
     * Ligas sem tabela de verdade no summary ([SportsLeague.hasTable] =
     * false — as 5 americanas do Lote 16 + as 72 do Lote 18) voltam null
     * direto: a tela mostra "sem dados", sem inventar tabela de fragmento
     * de divisão.
     */
    suspend fun leagueTable(league: SportsLeague): LeagueTable? {
        if (!league.hasTable) return null
        if (league.multiGroup) return leagueTableMultiGroup(league)
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
            val summary = fetchSummary(league, first.eventId) ?: continue
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
            val raw = fetchScoreboard(league, ymd, today) ?: continue
            EspnSportsApi.parseFormMap(raw).forEach { (team, results) -> form.putIfAbsent(team, results) }
        }
        val table = EspnSportsApi.parseTable(summaryJson, league, form, now) ?: return null
        writeCache("table_${league.espnSlug}.json", summaryJson)
        return table
    }

    /**
     * Tabela multi-grupo (Libertadores, Lote 20, v1.30.0). O resumo da ESPN
     * traz SÓ o grupo do jogo, então a tabela é montada com um resumo por
     * grupo: varre os placares de trás p/ frente (máx. 90 dias, para no
     * 8º grupo — a fase de grupos tem 8) e guarda o 1º evento de cada
     * grupo; cada resumo vira um [TableGroup] com o nome vindo do placar.
     * Cache combinado de 6 h (`table_multi_<slug>.json`: grupo → evento);
     * os resumos em si já ficam no cache de detalhe. Fora da fase de
     * grupos (mata-mata), a varredura não acha grupo nenhum e volta null
     * — a tela mostra "sem dados", sem inventar tabela. O cache negativo
     * também é guardado (sem varrer de novo por 6 h). Últimos 5: o `form`
     * dos placares varridos (o mais recente ganha).
     */
    private suspend fun leagueTableMultiGroup(league: SportsLeague): LeagueTable? {
        val cacheName = "table_multi_${league.espnSlug}.json"
        val now = System.currentTimeMillis()
        val cached = readCache(cacheName, tableTtlMs)
        if (cached != null) {
            // Cache válido: mapa vazio = negativo (fora da fase de grupos) —
            // volta null sem varrer de novo; mapa ilegível = null, varre.
            val foundCached = parseMultiCacheMap(cached.first)
            if (foundCached != null) {
                if (foundCached.isEmpty()) return null
                return buildMultiTable(league, foundCached, emptyMap(), cached.second)
            }
        }
        val found = LinkedHashMap<String, String>()
        val form = LinkedHashMap<String, List<FormResult>>()
        for (back in 0..90) {
            if (found.size >= 8) break
            val dayMs = now - back * 24L * 60 * 60 * 1000
            val raw = fetchScoreboard(league, EspnSportsApi.ymd(dayMs), back == 0) ?: continue
            EspnSportsApi.parseScoreboardGroups(raw).forEach { (name, eventId) -> found.putIfAbsent(name, eventId) }
            EspnSportsApi.parseFormMap(raw).forEach { (team, results) -> form.putIfAbsent(team, results) }
        }
        writeCache(cacheName, multiCacheJson(found, now))
        return buildMultiTable(league, found, form, now)
    }

    /** Monta a tabela a partir do mapa grupo → evento (cache ou varredura). */
    private suspend fun buildMultiTable(
        league: SportsLeague,
        found: Map<String, String>,
        form: Map<String, List<FormResult>>,
        nowMs: Long,
    ): LeagueTable? {
        if (found.isEmpty()) return null
        val groups = ArrayList<TableGroup>()
        found.entries.sortedBy { it.key }.forEach { (name, eventId) ->
            val json = fetchSummary(league, eventId) ?: return@forEach
            val groupForm = LinkedHashMap<String, List<FormResult>>()
            groupForm.putAll(form)
            groupForm.putAll(EspnSportsApi.parseLastFive(json))
            EspnSportsApi.parseTableGroups(json, league, groupForm, nowMs, name).forEach { groups += it }
        }
        if (groups.isEmpty()) return null
        return LeagueTable(league = league, rows = groups.flatMap { it.rows }, groups = groups, updatedAtMs = nowMs)
    }

    /** Lê o cache combinado (grupo → evento). null = JSON ilegível; mapa vazio = negativo. */
    private fun parseMultiCacheMap(json: String): Map<String, String>? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val found = LinkedHashMap<String, String>()
        val arr = root.optJSONArray("groups") ?: return null
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val name = g.optString("name")
            val eventId = g.optString("eventId")
            if (name.isNotBlank() && eventId.isNotBlank()) found[name] = eventId
        }
        return found
    }

    private fun multiCacheJson(found: Map<String, String>, atMs: Long): String {
        val groups = found.entries.joinToString(",") { (name, eventId) ->
            "{\"name\":${JSONObject.quote(name)},\"eventId\":${JSONObject.quote(eventId)}}"
        }
        return "{\"groups\":[$groups],\"updatedAtMs\":$atMs}"
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
        val json = fetchSummary(league, eventId) ?: return null
        return EspnSportsApi.parseGameDetail(json, league.id, league.espnSport)
    }
}
