package com.aotv.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.vodNavItems
import com.aotv.app.ui.common.DeviceLayout
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.movies.*
import com.aotv.app.ui.settings.SettingsPageScreen
import com.aotv.app.ui.settings.SettingsScreen

private sealed class MoviesRoute(val route: String) {
    object Home     : MoviesRoute("movies_home")
    object Detail   : MoviesRoute("movies_detail/{embedId}/{tmdbId}/{type}") {
        // embedId vazio (item vindo de Minha Lista/histórico, sem addon de origem) quebra o
        // path do Nav (regex de argumento não casa segmento vazio = IllegalArgumentException
        // ao navegar). Ninguém lê embedId de verdade (tmdbId é a chave real em tudo), então o
        // próprio tmdbId serve de placeholder estável.
        fun create(embedId: String, tmdbId: Int, type: MediaType) =
            "movies_detail/${embedId.ifBlank { tmdbId.toString() }}/$tmdbId/${type.name}"
    }
    // Player usa ID numérico simples. A URL real fica no playerCache (evita problema de encoding)
    object Player   : MoviesRoute("movies_player/{playId}") {
        fun create(playId: Int) = "movies_player/$playId"
    }
    object Search   : MoviesRoute("movies_search")
    object Settings : MoviesRoute("movies_settings")
    // Páginas das Configurações (Fase A, Frente 2) — o hub é movies_settings.
    // O `?anchor=` (Lote 6, v1.15.0) é a seção da busca profunda: a página rola
    // até ela e destaca o título. Sem âncora, abre no topo como antes.
    object SettingsPage : MoviesRoute("movies_settings/{page}?anchor={anchor}") {
        fun create(page: String, anchor: String? = null) =
            if (anchor.isNullOrBlank()) "movies_settings/$page"
            else "movies_settings/$page?anchor=${Uri.encode(anchor)}"
    }
    object Addons : MoviesRoute("movies_addons")
    object Plugins : MoviesRoute("movies_plugins")
    object KodiSpike : MoviesRoute("movies_kodi_spike")
    // Gerenciador de addons Kodi (v1.4, Fase 4) — instalar ZIP/URL,
    // listar, ligar/desligar, settings, diagnóstico, remover.
    object KodiAddons : MoviesRoute("movies_kodi_addons")
    // Navegação de addon Kodi (v1.2, Fase 2) — addonId é o id do addon instalado.
    // v1.5 — busca automática (folha de fontes): `search` opcional leva o título
    // p/ buscar direto no addon; sem ele, abre na raiz como antes.
    object KodiBrowser : MoviesRoute("movies_kodi_browser/{addonId}?search={search}") {
        fun create(addonId: String, search: String? = null) =
            "movies_kodi_browser/$addonId" + if (search.isNullOrBlank()) "" else "?search=" + Uri.encode(search)
    }
    object AddonStore : MoviesRoute("movies_addon_store")
    object MinhaLista : MoviesRoute("movies_minha_lista")
    object Descobrir : MoviesRoute("movies_descobrir")
    object Downloads : MoviesRoute("movies_downloads")
    object Profiles : MoviesRoute("movies_profiles")
    object HomeCustomize : MoviesRoute("movies_home_customize")
    object Debrid : MoviesRoute("movies_debrid")
    // Servidores de mídia (Lote 10, v1.20.0) — Jellyfin, Emby e Plex do usuário.
    object MediaServers : MoviesRoute("movies_media_servers")
    object Accounts : MoviesRoute("movies_accounts")
    object Live : MoviesRoute("movies_live")
    object LiveSources : MoviesRoute("movies_live_sources")
    // On Demand Xtream (Lote 9, v1.19.0) — o hub da Live TV (Frente 3) linka esta rota.
    // Acréscimo v1.26.0: deep link da Início — ?tab={tab}&itemId={itemId} abre a
    // aba e o detalhe do item; sem eles, abre no topo como antes.
    object LiveOnDemand : MoviesRoute("live_ondemand?tab={tab}&itemId={itemId}") {
        fun create(tab: String = "", itemId: String = "") =
            "live_ondemand" +
                if (tab.isBlank() && itemId.isBlank()) ""
                else "?tab=${android.net.Uri.encode(tab)}&itemId=${android.net.Uri.encode(itemId)}"
    }
    // Live TV parte 2 (Lote 12, v1.22.0 — Frente 1): Multiview, guia em
    // rota própria (aberta do player) e gerenciador de canais.
    object LiveMultiview : MoviesRoute("live_multiview")
    object LiveGuide : MoviesRoute("movies_live_guide/{channelId}") {
        fun create(channelId: String) = "movies_live_guide/" + android.net.Uri.encode(channelId)
    }
    object LiveChannelsManage : MoviesRoute("live_channels_manage")
    // Esportes parte 2 (Lote 13, v1.23.0 — Frente 1): tabelas de ligas +
    // League Networks e a página do jogo (placar, gols, estatísticas,
    // escalações). A página do jogo leva liga + id do evento da ESPN.
    object SportsTables : MoviesRoute("live_sports_tables")
    object SportsGame : MoviesRoute("live_sports_game/{league}/{eventId}") {
        fun create(league: String, eventId: String) =
            "live_sports_game/" + android.net.Uri.encode(league) + "/" + android.net.Uri.encode(eventId)
    }
    // Aba de Esportes (Lote 15, v1.25.0 — Bloco 1): 6ª aba da pílula, SÓ no
    // celular. Na TV, esportes ficam dentro do hub do Ao vivo, como hoje
    // (a rota não entra no menu da TV).
    object SportsTab : MoviesRoute("movies_sports_tab")
    // Busca com query pronta (Lote 12, v1.22.0 — Frente 1): o painel do
    // programa no guia abre a busca com o título ("Buscar nos addons").
    object SearchWithQuery : MoviesRoute("movies_search_query/{query}") {
        fun create(query: String) = "movies_search_query/" + android.net.Uri.encode(query)
    }
    object LivePlayer : MoviesRoute("movies_live_player/{id}?start={start}&stop={stop}") {
        fun create(id: String) = "movies_live_player/" + android.net.Uri.encode(id)
        // Catch-up (v1.17.0): [start]/[stop] em epoch segundos; 0 = ao vivo normal.
        fun create(id: String, start: Long, stop: Long) =
            "movies_live_player/" + android.net.Uri.encode(id) + "?start=$start&stop=$stop"
    }
    object Collections : MoviesRoute("movies_collections")
    object CollectionEdit : MoviesRoute("movies_collection_edit/{collectionId}") {
        fun create(id: String?) = "movies_collection_edit/" + android.net.Uri.encode(id ?: "new")
    }
    object CollectionFolder : MoviesRoute("movies_collection_folder/{collectionId}/{folderId}") {
        fun create(collectionId: String, folderId: String) =
            "movies_collection_folder/" + android.net.Uri.encode(collectionId) + "/" + android.net.Uri.encode(folderId)
    }
}

/** Na TV a tela entra na escala do webOS ([com.aotv.app.ui.tv.TvScaled]). No celular fica igual. */
@Composable
private fun tvScale(isTV: Boolean, content: @Composable () -> Unit) {
    if (isTV) com.aotv.app.ui.tv.TvScaled(content) else content()
}

// Cache compartilhado — MovieItem e PlayerData sobrevivem à serialização do NavController.
// Chave por tipo+tmdbId: filme e série têm IDs do TMDB em espaços separados e podem repetir.
// Sem id do TMDB (item de addon com só o id IMDb), o embedId diferencia os itens.
private val movieCache  = mutableMapOf<String, MovieItem>()
private fun cacheKey(tmdbId: Int, type: MediaType, embedId: String = "") =
    if (tmdbId > 0) "${type.name}:$tmdbId" else "${type.name}:imdb:${embedId.ifBlank { tmdbId.toString() }}"
