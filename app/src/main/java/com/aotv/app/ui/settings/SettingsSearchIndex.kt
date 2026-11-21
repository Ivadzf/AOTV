package com.aotv.app.ui.settings

/**
 * Índice da busca profunda das Configurações (Lote 6, v1.15.0 — redesign).
 *
 * O hub antes só filtrava as PÁGINAS (título/sub/keywords). Este índice lista
 * cada OPÇÃO dentro das páginas (título, subtítulo e palavras-chave), para a
 * busca achar a opção e abrir a página certa — com rolagem e destaque até a
 * seção quando a página suporta âncora ([SettingOptionEntry.anchor]).
 *
 * Páginas sem âncora (Trackers, que tem scaffold próprio, e o Backup fora
 * da seção do Drive): o resultado abre a página no topo; a opção continua
 * listada. A seção do Google Drive (Lote 10, v1.20.0) TEM âncora
 * ([SettingsAnchors.BACKUP_DRIVE]).
 *
 * NENHUMA chave de pref é renomeada aqui — o índice só descreve texto.
 * [kidsHidden] espelha a trava de rota: a opção some da busca no perfil
 * infantil, igual à página que a contém.
 */

/** Âncoras das seções dentro das páginas (fonte única — ver SettingsPages.kt). */
object SettingsAnchors {
    // Layout
    const val LAYOUT_HOME = "layout-inicio"
    const val LAYOUT_POSTERS = "layout-posteres"
    const val LAYOUT_HERO = "layout-destaque"
    const val LAYOUT_TITLES = "layout-titulos"
    const val LAYOUT_RANDOM = "layout-aleatorio"
    // Lote 24 (v1.34.0), item 4: abas da pílula + aba ao abrir.
    const val LAYOUT_NAV = "layout-navegacao"
    // Página de detalhes
    const val DETAIL_BG = "detalhe-fundo"
    const val DETAIL_ACTIONS = "detalhe-acoes"
    const val DETAIL_ORIGINAL = "detalhe-titulo-original"
    const val DETAIL_EPISODES = "detalhe-episodios"
    const val DETAIL_RATINGS = "detalhe-notas"
    const val DETAIL_REVIEWS = "detalhe-avaliacoes"
    const val DETAIL_QUOTES = "detalhe-frases"
    // Streams
    const val STREAMS_SHEET = "streams-folha"
    const val STREAMS_BADGES = "streams-emblemas"
    const val STREAMS_LOGO_BADGES = "streams-selos-logo"
    const val STREAMS_PINNED = "streams-fixadas"
    // Ranking de fontes (Lote 17, v1.27.0): preset, estratégia, idioma, ordem manual.
    const val STREAMS_RANKING = "streams-ranking"
    // Conteúdo & Descoberta
    const val CONTENT_ADDONS = "conteudo-addons"
    const val CONTENT_STATUS = "conteudo-status"
    const val CONTENT_LIBRARY = "conteudo-biblioteca"
    // Integrações
    const val INTEGR_ACCOUNTS = "integracoes-contas"
    const val INTEGR_LIVE = "integracoes-tv"
    // Live TV parte 2 (Lote 12 — Frente 1, v1.22.0): Multiview e canais.
    const val INTEGR_LIVE_MANAGE = "integracoes-tv-canais"
    // Acompanhamento
    const val TRACK_PROFILE = "acompanhamento-perfil"
    const val TRACK_TMDB = "acompanhamento-tmdb"
    const val TRACK_TRAKT = "acompanhamento-trakt"
    // Aparência
    const val APPEAR_THEME = "aparencia-tema"
    const val APPEAR_CUSTOM = "aparencia-tema-personalizado"
    const val APPEAR_PANELS = "aparencia-paineis"
    const val APPEAR_ICON = "aparencia-icone"
    const val APPEAR_DEVICE = "aparencia-aparelho"
    // Atualizações do app (Lote 12 — Frente 3, v1.22.0).
    const val APPEAR_UPDATE = "aparencia-atualizacoes"
    // Reprodução
    const val PLAY_MAIN = "reproducao-reproducao"
    const val PLAY_SUBS = "reproducao-legendas"
    // Lote 24 (v1.34.0), itens 8 e 9: Ambilight e modo sensorial.
    const val PLAY_COMFORT = "reproducao-conforto"
    const val PLAY_BUTTONS = "reproducao-botoes"
    const val PLAY_TOP = "reproducao-botoes-topo"
    const val PLAY_PLAYER = "reproducao-player"
    // MPV (remodel v1.44.0, Frente 2): as opções do motor/mpv saíram de
    // Reprodução p/ a aba própria — as âncoras acompanham a mudança.
    const val MPV_ENGINE = "mpv-motor"
    const val MPV_OPTIONS = "mpv-opcoes"
    const val MPV_EXTRAS = "mpv-extras"
    const val MPV_ENHANCE = "mpv-melhorar-imagem"
    const val PLAY_SKIP = "reproducao-pular"
    const val PLAY_EQ = "reproducao-equalizador"
    // Downloads
    const val DL_DATA = "downloads-dados"
    const val DL_LIMIT = "downloads-limite"
    const val DL_MAIN = "downloads-downloads"
    const val DL_COUNTED = "downloads-conta"
    // Torrent — espaço temporário (Lote 18, v1.28.0).
    const val DL_TORRENT = "downloads-torrent"
    // Gravações (Lote 8)
    const val DL_RECORD = "downloads-gravacoes"
    // Backup — Google Drive (Lote 10, v1.20.0). As outras opções do Backup
    // seguem sem âncora (a página tem scaffold próprio e só esta seção
    // ganhou rolagem + destaque).
    const val BACKUP_DRIVE = "backup-drive"
    // Esportes e Calendário (Lote 8)
    const val LAYOUT_SPORTS = "layout-esportes"
    // VPN e Watch Party (Lote 10)
    const val VPN_IMPORT = "vpn-importar"
    const val VPN_PROFILES = "vpn-perfis"
    const val VPN_SCOPE = "vpn-escopo"
    const val VPN_STATUS = "vpn-status"
    const val PARTY_CREATE = "party-criar"
    const val PARTY_JOIN = "party-entrar"
    const val PARTY_MEMBERS = "party-participantes"
    const val PARTY_SYNC = "party-sincronizacao"
    // Watch Party real (Lote 19, v1.29.0, item 1): voz, câmera e relay TURN.
    const val PARTY_VOICE = "party-voz"
    const val PARTY_CAMERA = "party-camera"
    const val PARTY_RELAY = "party-relay"
    // Social Hub (Lote 19, v1.29.0, item 2): amigos, quer assistir, match,
    // film night e a sessão ativa (que é da Watch Party — só espelhada aqui).
    const val SOCIAL_ABOUT = "social-sem-servidor"
    const val SOCIAL_SESSION = "social-sessao"
    const val SOCIAL_FRIENDS = "social-amigos"
    const val SOCIAL_WANTS = "social-quer-assistir"
    const val SOCIAL_MATCH = "social-couch-match"
    const val SOCIAL_FILM_NIGHT = "social-film-night"
    // Diagnóstico (Lote 11, v1.21.0)
    const val DIAG_DEVICE = "diagnostico-aparelho"
    const val DIAG_ADDONS = "diagnostico-addons"
    const val DIAG_FRAMES = "diagnostico-quadros"
    const val DIAG_SUGGESTION = "diagnostico-sugestao"
    // Último crash (Lote 13, v1.23.0 — Frente 3, Bug 1 da TV).
    const val DIAG_CRASH = "diagnostico-ultimo-crash"
    // Últimos passos do mpv (Lote mpv 3, v1.38.0).
    const val DIAG_MPV_STEPS = "diagnostico-passos-mpv"
    // Propriedades observadas do mpv (Lote mpv 4, v1.39.0 — Frente 3).
    const val DIAG_MPV_PROPS = "diagnostico-propriedades-mpv"
    // Lote 25 (v1.35.0) — Frente B: "Report a problem".
    const val DIAG_REPORT = "diagnostico-reportar-problema"
    // Controle remoto e TV (Lote 22, v1.32.0).
    const val REMOTE_SERVER = "remotetv-servidor"
    const val REMOTE_CLIENT = "remotetv-cliente"
    const val REMOTE_TV = "remotetv-tv"
    const val REMOTE_WATCHNEXT = "remotetv-watchnext"
    const val REMOTE_GAMEPAD = "remotetv-gamepad"
    const val REMOTE_EXTERNAL = "remotetv-player-externo"
}

/** Uma opção dentro de uma página das Configurações. */
data class SettingOptionEntry(
    val title: String,
    val sub: String,
    val keywords: List<String>,
    val pageId: String,
    /** Seção da página p/ rolar até e destacar; null abre a página no topo. */
    val anchor: String? = null,
    /** Escondida no perfil infantil (igual à página ou à linha que a contém). */
    val kidsHidden: Boolean = false,
)

