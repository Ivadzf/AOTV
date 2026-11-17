package com.aotv.app.data.sports

/**
 * Dicionário local de esportes (Lote 20, v1.30.0) — embarcado no app, sem
 * rede: classifica o programa do guia (título/descrição) em esporte,
 * competição e times. Antes, a classificação era só por palavras soltas
 * ([SPORT_WORDS] em SportsScores.kt: "campeonato", "copa", "jogo"...) —
 * um "Campeonato de Xadrez" virava esporte e "Flamengo x Palmeiras" não
 * dizia nem o esporte nem a competição.
 *
 * CONTEÚDO (listas de melhor esforço, conferidas nas temporadas 2025/2026
 * quando escritas — elenco de uma temporada pode mudar sem quebrar nada:
 * um time fora da lista só deixa de ser reconhecido, nunca inventa dado):
 * 20 esportes, competições com apelidos em PT-BR (ex.: "Brasileirão",
 * "Champions") e os times das principais competições. Canais de esporte
 * ([SPORTS_CHANNELS]) p/ reconhecer o canal pelo nome.
 *
 * Uso: [classifySportsProgram] (título/descrição → classificação) e o
 * casamento de time seguido ([teamMatches]).
 */
data class SportsCompetition(
    val id: String,
    val sport: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val teams: List<String> = emptyList(),
)

/** Esportes do dicionário: id → nome em PT-BR. */
val SPORTS_DICTIONARY_SPORTS = listOf(
    "futebol" to "Futebol",
    "futsal" to "Futsal",
    "basquete" to "Basquete",
    "volei" to "Vôlei",
    "volei-praia" to "Vôlei de Praia",
    "tenis" to "Tênis",
    "futebol-americano" to "Futebol Americano",
    "beisebol" to "Beisebol",
    "hoquei" to "Hóquei no Gelo",
    "mma" to "MMA",
    "boxe" to "Boxe",
    "formula-1" to "Fórmula 1",
    "stock-car" to "Stock Car",
    "nascar" to "Nascar",
    "golfe" to "Golfe",
    "ciclismo" to "Ciclismo",
    "atletismo" to "Atletismo",
    "natacao" to "Natação",
    "handebol" to "Handebol",
    "rugby" to "Rugby",
)

