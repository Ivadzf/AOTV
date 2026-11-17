package com.aotv.app.data.remote

// ── Selos com logo na folha de fontes, Lote 19 item 9 (v1.29.0) ─────────────
// Estilo Fusion/Elite do Nuvio (fotos do Davi, 2026-11-17): cada card de
// fonte mostra uma fileira de selos com LOGO — 4K, Dolby Vision, DTS-HD MA,
// 5.1, REMUX, SIZE 66.8 GB — em vez de só pills de texto.
//
// Extração: REUSA a leitura que já existe (Lote 17 / score) — [qualityRankOf],
// [hasDolbyVision], [hasHdr], [hasHdr10Plus], [hasAtmos], [releaseKindOf],
// [codecOf] e [ptAudio] — mais regexes SÓ p/ o que ela não cobre (TrueHD,
// DTS-HD MA, DTS:X, Dolby Digital, canais 5.1/7.1, IMAX, serviços de
// streaming, códigos de idioma). Não há segundo parser paralelo do rótulo.
//
// Pack de selos: JSON configurável por URL, colado pelo usuário nas
// Configurações > Streams. NENHUMA URL de pack de terceiro é embutida como
// padrão (as URLs das fotos — gist de um usuário e repo Elite-Badges — são
// de terceiros; o campo começa VAZIO). Sem pack carregado, a folha usa os
// selos de texto atuais. Pack inválido ou offline → fallback silencioso
// p/ texto, com a mensagem honesta na tela de prévia.
//
// Formato do pack (JSON) — documentado aqui e na tela de prévia:
//   {"badges": [{"id": "4k", "category": "resolution", "label": "4K",
//                "image": "https://exemplo.com/selos/4k.png"}]}
//   · O topo também pode ser um array direto: [{"id": ..., ...}, ...].
//   · Formato Fusion do Nuvio (Lote 20, v1.30.0): {"filters": [{"id":
//     "res-4k", "groupId": "resolution", "name": "4K", "imageURL":
//     "https://...", ...}]} — ex.: o FR-Badges
//     (farizmohd70/FR-Badges, 219 filtros). O `groupId` vira a categoria
//     e o `id` é traduzido p/ o id do AOTV (ver [FUSION_ID_ALIASES] e
//     [fusionBadgeId]); entrada sem imagem vira selo de texto.
//   · "id" é obrigatório (casa com o selo extraído do texto, sem diferenciar
//     maiúscula/minúscula); "label" é o texto do selo; "image" é a URL do
//     logo — sem "image", o selo continua como texto.
//   · "category" é uma de: source, resolution, video, audio, channels,
//     encoder, language, streaming, size. Categoria desconhecida → a
//     entrada é ignorada (contada em [BadgePackParse.skipped]).
//   · Sinônimos aceitos: "name" p/ id, "text"/"title" p/ label,
//     "url"/"icon"/"imageUrl" p/ image, "group"/"type" p/ category.
// As imagens dos logos são carregadas por URL com o cache do Coil
// (AsyncImage/SubcomposeAsyncImage) — não se baixa tudo a cada abertura
// da folha; se a imagem falhar, o selo volta a ser pill de texto.

/** Categoria de um selo, na ordem em que aparece na fileira do card. */
enum class BadgeCategory(val id: String, val label: String) {
    SOURCE("source", "Origem"),
    RESOLUTION("resolution", "Resolução"),
    VIDEO("video", "Vídeo"),
    AUDIO("audio", "Áudio"),
    CHANNELS("channels", "Canais de áudio"),
    ENCODER("encoder", "Codec"),
    LANGUAGE("language", "Idioma"),
    STREAMING("streaming", "Streaming"),
    SIZE("size", "Tamanho"),
    ;

    companion object {
        /** Ordem fixa da fileira (o SIZE vai sempre por último, como nas fotos). */
        val ORDER: List<BadgeCategory> = listOf(
            SOURCE, RESOLUTION, VIDEO, AUDIO, CHANNELS, ENCODER, LANGUAGE, STREAMING, SIZE,
        )

        fun of(id: String): BadgeCategory? =
            entries.firstOrNull { it.id == id.trim().lowercase() }
    }
}

