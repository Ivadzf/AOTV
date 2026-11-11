package com.aotv.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.local.AddonEntry
import com.aotv.app.data.remote.AddonManifest
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/**
 * Gerenciador de addons (celular). Instala por URL de manifest igual o Stremio, lista cada
 * addon num card separado com logo/nome/descrição, e deixa ordenar a prioridade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonManagerScreen(
    onBack: () -> Unit,
    onExplore: () -> Unit = {},
    onCustomizeHome: () -> Unit = {},
    viewModel: AddonsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    // Addon aberto pra reconfigurar no site (token vencido, opção nova etc). null = fechado.
    var siteFor by remember { mutableStateOf<AddonEntry?>(null) }
    val ativos = state.addons.count { it.enabled }

    // Só limpa o campo quando a instalação termina com SUCESSO. Em erro o texto digitado fica
    // (na TV não dá pra colar de novo, perder o que já tinha digitado dói mais).
    LaunchedEffect(state.installing) {
        if (!state.installing && state.error == null) input = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .topInsetsPadding(),
    ) {
        // ── Topo ──
        val refreshing = state.loading.isNotEmpty()
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = com.aotv.app.ui.common.pagePadding(), top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Na TV quem volta é o Voltar do controle. O botão sai do caminho do foco.
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(
                "Addons", Modifier.weight(1f),
                sub = if (state.addons.isEmpty()) "Nenhum instalado" else "${state.addons.size} instalados · $ativos ativos",
            )
            // Atualizar lê os manifests na rede. Gira e trava até terminar.
            com.aotv.app.ui.common.GlassCircleButton(
                Icons.Default.Refresh, { viewModel.refreshAll() },
                contentDescription = if (refreshing) "Atualizando addons" else "Atualizar addons",
                busy = refreshing,
            )
        }

        val wide = com.aotv.app.ui.common.isWideLayout()
        val pad = com.aotv.app.ui.common.pagePadding()
        val install: @Composable () -> Unit = {
            InstallCard(
                input = input,
                onInputChange = { input = it; if (state.error != null || state.installedNow != null) viewModel.clearError() },
                installing = state.installing,
                error = state.error,
                installed = state.installedNow,
                onInstall = {
                    keyboard?.hide()
                    viewModel.install(input)
                },
            )
        }
        // Em tela larga a lista fica à esquerda e o painel de instalar fica fixo à direita.
        Row(Modifier.fillMaxSize()) {
        if (wide) Column(
            Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(start = pad, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExploreEntry(onExplore)
            install()
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!wide) {
                // ── Explorar o catálogo da comunidade ──
                item { ExploreEntry(onExplore) }
                // ── Instalar ──
                item { install() }
            }

            // ── Catálogo próprio do app, fixo no topo e desligável como um addon ──
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                    com.aotv.app.ui.common.Eyebrow("Catálogo do app")
                }
            }
            item {
                AotvCatalogCard(
                    on = state.catalogOn,
                    rowsOn = state.aotvRowsOn,
                    onToggle = { viewModel.setCatalogOn(!state.catalogOn) },
                    onCustomize = onCustomizeHome,
                )
            }

            // ── Cabeçalho da lista ──
            if (state.addons.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                        com.aotv.app.ui.common.Eyebrow("Instalados")
                        Text(
                            "A ordem é a prioridade. O primeiro da lista é tentado primeiro.",
                            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            itemsIndexed(state.addons, key = { _, a -> a.url }) { index, addon ->
                AddonCard(
                    position = index + 1,
                    addon = addon,
                    manifest = state.manifests[addon.url],
                    loading = state.loading.contains(addon.url),
                    offline = state.offline.contains(addon.url),
                    isFirst = index == 0,
                    isLast = index == state.addons.lastIndex,
                    onToggle = { viewModel.toggle(addon.url) },
                    onUp = { viewModel.moveUp(index) },
                    onDown = { viewModel.moveDown(index) },
                    onRemove = { viewModel.remove(addon.url) },
                    onEdit = { novo -> viewModel.editUrl(addon.url, novo) },
                    onConfigureSite = { siteFor = addon },
                    homeKeys = state.homeCatalogKeys,
                    onToggleCatalog = { c ->
                        viewModel.toggleHomeCatalog(addon.url, state.manifests[addon.url]?.name.orEmpty(), c)
                    },
                )
            }

            if (state.addons.isEmpty()) {
                item { EmptyAddons() }
            }
        }
        }
    }

    siteFor?.let { a ->
        // Fecha a página assim que a reconfiguração (editUrl) terminar.
        var started by remember(a.url) { mutableStateOf(false) }
        LaunchedEffect(state.installing) { if (started && !state.installing) siteFor = null }
        AddonSiteConfig(
            name = state.manifests[a.url]?.name?.takeIf { it.isNotBlank() } ?: a.url,
            configureUrl = com.aotv.app.data.remote.AddonLinks.baseOf(a.url) + "/configure",
            plainUrl = null,
            busy = state.installing,
            onInstall = { url -> started = true; viewModel.editUrl(a.url, url) },
            onClose = { siteFor = null },
        )
    }
}

// ── Card de instalação ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallCard(
    input: String,
    onInputChange: (String) -> Unit,
    installing: Boolean,
    error: String?,
    installed: String?,
    onInstall: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(AccentSoft, Color(0xFF141414))))
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(AccentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Link, null, tint = Accent, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    "Instalar addon",
                    color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text("Cole o link do manifest", color = TextTertiary, fontSize = 13.sp)
            }
        }

        com.aotv.app.ui.common.TvTextFieldGate(Modifier.fillMaxWidth()) { gate ->
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                placeholder = {
                    Text("https://exemplo.com/manifest.json", color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = Inter),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!installing) onInstall() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    cursorColor = Accent,
                ),
                modifier = gate,
            )
        }

        // Instalar consulta o manifest na rede. Gira "Verificando…" e trava o segundo toque.
        com.aotv.app.ui.common.AotvButton(
            "Instalar", onInstall, Modifier.fillMaxWidth(),
            icon = Icons.Default.Download, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
            enabled = input.isNotBlank() || installing, busy = installing, busyLabel = "Verificando…",
        )

        // Feedback (erro / sucesso)
        AnimatedVisibility(error != null || installed != null, enter = fadeIn(), exit = fadeOut()) {
            val ok = installed != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (ok) Success else LiveRed).copy(alpha = 0.13f))
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    null, tint = if (ok) Success else LiveRed, modifier = Modifier.size(18.dp),
                )
                Text(
                    if (ok) "$installed instalado" else (error ?: ""),
                    color = if (ok) Success else LiveRed,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            "Aceita link stremio:// e endereço sem /manifest.json no fim.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
        )
    }
}

// ── Card de um addon ──────────────────────────────────────────────────────────
@Composable
private fun AddonCard(
    position: Int,
    addon: AddonEntry,
    manifest: AddonManifest?,
    loading: Boolean,
    offline: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onEdit: (String) -> Unit,
    onConfigureSite: () -> Unit,
    homeKeys: Set<String>,
    onToggleCatalog: (com.aotv.app.data.remote.AddonCatalog) -> Unit,
) {
    var showCatalogs by remember(addon.url) { mutableStateOf(false) }
    val catalogs = manifest?.catalogs.orEmpty()
    if (showCatalogs && catalogs.isNotEmpty()) {
        HomeCatalogsDialog(
            addonName = manifest?.name.orEmpty(),
            base = com.aotv.app.data.remote.AddonLinks.baseOf(addon.url),
            catalogs = catalogs,
            homeKeys = homeKeys,
            onToggle = onToggleCatalog,
            onDismiss = { showCatalogs = false },
        )
    }
    var confirmRemove by remember(addon.url) { mutableStateOf(false) }
    // Edição da URL aberta? Guarda o texto em andamento.
    var editing by remember(addon.url) { mutableStateOf(false) }
    var editText by remember(addon.url) { mutableStateOf(addon.url) }
    val host = remember(addon.url) { runCatching { java.net.URI(addon.url).host }.getOrNull().orEmpty() }
    val contentAlpha by animateFloatAsState(if (addon.enabled) 1f else 0.45f, tween(180), label = "addonAlpha")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Logo + posição na prioridade
            Box {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .alpha(contentAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    val logo = manifest?.logo.orEmpty()
                    if (logo.isNotBlank()) {
                        AsyncImage(
                            model = logo,
                            contentDescription = manifest?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                        )
                    } else {
                        Icon(Icons.Default.Extension, null, tint = TextTertiary, modifier = Modifier.size(24.dp))
                    }
                }
                // Badge da prioridade
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (addon.enabled) Accent else BackgroundCard2)
                        .border(2.dp, BackgroundCard2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$position",
                        color = if (addon.enabled) Color.White else TextTertiary,
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = ManropeFontFamily,
                    )
                }
            }

            Column(Modifier.weight(1f).alpha(contentAlpha)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        manifest?.name?.takeIf { it.isNotBlank() } ?: host.ifBlank { "Addon" },
                        color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    manifest?.version?.takeIf { it.isNotBlank() }?.let { TinyPill("v$it", TextTertiary) }
                    // Estado como no .addon-status do webOS.
                    TinyPill(if (addon.enabled) "Ativo" else "Desligado", if (addon.enabled) Ok else DangerText)
                }
                Text(
                    host,
                    color = TextTertiary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Ativo/inativo
            Switch(
                checked = addon.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BackgroundCard2,
                    uncheckedBorderColor = Border,
                ),
            )
        }

        // Descrição
        val desc = manifest?.description.orEmpty()
        if (desc.isNotBlank()) {
            Text(
                desc,
                color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha),
            )
        }

        // Estado + tipos/recursos
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                loading -> {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Text("Lendo manifest…", color = TextTertiary, fontSize = 13.sp)
                }
                offline -> {
                    Icon(Icons.Default.CloudOff, null, tint = LiveRed, modifier = Modifier.size(14.dp))
                    Text("Não respondeu", color = DangerText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                else -> {
                    manifest?.types.orEmpty().take(3).forEach { t ->
                        TinyPill(if (t == "movie") "Filmes" else if (t == "series") "Séries" else t, Accent)
                    }
                    manifest?.resources.orEmpty().take(2).forEach { r -> TinyPill(r, TextTertiary) }
                }
            }
        }

        HorizontalDivider(color = BorderSubtle)

        // Ações: prioridade numa linha, o resto (que cresceu com "Configurar no site") noutra
        // que quebra sozinha em linhas extras se não couber — numa linha só lado a lado
        // estourava a largura do celular.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUp, enabled = !isFirst, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.ArrowUpward, "Subir prioridade",
                    tint = if (isFirst) TextTertiary.copy(alpha = 0.35f) else TextSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(onClick = onDown, enabled = !isLast, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.ArrowDownward, "Descer prioridade",
                    tint = if (isLast) TextTertiary.copy(alpha = 0.35f) else TextSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
            if (catalogs.isNotEmpty()) {
                val on = catalogs.count { "${com.aotv.app.data.remote.AddonLinks.baseOf(addon.url)}|${it.type}|${it.id}" in homeKeys }
                Text(
                    if (on > 0) "Na Início ($on)" else "Na Início",
                    color = if (on > 0) Accent else TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showCatalogs = true }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (confirmRemove) {
                Text(
                    "Cancelar",
                    color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { confirmRemove = false }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                )
                Text(
                    "Remover mesmo",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(LiveRed)
                        .clickable { onRemove() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                )
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { confirmRemove = true }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Delete, null, tint = LiveRed, modifier = Modifier.size(16.dp))
                    Text("Remover", color = LiveRed, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily)
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onConfigureSite() }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                Text(
                    "Configurar no site",
                    color = TextSecondary, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { editing = !editing; editText = addon.url }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                Text(
                    if (editing) "Cancelar" else "Editar URL",
                    color = TextSecondary, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily,
                )
            }
        }
        }

        // Campo de edição do endereço. Serve pra quando o addon muda de host ou de token,
        // sem precisar remover e reinstalar (o que jogaria ele pro fim da prioridade).
        if (editing) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                label = { Text("Endereço do manifest", color = TextTertiary, fontSize = 12.sp) },
                singleLine = false,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = ManropeFontFamily),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .clickable {
                        if (editText.isNotBlank() && editText != addon.url) onEdit(editText)
                        editing = false
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    "Salvar endereço",
                    color = Color(0xFF06070A), fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, fontFamily = ManropeFontFamily,
                )
            }
        }
    }
}

/** Escolhe quais catálogos do addon viram fileira na Início. Marca na hora, sem servidor. */
@Composable
private fun HomeCatalogsDialog(
    addonName: String,
    base: String,
    catalogs: List<com.aotv.app.data.remote.AddonCatalog>,
    homeKeys: Set<String>,
    onToggle: (com.aotv.app.data.remote.AddonCatalog) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = {
            Column {
                Text("Catálogos de $addonName", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily)
                Text("Marcados viram fileiras na Início", color = TextTertiary, fontSize = 12.sp, fontFamily = ManropeFontFamily)
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(catalogs.size) { i ->
                    val c = catalogs[i]
                    val checked = "$base|${c.type}|${c.id}" in homeKeys
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggle(c) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(c) }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily)
                            Text(if (c.type == "movie") "Filmes" else "Séries", color = TextTertiary, fontSize = 11.sp, fontFamily = ManropeFontFamily)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Pronto", color = Accent, fontWeight = FontWeight.ExtraBold) } },
    )
}

