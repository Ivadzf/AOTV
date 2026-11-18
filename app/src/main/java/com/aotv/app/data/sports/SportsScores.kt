package com.aotv.app.data.sports

import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram
import java.util.Collections

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
    /** Classificação do dicionário local (Lote 20, v1.30.0): esporte,
     * competição e times — "" quando não classificado. */
    val sport: String = "",
    val competition: String = "",
    val homeTeam: String = "",
    val awayTeam: String = "",
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
    return SPORT_WORDS.any { it in hay } || isSportsChannelName(channel.name)
}

/** O programa é de esporte (título/descrição) ou já traz um placar no texto.
 * Lote 20 (v1.30.0): o dicionário local também classifica — "Flamengo x
 * Palmeiras" é esporte mesmo sem nenhuma palavra da lista.
 * Lote 21 (v1.31.0): a classificação usa o cache ([classifySportsProgramCached])
 * — o mesmo programa não é varrido de novo a cada recomposição/emissão. */
fun isSportsProgram(program: LiveProgram): Boolean {
    val hay = (program.title + " " + program.desc).lowercase()
    if (SPORT_WORDS.any { it in hay }) return true
    if (extractScore(program.title + " " + program.desc) != null) return true
    return classifySportsProgramCached(program.title, program.desc) != null
}

/**
 * Cache da classificação do dicionário (Lote 21, v1.31.0 — fix do
 * travamento da v1.30.0): classificar um programa varre 607 times; sem
 * cache, cada recomposição da aba Ao vivo e cada emissão do estado do Ao
 * vivo na Início revarria TODO o EPG na thread principal (ANR). A chave é
 * título + descrição (é só disso que a classificação depende); LRU de 512
 * entradas, sincronizado (a classificação roda em Dispatchers.Default e
 * pode ser chamada de várias corrotinas).
 */
private val classificationCache: MutableMap<String, SportsClassification?> =
    Collections.synchronizedMap(
        object : LinkedHashMap<String, SportsClassification?>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SportsClassification?>): Boolean =
                size > 512
        }
    )

/** [classifySportsProgram] com cache por programa (ver [classificationCache]). */
fun classifySportsProgramCached(title: String, desc: String = ""): SportsClassification? {
    val key = "$title\u0000$desc"
    val cached = classificationCache[key]
    if (cached != null || classificationCache.containsKey(key)) return cached
    val found = classifySportsProgram(title, desc)
    classificationCache[key] = found
    return found
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
 *
 * Lote 21 (v1.31.0) — fix do travamento da v1.30.0: (a) UMA só varredura
 * do dicionário por programa (antes, [isSportsProgram] varria e depois
 * [classifySportsProgram] varria DE NOVO p/ o mesmo programa); (b) janela
 * de 24 h — programa que só começa depois de amanhã não é "próximo" e nem
 * entra na varredura; (c) classificação com cache
 * ([classifySportsProgramCached]). O chamador ainda precisa rodar fora da
 * thread principal (ver MoviesHomeViewModel) — aqui só fica barato.
 */
fun upcomingSportsGames(
    channels: List<LiveChannel>,
    epg: Map<String, List<LiveProgram>>,
    now: Long,
    limit: Int = 10,
): List<SportsGame> {
    val horizonMs = now + 24 * 60 * 60 * 1000L
    val out = ArrayList<SportsGame>()
    channels.forEach { c ->
        val isChannel = isSportsChannel(c)
        val programs = epg[c.id].orEmpty()
        programs.forEach { p ->
            if (p.stop <= now || p.start >= horizonMs) return@forEach
            val text = p.title + " " + p.desc
            val hay = text.lowercase()
            // Sinal barato: canal de esporte, palavra de esporte ou placar
            // no texto. Sem ele, o dicionário só vale a pena quando o texto
            // tem cara de confronto ("Time x Time", "vs") — programa com só
            // o nome de um time, sem adversário, competição nem palavra de
            // esporte, não é classificado (ambíguo: pode ser documentário).
            val cheapHit = isChannel || SPORT_WORDS.any { it in hay } || extractScore(text) != null
            val needsScan = cheapHit || " x " in hay || " vs " in hay || "×" in hay
            // UMA só classificação por programa (com cache): decide se é
            // esporte E preenche esporte/competição/times do jogo.
            val classification = if (needsScan) classifySportsProgramCached(p.title, p.desc) else null
            if (!cheapHit && classification == null) return@forEach
            out += SportsGame(
                channelId = c.id, channelName = c.name, channelUrl = c.url,
                channelLogo = c.logo, channelHeaders = c.headers,
                title = p.title, desc = p.desc, startMs = p.start, stopMs = p.stop,
                score = extractScore(text),
                sport = classification?.sport.orEmpty(),
                competition = classification?.competition.orEmpty(),
                homeTeam = classification?.homeTeam.orEmpty(),
                awayTeam = classification?.awayTeam.orEmpty(),
            )
        }
    }
    return out.sortedWith(compareBy<SportsGame> { it.startMs }.thenBy { it.channelName }).take(limit)
}
