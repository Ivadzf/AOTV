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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.SubLangs
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
        SettingsPageIds.Layout -> LayoutPage(onOpenHomeCustomize, onOpenCollections, onBack)
        SettingsPageIds.Detalhe -> PlaceholderPage(
            title = "Página de detalhes",
            sub = "Fundo, abas e ações do título",
            note = "Nada aqui ainda. As opções da página de detalhes — fundo cinematográfico, transição do pôster, ações em ícones, trailer em destaque, layout de abas, notas, Rating Map e modo aleatório — chegam na Fase B.",
            onBack = onBack,
        )
        SettingsPageIds.Streams -> PlaceholderPage(
            title = "Streams",
            sub = "Emblemas, pesquisa e ordem das fontes",
            note = "Nada aqui ainda. As opções de streams — emblemas de tamanho e posição, mostrar no player, plano de fundo, logo do addon e barra de pesquisa — chegam na Fase C.",
            onBack = onBack,
        )
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
) {
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
) {
    val audio by viewModel.audioPref.collectAsStateWithLifecycle()
    val maxQ by viewModel.maxQuality.collectAsStateWithLifecycle()
    val heroTrailer by viewModel.heroTrailer.collectAsStateWithLifecycle()
    val subOn by viewModel.subOn.collectAsStateWithLifecycle()
    val subLang by viewModel.subLang.collectAsStateWithLifecycle()
    var pickLang by remember { mutableStateOf(false) }
    SettingsPageScaffold("Reprodução", "Áudio, qualidade, trailer e legendas", onBack) {
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
