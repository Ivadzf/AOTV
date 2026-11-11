package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.HomeCatalogRef
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AddonLinks
import com.aotv.app.data.remote.StremioAddonService
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.LocalIsTv
import com.aotv.app.ui.common.TvTextFieldGate
import com.aotv.app.ui.common.pressScale
import com.aotv.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Peças que Minha Início, Addons e a loja dividem. Um painel só de Fileiras de X, com várias portas.

/** Clique de linha sem o anel genérico (a linha desenha o próprio foco) e com o encolher do toque. */
internal fun Modifier.catRowClick(role: Role = Role.Button, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    this.pressScale(src, 0.98f).clickable(src, null, enabled = enabled, role = role, onClick = onClick)
}

/** Interruptor de linha com a mesma regra do [catRowClick]. */
internal fun Modifier.catRowToggle(on: Boolean, onToggle: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    this.pressScale(src, 0.98f).toggleable(value = on, interactionSource = src, indication = null, role = Role.Switch, onValueChange = { onToggle() })
}

/** Número com ponto de milhar (1.024). */
internal fun catNum(n: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR")).format(n)

/** Nome do tipo de catálogo em português. */
internal fun rowTypeLabel(type: String): String = when (type) {
    "movie" -> "Filmes"
    "series" -> "Séries"
    "anime" -> "Anime"
    "tv" -> "TV"
    "channel" -> "Canais"
    else -> type
}

/**
 * Pede o foco e diz se pegou. Espera um instante antes porque o item pode ainda não ter sido medido,
 * e tenta de novo. Serve com requestFocus devolvendo Unit ou Boolean.
 */
internal suspend fun FocusRequester.focusWithRetry(tries: Int = 16, firstWait: Long = 120L): Boolean {
    delay(firstWait)
    repeat(tries) {
        val r: Any? = runCatching { requestFocus() }.getOrNull()
        if (r != null && r != false) return true
        delay(70)
    }
    return false
}

/** Pede o foco assim que o item existe. */
@Composable
internal fun CatRequestFocus(fr: FocusRequester, key: Any? = Unit, enabled: Boolean = true) {
    LaunchedEffect(key, enabled) { if (enabled) fr.focusWithRetry() }
}

// ── Aviso curto (toast) com Desfazer ─────────────────────────────────────────

/**
 * Aviso curto no pé da tela. Com [undo] mostra Desfazer. Na TV o OK desfaz enquanto o aviso estiver
 * à mostra e nenhuma outra tecla tiver sido apertada (qualquer seta segue o caminho normal).
 */
class CatalogToast {
    var text by mutableStateOf<String?>(null)
        private set
    var undo by mutableStateOf<(() -> Unit)?>(null)
        private set
    internal var token by mutableIntStateOf(0)
        private set
    internal var ms = 3500L
        private set

    /** O OK da TV ainda desfaz. Cai quando outra tecla é apertada. */
    var armed = false

    fun show(message: String, millis: Long = 3500L, onUndo: (() -> Unit)? = null) {
        text = message
        undo = onUndo
        ms = millis
        armed = onUndo != null
        token++
    }

    fun dismiss() { text = null; undo = null; armed = false }

    /** Roda o Desfazer e some. Devolve true quando havia o que desfazer. */
    fun undoNow(): Boolean {
        val f = undo ?: return false
        dismiss()
        f()
        return true
    }
}

@Composable
fun rememberCatalogToast(): CatalogToast = remember { CatalogToast() }

@Composable
fun CatalogToastHost(toast: CatalogToast, modifier: Modifier = Modifier) {
    LaunchedEffect(toast.token) {
        if (toast.text == null) return@LaunchedEffect
        delay(toast.ms)
        toast.dismiss()
    }
    val text = toast.text ?: return
    val tv = LocalIsTv.current
    Row(
        modifier
            .widthIn(max = 720.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF2111111))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp))
            .padding(start = 18.dp, end = if (toast.undo != null) 6.dp else 18.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 14.dp))
        if (toast.undo != null) {
            Text(
                if (tv) "OK desfaz" else "Desfazer",
                color = Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (tv) Modifier else Modifier.clickable(role = Role.Button) { toast.undoNow() })
                    .padding(horizontal = 12.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

// ── Confirmação com o foco em Cancelar ───────────────────────────────────────

@Composable
fun CatalogConfirm(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val cancel = remember { FocusRequester() }
    CatRequestFocus(cancel)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(BackgroundCard).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(body, color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                AotvButton("Cancelar", onDismiss, Modifier.focusRequester(cancel), compact = true)
                AotvButton(confirm, onConfirm, tone = if (danger) ButtonTone.DANGER else ButtonTone.PRIMARY, compact = true)
            }
        }
    }
}

// ── Painel lateral ───────────────────────────────────────────────────────────

/**
 * Painel que entra pela direita. É um diálogo, então o foco do controle fica preso nele e Voltar
 * fecha. No toque ganha o botão de fechar e tocar fora também fecha.
 */