/** Competições do dicionário (id, esporte, nome, apelidos, times). */
val SPORTS_DICTIONARY_COMPETITIONS: List<SportsCompetition> = listOf(
    // ── Futebol — Brasil ──────────────────────────────────────────────
    SportsCompetition(
        "bra-serie-a", "futebol", "Brasileirão Série A",
        aliases = listOf("brasileirao", "brasileirao serie a", "campeonato brasileiro", "campeonato brasileiro serie a", "bra 1", "serie a"),
        teams = listOf(
            "Atlético-MG", "Bahia", "Botafogo", "Ceará", "Corinthians", "Cruzeiro", "Flamengo",
            "Fluminense", "Fortaleza", "Grêmio", "Internacional", "Juventude", "Mirassol", "Palmeiras",
            "Red Bull Bragantino", "Santos", "São Paulo", "Sport", "Vasco", "Vitória",
        ),
    ),
    SportsCompetition(
        "bra-serie-b", "futebol", "Brasileirão Série B",
        aliases = listOf("brasileirao serie b", "campeonato brasileiro serie b", "bra 2", "serie b"),
        teams = listOf(
            "América-MG", "Athletic", "Athletico-PR", "Atlético-GO", "Avaí", "Botafogo-SP",
            "Chapecoense", "Coritiba", "CRB", "Criciúma", "Cuiabá", "Ferroviária", "Goiás",
            "Novorizontino", "Operário-PR", "Paysandu", "Remo", "Vila Nova", "Volta Redonda", "Amazonas",
        ),
    ),
    SportsCompetition(
        "bra-serie-c", "futebol", "Brasileirão Série C",
        aliases = listOf("brasileirao serie c", "campeonato brasileiro serie c", "bra 3", "serie c"),
        teams = listOf(
            "ABC", "Anápolis", "Brusque", "Caxias", "Confiança", "CSA", "Figueirense", "Floresta",
            "Guarani", "Itabaiana", "Ituano", "Londrina", "Maringá", "Náutico", "Ponte Preta",
            "Retrô", "São Bernardo", "Tombense", "Ypiranga-RS", "Botafogo-PB",
        ),
    ),
    SportsCompetition(
        "bra-serie-d", "futebol", "Brasileirão Série D",
        aliases = listOf("brasileirao serie d", "campeonato brasileiro serie d", "bra 4", "serie d"),
    ),
    SportsCompetition(
        "bra-copa-do-brasil", "futebol", "Copa do Brasil",
        aliases = listOf("copa do brasil"),
    ),
    SportsCompetition(
        "bra-supercopa", "futebol", "Supercopa do Brasil",
        aliases = listOf("supercopa do brasil", "supercopa rei"),
    ),
    SportsCompetition(
        "bra-feminino-a", "futebol", "Brasileirão Feminino Série A",
        aliases = listOf("brasileirao feminino", "brasileirao feminino serie a"),
        teams = listOf(
            "Corinthians", "Palmeiras", "São Paulo", "Ferroviária", "Internacional", "Grêmio",
            "Cruzeiro", "Flamengo", "Fluminense", "Santos", "Bahia", "Real Brasília", "América-MG",
            "Red Bull Bragantino", "Atlético-MG", "Juventude",
        ),
    ),
    SportsCompetition(
        "bra-paulistao", "futebol", "Paulistão",
        aliases = listOf("paulistao", "campeonato paulista", "paulista"),
        teams = listOf(
            "Corinthians", "Palmeiras", "São Paulo", "Santos", "Red Bull Bragantino", "Guarani",
            "Ponte Preta", "Novorizontino", "Mirassol", "São Bernardo", "Portuguesa", "Botafogo-SP",
            "Inter de Limeira", "Água Santa", "Noroeste", "Velo Clube",
        ),
    ),
    SportsCompetition(
        "bra-carioca", "futebol", "Carioca",
        aliases = listOf("carioca", "campeonato carioca"),
        teams = listOf(
            "Flamengo", "Fluminense", "Botafogo", "Vasco", "Madureira", "Volta Redonda",
            "Nova Iguaçu", "Boavista", "Portuguesa-RJ", "Sampaio Corrêa-RJ", "Bangu", "Maricá",
        ),
    ),
    SportsCompetition("bra-mineiro", "futebol", "Mineiro", aliases = listOf("mineiro", "campeonato mineiro")),
    SportsCompetition("bra-gaucho", "futebol", "Gaúcho", aliases = listOf("gaucho", "campeonato gaucho")),
    SportsCompetition("bra-baiano", "futebol", "Baiano", aliases = listOf("baiano", "campeonato baiano")),
    SportsCompetition("bra-pernambucano", "futebol", "Pernambucano", aliases = listOf("pernambucano", "campeonato pernambucano")),
    SportsCompetition("bra-paranaense", "futebol", "Paranaense", aliases = listOf("paranaense", "campeonato paranaense")),
    SportsCompetition("bra-catarinense", "futebol", "Catarinense", aliases = listOf("catarinense", "campeonato catarinense")),
    SportsCompetition("bra-goiano", "futebol", "Goiano", aliases = listOf("goiano", "campeonato goiano")),
    SportsCompetition("bra-cearense", "futebol", "Cearense", aliases = listOf("cearense", "campeonato cearense")),
    SportsCompetition("bra-alagoano", "futebol", "Alagoano", aliases = listOf("alagoano", "campeonato alagoano")),
    SportsCompetition("bra-paraense", "futebol", "Paraense", aliases = listOf("paraense", "campeonato paraense")),
    SportsCompetition("bra-mato-grossense", "futebol", "Mato-Grossense", aliases = listOf("mato-grossense", "campeonato mato-grossense")),
    SportsCompetition("bra-sul-mato-grossense", "futebol", "Sul-Mato-Grossense", aliases = listOf("sul-mato-grossense", "campeonato sul-mato-grossense")),
    SportsCompetition("bra-amazonense", "futebol", "Amazonense", aliases = listOf("amazonense", "campeonato amazonense")),
    SportsCompetition("bra-maranhense", "futebol", "Maranhense", aliases = listOf("maranhense", "campeonato maranhense")),
    SportsCompetition("bra-piauiense", "futebol", "Piauiense", aliases = listOf("piauiense", "campeonato piauiense")),
    SportsCompetition("bra-sergipano", "futebol", "Sergipano", aliases = listOf("sergipano", "campeonato sergipano")),
    SportsCompetition("bra-paraibano", "futebol", "Paraibano", aliases = listOf("paraibano", "campeonato paraibano")),
    SportsCompetition("bra-potiguar", "futebol", "Potiguar", aliases = listOf("potiguar", "campeonato potiguar")),
    SportsCompetition("bra-brasiliense", "futebol", "Brasiliense", aliases = listOf("brasiliense", "campeonato brasiliense")),
    SportsCompetition("bra-capixaba", "futebol", "Capixaba", aliases = listOf("capixaba", "campeonato capixaba")),
    SportsCompetition("bra-acreano", "futebol", "Acreano", aliases = listOf("acreano", "campeonato acreano")),
    SportsCompetition("bra-amapaense", "futebol", "Amapaense", aliases = listOf("amapaense", "campeonato amapaense")),
    SportsCompetition("bra-rondoniense", "futebol", "Rondoniense", aliases = listOf("rondoniense", "campeonato rondoniense")),
    SportsCompetition("bra-roraimense", "futebol", "Roraimense", aliases = listOf("roraimense", "campeonato roraimense")),
    SportsCompetition("bra-tocantinense", "futebol", "Tocantinense", aliases = listOf("tocantinense", "campeonato tocantinense")),
    // ── Futebol — América do Sul ────────────────────────────────────────
    SportsCompetition(
        "conmebol-libertadores", "futebol", "Libertadores",
        aliases = listOf("libertadores", "copa libertadores", "conmebol libertadores", "taça libertadores"),
        teams = listOf(
            "Palmeiras", "Flamengo", "Botafogo", "São Paulo", "Internacional", "Fortaleza", "Bahia",
            "River Plate", "Boca Juniors", "Racing", "Estudiantes", "Vélez Sarsfield", "Talleres",
            "Central Córdoba", "Peñarol", "Nacional", "Olimpia", "Cerro Porteño", "Libertad",
            "LDU Quito", "Independiente del Valle", "Barcelona SC", "Atlético Nacional",
            "Atlético Bucaramanga", "Deportivo Táchira", "Carabobo", "Alianza Lima",
            "Sporting Cristal", "Universitario", "Bolívar", "San Antonio Bulo Bulo",
            "Colo-Colo", "Universidad de Chile",
        ),
    ),
    SportsCompetition(
        "conmebol-sulamericana", "futebol", "Sul-Americana",
        aliases = listOf("sul-americana", "sulamericana", "copa sul-americana", "conmebol sul-americana"),
        teams = listOf(
            "Corinthians", "Cruzeiro", "Grêmio", "Vasco", "Vitória", "Atlético-MG",
            "Independiente", "Lanús", "Godoy Cruz", "Unión", "Defensa y Justicia",
            "América de Cali", "Once Caldas", "Deportivo Cali", "Millonarios",
            "Universidad Católica", "Palestino", "Unión Española", "Guaraní", "Sportivo Luqueño",
            "Cienciano", "Melgar", "Cerro Largo", "Boston River", "Mushuc Runa", "Puerto Cabello",
            "Blooming", "Nacional Potosí", "Vitória", "Fluminense",
        ),
    ),
    SportsCompetition("conmebol-recopa", "futebol", "Recopa Sul-Americana", aliases = listOf("recopa", "recopa sul-americana")),
    SportsCompetition("conmebol-libertadores-fem", "futebol", "Libertadores Feminina", aliases = listOf("libertadores feminina", "copa libertadores feminina")),
    // ── Futebol — Europa ────────────────────────────────────────────────
    SportsCompetition(
        "uefa-champions", "futebol", "Champions League",
        aliases = listOf("champions", "champions league", "liga dos campeoes", "uefa champions league", "ucl"),
        teams = listOf(
            "Real Madrid", "Barcelona", "Atlético de Madrid", "Athletic Bilbao", "Villarreal",
            "Manchester City", "Liverpool", "Arsenal", "Chelsea", "Tottenham", "Newcastle",
            "Bayern de Munique", "Borussia Dortmund", "Bayer Leverkusen", "Eintracht Frankfurt",
            "Inter de Milão", "Napoli", "Juventus", "Atalanta", "PSG", "Olympique de Marseille",
            "Monaco", "Ajax", "PSV", "Benfica", "Sporting", "Porto", "Club Brugge",
            "Union Saint-Gilloise", "Galatasaray", "Olympiacos", "Slavia Praga", "Copenhagen",
            "Bodo/Glimt", "Kairat Almaty", "Pafos", "Qarabag",
        ),
    ),
    SportsCompetition("uefa-europa", "futebol", "Europa League", aliases = listOf("europa league", "liga europa", "uel")),
    SportsCompetition("uefa-conference", "futebol", "Conference League", aliases = listOf("conference league", "liga conferencia")),
    SportsCompetition(
        "eng-premier", "futebol", "Premier League",
        aliases = listOf("premier league", "campeonato ingles", "ingles"),
        teams = listOf(
            "Arsenal", "Aston Villa", "Bournemouth", "Brentford", "Brighton", "Burnley", "Chelsea",
            "Crystal Palace", "Everton", "Fulham", "Leeds United", "Liverpool", "Manchester City",
            "Manchester United", "Newcastle", "Nottingham Forest", "Sunderland", "Tottenham",
            "West Ham", "Wolverhampton",
        ),
    ),
    SportsCompetition("eng-fa-cup", "futebol", "Copa da Inglaterra", aliases = listOf("fa cup", "copa da inglaterra")),
    SportsCompetition("eng-league-cup", "futebol", "Copa da Liga Inglesa", aliases = listOf("league cup", "carabao cup", "copa da liga inglesa")),
    SportsCompetition("eng-championship", "futebol", "Championship", aliases = listOf("championship", "segunda divisao inglesa")),
    SportsCompetition(
        "esp-laliga", "futebol", "La Liga",
        aliases = listOf("la liga", "campeonato espanhol", "espanhol"),
        teams = listOf(
            "Real Madrid", "Barcelona", "Atlético de Madrid", "Athletic Bilbao", "Villarreal",
            "Real Betis", "Real Sociedad", "Sevilla", "Valencia", "Getafe", "Celta de Vigo",
            "Osasuna", "Rayo Vallecano", "Mallorca", "Girona", "Alavés", "Espanyol", "Levante",
            "Elche", "Oviedo",
        ),
    ),
    SportsCompetition("esp-copa-del-rey", "futebol", "Copa do Rei", aliases = listOf("copa del rey", "copa do rei")),
    SportsCompetition("esp-supercopa", "futebol", "Supercopa da Espanha", aliases = listOf("supercopa da espanha", "supercopa espanhola")),
    SportsCompetition("esp-segunda", "futebol", "Segunda Divisão (Espanha)", aliases = listOf("segunda divisao espanhola", "laliga 2")),
    SportsCompetition(
        "ita-serie-a", "futebol", "Serie A (Itália)",
        aliases = listOf("serie a", "campeonato italiano", "italiano"),
        teams = listOf(
            "Inter de Milão", "Napoli", "Juventus", "Milan", "Atalanta", "Roma", "Lazio",
            "Fiorentina", "Bologna", "Torino", "Udinese", "Genoa", "Verona", "Lecce", "Parma",
            "Cagliari", "Como", "Sassuolo", "Pisa", "Cremonese",
        ),
    ),
    SportsCompetition("ita-copa-italia", "futebol", "Copa da Itália", aliases = listOf("coppa italia", "copa da italia")),
    SportsCompetition("ita-serie-b", "futebol", "Serie B (Itália)", aliases = listOf("serie b italiana", "serie b")),
    SportsCompetition(
        "ger-bundesliga", "futebol", "Bundesliga",
        aliases = listOf("bundesliga", "campeonato alemao", "alemao"),
        teams = listOf(
            "Bayern de Munique", "Borussia Dortmund", "Bayer Leverkusen", "RB Leipzig",
            "Eintracht Frankfurt", "Stuttgart", "Wolfsburg", "Borussia Mönchengladbach", "Freiburg",
            "Hoffenheim", "Augsburg", "Werder Bremen", "Union Berlin", "St. Pauli", "Mainz",
            "Köln", "Hamburger SV", "Heidenheim",
        ),
    ),
    SportsCompetition("ger-dfb-pokal", "futebol", "Copa da Alemanha", aliases = listOf("dfb pokal", "copa da alemanha")),
    SportsCompetition("ger-2-bundesliga", "futebol", "2. Bundesliga", aliases = listOf("2 bundesliga", "segunda divisao alema")),
    SportsCompetition(
        "fra-ligue-1", "futebol", "Ligue 1",
        aliases = listOf("ligue 1", "campeonato frances", "frances"),
        teams = listOf(
            "PSG", "Olympique de Marseille", "Monaco", "Lyon", "Lille", "Nice", "Lens", "Rennes",
            "Strasbourg", "Toulouse", "Brest", "Nantes", "Auxerre", "Angers", "Le Havre", "Metz",
            "Lorient", "Paris FC",
        ),
    ),
    SportsCompetition("fra-coupe", "futebol", "Copa da França", aliases = listOf("coupe de france", "copa da franca")),
    SportsCompetition("fra-ligue-2", "futebol", "Ligue 2", aliases = listOf("ligue 2", "segunda divisao francesa")),
    SportsCompetition(
        "por-primeira-liga", "futebol", "Primeira Liga",
        aliases = listOf("primeira liga", "campeonato portugues", "portugues", "liga portugal"),
        teams = listOf(
            "Benfica", "Porto", "Sporting", "Braga", "Vitória de Guimarães", "Gil Vicente",
            "Famalicão", "Moreirense", "Arouca", "Estoril", "Rio Ave", "Casa Pia", "Santa Clara",
            "Nacional", "Estrela da Amadora", "AVS", "Alverca", "Tondela",
        ),
    ),
    SportsCompetition("por-taca", "futebol", "Taça de Portugal", aliases = listOf("taca de portugal", "taça de portugal")),
    SportsCompetition("por-liga-2", "futebol", "Liga Portugal 2", aliases = listOf("liga portugal 2", "segunda liga")),
    SportsCompetition(
        "ned-eredivisie", "futebol", "Eredivisie",
        aliases = listOf("eredivisie", "campeonato holandes", "holandes"),
        teams = listOf(
            "Ajax", "PSV", "Feyenoord", "AZ Alkmaar", "Twente", "Utrecht", "Heerenveen",
            "Sparta Rotterdam", "Go Ahead Eagles", "NEC Nijmegen", "Fortuna Sittard", "PEC Zwolle",
            "Heracles", "Groningen", "NAC Breda", "Telstar", "Excelsior", "Volendam",
        ),
    ),
    SportsCompetition("sco-premiership", "futebol", "Scottish Premiership", aliases = listOf("scottish premiership", "campeonato escoces", "escoces"), teams = listOf("Celtic", "Rangers", "Hearts", "Aberdeen", "Hibernian")),
    SportsCompetition("bel-pro-league", "futebol", "Pro League (Bélgica)", aliases = listOf("pro league", "campeonato belga", "belga")),
    SportsCompetition("sui-super-league", "futebol", "Super League (Suíça)", aliases = listOf("super league suica", "campeonato suico", "suico")),
    SportsCompetition("aut-bundesliga", "futebol", "Bundesliga (Áustria)", aliases = listOf("bundesliga austriaca", "campeonato austriaco")),
    SportsCompetition("tur-super-lig", "futebol", "Süper Lig", aliases = listOf("super lig", "campeonato turco", "turco"), teams = listOf("Galatasaray", "Fenerbahçe", "Besiktas", "Trabzonspor", "Basaksehir")),
    SportsCompetition("gre-super-league", "futebol", "Super League (Grécia)", aliases = listOf("super league grecia", "campeonato grego", "grego"), teams = listOf("Olympiacos", "PAOK", "AEK Atenas", "Panathinaikos")),
    SportsCompetition("rus-premier", "futebol", "Premier League (Rússia)", aliases = listOf("campeonato russo", "russo")),
    SportsCompetition("ukr-premier", "futebol", "Premier League (Ucrânia)", aliases = listOf("campeonato ucraniano", "ucraniano")),
    SportsCompetition("pol-ekstraklasa", "futebol", "Ekstraklasa", aliases = listOf("ekstraklasa", "campeonato polones", "polones")),
    SportsCompetition("cze-first-league", "futebol", "First League (Tchéquia)", aliases = listOf("campeonato tcheco", "tcheco")),
    SportsCompetition("cro-hnl", "futebol", "HNL (Croácia)", aliases = listOf("campeonato croata", "croata"), teams = listOf("Dinamo Zagreb", "Hajduk Split", "Rijeka", "Osijek")),
    SportsCompetition("srb-superliga", "futebol", "SuperLiga (Sérvia)", aliases = listOf("campeonato servio", "servio"), teams = listOf("Estrela Vermelha", "Partizan")),
    SportsCompetition("den-superliga", "futebol", "Superliga (Dinamarca)", aliases = listOf("campeonato dinamarques", "dinamarques"), teams = listOf("Copenhagen", "Midtjylland", "Brondby")),
    SportsCompetition("nor-eliteserien", "futebol", "Eliteserien", aliases = listOf("eliteserien", "campeonato noruegues", "noruegues"), teams = listOf("Bodo/Glimt", "Molde", "Rosenborg")),
    SportsCompetition("swe-allsvenskan", "futebol", "Allsvenskan", aliases = listOf("allsvenskan", "campeonato sueco", "sueco")),
    SportsCompetition("fin-veikkausliiga", "futebol", "Veikkausliiga", aliases = listOf("veikkausliiga", "campeonato finlandes", "finlandes")),
    SportsCompetition("uefa-champions-fem", "futebol", "Champions League Feminina", aliases = listOf("champions feminina", "liga dos campeoes feminina")),
    SportsCompetition("uefa-euro-fem", "futebol", "Eurocopa Feminina", aliases = listOf("euro feminina", "eurocopa feminina")),
    // ── Futebol — seleções ──────────────────────────────────────────────
    SportsCompetition("fifa-copa-do-mundo", "futebol", "Copa do Mundo", aliases = listOf("copa do mundo", "world cup", "mundial")),
    SportsCompetition("fifa-copa-america", "futebol", "Copa América", aliases = listOf("copa america", "copa américa")),
    SportsCompetition("uefa-euro", "futebol", "Eurocopa", aliases = listOf("eurocopa", "euro", "uefa euro")),
    SportsCompetition("concacaf-gold-cup", "futebol", "Copa Ouro", aliases = listOf("copa ouro", "gold cup")),
    SportsCompetition("fifa-mundial-clubes", "futebol", "Mundial de Clubes", aliases = listOf("mundial de clubes", "club world cup")),
    SportsCompetition("conmebol-eliminatorias", "futebol", "Eliminatórias (América do Sul)", aliases = listOf("eliminatorias", "eliminatorias sul-americanas")),
    SportsCompetition("uefa-nations", "futebol", "Nations League", aliases = listOf("nations league", "liga das nacoes")),
    SportsCompetition("afc-champions", "futebol", "Champions League (Ásia)", aliases = listOf("afc champions league", "liga dos campeoes asia")),
    SportsCompetition("caf-champions", "futebol", "Champions League (África)", aliases = listOf("caf champions league", "liga dos campeoes africa")),
    SportsCompetition("concacaf-champions", "futebol", "Champions Cup (CONCACAF)", aliases = listOf("concacaf champions cup", "concachampions")),
    // ── Futebol — resto do mundo ────────────────────────────────────────
    SportsCompetition(
        "usa-mls", "futebol", "MLS",
        aliases = listOf("mls", "major league soccer"),
        teams = listOf(
            "Atlanta United", "Austin FC", "Charlotte FC", "Chicago Fire", "FC Cincinnati",
            "Colorado Rapids", "Columbus Crew", "FC Dallas", "DC United", "Houston Dynamo",
            "Sporting Kansas City", "LA Galaxy", "Los Angeles FC", "Inter Miami",
            "Minnesota United", "CF Montréal", "Nashville SC", "New England Revolution",
            "New York City FC", "New York Red Bulls", "Orlando City", "Philadelphia Union",
            "Portland Timbers", "Real Salt Lake", "San Diego FC", "San Jose Earthquakes",
            "Seattle Sounders", "St. Louis City SC", "Toronto FC", "Vancouver Whitecaps",
        ),
    ),
    SportsCompetition("mex-liga-mx", "futebol", "Liga MX", aliases = listOf("liga mx", "campeonato mexicano", "mexicano"), teams = listOf("América", "Chivas", "Cruz Azul", "Tigres", "Monterrey", "Pumas", "Toluca", "León", "Pachuca", "Santos Laguna")),
    SportsCompetition("arg-liga", "futebol", "Liga Profissional (Argentina)", aliases = listOf("liga profissional argentina", "campeonato argentino", "argentino"), teams = listOf("River Plate", "Boca Juniors", "Racing", "Independiente", "San Lorenzo", "Estudiantes", "Vélez Sarsfield", "Talleres", "Lanús", "Rosario Central", "Newell's Old Boys", "Huracán")),
    SportsCompetition("uru-primera", "futebol", "Campeonato Uruguaio", aliases = listOf("campeonato uruguaio", "uruguaio"), teams = listOf("Peñarol", "Nacional", "Defensor Sporting", "Liverpool Montevideo")),
    SportsCompetition("chi-primera", "futebol", "Campeonato Chileno", aliases = listOf("campeonato chileno", "chileno"), teams = listOf("Colo-Colo", "Universidad de Chile", "Universidad Católica", "Palestino")),
    SportsCompetition("col-primera", "futebol", "Campeonato Colombiano", aliases = listOf("campeonato colombiano", "colombiano"), teams = listOf("Atlético Nacional", "Millonarios", "América de Cali", "Deportivo Cali", "Junior", "Santa Fe")),
    SportsCompetition("sau-pro-league", "futebol", "Liga Saudita", aliases = listOf("liga saudita", "saudi pro league", "saudita"), teams = listOf("Al Hilal", "Al Nassr", "Al Ittihad", "Al Ahli", "Al Ettifaq")),
    SportsCompetition("jpn-j-league", "futebol", "J-League", aliases = listOf("j-league", "j league", "campeonato japones", "japones")),
    SportsCompetition("kor-k-league", "futebol", "K-League", aliases = listOf("k-league", "k league", "campeonato coreano", "coreano")),
    // ── Futsal ──────────────────────────────────────────────────────────
    SportsCompetition("futsal-lnf", "futsal", "Liga Nacional de Futsal", aliases = listOf("liga nacional de futsal", "lnf", "futsal")),
    SportsCompetition("futsal-libertadores", "futsal", "Libertadores de Futsal", aliases = listOf("libertadores de futsal")),
    SportsCompetition("futsal-mundial", "futsal", "Copa do Mundo de Futsal", aliases = listOf("copa do mundo de futsal", "mundial de futsal")),
    // ── Basquete ────────────────────────────────────────────────────────
    SportsCompetition(
        "basq-nba", "basquete", "NBA",
        aliases = listOf("nba"),
        teams = listOf(
            "Atlanta Hawks", "Boston Celtics", "Brooklyn Nets", "Charlotte Hornets", "Chicago Bulls",
            "Cleveland Cavaliers", "Dallas Mavericks", "Denver Nuggets", "Detroit Pistons",
            "Golden State Warriors", "Houston Rockets", "Indiana Pacers", "LA Clippers",
            "Los Angeles Lakers", "Memphis Grizzlies", "Miami Heat", "Milwaukee Bucks",
            "Minnesota Timberwolves", "New Orleans Pelicans", "New York Knicks",
            "Oklahoma City Thunder", "Orlando Magic", "Philadelphia 76ers", "Phoenix Suns",
            "Portland Trail Blazers", "Sacramento Kings", "San Antonio Spurs", "Toronto Raptors",
            "Utah Jazz", "Washington Wizards",
        ),
    ),
    SportsCompetition(
        "basq-nbb", "basquete", "NBB",
        aliases = listOf("nbb", "novo basquete brasil"),
        teams = listOf(
            "Flamengo", "Franca", "Minas", "São Paulo", "Corinthians", "Pinheiros", "Paulistano",
            "Bauru", "Mogi", "Fortaleza BC", "Brasília", "Caxias do Sul", "União Corinthians",
            "Pato Basquete", "Botafogo", "Vasco", "São José", "Unifacisa",
        ),
    ),
    SportsCompetition("basq-euroliga", "basquete", "Euroliga", aliases = listOf("euroliga", "euroleague")),
    SportsCompetition("basq-mundial", "basquete", "Copa do Mundo de Basquete", aliases = listOf("copa do mundo de basquete", "mundial de basquete")),
    SportsCompetition("basq-wnba", "basquete", "WNBA", aliases = listOf("wnba")),
    SportsCompetition("basq-acb", "basquete", "Liga ACB", aliases = listOf("liga acb", "acb")),
    SportsCompetition("basq-bcl-americas", "basquete", "BCL Américas", aliases = listOf("bcl americas", "basketball champions league americas")),
    // ── Vôlei ───────────────────────────────────────────────────────────
    SportsCompetition(
        "volei-superliga-m", "volei", "Superliga Masculina",
        aliases = listOf("superliga masculina", "superliga m", "superliga"),
        teams = listOf(
            "Sada Cruzeiro", "Sesi Bauru", "Minas", "Campinas", "Praia Clube", "Suzano",
            "Joinville", "Guarulhos", "São José", "Blumenau", "Montes Claros", "Viçosa",
        ),
    ),
    SportsCompetition(
        "volei-superliga-f", "volei", "Superliga Feminina",
        aliases = listOf("superliga feminina", "superliga f"),
        teams = listOf(
            "Minas", "Praia Clube", "Sesi Bauru", "Osasco", "Fluminense", "Flamengo",
            "Maringá", "Brasília", "Pinheiros", "Barueri",
        ),
    ),
    SportsCompetition("volei-vnl-m", "volei", "Liga das Nações (M)", aliases = listOf("liga das nacoes", "vnl", "liga das nações")),
    SportsCompetition("volei-mundial-m", "volei", "Mundial de Vôlei (M)", aliases = listOf("mundial de volei", "campeonato mundial de volei")),
    SportsCompetition("volei-champions", "volei", "Champions League de Vôlei", aliases = listOf("champions league de volei", "cev champions league")),
    SportsCompetition("volei-copa-brasil", "volei", "Copa do Brasil de Vôlei", aliases = listOf("copa do brasil de volei")),
    // ── Tênis ───────────────────────────────────────────────────────────
    SportsCompetition("tenis-australian", "tenis", "Australian Open", aliases = listOf("australian open", "aus open")),
    SportsCompetition("tenis-roland", "tenis", "Roland Garros", aliases = listOf("roland garros")),
    SportsCompetition("tenis-wimbledon", "tenis", "Wimbledon", aliases = listOf("wimbledon")),
    SportsCompetition("tenis-us-open", "tenis", "US Open", aliases = listOf("us open")),
    SportsCompetition("tenis-atp-finals", "tenis", "ATP Finals", aliases = listOf("atp finals")),
    SportsCompetition("tenis-wta-finals", "tenis", "WTA Finals", aliases = listOf("wta finals")),
    SportsCompetition("tenis-davis", "tenis", "Copa Davis", aliases = listOf("copa davis", "davis cup")),
    SportsCompetition("tenis-united-cup", "tenis", "United Cup", aliases = listOf("united cup")),
    // ── Futebol Americano ───────────────────────────────────────────────
    SportsCompetition(
        "fa-nfl", "futebol-americano", "NFL",
        aliases = listOf("nfl"),
        teams = listOf(
            "Arizona Cardinals", "Atlanta Falcons", "Baltimore Ravens", "Buffalo Bills",
            "Carolina Panthers", "Chicago Bears", "Cincinnati Bengals", "Cleveland Browns",
            "Dallas Cowboys", "Denver Broncos", "Detroit Lions", "Green Bay Packers",
            "Houston Texans", "Indianapolis Colts", "Jacksonville Jaguars", "Kansas City Chiefs",
            "Las Vegas Raiders", "Los Angeles Chargers", "Los Angeles Rams", "Miami Dolphins",
            "Minnesota Vikings", "New England Patriots", "New Orleans Saints", "New York Giants",
            "New York Jets", "Philadelphia Eagles", "Pittsburgh Steelers",
            "San Francisco 49ers", "Seattle Seahawks", "Tampa Bay Buccaneers",
            "Tennessee Titans", "Washington Commanders",
        ),
    ),
    SportsCompetition("fa-ncaaf", "futebol-americano", "College Football", aliases = listOf("college football", "ncaaf", "ncaa football")),
    SportsCompetition("fa-super-bowl", "futebol-americano", "Super Bowl", aliases = listOf("super bowl")),
    // ── Beisebol ────────────────────────────────────────────────────────
    SportsCompetition(
        "beis-mlb", "beisebol", "MLB",
        aliases = listOf("mlb"),
        teams = listOf(
            "Arizona Diamondbacks", "Athletics", "Atlanta Braves", "Baltimore Orioles",
            "Boston Red Sox", "Chicago Cubs", "Chicago White Sox", "Cincinnati Reds",
            "Cleveland Guardians", "Colorado Rockies", "Detroit Tigers", "Houston Astros",
            "Kansas City Royals", "Los Angeles Angels", "Los Angeles Dodgers", "Miami Marlins",
            "Milwaukee Brewers", "Minnesota Twins", "New York Mets", "New York Yankees",
            "Philadelphia Phillies", "Pittsburgh Pirates", "San Diego Padres",
            "San Francisco Giants", "Seattle Mariners", "St. Louis Cardinals", "Tampa Bay Rays",
            "Texas Rangers", "Toronto Blue Jays", "Washington Nationals",
        ),
    ),
    SportsCompetition("beis-npb", "beisebol", "NPB", aliases = listOf("npb", "beisebol japones")),
    SportsCompetition("beis-wbc", "beisebol", "World Baseball Classic", aliases = listOf("world baseball classic", "wbc")),
    // ── Hóquei ──────────────────────────────────────────────────────────
    SportsCompetition(
        "hoq-nhl", "hoquei", "NHL",
        aliases = listOf("nhl"),
        teams = listOf(
            "Anaheim Ducks", "Boston Bruins", "Buffalo Sabres", "Calgary Flames",
            "Carolina Hurricanes", "Chicago Blackhawks", "Colorado Avalanche",
            "Columbus Blue Jackets", "Dallas Stars", "Detroit Red Wings", "Edmonton Oilers",
            "Florida Panthers", "Los Angeles Kings", "Minnesota Wild", "Montreal Canadiens",
            "Nashville Predators", "New Jersey Devils", "New York Islanders", "New York Rangers",
            "Ottawa Senators", "Philadelphia Flyers", "Pittsburgh Penguins", "San Jose Sharks",
            "Seattle Kraken", "St. Louis Blues", "Tampa Bay Lightning", "Toronto Maple Leafs",
            "Utah Mammoth", "Vancouver Canucks", "Vegas Golden Knights", "Washington Capitals",
            "Winnipeg Jets",
        ),
    ),
    // ── Lutas ───────────────────────────────────────────────────────────
    SportsCompetition("mma-ufc", "mma", "UFC", aliases = listOf("ufc")),
    SportsCompetition("mma-bellator", "mma", "Bellator", aliases = listOf("bellator")),
    SportsCompetition("mma-pfl", "mma", "PFL", aliases = listOf("pfl", "professional fighters league")),
    SportsCompetition("boxe-luta", "boxe", "Boxe", aliases = listOf("boxe", "boxing")),
    // ── Automobilismo ───────────────────────────────────────────────────
    SportsCompetition(
        "f1-formula-1", "formula-1", "Fórmula 1",
        aliases = listOf("formula 1", "f1", "grande premio", "gp"),
        teams = listOf(
            "Red Bull", "Ferrari", "Mercedes", "McLaren", "Aston Martin", "Alpine",
            "Williams", "RB", "Haas", "Sauber",
        ),
    ),
    SportsCompetition("stock-car", "stock-car", "Stock Car", aliases = listOf("stock car")),
    SportsCompetition("nascar-cup", "nascar", "Nascar Cup", aliases = listOf("nascar", "nascar cup series")),
    // ── Golfe ───────────────────────────────────────────────────────────
    SportsCompetition("golfe-masters", "golfe", "Masters", aliases = listOf("masters", "the masters")),
    SportsCompetition("golfe-pga", "golfe", "PGA Championship", aliases = listOf("pga championship")),
    SportsCompetition("golfe-us-open", "golfe", "US Open de Golfe", aliases = listOf("us open de golfe")),
    SportsCompetition("golfe-open", "golfe", "The Open", aliases = listOf("the open", "british open")),
    SportsCompetition("golfe-ryder", "golfe", "Ryder Cup", aliases = listOf("ryder cup")),
    // ── Ciclismo ────────────────────────────────────────────────────────
    SportsCompetition("ciclismo-tour", "ciclismo", "Tour de France", aliases = listOf("tour de france", "tour")),
    SportsCompetition("ciclismo-giro", "ciclismo", "Giro d'Italia", aliases = listOf("giro d'italia", "giro")),
    SportsCompetition("ciclismo-vuelta", "ciclismo", "Vuelta a España", aliases = listOf("vuelta a espana", "vuelta")),
    // ── Atletismo / Natação ─────────────────────────────────────────────
    SportsCompetition("atletismo-mundial", "atletismo", "Mundial de Atletismo", aliases = listOf("mundial de atletismo", "world athletics")),
    SportsCompetition("atletismo-olimpiadas", "atletismo", "Olimpíadas", aliases = listOf("olimpiadas", "jogos olimpicos", "olimpíadas")),
    SportsCompetition("natacao-mundial", "natacao", "Mundial de Natação", aliases = listOf("mundial de natacao", "world aquatics")),
    // ── Handebol / Rugby / Vôlei de Praia ───────────────────────────────
    SportsCompetition("handebol-mundial", "handebol", "Mundial de Handebol", aliases = listOf("mundial de handebol")),
    SportsCompetition("handebol-champions", "handebol", "Champions League de Handebol", aliases = listOf("champions league de handebol", "ehf champions league")),
    SportsCompetition("rugby-six-nations", "rugby", "Six Nations", aliases = listOf("six nations", "seis nacoes")),
    SportsCompetition("rugby-championship", "rugby", "Rugby Championship", aliases = listOf("rugby championship")),
    SportsCompetition("rugby-mundial", "rugby", "Copa do Mundo de Rugby", aliases = listOf("copa do mundo de rugby", "rugby world cup")),
    SportsCompetition("volei-praia-circuito", "volei-praia", "Circuito Mundial de Vôlei de Praia", aliases = listOf("volei de praia", "beach volleyball")),
)

