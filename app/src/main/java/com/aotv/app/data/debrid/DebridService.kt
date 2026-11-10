package com.aotv.app.data.debrid

import android.content.Context
import com.aotv.app.data.torrent.TorrentMagnet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debrid do app. Guarda as contas (do aparelho), diz quais torrents o serviço tem prontos e
 * troca o magnet pelo link direto na hora de tocar. O magnet continua sendo a fonte guardada.
 */
@Singleton
class DebridService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("aotv_debrid", Context.MODE_PRIVATE)
    private val http = DebridHttp(
        com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
            .callTimeout(25, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    )
    private val rd = RealDebridApi(http)
    private val tb = TorBoxApi(http)
    private val pm = PremiumizeApi(http)

    private val _accounts = MutableStateFlow(readAccounts())
    val accounts: StateFlow<List<DebridAccount>> = _accounts.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<DebridSettings> = _settings.asStateFlow()

    /** Conta que resolve os torrents agora. null com debrid desligado ou sem conta. */
    fun active(): DebridAccount? {
        val s = _settings.value
        if (!s.enabled) return null
        val list = _accounts.value
        return list.firstOrNull { it.provider.id == s.preferred } ?: list.firstOrNull()
    }

    // ── Contas ──────────────────────────────────────────────────────────────────

    /** Conecta com a chave colada. Confere no serviço antes de guardar. */
    suspend fun connectKey(provider: DebridProvider, key: String): Result<DebridAccount> = withContext(Dispatchers.IO) {
        val k = key.trim()
        if (k.length < 8) return@withContext Result.failure(IllegalArgumentException("Chave curta demais"))
        val info = when (provider) {
            DebridProvider.REAL_DEBRID -> rd.user(k)
            DebridProvider.TORBOX -> tb.user(k)
            DebridProvider.PREMIUMIZE -> pm.user(k)
        } ?: return@withContext Result.failure(IllegalStateException("${provider.label} não aceitou essa chave"))
        val acc = DebridAccount(provider, k, info.first, info.second)
        save(acc)
        Result.success(acc)
    }

    suspend fun startDevice(provider: DebridProvider): Result<DeviceCode> = withContext(Dispatchers.IO) {
        val d = when (provider) {
            DebridProvider.REAL_DEBRID -> rd.deviceStart()
            DebridProvider.TORBOX -> tb.deviceStart()
            DebridProvider.PREMIUMIZE -> null
        }
        if (d == null) Result.failure(IllegalStateException("${provider.label} não deu o código agora. Tente de novo ou use a chave."))
        else Result.success(d)
    }

    /** Uma consulta. Quem chama repete no intervalo do código até sair de Pending. */
    suspend fun pollDevice(d: DeviceCode): DevicePoll = withContext(Dispatchers.IO) {
        val r = when (d.provider) {
            DebridProvider.REAL_DEBRID -> rd.devicePoll(d)
            DebridProvider.TORBOX -> tb.devicePoll(d)
            DebridProvider.PREMIUMIZE -> DevicePoll.Failed("Premiumize só entra com a chave")
        }
        if (r is DevicePoll.Done) save(r.account)
        r
    }

    fun disconnect(provider: DebridProvider) {
        val list = _accounts.value.filterNot { it.provider == provider }
        writeAccounts(list)
        cache.keys.removeAll { it.startsWith(provider.id + ":") }
        resolved.keys.removeAll { it.startsWith(provider.id + ":") }
    }

    fun setEnabled(on: Boolean) = writeSettings(_settings.value.copy(enabled = on))
    fun setPreferred(p: DebridProvider) = writeSettings(_settings.value.copy(preferred = p.id))
    fun setHideUncached(on: Boolean) = writeSettings(_settings.value.copy(hideUncached = on))

    private fun save(acc: DebridAccount) {
        writeAccounts(_accounts.value.filterNot { it.provider == acc.provider } + acc)
    }

    // ── Cache do serviço ────────────────────────────────────────────────────────

    /** "provedor:hash" → o serviço tem pronto. Vale enquanto o app está aberto. */
    private val cache = ConcurrentHashMap<String, Boolean>()

    /** O que já se sabe do torrent no serviço ativo. null quando ainda não foi consultado. */
    fun knownCached(magnetOrHash: String?): Boolean? {
        val a = active() ?: return null
        val h = hashOf(magnetOrHash) ?: return null
        return cache["${a.provider.id}:$h"]
    }

    /**
     * Consulta de uma vez os torrents da lista. TorBox e Premiumize respondem em lote. O
     * Real-Debrid não tem consulta, então ele confere só os [rdTop] primeiros tentando resolver
     * (o que já deixa o link pronto para tocar na hora).
     */
    suspend fun check(magnets: List<String>, season: Int = 0, episode: Int = 0, rdTop: Int = 3): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val a = active() ?: return@withContext emptyMap()
        val hashes = magnets.mapNotNull { hashOf(it) }.distinct()
        val todo = hashes.filter { cache["${a.provider.id}:$it"] == null }
        when (a.provider) {
            DebridProvider.TORBOX, DebridProvider.PREMIUMIZE -> if (todo.isNotEmpty()) {
                val ok = if (a.provider == DebridProvider.TORBOX) tb.cached(a.token, todo) else pm.cached(a.token, todo)
                todo.forEach { cache["${a.provider.id}:$it"] = it in ok }
            }
            DebridProvider.REAL_DEBRID -> magnets.filter { m -> hashOf(m) in todo }.take(rdTop).forEach { resolve(it, season, episode) }
        }
        hashes.mapNotNull { h -> cache["${a.provider.id}:$h"]?.let { h to it } }.toMap()
    }

    // ── Link direto ─────────────────────────────────────────────────────────────

    private data class Hit(val result: DebridResolve.Ok, val at: Long)
    /** Link pronto por 15 min (o serviço dá algumas horas, mas a fonte pode mudar). */
    private val resolved = ConcurrentHashMap<String, Hit>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<DebridResolve>>()

    /** Link já resolvido para o magnet, sem rede. Serve para o Chromecast. */
    fun resolvedNow(magnet: String?, season: Int = 0, episode: Int = 0): String? {
        val a = active() ?: return null
        val k = keyOf(a, magnet ?: return null, season, episode) ?: return null
        return resolved[k]?.takeIf { System.currentTimeMillis() - it.at < RESOLVED_TTL }?.result?.url
    }

    suspend fun resolve(magnet: String, season: Int = 0, episode: Int = 0): DebridResolve {
        var a = active() ?: return DebridResolve.NoAccount
        val spec = TorrentMagnet.parse(magnet) ?: return DebridResolve.Failed("Fonte sem torrent válido")
        val key = keyOf(a, magnet, season, episode)!!
        resolved[key]?.let { if (System.currentTimeMillis() - it.at < RESOLVED_TTL) return it.result }
        val mine = CompletableDeferred<DebridResolve>()
        val running = inFlight.putIfAbsent(key, mine)
        if (running != null) return running.await()
        var result: DebridResolve = DebridResolve.Failed("Cancelado")
        try {
            result = try {
                withContext(Dispatchers.IO) {
                    if (a.provider == DebridProvider.REAL_DEBRID && a.expiresAt > 0 && a.expiresAt - System.currentTimeMillis() < 120_000) {
                        rd.refresh(a)?.let { save(it); a = it }
                    }
                    // Magnet limpo (hash, trackers, nome) é o que os serviços entendem.
                    val clean = TorrentMagnet.forEngine(spec) + (spec.fileName?.let { "&dn=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
                    when (a.provider) {
                        DebridProvider.REAL_DEBRID -> rd.resolve(a.token, clean, spec, season, episode)
                        DebridProvider.TORBOX -> tb.resolve(a.token, clean, spec, season, episode)
                        DebridProvider.PREMIUMIZE -> pm.resolve(a.token, clean, spec, season, episode)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DebridResolve.Failed(e.message ?: "Falha no ${a.provider.label}")
            }
            when (val r = result) {
                is DebridResolve.Ok -> { resolved[key] = Hit(r, System.currentTimeMillis()); cache["${a.provider.id}:${spec.infoHash}"] = true }
                DebridResolve.NotCached -> cache["${a.provider.id}:${spec.infoHash}"] = false
                else -> Unit
            }
            return result
        } finally {
            // Sai da fila mesmo cancelado, senão o próximo pedido do mesmo torrent esperaria para sempre.
            inFlight.remove(key, mine)
            mine.complete(result)
        }
    }

    private fun keyOf(a: DebridAccount, magnet: String, season: Int, episode: Int): String? {
        val s = TorrentMagnet.parse(magnet) ?: return null
        return "${a.provider.id}:${s.infoHash}:${s.fileIdx}:${s.fileName}:${s.mustInclude}:$season:$episode"
    }

    private fun hashOf(magnetOrHash: String?): String? {
        if (magnetOrHash == null) return null
        if (Regex("^[0-9a-fA-F]{40}$").matches(magnetOrHash)) return magnetOrHash.lowercase()
        return TorrentMagnet.parse(magnetOrHash)?.infoHash
    }

    // ── Persistência ────────────────────────────────────────────────────────────

    private fun readAccounts(): List<DebridAccount> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("accounts", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val p = DebridProvider.of(o.optString("p")) ?: return@mapNotNull null
            DebridAccount(
                p, o.optString("t"), o.optString("u"), o.optLong("pu"),
                o.optString("r"), o.optString("ci"), o.optString("cs"), o.optLong("ex"),
            ).takeIf { it.token.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun writeAccounts(list: List<DebridAccount>) {
        val arr = org.json.JSONArray()
        list.forEach {
            arr.put(
                org.json.JSONObject().put("p", it.provider.id).put("t", it.token).put("u", it.user).put("pu", it.premiumUntil)
                    .put("r", it.refresh).put("ci", it.clientId).put("cs", it.clientSecret).put("ex", it.expiresAt)
            )
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
        _accounts.value = list
    }

    private fun readSettings() = DebridSettings(
        enabled = prefs.getBoolean("enabled", true),
        preferred = prefs.getString("preferred", "").orEmpty(),
        hideUncached = prefs.getBoolean("hide_uncached", false),
    )

    private fun writeSettings(s: DebridSettings) {
        prefs.edit().putBoolean("enabled", s.enabled).putString("preferred", s.preferred).putBoolean("hide_uncached", s.hideUncached).apply()
        _settings.value = s
    }

    private companion object {
        const val RESOLVED_TTL = 15 * 60_000L
    }
}
