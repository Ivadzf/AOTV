package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import com.aotv.app.data.local.DEFAULT_PLAYER_BUTTONS
import com.aotv.app.data.local.DEFAULT_PLAYER_TOP_ACTIONS
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Rótulo em pt-BR de um botão do player, pelo id canônico. */
fun playerButtonLabel(id: String): String = when (id) {
    "subs" -> "Legendas"
    "audio" -> "Áudio"
    "sources" -> "Fontes"
    "episodes" -> "Episódios"
    "prev" -> "Anterior"
    "next" -> "Próximo"
    "screen" -> "Tela"
    "speed" -> "Velocidade"
    "pin" -> "Fixar fonte"
    "skip" -> "Pular abertura"
    else -> id
}

/** Rótulo em pt-BR de um botão do topo do player ("Voltar" sempre aparece). */
fun playerTopActionLabel(id: String): String = when (id) {
    "cast" -> "Transmitir"
    "rotate" -> "Girar"
    "lock" -> "Travar"
    else -> id
}

/** Botões do player em vigor: os guardados, ou o padrão quando não há nada salvo. */
fun effectivePlayerButtons(saved: List<String>): List<String> =
    saved.ifEmpty { DEFAULT_PLAYER_BUTTONS }

/** Botões do topo do player em vigor: os guardados, ou o padrão quando não há nada salvo. */
fun effectivePlayerTopActions(saved: List<String>): List<String> =
    saved.ifEmpty { DEFAULT_PLAYER_TOP_ACTIONS }

/**
 * Preferências do player (Fase C). Lê os fluxos do PlatformPrefs, então mudar
 * nas Configurações aplica na hora no player, mesmo com ele na pilha.
 * Lista guardada vazia = padrão; guardar a lista igual ao padrão limpa o salvo.
 */
@HiltViewModel
class PlayerPrefsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
) : ViewModel() {
    /** Botões do player guardados, na ordem (vazio = padrão). */
    val buttons: StateFlow<List<String>> = prefs.playerButtonsFlow

    /** Move um botão da lista em vigor ([delta] -1 sobe, 1 desce). Só vale para botão ligado. */
    fun moveButton(id: String, delta: Int) {
        val cur = effectivePlayerButtons(buttons.value).toMutableList()
        val i = cur.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in cur.indices) return
        cur[i] = cur[j].also { cur[j] = cur[i] }
        saveButtons(cur)
    }

    /** Liga ou desliga um botão. Ligar volta o botão na posição que ele tem no padrão. */
    fun setButtonEnabled(id: String, enabled: Boolean) {
        val cur = effectivePlayerButtons(buttons.value).toMutableList()
        if (enabled) {
            if (id !in cur) {
                val pos = cur.count { DEFAULT_PLAYER_BUTTONS.indexOf(it) < DEFAULT_PLAYER_BUTTONS.indexOf(id) }
                cur.add(pos, id)
            }
        } else {
            cur.remove(id)
        }
        saveButtons(cur)
    }

    /** Volta os botões do player para o padrão (limpa o salvo). */
    fun resetButtons() { prefs.playerButtons = emptyList() }

    private fun saveButtons(list: List<String>) {
        prefs.playerButtons = if (list == DEFAULT_PLAYER_BUTTONS) emptyList() else list
    }

    /** Botões do topo do player guardados (vazio = padrão). "Voltar" sempre aparece. */
    val topActions: StateFlow<List<String>> = prefs.playerTopActionsFlow

    /** Liga ou desliga um botão do topo. */
    fun setTopActionEnabled(id: String, enabled: Boolean) {
        val cur = effectivePlayerTopActions(topActions.value).toMutableList()
        if (enabled) {
            if (id !in cur) {
                val pos = cur.count { DEFAULT_PLAYER_TOP_ACTIONS.indexOf(it) < DEFAULT_PLAYER_TOP_ACTIONS.indexOf(id) }
                cur.add(pos, id)
            }
        } else {
            cur.remove(id)
        }
        prefs.playerTopActions = if (cur == DEFAULT_PLAYER_TOP_ACTIONS) emptyList() else cur
    }

    val tapSeek: StateFlow<Boolean> = prefs.playerTapSeekFlow
    fun setTapSeek(v: Boolean) { prefs.playerTapSeek = v }

    val volBoost: StateFlow<Boolean> = prefs.playerVolBoostFlow
    fun setVolBoost(v: Boolean) { prefs.playerVolBoost = v }

    val volBoostLevel: StateFlow<Float> = prefs.playerVolBoostLevelFlow
    fun setVolBoostLevel(v: Float) { prefs.playerVolBoostLevel = v.coerceIn(1f, 2f) }

    val mini: StateFlow<Boolean> = prefs.playerMiniFlow
    fun setMini(v: Boolean) { prefs.playerMini = v }

    val autoNext: StateFlow<Boolean> = prefs.playerAutoNextFlow
    fun setAutoNext(v: Boolean) { prefs.playerAutoNext = v }

    val autoNextDelay: StateFlow<Int> = prefs.playerAutoNextDelayFlow
    fun setAutoNextDelay(v: Int) { prefs.playerAutoNextDelay = v }
}
