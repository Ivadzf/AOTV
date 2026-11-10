package com.aotv.app.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.collections.CollectionFolder
import com.aotv.app.data.collections.CollectionSource
import com.aotv.app.data.collections.MediaCollection
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** Editor completo da coleção, no modelo do Nuvio. Grava só no Salvar. */
@Composable
fun CollectionEditorScreen(
    onBack: () -> Unit,
    viewModel: CollectionEditorViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(st.saved) { if (st.saved) onBack() }
    val leave: () -> Unit = {
        when {
            st.folder != null -> viewModel.closeFolder()
            st.dirty -> confirmLeave = true
            else -> onBack()
        }
    }
    BackHandler(onBack = leave)

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = leave, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, if (st.folder != null) "Voltar para a coleção" else "Voltar", tint = TextPrimary)
            }
            val folder = st.currentFolder
            com.aotv.app.ui.common.PageHeader(
                if (folder != null) folder.title.ifBlank { "Pasta nova" } else if (st.isNew) "Nova coleção" else "Editar coleção",
                Modifier.weight(1f),
                sub = if (folder != null) "Pasta de ${st.draft.title.ifBlank { "coleção sem nome" }}" else countOf(st.draft.folders.size, "pasta", "pastas"),
            )
            AotvButton("Salvar", viewModel::save, icon = Icons.Default.Check, tone = ButtonTone.PRIMARY, compact = true)
        }
        st.error?.let {
            Text(
                it, color = DangerText, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 6.dp),
            )
        }
        val folder = st.currentFolder
        if (folder != null) FolderEditor(folder, st, viewModel) else CollectionForm(st.draft, viewModel)
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        containerColor = BackgroundCard,
        title = { Text("Sair sem salvar?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Text("As mudanças desta coleção se perdem.", color = TextSecondary, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Sair", color = DangerText, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Continuar editando", color = Accent) } },
    )
}

@Composable
private fun CollectionForm(c: MediaCollection, vm: CollectionEditorViewModel) {
    val pad = com.aotv.app.ui.common.pagePadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CollectionCard {
                EditorField("Nome da coleção", c.title, vm::setTitle, hint = "Ex. Estúdios, Streamings, Franquias")
                Spacer(Modifier.height(10.dp))
                EditorField("Imagem de fundo (link, opcional)", c.backdropImageUrl.orEmpty(), vm::setBackdrop, url = true)
                Spacer(Modifier.height(6.dp))
                SwitchLine("Fixar no topo da Início", "Aparece antes das outras fileiras", c.pinToTop, vm::setPin)
            }
        }
        item {
            CollectionCard {
                ChipGroup("Como a pasta abre") {
                    FilterChipPill("Abas com grade", c.viewMode.equals(MediaCollection.VIEW_TABS, true), { vm.setViewMode(MediaCollection.VIEW_TABS) })
                    FilterChipPill("Uma fileira por fonte", c.viewMode.equals(MediaCollection.VIEW_ROWS, true), { vm.setViewMode(MediaCollection.VIEW_ROWS) })
                    FilterChipPill("Seguir a Início", c.viewMode.equals(MediaCollection.VIEW_FOLLOW, true), { vm.setViewMode(MediaCollection.VIEW_FOLLOW) })
                }
                if (c.tabbed) {
                    Spacer(Modifier.height(6.dp))
                    SwitchLine("Aba Tudo", "Junta as fontes da pasta numa aba só, intercaladas", c.showAllTab, vm::setShowAll)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.aotv.app.ui.common.Eyebrow("Pastas", Modifier.weight(1f).padding(start = 4.dp))
                AotvButton("Nova pasta", vm::addFolder, icon = Icons.Default.Add, compact = true)
            }
        }
        if (c.folders.isEmpty()) item {
            Text(
                "Cada pasta junta fontes. Por exemplo, uma pasta Marvel com o estúdio Marvel Studios, ou uma pasta Netflix com o streaming e um catálogo de addon.",
                color = TextTertiary, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        itemsIndexed(c.folders, key = { _, f -> f.id }) { i, f ->
            CollectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(72.dp)) { FolderTile(f.copy(hideTitle = true, tileShape = "poster"), onClick = { vm.openFolder(i) }) }
                    Column(Modifier.weight(1f)) {
                        Text(f.title.ifBlank { "Pasta sem nome" }, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(countOf(f.sources.size, "fonte", "fontes") + " · " + shapeLabel(f.shape), color = TextSecondary, fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.openFolder(i) }) { Icon(Icons.Default.Edit, "Editar pasta", tint = TextPrimary) }
                    IconButton(onClick = { vm.moveFolder(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (i > 0) TextPrimary else TextTertiary) }
                    IconButton(onClick = { vm.moveFolder(i, 1) }, enabled = i < c.folders.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "Descer", tint = if (i < c.folders.lastIndex) TextPrimary else TextTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.removeFolder(i) }) { Icon(Icons.Default.Delete, "Apagar pasta", tint = DangerText) }
                }
            }
        }
    }
}

private fun shapeLabel(shape: String) = when (shape) { "landscape" -> "paisagem"; "square" -> "quadrado"; else -> "pôster" }

