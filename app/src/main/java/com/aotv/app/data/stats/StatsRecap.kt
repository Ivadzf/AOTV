// ─── data/stats/StatsRecap.kt (Lote 23, v1.33.0) ────────────────────────────
// Estatísticas de consumo + recap: agregação PURA sobre o histórico que já
// existe (MovieHistoryDao.statsRows + WatchedEpisodeDao). Nada aqui toca em
// rede, banco ou prefs — tudo entra por parâmetro, p/ ser testável sem
// Android. Só no aparelho: nenhum dado sai daqui.
//
// Definições (documentadas porque a tela e o recap dependem delas):
// - "Hora assistida" de uma linha = min(positionMs, durationMs); sem
//   durationMs (> 0), vale positionMs. positionMs negativo vira 0.
// - "Título" = uma linha do histórico (um título assistido num dia conta 1
//   nesse dia, mesmo revisto no mesmo dia — o histórico guarda só a última
//   posição por título).
// - Heatmap = nº de títulos por dia nas últimas 12 semanas (84 dias,
//   terminando hoje), independente do período selecionado.
// - Sequência (streak) = dias seguidos com pelo menos um título assistido,
//   contando p/ trás a partir de hoje; se hoje ainda não tem nada, vale a
//   partir de ontem (a sequência de ontem continua viva hoje).
// - Semana passada = os 7 dias anteriores aos últimos 7 dias; sem horas na
//   semana anterior, não há base de comparação (null, não 0%).

package com.aotv.app.data.stats

import com.aotv.app.data.local.dao.HistoryStatsRow
import com.aotv.app.data.local.entity.WatchedEpisodeEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Períodos da tela de Estatísticas (o mesmo valor de `prefs.statsPeriod`). */
fun statsPeriodDays(period: String): Int? = when (period) {
    "7d" -> 7
    "30d" -> 30
    "90d" -> 90
    "all" -> null
    else -> 30
}

/** Rótulo do período p/ a tela e o recap. */
fun statsPeriodLabel(period: String): String = when (period) {
    "7d" -> "Últimos 7 dias"
    "30d" -> "Últimos 30 dias"
    "90d" -> "Últimos 90 dias"
    "all" -> "Todo o histórico"
    else -> "Últimos 30 dias"
}

