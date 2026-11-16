package com.aotv.app.data.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TV ao vivo. Guarda as fontes (listas M3U e contas Xtream), baixa os canais e o guia e lembra
 * favoritos e recentes. Canais e guia ficam em arquivo e a tela abre sem esperar a rede; a
 * atualização automática segue os intervalos das prefs (Lote 8: listas 24 h, guia 12 h).
 */
@Singleton
class LiveTvRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformPrefs: com.aotv.app.data.local.PlatformPrefs,
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
        /**
         * Algum addon ligado tem catálogo de TV (Lote 15, Bloco 2 follow-up,
         * v1.25.0): a aba aparece mesmo sem M3U/Xtream. Só um cache (prefs
         * "aotv_live"), atualizado quando os catálogos carregam.
         */
        val hasAddonTv: Boolean = false,
    ) {
        val hasSources get() = sources.isNotEmpty()
    }

    private val prefs = context.getSharedPreferences("aotv_live", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "live").apply { mkdirs() }
    private val client = com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val lock = Mutex()

    // Lote 8 (v1.18.0): avisos de formato Xtream da última atualização (fonte
    // que não libera o formato escolhido) e a hora da última atualização de
    // listas e de guia — o refresh automático compara com os intervalos das
    // prefs. O refresh de listas/guia NÃO é agendado em 2º plano (o WorkManager
    // do Lote 13, v1.23.0, serve só a alertas e gravações): a checagem acontece
    // ao abrir a aba Ao vivo + no manual.
    private val formatWarnings = mutableListOf<String>()
    private var lastListsAt: Long
        get() = prefs.getLong("live_last_lists_at", 0L)
        set(value) { prefs.edit().putLong("live_last_lists_at", value).apply() }
    private var lastEpgAt: Long
        get() = prefs.getLong("live_last_epg_at", 0L)
        set(value) { prefs.edit().putLong("live_last_epg_at", value).apply() }

    /** Intervalo da atualização das listas, em ms (pref por perfil, padrão 24 h). */
    fun listsIntervalMs(): Long = platformPrefs.liveRefreshListsHours.coerceIn(1, 168) * 3_600_000L

    /** Intervalo da atualização do guia, em ms (pref por perfil, padrão 12 h). */
    fun epgIntervalMs(): Long = platformPrefs.liveRefreshEpgHours.coerceIn(1, 168) * 3_600_000L

    private val _state = MutableStateFlow(
        State(
            sources = readSources(),
            favorites = prefs.getStringSet("favorites", emptySet()).orEmpty(),
            recents = prefs.getString("recents", "").orEmpty().split('\n').filter { it.isNotBlank() },
            showInBar = prefs.getBoolean("show_in_bar", true),
            hasAddonTv = prefs.getBoolean("has_addon_tv", false),
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    // Lote 15 (v1.25.0, Bloco 2 follow-up): canais de addon de TV com o
    // stream já resolvido no toque — SÓ em memória (a URL costuma trazer
    // token de validade curta e não pode ir p/ o disco); o player os
    // encontra pelo id "addon-tv:..." (ver [channel] e o estado do
    // LivePlayerViewModel). Acesso pela Main (ViewModels) — o synchronized
    // cobre leituras de outras threads.
    private val addonChannels = LinkedHashMap<String, LiveChannel>()

    /** Guarda o canal de addon com o stream resolvido (só em memória). */
    fun registerAddonChannel(channel: LiveChannel) {
        synchronized(addonChannels) { addonChannels[channel.id] = channel }
    }

    /** Canais de addon registrados (com o stream resolvido). */
    fun addonChannels(): List<LiveChannel> =
        synchronized(addonChannels) { addonChannels.values.toList() }

    /**
     * Assinatura dos addons ligados (URLs ordenadas): diz se a checagem de
     * catálogo de TV está desatualizada (addon instalado, ligado ou
     * desligado depois da última checagem).
     */
    fun addonTvSig(): String =
        platformPrefs.addons.value.filter { it.enabled }.map { it.url }.sorted().joinToString("\n")

    /** Assinatura da última checagem de catálogo de TV guardada. */
    fun addonTvCheckedSig(): String = prefs.getString("addon_tv_sig", "").orEmpty()

    /**
     * Guarda se algum addon ligado tem catálogo de TV (cache p/ a aba Ao
     * vivo aparecer sem M3U/Xtream) + a assinatura conferida. Só a aba lê
     * isto ([hasAddonTv] no [State]).
     */
    fun setAddonTvAvailable(has: Boolean) {
        prefs.edit().putBoolean("has_addon_tv", has).putString("addon_tv_sig", addonTvSig()).apply()
        _state.update { it.copy(hasAddonTv = has) }
    }

    init {
        // Abre com o que estava em arquivo. A atualização pela rede vem quando a tela pede.
        val s = _state.value.sources.filter { it.enabled }
        val chans = s.flatMap { readChannels(it.id)?.first.orEmpty() }
        _state.update { it.copy(channels = chans, epg = readEpg()?.first.orEmpty()) }
    }

    // ── Fontes ──────────────────────────────────────────────────────────────────

    /**
     * Adiciona lista M3U por endereço. Valida o endereço antes de guardar: URL
     * inacessível ou sem `#EXTM3U` volta em [Result.failure] com a mensagem real.
     */
    suspend fun addM3u(name: String, url: String, epgUrl: String): Result<LiveSource> = try {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http", true)) { "Cole o endereço da lista (http ou https)" }
        validateM3uUrl(cleanUrl)
        val src = LiveSource(newId(), name.trim().ifBlank { hostOf(cleanUrl) }, LiveSourceType.M3U, url = cleanUrl, epgUrl = epgUrl.trim())
        writeSources(_state.value.sources + src)
        Result.success(src)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Testa acessibilidade da URL M3U antes de salvar.
     * Retorna o começo do conteúdo em caso de sucesso (vazio quando o HEAD bastou).
     */
    private suspend fun validateM3uUrl(url: String): String = withContext(Dispatchers.IO) {
        val headReq = Request.Builder().url(url).head().build()
        val headResp = runCatching { client.newCall(headReq).execute() }.getOrNull()
        // Se HEAD não for suportado (405) ou falhar, tenta GET com range mínimo.
        if (headResp == null || headResp.code == 405) {
            headResp?.close()
            val getReq = Request.Builder().url(url).addHeader("Range", "bytes=0-511").build()
            client.newCall(getReq).execute().use { r ->
                if (!r.isSuccessful) throw Exception("URL inacessível (${r.code}): ${r.message}")
                val preview = r.body?.string() ?: ""
                if (preview.isNotBlank() && !preview.trimStart().startsWith("#EXTM3U")) {
                    throw Exception("URL não retornou uma playlist M3U válida")
                }
                return@withContext preview
            }
        }
        headResp?.use {
            if (!it.isSuccessful) throw Exception("URL inacessível (${it.code}): ${it.message}")
        }
        ""
    }

    /**
     * Importa playlist M3U por arquivo via SAF (Storage Access Framework).
     * [uri] vem de Intent.ACTION_OPEN_DOCUMENT. A URI fica guardada na fonte
     * ([LiveSource.isFileUri]) e o [refresh] a relê pelo ContentResolver.
     */
    suspend fun addM3uFromFile(
        context: Context,
        uri: Uri,
        name: String,
        epgUrl: String = "",
    ): Result<LiveSource> = try {
        withContext(Dispatchers.IO) {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: throw Exception("Arquivo não encontrado")
            val mimeType = doc.type ?: ""
            require(
                mimeType.contains("mpegurl", ignoreCase = true)
                    || mimeType.contains("text", ignoreCase = true)
                    || doc.name?.endsWith(".m3u", ignoreCase = true) == true
                    || doc.name?.endsWith(".m3u8", ignoreCase = true) == true
            ) { "Arquivo não reconhecido como playlist M3U (tipo: $mimeType)" }
            val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("Não foi possível ler o arquivo")
            require(content.isNotEmpty()) { "Arquivo vazio" }
            val text = content.toString(Charsets.UTF_8)
            require(text.trimStart().startsWith("#EXTM3U")) {
                "Arquivo não começa com #EXTM3U — verifique se é uma playlist válida"
            }
            // Permissão persistida p/ reler o arquivo depois (o refresh usa a URI guardada).
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val source = LiveSource(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { doc.name ?: "M3U" },
                type = LiveSourceType.M3U,
                url = uri.toString(),
                epgUrl = epgUrl,
                enabled = true,
                isFileUri = true,
            )
            val parsed = M3uParser.parse(text, source.id)
            writeSources(_state.value.sources + source)
            writeChannels(source.id, parsed.channels, parsed.epgUrls)
            _state.update { it.copy(channels = it.channels + parsed.channels) }
            val epgUrls = (parsed.epgUrls + listOfNotNull(epgUrl.takeIf { it.startsWith("http", true) })).distinct()
            refreshEpg(parsed.channels, epgUrls, false)
            Result.success(source)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Monta a URL de catch-up para um canal e programa.
     * [LiveProgram.start]/[stop] são epoch ms (como no resto do app); aqui viram segundos.
     * Suporta template explícito (`catchup-template`), `catchup-source=default`
     * (`?utc=&utcend=`), `append`, `timeshift` e Xtream nativo (`/live/` → `/timeshift/`).
     * Retorna null se o canal não tem catch-up ou o programa não é passado.
     */
    fun catchupUrl(channel: LiveChannel, program: LiveProgram): String? {
        if (channel.catchupDays <= 0) return null
        val start = program.start / 1000
        val stop = program.stop / 1000
        if (start <= 0 || stop <= 0) return null
        val nowSec = System.currentTimeMillis() / 1000
        if (start >= nowSec) return null // programa futuro — sem catch-up
        if (nowSec - start > channel.catchupDays * 86400L) return null // mais velho que catchupDays

        val baseUrl = channel.url.ifBlank { return null }
        val catchupSource = channel.catchupSource.ifBlank { null }
        val catchupTemplate = channel.catchupTemplate.ifBlank { null }

        return when {
            // 1. Template explícito na M3U (catchup-source + catchup-template)
            catchupTemplate != null -> catchupTemplate
                .replace("{utc}", "$start")
                .replace("{utcend}", "$stop")
                .replace("{Y}", java.time.format.DateTimeFormatter.ofPattern("yyyy")
                    .format(java.time.Instant.ofEpochSecond(start).atZone(java.time.ZoneOffset.UTC)))
                .replace("{m}", java.time.format.DateTimeFormatter.ofPattern("MM")
                    .format(java.time.Instant.ofEpochSecond(start).atZone(java.time.ZoneOffset.UTC)))
                .replace("{d}", java.time.format.DateTimeFormatter.ofPattern("dd")
                    .format(java.time.Instant.ofEpochSecond(start).atZone(java.time.ZoneOffset.UTC)))
                .replace("{hh}", java.time.format.DateTimeFormatter.ofPattern("HH")
                    .format(java.time.Instant.ofEpochSecond(start).atZone(java.time.ZoneOffset.UTC)))
                .replace("{duration}", "${stop - start}")

            // 2. catchup-source=default (ou ausente) → params utc/utcend
            catchupSource == "default" || catchupSource == null -> {
                val sep = if (baseUrl.contains('?')) '&' else '?'
                "$baseUrl${sep}utc=$start&utcend=$stop"
            }

            // 3. catchup-source=append
            catchupSource == "append" -> "$baseUrl$start-${stop - start}"

            // 4. catchup-source=timeshift
            catchupSource == "timeshift" -> {
                val duration = (stop - start) / 60
                val sep = if (baseUrl.contains('?')) '&' else '?'
                "$baseUrl${sep}timeshift=$duration"
            }

            // 5. Xtream: detecta padrão /live/ e usa action timeshift
            baseUrl.contains("/live/") -> {
                val parts = baseUrl.split("/live/")
                if (parts.size == 2) {
                    val serverPart = parts[0]
                    val rest = parts[1].split("/")
                    if (rest.size >= 3) {
                        val user = rest[0]
                        val pass = rest[1]
                        val streamId = rest[2].split(".")[0]
                        // Lote 8 (v1.18.0): com catch-up em HLS, a gravação vem em
                        // .m3u8 em vez de .ts. Só vale p/ o timeshift Xtream.
                        val ext = if (platformPrefs.liveCatchupHls) "m3u8" else "ts"
                        "$serverPart/timeshift/$user/$pass/${(stop - start) / 60}/$start/$streamId.$ext"
                    } else null
                } else null
            }

            else -> null
        }
    }

    /**
     * Edita metadados de uma fonte M3U existente.
     * Mantém o id e o estado enabled/disabled. Recarrega os canais se a URL mudou.
     */
    suspend fun updateM3u(
        id: String,
        name: String,
        url: String,
        epgUrl: String,
    ): Result<LiveSource> = try {
        val existing = getSource(id) ?: throw Exception("Fonte $id não encontrada")
        require(existing.type == LiveSourceType.M3U) { "Fonte $id não é M3U" }
        val cleanUrl = url.trim()
        val urlChanged = existing.url != cleanUrl
        if (urlChanged && cleanUrl.isNotBlank() && !cleanUrl.startsWith("content://")) {
            validateM3uUrl(cleanUrl)
        }
        val updated = existing.copy(name = name.trim().ifBlank { existing.name }, url = cleanUrl, epgUrl = epgUrl.trim())
        writeSources(_state.value.sources.map { if (it.id == id) updated else it })
        if (urlChanged) {
            deleteChannelsBySource(id)
            refresh(true)
        }
        Result.success(updated)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Edita credenciais de uma fonte Xtream existente.
     * Revalida antes de salvar (mesmo padrão do addXtream) e recarrega os canais.
     */
    suspend fun updateXtream(
        id: String,
        name: String,
        server: String,
        user: String,
        pass: String,
    ): Result<LiveSource> = try {
        val existing = getSource(id) ?: throw Exception("Fonte $id não encontrada")
        require(existing.type == LiveSourceType.XTREAM) { "Fonte $id não é Xtream" }
        val (base, acc) = validateXtreamAccount(server, user, pass).getOrThrow()
        val updated = existing.copy(
            name = name.trim().ifBlank { existing.name },
            server = base,
            user = user.trim(),
            pass = pass,
        )
        writeSources(_state.value.sources.map { if (it.id == id) updated else it })
        _state.update { it.copy(accounts = it.accounts + (id to acc)) }
        deleteChannelsBySource(id)
        refresh(true)
        Result.success(updated)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Confere usuário e senha no servidor Xtream. Devolve a base normalizada e a conta. */
    private suspend fun validateXtreamAccount(
        server: String,
        user: String,
        pass: String,
    ): Result<Pair<String, XtreamAccount>> = withContext(Dispatchers.IO) {
        val base = normalizeServer(server)
            ?: return@withContext Result.failure(IllegalArgumentException("Endereço do servidor inválido"))
        val acc = getText("$base/player_api.php?username=${enc(user)}&password=${enc(pass)}")?.let(XtreamParser::account)
            ?: return@withContext Result.failure(IllegalStateException("Servidor não aceitou usuário e senha"))
        Result.success(base to acc)
    }

    private fun getSource(id: String): LiveSource? = _state.value.sources.firstOrNull { it.id == id }

    /** Apaga o cache de canais de uma fonte (p/ recarregar depois de editar). */
    private fun deleteChannelsBySource(id: String) {
        File(dir, "ch_$id.json").delete()
        _state.update { st -> st.copy(channels = st.channels.filterNot { it.sourceId == id }) }
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

    /** Atualiza canais (e depois o guia). [force] ignora o arquivo e baixa tudo de novo. */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            val sources = _state.value.sources.filter { it.enabled }
            if (sources.isEmpty()) { _state.update { it.copy(channels = emptyList(), error = null) }; return@withContext }
            _state.update { it.copy(loading = true, error = null) }
            formatWarnings.clear()
            val errors = mutableListOf<String>()
            val epgUrls = LinkedHashSet<String>()
            val all = ArrayList<LiveChannel>()
            for (s in sources) {
                val cached = readChannels(s.id)
                val fresh = cached != null && System.currentTimeMillis() - cached.third < listsIntervalMs()
                val (chans, urls) = if (!force && fresh) cached!!.first to cached.second
                else runCatching { fetch(s) }.getOrNull()?.also { (c, u) -> writeChannels(s.id, c, u) } ?: run {
                    errors += s.name
                    (cached?.first.orEmpty()) to (cached?.second.orEmpty())
                }
                all += chans
                epgUrls += urls
                if (s.epgUrl.startsWith("http", true)) epgUrls += s.epgUrl
            }
            lastListsAt = System.currentTimeMillis()
            val errorParts = mutableListOf<String>()
            if (errors.isNotEmpty()) errorParts += "Não deu para atualizar ${errors.joinToString(", ")}. Mostrando o que estava salvo."
            if (formatWarnings.isNotEmpty()) {
                val label = if (platformPrefs.liveXtreamFormat == "hls") "HLS" else "TS"
                errorParts += "Formato $label indisponível em ${formatWarnings.joinToString(", ")} — usando o formato da conta."
            }
            _state.update {
                it.copy(
                    channels = all, loading = false,
                    error = errorParts.joinToString(" ").takeIf { it.isNotBlank() },
                )
            }
            refreshEpg(all, epgUrls.toList(), force)
        }
    }

    /**
     * Atualiza sozinho ao abrir a aba Ao vivo (Lote 8, v1.18.0): se passou o
     * intervalo das listas, baixa tudo; senão, só o guia se passou o dele.
     * Não há agendamento em 2º plano (sem WorkManager no app) — a checagem
     * é na abertura + no botão "Atualizar agora".
     */
    suspend fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastListsAt >= listsIntervalMs()) refresh(true) else refresh(false)
    }

    /**
     * Extensão do stream Xtream (Lote 8, v1.18.0). "auto" usa o da conta
     * (m3u8 quando a conta deixa, senão ts); "ts"/"hls" forçam o formato.
     * Se a conta não libera o formato escolhido, volta ao da conta e anota
     * o aviso, que o [refresh] mostra como erro real na tela.
     */
    private fun xtreamExt(acc: XtreamAccount?, sourceName: String): String {
        val auto = if (acc == null || acc.formats.isEmpty() || "m3u8" in acc.formats) "m3u8" else "ts"
        return when (platformPrefs.liveXtreamFormat) {
            "ts" -> {
                if (acc != null && acc.formats.isNotEmpty() && "ts" !in acc.formats) {
                    formatWarnings += sourceName
                    auto
                } else "ts"
            }
            "hls" -> {
                if (acc != null && acc.formats.isNotEmpty() && "m3u8" !in acc.formats) {
                    formatWarnings += sourceName
                    auto
                } else "m3u8"
            }
            else -> auto
        }
    }

    private fun fetch(s: LiveSource): Pair<List<LiveChannel>, List<String>>? = when (s.type) {
        LiveSourceType.M3U -> {
            // Lista por arquivo (SAF): relê pelo ContentResolver, não por HTTP.
            val text = if (s.isFileUri || s.url.startsWith("content://", true)) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(s.url))
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
            } else getText(s.url)
            text?.let { M3uParser.parse(it, s.id) }?.takeIf { it.channels.isNotEmpty() }?.let { it.channels to it.epgUrls }
        }
        LiveSourceType.XTREAM -> {
            val base = "${s.server}/player_api.php?username=${enc(s.user)}&password=${enc(s.pass)}"
            val acc = getText(base)?.let(XtreamParser::account)
            if (acc != null) _state.update { it.copy(accounts = it.accounts + (s.id to acc)) }
            val ext = xtreamExt(acc, s.name)
            val groups = getText("$base&action=get_live_categories")?.let(XtreamParser::categories).orEmpty()
            getText("$base&action=get_live_streams")?.let { XtreamParser.live(it, s, groups, ext) }?.takeIf { it.isNotEmpty() }
                ?.let { it to listOf("${s.server}/xmltv.php?username=${enc(s.user)}&password=${enc(s.pass)}") }
        }
    }

    // ── On Demand Xtream (Lote 9, v1.19.0) ───────────────────────────────────

    /** Contas Xtream ativas. O On Demand usa SÓ elas — nenhuma outra forma de login. */
    fun xtreamSources(): List<LiveSource> =
        _state.value.sources.filter { it.enabled && it.type == LiveSourceType.XTREAM }

    /** Base do player_api.php de uma fonte Xtream. */
    private fun xtreamApi(s: LiveSource) =
        "${s.server}/player_api.php?username=${enc(s.user)}&password=${enc(s.pass)}"

    /**
     * Extensão do VOD: a que a API declara (container_extension) quando vem;
     * sem ela, a do formato Xtream configurado ([xtreamExt]).
     */
    private fun vodExt(acc: XtreamAccount?, sourceName: String, containerExt: String): String {
        val ext = containerExt.trim().lowercase()
        if (ext.isNotBlank()) return ext
        return xtreamExt(acc, sourceName)
    }

    /** URL do filme Xtream: /movie/usuario/senha/id.ext (mesmo padrão do ao vivo). */
    fun vodStreamUrl(source: LiveSource, item: VodItem): String {
        val base = source.server.trimEnd('/')
        val ext = vodExt(_state.value.accounts[source.id], source.name, item.containerExtension)
        return "$base/movie/${enc(source.user)}/${enc(source.pass)}/${item.id}.$ext"
    }

    /** URL do episódio Xtream: /series/usuario/senha/idEpisodio.ext. */
    fun episodeStreamUrl(source: LiveSource, episode: VodEpisode): String {
        val base = source.server.trimEnd('/')
        val ext = vodExt(_state.value.accounts[source.id], source.name, episode.containerExtension)
        return "$base/series/${enc(source.user)}/${enc(source.pass)}/${episode.id}.$ext"
    }

    /**
     * On Demand de uma conta Xtream: categorias + filmes (get_vod_streams) ou
     * séries (get_series). Devolve null quando a LISTA não veio (rede/servidor
     * falhou) — lista vazia é honesta (a conta não tem nada ali), não erro.
     */
    suspend fun fetchOnDemand(source: LiveSource, series: Boolean): Pair<List<VodCategory>, List<VodItem>>? = withContext(Dispatchers.IO) {
        val base = xtreamApi(source)
        val catAction = if (series) "get_series_categories" else "get_vod_categories"
        val listAction = if (series) "get_series" else "get_vod_streams"
        val catText = getText("$base&action=$catAction")
        val groups = catText?.let(XtreamParser::categories).orEmpty()
        val cats = catText?.let(XtreamParser::categoryList).orEmpty()
        val listText = getText("$base&action=$listAction") ?: return@withContext null
        cats.ifEmpty { groups.entries.map { VodCategory(it.key, it.value) } } to
            XtreamParser.vod(listText, source, groups, series)
    }

    /** Detalhe de uma série (get_series_info): info + episódios por temporada. */
    suspend fun fetchSeriesDetail(source: LiveSource, seriesId: String): VodSeriesDetail? = withContext(Dispatchers.IO) {
        val base = xtreamApi(source)
        getText("$base&action=get_series_info&series_id=${enc(seriesId)}")
            ?.let { XtreamParser.seriesInfo(it, seriesId, source) }
    }

    /** Detalhe de um filme (get_vod_info): preenche os campos que a lista não trouxe. */
    suspend fun fetchVodInfo(source: LiveSource, item: VodItem): VodItem = withContext(Dispatchers.IO) {
        val base = xtreamApi(source)
        getText("$base&action=get_vod_info&vod_id=${enc(item.id)}")
            ?.let { XtreamParser.vodInfo(it, item) } ?: item
    }

    private fun refreshEpg(channels: List<LiveChannel>, urls: List<String>, force: Boolean) {
        if (urls.isEmpty() || channels.isEmpty()) return
        // Lote 8 (v1.18.0): a janela do guia vem das prefs (dias à frente e
        // horas para trás); mudar a janela invalida o cache guardado.
        val daysAhead = platformPrefs.liveGuideDaysAhead.coerceIn(1, 7)
        val hoursBack = platformPrefs.liveGuideHoursBack.coerceIn(0, 24)
        val sig = (urls.sorted().joinToString("|") + channels.size + "|$daysAhead|$hoursBack").hashCode().toString()
        val cached = readEpg()
        if (!force && cached != null && cached.second == sig && System.currentTimeMillis() - cached.third < epgIntervalMs()) {
            _state.update { it.copy(epg = cached.first) }
            lastEpgAt = System.currentTimeMillis()
            return
        }
        _state.update { it.copy(epgLoading = true) }
        val wanted = HashMap<String, MutableList<String>>()
        val names = HashMap<String, MutableList<String>>()
        channels.forEach { c ->
            // Lote 12 (v1.22.0): o Assign EPG manual também entra no
            // "wanted" — o guia associado é baixado junto com o resto.
            val effId = effectiveEpgId(c)
            if (effId.isNotBlank()) wanted.getOrPut(effId.lowercase()) { mutableListOf() } += c.id
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
                    XmltvParser.parse(input, wanted, names, hoursBack = hoursBack, daysAhead = daysAhead).forEach { (k, v) -> if ((merged[k]?.size ?: 0) < v.size) merged[k] = v }
                }
            }
        }
        if (merged.isNotEmpty()) {
            writeEpg(merged, sig)
            lastEpgAt = System.currentTimeMillis()
        }
        _state.update { it.copy(epg = if (merged.isNotEmpty()) merged else cached?.first ?: it.epg, epgLoading = false) }
    }

    /**
     * Canal pelo id — o de addon resolvido vem primeiro (só em memória, ver
     * [registerAddonChannel]); sem ele, o da lista M3U/Xtream.
     */
    fun channel(id: String): LiveChannel? =
        synchronized(addonChannels) { addonChannels[id] }
            ?: _state.value.channels.firstOrNull { it.id == id }

    // ── Lote 12 (v1.22.0) — Gerenciar canais + Assign EPG + time shift ──
    // As personalizações do usuário (por perfil, em PlatformPrefs) valem em
    // TODAS as vistas: quem lê canais p/ exibir usa [displayChannels] e
    // quem lê o guia usa [epgForChannel] — nunca a lista crua do estado.

    /** Aplica nome, número, grupo e logo próprios de um canal (vazio = o da lista). */
    fun displayChannel(c: LiveChannel): LiveChannel {
        val names = platformPrefs.liveChannelNames
        val groups = platformPrefs.liveChannelGroups
        val numbers = platformPrefs.liveChannelNumbers
        val logos = platformPrefs.liveChannelLogos
        val name = names[c.id]
        val group = groups[c.id]
        val number = numbers[c.id]
        val logo = logos[c.id]
        if (name == null && group == null && number == null && logo == null) return c
        return c.copy(
            name = name ?: c.name,
            group = group ?: c.group,
            number = number ?: c.number,
            logo = logo ?: c.logo,
        )
    }

    /** Canais visíveis, com as personalizações aplicadas e na ordem própria. */
    fun displayChannels(channels: List<LiveChannel> = _state.value.channels): List<LiveChannel> {
        val hidden = platformPrefs.liveHiddenChannels.toSet()
        val order = platformPrefs.liveChannelOrder
        val visible = channels.filterNot { it.id in hidden }.map { displayChannel(it) }
        if (order.isEmpty()) return visible
        val pos = order.withIndex().associate { it.value to it.index }
        // Canais na ordem própria primeiro; fora da lista, na ordem da fonte.
        return visible.sortedWith(compareBy({ pos[it.id] ?: Int.MAX_VALUE }, { visible.indexOf(it) }))
    }

    /** Id do guia efetivo de um canal (Assign EPG manual, ou o tvg-id da lista). */
    fun effectiveEpgId(channel: LiveChannel): String =
        platformPrefs.liveEpgAssign[channel.id]?.takeIf { it.isNotBlank() } ?: channel.epgId

    /** Deslocamento do guia de um canal, em horas (-24..+24; 0 = sem deslocar). */
    fun epgShiftHours(channelId: String): Int = platformPrefs.liveEpgShift[channelId] ?: 0

    /**
     * Guia de um canal com Assign EPG + time shift aplicados. O deslocamento
     * move TODOS os programas do canal (o "agora" casa com o guia deslocado);
     * só vale p/ este canal. Sem associação nem deslocamento, é o guia cru.
     */
    fun epgForChannel(channelId: String): List<LiveProgram> {
        // O Assign EPG já vale no download (ver refreshEpg): o guia
        // associado chega guardado sob o id deste canal. Aqui só desloca.
        val base = _state.value.epg[channelId].orEmpty()
        val shift = platformPrefs.liveEpgShift[channelId] ?: 0
        if (shift == 0) return base
        val delta = shift * 3_600_000L
        return base.map { it.copy(start = it.start + delta, stop = it.stop + delta) }
    }

    /** Hora da última atualização do guia (epoch ms, 0 = nunca) — p/ a grade mostrar "guia salvo de HH:mm". */
    fun lastEpgAtMs(): Long = lastEpgAt

    /**
     * Guia instantâneo (Lote 12, v1.22.0): a grade abre na hora com o EPG
     * salvo em disco (o init já o carrega); aqui só atualiza em 2º plano
     * quando o app está aberto e passou o intervalo da pref — sem
     * WorkManager (não há agendamento com o app fechado) e sem tocar nas
     * listas. Sem efeito quando o guia ainda está fresco.
     */
    suspend fun refreshEpgIfStale() = lock.withLock {
        withContext(Dispatchers.IO) {
            if (System.currentTimeMillis() - lastEpgAt < epgIntervalMs()) return@withContext
            val sources = _state.value.sources.filter { it.enabled }
            if (sources.isEmpty()) return@withContext
            val all = sources.flatMap { readChannels(it.id)?.first.orEmpty() }
            val urls = LinkedHashSet<String>()
            sources.forEach { s ->
                readChannels(s.id)?.second?.let { urls += it }
                if (s.epgUrl.startsWith("http", true)) urls += s.epgUrl
            }
            refreshEpg(all, urls.toList(), false)
        }
    }

    /** Programa de agora e o próximo (com Assign EPG + time shift do canal). */
    fun nowNext(id: String, now: Long = System.currentTimeMillis()): Pair<LiveProgram?, LiveProgram?> {
        val list = epgForChannel(id)
        if (list.isEmpty()) return null to null
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
                o.optBoolean("file", false),
            )
        }
    }.getOrDefault(emptyList())

    private fun writeSources(list: List<LiveSource>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name).put("url", it.url)
                    .put("server", it.server).put("user", it.user).put("pass", it.pass).put("epg", it.epgUrl).put("on", it.enabled)
                    .put("file", it.isFileUri)
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
                    .put("cd", c.catchupDays).put("cs", c.catchupSource).put("ct", c.catchupTemplate)
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
                c.optInt("cd"), c.optString("cs"), c.optString("ct"),
            )
        }
        val epg = o.optJSONArray("epg")?.let { e -> (0 until e.length()).map { e.optString(it) } }.orEmpty()
        Triple(chans, epg, o.optLong("at"))
    }.getOrNull()

    private fun writeEpg(map: Map<String, List<LiveProgram>>, sig: String) = runCatching {
        val root = JSONObject()
        map.forEach { (k, v) ->
            val a = JSONArray()
            v.forEach { p -> a.put(JSONObject().put("s", p.start).put("e", p.stop).put("t", p.title).put("d", p.desc).put("i", p.image)) }
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
            (0 until a.length()).map { i -> a.getJSONObject(i).let { LiveProgram(it.getLong("s"), it.getLong("e"), it.optString("t"), it.optString("d"), it.optString("i")) } }
        }
        Triple(map, o.optString("sig"), o.optLong("at"))
    }.getOrNull()

    private fun newId() = "s" + java.lang.Long.toString(System.currentTimeMillis(), 36)

    private fun hostOf(url: String) = Regex("^[a-z]+://([^/:?]+)", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1) ?: "Lista"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        /** "servidor.com:8080" ou "http://servidor.com:8080/player_api.php" viram a base. */
        fun normalizeServer(input: String): String? {
            val raw = input.trim().ifBlank { return null }
            val withScheme = if ("://" in raw) raw else "http://$raw"
            val base = withScheme.substringBefore("/player_api.php").substringBefore("/get.php").trimEnd('/')
            return base.takeIf { Regex("^https?://[^/\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        }
    }
}