@Composable
fun SidePanelDialog(
    title: String,
    onDismiss: () -> Unit,
    sub: String? = null,
    toast: CatalogToast? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tv = LocalIsTv.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .55f))
                .focusProperties { canFocus = false }
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier.fillMaxHeight().widthIn(max = 480.dp).fillMaxWidth()
                    .background(PanelBackground)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .focusProperties { canFocus = false }
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        title, color = TextPrimary, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (!tv) com.aotv.app.ui.common.GlassCircleButton(Icons.Default.Close, onDismiss, size = 48.dp, contentDescription = "Fechar painel")
                }
                if (!sub.isNullOrBlank()) Text(sub, color = TextTertiary, fontSize = 14.sp, lineHeight = 19.sp)
                content()
            }
            if (toast != null) CatalogToastHost(toast, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 18.dp, start = 12.dp, end = 12.dp))
        }
    }
}

/** Linha de painel com o foco do controle (fundo suave e borda branca). */
@Composable
fun PanelItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = null,
    tag: String? = null,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(if (focused) AccentSoft else BackgroundCard2)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .catRowClick(Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!tag.isNullOrBlank()) Text(tag, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (selected) Icon(Icons.Default.Check, "Em uso", tint = Accent, modifier = Modifier.size(22.dp))
    }
}

/** Marca redonda da linha. Cheia na cor do tema com o visto quando está na Início, anel cinza quando não. */
@Composable
fun StateMark(on: Boolean, size: Int = 34) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .then(if (on) Modifier.background(Accent) else Modifier.border(2.dp, TextTertiary, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size((size * 0.62f).dp))
    }
}

// ── Fileiras de um addon ─────────────────────────────────────────────────────