/** Canais de esporte (nomes como aparecem nas listas) — p/ reconhecer o canal pelo nome. */
val SPORTS_DICTIONARY_CHANNELS = listOf(
    "SporTV", "SporTV 2", "SporTV 3", "Premiere", "Premiere 2", "Premiere 3", "Premiere 4",
    "Premiere 5", "Premiere 6", "Premiere 7", "Premiere 8", "ESPN", "ESPN 2", "ESPN 3",
    "ESPN 4", "ESPN 5", "ESPN Extra", "ESPN Deportes", "Fox Sports", "Fox Sports 2",
    "Fox Sports 3", "BandSports", "TNT Sports", "DAZN", "Combate", "CazéTV", "Nosso Futebol",
    "GOAT", "Paramount+", "Sport TV 1", "Sport TV 2", "Sport TV 3", "Sport TV 4", "Sport TV 5",
    "Sport TV 6", "Sport TV +", "Eleven Sports", "Eurosport 1", "Eurosport 2", "NBA TV",
    "NFL Network", "MLB Network", "NHL Network", "Golf Channel", "Tennis Channel",
    "beIN Sports", "beIN Sports 2", "beIN Sports 3", "Sky Sports Main Event",
    "Sky Sports Football", "Sky Sports F1", "Sky Sports Golf", "Sky Sports Action",
    "Sky Sports Arena", "Sky Sports News", "Sky Sports Premier League", "Sky Sports Mix",
    "Sky Sports 1", "Sky Sports 2", "Sky Sports 3", "Sky Sports 4", "Sky Sports 5",
)

