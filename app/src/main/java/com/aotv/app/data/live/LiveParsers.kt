package com.aotv.app.data.live

import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/** Lista M3U (a de IPTV). Lê grupo, logo, id do guia, número e os cabeçalhos que o canal pede. */
object M3uParser {
    // String escapada (não crua """..."""): o ktbalance não lida com a
    // regex crua aqui e acusa chaves/parênteses não fechados.
    private val ATTR = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    fun parse(text: String, sourceId: String): M3uResult {
        val epg = mutableListOf<String>()
        val out = ArrayList<LiveChannel>()
        val seen = HashSet<String>()
        var attrs = emptyMap<String, String>()
        var name = ""
        var group = ""
        val headers = LinkedHashMap<String, String>()
        var index = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U", true) -> {
                    val a = attrsOf(line)
                    listOf("url-tvg", "x-tvg-url", "tvg-url").mapNotNull { a[it] }.flatMap { it.split(',') }
                        .map { it.trim() }.filter { it.startsWith("http", true) }.forEach { if (it !in epg) epg += it }
                }
                line.startsWith("#EXTINF", true) -> {
                    attrs = attrsOf(line)
                    name = nameOf(line).ifBlank { attrs["tvg-name"].orEmpty() }
                    group = attrs["group-title"].orEmpty()
                }
                line.startsWith("#EXTGRP:", true) -> if (group.isBlank()) group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", true) -> {
                    val kv = line.substringAfter(':')
                    val k = kv.substringBefore('=').trim().lowercase()
                    val v = kv.substringAfter('=', "").trim()
                    when (k) {
                        "http-user-agent" -> headers["User-Agent"] = v
                        "http-referrer", "http-referer" -> headers["Referer"] = v
                        "http-origin" -> headers["Origin"] = v
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    // Formato do Kodi: url|User-Agent=x&Referer=y
                    var url = line
                    if ('|' in line) {
                        url = line.substringBefore('|')
                        line.substringAfter('|').split('&').forEach { p ->
                            val k = p.substringBefore('=').trim()
                            val rawV = p.substringAfter('=', "")
                            // Lista malformada (% solto) não pode derrubar a leitura inteira.
                            val v = runCatching { java.net.URLDecoder.decode(rawV, "UTF-8") }.getOrDefault(rawV)
                            when (k.lowercase()) {
                                "user-agent" -> headers["User-Agent"] = v
                                "referer", "referrer" -> headers["Referer"] = v
                                "origin" -> headers["Origin"] = v
                            }
                        }
                    }
                    if (url.startsWith("http", true) || url.startsWith("rtmp", true) || url.startsWith("rtsp", true)) {
                        if (seen.add(url)) {
                            val title = name.ifBlank { url.substringAfterLast('/').substringBefore('?').ifBlank { "Canal" } }
                            out += LiveChannel(
                                id = "$sourceId:" + stableId(attrs["tvg-id"].orEmpty() + "|" + title + "|" + url),
                                sourceId = sourceId,
                                name = title,
                                url = url,
                                logo = attrs["tvg-logo"].orEmpty(),
                                group = group,
                                epgId = attrs["tvg-id"].orEmpty(),
                                number = attrs["tvg-chno"]?.toIntOrNull() ?: ++index,
                                headers = HashMap(headers),
                                catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: 0,
                                catchupSource = attrs["catchup-source"].orEmpty(),
                                catchupTemplate = attrs["catchup-template"].orEmpty(),
                            )
                        }
                    }
                    attrs = emptyMap(); name = ""; group = ""; headers.clear()
                }
            }
        }
        return M3uResult(out, epg)
    }

    private fun attrsOf(line: String) = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

    /** Nome depois da primeira vírgula fora das aspas. */
    internal fun nameOf(line: String): String {
        var quoted = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') quoted = !quoted
            else if (c == ',' && !quoted) return line.substring(i + 1).trim()
        }
        return ""
    }

    internal fun stableId(s: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it) }
    }
}

/** Respostas do player_api.php do Xtream Codes. */
object XtreamParser {
    fun account(json: String): XtreamAccount? = runCatching {
        val u = JSONObject(json).optJSONObject("user_info") ?: return null
        if (u.optInt("auth", 1) == 0) return null
        val formats = u.optJSONArray("allowed_output_formats")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        XtreamAccount(
            u.optString("status"),
            u.optString("exp_date").toLongOrNull()?.times(1000) ?: 0L,
            u.optString("max_connections").toIntOrNull() ?: 0,
            formats,
            // Lote 20 (v1.30.0): telas em uso (`active_cons` — nº de conexões
            // ativas no momento; vem como string ou número).
            u.optString("active_cons").toIntOrNull() ?: u.optInt("active_cons", 0),
        )
    }.getOrNull()