private typealias MoviePlayerSource = com.aotv.app.ui.movies.MoviePlayerSource
private data class PlayerData(
    val embedUrl: String, val title: String,
    val tmdbId: Int = 0, val type: String = "MOVIE", val posterUrl: String = "",
    val lastEpisode: String = "", val startPositionMs: Long = 0,
    val backdropUrl: String = "",
    // Episódio (séries) — p/ o botão "Próximo episódio" no player
    val season: Int = 0, val episode: Int = 0, val episodeTotal: Int = 0,
    // Todas as fontes do filme/episódio — p/ trocar dentro do player
    val sources: List<MoviePlayerSource> = emptyList(),
    // Diagnóstico do play Kodi (resolveu= + http:) — v1.2.7: vai p/ a tela
    // de erro do player, p/ o Davi mandar o print quando o ExoPlayer não
    // carregar o stream resolvido. Vazio p/ todo o resto (Nuvio, Stremio...).
    val kodiDiag: String = "",
    // Fonte travada (guarda de duração confirmada, v1.21.0): o player abre
    // com userLockedSource e não auto-troca por duração nem por lentidão.
    val lockedSource: Boolean = false,
)
private val playerCache = mutableMapOf<Int, PlayerData>()
private var playerIdCounter = 0

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MoviesNavigation(
    deviceLayout: DeviceLayout = DeviceLayout.PHONE,
    onRestartApp: () -> Unit,
) {
    val isTV = deviceLayout == DeviceLayout.TV
    val inputModeManager = androidx.compose.ui.platform.LocalInputModeManager.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Deep link da Watch Party (Lote 10, v1.20.0): a MainActivity oferece o
    // código (onCreate/onNewIntent) e aqui a navegação pousa na página da
    // festa — a tela lê o código do intent e pré-preenche a entrada.
    val partyCode by com.aotv.app.data.party.PartyDeepLink.code.collectAsStateWithLifecycle()
    LaunchedEffect(partyCode) {
        if (partyCode != null) {
            navController.navigate(
                MoviesRoute.SettingsPage.create(com.aotv.app.ui.settings.SettingsPageIds.WatchParty)
            ) { launchSingleTop = true }
            com.aotv.app.data.party.PartyDeepLink.consume()
        }
    }

    // Deep link stremio:// (Lote 17, v1.27.0): a MainActivity oferece a URL
    // (onCreate/onNewIntent) e aqui a navegação pousa na tela de addons — o
    // AddonsViewModel consome a URL do holder e instala pelo fluxo normal
    // de install(). O holder NÃO é consumido aqui: o ViewModel consome ao
    // receber, p/ não perder a URL se a tela remontar antes do init.
    val addonDeepLinkUrl by com.aotv.app.data.party.AddonDeepLink.url.collectAsStateWithLifecycle()
    LaunchedEffect(addonDeepLinkUrl) {
        if (addonDeepLinkUrl != null) {
            navController.navigate(MoviesRoute.Addons.route) { launchSingleTop = true }
        }
    }

    // Fullscreen: player, detalhe e telas de fluxo (loja, contas, debrid, etc.).
    // Busca e Perfil NÃO são fullscreen (Fase A): a pílula fica visível nas 4 abas.
    // Configurações cobre o hub e as páginas (movies_settings/*).
    val isFullscreen = currentRoute?.startsWith("movies_player") == true ||
        currentRoute?.startsWith("movies_detail") == true ||
        currentRoute?.startsWith("movies_settings") == true ||
        currentRoute == MoviesRoute.AddonStore.route ||
        currentRoute == MoviesRoute.HomeCustomize.route ||
        currentRoute == MoviesRoute.Debrid.route ||
        currentRoute == MoviesRoute.MediaServers.route ||
        currentRoute == MoviesRoute.Accounts.route ||
        currentRoute == MoviesRoute.LiveSources.route ||
        currentRoute == MoviesRoute.LiveOnDemand.route ||
        currentRoute == MoviesRoute.LiveMultiview.route ||
        currentRoute == MoviesRoute.LiveChannelsManage.route ||
        currentRoute == MoviesRoute.SportsTables.route ||
        currentRoute?.startsWith("live_sports_game") == true ||
        currentRoute?.startsWith("movies_live_guide") == true ||
        currentRoute?.startsWith("movies_search_query") == true ||
        currentRoute?.startsWith("movies_live_player") == true
    val showTabletRail = deviceLayout == DeviceLayout.TABLET && !isFullscreen

    fun navigateToPlayer(
        embedUrl: String, title: String,
        tmdbId: Int = 0, type: String = "MOVIE", posterUrl: String = "",
        lastEpisode: String = "", startPositionMs: Long = 0, backdropUrl: String = "",
        season: Int = 0, episode: Int = 0, episodeTotal: Int = 0,
        sources: List<MoviePlayerSource> = emptyList(),
        kodiDiag: String = "",
        lockedSource: Boolean = false,
    ) {
        // Novo play mata o mini player ativo (só existe 1 player por vez — sem
        // isso o áudio minimizado seguiria tocando junto com o novo).
        com.aotv.app.ui.movies.MiniPlayerState.close()
        val playId = ++playerIdCounter
        playerCache[playId] = PlayerData(
            embedUrl, title, tmdbId, type, posterUrl, lastEpisode, startPositionMs, backdropUrl,
            season, episode, episodeTotal, sources, kodiDiag, lockedSource,
        )
        navController.navigate(MoviesRoute.Player.create(playId))
    }

    // Navegação da barra inferior (mantém único topo e restaura estado)
    fun navTab(route: String) = navController.navigate(route) {
        popUpTo(MoviesRoute.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    var menuOpen by remember { mutableStateOf(false) }
    val navHost: @Composable () -> Unit = {
        // Troca de tela de 220 ms com opacidade e deslocamento curto, como o .screen-enter do webOS.
        val reduced = com.aotv.app.ui.common.rememberReducedMotion()
        val dur = if (reduced) 0 else 220
        NavHost(
            navController, startDestination = MoviesRoute.Home.route,
            enterTransition = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(dur)) +
                    androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(dur)) { if (reduced) 0 else 48 }
            },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 150)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(dur)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 150)) },
        ) {

            composable(MoviesRoute.Home.route) {
                // Estável entre recomposições: lambda nova a cada vez recompunha a Início inteira.
                val openDetail: (MovieItem) -> Unit = androidx.compose.runtime.remember(navController) { { item ->
                    // Ao vivo no Continuar (Lote 8, v1.18.0): o card do canal
                    // volta ao player, não ao detalhe — se o programa já
                    // acabou, vai pelo catch-up (o player cai p/ o ao vivo
                    // com aviso quando não há gravação).
                    val live = com.aotv.app.ui.live.LivePlayerViewModel.parseLiveEmbed(item.embedId)
                    if (live != null) {
                        val (channelId, startSec, stopSec) = live
                        if (stopSec > 0 && System.currentTimeMillis() / 1000 >= stopSec) {
                            navController.navigate(MoviesRoute.LivePlayer.create(channelId, startSec, stopSec))
                        } else {
                            navController.navigate(MoviesRoute.LivePlayer.create(channelId))
                        }
                    } else {
                        movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                        navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                    }
                } }
                // TV tem Início própria no desenho do webOS. Celular segue com a dele.
                if (isTV) com.aotv.app.ui.tv.TvHomeScreen(
                    onItemClick = openDetail, navigationOpen = menuOpen,
                    onFolderClick = { c, f -> navController.navigate(MoviesRoute.CollectionFolder.create(c, f)) },
                )
                else MoviesHomeScreen(
                    onItemClick     = openDetail,
                    onPlayLive      = { channelId -> navController.navigate(MoviesRoute.LivePlayer.create(channelId)) },
                    // On Demand do Xtream (acréscimo v1.26.0): pôster da fileira
                    // abre o detalhe no On Demand; "Ver tudo" abre a aba.
                    onVodClick      = { item, series ->
                        navController.navigate(
                            MoviesRoute.LiveOnDemand.create(
                                if (series) com.aotv.app.ui.live.OnDemandViewModel.SERIES
                                else com.aotv.app.ui.live.OnDemandViewModel.MOVIES,
                                item.id,
                            ),
                        )
                    },
                    onOpenOnDemand  = { series ->
                        navController.navigate(
                            MoviesRoute.LiveOnDemand.create(
                                if (series) com.aotv.app.ui.live.OnDemandViewModel.SERIES
                                else com.aotv.app.ui.live.OnDemandViewModel.MOVIES,
                            ),
                        )
                    },
                    onSearchClick   = { navController.navigate(MoviesRoute.Search.route) },
                    onSettingsClick = { navController.navigate(MoviesRoute.Settings.route) },
                    onLibraryClick  = { navController.navigate(MoviesRoute.MinhaLista.route) },
                    onDiscoverClick = { navController.navigate(MoviesRoute.Descobrir.route) },
                    onCustomize     = { navController.navigate(MoviesRoute.HomeCustomize.route) },
                    onFolderClick   = { c, f -> navController.navigate(MoviesRoute.CollectionFolder.create(c, f)) },
                    // Modo aleatório (Lote 2): episódio sorteado abre DIRETO no
                    // player, sem folha de fontes (mesmo caminho do detalhe).
                    onPlayEpisode   = { item, season, episode, epTotal ->
                        navigateToPlayer(
                            "", item.title, tmdbId = item.tmdbId, type = "SERIES",
                            posterUrl = item.posterUrl, lastEpisode = "T$season:E$episode",
                            backdropUrl = item.backdropUrl,
                            season = season, episode = episode, episodeTotal = epTotal,
                        )
                    },
                )
            }

            // Coleções no formato do Nuvio (celular primeiro).
            composable(MoviesRoute.Collections.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.collections.CollectionsScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(MoviesRoute.CollectionEdit.create(id)) },
                    )
                }
            }
            composable(
                MoviesRoute.CollectionEdit.route,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
            ) {
                tvScale(isTV) { com.aotv.app.ui.collections.CollectionEditorScreen(onBack = { navController.popBackStack() }) }
            }
            composable(
                MoviesRoute.CollectionFolder.route,
                arguments = listOf(
                    navArgument("collectionId") { type = NavType.StringType },
                    navArgument("folderId") { type = NavType.StringType },
                ),
            ) {
                tvScale(isTV) { com.aotv.app.ui.collections.FolderScreen(
                    onBack = { navController.popBackStack() },
                    onItemClick = { item ->
                        movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                        navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                    },
                ) }
            }

            composable(
                MoviesRoute.Detail.route,
                arguments = listOf(
                    navArgument("embedId") { type = NavType.StringType },
                    navArgument("tmdbId")  { type = NavType.IntType },
                    navArgument("type")    { type = NavType.StringType },
                ),
            ) { entry ->
                val embedId   = entry.arguments?.getString("embedId") ?: return@composable
                val tmdbId    = entry.arguments?.getInt("tmdbId") ?: 0
                val mediaType = runCatching {
                    MediaType.valueOf(entry.arguments?.getString("type") ?: "MOVIE")
                }.getOrDefault(MediaType.MOVIE)
                val stub = movieCache[cacheKey(tmdbId, mediaType, embedId)] ?: MovieItem(
                    tmdbId = tmdbId, embedId = embedId, title = "",
                    overview = "", posterUrl = "", backdropUrl = "",
                    year = "", rating = 0.0, type = mediaType,
                )
                val detailVm: com.aotv.app.ui.movies.MovieDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                // Episódio abre o player sem URL. O player busca as fontes do episódio nos addons.
                val onPlayEpisodeCb: (Int, Int, Int, Int) -> Unit = { tid, season, episode, epTotal ->
                    detailVm.markEpisodeWatched(season, episode)
                    navigateToPlayer(
                        "", stub.title, tmdbId = tid, type = "SERIES",
                        posterUrl = stub.posterUrl, lastEpisode = "T$season:E$episode", backdropUrl = stub.backdropUrl,
                        season = season, episode = episode, episodeTotal = epTotal,
                    )
                }
                val onPlayDirectCb: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit = { url, startPos, lastEp, sources, season, episode, epTotal ->
                    navigateToPlayer(
                        url, stub.title, tmdbId = stub.tmdbId, type = stub.type.name,
                        posterUrl = stub.posterUrl, lastEpisode = lastEp, startPositionMs = startPos, backdropUrl = stub.backdropUrl,
                        season = season, episode = episode, episodeTotal = epTotal,
                        sources = sources,
                    )
                }
                // Guarda de duração confirmada (v1.21.0): o play sai com a fonte
                // travada, p/ o player não auto-trocar por duração — o usuário
                // já viu o aviso e mandou tocar mesmo assim. Só o detalhe do
                // celular passa este callback; a TV usa o normal.
                val onPlayDirectLockedCb: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit = { url, startPos, lastEp, sources, season, episode, epTotal ->
                    navigateToPlayer(
                        url, stub.title, tmdbId = stub.tmdbId, type = stub.type.name,
                        posterUrl = stub.posterUrl, lastEpisode = lastEp, startPositionMs = startPos, backdropUrl = stub.backdropUrl,
                        season = season, episode = episode, episodeTotal = epTotal,
                        sources = sources, lockedSource = true,
                    )
                }
                val onBackCb: () -> Unit = { navController.popBackStack() }
                val onRelatedCb: (MovieItem) -> Unit = { rel ->
                    movieCache[cacheKey(rel.tmdbId, rel.type, rel.embedId)] = rel
                    navController.navigate(MoviesRoute.Detail.create(rel.embedId, rel.tmdbId, rel.type))
                }
                KidsGate(tmdbId, mediaType.name, onBack = onBackCb) {
                    if (isTV) com.aotv.app.ui.tv.TvDetailScreen(
                        item = stub,
                        onPlayEpisode = onPlayEpisodeCb,
                        onPlayDirect = onPlayDirectCb,
                        onRelatedClick = onRelatedCb,
                        onOpenKodiAddon = { addonId, searchTitle -> navController.navigate(MoviesRoute.KodiBrowser.create(addonId, searchTitle)) },
                        viewModel = detailVm,
                    ) else MovieDetailScreen(
                        item = stub,
                        onPlayEpisode = onPlayEpisodeCb,
                        onPlayDirect = onPlayDirectCb,
                        onPlayDirectLocked = onPlayDirectLockedCb,
                        onBack = onBackCb,
                        onRelatedClick = onRelatedCb,
                        onOpenKodiAddon = { addonId, searchTitle -> navController.navigate(MoviesRoute.KodiBrowser.create(addonId, searchTitle)) },
                        viewModel = detailVm,
                    )
                }
            }

            composable(
                MoviesRoute.Player.route,
                arguments = listOf(navArgument("playId") { type = NavType.IntType }),
            ) { entry ->
                val playId = entry.arguments?.getInt("playId") ?: -1
                val data   = playerCache[playId]
                if (data == null) {
                    Box(
                        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Erro: playId=$playId cache=${playerCache.keys}", color = androidx.compose.ui.graphics.Color.White)
                        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomCenter)) {
                            Text("Voltar", color = com.aotv.app.ui.theme.AccentFocus)
                        }
                    }
                    return@composable
                }
                KidsGate(data.tmdbId, data.type, onBack = { navController.popBackStack() }) {
                MoviePlayerScreen(
                    embedUrl    = data.embedUrl,
                    title       = data.title,
                    tmdbId      = data.tmdbId,
                    type        = data.type,
                    posterUrl   = data.posterUrl,
                    lastEpisode = data.lastEpisode,
                    startPositionMs = data.startPositionMs,
                    backdropUrl = data.backdropUrl,
                    season      = data.season,
                    episode     = data.episode,
                    episodeTotal = data.episodeTotal,
                    sources     = data.sources,
                    kodiDiag    = data.kodiDiag,
                    startLocked = data.lockedSource,
                    television = isTV,
                    onBack      = { navController.popBackStack() },
                    // Mini player (Fase C): expandir reabre ESTA sessão (mesmo
                    // ExoPlayer, mesma posição), em vez de criar outro player.
                    playId = playId,
                    existingSession = com.aotv.app.ui.movies.MiniPlayerState.sessionFor(playId),
                )
                }
            }

            composable(MoviesRoute.Search.route) {
                val onSearchItem: (MovieItem) -> Unit = { item ->
                    movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                    navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                }
                tvScale(isTV) {
                    MediaSearchScreen(
                        onItemClick = onSearchItem,
                        onBack = { navController.popBackStack() },
                        // Lote 18 item 3 — magnet/link colado toca direto no player.
                        onPlayUrl = { url, title -> navigateToPlayer(url, title, type = "MOVIE") },
                    )
                }
            }

            composable(MoviesRoute.Descobrir.route) {
                val openItem: (MovieItem) -> Unit = { item ->
                    movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                    navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                }
                if (isTV) com.aotv.app.ui.tv.TvDiscoverScreen(onItemClick = openItem)
                else DiscoverScreen(
                        onItemClick = openItem,
                        onBack = { navController.popBackStack() },
                        columns = 3,
                    )
            }

            composable(MoviesRoute.MinhaLista.route) {
                val onListItem: (MovieItem) -> Unit = { item ->
                    movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                    navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                }
                if (isTV) com.aotv.app.ui.tv.TvMyListScreen(onItemClick = { onListItem(movieCache[cacheKey(it.tmdbId, it.type, it.embedId)] ?: it) })
                else MinhaListaScreen(
                    onItemClick = onListItem,
                    onDownloadsClick = { navController.navigate(MoviesRoute.Downloads.route) },
                    onMediaServersClick = { navController.navigate(MoviesRoute.MediaServers.route) },
                    // Modo aleatório (Lote 2): mesmo caminho direto ao player da Início.
                    onPlayEpisode = { item, season, episode, epTotal ->
                        navigateToPlayer(
                            "", item.title, tmdbId = item.tmdbId, type = "SERIES",
                            posterUrl = item.posterUrl, lastEpisode = "T$season:E$episode",
                            backdropUrl = item.backdropUrl,
                            season = season, episode = episode, episodeTotal = epTotal,
                        )
                    },
                )
            }

            // Hub das Configurações (Fase A, Frente 2) — as páginas são movies_settings/{page}.
            composable(MoviesRoute.Settings.route) {
                tvScale(isTV) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPage = { navController.navigate(MoviesRoute.SettingsPage.create(it)) },
                        onOpenProfiles = { navController.navigate(MoviesRoute.Profiles.route) },
                        onOpenOption = { page, anchor ->
                            navController.navigate(MoviesRoute.SettingsPage.create(page, anchor))
                        },
                    )
                }
            }

            // Uma página das Configurações (Frente 2) — `page` é um dos ids de SettingsPageIds.
            composable(
                MoviesRoute.SettingsPage.route,
                arguments = listOf(
                    navArgument("page") { type = NavType.StringType },
                    navArgument("anchor") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStackEntry ->
                tvScale(isTV) {
                    SettingsPageScreen(
                        pageId = backStackEntry.arguments?.getString("page") ?: "",
                        highlightAnchor = backStackEntry.arguments?.getString("anchor"),
                        onBack = { navController.popBackStack() },
                        onPlatformChanged = onRestartApp,
                        onOpenAddons = { navController.navigate(MoviesRoute.Addons.route) },
                        onOpenPlugins = { navController.navigate(MoviesRoute.Plugins.route) },
                        onOpenKodiAddons = { navController.navigate(MoviesRoute.KodiAddons.route) },
                        onOpenProfiles = { navController.navigate(MoviesRoute.Profiles.route) },
                        onOpenHomeCustomize = { navController.navigate(MoviesRoute.HomeCustomize.route) },
                        onOpenDebrid = { navController.navigate(MoviesRoute.Debrid.route) },
                        onOpenMediaServers = { navController.navigate(MoviesRoute.MediaServers.route) },
                        onOpenAccounts = { navController.navigate(MoviesRoute.Accounts.route) },
                        onOpenLive = { navController.navigate(MoviesRoute.LiveSources.route) },
                        // Lote 12 (v1.22.0 — Frente 1): gerenciar canais, a
                        // partir de Integrações → Multiview e canais.
                        onOpenLiveChannels = { navController.navigate(MoviesRoute.LiveChannelsManage.route) },
                        onOpenCollections = { navController.navigate(MoviesRoute.Collections.route) },
                    )
                }
            }

            // Quem está assistindo. Escolher outro perfil remonta a navegação (key no MainActivity).
            composable(MoviesRoute.Profiles.route) {
                tvScale(isTV) { com.aotv.app.ui.settings.ProfilesScreen(
                    onPicked = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(MoviesRoute.Settings.route) },
                ) }
            }

            // Gerenciador de addons (celular) — instala por manifest URL, ordena e ativa/desativa.
            composable(MoviesRoute.Addons.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.settings.AddonManagerScreen(
                        onBack = { navController.popBackStack() },
                        onExplore = { navController.navigate(MoviesRoute.AddonStore.route) },
                        onCustomizeHome = { navController.navigate(MoviesRoute.HomeCustomize.route) },
                    )
                }
            }

            // Plugins do Nuvio (scrapers JS) — instala por manifest URL, liga/desliga e configura.
            composable(MoviesRoute.Plugins.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.settings.PluginsScreen(
                        onBack = { navController.popBackStack() },
                        onAddons = { navController.navigate(MoviesRoute.Addons.route) },
                        onLive = { navController.navigate(MoviesRoute.LiveSources.route) },
                    )
                }
            }

            // Spike Kodi (Fase 0, v1.1) — instala o ZIP do Vulgata por SAF e
            // testa se o .so dele carrega sob o Python do Chaquopy.
            // A tela de gerenciamento (Fase 4) é a rota KodiAddons; o spike
            // continua acessível pelo diagnóstico dela.
            composable(MoviesRoute.KodiSpike.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.settings.KodiSpikeScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVulgata = {
                            navController.navigate(MoviesRoute.KodiBrowser.create("plugin.video.vulgata"))
                        },
                    )
                }
            }

            // Addons Kodi (v1.4, Fase 4) — gerenciar: instalar ZIP/URL,
            // listar, ligar/desligar, settings, diagnóstico, remover.
            composable(MoviesRoute.KodiAddons.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.kodi.KodiAddonsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAddon = { navController.navigate(MoviesRoute.KodiBrowser.create(it)) },
                        onOpenSpike = { navController.navigate(MoviesRoute.KodiSpike.route) },
                    )
                }
            }

            // Navegação de addon Kodi (v1.2, Fase 2) — menu do addon; item
            // jogável resolve e toca no player do app (mesmo ExoPlayer).
            composable(
                MoviesRoute.KodiBrowser.route,
                arguments = listOf(
                    navArgument("addonId") { type = NavType.StringType },
                    navArgument("search") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                tvScale(isTV) {
                    com.aotv.app.ui.kodi.KodiBrowserScreen(
                        onBack = { navController.popBackStack() },
                        onPlay = { url, title, addonId, diag ->
                            navigateToPlayer(
                                embedUrl = url,
                                title = title,
                                sources = listOf(
                                    MoviePlayerSource(label = title, url = url, addon = addonId),
                                ),
                                kodiDiag = diag,
                            )
                        },
                    )
                }
            }

            // TV ao vivo (listas M3U e contas Xtream, com guia).
            composable(MoviesRoute.Live.route) {
                com.aotv.app.ui.live.LiveTvScreen(
                    onPlay = { id -> navController.navigate(MoviesRoute.LivePlayer.create(id)) },
                    onSources = { navController.navigate(MoviesRoute.LiveSources.route) },
                    onPlayCatchup = { route -> navController.navigate(route) },
                    // Lote 9 (v1.19.0): hub da Live TV — o Calendário vive na
                    // Biblioteca (MinhaListaScreen, que já existe); o
                    // On Demand é a rota "live_ondemand" (Frente 2): sem ela
                    // no grafo, o toque não faz nada (runCatching) em vez de
                    // quebrar a navegação.
                    onOpenCalendar = { navController.navigate(MoviesRoute.MinhaLista.route) },
                    onOpenVod = { runCatching { navController.navigate(com.aotv.app.ui.live.LIVE_ON_DEMAND_ROUTE) } },
                    // Lote 12 (v1.22.0 — Frente 1): Multiview e "Buscar nos
                    // addons" a partir do programa no guia.
                    onOpenMultiview = { navController.navigate(MoviesRoute.LiveMultiview.route) },
                    onSearchTitle = { title -> navController.navigate(MoviesRoute.SearchWithQuery.create(title)) },
                    // Lote 13 (v1.23.0 — Frente 1): tabelas de ligas.
                    onOpenTables = { navController.navigate(MoviesRoute.SportsTables.route) },
                )
            }
            // Tabelas de ligas (Lote 13, v1.23.0 — Frente 1): placar do dia,
            // tabela, League Networks e Fix Channel. O jogo abre a página
            // dele; "Assistir" abre o player do canal resolvido.
            composable(MoviesRoute.SportsTables.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.sports.SportsTablesScreen(
                        onBack = { navController.popBackStack() },
                        onPlayChannel = { id -> navController.navigate(MoviesRoute.LivePlayer.create(id)) },
                        onOpenGame = { league, eventId -> navController.navigate(MoviesRoute.SportsGame.create(league, eventId)) },
                    )
                }
            }
            // Página do jogo (Lote 13, v1.23.0 — Frente 1): placar, gols,
            // estatísticas, escalações e Fix Channel.
            composable(
                MoviesRoute.SportsGame.route,
                arguments = listOf(
                    navArgument("league") { type = NavType.StringType },
                    navArgument("eventId") { type = NavType.StringType },
                ),
            ) {
                tvScale(isTV) {
                    com.aotv.app.ui.sports.SportsGameScreen(
                        onBack = { navController.popBackStack() },
                        onPlayChannel = { id -> navController.navigate(MoviesRoute.LivePlayer.create(id)) },
                    )
                }
            }
            // Aba de Esportes (Lote 15, v1.25.0 — Bloco 1): 6ª aba da pílula
            // no celular. NÃO é fullscreen (a pílula fica visível) e não
            // entra no menu da TV. O jogo abre a página dele; "Assistir"
            // abre o player do canal resolvido.
            composable(MoviesRoute.SportsTab.route) {
                com.aotv.app.ui.sports.SportsTabScreen(
                    onOpenGame = { league, eventId -> navController.navigate(MoviesRoute.SportsGame.create(league, eventId)) },
                    onPlayChannel = { id -> navController.navigate(MoviesRoute.LivePlayer.create(id)) },
                )
            }
            composable(MoviesRoute.LiveSources.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.live.LiveSourcesScreen(
                        onBack = { navController.popBackStack() },
                        onManageChannels = { navController.navigate(MoviesRoute.LiveChannelsManage.route) },
                    )
                }
            }
            // Multiview (Lote 12, v1.22.0 — Frente 1): até 4 canais ao mesmo
            // tempo; expandir abre o player cheio do canal.
            composable(MoviesRoute.LiveMultiview.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.live.LiveMultiviewScreen(
                        onBack = { navController.popBackStack() },
                        onExpand = { id -> navController.navigate(MoviesRoute.LivePlayer.create(id)) },
                    )
                }
            }
            // Guia em rota própria (Lote 12, v1.22.0 — Frente 1): aberta do
            // player; voltar retorna ao player do canal (popBackStack).
            composable(
                MoviesRoute.LiveGuide.route,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
            ) { entry ->
                val channelId = entry.arguments?.getString("channelId").orEmpty()
                tvScale(isTV) {
                    com.aotv.app.ui.live.LiveGuideScreen(
                        onPlay = { id -> navController.navigate(MoviesRoute.LivePlayer.create(id)) },
                        onPlayCatchup = { route -> navController.navigate(route) },
                        onBack = { navController.popBackStack() },
                        onSearchTitle = { title -> navController.navigate(MoviesRoute.SearchWithQuery.create(title)) },
                        focusChannelId = channelId,
                    )
                }
            }
            // Gerenciar canais (Lote 12, v1.22.0 — Frente 1): ocultar,
            // reordenar, renomear, Assign EPG, time shift e logo.
            composable(MoviesRoute.LiveChannelsManage.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.live.LiveChannelManagerScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            // Busca com query pronta (Lote 12, v1.22.0 — Frente 1): o painel
            // do programa no guia abre a busca com o título.
            composable(
                MoviesRoute.SearchWithQuery.route,
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
            ) { entry ->
                val onSearchItem: (MovieItem) -> Unit = { item ->
                    movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                    navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                }
                tvScale(isTV) {
                    MediaSearchScreen(
                        onItemClick = onSearchItem,
                        onBack = { navController.popBackStack() },
                        initialQuery = entry.arguments?.getString("query").orEmpty(),
                        // Lote 18 item 3 — magnet/link colado toca direto no player.
                        onPlayUrl = { url, title -> navigateToPlayer(url, title, type = "MOVIE") },
                    )
                }
            }
            // On Demand Xtream (Lote 9, v1.19.0): filmes e séries da conta já
            // configurada; toca pelo player existente (mesmo caminho do detalhe).
            // Acréscimo v1.26.0: deep link da Início (?tab=&itemId=) abre a aba
            // e o detalhe do item; sem eles, abre no topo como antes.
            composable(
                MoviesRoute.LiveOnDemand.route,
                arguments = listOf(
                    navArgument("tab") { type = NavType.StringType; defaultValue = "" },
                    navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                tvScale(isTV) {
                    com.aotv.app.ui.live.OnDemandScreen(
                        onBack = { navController.popBackStack() },
                        onSources = { navController.navigate(MoviesRoute.LiveSources.route) },
                        onPlayMovie = { url, title ->
                            navigateToPlayer(url, title, type = "MOVIE")
                        },
                        onPlayEpisode = { url, title, season, episode ->
                            navigateToPlayer(
                                url, title, type = "SERIES",
                                lastEpisode = "T$season:E$episode",
                                season = season, episode = episode,
                            )
                        },
                        initialTab = entry.arguments?.getString("tab").orEmpty(),
                        initialItemId = entry.arguments?.getString("itemId").orEmpty(),
                    )
                }
            }
            composable(
                MoviesRoute.LivePlayer.route,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("start") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("stop") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                com.aotv.app.ui.live.LivePlayerScreen(
                    channelId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    catchupStart = entry.arguments?.getLong("start") ?: 0L,
                    catchupStop = entry.arguments?.getLong("stop") ?: 0L,
                    // Lote 12 (v1.22.0 — Frente 1): guia a partir do player;
                    // voltar do guia retorna a este player.
                    onOpenGuide = { id -> navController.navigate(MoviesRoute.LiveGuide.create(id)) },
                )
            }

            // Contas Stremio e Nuvio (login, importar addons, lista, progresso e debrid).
            composable(MoviesRoute.Accounts.route) {
                tvScale(isTV) { com.aotv.app.ui.settings.AccountsScreen(onBack = { navController.popBackStack() }) }
            }

            // Debrid (Real-Debrid, TorBox, Premiumize e AllDebrid). Torrent vira link direto.
            composable(MoviesRoute.Debrid.route) {
                tvScale(isTV) { com.aotv.app.ui.settings.DebridScreen(onBack = { navController.popBackStack() }) }
            }

            // Servidores de mídia (Lote 10, v1.20.0) — Jellyfin, Emby e Plex do
            // usuário. Toca pelo player existente (mesmo caminho do detalhe).
            composable(MoviesRoute.MediaServers.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.settings.MediaServersScreen(
                        onBack = { navController.popBackStack() },
                        onPlay = { url, title, type, posterUrl, season, episode ->
                            navigateToPlayer(
                                url, title, type = type, posterUrl = posterUrl,
                                season = season, episode = episode,
                            )
                        },
                    )
                }
            }

            // Personalizar Início (fileiras, ordem, nomes, destaque e catálogo AOTV).
            composable(MoviesRoute.HomeCustomize.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.settings.HomeCustomizeScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAddons = { navController.navigate(MoviesRoute.Addons.route) },
                    )
                }
            }

            // Explorar addons do catálogo da comunidade (celular). A tela de TV fica para a etapa 7.
            composable(MoviesRoute.AddonStore.route) {
                tvScale(isTV) {
                    com.aotv.app.ui.settings.AddonStoreScreen(
                        onBack = { navController.popBackStack() },
                        onOpenHome = { navController.popBackStack(MoviesRoute.Home.route, inclusive = false) },
                    )
                }
            }

            composable(MoviesRoute.Downloads.route) {
                Box(Modifier.fillMaxSize()) {
                DownloadsScreen(
                    onPlayLocal = { d ->
                        // Toca o arquivo local (file://...) pelo mesmo player. Extrai season/episode
                        // do "T1:E3" — sem isso o player trata como filme e o progresso embaralha
                        // com o de outro episódio da mesma série.
                        val m = if (d.lastEpisode.isNotBlank()) Regex("T(\\d+):E(\\d+)").find(d.lastEpisode) else null
                        navigateToPlayer(
                            d.localUri, d.title,
                            tmdbId = d.tmdbId, type = d.type, posterUrl = d.posterUrl,
                            lastEpisode = d.lastEpisode,
                            season = m?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                            episode = m?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                        )
                    },
                )
                }
            }
        }
    }

    // Pílula inferior do celular (Fase A): Início, Busca, Biblioteca e Perfil (+ Ao vivo
    // condicional). Descobrir e Downloads saíram da barra (viraram entradas na Início
    // e na Biblioteca); o "Mais" foi removido — Configurações sai pela aba Perfil.
    // "Cinema" foi removido: apontava para a mesma rota de "Início" (destaque duplo + aba morta).
    val liveBar: com.aotv.app.ui.live.LiveBarViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val liveOn by liveBar.show.collectAsStateWithLifecycle()
    val baseItems = vodNavItems(
        onInicio     = { navTab(MoviesRoute.Home.route) },
        onMinhaLista = { navTab(MoviesRoute.MinhaLista.route) },
        onBusca      = { navTab(MoviesRoute.Search.route) },
        onDownloads  = { navTab(MoviesRoute.Downloads.route) },
        onDescobrir  = { navTab(MoviesRoute.Descobrir.route) },
        inicioRoute     = MoviesRoute.Home.route,
        minhaListaRoute = MoviesRoute.MinhaLista.route,
        buscaRoute      = MoviesRoute.Search.route,
        downloadsRoute  = MoviesRoute.Downloads.route,
        descobrirRoute  = MoviesRoute.Descobrir.route,
    )
    // Aba Ao vivo só existe com fonte de canais, logo depois da Início.
    val vodItems = if (!liveOn) baseItems else baseItems.take(1) +
        com.aotv.app.ui.common.PhoneNavItem(MoviesRoute.Live.route, "Ao vivo", androidx.compose.material.icons.Icons.Default.LiveTv) { navTab(MoviesRoute.Live.route) } +
        baseItems.drop(1)

    // Pílula da Fase A (4 abas): Início, Busca, Biblioteca, Perfil. "Ao vivo" entra
    // como extra logo após Início só com fonte de canais (mesma condição de antes).
    // Descobrir e Downloads saíram da barra: Descobrir virou entrada na Início e
    // Downloads, entrada na Biblioteca (Minha Lista). As rotas continuam existindo.
    // Perfil é item à parte (usa o avatar do perfil em uso, não um ícone).
    val pillItems = listOf(
        com.aotv.app.ui.common.PhoneNavItem(MoviesRoute.Home.route, "Início", Icons.Default.Home) { navTab(MoviesRoute.Home.route) },
    ) + (if (liveOn) listOf(
        com.aotv.app.ui.common.PhoneNavItem(MoviesRoute.Live.route, "Ao vivo", Icons.Default.LiveTv) { navTab(MoviesRoute.Live.route) },
    ) else emptyList()) + listOf(
        com.aotv.app.ui.common.PhoneNavItem(MoviesRoute.Search.route, "Busca", Icons.Default.Search) { navTab(MoviesRoute.Search.route) },
        com.aotv.app.ui.common.PhoneNavItem(MoviesRoute.MinhaLista.route, "Biblioteca", Icons.Default.BookmarkBorder) { navTab(MoviesRoute.MinhaLista.route) },
        // Lote 15 (v1.25.0 — Bloco 1): aba Esportes, 6ª da pílula, SÓ no
        // celular (a pílula só existe no celular — na TV o hideBar a esconde).
        com.aotv.app.ui.common.PhoneNavItem(MoviesRoute.SportsTab.route, "Esportes", Icons.Default.SportsSoccer) { navTab(MoviesRoute.SportsTab.route) },
    )

    // IMPORTANTE: o NavHost precisa ficar SEMPRE no mesmo slot de composição. Se ele
    // trocar de pai (ex.: dentro do Scaffold vs. direto) quando isFullscreen muda, o
    // Compose descarta e recria o NavHost, reiniciando a back stack no Home — foi a causa
    // de clicar no poster "não abrir" o detalhe (a navegação era cancelada na mesma frame).
    // Mantemos o NavHost como primeiro filho fixo de um Box e apenas sobrepomos a barra.
    // Telas da TV com o menu lateral, como as do webOS. Detalhe, player e loja ficam sem.
    val tvMenuRoutes = setOf(
        MoviesRoute.Home.route, MoviesRoute.Search.route, MoviesRoute.Descobrir.route,
        MoviesRoute.MinhaLista.route, MoviesRoute.Addons.route, MoviesRoute.Profiles.route,
        MoviesRoute.Settings.route,
        MoviesRoute.Live.route,
        MoviesRoute.LiveOnDemand.route,
    )
    val showTvMenu = isTV && currentRoute in tvMenuRoutes
    val profVm: com.aotv.app.ui.settings.ProfilesViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val profiles by profVm.profiles.collectAsStateWithLifecycle()
    val pid by profVm.profileId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == pid } ?: profiles.first()
    // Blur/vidro fosco (Lote 4): mesma pref `pref_panel_blur` do detalhe.
    // "Gota" do Davi = esta pílula flutuante de navegação (formato de
    // pílula/gota, embaixo da tela) + o mini player acima dela.
    val panelBlurOn by androidx.hilt.navigation.compose.hiltViewModel<com.aotv.app.ui.movies.DetailPrefsViewModel>().panelBlur.collectAsStateWithLifecycle()

    // ── Lote 15 (v1.25.0 — Bloco 4, itens 6 e 7) ──────────────────────────
    // Atualizador estilo Nuvio (item 7): checa na abertura do app (cold
    // start, no máx. 1x/dia — o gate de 24 h vive em
    // SettingsViewModel.maybeAutoCheckForUpdates()) e mostra o banner no
    // topo quando há versão nova. A checagem manual da página Aparência
    // continua como está. A flag da sessão evita um 2º check se a
    // navegação recompor (o gate de 24 h já barraria, mas a flag deixa
    // explícito que é 1x por abertura — nunca a cada recomposição).
    val settingsVm: com.aotv.app.ui.settings.SettingsViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val updateState by settingsVm.updateState.collectAsStateWithLifecycle()
    val updateDownloading by settingsVm.updateDownloading.collectAsStateWithLifecycle()
    val updateDownloadError by settingsVm.updateDownloadError.collectAsStateWithLifecycle()
    val updateNeedsPermission by settingsVm.updateNeedsPermission.collectAsStateWithLifecycle()
    val whatsNewText by settingsVm.whatsNewText.collectAsStateWithLifecycle()
    val whatsNewDotVisible by settingsVm.whatsNewDotVisible.collectAsStateWithLifecycle()
    var updateCheckedThisSession by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!updateCheckedThisSession) {
            updateCheckedThisSession = true
            settingsVm.maybeAutoCheckForUpdates()
        }
        // Lote 18 (v1.28.0): "What's new" — 1ª abertura com a versão nova
        // mostra o que mudou e acende a bolinha no avatar até o usuário ver.
        settingsVm.checkWhatsNewOnStart()
    }
    var updateBannerDismissed by remember { mutableStateOf(false) }
    val updateAvailable =
        updateState as? com.aotv.app.data.remote.UpdateCheckResult.Available

    // Mini cartão "Continuar assistindo" (item 6): mesmo dado da fileira
    // Continuar assistindo — MinhaListaViewModel.recentlyPlayed() lê
    // LibraryRepository.recentlyPlayed() (histórico Room, com o oculto por
    // perfil filtrado, igual à fileira) — e a arte vem de recentArtwork.
    // Só no cold start: o estado vive em remember (não rememberSaveable),
    // então voltar do segundo plano não reexibe; reabrir o app, sim.
    // Não mostra sem nada p/ continuar nem no modo privado.
    val continueVm: com.aotv.app.ui.movies.MinhaListaViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val recentPlayed by continueVm.recentlyPlayed.collectAsStateWithLifecycle()
    val recentArtwork by continueVm.recentArtwork.collectAsStateWithLifecycle()
    val resumePrivateMode by androidx.hilt.navigation.compose.hiltViewModel<com.aotv.app.ui.movies.PlayerPrefsViewModel>().privateMode.collectAsStateWithLifecycle()
    var resumeVisible by remember { mutableStateOf(true) }
    // Terminado (90%+) some, igual à fileira (MoviesHomeViewModel): p/
    // série terminada a fileira avança p/ o próximo episódio (precisa do
    // TMDB) — o cartão pula p/ o próximo item inacabado em vez disso.
    val resumeCandidate = remember(recentPlayed) {
        recentPlayed.firstOrNull { !isResumeFinished(it) }
    }
    val showResumeCard = !resumePrivateMode && resumeVisible && resumeCandidate != null
    LaunchedEffect(resumeCandidate) {
        if (resumeCandidate != null && resumeVisible) {
            kotlinx.coroutines.delay(5000)
            resumeVisible = false
        }
    }
    // Retomar: mesmo caminho do "Continuar" do detalhe (DetailPrimary) —
    // a última fonte salva toca da posição salva; sem fonte salva, o
    // player busca as fontes (embedUrl vazio). Ao vivo volta ao player
    // do canal, como o toque no card da fileira.
    fun resumeContinue(h: com.aotv.app.data.local.entity.MovieHistoryEntity) {
        resumeVisible = false
        if (h.type == com.aotv.app.ui.live.LivePlayerViewModel.LIVE_TYPE) {
            val live = com.aotv.app.ui.live.LivePlayerViewModel.parseLiveEmbed(
                "live|" + h.lastSourceUrl + "|" + h.lastAddon
            )
            if (live != null) {
                val (channelId, startSec, stopSec) = live
                if (stopSec > 0 && System.currentTimeMillis() / 1000 >= stopSec) {
                    navController.navigate(MoviesRoute.LivePlayer.create(channelId, startSec, stopSec))
                } else {
                    navController.navigate(MoviesRoute.LivePlayer.create(channelId))
                }
                return
            }
        }
        val ep = parseResumeEpisode(h.lastEpisode)
        navigateToPlayer(
            h.lastSourceUrl, h.title, tmdbId = h.tmdbId, type = h.type,
            posterUrl = h.posterUrl, lastEpisode = h.lastEpisode,
            startPositionMs = h.positionMs,
            season = ep?.first ?: 0, episode = ep?.second ?: 0,
        )
    }

    // Foco da TV. Cada tela nova recebe o foco no conteúdo e Voltar na Início vai ao menu e
    // depois pergunta se quer sair, igual ao back() do webOS.
    val menuRequesters = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
    fun menuRequester(route: String) = menuRequesters.getOrPut(route) { androidx.compose.ui.focus.FocusRequester() }
    val contentFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }
    // Hora da última tecla. O foco automático não briga com quem já está usando o menu.
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    // Última seta para a esquerda ou Voltar. Só essas abrem o menu de propósito.
    var lastMenuKeyAt by remember { mutableLongStateOf(0L) }
    if (isTV) {
        LaunchedEffect(currentRoute) {
            // Tela nova (pelo menu ou pelo Voltar) começa no conteúdo, como o showEntry do webOS.
            // ponytail: vigia por até 20 s (Início espera a rede, e o Compose às vezes devolve o foco
            // ao menu quando a tela de trás some). Para na primeira tecla depois da troca de tela.
            val start = android.os.SystemClock.uptimeMillis()
            repeat(80) {
                kotlinx.coroutines.delay(250)
                if (lastKeyAt > start) return@LaunchedEffect
                if (!contentHasFocus) runCatching { contentFocus.requestFocus() }
            }
        }
        // Só a Início, registrado AQUI (antes do navHost()) de propósito: a Início tem seu
        // próprio BackHandler interno pra fechar a grade do "Ver tudo" (TvHomeScreen.kt), e
        // esse precisa continuar ganhando prioridade sobre este (o dispatcher chama o callback
        // mais recente primeiro, e o da Início é registrado depois do nosso por estar dentro
        // do navHost()). Não dá pra juntar com o handler de "fora da Início" lá embaixo sem
        // quebrar essa ordem.
        androidx.activity.compose.BackHandler(enabled = currentRoute == MoviesRoute.Home.route) {
            lastKeyAt = android.os.SystemClock.uptimeMillis()
            lastMenuKeyAt = lastKeyAt
            if (menuOpen) askExit = true
            else {
                inputModeManager.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
                runCatching { menuRequester(MoviesRoute.Home.route).requestFocus() }
            }
        }
        if (askExit) {
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            TvExitDialog(onExit = { activity?.finish() }, onStay = { askExit = false })
        }
    }

    com.aotv.app.ui.common.ProvideTvMode(isTV) {
    Box(
        Modifier.fillMaxSize().background(Background)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .then(if (isTV) Modifier.onPreviewKeyEvent {
                if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    lastKeyAt = android.os.SystemClock.uptimeMillis()
                    val kc = it.nativeKeyEvent.keyCode
                    if (kc == android.view.KeyEvent.KEYCODE_DPAD_LEFT || kc == android.view.KeyEvent.KEYCODE_BACK) lastMenuKeyAt = lastKeyAt
                    if (menuOpen && kc == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        runCatching { contentFocus.requestFocus() }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            } else Modifier),
    ) {
        // Reserva p/ a pílula (Lote 15, v1.25.0 — menor: item 46 + paddings,
        // ~66dp no total) + inset da navigation bar do sistema, para a
        // última linha do grid não ficar escondida atrás da pílula. Com o
        // mini player ativo (Fase C), soma a altura da barra compacta
        // (~64dp) que fica acima da pílula.
        val miniActive = com.aotv.app.ui.movies.MiniPlayerState.minimized.value
        val barReserve = (84.dp + if (miniActive) 64.dp else 0.dp) + WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding()
        // Na TV a barra vira o menu lateral próprio (TvSideMenu).
        val hideBar = isFullscreen || isTV || showTabletRail
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    when {
                        showTvMenu && currentRoute != MoviesRoute.Home.route -> Modifier.padding(start = TvMenuClosed)
                        showTabletRail -> Modifier.padding(start = tabletRailWidth())
                        hideBar -> Modifier
                        else -> Modifier.padding(bottom = barReserve)
                    }
                )
                .then(
                    if (isTV) Modifier
                        .onFocusChanged { contentHasFocus = it.hasFocus }
                        .focusRequester(contentFocus)
                        .focusGroup()
                    else Modifier
                ),
        ) {
            navHost()
        }
        if (isTV) {
            // Fora da Início, com o menu aberto. Registrado DEPOIS do navHost() de propósito:
            // o NavHost da navigation-compose 2.8.3 registra o próprio OnBackPressedCallback de
            // pop quando a pilha tem mais de 1 tela (sempre o caso aqui, pilha [Home, X]), e o
            // dispatcher chama o callback mais recente primeiro. Registrado antes do navHost(),
            // o pop dele ganhava do nosso e Voltar saía trocando de tela (ou nada, dependendo da
            // versão) em vez de só fechar o menu e devolver o foco ao conteúdo.
            androidx.activity.compose.BackHandler(enabled = currentRoute != MoviesRoute.Home.route && menuOpen) {
                runCatching { contentFocus.requestFocus() }
            }
        }
        if (showTvMenu) {
            val reduced = com.aotv.app.ui.common.rememberReducedMotion()
            val scrim by androidx.compose.animation.core.animateFloatAsState(
                if (menuOpen) .4f else 0f,
                com.aotv.app.ui.common.aotvTween(220, reduced), label = "menuScrim",
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = scrim }
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .focusProperties { canFocus = false }
                    .then(if (menuOpen) Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { runCatching { contentFocus.requestFocus() } } else Modifier),
            )
            // Selecionar a própria rota ativa só fecha o menu (launchSingleTop não muda currentRoute,
            // então nada reabriria o foco no conteúdo sozinho).
            val go: (String) -> () -> Unit = { r -> {
                if (r == currentRoute) runCatching { contentFocus.requestFocus() } else navTab(r)
            } }
            TvSideMenu(
                main = buildList {
                    add(TvMenuItem(MoviesRoute.Home.route, "Início", Icons.Default.Home, go(MoviesRoute.Home.route)))
                    if (liveOn) add(TvMenuItem(MoviesRoute.Live.route, "Ao vivo", Icons.Default.LiveTv, go(MoviesRoute.Live.route)))
                    add(TvMenuItem(MoviesRoute.Search.route, "Buscar", Icons.Default.Search, go(MoviesRoute.Search.route)))
                    add(TvMenuItem(MoviesRoute.Descobrir.route, "Descobrir", Icons.Default.Explore, go(MoviesRoute.Descobrir.route)))
                    add(TvMenuItem(MoviesRoute.MinhaLista.route, "Biblioteca", Icons.Default.Bookmark, go(MoviesRoute.MinhaLista.route)))
                    add(TvMenuItem(MoviesRoute.Addons.route, "Addons", Icons.Default.Extension, go(MoviesRoute.Addons.route)))
                },
                bottom = listOf(
                    TvMenuItem(MoviesRoute.Profiles.route, profile.name, null, go(MoviesRoute.Profiles.route), avatar = profile),
                    TvMenuItem(MoviesRoute.Settings.route, "Configurações", androidx.compose.material.icons.Icons.Default.Settings, go(MoviesRoute.Settings.route)),
                ),
                currentRoute = currentRoute,
                requesters = tvMenuRoutes.associateWith { menuRequester(it) },
                open = menuOpen,
                onOpenChange = { open ->
                    menuOpen = open
                    // O Compose às vezes joga o foco no menu quando o item focado some (perfil
                    // removido, janela fechada). Sem seta para a esquerda nem Voltar, volta ao conteúdo.
                    if (open && android.os.SystemClock.uptimeMillis() - lastMenuKeyAt > 600)
                        runCatching { contentFocus.requestFocus() }
                },
            )
        }
        // Toque fora dispensa o mini cartão (Lote 15, item 6): scrim
        // transparente por cima do conteúdo (a pílula e o cartão ficam por
        // cima dele, na Column abaixo, e continuam tocáveis). Só existe
        // enquanto o cartão está visível (no máx. 5 s).
        if (!hideBar && showResumeCard) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { resumeVisible = false },
            )
        }
        if (!hideBar) {
            // Mini player (Fase C) acima da pílula: título + play/pause +
            // expandir + fechar. Só no celular (hideBar cobre TV/tablet) e só
            // fora da rota do player (lá o player cheio está na tela).
            Column(Modifier.align(Alignment.BottomCenter)) {
                // Mini cartão "Continuar assistindo" (Lote 15, item 6):
                // flutua acima da pílula (e do mini player, quando ativo),
                // some sozinho em ~5 s, e o toque retoma a reprodução.
                val resumeItem = resumeCandidate
                if (showResumeCard && resumeItem != null) {
                    ResumeContinueCard(
                        item = resumeItem,
                        artwork = recentArtwork[resumeArtworkKey(resumeItem)],
                        onResume = { resumeContinue(resumeItem) },
                        onDismiss = { resumeVisible = false },
                        frosted = panelBlurOn,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (miniActive) {
                    com.aotv.app.ui.movies.MiniPlayerBar(
                        onExpand = {
                            val id = com.aotv.app.ui.movies.MiniPlayerState.playId
                            if (id >= 0) navController.navigate(MoviesRoute.Player.create(id))
                        },
                        frosted = panelBlurOn,
                    )
                }
                MoviesBottomBar(
                    currentRoute = currentRoute,
                    items = pillItems,
                    profile = profile,
                    profilesRoute = MoviesRoute.Profiles.route,
                    onProfile = { navTab(MoviesRoute.Profiles.route) },
                    frosted = panelBlurOn,
                    showWhatsNewDot = whatsNewDotVisible,
                )
            }
        }
        if (showTabletRail) {
            MoviesTabletRail(
                currentRoute = currentRoute,
                items = vodItems,
                onSettings = { navTab(MoviesRoute.Settings.route) },
                settingsRoute = MoviesRoute.Settings.route,
                profile = profile,
                onProfile = { navController.navigate(MoviesRoute.Profiles.route) },
                showWhatsNewDot = whatsNewDotVisible,
            )
        }
        // Banner de atualização estilo Nuvio (Lote 15, item 7): no topo,
        // só no celular fora de tela cheia (hideBar cobre TV/tablet/
        // player/detalhe). O X dispensa nesta sessão; "Atualizar" baixa o
        // APK da release oficial e pede p/ instalar (fluxo existente).
        val availableUpdate = updateAvailable
        if (!hideBar && availableUpdate != null && !updateBannerDismissed) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .statusBarsPadding().padding(top = 8.dp),
            ) {
                AppUpdateBanner(
                    tag = availableUpdate.release.tag,
                    sizeBytes = availableUpdate.release.sizeBytes,
                    downloading = updateDownloading,
                    downloadError = updateDownloadError,
                    onUpdate = { settingsVm.downloadAndInstallUpdate() },
                    onDismiss = { updateBannerDismissed = true },
                    frosted = panelBlurOn,
                    ready = settingsVm.isUpdateReady(availableUpdate.release.tag),
                )
            }
        }
        // Sem permissão p/ instalar apps desconhecidos: mesmo diálogo da
        // página Aparência (o banner só baixa; a permissão é do sistema).
        if (updateNeedsPermission) {
            AlertDialog(
                onDismissRequest = { settingsVm.dismissUpdatePermission() },
                title = { Text("Permitir instalação") },
                text = { Text("O Android ainda não permite que o AOTV instale apps. Ative \"Instalar apps desconhecidos\" p/ o AOTV nas configurações do sistema e tente de novo.") },
                confirmButton = {
                    TextButton(onClick = { settingsVm.dismissUpdatePermission(); settingsVm.openUnknownSourcesSettings() }) {
                        Text("Abrir configurações")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { settingsVm.dismissUpdatePermission() }) { Text("Agora não") }
                },
            )
        }
        // "What's new" (Lote 18, v1.28.0): o que mudou na versão instalada.
        // Fechar apaga a bolinha do avatar (mesmo diálogo da Aparência).
        val whatsNew = whatsNewText
        if (whatsNew != null) {
            AlertDialog(
                onDismissRequest = { settingsVm.dismissWhatsNew() },
                title = { Text("Novidades da v${settingsVm.installedAppVersion}") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(whatsNew)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { settingsVm.dismissWhatsNew() }) { Text("Entendi") }
                },
            )
        }
    }
    }
}

