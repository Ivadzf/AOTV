package com.aotv.app.data.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TV ao vivo. Guarda as fontes (listas M3U e contas Xtream), baixa os canais e o guia e lembra
 * favoritos e recentes. Canais e guia ficam em arquivo por 12 h, a tela abre sem esperar a rede.
 */
@Singleton
class LiveTvRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class State(
        val sources: List<LiveSource> = emptyList(),
        val channels: List<LiveChannel> = emptyList(),
        val epg: Map<String, List<LiveProgram>> = emptyMap(),
        val favorites: Set<String> = emptySet(),
        val recents: List<String> = emptyList(),
        val loading: Boolean = false,
        val epgLoading: Boolean = false,
        val error: String? = null,
        val accounts: Map<String, XtreamAccount> = emptyMap(),
        /** Aba "Ao vivo" na barra de baixo. */
        val showInBar: Boolean = true,
    ) {
        val hasSources get() = sources.isNotEmpty()
    }

    private val prefs = context.getSharedPreferences("aotv_live", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "live").apply { mkdirs() }
    private val client = com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val lock = Mutex()

    private val _state = MutableStateFlow(
        State(
            sources = readSources(),
            favorites = prefs.getStringSet("favorites", emptySet()).orEmpty(),
            recents = prefs.getString("recents", "").orEmpty().split('\n').filter { it.isNotBlank() },
            showInBar = prefs.getBoolean("show_in_bar", true),
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        // Abre com o que estava em arquivo. A atualização pela rede vem quando a tela pede.
        val s = _state.value.sources.filter { it.enabled }
        val chans = s.flatMap { readChannels(it.id)?.first.orEmpty() }
        _state.update { it.copy(channels = chans, epg = readEpg()?.first.orEmpty()) }
    }

    // ── Fontes ──────────────────────────────────────────────────────────────────

    fun addM3u(name: String, url: String, epgUrl: String): LiveSource {
        val src = LiveSource(newId(), name.trim().ifBlank { hostOf(url) }, LiveSourceType.M3U, url = url.trim(), epgUrl = epgUrl.trim())
        writeSources(_state.value.sources + src)
        return src
    }

    /** Confere a conta no servidor antes de guardar. */
    suspend fun addXtream(name: String, server: String, user: String, pass: String, epgUrl: String = ""): Result<LiveSource> = withContext(Dispatchers.IO) {
        val base = normalizeServer(server) ?: return@withContext Result.failure(IllegalArgumentException("Endereço do servidor inválido"))
        val acc = getText("$base/player_api.php?username=${enc(user)}&password=${enc(pass)}")?.let(XtreamParser::account)
            ?: return@withContext Result.failure(IllegalStateException("Servidor não aceitou usuário e senha"))
        val src = LiveSource(newId(), name.trim().ifBlank { hostOf(base) }, LiveSourceType.XTREAM, server = base, user = user.trim(), pass = pass, epgUrl = epgUrl.trim())
        writeSources(_state.value.sources + src)
        _state.update { it.copy(accounts = it.accounts + (src.id to acc)) }
        Result.success(src)
    }

    fun setEnabled(id: String, on: Boolean) {
        writeSources(_state.value.sources.map { if (it.id == id) it.copy(enabled = on) else it })
        val s = _state.value.sources.filter { it.enabled }
        _state.update { st -> st.copy(channels = s.flatMap { readChannels(it.id)?.first.orEmpty() }) }
    }

    fun remove(id: String) {
        writeSources(_state.value.sources.filterNot { it.id == id })
        File(dir, "ch_$id.json").delete()
        _state.update { st -> st.copy(channels = st.channels.filterNot { it.sourceId == id }, accounts = st.accounts - id) }
    }

    fun setShowInBar(on: Boolean) {
        prefs.edit().putBoolean("show_in_bar", on).apply()
        _state.update { it.copy(showInBar = on) }
    }

    // ── Canais e guia ───────────────────────────────────────────────────────────

    /** Atualiza canais (e depois o guia). [force] ignora o arquivo de 12 h. */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            val sources = _state.value.sources.filter { it.enabled }
            if (sources.isEmpty()) { _state.update { it.copy(channels = emptyList(), error = null) }; return@withContext }
            _state.update { it.copy(loading = true, error = null) }
            val errors = mutableListOf<String>()
            val epgUrls = LinkedHashSet<String>()
            val all = ArrayList<LiveChannel>()
            for (s in sources) {
                val cached = readChannels(s.id)
                val fresh = cached != null && System.currentTimeMillis() - cached.third < TTL
                val (chans, urls) = if (!force && fresh) cached!!.first to cached.second
                else runCatching { fetch(s) }.getOrNull()?.also { (c, u) -> writeChannels(s.id, c, u) } ?: run {
                    errors += s.name
                    (cached?.first.orEmpty()) to (cached?.second.orEmpty())
                }
                all += chans
                epgUrls += urls
                if (s.epgUrl.startsWith("http", true)) epgUrls += s.epgUrl
            }
            _state.update {
                it.copy(
                    channels = all, loading = false,
                    error = if (errors.isEmpty()) null else "Não deu para atualizar ${errors.joinToString(", ")}. Mostrando o que estava salvo.",
                )
            }
            refreshEpg(all, epgUrls.toList(), force)
        }
    }

    private fun fetch(s: LiveSource): Pair<List<LiveChannel>, List<String>>? = when (s.type) {
        LiveSourceType.M3U -> getText(s.url)?.let { M3uParser.parse(it, s.id) }?.takeIf { it.channels.isNotEmpty() }?.let { it.channels to it.epgUrls }
        LiveSourceType.XTREAM -> {
            val base = "${s.server}/player_api.php?username=${enc(s.user)}&password=${enc(s.pass)}"
            val acc = getText(base)?.let(XtreamParser::account)
            if (acc != null) _state.update { it.copy(accounts = it.accounts + (s.id to acc)) }
            val ext = if (acc == null || acc.formats.isEmpty() || "m3u8" in acc.formats) "m3u8" else "ts"
            val groups = getText("$base&action=get_live_categories")?.let(XtreamParser::categories).orEmpty()
            getText("$base&action=get_live_streams")?.let { XtreamParser.live(it, s, groups, ext) }?.takeIf { it.isNotEmpty() }
                ?.let { it to listOf("${s.server}/xmltv.php?username=${enc(s.user)}&password=${enc(s.pass)}") }
        }
    }

    private fun refreshEpg(channels: List<LiveChannel>, urls: List<String>, force: Boolean) {
        if (urls.isEmpty() || channels.isEmpty()) return
        val sig = (urls.sorted().joinToString("|") + channels.size).hashCode().toString()
        val cached = readEpg()
        if (!force && cached != null && cached.second == sig && System.currentTimeMillis() - cached.third < TTL) {
            _state.update { it.copy(epg = cached.first) }
            return
        }
        _state.update { it.copy(epgLoading = true) }
        val wanted = HashMap<String, MutableList<String>>()
        val names = HashMap<String, MutableList<String>>()
        channels.forEach { c ->
            if (c.epgId.isNotBlank()) wanted.getOrPut(c.epgId.lowercase()) { mutableListOf() } += c.id
            names.getOrPut(XmltvParser.norm(c.name)) { mutableListOf() } += c.id
        }
        val merged = HashMap<String, List<LiveProgram>>()
        for (u in urls) {
            runCatching {
                client.newCall(Request.Builder().url(u).header("User-Agent", "AOTV").build()).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val raw = BufferedInputStream(r.body!!.byteStream())
                    raw.mark(2)
                    val gz = raw.read() == 0x1f && raw.read() == 0x8b
                    raw.reset()
                    val input = if (gz || u.substringBefore('?').endsWith(".gz", true)) GZIPInputStream(raw) else raw
                    XmltvParser.parse(input, wanted, names).forEach { (k, v) -> if ((merged[k]?.size ?: 0) < v.size) merged[k] = v }
                }
            }
        }
        if (merged.isNotEmpty()) writeEpg(merged, sig)
        _state.update { it.copy(epg = if (merged.isNotEmpty()) merged else cached?.first ?: it.epg, epgLoading = false) }
    }

    fun channel(id: String): LiveChannel? = _state.value.channels.firstOrNull { it.id == id }

    /** Programa de agora e o próximo. */
    fun nowNext(id: String, now: Long = System.currentTimeMillis()): Pair<LiveProgram?, LiveProgram?> {
        val list = _state.value.epg[id] ?: return null to null
        val i = list.indexOfFirst { it.isNow(now) }
        if (i < 0) return null to list.firstOrNull { it.start > now }
        return list[i] to list.getOrNull(i + 1)
    }

    fun toggleFavorite(id: String) {
        val f = _state.value.favorites.let { if (id in it) it - id else it + id }
        prefs.edit().putStringSet("favorites", f).apply()
        _state.update { it.copy(favorites = f) }
    }

    fun markWatched(id: String) {
        val r = (listOf(id) + _state.value.recents.filterNot { it == id }).take(30)
        prefs.edit().putString("recents", r.joinToString("\n")).apply()
        _state.update { it.copy(recents = r) }
    }

    fun clearRecents() {
        prefs.edit().remove("recents").apply()
        _state.update { it.copy(recents = emptyList()) }
    }

    // ── Rede e arquivos ─────────────────────────────────────────────────────────

    private fun getText(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "AOTV").build()).execute().use {
            if (it.isSuccessful) it.body?.string() else null
        }
    }.getOrNull()

    private fun readSources(): List<LiveSource> = runCatching {
        val a = JSONArray(prefs.getString("sources", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            LiveSource(
                o.getString("id"), o.optString("name"), runCatching { LiveSourceType.valueOf(o.optString("type")) }.getOrDefault(LiveSourceType.M3U),
                o.optString("url"), o.optString("server"), o.optString("user"), o.optString("pass"), o.optString("epg"), o.optBoolean("on", true),
            )
        }
    }.getOrDefault(emptyList())

    private fun writeSources(list: List<LiveSource>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name).put("url", it.url)
                    .put("server", it.server).put("user", it.user).put("pass", it.pass).put("epg", it.epgUrl).put("on", it.enabled)
            )
        }
        prefs.edit().putString("sources", a.toString()).apply()
        _state.update { it.copy(sources = list) }
    }

    private fun writeChannels(sourceId: String, list: List<LiveChannel>, epgUrls: List<String>) = runCatching {
        val a = JSONArray()
        list.forEach { c ->
            val h = JSONObject()
            c.headers.forEach { (k, v) -> h.put(k, v) }
            a.put(
                JSONObject().put("i", c.id).put("n", c.name).put("u", c.url).put("l", c.logo).put("g", c.group)
                    .put("e", c.epgId).put("c", c.number).put("h", h)
            )
        }
        File(dir, "ch_$sourceId.json").writeText(
            JSONObject().put("at", System.currentTimeMillis()).put("epg", JSONArray(epgUrls)).put("ch", a).toString()
        )
    }

    /** Canais, guias do cabeçalho e hora da gravação. null sem arquivo. */
    private fun readChannels(sourceId: String): Triple<List<LiveChannel>, List<String>, Long>? = runCatching {
        val f = File(dir, "ch_$sourceId.json")
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        val a = o.getJSONArray("ch")
        val chans = (0 until a.length()).mapNotNull { i ->
            val c = a.optJSONObject(i) ?: return@mapNotNull null
            val h = c.optJSONObject("h")
            LiveChannel(
                c.getString("i"), sourceId, c.optString("n"), c.optString("u"), c.optString("l"), c.optString("g"),
                c.optString("e"), c.optInt("c"), h?.keys()?.asSequence()?.associateWith { k -> h.optString(k) }.orEmpty(),
            )
        }
        val epg = o.optJSONArray("epg")?.let { e -> (0 until e.length()).map { e.optString(it) } }.orEmpty()
        Triple(chans, epg, o.optLong("at"))
    }.getOrNull()

    private fun writeEpg(map: Map<String, List<LiveProgram>>, sig: String) = runCatching {
        val root = JSONObject()
        map.forEach { (k, v) ->
            val a = JSONArray()
            v.forEach { p -> a.put(JSONObject().put("s", p.start).put("e", p.stop).put("t", p.title).put("d", p.desc)) }
            root.put(k, a)
        }
        File(dir, "epg.json").writeText(JSONObject().put("at", System.currentTimeMillis()).put("sig", sig).put("p", root).toString())
    }

    private fun readEpg(): Triple<Map<String, List<LiveProgram>>, String, Long>? = runCatching {
        val f = File(dir, "epg.json")
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        val p = o.getJSONObject("p")
        val map = p.keys().asSequence().associateWith { k ->
            val a = p.getJSONArray(k)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { LiveProgram(it.getLong("s"), it.getLong("e"), it.optString("t"), it.optString("d")) } }
        }
        Triple(map, o.optString("sig"), o.optLong("at"))
    }.getOrNull()

    private fun newId() = "s" + java.lang.Long.toString(System.currentTimeMillis(), 36)

    private fun hostOf(url: String) = Regex("^[a-z]+://([^/:?]+)", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1) ?: "Lista"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val TTL = 12 * 3_600_000L

        /** "servidor.com:8080" ou "http://servidor.com:8080/player_api.php" viram a base. */
        fun normalizeServer(input: String): String? {
            val raw = input.trim().ifBlank { return null }
            val withScheme = if ("://" in raw) raw else "http://$raw"
            val base = withScheme.substringBefore("/player_api.php").substringBefore("/get.php").trimEnd('/')
            return base.takeIf { Regex("^https?://[^/\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        }
    }
}