/**
 * Catálogo AOTV. Não sai da lista nem muda de lugar (não tem fonte, então não entra na
 * prioridade). Desligado, a Início fica só com o progresso e os catálogos de addon.
 */
@Composable
private fun AotvCatalogCard(on: Boolean, rowsOn: Int, onToggle: () -> Unit, onCustomize: () -> Unit) {
    val contentAlpha by animateFloatAsState(if (on) 1f else 0.45f, tween(180), label = "aotvAlpha")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .border(1.dp, if (on) Accent.copy(alpha = 0.35f) else BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Surface2).alpha(contentAlpha),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.aotv.app.R.drawable.aotv_logo), "AOTV",
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(Modifier.weight(1f).alpha(contentAlpha)) {
                Text(
                    "AOTV Catálogo", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Etiquetas na linha de baixo, assim o nome nunca corta.
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TinyPill("Fixo", TextTertiary)
                    TinyPill(if (on) "Ativo" else "Desligado", if (on) Ok else DangerText)
                    Text(
                        if (on) "$rowsOn fileiras" else "Fora da Início",
                        color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = on,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BackgroundCard2,
                    uncheckedBorderColor = Border,
                ),
            )
        }
        Text(
            "Em alta, nos cinemas, lançamentos, trailers e gêneros, direto do TMDB. Desligue para montar a Início só com catálogos de addon.",
            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.alpha(contentAlpha),
        )
        HorizontalDivider(color = BorderSubtle)
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCustomize)
                .padding(horizontal = 10.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Personalizar Início", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
    }
}

/** Entrada para a loja de addons da comunidade. */
@Composable
private fun ExploreEntry(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.TravelExplore, null, tint = Accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text("Explorar addons", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Catálogo da comunidade, instale e configure aqui", color = TextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
    }
}

@Composable
private fun TinyPill(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.3.sp, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyAddons() {
    com.aotv.app.ui.common.AotvEmptyState(
        Icons.Default.Extension, "Nenhum addon instalado", "Cole o link de um manifest para instalar ou explore o catálogo da comunidade.",
    )
}