@Composable
private fun tabletRailWidth() = (80 + 24 * (androidx.compose.ui.platform.LocalDensity.current.fontScale - 1f).coerceIn(0f, 1f)).dp

@Composable
private fun MoviesTabletRail(
    currentRoute: String?,
    items: List<com.aotv.app.ui.common.PhoneNavItem>,
    onSettings: () -> Unit,
    settingsRoute: String,
    profile: com.aotv.app.data.local.Profile,
    onProfile: () -> Unit,
    /** Bolinha "What's new" no avatar (Lote 18, v1.28.0). */
    showWhatsNewDot: Boolean = false,
) {
    Column(Modifier.fillMaxHeight().width(tabletRailWidth()).background(com.aotv.app.ui.theme.BackgroundElevated)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
        .verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            TouchRailItem(item.icon, item.label, currentRoute == item.route, item.onClick)
        }
        TouchRailItem(Icons.Default.Settings, "Configurações", currentRoute == settingsRoute, onSettings)
        TouchRailItem(Icons.Default.Bookmark, "Perfil", false, onProfile, profile, showWhatsNewDot)
    }
}

@Composable
private fun TouchRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit,
    profile: com.aotv.app.data.local.Profile? = null,
    showWhatsNewDot: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
        .background(if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)
        .semantics { this.selected = selected }
        .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick).padding(horizontal = 5.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (profile != null) com.aotv.app.ui.settings.ProfileAvatar(profile, 28.dp, showDot = showWhatsNewDot)
        else Icon(icon, null, tint = if (selected) com.aotv.app.ui.theme.TextOnLight else com.aotv.app.ui.theme.TextSecondary,
            modifier = Modifier.size(26.dp))
        Text(label, color = if (selected) com.aotv.app.ui.theme.TextOnLight else com.aotv.app.ui.theme.TextSecondary,
            fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Pílula flutuante da Fase A: 4 abas (Início, Busca, Biblioteca, Perfil) + Ao vivo
 *  condicional. A aba selecionada leva o fundo na cor de acento (turquesa) com
 *  ícone e rótulo escuros; Perfil usa o avatar do perfil em uso. Sem "Mais":
 *  Configurações sai pela aba Perfil.
 *
 *  Lote 15 (v1.25.0 — Bloco 4, item 4): pílula menor, estilo Nuvio — mais
 *  estreita (margens laterais de 20dp), itens de 46dp, ícones de 20dp,
 *  paddings menores. O vidro é o mesmo tratamento de antes ([GlassBg]
 *  translúcido + borda sutil, com o toggle `pref_panel_blur`); blur real
 *  de fundo não existe aqui (o RenderEffect desfoca o próprio conteúdo,
 *  não o que está atrás da barra). A lista de itens continua sendo
 *  parâmetro ([items]), p/ a aba Esportes entrar depois sem reescrever. */
@Composable
private fun MoviesBottomBar(
    currentRoute: String?,
    items: List<com.aotv.app.ui.common.PhoneNavItem>,
    profile: com.aotv.app.data.local.Profile,
    profilesRoute: String,
    onProfile: () -> Unit,
    /**
     * Vidro fosco (Lote 4): com o toggle `pref_panel_blur` ligado, a pílula
     * ("gota") fica mais translúcida (0.62 em vez de 0.72) p/ o conteúdo
     * aparecer através dela. Blur real não alcança aqui: o RenderEffect
     * desfoca o próprio conteúdo (ícones inclusos), não o que está atrás
     * da barra. Funciona em qualquer API.
     */
    frosted: Boolean = false,
    /** Bolinha "What's new" no avatar do Perfil (Lote 18, v1.28.0). */
    showWhatsNewDot: Boolean = false,
) {
    // Pílula flutuante estilo Nuvio: vidro translúcido + sombra, ícones menores com rótulo.
    // O blur de fundo vem da translucência do GlassBg: o blur do Compose desfoca o
    // próprio conteúdo (ícones inclusos), não o que está atrás da barra.
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 6.dp)
        .shadow(10.dp, MaterialTheme.shapes.extraLarge)
        .clip(MaterialTheme.shapes.extraLarge).background(if (frosted) com.aotv.app.ui.theme.GlassBg.copy(alpha = 0.62f) else com.aotv.app.ui.theme.GlassBg)
        .border(1.dp, com.aotv.app.ui.theme.BorderSubtle, MaterialTheme.shapes.extraLarge)
        .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            MoviesBottomBarItem(item.icon, item.label, currentRoute == item.route, item.onClick, Modifier.weight(1f))
        }
        MoviesBottomBarItem(
            icon = null, label = "Perfil", selected = currentRoute == profilesRoute,
            onClick = onProfile, modifier = Modifier.weight(1f), profile = profile,
            showWhatsNewDot = showWhatsNewDot,
        )
    }
}

