package com.aotv.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.DEFAULT_PLAYER_BUTTONS
import com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.SubLangs
import com.aotv.app.ui.movies.PlayerPrefsViewModel
import com.aotv.app.ui.movies.SUB_BG_OPACITIES
import com.aotv.app.ui.movies.SUB_COLORS
import com.aotv.app.ui.movies.SUB_FONTS
import com.aotv.app.ui.movies.StreamPrefsViewModel
import com.aotv.app.ui.movies.effectivePlayerButtons
import com.aotv.app.ui.movies.effectivePlayerTopActions
import com.aotv.app.ui.movies.playerButtonLabel
import com.aotv.app.ui.movies.playerTopActionLabel
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.Danger
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.Ok
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
// DEPENDÊNCIA (outra frente da Fase A): ThemeSelector() vive em ui/theme/ThemePicker.kt.
// Se o arquivo ainda não existe, esta importação é o único ponto a integrar.
import com.aotv.app.ui.theme.ThemeSelector

/** Ids das páginas — são os segmentos da rota `movies_settings/{page}`. */
object SettingsPageIds {
    const val Layout = "layout"
    const val Detalhe = "detalhe"
    const val Streams = "streams"
    const val Conteudo = "conteudo"
    const val Integracoes = "integracoes"
    const val Acompanhamento = "acompanhamento"
    const val Aparencia = "aparencia"
    const val Reproducao = "reproducao"

    /** Páginas escondidas no perfil infantil (igual à tela única antiga). */
    val kidsHidden = setOf(Layout, Conteudo, Integracoes, Aparencia)

    fun title(id: String): String = when (id) {
        Layout -> "Layout"
        Detalhe -> "Página de detalhes"
        Streams -> "Streams"
        Conteudo -> "Conteúdo & Descoberta"
        Integracoes -> "Integrações"
        Acompanhamento -> "Acompanhamento"
        Aparencia -> "Aparência"
        Reproducao -> "Reprodução"
        else -> "Configurações"
    }
}

/**
 * Despacha a página `movies_settings/{page}`. O conteúdo é o mesmo da tela única antiga,
 * só reorganizado — nenhuma setting nova além do seletor de tema (Aparência, Fase A).
 */
@Composable
fun SettingsPageScreen(
    pageId: String,
    onOpenAddons: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    onOpenKodiAddons: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenHomeCustomize: () -> Unit = {},
    onOpenDebrid: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenLive: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onBack: () -> Unit,
    onPlatformChanged: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val pid by viewModel.profileId.collectAsStateWithLifecycle()
    val me = profiles.firstOrNull { it.id == pid } ?: profiles.first()
    if (me.kids && pageId in SettingsPageIds.kidsHidden) {
        LockedPage(SettingsPageIds.title(pageId), onBack)
        return
    }
    when (pageId) {
        SettingsPageIds.Layout -> LayoutPage(onOpenHomeCustomize, onOpenCollections, onBack, viewModel)
        SettingsPageIds.Detalhe -> DetalhePage(onBack, viewModel)
        SettingsPageIds.Streams -> StreamsPage(onBack)
        SettingsPageIds.Conteudo -> ConteudoPage(onOpenAddons, onOpenPlugins, onOpenKodiAddons, onOpenDebrid, onBack, viewModel)
        SettingsPageIds.Integracoes -> IntegracoesPage(onOpenAccounts, onOpenLive, onBack, viewModel)
        SettingsPageIds.Acompanhamento -> AcompanhamentoPage(me.name, me.kids, onOpenProfiles, onBack, viewModel)
        SettingsPageIds.Aparencia -> AparenciaPage(onBack, onPlatformChanged, viewModel)
        SettingsPageIds.Reproducao -> ReproducaoPage(onBack, viewModel)
        else -> PlaceholderPage(
            title = "Configurações",
            sub = null,
            note = "Essa página não existe. Volte e escolha outra.",
            onBack = onBack,
        )
    }
}

/** Moldura comum das páginas: voltar + título + coluna rolável. */
@Composable
private fun SettingsPageScaffold(
    title: String,
    sub: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Na TV quem volta é o Voltar do controle. O botão sai do caminho do foco.
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(title, Modifier.weight(1f), sub = sub)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = pad).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Página travada no perfil infantil (o hub já esconde a entrada; isto é a trava de rota). */
@Composable
private fun LockedPage(title: String, onBack: () -> Unit) {
    SettingsPageScaffold(title, null, onBack) {
        SettingsCard {
            Text(
                "Disponível fora do perfil infantil. Troque de perfil para mexer aqui.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
            )
        }
    }
}

