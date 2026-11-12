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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreHoriz
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
    object Accounts : MoviesRoute("movies_accounts")
    object Live : MoviesRoute("movies_live")
    object LiveSources : MoviesRoute("movies_live_sources")
    object LivePlayer : MoviesRoute("movies_live_player/{id}") {
        fun create(id: String) = "movies_live_player/" + android.net.Uri.encode(id)
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

    val isFullscreen = currentRoute?.startsWith("movies_player") == true ||
        currentRoute?.startsWith("movies_detail") == true ||
        currentRoute == MoviesRoute.Search.route ||
        currentRoute == MoviesRoute.Settings.route ||
        currentRoute == MoviesRoute.Profiles.route ||
        currentRoute == MoviesRoute.AddonStore.route ||
        currentRoute == MoviesRoute.HomeCustomize.route ||
        currentRoute == MoviesRoute.Debrid.route ||
        currentRoute == MoviesRoute.Accounts.route ||
        currentRoute == MoviesRoute.LiveSources.route ||
        currentRoute?.startsWith("movies_live_player") == true
    val showTabletRail = deviceLayout == DeviceLayout.TABLET && !isFullscreen

    fun navigateToPlayer(
        embedUrl: String, title: String,
        tmdbId: Int = 0, type: String = "MOVIE", posterUrl: String = "",
        lastEpisode: String = "", startPositionMs: Long = 0, backdropUrl: String = "",
        season: Int = 0, episode: Int = 0, episodeTotal: Int = 0,
        sources: List<MoviePlayerSource> = emptyList(),
        kodiDiag: String = "",
    ) {
        val playId = ++playerIdCounter
        playerCache[playId] = PlayerData(
            embedUrl, title, tmdbId, type, posterUrl, lastEpisode, startPositionMs, backdropUrl,
            season, episode, episodeTotal, sources, kodiDiag,
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
                val openDetail: (MovieItem) -> Unit = { item ->
                    movieCache[cacheKey(item.tmdbId, item.type, item.embedId)] = item
                    navController.navigate(MoviesRoute.Detail.create(item.embedId, item.tmdbId, item.type))
                }
                // TV tem Início própria no desenho do webOS. Celular segue com a dele.
                if (isTV) com.aotv.app.ui.tv.TvHomeScreen(
                    onItemClick = openDetail, navigationOpen = menuOpen,
                    onFolderClick = { c, f -> navController.navigate(MoviesRoute.CollectionFolder.create(c, f)) },
                )
                else MoviesHomeScreen(
                    onItemClick     = openDetail,
                    onSearchClick   = { navController.navigate(MoviesRoute.Search.route) },
                    onSettingsClick = { navController.navigate(MoviesRoute.Settings.route) },
                    onLibraryClick  = { navController.navigate(MoviesRoute.MinhaLista.route) },
                    onCustomize     = { navController.navigate(MoviesRoute.HomeCustomize.route) },
                    onFolderClick   = { c, f -> navController.navigate(MoviesRoute.CollectionFolder.create(c, f)) },
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
                    television = isTV,
                    onBack      = { navController.popBackStack() },
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
                else MinhaListaScreen(onItemClick = onListItem)
            }

            composable(MoviesRoute.Settings.route) {
                tvScale(isTV) {
                    SettingsScreen(
                        onBack            = { navController.popBackStack() },
                        onPlatformChanged = onRestartApp,
                        onOpenAddons      = { navController.navigate(MoviesRoute.Addons.route) },
                        onOpenPlugins     = { navController.navigate(MoviesRoute.Plugins.route) },
                        onOpenKodiAddons  = { navController.navigate(MoviesRoute.KodiAddons.route) },
                        onOpenProfiles    = { navController.navigate(MoviesRoute.Profiles.route) },
                        onOpenHomeCustomize = { navController.navigate(MoviesRoute.HomeCustomize.route) },
                        onOpenDebrid = { navController.navigate(MoviesRoute.Debrid.route) },
                        onOpenAccounts = { navController.navigate(MoviesRoute.Accounts.route) },
                        onOpenLive = { navController.navigate(MoviesRoute.LiveSources.route) },
                        onOpenCollections = { navController.navigate(MoviesRoute.Collections.route) },
                    )
                }
            }

            // Quem está assistindo. Escolher outro perfil remonta a navegação (key no MainActivity).
            composable(MoviesRoute.Profiles.route) {
                tvScale(isTV) { com.aotv.app.ui.settings.ProfilesScreen(onPicked = { navController.popBackStack() }) }
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
                )
            }
            composable(MoviesRoute.LiveSources.route) {
                tvScale(isTV) { com.aotv.app.ui.live.LiveSourcesScreen(onBack = { navController.popBackStack() }) }
            }
            composable(
                MoviesRoute.LivePlayer.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                com.aotv.app.ui.live.LivePlayerScreen(
                    channelId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            // Contas Stremio e Nuvio (login, importar addons, lista, progresso e debrid).
            composable(MoviesRoute.Accounts.route) {
                tvScale(isTV) { com.aotv.app.ui.settings.AccountsScreen(onBack = { navController.popBackStack() }) }
            }

            // Debrid (Real-Debrid, TorBox e Premiumize). Torrent vira link direto.
            composable(MoviesRoute.Debrid.route) {
                tvScale(isTV) { com.aotv.app.ui.settings.DebridScreen(onBack = { navController.popBackStack() }) }
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

    // Barra inferior do celular (Início, Descobrir, Minha Lista, Downloads, Busca e Configurações).
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
    )
    val showTvMenu = isTV && currentRoute in tvMenuRoutes
    val profVm: com.aotv.app.ui.settings.ProfilesViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val profiles by profVm.profiles.collectAsStateWithLifecycle()
    val pid by profVm.profileId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == pid } ?: profiles.first()

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
        // Reserva p/ a barra: altura do conteúdo (~62dp) + inset da navigation bar do
        // sistema, para a última linha do grid não ficar escondida atrás da barra.
        val barReserve = 88.dp + WindowInsets.navigationBars.asPaddingValues()
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
                    add(TvMenuItem(MoviesRoute.MinhaLista.route, "Minha Lista", Icons.Default.Bookmark, go(MoviesRoute.MinhaLista.route)))
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
        if (!hideBar) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                MoviesBottomBar(
                    currentRoute = currentRoute,
                    items = vodItems,
                    onSettings = { navTab(MoviesRoute.Settings.route) },
                    settingsRoute = MoviesRoute.Settings.route,
                    profile = profile,
                    onProfile = { navController.navigate(MoviesRoute.Profiles.route) },
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
) {
    Column(Modifier.fillMaxHeight().width(tabletRailWidth()).background(com.aotv.app.ui.theme.BackgroundElevated)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
        .verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            TouchRailItem(item.icon, item.label, currentRoute == item.route, item.onClick)
        }
        TouchRailItem(Icons.Default.Settings, "Configurações", currentRoute == settingsRoute, onSettings)
        TouchRailItem(Icons.Default.Bookmark, "Perfil", false, onProfile, profile)
    }
}

@Composable
private fun TouchRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit,
    profile: com.aotv.app.data.local.Profile? = null,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
        .background(if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)
        .semantics { this.selected = selected }
        .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick).padding(horizontal = 5.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (profile != null) com.aotv.app.ui.settings.ProfileAvatar(profile, 28.dp)
        else Icon(icon, null, tint = if (selected) com.aotv.app.ui.theme.TextOnLight else com.aotv.app.ui.theme.TextSecondary,
            modifier = Modifier.size(26.dp))
        Text(label, color = if (selected) com.aotv.app.ui.theme.TextOnLight else com.aotv.app.ui.theme.TextSecondary,
            fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Cápsula inferior por toque. As ações extras ficam na lista rolável de Mais. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoviesBottomBar(
    currentRoute: String?,
    items: List<com.aotv.app.ui.common.PhoneNavItem>,
    onSettings: () -> Unit,
    settingsRoute: String,
    profile: com.aotv.app.data.local.Profile,
    onProfile: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    val primaryRoutes = setOf(MoviesRoute.Home.route, MoviesRoute.Descobrir.route, MoviesRoute.MinhaLista.route, MoviesRoute.Search.route)
    val primaryCount = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 300) 3 else 4
    val primary = items.filter { it.route in primaryRoutes }.take(primaryCount)
    val extra = items.filter { it !in primary }
    val moreSelected = currentRoute == settingsRoute || extra.any { it.route == currentRoute }
    // Pílula flutuante estilo Nuvio: vidro translúcido + sombra, ícones menores com rótulo.
    // O blur de fundo vem da translucência do GlassBg: o blur do Compose desfoca o
    // próprio conteúdo (ícones inclusos), não o que está atrás da barra.
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
        .shadow(16.dp, androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp)).background(com.aotv.app.ui.theme.GlassBg)
        .border(1.dp, com.aotv.app.ui.theme.BorderSubtle, androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
        .padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        primary.forEach { item ->
            MoviesBottomBarItem(item.icon, item.label, currentRoute == item.route, item.onClick, Modifier.weight(1f))
        }
        MoviesBottomBarItem(Icons.Default.MoreHoriz, "Mais", moreSelected || more, { more = true }, Modifier.weight(1f), contentDescription = "Mais opções")
    }
    if (more) ModalBottomSheet(onDismissRequest = { more = false }, containerColor = com.aotv.app.ui.theme.BackgroundElevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Navegação", color = com.aotv.app.ui.theme.TextPrimary, fontSize = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            extra.forEach { item ->
                TouchMenuRow(item.icon, item.label, currentRoute == item.route) { more = false; item.onClick() }
            }
            TouchMenuRow(Icons.Default.Settings, "Configurações", currentRoute == settingsRoute) { more = false; onSettings() }
            TouchMenuRow(Icons.Default.Bookmark, "Perfil · "+profile.name, false) { more = false; onProfile() }
        }
    }
}