/**
 * Um selo. [imageUrl] null = selo de texto (o fallback atual); com URL, a
 * folha desenha o logo e só usa [label] se a imagem falhar.
 */
data class SourceBadge(
    val id: String,
    val category: BadgeCategory,
    val label: String,
    val imageUrl: String? = null,
)

// ── Extração do texto da fonte (reusa Lote 17; regexes novos só p/ o resto) ──

private val TRUEHD_RE = Regex("\\btrue[ .-]?hd\\b", RegexOption.IGNORE_CASE)
private val DTS_HD_MA_RE = Regex(
    "\\bdts[ .-]?hd[ .-]?ma\\b|\\bdts[ .-]?hd[ .-]?master[ .-]?audio\\b",
    RegexOption.IGNORE_CASE,
)
private val DTS_X_RE = Regex("\\bdts[\\s.:-]?x\\b", RegexOption.IGNORE_CASE)
private val DTS_HD_RE = Regex("\\bdts[ .-]?hd\\b", RegexOption.IGNORE_CASE)
private val DTS_RE = Regex("\\bdts\\b", RegexOption.IGNORE_CASE)
private val DD_PLUS_RE = Regex("\\bdd\\+", RegexOption.IGNORE_CASE)
private val DD_RE = Regex(
    "\\bdd\\+|\\bdd\\b|\\bdolby[ .-]?digital\\b|\\be[ .-]?ac[ .-]?3\\b",
    RegexOption.IGNORE_CASE,
)
private val HDR10_RE = Regex("\\bhdr10\\b", RegexOption.IGNORE_CASE)
private val HDR_WORD_RE = Regex("\\bhdr\\b|\\bhlg\\b", RegexOption.IGNORE_CASE)
private val IMAX_RE = Regex("\\bimax\\b", RegexOption.IGNORE_CASE)
private val CH_71_RE = Regex("\\b7\\.1\\b")
private val CH_51_RE = Regex("\\b5\\.1\\b")

/** Códigos de idioma que viram selo de bandeira (ordem: o composto antes do simples). */
private val LANG_CODES = listOf(
    "pt-br" to "PT-BR", "es-mx" to "ES-MX",
    "en" to "EN", "pt" to "PT", "es" to "ES", "fr" to "FR", "de" to "DE",
    "it" to "IT", "ja" to "JA", "ru" to "RU", "pl" to "PL", "cs" to "CS",
    "ko" to "KO", "zh" to "ZH", "nl" to "NL", "tr" to "TR",
)

/** Serviços de streaming que viram selo (nome no texto → id/lábel do selo). */
private val STREAMING_NAMES = listOf(
    Triple("netflix", Regex("\\bnetflix\\b", RegexOption.IGNORE_CASE), "Netflix"),
    Triple("primevideo", Regex("prime[ .-]?video|\\bamazon\\b", RegexOption.IGNORE_CASE), "Prime Video"),
    Triple("disneyplus", Regex("disney\\+|disney[ .-]?plus", RegexOption.IGNORE_CASE), "Disney+"),
    Triple("hbomax", Regex("hbo[ .-]?max", RegexOption.IGNORE_CASE), "HBO Max"),
    Triple("appletv", Regex("apple[ .-]?tv", RegexOption.IGNORE_CASE), "Apple TV"),
    Triple("hulu", Regex("\\bhulu\\b", RegexOption.IGNORE_CASE), "Hulu"),
    Triple("paramountplus", Regex("paramount\\+|paramount[ .-]?plus", RegexOption.IGNORE_CASE), "Paramount+"),
    Triple("peacock", Regex("\\bpeacock\\b", RegexOption.IGNORE_CASE), "Peacock"),
    Triple("crunchyroll", Regex("\\bcrunchyroll\\b", RegexOption.IGNORE_CASE), "Crunchyroll"),
    Triple("crave", Regex("\\bcrave\\b", RegexOption.IGNORE_CASE), "Crave"),
    Triple("globoplay", Regex("\\bgloboplay\\b", RegexOption.IGNORE_CASE), "Globoplay"),
    Triple("starplus", Regex("star\\+", RegexOption.IGNORE_CASE), "Star+"),
)