/** Resultado da classificação de um programa do guia. */
data class SportsClassification(
    /** Id do esporte ("futebol"). */
    val sport: String,
    /** Nome do esporte em PT-BR ("Futebol"). */
    val sportName: String,
    /** Nome da competição ("Brasileirão Série A") — null sem competição conhecida. */
    val competition: String?,
    val competitionId: String?,
    /** Times na ordem em que aparecem no título (null sem time reconhecido). */
    val homeTeam: String?,
    val awayTeam: String?,
)

/** Índice: nome normalizado do time → (competição, nome de exibição). */
private val TEAM_INDEX: List<Triple<String, SportsCompetition, String>> by lazy {
    SPORTS_DICTIONARY_COMPETITIONS.flatMap { comp ->
        comp.teams.map { team -> Triple(normalizeSportsName(team), comp, team) }
    }.sortedByDescending { it.first.length }
}

/** Palavras que indicam o esporte quando não há time nem competição no texto.
 * ORDEM IMPORTA: o específico antes do genérico ("futsal" antes de
 * "futebol", "vôlei de praia" antes de "vôlei", "futebol americano"
 * antes de "futebol") — senão o genérico casa primeiro. */
private val SPORT_KEYWORDS = mapOf(
    "futsal" to listOf("futsal"),
    "volei-praia" to listOf("volei de praia", "beach volley"),
    "futebol-americano" to listOf("futebol americano", "nfl", "football americano"),
    "futebol" to listOf("futebol", "football", "soccer"),
    "basquete" to listOf("basquete", "basket", "nba", "basquetebol"),
    "volei" to listOf("volei", "vôlei", "volley"),
    "tenis" to listOf("tenis", "tênis", "tennis"),
    "beisebol" to listOf("beisebol", "baseball", "mlb"),
    "hoquei" to listOf("hoquei", "hóquei", "hockey", "nhl"),
    "mma" to listOf("mma", "ufc"),
    "boxe" to listOf("boxe", "boxing"),
    "formula-1" to listOf("formula 1", "fórmula 1", "f1"),
    "stock-car" to listOf("stock car"),
    "nascar" to listOf("nascar"),
    "golfe" to listOf("golfe", "golf"),
    "ciclismo" to listOf("ciclismo", "cycling", "tour de france"),
    "atletismo" to listOf("atletismo", "athletics"),
    "natacao" to listOf("natacao", "natação", "swimming"),
    "handebol" to listOf("handebol", "handball"),
    "rugby" to listOf("rugby"),
)

