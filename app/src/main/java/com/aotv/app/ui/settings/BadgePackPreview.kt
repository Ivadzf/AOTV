package com.aotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.remote.groupBadgesByCategory
import com.aotv.app.ui.common.TvTextFieldGate
import com.aotv.app.ui.movies.LogoBadge
import com.aotv.app.ui.movies.StreamPrefsViewModel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * Prévia do pack de selos com logo (Lote 19 item 9, v1.29.0) — estilo
 * "Fusion badge preview" / "Badge preview" das fotos do Davi: mostra a URL,
 * quantos selos vieram dela e a prévia agrupada por categoria.
 *
 * O usuário cola a URL do pack (JSON); sem pack carregado, a folha usa os
 * selos de texto atuais. Pack inválido ou offline → a mensagem diz o motivo
 * e o pack guardado (se houver) continua valendo — nada some em silêncio.
 * Nenhuma URL de pack é embutida: o campo começa VAZIO.
 *
 * Formato do pack (JSON), documentado aqui e na tela:
 *   {"badges": [{"id": "4k", "category": "resolution", "label": "4K",
 *                "image": "https://exemplo.com/selos/4k.png"}]}
 * O topo também pode ser um array direto. "id" é obrigatório (casa com o
 * selo extraído do texto da fonte); "label" é o texto do selo; "image" é a
 * URL do logo — sem "image", o selo continua como texto. "category" é uma
 * de: source, resolution, video, audio, channels, encoder, language,
 * streaming, size. Entrada sem id ou com categoria desconhecida é ignorada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgePackPreviewDialog(
    viewModel: StreamPrefsViewModel,
    onDismiss: () -> Unit,
) {
    val url by viewModel.badgePackUrl.collectAsStateWithLifecycle()
    val pack by viewModel.packBadges.collectAsStateWithLifecycle()
    val message by viewModel.packMessage.collectAsStateWithLifecycle()
    val loading by viewModel.packLoading.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Selos com logo", color = TextPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Cole a URL de um pack de selos (JSON). O app baixa o pack e a " +
                        "folha de fontes mostra os logos (4K, Dolby Vision, DTS-HD…) " +
                        "no lugar dos selos de texto.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                )
                TvTextFieldGate { gate ->
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("URL do pack (JSON)") },
                        placeholder = { Text("https://…/badges.json") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = gate.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            cursorColor = Accent,
                        ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.setBadgePackUrl(draft); viewModel.loadBadgePack() },
                        enabled = !loading && draft.isNotBlank(),
                    ) { Text(if (loading) "Carregando…" else "Carregar", color = Accent, fontWeight = FontWeight.Bold) }
                    if (pack.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearBadgePack() }, enabled = !loading) {
                            Text("Limpar pack", color = DangerText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (message.isNotBlank()) {
                    // Só a confirmação de carga é neutra; qualquer falha é
                    // vermelha — mesmo com pack antigo ainda valendo.
                    val loaded = message.contains("selos carregados desta URL")
                    Text(
                        message,
                        color = if (loaded) TextSecondary else DangerText,
                        fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
                if (pack.isEmpty()) {
                    Text(
                        "Sem pack carregado — a folha usa os selos de texto atuais.",
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                } else {
                    Text(
                        "${pack.size} selos desta URL",
                        color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    if (url.isNotBlank()) {
                        Text(url, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    groupBadgesByCategory(pack).forEach { (category, badges) ->
                        Text(
                            "${category.label} (${badges.size})",
                            color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            badges.forEach { badge -> LogoBadge(badge) }
                        }
                    }
                }
                Text(
                    "Formato do pack: {\"badges\": [{\"id\": \"4k\", \"category\": " +
                        "\"resolution\", \"label\": \"4K\", \"image\": \"https://…/4k.png\"}]}. " +
                        "O topo também pode ser um array direto. Categorias: source, " +
                        "resolution, video, audio, channels, encoder, language, streaming, " +
                        "size. Sem \"image\", o selo fica como texto.",
                    color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
    )
}