@Composable
private fun MoviesBottomBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    profile: com.aotv.app.data.local.Profile? = null,
    showWhatsNewDot: Boolean = false,
) {
    Column(modifier.heightIn(min = 46.dp).clip(MaterialTheme.shapes.large)
        .background(if (selected) com.aotv.app.ui.theme.Accent else androidx.compose.ui.graphics.Color.Transparent)
        .semantics { this.selected = selected }
        .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val fg = if (selected) com.aotv.app.ui.theme.TextOnLight else com.aotv.app.ui.theme.TextSecondary
        if (profile != null) com.aotv.app.ui.settings.ProfileAvatar(profile, 20.dp, showDot = showWhatsNewDot)
        else if (icon != null) Icon(icon, label, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, color = fg,
            fontSize = 9.sp, lineHeight = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = com.aotv.app.ui.theme.Inter, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Tela "Minha Lista" do modo Filmes & Séries.
 *
 * Duas seções. "Continuar assistindo" (recentlyPlayed) num rail horizontal com barra de
 * progresso, selo do episódio e tempo restante. "Salvos" (watchlist) num grid de 3 colunas
 * com filtro por tipo. Nas duas o título fica ABAIXO da arte, nunca sobre o pôster, então
 * nada some no fundo claro da imagem nem corta no meio da palavra.
 *
 * Clicar reconstrói o [MovieItem] a partir da entidade (mesclando com o movieCache quando
 * houver) e navega ao detalhe via [onItemClick]. Segurar remove, com confirmação.
 */
@Composable
private fun MinhaListaScreen(
    onItemClick: (MovieItem) -> Unit,
    onDownloadsClick: () -> Unit = {},
    /** Servidores de mídia (Lote 10): abre a tela de Jellyfin/Emby/Plex. */
    onMediaServersClick: () -> Unit = {},
    /** Modo aleatório (Lote 2): episódio sorteado abre DIRETO no player. */
    onPlayEpisode: (MovieItem, Int, Int, Int) -> Unit = { _, _, _, _ -> },
) {
    val vm: com.aotv.app.ui.movies.MinhaListaViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val randomVm: com.aotv.app.ui.movies.RandomEpisodeViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val recent by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val recentArtwork by vm.recentArtwork.collectAsStateWithLifecycle()
    // Ainda não assistidos (Lote 11, v1.21.0): títulos da Minha Lista sem
    // nenhuma reprodução e sem episódio visto.
    val notWatched by vm.notWatchedYet.collectAsStateWithLifecycle()
    // Abas da Biblioteca (Lote 11, v1.21.0): 0 = Biblioteca (o de sempre),
    // 1 = Histórico (o histórico de reproduções, com busca e ordenação).
    var tab by remember { mutableStateOf(0) }
    // Blur/vidro fosco (Lote 4): mesma pref `pref_panel_blur` do detalhe.
    val panelBlurOn by androidx.hilt.navigation.compose.hiltViewModel<com.aotv.app.ui.movies.DetailPrefsViewModel>().panelBlur.collectAsStateWithLifecycle()
    // Calendário (Lote 2, item 6): carrega 1x ao abrir a Biblioteca; o "Atualizar"
    // da seção força a releitura (o cache de detalhe tem TTL de 6h).
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadCalendar() }
    // Modo aleatório (Lote 2): o sorteio abre direto no player quando sai;
    // sem série elegível, mostra o estado vazio em diálogo (não trava).
    val randomState by randomVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(randomState) {
        val ready = randomState as? com.aotv.app.ui.movies.RandomEpisodeViewModel.RandomState.Ready
            ?: return@LaunchedEffect
        onPlayEpisode(ready.item, ready.season, ready.episode, ready.episodeTotal)
        randomVm.consume()
    }
    (randomState as? com.aotv.app.ui.movies.RandomEpisodeViewModel.RandomState.Empty)?.let { empty ->
        AlertDialog(
            onDismissRequest = { randomVm.consume() },
            title = { Text("Nada para sortear") },
            text = { Text(empty.message) },
            confirmButton = {
                TextButton(onClick = { randomVm.consume() }) { Text("Entendi") }
            },
        )
    }

    // Itens pendentes de remoção (segurar → confirma), um por seção.
    var pendingRemove by remember { mutableStateOf<com.aotv.app.data.local.entity.MovieHistoryEntity?>(null) }
    var pendingUnsave by remember { mutableStateOf<com.aotv.app.data.local.entity.WatchlistEntity?>(null) }
    // Filtro do grid de salvos. "" = tudo.
    var filter by remember { mutableStateOf("") }

    pendingRemove?.let { h ->
        ConfirmRemoveDialog(
            title = "Remover de Continuar assistindo?",
            body = h.title,
            onConfirm = { vm.removeFromContinue(h.tmdbId, h.type); pendingRemove = null },
            onDismiss = { pendingRemove = null },
        )
    }
    pendingUnsave?.let { w ->
        ConfirmRemoveDialog(
            title = "Tirar da Minha Lista?",
            body = w.title,
            onConfirm = { vm.removeFromWatchlist(w.tmdbId, w.type); pendingUnsave = null },
            onDismiss = { pendingUnsave = null },
        )
    }

    // Reconstrói um MovieItem completo: usa o do cache (tem embedId/backdrop/overview)
    // quando existir; senão monta um stub mínimo a partir da entidade salva.
    fun rebuild(tmdbId: Int, type: MediaType, title: String, posterUrl: String, year: String): MovieItem =
        movieCache[cacheKey(tmdbId, type)] ?: MovieItem(
            tmdbId = tmdbId,
            embedId = "",
            title = title,
            overview = "",
            posterUrl = posterUrl,
            backdropUrl = "",
            year = year,
            rating = 0.0,
            type = type,
        )

    fun typeOf(raw: String): MediaType =
        runCatching { MediaType.valueOf(raw) }.getOrDefault(MediaType.MOVIE)

    val hasBothTypes = remember(watchlist) {
        watchlist.any { it.type == "MOVIE" } && watchlist.any { it.type == "SERIES" }
    }
    val saved = remember(watchlist, filter) {
        if (filter.isBlank()) watchlist else watchlist.filter { it.type == filter }
    }

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        com.aotv.app.ui.common.PageHeader(
            title = "Minha Lista",
            sub = if (watchlist.isEmpty() && recent.isEmpty()) null else "${watchlist.size} salvos  ·  ${recent.size} em andamento",
        )
        // Abas (Lote 11, v1.21.0): Biblioteca e Histórico. Na TV não há abas
        // (TvMyListScreen, sem mudanças).
        Row(
            Modifier.fillMaxWidth()
                .padding(start = com.aotv.app.ui.common.pagePadding(), end = com.aotv.app.ui.common.pagePadding(), bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.aotv.app.ui.common.FilterChipPill("Biblioteca", tab == 0, onClick = { tab = 0 })
            com.aotv.app.ui.common.FilterChipPill("Histórico", tab == 1, onClick = { tab = 1 })
        }
        if (tab == 0) {
        // Downloads saiu da barra (Fase A, pílula de 4 abas): a entrada fica aqui,
        // na Biblioteca. Sempre visível, mesmo com a lista vazia.
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(com.aotv.app.ui.theme.BackgroundCard)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onDownloadsClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.Download, null, tint = com.aotv.app.ui.theme.TextPrimary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text("Downloads", color = com.aotv.app.ui.theme.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("Vídeos baixados no aparelho", color = com.aotv.app.ui.theme.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Icon(Icons.Default.ChevronRight, "Abrir downloads", tint = com.aotv.app.ui.theme.TextTertiary, modifier = Modifier.size(22.dp))
        }
        // Modo aleatório (Lote 2): segunda entrada, na Biblioteca. Sempre
        // visível, mesmo com a lista vazia — o estado vazio é do sorteio.
        com.aotv.app.ui.movies.RandomEpisodeEntry(
            rolling = randomState is com.aotv.app.ui.movies.RandomEpisodeViewModel.RandomState.Rolling,
            onRoll = { randomVm.roll() },
        )
        // Servidores de mídia (Lote 10, v1.20.0): terceira entrada, na
        // Biblioteca. Só aparece com servidor conectado e o atalho ligado
        // (pref por perfil `media_servers_show_v1`).
        val mediaServersVm: com.aotv.app.ui.settings.MediaServersViewModel =
            androidx.hilt.navigation.compose.hiltViewModel()
        val showMediaServers by mediaServersVm.hasServers.collectAsStateWithLifecycle()
        if (showMediaServers) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(com.aotv.app.ui.theme.BackgroundCard)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMediaServersClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Default.Dns, null, tint = com.aotv.app.ui.theme.TextPrimary, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Servidores de mídia", color = com.aotv.app.ui.theme.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("Jellyfin, Emby e Plex — sua coleção", color = com.aotv.app.ui.theme.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Icon(Icons.Default.ChevronRight, "Abrir servidores de mídia", tint = com.aotv.app.ui.theme.TextTertiary, modifier = Modifier.size(22.dp))
            }
        }
        val pad = com.aotv.app.ui.common.pagePadding()
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val availableWidth = config.screenWidthDp - if (config.screenWidthDp >= 600 && config.screenHeightDp >= 480) tabletRailWidth().value.toInt() else 0
        val perRow = com.aotv.app.ui.common.posterColumns(availableWidth, config.fontScale)

        if (tab == 0 && watchlist.isEmpty() && recent.isEmpty()) {
            // Estado vazio premium centralizado.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.aotv.app.ui.common.AotvEmptyState(
                    icon = androidx.compose.material.icons.Icons.Default.BookmarkBorder,
                    title = "Sua lista está vazia",
                    message = "Salve filmes e séries no detalhe para vê-los aqui. Os títulos que você começar a assistir aparecem em \"Continuar assistindo\".",
                )
            }
            return@Column
        }

        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            // ── (C) Continuar assistindo — rail horizontal ───────────────────
            if (recent.isNotEmpty()) {
                item {
                    com.aotv.app.ui.common.SectionHeader(
                        title = "Continuar assistindo",
                        action = { CountPill(recent.size) },
                    )
                }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = pad),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            recent,
                            key = { "${it.tmdbId}:${it.type}" },
                        ) { h ->
                            val pct = if (h.durationMs > 0L)
                                (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else null
                            val item = rebuild(h.tmdbId, typeOf(h.type), h.title, h.posterUrl, "")
                            com.aotv.app.ui.common.ContinueCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                artwork = recentArtwork[com.aotv.app.data.local.posterKey(item)],
                                remaining = if (h.durationMs > h.positionMs) ((h.durationMs - h.positionMs + 59_999) / 60_000).toInt() else null,
                                episode = h.lastEpisode,
                                progress = pct,
                                onLongClick = { pendingRemove = h }, // segurar → remover
                                frosted = panelBlurOn,
                            )
                        }
                    }
                }
            }

            // ── (A) Ainda não assistidos (Lote 11, v1.21.0) ─────────────────
            // Fileira horizontal com os títulos da Minha Lista que o perfil
            // ainda não assistiu (ver MinhaListaViewModel.notWatchedYet).
            // Some quando não há nenhum — a seção "Salvos" continua abaixo.
            if (notWatched.isNotEmpty()) {
                item {
                    com.aotv.app.ui.common.SectionHeader(
                        title = "Ainda não assistidos",
                        eyebrow = "Minha Lista",
                        action = { CountPill(notWatched.size) },
                    )
                }
                item {
                    val railWidth = com.aotv.app.ui.common.posterWidthFor(com.aotv.app.data.local.PosterSize.SMALL)
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = pad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(notWatched, key = { "${it.tmdbId}:${it.type}" }) { w ->
                            val item = rebuild(w.tmdbId, typeOf(w.type), w.title, w.posterUrl, w.year)
                            com.aotv.app.ui.common.PosterCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                modifier = Modifier.width(railWidth),
                                onLongClick = { pendingUnsave = w }, // segurar → tirar da lista
                            )
                        }
                    }
                }
            }

            // ── (D) Calendário — próximos episódios das séries acompanhadas ──
            // (Lote 2, item 6): Minha Lista + Continuar assistindo, ordenado por
            // data. Sem série acompanhada a seção nem aparece; sem data no TMDB
            // o episódio não entra. Tocar abre o detalhe da série.
            if (calendar.hasTrackedSeries) {
                item {
                    com.aotv.app.ui.common.SectionHeader(
                        title = "Próximos episódios",
                        eyebrow = "Calendário",
                        action = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                CountPill(calendar.entries.size)
                                IconButton(onClick = { vm.loadCalendar(force = true) }) {
                                    Icon(
                                        Icons.Default.Refresh, "Atualizar datas",
                                        tint = com.aotv.app.ui.theme.TextTertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                if (calendar.loading) {
                    item {
                        Text(
                            "Buscando datas…",
                            color = com.aotv.app.ui.theme.TextTertiary, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = pad, vertical = 8.dp),
                        )
                    }
                } else if (calendar.entries.isEmpty()) {
                    item {
                        EmptySavedHint("Nenhuma data de episódio nas séries acompanhadas. Séries sem previsão no TMDB não entram aqui.")
                    }
                } else {
                    val today = todayMs()
                    items(calendar.entries, key = { "${it.tmdbId}:${it.season}:${it.episode}" }) { e ->
                        CalendarRow(
                            entry = e,
                            todayMs = today,
                            onClick = { onItemClick(rebuild(e.tmdbId, MediaType.SERIES, e.title, e.posterUrl, "")) },
                        )
                    }
                }
            }

            // ── (B) Minha Lista — grid 3 colunas ─────────────────────────────
            item {
                com.aotv.app.ui.common.SectionHeader(
                    title = "Salvos",
                    eyebrow = "Minha Lista",
                    action = { CountPill(watchlist.size) },
                )
            }
            // Filtro só aparece quando há os dois tipos, senão é chip inútil na tela.
            if (hasBothTypes) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = pad, end = pad, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.aotv.app.ui.common.FilterChipPill("Tudo", filter.isBlank(), onClick = { filter = "" })
                        com.aotv.app.ui.common.FilterChipPill("Filmes", filter == "MOVIE", onClick = { filter = "MOVIE" })
                        com.aotv.app.ui.common.FilterChipPill("Séries", filter == "SERIES", onClick = { filter = "SERIES" })
                    }
                }
            }
            if (saved.isEmpty()) {
                item {
                    EmptySavedHint(
                        if (watchlist.isEmpty())
                            "Nada salvo ainda. Abra um título e toque no marcador para guardá-lo aqui."
                        else
                            "Nenhum título salvo nesse filtro."
                    )
                }
            }
            // Linhas de 3 colunas dentro da LazyColumn (evita aninhar grid rolável).
            val rows = saved.chunked(perRow)
            items(rows) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = pad, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { w ->
                        val item = rebuild(w.tmdbId, typeOf(w.type), w.title, w.posterUrl, w.year)
                        com.aotv.app.ui.common.PosterCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            modifier = Modifier.weight(1f),
                            onLongClick = { pendingUnsave = w }, // segurar → tirar da lista
                        )
                    }
                    // Preenche colunas faltantes p/ manter o alinhamento da última linha.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
        } else {
            HistoryTab(
                history = recent,
                onItemClick = onItemClick,
                onRemoveRequest = { pendingRemove = it },
                onClearHistory = { vm.clearHistory() },
                rebuild = { h -> rebuild(h.tmdbId, typeOf(h.type), h.title, h.posterUrl, "") },
            )
        }
    }
}

