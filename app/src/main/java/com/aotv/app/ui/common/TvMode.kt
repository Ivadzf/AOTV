package com.aotv.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.launch

/** true no modo TV (controle remoto), inclusive quando a TV foi forçada nas Configurações. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Liga o modo TV para tudo que vem dentro. Todo clicável comum ganha anel branco e leve
 * aumento no foco (.is-focus do webOS) e os componentes Material ganham um realce forte no
 * foco, para o controle remoto sempre mostrar onde está.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProvideTvMode(isTv: Boolean, content: @Composable () -> Unit) {
    if (!isTv) {
        CompositionLocalProvider(LocalIsTv provides false, content = content)
        return
    }
    CompositionLocalProvider(
        LocalIsTv provides true,
        LocalIndication provides TvFocusIndication,
        LocalRippleConfiguration provides RippleConfiguration(Color.White, RippleAlpha(0.16f, 0.32f, 0.08f, 0.24f)),
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides TvBringIntoView,
        content = content,
    )
}

/**
 * Rolagem do foco na TV. O item focado fica longe da borda (12% da área), assim o título
 * embaixo do pôster e o aumento do foco nunca saem cortados.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object TvBringIntoView : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.12f
        // Não cabe com a folga. Regra padrão do Compose, que é estável (não fica indo e voltando).
        if (size + 2 * margin > containerSize) return plain(offset, size, containerSize)
        return plain(offset - margin, size, containerSize - 2 * margin)
    }

    private fun plain(leading: Float, size: Float, container: Float): Float {
        val trailing = leading + size
        return when {
            leading >= 0 && trailing <= container -> 0f
            leading < 0 && trailing > container -> 0f
            kotlin.math.abs(leading) < kotlin.math.abs(trailing - container) -> leading
            else -> trailing - container
        }
    }
}

/** Anel branco de 3dp e aumento de 4% no foco, 160 ms. Sem animação com "remover animações". */
private object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0x7f0c05

    private class Node(private val src: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private val focus = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var n = 0
                src.interactions.collect { i ->
                    when (i) {
                        is FocusInteraction.Focus -> n++
                        is FocusInteraction.Unfocus -> n = (n - 1).coerceAtLeast(0)
                        else -> return@collect
                    }
                    launch { focus.animateTo(if (n > 0) 1f else 0f, tween(160)) }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val f = focus.value
            if (f == 0f) { drawContent(); return }
            scale(1f + 0.04f * f) { this@draw.drawContent() }
            val w = 3.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = f), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(w),
            )
        }
    }
}

// ── TECLADO NA TELA ─────────────────────────────────────────────────────────

private val KEY_ROWS = listOf("ABCDEFGHIJ", "KLMNOPQRST", "UVWXYZÇ-'.", "1234567890")

/** Junta a letra ao texto. Maiúscula no começo e depois de espaço, minúscula no resto. */
fun typeKey(value: String, key: String): String =
    value + if (value.isEmpty() || value.endsWith(' ')) key else key.lowercase()

/**
 * Teclado do controle remoto (.kbd do webOS). Letras, números, espaço, apagar e limpar, tudo
 * navegável com as setas. [first] recebe o foco da primeira tecla.
 */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    first: FocusRequester? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KEY_ROWS.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, ch ->
                    TvKey(
                        Modifier.weight(1f).then(if (r == 0 && c == 0 && first != null) Modifier.focusRequester(first) else Modifier),
                        label = ch.toString(), onClick = { onKey(ch.toString()) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvKey(Modifier.weight(2f), "Espaço", Icons.Default.SpaceBar) { onKey(" ") }
            TvKey(Modifier.weight(1.5f), "Apagar", Icons.AutoMirrored.Filled.Backspace, onBackspace)
            TvKey(Modifier.weight(1.5f), "Limpar", Icons.Default.Close, onClear)
        }
    }
}

@Composable
private fun TvKey(modifier: Modifier, label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color.White else Surface2
    val fg = if (focused) TextOnLight else TextPrimary
    Row(
        modifier
            .height(44.dp)
            .scale(if (focused) 1.07f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontSize = if (icon != null) 13.sp else 17.sp, fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = 1)
    }
}

/**
 * Pergunta um texto com o teclado na tela (App.ui.prompt do webOS). Voltar cancela.
 */
@Composable
fun TvTextPrompt(
    title: String,
    initial: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 20,
    okLabel: String = "Salvar",
) {
    var value by remember { mutableStateOf(initial) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(20.dp)).background(BackgroundCard).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter)
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2B3644)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    value.ifEmpty { "Nome do perfil" } + if (value.isEmpty()) "" else "|",
                    color = if (value.isEmpty()) TextTertiary else TextPrimary, fontSize = 18.sp, fontFamily = Inter, maxLines = 1,
                )
            }
            TvKeyboard(
                onKey = { if (value.length < maxLength) value = typeKey(value, it) },
                onBackspace = { value = value.dropLast(1) },
                onClear = { value = "" },
                first = first,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                AotvButton("Cancelar", onDismiss, compact = true)
                AotvButton(okLabel, { onDone(value.trim()) }, icon = Icons.Default.Check, tone = ButtonTone.PRIMARY, enabled = value.isNotBlank(), compact = true)
            }
        }
    }
}

/**
 * Campo de texto na TV. As setas passam por ele sem abrir o teclado do sistema (que rouba o
 * controle). OK no campo libera a digitação e o teclado abre, sair do campo trava de novo.
 * No celular desenha o campo direto.
 */
@Composable
fun TvTextFieldGate(modifier: Modifier = Modifier, label: String? = null, field: @Composable (Modifier) -> Unit) {
    if (!LocalIsTv.current) { field(modifier); return }
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Box(modifier) {
        field(
            Modifier.fillMaxWidth()
                .focusRequester(fieldFocus)
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (!it.isFocused && editing) editing = false }
                // Campo de uma linha. Cima e baixo saem dele, fecham o teclado e seguem a seta.
                .onPreviewKeyEvent { ev ->
                    if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val dir = when (ev.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> androidx.compose.ui.focus.FocusDirection.Down
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> androidx.compose.ui.focus.FocusDirection.Up
                        else -> return@onPreviewKeyEvent false
                    }
                    keyboard?.hide()
                    editing = false
                    focusManager.moveFocus(dir)
                    true
                },
        )
        if (!editing) Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(12.dp))
                .then(label?.let { name -> Modifier.semantics { contentDescription = name } } ?: Modifier)
                // Passa o foco direto ao campo no mesmo clique. Se o botão sumisse antes, o foco
                // cairia em outro lugar e o campo nem chegaria a abrir.
                .clickable(role = Role.Button, onClickLabel = "Digitar") {
                    editing = true
                    runCatching { fieldFocus.requestFocus() }
                },
        )
    }
}