/** O texto menciona o time (normalizado, com fronteira de palavra). */
fun teamMatches(text: String, team: String): Boolean {
    val needle = normalizeSportsName(team)
    if (needle.isBlank()) return false
    return hasWord(normalizeSportsName(text), needle)
}

/** O nome do canal é de esporte (lista do dicionário, normalizada). */
fun isSportsChannelName(name: String): Boolean {
    val hay = normalizeSportsName(name)
    if (hay.isBlank()) return false
    return SPORTS_DICTIONARY_CHANNELS.any { channel ->
        hasWord(hay, normalizeSportsName(channel))
    }
}

/** [needle] aparece em [hay] com fronteira de palavra (início/fim/espaço/pontuação). */
private fun hasWord(hay: String, needle: String): Boolean {
    var idx = hay.indexOf(needle)
    while (idx >= 0) {
        val beforeOk = idx == 0 || !hay[idx - 1].isLetterOrDigit()
        val afterIdx = idx + needle.length
        val afterOk = afterIdx >= hay.length || !hay[afterIdx].isLetterOrDigit()
        if (beforeOk && afterOk) return true
        idx = hay.indexOf(needle, idx + 1)
    }
    return false
}

/**
 * Classifica um programa do guia (título + descrição). null = não é
 * esporte reconhecível (sem time, competição nem palavra de esporte).
 *
 * Ordem: (1) times no texto — o nome longo ganha sobre o curto na mesma
 * posição ("Santos Laguna" antes de "Santos"); (2) competição — a dos
 * times achados (a com mais times no texto) ou o apelido no texto;
 * (3) esporte — o da competição, ou a palavra-chave. O 1º e o 2º time
 * na ordem de aparição no TÍTULO viram mandante/visitante.
 */