/**
 * Selos extraídos do texto da fonte — ex.: "HEVC | DV || DTS-HD MA (5.1)"
 * vira Dolby Vision + DTS-HD MA + 5.1. [sizeText] é o tamanho já lido
 * ("66.8 GB"); vazio = sem selo SIZE. Sem duplicar por id, na ordem das
 * categorias ([BadgeCategory.ORDER]).
 */
fun extractSourceBadges(label: String, sizeText: String = ""): List<SourceBadge> {
    val out = LinkedHashMap<String, SourceBadge>()
    fun add(badge: SourceBadge) {
        out.putIfAbsent(badge.id, badge)
    }

    // Origem do arquivo — REUSA releaseKindOf (Lote 17 / score).
    when (releaseKindOf(label)) {
        "Remux" -> add(SourceBadge("remux", BadgeCategory.SOURCE, "REMUX"))
        "BluRay" -> add(SourceBadge("bluray", BadgeCategory.SOURCE, "BluRay"))
        "WEB-DL" -> add(SourceBadge("webdl", BadgeCategory.SOURCE, "WEB-DL"))
        "WEBRip" -> add(SourceBadge("webrip", BadgeCategory.SOURCE, "WEBRip"))
        "HDTV" -> add(SourceBadge("hdtv", BadgeCategory.SOURCE, "HDTV"))
    }

    // Resolução — REUSA qualityRankOf (a mesma leitura do score).
    when (qualityRankOf(label)) {
        2160 -> add(SourceBadge("4k", BadgeCategory.RESOLUTION, "4K"))
        1440 -> add(SourceBadge("1440p", BadgeCategory.RESOLUTION, "1440p"))
        1080 -> add(SourceBadge("1080p", BadgeCategory.RESOLUTION, "1080p"))
        720 -> add(SourceBadge("720p", BadgeCategory.RESOLUTION, "720p"))
        480 -> add(SourceBadge("480p", BadgeCategory.RESOLUTION, "480p"))
    }

    // Tecnologia de vídeo — REUSA hasDolbyVision / hasHdr10Plus / hasHdr.
    // Dolby Vision já traz HDR: com DV, o selo é só o DV (como nas fotos —
    // "HDR10 / DV / HDR" mostra só Vision, nunca HDR10 nem HDR).
    if (hasDolbyVision(label)) {
        add(SourceBadge("dolbyvision", BadgeCategory.VIDEO, "Dolby Vision"))
    } else if (hasHdr10Plus(label)) {
        add(SourceBadge("hdr10plus", BadgeCategory.VIDEO, "HDR10+"))
    } else if (HDR10_RE.containsMatchIn(label)) {
        add(SourceBadge("hdr10", BadgeCategory.VIDEO, "HDR10"))
    } else if (hasHdr(label) || HDR_WORD_RE.containsMatchIn(label)) {
        add(SourceBadge("hdr", BadgeCategory.VIDEO, "HDR"))
    }
    if (IMAX_RE.containsMatchIn(label)) add(SourceBadge("imax", BadgeCategory.VIDEO, "IMAX"))

    // Áudio — REUSA hasAtmos; o resto não existe na leitura do score.
    if (hasAtmos(label)) add(SourceBadge("atmos", BadgeCategory.AUDIO, "Dolby Atmos"))
    if (TRUEHD_RE.containsMatchIn(label)) add(SourceBadge("truehd", BadgeCategory.AUDIO, "TrueHD"))
    if (DTS_HD_MA_RE.containsMatchIn(label)) {
        add(SourceBadge("dtshdma", BadgeCategory.AUDIO, "DTS-HD MA"))
    } else if (DTS_X_RE.containsMatchIn(label)) {
        add(SourceBadge("dtsx", BadgeCategory.AUDIO, "DTS:X"))
    } else if (DTS_HD_RE.containsMatchIn(label)) {
        add(SourceBadge("dtshd", BadgeCategory.AUDIO, "DTS-HD"))
    } else if (DTS_RE.containsMatchIn(label)) {
        add(SourceBadge("dts", BadgeCategory.AUDIO, "DTS"))
    }
    if (DD_RE.containsMatchIn(label)) {
        add(
            SourceBadge(
                "dolbydigital", BadgeCategory.AUDIO,
                if (DD_PLUS_RE.containsMatchIn(label)) "DD+" else "Dolby Digital",
            ),
        )
    }

    // Canais de áudio.
    if (CH_71_RE.containsMatchIn(label)) add(SourceBadge("ch71", BadgeCategory.CHANNELS, "7.1"))
    if (CH_51_RE.containsMatchIn(label)) add(SourceBadge("ch51", BadgeCategory.CHANNELS, "5.1"))

    // Codec — REUSA codecOf (a mesma leitura do score).
    when (codecOf(label)) {
        "AV1" -> add(SourceBadge("av1", BadgeCategory.ENCODER, "AV1"))
        "HEVC" -> add(SourceBadge("hevc", BadgeCategory.ENCODER, "HEVC"))
        "H264" -> add(SourceBadge("h264", BadgeCategory.ENCODER, "H.264"))
        "VP9" -> add(SourceBadge("vp9", BadgeCategory.ENCODER, "VP9"))
    }

    // Idioma (bandeiras): códigos no texto; dublado em PT sem código vira PT-BR.
    // O código simples não casa dentro do composto ("PT" em "PT-BR" já é PT-BR).
    var langFound = false
    for ((code, shown) in LANG_CODES) {
        if (code == "pt" && out.containsKey("lang-pt-br")) continue
        if (code == "es" && out.containsKey("lang-es-mx")) continue
        if (Regex("\\b" + Regex.escape(code) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
            add(SourceBadge("lang-$code", BadgeCategory.LANGUAGE, shown))
            langFound = true
        }
    }
    if (!langFound && ptAudio(label)) {
        add(SourceBadge("lang-pt-br", BadgeCategory.LANGUAGE, "PT-BR"))
    }

    // Serviço de streaming citado no texto.
    for ((id, re, shown) in STREAMING_NAMES) {
        if (re.containsMatchIn(label)) add(SourceBadge(id, BadgeCategory.STREAMING, shown))
    }

    // Tamanho — sempre por último, como nas fotos ("SIZE 66.8 GB").
    if (sizeText.isNotBlank()) {
        add(SourceBadge("size", BadgeCategory.SIZE, "SIZE $sizeText"))
    }

    return BadgeCategory.ORDER.flatMap { cat -> out.values.filter { it.category == cat } }
}

/** Selos de texto embutidos (o fallback quando não há pack com logo). */
fun builtinBadgesFor(extracted: List<SourceBadge>): List<SourceBadge> = extracted

// ── Pack de selos (JSON por URL) ─────────────────────────────────────────────

/** Resultado do parse de um pack: selos válidos, entradas ignoradas e erro. */
data class BadgePackParse(
    val badges: List<SourceBadge>,
    val skipped: Int,
    val error: String? = null,
)

/** Teto de entradas por pack — pack maior que isso é erro, não trava a tela. */
const val MAX_BADGE_PACK_ENTRIES = 500

/**
 * Grupo do pack Fusion → categoria do AOTV (Lote 20, v1.30.0). Grupos do
 * FR-Badges conferidos ao vivo (2026-11-17): languages, streaming,
 * subtitles, audio-codec, editions, source, dynamic-range, video-codec,
 * audio-channels, audio-subs, resolution, video-format, debrid,
 * containers. Grupo desconhecido → entrada ignorada (contada em skipped).
 */
private val FUSION_GROUP_TO_CATEGORY = mapOf(
    "resolution" to BadgeCategory.RESOLUTION,
    "dynamic-range" to BadgeCategory.VIDEO,
    "video-format" to BadgeCategory.VIDEO,
    "video-codec" to BadgeCategory.ENCODER,
    "audio-codec" to BadgeCategory.AUDIO,
    "audio-channels" to BadgeCategory.CHANNELS,
    "audio-subs" to BadgeCategory.AUDIO,
    "subtitles" to BadgeCategory.LANGUAGE,
    "languages" to BadgeCategory.LANGUAGE,
    "source" to BadgeCategory.SOURCE,
    "editions" to BadgeCategory.SOURCE,
    "containers" to BadgeCategory.SOURCE,
    "streaming" to BadgeCategory.STREAMING,
    "debrid" to BadgeCategory.STREAMING,
)

/**
 * Id do pack Fusion → id do AOTV (Lote 20, v1.30.0). Os ids do Fusion
 * carregam o prefixo do grupo ("res-4k", "acodec-atmos") e usam hífen
 * ("dolby-vision"); os ids do AOTV são os extraídos do texto da fonte
 * ("4k", "atmos", "dolbyvision"). Só os casos que a regra genérica não
 * resolve entram aqui — o resto passa por [fusionBadgeId].
 */
private val FUSION_ID_ALIASES = mapOf(
    "res-4k" to "4k",
    "dr-dolby-vision" to "dolbyvision",
    "dr-dv-p7" to "dolbyvision",
    "dr-dv-p8" to "dolbyvision",
    "acodec-atmos" to "atmos",
    "acodec-ddplus" to "dolbydigital",
    "acodec-dd" to "dolbydigital",
    "acodec-dtshd-ma" to "dtshdma",
    "ch-7-1" to "ch71",
    "ch-71" to "ch71",
    "ch-5-1" to "ch51",
    "ch-51" to "ch51",
    "codec-av1" to "av1",
    "codec-hevc" to "hevc",
    "codec-avc" to "h264",
    "stream-netflix" to "netflix",
    "stream-prime-video" to "primevideo",
    "stream-apple-tv" to "appletv",
    "stream-disney-plus" to "disneyplus",
    "stream-max" to "hbomax",
    "source-remux" to "remux",
    "source-bluray" to "bluray",
    "source-uhd-bluray" to "bluray",
    "source-webdl" to "webdl",
    "source-webrip" to "webrip",
    "source-hdtv" to "hdtv",
    "edition-imax" to "imax",
    "edition-imax-enhanced" to "imax",
)

/**
 * Traduz o id de um filtro Fusion p/ o id do AOTV. Alias exato primeiro;
 * `lang-*` já casa ("lang-pt-br"); o resto tira o prefixo do grupo e os
 * separadores ("res-1080p" → "1080p", "dr-hdr10" → "hdr10"). Id sem
 * tradução volta normalizado — se não casar com nenhum selo extraído, o
 * pack só não o usa (o [resolvePackBadges] nunca inventa selo).
 */
fun fusionBadgeId(fusionId: String): String {
    val raw = fusionId.trim().lowercase()
    FUSION_ID_ALIASES[raw]?.let { return it }
    if (raw.startsWith("lang-")) return raw
    var stripped = raw
    for (prefix in listOf("res-", "source-", "codec-", "acodec-", "dr-", "ch-", "stream-", "edition-", "container-")) {
        if (stripped.startsWith(prefix)) {
            stripped = stripped.removePrefix(prefix)
            break
        }
    }
    return stripped.replace("-", "").replace("_", "").replace(".", "")
}

/**
 * Lê um pack no formato Fusion do Nuvio (`{"filters": [...]}`). Cada filtro
 * tem `id`, `groupId`, `name`, `imageURL` (com URL maiúsculo — case
 * sensitive) e `isEnabled` (false = ignorado). Entrada sem id ou com grupo
 * desconhecido é ignorada e contada em [BadgePackParse.skipped].
 */
fun parseFusionBadgePack(json: String): BadgePackParse {
    if (json.isBlank()) return BadgePackParse(emptyList(), 0, null)
    return runCatching {
        val arr = org.json.JSONObject(json.trim()).optJSONArray("filters")
            ?: return BadgePackParse(emptyList(), 0, "O JSON não tem um array \"filters\".")
        if (arr.length() > MAX_BADGE_PACK_ENTRIES) {
            return BadgePackParse(
                emptyList(), 0,
                "O pack tem ${arr.length()} selos (máximo $MAX_BADGE_PACK_ENTRIES).",
            )
        }
        val out = LinkedHashMap<String, SourceBadge>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                skipped++
                continue
            }
            if (o.optBoolean("isEnabled", true) == false) {
                skipped++
                continue
            }
            val rawId = o.optString("id").trim()
            val category = FUSION_GROUP_TO_CATEGORY[o.optString("groupId").trim().lowercase()]
            if (rawId.isBlank() || category == null) {
                skipped++
                continue
            }
            val id = fusionBadgeId(rawId)
            val label = o.optString("name").ifBlank { id }
            // Fusion usa `imageURL` (URL maiúsculo); os sinônimos do formato
            // próprio continuam valendo p/ packs mistos.
            val image = o.optString("imageURL")
                .ifBlank { o.optString("imageUrl") }
                .ifBlank { o.optString("image") }
                .ifBlank { o.optString("url") }
                .trim()
            if (id !in out) {
                out[id] = SourceBadge(id, category, label, image.ifBlank { null })
            }
        }
        BadgePackParse(out.values.toList(), skipped, null)
    }.getOrElse { e ->
        BadgePackParse(emptyList(), 0, "JSON inválido: ${e.message ?: "não deu pra ler"}")
    }
}

