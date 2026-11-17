package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.party.PartyRuntime
import com.aotv.app.data.party.PartySession
import com.aotv.app.data.party.PartySyncState
import com.aotv.app.data.party.WatchPartyStore
import com.aotv.app.data.social.CouchMatch
import com.aotv.app.data.social.FilmNight
import com.aotv.app.data.social.FriendWant
import com.aotv.app.data.social.SocialFriend
import com.aotv.app.data.social.SocialHubStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado da tela "Social Hub" (Lote 19, v1.29.0, item 2). */
data class SocialHubUiState(
    val friends: List<SocialFriend> = emptyList(),
    val wants: List<FriendWant> = emptyList(),
    val filmNights: List<FilmNight> = emptyList(),
    val matches: List<CouchMatch> = emptyList(),
    /** Sessão ativa da Watch Party (null = sem sessão) — lida do bloco dela. */
    val session: PartySession? = null,
    /** Estado da sincronização ao vivo, lido do PartyRuntime (nunca fixo). */
    val partySyncState: PartySyncState = PartySyncState.IDLE,
    val localName: String = "",
    val message: String? = null,
    val error: String? = null,
    /** Código da sessão criada por "Assistir junto" (a tela navega 1x e consome). */
    val partyNavigationCode: String? = null,
)

@HiltViewModel
class SocialHubViewModel @Inject constructor(
    private val store: SocialHubStore,
    private val partyStore: WatchPartyStore,
    private val partyRuntime: PartyRuntime,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        SocialHubUiState(
            friends = store.friends,
            wants = store.wants,
            filmNights = store.filmNights,
            matches = store.couchMatches(),
            session = partyStore.sessionFlow.value,
            partySyncState = partyRuntime.connectionState.value,
            localName = store.localName,
        ),
    )
    val ui: StateFlow<SocialHubUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.friendsFlow.collect {
                _ui.update { state -> state.copy(friends = it, matches = store.couchMatches()) }
            }
        }
        viewModelScope.launch {
            store.wantsFlow.collect {
                _ui.update { state -> state.copy(wants = it, matches = store.couchMatches()) }
            }
        }
        viewModelScope.launch {
            store.filmNightsFlow.collect {
                _ui.update { state -> state.copy(filmNights = it) }
            }
        }
        viewModelScope.launch {
            partyStore.sessionFlow.collect { session ->
                _ui.update { state -> state.copy(session = session) }
            }
        }
        viewModelScope.launch {
            partyRuntime.connectionState.collect { syncState ->
                _ui.update { state -> state.copy(partySyncState = syncState) }
            }
        }
    }

    /** Adiciona um amigo pelo nome digitado. */
    fun addFriend(name: String) {
        val added = store.addFriend(name)
        if (added == null) {
            _ui.update {
                it.copy(
                    error = "Não deu p/ adicionar: nome vazio, repetido, com mais de 40 caracteres ou limite de 30 amigos.",
                    message = null,
                )
            }
            return
        }
        _ui.update { it.copy(message = "${added.name} entrou na sua lista de amigos.", error = null) }
    }

    /** Remove um amigo (apaga os "quer assistir" dele e tira ele das film nights). */
    fun removeFriend(id: String) {
        val name = _ui.value.friends.firstOrNull { it.id == id }?.name ?: return
        store.removeFriend(id)
        _ui.update { it.copy(message = "$name saiu da sua lista de amigos.", error = null) }
    }

    /** Anota que um amigo quer assistir um título. */
    fun addWant(friendId: String, title: String) {
        val added = store.addWant(friendId, title)
        if (added == null) {
            _ui.update {
                it.copy(
                    error = "Não deu p/ anotar: título vazio, repetido p/ esse amigo, com mais de 120 caracteres ou limite de 50 por amigo.",
                    message = null,
                )
            }
            return
        }
        _ui.update { it.copy(message = "Anotado: ${added.title}.", error = null) }
    }

    /** Apaga um "quer assistir". */
    fun removeWant(id: String) {
        store.removeWant(id)
        _ui.update { it.copy(message = null, error = null) }
    }

    /** Agenda uma film night (título + quando + amigos). */
    fun createFilmNight(title: String, startsAtMs: Long, friendIds: List<String>) {
        val created = store.createFilmNight(title, startsAtMs, friendIds)
        if (created == null) {
            _ui.update {
                it.copy(
                    error = "Não deu p/ agendar: título vazio, data no passado ou limite de 20 film nights.",
                    message = null,
                )
            }
            return
        }
        _ui.update { it.copy(message = "Film night agendada: ${created.title}.", error = null) }
    }

    /** Cancela uma film night. */
    fun cancelFilmNight(id: String) {
        store.cancelFilmNight(id)
        _ui.update { it.copy(message = "Film night cancelada.", error = null) }
    }

    /**
     * "Assistir junto" em 1 toque (a partir de um couch match ou de uma
     * film night).
     *
     * GANCHO p/ o bloco Watch Party (item 1 do Lote 19): a sessão é criada
     * por [WatchPartyStore.createSession] — a criação, o código e o convite
     * são dele, este hub só chama e pede p/ a tela abrir a Watch Party. Se
     * o item 1 mudar a assinatura de `createSession`, este é o único ponto
     * a atualizar aqui. A sincronização em tempo real continua sendo do
     * bloco dele (sem relay, indisponível — a tela da Watch Party diz isso).
     */
    fun watchTogether(title: String, filmNightId: String? = null) {
        val created = partyStore.createSession()
        if (filmNightId != null) store.attachPartyCode(filmNightId, created.code)
        _ui.update {
            it.copy(
                partyNavigationCode = created.code,
                message = "Sessão criada (código ${created.code}). Compartilhe o convite na Watch Party.",
                error = null,
            )
        }
    }

    /** A tela consumiu o pedido de navegação p/ a Watch Party (não re-navega ao remontar). */
    fun consumePartyNavigation() {
        _ui.update { it.copy(partyNavigationCode = null) }
    }

    /** Limpa a mensagem/erro da tela. */
    fun clearMessage() {
        _ui.update { it.copy(message = null, error = null) }
    }
}