/** Nome do dia da semana em português. */
fun weekdayNamePt(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Segunda-feira"
    DayOfWeek.TUESDAY -> "Terça-feira"
    DayOfWeek.WEDNESDAY -> "Quarta-feira"
    DayOfWeek.THURSDAY -> "Quinta-feira"
    DayOfWeek.FRIDAY -> "Sexta-feira"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

/** Resumo agregado de um período. `hasData` false = período sem histórico. */
data class StatsSummary(
    val hoursWatched: Double,
    val titlesWatched: Int,
    val episodesWatched: Int,
    val daysActive: Int,
    val streakDays: Int,
    val bestWeekday: DayOfWeek?,
    val weekOverWeekPct: Double?,
    val heatmap: Map<LocalDate, Int>,
    val hasData: Boolean,
)

/** Milissegundos assistidos de uma linha (ver o cabeçalho do arquivo). */
fun watchedMsOf(row: HistoryStatsRow): Long {
    val position = row.positionMs.coerceAtLeast(0L)
    return if (row.durationMs > 0L) minOf(position, row.durationMs) else position
}

/** Início do dia (00:00 local) em epoch ms. */
private fun LocalDate.startOfDayMs(zone: ZoneId): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Agrega o histórico do perfil num [StatsSummary] p/ o período pedido.
 * `rows` = statsRows do perfil; `episodes` = episódios vistos do perfil.
 * `nowMs`/`zone` são parâmetros p/ o cálculo ser testável sem relógio.
 */
fun computeStats(
    rows: List<HistoryStatsRow>,
    episodes: List<WatchedEpisodeEntity>,
    period: String,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): StatsSummary {
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
    val days = statsPeriodDays(period)
    val periodStartMs = if (days == null) Long.MIN_VALUE
    else today.minusDays((days - 1).toLong()).startOfDayMs(zone)
    val inPeriod = rows.filter { it.lastWatchedAt >= periodStartMs && it.lastWatchedAt <= nowMs }
    val episodesInPeriod = episodes.count { it.watchedAt >= periodStartMs && it.watchedAt <= nowMs }
    val hours = inPeriod.sumOf { watchedMsOf(it) } / 3_600_000.0
    val activeDates = inPeriod.map {
        LocalDate.ofInstant(Instant.ofEpochMilli(it.lastWatchedAt), zone)
    }.toSet()
    val bestWeekday = inPeriod
        .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.lastWatchedAt), zone).dayOfWeek }
        .mapValues { entry -> entry.value.sumOf { watchedMsOf(it) } }
        .maxByOrNull { it.value }?.key
    // Semana passada: últimos 7 dias vs. os 7 anteriores (sobre TODAS as
    // linhas, não só as do período — p/ "7d" a semana anterior está fora).
    val thisWeekStartMs = today.minusDays(6).startOfDayMs(zone)
    val prevWeekStartMs = today.minusDays(13).startOfDayMs(zone)
    val thisWeekMs = rows.filter { it.lastWatchedAt in thisWeekStartMs..nowMs }.sumOf { watchedMsOf(it) }
    val prevWeekMs = rows.filter { it.lastWatchedAt in prevWeekStartMs until thisWeekStartMs }.sumOf { watchedMsOf(it) }
    val weekOverWeekPct = if (prevWeekMs <= 0L) null
    else (thisWeekMs - prevWeekMs).toDouble() / prevWeekMs.toDouble() * 100.0
    // Heatmap: 84 dias terminando hoje (12 semanas), com zero explícito.
    val heatmap = buildMap<LocalDate, Int> {
        for (i in 83 downTo 0) put(today.minusDays(i.toLong()), 0)
        rows.forEach { row ->
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(row.lastWatchedAt), zone)
            if (containsKey(date)) put(date, getValue(date) + 1)
        }
    }
    // Sequência: p/ trás a partir de hoje (ou de ontem, se hoje está vazio).
    val allActive = rows.map {
        LocalDate.ofInstant(Instant.ofEpochMilli(it.lastWatchedAt), zone)
    }.toSet()
    var streak = 0
    var cursor = if (today in allActive) today else today.minusDays(1)
    if (cursor in allActive) {
        while (cursor in allActive) {
            streak++
            cursor = cursor.minusDays(1)
        }
    }
    return StatsSummary(
        hoursWatched = hours,
        titlesWatched = inPeriod.size,
        episodesWatched = episodesInPeriod,
        daysActive = activeDates.size,
        streakDays = streak,
        bestWeekday = bestWeekday,
        weekOverWeekPct = weekOverWeekPct,
        heatmap = heatmap,
        hasData = inPeriod.isNotEmpty() || episodesInPeriod > 0,
    )
}

/**
 * Grade do heatmap p/ desenhar: 12 colunas (semanas, da mais antiga p/ a
 * mais recente), cada uma com 7 contagens (segunda a domingo). Dias fora da
 * janela de 84 dias (o começo da primeira semana) contam 0.
 */
fun heatmapGrid(heatmap: Map<LocalDate, Int>, today: LocalDate): List<List<Int>> {
    val firstDay = today.minusDays(83)
    // Segunda-feira da semana do primeiro dia (pode cair antes da janela).
    val monday = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    return (0 until 12).map { week ->
        (0 until 7).map { day ->
            val date = monday.plusDays((week * 7 + day).toLong())
            heatmap[date] ?: 0
        }
    }
}

/** "12,5 h" ou "45 min" quando menos de uma hora. */
fun formatHours(hours: Double): String {
    if (hours < 1.0) return "${(hours * 60.0).roundToInt()} min"
    return String.format(Locale("pt", "BR"), "%.1f h", hours)
}

/** "+12%" / "-8%" / null quando não há semana anterior p/ comparar. */
fun formatWeekOverWeek(pct: Double?): String? {
    if (pct == null) return null
    val sign = if (pct >= 0.0) "+" else ""
    return "$sign${pct.roundToInt()}% vs. semana passada"
}