/**
 * Lê um pack de selos em JSON. Aceita `{"badges": [...]}` ou um array direto.
 * Entrada sem id ou com categoria desconhecida é ignorada e contada em
 * [BadgePackParse.skipped]; id repetido fica com a primeira ocorrência.
 * Nunca lança: JSON inválido vira [BadgePackParse.error].
 */
fun parseBadgePack(json: String): BadgePackParse {
    if (json.isBlank()) return BadgePackParse(emptyList(), 0, null)
    return runCatching {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) {
            org.json.JSONArray(trimmed)
        } else {
            val obj = org.json.JSONObject(trimmed)
            // Formato Fusion do Nuvio (Lote 20, v1.30.0): {"filters": [...]}.
            // O erro honesto de 404 continua no repositório — aqui só o
            // formato é decidido (o `badges` próprio tem prioridade).
            if (obj.optJSONArray("badges") == null && obj.optJSONArray("filters") != null) {
                return parseFusionBadgePack(trimmed)
            }
            obj.optJSONArray("badges")
                ?: return BadgePackParse(emptyList(), 0, "O JSON não tem um array \"badges\".")
        }
        if (arr.length() > MAX_BADGE_PACK_ENTRIES) {
            return BadgePackParse(
                emptyList(), 0,
                "O pack tem ${arr.length()} selos (máximo $MAX_BADGE_PACK_ENTRIES).",
            )
        }
        val out = LinkedHashMap<String, SourceBadge>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                skipped++
                continue
            }
            val id = o.optString("id").ifBlank { o.optString("name") }.trim()
            val label = o.optString("label")
                .ifBlank { o.optString("text") }
                .ifBlank { o.optString("title") }
                .ifBlank { id }
            val category = BadgeCategory.of(
                o.optString("category")
                    .ifBlank { o.optString("group") }
                    .ifBlank { o.optString("type") },
            )
            val image = o.optString("image")
                .ifBlank { o.optString("url") }
                .ifBlank { o.optString("icon") }
                .ifBlank { o.optString("imageUrl") }
                .trim()
            if (id.isBlank() || category == null) {
                skipped++
                continue
            }
            val key = id.lowercase()
            if (key !in out) {
                out[key] = SourceBadge(key, category, label, image.ifBlank { null })
            }
        }
        BadgePackParse(out.values.toList(), skipped, null)
    }.getOrElse { e ->
        BadgePackParse(emptyList(), 0, "JSON inválido: ${e.message ?: "não deu pra ler"}")
    }
}

/**
 * Casa os selos extraídos com o pack: quando o pack tem o mesmo id, o selo
 * usa o rótulo e a imagem do pack; sem pack (ou sem esse id), fica o selo
 * de texto embutido. Nunca inventa selo que o texto da fonte não tem.
 */
fun resolvePackBadges(extracted: List<SourceBadge>, pack: List<SourceBadge>): List<SourceBadge> {
    if (pack.isEmpty()) return extracted
    val byId = pack.associateBy { it.id.lowercase() }
    return extracted.map { badge ->
        val packed = byId[badge.id.lowercase()]
        if (packed == null) badge else badge.copy(label = packed.label, imageUrl = packed.imageUrl)
    }
}

/** Selos agrupados por categoria, na ordem da fileira — p/ a tela de prévia. */
fun groupBadgesByCategory(
    badges: List<SourceBadge>,
): List<Pair<BadgeCategory, List<SourceBadge>>> =
    BadgeCategory.ORDER.mapNotNull { cat ->
        badges.filter { it.category == cat }.takeIf { it.isNotEmpty() }?.let { cat to it }
    }