/** Dados do painel Fileiras de X. Lê do layout do perfil e do manifest guardado, então abre na hora. */
@HiltViewModel
class AddonRowsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val stremio: StremioAddonService,
) : ViewModel() {

    data class Item(val key: String, val name: String, val type: String, val id: String, val on: Boolean)
    enum class Load { READY, LOADING, FAILED, EMPTY }
    data class UiState(
        val load: Load = Load.LOADING,
        val items: List<Item> = emptyList(),
        val profile: String = "",
        /** Base do addon que estes dados descrevem. A tela ignora dados de outro addon. */
        val base: String = "",
    ) { val on: Int get() = items.count { it.on } }

    private data class Target(val url: String, val name: String)

    private val target = MutableStateFlow<Target?>(null)
    private val fetch = MutableStateFlow(Load.LOADING)

    val state: StateFlow<UiState> = combine(target, prefs.homeLayoutFlow, prefs.addonMetaFlow, fetch, prefs.profileId) { t, layout, meta, f, _ ->
        if (t == null) return@combine UiState(profile = prefs.profile.name)
        val base = AddonLinks.baseOf(t.url)
        val on = layout.filter { it.enabled && it.ref?.base == base }.mapTo(HashSet()) { it.key }
        val cats = meta[base]?.catalogs
        val items = cats?.map { c ->
            val key = "$base|${c.type}|${c.id}"
            Item(key, c.name, c.type, c.id, key in on)
        }.orEmpty()
        val load = when {
            cats == null -> if (f == Load.FAILED) Load.FAILED else Load.LOADING
            cats.isEmpty() -> Load.EMPTY
            else -> Load.READY
        }
        UiState(load, items, prefs.profile.name, base)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /** Abre o painel de um addon. Sem manifest guardado, lê na rede com o painel já aberto. */
    fun open(url: String, name: String) {
        target.value = Target(url, name)
        if (prefs.addonMetaFlow.value[AddonLinks.baseOf(url)] == null) load(url) else fetch.value = Load.READY
    }

    fun load(url: String) {
        fetch.value = Load.LOADING
        viewModelScope.launch { fetch.value = if (stremio.getManifest(url) == null) Load.FAILED else Load.READY }
    }

    fun set(item: Item, on: Boolean) {
        val t = target.value ?: return
        val base = AddonLinks.baseOf(t.url)
        prefs.setHomeRowEnabled(item.key, on, HomeCatalogRef(base, item.type, item.id, item.name, t.name))
    }

    fun setAll(on: Boolean) = prefs.setHomeRowsEnabled(state.value.items.mapTo(HashSet()) { it.key }, on)
}

private const val ROWS_PAGE = 60
private const val ROWS_BULK_TOP = 20

/**
 * Painel Fileiras de X. Cada linha mostra o estado (visto na cor do tema e Na Início, ou anel cinza e
 * Escondida) e o tipo. O cabeçalho diz quantas estão na Início. Marcar e desmarcar vale só para o perfil.
 * Abre com o que já está guardado e lê o manifest em seguida quando faltar.
 */
@Composable
fun AddonRowsPanel(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    vm: AddonRowsViewModel = hiltViewModel(),
) {
    LaunchedEffect(url) { vm.open(url, name) }
    val raw by vm.state.collectAsStateWithLifecycle()
    // Enquanto o painel troca de addon, os dados do anterior não aparecem.
    val st = if (raw.base == AddonLinks.baseOf(url)) raw else AddonRowsViewModel.UiState(profile = raw.profile)
    val toast = rememberCatalogToast()
    var limit by remember(url) { mutableIntStateOf(ROWS_PAGE) }
    var query by remember(url) { mutableStateOf("") }
    var bulk by remember(url) { mutableStateOf<Boolean?>(null) }
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) st.items else st.items.filter { it.name.lowercase().contains(q) }
    val first = remember { FocusRequester() }
    val ready = st.load == AddonRowsViewModel.Load.READY
    CatRequestFocus(first, key = ready && st.items.isNotEmpty(), enabled = ready && st.items.isNotEmpty())

    SidePanelDialog(
        title = "Fileiras de $name",
        sub = "Escolha quais aparecem na Início do perfil ${st.profile}.",
        onDismiss = onDismiss,
        toast = toast,
    ) {
        val count = when (st.load) {
            AddonRowsViewModel.Load.LOADING -> "Lendo as fileiras…"
            AddonRowsViewModel.Load.FAILED -> "Não deu para ler as fileiras deste addon"
            AddonRowsViewModel.Load.EMPTY -> "Este addon não tem fileiras"
            AddonRowsViewModel.Load.READY -> ""
        }
        if (ready) Row(verticalAlignment = Alignment.Bottom) {
            Text("${st.on} de ${st.items.size}", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(" na sua Início", color = TextPrimary, fontSize = 17.sp)
        } else Text(count, color = TextSecondary, fontSize = 15.sp)

        if (st.load == AddonRowsViewModel.Load.FAILED) {
            PanelItem("Tentar de novo", { vm.load(url) }, sub = "Confira a internet e tente outra vez", icon = Icons.Default.Refresh)
        }
        if (st.load == AddonRowsViewModel.Load.EMPTY) Text("Ele só traz fontes de vídeo ou legendas.", color = TextTertiary, fontSize = 14.sp)

        if (ready) {
            val bulkRow: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AotvButton("Marcar todas", { if (st.items.size <= ROWS_BULK_TOP) { vm.setAll(true); toast.show("Todas as ${st.items.size} estão na Início", 2200) } else bulk = true }, icon = Icons.Default.Check, compact = true)
                    AotvButton("Desmarcar todas", { if (st.items.size <= ROWS_BULK_TOP) { vm.setAll(false); toast.show("Todas saíram da Início", 2200) } else bulk = false }, icon = Icons.Default.RadioButtonUnchecked, compact = true)
                }
            }
            if (st.items.size <= ROWS_BULK_TOP) bulkRow()
            if (st.items.size > ROWS_PAGE) {
                TvTextFieldGate(Modifier.fillMaxWidth()) { gate ->
                    OutlinedTextField(
                        value = query, onValueChange = { query = it; limit = ROWS_PAGE }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                        placeholder = { Text("Buscar fileira entre ${catNum(st.items.size)}", color = TextTertiary, fontSize = 14.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent,
                            focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
                        ),
                        modifier = gate,
                    )
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.take(limit).forEachIndexed { i, item ->
                    item(key = item.key) {
                        RowSwitch(
                            item,
                            Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        ) {
                            vm.set(item, !item.on)
                            toast.show(item.name + if (item.on) " saiu da Início" else " vai aparecer na Início", 1800)
                        }
                    }
                }
                if (filtered.size > limit) item(key = "more") {
                    PanelItem("Mostrar mais ${minOf(ROWS_PAGE, filtered.size - limit)} de ${catNum(filtered.size - limit)}", { limit += ROWS_PAGE })
                }
                if (filtered.isEmpty()) item(key = "none") {
                    Text("Nenhuma com esse nome", color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                }
                if (st.items.size > ROWS_BULK_TOP) item(key = "bulk") { Box(Modifier.padding(top = 4.dp)) { bulkRow() } }
            }
        } else Spacer(Modifier.weight(1f))
    }

    bulk?.let { on ->
        val n = st.items.size
        CatalogConfirm(
            title = if (on) "Mostrar as ${catNum(n)} na Início?" else "Esconder as ${catNum(n)}?",
            body = if (on) "A Início fica bem longa. Dá para esconder de novo aqui." else "Elas ficam em Escondidas na Minha Início.",
            confirm = if (on) "Marcar todas" else "Desmarcar todas",
            onConfirm = {
                vm.setAll(on); bulk = null
                toast.show(if (on) "Todas as ${catNum(n)} estão na Início" else "Todas saíram da Início", 2200)
            },
            onDismiss = { bulk = null },
        )
    }
}

/** Uma linha do painel de fileiras. Interruptor com o estado dito em texto, nunca só por cor. */
@Composable
private fun RowSwitch(item: AddonRowsViewModel.Item, modifier: Modifier, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val state = if (item.on) "na Início" else "escondida"
    Row(
        modifier
            .fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (focused) AccentSoft else BackgroundCard2)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { contentDescription = "${item.name}, ${rowTypeLabel(item.type)}, $state" }
            .catRowToggle(item.on, onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StateMark(item.on)
        Column(Modifier.weight(1f)) {
            Text(item.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(rowTypeLabel(item.type), color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Text(
            if (item.on) "Na Início" else "Escondida",
            color = if (item.on) Accent else TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                .background((if (item.on) Accent else TextTertiary).copy(alpha = .16f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
