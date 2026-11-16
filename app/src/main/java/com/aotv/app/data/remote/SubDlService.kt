package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── SubDL — 2ª fonte de legendas (Lote 16, v1.26.0) ─────────────────────────
//
// Contrato verificado na documentação oficial (subdl.com/api-doc, lida da
// VM em 2026-11-16 — NÃO houve chamada ao vivo: a API exige chave e não há
// chave de teste; sem chave do usuário o serviço fica desligado, por design):
//   GET https://api.subdl.com/api/v1/subtitles
//     ?api_key={chave}&imdb_id={tt...}&type=movie|tv
//     &season_number={s}&episode_number={e}&languages=EN,PT
//     &subs_per_page=30&unpack=1
//   Resposta 200: {"status":true,"results":[{...}],
//     "subtitles":[{"release_name":"...","name":"....zip",
//       "url":"/subtitle/3197651-3213944.zip","season":1,"episode":1,
//       "hi":false,"full_season":true,
//       "unpack_files":[{"file_n_id":"...","name":"Episode.One.srt",
//         "season":1,"episode":1,"language":"EN","hi":false,
//         "url":"/subtitle/3197651/file123"}]}]}
//   Download: "https://dl.subdl.com" + url (o .zip baixa e o
//   SubtitleService.download já extrai o .srt/.vtt de dentro e trata
//   UTF-16/BOM — ver o KDoc dele, v1.13.0). Chave GRÁTIS: o usuário cria em
//   subdl.com e digita nas Configurações (limite da chave grátis: 2.000
//   buscas e 50 downloads/dia, zera às 00:00 UTC). A chave viaja na query
//   porque é o contrato da própria SubDL (o exemplo oficial usa
//   ?api_key=...); ela NUNCA é embutida no código — sem chave, search()
//   devolve lista vazia. Erro (status false, HTTP != 200, sem rede):
//   lista vazia, nunca exceção.
//
// POR QUE unpack=1: pacotes de temporada (full_season=true) trazem TODOS os
// episódios num .zip só — baixar o pacote e pegar "o primeiro .srt" pode
// entregar a legenda do episódio errado. Com unpack=1 a API lista os
// arquivos do pacote e este serviço escolhe o do episódio certo
// (season/episode); sem arquivo do episódio, cai p/ a URL do pacote.
//
// COMO PLUGAR (o coordenador integra no MoviePlayerScreen.kt / Settings):
//   val subdl = SubDlService()
//   val key = prefs.getString("subdl_api_key", "") // chave digitada pelo
//       // usuário; a chave de pref é SUBDL_API_KEY_PREF (abaixo) — a tela de
//       // Configurações grava nela, este serviço só LÊ via parâmetro.
//   val found = subdl.search(key, imdb, type = "SERIES", season, episode)
//   val all = (externalSubs + found.map { it.toExternalSub() })
//       .distinctBy { it.url } // mescla com OpenSubtitles/addons
//   // O download usa o caminho existente: episodeVm.subtitleCues(url).
// Idiomas: a API aceita códigos de 2 letras em maiúsculo ("EN","PT") — ver
// [subDlLangCode]. O rótulo segue o padrão do app ("Português (Brasil) ·
// SubDL"), igual ao sufixo de addon do SubtitleService.

/** Uma legenda encontrada no SubDL. [url] já é absoluta (dl.subdl.com). */
data class SubDlResult(
    val url: String,
    /** Código ISO 639-1 minúsculo ("pt", "en") — igual ao ExternalSub. */
    val lang: String,
    val label: String,
    /** Hearing-impaired (SDH/CC): a API devolve o campo em todo resultado. */
    val hi: Boolean,
    val releaseName: String,
    /** Pacote de temporada inteira (só vale com unpack=1 resolvido). */
    val fullSeason: Boolean,
) {
    /** P/ mesclar com a lista do SubtitleService (OpenSubtitles + addons). */
    fun toExternalSub(): ExternalSub = ExternalSub(url, lang, label)
}

