package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import com.aotv.app.ui.common.topInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.MAX_PROFILES
import com.aotv.app.data.local.PROFILE_COLORS
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.Profile
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val library: LibraryRepository,
) : ViewModel() {
    val profiles = prefs.profiles
    val profileId = prefs.profileId

    fun select(id: String) = prefs.selectProfile(id)
    fun add(name: String, kids: Boolean, color: Int) {
        prefs.addProfile(name, kids)?.let { prefs.updateProfile(it.copy(color = color)) }
    }
    fun update(p: Profile) = prefs.updateProfile(p)
    fun remove(id: String) {
        if (prefs.removeProfile(id)) viewModelScope.launch { library.dropProfile(id) }
    }

    // PIN dos pais: trava sair do infantil e entrar em editar perfis com infantil ativo.
    fun hasPin() = prefs.hasParentalPin
    fun checkPin(pin: String) = prefs.checkParentalPin(pin)
    fun setPin(pin: String) = prefs.setParentalPin(pin)

    // Modo privado (Lote 2, item 7 — v1.10.0): por perfil, vale p/ o perfil ativo.
    val privateMode = prefs.privateModeFlow
    fun setPrivateMode(v: Boolean) { prefs.privateMode = v }
}

/** Círculo com a inicial na cor do perfil. Usado na grade, na barra e nas Configurações. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val initialSize = with(density) { (size * 0.44f).toSp() }
    val kidsSize = with(density) { (size * 0.13f).coerceAtLeast(7.dp).toSp() }
    val avatarText = androidx.compose.ui.text.TextStyle(
        fontFamily = com.aotv.app.ui.theme.Inter,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
    )
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Color(PROFILE_COLORS[profile.color.mod(PROFILE_COLORS.size)])),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.trim().take(1).uppercase().ifBlank { "?" },
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = initialSize,
            lineHeight = initialSize, textAlign = TextAlign.Center, style = avatarText,
        )
        if (profile.kids && size >= 48.dp) {
            Text(
                "KIDS", color = Color.White, fontSize = kidsSize, lineHeight = kidsSize,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center, style = avatarText,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.06f),
            )
        }
    }
}

/**
 * "Quem está assistindo?". Abre sozinha quando há mais de um perfil e também pela barra e pelas
 * Configurações. Em modo edição, tocar num perfil abre o painel para renomear, trocar a cor,
 * marcar infantil ou remover. Fase A: a aba Perfil dá acesso às Configurações
 * (o "Mais" da barra antiga foi removido) via [onOpenSettings].
 */
