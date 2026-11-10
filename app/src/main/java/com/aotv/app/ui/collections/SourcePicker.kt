package com.aotv.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aotv.app.data.collections.CollectionSource
import com.aotv.app.data.collections.CollectionSourceResolver.SearchHit
import com.aotv.app.data.collections.TmdbCollectionRules
import com.aotv.app.data.collections.TmdbFilters
import com.aotv.app.data.collections.TmdbSourceType
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent,
    focusedLabelColor = Accent, unfocusedLabelColor = TextSecondary,
)

/** Campo de texto do editor. [url] liga o teclado de link e desliga a correção. */
@Composable
internal fun EditorField(label: String, value: String, onChange: (String) -> Unit, url: Boolean = false, number: Boolean = false, hint: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it, color = TextTertiary) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = when { url -> KeyboardType.Uri; number -> KeyboardType.Number; else -> KeyboardType.Text },
            autoCorrectEnabled = !url && !number,
        ),
        colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
    )
}

/** Linha com rótulo e chave liga e desliga. A linha inteira responde ao toque. */
@Composable
internal fun SwitchLine(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            sub?.let { Text(it, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = collectionSwitchColors())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipGroup(title: String?, content: @Composable FlowRowScope.() -> Unit) {
    Column {
        title?.let { Text(it, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private val DIALOG_TOP = 32.dp

private val SORTS = listOf(
    TmdbCollectionRules.SORT_POPULAR to "Popular",
    TmdbCollectionRules.SORT_RATING to "Melhor nota",
    TmdbCollectionRules.SORT_VOTES to "Mais votados",
    TmdbCollectionRules.SORT_RELEASE to "Mais recentes",
)

/** Seletor de fontes da pasta. Fica aberto para adicionar várias, e mostra a última que entrou. */
@Composable
internal fun SourcePickerDialog(
    st: CollectionEditorViewModel.Ui,
    vm: CollectionEditorViewModel,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var added by remember { mutableStateOf<String?>(null) }
    val add: (CollectionSource) -> Unit = { s -> vm.addSource(s); added = sourceLabel(s) }
    val tabs = listOf("Addons", "Atalhos", "Buscar", "Por link", "Descobrir")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
            // Diálogo em tela cheia não recebe o recuo da barra de status. O topo fixo deixa o título longe da câmera.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = DIALOG_TOP), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Adicionar fonte", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(st.currentFolder?.title?.ifBlank { null }?.let { "Na pasta $it" } ?: "Na pasta nova", color = TextSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar", tint = TextPrimary) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, t -> FilterChipPill(t, tab == i, { tab = i; vm.clearFound() }) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> AddonTab(st, vm, add)
                    1 -> PresetTab(vm, add)
                    2 -> SearchTab(st, vm, add)
                    3 -> LinkTab(st, vm, add)
                    else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        DiscoverBuilder(initial = null, st = st, vm = vm, action = "Adicionar fonte") { add(it) }
                    }
                }
            }
            added?.let {
                Row(
                    Modifier.fillMaxWidth().background(BackgroundCard2).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Check, null, tint = Ok)
                    Text("$it entrou na pasta", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = onDismiss) { Text("Concluir", color = Accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun AddonTab(st: CollectionEditorViewModel.Ui, vm: CollectionEditorViewModel, add: (CollectionSource) -> Unit) {
    LaunchedEffect(Unit) { vm.loadCatalogs() }
    var open by remember { mutableStateOf<String?>(null) }
    val list = st.catalogs
    when {
        st.catalogsLoading || list == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        list.isEmpty() -> Text(
            "Nenhum addon ligado tem catálogo de filme ou série. Instale um em Addons e volte aqui.",
            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(16.dp),
        )
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.groupBy { it.addonName }.forEach { (addon, cats) ->
                item(key = "h:$addon") { com.aotv.app.ui.common.Eyebrow(addon, Modifier.padding(top = 8.dp)) }
                items(cats, key = { "${it.addonId}|${it.type}|${it.id}" }) { c ->
                    val key = "${c.addonId}|${c.type}|${c.id}"
                    CollectionCard {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                if (c.genres.isEmpty()) add(CollectionSource(provider = "addon", addonId = c.addonId, type = c.type, catalogId = c.id, title = c.name))
                                else open = if (open == key) null else key
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (c.type == "series") "Séries" else "Filmes") + if (c.genres.isNotEmpty()) " · escolha o gênero" else "",
                                    color = TextSecondary, fontSize = 13.sp,
                                )
                            }
                            Icon(Icons.Default.Add, "Adicionar", tint = Accent)
                        }
                        if (open == key) ChipGroup(null) {
                            if (!c.genreRequired) FilterChipPill("Todos", false, {
                                add(CollectionSource(provider = "addon", addonId = c.addonId, type = c.type, catalogId = c.id, title = c.name))
                            })
                            c.genres.forEach { g ->
                                FilterChipPill(g, false, {
                                    add(CollectionSource(provider = "addon", addonId = c.addonId, type = c.type, catalogId = c.id, genre = g, title = "${c.name} · $g"))
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetTab(vm: CollectionEditorViewModel, add: (CollectionSource) -> Unit) {
    val presets = remember { vm.presets() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Estúdios e streamings mais usados, os mesmos atalhos do Nuvio.", color = TextSecondary, fontSize = 14.sp)
        ChipGroup("Estúdios (filmes)") {
            presets.filter { it.source.tmdbType == TmdbSourceType.COMPANY }.forEach { p -> FilterChipPill(p.label, false, { add(p.source) }) }
        }
        ChipGroup("Streamings e canais (séries)") {
            presets.filter { it.source.tmdbType == TmdbSourceType.NETWORK }.forEach { p -> FilterChipPill(p.label, false, { add(p.source) }) }
        }
    }
}

@Composable
private fun SearchTab(st: CollectionEditorViewModel.Ui, vm: CollectionEditorViewModel, add: (CollectionSource) -> Unit) {
    var kind by remember { mutableStateOf(CollectionEditorViewModel.SearchKind.COMPANY) }
    var q by remember { mutableStateOf("") }
    // Estúdio começa em filmes. Pessoa traz filme e série, a não ser que escolha.
    var media by remember(kind) { mutableStateOf(if (kind == CollectionEditorViewModel.SearchKind.COMPANY) "MOVIE" else "") }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipGroup(null) {
                CollectionEditorViewModel.SearchKind.entries.forEach { k -> FilterChipPill(k.label, kind == k, { kind = k; vm.search(k, q) }) }
            }
            if (kind != CollectionEditorViewModel.SearchKind.COLLECTION) ChipGroup(null) {
                if (kind != CollectionEditorViewModel.SearchKind.COMPANY) FilterChipPill("Filmes e séries", media == "", { media = "" })
                FilterChipPill("Filmes", media == "MOVIE", { media = "MOVIE" })
                FilterChipPill("Séries", media == "TV", { media = "TV" })
            }
            OutlinedTextField(
                value = q, onValueChange = { q = it; vm.search(kind, it) }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                placeholder = { Text("Nome do ${kind.label.lowercase()}", color = TextTertiary) },
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (st.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Accent)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(st.search, key = { it.id }) { hit ->
                HitRow(hit) {
                    add(CollectionSource(
                        provider = "tmdb", tmdbSourceType = kind.type.name, title = hit.name, tmdbId = hit.id,
                        mediaType = media.ifBlank { null },
                        sortBy = when (kind) {
                            CollectionEditorViewModel.SearchKind.COMPANY -> TmdbCollectionRules.SORT_POPULAR
                            CollectionEditorViewModel.SearchKind.COLLECTION -> TmdbCollectionRules.SORT_ORIGINAL
                            else -> TmdbCollectionRules.SORT_RELEASE
                        },
                    ))
                }
            }
            if (!st.searching && q.isNotBlank() && st.search.isEmpty()) item {
                Text("Nada encontrado no TMDB.", color = TextTertiary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            if (hit.image != null) AsyncImage(model = hit.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
            else Text(hit.name.take(1).uppercase(), color = TextSecondary, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            Text(hit.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hit.sub.isNotBlank()) Text(hit.sub, color = TextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Default.Add, "Adicionar", tint = Accent)
    }
}

@Composable
private fun LinkTab(st: CollectionEditorViewModel.Ui, vm: CollectionEditorViewModel, add: (CollectionSource) -> Unit) {
    val types = listOf(TmdbSourceType.LIST, TmdbSourceType.COLLECTION, TmdbSourceType.COMPANY, TmdbSourceType.NETWORK, TmdbSourceType.PERSON, TmdbSourceType.DIRECTOR)
    var type by remember { mutableStateOf(TmdbSourceType.LIST) }
    var input by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cole o número ou o link da página no site do TMDB.", color = TextSecondary, fontSize = 14.sp)
        ChipGroup(null) { types.forEach { t -> FilterChipPill(tmdbTypeLabel(t), type == t, { type = t; vm.clearFound() }) } }
        EditorField("Número ou link do TMDB", input, { input = it; vm.clearFound() }, url = true, hint = "https://www.themoviedb.org/list/…")
        AotvButton("Conferir", { vm.find(type, input) }, icon = Icons.Default.Search, busy = st.finding, busyLabel = "Conferindo…", enabled = !st.finding && input.isNotBlank(), compact = true)
        st.findError?.let { Text(it, color = DangerText, fontSize = 13.sp) }
        st.found?.let { hit ->
            HitRow(hit) {
                add(CollectionSource(
                    provider = "tmdb", tmdbSourceType = type.name, title = hit.name, tmdbId = hit.id,
                    mediaType = when (type) { TmdbSourceType.NETWORK -> "TV"; TmdbSourceType.COMPANY -> "MOVIE"; else -> null },
                    sortBy = if (type == TmdbSourceType.COMPANY || type == TmdbSourceType.NETWORK) TmdbCollectionRules.SORT_POPULAR else TmdbCollectionRules.SORT_ORIGINAL,
                ))
                vm.clearFound()
                input = ""
            }
        }
    }
}

private val LANGS = listOf("" to "Qualquer", "pt" to "Português", "en" to "Inglês", "es" to "Espanhol", "ja" to "Japonês", "ko" to "Coreano", "fr" to "Francês", "hi" to "Hindi", "it" to "Italiano", "de" to "Alemão")
private val COUNTRIES = listOf("" to "Qualquer", "BR" to "Brasil", "US" to "EUA", "GB" to "Reino Unido", "JP" to "Japão", "KR" to "Coreia do Sul", "FR" to "França", "ES" to "Espanha", "IN" to "Índia", "MX" to "México")

/**
 * Descoberta do TMDB com os filtros do Nuvio. [initial] preenche quando é edição de uma fonte que
 * já existe. Os filtros que a tela não mostra (vindos de JSON) continuam guardados.
 */
@Composable
internal fun DiscoverBuilder(
    initial: CollectionSource?,
    st: CollectionEditorViewModel.Ui,
    vm: CollectionEditorViewModel,
    action: String,
    onDone: (CollectionSource) -> Unit,
) {
    val f0 = initial?.filters ?: TmdbFilters()
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var tv by remember { mutableStateOf(initial?.tv ?: false) }
    var sort by remember { mutableStateOf(initial?.sortBy ?: TmdbCollectionRules.SORT_POPULAR) }
    var withG by remember { mutableStateOf(ids(f0.withGenres)) }
    var withoutG by remember { mutableStateOf(ids(f0.withoutGenres)) }
    var year by remember { mutableStateOf(f0.year?.toString().orEmpty()) }
    var gte by remember { mutableStateOf(f0.releaseDateGte.orEmpty()) }
    var lte by remember { mutableStateOf(f0.releaseDateLte.orEmpty()) }
    var vote by remember { mutableStateOf(f0.voteAverageGte) }
    var votes by remember { mutableStateOf(f0.voteCountGte) }
    var lang by remember { mutableStateOf(f0.withOriginalLanguage.orEmpty()) }
    var country by remember { mutableStateOf(f0.withOriginCountry.orEmpty()) }
    var keywords by remember { mutableStateOf(ids(f0.withKeywords).map { SearchHit(it, "Palavra $it", null) }) }
    var kwQuery by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(f0.watchRegion ?: "BR") }
    var provs by remember { mutableStateOf(ids(f0.withWatchProviders)) }
    var companies by remember { mutableStateOf(f0.withCompanies.orEmpty()) }
    var networks by remember { mutableStateOf(f0.withNetworks.orEmpty()) }

    LaunchedEffect(tv) { vm.loadGenres(tv) }
    LaunchedEffect(tv, region) { vm.loadProviders(region, tv) }
    val genres = st.genres[tv].orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EditorField("Nome da fonte", title, { title = it.take(60) }, hint = "Ex. Animações premiadas")
        ChipGroup("Tipo") {
            FilterChipPill("Filmes", !tv, { tv = false; withG = emptySet(); withoutG = emptySet() })
            FilterChipPill("Séries", tv, { tv = true; withG = emptySet(); withoutG = emptySet() })
        }
        ChipGroup("Ordem") { SORTS.forEach { (k, l) -> FilterChipPill(l, sort == k, { sort = k }) } }
        ChipGroup("Gêneros") {
            if (genres.isEmpty()) Text("Carregando gêneros…", color = TextTertiary, fontSize = 13.sp)
            genres.forEach { g -> FilterChipPill(g.name, g.id in withG, { withG = toggle(withG, g.id); withoutG = withoutG - g.id }) }
        }
        ChipGroup("Sem estes gêneros") {
            genres.forEach { g -> FilterChipPill(g.name, g.id in withoutG, { withoutG = toggle(withoutG, g.id); withG = withG - g.id }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { EditorField("Ano", year, { year = it.filter(Char::isDigit).take(4) }, number = true) }
            Box(Modifier.weight(1f)) { EditorField("A partir de", gte, { gte = it.take(10) }, hint = "2020-01-01") }
            Box(Modifier.weight(1f)) { EditorField("Até", lte, { lte = it.take(10) }, hint = "2024-12-31") }
        }
        ChipGroup("Nota mínima") {
            listOf(null to "Qualquer", 5.0 to "5+", 6.0 to "6+", 7.0 to "7+", 8.0 to "8+").forEach { (v, l) -> FilterChipPill(l, vote == v, { vote = v }) }
        }
        ChipGroup("Mínimo de votos") {
            listOf(null to "Qualquer", 50 to "50", 200 to "200", 1000 to "1.000").forEach { (v, l) -> FilterChipPill(l, votes == v, { votes = v }) }
        }
        ChipGroup("Idioma original") { LANGS.forEach { (k, l) -> FilterChipPill(l, lang == k, { lang = k }) } }
        ChipGroup("País de origem") { COUNTRIES.forEach { (k, l) -> FilterChipPill(l, country == k, { country = k }) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Palavras-chave", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (keywords.isNotEmpty()) ChipGroup(null) {
                keywords.forEach { k -> FilterChipPill(k.name, true, { keywords = keywords.filterNot { it.id == k.id } }, icon = Icons.Default.Close) }
            }
            EditorField("Buscar palavra-chave", kwQuery, { kwQuery = it; vm.searchKeywords(it) }, hint = "Ex. super-herói, viagem no tempo")
            if (kwQuery.isNotBlank() && st.keywords.isNotEmpty()) ChipGroup(null) {
                st.keywords.take(12).forEach { k -> FilterChipPill(k.name, false, { if (keywords.none { it.id == k.id }) keywords = keywords + k; kwQuery = ""; vm.searchKeywords("") }, icon = Icons.Default.Add) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Onde assistir", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            EditorField("País do streaming (2 letras)", region, { region = it.uppercase().filter(Char::isLetter).take(2) })
            if (st.providers.isNotEmpty()) ChipGroup(null) {
                st.providers.take(40).forEach { p -> FilterChipPill(p.name, p.id in provs, { provs = toggle(provs, p.id) }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avançado", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            EditorField("Estúdios (números do TMDB, separados por vírgula)", companies, { companies = it.filter { c -> c.isDigit() || c == ',' || c == '|' } }, number = true)
            if (tv) EditorField("Canais (números do TMDB, separados por vírgula)", networks, { networks = it.filter { c -> c.isDigit() || c == ',' || c == '|' } }, number = true)
        }
        AotvButton(action, {
            val filters = f0.copy(
                withGenres = withG.joinToString(",").ifBlank { null },
                withoutGenres = withoutG.joinToString(",").ifBlank { null },
                year = year.toIntOrNull(),
                releaseDateGte = gte.trim().ifBlank { null },
                releaseDateLte = lte.trim().ifBlank { null },
                voteAverageGte = vote,
                voteCountGte = votes,
                withOriginalLanguage = lang.ifBlank { null },
                withOriginCountry = country.ifBlank { null },
                withKeywords = keywords.joinToString(",") { it.id.toString() }.ifBlank { null },
                watchRegion = if (provs.isEmpty()) f0.watchRegion else region.ifBlank { null },
                withWatchProviders = provs.joinToString("|").ifBlank { null },
                withCompanies = companies.trim(',', '|').ifBlank { null },
                withNetworks = if (tv) networks.trim(',', '|').ifBlank { null } else null,
            )
            onDone(CollectionSource(
                provider = "tmdb", tmdbSourceType = TmdbSourceType.DISCOVER.name,
                title = title.trim().ifBlank { if (tv) "Séries descobertas" else "Filmes descobertos" },
                mediaType = if (tv) "TV" else "MOVIE", sortBy = sort, filters = filters.takeUnless { it.isEmpty },
            ))
        }, icon = Icons.Default.Check, tone = ButtonTone.PRIMARY)
    }
}

private fun ids(raw: String?): Set<Int> = raw.orEmpty().split(',', '|').mapNotNull { it.trim().toIntOrNull() }.toCollection(LinkedHashSet())
private fun toggle(set: Set<Int>, id: Int): Set<Int> = if (id in set) set - id else set + id

/**
 * Edição de uma fonte que já está na pasta. Nome e ordem para todas as do TMDB, tipo para estúdio
 * e pessoa, todos os filtros para a descoberta e o gênero para catálogo de addon.
 */
@Composable
internal fun SourceEditDialog(
    source: CollectionSource,
    st: CollectionEditorViewModel.Ui,
    vm: CollectionEditorViewModel,
    onSave: (CollectionSource) -> Unit,
    onDismiss: () -> Unit,
) {
    if (source.tmdbType == TmdbSourceType.DISCOVER) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = DIALOG_TOP), verticalAlignment = Alignment.CenterVertically) {
                    Text("Editar descoberta", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar", tint = TextPrimary) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    DiscoverBuilder(initial = source, st = st, vm = vm, action = "Salvar fonte") { onSave(it); onDismiss() }
                }
            }
        }
        return
    }
    var title by remember { mutableStateOf(source.title.orEmpty()) }
    var sort by remember { mutableStateOf(source.sortBy ?: TmdbCollectionRules.SORT_ORIGINAL) }
    var media by remember { mutableStateOf(source.mediaType?.uppercase().orEmpty()) }
    var genre by remember { mutableStateOf(source.genre.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Editar fonte", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(sourceKind(source), color = TextSecondary, fontSize = 13.sp)
                EditorField("Nome na aba", title, { title = it.take(60) })
                when {
                    source.isAddon -> EditorField("Gênero (opcional)", genre, { genre = it.take(40) })
                    source.isTmdb -> {
                        ChipGroup("Ordem") {
                            (listOf(TmdbCollectionRules.SORT_ORIGINAL to "Original") + SORTS).forEach { (k, l) -> FilterChipPill(l, sort == k, { sort = k }) }
                        }
                        val t = source.tmdbType
                        if (t == TmdbSourceType.COMPANY || t == TmdbSourceType.PERSON || t == TmdbSourceType.DIRECTOR) ChipGroup("Tipo") {
                            if (t != TmdbSourceType.COMPANY) FilterChipPill("Filmes e séries", media.isBlank(), { media = "" })
                            FilterChipPill("Filmes", media == "MOVIE", { media = "MOVIE" })
                            FilterChipPill("Séries", media == "TV", { media = "TV" })
                        }
                    }
                    else -> Text("Lista do Trakt fica guardada e volta no exportar, mas só carrega quando o app tiver a conta do Trakt.", color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(source.copy(
                    title = title.trim().ifBlank { null },
                    sortBy = if (source.isTmdb) sort else source.sortBy,
                    mediaType = if (source.isTmdb && source.tmdbType in setOf(TmdbSourceType.COMPANY, TmdbSourceType.PERSON, TmdbSourceType.DIRECTOR)) media.ifBlank { null } else source.mediaType,
                    genre = if (source.isAddon) genre.trim().ifBlank { null } else source.genre,
                ))
                onDismiss()
            }) { Text("Salvar", color = Accent, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) } },
    )
}
