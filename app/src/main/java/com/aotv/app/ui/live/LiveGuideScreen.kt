package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.ui.movies.RecordProgramDialog
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Guia em grade (Lote 7). Canais nas linhas, horários nas colunas, programa
 * atual destacado, linha vermelha no "agora". Reutiliza o EPG já carregado
 * ([LiveTvViewModel.View.epg]); sem guia, a linha mostra só o canal, sem
 * inventar programação. Respeita os filtros e a busca da aba Ao vivo, porque
 * usa as mesmas fileiras ([LiveTvViewModel.View.rows]).
 *
 * Toques: no canal ou no programa atual abre ao vivo; no programa passado COM
 * catch-up abre a gravação ([livePlayerCatchupRoute]); no programa passado sem
 * catch-up ou futuro mostra o detalhe, sem tocar nada.
 *
 * A grade é a mesma tela no celular e na TV (a rota "Ao vivo" não tem tela
 * própria de TV): as células são clicáveis e focáveis, mas o scroll
 * horizontal por controle remoto não foi testado — ver o caveat no relatório.
 *
 * Lote 19 (v1.29.0, item 8 — guia estilo JOTV): cabeçalho "N canais ·
 * M categorias", busca dentro do guia (canal, grupo ou título do programa),
 * categorias com contagem (barra lateral na TV, chips no celular) e
 * alternância Cartões/Lista. A categoria é o `group` que a fonte trouxe
 * (M3U `group-title` / categoria Xtream, com ";" separando várias); canal
 * sem categoria cai em "Sem grupo" ([LiveTvViewModel.NO_GROUP]) — nunca
 * inventada. O modo Cartões/Lista, a categoria e a busca vivem só nesta
 * tela (rememberSaveable, sem pref nova) e o toque continua abrindo o
 * mesmo destino de hoje.
 */
