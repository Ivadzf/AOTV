package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.data.local.Profile
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.FocusRing
import com.aotv.app.ui.theme.Ok
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Hub das Configurações (redesign Lote 6, v1.15.0, formato Nuvio): busca no
 * topo, cartão do perfil e as 17 páginas em 8 cartões por grupo — Conta,
 * Aparência, Layout, Conteúdo & Descoberta, Reprodução, Integrações,
 * Acompanhamento e Conexão (VPN + Watch Party, Lote 10, + Social Hub,
 * Lote 19). Cada página é uma
 * `?anchor=` opcional p/ a busca profunda — ver SettingsPages.kt).
 *
 * A busca profunda casa também cada OPÇÃO dentro das páginas (ver
 * SettingsSearchIndex.kt) e abre a página certa, rolando até a seção.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenOption: (String, String?) -> Unit = { page, _ -> onOpenPage(page) },
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val currentMode by viewModel.platformMode.collectAsStateWithLifecycle()
    val deviceLayout = com.aotv.app.ui.common.currentDeviceLayout(currentMode)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val pid by viewModel.profileId.collectAsStateWithLifecycle()
    val me = profiles.firstOrNull { it.id == pid } ?: profiles.first()
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val addonCount = addons.size
    val sourceStatus by viewModel.sourceStatus.collectAsStateWithLifecycle()
    val checkingHealth by viewModel.checkingHealth.collectAsStateWithLifecycle()
    val traktUser by viewModel.traktUser.collectAsStateWithLifecycle()
    // Verifica automaticamente ao abrir as Configurações (uma vez).
    LaunchedEffect(Unit) { if (sourceStatus.isEmpty()) viewModel.checkSourcesHealth() }
    val pad = com.aotv.app.ui.common.pagePadding()
    // Busca das configurações (estilo Nuvio). Filtra as páginas por título, descrição e palavras-chave,
    // e as opções dentro delas pelo índice de SettingsSearchIndex.kt.
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    fun match(vararg texts: String) = q.isEmpty() || texts.any { it.contains(q, ignoreCase = true) }
    // Páginas escondidas no perfil infantil — igual à tela única antiga, que escondia
    // Início, Addons, Contas/TMDB/TV ao vivo e Aparelho com o infantil ativo.
    val pageResults = hubEntries.filter { !me.kids || !it.kidsHidden }
        .filter { match(it.title, it.sub, *it.keywords.toTypedArray()) }
    val optionResults = remember(q, me.kids) { searchSettingOptions(q, me.kids) }

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
            com.aotv.app.ui.common.PageHeader("Configurações", Modifier.weight(1f), sub = "Perfil, reprodução, addons e aparelho")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar configurações...", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
            trailingIcon = if (query.isNotEmpty()) ({
                IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Limpar busca", tint = TextSecondary)
                }
            }) else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedPlaceholderColor = TextTertiary,
                unfocusedPlaceholderColor = TextTertiary,
                cursorColor = Accent,
            ),
        )
        // Resumo à direita em tela larga, embaixo do conteúdo em tela estreita (.set-summary).
        val summary: @Composable () -> Unit = {
            SettingsSummary(
                profile = me.name + if (me.kids) " (infantil)" else "",
                addonCount = addonCount,
                online = sourceStatus.count { it.online },
                total = sourceStatus.size,
                checking = checkingHealth,
                platform = when (currentMode) {
                    PlatformMode.TV -> "TV"
                    PlatformMode.PHONE -> "Toque · ${deviceLayout.label}"
                    PlatformMode.AUTO -> "Automático · ${deviceLayout.label}"
                },
                trakt = if (viewModel.traktConfigured) traktUser else null,
                traktConfigured = viewModel.traktConfigured,
                traktConnected = viewModel.traktConnected,
            )
        }
        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = pad).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (q.isEmpty()) {
                    // Cartão do perfil (estilo Nuvio): tocar abre "Quem está assistindo?".
                    ProfileCard(me, onOpenProfiles)
                    // Cartões por grupo, na ordem do Nuvio. Cartão vazio no perfil
                    // infantil (só páginas travadas) some.
                    hubCards.forEach { cardIds ->
                        val rows = cardIds.mapNotNull { hubEntriesById[it] }
                            .filter { !me.kids || !it.kidsHidden }
                        if (rows.isNotEmpty()) {
                            com.aotv.app.ui.common.SettingsGroup {
                                rows.forEachIndexed { i, entry ->
                                    com.aotv.app.ui.common.SettingsRow(
                                        icon = entry.icon,
                                        iconTint = Accent,
                                        title = entry.title,
                                        sub = entry.sub,
                                        onClick = { onOpenPage(entry.id) },
                                        grouped = true,
                                        divider = i > 0,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (optionResults.isNotEmpty()) {
                        com.aotv.app.ui.common.Eyebrow("Opções", Modifier.padding(start = 4.dp, top = 8.dp))
                        com.aotv.app.ui.common.SettingsGroup {
                            optionResults.forEachIndexed { i, option ->
                                com.aotv.app.ui.common.SettingsRow(
                                    icon = hubEntriesById[option.pageId]?.icon ?: Icons.Default.Search,
                                    iconTint = Accent,
                                    title = option.title,
                                    sub = option.sub + " · em " + SettingsPageIds.title(option.pageId),
                                    onClick = { onOpenOption(option.pageId, option.anchor) },
                                    grouped = true,
                                    divider = i > 0,
                                )
                            }
                        }
                    }
                    if (pageResults.isNotEmpty()) {
                        com.aotv.app.ui.common.Eyebrow("Páginas", Modifier.padding(start = 4.dp, top = 8.dp))
                        com.aotv.app.ui.common.SettingsGroup {
                            pageResults.forEachIndexed { i, entry ->
                                com.aotv.app.ui.common.SettingsRow(
                                    icon = entry.icon,
                                    iconTint = Accent,
                                    title = entry.title,
                                    sub = entry.sub,
                                    onClick = { onOpenPage(entry.id) },
                                    grouped = true,
                                    divider = i > 0,
                                )
                            }
                        }
                    }
                    if (optionResults.isEmpty() && pageResults.isEmpty()) {
                        Text(
                            "Nenhuma opção ou página bate com \"$q\".",
                            fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                below?.let { Spacer(Modifier.height(18.dp)); it() }
            }
        }
    }
}

/** Cartão do perfil ativo no topo do hub (estilo Nuvio). Tocar troca ou edita perfis. */
@Composable
private fun ProfileCard(profile: Profile, onOpenProfiles: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(BackgroundCard2)
            .then(if (focused) Modifier.border(2.dp, FocusRing, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onOpenProfiles)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(profile, 56.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                profile.name + if (profile.kids) " (infantil)" else "",
                color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Trocar ou editar perfis",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
    }
}

/** Uma página do hub. [kidsHidden] esconde a entrada no perfil infantil. */
private data class HubEntry(
    val id: String,
    val title: String,
    val sub: String,
    val icon: ImageVector,
    val keywords: List<String>,
    val kidsHidden: Boolean = false,
)

/** As 17 páginas. Os ids são as rotas `movies_settings/{id}`. */
private val hubEntries = listOf(
    HubEntry(
        id = SettingsPageIds.Layout,
        title = "Layout",
        sub = "Personalizar Início e Coleções",
        icon = Icons.Default.Dashboard,
        keywords = listOf("Início", "Personalizar", "fileiras", "Coleções", "pastas", "destaque", "catálogo"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Detalhe,
        title = "Página de detalhes",
        sub = "Fundo, abas e ações do título",
        icon = Icons.Default.Movie,
        keywords = listOf("detalhes", "pôster", "trailer", "abas", "notas", "aleatório", "cinematográfico"),
    ),
    HubEntry(
        id = SettingsPageIds.Streams,
        title = "Streams",
        sub = "Emblemas, pesquisa e ordem das fontes",
        icon = Icons.Default.PlayArrow,
        keywords = listOf("streams", "fontes", "emblemas", "qualidade", "player", "ordem"),
    ),
    HubEntry(
        id = SettingsPageIds.Conteudo,
        title = "Conteúdo & Descoberta",
        sub = "Addons, plugins, fontes e Biblioteca",
        icon = Icons.Default.Extension,
        keywords = listOf("Addons", "Gerenciar", "Plugins", "Kodi", "Debrid", "Real-Debrid", "TorBox", "Premiumize", "Status", "fontes", "Verificar", "online", "Biblioteca", "Continuar assistindo", "Histórico", "Limpar"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Integracoes,
        title = "Integrações",
        sub = "Contas e TV ao vivo",
        icon = Icons.Default.AccountCircle,
        keywords = listOf("Contas", "Stremio", "Nuvio", "TV ao vivo", "Canais", "M3U", "Xtream", "guia"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Acompanhamento,
        title = "Acompanhamento",
        sub = "Perfil, PIN dos pais, TMDB e Trakt",
        icon = Icons.Default.Person,
        keywords = listOf("Perfil", "trocar", "editar", "PIN", "pais", "TMDB", "chave", "Trakt", "sincronizar", "Minha Lista"),
    ),
    HubEntry(
        id = SettingsPageIds.Aparencia,
        title = "Aparência",
        sub = "Tema e aparelho",
        icon = Icons.Default.Palette,
        keywords = listOf("Aparelho", "Automático", "TV", "Celular", "tablet", "Layout", "Tema", "cores"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Diagnostico,
        title = "Diagnóstico",
        sub = "Aparelho, fontes e quadros — tudo medido",
        icon = Icons.Default.Speed,
        keywords = listOf("Diagnóstico", "aparelho", "memória", "quadros", "addons", "fontes", "ajuste", "buffer", "rede"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Reproducao,
        title = "Reprodução",
        sub = "Áudio, qualidade, trailer e legendas",
        icon = Icons.Default.ClosedCaption,
        keywords = listOf("Reprodução", "Áudio", "Qualidade", "Trailer", "destaque", "Legenda", "idioma", "buffer", "Buffer"),
    ),
    // Remodel v1.44.0 (Frente 2): aba própria do MPV — motor, opções do
    // Libmpv, scripts Lua e Melhorar imagem saíram de Reprodução.
    HubEntry(
        id = SettingsPageIds.Mpv,
        title = "MPV",
        sub = "Motor do player, Libmpv e Melhorar imagem",
        icon = Icons.Default.Tune,
        keywords = listOf("MPV", "Libmpv", "motor", "player", "Melhorar imagem", "upscaler", "Anime4K", "scripts", "Lua", "display-fps"),
    ),
    HubEntry(
        id = SettingsPageIds.Downloads,
        title = "Downloads",
        sub = "Só Wi-Fi, pasta e dados móveis",
        icon = Icons.Default.Download,
        keywords = listOf("Downloads", "Wi-Fi", "pasta", "dados móveis", "limite", "offline", "fila"),
    ),
    HubEntry(
        id = SettingsPageIds.Backup,
        title = "Backup e restauração",
        sub = "Arquivo JSON ou Wi-Fi direto",
        icon = Icons.Default.Backup,
        keywords = listOf("Backup", "restauração", "exportar", "importar", "JSON", "Wi-Fi", "QR", "PIN", "sincronizar"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Trackers,
        title = "Trackers e Sincronização",
        sub = "Simkl e Trakt por código e QR",
        icon = Icons.Default.Sync,
        keywords = listOf("Trackers", "Simkl", "Trakt", "sincronização", "scrobble", "PIN", "QR", "client ID"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Vpn,
        title = "VPN",
        sub = "Perfis WireGuard e OpenVPN do seu provedor",
        icon = Icons.Default.VpnKey,
        keywords = listOf("VPN", "WireGuard", "OpenVPN", "perfil", "conf", "ovpn", "túnel", "privacidade"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.WatchParty,
        title = "Watch Party",
        sub = "Assista junto com um código",
        icon = Icons.Default.Group,
        keywords = listOf("Watch Party", "assistir junto", "código", "convite", "sessão", "grupo", "sincronizar"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.SocialHub,
        title = "Social Hub",
        sub = "Amigos, film night e couch match — tudo local",
        icon = Icons.Default.People,
        keywords = listOf("Social Hub", "amigos", "film night", "couch match", "assistir junto", "grupo", "noite de filme"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.RemoteTv,
        title = "Controle remoto e TV",
        sub = "QR, Send to TV, Watch Next, gamepad e player externo",
        icon = Icons.Default.Cast,
        keywords = listOf("Controle remoto", "QR", "TV", "Send to TV", "Chromecast", "DLNA", "AirPlay", "Watch Next", "gamepad", "controle", "player externo", "handoff", "pareamento"),
        kidsHidden = true,
    ),
)

private val hubEntriesById = hubEntries.associateBy { it.id }

/**
 * Os 8 cartões do hub, na ordem do Nuvio — cada cartão é um grupo de páginas:
 * Conta (só o backup real — sem fingir sync), Aparência (com Diagnóstico,
 * Lote 11), Layout (Início, detalhes e streams, como o "Layout" do Nuvio),
 * Conteúdo & Descoberta, Reprodução (com Downloads), Integrações,
 * Acompanhamento (com Trackers) e Conexão (VPN + Watch Party, Lote 10, +
 * Social Hub, Lote 19). Reprodução (com MPV e Downloads) — a aba MPV
 * é do remodel v1.44.0 (Frente 2).
 * Sem "Notificações" (não existe no AOTV) e sem "Avançadas" (sem conteúdo
 * real) — grupos omitidos de propósito.
 */
private val hubCards: List<List<String>> = listOf(
    listOf(SettingsPageIds.Backup),
    listOf(SettingsPageIds.Aparencia, SettingsPageIds.Diagnostico),
    listOf(SettingsPageIds.Layout, SettingsPageIds.Detalhe, SettingsPageIds.Streams),
    listOf(SettingsPageIds.Conteudo),
    listOf(SettingsPageIds.Reproducao, SettingsPageIds.Mpv, SettingsPageIds.Downloads),
    listOf(SettingsPageIds.Integracoes),
    listOf(SettingsPageIds.Acompanhamento, SettingsPageIds.Trackers),
    listOf(SettingsPageIds.Vpn, SettingsPageIds.WatchParty, SettingsPageIds.SocialHub, SettingsPageIds.RemoteTv),
)

/** Resumo das configurações (.set-summary). Logo, versão e o estado do que importa. */
@Composable
private fun SettingsSummary(
    profile: String,
    addonCount: Int,
    online: Int,
    total: Int,
    checking: Boolean,
    platform: String,
    trakt: String?,
    traktConfigured: Boolean,
    traktConnected: Boolean,
) {
    com.aotv.app.ui.common.SummaryCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.aotv.app.R.drawable.aotv_logo), "AOTV",
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.padding(start = 14.dp)) {
                Text("AOTV", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Versão ${com.aotv.app.BuildConfig.VERSION_NAME}", color = TextTertiary, fontSize = 13.sp)
            }
        }
        com.aotv.app.ui.common.SummaryRow("Perfil", profile)
        com.aotv.app.ui.common.SummaryRow("Addons", if (addonCount == 0) "Nenhum" else "$addonCount instalados")
        com.aotv.app.ui.common.SummaryRow(
            "Fontes online",
            if (checking) "Verificando…" else if (total == 0) "Sem teste" else "$online de $total",
            if (checking || total == 0) TextPrimary else if (online == total) Ok else DangerText,
        )
        com.aotv.app.ui.common.SummaryRow("Aparelho", platform)
        if (traktConfigured) com.aotv.app.ui.common.SummaryRow("Trakt", if (traktConnected) trakt?.ifBlank { "Conectado" } ?: "Conectado" else "Desconectado", if (traktConnected) Ok else TextPrimary)
        Text(
            "As escolhas de reprodução valem só para o perfil ativo.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp),
        )
    }
}
