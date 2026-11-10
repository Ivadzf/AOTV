package com.aotv.app.data.local

/**
 * Idiomas de legenda, os mesmos do webOS (sublang.js). A chave é o código do OpenSubtitles
 * (ISO 639-2/B, com "pob" para o português do Brasil), que é o que os addons do Stremio mandam.
 * Os apelidos cobrem faixas do vídeo e outros addons (ISO 639-1, 639-2/T e tags como "pt-BR").
 */
object SubLangs {
    const val DEFAULT = "pob"

    /** [family] junta variantes do mesmo idioma (pt-BR e pt-PT, espanhol da Espanha e da América). */
    class Lang(val key: String, val label: String, val family: String, val aliases: List<String>)

    /** Escolha automática. [embedded] diz se é faixa do vídeo ou legenda externa. */
    data class Pick(val embedded: Boolean, val index: Int)

    val all: List<Lang> = listOf(
        Lang("pob", "Português (Brasil)", "pt", listOf("pt-br", "ptbr", "pb", "brazilian")),
        Lang("por", "Português (Portugal)", "pt", listOf("pt", "pt-pt", "portuguese")),
        Lang("eng", "Inglês", "en", listOf("en", "en-us", "en-gb", "english")),
        Lang("spa", "Espanhol", "es", listOf("es", "es-es", "spn", "spanish")),
        Lang("spl", "Espanhol (América Latina)", "es", listOf("es-419", "es-mx", "es-la", "es-us", "ea")),
        Lang("fre", "Francês", "fr", listOf("fra", "fr", "fr-fr", "fr-ca", "french")),
        Lang("ger", "Alemão", "de", listOf("deu", "de", "german")),
        Lang("ita", "Italiano", "it", listOf("it", "italian")),
        Lang("jpn", "Japonês", "ja", listOf("ja", "jp", "japanese")),
        Lang("kor", "Coreano", "ko", listOf("ko", "korean")),
        Lang("chi", "Chinês", "zh", listOf("zho", "zh", "zh-cn", "zh-hans", "zht", "zh-tw", "zh-hant", "zhe", "chinese")),
        Lang("rus", "Russo", "ru", listOf("ru", "russian")),
        Lang("ara", "Árabe", "ar", listOf("ar", "arabic")),
        Lang("hin", "Hindi", "hi", listOf("hi", "hindi")),
        Lang("tur", "Turco", "tr", listOf("tr", "turkish")),
        Lang("pol", "Polonês", "pl", listOf("pl", "polish")),
        Lang("dut", "Holandês", "nl", listOf("nld", "nl", "dutch")),
        Lang("swe", "Sueco", "sv", listOf("sv", "swedish")),
        Lang("nor", "Norueguês", "no", listOf("no", "nb", "nob", "nn", "nno", "norwegian")),
        Lang("dan", "Dinamarquês", "da", listOf("da", "danish")),
        Lang("fin", "Finlandês", "fi", listOf("fi", "finnish")),
        Lang("gre", "Grego", "el", listOf("ell", "el", "greek")),
        Lang("heb", "Hebraico", "he", listOf("he", "iw", "hebrew")),
        Lang("rum", "Romeno", "ro", listOf("ron", "ro", "romanian")),
        Lang("hun", "Húngaro", "hu", listOf("hu", "hungarian")),
        Lang("cze", "Tcheco", "cs", listOf("ces", "cs", "czech")),
        Lang("slo", "Eslovaco", "sk", listOf("slk", "sk", "slovak")),
        Lang("ukr", "Ucraniano", "uk", listOf("uk", "ukrainian")),
        Lang("bul", "Búlgaro", "bg", listOf("bg", "bulgarian")),
        Lang("hrv", "Croata", "hr", listOf("hr", "croatian")),
        Lang("srp", "Sérvio", "sr", listOf("sr", "scc", "serbian")),
        Lang("vie", "Vietnamita", "vi", listOf("vi", "vietnamese")),
        Lang("tha", "Tailandês", "th", listOf("th", "thai")),
        Lang("ind", "Indonésio", "id", listOf("id", "in", "indonesian")),
        Lang("may", "Malaio", "ms", listOf("msa", "ms", "malay")),
        Lang("per", "Persa", "fa", listOf("fas", "fa", "farsi", "persian")),
        Lang("cat", "Catalão", "ca", listOf("ca", "catalan")),
        Lang("slv", "Esloveno", "sl", listOf("sl", "slovenian")),
        Lang("est", "Estoniano", "et", listOf("et", "estonian")),
        Lang("lav", "Letão", "lv", listOf("lv", "latvian")),
        Lang("lit", "Lituano", "lt", listOf("lt", "lithuanian")),
        Lang("mac", "Macedônio", "mk", listOf("mkd", "mk", "macedonian")),
        Lang("alb", "Albanês", "sq", listOf("sqi", "sq", "albanian")),
        Lang("bos", "Bósnio", "bs", listOf("bs", "bosnian")),
        Lang("ice", "Islandês", "is", listOf("isl", "is", "icelandic")),
        Lang("baq", "Basco", "eu", listOf("eus", "eu", "basque")),
        Lang("glg", "Galego", "gl", listOf("gl", "galician")),
        Lang("ben", "Bengali", "bn", listOf("bn", "bengali")),
        Lang("tam", "Tâmil", "ta", listOf("ta", "tamil")),
        Lang("tel", "Telugo", "te", listOf("te", "telugu")),
        Lang("mal", "Malaiala", "ml", listOf("ml", "malayalam")),
        Lang("urd", "Urdu", "ur", listOf("ur", "urdu")),
        Lang("tgl", "Filipino", "tl", listOf("fil", "tl", "tagalog", "filipino")),
    )

