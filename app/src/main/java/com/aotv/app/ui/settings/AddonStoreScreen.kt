package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import com.aotv.app.ui.common.topInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.remote.AddonLinks
import com.aotv.app.data.remote.CommunityAddon
import com.aotv.app.ui.theme.*

private val RESOURCE_LABEL = mapOf(
    "stream" to "Filmes e séries", "subtitles" to "Legendas", "catalog" to "Catálogos",
    "meta" to "Metadados", "addon_catalog" to "Lista de addons",
)
private val TYPE_LABEL = mapOf(
    "movie" to "Filmes", "series" to "Séries", "anime" to "Anime", "tv" to "TV", "channel" to "Canais", "other" to "Outros",
)

/**
 * Explorar addons (celular). Lista do catálogo da comunidade com busca e filtros. Em tela larga
 * a ficha do addon fica fixa à direita. Em tela estreita ela abre logo abaixo do cartão tocado.
 */
@Composable
fun AddonStoreScreen(onBack: () -> Unit, onOpenHome: () -> Unit = {}, vm: AddonStoreViewModel = hiltViewModel()) {
    val st by vm.ui.collectAsStateWithLifecycle()
    val list = remember(st.all, st.query, st.show, st.installed) { st.filtered }
    var formFor by remember { mutableStateOf<CommunityAddon?>(null) }
    var siteFor by remember { mutableStateOf<CommunityAddon?>(null) }
    val snack = remember { SnackbarHostState() }
    // Fileiras do addon no mesmo painel da tela Addons. Remover pede confirmação com o número de fileiras.
    var rowsFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var removeAsk by remember { mutableStateOf<CommunityAddon?>(null) }
    val rowsFocus = remember { FocusRequester() }
    val tv = com.aotv.app.ui.common.LocalIsTv.current
    // Recém instalado, o foco da TV vai a Escolher fileiras.
    LaunchedEffect(st.justInstalled) {
        if (st.justInstalled == null) return@LaunchedEffect
        if (tv) rowsFocus.focusWithRetry()
        vm.clearJustInstalled()
    }

    LaunchedEffect(st.message) {
        st.message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    val actions = DetailActions(
        install = { vm.install(it) },
        remove = { removeAsk = it },
        form = { formFor = it },
        site = { siteFor = it },
        rows = { a -> vm.ui.value.entryOf(a)?.let { rowsFor = it.url to a.name } },
        home = { vm.markHomeNew(); onOpenHome() },
    )

    Box(Modifier.fillMaxSize().background(Background)) {
        BoxWithConstraints(Modifier.fillMaxSize().topInsetsPadding()) {
            val wide = maxWidth >= 700.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    StoreHeader(st, vm, onBack, list.size)
                    when {
                        st.loading && st.all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                                Text("Lendo o catálogo da comunidade", color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                        st.failed && st.all.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Não deu para ler stremio-addons.net. Confira a internet e toque em Atualizar.",
                                color = TextSecondary, fontSize = 15.sp)
                        }
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = com.aotv.app.ui.common.pagePadding(), end = com.aotv.app.ui.common.pagePadding(), bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (list.isEmpty()) item {
                                Text("Nenhum addon com esses filtros.", color = TextTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 24.dp))
                            }
                            items(list, key = { it.url + "#" + it.rank }) { a ->
                                val selected = st.selectedUrl == a.url
                                StoreCard(a, st.isInstalled(a), selected) { vm.select(a) }
                                if (selected && !wide) {
                                    AddonDetail(a, st.isInstalled(a), st.busy == a.url, st.busy != null, actions, rowsFocus,
                                        Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
                if (wide) {
                    Box(
                        Modifier.width(380.dp).fillMaxHeight().padding(top = 16.dp, end = 24.dp, bottom = 16.dp),
                    ) {
                        val sel = st.selected
                        if (sel == null) {
                            com.aotv.app.ui.common.SummaryCard(Modifier.fillMaxHeight()) {
                                Text("Escolha um addon. A ficha dele aparece aqui.", color = TextTertiary, fontSize = 15.sp)
                            }
                        } else {
                            AddonDetail(sel, st.isInstalled(sel), st.busy == sel.url, st.busy != null, actions, rowsFocus,
                                Modifier.verticalScroll(rememberScrollState()))
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())

        rowsFor?.let { (url, name) -> AddonRowsPanel(url = url, name = name, onDismiss = { rowsFor = null }) }
        removeAsk?.let { a ->
            val n = st.rowsOf(a)?.on ?: 0
            CatalogConfirm(
                title = "Remover ${a.name}?",
                body = when (n) {
                    0 -> "Dá para instalar de novo depois."
                    1 -> "A fileira dele sai da Início."
                    else -> "As ${catNum(n)} fileiras dele saem da Início."
                },
                confirm = "Remover", danger = true,
                onConfirm = { removeAsk = null; vm.remove(a) },
                onDismiss = { removeAsk = null },
            )
        }
        formFor?.let { a ->
            ConfigFormDialog(a, onDismiss = { formFor = null }) { url ->
                formFor = null
                vm.install(a, url)
            }
        }
        siteFor?.let { a ->
            // Fecha a página assim que a instalação termina.
            var started by remember(a.url) { mutableStateOf(false) }
            LaunchedEffect(st.busy) { if (started && st.busy == null) siteFor = null }
            AddonSiteConfig(
                name = a.name,
                configureUrl = AddonLinks.baseOf(a.url) + "/configure",
                plainUrl = if (a.configRequired) null else a.url,
                busy = st.busy == a.url,
                onInstall = { url -> started = true; vm.install(a, url) },
                onClose = { siteFor = null },
            )
        }
    }
}

private class DetailActions(
    val install: (CommunityAddon) -> Unit,
    val remove: (CommunityAddon) -> Unit,
    val form: (CommunityAddon) -> Unit,
    val site: (CommunityAddon) -> Unit,
    val rows: (CommunityAddon) -> Unit,
    val home: () -> Unit,
)

@Composable
private fun StoreHeader(st: AddonStoreViewModel.UiState, vm: AddonStoreViewModel, onBack: () -> Unit, count: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
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
                "Explorar addons", Modifier.weight(1f),
                sub = if (st.loading) "Atualizando…" else "stremio-addons.net · $count addons",
            )
            // Atualizar lê o catálogo na rede. Gira e trava até terminar.
            com.aotv.app.ui.common.GlassCircleButton(
                Icons.Default.Refresh, { if (!st.loading) vm.load(true) },
                contentDescription = if (st.loading) "Atualizando catálogo" else "Atualizar catálogo", busy = st.loading,
            )
        }
        com.aotv.app.ui.common.TvTextFieldGate(Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding())) { gate ->
            OutlinedTextField(
                value = st.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                placeholder = { Text("Buscar pelo nome ou pelo que ele faz", color = TextTertiary, fontSize = 15.sp) },
                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 15.sp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Color.Transparent, cursorColor = Accent,
                    focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
                ),
                modifier = gate,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            items(AddonStoreViewModel.Show.entries.toList()) { s ->
                com.aotv.app.ui.common.FilterChipPill(s.label, st.show == s, onClick = { vm.setShow(s) })
            }
        }
    }
}

@Composable
private fun AddonLogo(a: CommunityAddon, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(a.name.take(1).uppercase(), color = TextTertiary, fontSize = (size / 2.4).sp, fontWeight = FontWeight.ExtraBold)
        if (a.logo.isNotBlank()) {
            AsyncImage(a.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Badges(a: CommunityAddon, installed: Boolean) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (installed) Pill("Instalado", Success)
        a.resources.forEach { r -> RESOURCE_LABEL[r]?.let { Pill(it, if (r == "stream" || r == "subtitles") Accent else TextTertiary) } }
        if (a.configRequired) Pill("Exige configuração", Gold)
        else if (a.configurable) Pill("Configurável", TextTertiary)
        if (a.p2p) Pill("P2P", Gold)
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun StoreCard(a: CommunityAddon, installed: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AccentSoft else BackgroundCard2)
            .border(if (selected) 2.dp else 0.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AddonLogo(a, 48)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (a.version.isNotBlank()) Text("v${a.version}", color = TextTertiary, fontSize = 12.sp, maxLines = 1)
            }
            Text(a.description.ifBlank { "Sem descrição." }, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Badges(a, installed)
        }
    }
}

private fun usageText(a: CommunityAddon): String {
    val parts = buildList {
        if ("stream" in a.resources) add("aparece em Fontes no detalhe e no player")
        if ("subtitles" in a.resources) add("aparece em Legendas no player")
    }
    if (parts.isEmpty()) return "Só traz catálogo ou metadados. O catálogo de filmes e séries pode virar fileira na Início pelo botão Na Início do addon instalado."
    return "No AOTV " + parts.joinToString(" e ") + "."
}

@Composable
private fun AddonDetail(
    a: CommunityAddon,
    installed: Boolean,
    busy: Boolean,
    anyBusy: Boolean,
    actions: DetailActions,
    rowsFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    com.aotv.app.ui.common.SummaryCard(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddonLogo(a, 60)
            Column(Modifier.weight(1f)) {
                Text(a.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOf(if (a.version.isNotBlank()) "v${a.version}" else "", AddonLinks.hostOf(a.url)).filter { it.isNotBlank() }.joinToString("  ·  "),
                    color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Badges(a, installed)
        Text(a.description.ifBlank { "Sem descrição." }, color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
        InfoRow("Conteúdo", a.types.map { TYPE_LABEL[it] ?: it }.distinct().take(6).joinToString(" · ").ifBlank { "Não informado" })
        InfoRow("Recursos", a.resources.map { RESOURCE_LABEL[it] ?: it }.distinct().joinToString(" · ").ifBlank { "Não informado" })
        // Aviso de uso (.sd-usage). Verde quando funciona aqui, âmbar quando não.
        val works = AddonStoreViewModel.worksHere(a)
        Text(
            usageText(a), color = if (works) Color(0xFF7FE8AD) else Color(0xFFFFC764), fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (works) Ok.copy(alpha = 0.1f) else Color(0x1AFFAA00)).padding(12.dp),
        )
        if (a.configRequired && a.config.isEmpty()) {
            Text("Este addon só funciona depois de configurado na página dele. O link é pego sozinho.",
                color = Gold, fontSize = 14.sp, lineHeight = 20.sp)
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installed) {
                // Instalado, a ficha leva às fileiras dele e à Minha Início.
                if ("catalog" in a.resources) {
                    com.aotv.app.ui.common.AotvButton(
                        "Escolher fileiras", { actions.rows(a) }, Modifier.focusRequester(rowsFocus),
                        icon = Icons.Default.GridView, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY, compact = true,
                    )
                    com.aotv.app.ui.common.AotvButton("Ver minha Início", { actions.home() }, icon = Icons.Default.Home, compact = true)
                }
                com.aotv.app.ui.common.AotvButton(
                    "Remover", { actions.remove(a) }, tone = com.aotv.app.ui.common.ButtonTone.DANGER,
                    enabled = !anyBusy, compact = true,
                )
            } else if (!a.configRequired || (a.config.isNotEmpty() && a.config.none { it.required })) {
                BusyButton("Instalar", busy, primary = true) { actions.install(a) }
            }
            when {
                a.config.isNotEmpty() -> BusyButton(
                    if (installed) "Reconfigurar" else "Configurar e instalar",
                    busy && !installed && a.configRequired, primary = !installed && a.configRequired,
                ) { if (!anyBusy) actions.form(a) }
                a.configurable -> BusyButton(
                    "Configurar no site", busy && a.configRequired, primary = !installed && a.configRequired,
                ) { if (!anyBusy) actions.site(a) }
            }
        }
    }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = TextTertiary, fontSize = 14.sp, modifier = Modifier.width(86.dp))
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

/**
 * Formulário montado do `config` do manifest (text, number, password, checkbox, select).
 * Gera a URL configurada no padrão do stremio-addon-sdk, igual o configuredUrl do webOS.
 */
@Composable
private fun ConfigFormDialog(a: CommunityAddon, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val values = remember(a.url) {
        mutableStateMapOf<String, String>().apply {
            a.config.forEach { c ->
                put(c.key, when (c.type) {
                    "checkbox" -> if (c.default == "checked" || c.default == "true" || c.default == "on") "on" else ""
                    "select" -> c.default ?: c.options.firstOrNull().orEmpty()
                    else -> c.default.orEmpty()
                })
            }
        }
    }
    var msg by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp).clip(RoundedCornerShape(20.dp)).background(BackgroundCard)
                .border(1.dp, Border, RoundedCornerShape(20.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Configurar ${a.name}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                a.config.forEach { c ->
                    val label = c.title + if (c.required) " (obrigatório)" else ""
                    when {
                        c.type == "checkbox" -> Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { values[c.key] = if (values[c.key] == "on") "" else "on" }) {
                            Checkbox(values[c.key] == "on", { values[c.key] = if (it) "on" else "" }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                            Text(label, color = TextPrimary, fontSize = 13.sp)
                        }
                        c.type == "select" && c.options.isNotEmpty() -> Column {
                            Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                c.options.forEach { o ->
                                    com.aotv.app.ui.common.FilterChipPill(o, values[c.key] == o, onClick = { values[c.key] = o })
                                }
                            }
                        }
                        else -> OutlinedTextField(
                            value = values[c.key].orEmpty(),
                            onValueChange = { values[c.key] = it; msg = null },
                            label = { Text(label, fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = if (c.type == "password") PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(keyboardType = when (c.type) {
                                "number" -> KeyboardType.Number
                                "password" -> KeyboardType.Password
                                else -> KeyboardType.Text
                            }),
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            msg?.let { Text(it, color = LiveRed, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
                Button(
                    onClick = {
                        val out = LinkedHashMap<String, Any>()
                        for (c in a.config) {
                            val v = values[c.key].orEmpty().trim()
                            if (c.type == "checkbox") { if (v == "on") out[c.key] = "on"; continue }
                            if (c.required && v.isBlank()) { msg = "Preencha ${c.title}."; return@Button }
                            out[c.key] = v
                        }
                        onDone(AddonLinks.configuredUrl(a.url, out))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusSurface, contentColor = OnFocusSurface),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Salvar e instalar", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}