fun classifySportsProgram(title: String, desc: String = ""): SportsClassification? {
    val hay = normalizeSportsName("$title $desc")
    if (hay.isBlank()) return null
    val titleHay = normalizeSportsName(title)

    // (1) Times, sem sobreposição: por posição, fica o nome mais longo.
    data class Hit(val start: Int, val end: Int, val comp: SportsCompetition, val display: String)
    val hits = ArrayList<Hit>()
    for ((norm, comp, display) in TEAM_INDEX) {
        var idx = hay.indexOf(norm)
        while (idx >= 0) {
            val beforeOk = idx == 0 || !hay[idx - 1].isLetterOrDigit()
            val afterIdx = idx + norm.length
            val afterOk = afterIdx >= hay.length || !hay[afterIdx].isLetterOrDigit()
            if (beforeOk && afterOk) hits += Hit(idx, afterIdx, comp, display)
            idx = hay.indexOf(norm, idx + 1)
        }
    }
    val chosen = hits.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.end - it.start })
        .fold(ArrayList<Hit>()) { acc, hit ->
            if (acc.none { hit.start < it.end && it.start < hit.end }) acc += hit
            acc
        }.sortedBy { it.start }
    val titleHits = chosen.filter { it.start < titleHay.length }

    // (2) Competição: a dos times (mais times no texto) ou o apelido.
    var competition: SportsCompetition? = null
    if (chosen.isNotEmpty()) {
        competition = chosen.groupingBy { it.comp }.eachCount()
            .maxByOrNull { it.value }?.key
    }
    if (competition == null) {
        val aliasHit = SPORTS_DICTIONARY_COMPETITIONS.flatMap { comp ->
            (comp.aliases + comp.name).map { alias -> normalizeSportsName(alias) to comp }
        }.filter { (alias, _) -> alias.isNotBlank() && hasWord(hay, alias) }
            .maxByOrNull { (alias, _) -> alias.length }?.second
        competition = aliasHit
    }

    // (3) Esporte: o da competição, ou a palavra-chave.
    val sportId = competition?.sport ?: run {
        val hit = SPORT_KEYWORDS.entries.firstOrNull { (_, words) ->
            words.any { word -> hasWord(hay, normalizeSportsName(word)) }
        }?.key ?: return null
        hit
    }
    val sportName = SPORTS_DICTIONARY_SPORTS.firstOrNull { it.first == sportId }?.second ?: sportId
    return SportsClassification(
        sport = sportId,
        sportName = sportName,
        competition = competition?.name,
        competitionId = competition?.id,
        homeTeam = titleHits.getOrNull(0)?.display,
        awayTeam = titleHits.getOrNull(1)?.display,
    )
}
