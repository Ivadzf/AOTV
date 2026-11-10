package com.aotv.app.ui.collections

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.collections.MediaCollection
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/**
 * Coleções do perfil, no formato do Nuvio. Cria, edita, ordena, fixa no topo, apaga, importa
 * (JSON colado, link ou conta Nuvio) e exporta o JSON para levar a outro aparelho ou ao Nuvio.
 */
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onEdit: (collectionId: String?) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val pad = com.aotv.app.ui.common.pagePadding()
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MediaCollection?>(null) }
    val folders = st.collections.sumOf { it.folders.size }

    fun export() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Coleções do AOTV")
            putExtra(Intent.EXTRA_TEXT, viewModel.exportJson())
        }
        ctx.startActivity(Intent.createChooser(send, "Exportar coleções"))
    }

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
                "Coleções", Modifier.weight(1f),
                sub = if (st.collections.isEmpty()) "Pastas de estúdios, streamings e franquias na Início"
                else countOf(st.collections.size, "coleção", "coleções") + " · " + countOf(folders, "pasta", "pastas"),
            )
            com.aotv.app.ui.common.GlassCircleButton(Icons.Default.Add, { onEdit(null) }, contentDescription = "Nova coleção")
        }

        val summary: @Composable () -> Unit = {
            com.aotv.app.ui.common.SummaryCard {
                Text("Coleções", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                com.aotv.app.ui.common.SummaryRow("Coleções", "${st.collections.size}")
                com.aotv.app.ui.common.SummaryRow("Pastas", "$folders")
                com.aotv.app.ui.common.SummaryRow("Fixadas no topo", "${st.collections.count { it.pinToTop }}")
                com.aotv.app.ui.common.SummaryRow("Conta Nuvio", if (st.nuvioLogged) "Conectada" else "Não conectada", if (st.nuvioLogged) Ok else TextSecondary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Usa o mesmo formato do Nuvio. O JSON exportado daqui entra lá, e o de lá entra aqui. Cada coleção vira uma fileira da Início, que dá para ordenar em Personalizar Início.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AotvButton("Nova coleção", { onEdit(null) }, icon = Icons.Default.Add, tone = ButtonTone.PRIMARY, compact = true)
                        AotvButton("Importar", { importing = true }, icon = Icons.Default.CloudDownload, compact = true)
                        AotvButton("Exportar", ::export, icon = Icons.Default.Share, enabled = st.collections.isNotEmpty(), compact = true)
                    }
                }
                (st.error ?: st.message)?.let { msg ->
                    item {
                        CollectionCard(highlight = true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(msg, color = if (st.error != null) DangerText else TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearMessage) { Text("Ok", color = Accent, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                item { com.aotv.app.ui.common.SettingsSection("Suas coleções") }
                if (st.collections.isEmpty()) item {
                    com.aotv.app.ui.common.AotvEmptyState(
                        Icons.Default.ViewModule, "Nenhuma coleção ainda",
                        "Monte uma com estúdios, streamings, franquias, atores e catálogos dos addons, ou importe o JSON do Nuvio.",
                    )
                }
                itemsIndexed(st.collections, key = { _, c -> c.id }) { i, c ->
                    CollectionItem(
                        c,
                        canUp = i > 0, canDown = i < st.collections.lastIndex,
                        onEdit = { onEdit(c.id) },
                        onUp = { viewModel.move(c.id, -1) },
                        onDown = { viewModel.move(c.id, 1) },
                        onPin = { viewModel.togglePin(c.id) },
                        onDelete = { deleting = c },
                    )
                }
                below?.let { b -> item { Spacer(Modifier.height(12.dp)); b() } }
            }
        }
    }

    if (importing) ImportDialog(
        st = st,
        onPaste = viewModel::importText,
        onUrl = viewModel::importUrl,
        onNuvio = viewModel::importNuvio,
        onDismiss = { importing = false },
    )
    // Importou, fecha o diálogo. A mensagem fica na tela.
    LaunchedEffect(st.message) { if (st.message != null) importing = false }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = BackgroundCard,
            title = { Text("Apagar ${c.title}?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("A coleção sai da Início deste perfil, com " + countOf(c.folders.size, "pasta", "pastas") + ".", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { viewModel.remove(c.id); deleting = null }) { Text("Apagar", color = DangerText, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

@Composable
private fun CollectionItem(
    c: MediaCollection,
    canUp: Boolean,
    canDown: Boolean,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    CollectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sources = c.folders.sumOf { it.sources.size }
                Text(
                    countOf(c.folders.size, "pasta", "pastas") + " · " + countOf(sources, "fonte", "fontes") + " · " + if (c.tabbed) "abas" else "fileiras",
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                )
                if (c.pinToTop || c.folders.isEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.pinToTop) CollectionTag("Fixada no topo")
                    if (c.folders.isEmpty()) CollectionTag("Sem pastas, não aparece na Início", TextTertiary)
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar", tint = TextPrimary) }
            IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Default.KeyboardArrowUp, "Subir", tint = if (canUp) TextPrimary else TextTertiary) }
            IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Default.KeyboardArrowDown, "Descer", tint = if (canDown) TextPrimary else TextTertiary) }
            IconButton(onClick = onPin) {
                Icon(if (c.pinToTop) Icons.Default.PushPin else Icons.Outlined.PushPin, if (c.pinToTop) "Soltar do topo" else "Fixar no topo", tint = if (c.pinToTop) Accent else TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Apagar", tint = DangerText) }
        }
    }
}

/** Importar por JSON colado, link do JSON ou conta Nuvio. O botão gira e trava até a resposta. */
@Composable
private fun ImportDialog(
    st: CollectionsViewModel.Ui,
    onPaste: (String) -> Unit,
    onUrl: (String) -> Unit,
    onNuvio: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf("paste") }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent,
    )
    AlertDialog(
        onDismissRequest = { if (st.busy == null) onDismiss() },
        containerColor = BackgroundCard,
        title = { Text("Importar coleções", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.aotv.app.ui.common.FilterChipPill("Colar JSON", mode == "paste", { mode = "paste" })
                    com.aotv.app.ui.common.FilterChipPill("Link", mode == "url", { mode = "url" })
                    com.aotv.app.ui.common.FilterChipPill("Conta Nuvio", mode == "nuvio", { mode = "nuvio" })
                }
                when (mode) {
                    "paste" -> OutlinedTextField(
                        value = text, onValueChange = { text = it }, minLines = 4, maxLines = 8,
                        placeholder = { Text("Cole aqui o JSON exportado do Nuvio ou do AOTV", color = TextTertiary) },
                        colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    )
                    "url" -> OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true,
                        placeholder = { Text("https://…/colecoes.json", color = TextTertiary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    )
                    else -> Text(
                        if (st.nuvioLogged) "Traz as coleções do perfil principal da conta Nuvio. Para outro perfil, use Configurações, Contas."
                        else "Entre na conta Nuvio em Configurações, Contas, e volte aqui.",
                        color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
                Text("Coleção com o mesmo id de uma que já existe é substituída. As outras ficam.", color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp)
                st.error?.let { Text(it, color = DangerText, fontSize = 13.sp, lineHeight = 18.sp) }
            }
        },
        confirmButton = {
            when (mode) {
                "paste" -> AotvButton("Importar", { onPaste(text) }, icon = Icons.Default.CloudDownload, tone = ButtonTone.PRIMARY,
                    busy = st.busy == "paste", busyLabel = "Importando…", enabled = st.busy == null && text.isNotBlank(), compact = true)
                "url" -> AotvButton("Baixar e importar", { onUrl(url) }, icon = Icons.Default.CloudDownload, tone = ButtonTone.PRIMARY,
                    busy = st.busy == "url", busyLabel = "Baixando…", enabled = st.busy == null && url.isNotBlank(), compact = true)
                else -> AotvButton("Importar da conta", onNuvio, icon = Icons.Default.CloudDownload, tone = ButtonTone.PRIMARY,
                    busy = st.busy == "nuvio", busyLabel = "Importando…", enabled = st.busy == null && st.nuvioLogged, compact = true)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = st.busy == null) { Text("Fechar", color = TextSecondary) } },
    )
}