    fun categories(json: String): Map<String, String> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optString("category_id") to it.optString("category_name") }
    }.getOrDefault(emptyMap())

    /** Categorias do On Demand, na ordem da API (o mapa acima perde a ordem). */
    fun categoryList(json: String): List<VodCategory> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("category_id").ifBlank { return@mapNotNull null }
            VodCategory(id, o.optString("category_name").ifBlank { "Categoria $id" })
        }
    }.getOrDefault(emptyList())

    /**
     * Filmes (get_vod_streams) ou séries (get_series). [isSeries] marca o tipo.
     * Só o que a API devolver — campo ausente vira vazio, nunca inventado.
     */
    fun vod(json: String, source: LiveSource, groups: Map<String, String>, isSeries: Boolean): List<VodItem> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val sid = o.optString("stream_id").ifBlank { return@mapNotNull null }
            VodItem(
                id = sid,
                sourceId = source.id,
                title = o.optString("name").ifBlank { if (isSeries) "Série $sid" else "Filme $sid" },
                cover = o.optString("stream_icon").ifBlank { o.optString("cover") },
                plot = o.optString("plot"),
                categoryId = o.optString("category_id"),
                category = groups[o.optString("category_id")].orEmpty(),
                containerExtension = o.optString("container_extension"),
                year = o.optString("year"),
                rating = o.optString("rating"),
                isSeries = isSeries,
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Detalhe de uma série (get_series_info): info + episódios por temporada.
     * O "episodes" da API é um objeto temporada → lista; a ordem das temporadas
     * segue a ordem das chaves. Episódio sem id é ignorado.
     */
    fun seriesInfo(json: String, seriesId: String, source: LiveSource, groups: Map<String, String> = emptyMap()): VodSeriesDetail? = runCatching {
        val root = JSONObject(json)
        val info = root.optJSONObject("info") ?: return null
        val series = VodItem(
            id = seriesId,
            sourceId = source.id,
            title = info.optString("name").ifBlank { "Série $seriesId" },
            cover = info.optString("cover").ifBlank { info.optString("stream_icon") },
            plot = info.optString("plot"),
            categoryId = info.optString("category_id"),
            category = groups[info.optString("category_id")].orEmpty(),
            year = info.optString("year"),
            rating = info.optString("rating"),
            isSeries = true,
        )
        val eps = ArrayList<VodEpisode>()
        val seasons = root.optJSONObject("episodes")
        seasons?.keys()?.asSequence()?.toList()?.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }?.forEach { key ->
            val arr = seasons.optJSONArray(key) ?: return@forEach
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }.forEach { ep ->
                val eid = ep.optString("id").ifBlank { return@forEach }
                val epInfo = ep.optJSONObject("info")
                eps += VodEpisode(
                    id = eid,
                    title = ep.optString("title").ifBlank { "Episódio ${ep.optString("episode_num")}" },
                    season = key.toIntOrNull() ?: ep.optInt("season", 0),
                    episode = ep.optInt("episode_num", 0),
                    containerExtension = ep.optString("container_extension"),
                    plot = ep.optString("plot").ifBlank { epInfo?.optString("plot").orEmpty() },
                    cover = ep.optString("movie_image").ifBlank { epInfo?.optString("movie_image").orEmpty() },
                    duration = ep.optString("duration").ifBlank { epInfo?.optString("duration").orEmpty() },
                )
            }
        }
        VodSeriesDetail(series, eps)
    }.getOrNull()

    /**
     * Detalhe de um filme (get_vod_info): {"info": {...}, "movie_data": {...}}.
     * Preenche SÓ os campos vazios do [item] — a lista já traz quase tudo.
     */
    fun vodInfo(json: String, item: VodItem): VodItem = runCatching {
        val root = JSONObject(json)
        val info = root.optJSONObject("info") ?: return item
        val data = root.optJSONObject("movie_data")
        item.copy(
            title = item.title.ifBlank { info.optString("name") },
            cover = item.cover.ifBlank { info.optString("cover").ifBlank { info.optString("movie_image") } },
            plot = item.plot.ifBlank { info.optString("plot") },
            categoryId = item.categoryId.ifBlank { info.optString("category_id") },
            containerExtension = item.containerExtension.ifBlank { data?.optString("container_extension").orEmpty() },
            year = item.year.ifBlank { info.optString("year") },
            rating = item.rating.ifBlank { info.optString("rating") },
        )
    }.getOrDefault(item)

    /** Canais ao vivo. [ext] é "m3u8" quando a conta deixa, senão "ts". */
    fun live(json: String, source: LiveSource, groups: Map<String, String>, ext: String): List<LiveChannel> = runCatching {
        val a = JSONArray(json)
        val base = source.server.trimEnd('/')
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val sid = o.optString("stream_id").ifBlank { return@mapNotNull null }
            val direct = o.optString("direct_source").takeIf { it.startsWith("http", true) }
            // Lote 20 (v1.30.0) — catch-up Xtream: `tv_archive` (0/1) diz se
            // o canal grava, `tv_archive_duration` diz por quantas HORAS.
            // Sem isso o guia nunca oferecia catch-up no Xtream, mesmo com o
            // servidor suportando timeshift (o `catchupUrl` já sabia montar
            // a URL `/timeshift/`). `catchupSource = "xtream"` cai no ramo
            // do `/live/` do `catchupUrl` (não no `?utc=` do default).
            val archived = o.optString("tv_archive").toIntOrNull() ?: o.optInt("tv_archive", 0)
            val archiveHours = o.optString("tv_archive_duration").toIntOrNull() ?: o.optInt("tv_archive_duration", 0)
            val catchupDays = if (archived == 1 && archiveHours > 0) maxOf(1, archiveHours / 24) else 0
            LiveChannel(
                id = "${source.id}:$sid",
                sourceId = source.id,
                name = o.optString("name").ifBlank { "Canal $sid" },
                url = direct ?: "$base/live/${enc(source.user)}/${enc(source.pass)}/$sid.$ext",
                logo = o.optString("stream_icon"),
                group = groups[o.optString("category_id")].orEmpty(),
                epgId = o.optString("epg_channel_id"),
                number = o.optInt("num", i + 1),
                catchupDays = catchupDays,
                catchupSource = if (catchupDays > 0) "xtream" else "",
            )
        }
    }.getOrDefault(emptyList())

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/**
 * Guia XMLTV. Lê em fluxo (o arquivo pode ter dezenas de MB) e guarda só os programas dos
 * canais pedidos, entre [hoursBack] horas atrás e [daysAhead] dias à frente (Lote 8, v1.18.0:
 * antes era fixo 3 h atrás e 24 h à frente). Sem guia, não há programação — nunca inventar.
 */