@Composable
fun ProfilesScreen(
    onPicked: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    startEditing: Boolean = false,
    vm: ProfilesViewModel = hiltViewModel(),
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val current by vm.profileId.collectAsStateWithLifecycle()
    val me = profiles.firstOrNull { it.id == current }
    var editing by remember { mutableStateOf(startEditing) }
    // null fechado, Profile com id vazio é "novo perfil".
    var panel by remember { mutableStateOf<Profile?>(null) }
    // PIN dos pais: perfil pra trocar depois de confirmar, ou "entrar em editar" pendente.
    var pendingSwitch by remember { mutableStateOf<Profile?>(null) }
    var pendingEdit by remember { mutableStateOf(false) }
    var showSetPinNudge by remember { mutableStateOf(false) }
    // Controle remoto começa no perfil em uso.
    val currentTile = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(200); runCatching { currentTile.requestFocus() } }

    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(AccentSoft, Background), radius = 1400f))
            .verticalScroll(rememberScrollState())
            .topInsetsPadding().padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (editing) "Editar perfis" else "Quem está assistindo?",
            color = TextPrimary, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, letterSpacing = (-0.6).sp,
        )
        Text(
            if (editing) "Toque num perfil para mudar nome, cor ou modo infantil" else "Cada perfil tem a própria lista e o próprio progresso",
            color = TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(36.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            profiles.forEach { p ->
                ProfileTile(
                    label = p.name,
                    modifier = if (p.id == current) Modifier.focusRequester(currentTile) else Modifier,
                    selected = p.id == current && !editing,
                    onClick = {
                        if (editing) panel = p
                        // Sair do infantil pra outro perfil pede o PIN, quando existe um definido.
                        else if (me?.kids == true && !p.kids && vm.hasPin()) pendingSwitch = p
                        else { vm.select(p.id); onPicked() }
                    },
                ) {
                    Box {
                        ProfileAvatar(p, 96.dp)
                        if (editing) {
                            Box(
                                Modifier.matchParentSize().clip(RoundedCornerShape(27.dp)).background(Color(0x88000000)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Edit, "Editar ${p.name}", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        }
                    }
                }
            }
            if (profiles.size < MAX_PROFILES) {
                ProfileTile(label = "Adicionar", selected = false, onClick = {
                    panel = Profile("", "", profiles.size % PROFILE_COLORS.size, false)
                }) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(27.dp)).border(2.dp, Border, RoundedCornerShape(27.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, "Adicionar perfil", tint = TextSecondary, modifier = Modifier.size(40.dp)) }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        com.aotv.app.ui.common.AotvButton(
            if (editing) "Pronto" else "Editar perfis",
            {
                // Entrar em editar com o infantil ativo pede o PIN. Sair (Pronto) não pede nada.
                if (!editing && me?.kids == true && vm.hasPin()) pendingEdit = true else editing = !editing
            },
            icon = if (editing) Icons.Default.Check else Icons.Default.Edit,
            tone = if (editing) com.aotv.app.ui.common.ButtonTone.PRIMARY else com.aotv.app.ui.common.ButtonTone.SECONDARY,
        )
        // Configurações (Fase A): sem o "Mais" da barra, o caminho p/ Configurações é a aba Perfil.
        val openSettings = onOpenSettings
        if (openSettings != null) {
            Spacer(Modifier.height(16.dp))
            com.aotv.app.ui.common.AotvButton(
                "Configurações",
                openSettings,
                icon = Icons.Default.Settings,
                tone = com.aotv.app.ui.common.ButtonTone.SECONDARY,
            )
        }
        // Modo privado (Lote 2, item 7 — v1.10.0): toggle do perfil ativo, no
        // mesmo lugar onde ficam as Configurações (a aba Perfil é o "hub" do
        // perfil). Ligado, assistir não salva progresso, não atualiza o
        // Continuar assistindo, não marca episódio como visto e não avisa o
        // Trakt. Desligar volta ao normal; o que foi visto no privado NÃO
        // aparece depois, porque nunca foi gravado. O progresso anterior
        // (de antes de ligar) NÃO é apagado. O mesmo toggle existe no player
        // (chip "Privado"), p/ ligar/desligar sem sair do vídeo.
        val privateMode by vm.privateMode.collectAsStateWithLifecycle()
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BackgroundCard)
                .clickable(role = Role.Button, onClick = { vm.setPrivateMode(!privateMode) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.VisibilityOff, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Modo privado", color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (privateMode) "Ligado — assistir não salva progresso nem histórico"
                    else "Assistir sem salvar progresso, Continuar assistindo ou episódios vistos",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
            Switch(
                checked = privateMode,
                onCheckedChange = { vm.setPrivateMode(it) },
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }
    }

    panel?.let { p ->
        ProfileEditDialog(
            initial = p,
            canRemove = p.id.isNotEmpty() && profiles.size > 1,
            onDismiss = { panel = null },
            onSave = { edited ->
                val becameKids = edited.kids && !p.kids
                if (edited.id.isEmpty()) vm.add(edited.name, edited.kids, edited.color) else vm.update(edited)
                panel = null
                // Marcou infantil pela 1ª vez sem PIN nenhum definido no aparelho: convida a criar
                // um agora, senão o modo infantil não trava nada (qualquer um sai dele na hora).
                if (becameKids && !vm.hasPin()) showSetPinNudge = true
            },
            onRemove = { vm.remove(p.id); panel = null },
        )
    }

    pendingSwitch?.let { target ->
        VerifyPinDialog(
            onConfirm = { pin ->
                if (vm.checkPin(pin)) { vm.select(target.id); pendingSwitch = null; onPicked(); true } else false
            },
            onDismiss = { pendingSwitch = null },
        )
    }
    if (pendingEdit) {
        VerifyPinDialog(
            onConfirm = { pin -> if (vm.checkPin(pin)) { editing = true; pendingEdit = false; true } else false },
            onDismiss = { pendingEdit = false },
        )
    }
    if (showSetPinNudge) {
        SetPinDialog(
            title = "Criar PIN pro infantil?",
            onSet = { pin -> vm.setPin(pin); showSetPinNudge = false },
            onDismiss = { showSetPinNudge = false },
        )
    }
}

/** Perfil da grade. Foco do controle aumenta o avatar com anel branco (.prof-tile.is-focus). */
@Composable
private fun ProfileTile(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, avatar: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val tileScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Column(
        modifier.width(120.dp * tileScale).onFocusChanged { focused = it.isFocused }
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.scale(if (focused) 1.08f else 1f)
                .border(3.dp, if (focused || selected) TextPrimary else Color.Transparent, RoundedCornerShape(30.dp)).padding(3.dp),
        ) { avatar() }
        Spacer(Modifier.height(10.dp))
        Text(
            label, color = if (selected || focused) TextPrimary else TextSecondary, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileEditDialog(
    initial: Profile,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
    onRemove: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kids by remember { mutableStateOf(initial.kids) }
    var color by remember { mutableIntStateOf(initial.color) }
    var confirmRemove by remember { mutableStateOf(false) }
    val draft = initial.copy(name = name, kids = kids, color = color)
    // Na TV o nome vem do teclado na tela. Perfil novo já abre pedindo o nome.
    val tv = com.aotv.app.ui.common.LocalIsTv.current
    var naming by remember { mutableStateOf(tv && initial.id.isEmpty()) }
    // Uma janela por vez. O teclado some e a ficha do perfil volta com o nome novo.
    if (naming) {
        com.aotv.app.ui.common.TvTextPrompt(
            title = if (initial.id.isEmpty()) "Novo perfil" else "Renomear perfil",
            initial = name,
            onDone = { name = it.take(20); naming = false },
            // Desistir do nome de um perfil novo desiste do perfil.
            onDismiss = { if (initial.id.isEmpty() && name.isBlank()) onDismiss() else naming = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text(if (initial.id.isEmpty()) "Novo perfil" else "Editar perfil", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProfileAvatar(draft.copy(name = name.ifBlank { "?" }), 56.dp)
                    if (tv) com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.Edit, title = name.ifBlank { "Sem nome" }, sub = "Renomear",
                        modifier = Modifier.weight(1f), onClick = { naming = true },
                    ) else OutlinedTextField(
                        value = name, onValueChange = { name = it.take(20) },
                        label = { Text("Nome") }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                        ),
                    )
                }
                Text("Cor", color = TextTertiary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PROFILE_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                .border(3.dp, if (i == color) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { if (i == color) Icon(Icons.Default.Check, "Cor escolhida", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { kids = !kids }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Perfil infantil", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Só desenhos e títulos até 12 anos", color = TextTertiary, fontSize = 12.sp)
                    }
                    // A linha inteira já liga e desliga. O controle não precisa parar no Switch.
                    Switch(checked = kids, onCheckedChange = { kids = it }, modifier = Modifier.focusProperties { canFocus = false })
                }
                if (canRemove) {
                    TextButton(onClick = { if (confirmRemove) onRemove() else confirmRemove = true }) {
                        Text(
                            if (confirmRemove) "Tocar de novo apaga a lista e o histórico" else "Remover perfil",
                            color = LiveRed, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = name.isNotBlank()) { Text("Salvar", color = Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) } },
    )
}
