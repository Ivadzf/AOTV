package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Assistente por comandos (Lote 25, v1.35.0, item 1 — ideia do MKCinema/Miko,
 * reimplementada: a personagem/raposa é identidade deles e não entra aqui).
 *
 * Parser LOCAL, em Kotlin puro, sem rede e sem conta: entende os comandos do
 * dia a dia em PT (com aliases em EN). "Play X" busca X e toca o primeiro
 * resultado se inequívoco, senão abre a busca com a query. Os comandos de
 * reprodução (pausa, voltar, legendas, próximo episódio) só valem com o
 * player aberto — fora dele, o diálogo mostra um aviso curto.
 */

// ── Destinos de navegação do assistente ────────────────────────────────────

/** Telas que o assistente sabe abrir por comando ("ir para a busca"). */
enum class AssistantDestination {
    HOME, SEARCH, DISCOVER, LIBRARY, DOWNLOADS, LIVE, SPORTS, SETTINGS, PROFILES, ADDONS
}

// ── Intents ─────────────────────────────────────────────────────────────────

/** O que o parser entendeu de um comando. Tudo aqui é dado, não UI. */
sealed class AssistantIntent {
    /** "Play Dune" — buscar o título e tocar o primeiro resultado inequívoco. */
    data class PlayTitle(val title: String) : AssistantIntent()

    /** "pausa" — só com o player aberto. */
    object Pause : AssistantIntent()

    /** "continuar" / "play" sozinho — só com o player aberto. */
    object Resume : AssistantIntent()

    /** "volta 30 s" — voltar N segundos no player aberto. */
    data class SeekBack(val seconds: Int) : AssistantIntent()

    /** "avança 30 s" — avançar N segundos no player aberto. */
    data class SeekForward(val seconds: Int) : AssistantIntent()

    /** "vai p/ 1:20:00" — pular para a posição no player aberto. */
    data class SeekTo(val positionMs: Long) : AssistantIntent()

    /** "legendas off" / "legendas on" — só com o player aberto. */
    data class Subtitles(val enabled: Boolean) : AssistantIntent()

    /** "próximo episódio" — só com o player aberto (série). */
    object NextEpisode : AssistantIntent()

    /** "ir para a busca" — abrir uma tela. */
    data class Navigate(val destination: AssistantDestination) : AssistantIntent()

    /** Tudo o mais: tratado como busca pelo texto digitado. */
    data class Search(val query: String) : AssistantIntent()
}

// ── Parser (Kotlin puro, sem Android — dá p/ testar sem o app) ─────────────

/** Minúsculas, sem acento, espaços colapsados. "Próximo Episódio" vira "proximo episodio". */
private fun normalizeAssistantText(raw: String): String {
    val noAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noAccents.lowercase().trim().replace(Regex("\\s+"), " ")
}

private val SEEK_TO_HMS = Regex("^(vai|ir)\\s+(p/\\s*|para\\s+|pra\\s+|para\\s+o\\s+)?(\\d+):(\\d{1,2})(?::(\\d{1,2}))?$")
private val SEEK_TO_BARE = Regex("^(\\d+):(\\d{2})(?::(\\d{2}))?$")
private val SEEK_TO_MIN = Regex("^(vai|ir)\\s+(p/\\s*|para\\s+|pra\\s+)?(\\d+)\\s*(min|minutos?)$")
private val SEEK_BACK = Regex("^(volta|voltar|retrocede|retroceder|back|rewind)\\s+(\\d+)\\s*(s|seg|segs|segundos?|min|minutos?)?$")
private val SEEK_FWD = Regex("^(avanca|avancar|adianta|adiantar|pula|pular|forward|skip|avance)\\s+(\\d+)\\s*(s|seg|segs|segundos?|min|minutos?)?$")
private val PLAY_TITLE = Regex("^(play|tocar|assistir|ver|reproduzir|colocar)\\s+(.+)$", RegexOption.IGNORE_CASE)
private val NAV_PREFIX = Regex("^(ir\\s+para|abrir|vai\\s+para|mostrar|abre|ir\\s+pra)\\s+(.+)$")

