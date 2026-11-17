package com.aotv.app.data.social

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Social Hub (Lote 19, v1.29.0, item 2) — modelos e lógica pura.
 *
 * Referência: MKCinema (ver o que amigos assistem + 1 toque p/ assistir
 * junto; film night; couch match). Reimplementada, sem copiar código.
 *
 * ── O QUE "AMIGOS" SIGNIFICA SEM BACKEND ───────────────────────────────
 * O AOTV não tem servidor de amigos, conta na nuvem nem presença: não há
 * como saber o que um amigo está assistindo AGORA fora de uma sessão, nem
 * descobrir amigos, nem mandar nada p/ o aparelho dele. O que existe aqui
 * é honesto e local (tudo por perfil, nas prefs `social_*_v1`):
 *  - Amigos: lista local, adicionada manualmente pelo usuário (só um nome).
 *  - "Quer assistir": p/ cada amigo, o usuário anota os títulos que aquele
 *    amigo quer ver (digitado à mão — nada é lido do aparelho do amigo).
 *  - Couch match: títulos que 2 ou mais amigos da lista querem ver (ou o
 *    mesmo título anotado p/ 2 amigos). É o match que dá p/ calcular no
 *    aparelho, sem servidor — não é gosto medido, é lista manual.
 *  - Film night: agendamento local (título + data/hora + amigos). Na hora,
 *    1 toque cria a sessão da Watch Party com o código p/ compartilhar.
 *    Não há lembrete no aparelho do amigo: o convite vai pelo link/código,
 *    como na Watch Party (ver [com.aotv.app.data.party.WatchParty]).
 *  - "Ver o que amigos assistem": SÓ dentro de uma Watch Party ativa — a
 *    sessão tem o título da film night que a criou e o código; fora de
 *    sessão não há atividade visível (ver [SOCIAL_HUB_NO_SERVER_NOTE]).
 *  - "Assistir junto" (1 toque): cria a sessão via
 *    [com.aotv.app.data.party.WatchPartyStore.createSession] — a sessão é
 *    do bloco Watch Party (item 1 do Lote 19); este hub só chama e navega
 *    p/ a tela dele. Nada aqui reimplementa sessão, código ou convite.
 *
 * Nada é simulado: sem amigo na lista, sem match e sem film night, o hub
 * mostra as seções vazias e explica o que falta — nunca inventa amigo,
 * atividade ou match.
 */

/** Limites locais (evitam lista infinita digitada à mão). */
const val SOCIAL_MAX_FRIENDS = 30
const val SOCIAL_MAX_WANTS_PER_FRIEND = 50
const val SOCIAL_MAX_FILM_NIGHTS = 20
const val SOCIAL_FRIEND_NAME_MAX = 40
const val SOCIAL_TITLE_MAX = 120

/** Texto honesto mostrado no topo do hub (e na busca profunda). */
const val SOCIAL_HUB_NO_SERVER_NOTE =
    "Sem servidor não há como ver o que um amigo está assistindo agora: " +
        "amigos são uma lista local (só nomes), o \"quer assistir\" é anotado " +
        "à mão e a atividade só aparece dentro de uma Watch Party ativa. " +
        "O convite p/ assistir junto vai por link/código, como na Watch Party."

/** Um amigo da lista local: só um nome (sem conta, sem contato, sem foto). */
data class SocialFriend(
    val id: String,
    val name: String,
    val createdAtMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("createdAtMs", createdAtMs)

    companion object {
        fun fromJson(o: JSONObject?): SocialFriend? {
            if (o == null) return null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = o.optString("name").trim().takeIf { it.isNotBlank() } ?: return null
            return SocialFriend(id, name, o.optLong("createdAtMs", 0L))
        }
    }
}

/** Um título que um amigo quer assistir, anotado à mão pelo usuário. */
data class FriendWant(
    val id: String,
    val friendId: String,
    val title: String,
    /** TMDB id quando o título veio de um título conhecido (0 = só texto). */
    val tmdbId: Long = 0L,
    val addedAtMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("friendId", friendId)
        .put("title", title)
        .put("tmdbId", tmdbId)
        .put("addedAtMs", addedAtMs)

    companion object {
        fun fromJson(o: JSONObject?): FriendWant? {
            if (o == null) return null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val friendId = o.optString("friendId").takeIf { it.isNotBlank() } ?: return null
            val title = o.optString("title").trim().takeIf { it.isNotBlank() } ?: return null
            return FriendWant(id, friendId, title, o.optLong("tmdbId", 0L), o.optLong("addedAtMs", 0L))
        }
    }
}

/** Estado de uma film night em relação a [nowMs]. */
enum class FilmNightStatus { UPCOMING, TODAY, PAST }