@Composable
fun LiveGuideScreen(
    onPlay: (String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
    /** Rota de catch-up pronta (ver [livePlayerCatchupRoute]); sem ele, passado com catch-up cai no ao vivo. */
    onPlayCatchup: ((String) -> Unit)? = null,
    /** Voltar (rota própria do guia, aberta do player — Lote 12, v1.22.0). Sem ele, a grade vive dentro da aba Ao vivo e não tem botão de voltar. */
    onBack: (() -> Unit)? = null,
    /** Buscar o título do programa nos addons (Lote 12, v1.22.0): abre a busca com a query. Sem ele, o botão some. */
    onSearchTitle: ((String) -> Unit)? = null,
    /** Canal a destacar ao abrir (vindo do player). */
    focusChannelId: String? = null,
) {
    val v by viewModel.view.collectAsStateWithLifecycle()
    // Guia instantâneo (Lote 12, v1.22.0): a grade já abriu com o EPG salvo
    // em disco (o repo carrega o arquivo no init); aqui só pede a
    // atualização em 2º plano quando passou o intervalo — sem travar a tela.
    LaunchedEffect(Unit) { viewModel.refreshEpgIfStale() }
    // Lote 8 (v1.18.0): janela visível continua 4 h, mas os limites vêm das
    // prefs — não rola para antes de (agora − horas atrás) nem para depois de
    // (agora + dias à frente). windowStart = agora − 1 h + deslocamento, então
    // o mínimo é −(horasAtrás − 1) e o máximo é 1 + diasÀFrente × 24.
    // Sem EPG não há programação — nunca inventar (a grade mostra "Sem guia").
    val minShift = -(v.guideHoursBack - 1)
    val maxShift = 1 + v.guideDaysAhead * 24
    var shiftHours by remember { mutableIntStateOf(0) }
    val shift = shiftHours.coerceIn(minShift, maxShift)
    val now = v.now
    val windowStart = now - HOUR_MS + shift * HOUR_MS
    val windowEnd = windowStart + WINDOW_MS
    val totalWidth = HOUR_WIDTH_DP * WINDOW_HOURS
    // Um só ScrollState para o cabeçalho e todas as linhas: as colunas ficam alinhadas.
    val scroll = rememberScrollState()
    // Lote 12 (v1.22.0): abrir do player rola até o canal que estava em tela cheia.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(focusChannelId, v.rows) {
        val idx = v.rows.indexOfFirst { it.channel.id == focusChannelId }
        if (idx >= 0) listState.scrollToItem(idx)
    }
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR_WIDTH_DP.toPx() }
    val nowXdp = ((now - windowStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp
    // Ao abrir (e a cada deslocamento), rola para o "agora" ficar visível.
    LaunchedEffect(shift) { scroll.scrollTo((((now - windowStart).toFloat() / HOUR_MS) * hourPx).toInt().coerceAtLeast(0)) }
    var detail by remember { mutableStateOf<Pair<LiveChannel, LiveProgram>?>(null) }
    // Lote 8 (v1.18.0): programa a gravar (diálogo da Frente 2).
    var record by remember { mutableStateOf<Pair<LiveChannel, LiveProgram>?>(null) }
    // Lote 19 (v1.29.0, item 8 — guia estilo JOTV): estado só desta tela.
    val isTv = com.aotv.app.ui.common.LocalIsTv.current
    var guideQuery by rememberSaveable { mutableStateOf("") }
    // Lote 25 (v1.35.0), item 3 — voz + QR na busca do guia (reusa a voz do
    // Lote 3 e o QR da busca principal; na TV não há microfone).
    val guideVoice = com.aotv.app.ui.movies.rememberVoiceSearch(onResult = { heard -> guideQuery = heard })
    var guideQrOpen by remember { mutableStateOf(false) }
    if (guideQrOpen) com.aotv.app.ui.movies.SearchQrDialog(query = guideQuery, onDismiss = { guideQrOpen = false })
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    // Contador p/ rolar a grade ao topo quando a categoria muda (o efeito
    // só rola depois da 1ª mudança — na abertura, o scroll p/ focusChannelId
    // acima continua valendo).
    var categorySeq by remember { mutableIntStateOf(0) }
    // Categorias = grupos da fonte, com contagem, na ordem da lista
    // ([View.groups]); os totais do cabeçalho são os da fonte inteira, e as
    // linhas seguem o filtro/busca da aba Ao vivo ([View.rows]) — com um
    // filtro da aba ativo, a vista pode ter menos canais que o total.
    val categories: List<Pair<String, Int>> = v.groups
    val guideRows: List<LiveRow> = remember(v.rows, v.epg, selectedCategory, guideQuery, v.cleanGroupNames) {
        val needle = guideQuery.trim().lowercase()
        v.rows.filter { row ->
            val inCategory = selectedCategory == null ||
                LiveTvViewModel.groupNames(row.channel, v.cleanGroupNames).contains(selectedCategory)
            if (!inCategory) return@filter false
            if (needle.isEmpty()) return@filter true
            if (row.channel.name.lowercase().contains(needle)) return@filter true
            if (LiveTvViewModel.groupLabel(row.channel).lowercase().contains(needle)) return@filter true
            (v.epg[row.channel.id].orEmpty()).any { program -> program.title.lowercase().contains(needle) }
        }
    }
    // Seções da vista em Lista, na ordem das categorias (canal em 2 grupos
    // aparece nas 2, igual a [View.groupedRows]).
    val guideSections: List<Pair<String, List<LiveRow>>> = remember(guideRows, categories, v.cleanGroupNames) {
        categories.mapNotNull { category ->
            val rows = guideRows.filter { row ->
                LiveTvViewModel.groupNames(row.channel, v.cleanGroupNames).contains(category.first)
            }
            if (rows.isEmpty()) null else category.first to rows
        }
    }
    /** Troca a categoria do guia (null = Todas) e rola a grade ao topo. */
    fun selectGuideCategory(category: String?) {
        selectedCategory = category
        categorySeq++
    }
    // Rola a grade ao topo quando a categoria muda (só depois da 1ª mudança
    // — na abertura, o scroll p/ focusChannelId acima continua valendo).
    LaunchedEffect(categorySeq) {
        if (categorySeq > 0 && guideRows.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        // Lote 12 (v1.22.0): voltar da rota própria do guia retorna ao
        // player do canal que estava aberto (popBackStack na navegação).
        if (onBack != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar ao canal", tint = TextPrimary)
                }
                Text("Guia de programação", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        // Lote 19 (v1.29.0, item 8): cabeçalho estilo JOTV — "N canais ·
        // M categorias" (totais da fonte) + quantos a vista atual mostra
        // quando há categoria ou busca no guia. Na TV, a dica de pular seção.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${v.total} canais · ${v.groups.size} categorias" +
                    if (selectedCategory != null || guideQuery.isNotBlank()) " · ${guideRows.size} nesta vista" else "",
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (isTv) {
                Text("Segure ↑↓ para pular seção", color = TextTertiary, fontSize = 12.sp)
            }
        }
        // Honestidade: "Sem grupo" é o canal cuja fonte não trouxe categoria.
        if (v.groups.any { group -> group.first == LiveTvViewModel.NO_GROUP }) {
            Text(
                "Canais sem categoria na fonte aparecem em \"Sem grupo\".",
                color = TextTertiary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = com.aotv.app.ui.common.pagePadding()),
            )
        }
        OutlinedTextField(
            value = guideQuery,
            onValueChange = { newQuery -> guideQuery = newQuery },
            singleLine = true,
            placeholder = { Text("Buscar no guia — canal ou programa") },
            leadingIcon = { Icon(Icons.Default.Search, "Buscar", tint = TextTertiary) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTv && guideVoice.available) {
                        IconButton(onClick = guideVoice.start, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Mic,
                                if (guideVoice.listening) "Ouvindo…" else "Buscar por voz",
                                tint = if (guideVoice.listening) Accent else TextTertiary,
                            )
                        }
                    }
                    if (guideQuery.isNotBlank()) {
                        IconButton(onClick = { guideQrOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.QrCode2, "Mostrar QR desta busca", tint = TextTertiary)
                        }
                        IconButton(onClick = { guideQuery = "" }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, "Limpar busca", tint = TextTertiary)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.aotv.app.ui.common.FilterChipPill(label = "Cartões", selected = !listMode, onClick = { listMode = false }, icon = Icons.Default.GridView)
            com.aotv.app.ui.common.FilterChipPill(label = "Lista", selected = listMode, onClick = { listMode = true }, icon = Icons.Default.List)
            if (!isTv) {
                Text("A lista mostra o que está passando agora", color = TextTertiary, fontSize = 12.sp)
            }
        }
        // Categorias no celular: chips com contagem (na TV é a barra lateral).
        if (!isTv) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    com.aotv.app.ui.common.FilterChipPill(
                        label = "Todas ${v.total}", selected = selectedCategory == null,
                        onClick = { selectGuideCategory(null) },
                    )
                }
                items(categories, key = { category -> category.first }) { category ->
                    com.aotv.app.ui.common.FilterChipPill(
                        label = "${category.first} ${category.second}", selected = selectedCategory == category.first,
                        onClick = { selectGuideCategory(category.first) },
                    )
                }
            }
        }
        if (!listMode) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.aotv.app.ui.common.FilterChipPill("‹ 1 h", false, { shiftHours = (shiftHours - 1).coerceAtLeast(minShift) }, icon = Icons.Default.ChevronLeft)
            com.aotv.app.ui.common.FilterChipPill("Agora", shift == 0, { shiftHours = 0.coerceIn(minShift, maxShift) })
            com.aotv.app.ui.common.FilterChipPill("1 h ›", false, { shiftHours = (shiftHours + 1).coerceAtMost(maxShift) }, icon = Icons.Default.ChevronRight)
            Text(
                "${hhmm(windowStart)} – ${hhmm(windowEnd)}", color = TextTertiary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            // Guia instantâneo (Lote 12, v1.22.0): de quando é o guia salvo
            // que a grade está mostrando (nunca inventado: sem hora, sem linha).
            if (v.epgUpdatedAt > 0) {
                Text(
                    if (v.epgLoading) "Guia de ${hhmm(v.epgUpdatedAt)} · atualizando…" else "Guia de ${hhmm(v.epgUpdatedAt)}",
                    color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CHANNEL_WIDTH_DP))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(totalWidth).height(28.dp)) {
                    var h = windowStart - windowStart % HOUR_MS
                    while (h <= windowEnd) {
                        Text(
                            hhmm(h), color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = ((h - windowStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp + 6.dp, y = 4.dp),
                        )
                        h += HOUR_MS
                    }
                    Box(
                        Modifier.offset(x = nowXdp).width(2.dp).fillMaxHeight().background(LiveRed),
                    )
                }
            }
        }
        }

        Row(Modifier.fillMaxWidth().weight(1f)) {
            if (isTv) {
                GuideCategorySidebar(
                    categories = categories,
                    total = v.total,
                    selected = selectedCategory,
                    onSelect = { category -> selectGuideCategory(category) },
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (guideRows.isEmpty()) {
                    Text(
                        "Nenhum canal nesta vista — mude a categoria ou limpe a busca.",
                        color = TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.padding(com.aotv.app.ui.common.pagePadding()),
                    )
                } else if (listMode) {
                    GuideListContent(sections = guideSections, now = now, onPlay = onPlay)
                } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                // TV (Lote 19, item 8): segurar ↑↓ na grade pula de categoria
                // (a dica está no cabeçalho); o toque curto continua andando
                // o foco normalmente (repeatCount 0 passa direto).
                if (!isTv) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key != Key.DirectionDown && event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.repeatCount == 0) return@onPreviewKeyEvent false
                val all = listOf<String?>(null) + categories.map { category -> category.first }
                val idx = all.indexOf(selectedCategory)
                val next = if (event.key == Key.DirectionDown) (idx + 1).coerceAtMost(all.size - 1) else (idx - 1).coerceAtLeast(0)
                if (next == idx) return@onPreviewKeyEvent false
                selectGuideCategory(all[next])
                true
            },
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(guideRows, key = { row -> row.channel.id }) { row ->
                val channel = row.channel
                val programs = (v.epg[channel.id].orEmpty()).filter { it.stop > windowStart && it.start < windowEnd }
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.width(CHANNEL_WIDTH_DP).fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp)).background(BackgroundCard2)
                            .clickable { onPlay(channel.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(width = 40.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                            if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            else Text(channel.name.take(2).uppercase(), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(channel.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Box(Modifier.width(totalWidth).fillMaxHeight()) {
                            if (programs.isEmpty()) {
                                Text(
                                    "Sem guia para este canal", color = TextTertiary, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 10.dp).align(Alignment.CenterStart),
                                )
                            }
                            programs.forEach { p ->
                                val isCurrent = p.isNow(now)
                                val isPast = p.stop <= now
                                val hasCatchup = channel.catchupDays > 0 && isPast &&
                                    (now - p.start) <= channel.catchupDays * 86_400_000L
                                GuideProgramCell(
                                    program = p, now = now, windowStart = windowStart,
                                    hasCatchup = hasCatchup,
                                    onClick = {
                                        when {
                                            isCurrent -> onPlay(channel.id)
                                            hasCatchup -> {
                                                val route = livePlayerCatchupRoute(channel.id, p.start / 1000, p.stop / 1000)
                                                if (onPlayCatchup != null) onPlayCatchup(route) else onPlay(channel.id)
                                            }
                                            else -> detail = channel to p
                                        }
                                    },
                                )
                            }
                            Box(Modifier.offset(x = nowXdp).width(2.dp).fillMaxHeight().background(LiveRed))
                        }
                    }
                }
            }
        }
                }
            }
        }
    }

    detail?.let { pair ->
        val channel = pair.first
        val program = pair.second
        val isCurrent = program.isNow(now)
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = BackgroundCard,
            title = { Text(program.title, color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Lote 12 (v1.22.0): pôster do programa, SÓ quando o EPG
                    // traz `<icon src>` — sem imagem, sem espaço reservado.
                    if (program.image.isNotBlank()) {
                        AsyncImage(
                            program.image, program.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Text("${channel.name} · ${hhmm(program.start)} até ${hhmm(program.stop)}", color = TextSecondary, fontSize = 14.sp)
                    if (program.desc.isNotBlank()) Text(program.desc, color = TextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                // Lote 12 (v1.22.0): "Buscar nos addons" abre a busca com o
                // título do programa (mesma busca do detalhe, via rota).
                Column(horizontalAlignment = Alignment.End) {
                    when {
                        isCurrent -> TextButton(onClick = { onPlay(channel.id); detail = null }) { Text("Assistir ao vivo", color = Accent, fontWeight = FontWeight.ExtraBold) }
                        // Lote 8 (v1.18.0): gravar programa futuro ou atual (passado não grava).
                        program.stop > now -> TextButton(onClick = { record = channel to program; detail = null }) { Text("Gravar", color = Accent, fontWeight = FontWeight.ExtraBold) }
                        else -> TextButton(onClick = { detail = null }) { Text("Fechar", color = TextSecondary) }
                    }
                    if (onSearchTitle != null) {
                        TextButton(onClick = { onSearchTitle(program.title); detail = null }) { Text("Buscar nos addons", color = Accent) }
                    }
                }
            },
            dismissButton = {
                if (isCurrent || program.stop > now) TextButton(onClick = { detail = null }) { Text("Fechar", color = TextSecondary) }
            },
        )
    }

    // Lote 8 (v1.18.0): diálogo de agendar gravação (Frente 2).
    record?.let { pair ->
        val channel = pair.first
        val program = pair.second
        RecordProgramDialog(
            channelId = channel.id, channelName = channel.name, channelUrl = channel.url,
            channelHeaders = channel.headers, programTitle = program.title,
            startMs = program.start, stopMs = program.stop,
            onDismiss = { record = null },
            onScheduled = { record = null },
        )
    }
}

@Composable
private fun GuideProgramCell(
    program: LiveProgram,
    now: Long,
    windowStart: Long,
    onClick: () -> Unit,
    hasCatchup: Boolean = false,
) {
    val isCurrent = program.isNow(now)
    val isPast = program.stop <= now
    val visibleStart = maxOf(program.start, windowStart)
    val visibleEnd = minOf(program.stop, windowStart + WINDOW_MS)
    if (visibleEnd <= visibleStart) return
    val x = ((visibleStart - windowStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp
    val w = ((visibleEnd - visibleStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp
    val bg = when {
        // Dourado: programa passado com catch-up disponível.
        hasCatchup -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
        isCurrent -> AccentSoft
        isPast -> Surface2
        else -> BackgroundCard2
    }
    val titleColor = when {
        isCurrent -> TextPrimary
        isPast -> TextTertiary
        else -> TextSecondary
    }
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight().padding(end = 2.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (isCurrent) Modifier.border(1.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(program.title, color = titleColor, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${hhmm(program.start)}–${hhmm(program.stop)}", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

private const val HOUR_MS = 3_600_000L
private const val WINDOW_HOURS = 4
private val WINDOW_MS = WINDOW_HOURS * HOUR_MS
private val HOUR_WIDTH_DP = 150.dp
private val CHANNEL_WIDTH_DP = 132.dp

/** Rota do player ao vivo com catch-up. [startSec]/[stopSec] em epoch segundos. */
fun livePlayerCatchupRoute(channelId: String, startSec: Long, stopSec: Long): String =
    "movies_live_player/$channelId?start=$startSec&stop=$stopSec"

// ── Lote 19 (v1.29.0, item 8 — guia estilo JOTV) ──────────────────────────

/** Barra lateral de Categorias (TV): "Todas" + cada grupo com a contagem. Itens clicáveis e focáveis (D-pad). */
@Composable
private fun GuideCategorySidebar(
    categories: List<Pair<String, Int>>,
    total: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.width(200.dp).fillMaxHeight().padding(end = 8.dp)) {
        Text(
            "Categorias", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                GuideCategoryItem(label = "Todas", count = total, selected = selected == null, onClick = { onSelect(null) })
            }
            items(categories, key = { category -> category.first }) { category ->
                GuideCategoryItem(
                    label = category.first, count = category.second,
                    selected = selected == category.first, onClick = { onSelect(category.first) },
                )
            }
        }
    }
}

@Composable
private fun GuideCategoryItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentFill else BackgroundCard2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label, color = if (selected) Color.White else TextPrimary,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text("$count", color = if (selected) Color.White else TextTertiary, fontSize = 13.sp)
    }
}

/**
 * Vista em Lista do guia: canais agrupados por categoria, com o programa
 * atual e o seguinte. O toque na linha abre o ao vivo do canal — o mesmo
 * destino do toque na célula do canal na grade. Sem linha do tempo: o
 * passado com catch-up e os programas futuros continuam só nos Cartões.
 * Na TV, segurar ↑↓ pula de seção (cada seção é um item da lista).
 */
@Composable
private fun GuideListContent(
    sections: List<Pair<String, List<LiveRow>>>,
    now: Long,
    onPlay: (String) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isTv = com.aotv.app.ui.common.LocalIsTv.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (!isTv) return@onPreviewKeyEvent false
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (event.key != Key.DirectionDown && event.key != Key.DirectionUp) return@onPreviewKeyEvent false
            if (event.nativeKeyEvent.repeatCount == 0) return@onPreviewKeyEvent false
            val current = listState.firstVisibleItemIndex
            val target = if (event.key == Key.DirectionDown) (current + 1).coerceAtMost(sections.size - 1) else (current - 1).coerceAtLeast(0)
            if (target == current) return@onPreviewKeyEvent false
            scope.launch { listState.scrollToItem(target) }
            true
        },
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sections, key = { section -> section.first }) { section ->
            val name = section.first
            val rows = section.second
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$name · ${rows.size} canais", color = TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                rows.forEach { row ->
                    GuideListRow(row = row, now = now, onClick = { onPlay(row.channel.id) })
                }
            }
        }
    }
}

@Composable
private fun GuideListRow(row: LiveRow, now: Long, onClick: () -> Unit) {
    val channel = row.channel
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            else Text(channel.name.take(2).uppercase(), color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(channel.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val current = row.now
            if (current != null) {
                Text(current.title, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.aotv.app.ui.common.ProgressBar(current.progress(now), Modifier.fillMaxWidth(0.9f), height = 3.dp)
            } else {
                Text("Sem guia para este canal", color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            row.next?.let { next ->
                Text("A seguir ${hhmm(next.start)} ${next.title}", color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
