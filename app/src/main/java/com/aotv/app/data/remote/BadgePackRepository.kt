package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado do carregamento de um pack de selos por URL. */
sealed interface BadgePackLoad {
    data class Loaded(val badges: List<SourceBadge>, val skipped: Int) : BadgePackLoad
    data class Failed(val message: String) : BadgePackLoad
}

/**
 * Pack de selos com logo (Lote 19 item 9, v1.29.0): o usuário cola a URL de
 * um JSON público nas Configurações > Streams e o app baixa e guarda o JSON
 * (só o JSON — as imagens dos logos ficam no cache do Coil e são carregadas
 * por URL quando a folha abre). Sem pack carregado, a folha usa os selos de
 * texto; pack inválido ou offline NÃO apaga o pack que já estava guardado
 * (fallback silencioso) — a tela de prévia mostra o motivo, com honestidade.
 * Nenhuma URL de pack é embutida: o campo começa vazio.
 */
@Singleton
class BadgePackRepository @Inject constructor(
    private val prefs: PlatformPrefs,
) {
    /** Selos do pack guardado (vazio = sem pack). Nunca lança. */
    fun cachedPack(): List<SourceBadge> =
        parseBadgePack(prefs.badgePackJson).badges

    /**
     * Baixa o pack da [url] e, se for válido, guarda o JSON. Falha de rede,
     * HTTP de erro, JSON inválido ou pack sem selo válido → [BadgePackLoad.Failed]
     * e o pack guardado continua valendo.
     */
    suspend fun fetchPack(url: String): BadgePackLoad = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return@withContext BadgePackLoad.Failed("A URL precisa começar com http:// ou https://.")
        }
        runCatching {
            val req = Request.Builder().url(clean)
                .header("Accept", "application/json")
                .header("User-Agent", STREAM_UA)
                .build()
            val client = AppDns.mediaClient.newBuilder()
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext BadgePackLoad.Failed(
                        "O servidor respondeu ${response.code} — o pack não foi carregado.",
                    )
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext BadgePackLoad.Failed("A URL respondeu vazia.")
                }
                if (body.length > MAX_PACK_BYTES) {
                    return@withContext BadgePackLoad.Failed(
                        "O pack é grande demais (limite ${MAX_PACK_BYTES / 1024} KB).",
                    )
                }
                val parsed = parseBadgePack(body)
                if (parsed.error != null) {
                    return@withContext BadgePackLoad.Failed(parsed.error)
                }
                if (parsed.badges.isEmpty()) {
                    val extra = if (parsed.skipped > 0) " (${parsed.skipped} entradas ignoradas)" else ""
                    return@withContext BadgePackLoad.Failed("O pack não tem nenhum selo válido$extra.")
                }
                prefs.badgePackJson = body
                BadgePackLoad.Loaded(parsed.badges, parsed.skipped)
            }
        }.getOrElse { e ->
            val why = if (e is IOException) {
                "Sem conexão com a URL (verifique a internet e a URL)."
            } else {
                "Não deu pra carregar o pack: ${e.message ?: "erro desconhecido"}"
            }
            BadgePackLoad.Failed(why)
        }
    }

    /** Apaga o pack (URL + JSON guardado). A folha volta aos selos de texto. */
    fun clearPack() {
        prefs.badgePackUrl = ""
        prefs.badgePackJson = ""
    }

    companion object {
        /** Teto do JSON do pack (512 KB — pack de selo é lista pequena de URLs). */
        const val MAX_PACK_BYTES = 512 * 1024
    }
}
