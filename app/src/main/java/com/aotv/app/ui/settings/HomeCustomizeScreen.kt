package com.aotv.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.SystemRow
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/**
 * Personalizar Início. Liga e desliga cada fileira (do AOTV, do progresso e dos addons),
 * muda a ordem, troca o nome e escolhe de onde vem o destaque. Aplica na hora.
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
                    "Destaque", st.heroOptions.firstOrNull { it.key == st.heroSource }?.label ?: "Automático",
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
                Modifier.fillMaxSize(),
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

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.aotv.app.ui.common.Eyebrow("Fileiras", Modifier.weight(1f).padding(start = 4.dp))
                        com.aotv.app.ui.common.FilterChipPill("Só as ligadas", st.onlyOn, { viewModel.setOnlyOn(!st.onlyOn) })
                    }
                }
                if (st.kids) item {
                    Text(
                        "No perfil infantil as fileiras de addon não aparecem, porque não dá para filtrar por idade.",
                        color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                items(st.rows, key = { it.pref.key }) { row ->
                    HomeRowCard(
                        row = row,
                        onToggle = { viewModel.toggle(row.pref.key) },
                        onUp = { viewModel.move(row.pref.key, -1) },
                        onDown = { viewModel.move(row.pref.key, 1) },
                        onRename = { renaming = row },
                    )
                }
                if (st.rows.isEmpty()) item {
                    Text("Nenhuma fileira ligada.", color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
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
                    "Volta a ordem original, liga as fileiras do AOTV e do progresso e tira os nomes trocados. As fileiras de addon ficam como estão.",
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
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRename: () -> Unit,
) {
    val p = row.pref
    val alpha by animateFloatAsState(if (p.enabled && row.blocked == null) 1f else 0.5f, tween(180), label = "rowAlpha")
    val isGenres = p.key == SystemRow.GENRES.key
    Column(
        Modifier
            .fillMaxWidth()
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

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary,
    uncheckedTrackColor = BackgroundCard2,
    uncheckedBorderColor = Border,
)