object XmltvParser {
    private val FORMATS = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss", "yyyyMMddHHmm Z", "yyyyMMddHHmm")

    /**
     * [wanted] liga o id do guia (em minúsculas) aos canais que usam esse id. [names] faz o mesmo
     * com o nome normalizado, para canal sem tvg-id. Devolve canal → programas em ordem.
     */
    fun parse(
        input: InputStream,
        wanted: Map<String, List<String>>,
        names: Map<String, List<String>>,
        now: Long = System.currentTimeMillis(),
        hoursBack: Int = 3,
        daysAhead: Int = 1,
    ): Map<String, List<LiveProgram>> {
        val from = now - hoursBack.coerceIn(0, 24) * 3_600_000L
        val to = now + daysAhead.coerceIn(1, 7) * 24 * 3_600_000L
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val idToChannels = HashMap<String, List<String>>()
        val out = HashMap<String, MutableList<LiveProgram>>()
        var chId: String? = null
        var progCh: String? = null
        var start = 0L
        var stop = 0L
        var title = ""
        var desc = ""
        // Lote 12 (v1.22.0): pôster do programa (`<icon src>` dentro do
        // `<programme>`) — só quando o guia traz; vazio sem imagem.
        var image = ""
        var tag = ""
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    when (tag) {
                        "channel" -> {
                            chId = p.getAttributeValue(null, "id")?.trim()
                            chId?.lowercase()?.let { id -> wanted[id]?.let { idToChannels[id] = it } }
                        }
                        "programme" -> {
                            progCh = p.getAttributeValue(null, "channel")?.trim()?.lowercase()
                            start = time(p.getAttributeValue(null, "start"))
                            stop = time(p.getAttributeValue(null, "stop"))
                            title = ""; desc = ""; image = ""
                        }
                        // Pôster do programa (Lote 12, v1.22.0): `<icon src>`
                        // dentro do `<programme>`; só a primeira vale.
                        "icon" -> if (progCh != null && image.isBlank()) {
                            image = p.getAttributeValue(null, "src")?.trim().orEmpty()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = p.text?.trim().orEmpty()
                    if (t.isNotEmpty()) when (tag) {
                        "display-name" -> chId?.lowercase()?.let { id ->
                            if (idToChannels[id] == null) names[norm(t)]?.let { idToChannels[id] = it }
                        }
                        "title" -> if (progCh != null && title.isEmpty()) title = t
                        "desc" -> if (progCh != null && desc.isEmpty()) desc = t
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (p.name) {
                        "channel" -> chId = null
                        "programme" -> {
                            val id = progCh
                            val chans = id?.let { idToChannels[it] ?: wanted[it] }
                            if (chans != null && stop > from && start < to && title.isNotEmpty()) {
                                val prog = LiveProgram(start, if (stop > start) stop else start + 3_600_000L, title, desc.take(400), image.take(500))
                                chans.forEach { c -> out.getOrPut(c) { mutableListOf() } += prog }
                            }
                            progCh = null
                        }
                    }
                    tag = ""
                }
            }
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start }.distinctBy { it.start } }
    }

    internal fun time(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val v = s.trim()
        for (f in FORMATS) {
            runCatching {
                val fmt = java.text.SimpleDateFormat(f, java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC"); isLenient = false }
                return fmt.parse(v)!!.time
            }
        }
        return 0L
    }

    /** Nome sem acento, qualidade e prefixo de país, para casar canal sem id com o guia. */
    fun norm(name: String): String {
        var s = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        s = s.replace(Regex("\\[[^]]*]|\\([^)]*\\)"), " ")
        s = s.replace(Regex("^[a-z]{2,3}\\s*[:|]\\s*"), "")
        s = s.replace(Regex("\\b(fhd|uhd|hd|sd|4k|hevc|h265|1080p?|720p?|480p?)\\b"), " ")
        return s.replace(Regex("[^a-z0-9]"), "")
    }
}
