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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.aotv.app.data.accounts.NuvioProfile
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
    fun add(name: String, kids: Boolean, color: Int, avatarUrl: String = "", avatarId: String = "", usePrimaryAddons: Boolean = false, usePrimaryPlugins: Boolean = false, kidsAgeLimit: Int = 12) {
        prefs.addProfile(name, kids)?.let {
            prefs.updateProfile(it.copy(color = color, avatarUrl = sanitizeAvatarUrlField(avatarUrl), avatarId = avatarId, usePrimaryAddons = usePrimaryAddons, usePrimaryPlugins = usePrimaryPlugins))
            if (kids) setKidsAgeLimit(it.id, kidsAgeLimit)
        }
    }

    // Limite de idade do perfil infantil (Lote 23, v1.33.0): por perfil, via
    // chave crua — o setter tipado (`prefs.kidsAgeLimit`) só escreve no
    // perfil ATIVO, e aqui se edita qualquer perfil (inclusive o que está
    // sendo criado, que ainda não é o ativo). 0 = só gênero, sem idade.
    fun kidsAgeLimitOf(profileId: String): Int =
        (prefs.rawPref("kids_age_limit_v1", profileId) as? Int) ?: 12

    // Trava do limite kids por PIN ligada? (v1.45.0, item 10).
    val kidsAgeLimitLocked: Boolean get() = prefs.kidsAgeLimitLocked
    val kidsAgeLimitLockedFlow = prefs.kidsAgeLimitLockedFlow
    fun setKidsAgeLimitLocked(v: Boolean) { prefs.kidsAgeLimitLocked = v }

    fun setKidsAgeLimit(profileId: String, value: Int) {
        prefs.writeRawPref("kids_age_limit_v1", value.coerceIn(0, 18), profileId)
    }

    // Trava do PIN parental (Lote 23): 5 erros seguidos travam por 5 min.
    val isParentalLockedOut: Boolean get() = prefs.isParentalLockedOut

    /** Minutos restantes da trava (arredonda p/ cima; 0 = livre). */
    fun parentalLockoutMinutesLeft(): Int {
        val left = prefs.parentalLockoutUntilMs - System.currentTimeMillis()
        if (left <= 0) return 0
        return ((left + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    /** Registra um erro do PIN parental. Devolve true quando travou agora (5ª seguida). */
    fun recordParentalPinFailure(): Boolean = prefs.recordParentalPinFailure()

    /** PIN parental certo: zera as falhas seguidas. */
    fun resetParentalPinFailures() { prefs.resetParentalPinFailures() }
    fun update(p: Profile) = prefs.updateProfile(p)
    fun remove(id: String) {
        if (prefs.removeProfile(id)) viewModelScope.launch { library.dropProfile(id) }
    }

    // PIN do perfil (Lote 3, v1.11.0): hash guardado no próprio Profile.
    fun checkProfilePin(p: Profile, pin: String) = prefs.checkProfilePin(p, pin)
    fun hashProfilePin(pin: String) = prefs.hashProfilePin(pin)

    // PIN dos pais: trava sair do infantil e entrar em editar perfis com infantil ativo.
    fun hasPin() = prefs.hasParentalPin
    fun checkPin(pin: String) = prefs.checkParentalPin(pin)
    fun setPin(pin: String) = prefs.setParentalPin(pin)

    // Modo privado (Lote 2, item 7 — v1.10.0): por perfil, vale p/ o perfil ativo.
    val privateMode = prefs.privateModeFlow
    fun setPrivateMode(v: Boolean) { prefs.privateMode = v }
}

/**
 * Avatar do perfil. Ordem (Lote 3, v1.11.0): URL personalizada (Coil, a mesma
 * do resto do app) → preset do catálogo embutido → inicial colorida (o atual).
 * URL inválida/vazia ou que falha ao carregar cai p/ o próximo, sem travar.
 * Usado na grade, na barra e nas Configurações (mesma assinatura de antes).
 */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier, showDot: Boolean = false) {
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
    val url = validAvatarUrl(profile.avatarUrl)
    var imgFailed by remember(url) { mutableStateOf(false) }
    val preset = avatarPreset(profile.avatarId)
    val bg = Color(preset?.color ?: PROFILE_COLORS[profile.color.mod(PROFILE_COLORS.size)].toInt())
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank() && !imgFailed) {
            AsyncImage(
                model = url, contentDescription = profile.name, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imgFailed = true },
            )
        } else if (preset != null) {
            Text(
                preset.glyph, color = Color.White, fontSize = initialSize, lineHeight = initialSize,
                textAlign = TextAlign.Center, style = avatarText,
            )
        } else {
            Text(
                profile.name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = initialSize,
                lineHeight = initialSize, textAlign = TextAlign.Center, style = avatarText,
            )
        }
        if (profile.kids && size >= 48.dp) {
            Text(
                "KIDS", color = Color.White, fontSize = kidsSize, lineHeight = kidsSize,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center, style = avatarText,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.06f),
            )
        }
        if (profile.pinHash.isNotBlank() && size >= 64.dp) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).clip(CircleShape)
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Lock, "Perfil com PIN", tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
        // Bolinha "What's new" (Lote 18, v1.28.0): novidade não vista da
        // versão instalada — some quando o usuário vê o diálogo.
        if (showDot) {
            Box(
                Modifier.align(Alignment.TopEnd).size((size * 0.34f).coerceAtLeast(8.dp)).clip(CircleShape)
                    .background(com.aotv.app.ui.theme.Accent)
                    .border(1.5.dp, Color.White, CircleShape),
            )
        }
    }
}

