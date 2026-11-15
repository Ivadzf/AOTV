package com.aotv.app.data.sports

import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram

/**
 * Esportes (Lote 8, v1.18.0) — funções puras, sem rede e sem API de esportes.
 *
 * REGRA DE DADO HONESTO (central aqui): NÃO existe API de esportes no app e
 * NENHUM placar é inventado. O placar só existe quando o texto do EPG
 * (título ou descrição do programa) literalmente contém um placar, no
 * formato "Time A 2 x 1 Time B" — [extractScore] procura esse padrão no
 * texto. Sem placar no texto, NÃO há placar na tela: o jogo aparece sem
 * resultado, nunca com um número chutado.
 *
 * Canal de esporte = grupo ou nome do canal indica esporte, ou o programa
 * atual/futuro dele é de esporte ([isSportsProgram]). Sem EPG, só o
 * grupo/nome decide; sem nenhum dos dois, não é esporte.
 */

/** Um jogo de esporte vindo do EPG. [score] só existe quando o texto do EPG traz o placar. */
data class SportsGame(
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val channelLogo: String = "",
    val channelHeaders: Map<String, String> = emptyMap(),
    val title: String,
    val desc: String = "",
    val startMs: Long,
    val stopMs: Long,
    /** Placar extraído do texto do EPG ("2 x 1"), null quando o texto não traz placar. */
    val score: String? = null,
) {
    /** O jogo está passando agora. */
    fun isNow(now: Long) = now in startMs until stopMs
}

/** Palavras que indicam esporte, em grupo/nome de canal ou título/descrição de programa. */
private val SPORT_WORDS = listOf(
    "esport", "sport", "futebol", "football", "soccer", "futsal",
    "basquete", "basket", "vôlei", "volei", "volley",
    "tênis", "tenis", "tennis", "fórmula", "formula", "f1", "nascar",
    "nfl", "nba", "mlb", "nhl", "mma", "ufc", "boxe", "luta",
    "golfe", "golf", "beisebol", "baseball", "hóquei", "hockey",
    "ciclismo", "corrida", "racing", "atletismo", "olimp",
    "campeonato", "copa", "liga", "league", "champions",
    "jogo", "partida", "match", "ao vivo", "transmissão",
)

/** "2 x 1" no texto (com ou sem espaços, x ou ×). Só o padrão com x: "2-1" seria ambíguo com datas. */
private val SCORE_REGEX = Regex("""\b(\d{1,2})\s*[xX×]\s*(\d{1,2})\b""")

/** O grupo ou o nome do canal indica esporte. */
fun isSportsChannel(channel: LiveChannel): Boolean {
    val hay = (channel.group + " " + channel.name).lowercase()
    return SPORT_WORDS.any { it in hay }
}

/** O programa é de esporte (título/descrição) ou já traz um placar no texto. */
fun isSportsProgram(program: LiveProgram): Boolean {
    val hay = (program.title + " " + program.desc).lowercase()
    return SPORT_WORDS.any { it in hay } || extractScore(program.title + " " + program.desc) != null
}

/**
 * Extrai o placar do texto do EPG. "Time A 2 x 1 Time B" → "2 x 1".
 * null quando o texto não traz placar — e aí NÃO há placar na tela.
 */
fun extractScore(text: String): String? {
    val m = SCORE_REGEX.find(text) ?: return null
    return "${m.groupValues[1]} x ${m.groupValues[2]}"
}

/**
 * O placar a mostrar num programa, respeitando as prefs do perfil.
 * null quando: placares desligados ([hideScores]), o atraso anti-spoiler
 * ainda não passou ([delaySec] desde o início do programa), ou o texto
 * do EPG não traz placar nenhum.
 */
fun visibleScore(program: LiveProgram, now: Long, hideScores: Boolean, delaySec: Int): String? {
    if (hideScores) return null
    if (now < program.start + delaySec * 1000L) return null
    return extractScore(program.title + " " + program.desc)
}

/**
 * Canais com programa de esporte passando AGORA (p/ a seção de esportes do
 * Ao vivo). Canal de esporte sem programa de esporte no ar também entra,
 * com o programa atual (ou null) — o que decide é o canal OU o programa.
 */
fun liveSportsNow(
    channels: List<LiveChannel>,
    epg: Map<String, List<LiveProgram>>,
    now: Long,
): List<Pair<LiveChannel, LiveProgram?>> {
    val out = ArrayList<Pair<LiveChannel, LiveProgram?>>()
    channels.forEach { c ->
        val current = epg[c.id].orEmpty().firstOrNull { it.isNow(now) }
        if (isSportsChannel(c) || (current != null && isSportsProgram(current))) {
            out += c to current
        }
    }
    return out
}

/**
 * Próximos jogos de esporte do EPG (p/ a fileira "Próximos" da Início):
 * programas de esporte que começam depois de [now] OU já estão no ar,
 * ordenados pelo início. Sem EPG não há jogo — a lista volta vazia.
 */
fun upcomingSportsGames(
    channels: List<LiveChannel>,
    epg: Map<String, List<LiveProgram>>,
    now: Long,
    limit: Int = 10,
): List<SportsGame> {
    val out = ArrayList<SportsGame>()
    channels.forEach { c ->
        val programs = epg[c.id].orEmpty()
        programs.forEach { p ->
            if (p.stop <= now) return@forEach
            if (!isSportsChannel(c) && !isSportsProgram(p)) return@forEach
            out += SportsGame(
                channelId = c.id, channelName = c.name, channelUrl = c.url,
                channelLogo = c.logo, channelHeaders = c.headers,
                title = p.title, desc = p.desc, startMs = p.start, stopMs = p.stop,
                score = extractScore(p.title + " " + p.desc),
            )
        }
    }
    return out.sortedWith(compareBy<SportsGame> { it.startMs }.thenBy { it.channelName }).take(limit)
}
