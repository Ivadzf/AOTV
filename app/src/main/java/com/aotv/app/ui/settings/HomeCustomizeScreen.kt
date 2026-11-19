package com.aotv.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.SystemRow
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/**
 * Personalizar Início. Liga e desliga cada fileira (do AOTV, do progresso e dos addons),
 * muda a ordem, troca o nome e escolhe de onde vem o destaque. Aplica na hora.
 * O Layout da Página Inicial (Bloco 3, Lote 15) fica na mesma tela: destaque
 * liga/desliga, sufixo de tipo, ocultar não lançados e catálogos do destaque.
 */
@Composable
fun HomeCustomizeScreen(
    onBack: () -> Unit,
    onOpenAddons: () -> Unit,
    viewModel: HomeCustomizeViewModel = hiltViewModel(),
) {
    val st by viewModel.state.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var renaming by remember { mutableStateOf<HomeCustomizeViewModel.Row?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    // Lote 24 (v1.34.0), item 11: arrastar p/ reordenar. O estado do drag
    // vive aqui (a lista precisa dele p/ medir o passo de cada item).
    val rowListState = rememberLazyListState()
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(
                "Personalizar Início", Modifier.weight(1f),
                sub = "${st.enabledCount} de ${st.total} fileiras ligadas",
            )
            com.aotv.app.ui.common.GlassCircleButton(
                Icons.Default.RestartAlt, { confirmReset = true }, contentDescription = "Restaurar o padrão",
            )
        }

        val summary: @Composable () -> Unit = {
            com.aotv.app.ui.common.SummaryCard {
                Text("Sua Início", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                com.aotv.app.ui.common.SummaryRow("Fileiras ligadas", "${st.enabledCount} de ${st.total}")
                com.aotv.app.ui.common.SummaryRow(
                    "Destaque",
                    if (!st.heroOn) "Desligado"
                    else st.heroOptions.firstOrNull { it.key == st.heroSource }?.label ?: "Automático",
                )
                com.aotv.app.ui.common.SummaryRow(
                    "Catálogos do destaque",
                    if (st.heroCatalogs == null) "Automático"
                    else "${st.heroCatalogOptions.count { it.selected }} de ${st.heroCatalogOptions.size}",
                )
                com.aotv.app.ui.common.SummaryRow(
                    "Catálogo AOTV", if (st.catalogOn) "Ligado" else "Desligado", if (st.catalogOn) Ok else DangerText,
                )
                com.aotv.app.ui.common.SummaryRow(
                    "Addons com catálogo", if (st.addonsWithCatalogs == 0) "Nenhum" else "${st.addonsWithCatalogs}",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "As mudanças valem para o perfil ativo e aparecem na Início na hora.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            LazyColumn(
                state = rowListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { com.aotv.app.ui.common.SettingsSection("Destaque") }
                item {
                    CardBox {
                        Text("De onde vem o destaque do topo", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "No automático vem o que está em alta no AOTV. Sem o AOTV, a primeira fileira de addon.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            st.heroOptions.forEach { o ->
                                com.aotv.app.ui.common.FilterChipPill(o.label, st.heroSource == o.key, { viewModel.setHeroSource(o.key) })
                            }
                        }
                    }
                }
                item {
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.Movie,
                        title = "Trailer no destaque",
                        sub = "Toca sem som depois de 5 s parado num título",
                        trailing = { Switch(checked = st.heroTrailer, onCheckedChange = { viewModel.setHeroTrailer(it) }, colors = switchColors()) },
                        onClick = { viewModel.setHeroTrailer(!st.heroTrailer) },
                    )
                }

                item { com.aotv.app.ui.common.SettingsSection("Catálogo AOTV") }
                item {
                    CardBox(highlight = !st.catalogOn) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (st.catalogOn) "Catálogo AOTV ligado" else "Catálogo AOTV desligado",
                                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (st.catalogOn) "Em alta, cinemas, lançamentos, trailers e gêneros do TMDB."
                                    else "A Início fica com o seu progresso e os catálogos dos addons.",
                                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                                )
                            }
                            Switch(checked = st.catalogOn, onCheckedChange = { viewModel.setCatalogOn(it) }, colors = switchColors())
                        }
                        if (st.addonsWithCatalogs == 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Nenhum addon com catálogo instalado ainda. Instale um em Addons para ter fileiras próprias.",
                                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                            Text(
                                "Abrir Addons", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp))
                                    .clickable(onClick = onOpenAddons).padding(vertical = 10.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }

                // ── Layout da Página Inicial (Bloco 3, Lote 15) ────────────────
                item { com.aotv.app.ui.common.SettingsSection("Layout da Página Inicial") }
                item {
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Destaque no topo", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "O carrossel com os títulos em alta. Desligado, a Início abre direto nas fileiras e a busca não mostra Destaques.",
                                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                                )
                            }
                            Switch(checked = st.heroOn, onCheckedChange = { viewModel.setHeroOn(it) }, colors = switchColors())
                        }
                    }
                }
                item {
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.Movie,
                        title = "Sufixo de tipo de catálogo",
                        sub = "Mostra “· Filme” ou “· Série” no título das fileiras de addon na Início",
                        trailing = { Switch(checked = st.typeSuffix, onCheckedChange = { viewModel.setTypeSuffix(it) }, colors = switchColors()) },
                        onClick = { viewModel.setTypeSuffix(!st.typeSuffix) },
                    )
                }
                item {
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.VisibilityOff,
                        title = "Ocultar conteúdo não lançado",
                        sub = "Títulos com estreia depois de hoje somem das fileiras e do destaque",
                        trailing = { Switch(checked = st.hideUnreleased, onCheckedChange = { viewModel.setHideUnreleased(it) }, colors = switchColors()) },
                        onClick = { viewModel.setHideUnreleased(!st.hideUnreleased) },
                    )
                }
                item { com.aotv.app.ui.common.SettingsSection("Catálogos do destaque") }
                item {
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Catálogos do destaque", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "No automático, o destaque junta os títulos destes catálogos. Desligar um tira ele do destaque — a fileira continua na Início. A ordem é a da lista de fileiras.",
                                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                                )
                            }
                            Text(
                                "${st.heroCatalogOptions.count { it.selected }} de ${st.heroCatalogOptions.size}",
                                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        st.heroCatalogOptions.forEach { o ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    o.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.moveHeroCatalog(o.key, -1) }, enabled = o.canUp, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.ArrowUpward, "Subir ${o.label}", tint = if (o.canUp) TextSecondary else TextTertiary.copy(alpha = .35f), modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = { viewModel.moveHeroCatalog(o.key, 1) }, enabled = o.canDown, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.ArrowDownward, "Descer ${o.label}", tint = if (o.canDown) TextSecondary else TextTertiary.copy(alpha = .35f), modifier = Modifier.size(19.dp))
                                }
                                Switch(checked = o.selected, onCheckedChange = { viewModel.setHeroCatalog(o.key, it) }, colors = switchColors())
                            }
                        }
                        if (st.heroCatalogOptions.isEmpty()) {
                            Text(
                                "Nenhum catálogo pode alimentar o destaque agora.",
                                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                        TextButton(onClick = { viewModel.resetHeroCatalogs() }) {
                            Text("Redefinir para Padrão", color = Accent, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                item { com.aotv.app.ui.common.SettingsSection("Canais de TV dos addons") }
                item {
                    CardBox {
                        Text("Canais de TV dos addons", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Os catálogos de TV dos addons ligados também aparecem na aba Ao vivo. Ligar um aqui adiciona ele como fileira na Início — depois dá p/ reordenar e renomear na lista de fileiras abaixo.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        )
                        if (st.tvCatalogsLoading) {
                            Text("Lendo os catálogos de TV…", color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        st.tvCatalogOptions.forEach { o ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        o.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (o.type == "channel") "Canais" else "TV",
                                        color = TextTertiary, fontSize = 12.sp, maxLines = 1,
                                    )
                                }
                                Switch(
                                    checked = o.inHome,
                                    onCheckedChange = { viewModel.setTvCatalogInHome(o.key, it) },
                                    colors = switchColors(),
                                )
                            }
                        }
                        if (!st.tvCatalogsLoading && st.tvCatalogOptions.isEmpty()) {
                            Text(
                                "Nenhum addon ligado trouxe catálogo de TV.",
                                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                        st.tvDiag.forEach { d ->
                            Text(
                                "${d.addonName} respondeu, mas só declara ${d.declaredTypes.joinToString(", ") { tvTypeLabel(it) }} — sem catálogo de TV.",
                                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.aotv.app.ui.common.Eyebrow("Fileiras", Modifier.weight(1f).padding(start = 4.dp))
                            com.aotv.app.ui.common.FilterChipPill("Só as ligadas", st.onlyOn, { viewModel.setOnlyOn(!st.onlyOn) })
                        }
                        // Lote 24 (v1.34.0), item 11: segurar e arrastar
                        // reordena; os botões ↑↓ continuam como alternativa.
                        Text(
                            "Segure um cartão e arraste p/ reordenar — ou use ↑↓.",
                            color = TextTertiary, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                }
                if (st.kids) item {
                    Text(
                        "No perfil infantil as fileiras de addon não aparecem, porque não dá para filtrar por idade.",
                        color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                items(st.rows, key = { it.pref.key }) { row ->
                    // Lote 24 (v1.34.0), item 11: arrastar p/ reordenar.
                    // NÃO há API de reorder no Foundation fixado (1.7.0 —
                    // conferido no AAR: sem rememberReorderableLazyListState
                    // nem detectReorderAfterLongPress), então é pointerInput:
                    // toque longo + arrasto vertical. A cada passo (~60% da
                    // altura do cartão) o item troca de lugar com o vizinho
                    // pelo MESMO move() dos botões ↑↓ — a ordem persiste
                    // igual (home_layout_v1, por perfil). O gesto consome o
                    // arrasto, então a lista não rola enquanto arrasta.
                    val isDragging = dragKey == row.pref.key
                    val dragModifier = Modifier.pointerInput(row.pref.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragKey = row.pref.key; dragDy = 0f },
                            onDragEnd = { dragKey = null; dragDy = 0f },
                            onDragCancel = { dragKey = null; dragDy = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDy += dragAmount.y
                                val info = rowListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == row.pref.key }
                                val step = (info?.size ?: 200).toFloat()
                                if (step > 0f && dragDy >= step * 0.6f) {
                                    viewModel.move(row.pref.key, 1)
                                    dragDy -= step
                                } else if (step > 0f && dragDy <= -step * 0.6f) {
                                    viewModel.move(row.pref.key, -1)
                                    dragDy += step
                                }
                            },
                        )
                    }
                    HomeRowCard(
                        row = row,
                        itemModifier = Modifier.animateItemPlacement(),
                        dragModifier = dragModifier,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragDy else 0f,
                        onToggle = { viewModel.toggle(row.pref.key) },
                        onUp = { viewModel.move(row.pref.key, -1) },
                        onDown = { viewModel.move(row.pref.key, 1) },
                        onRename = { renaming = row },
                    )
                }
                if (st.rows.isEmpty()) item {
                    Text("Nenhuma fileira ligada.", color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
                // Lote 24 (v1.34.0), item 3: pastas da Home. O editor lista
                // TODAS as fileiras (st.allRows, sem o filtro "Só as ligadas")
                // p/ agrupar; a ordem das pastas na Início segue a ordem
                // das fileiras do layout.
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.aotv.app.ui.common.Eyebrow("Pastas", Modifier.weight(1f).padding(start = 4.dp))
                        }
                        Text(
                            "Agrupe fileiras sob um cabeçalho recolhível na Início.",
                            color = TextTertiary, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                }
                item {
                    com.aotv.app.ui.lote24.HomeFolderEditor(
                        settings = viewModel.lote24Settings,
                        availableRows = st.allRows.map { it.pref.key to it.pref.displayTitle },
                    )
                }
                below?.let { b -> item { Spacer(Modifier.height(12.dp)); b() } }
            }
        }
    }

    renaming?.let { r ->
        RenameDialog(
            original = r.pref.defaultTitle,
            current = r.pref.title,
            onSave = { viewModel.rename(r.pref.key, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = BackgroundCard,
            title = { Text("Restaurar o padrão?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Volta a ordem original, liga as fileiras do AOTV e do progresso e tira os nomes trocados. O destaque volta a ser automático e ligado. As fileiras de addon ficam como estão.",
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); confirmReset = false }) { Text("Restaurar", color = Accent, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

@Composable
private fun HomeRowCard(
    row: HomeCustomizeViewModel.Row,
    itemModifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRename: () -> Unit,
) {
    val p = row.pref
    val alpha by animateFloatAsState(if (p.enabled && row.blocked == null) 1f else 0.5f, tween(180), label = "rowAlpha")
    val isGenres = p.key == SystemRow.GENRES.key
    Column(
        itemModifier
            .fillMaxWidth()
            .then(dragModifier)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).alpha(alpha)) {
                Text(
                    p.displayTitle.ifBlank { "Catálogo" }, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (p.title.isNotBlank() && p.title != p.defaultTitle) {
                    Text("Original ${p.defaultTitle}", color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(checked = p.enabled, onCheckedChange = { onToggle() }, colors = switchColors())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).alpha(alpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SourcePill(row.source, if (row.source == "AOTV") Accent else if (row.source == "Seu progresso") Ok else TextSecondary)
                if (row.typeLabel.isNotBlank()) SourcePill(row.typeLabel, TextTertiary)
                when {
                    row.blocked != null -> Text(row.blocked, color = DangerText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    isGenres -> Text("Sempre por último", color = TextTertiary, fontSize = 12.sp, maxLines = 1)
                }
            }
            IconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Edit, "Renomear ${p.displayTitle}", tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
            if (!isGenres) {
                IconButton(onClick = onUp, enabled = row.canUp, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowUpward, "Subir ${p.displayTitle}", tint = if (row.canUp) TextSecondary else TextTertiary.copy(alpha = .35f), modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onDown, enabled = row.canDown, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Descer ${p.displayTitle}", tint = if (row.canDown) TextSecondary else TextTertiary.copy(alpha = .35f), modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(original: String, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.ifBlank { original }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Nome da fileira", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(60) }, singleLine = true,
                    label = { Text("Nome na Início") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Text("Nome original $original", color = TextTertiary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (text.trim() == original) "" else text) }) { Text("Salvar", color = Accent, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = {
            Row {
                if (current.isNotBlank()) TextButton(onClick = { onSave("") }) { Text("Usar o original", color = TextSecondary) }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
            }
        },
    )
}

@Composable
private fun CardBox(highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .then(if (highlight) Modifier.border(1.dp, DangerText.copy(alpha = .45f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SourcePill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Rótulo curto de um type declarado no manifest (p/ o diagnóstico de TV). */
private fun tvTypeLabel(type: String): String = when (type) {
    "movie" -> "Filmes"
    "series" -> "Séries"
    "tv" -> "TV"
    "channel" -> "Canais"
    else -> type
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary,
    uncheckedTrackColor = BackgroundCard2,
    uncheckedBorderColor = Border,
)
