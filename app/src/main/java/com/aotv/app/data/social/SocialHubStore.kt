package com.aotv.app.data.social

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Social Hub (Lote 19, v1.29.0, item 2) — estado local.
 *
 * Amigos, "quer assistir" e film nights vivem em 3 prefs JSON por perfil
 * (`social_friends_v1`, `social_wants_v1`, `social_film_nights_v1`, em
 * [PlatformPrefs.SCOPED_KEYS] — viajam no backup do perfil, sem segredo:
 * só nomes e títulos, nenhuma credencial). Tudo é local e manual: sem
 * servidor não há descoberta de amigos nem atividade ao vivo (ver
 * [SOCIAL_HUB_NO_SERVER_NOTE]).
 *
 * A sessão da Watch Party NÃO vive aqui: é do
 * [com.aotv.app.data.party.WatchPartyStore] (item 1 do Lote 19). Este
 * store só guarda o `partyCode` na film night que originou a sessão.
 */
@Singleton
class SocialHubStore @Inject constructor(
    private val prefs: PlatformPrefs,
) {
    private fun readFriends(): List<SocialFriend> =
        parseSocialList(prefs.socialFriendsJson) { SocialFriend.fromJson(it) }

    private fun readWants(): List<FriendWant> =
        parseSocialList(prefs.socialWantsJson) { FriendWant.fromJson(it) }

    private fun readFilmNights(): List<FilmNight> =
        parseSocialList(prefs.socialFilmNightsJson) { FilmNight.fromJson(it) }

    private val _friends = MutableStateFlow(readFriends())
    /** Amigos locais (só nomes), na ordem em que foram adicionados. */
    val friendsFlow: StateFlow<List<SocialFriend>> = _friends.asStateFlow()
    val friends: List<SocialFriend> get() = _friends.value

    private val _wants = MutableStateFlow(readWants())
    /** Títulos que os amigos querem assistir (anotados à mão). */
    val wantsFlow: StateFlow<List<FriendWant>> = _wants.asStateFlow()
    val wants: List<FriendWant> get() = _wants.value

    private val _filmNights = MutableStateFlow(readFilmNights())
    /** Film nights agendadas (todas, passadas e futuras). */
    val filmNightsFlow: StateFlow<List<FilmNight>> = _filmNights.asStateFlow()
    val filmNights: List<FilmNight> get() = _filmNights.value

    /** Nome de quem está neste aparelho (o nome do perfil ativo). */
    val localName: String get() = prefs.profile.name.trim().ifBlank { "Você" }

    /** Couch matches atuais (títulos queridos por 2+ amigos). */
    fun couchMatches(): List<CouchMatch> = findCouchMatches(_wants.value, _friends.value)

    /** Film nights futuras, da mais próxima p/ a mais distante. */
    fun upcomingFilmNights(nowMs: Long): List<FilmNight> =
        _filmNights.value.filter { it.status(nowMs) != FilmNightStatus.PAST }
            .sortedBy { it.startsAtMs }

    /** Film nights passadas, da mais recente p/ a mais antiga. */
    fun pastFilmNights(nowMs: Long): List<FilmNight> =
        _filmNights.value.filter { it.status(nowMs) == FilmNightStatus.PAST }
            .sortedByDescending { it.startsAtMs }

    /** Adiciona um amigo. null quando o nome é inválido ou o limite estourou. */
    fun addFriend(rawName: String): SocialFriend? {
        val name = rawName.trim()
        if (!isValidFriendName(name)) return null
        if (_friends.value.size >= SOCIAL_MAX_FRIENDS) return null
        if (_friends.value.any { it.name.equals(name, ignoreCase = true) }) return null
        val friend = SocialFriend(newSocialId(), name, System.currentTimeMillis())
        saveFriends(_friends.value + friend)
        return friend
    }

    /** Renomeia um amigo. false quando o nome é inválido, repetido ou o id não existe. */
    fun renameFriend(id: String, rawName: String): Boolean {
        val name = rawName.trim()
        if (!isValidFriendName(name)) return false
        if (_friends.value.any { it.id != id && it.name.equals(name, ignoreCase = true) }) return false
        val current = _friends.value
        if (current.none { it.id == id }) return false
        saveFriends(current.map { if (it.id == id) it.copy(name = name) else it })
        return true
    }

    /**
     * Remove um amigo. Em cascata: apaga os "quer assistir" dele e tira o
     * id dele das film nights (a film night sem amigos continua existindo,
     * como lembrete com título e data).
     */
    fun removeFriend(id: String) {
        if (_friends.value.none { it.id == id }) return
        saveFriends(_friends.value.filter { it.id != id })
        saveWants(_wants.value.filter { it.friendId != id })
        saveFilmNights(_filmNights.value.map { it.copy(friendIds = it.friendIds - id) })
    }

    /** Anota que um amigo quer assistir um título. null quando inválido ou o limite estourou. */
    fun addWant(friendId: String, rawTitle: String, tmdbId: Long = 0L): FriendWant? {
        val title = rawTitle.trim()
        if (!isValidSocialTitle(title)) return null
        if (_friends.value.none { it.id == friendId }) return null
        if (_wants.value.count { it.friendId == friendId } >= SOCIAL_MAX_WANTS_PER_FRIEND) return null
        if (_wants.value.any { it.friendId == friendId && normalizeTitleForMatch(it.title) == normalizeTitleForMatch(title) }) return null
        val want = FriendWant(newSocialId(), friendId, title, tmdbId, System.currentTimeMillis())
        saveWants(_wants.value + want)
        return want
    }

    /** Apaga um "quer assistir". */
    fun removeWant(id: String) {
        if (_wants.value.none { it.id == id }) return
        saveWants(_wants.value.filter { it.id != id })
    }

    /**
     * Agenda uma film night. null quando o título é inválido, a data não é
     * futura ou o limite estourou. Amigos desconhecidos são ignorados (a
     * film night fica com quem existe — nunca com id fantasma).
     */
    fun createFilmNight(rawTitle: String, startsAtMs: Long, friendIds: List<String>, tmdbId: Long = 0L): FilmNight? {
        val title = rawTitle.trim()
        if (!isValidSocialTitle(title)) return null
        if (startsAtMs <= System.currentTimeMillis()) return null
        if (_filmNights.value.size >= SOCIAL_MAX_FILM_NIGHTS) return null
        val known = friendIds.filter { id -> _friends.value.any { it.id == id } }.distinct()
        val night = FilmNight(newSocialId(), title, tmdbId, startsAtMs, known, "", System.currentTimeMillis())
        saveFilmNights(_filmNights.value + night)
        return night
    }

    /** Cancela (apaga) uma film night. */
    fun cancelFilmNight(id: String) {
        if (_filmNights.value.none { it.id == id }) return
        saveFilmNights(_filmNights.value.filter { it.id != id })
    }

    /** Guarda o código da Watch Party criada a partir desta film night. */
    fun attachPartyCode(id: String, code: String) {
        val current = _filmNights.value
        if (current.none { it.id == id }) return
        saveFilmNights(current.map { if (it.id == id) it.copy(partyCode = code) else it })
    }

    /** Recarrega das prefs (troca de perfil). */
    fun refresh() {
        _friends.value = readFriends()
        _wants.value = readWants()
        _filmNights.value = readFilmNights()
    }

    private fun saveFriends(value: List<SocialFriend>) {
        prefs.socialFriendsJson = writeSocialList(value) { it.toJson() }
        _friends.value = value
    }

    private fun saveWants(value: List<FriendWant>) {
        prefs.socialWantsJson = writeSocialList(value) { it.toJson() }
        _wants.value = value
    }

    private fun saveFilmNights(value: List<FilmNight>) {
        prefs.socialFilmNightsJson = writeSocialList(value) { it.toJson() }
        _filmNights.value = value
    }
}