/**
 * Aba "Histórico" da Biblioteca (Lote 11, v1.21.0): o histórico de
 * reproduções do perfil (movie_history, o mesmo do Continuar assistindo —
 * no máximo 30 registros, os mais recentes primeiro). Busca por título e
 * ordenação por data (padrão) ou por título. Tocar abre o detalhe; o
 * botão de lixeira remove (o mesmo diálogo do Continuar); "Limpar
 * histórico" apaga tudo, com confirmação. Na TV a aba não existe
 * (TvMyListScreen, sem mudanças).
 */
@Composable
private fun HistoryTab(
    history: List<com.aotv.app.data.local.entity.MovieHistoryEntity>,
    onItemClick: (MovieItem) -> Unit,
    onRemoveRequest: (com.aotv.app.data.local.entity.MovieHistoryEntity) -> Unit,
    onClearHistory: () -> Unit,
    rebuild: (com.aotv.app.data.local.entity.MovieHistoryEntity) -> MovieItem,
) {
    var query by remember { mutableStateOf("") }
    var sortByTitle by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = remember(history, query, sortByTitle) {
        val filtered = if (query.isBlank()) history
            else history.filter { it.title.contains(query, ignoreCase = true) }
        if (sortByTitle) filtered.sortedBy { it.title.lowercase() } else filtered
    }
    if (confirmClear) {
        ConfirmRemoveDialog(
            title = "Limpar todo o histórico?",
            body = "Apaga as ${history.size} reproduções registradas. A Minha Lista não é afetada.",
            onConfirm = { onClearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
    val pad = com.aotv.app.ui.common.pagePadding()
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Buscar no histórico", color = com.aotv.app.ui.theme.TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = com.aotv.app.ui.theme.TextTertiary, modifier = Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = com.aotv.app.ui.theme.Accent,
                unfocusedBorderColor = com.aotv.app.ui.theme.Border,
                focusedTextColor = com.aotv.app.ui.theme.TextPrimary,
                unfocusedTextColor = com.aotv.app.ui.theme.TextPrimary,
                cursorColor = com.aotv.app.ui.theme.Accent,
                focusedContainerColor = com.aotv.app.ui.theme.Surface2,
                unfocusedContainerColor = com.aotv.app.ui.theme.Surface2,
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.aotv.app.ui.common.FilterChipPill("Mais recentes", !sortByTitle, onClick = { sortByTitle = false })
            com.aotv.app.ui.common.FilterChipPill("Título A–Z", sortByTitle, onClick = { sortByTitle = true })
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Limpar histórico", color = com.aotv.app.ui.theme.LiveRed, fontSize = 13.sp)
                }
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.aotv.app.ui.common.AotvEmptyState(
                    icon = androidx.compose.material.icons.Icons.Default.History,
                    title = "Nenhuma reprodução ainda",
                    message = "Os títulos que você assistir aparecem aqui, com a data e o progresso.",
                )
            }
            return@Column
        }
        if (shown.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nada com esse nome no histórico.",
                    color = com.aotv.app.ui.theme.TextTertiary,
                    fontSize = 15.sp,
                )
            }
            return@Column
        }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            items(shown, key = { "${it.tmdbId}:${it.type}" }) { h ->
                val pct = if (h.durationMs > 0L)
                    (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else null
                val sub = buildList {
                    if (h.lastEpisode.isNotBlank()) add(h.lastEpisode)
                    add(
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
                            .format(java.util.Date(h.lastWatchedAt)),
                    )
                    if (pct != null) add("${(pct * 100).toInt()}% assistido")
                }.joinToString("  ·  ")
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = pad, vertical = 4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(com.aotv.app.ui.theme.BackgroundCard)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { onItemClick(rebuild(h)) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Default.History, null, tint = com.aotv.app.ui.theme.TextTertiary, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            h.title,
                            color = com.aotv.app.ui.theme.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(sub, color = com.aotv.app.ui.theme.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    IconButton(onClick = { onRemoveRequest(h) }) {
                        Icon(Icons.Default.Delete, "Remover do histórico", tint = com.aotv.app.ui.theme.TextTertiary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Linha do calendário: título + episódio + data, com o que já foi ao ar em estilo apagado. */
@Composable
private fun CalendarRow(
    entry: com.aotv.app.ui.movies.CalendarEntry,
    todayMs: Long,
    onClick: () -> Unit,
) {
    val aired = entry.isAired(todayMs)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(com.aotv.app.ui.theme.BackgroundCard)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Default.CalendarMonth, null,
            tint = if (aired) com.aotv.app.ui.theme.TextTertiary else com.aotv.app.ui.theme.Accent,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                color = if (aired) com.aotv.app.ui.theme.TextSecondary else com.aotv.app.ui.theme.TextPrimary,
                fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(entry.describe(), color = com.aotv.app.ui.theme.TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text(
            entry.dateLabel(todayMs),
            color = if (aired) com.aotv.app.ui.theme.TextTertiary else com.aotv.app.ui.theme.Accent,
            fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

/** Contador da seção (pílula discreta ao lado do título). */
@Composable
private fun CountPill(count: Int) {
    Text(
        if (count == 1) "1 título" else "$count títulos",
        color = com.aotv.app.ui.theme.TextTertiary,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .background(androidx.compose.ui.graphics.Color(0x0DFFFFFF))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** Aviso dentro da seção "Salvos" quando ela está vazia (evita meia tela em branco). */
@Composable
private fun EmptySavedHint(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = com.aotv.app.ui.common.pagePadding())
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0x08FFFFFF))
            .border(
                1.dp,
                com.aotv.app.ui.theme.Border,
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            color = com.aotv.app.ui.theme.TextTertiary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Diálogo de confirmação de remoção (mesmo visual nas duas seções). */
@Composable
private fun ConfirmRemoveDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remover", color = com.aotv.app.ui.theme.LiveRed)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** "Faltam 42 min" a partir do progresso salvo. null quando não há duração conhecida. */
private fun remainingLabel(positionMs: Long, durationMs: Long): String? {
    if (durationMs <= 0L || positionMs <= 0L) return null
    val left = ((durationMs - positionMs) / 60_000L).toInt()
    return when {
        left <= 0 -> "Quase no fim"
        left < 60 -> "Faltam $left min"
        else -> "Faltam ${left / 60}h ${left % 60}min"
    }
}
