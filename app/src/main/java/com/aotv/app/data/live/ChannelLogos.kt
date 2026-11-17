package com.aotv.app.data.live

import java.text.Normalizer
import java.util.Locale

/**
 * Logos de canais do repositório público tv-logo/tv-logos (Lote 20,
 * v1.30.0) — sem hospedar nada: a URL é do raw.githubusercontent e o Coil
 * carrega com cache.
 *
 * Formato conferido ao vivo (2026-11-17, via API do GitHub + raw):
 * `countries/brazil/<slug>-br.png` — 121 arquivos, ex.: `globo-br.png`,
 * `sportv-br.png`, `sportv2-br.png`, `espn-4-br.png`,
 * `tele-cine-premium-br.png`, `premiere-br.png`. Arquivo existente
 * devolve HTTP 200 image/png; inexistente, 404.
 *
 * REGRA: só preenche o logo quando a lista/canal não traz nenhum — nunca
 * sobrescreve o logo da lista nem o logo manual (ver
 * [LiveTvRepository.displayChannel]). Um slug sem arquivo correspondente
 * (ex.: "ESPN" — o repo só tem espn-4/5/extra) devolve a URL mesmo assim:
 * o Coil falha com 404 e a tela cai no fallback de texto — logo errado
 * nunca é inventado, só ausente. Os aliases abaixo são só p/ os nomes
 * cujo slug NÃO é a normalização direta do nome (verificados na listagem
 * do diretório brazil em 2026-11-17).
 */
object ChannelLogos {
    /** Base do diretório do Brasil no tv-logos (branch main). */
    const val BASE_URL = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/brazil/"

    /** Palavras de ruído do nome do canal (qualidade/transmissão) — fora do slug. */
    private val NOISE_WORDS = setOf("hd", "fhd", "uhd", "4k", "ao", "vivo", "live")

    /** Nome normalizado → slug do arquivo (só os que a normalização direta erra). */
    private val ALIASES = mapOf(
        "sportv 2" to "sportv2",
        "sportv 3" to "sportv3",
        "espn 4" to "espn-4",
        "espn 5" to "espn-5",
        "espn extra" to "espn-extra",
        "telecine premium" to "tele-cine-premium",
        "telecine action" to "tele-cine-action",
        "telecine cult" to "tele-cine-cult",
        "telecine fun" to "tele-cine-fun",
        "telecine pipoca" to "tele-cine-pipoca",
        "telecine touch" to "tele-cine-touch",
        "rede globo" to "globo",
        "a e" to "a-and-e",
        "arte 1" to "arte1",
        "off" to "canal-off",
        "bandsports" to "band-sports",
        "globonews" to "globo-news",
        "recordnews" to "record-news",
        "bandnews" to "band-news",
        "cnnbrasil" to "cnn-brasil",
        "sbtnews" to "sbt-news",
        "redetv" to "rede-tv",
        "tvcultura" to "tv-cultura",
        "tvbrasil" to "tv-brasil",
        "cartoonnetwork" to "cartoon-network",
        "discoverykids" to "discovery-kids",
    )

    /** Normaliza o nome do canal p/ casamento (minúsculo, sem acento, 1 espaço). */
    fun normalizeName(name: String): String {
        val noAccent = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccent.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** Slug do arquivo no tv-logos p/ o nome do canal, ou null sem nome. */
    fun slugFor(channelName: String): String? {
        val words = normalizeName(channelName)
            .split(" ")
            .filter { it.isNotBlank() && it !in NOISE_WORDS }
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ")
        ALIASES[joined]?.let { return it }
        val glued = words.joinToString("")
        ALIASES[glued]?.let { return it }
        return words.joinToString("-")
    }

    /** URL do logo no tv-logos p/ o nome do canal, ou null sem nome. */
    fun urlFor(channelName: String): String? =
        slugFor(channelName)?.let { "$BASE_URL$it-br.png" }
}
