package com.aotv.app.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.party.PARTY_SYNC_UNAVAILABLE_REASON
import com.aotv.app.data.party.PartyRole
import com.aotv.app.data.party.WatchParty
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

/**
 * Tela "Watch Party" (Lote 10, v1.20.0) — sessão por código, sem servidor.
 *
 * Cria a sessão (gera o código), entra com o código e mostra quem está na
 * sessão. A SINCRONIZAÇÃO em tempo real (play/pause/seek) NÃO existe:
 * precisa de um relay e o AOTV não tem um (ver `WatchParty.kt`). A tela
 * mostra isso em vez de fingir que os aparelhos estão sincronizados, e a
 * lista de participantes só mostra quem está NESTE aparelho — nenhum
 * participante é simulado.
 *
 * Deep link: `aotv://party/?code=XXXXXX` (ver AndroidManifest.xml). Quem
 * abre o convite cai no app e, ao abrir esta tela, o código já vem
 * pré-preenchido — entrar continua sendo um toque do usuário.
 *
 * Ponto de entrada p/ a navegação (o coordenador faz a fiação da rota e do
 * hub; esta tela NÃO se conecta à navegação sozinha):
 * `WatchPartyScreen(onBack = { ... })`.
 */
@Composable
fun WatchPartyScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: WatchPartyViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val session = st.session

    // Código que chegou por deep link (o intent da Activity que abriu o app).
    // LocalContext.current NÃO pode ir dentro de remember {} — lê fora e usa no LaunchedEffect.
    val activity = context as? Activity
    val deepLinkCode = activity?.intent?.data?.let { WatchParty.parseDeepLink(it) }
    LaunchedEffect(deepLinkCode) {
        if (deepLinkCode != null) viewModel.onInviteReceived(deepLinkCode)
    }

    var joinCode by remember(st.inviteCode) { mutableStateOf(st.inviteCode.orEmpty()) }

    // Só abre o compartilhamento do sistema. O texto é o convite gerado aqui.
    fun share(text: String) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, "Compartilhar convite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
    )

    VpnPartyScaffold(
        title = "Watch Party",
        sub = "Assista junto com um código — sem servidor",
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

        SummaryCard {
            Text("Sessão", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            SummaryRow("Status", if (session == null) "Sem sessão" else "Na sessão ${session.code}", TextPrimary)
            SummaryRow("Papel", if (session == null) "—" else if (session.role == PartyRole.HOST) "Anfitrião" else "Convidado", TextPrimary)
            SummaryRow("Sincronização", "Indisponível (sem relay)", DangerText)
            Spacer(Modifier.height(12.dp))
            Text(
                "A Watch Party do Tuvora sincroniza play, pause e seek entre os participantes. Aqui, sem servidor de relay, isso ainda não existe: o código e o convite funcionam, mas cada aparelho toca por conta própria por enquanto.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        if (session == null) {
            VpnPartySection("Criar sessão", anchor = SettingsAnchors.PARTY_CREATE)
            Text(
                "Você vira o anfitrião e recebe um código de 6 caracteres p/ compartilhar. Quem entrar com o código cai na mesma sessão.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            AotvButton(
                "Criar sessão",
                onClick = viewModel::createSession,
                icon = Icons.Default.Group,
                tone = ButtonTone.PRIMARY,
            )

            VpnPartySection("Entrar com código", anchor = SettingsAnchors.PARTY_JOIN)
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) },
                label = { Text("Código da sessão") },
                placeholder = { Text("Ex.: K7M2QX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            AotvButton(
                "Entrar na sessão",
                onClick = { viewModel.joinSession(joinCode) },
                icon = Icons.Default.Login,
                tone = ButtonTone.PRIMARY,
                enabled = joinCode.length == WatchParty.CODE_LENGTH,
            )
        } else {
            VpnPartySection("Código da sessão", anchor = SettingsAnchors.PARTY_CREATE)
            Text(
                "Compartilhe o código ou o link com quem vai assistir junto. Quem abrir o link cai no app com o código já preenchido.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Row(
                Modifier.fillMaxWidth().background(BackgroundCard2, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    session.code.chunked(3).joinToString(" "),
                    color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(session.code)) }) {
                    Icon(Icons.Default.ContentCopy, "Copiar código", tint = TextSecondary)
                }
                IconButton(onClick = { share(viewModel.inviteText()) }) {
                    Icon(Icons.Default.Share, "Compartilhar convite", tint = TextSecondary)
                }
            }
            Text(
                "Link do convite: ${viewModel.inviteLink()}",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            AotvButton(
                "Sair da sessão",
                onClick = viewModel::leaveSession,
                tone = ButtonTone.DANGER,
            )

            VpnPartySection("Participantes", anchor = SettingsAnchors.PARTY_MEMBERS)
            if (st.members.isEmpty()) {
                Text("Ninguém na sessão.", color = TextSecondary, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.members.forEach { member ->
                        SettingsRow(
                            icon = Icons.Default.Person,
                            title = member.name,
                            sub = if (member.isSelf) "Você (neste aparelho)" else "Participante",
                            trailing = null,
                        )
                    }
                }
            }
            Text(
                "Sem servidor não há como saber quem mais está na sessão: só você aparece aqui. Nenhum participante é inventado.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        VpnPartySection("Sincronização", anchor = SettingsAnchors.PARTY_SYNC)
        Text(
            PARTY_SYNC_UNAVAILABLE_REASON,
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
    }
}
