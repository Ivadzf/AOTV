package com.aotv.app.ui.settings

/**
 * Índice da busca profunda das Configurações (Lote 6, v1.15.0 — redesign).
 *
 * O hub antes só filtrava as PÁGINAS (título/sub/keywords). Este índice lista
 * cada OPÇÃO dentro das páginas (título, subtítulo e palavras-chave), para a
 * busca achar a opção e abrir a página certa — com rolagem e destaque até a
 * seção quando a página suporta âncora ([SettingOptionEntry.anchor]).
 *
 * Páginas sem âncora (Backup e Trackers, que têm scaffold próprio):
 * o resultado abre a página no topo; a opção continua listada.
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
    // Página de detalhes
    const val DETAIL_BG = "detalhe-fundo"
    const val DETAIL_ACTIONS = "detalhe-acoes"
    const val DETAIL_ORIGINAL = "detalhe-titulo-original"
    const val DETAIL_EPISODES = "detalhe-episodios"
    // Streams
    const val STREAMS_SHEET = "streams-folha"
    const val STREAMS_BADGES = "streams-emblemas"
    const val STREAMS_PINNED = "streams-fixadas"
    // Conteúdo & Descoberta
    const val CONTENT_ADDONS = "conteudo-addons"
    const val CONTENT_STATUS = "conteudo-status"
    const val CONTENT_LIBRARY = "conteudo-biblioteca"
    // Integrações
    const val INTEGR_ACCOUNTS = "integracoes-contas"
    const val INTEGR_LIVE = "integracoes-tv"
    // Acompanhamento
    const val TRACK_PROFILE = "acompanhamento-perfil"
    const val TRACK_TMDB = "acompanhamento-tmdb"
    const val TRACK_TRAKT = "acompanhamento-trakt"
    // Aparência
    const val APPEAR_THEME = "aparencia-tema"
    const val APPEAR_CUSTOM = "aparencia-tema-personalizado"
    const val APPEAR_PANELS = "aparencia-paineis"
    const val APPEAR_DEVICE = "aparencia-aparelho"
    // Reprodução
    const val PLAY_MAIN = "reproducao-reproducao"
    const val PLAY_SUBS = "reproducao-legendas"
    const val PLAY_BUTTONS = "reproducao-botoes"
    const val PLAY_TOP = "reproducao-botoes-topo"
    const val PLAY_PLAYER = "reproducao-player"
    const val PLAY_ENGINE = "reproducao-motor"
    const val PLAY_SKIP = "reproducao-pular"
    // Downloads
    const val DL_DATA = "downloads-dados"
    const val DL_LIMIT = "downloads-limite"
    const val DL_MAIN = "downloads-downloads"
    const val DL_COUNTED = "downloads-conta"
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
        sub = "Etiqueta e selos 4K, Dolby, HDR e DUB",
        keywords = listOf("selos", "etiqueta", "4K", "Dolby", "HDR", "DUB", "emblema"),
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
        title = "Estilo do card de episódio",
        sub = "Horizontal com arte ou Empilhado com texto",
        keywords = listOf("card", "episódio", "estilo"),
        pageId = SettingsPageIds.Detalhe,
        anchor = SettingsAnchors.DETAIL_EPISODES,
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
    SettingOptionEntry(
        title = "Fontes fixadas",
        sub = "As fixadas aparecem primeiro na folha de fontes",
        keywords = listOf("fixadas", "pin", "ordem", "fontes"),
        pageId = SettingsPageIds.Streams,
        anchor = SettingsAnchors.STREAMS_PINNED,
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
        sub = "Real-Debrid, TorBox ou Premiumize",
        keywords = listOf("debrid", "real-debrid", "torbox", "premiumize", "torrent"),
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
    // ── Acompanhamento ───────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Trocar de perfil",
        sub = "Perfil ativo: trocar ou editar perfis",
        keywords = listOf("perfil", "trocar", "editar", "quem está assistindo"),
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
    // ── Reprodução ───────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Áudio preferido",
        sub = "Qualquer, Dublado ou Legendado",
        keywords = listOf("áudio", "dublado", "legendado", "preferência"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_MAIN,
    ),
    SettingOptionEntry(
        title = "Qualidade máxima",
        sub = "Sem limite, 4K, 1080p ou 720p",
        keywords = listOf("qualidade", "resolução", "4k", "1080p", "720p"),
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
    SettingOptionEntry(
        title = "Troca automática de fonte",
        sub = "Se a fonte travar, troca sozinha por outra",
        keywords = listOf("troca automática", "fonte", "travar", "falhar"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_PLAYER,
    ),
    SettingOptionEntry(
        title = "Tempo para trocar de fonte",
        sub = "8 s a 60 s",
        keywords = listOf("tempo", "troca", "fonte", "timeout"),
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
    SettingOptionEntry(
        title = "Player padrão",
        sub = "ExoPlayer interno ou player externo",
        keywords = listOf("player", "externo", "vlc", "mx player", "exoplayer"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_ENGINE,
    ),
    SettingOptionEntry(
        title = "Decoder de vídeo",
        sub = "Hardware (HW) ou Hardware com fallback",
        keywords = listOf("decoder", "hardware", "hw", "vídeo"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_ENGINE,
    ),
    SettingOptionEntry(
        title = "Buffer do player",
        sub = "Baixo, Normal ou Alto",
        keywords = listOf("buffer", "carregamento", "travando", "exoplayer"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_ENGINE,
    ),
    SettingOptionEntry(
        title = "Client ID do Anime-Skip",
        sub = "Pular trechos em séries (IntroDB/SkipDB sem chave)",
        keywords = listOf("anime-skip", "pular", "abertura", "encerramento", "intro", "skip", "client id"),
        pageId = SettingsPageIds.Reproducao,
        anchor = SettingsAnchors.PLAY_SKIP,
    ),
    // ── Downloads ────────────────────────────────────────────────────────
    SettingOptionEntry(
        title = "Dados móveis usados",
        sub = "Hoje e este mês (só downloads .mp4 medidos)",
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
    // ── Backup e restauração (sem âncora: abre a página no topo) ────────
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