/** Página sem conteúdo existente ainda — nota honesta, sem toggles inventados. */
@Composable
private fun PlaceholderPage(title: String, sub: String?, note: String, onBack: () -> Unit) {
    SettingsPageScaffold(title, sub, onBack) {
        SettingsCard {
            Text(note, fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun SettingsSection(text: String) = com.aotv.app.ui.common.SettingsSection(text)

/** Bloco com fundo de cartão para agrupar controles que não são uma linha só. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(16.dp),
        content = content,
    )
}

// ── Layout ───────────────────────────────────────────────────────────────────

@Composable
private fun LayoutPage(
    onOpenHomeCustomize: () -> Unit,
    onOpenCollections: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val display by viewModel.homeDisplay.collectAsStateWithLifecycle()
    SettingsPageScaffold("Layout", "Personalizar Início e Coleções", onBack) {
        SettingsSection("Início")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Dashboard,
                iconTint = Accent,
                title = "Personalizar Início",
                sub = "Liga, desliga, ordena e renomeia as fileiras. Escolhe o destaque e o catálogo AOTV.",
                onClick = onOpenHomeCustomize,
                grouped = true,
            )
            // Coleções saem primeiro no celular. A TV ganha a tela dela na etapa seguinte.
            if (!com.aotv.app.ui.common.LocalIsTv.current) com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ViewModule,
                iconTint = Accent,
                title = "Coleções",
                sub = "Pastas de estúdios, streamings, franquias e catálogos na Início. Importa e exporta no formato do Nuvio.",
                onClick = onOpenCollections,
                grouped = true,
                divider = true,
            )
        }

        // Pôsteres e densidade (Fase B, frente compacta). O padrão é o compacto:
        // pôster pequeno, fileiras apertadas, sem selos nem etiqueta no pôster.
        // Muda na hora, sem reabrir o app. Vale para o perfil ativo.
        SettingsSection("Pôsteres")
        SettingsCard {
            PrefRow(
                "Tamanho do pôster",
                listOf(
                    com.aotv.app.data.local.PosterSize.SMALL to "Pequeno",
                    com.aotv.app.data.local.PosterSize.MEDIUM to "Médio",
                    com.aotv.app.data.local.PosterSize.LARGE to "Grande",
                ),
                display.posterSize, viewModel::setPosterSize,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Raio dos cantos",
                listOf(8 to "Pequeno", 14 to "Médio", 20 to "Grande"),
                display.posterRadiusDp, viewModel::setPosterRadius,
            )
            Spacer(Modifier.height(14.dp))
            PrefRow(
                "Densidade",
                listOf(
                    com.aotv.app.data.local.HomeDensity.COMPACT to "Compacta",
                    com.aotv.app.data.local.HomeDensity.COMFORTABLE to "Confortável",
                ),
                display.density, viewModel::setHomeDensity,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Mostrar selos nos pôsteres",
                sub = if (display.showPosterBadges) "Etiqueta do topo e selos 4K, Dolby, HDR e DUB visíveis"
                    else "Só nota e ano + gênero. Liga para ver etiqueta e selos de novo",
                // A linha liga e desliga. O Switch fica fora do caminho do controle.
                trailing = { Switch(checked = display.showPosterBadges, onCheckedChange = { viewModel.setShowPosterBadges(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setShowPosterBadges(!display.showPosterBadges) },
                grouped = true,
            )
        }

        // Destaque do topo.
        SettingsSection("Destaque")
        SettingsCard {
            PrefRow(
                "Estilo do herói",
                listOf(
                    com.aotv.app.data.local.HeroStyle.FULL_BLEED to "Tela cheia",
                    com.aotv.app.data.local.HeroStyle.CARD to "Cartão",
                ),
                display.heroStyle, viewModel::setHeroStyle,
            )
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Fundo dinâmico",
                sub = if (display.dynamicBackground) "A Início ganha um gradiente com as cores do destaque"
                    else "Fundo sólido de sempre. Liga para tingir com as cores do destaque",
                trailing = { Switch(checked = display.dynamicBackground, onCheckedChange = { viewModel.setDynamicBackground(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDynamicBackground(!display.dynamicBackground) },
                grouped = true,
            )
        }

        // Títulos das seções.
        SettingsSection("Títulos das seções")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.CheckCircle,
                title = "Sublinhado de destaque",
                sub = if (display.sectionUnderline) "Barra na cor do tema abaixo do título de cada seção"
                    else "Sem barra abaixo dos títulos das seções",
                trailing = { Switch(checked = display.sectionUnderline, onCheckedChange = { viewModel.setSectionUnderline(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSectionUnderline(!display.sectionUnderline) },
                grouped = true,
            )
        }
    }
}

// ── Página de detalhes (Fase B) ─────────────────────────────────────────────

@Composable
private fun DetalhePage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val cinematic by viewModel.detailCinematic.collectAsStateWithLifecycle()
    val posterTransition by viewModel.detailPosterTransition.collectAsStateWithLifecycle()
    val iconActions by viewModel.detailIconActions.collectAsStateWithLifecycle()
    val tabs by viewModel.detailTabs.collectAsStateWithLifecycle()
    val episodeStyle by viewModel.detailEpisodeStyle.collectAsStateWithLifecycle()
    SettingsPageScaffold("Página de detalhes", "Fundo, abas e ações do título", onBack) {
        SettingsSection("Fundo e entrada")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Fundo cinematográfico",
                sub = "Arte desfocada em tela cheia atrás da página. Desligado, fica o backdrop no topo.",
                trailing = { Switch(checked = cinematic, onCheckedChange = { viewModel.setDetailCinematic(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailCinematic(!cinematic) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Visibility,
                title = "Transição do pôster",
                sub = "O pôster cresce e aparece ao abrir os detalhes",
                trailing = { Switch(checked = posterTransition, onCheckedChange = { viewModel.setDetailPosterTransition(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailPosterTransition(!posterTransition) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Ações e organização")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Dashboard,
                title = "Ações em ícones",
                sub = "Minha Lista e Trailer viram ícones com rótulo abaixo do Reproduzir, em vez dos botões em vidro",
                trailing = { Switch(checked = iconActions, onCheckedChange = { viewModel.setDetailIconActions(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailIconActions(!iconActions) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ViewModule,
                title = "Layout de abas",
                sub = "Episódios, Elenco e Mais como isto em abas. Desligado, tudo numa rolagem só.",
                trailing = { Switch(checked = tabs, onCheckedChange = { viewModel.setDetailTabs(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setDetailTabs(!tabs) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Episódios")
        SettingsCard {
            PrefRow(
                "Estilo do card de episódio",
                listOf("horizontal" to "Horizontal com arte", "stacked" to "Empilhado com texto"),
                episodeStyle, viewModel::setDetailEpisodeStyle,
            )
        }
    }
}

// ── Streams (Fase C) ─────────────────────────────────────────────────────────

@Composable
private fun StreamsPage(
    onBack: () -> Unit,
    viewModel: StreamPrefsViewModel = hiltViewModel(),
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val badgesSize by viewModel.badgesSize.collectAsStateWithLifecycle()
    val badgesCodec by viewModel.badgesCodec.collectAsStateWithLifecycle()
    val badgesLang by viewModel.badgesLang.collectAsStateWithLifecycle()
    val badgesPos by viewModel.badgesPos.collectAsStateWithLifecycle()
    val logo by viewModel.logo.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    SettingsPageScaffold("Streams", "Pesquisa, emblemas, logo e fontes fixadas", onBack) {
        SettingsSection("Folha de fontes")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Search,
                title = "Barra de pesquisa nas fontes",
                sub = if (search) "Filtra as fontes digitando o nome do addon"
                    else "Sem barra de pesquisa. A folha mostra todas as fontes",
                trailing = { Switch(checked = search, onCheckedChange = { viewModel.setSearch(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSearch(!search) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Image,
                title = "Logo do addon",
                sub = if (logo) "Mostra o logo do addon ao lado do nome da fonte"
                    else "Só o nome da fonte, sem logo",
                trailing = { Switch(checked = logo, onCheckedChange = { viewModel.setLogo(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setLogo(!logo) },
                grouped = true,
                divider = true,
            )
        }
        SettingsSection("Emblemas")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Mostrar tamanho",
                sub = "Emblema com o tamanho do arquivo (ex.: 2,4 GB), quando a fonte informa",
                trailing = { Switch(checked = badgesSize, onCheckedChange = { viewModel.setBadgesSize(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgesSize(!badgesSize) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Mostrar codec",
                sub = "Emblema com o codec (ex.: HEVC, AV1), quando a fonte informa",
                trailing = { Switch(checked = badgesCodec, onCheckedChange = { viewModel.setBadgesCodec(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgesCodec(!badgesCodec) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Label,
                title = "Mostrar idioma",
                sub = "Emblema Dublado ou Legendado em cada fonte",
                trailing = { Switch(checked = badgesLang, onCheckedChange = { viewModel.setBadgesLang(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setBadgesLang(!badgesLang) },
                grouped = true,
                divider = true,
            )
        }
        SettingsCard {
            PrefRow(
                "Posição dos emblemas",
                listOf("stats" to "No canto", "pills" to "Na linha de pills"),
                badgesPos, viewModel::setBadgesPos,
            )
        }
        SettingsSection("Fontes fixadas")
        Text(
            "Para fixar uma fonte, toque no pin ao lado do nome dela na folha de fontes. As fixadas aparecem primeiro.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        if (pinned.isEmpty()) {
            SettingsCard {
                Text("Nenhuma fonte fixada", fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
            }
        } else {
            com.aotv.app.ui.common.SettingsGroup {
                pinned.forEachIndexed { i, name ->
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.PushPin,
                        title = name,
                        sub = "Aparece primeiro na folha de fontes",
                        trailing = { TextButton(onClick = { viewModel.unpin(name) }) { Text("Desfixar", color = DangerText) } },
                        grouped = true,
                        divider = i > 0,
                    )
                }
            }
        }
    }
}

// ── Conteúdo & Descoberta ────────────────────────────────────────────────────

@Composable
private fun ConteudoPage(
    onOpenAddons: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenKodiAddons: () -> Unit,
    onOpenDebrid: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val addonCount = addons.size
    val sourceStatus by viewModel.sourceStatus.collectAsStateWithLifecycle()
    val checkingHealth by viewModel.checkingHealth.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (sourceStatus.isEmpty()) viewModel.checkSourcesHealth() }
    SettingsPageScaffold("Conteúdo & Descoberta", "Addons, plugins, fontes e Biblioteca", onBack) {
        SettingsSection("Addons")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Extension,
                iconTint = Accent,
                title = "Gerenciar addons",
                sub = (if (addonCount == 0) "Nenhum instalado" else "$addonCount instalado(s)") +
                    ". As fontes vêm dos addons. Instale, ordene e ative cada um.",
                onClick = onOpenAddons,
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Code,
                iconTint = Accent,
                title = "Plugins do Nuvio",
                sub = "Scrapers JavaScript (ex.: os do seu repositório). Instale por manifest, ative cada um e ajuste o timeout.",
                onClick = onOpenPlugins,
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Extension,
                iconTint = Accent,
                title = "Addons Kodi",
                sub = "Instale addons Kodi por ZIP ou URL, ative cada um e ajuste as configurações.",
                onClick = onOpenKodiAddons,
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Bolt,
                iconTint = Accent,
                title = "Debrid",
                sub = "Real-Debrid, TorBox ou Premiumize. Torrent toca na hora por link direto.",
                onClick = onOpenDebrid,
                grouped = true,
                divider = true,
            )
        }

        // Status das fontes. Chama a rede, então gira e trava enquanto verifica.
        SettingsSection("Status das fontes")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = if (checkingHealth) "Verificando…" else "Verificar fontes agora",
                sub = "Confere se o TMDB e os addons estão online (DNS Cloudflare)",
                busy = checkingHealth,
                trailing = null,
                onClick = { viewModel.checkSourcesHealth() },
                grouped = true,
            )
        }
        if (sourceStatus.isNotEmpty()) SettingsCard {
            sourceStatus.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(if (s.online) Ok else Danger))
                    Text(s.name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        if (s.online) "${s.latencyMs} ms" else s.detail,
                        color = if (s.online) TextTertiary else DangerText, fontSize = 13.sp, maxLines = 1,
                    )
                }
            }
        }

        // Biblioteca fica aqui dentro (decisão da Frente 2): o hub continua com as 8 páginas
        // da fork e a limpeza do Continuar assistindo mora junto do conteúdo.
        SettingsSection("Biblioteca")
        var confirmClear by remember { mutableStateOf(false) }
        val context = LocalContext.current
        com.aotv.app.ui.common.SettingsRow(
            icon = Icons.Default.History,
            title = "Limpar Continuar assistindo",
            sub = "Apaga posições salvas de filmes e séries. Minha Lista continua",
            onClick = { confirmClear = true },
        )
        if (confirmClear) AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = BackgroundCard,
            title = { Text("Limpar Continuar assistindo?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("As posições salvas somem. Minha Lista continua.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearContinueWatching()
                    confirmClear = false
                    Toast.makeText(context, "Histórico limpo", Toast.LENGTH_SHORT).show()
                }) { Text("Limpar", color = DangerText) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

// ── Integrações ──────────────────────────────────────────────────────────────

@Composable
private fun IntegracoesPage(
    onOpenAccounts: () -> Unit,
    onOpenLive: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val customTmdbKey by viewModel.customTmdbApiKey.collectAsStateWithLifecycle()
    val hasTmdbKey by viewModel.hasTmdbKey.collectAsStateWithLifecycle()
    var tmdbDraft by remember(customTmdbKey) { mutableStateOf(customTmdbKey) }
    var tmdbVisible by remember(customTmdbKey) { mutableStateOf(false) }
    SettingsPageScaffold("Integrações", "Contas, TMDB e TV ao vivo", onBack) {
        SettingsSection("Contas")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.AccountCircle,
                iconTint = Accent,
                title = "Stremio e Nuvio",
                sub = "Entre na sua conta e traga addons, Minha Lista, progresso e debrid.",
                onClick = onOpenAccounts,
                grouped = true,
            )
        }
        SettingsSection("TV ao vivo")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.LiveTv,
                iconTint = Accent,
                title = "Canais ao vivo",
                sub = "Listas M3U e contas Xtream, com guia de programação.",
                onClick = onOpenLive,
                grouped = true,
            )
        }
        SettingsSection("TMDB")
        Text(
            when {
                customTmdbKey.isNotBlank() -> "Chave pessoal em uso. Você pode trocar a chave ou restaurar o padrão do app."
                hasTmdbKey -> "Chave padrão do app em uso. Se preferir, informe sua própria chave abaixo."
                else -> "Nenhuma chave configurada. Cole sua chave TMDB abaixo para liberar o catálogo, a busca e as fileiras."
            },
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                OutlinedTextField(
                    value = tmdbDraft,
                    onValueChange = { tmdbDraft = it },
                    label = { Text("Sua chave TMDB (opcional)") },
                    placeholder = { Text("Cole sua chave aqui", color = TextTertiary) },
                    visualTransformation = if (tmdbVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = gate,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextTertiary,
                        cursorColor = Accent,
                    ),
                )
            }
            IconButton(
                onClick = { tmdbVisible = !tmdbVisible }, enabled = tmdbDraft.isNotEmpty(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (tmdbVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (tmdbVisible) "Ocultar chave pessoal" else "Mostrar chave pessoal",
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.aotv.app.ui.common.AotvButton(
                "Salvar chave", onClick = {
                    viewModel.setTmdbApiKey(tmdbDraft)
                    tmdbDraft = viewModel.customTmdbApiKey.value
                    tmdbVisible = false
                },
                enabled = tmdbDraft.isNotBlank() && tmdbDraft.trim() != customTmdbKey,
                compact = true, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
            )
            if (customTmdbKey.isNotBlank()) com.aotv.app.ui.common.AotvButton(
                "Usar chave padrão", onClick = { viewModel.setTmdbApiKey(""); tmdbDraft = ""; tmdbVisible = false },
                compact = true,
            )
        }
    }
}

// ── Acompanhamento ───────────────────────────────────────────────────────────

@Composable
private fun AcompanhamentoPage(
    profileName: String,
    isKids: Boolean,
    onOpenProfiles: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    var showSetPin by remember { mutableStateOf(false) }
    val hasPin = viewModel.hasParentalPin
    SettingsPageScaffold("Acompanhamento", "Perfil, PIN dos pais e Trakt", onBack) {
        // Perfil ativo. Tocar abre o "Quem está assistindo?" para trocar ou editar.
        // PIN dos pais. Só mexe quem já está fora do infantil (sair do infantil já pede
        // o PIN quando existe um, então chegar aqui com infantil ativo não rola).
        SettingsSection("Perfil")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Person,
                title = profileName,
                sub = (if (isKids) "Infantil" else "Perfil") + " · trocar ou editar perfis",
                onClick = onOpenProfiles,
                grouped = true,
            )
            if (!isKids) {
                com.aotv.app.ui.common.SettingsRow(
                    icon = Icons.Default.Lock,
                    title = if (hasPin) "Trocar PIN dos pais" else "Criar PIN dos pais",
                    sub = "Trava sair do infantil e abrir Addons, TMDB e Aparelho com o infantil ativo",
                    onClick = { showSetPin = true },
                    grouped = true,
                    divider = true,
                )
            }
        }
        if (showSetPin) SetPinDialog(
            title = if (hasPin) "Trocar PIN dos pais" else "Criar PIN dos pais",
            onSet = { pin -> viewModel.setParentalPin(pin); showSetPin = false },
            onDismiss = { showSetPin = false },
        )

        if (viewModel.traktConfigured) {
            SettingsSection("Trakt")
            SettingsCard { TraktSection(viewModel) }
        }
    }
}

/**
 * Conta do Trakt do perfil ativo. Conectar mostra o código para digitar em trakt.tv/activate.
 * Botão de rede marca na hora, gira "Salvando…" e trava o segundo toque até a resposta.
 */
@Composable
private fun TraktSection(viewModel: SettingsViewModel) {
    val user by viewModel.traktUser.collectAsStateWithLifecycle()
    val busy by viewModel.traktBusy.collectAsStateWithLifecycle()
    val code by viewModel.traktCode.collectAsStateWithLifecycle()
    Text(
        if (user == null) "Junta a Minha Lista, o ponto onde parou e os episódios vistos com o celular, a TV e o site."
        else "Conectado${if (user.isNullOrBlank()) "" else " como $user"}. Sincroniza ao abrir a Início.",
        fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BusyButton(
            label = if (user == null) "Conectar" else "Sincronizar agora",
            busyLabel = busy,
            onClick = { if (user == null) viewModel.traktConnect() else viewModel.traktSync() },
        )
        if (user != null && busy == null) {
            com.aotv.app.ui.common.AotvButton("Desconectar", viewModel::traktLogout, compact = true)
        }
    }
    code?.let { c ->
        AlertDialog(
            onDismissRequest = viewModel::traktCancel,
            containerColor = BackgroundCard,
            title = { Text("Conectar ao Trakt", color = TextPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Abra no celular ou no computador", color = TextSecondary, fontSize = 13.sp)
                    Text(c.url.removePrefix("https://"), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Text("e digite o código", color = TextSecondary, fontSize = 13.sp)
                    Text(c.userCode, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                        Text("Aguardando confirmação…", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::traktCancel) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

/** Botão de chamada de rede. Com [busyLabel] gira, mostra o texto e não aceita outro toque. */
@Composable
private fun BusyButton(label: String, busyLabel: String?, onClick: () -> Unit) {
    com.aotv.app.ui.common.AotvButton(
        label, onClick, icon = Icons.Default.Sync, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
        busy = busyLabel != null, busyLabel = busyLabel ?: "", compact = true,
    )
}

// ── Aparência ────────────────────────────────────────────────────────────────

@Composable
private fun AparenciaPage(
    onBack: () -> Unit,
    onPlatformChanged: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val currentMode by viewModel.platformMode.collectAsStateWithLifecycle()
    val deviceLayout = com.aotv.app.ui.common.currentDeviceLayout(currentMode)
    SettingsPageScaffold("Aparência", "Tema e aparelho", onBack) {
        SettingsSection("Tema")
        // Única setting nova da Fase A. O composable vem de ui/theme/ThemePicker.kt
        // (outra frente); sem ele, esta é a única importação a integrar.
        ThemeSelector()
        SettingsSection("Aparelho")
        Text(
            "Layout atual ${deviceLayout.label}. O modo automático reconhece TV e adapta celular e tablet ao espaço disponível.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        com.aotv.app.ui.common.SettingsGroup {
            PlatformOption(
                selected = currentMode == PlatformMode.AUTO,
                title = "Automático",
                subtitle = "Reconhece TV, tablet e celular e acompanha o tamanho da janela",
                icon = Icons.Default.Devices,
                onClick = { viewModel.setPlatformMode(PlatformMode.AUTO); onPlatformChanged() },
                grouped = true,
            )
            PlatformOption(
                selected = currentMode == PlatformMode.TV,
                title = "Forçar TV",
                subtitle = "Layout de controle remoto com menu lateral",
                icon = Icons.Default.Tv,
                onClick = { viewModel.setPlatformMode(PlatformMode.TV); onPlatformChanged() },
                grouped = true,
                divider = true,
            )
            PlatformOption(
                selected = currentMode == PlatformMode.PHONE,
                title = "Celular ou tablet",
                subtitle = "Layout de toque adaptado ao espaço disponível",
                icon = Icons.Default.PhoneAndroid,
                onClick = { viewModel.setPlatformMode(PlatformMode.PHONE); onPlatformChanged() },
                grouped = true,
                divider = true,
            )
        }
    }
}

/** Opção de aparelho. Selecionada em turquesa com o check. Dentro de um grupo, sem borda própria. */
@Composable
private fun PlatformOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    grouped: Boolean = false,
    divider: Boolean = false,
) {
    com.aotv.app.ui.common.SettingsRow(
        icon = icon,
        title = title,
        sub = subtitle,
        iconTint = if (selected) Accent else TextSecondary,
        modifier = if (selected && !grouped) Modifier.border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier,
        trailing = if (selected) ({ Icon(Icons.Default.CheckCircle, "Selecionado", tint = Accent) }) else null,
        onClick = onClick,
        grouped = grouped,
        divider = divider,
    )
}

// ── Reprodução ───────────────────────────────────────────────────────────────

@Composable
private fun ReproducaoPage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    playerPrefs: PlayerPrefsViewModel = hiltViewModel(),
) {
    val audio by viewModel.audioPref.collectAsStateWithLifecycle()
    val maxQ by viewModel.maxQuality.collectAsStateWithLifecycle()
    val heroTrailer by viewModel.heroTrailer.collectAsStateWithLifecycle()
    val subOn by viewModel.subOn.collectAsStateWithLifecycle()
    val subLang by viewModel.subLang.collectAsStateWithLifecycle()
    var pickLang by remember { mutableStateOf(false) }
    // Botões do player (Fase C). A lista guardada vazia é o padrão; aqui a
    // ordem em vigor, com os desligados no fim para religar sem perder o lugar.
    val savedButtons by playerPrefs.buttons.collectAsStateWithLifecycle()
    val effectiveButtons = effectivePlayerButtons(savedButtons)
    val disabledButtons = DEFAULT_PLAYER_BUTTONS.filterNot { it in effectiveButtons }
    val savedTopActions by playerPrefs.topActions.collectAsStateWithLifecycle()
    val effectiveTopActions = effectivePlayerTopActions(savedTopActions)
    val tapSeek by playerPrefs.tapSeek.collectAsStateWithLifecycle()
    val swipeSeek by playerPrefs.swipeSeek.collectAsStateWithLifecycle()
    val volBoost by playerPrefs.volBoost.collectAsStateWithLifecycle()
    val volBoostLevel by playerPrefs.volBoostLevel.collectAsStateWithLifecycle()
    val mini by playerPrefs.mini.collectAsStateWithLifecycle()
    val autoNext by playerPrefs.autoNext.collectAsStateWithLifecycle()
    val autoNextDelay by playerPrefs.autoNextDelay.collectAsStateWithLifecycle()
    val autoSwitch by playerPrefs.autoSwitch.collectAsStateWithLifecycle()
    val sourceTimeout by playerPrefs.sourceTimeout.collectAsStateWithLifecycle()
    val animeSkipId by playerPrefs.animeSkipClientId.collectAsStateWithLifecycle()
    // Personalização de legenda e velocidade padrão (v1.9.0).
    val subColor by playerPrefs.subColor.collectAsStateWithLifecycle()
    val subBgOpacity by playerPrefs.subBgOpacity.collectAsStateWithLifecycle()
    val subOutline by playerPrefs.subOutline.collectAsStateWithLifecycle()
    val subFont by playerPrefs.subFont.collectAsStateWithLifecycle()
    val playerSpeed by playerPrefs.speed.collectAsStateWithLifecycle()
    var animeSkipDraft by remember(animeSkipId) { mutableStateOf(animeSkipId) }
    SettingsPageScaffold("Reprodução", "Áudio, qualidade, botões do player e legendas", onBack) {
        // Reprodução do perfil ativo. Ordena as fontes e limita a qualidade escolhida no Assistir.
        SettingsSection("Reprodução")
        SettingsCard {
            PrefRow("Áudio preferido", listOf("any" to "Qualquer", "dub" to "Dublado", "leg" to "Legendado"), audio, viewModel::setAudio)
            Spacer(Modifier.height(14.dp))
            PrefRow("Qualidade máxima", listOf(0 to "Sem limite", 2160 to "4K", 1080 to "1080p", 720 to "720p"), maxQ, viewModel::setMaxQuality)
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Movie,
                title = "Trailer no destaque",
                sub = "Toca sem som depois de 5 s parado num título da Início",
                // A linha liga e desliga. O Switch fica fora do caminho do controle.
                trailing = { Switch(checked = heroTrailer, onCheckedChange = { viewModel.setHeroTrailer(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setHeroTrailer(!heroTrailer) },
                grouped = true,
            )
            // Legenda do perfil. Ligada ao abrir e o idioma em que ela liga sozinha no player.
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ClosedCaption,
                title = "Legenda ao abrir",
                sub = if (subOn) "Liga sozinha em ${SubLangs.label(subLang)} quando o título tiver" else "Desligada. Dá para ligar no player, em Legendas",
                trailing = { Switch(checked = subOn, onCheckedChange = { viewModel.setSubOn(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { viewModel.setSubOn(!subOn) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Translate,
                title = "Idioma da legenda",
                sub = SubLangs.label(subLang) + ". Também aparece primeiro na lista do player",
                onClick = { pickLang = true },
                grouped = true,
                divider = true,
            )
        }
        if (pickLang) SubLangDialog(subLang, onPick = { viewModel.setSubLang(it); pickLang = false }, onDismiss = { pickLang = false })

        // Personalização de legenda (v1.9.0): cor, fonte, opacidade do fundo e
        // contorno. Vale para a legenda embutida e para a externa, e também dá
        // para mudar no player, em Legendas. Tamanho e fundo continuam no player.
        SettingsSection("Legendas")
        SettingsCard {
            PrefRow("Cor da legenda", SUB_COLORS, subColor, playerPrefs::setSubColor)
            Spacer(Modifier.height(14.dp))
            PrefRow("Fonte da legenda", SUB_FONTS, subFont, playerPrefs::setSubFont)
            Spacer(Modifier.height(14.dp))
            PrefRow("Opacidade do fundo", SUB_BG_OPACITIES, subBgOpacity, playerPrefs::setSubBgOpacity)
        }
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.ClosedCaption,
                title = "Contorno da legenda",
                sub = if (subOutline) "Contorno preto ao redor do texto quando não há fundo"
                    else "Sem contorno. Sem fundo, o texto pode sumir em cena clara",
                trailing = { Switch(checked = subOutline, onCheckedChange = { playerPrefs.setSubOutline(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSubOutline(!subOutline) },
                grouped = true,
            )
        }

        // Botões do player (Fase C). Sobe e desce reordena, o switch tira da
        // barra. Desligado, o botão some do player mas continua aqui p/ religar.
        SettingsSection("Botões do player")
        SettingsCard {
            (effectiveButtons + disabledButtons).forEachIndexed { i, id ->
                val enabled = id in effectiveButtons
                val pos = effectiveButtons.indexOf(id)
                PlayerButtonRow(
                    label = playerButtonLabel(id),
                    enabled = enabled,
                    canUp = enabled && pos > 0,
                    canDown = enabled && pos >= 0 && pos < effectiveButtons.lastIndex,
                    onUp = { playerPrefs.moveButton(id, -1) },
                    onDown = { playerPrefs.moveButton(id, 1) },
                    onToggle = { playerPrefs.setButtonEnabled(id, it) },
                )
                if (i < DEFAULT_PLAYER_BUTTONS.lastIndex) Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { playerPrefs.resetButtons() }) { Text("Restaurar padrão", color = Accent) }
            }
        }
        SettingsSection("Botões do topo")
        Text(
            "O botão Voltar sempre aparece. Estes são os opcionais do topo do player.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        com.aotv.app.ui.common.SettingsGroup {
            listOf(
                "cast" to Icons.Default.Cast,
                "rotate" to Icons.Default.ScreenRotation,
                "lock" to Icons.Default.Lock,
            ).forEachIndexed { i, (id, icon) ->
                val on = id in effectiveTopActions
                com.aotv.app.ui.common.SettingsRow(
                    icon = icon,
                    title = playerTopActionLabel(id),
                    sub = when (id) {
                        "cast" -> "Manda o vídeo para a TV (Chromecast)"
                        "rotate" -> "Gira a tela do player"
                        else -> "Trava os controles para não tocar sem querer"
                    },
                    trailing = { Switch(checked = on, onCheckedChange = { playerPrefs.setTopActionEnabled(id, it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                    onClick = { playerPrefs.setTopActionEnabled(id, !on) },
                    grouped = true,
                    divider = i > 0,
                )
            }
        }
        SettingsSection("Reprodução no player")
        com.aotv.app.ui.common.SettingsGroup {
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.TouchApp,
                title = "Tocar na barra para buscar",
                sub = if (tapSeek) "Tocar na barra de progresso pula para aquele ponto"
                    else "Só arrastando a barra. Tocar nela não pula",
                trailing = { Switch(checked = tapSeek, onCheckedChange = { playerPrefs.setTapSeek(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setTapSeek(!tapSeek) },
                grouped = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SwapHoriz,
                title = "Deslizar para avançar/retroceder",
                sub = if (swipeSeek) "Deslizar sobre o vídeo avança ou retrocede 10s"
                    else "Deslizar sobre o vídeo não busca",
                trailing = { Switch(checked = swipeSeek, onCheckedChange = { playerPrefs.setSwipeSeek(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setSwipeSeek(!swipeSeek) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.VolumeUp,
                title = "Volume Boost",
                sub = if (volBoost) "Deixa o volume passar de 100%"
                    else "Volume máximo é 100%",
                trailing = { Switch(checked = volBoost, onCheckedChange = { playerPrefs.setVolBoost(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setVolBoost(!volBoost) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.PictureInPictureAlt,
                title = "Mini player",
                sub = if (mini) "Dá para minimizar o player e continuar navegando no app"
                    else "Sem mini player. Sair do player pausa o vídeo",
                trailing = { Switch(checked = mini, onCheckedChange = { playerPrefs.setMini(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setMini(!mini) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.SkipNext,
                title = "'A seguir' automático",
                sub = if (autoNext) "Ao fim do episódio, avança sozinho para o próximo"
                    else "Ao fim do episódio, espera você escolher o próximo",
                trailing = { Switch(checked = autoNext, onCheckedChange = { playerPrefs.setAutoNext(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoNext(!autoNext) },
                grouped = true,
                divider = true,
            )
            com.aotv.app.ui.common.SettingsRow(
                icon = Icons.Default.Refresh,
                title = "Troca automática de fonte",
                sub = if (autoSwitch) "Se a fonte não iniciar ou travar, troca sozinha por outra"
                    else "Se a fonte travar, espera nela sem trocar",
                trailing = { Switch(checked = autoSwitch, onCheckedChange = { playerPrefs.setAutoSwitch(it) }, modifier = Modifier.focusProperties { canFocus = false }) },
                onClick = { playerPrefs.setAutoSwitch(!autoSwitch) },
                grouped = true,
                divider = true,
            )
        }
        // Velocidade padrão (v1.9.0): o player abre nela; mudar no player salva aqui.
        SettingsCard {
            PrefRow(
                "Velocidade padrão",
                listOf(0.5f to "0,5x", 1f to "1x", 1.5f to "1,5x", 2f to "2x"),
                playerSpeed, playerPrefs::setSpeed,
            )
        }
        Column(Modifier.alpha(if (volBoost) 1f else 0.45f)) {
            SettingsCard {
                PrefRow(
                    "Nível do Volume Boost",
                    listOf(1.0f to "100%", 1.25f to "125%", 1.5f to "150%", 1.75f to "175%", 2.0f to "200%"),
                    volBoostLevel, { if (volBoost) playerPrefs.setVolBoostLevel(it) },
                )
                if (!volBoost) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ligue o Volume Boost para mudar o nível.", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Column(Modifier.alpha(if (autoNext) 1f else 0.45f)) {
            SettingsCard {
                PrefRow(
                    "Atraso do \"A seguir\"",
                    listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s"),
                    autoNextDelay, { if (autoNext) playerPrefs.setAutoNextDelay(it) },
                )
                if (!autoNext) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ligue o \"A seguir\" automático para mudar o atraso.", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        Column(Modifier.alpha(if (autoSwitch) 1f else 0.45f)) {
            SettingsCard {
                PrefRow(
                    "Tempo para trocar de fonte",
                    listOf(8 to "8 s", 12 to "12 s", 20 to "20 s", 30 to "30 s", 45 to "45 s", 60 to "60 s"),
                    sourceTimeout, { if (autoSwitch) playerPrefs.setSourceTimeout(it) },
                )
                if (!autoSwitch) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ligue a troca automática de fonte para mudar o tempo.", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }
        SettingsSection("Pular trechos")
        Text(
            "O player busca os trechos (resumo, abertura, encerramento e pós-créditos) no IntroDB e no SkipDB, sem chave. O Anime-Skip (só séries) precisa de um client ID seu — vazio, ele fica desligado.",
            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                OutlinedTextField(
                    value = animeSkipDraft,
                    onValueChange = { animeSkipDraft = it },
                    label = { Text("Client ID do Anime-Skip (opcional)") },
                    placeholder = { Text("Cole seu client ID aqui", color = TextTertiary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = gate,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextTertiary,
                        cursorColor = Accent,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.aotv.app.ui.common.AotvButton(
                "Salvar ID", onClick = { playerPrefs.setAnimeSkipClientId(animeSkipDraft) },
                enabled = animeSkipDraft.trim() != animeSkipId,
                compact = true, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
            )
            if (animeSkipId.isNotBlank()) com.aotv.app.ui.common.AotvButton(
                "Desligar Anime-Skip", onClick = { playerPrefs.setAnimeSkipClientId(""); animeSkipDraft = "" },
                compact = true,
            )
        }
    }
}

/** Linha de um botão do player: rótulo, subir/descer e liga/desliga. */
@Composable
private fun PlayerButtonRow(
    label: String,
    enabled: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (canUp) TextPrimary else TextTertiary)
        }
        IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Descer", tint = if (canDown) TextPrimary else TextTertiary)
        }
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.focusProperties { canFocus = false })
    }
}

/** Lista dos idiomas de legenda. Na TV o foco já começa no idioma atual. */
@Composable
private fun SubLangDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val langs = SubLangs.all
    val start = langs.indexOfFirst { it.key == current }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    val isTv = com.aotv.app.ui.common.LocalIsTv.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Idioma da legenda", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(langs, key = { _, l -> l.key }) { i, l ->
                    val fr = remember { FocusRequester() }
                    LangOption(l.label, l.key == current, Modifier.focusRequester(fr)) { onPick(l.key) }
                    // No celular o toque basta. Foco puxado ali acenderia a borda sem ninguém pedir.
                    if (isTv && i == start) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) } },
    )
}

@Composable
private fun LangOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (focused || selected) AccentSoft else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, "Selecionado", tint = Accent)
    }
}

/** Uma preferência de escolha única em pílulas. Salva na hora (é local, sem rede). */
@Composable
private fun <T> PrefRow(title: String, options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            com.aotv.app.ui.common.FilterChipPill(label, v == value, onClick = { onPick(v) })
        }
    }
}