/** Todas as opções indexadas, por página, na ordem em que aparecem na tela. */
val settingsOptionIndex: List<SettingOptionEntry> = listOf(
    // ── Layout ───────────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Personalizar Início",
        sub = "Liga, desliga, ordena e renomeia as fileiras",
        keywords = listOf("personalizar", "fileiras", "ordem", "destaque", "catálogo"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Coleções",
        sub = "Pastas de estúdios, streamings e franquias na Início",
        keywords = listOf("coleções", "pastas", "estúdios", "franquias"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    // Lote 24 (v1.34.0): herói, pastas, abas, MK Channel e Peak Cinema.
    SettingOptionEntry(
        title = "Rotação do herói",
        sub = "Automática, Sempre ligada ou Desligada",
        keywords = listOf("herói", "rotação", "destaque", "carrossel", "automático"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Prévia de cena ao focar",
        sub = "O trailer do destaque começa em mudo ao parar nele",
        keywords = listOf("prévia", "trailer", "herói", "foco", "destaque"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Pastas da Home",
        sub = "Agrupa fileiras sob um cabeçalho recolhível (em Personalizar Início)",
        keywords = listOf("pastas", "fileiras", "agrupar", "organizar"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Abas da pílula",
        sub = "Esconde ou mostra cada aba da navegação inferior",
        keywords = listOf("abas", "pílula", "navegação", "esconder", "mostrar"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_NAV,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Aba ao abrir o app",
        sub = "A aba em que o app abre: Início, Ao vivo, Busca, Biblioteca, Esportes ou Perfil",
        keywords = listOf("aba padrão", "abrir", "início", "navegação"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_NAV,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Fonte do MK Channel",
        sub = "Up next, Minha Lista ou Maratona p/ a fileira \"liga e já tem algo tocando\"",
        keywords = listOf("MK Channel", "up next", "maratona", "minha lista", "tocando"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Peak Cinema",
        sub = "Fileira \"melhores cenas\" por humor, depois do MK Channel",
        keywords = listOf("Peak Cinema", "melhores cenas", "humor", "gênero"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Tamanho do pôster",
        sub = "Pequeno, Médio ou Grande",
        keywords = listOf("pôster", "tamanho", "cartaz"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_POSTERS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Raio dos cantos",
        sub = "Cantos do pôster: Pequeno, Médio ou Grande",
        keywords = listOf("raio", "cantos", "arredondado"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_POSTERS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Densidade",
        sub = "Compacta ou Confortável",
        keywords = listOf("densidade", "espaçamento"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_POSTERS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Mostrar selos nos pôsteres",
        sub = "Etiqueta do topo do pôster",
        keywords = listOf("selos", "etiqueta", "emblema"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_POSTERS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Selos de qualidade e fonte",
        sub = "4K, Remux, BluRay, WEB-DL, DV, HDR10+ e DUB no pôster — ligados por padrão",
        keywords = listOf("selos", "qualidade", "4K", "Remux", "BluRay", "WEB-DL", "DV", "HDR10+", "fonte", "emblema"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_POSTERS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Estilo do herói",
        sub = "Tela cheia ou Cartão",
        keywords = listOf("herói", "destaque", "banner"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HERO,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Fundo dinâmico",
        sub = "Gradiente com as cores do destaque",
        keywords = listOf("fundo", "dinâmico", "gradiente", "cores"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HERO,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Sublinhado de destaque",
        sub = "Barra na cor do tema abaixo do título de cada seção",
        keywords = listOf("sublinhado", "títulos", "seções"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_TITLES,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Sortear do Continuar assistindo",
        sub = "Séries em andamento entram no sorteio",
        keywords = listOf("sorteio", "aleatório", "surpreenda-me", "continuar assistindo"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_RANDOM,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Sortear da Minha Lista",
        sub = "Séries salvas entram no sorteio",
        keywords = listOf("sorteio", "aleatório", "minha lista"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_RANDOM,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Só episódios não vistos",
        sub = "O sorteio evita episódios já vistos",
        keywords = listOf("sorteio", "não vistos", "vistos"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_RANDOM,
        kidsHidden = true,
    ),
    // ── Esportes e Calendário (Lote 8, v1.18.0) ──────────────────────────
    SettingOptionEntry(
        title = "Mostrar esportes",
        sub = "Seção de esportes no Ao vivo e jogos na fileira \"Próximos\"",
        keywords = listOf("esportes", "futebol", "ao vivo", "placar", "jogos"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Placares ao vivo",
        sub = "O placar do EPG aparece no canal de esporte",
        keywords = listOf("placar", "ao vivo", "resultado", "gol", "esporte"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Esconder placares",
        sub = "Nenhum placar aparece (anti-spoiler)",
        keywords = listOf("esconder placar", "spoiler", "resultado", "esporte"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Atraso do placar",
        sub = "O placar só aparece esse tempo depois do início do jogo",
        keywords = listOf("atraso", "placar", "spoiler", "segundos", "anti-spoiler"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Alertas de jogo",
        sub = "Avisa quando um jogo começa, mesmo com o app fechado",
        keywords = listOf("alerta", "jogo", "notificação", "esporte", "começando"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Esconder spoilers do calendário",
        sub = "O episódio aparece sem T/E até ir ao ar",
        keywords = listOf("spoiler", "calendário", "episódio", "sinopse", "próximos"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    // ── Auto-add do calendário via trackers (Lote 13, v1.23.0) ──────────
    SettingOptionEntry(
        title = "Séries do Trakt/Simkl no calendário",
        sub = "Séries acompanhadas no Trakt/Simkl entram no calendário sozinhas",
        keywords = listOf("calendário", "trakt", "simkl", "auto-add", "séries", "episódio", "novos episódios"),
        pageId = SettingsPageIds.Layout,
        anchor = SettingsAnchors.LAYOUT_HOME,
        kidsHidden = true,
    ),
    // ── Página de detalhes ───────────────────────────────────────────────
    SettingOptionEntry(
        title = "Fundo cinematográfico",
        sub = "Arte desfocada em tela cheia atrás da página",
        keywords = listOf("fundo", "cinematográfico", "backdrop", "desfoque"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_BG,
    ),
    SettingOptionEntry(
        title = "Transição do pôster",
        sub = "O pôster cresce ao abrir os detalhes",
        keywords = listOf("transição", "pôster", "animação"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_BG,
    ),
    SettingOptionEntry(
        title = "Ações em ícones",
        sub = "Minha Lista e Trailer como ícones",
        keywords = listOf("ações", "ícones", "botões", "minha lista", "trailer"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_ACTIONS,
    ),
    SettingOptionEntry(
        title = "Layout de abas",
        sub = "Episódios, Elenco e Mais como isto em abas",
        keywords = listOf("abas", "episódios", "elenco"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_ACTIONS,
    ),
    SettingOptionEntry(
        title = "Título em idioma original",
        sub = "Mostra o título original do TMDB abaixo do traduzido",
        keywords = listOf("título original", "idioma", "inglês"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_ORIGINAL,
    ),
    SettingOptionEntry(
        title = "Mostrar notas dos episódios",
        sub = "A nota do TMDB nos cards de episódio",
        keywords = listOf("notas", "avaliação", "rating", "episódios"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_EPISODES,
    ),
    SettingOptionEntry(
        title = "Blur anti-spoiler",
        sub = "Borra a arte e esconde o título dos episódios não assistidos",
        keywords = listOf("spoiler", "blur", "desfoque", "episódio", "não assistido", "sinopse"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_EPISODES,
    ),
    SettingOptionEntry(
        title = "Estilo do card de episódio",
        sub = "Horizontal com arte ou Empilhado com texto",
        keywords = listOf("card", "episódio", "estilo"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_EPISODES,
    ),
    SettingOptionEntry(
        title = "Nota agregada",
        sub = "Nota de 0 a 100 com as notas por fonte (MDBList)",
        keywords = listOf("nota agregada", "nota", "avaliação", "MDBList", "IMDb", "TMDB"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_RATINGS,
    ),
    // Lote 12 — Frente 3 (Notas, v1.22.0): fonte da nota exibida.
    SettingOptionEntry(
        title = "Fonte da nota exibida",
        sub = "Automática (MDBList quando conectado), MDBList ou TMDB",
        keywords = listOf("fonte da nota", "nota", "MDBList", "TMDB", "avaliação"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_RATINGS,
    ),
    // Lote 24 (v1.34.0), item 12: fonte da nota dos episódios + chave do OMDb.
    SettingOptionEntry(
        title = "Nota dos episódios",
        sub = "Média das fontes com nota, ou uma só: TMDB, Trakt, OMDb ou TVmaze",
        keywords = listOf("nota dos episódios", "média", "TMDB", "Trakt", "OMDb", "TVmaze", "avaliação"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_RATINGS,
    ),
    SettingOptionEntry(
        title = "Chave do OMDb",
        sub = "Sua chave gratuita do OMDb (omdbapi.com) p/ a nota dos episódios",
        keywords = listOf("OMDb", "chave", "IMDb", "nota dos episódios"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_RATINGS,
    ),
    SettingOptionEntry(
        title = "O que estão dizendo",
        sub = "Avaliações do Trakt na página de detalhes",
        keywords = listOf("avaliações", "Trakt", "comentários", "opiniões"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_REVIEWS,
    ),
    SettingOptionEntry(
        title = "Frases e ficha de produção",
        sub = "Frases do Wikiquote e resumo da Wikipédia",
        keywords = listOf("frases", "citações", "Wikiquote", "Wikipédia", "ficha"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_QUOTES,
    ),
    // ── Streams ──────────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Barra de pesquisa nas fontes",
        sub = "Filtra as fontes digitando o nome do addon",
        keywords = listOf("pesquisa", "busca", "fontes", "filtro"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_SHEET,
    ),
    SettingOptionEntry(
        title = "Logo do addon",
        sub = "Mostra o logo do addon ao lado do nome da fonte",
        keywords = listOf("logo", "addon", "imagem"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_SHEET,
    ),
    SettingOptionEntry(
        title = "Mostrar tamanho",
        sub = "Emblema com o tamanho do arquivo",
        keywords = listOf("tamanho", "emblema", "GB"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_BADGES,
    ),
    SettingOptionEntry(
        title = "Mostrar codec",
        sub = "Emblema com o codec (HEVC, AV1)",
        keywords = listOf("codec", "emblema", "HEVC", "AV1"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_BADGES,
    ),
    SettingOptionEntry(
        title = "Mostrar idioma",
        sub = "Emblema Dublado ou Legendado em cada fonte",
        keywords = listOf("idioma", "emblema", "dublado", "legendado"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_BADGES,
    ),
    SettingOptionEntry(
        title = "Posição dos emblemas",
        sub = "No canto ou na linha de pills",
        keywords = listOf("posição", "emblemas"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_BADGES,
    ),
    // ── Selos com logo (Lote 19 item 9, v1.29.0) ─────────────────────────
    SettingOptionEntry(
        title = "Selos com logo",
        sub = "Logos (4K, Dolby Vision, DTS-HD) na folha de fontes",
        keywords = listOf("selos", "logo", "4K", "Dolby Vision", "pack", "emblemas"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_LOGO_BADGES,
    ),
    SettingOptionEntry(
        title = "Pack de selos (URL)",
        sub = "URL do pack de selos em JSON + prévia por categoria",
        keywords = listOf("pack", "URL", "JSON", "selos", "prévia", "logo"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_LOGO_BADGES,
    ),
    SettingOptionEntry(
        title = "Fontes fixadas",
        sub = "As fixadas aparecem primeiro na folha de fontes",
        keywords = listOf("fixadas", "pin", "ordem", "fontes"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_PINNED,
    ),
    // ── Ranking de fontes (Lote 17, v1.27.0) ─────────────────────────────
    SettingOptionEntry(
        title = "Preset de fontes",
        sub = "Melhor imagem, Equilibrado ou Início rápido",
        keywords = listOf("preset", "fontes", "ranking", "qualidade", "rápido"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    SettingOptionEntry(
        title = "Estratégia do ranking",
        sub = "Qualidade, rapidez, confiabilidade ou idioma primeiro",
        keywords = listOf("estratégia", "ranking", "fontes", "score", "ordem"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    SettingOptionEntry(
        title = "Idioma das fontes",
        sub = "Todos, Dublado, Legendado ou Original",
        keywords = listOf("idioma", "fontes", "dublado", "legendado", "original", "filtro"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    // Lote mpv 3 (v1.38.0): formato de fonte preferido (HLS/MP4) — bônus no score do ranking.
    SettingOptionEntry(
        title = "Formato preferido das fontes",
        sub = "Automático, HLS ou MP4 — o formato preferido ganha pontos no ranking",
        keywords = listOf("formato", "HLS", "MP4", "m3u8", "fontes", "ranking"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    SettingOptionEntry(
        title = "Ordem manual das fontes",
        sub = "A ordem escolhida vale por cima do ranking",
        keywords = listOf("ordem", "manual", "fontes", "ranking", "reordenar"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    SettingOptionEntry(
        title = "Esconder o que o aparelho não toca",
        sub = "Fontes com codec sem suporte não aparecem na lista",
        keywords = listOf("codec", "AV1", "HEVC", "esconder", "fontes", "aparelho"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    SettingOptionEntry(
        title = "Lembrar minha escolha por título",
        sub = "A última fonte escolhida à mão ganha pontos no ranking",
        keywords = listOf("lembrar", "escolha", "título", "fontes", "última"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_RANKING,
    ),
    // ── Conteúdo & Descoberta ────────────────────────────────────────────
    SettingOptionEntry(
        title = "Gerenciar addons",
        sub = "Instale, ordene e ative cada addon",
        keywords = listOf("addons", "gerenciar", "instalar", "fontes"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    // Lote 24 (v1.34.0), item 3: código de setup — fica no topo da Loja de
    // addons (a busca abre a página de Conteúdo, de onde a loja abre).
    SettingOptionEntry(
        title = "Compartilhar setup de addons",
        sub = "Gere um código com os addons instalados ou aplique o código de outro aparelho",
        keywords = listOf("compartilhar", "setup", "código", "addons", "transferir", "outro aparelho"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Plugins do Nuvio",
        sub = "Scrapers JavaScript por manifest",
        keywords = listOf("plugins", "scrapers", "manifest", "javascript"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Addons Kodi",
        sub = "Instale addons Kodi por ZIP ou URL",
        keywords = listOf("kodi", "zip", "addons"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Debrid",
        sub = "Real-Debrid, TorBox, Premiumize ou AllDebrid",
        keywords = listOf("debrid", "real-debrid", "torbox", "premiumize", "alldebrid", "torrent"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Servidores de mídia",
        sub = "Jellyfin, Emby e Plex — sua coleção no app",
        keywords = listOf("servidor", "servidores", "jellyfin", "emby", "plex", "coleção", "mídia", "biblioteca"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Verificar fontes agora",
        sub = "Confere se o TMDB e os addons estão online",
        keywords = listOf("verificar", "fontes", "status", "online", "saúde"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_STATUS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Limpar Continuar assistindo",
        sub = "Apaga posições salvas de filmes e séries",
        keywords = listOf("limpar", "continuar assistindo", "histórico", "apagar"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_LIBRARY,
        kidsHidden = true,
    ),
    // ── Lote 23 (v1.33.0): ordem da Minha Lista, período das Estatísticas e gêneros bloqueados ──
    SettingOptionEntry(
        title = "Ordem da Minha Lista",
        sub = "Recentes, Nome ou Não assistidos primeiro",
        keywords = listOf("minha lista", "ordem", "ordenar", "recentes", "nome", "não assistidos"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_LIBRARY,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Período das Estatísticas",
        sub = "7 dias, 30 dias, 90 dias ou Tudo",
        keywords = listOf("estatísticas", "período", "horas", "recap", "heatmap", "sequência"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_LIBRARY,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Gêneros bloqueados",
        sub = "Esconde gêneros de Início, Busca e Filmes",
        keywords = listOf("gêneros", "bloquear", "esconder", "terror", "bloqueio"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_LIBRARY,
        kidsHidden = true,
    ),
    // ── Integrações ──────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Stremio e Nuvio",
        sub = "Traga addons, Minha Lista, progresso e debrid",
        keywords = listOf("stremio", "nuvio", "conta", "login"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_ACCOUNTS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Canais ao vivo",
        sub = "Listas M3U e contas Xtream, com guia",
        keywords = listOf("tv ao vivo", "canais", "m3u", "xtream", "guia", "epg"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    // ── IPTV (Lote 7) ────────────────────────────────────────────────
    // Todas caem na seção "TV ao vivo" de Integrações, que abre as fontes
    // (LiveSourcesScreen: adicionar/remover, EPG, atualizar, aba).
    SettingOptionEntry(
        title = "Ao vivo",
        sub = "Abrir a aba de canais ao vivo e o guia de programação",
        keywords = listOf("iptv", "ao vivo", "live", "tv", "canais", "guia"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Fontes de canais",
        sub = "Adicionar ou remover listas M3U e contas Xtream",
        keywords = listOf("fontes", "m3u", "xtream", "lista", "conta", "iptv"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Guia XMLTV",
        sub = "Endereço do guia de programação (XMLTV) da lista",
        keywords = listOf("guia", "xmltv", "epg", "programação", "grade"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Guia de programação",
        sub = "Grade por horário com o que está passando agora",
        keywords = listOf("guia", "grade", "programação", "epg", "agora", "horários"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Atualizar canais",
        sub = "Baixar de novo as listas e o guia",
        keywords = listOf("atualizar", "recarregar", "refresh", "canais", "sincronizar"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Aba Ao vivo na barra",
        sub = "Mostra ou esconde os canais na barra de baixo",
        keywords = listOf("aba", "barra", "mostrar", "esconder", "navegação"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    // ── IPTV catch-up e edição de fonte (v1.17.0) ───────────────────────
    SettingOptionEntry(
        title = "Catch-up",
        sub = "Rever programas passados que a lista permite gravar",
        keywords = listOf("catch-up", "catchup", "replay", "reprisa", "arquivo", "rever", "gravado"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Editar fonte",
        sub = "Alterar nome, endereço ou credenciais de uma fonte de canais",
        keywords = listOf("editar fonte", "edit source", "modificar", "atualizar m3u", "credenciais xtream", "editar lista"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    // ── Live TV (Lote 8, v1.18.0) ───────────────────────────────────────
    // Guia configurável, atualização, exibição, formato Xtream, reserva,
    // Continuar assistindo, faixa de áudio e catch-up HLS. Todas caem na
    // seção "TV ao vivo" de Integrações.
    SettingOptionEntry(
        title = "Guia: dias à frente",
        sub = "Até quantos dias o guia de programação alcança",
        keywords = listOf("guia", "dias", "grade", "programação", "frente", "epg"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Guia: horas para trás",
        sub = "Até quantas horas atrás o guia de programação volta",
        keywords = listOf("guia", "horas", "atrás", "grade", "programação", "passado", "epg"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Atualização das listas",
        sub = "De quanto em quanto tempo as listas atualizam sozinhas ao abrir o Ao vivo",
        keywords = listOf("atualizar", "listas", "intervalo", "refresh", "automático"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Atualização do guia",
        sub = "De quanto em quanto tempo o guia de programação atualiza sozinho",
        keywords = listOf("atualizar", "guia", "epg", "intervalo", "refresh", "automático"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Atualizar agora",
        sub = "Baixar de novo as listas e o guia na hora",
        keywords = listOf("atualizar agora", "recarregar", "refresh", "forçar", "canais", "guia"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Logos dos canais",
        sub = "Mostrar ou esconder a logo na fileira do canal",
        keywords = listOf("logo", "canais", "exibição", "mostrar", "imagem"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Nomes dos canais",
        sub = "Mostrar ou esconder o nome na fileira do canal",
        keywords = listOf("nome", "canais", "exibição", "mostrar", "título"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Números dos canais",
        sub = "Mostrar o número do canal antes do nome",
        keywords = listOf("número", "canais", "exibição", "mostrar", "tvg-chno"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Limpar nomes de grupos",
        sub = "Tirar prefixos tipo \"BR | \" dos nomes de grupos na exibição",
        keywords = listOf("grupo", "limpar", "prefixo", "br", "exibição", "nome"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Formato do stream Xtream",
        sub = "Automático, TS ou HLS para os canais das contas Xtream",
        keywords = listOf("xtream", "formato", "ts", "hls", "m3u8", "stream"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Canal reserva",
        sub = "Se o canal falhar, tenta outro com o mesmo nome de outra fonte",
        keywords = listOf("reserva", "backup", "canal", "falha", "outra fonte"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Ao vivo no Continuar assistindo",
        sub = "O canal assistido aparece no Continuar, com o programa atual",
        keywords = listOf("continuar assistindo", "ao vivo", "histórico", "canal", "programa"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Lembrar faixa de áudio",
        sub = "A faixa de áudio escolhida volta sozinha neste canal",
        keywords = listOf("áudio", "faixa", "lembrar", "idioma", "dublado", "canal"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Catch-up em HLS",
        sub = "A gravação do programa vem em .m3u8 em vez de .ts",
        keywords = listOf("catch-up", "catchup", "hls", "m3u8", "gravação", "timeshift"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    // ── Hub da Live TV (Lote 9, v1.19.0) ────────────────────────────────
    // Caem na seção "TV ao vivo" de Integrações, como as outras entradas de
    // Live TV. O On Demand é a tela da Frente 2 (rota "live_ondemand").
    SettingOptionEntry(
        title = "Hub da Live TV",
        sub = "Busca, Guia, Calendário, On Demand, Esportes e Configurações; grupos de canais em cartões",
        keywords = listOf("live tv", "hub", "ao vivo", "grupos", "cartões", "busca", "guia", "calendário", "esportes"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "On Demand",
        sub = "Filmes e séries sob demanda da conta Xtream, no hub da Live TV",
        keywords = listOf("on demand", "sob demanda", "vod", "filmes", "séries", "xtream"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE,
        kidsHidden = true,
    ),
    // ── Live TV parte 2 (Lote 12, v1.22.0 — Frente 1) ─────────────────
    // Caem na seção "Multiview e canais" de Integrações.
    SettingOptionEntry(
        title = "Multiview",
        sub = "Até 4 canais ao mesmo tempo, com áudio só na célula em foco",
        keywords = listOf("multiview", "multi", "4 canais", "2 canais", "tela dividida", "ao vivo"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE_MANAGE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Gerenciar canais",
        sub = "Ocultar, reordenar, renomear, trocar a logo e associar o guia de cada canal",
        keywords = listOf("gerenciar canais", "ocultar canal", "renomear", "renumerar", "reordenar", "logo", "grupo próprio"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE_MANAGE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Assign EPG",
        sub = "Associar manualmente uma fonte de guia a um canal e deslocar o guia em horas",
        keywords = listOf("assign epg", "associar guia", "epg", "time shift", "deslocar guia", "guia do canal"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_LIVE_MANAGE,
        kidsHidden = true,
    ),
    // ── Acompanhamento ───────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Trocar de perfil/conta",
        sub = "Perfil ativo: trocar de conta ou perfil",
        keywords = listOf("perfil", "trocar", "editar", "conta", "quem está assistindo"),
        pageId = SettingsPageIds.Acompanhamento,
        anchor = SettingsAnchors.TRACK_PROFILE,
    ),
    SettingOptionEntry(
        title = "PIN dos pais",
        sub = "Criar ou trocar o PIN dos pais",
        keywords = listOf("pin", "pais", "senha", "bloqueio", "infantil"),
        pageId = SettingsPageIds.Acompanhamento,
        anchor = SettingsAnchors.TRACK_PROFILE,
        kidsHidden = true,
    ),
    // Lote 23 (v1.33.0): só aparece com o perfil infantil ativo, então NÃO é kidsHidden.
    SettingOptionEntry(
        title = "Limite de idade",
        sub = "Idade máxima do perfil infantil",
        keywords = listOf("idade", "limite", "infantil", "criança", "classificação"),
        pageId = SettingsPageIds.Acompanhamento,
        anchor = SettingsAnchors.TRACK_PROFILE,
    ),
    SettingOptionEntry(
        title = "Grupos trancados com PIN",
        sub = "Grupos de canais ao vivo trancados com o PIN dos pais",
        keywords = listOf("pin", "grupo", "trancar", "cadeado", "bloqueio", "ao vivo", "canais"),
        pageId = SettingsPageIds.Acompanhamento,
        anchor = SettingsAnchors.TRACK_PROFILE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Chave TMDB",
        sub = "Sua chave TMDB (opcional)",
        keywords = listOf("tmdb", "chave", "api", "api key", "catálogo", "busca"),
        pageId = SettingsPageIds.Acompanhamento,
        anchor = SettingsAnchors.TRACK_TMDB,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Trakt",
        sub = "Conectar, sincronizar ou desconectar o Trakt",
        keywords = listOf("trakt", "sincronizar", "scrobble", "minha lista"),
        pageId = SettingsPageIds.Acompanhamento,
        anchor = SettingsAnchors.TRACK_TRAKT,
    ),
    // ── Aparência ────────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Tema",
        sub = "Tema do app",
        keywords = listOf("tema", "cores", "escuro", "claro"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_THEME,
        kidsHidden = true,
    ),
    // Lote 24 (v1.34.0), item 1: preto puro por cima do tema.
    SettingOptionEntry(
        title = "Preto puro (AMOLED)",
        sub = "Fundo #000000 e cartões quase pretos por cima do tema",
        keywords = listOf("AMOLED", "preto puro", "OLED", "bateria", "escuro"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_THEME,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Tema personalizado",
        sub = "As 6 cores editáveis do tema",
        keywords = listOf("tema personalizado", "cores", "editar"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_CUSTOM,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Blur nos painéis",
        sub = "Desfoque atrás das folhas e da pílula",
        keywords = listOf("blur", "desfoque", "vidro", "painéis"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_PANELS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Ícone do app",
        sub = "Ícone colorido: Padrão, Vermelho, Turquesa, Verde, Amarelo, Roxo ou Rosa",
        keywords = listOf("ícone", "icone", "cor", "colorido", "launcher", "tela inicial"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_ICON,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Aparelho automático",
        sub = "Reconhece TV, tablet e celular",
        keywords = listOf("automático", "aparelho", "tv", "tablet"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_DEVICE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Forçar TV",
        sub = "Layout de controle remoto com menu lateral",
        keywords = listOf("tv", "forçar", "controle remoto", "menu lateral"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_DEVICE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Celular ou tablet",
        sub = "Layout de toque adaptado ao espaço",
        keywords = listOf("celular", "tablet", "toque"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_DEVICE,
        kidsHidden = true,
    ),
    // Lote 12 — Frente 3 (Atualizador, v1.22.0).
    SettingOptionEntry(
        title = "Verificar atualizações",
        sub = "Consulta a release mais recente no GitHub e instala o APK",
        keywords = listOf("atualização", "atualizar", "versão", "nova versão", "release", "apk", "github"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_UPDATE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Verificação automática de atualização",
        sub = "Avisa quando houver versão nova (no máximo 1 vez por dia)",
        keywords = listOf("atualização automática", "atualizar", "versão", "aviso"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_UPDATE,
        kidsHidden = true,
    ),
    // Lote 18 (v1.28.0): download em 2º plano + "What's new".
    SettingOptionEntry(
        title = "Atualização em 2º plano",
        sub = "Baixa a versão nova sozinha e avisa quando estiver pronta",
        keywords = listOf("atualização", "segundo plano", "baixar sozinho", "versão", "apk"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_UPDATE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Novidades da versão",
        sub = "O que mudou na versão instalada",
        keywords = listOf("novidades", "what's new", "changelog", "o que mudou", "versão"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_UPDATE,
        kidsHidden = true,
    ),
    // Lote 25 (v1.35.0), item 4 — Tour de boas-vindas (Frente A).
    SettingOptionEntry(
        title = "Tour de boas-vindas",
        sub = "Rever os 5 capítulos: boas-vindas, Início, busca, player e perfis",
        keywords = listOf("tour", "boas-vindas", "onboarding", "ajuda", "começar", "tutorial"),
        pageId = SettingsPageIds.Aparencia,
        anchor = SettingsAnchors.APPEAR_UPDATE,
        kidsHidden = true,
    ),
    // ── Reprodução ───────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Áudio preferido",
        sub = "Qualquer, Dublado ou Legendado",
        keywords = listOf("áudio", "dublado", "legendado", "preferência"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    // Lote 24 (v1.34.0), itens 8 e 9: Ambilight e modo sensorial.
    SettingOptionEntry(
        title = "Ambilight no player",
        sub = "O fundo do player acompanha as cores da cena",
        keywords = listOf("Ambilight", "cores", "luz", "fundo", "player"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_COMFORT,
    ),
    SettingOptionEntry(
        title = "Intensidade do Ambilight",
        sub = "Baixa, Média ou Alta",
        keywords = listOf("Ambilight", "intensidade", "brilho"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_COMFORT,
    ),
    SettingOptionEntry(
        title = "Modo sensorial",
        sub = "Suaviza estouros de som e pisca-pisca",
        keywords = listOf("sensorial", "sensível", "som", "pisca", "estouro", "calmo"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_COMFORT,
    ),
    SettingOptionEntry(
        title = "Qualidade máxima",
        sub = "Sem limite, 4K, 1080p ou 720p",
        keywords = listOf("qualidade", "resolução", "4k", "1080p", "720p"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    // Lote 25 (v1.35.0), itens 7–9 — Frente C: gate de dados móveis,
    // governador de bateria e saída de áudio.
    SettingOptionEntry(
        title = "Dados móveis ao dar play",
        sub = "Perguntar sempre, liberar sempre ou só Wi-Fi",
        keywords = listOf("dados móveis", "3g", "4g", "5g", "wifi", "wi-fi", "play", "gate"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Governador de bateria",
        sub = "Com bateria fraca, sugere o modo economia (até 720p)",
        keywords = listOf("bateria", "economia", "720p", "qualidade", "fraca"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Saída de áudio (TV ou receiver)",
        sub = "Automática, alto-falantes ou receiver (HDMI)",
        keywords = listOf("áudio", "saída", "receiver", "hdmi", "alto-falante", "caixa de som"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Passthrough Dolby/DTS",
        sub = "Manda o bitstream direto p/ o receiver (vale na próxima reprodução)",
        keywords = listOf("passthrough", "dolby", "dts", "bitstream", "receiver", "atmos"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Trailer no destaque",
        sub = "Toca sem som depois de 5 s parado num título",
        keywords = listOf("trailer", "destaque", "autoplay"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    // Lote 21 (v1.31.0): autoplay do trailer no topo do detalhe.
    SettingOptionEntry(
        title = "Autoplay do trailer no detalhe",
        sub = "Toca no topo do detalhe depois de alguns segundos",
        keywords = listOf("trailer", "detalhe", "autoplay", "mini-player"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Autoplay do trailer só no Wi-Fi",
        sub = "No dados móveis, o trailer só toca no botão",
        keywords = listOf("trailer", "wifi", "dados móveis", "autoplay"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Legenda ao abrir",
        sub = "Liga sozinha no idioma escolhido quando o título tiver",
        keywords = listOf("legenda", "abrir", "automática"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Idioma da legenda",
        sub = "Também aparece primeiro na lista do player",
        keywords = listOf("idioma", "legenda", "português", "inglês"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Idioma do áudio",
        sub = "O player escolhe a faixa nesse idioma",
        keywords = listOf("idioma", "áudio", "faixa"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Cor da legenda",
        sub = "Cor do texto da legenda",
        keywords = listOf("cor", "legenda"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Fonte da legenda",
        sub = "Tipografia do texto da legenda",
        keywords = listOf("fonte", "legenda", "tipografia"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Opacidade do fundo",
        sub = "Opacidade do fundo da legenda",
        keywords = listOf("opacidade", "fundo", "legenda"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Contorno da legenda",
        sub = "Contorno preto ao redor do texto",
        keywords = listOf("contorno", "legenda", "borda"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    // Lote 16 item 3 (v1.26.0): legendas nível 2.
    SettingOptionEntry(
        title = "Só legendas em língua estrangeira",
        sub = "A legenda só abre sozinha quando o áudio não é do seu idioma",
        keywords = listOf("legenda", "estrangeira", "foreign", "idioma", "áudio"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Ocultar legendas SDH",
        sub = "Faixas para surdos não entram na escolha automática",
        keywords = listOf("legenda", "sdh", "surdos", "cc", "closed caption"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Chave do SubDL",
        sub = "2ª fonte de legendas (chave grátis do usuário)",
        keywords = listOf("legenda", "subdl", "chave", "fonte"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Botões do player",
        sub = "Ordem e liga/desliga dos botões do player",
        keywords = listOf("botões", "player", "ordem", "controles"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_BUTTONS,
    ),
    SettingOptionEntry(
        title = "Botões do topo",
        sub = "Cast, girar tela e travar controles",
        keywords = listOf("cast", "chromecast", "girar", "rotação", "travar", "bloqueio de tela", "topo"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_TOP,
    ),
    SettingOptionEntry(
        title = "Tocar na barra para buscar",
        sub = "Tocar na barra de progresso pula para aquele ponto",
        keywords = listOf("tocar", "barra", "buscar", "progresso", "seek"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Deslizar para avançar/retroceder",
        sub = "Deslizar sobre o vídeo avança ou retrocede 10s",
        keywords = listOf("deslizar", "gesto", "avançar", "retroceder", "10s"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Volume Boost",
        sub = "Deixa o volume passar de 100%",
        keywords = listOf("volume", "boost", "alto", "som"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    // Lote 16 item 4 (v1.26.0): taxa de quadros.
    SettingOptionEntry(
        title = "Sincronizar taxa de quadros",
        sub = "A tela acompanha o fps do vídeo (24/25/30 fps sem engasgo)",
        keywords = listOf("taxa", "quadros", "fps", "frame rate", "tela", "hz"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Nível do Volume Boost",
        sub = "100% a 200%",
        keywords = listOf("volume", "boost", "nível"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Mini player",
        sub = "Minimizar o player e continuar navegando",
        keywords = listOf("mini player", "picture-in-picture", "pip"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "'A seguir' automático",
        sub = "Avança sozinho para o próximo episódio",
        keywords = listOf("a seguir", "automático", "próximo episódio", "autoplay"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Atraso do \"A seguir\"",
        sub = "5 s a 30 s",
        keywords = listOf("atraso", "a seguir", "segundos"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    // Lote 19 item 7 (v1.29.0) — Opções de Reprodução do JOTV: o título (c)
    // é o da foto; (e) e (f) são as opções novas do lote.
    SettingOptionEntry(
        title = "Trocar de fonte sozinho",
        sub = "Se a fonte travar, troca sozinha por outra",
        keywords = listOf("troca automática", "fonte", "travar", "falhar"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Subir a qualidade sozinho",
        sub = "Se acha uma fonte melhor logo no começo, troca uma vez sem perder o ponto",
        keywords = listOf("qualidade", "melhor", "upgrade", "subir", "trocar"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    // Lote mpv 3 (v1.38.0): desligar a procura automática de fonte no começo.
    SettingOptionEntry(
        title = "Procurar outra fonte no começo",
        sub = "Ao abrir, procura outra fonte se a atual não iniciar ou se achar uma melhor — em P2P nunca troca sozinha",
        keywords = listOf("procurar", "fonte", "começo", "troca automática", "upgrade", "torrent", "P2P"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Debrid só com arquivo pronto",
        sub = "A troca automática nunca abre torrent que o debrid ainda não tem",
        keywords = listOf("debrid", "torrent", "cache", "pronto", "download"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Avisar quando a duração não bate",
        sub = "Pergunta antes de tocar um arquivo com duração muito diferente da do título",
        keywords = listOf("duração", "guarda", "arquivo", "outro filme", "minutos"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Espera antes de trocar",
        sub = "8 s a 60 s",
        keywords = listOf("tempo", "troca", "fonte", "timeout", "espera"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Reutilizar última fonte",
        sub = "Abre cada título na última fonte escolhida",
        keywords = listOf("reutilizar", "última fonte", "lembrar"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Velocidade padrão",
        sub = "0,5x a 2x",
        keywords = listOf("velocidade", "reprodução", "2x", "lenta", "rápida"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    // ── Lote 12 — Frente 2 (Player e fontes, v1.22.0) ─────────────────────
    SettingOptionEntry(
        title = "Modo de tela padrão",
        sub = "Automático, Ajustar, Preencher, Zoom ou Esticar",
        keywords = listOf("tela", "modo de tela", "automático", "ajustar", "preencher", "zoom", "esticar", "proporção"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Fontes em ranking",
        sub = "Folha de fontes da melhor para a pior em vez de agrupada por addon",
        keywords = listOf("ranking", "fontes", "melhor", "pior", "ordem", "folha de fontes"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Player padrão",
        sub = "ExoPlayer, Libmpv ou player externo",
        keywords = listOf("player", "externo", "vlc", "mx player", "exoplayer", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_ENGINE,
    ),
    SettingOptionEntry(
        title = "Decoder de vídeo",
        sub = "Hardware (HW) ou Hardware com fallback",
        keywords = listOf("decoder", "hardware", "hw", "vídeo"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_ENGINE,
    ),
    // Lote mpv 2 (v1.37.0): o painel "Melhorar imagem" (upscaler Anime4K,
    // filtros, desempenho) só existe com o motor Libmpv — a busca leva p/
    // a aba MPV, seção Melhorar imagem (remodel v1.44.0).
    SettingOptionEntry(
        title = "Melhorar imagem (Libmpv)",
        sub = "Upscaler Anime4K, filtros e desempenho — só com o motor Libmpv",
        keywords = listOf("melhorar imagem", "upscaler", "anime4k", "libmpv", "shader", "nitidez", "debanding"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_ENHANCE,
    ),
    SettingOptionEntry(
        title = "Buffer do player",
        sub = "Baixo, Normal ou Alto",
        keywords = listOf("buffer", "carregamento", "travando", "exoplayer"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_ENGINE,
    ),
    // Lote mpv 3 (v1.38.0) — Opções do Libmpv: display-fps, video-sync,
    // hwdec, cache, correção de tom, posição das legendas e detecção de
    // hardware do upscaling.
    SettingOptionEntry(
        title = "Taxa da tela do mpv",
        sub = "Automática ou fixa (24 a 120 Hz) — fixe a taxa do painel se o vídeo engasgar",
        keywords = listOf("display-fps", "taxa", "tela", "Hz", "engasgando", "mpv", "libmpv", "fps"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_OPTIONS,
    ),
    SettingOptionEntry(
        title = "Sincronização de vídeo do mpv",
        sub = "Display (resample/vdrop) ou Áudio",
        keywords = listOf("video-sync", "sincronização", "resample", "vdrop", "áudio", "mpv", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_OPTIONS,
    ),
    SettingOptionEntry(
        title = "Decodificação por hardware do mpv",
        sub = "Hardware (zero-copy), Hardware (com cópia) ou Software",
        keywords = listOf("hwdec", "hardware", "mediacodec", "software", "decoder", "mpv", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_OPTIONS,
    ),
    SettingOptionEntry(
        title = "Cache do mpv",
        sub = "Padrão do mpv ou 10 a 120 segundos",
        keywords = listOf("cache", "demuxer", "buffer", "mpv", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_OPTIONS,
    ),
    SettingOptionEntry(
        title = "Posição das legendas (mpv)",
        sub = "Embaixo, acima ou no meio da tela",
        keywords = listOf("legenda", "posição", "sub-pos", "vertical", "mpv", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_OPTIONS,
    ),
    SettingOptionEntry(
        title = "Manter o tom da voz ao mudar a velocidade",
        sub = "A voz não distorce quando a velocidade muda (mpv e ExoPlayer)",
        keywords = listOf("tom", "voz", "pitch", "velocidade", "distorcer", "mpv", "scaletempo"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_OPTIONS,
    ),
    SettingOptionEntry(
        title = "Detecção de hardware do upscaling",
        sub = "Automática, Fraca ou Forte — em aparelho fraco o Auto fica no preset mais leve",
        keywords = listOf("hardware", "upscaling", "fraca", "forte", "automática", "placa", "melhorar imagem", "mpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_ENHANCE,
    ),
    // Lote mpv 4 (v1.39.0) — Frente 3: pré-carregar o motor, scripts Lua e
    // servidor IPC do mpv.
    SettingOptionEntry(
        title = "Pré-carregar o motor Libmpv",
        sub = "O motor já está quente ao abrir o player — desliga p/ não gastar memória antes",
        keywords = listOf("pré-carregar", "preload", "motor", "quente", "memória", "mpv", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_EXTRAS,
    ),
    SettingOptionEntry(
        title = "Scripts Lua do mpv",
        sub = "Instala um .lua do celular, liga/desliga e exclui — lista os scripts presentes",
        keywords = listOf("scripts", "lua", "mpv", "libmpv", "pasta", "exemplo", "instalar", "excluir"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_EXTRAS,
    ),
    SettingOptionEntry(
        title = "Servidor IPC do mpv",
        sub = "Controla o player por socket — desligado por padrão",
        keywords = listOf("ipc", "socket", "servidor", "controle", "mpv", "libmpv"),
        pageId = SettingsPageIds.Mpv,
        anchor = SettingsAnchors.MPV_EXTRAS,
    ),
    SettingOptionEntry(
        title = "Client ID do Anime-Skip",
        sub = "Pular trechos em séries (IntroDB/SkipDB sem chave)",
        keywords = listOf("anime-skip", "pular", "abertura", "encerramento", "intro", "skip", "client id"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SKIP,
    ),
    // ── Lote 9 — Player parte 3 (v1.19.0) ─────────────────────────────────
    SettingOptionEntry(
        title = "Sincronização automática da legenda",
        sub = "Mede o atraso da legenda externa contra a embutida",
        keywords = listOf("autosync", "sincronização", "sincronizar", "atraso", "legenda", "automática"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    // Lote 23 (v1.33.0): legenda forçada automática.
    SettingOptionEntry(
        title = "Legenda forçada automática",
        sub = "Sem legenda completa, entra sozinha a faixa forçada",
        keywords = listOf("legenda forçada", "forced", "automática", "idioma", "falas"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SUBS,
    ),
    SettingOptionEntry(
        title = "Pular abertura automaticamente",
        sub = "Pula a abertura e o encerramento sozinho (1x por episódio)",
        keywords = listOf("pular", "abertura", "encerramento", "automático", "skip", "intro"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SKIP,
    ),
    SettingOptionEntry(
        title = "Mostrar cartão \"A seguir\"",
        sub = "Cartão do próximo episódio no fim do episódio",
        keywords = listOf("a seguir", "próximo episódio", "cartão", "up next", "fim do episódio"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Equalizador",
        sub = "Presets do aparelho e 5 bandas em dB (só com o player aberto)",
        keywords = listOf("equalizador", "eq", "preset", "bandas", "graves", "agudos", "som"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_EQ,
    ),
    // ── Player parte 3 (Lote 9, v1.19.0) — sem âncora ─────────────────────
    // Sleep timer e Saída de áudio não são configurações: vivem no painel
    // do player (sessão e infos técnicas) e por isso abrem a página
    // Reprodução no topo. As outras 4 opções do player (sincronização da
    // legenda, pular abertura, cartão "A seguir", Equalizador) têm entradas
    // COM âncora logo acima (PLAY_SUBS / PLAY_SKIP / PLAY_PLAYER / PLAY_EQ).
    SettingOptionEntry(
        title = "Sleep timer",
        sub = "Para a reprodução depois de um tempo ou no fim do episódio",
        keywords = listOf("sleep timer", "dormir", "desligar", "tempo", "fim do episódio", "timer"),
        pageId = SettingsPageIds.Reproducao,
    ),
    SettingOptionEntry(
        title = "Saída de áudio",
        sub = "Alto-falante, fone, Bluetooth ou Cast nas infos técnicas do player",
        keywords = listOf("saída de áudio", "alto-falante", "fone", "bluetooth", "cast", "infos técnicas"),
        pageId = SettingsPageIds.Reproducao,
    ),
    // ── Downloads ────────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Dados móveis usados",
        sub = "Hoje e este mês (downloads, streaming e torrent medidos)",
        keywords = listOf("dados móveis", "consumo", "hoje", "mês"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_DATA,
    ),
    SettingOptionEntry(
        title = "Avisar quando chegar a",
        sub = "Limite mensal de dados móveis (só aviso)",
        keywords = listOf("limite", "dados móveis", "aviso", "MB", "GB"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_LIMIT,
    ),
    SettingOptionEntry(
        title = "Só Wi-Fi",
        sub = "Downloads em dados móveis ficam na fila",
        keywords = listOf("wi-fi", "wifi", "dados móveis", "fila"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_MAIN,
    ),
    SettingOptionEntry(
        title = "Pasta de download",
        sub = "Os downloads concluídos vão para lá",
        keywords = listOf("pasta", "download", "armazenamento", "SAF"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_MAIN,
    ),
    // Torrent — espaço temporário (Lote 18, v1.28.0).
    SettingOptionEntry(
        title = "Teto do espaço temporário do torrent",
        sub = "Limite de 32–256 MB + descartar o que já foi assistido",
        keywords = listOf("torrent", "espaço", "temporário", "teto", "descarte", "cache"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_TORRENT,
    ),
    // ── Gravações (Lote 8, v1.18.0) ─────────────────────────────────────
    SettingOptionEntry(
        title = "Gravações",
        sub = "Gravar programas ao vivo e ver as gravações",
        keywords = listOf("gravações", "gravar", "ao vivo", "programa", "record"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    SettingOptionEntry(
        title = "Pasta de gravações",
        sub = "As gravações concluídas vão para lá",
        keywords = listOf("pasta", "gravações", "armazenamento", "SAF"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    SettingOptionEntry(
        title = "Notificações de gravação",
        sub = "Avisa início, fim e falha da gravação",
        keywords = listOf("notificação", "gravação", "aviso", "início", "fim", "falha"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    SettingOptionEntry(
        title = "Começar a gravar antes",
        sub = "A gravação começa antes do programa",
        keywords = listOf("gravar", "antes", "antecedência", "minutos"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    SettingOptionEntry(
        title = "Continuar gravando depois",
        sub = "A gravação continua depois do fim do programa",
        keywords = listOf("gravar", "depois", "estender", "tolerância", "minutos"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    SettingOptionEntry(
        title = "Manter gravações",
        sub = "Quantas gravações guardar antes de apagar a mais antiga",
        keywords = listOf("manter", "apagar", "antigas", "limite", "retenção"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    SettingOptionEntry(
        title = "Espaço livre para gravar",
        sub = "Avisa quando o espaço ficar abaixo do limite",
        keywords = listOf("espaço", "armazenamento", "livre", "limite", "MB"),
        pageId = SettingsPageIds.Downloads,
        anchor = SettingsAnchors.DL_RECORD,
    ),
    // ── Backup e restauração (sem âncora, exceto o Google Drive) ──────
    SettingOptionEntry(
        title = "Sincronizar com o Google Drive",
        sub = "Subir e baixar o backup na sua conta Google",
        keywords = listOf("drive", "google", "sincronizar", "nuvem", "backup", "sync"),
        pageId = SettingsPageIds.Backup,
        anchor = SettingsAnchors.BACKUP_DRIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Subir backup para o Drive",
        sub = "Envia o backup atual p/ o Google Drive",
        keywords = listOf("drive", "google", "subir", "enviar", "upload", "nuvem"),
        pageId = SettingsPageIds.Backup,
        anchor = SettingsAnchors.BACKUP_DRIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Baixar backup do Drive",
        sub = "Baixa o backup do Drive e mostra o resumo p/ restaurar",
        keywords = listOf("drive", "google", "baixar", "download", "restaurar", "nuvem"),
        pageId = SettingsPageIds.Backup,
        anchor = SettingsAnchors.BACKUP_DRIVE,
        kidsHidden = true,
    ),
    // Lote 23 (v1.33.0): sync contínuo p/ o Drive.
    SettingOptionEntry(
        title = "Sync contínuo",
        sub = "Sobe o backup p/ o Drive sozinho a cada 6 h e na abertura",
        keywords = listOf("sync contínuo", "automático", "drive", "nuvem", "sincronizar"),
        pageId = SettingsPageIds.Backup,
        anchor = SettingsAnchors.BACKUP_DRIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Client ID do Google Drive",
        sub = "Client ID OAuth do seu projeto no Google Cloud",
        keywords = listOf("drive", "google", "client id", "oauth", "configurar", "cloud"),
        pageId = SettingsPageIds.Backup,
        anchor = SettingsAnchors.BACKUP_DRIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Conectar conta Google",
        sub = "Login com a conta que vai guardar o backup",
        keywords = listOf("drive", "google", "conectar", "conta", "login"),
        pageId = SettingsPageIds.Backup,
        anchor = SettingsAnchors.BACKUP_DRIVE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Incluir chaves e sessões",
        sub = "O backup inclui senhas e sessões",
        keywords = listOf("chaves", "sessões", "senhas", "incluir", "segredos"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Abrir servidor Wi-Fi (QR e PIN)",
        sub = "Mostra IP, porta, QR e PIN",
        keywords = listOf("wi-fi", "wifi", "servidor", "QR", "PIN", "transferir"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Trazer de outro aparelho pelo Wi-Fi",
        sub = "Digite o IP e PIN do outro aparelho",
        keywords = listOf("trazer", "receber", "wi-fi", "importar"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Enviar para outro aparelho pelo Wi-Fi",
        sub = "Envia o perfil atual com PIN",
        keywords = listOf("enviar", "wi-fi", "exportar"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Exportar arquivo (.json)",
        sub = "Salva na pasta Downloads",
        keywords = listOf("exportar", "json", "arquivo", "salvar"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Importar arquivo (.json)",
        sub = "Escolher um arquivo de backup salvo",
        keywords = listOf("importar", "json", "arquivo", "restaurar"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Colar backup JSON",
        sub = "Cole o texto JSON",
        keywords = listOf("colar", "json", "texto", "restaurar"),
        pageId = SettingsPageIds.Backup,
        kidsHidden = true,
    ),
    // ── Trackers e Sincronização (sem âncora: abre a página no topo) ────
    SettingOptionEntry(
        title = "Client ID do Simkl",
        sub = "API Key do seu app Simkl",
        keywords = listOf("simkl", "client id", "api key", "chave"),
        pageId = SettingsPageIds.Trackers,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Conectar ao Simkl",
        sub = "Login por PIN e QR (device flow)",
        keywords = listOf("simkl", "conectar", "pin", "qr", "login"),
        pageId = SettingsPageIds.Trackers,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Desconectar do Simkl",
        sub = "Apaga só a sessão deste perfil",
        keywords = listOf("simkl", "desconectar", "sair", "logout"),
        pageId = SettingsPageIds.Trackers,
        kidsHidden = true,
    ),
    // Lote 23 (v1.33.0): sync de 2 vias do Simkl.
    SettingOptionEntry(
        title = "Sincronização de 2 vias do Simkl",
        sub = "Puxar assistidos e Minha Lista do Simkl",
        keywords = listOf("simkl", "sincronizar", "sync", "2 vias", "puxar", "assistidos"),
        pageId = SettingsPageIds.Trackers,
        kidsHidden = true,
    ),
    // Lote 25 (v1.35.0) — Frente D, item 14: sincronia contínua do histórico.
    SettingOptionEntry(
        title = "Sincronia contínua do histórico",
        sub = "Trakt e Simkl sempre atualizados — em Contas",
        keywords = listOf("sincronia contínua", "histórico", "sync", "trakt", "simkl", "contínuo"),
        pageId = SettingsPageIds.Integracoes,
        anchor = SettingsAnchors.INTEGR_ACCOUNTS,
        kidsHidden = true,
    ),
    // ── VPN (Lote 10, v1.20.0) ────────────────────────────────────────────
    SettingOptionEntry(
        title = "Importar perfil VPN",
        sub = "Arquivo .conf (WireGuard) ou .ovpn (OpenVPN) do seu provedor",
        keywords = listOf("vpn", "wireguard", "openvpn", "conf", "ovpn", "importar", "perfil", "túnel"),
        pageId = SettingsPageIds.Vpn,
        anchor = SettingsAnchors.VPN_IMPORT,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Perfis de VPN",
        sub = "Renomear, trocar o escopo e remover",
        keywords = listOf("vpn", "perfis", "renomear", "remover", "wireguard", "openvpn"),
        pageId = SettingsPageIds.Vpn,
        anchor = SettingsAnchors.VPN_PROFILES,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Escopo da VPN",
        sub = "Só o AOTV ou todo o aparelho",
        keywords = listOf("vpn", "escopo", "aparelho", "só o app", "todo o aparelho"),
        pageId = SettingsPageIds.Vpn,
        anchor = SettingsAnchors.VPN_SCOPE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Status da VPN",
        sub = "Desconectado — a conexão depende do motor nativo",
        keywords = listOf("vpn", "status", "conectar", "desconectar", "desconectado"),
        pageId = SettingsPageIds.Vpn,
        anchor = SettingsAnchors.VPN_STATUS,
        kidsHidden = true,
    ),
    // ── Watch Party (Lote 10, v1.20.0) ────────────────────────────────────
    SettingOptionEntry(
        title = "Criar Watch Party",
        sub = "Gera um código para assistir junto",
        keywords = listOf("watch party", "criar", "código", "sessão", "assistir junto", "grupo"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_CREATE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Entrar numa Watch Party",
        sub = "Entre com o código do convite",
        keywords = listOf("watch party", "entrar", "código", "convite", "sessão", "participar"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_JOIN,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Participantes da Watch Party",
        sub = "Quem está na sessão",
        keywords = listOf("watch party", "participantes", "quem está", "sessão", "grupo"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_MEMBERS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Sincronização da Watch Party",
        sub = "Play, pause e seek sincronizados via WebRTC",
        keywords = listOf("watch party", "sincronização", "sincronizar", "play", "pause", "seek"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_SYNC,
        kidsHidden = true,
    ),
    // ── Watch Party real (Lote 19, v1.29.0, item 1) ───────────────────────
    SettingOptionEntry(
        title = "Voz da Watch Party",
        sub = "Microfone e anel de quem fala",
        keywords = listOf("watch party", "voz", "microfone", "falar", "anel"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_VOICE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Câmera da Watch Party",
        sub = "Sua câmera na TV",
        keywords = listOf("watch party", "câmera", "camera", "vídeo", "video"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_CAMERA,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Relay (TURN) da Watch Party",
        sub = "Só quando o P2P direto falha",
        keywords = listOf("watch party", "relay", "turn", "conexão", "p2p", "nat"),
        pageId = SettingsPageIds.WatchParty,
        anchor = SettingsAnchors.PARTY_RELAY,
        kidsHidden = true,
    ),
    // ── Social Hub (Lote 19, v1.29.0, item 2) ────────────────────────────
    // Sem servidor: amigos são lista local, o "quer assistir" é anotado à
    // mão e a atividade só aparece dentro de uma Watch Party ativa.
    SettingOptionEntry(
        title = "Social Hub",
        sub = "Amigos, film night e couch match — tudo local, sem servidor",
        keywords = listOf("social", "amigos", "film night", "couch match", "assistir junto", "grupo"),
        pageId = SettingsPageIds.SocialHub,
        anchor = SettingsAnchors.SOCIAL_ABOUT,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Amigos do Social Hub",
        sub = "Lista local de amigos, só nomes",
        keywords = listOf("social", "amigos", "adicionar amigo", "lista de amigos", "remover amigo"),
        pageId = SettingsPageIds.SocialHub,
        anchor = SettingsAnchors.SOCIAL_FRIENDS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "O que meus amigos querem assistir",
        sub = "Títulos anotados à mão p/ cada amigo",
        keywords = listOf("social", "quer assistir", "amigos", "anotar", "lista", "títulos"),
        pageId = SettingsPageIds.SocialHub,
        anchor = SettingsAnchors.SOCIAL_WANTS,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Couch match",
        sub = "Títulos que 2 ou mais amigos querem ver — assistir junto em 1 toque",
        keywords = listOf("social", "couch match", "match", "amigos", "querem ver", "assistir junto", "combinar"),
        pageId = SettingsPageIds.SocialHub,
        anchor = SettingsAnchors.SOCIAL_MATCH,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Film night",
        sub = "Agende a noite do filme com os amigos",
        keywords = listOf("social", "film night", "noite de filme", "agendar", "amigos", "data", "hora"),
        pageId = SettingsPageIds.SocialHub,
        anchor = SettingsAnchors.SOCIAL_FILM_NIGHT,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Assistir junto com amigos",
        sub = "Cria a sessão da Watch Party a partir do match ou da film night",
        keywords = listOf("social", "assistir junto", "watch party", "sessão", "código", "convite", "amigos"),
        pageId = SettingsPageIds.SocialHub,
        anchor = SettingsAnchors.SOCIAL_SESSION,
        kidsHidden = true,
    ),
    // ── Diagnóstico (Lote 11, v1.21.0) ────────────────────────────────────
    SettingOptionEntry(
        title = "Diagnóstico",
        sub = "Aparelho, addons, fontes e quadros — tudo medido",
        keywords = listOf("diagnóstico", "diagnostico", "aparelho", "memória", "quadros", "addons", "fontes", "tela"),
        pageId = SettingsPageIds.Diagnostico,
        anchor = SettingsAnchors.DIAG_DEVICE,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Ajuste inicial",
        sub = "Sugestão de buffer e rede a partir do aparelho medido",
        keywords = listOf("ajuste", "sugestão", "buffer", "rede", "primeira abertura", "auto-ajuste"),
        pageId = SettingsPageIds.Diagnostico,
        anchor = SettingsAnchors.DIAG_SUGGESTION,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Último crash",
        sub = "Registro do último fechamento do app, p/ confirmar a causa",
        keywords = listOf("crash", "erro", "fechou", "travou", "registro", "stack trace"),
        pageId = SettingsPageIds.Diagnostico,
        anchor = SettingsAnchors.DIAG_CRASH,
        kidsHidden = true,
    ),
    // Lote mpv 3 (v1.38.0): os passos do mpv (create/init/attachSurface/
    // loadfile/destroy) — o crash nativo do libmpv não deixa diagnóstico JVM.
    SettingOptionEntry(
        title = "Últimos passos do mpv",
        sub = "O que o Libmpv fez antes do app fechar — p/ investigar crash na 1ª abertura de fonte",
        keywords = listOf("mpv", "libmpv", "passos", "crash", "nativo", "fechou", "abertura", "init"),
        pageId = SettingsPageIds.Diagnostico,
        anchor = SettingsAnchors.DIAG_MPV_STEPS,
        kidsHidden = true,
    ),
    // Lote mpv 4 (v1.39.0) — Frente 3: propriedades observadas do mpv
    // (time-pos, duration, pause, paused-for-cache, core-idle).
    SettingOptionEntry(
        title = "Propriedades observadas do mpv",
        sub = "Posição, duração, pausa e estado do núcleo — medidos enquanto o Libmpv toca",
        keywords = listOf("mpv", "libmpv", "propriedades", "time-pos", "duração", "pausa", "observadas"),
        pageId = SettingsPageIds.Diagnostico,
        anchor = SettingsAnchors.DIAG_MPV_PROPS,
        kidsHidden = true,
    ),
    // Lote 25 (v1.35.0) — Frente B: "Report a problem" + saúde dos addons.
    SettingOptionEntry(
        title = "Reportar um problema",
        sub = "Relato pré-preenchido com versão e aparelho, sem chaves, + teste de velocidade",
        keywords = listOf("reportar", "problema", "bug", "issue", "erro", "relato", "velocidade", "teste de velocidade", "compartilhar", "copiar"),
        pageId = SettingsPageIds.Diagnostico,
        anchor = SettingsAnchors.DIAG_REPORT,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Saúde dos addons",
        sub = "Latência de cada addon, o que está lento, Addon Studio e Addon Lab",
        keywords = listOf("saúde", "saude", "addon", "latência", "latencia", "lento", "manifest", "studio", "lab", "log", "repetir"),
        pageId = SettingsPageIds.Conteudo,
        anchor = SettingsAnchors.CONTENT_ADDONS,
        kidsHidden = true,
    ),
    // ── Controle remoto e TV (Lote 22, v1.32.0) ──────────────────────────
    SettingOptionEntry(
        title = "Servidor do controle remoto",
        sub = "QR p/ parear o celular com a TV, log ao vivo e botões",
        keywords = listOf("controle remoto", "QR", "pareamento", "servidor", "log", "botões"),
        pageId = SettingsPageIds.RemoteTv,
        anchor = SettingsAnchors.REMOTE_SERVER,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Enviar chaves e addons p/ a TV",
        sub = "Cola as chaves de contas e o link do addon no celular e envia tudo de uma vez",
        keywords = listOf("chaves", "TMDB", "Trakt", "Simkl", "MDBList", "TVDB", "SubDL", "addon", "instalar", "pareamento"),
        pageId = SettingsPageIds.RemoteTv,
        anchor = SettingsAnchors.REMOTE_CLIENT,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Enviar p/ a TV",
        sub = "Chromecast, DLNA e AirPlay — sem depender do Google Cast SDK",
        keywords = listOf("Send to TV", "Chromecast", "DLNA", "AirPlay", "transmitir", "TV", "DIAL"),
        pageId = SettingsPageIds.RemoteTv,
        anchor = SettingsAnchors.REMOTE_TV,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Watch Next no launcher",
        sub = "O Continuar assistindo vira canal de recomendações na tela inicial da Google TV",
        keywords = listOf("Watch Next", "launcher", "Continuar assistindo", "Google TV", "canal", "recomendações"),
        pageId = SettingsPageIds.RemoteTv,
        anchor = SettingsAnchors.REMOTE_WATCHNEXT,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Gamepad",
        sub = "Presets por marca e mapeamento manual dos botões",
        keywords = listOf("gamepad", "controle", "joystick", "botões", "mapear", "Xbox", "PlayStation", "8BitDo"),
        pageId = SettingsPageIds.RemoteTv,
        anchor = SettingsAnchors.REMOTE_GAMEPAD,
        kidsHidden = true,
    ),
    SettingOptionEntry(
        title = "Player externo",
        sub = "Escolher o player por título ou por tipo e salvar o progresso ao voltar",
        keywords = listOf("player externo", "VLC", "MX Player", "progresso", "Continuar assistindo", "padrão"),
        pageId = SettingsPageIds.RemoteTv,
        anchor = SettingsAnchors.REMOTE_EXTERNAL,
        kidsHidden = true,
    ),
)

/**
 * Busca profunda: casa a consulta com título, subtítulo e palavras-chave de
 * cada opção (ignora maiúsculas/minúsculas). No perfil infantil, as opções
 * marcadas [SettingOptionEntry.kidsHidden] somem, igual às páginas.
 */
fun searchSettingOptions(query: String, kids: Boolean): List<SettingOptionEntry> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return settingsOptionIndex.filter { entry ->
        (!kids || !entry.kidsHidden) &&
            (entry.title.contains(q, ignoreCase = true) ||
                entry.sub.contains(q, ignoreCase = true) ||
                entry.keywords.any { it.contains(q, ignoreCase = true) })
    }
}