@Composable
private fun FolderEditor(f: CollectionFolder, st: CollectionEditorViewModel.Ui, vm: CollectionEditorViewModel) {
    val pad = com.aotv.app.ui.common.pagePadding()
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FolderTile(f.copy(title = f.title.ifBlank { "Pasta" }), onClick = {})
                Text(
                    "Assim a pasta aparece na Início. Sem imagem, vale o emoji. Sem emoji, a inicial do nome.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
            }
        }
        item {
            CollectionCard {
                EditorField("Nome da pasta", f.title, { v -> vm.updateFolder { it.copy(title = v.take(60)) } }, hint = "Ex. Marvel")
                Spacer(Modifier.height(10.dp))
                EditorField("Imagem da capa (link)", f.coverImageUrl.orEmpty(), { v -> vm.updateFolder { it.copy(coverImageUrl = v.trim().ifBlank { null }) } }, url = true)
                Spacer(Modifier.height(10.dp))
                EditorField("Emoji da capa (sem imagem)", f.coverEmoji.orEmpty(), { v -> vm.updateFolder { it.copy(coverEmoji = v.trim().take(8).ifBlank { null }) } })
                Spacer(Modifier.height(10.dp))
                EditorField("GIF animado (link, opcional)", f.focusGifUrl.orEmpty(), { v -> vm.updateFolder { it.copy(focusGifUrl = v.trim().ifBlank { null }) } }, url = true)
                if (!f.focusGifUrl.isNullOrBlank()) SwitchLine("Animar o GIF", "Desligado, a capa fica parada", f.focusGifEnabled) { on -> vm.updateFolder { it.copy(focusGifEnabled = on) } }
                Spacer(Modifier.height(8.dp))
                ChipGroup("Formato do cartão") {
                    FilterChipPill("Pôster", f.shape == "poster", { vm.updateFolder { it.copy(tileShape = "poster") } })
                    FilterChipPill("Paisagem", f.shape == "landscape", { vm.updateFolder { it.copy(tileShape = "landscape") } })
                    FilterChipPill("Quadrado", f.shape == "square", { vm.updateFolder { it.copy(tileShape = "square") } })
                }
                Spacer(Modifier.height(6.dp))
                SwitchLine("Esconder o nome", "Para capa que já traz o nome escrito", f.hideTitle) { on -> vm.updateFolder { it.copy(hideTitle = on) } }
            }
        }
        item {
            CollectionCard {
                Text("Topo da pasta", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                EditorField("Imagem de fundo (link)", f.heroBackdropUrl.orEmpty(), { v -> vm.updateFolder { it.copy(heroBackdropUrl = v.trim().ifBlank { null }) } }, url = true)
                Spacer(Modifier.height(10.dp))
                EditorField("Logo no lugar do nome (link)", f.titleLogoUrl.orEmpty(), { v -> vm.updateFolder { it.copy(titleLogoUrl = v.trim().ifBlank { null }) } }, url = true)
                Spacer(Modifier.height(10.dp))
                EditorField("Vídeo de fundo (link, usado na TV)", f.heroVideoUrl.orEmpty(), { v -> vm.updateFolder { it.copy(heroVideoUrl = v.trim().ifBlank { null }) } }, url = true)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.aotv.app.ui.common.Eyebrow("Fontes", Modifier.weight(1f).padding(start = 4.dp))
                AotvButton("Adicionar fonte", { picking = true }, icon = Icons.Default.Add, tone = ButtonTone.PRIMARY, compact = true)
            }
        }
        if (f.sources.isEmpty()) item {
            Text(
                "Sem fonte a pasta abre vazia. Adicione um catálogo de addon, um estúdio, um streaming, uma franquia, um ator ou uma descoberta do TMDB.",
                color = TextTertiary, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        itemsIndexed(f.sources, key = { i, s -> "$i:${s.key}" }) { i, s -> SourceItem(s, i, f.sources.lastIndex, vm, onEdit = { editing = i }) }
    }
    if (picking) SourcePickerDialog(st, vm, onDismiss = { picking = false })
    editing?.let { i ->
        f.sources.getOrNull(i)?.let { s -> SourceEditDialog(s, st, vm, onSave = { vm.updateSource(i, it) }, onDismiss = { editing = null }) }
            ?: run { editing = null }
    }
}

@Composable
private fun SourceItem(s: CollectionSource, i: Int, last: Int, vm: CollectionEditorViewModel, onEdit: () -> Unit) {
    CollectionCard(highlight = s.isTrakt) {
        Text(sourceLabel(s), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sourceKind(s), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar fonte", tint = TextPrimary) }
            IconButton(onClick = { vm.moveSource(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (i > 0) TextPrimary else TextTertiary) }
            IconButton(onClick = { vm.moveSource(i, 1) }, enabled = i < last) { Icon(Icons.Default.KeyboardArrowDown, "Descer", tint = if (i < last) TextPrimary else TextTertiary) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.removeSource(i) }) { Icon(Icons.Default.Delete, "Tirar fonte", tint = DangerText) }
        }
    }
}
