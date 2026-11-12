package com.aotv.app.data.kodi

import android.content.Context
import android.net.Uri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor de plugins Kodi (v1.2, Fase 2).
 *
 * Despacha `plugin://` via Chaquopy (aotv_kodi.run_plugin) e devolve o
 * resultado já classificado: listagem de diretório, URL resolvida
 * (setResolvedUrl), vazio ou falha. O dispatch é genérico — não chama
 * nada interno dos addons (o default.py do Vulgata é ofuscado; só o
 * contrato plugin:// + a coleta do xbmcplugin).
 *
 * Decisão de roteamento (o Vulgata marca TODOS os itens do menu com
 * isFolder=False, então o flag NÃO decide nada aqui): o resultado do
 * dispatch decide — se voltou lista, é navegação; se voltou URL
 * resolvida, é playback; se não voltou nada, é ação sem retorno
 * (ex.: Configurações, que só registra a chamada). Isso cobre os dois
 * casos sem depender do isFolder do addon.
 *
 * O Python roda em Dispatchers.Default (nunca na main): o dispatch pode
 * demorar segundos (init do .so + rede do addon). O aotv_kodi.py já
 * serializa os dispatches (_run_lock — um addon por vez).
 */
@Singleton
class KodiPluginRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addonStore: KodiAddonStore,
) {

    /** Um item de diretório devolvido pelo plugin (tradução do ListItem). */
    data class KodiItem(
        val label: String,
        val label2: String,
        /** URL original do item (plugin://... ou caminho p/ main.py). */
        val url: String,
        /** Flag do addon (só dica p/ o ícone — NÃO decide navegação). */
        val isFolder: Boolean,
        /** Art (thumb/poster/icon/fanart) — valores http(s) ou locais. */
        val art: Map<String, String> = emptyMap(),
        /** Info (title/plot/genre/year...) — tudo como string. */
        val info: Map<String, String> = emptyMap(),
        val mimeType: String = "",
    ) {
        /** Título p/ exibição (info.title tem precedência sobre o label). */
        val title: String get() = cleanKodiText(info["title"].orEmpty().ifBlank { label })
        /** Sinopse p/ exibição (plot da info ou o label2). */
        val plot: String get() = cleanKodiText(info["plot"].orEmpty().ifBlank { label2 })
        /** Miniatura p/ exibição: poster, thumb ou icon — só http(s). */
        val thumbUrl: String? get() {
            val cand = art["poster"].orEmpty().ifBlank { art["thumb"].orEmpty() }
                .ifBlank { art["icon"].orEmpty() }
            return cand.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }

    /** Resultado classificado de um dispatch. */
    sealed interface KodiOpenResult {
        /** O plugin devolveu uma listagem — navegar p/ ela. */
        data class Listing(val items: List<KodiItem>) : KodiOpenResult
        /** O plugin resolveu um stream — tocar esta URL no ExoPlayer.
         *  v1.2.7: carrega o `diag` do dispatch junto, p/ ele aparecer na
         *  tela de erro do player (antes o diag só ia p/ o Empty e se
         *  perdia no caminho do play — era por isso que o Davi via só o
         *  "Fonte indisponível", sem diagnóstico). */
        data class Resolved(val url: String, val diag: String = "") : KodiOpenResult
        /** O plugin rodou mas não devolveu lista nem URL (ação sem retorno). */
        data class Empty(val message: String, val diag: String = "") : KodiOpenResult
        /** O dispatch falhou (addon ausente, erro do plugin, erro Python). */
        data class Failed(val error: String) : KodiOpenResult
    }

    /** URL plugin:// decomposta (addon, caminho, query). */
    data class PluginUrl(val addonId: String, val path: String, val query: String)

    /**
     * Despacha um diretório do addon e classifica o resultado.
     * path/query vazios = raiz do addon (menu).
     */
    suspend fun open(
        addonId: String,
        path: String = "",
        query: String = "",
    ): KodiOpenResult = withContext(Dispatchers.Default) {
        val addon = addonStore.getAddon(addonId)
            ?: return@withContext KodiOpenResult.Failed("Addon não instalado: $addonId")
        if (!addonStore.isEnabled(addonId)) {
            return@withContext KodiOpenResult.Failed("Addon desativado: ${addon.name}")
        }
        try {
            ensurePython()
            val res = Python.getInstance().getModule("aotv_kodi")
                .callAttr("run_plugin", addonId, path, query, 1)
            parseResult(res)
        } catch (e: Exception) {
            KodiOpenResult.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Inicia o Python (só na 1ª vez) e configura os caminhos special://. */
    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        Python.getInstance().getModule("aotv_kodi").callAttr(
            "configure",
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
        )
    }

    /** Classifica o dict devolvido pelo run_plugin (ver aotv_kodi.py). */
    private fun parseResult(res: com.chaquo.python.PyObject): KodiOpenResult {
        val error = dictStr(res, "error")
        val resolved = dictStr(res, "resolved_url")
        val succeeded = dictBool(res, "succeeded")
        val items = parseItems(res)
        // Diagnóstico do lookup do run(argv) (aotv_kodi.py) — vai p/
        // a tela de estado vazio, p/ o Davi mandar o print quando o
        // 2º nível voltar vazio no celular. v1.2.7: vai TAMBÉM no
        // Resolved, p/ aparecer na tela de erro do player quando o
        // ExoPlayer não carregar o stream resolvido.
        val diag = dictStr(res, "diag")
        return when {
            items.isNotEmpty() -> KodiOpenResult.Listing(items)
            resolved.isNotBlank() && succeeded -> KodiOpenResult.Resolved(resolved, diag)
            resolved.isNotBlank() -> KodiOpenResult.Failed(
                "O addon não conseguiu resolver este item."
            )
            error.isNotBlank() -> KodiOpenResult.Failed(shortError(error))
            else -> KodiOpenResult.Empty(
                "O addon não devolveu itens para esta ação.", diag
            )
        }
    }

    /** Itens da lista devolvida (dict "items" do run_plugin). */
    private fun parseItems(res: com.chaquo.python.PyObject): List<KodiItem> {
        val listObj = runCatching { res.callAttr("__getitem__", "items") }.getOrNull()
            ?: return emptyList()
        val n = runCatching { listObj.callAttr("__len__").toString().toInt() }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<KodiItem>(n)
        for (i in 0 until n) {
            val item = runCatching { listObj.callAttr("__getitem__", i) }.getOrNull()
                ?: continue
            val label = dictStr(item, "label")
            val url = dictStr(item, "url").ifBlank { dictStr(item, "path") }
            if (label.isBlank() && url.isBlank()) continue
            out += KodiItem(
                label = label.ifBlank { url },
                label2 = dictStr(item, "label2"),
                url = url,
                isFolder = dictBool(item, "is_folder"),
                art = dictStrMap(item, "art"),
                info = dictStrMap(item, "info"),
                mimeType = dictStr(item, "mime_type"),
            )
        }
        return out
    }

    /** Erro curto p/ a UI (traceback do Python pode ter dezenas de linhas). */
    private fun shortError(error: String): String {
        val first = error.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return if (first.length > 300) first.take(300) + "…" else first.ifBlank { error.take(300) }
    }

    private fun dictStr(dict: com.chaquo.python.PyObject, key: String): String =
        runCatching { dict.callAttr("__getitem__", key).toString() }.getOrDefault("")

    private fun dictBool(dict: com.chaquo.python.PyObject, key: String): Boolean =
        runCatching { dict.callAttr("__getitem__", key).toBoolean() }.getOrDefault(false)

    /**
     * Sub-dict de strings (art/info) — via toJava com fallback p/ vazio.
     * Valores não-string (ex.: year int) viram string pelo toString().
     */
    private fun dictStrMap(dict: com.chaquo.python.PyObject, key: String): Map<String, String> {
        val sub = runCatching { dict.callAttr("__getitem__", key) }.getOrNull()
            ?: return emptyMap()
        val map = runCatching {
            @Suppress("UNCHECKED_CAST")
            sub.toJava(Map::class.java) as Map<*, *>
        }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, String>(map.size)
        for ((k, v) in map) {
            val ks = k?.toString().orEmpty()
            if (ks.isNotBlank()) out[ks] = v?.toString().orEmpty()
        }
        return out
    }

    companion object {
        /**
         * Tags de formatação do Kodi ([B], [COLOR x], [CR]...) — o
         * BrazucaPlay enche os labels delas; o Text do Compose não as
         * interpreta, então são removidas p/ exibição. Só as tags
         * conhecidas; colchetes comuns de texto são preservados.
         */
        private val KODI_TAG_REGEX = Regex(
            "\\[(?:/?[BI]|/?COLOR[^\\]]*|CR|UPPERCASE|LOWERCASE|CAPITALIZE)\\]",
            RegexOption.IGNORE_CASE,
        )

        fun cleanKodiText(text: String): String =
            KODI_TAG_REGEX.replace(text, "").trim()

        /**
         * Decompõe a URL de um item em (addon, caminho, query) p/ o próximo
         * dispatch. Aceita `plugin://<id>/<caminho>?<query>` (o normal, que
         * o addon monta a partir do sys.argv[0]) e, como fallback, qualquer
         * URL com `?query` (ex.: caminho p/ main.py — o addon montou a URL
         * a partir do argv[0] sem o esquema plugin://). Sem query e sem
         * esquema plugin://, devolve null (URL não navegável).
         */
        fun parsePluginUrl(url: String, currentAddonId: String): PluginUrl? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val uri = Uri.parse(trimmed)
            if (uri.scheme == "plugin") {
                val addonId = uri.host.orEmpty().ifBlank { currentAddonId }
                val path = (uri.encodedPath.orEmpty()).trimStart('/')
                return PluginUrl(addonId, path, uri.encodedQuery.orEmpty())
            }
            val q = trimmed.indexOf('?')
            if (q >= 0) return PluginUrl(currentAddonId, "", trimmed.substring(q + 1))
            return null
        }
    }
}