@Singleton
class SubDlService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Busca legendas no SubDL. [apiKey] é a chave GRÁTIS do usuário (""
     * = SubDL desligado, sem chamada). [type] é "SERIES" ou "MOVIE"
     * (o mesmo que o player já usa). [languages] são códigos do SubLangs
     * ("pob", "eng"...) ou de 2 letras ("pt", "en") — [subDlLangCode]
     * normaliza p/ o que a API aceita. Nunca lança exceção.
     */
    suspend fun search(
        apiKey: String,
        imdb: String,
        type: String,
        season: Int,
        episode: Int,
        languages: List<String> = listOf("pob", "eng"),
    ): List<SubDlResult> {
        if (apiKey.isBlank() || !imdb.startsWith("tt")) return emptyList()
        val langs = languages.map { subDlLangCode(it) }.distinct().filter { it.isNotBlank() }
        val isSeries = type == "SERIES"
        val url = runCatching {
            "https://api.subdl.com/api/v1/subtitles".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey.trim())
                .addQueryParameter("imdb_id", imdb)
                .addQueryParameter("type", if (isSeries) "tv" else "movie")
                .apply {
                    if (isSeries && season > 0 && episode > 0) {
                        addQueryParameter("season_number", season.toString())
                        addQueryParameter("episode_number", episode.toString())
                    }
                }
                .addQueryParameter("languages", langs.joinToString(","))
                .addQueryParameter("subs_per_page", "30")
                .addQueryParameter("unpack", "1")
                .build()
        }.getOrNull() ?: return emptyList()
        val body = runCatching {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute().use {
                    if (it.isSuccessful) it.body?.string() else null
                }
            }
        }.getOrNull() ?: return emptyList()
        return parseResults(body, isSeries, season, episode)
    }

    companion object {
        /** Chave de pref da chave SubDL do usuário (aparelho, fora do
         *  backup sem o toggle de segredos — mesmo precedente do client ID
         *  do Trakt/Drive). "" = SubDL desligado. A tela de Configurações
         *  grava aqui; este serviço recebe a chave por parâmetro. */
        const val SUBDL_API_KEY_PREF = "subdl_api_key"

        private const val DL_BASE = "https://dl.subdl.com"

        /** Código de idioma p/ a API SubDL ("PT", "EN", "ES"...): usa a
         *  família do SubLangs ("pob" e "por" viram "PT"). Quando o código
         *  não é conhecido, devolve o próprio código em maiúsculo — a API
         *  ignora códigos inválidos sem quebrar a busca. */
        fun subDlLangCode(code: String): String {
            val lang = com.aotv.app.data.local.SubLangs.of(code)
            if (lang != null) return lang.family.uppercase()
            val clean = code.trim().uppercase()
            return if (clean.length == 2) clean else "EN"
        }

        private fun absUrl(raw: String?): String? {
            val u = raw?.trim().takeIf { it.isNotEmpty() } ?: return null
            return if (u.startsWith("http")) u else DL_BASE + u
        }

        private fun labelFor(lang: String): String =
            com.aotv.app.data.local.SubLangs.label(lang) + " · SubDL"

        /**
         * Resposta da API p/ a lista de resultados. Com unpack=1, prefere o
         * arquivo do episódio dentro do pacote (season/episode); sem ele,
         * usa a URL do pacote. Filmes e episódios sem unpack caem na URL
         * direta. Entradas sem URL válida saem; duplicadas por URL saem.
         */
        fun parseResults(body: String, isSeries: Boolean, season: Int, episode: Int): List<SubDlResult> {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
            if (!json.optBoolean("status", false)) return emptyList()
            val arr = json.optJSONArray("subtitles") ?: return emptyList()
            val out = mutableListOf<SubDlResult>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val fullSeason = s.optBoolean("full_season", false)
                val releaseName = s.optString("release_name", "")
                // Arquivo do episódio dentro do pacote (unpack=1).
                var pick: JSONObject? = null
                val unpack = s.optJSONArray("unpack_files")
                if (unpack != null) {
                    for (j in 0 until unpack.length()) {
                        val f = unpack.optJSONObject(j) ?: continue
                        if (!isSeries || (f.optInt("season", -1) == season && f.optInt("episode", -1) == episode)) {
                            pick = f
                            break
                        }
                    }
                    if (pick == null && isSeries) continue // pacote sem o episódio
                }
                val src = pick ?: s
                val url = absUrl(src.optString("url", "")) ?: continue
                val rawLang = src.optString("language", s.optString("language", ""))
                val lang = rawLang.trim().lowercase().takeIf { it.isNotEmpty() } ?: continue
                val hi = src.optBoolean("hi", s.optBoolean("hi", false))
                out += SubDlResult(url, lang, labelFor(lang), hi, releaseName, fullSeason && pick == null)
            }
            return out.distinctBy { it.url }.take(30)
        }
    }
}