@Composable
private fun TouchMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        .background(if (selected) com.aotv.app.ui.theme.AccentSoft else com.aotv.app.ui.theme.BackgroundCard)
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = com.aotv.app.ui.theme.TextPrimary, modifier = Modifier.size(24.dp))
        Text(label, color = com.aotv.app.ui.theme.TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

@Composable
private fun MoviesBottomBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Column(modifier.heightIn(min = 56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
        .background(if (selected) androidx.compose.ui.graphics.Color.White.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Transparent)
        .semantics { this.selected = selected }
        .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = contentDescription ?: label,
            tint = if (selected) androidx.compose.ui.graphics.Color.White else com.aotv.app.ui.theme.TextSecondary,
            modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) androidx.compose.ui.graphics.Color.White else com.aotv.app.ui.theme.TextSecondary,
            fontSize = 10.sp, lineHeight = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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
) {
    val vm: com.aotv.app.ui.movies.MinhaListaViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val recent by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val recentArtwork by vm.recentArtwork.collectAsStateWithLifecycle()

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
        val pad = com.aotv.app.ui.common.pagePadding()
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val availableWidth = config.screenWidthDp - if (config.screenWidthDp >= 600 && config.screenHeightDp >= 480) tabletRailWidth().value.toInt() else 0
        val perRow = com.aotv.app.ui.common.posterColumns(availableWidth, config.fontScale)

        if (watchlist.isEmpty() && recent.isEmpty()) {
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
                            )
                        }
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
