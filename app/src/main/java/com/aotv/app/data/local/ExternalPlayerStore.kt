package com.aotv.app.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONObject

/**
 * Player externo por título/tipo, com progresso salvo (Lote 22, v1.32.0, item 6).
 *
 * Escolha (por perfil, nas prefs):
 * 1. por título ("tmdbId:TIPO" -> pacote) — o título abre sempre naquele player;
 * 2. por tipo (filme/série) — o padrão do tipo;
 * 3. sem escolha: o seletor do sistema (quando "perguntar" está ligado) ou
 *    o único player instalado (sem seletor).
 *
 * Progresso ao voltar: o player externo é aberto com
 * `ActivityResultLauncher` (ver MoviePlayerScreen) e, ao voltar, a posição
 * vem do extra `position` do resultado (MX Player devolve a posição em ms
 * no intent de retorno) ou, sem ele, é estimada pelo tempo decorrido desde
 * a abertura (posição inicial + tempo fora, limitada à duração). A posição
 * é salva no Continuar assistindo como qualquer reprodução interna.
 *
 * A lista de players instalados usa `queryIntentActivities` (ACTION_VIEW
 * video/*) — no Android 11+ isso exige o bloco `<queries>` no
 * AndroidManifest (sem ele, a lista vem vazia). O pacote do AOTV é
 * excluído da lista.
 */
data class ExternalPlayerApp(val label: String, val packageName: String)

/** Uma abertura externa, p/ calcular a posição ao voltar. */
data class ExternalPlayerLaunch(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterUrl: String,
    val lastEpisode: String,
    val sourceUrl: String,
    val startPositionMs: Long,
    val durationMs: Long,
    val startedAtMs: Long = System.currentTimeMillis(),
)

class ExternalPlayerStore(private val prefs: PlatformPrefs) {

    /** Players de vídeo instalados (rótulo + pacote), sem o próprio AOTV. */
    fun installedPlayers(context: Context): List<ExternalPlayerApp> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("http://localhost/"), "video/*")
        }
        val pm = context.packageManager
        val resolved = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: pkg
                ExternalPlayerApp(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Pacote escolhido p/ o título, ou null. */
    fun playerForTitle(tmdbId: Int, type: String): String? {
        if (tmdbId <= 0) return null
        return playerForTitleMap()["$tmdbId:$type"]
    }

    fun setPlayerForTitle(tmdbId: Int, type: String, packageName: String) {
        if (tmdbId <= 0) return
        val current = playerForTitleMap().toMutableMap()
        if (packageName.isBlank()) current.remove("$tmdbId:$type") else current["$tmdbId:$type"] = packageName
        prefs.externalPlayerByTitle = JSONObject(current).toString()
    }

    fun clearPlayerForTitle(tmdbId: Int, type: String) = setPlayerForTitle(tmdbId, type, "")

    fun playerForTitleMap(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.externalPlayerByTitle)
        val out = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.let { out[key] = it }
        }
        out
    }.getOrDefault(emptyMap())

    /** Padrão do tipo ("MOVIE"/"TV_SERIES" — nomes do MediaType), ou "" (sem padrão). */
    fun defaultForType(type: String): String =
        if (type.equals("TV_SERIES", ignoreCase = true) || type.equals("SERIES", ignoreCase = true)) {
            prefs.externalPlayerDefaultSeries
        } else {
            prefs.externalPlayerDefaultMovie
        }

    fun setDefaultForType(type: String, packageName: String) {
        if (type.equals("TV_SERIES", ignoreCase = true) || type.equals("SERIES", ignoreCase = true)) {
            prefs.externalPlayerDefaultSeries = packageName
        } else {
            prefs.externalPlayerDefaultMovie = packageName
        }
    }

    /**
     * Resolve o pacote p/ o título: por título, depois por tipo, senão null
     * (o chamador mostra o seletor quando "perguntar" está ligado, ou usa
     * o único instalado quando só há um).
     */
    fun resolvePlayer(tmdbId: Int, type: String): String? =
        playerForTitle(tmdbId, type)?.takeIf { it.isNotBlank() }
            ?: defaultForType(type).takeIf { it.isNotBlank() }

    companion object {
        /**
         * Posição ao voltar do player externo. Ordem:
         * 1. extra `position` do resultado (MX Player, em ms);
         * 2. estimativa: posição inicial + tempo fora, limitada à duração.
         * Sem duração conhecida, a estimativa não tem teto.
         */
        fun positionFromResult(data: Intent?, launch: ExternalPlayerLaunch): Long {
            val returned = runCatching {
                when {
                    data?.hasExtra("position") == true -> data.getIntExtra("position", -1).toLong()
                    else -> -1L
                }
            }.getOrDefault(-1L)
            if (returned > 0) {
                return if (launch.durationMs > 0) returned.coerceIn(0, launch.durationMs) else returned
            }
            val elapsed = System.currentTimeMillis() - launch.startedAtMs
            val estimated = launch.startPositionMs + elapsed
            return if (launch.durationMs > 0) estimated.coerceIn(0, launch.durationMs) else estimated
        }
    }
}