/** Palavras que desligam a legenda. Checadas ANTES das de ligar: "desligar" contém "ligar". */
private val SUB_OFF_WORDS = listOf("off", "deslig", "sem legenda", "sem legendas", "tira", "tirar", "esconde", "oculta", "remove")

private fun isSubtitlesOff(text: String): Boolean = SUB_OFF_WORDS.any { text.contains(it) }

private fun seekSeconds(value: Int, unit: String?): Int =
    if (unit != null && unit.startsWith("min")) value * 60 else value

private fun destinationFrom(text: String): AssistantDestination? = when {
    text.contains("inicio") || text == "home" -> AssistantDestination.HOME
    text.contains("busca") || text.contains("pesquisa") -> AssistantDestination.SEARCH
    text.contains("descobrir") -> AssistantDestination.DISCOVER
    text.contains("minha lista") || text.contains("biblioteca") -> AssistantDestination.LIBRARY
    text.contains("download") -> AssistantDestination.DOWNLOADS
    text.contains("ao vivo") || text.contains("live") -> AssistantDestination.LIVE
    text.contains("esporte") -> AssistantDestination.SPORTS
    text.contains("config") -> AssistantDestination.SETTINGS
    text.contains("perfil") || text.contains("perfis") -> AssistantDestination.PROFILES
    text.contains("addon") -> AssistantDestination.ADDONS
    else -> null
}

/**
 * Entende um comando. Ordem importa: "pular episódio" não é "pular N s",
 * e "legendas off" não é busca por "legendas". Texto que não casa em nada
 * vira [AssistantIntent.Search] com o texto original (com acentos e caixa).
 */
fun parseAssistantCommand(raw: String): AssistantIntent {
    val trimmed = raw.trim()
    val text = normalizeAssistantText(trimmed)
    if (text.isBlank()) return AssistantIntent.Search(trimmed)
    // Próximo episódio primeiro: "pular episódio" não é "pular N s".
    if (text.contains("proximo episodio") || text.contains("next episode")
        || text.contains("avancar episodio") || text.contains("pular episodio")
    ) return AssistantIntent.NextEpisode
    // Legendas: qualquer menção a "legenda" é comando, nunca busca.
    if (text.contains("legenda")) return AssistantIntent.Subtitles(!isSubtitlesOff(text))
    // "vai p/ 1:20:00" / "ir para 10:30" / "1:20:00" / "vai para 45 min".
    SEEK_TO_HMS.matchEntire(text)?.let { m ->
        val first = m.groupValues[3].toInt()
        val second = m.groupValues[4].toInt()
        val third = m.groupValues[5].toIntOrNull()
        val ms = if (third != null) ((first * 60L + second) * 60L + third) * 1_000L
            else (first * 60L + second) * 1_000L
        return AssistantIntent.SeekTo(ms)
    }
    SEEK_TO_BARE.matchEntire(text)?.let { m ->
        val first = m.groupValues[1].toInt()
        val second = m.groupValues[2].toInt()
        val third = m.groupValues[3].toIntOrNull()
        val ms = if (third != null) ((first * 60L + second) * 60L + third) * 1_000L
            else (first * 60L + second) * 1_000L
        return AssistantIntent.SeekTo(ms)
    }
    SEEK_TO_MIN.matchEntire(text)?.let { m ->
        return AssistantIntent.SeekTo(m.groupValues[3].toInt() * 60_000L)
    }
    SEEK_BACK.matchEntire(text)?.let { m ->
        return AssistantIntent.SeekBack(seekSeconds(m.groupValues[2].toInt(), m.groupValues[3].ifBlank { null }))
    }
    SEEK_FWD.matchEntire(text)?.let { m ->
        return AssistantIntent.SeekForward(seekSeconds(m.groupValues[2].toInt(), m.groupValues[3].ifBlank { null }))
    }
    // Pausa / continuar: só a palavra exata — "para" solto em outro comando não pausa nada.
    when (text) {
        "pausa", "pausar", "pause" -> return AssistantIntent.Pause
        "play", "tocar", "continuar", "retomar", "resume", "volta a tocar" -> return AssistantIntent.Resume
    }
    // "Play X": o título sai do texto ORIGINAL (mantém acentos e caixa p/ a busca).
    PLAY_TITLE.matchEntire(trimmed)?.let { m ->
        val title = m.groupValues[2].trim()
        if (title.isNotBlank()) return AssistantIntent.PlayTitle(title)
    }
    // "ir para a busca" / "abrir downloads" / "busca" sozinho.
    NAV_PREFIX.matchEntire(text)?.let { m ->
        destinationFrom(m.groupValues[2])?.let { dest -> return AssistantIntent.Navigate(dest) }
    }
    destinationFrom(text)?.let { dest -> return AssistantIntent.Navigate(dest) }
    return AssistantIntent.Search(trimmed)
}

