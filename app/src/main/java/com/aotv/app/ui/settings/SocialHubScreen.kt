package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.party.PARTY_SYNC_UNAVAILABLE_REASON
import com.aotv.app.data.party.PartyRole
import com.aotv.app.data.party.PartySyncState
import com.aotv.app.data.social.SOCIAL_HUB_NO_SERVER_NOTE
import com.aotv.app.data.social.formatFilmNightWhen
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.SettingsRow
import com.aotv.app.ui.common.SummaryCard
import com.aotv.app.ui.common.SummaryRow
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tela "Social Hub" (Lote 19, v1.29.0, item 2) — amigos, film night e
 * couch match, tudo local e sem servidor.
 *
 * O que "amigos" significa aqui está escrito no topo da tela (ver
 * [SOCIAL_HUB_NO_SERVER_NOTE]): lista local de nomes, "quer assistir"
 * anotado à mão, match entre 2+ amigos da lista e film night agendada
 * no aparelho. "Ver o que amigos assistem" só existe dentro de uma Watch
 * Party ativa (o cartão da sessão); fora de sessão não há atividade
 * visível e nada é inventado. "Assistir junto" cria a sessão pelo bloco
 * Watch Party (item 1 do Lote 19 — ver
 * [SocialHubViewModel.watchTogether]) e abre a tela dele: este hub não
 * reimplementa sessão, código ou convite.
 *
 * Ponto de entrada p/ a navegação (o coordenador faz a fiação da rota;
 * esta tela NÃO se conecta à navegação sozinha):
 * `SocialHubScreen(onBack = { ... }, onOpenWatchParty = { ... })`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SocialHubScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    onOpenWatchParty: () -> Unit = {},
    viewModel: SocialHubViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val session = st.session
    val nowMs = System.currentTimeMillis()

    // "Assistir junto" criou a sessão: abre a Watch Party 1x e consome o pedido.
    val navCode = st.partyNavigationCode
    LaunchedEffect(navCode) {
        if (navCode != null) {
            onOpenWatchParty()
            viewModel.consumePartyNavigation()
        }
    }

    // ── Estado local dos formulários ──────────────────────────────────────
    var newFriendName by remember { mutableStateOf("") }
    var wantFriendId by remember { mutableStateOf<String?>(null) }
    var wantTitle by remember { mutableStateOf("") }
    var nightTitle by remember { mutableStateOf("") }
    var nightDateMs by remember { mutableStateOf<Long?>(null) }
    var nightHour by remember { mutableStateOf<Int?>(null) }
    var nightMinute by remember { mutableStateOf<Int?>(null) }
    var nightFriendIds by remember { mutableStateOf(setOf<String>()) }
    var nightPrefilled by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // O selecionador de amigo do "quer assistir" segue a lista: sem amigos
    // não há seleção; com amigo novo, volta p/ o primeiro da lista.
    val wantFriend = st.friends.firstOrNull { it.id == wantFriendId } ?: st.friends.firstOrNull()
    val nightStartsAtMs = nightStartMs(nightDateMs, nightHour, nightMinute)
    val nightFormReady = nightTitle.trim().isNotBlank() && nightStartsAtMs != null && nightFriendIds.isNotEmpty()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
    )
    val dateState = rememberDatePickerState(initialSelectedDateMillis = nowMs)
    val timeState = rememberTimePickerState(initialHour = 20, initialMinute = 0, is24Hour = true)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { nightDateMs = it }
                        showDatePicker = false
                    },
                ) { Text("OK", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = TextSecondary) }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        nightHour = timeState.hour
                        nightMinute = timeState.minute
                        showTimePicker = false
                    },
                ) { Text("OK", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar", color = TextSecondary) }
            },
            text = { TimePicker(state = timeState) },
        )
    }

    VpnPartyScaffold(
        title = "Social Hub",
        sub = "Amigos, film night e couch match — tudo local",
        onBack = onBack,
        highlightAnchor = highlightAnchor,
    ) {
        val message = st.message
        if (message != null) {
            Row(
                Modifier.fillMaxWidth().background(BackgroundCard2, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 14.sp)
                IconButton(onClick = viewModel::clearMessage) { Icon(Icons.Default.Close, "Fechar", tint = TextSecondary) }
            }
        }
        val error = st.error
        if (error != null) {
            Row(
                Modifier.fillMaxWidth().background(BackgroundCard2, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = DangerText, modifier = Modifier.size(20.dp))
                Text(error, color = TextPrimary, modifier = Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp)
                IconButton(onClick = viewModel::clearMessage) { Icon(Icons.Default.Close, "Fechar", tint = TextSecondary) }
            }
        }

        VpnPartySection("Sem servidor — o que dá p/ fazer", anchor = SettingsAnchors.SOCIAL_ABOUT)
        SummaryCard {
            Text(
                SOCIAL_HUB_NO_SERVER_NOTE,
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        VpnPartySection("Sessão ativa", anchor = SettingsAnchors.SOCIAL_SESSION)
        if (session == null) {
            Text(
                "Você não está numa Watch Party agora. Crie uma sessão na Watch Party ou toque em \"Assistir junto\" num couch match ou numa film night.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            AotvButton(
                "Abrir Watch Party",
                onClick = onOpenWatchParty,
                icon = Icons.Default.Group,
                tone = ButtonTone.SECONDARY,
            )
        } else {
            SummaryCard {
                Text("Na sessão ${session.code}", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow("Papel", if (session.role == PartyRole.HOST) "Anfitrião" else "Convidado", TextPrimary)
                // Sincronização AO VIVO do PartyRuntime (Lote 19, fix do
                // coordenador): nunca texto fixo — o estado real da sala.
                val syncLabel = when (st.partySyncState) {
                    PartySyncState.CONNECTED -> "Sincronizada"
                    PartySyncState.LOBBY -> "Na sala, esperando gente"
                    PartySyncState.CONNECTING -> "Conectando…"
                    PartySyncState.FAILED -> "Falhou"
                    PartySyncState.IDLE -> "Sem conexão"
                }
                val syncColor = when (st.partySyncState) {
                    PartySyncState.CONNECTED -> TextPrimary
                    PartySyncState.FAILED -> DangerText
                    else -> TextSecondary
                }
                SummaryRow("Sincronização", syncLabel, syncColor)
                Spacer(Modifier.height(12.dp))
                Text(
                    when (st.partySyncState) {
                        PartySyncState.CONNECTED -> "Play, pausa e seek sincronizam com os participantes conectados."
                        PartySyncState.FAILED -> PARTY_SYNC_UNAVAILABLE_REASON
                        else -> "A sincronização ativa quando alguém entrar na sessão."
                    },
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
            AotvButton(
                "Abrir Watch Party",
                onClick = onOpenWatchParty,
                icon = Icons.Default.Group,
                tone = ButtonTone.PRIMARY,
            )
        }

        VpnPartySection("Amigos", anchor = SettingsAnchors.SOCIAL_FRIENDS)
        Text(
            "Lista local, só nomes — nada é lido do aparelho de ninguém e ninguém é avisado que entrou aqui. Máx. 30 amigos.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        OutlinedTextField(
            value = newFriendName,
            onValueChange = { newFriendName = it.take(40) },
            label = { Text("Nome do amigo") },
            placeholder = { Text("Ex.: Juliana") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        AotvButton(
            "Adicionar amigo",
            onClick = { viewModel.addFriend(newFriendName); newFriendName = "" },
            icon = Icons.Default.Add,
            tone = ButtonTone.PRIMARY,
            enabled = newFriendName.trim().isNotBlank(),
        )
        if (st.friends.isEmpty()) {
            Text("Nenhum amigo ainda. Adicione o primeiro acima.", color = TextSecondary, fontSize = 13.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                st.friends.forEach { friend ->
                    SettingsRow(
                        icon = Icons.Default.Person,
                        title = friend.name,
                        sub = "${st.wants.count { it.friendId == friend.id }} na lista de \"quer assistir\"",
                        trailing = {
                            IconButton(onClick = { viewModel.removeFriend(friend.id) }) {
                                Icon(Icons.Default.Delete, "Remover ${friend.name}", tint = TextSecondary)
                            }
                        },
                    )
                }
            }
        }

        VpnPartySection("Quer assistir", anchor = SettingsAnchors.SOCIAL_WANTS)
        Text(
            "Anote à mão o que cada amigo quer ver. É essa lista que alimenta o couch match — o app não lê a lista de ninguém.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (st.friends.isEmpty()) {
            Text("Adicione um amigo primeiro.", color = TextSecondary, fontSize = 13.sp)
        } else {
            Text("Amigo", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                st.friends.forEach { friend ->
                    AotvButton(
                        friend.name,
                        onClick = { wantFriendId = friend.id },
                        tone = if (wantFriend?.id == friend.id) ButtonTone.PRIMARY else ButtonTone.SECONDARY,
                        compact = true,
                    )
                }
            }
            OutlinedTextField(
                value = wantTitle,
                onValueChange = { wantTitle = it.take(120) },
                label = { Text("Título que ${wantFriend?.name ?: "o amigo"} quer assistir") },
                placeholder = { Text("Ex.: Duna: Parte 2") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            AotvButton(
                "Anotar",
                onClick = {
                    val target = wantFriend
                    if (target != null) {
                        viewModel.addWant(target.id, wantTitle)
                        wantTitle = ""
                    }
                },
                icon = Icons.Default.Add,
                tone = ButtonTone.PRIMARY,
                enabled = wantFriend != null && wantTitle.trim().isNotBlank(),
            )
            val groupedWants = st.friends.map { friend ->
                friend to st.wants.filter { it.friendId == friend.id }
            }.filter { (_, items) -> items.isNotEmpty() }
            if (groupedWants.isEmpty()) {
                Text("Nenhum título anotado ainda.", color = TextSecondary, fontSize = 13.sp)
            } else {
                groupedWants.forEach { (friend, items) ->
                    Text(friend.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEach { want ->
                            SettingsRow(
                                icon = Icons.Default.Movie,
                                title = want.title,
                                trailing = {
                                    IconButton(onClick = { viewModel.removeWant(want.id) }) {
                                        Icon(Icons.Default.Delete, "Apagar ${want.title}", tint = TextSecondary)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        VpnPartySection("Couch match", anchor = SettingsAnchors.SOCIAL_MATCH)
        Text(
            "Títulos que 2 ou mais amigos querem ver. O match é só da lista anotada aqui — não é gosto medido nem lista lida de outro aparelho.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (st.matches.isEmpty()) {
            Text(
                if (st.friends.size < 2) "Faltam amigos p/ um match: adicione pelo menos 2 e anote o que cada um quer assistir."
                else "Nenhum match ainda: nenhum título aparece na lista de 2 amigos ao mesmo tempo.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                st.matches.forEach { match ->
                    SummaryCard {
                        Text(match.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${match.friendNames.joinToString(" e ")} querem ver",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AotvButton(
                                "Assistir junto",
                                onClick = { viewModel.watchTogether(match.title) },
                                icon = Icons.Default.PlayArrow,
                                tone = ButtonTone.PRIMARY,
                                compact = true,
                            )
                            AotvButton(
                                "Criar film night",
                                onClick = {
                                    nightTitle = match.title
                                    nightFriendIds = match.friendIds.toSet()
                                    nightPrefilled = true
                                },
                                icon = Icons.Default.Event,
                                tone = ButtonTone.SECONDARY,
                                compact = true,
                            )
                        }
                    }
                }
            }
        }

        VpnPartySection("Film night", anchor = SettingsAnchors.SOCIAL_FILM_NIGHT)
        Text(
            "Agende a noite do filme no aparelho. Na hora, 1 toque cria a sessão da Watch Party — o convite vai por link/código, como na Watch Party.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (nightPrefilled) {
            Text(
                "Título e amigos já preenchidos a partir do couch match — falta só a data e a hora.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        OutlinedTextField(
            value = nightTitle,
            onValueChange = { nightTitle = it.take(120); nightPrefilled = false },
            label = { Text("Filme ou série da noite") },
            placeholder = { Text("Ex.: Duna: Parte 2") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AotvButton(
                nightDateMs?.let { "Data: ${SimpleDateFormat("EEE, dd/MM", Locale("pt", "BR")).format(Date(it))}" } ?: "Escolher data",
                onClick = { showDatePicker = true },
                icon = Icons.Default.Event,
                tone = ButtonTone.SECONDARY,
                compact = true,
            )
            AotvButton(
                if (nightHour != null && nightMinute != null) "Hora: %02d:%02d".format(nightHour, nightMinute) else "Escolher hora",
                onClick = { showTimePicker = true },
                icon = Icons.Default.Event,
                tone = ButtonTone.SECONDARY,
                compact = true,
            )
        }
        if (st.friends.isEmpty()) {
            Text("Adicione amigos p/ convidar p/ a film night.", color = TextSecondary, fontSize = 13.sp)
        } else {
            Text("Convidar", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                st.friends.forEach { friend ->
                    AotvButton(
                        friend.name,
                        onClick = {
                            nightFriendIds = if (friend.id in nightFriendIds) nightFriendIds - friend.id else nightFriendIds + friend.id
                        },
                        tone = if (friend.id in nightFriendIds) ButtonTone.PRIMARY else ButtonTone.SECONDARY,
                        compact = true,
                    )
                }
            }
        }
        AotvButton(
            "Agendar film night",
            onClick = {
                val startsAt = nightStartsAtMs
                if (startsAt != null) {
                    viewModel.createFilmNight(nightTitle, startsAt, nightFriendIds.toList())
                    nightTitle = ""
                    nightDateMs = null
                    nightHour = null
                    nightMinute = null
                    nightFriendIds = emptySet()
                    nightPrefilled = false
                }
            },
            icon = Icons.Default.Event,
            tone = ButtonTone.PRIMARY,
            enabled = nightFormReady,
        )
        val upcoming = st.filmNights.filter { it.startsAtMs >= nowMs }.sortedBy { it.startsAtMs }
        val past = st.filmNights.filter { it.startsAtMs < nowMs }.sortedByDescending { it.startsAtMs }
        if (upcoming.isEmpty() && past.isEmpty()) {
            Text("Nenhuma film night agendada.", color = TextSecondary, fontSize = 13.sp)
        } else {
            upcoming.forEach { night ->
                val names = night.friendIds.mapNotNull { id -> st.friends.firstOrNull { it.id == id }?.name }
                SummaryCard {
                    Text(night.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatFilmNightWhen(night.startsAtMs, nowMs) +
                            if (names.isNotEmpty()) " · com ${names.joinToString(", ")}" else "",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    if (night.partyCode.isNotBlank()) {
                        Text(
                            "Sessão criada: ${night.partyCode}",
                            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AotvButton(
                            "Assistir junto",
                            onClick = { viewModel.watchTogether(night.title, night.id) },
                            icon = Icons.Default.PlayArrow,
                            tone = ButtonTone.PRIMARY,
                            compact = true,
                        )
                        AotvButton(
                            "Cancelar",
                            onClick = { viewModel.cancelFilmNight(night.id) },
                            tone = ButtonTone.DANGER,
                            compact = true,
                        )
                    }
                }
            }
            if (past.isNotEmpty()) {
                Text("Passadas", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                past.forEach { night ->
                    SettingsRow(
                        icon = Icons.Default.Event,
                        title = night.title,
                        sub = formatFilmNightWhen(night.startsAtMs, nowMs) + " · já passou",
                        trailing = {
                            IconButton(onClick = { viewModel.cancelFilmNight(night.id) }) {
                                Icon(Icons.Default.Delete, "Apagar ${night.title}", tint = TextSecondary)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Junta data (dia escolhido no DatePicker, em ms UTC à meia-noite) + hora
 * em ms locais. O DatePicker devolve a meia-noite em UTC, então o dia é
 * extraído em UTC e recomposto no fuso local — sem isso, o dia viraria o
 * anterior p/ quem está a oeste de UTC (o Brasil).
 */
private fun nightStartMs(dateMs: Long?, hour: Int?, minute: Int?): Long? {
    if (dateMs == null || hour == null || minute == null) return null
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMs }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