/** Uma film night agendada localmente: título + quando + quem vai. */
data class FilmNight(
    val id: String,
    val title: String,
    val tmdbId: Long = 0L,
    val startsAtMs: Long,
    val friendIds: List<String> = emptyList(),
    /** Código da Watch Party criada a partir desta film night ("" = nenhuma). */
    val partyCode: String = "",
    val createdAtMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("tmdbId", tmdbId)
        .put("startsAtMs", startsAtMs)
        .put("friendIds", JSONArray(friendIds))
        .put("partyCode", partyCode)
        .put("createdAtMs", createdAtMs)

    /** Estado em relação a [nowMs] (p/ ordenar e p/ o rótulo da tela). */
    fun status(nowMs: Long): FilmNightStatus {
        if (startsAtMs < nowMs) return FilmNightStatus.PAST
        val day = SimpleDateFormat("yyyyMMdd", Locale("pt", "BR"))
        return if (day.format(Date(startsAtMs)) == day.format(Date(nowMs))) FilmNightStatus.TODAY
        else FilmNightStatus.UPCOMING
    }

    companion object {
        fun fromJson(o: JSONObject?): FilmNight? {
            if (o == null) return null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val title = o.optString("title").trim().takeIf { it.isNotBlank() } ?: return null
            val startsAtMs = o.optLong("startsAtMs", 0L).takeIf { it > 0L } ?: return null
            val friends = o.optJSONArray("friendIds")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()
            return FilmNight(
                id, title, o.optLong("tmdbId", 0L), startsAtMs, friends,
                o.optString("partyCode"), o.optLong("createdAtMs", 0L),
            )
        }
    }
}

/** Um couch match: título que 2+ amigos querem ver + quem são eles. */
data class CouchMatch(
    val title: String,
    val friendIds: List<String>,
    val friendNames: List<String>,
)

/** Gera um id local (nunca sai do aparelho). */
fun newSocialId(): String = UUID.randomUUID().toString()

/** Nome de amigo válido: não vazio, até [SOCIAL_FRIEND_NAME_MAX] caracteres. */
fun isValidFriendName(name: String): Boolean =
    name.trim().isNotBlank() && name.trim().length <= SOCIAL_FRIEND_NAME_MAX

/** Título válido: não vazio, até [SOCIAL_TITLE_MAX] caracteres. */
fun isValidSocialTitle(title: String): Boolean =
    title.trim().isNotBlank() && title.trim().length <= SOCIAL_TITLE_MAX

/**
 * Normaliza um título p/ comparar ("Duna: Parte 2" casa com "duna parte 2"):
 * minúsculas, sem acento, só letras/números/espaços, espaços colapsados.
 */
fun normalizeTitleForMatch(title: String): String {
    val noAccents = Normalizer.normalize(title.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noAccents.replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Couch match local: agrupa os "quer assistir" por título normalizado e
 * devolve os títulos queridos por 2 ou mais amigos distintos. O título
 * exibido é a grafia mais usada (desempate: a primeira anotada). Um amigo
 * com o mesmo título anotado 2x conta 1x. Sem 2 amigos no mesmo título,
 * a lista vem vazia — nunca inventa um match.
 */
fun findCouchMatches(wants: List<FriendWant>, friends: List<SocialFriend>): List<CouchMatch> {
    val namesById = friends.associate { it.id to it.name }
    val grouped = linkedMapOf<String, LinkedHashSet<String>>()
    val spellings = linkedMapOf<String, LinkedHashMap<String, Int>>()
    wants.forEach { want ->
        val key = normalizeTitleForMatch(want.title)
        if (key.isBlank()) return@forEach
        grouped.getOrPut(key) { linkedSetOf() }.add(want.friendId)
        val counts = spellings.getOrPut(key) { linkedMapOf() }
        counts[want.title.trim()] = (counts[want.title.trim()] ?: 0) + 1
    }
    return grouped.entries.mapNotNull { (key, friendIds) ->
        val ids = friendIds.filter { it in namesById }
        if (ids.size < 2) return@mapNotNull null
        val display = spellings[key]?.maxByOrNull { it.value }?.key ?: return@mapNotNull null
        CouchMatch(display, ids, ids.map { namesById.getValue(it) })
    }.sortedBy { it.title.lowercase() }
}

/** Rótulo de "quando" da film night ("Hoje, 20:00" / "Amanhã, 20:00" / "sex, 21/11 20:00"). */
fun formatFilmNightWhen(startsAtMs: Long, nowMs: Long): String {
    val br = Locale("pt", "BR")
    val day = SimpleDateFormat("yyyyMMdd", br)
    val time = SimpleDateFormat("HH:mm", br).format(Date(startsAtMs))
    val today = day.format(Date(nowMs))
    val tomorrow = day.format(Date(nowMs + 24L * 60L * 60L * 1000L))
    val whenDay = day.format(Date(startsAtMs))
    return when (whenDay) {
        today -> "Hoje, $time"
        tomorrow -> "Amanhã, $time"
        else -> SimpleDateFormat("EEE, dd/MM HH:mm", br).format(Date(startsAtMs))
    }
}

/** Lê uma lista JSON tolerando texto vazio/quebrado (volta vazia). */
fun <T> parseSocialList(raw: String, parse: (JSONObject?) -> T?): List<T> = runCatching {
    if (raw.isBlank()) return emptyList()
    val arr = JSONArray(raw)
    (0 until arr.length()).mapNotNull { parse(arr.optJSONObject(it)) }
}.getOrDefault(emptyList())

/** Escreve uma lista como JSON array. */
fun <T> writeSocialList(items: List<T>, toJson: (T) -> JSONObject): String {
    val arr = JSONArray()
    items.forEach { arr.put(toJson(it)) }
    return arr.toString()
}
