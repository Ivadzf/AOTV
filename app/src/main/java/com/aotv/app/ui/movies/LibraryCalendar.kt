package com.aotv.app.ui.movies

import com.aotv.app.data.local.dayString
import com.aotv.app.data.remote.dto.TmdbSeriesDetailDto

/**
 * Calendário da Biblioteca (Lote 2, item 6 — v1.10.0).
 *
 * CONJUNTO (decisão documentada): SÓ as séries que o perfil acompanha —
 * as da Minha Lista + as do Continuar assistindo (o mesmo conjunto da
 * fileira "Novos episódios" da Início), sem duplicar e com teto de
 * [CALENDAR_MAX_SERIES] séries. NÃO busca air dates do catálogo inteiro:
 * série fora da lista não aparece, mesmo que esteja estreando.
 *
 * CONTEÚDO por série (no máximo 2 linhas):
 * - o PRÓXIMO episódio a ir ao ar (`next_episode_to_air`), sem limite de
 *   distância — estreia daqui a 3 meses aparece, ordenada por data;
 * - o ÚLTIMO episódio a ir ao ar (`last_episode_to_air`), SÓ quando foi ao
 *   ar nos últimos [CALENDAR_LAST_AIRED_DAYS] dias E o perfil ainda não o
 *   marcou como visto (mesmo critério de "novo" da fileira da Início).
 * Sem `air_date` no TMDB, o episódio NÃO aparece — a data nunca é inventada.
 *
 * PARCIMÔNIA (como se evitou N chamadas ao TMDB):
 * - 1 chamada `tv/{id}` por série acompanhada, no máximo — e o detalhe vem
 *   do [com.aotv.app.data.remote.SeriesDetailCache], o mesmo cache em memória
 *   (TTL 6h) da fileira "Novos episódios": a série que a Início já buscou
 *   NÃO é buscada de novo ao abrir a Biblioteca;
 * - a MinhaListaViewModel só recarrega quando o conjunto de séries muda
 *   (ou no "Atualizar" da seção): abrir a Biblioteca 2x seguidas não refaz
 *   nada;
 * - sem chave TMDB, não chama a rede (a seção fica vazia).
 */

private const val DAY_MS = 86_400_000L

/** Teto de séries consultadas por carga do calendário. */
const val CALENDAR_MAX_SERIES = 30

/** O último episódio exibido só aparece se foi ao ar há no máximo 14 dias. */
const val CALENDAR_LAST_AIRED_DAYS = 14

/** Uma linha do calendário: um episódio com data de exibição do TMDB. */
data class CalendarEntry(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String,
    val season: Int,
    val episode: Int,
    /** Data de exibição (yyyy-MM-dd) como veio do TMDB. */
    val airDate: String,
    /** [airDate] em ms (meio-dia UTC, via [airDateMs]). */
    val atMs: Long,
    /** true = episódio que JÁ foi ao ar (o último); false = próximo a estrear. */
    val isLastAired: Boolean,
) {
    /** "T2 E5" p/ a linha. */
    val seLabel get() = "T$season E$episode"

    /** Já foi ao ar (data anterior à de hoje). Episódio de hoje NÃO conta como exibido. */
    fun isAired(todayMs: Long): Boolean = atMs < todayMs

    /** Rótulo curto da data p/ a pílula da linha. */
    fun dateLabel(todayMs: Long): String = when {
        atMs < todayMs -> if (atMs >= todayMs - DAY_MS) "Ontem" else "já foi ao ar"
        atMs == todayMs -> "Hoje"
        atMs == todayMs + DAY_MS -> "Amanhã"
        else -> "em ${((atMs - todayMs) / DAY_MS).toInt()} dias"
    }

    /** Linha de texto: "T2 E5 · estreia 17/11/2026" ou "T2 E4 · foi ao ar 10/11/2026". */
    fun describe(): String =
        "$seLabel · ${if (isLastAired) "foi ao ar" else "estreia"} ${formatBrDate(airDate)}"
}

/** "2026-11-17" → "17/11/2026". Sem traços, devolve como veio. */
internal fun formatBrDate(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

/** Estado da seção do calendário na Biblioteca. */
data class CalendarUiState(
    val loading: Boolean = true,
    val entries: List<CalendarEntry> = emptyList(),
    /** O perfil acompanha alguma série (false = seção nem aparece). */
    val hasTrackedSeries: Boolean = false,
)

/**
 * Linhas do calendário de UMA série a partir do detalhe do TMDB.
 * [lastSeen] = o perfil já marcou o último episódio como visto (então ele
 * não vira linha de "já foi ao ar"). [now] em ms.
 */
fun calendarEntriesOf(
    tmdbId: Int,
    title: String,
    posterUrl: String,
    detail: TmdbSeriesDetailDto,
    lastSeen: Boolean,
    now: Long,
): List<CalendarEntry> {
    val out = ArrayList<CalendarEntry>(2)
    // Próximo episódio: qualquer data futura (ou passada, quando o TMDB está
    // desatualizado — aí a linha sai com "já foi ao ar").
    detail.nextEpisodeToAir?.let { next ->
        val at = airDateMs(next.airDate)
        if (next.season > 0 && at != null) {
            out += CalendarEntry(tmdbId, title, posterUrl, next.season, next.episode, next.airDate!!, at, false)
        }
    }
    // Último episódio: só recente (14 dias) e ainda não visto.
    detail.lastEpisodeToAir?.let { last ->
        val at = airDateMs(last.airDate)
        if (last.season > 0 && at != null && now >= at && now - at <= CALENDAR_LAST_AIRED_DAYS * DAY_MS && !lastSeen) {
            out += CalendarEntry(tmdbId, title, posterUrl, last.season, last.episode, last.airDate!!, at, true)
        }
    }
    return out
}

/** Ordenação do calendário: por data (a mais próxima primeiro), desempate por título. */
fun sortCalendarEntries(list: List<CalendarEntry>): List<CalendarEntry> =
    list.sortedWith(compareBy<CalendarEntry> { it.atMs }.thenBy { it.title })

/** Início de hoje em ms (meio-dia UTC, mesma base do [airDateMs]). */
fun todayMs(): Long = airDateMs(dayString()) ?: System.currentTimeMillis()
