package com.aotv.app.data.local

/**
 * Autoplay inteligente (v1.45.0, item 7 — ideia do Notflix,
 * reimplementada: nada de código do Notflix aqui).
 *
 * O AOTV já tocava direto sem folha de fontes e já tinha 3 das 4 peças:
 * ordenação por idioma/qualidade ([PlatformPrefs.sortStreams]), "só em
 * cache" no debrid (`hideUncached`) e a última fonte escolhida à mão por
 * título ([LastSourcePick], via `PlayerProgressViewModel.lastSourcePick`).
 * O que faltava — e este arquivo entrega — é a prioridade CONFIGURÁVEL
 * (lista de addons + lista de qualidades) e o prazo da memória.
 *
 * Memória do "último link bom": NUNCA persiste a URL resolvida — links de
 * addon trazem token/assinatura com validade curta (o mesmo motivo do
 * KDoc de [LastSourcePick]). O que vale por 7 dias é addon + qualidade:
 * ao voltar ao título, [pickBest] prefere uma fonte do MESMO addon e da
 * MESMA qualidade, se ela ainda existir na lista nova. Depois de 7 dias
 * ([LAST_GOOD_LINK_TTL_MS]) a escolha expira e o autoplay volta à
 * prioridade configurada (o getter de `lastSourcePicks` já descarta o
 * expirado).
 *
 * Ordem do [rank]: (1) addon na lista de prioridade do usuário, na ordem
 * dela; (2) qualidade na lista de prioridade do usuário, na ordem dela;
 * (3) `qualityRank` maior; (4) em cache (debrid) primeiro, quando o
 * filtro "só em cache" está ligado. A lista de entrada já vem ordenada
 * por `sortStreams` (idioma de áudio PT primeiro, via `audioPref`) — o
 * rank aqui NÃO reordena por idioma, só desempata o resto.
 */
object SmartAutoplay {

    /** O "último link bom" vale 7 dias (item 7). */
    const val LAST_GOOD_LINK_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** Uma fonte candidata ao autoplay, já com o que o rank precisa. */
    data class Candidate(
        val addon: String,
        val label: String,
        val qualityRank: Int,
        val url: String,
        val cached: Boolean = false,
    )

    /** Chave estável p/ a memória por título: "TIPO:tmdbId" (a mesma de [LastSourcePick]). */
    fun keyFor(tmdbId: Int, type: String): String = "$type:$tmdbId"

    /**
     * Decodifica a lista de prioridade de addons (JSON com os nomes, na
     * ordem) que o usuário editou nas Configurações. Texto inválido = lista
     * vazia (ordem do gerenciador).
     */
    fun parseAddonPriority(raw: String): List<String> = runCatching {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Decodifica a lista de prioridade de qualidades ("1080p,720p,..."). */
    fun parseQualityPriority(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

    /**
     * Ordena as fontes p/ o autoplay. [lastPick] é a última fonte escolhida
     * à mão neste título (ou null): ela ganha do resto quando ainda existe
     * na lista e não expirou (o getter já expirou; aqui só confere se a
     * fonte existe). [onlyCached] é o filtro "só em cache" do debrid.
     */
    fun rank(
        candidates: List<Candidate>,
        addonPriority: List<String>,
        qualityPriority: List<String>,
        lastPick: LastSourcePick? = null,
        onlyCached: Boolean = false,
    ): List<Candidate> {
        val pool = if (onlyCached) candidates.filter { it.cached } else candidates
        val usable = pool.ifEmpty { candidates }
        val addonIndex = { c: Candidate ->
            addonPriority.indexOfFirst { it.equals(c.addon, ignoreCase = true) }
                .takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        val qualityIndex = { c: Candidate ->
            val label = c.label
            qualityPriority.indexOfFirst { label.contains(it, ignoreCase = true) }
                .takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        val lastBoost = { c: Candidate ->
            if (lastPick == null) 0 else {
                val sameAddon = c.addon.equals(lastPick.addon, ignoreCase = true)
                val sameQuality = c.qualityRank == lastPick.qualityRank
                if (sameAddon && sameQuality) 0 else if (sameAddon) 1 else 2
            }
        }
        return usable.sortedWith(
            compareBy({ lastBoost(it) }, { addonIndex(it) }, { qualityIndex(it) }, { -it.qualityRank }),
        )
    }

    /** A melhor fonte p/ tocar direto, ou null sem candidata. */
    fun pickBest(
        candidates: List<Candidate>,
        addonPriority: List<String>,
        qualityPriority: List<String>,
        lastPick: LastSourcePick? = null,
        onlyCached: Boolean = false,
    ): Candidate? = rank(candidates, addonPriority, qualityPriority, lastPick, onlyCached).firstOrNull()
}
