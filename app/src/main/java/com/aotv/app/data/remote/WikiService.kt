package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

// ─── data/remote/WikiService.kt (Lote 8, v1.18.0) ────────────────────────────
// Frases e ficha de produção da tela de detalhe, SÓ com APIs públicas, sem
// credencial nenhuma: resumo da Wikipédia (REST) e frases do Wikiquote
// (API pública). Só LEITURA — nunca grava nada. Prefere o título original
// quando disponível; tenta também o título sem o artigo inicial ("O/A/Os/
// As/Um/Uma"). Sem frase ou sem página, devolve vazio/null e a seção SOME
// na tela — nunca inventa frase nem descrição. Erro de rede propaga como
// Result failure com a mensagem real.

data class WikiSummary(
    val title: String,
    val extract: String,
    val description: String?,
)

@Singleton
class WikiService @Inject constructor(
    client: OkHttpClient,
) {
    private val http = client.newBuilder().cache(null).build()

    /** Títulos candidatos, na ordem: original, traduzido, sem artigo. */
    fun candidateTitles(title: String, originalTitle: String): List<String> {
        val out = mutableListOf<String>()
        listOf(originalTitle, title).forEach { t ->
            val clean = t.trim()
            if (clean.isNotBlank() && clean !in out) out.add(clean)
            val stripped = stripArticle(clean)
            if (stripped.isNotBlank() && stripped !in out) out.add(stripped)
        }
        return out
    }

    /** Tira o artigo inicial ("O ", "A ", "Os ", "As ", "Um ", "Uma "). */
    fun stripArticle(title: String): String {
        val t = title.trim()
        for (a in listOf("Os ", "As ", "Um ", "Uma ", "O ", "A ")) {
            if (t.startsWith(a, ignoreCase = true) && t.length > a.length) return t.substring(a.length).trim()
        }
        return t
    }

    private fun encode(title: String): String =
        URLEncoder.encode(title, "UTF-8").replace("+", "%20")

    private suspend fun getJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "AOTV/1.26.0 (Android)").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return@withContext null
            runCatching { JSONObject(body) }.getOrNull()
        }
    }

    /**
     * Resumo da Wikipédia em português. null = sem página para nenhum dos
     * títulos candidatos (não é erro). Falha de rede lança (o chamador
     * mostra o erro real na tela).
     */
    suspend fun getSummary(title: String, originalTitle: String = ""): WikiSummary? {
        for (candidate in candidateTitles(title, originalTitle)) {
            val json = getJson("https://pt.wikipedia.org/api/rest_v1/page/summary/${encode(candidate)}")
                ?: continue
            if (json.optString("type") == "disambiguation") continue
            val extract = json.optString("extract").trim()
            if (extract.isBlank()) continue
            return WikiSummary(
                title = json.optString("title", candidate),
                extract = extract,
                description = json.optString("description").takeIf { it.isNotBlank() },
            )
        }
        return null
    }

    /**
     * Frases do Wikiquote em português (até 5). Lista vazia = sem página
     * ou sem frase para nenhum dos títulos candidatos (não é erro).
     */
    suspend fun getQuotes(title: String, originalTitle: String = ""): List<String> {
        for (candidate in candidateTitles(title, originalTitle)) {
            val url = "https://pt.wikiquote.org/w/api.php?action=parse&page=${encode(candidate)}" +
                "&prop=wikitext&format=json&formatversion=2"
            val json = getJson(url) ?: continue
            val wikitext = json.optJSONObject("parse")?.optString("wikitext").orEmpty()
            if (wikitext.isBlank()) continue
            val quotes = extractQuotes(wikitext)
            if (quotes.isNotEmpty()) return quotes
        }
        return emptyList()
    }

    /**
     * Frases de um wikitext: linhas de lista de 1º nível ("* frase").
     * Sub-itens ("** ...", em geral a atribuição/fonte) são ignorados.
     * Remove marcação wiki ([[alvo|texto]], [[texto]], ''itálico'',
     * {{predefinição}}, <ref>...</ref>) e linhas curtas demais.
     */
    fun extractQuotes(wikitext: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in wikitext.lines()) {
            val line = raw.trim()
            if (!line.startsWith("* ") || line.startsWith("**")) continue
            var text = line.removePrefix("* ").trim()
            text = text.replace(Regex("<ref[^>]*>.*?</ref>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<ref[^>]*/>"), "")
            text = text.replace(Regex("\\{\\{[^}]*\\}\\}"), "")
            text = text.replace(Regex("\\[\\[[^\\]|]*\\|([^\\]]+)\\]\\]"), "$1")
            text = text.replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1")
            text = text.replace(Regex("'''?"), "")
            text = text.replace(Regex("\\[http[^\\s]*\\s+([^\\]]+)\\]"), "$1")
            text = text.trim().trimEnd('.', ' ', '"', '”')
            if (text.length < 10) continue
            if (text !in out) out.add(text)
            if (out.size >= 5) break
        }
        return out
    }
}
