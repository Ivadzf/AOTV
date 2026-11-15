package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.ui.theme.*

/** Rota do gerenciador de canais (Lote 12, v1.22.0). */
const val LIVE_CHANNELS_MANAGE_ROUTE = "live_channels_manage"

/**
 * Gerenciar canais (Lote 12, v1.22.0): ocultar, reordenar, renomear,
 * renumerar, grupo próprio, Assign EPG, time shift e logo — tudo por
 * perfil, na hora, sem sair da tela.
 *
 * Canal oculto some de TODAS as vistas (hub, grade, lista, busca, guia e
 * player). O grupo próprio cria grupos novos na aba Ao vivo (o canal passa
 * a aparecer no grupo digitado). O Assign EPG associa manualmente uma fonte
 * de guia ao canal; o time shift anda o guia dele em horas (-24..+24).
 */
@Composable
fun LiveChannelManagerScreen(
    onBack: () -> Unit,
    viewModel: LiveChannelManagerViewModel = hiltViewModel(),
) {
    val st by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { com.aotv.app.data.local.PlatformPrefs(context) }
    // Os mapas (nome, grupo, número, EPG, logo) não têm fluxo: o `tick`
    // força a releitura depois de cada edição (ver o comentário no fim).
    var tick by remember { mutableIntStateOf(0) }
    val hidden by prefs.liveHiddenChannelsFlow.collectAsStateWithLifecycle()
    val order by prefs.liveChannelOrderFlow.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var epgPickerFor by remember { mutableStateOf<String?>(null) }

    // Lista na ordem efetiva (com os ocultos, marcados); a busca filtra.
    val all = remember(st.channels, order, tick) { viewModel.orderedChannels() }
    val needle = query.trim().lowercase()
    val list = remember(all, needle) {
        if (needle.isEmpty()) all
        else all.filter { c ->
            val d = viewModel.displayChannel(c)
            d.name.lowercase().contains(needle) || d.group.lowercase().contains(needle)
        }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            Text("Gerenciar canais", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${all.size} canais", color = TextTertiary, fontSize = 13.sp)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Buscar canal ou grupo") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
            ),
        )
        Text(
            "Canal oculto some do hub, da grade, da lista, da busca, do guia e do player. O grupo digitado cria um grupo próprio na aba Ao vivo.",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 2.dp),
        )
        if (list.isEmpty()) {
            Text(
                if (st.channels.isEmpty()) "Nenhum canal aqui. Adicione uma fonte em Configurações → TV ao vivo." else "Nenhum canal com esse nome.",
                color = TextTertiary, fontSize = 14.sp,
                modifier = Modifier.padding(com.aotv.app.ui.common.pagePadding()),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = com.aotv.app.ui.common.pagePadding(), end = com.aotv.app.ui.common.pagePadding(),
                top = 6.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { channel ->
                val display = viewModel.displayChannel(channel)
                val isHidden = channel.id in hidden
                val isOpen = expanded == channel.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BackgroundCard2)
                        .clickable { expanded = if (isOpen) null else channel.id }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 48.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                            if (display.logo.isNotBlank()) AsyncImage(display.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                            else Text(display.name.take(2).uppercase(), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (display.number > 0) "${display.number} · ${display.name}" else display.name,
                                color = if (isHidden) TextTertiary else TextPrimary,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val groupText = LiveTvViewModel.groupLabel(display).ifBlank { "Sem grupo" }
                            Text(groupText, color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isHidden) Text("Oculto", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            if (isOpen) "Fechar ${display.name}" else "Editar ${display.name}",
                            tint = TextTertiary, modifier = Modifier.size(22.dp),
                        )
                    }
                    if (isOpen) {
                        ChannelEditor(
                            channel = channel,
                            display = display,
                            isHidden = isHidden,
                            epgAssign = viewModel.epgAssign(channel.id),
                            epgShift = viewModel.epgShift(channel.id),
                            onToggleHidden = { viewModel.toggleHidden(channel.id) },
                            onMoveUp = { viewModel.move(channel.id, -1) },
                            onMoveDown = { viewModel.move(channel.id, 1) },
                            onName = { viewModel.setName(channel.id, it); tick++ },
                            onGroup = { viewModel.setGroup(channel.id, it); tick++ },
                            onNumber = { viewModel.setNumber(channel.id, it); tick++ },
                            onLogo = { viewModel.setLogo(channel.id, it); tick++ },
                            onPickEpg = { epgPickerFor = channel.id },
                            onShift = { viewModel.setEpgShift(channel.id, it); tick++ },
                        )
                    }
                }
            }
        }
    }

    // Seletor de Assign EPG: só canais que TÊM guia; sem associação, o
    // canal volta a usar o tvg-id da lista.
    epgPickerFor?.let { channelId ->
        val channel = st.channels.firstOrNull { it.id == channelId }
        val choices = remember(st.epg, tick) { viewModel.epgChoices() }
        val current = viewModel.epgAssign(channelId)
        AlertDialog(
            onDismissRequest = { epgPickerFor = null },
            containerColor = BackgroundCard,
            title = { Text("Guia de ${channel?.let { viewModel.displayChannel(it).name } ?: "canal"}", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Associar a fonte de guia de outro canal. Sem associação, vale o guia da lista.", color = TextSecondary, fontSize = 14.sp)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.setEpgAssign(channelId, ""); tick++; epgPickerFor = null }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "Sem associação (guia da lista)", color = if (current == null) Accent else TextPrimary,
                            fontSize = 15.sp, fontWeight = if (current == null) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    choices.forEach { (_, name, epgId) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.setEpgAssign(channelId, epgId); tick++; epgPickerFor = null }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                name, color = if (current == epgId) Accent else TextPrimary,
                                fontSize = 15.sp, fontWeight = if (current == epgId) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (current == epgId) Text("Atual", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (choices.isEmpty()) Text("Nenhum canal com guia no momento.", color = TextTertiary, fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { epgPickerFor = null }) { Text("Fechar", color = TextSecondary) } },
        )
    }
}

/** Editor de um canal (nome, número, grupo, logo, ocultar, ordem, EPG e shift). */
@Composable
private fun ChannelEditor(
    channel: LiveChannel,
    display: LiveChannel,
    isHidden: Boolean,
    epgAssign: String?,
    epgShift: Int,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onName: (String) -> Unit,
    onGroup: (String) -> Unit,
    onNumber: (Int?) -> Unit,
    onLogo: (String) -> Unit,
    onPickEpg: () -> Unit,
    onShift: (Int) -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
    )
    var name by remember(channel.id) { mutableStateOf(display.name) }
    var group by remember(channel.id) { mutableStateOf(display.group) }
    var number by remember(channel.id) { mutableStateOf(if (display.number > 0) display.number.toString() else "") }
    var logo by remember(channel.id) { mutableStateOf(display.logo) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it; onName(it) }, singleLine = true,
            label = { Text("Nome do canal") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = number,
                onValueChange = { v ->
                    number = v.filter { it.isDigit() }.take(5)
                    onNumber(number.toIntOrNull())
                },
                singleLine = true, label = { Text("Número") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f), colors = fieldColors,
            )
            OutlinedTextField(
                value = group, onValueChange = { group = it; onGroup(it) }, singleLine = true,
                label = { Text("Grupo") }, modifier = Modifier.weight(2f), colors = fieldColors,
            )
        }
        OutlinedTextField(
            value = logo, onValueChange = { logo = it; onLogo(it) }, singleLine = true,
            label = { Text("Logo (endereço da imagem)") },
            placeholder = { Text("https://…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.AotvButton("Subir", onMoveUp, tone = com.aotv.app.ui.common.ButtonTone.SECONDARY, compact = true)
            com.aotv.app.ui.common.AotvButton("Descer", onMoveDown, tone = com.aotv.app.ui.common.ButtonTone.SECONDARY, compact = true)
            Spacer(Modifier.weight(1f))
            Text(if (isHidden) "Oculto" else "Visível", color = TextSecondary, fontSize = 14.sp)
            Switch(
                checked = !isHidden, onCheckedChange = { onToggleHidden() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BackgroundCard2, uncheckedBorderColor = Border),
            )
        }
        // Assign EPG + time shift: o deslocamento só vale p/ este canal.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.aotv.app.ui.common.AotvButton(
                if (epgAssign.isNullOrBlank()) "Associar guia…" else "Guia associado",
                onPickEpg, tone = com.aotv.app.ui.common.ButtonTone.SECONDARY, compact = true,
            )
            Spacer(Modifier.weight(1f))
            Text("Guia", color = TextSecondary, fontSize = 14.sp)
            com.aotv.app.ui.common.AotvButton("− 1 h", { onShift(epgShift - 1) }, tone = com.aotv.app.ui.common.ButtonTone.SECONDARY, compact = true)
            Text(
                when {
                    epgShift > 0 -> "+$epgShift h"
                    epgShift < 0 -> "$epgShift h"
                    else -> "0 h"
                },
                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            com.aotv.app.ui.common.AotvButton("+ 1 h", { onShift(epgShift + 1) }, tone = com.aotv.app.ui.common.ButtonTone.SECONDARY, compact = true)
        }
        Text(
            "O deslocamento anda o guia deste canal em horas (−24 h a +24 h, passo de 1 h). Em 0 h, o guia volta ao normal.",
            color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}