/**
 * "Quem está assistindo?". Abre sozinha quando há mais de um perfil e também pela barra e pelas
 * Configurações. Em modo edição, tocar num perfil abre o painel para renomear, trocar a cor,
 * marcar infantil ou remover. Lote 2 (v1.47.0), item 10: o seletor saiu da
 * aba Perfil (que virou o hub das Configurações) e vive dentro de
 * "Alternar perfil" no hub — sem botão de Configurações aqui dentro; o
 * Modo privado continua no seletor (é por perfil, e o seletor é onde os
 * perfis são gerenciados).
 */
@Composable
fun ProfilesScreen(
    onPicked: () -> Unit,
    startEditing: Boolean = false,
    vm: ProfilesViewModel = hiltViewModel(),
    // v1.46.0 (Lote 1 item 1): importar os perfis do Nuvio — o diálogo
    // precisa da conta Nuvio, que vive no AccountsViewModel.
    accountsVm: AccountsViewModel = hiltViewModel(),
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val current by vm.profileId.collectAsStateWithLifecycle()
    val ageLimitLocked by vm.kidsAgeLimitLockedFlow.collectAsStateWithLifecycle()
    val accountsUi by accountsVm.ui.collectAsStateWithLifecycle()
    val me = profiles.firstOrNull { it.id == current }
    var editing by remember { mutableStateOf(startEditing) }
    // null fechado, Profile com id vazio é "novo perfil".
    var panel by remember { mutableStateOf<Profile?>(null) }
    // PIN dos pais: perfil pra trocar depois de confirmar, ou "entrar em editar" pendente.
    var pendingSwitch by remember { mutableStateOf<Profile?>(null) }
    var pendingEdit by remember { mutableStateOf(false) }
    // v1.45.0 (item 10): mudança do limite de idade kids esperando o PIN
    // dos pais — (perfil editado, limite novo).
    var pendingLimitSave by remember { mutableStateOf<Pair<Profile, Int>?>(null) }
    // PIN do perfil (Lote 3): trocar p/ um perfil com PIN ou editar um perfil com PIN.
    var pendingSwitchPin by remember { mutableStateOf<Profile?>(null) }
    var pendingEditProfile by remember { mutableStateOf<Profile?>(null) }
    var showSetPinNudge by remember { mutableStateOf(false) }
    // v1.46.0 (Lote 1 item 1): diálogo "De qual perfil do Nuvio?".
    var showNuvioImport by remember { mutableStateOf(false) }
    // Conta Nuvio conectada: garante a lista de perfis carregada para o diálogo.
    LaunchedEffect(accountsUi.nuvio) {
        if (accountsUi.nuvio != null) accountsVm.refreshNuvioProfiles()
    }
    // Trava do PIN parental (Lote 23): com o PIN travado, o diálogo de
    // troca/edição mostra o tempo restante e não aceita tentativa.
    var parentalLocked by remember { mutableStateOf(false) }
    var parentalLockoutMin by remember { mutableIntStateOf(0) }
    fun refreshParentalLockout() {
        parentalLocked = vm.isParentalLockedOut
        parentalLockoutMin = vm.parentalLockoutMinutesLeft()
    }
    // Ao abrir um gate parental, lê a trava do momento (ela pode ter vencido
    // desde a última abertura — o diálogo não fica com o estado velho).
    LaunchedEffect(pendingSwitch, pendingEdit, pendingLimitSave) {
        if (pendingSwitch != null || pendingEdit || pendingLimitSave != null) refreshParentalLockout()
    }

    // Salva o perfil editado/criado (extraído do onSave p/ o gate de PIN do
    // limite kids reutilizar o mesmo caminho — v1.45.0, item 10).
    fun saveProfile(edited: Profile, limit: Int) {
        val original = profiles.firstOrNull { it.id == edited.id }
        val becameKids = edited.kids && original?.kids != true
        if (edited.id.isEmpty()) vm.add(edited.name, edited.kids, edited.color, edited.avatarUrl, edited.avatarId, edited.usePrimaryAddons, edited.usePrimaryPlugins, kidsAgeLimit = limit)
        else {
            vm.update(edited)
            if (edited.kids) vm.setKidsAgeLimit(edited.id, limit)
        }
        panel = null
        // Marcou infantil pela 1ª vez sem PIN nenhum definido no aparelho: convida a criar
        // um agora, senão o modo infantil não trava nada (qualquer um sai dele na hora).
        if (becameKids && !vm.hasPin()) showSetPinNudge = true
    }
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
                        if (editing) {
                            // Editar um perfil com PIN pede o PIN antes de abrir o editor.
                            if (p.pinHash.isNotBlank()) pendingEditProfile = p else panel = p
                        }
                        // Trocar p/ um perfil com PIN pede o PIN dele (antes do PIN dos pais).
                        else if (p.pinHash.isNotBlank()) pendingSwitchPin = p
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
        // v1.46.0 (Lote 1 item 1): importar os perfis do Nuvio de uma vez.
        // Só aparece com a conta Nuvio conectada.
        if (accountsUi.nuvio != null) {
            Spacer(Modifier.height(16.dp))
            com.aotv.app.ui.common.AotvButton(
                "Importar perfis do Nuvio",
                { accountsVm.clearMessage(); showNuvioImport = true },
                icon = Icons.Default.CloudDownload,
                tone = com.aotv.app.ui.common.ButtonTone.SECONDARY,
            )
        }
        // Modo privado (Lote 2, item 7 — v1.10.0): toggle do perfil ativo, no
        // seletor de perfis (é por perfil, e o seletor é onde os perfis são
        // gerenciados). Ligado, assistir não salva progresso, não atualiza o
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
            initialAgeLimit = if (p.id.isEmpty()) 12 else vm.kidsAgeLimitOf(p.id),
            canRemove = p.id.isNotEmpty() && profiles.size > 1,
            // Resumo como na foto do Nuvio ("Perfil 2 | Addons primários ativados / Avatar: Kai").
            summary = if (p.id.isEmpty()) "" else buildString {
                append("Perfil ${profiles.indexOfFirst { it.id == p.id } + 1}")
                if (p.usePrimaryAddons) append("  |  Addons primários ativados")
                if (p.usePrimaryPlugins) append("  |  Plugins primários ativados")
                avatarPreset(p.avatarId)?.let { append("  |  Avatar: ${it.name}") }
                if (p.pinHash.isNotBlank()) append("  |  Com PIN")
            },
            onDismiss = { panel = null },
            // v1.45.0 (item 10): trava do limite kids por PIN dos pais.
            ageLimitLocked = ageLimitLocked,
            onAgeLimitLockedChange = { vm.setKidsAgeLimitLocked(it) },
            onSave = { edited, limit ->
                // v1.45.0 (item 10): mudar o limite de idade de um perfil
                // infantil EXISTENTE, com a trava ligada e PIN dos pais
                // definido, pede o PIN dos pais antes de salvar. Criar um
                // perfil novo já define o limite na criação (sem gate).
                val limitChanged = edited.id.isNotEmpty() && edited.kids && vm.kidsAgeLimitOf(edited.id) != limit
                if (limitChanged && vm.kidsAgeLimitLocked && vm.hasPin()) {
                    pendingLimitSave = edited to limit
                } else {
                    saveProfile(edited, limit)
                }
            },
            onRemove = { vm.remove(p.id); panel = null },
            onHashPin = { pin -> vm.hashProfilePin(pin) },
        )
    }

    pendingSwitchPin?.let { target ->
        VerifyPinDialog(
            title = "PIN do perfil",
            onConfirm = { pin ->
                if (vm.checkProfilePin(target, pin)) { vm.select(target.id); pendingSwitchPin = null; onPicked(); true } else false
            },
            onDismiss = { pendingSwitchPin = null },
        )
    }
    pendingEditProfile?.let { target ->
        VerifyPinDialog(
            title = "PIN do perfil",
            onConfirm = { pin ->
                if (vm.checkProfilePin(target, pin)) { panel = target; pendingEditProfile = null; true } else false
            },
            onDismiss = { pendingEditProfile = null },
        )
    }

    pendingSwitch?.let { target ->
        VerifyPinDialog(
            lockedOut = parentalLocked,
            lockoutMinutes = parentalLockoutMin,
            onWrongPin = {
                if (vm.recordParentalPinFailure()) {
                    parentalLocked = true
                    parentalLockoutMin = vm.parentalLockoutMinutesLeft()
                }
            },
            onConfirm = { pin ->
                if (vm.checkPin(pin)) {
                    vm.resetParentalPinFailures()
                    parentalLocked = false
                    vm.select(target.id); pendingSwitch = null; onPicked(); true
                } else false
            },
            onDismiss = { pendingSwitch = null },
        )
    }
    if (pendingEdit) {
        VerifyPinDialog(
            lockedOut = parentalLocked,
            lockoutMinutes = parentalLockoutMin,
            onWrongPin = {
                if (vm.recordParentalPinFailure()) {
                    parentalLocked = true
                    parentalLockoutMin = vm.parentalLockoutMinutesLeft()
                }
            },
            onConfirm = { pin ->
                if (vm.checkPin(pin)) {
                    vm.resetParentalPinFailures()
                    parentalLocked = false
                    editing = true; pendingEdit = false; true
                } else false
            },
            onDismiss = { pendingEdit = false },
        )
    }
    // v1.45.0 (item 10): mudar o limite de idade kids pede o PIN dos pais
    // (mesma trava de 5 erros / 5 min dos outros gates parentais).
    pendingLimitSave?.let { (edited, limit) ->
        VerifyPinDialog(
            lockedOut = parentalLocked,
            lockoutMinutes = parentalLockoutMin,
            onWrongPin = {
                if (vm.recordParentalPinFailure()) {
                    parentalLocked = true
                    parentalLockoutMin = vm.parentalLockoutMinutesLeft()
                }
            },
            onConfirm = { pin ->
                if (vm.checkPin(pin)) {
                    vm.resetParentalPinFailures()
                    parentalLocked = false
                    pendingLimitSave = null
                    saveProfile(edited, limit)
                    true
                } else false
            },
            onDismiss = { pendingLimitSave = null },
        )
    }
    if (showSetPinNudge) {
        SetPinDialog(
            title = "Criar PIN pro infantil?",
            onSet = { pin -> vm.setPin(pin); showSetPinNudge = false },
            onDismiss = { showSetPinNudge = false },
        )
    }
    // v1.46.0 (Lote 1 item 1): "De qual perfil do Nuvio?".
    if (showNuvioImport) {
        NuvioProfileImportDialog(
            profiles = accountsUi.nuvioProfiles,
            busy = accountsUi.busy == "nuvio_import_all",
            progress = accountsUi.progress,
            message = accountsUi.message,
            error = accountsUi.error,
            onDismiss = { showNuvioImport = false },
            onImport = { selected -> accountsVm.nuvioImportAllProfiles(selected) },
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
    initialAgeLimit: Int = 12,
    canRemove: Boolean,
    summary: String = "",
    onDismiss: () -> Unit,
    // v1.45.0 (item 10): trava do limite kids por PIN dos pais.
    ageLimitLocked: Boolean = true,
    onAgeLimitLockedChange: (Boolean) -> Unit = {},
    onSave: (Profile, Int) -> Unit,
    onRemove: () -> Unit,
    onHashPin: (String) -> String,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kids by remember { mutableStateOf(initial.kids) }
    var ageLimit by remember { mutableStateOf(initialAgeLimit.coerceIn(0, 18)) }
    var color by remember { mutableIntStateOf(initial.color) }
    // Lote 3 (v1.11.0): avatar por URL, preset do catálogo, "usar addons
    // primários" e PIN do perfil. O perfil principal (id "main") não tem o
    // toggle de primários — ele JÁ é o primário.
    // O literal "null" gravado no perfil aparece vazio aqui, nunca como texto.
    var avatarUrl by remember { mutableStateOf(sanitizeAvatarUrlField(initial.avatarUrl)) }
    var avatarId by remember { mutableStateOf(initial.avatarId) }
    var usePrimaryAddons by remember { mutableStateOf(initial.usePrimaryAddons) }
    var usePrimaryPlugins by remember { mutableStateOf(initial.usePrimaryPlugins) }
    var pinHash by remember { mutableStateOf(initial.pinHash) }
    var showSetProfilePin by remember { mutableStateOf(false) }
    var confirmRemovePin by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val draft = initial.copy(
        name = name, kids = kids, color = color, avatarUrl = sanitizeAvatarUrlField(avatarUrl),
        avatarId = avatarId, pinHash = pinHash, usePrimaryAddons = usePrimaryAddons,
        usePrimaryPlugins = usePrimaryPlugins,
    )
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
    if (showSetProfilePin) {
        SetPinDialog(
            title = "Definir PIN do perfil",
            onSet = { pin -> pinHash = onHashPin(pin); showSetProfilePin = false },
            onDismiss = { showSetProfilePin = false },
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
                    Column(Modifier.weight(1f)) {
                        if (tv) com.aotv.app.ui.common.SettingsRow(
                            icon = Icons.Default.Edit, title = name.ifBlank { "Sem nome" }, sub = "Renomear",
                            modifier = Modifier.fillMaxWidth(), onClick = { naming = true },
                        ) else OutlinedTextField(
                            value = name, onValueChange = { name = it.take(20) },
                            label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                            ),
                        )
                        if (summary.isNotBlank()) Text(summary, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
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
                // ── Avatar por URL (Lote 3 item 2a) ─────────────────────────
                // Colar o link da imagem; vazio ou inválido volta p/ o catálogo
                // e depois p/ a inicial, sem travar (ver ProfileAvatar).
                Text("URL de avatar personalizada", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = avatarUrl, onValueChange = { avatarUrl = it },
                    label = { Text("https://exemplo.com/avatar.png") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                // ── Catálogo de avatares (Lote 3 item 2b) ───────────────────
                // Presets embutidos no app (emoji + cor, sem URL de terceiros).
                // A URL acima tem prioridade quando carrega; escolher aqui só
                // muda o preset de fallback.
                Text("Escolher um avatar", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    avatarPreset(avatarId)?.name ?: "Nenhum — usa a inicial",
                    color = TextTertiary, fontSize = 12.sp,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AVATAR_CATALOG.forEach { preset ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(preset.color))
                                .border(3.dp, if (preset.id == avatarId) Color.White else Color.Transparent, CircleShape)
                                .clickable { avatarId = preset.id },
                            contentAlignment = Alignment.Center,
                        ) { Text(preset.glyph, fontSize = 24.sp, textAlign = TextAlign.Center) }
                    }
                }
                // ── Usar addons primários (Lote 3 item 2d) ──────────────────
                // Espelho do Nuvio: com o toggle ligado o perfil usa os
                // addons do perfil principal em vez dos seus; desligado,
                // cada perfil tem os seus. O principal não mostra o toggle
                // (ele já é o primário).
                if (initial.id != com.aotv.app.data.local.MAIN_PROFILE) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { usePrimaryAddons = !usePrimaryAddons }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Usar addons primários", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Compartilhar a configuração de addon do perfil principal em vez de gerenciar uma lista separada",
                                color = TextTertiary, fontSize = 12.sp,
                            )
                        }
                        Switch(checked = usePrimaryAddons, onCheckedChange = { usePrimaryAddons = it }, modifier = Modifier.focusProperties { canFocus = false })
                    }
                    // ── Usar plugins primários (v1.46.0, Lote 1 item 1) ─────
                    // Mesmo espelho do Nuvio para os repositórios de plugins:
                    // ligado, o perfil usa os plugins do principal; desligado,
                    // cada perfil tem os seus.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { usePrimaryPlugins = !usePrimaryPlugins }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Usar plugins primários", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Compartilhar os repositórios de plugins do perfil principal em vez de gerenciar uma lista separada",
                                color = TextTertiary, fontSize = 12.sp,
                            )
                        }
                        Switch(checked = usePrimaryPlugins, onCheckedChange = { usePrimaryPlugins = it }, modifier = Modifier.focusProperties { canFocus = false })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { kids = !kids }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Perfil infantil", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (kids) {
                                if (ageLimit <= 0) "Só por gênero, sem limite de idade"
                                else "Só desenhos e títulos até $ageLimit anos"
                            } else "Só desenhos e títulos para crianças",
                            color = TextTertiary, fontSize = 12.sp,
                        )
                    }
                    // A linha inteira já liga e desliga. O controle não precisa parar no Switch.
                    Switch(checked = kids, onCheckedChange = { kids = it }, modifier = Modifier.focusProperties { canFocus = false })
                }
                // ── Limite de idade (Lote 23, v1.33.0) ───────────────────────
                // Vale pro perfil infantil: 0 ("Só gênero") ignora a
                // classificação indicativa e filtra só pelo gênero.
                if (kids) {
                    Text("Limite de idade", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 7, 10, 12, 14, 16, 18).forEach { limit ->
                            val selected = ageLimit == limit
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Accent else BackgroundCard)
                                    .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(999.dp))
                                    .clickable { ageLimit = limit }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (limit == 0) "Só gênero" else "Até $limit anos",
                                    color = if (selected) Color.White else TextPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    // v1.45.0 (item 10): travar o limite com o PIN dos pais.
                    // Sem PIN dos pais definido, a trava não trava nada.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAgeLimitLockedChange(!ageLimitLocked) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Travar limite com PIN dos pais", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (ageLimitLocked) "Mudar o limite pede o PIN dos pais"
                                else "Qualquer um muda o limite",
                                color = TextTertiary, fontSize = 12.sp,
                            )
                        }
                        Switch(checked = ageLimitLocked, onCheckedChange = onAgeLimitLockedChange, modifier = Modifier.focusProperties { canFocus = false })
                    }
                }
                // ── PIN do perfil (Lote 3 item 2c) ──────────────────────────
                // Trava entrar neste perfil E editar este perfil. Sem PIN, o
                // comportamento é o atual. O hash vive no Profile; a troca
                // pede o PIN (VerifyPinDialog "PIN do perfil").
                Text("Segurança", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Adicione um PIN se quiser que este perfil seja bloqueado antes de alternar para ele.",
                    color = TextTertiary, fontSize = 12.sp,
                )
                if (pinHash.isBlank()) {
                    TextButton(onClick = { showSetProfilePin = true }) {
                        Text("Definir bloqueio por PIN", color = Accent, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Text("PIN definido", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showSetProfilePin = true }) { Text("Trocar PIN", color = Accent) }
                        TextButton(onClick = { if (confirmRemovePin) { pinHash = ""; confirmRemovePin = false } else confirmRemovePin = true }) {
                            Text(if (confirmRemovePin) "Tocar de novo remove" else "Remover", color = LiveRed)
                        }
                    }
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
            TextButton(onClick = { onSave(draft, ageLimit) }, enabled = name.isNotBlank()) { Text("Salvar", color = Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) } },
    )
}