/** "1:20:00" / "10:30" p/ mostrar no aviso ("Indo para 1:20:00."). */
fun formatAssistantTime(positionMs: Long): String {
    val totalSec = (positionMs / 1_000L).coerceAtLeast(0L)
    val h = totalSec / 3_600L
    val m = (totalSec % 3_600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
}

// ── Ponte p/ o player ───────────────────────────────────────────────────────

/** Ações que o player aberto oferece ao assistente. null = sem player aberto. */
data class AssistantPlayerActions(
    val pause: () -> Unit,
    val resume: () -> Unit,
    val seekBy: (Long) -> Unit,
    val seekTo: (Long) -> Unit,
    val setSubtitles: (Boolean) -> Unit,
    /** Próximo episódio (ou próxima temporada); false quando não há. */
    val nextEpisode: () -> Boolean,
)

/** Resultado de um comando de reprodução. */
enum class AssistantPlayerResult { Applied, NoPlayer, NotAvailable }

/**
 * Ponte em memória entre o player e o assistente. O MoviePlayerScreen
 * registra as ações ao compor e limpa ao sair (onDispose) — fora do player
 * o assistente só mostra o aviso "sem player aberto".
 */
object AssistantPlayerBridge {
    @Volatile
    var actions: AssistantPlayerActions? = null

    /** Executa um comando de reprodução. Comandos de busca/navegação não passam aqui. */
    fun execute(intent: AssistantIntent): AssistantPlayerResult {
        val a = actions ?: return AssistantPlayerResult.NoPlayer
        return when (intent) {
            is AssistantIntent.Pause -> { a.pause(); AssistantPlayerResult.Applied }
            is AssistantIntent.Resume -> { a.resume(); AssistantPlayerResult.Applied }
            is AssistantIntent.SeekBack -> { a.seekBy(-intent.seconds * 1_000L); AssistantPlayerResult.Applied }
            is AssistantIntent.SeekForward -> { a.seekBy(intent.seconds * 1_000L); AssistantPlayerResult.Applied }
            is AssistantIntent.SeekTo -> { a.seekTo(intent.positionMs); AssistantPlayerResult.Applied }
            is AssistantIntent.Subtitles -> { a.setSubtitles(intent.enabled); AssistantPlayerResult.Applied }
            is AssistantIntent.NextEpisode -> if (a.nextEpisode()) AssistantPlayerResult.Applied else AssistantPlayerResult.NotAvailable
            else -> AssistantPlayerResult.NotAvailable
        }
    }
}

// ── Pedidos entre telas ─────────────────────────────────────────────────────

/** Abrir/fechar o diálogo do assistente (o diálogo vive na navegação). */
object AssistantRequest {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    fun open() { _open.value = true }
    fun close() { _open.value = false }
}

/** Uma busca pedida pelo assistente, consumida pela tela de busca. */
data class AssistantQuery(val text: String, val autoPlayFirst: Boolean)

/**
 * Fila de 1 pedido de busca do assistente. `autoPlayFirst` = "Play X": a
 * busca toca o primeiro resultado se for o único; senão deixa a busca
 * aberta com a query p/ o usuário escolher.
 */
object AssistantQueryRequest {
    private val _pending = MutableStateFlow<AssistantQuery?>(null)
    val pending: StateFlow<AssistantQuery?> = _pending.asStateFlow()
    fun request(text: String, autoPlayFirst: Boolean) {
        _pending.value = AssistantQuery(text.trim(), autoPlayFirst)
    }
    fun consume() { _pending.value = null }
}

// ── Diálogo ─────────────────────────────────────────────────────────────────

/** Exemplos que aparecem como chips no diálogo (um por comportamento). */
val ASSISTANT_EXAMPLES = listOf(
    "Play Dune",
    "pausa",
    "volta 30 s",
    "vai p/ 1:20:00",
    "legendas off",
    "próximo episódio",
)

/** Mensagem curta de confirmação de um comando de reprodução aplicado. */
private fun playerAppliedMessage(intent: AssistantIntent): String = when (intent) {
    is AssistantIntent.Pause -> "Pausado."
    is AssistantIntent.Resume -> "Continuando."
    is AssistantIntent.SeekBack -> "Voltou ${intent.seconds} s."
    is AssistantIntent.SeekForward -> "Avançou ${intent.seconds} s."
    is AssistantIntent.SeekTo -> "Indo para ${formatAssistantTime(intent.positionMs)}."
    is AssistantIntent.Subtitles -> if (intent.enabled) "Legendas ligadas." else "Legendas desligadas."
    is AssistantIntent.NextEpisode -> "Próximo episódio."
    else -> "Pronto."
}

/**
 * Diálogo do assistente: campo de comando + exemplos + aviso curto.
 * Comandos de reprodução executam na hora e o diálogo fica aberto (dá p/
 * mandar outro); Play/navegação/busca fecham o diálogo e o host navega.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantDialog(
    onDismiss: () -> Unit,
    onPlayTitle: (String) -> Unit,
    onNavigate: (AssistantDestination) -> Unit,
    onSearchQuery: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }

    fun submit(text: String) {
        val intent = parseAssistantCommand(text)
        when (intent) {
            is AssistantIntent.PlayTitle -> onPlayTitle(intent.title)
            is AssistantIntent.Navigate -> onNavigate(intent.destination)
            is AssistantIntent.Search -> onSearchQuery(intent.query)
            else -> {
                feedback = when (AssistantPlayerBridge.execute(intent)) {
                    AssistantPlayerResult.Applied -> playerAppliedMessage(intent)
                    AssistantPlayerResult.NoPlayer -> "Sem player aberto — abra um vídeo primeiro."
                    AssistantPlayerResult.NotAvailable -> "Não há próximo episódio neste título."
                }
                input = ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundCard)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Assistente",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ManropeFontFamily,
            )
            Text(
                "Digite um comando — tudo é entendido no aparelho, sem conta e sem internet.",
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = ManropeFontFamily,
                lineHeight = 19.sp,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("Ex.: Play Dune", color = TextTertiary, fontSize = 15.sp, fontFamily = ManropeFontFamily) },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null, tint = TextTertiary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (input.isNotBlank()) submit(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (feedback.isNotBlank()) {
                Text(
                    feedback,
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ManropeFontFamily,
                )
            }
            Text(
                "Experimente",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ManropeFontFamily,
                letterSpacing = 1.2.sp,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ASSISTANT_EXAMPLES.forEach { example ->
                    FilterChipPill(example, false, onClick = { submit(example) })
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) }
                Button(
                    onClick = { if (input.isNotBlank()) submit(input) },
                    enabled = input.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                ) {
                    Text("Enviar", fontSize = 14.sp)
                }
            }
        }
    }
}
