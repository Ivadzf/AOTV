package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.party.PARTY_SYNC_UNAVAILABLE_REASON
import com.aotv.app.data.party.PartyMember
import com.aotv.app.data.party.PartySession
import com.aotv.app.data.party.PartySyncState
import com.aotv.app.data.party.WatchParty
import com.aotv.app.data.party.WatchPartyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado da tela "Watch Party" (Lote 10, v1.20.0). */
data class WatchPartyUiState(
    val session: PartySession? = null,
    val localName: String = "",
    val members: List<PartyMember> = emptyList(),
    /** Código que chegou por deep link e ainda não foi usado (pré-preenche a entrada). */
    val inviteCode: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class WatchPartyViewModel @Inject constructor(
    private val store: WatchPartyStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        WatchPartyUiState(
            session = store.sessionFlow.value,
            localName = store.localName,
            members = store.members(),
        ),
    )
    val ui: StateFlow<WatchPartyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.sessionFlow.collect { session ->
                _ui.update { it.copy(session = session, members = store.members()) }
            }
        }
    }

    /** Estado da sincronização — sempre INDISPONÍVEL (sem relay, ver WatchParty.kt). */
    val syncState: PartySyncState get() = store.syncState

    /** Motivo exato da sincronização estar indisponível (mostrado na tela). */
    val syncUnavailableReason: String get() = PARTY_SYNC_UNAVAILABLE_REASON

    /** Link de convite da sessão atual ("" sem sessão). */
    fun inviteLink(): String = _ui.value.session?.let { WatchParty.buildInviteLink(it.code) }.orEmpty()

    /** Texto do convite da sessão atual ("" sem sessão). */
    fun inviteText(): String = _ui.value.session?.let { WatchParty.buildInviteText(it.code) }.orEmpty()

    /** Cria uma sessão nova como anfitrião (gera o código). */
    fun createSession() {
        val created = store.createSession()
        _ui.update {
            it.copy(
                message = "Sessão criada. Compartilhe o código ${created.code} com quem vai assistir junto.",
                error = null,
            )
        }
    }

    /** Entra numa sessão com o código digitado/colado. */
    fun joinSession(rawCode: String) {
        val joined = store.joinSession(rawCode)
        if (joined == null) {
            _ui.update {
                it.copy(
                    error = "Código inválido: esperado 6 caracteres (letras e números, sem 0/O/1/I/L).",
                    message = null,
                )
            }
            return
        }
        _ui.update {
            it.copy(
                inviteCode = null,
                message = "Você entrou na sessão ${joined.code}. A sincronização ainda não existe — cada aparelho toca por conta própria.",
                error = null,
            )
        }
    }

    /** Sai da sessão atual (apaga o código local). */
    fun leaveSession() {
        store.leaveSession()
        _ui.update { it.copy(message = "Você saiu da sessão.", error = null) }
    }

    /**
     * Código que chegou por deep link (`aotv://party/?code=`). Pré-preenche
     * a entrada; entrar continua sendo um toque do usuário — o link sozinho
     * nunca entra na sessão.
     */
    fun onInviteReceived(code: String) {
        if (_ui.value.session != null) return
        _ui.update {
            it.copy(
                inviteCode = code,
                message = "Convite recebido (código $code). Toque em Entrar para participar.",
                error = null,
            )
        }
    }

    /** Limpa a mensagem/erro da tela. */
    fun clearMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }
}