/**
 * "De qual perfil do Nuvio?" (v1.46.0, Lote 1 item 1). Lista os perfis da
 * conta Nuvio com "Todos os perfis" e escolha individual; o principal
 * (índice 1) substitui o Principal do app. Desenho reimplementado do
 * diálogo do JOTV 1.0.24 — nada de código do JOTV aqui.
 */
@Composable
private fun NuvioProfileImportDialog(
    profiles: List<NuvioProfile>,
    busy: Boolean,
    progress: String?,
    message: String?,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (Set<Int>) -> Unit,
) {
    // null = ainda sem escolha do usuário: todos marcados.
    var selected by remember { mutableStateOf<Set<Int>?>(null) }
    val sel = selected ?: profiles.map { it.index }.toSet()
    val done = !busy && (message != null || error != null)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = BackgroundCard,
        title = { Text("De qual perfil do Nuvio?", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cada perfil escolhido vira um perfil do aparelho, com a própria lista e o próprio progresso. O principal do Nuvio substitui o Principal do app.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                if (profiles.isEmpty()) {
                    Text("Carregando perfis da conta Nuvio…", color = TextTertiary, fontSize = 13.sp)
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { selected = if (sel.size == profiles.size) emptySet() else profiles.map { it.index }.toSet() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = sel.size == profiles.size,
                            onCheckedChange = { checked -> selected = if (checked) profiles.map { it.index }.toSet() else emptySet() },
                        )
                        Text("Todos os perfis", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    profiles.forEach { p ->
                        val checked = sel.contains(p.index)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { selected = if (checked) sel - p.index else sel + p.index }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked -> selected = if (isChecked) sel + p.index else sel - p.index },
                            )
                            ProfileAvatar(
                                Profile(id = "nuvio-${p.index}", name = p.name, avatarUrl = p.avatarUrl),
                                40.dp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                val sub = when {
                                    p.index == 1 -> "Principal — substitui o Principal do app"
                                    p.usesPrimaryAddons && p.usesPrimaryPlugins -> "Usa os addons e plugins do perfil principal"
                                    p.usesPrimaryAddons -> "Usa os addons do perfil principal"
                                    p.usesPrimaryPlugins -> "Usa os plugins do perfil principal"
                                    else -> "Perfil próprio"
                                }
                                Text(sub, color = TextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent)
                    if (progress != null) Text(progress, color = TextSecondary, fontSize = 13.sp)
                }
                if (message != null) Text(message, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                if (error != null) Text(error, color = LiveRed, fontSize = 13.sp, lineHeight = 18.sp)
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = onDismiss) { Text("Pronto", color = Accent, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(
                    onClick = { onImport(sel) },
                    enabled = sel.isNotEmpty() && profiles.isNotEmpty() && !busy,
                ) { Text("Importar ${if (sel.size == profiles.size) "todos" else "${sel.size}"}", color = Accent, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            if (!done) TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar", color = TextSecondary) }
        },
    )
}