    private val byCode: Map<String, Lang> = HashMap<String, Lang>().apply {
        all.forEach { l -> put(l.key, l) }
        all.forEach { l -> l.aliases.forEach { putIfAbsent(it, l) } }
    }

    /** Idioma de um código qualquer ("pt-BR", "por", "fra", "es_419"), ou null quando não conhece. */
    fun of(code: String?): Lang? {
        val c = code?.trim()?.lowercase()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
        return byCode[c] ?: byCode[c.substringBefore('-')]
    }

    fun label(code: String?, fallback: String = "Legenda"): String =
        of(code)?.label ?: code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: fallback

    /** 2 é o idioma escolhido, 1 é idioma vizinho (pt-PT para quem escolheu pt-BR), 0 é outro. */
    fun match(code: String?, pref: String): Int {
        val l = of(code) ?: return 0
        val p = of(pref) ?: return 0
        return when {
            l.key == p.key -> 2
            l.family == p.family -> 1
            else -> 0
        }
    }

    /** Ordem das listas. Idioma escolhido, o vizinho, a ordem da tabela e por fim os desconhecidos. */
    fun rank(code: String?, pref: String): Int {
        val l = of(code) ?: return 1000
        return when (match(code, pref)) {
            2 -> 0
            1 -> 1
            else -> 2 + all.indexOf(l)
        }
    }

    /** Faixa sem idioma marcado. Só entra quando não há nada melhor. */
    fun undetermined(code: String?): Boolean =
        code.isNullOrBlank() || code.trim().lowercase() in setOf("und", "unk", "mis", "zxx", "mul")

    /**
     * Legenda que liga sozinha. Ordem: faixa do vídeo no idioma, externa no idioma, faixa do vídeo
     * no idioma vizinho, externa no vizinho e faixa do vídeo sem idioma marcado. Null quando nada
     * serve, para não ligar legenda em idioma que a pessoa não escolheu.
     */
    fun choose(embedded: List<String?>, external: List<String?>, pref: String): Pick? {
        for (level in intArrayOf(2, 1)) {
            embedded.indexOfFirst { match(it, pref) == level }.takeIf { it >= 0 }?.let { return Pick(true, it) }
            external.indexOfFirst { match(it, pref) == level }.takeIf { it >= 0 }?.let { return Pick(false, it) }
        }
        return embedded.indexOfFirst { undetermined(it) }.takeIf { it >= 0 }?.let { Pick(true, it) }
    }
}
