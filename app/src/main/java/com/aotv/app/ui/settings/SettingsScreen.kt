package com.aotv.app.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.PlatformMode
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.Ok
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Hub das Configurações (Fase A, Frente 2). Lista as 8 páginas no estilo da fork;
 * cada página é uma rota `movies_settings/{page}` (ver SettingsPages.kt).
 * A busca filtra as entradas por título, descrição e palavras-chave e abre a página.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit = {},
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
    // Busca das configurações (estilo Nuvio). Filtra as páginas por título, descrição e palavras-chave.
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    fun match(vararg texts: String) = q.isEmpty() || texts.any { it.contains(q, ignoreCase = true) }
    // Páginas escondidas no perfil infantil — igual à tela única antiga, que escondia
    // Início, Addons, Contas/TMDB/TV ao vivo e Aparelho com o infantil ativo.
    val entries = hubEntries.filter { !me.kids || !it.kidsHidden }
        .filter { match(it.title, it.sub, *it.keywords.toTypedArray()) }

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
            )
        }
        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = pad).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        "Nenhuma página bate com \"$q\".",
                        fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    com.aotv.app.ui.common.SettingsGroup {
                        entries.forEachIndexed { i, entry ->
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
                below?.let { Spacer(Modifier.height(18.dp)); it() }
            }
        }
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

/** As 8 páginas, na ordem da fork. Os ids são as rotas `movies_settings/{id}`. */
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
        sub = "Contas, TMDB e TV ao vivo",
        icon = Icons.Default.AccountCircle,
        keywords = listOf("Contas", "Stremio", "Nuvio", "TMDB", "chave", "TV ao vivo", "Canais", "M3U", "Xtream", "guia"),
        kidsHidden = true,
    ),
    HubEntry(
        id = SettingsPageIds.Acompanhamento,
        title = "Acompanhamento",
        sub = "Perfil, PIN dos pais e Trakt",
        icon = Icons.Default.Person,
        keywords = listOf("Perfil", "trocar", "editar", "PIN", "pais", "Trakt", "sincronizar", "Minha Lista"),
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
        id = SettingsPageIds.Reproducao,
        title = "Reprodução",
        sub = "Áudio, qualidade, trailer e legendas",
        icon = Icons.Default.ClosedCaption,
        keywords = listOf("Reprodução", "Áudio", "Qualidade", "Trailer", "destaque", "Legenda", "idioma"),
    ),
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
        if (traktConfigured) com.aotv.app.ui.common.SummaryRow("Trakt", trakt?.ifBlank { "Conectado" } ?: "Desconectado", if (trakt != null) Ok else TextPrimary)
        Text(
            "As escolhas de reprodução valem só para o perfil ativo.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp),
        )
    }
}
