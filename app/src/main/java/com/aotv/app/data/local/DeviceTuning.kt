package com.aotv.app.data.local

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.Display

/**
 * Auto-ajuste inicial + diagnóstico (Lote 11, v1.21.0 — ideia do fork KE).
 *
 * Na primeira abertura, o app MEDE o aparelho (memória, tela, rede) e SUGERE
 * um perfil de buffer e de rede — nunca aplica sem a confirmação do usuário
 * (ver FirstRunScreen: o passo do ajuste só aparece quando há algo a
 * sugerir, e os dois botões são "Aplicar sugestão" / "Manter padrão"). O
 * buffer manual do Lote 5 continua existindo e manda sobre a sugestão: a
 * sugestão só aparece quando o valor atual é diferente do sugerido.
 *
 * Nada aqui é inventado: o que não dá p/ medir, não aparece. A taxa de
 * quadros da tela é MEDIDA e mostrada no diagnóstico, mas NÃO vira sugestão
 * — não existe pref de frame-rate no app p/ aplicar (o player acompanha a
 * taxa da tela sozinho). A memória é a RAM total do aparelho; a rede é o
 * transporte ativo agora (Wi-Fi, móvel, cabo ou sem rede).
 */

// ── Medição ─────────────────────────────────────────────────────────────────

/** O que foi medido no aparelho, p/ sugerir e p/ mostrar no diagnóstico. */
data class DeviceFacts(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val isTv: Boolean,
    val screenWidthDp: Int,
    val densityDpi: Int,
    /** Taxa da tela em Hz, medida. 0f = não deu p/ medir. */
    val refreshRateHz: Float,
    val networkKind: String,
    val meteredNetwork: Boolean,
    val model: String,
    val androidRelease: String,
    val androidSdk: Int,
)

/** RAM total abaixo disto = pouca memória (buffer Baixo). */
const val LOW_RAM_BYTES = 2L * 1024L * 1024L * 1024L

/** RAM total a partir disto = memória de sobra (buffer Alto). */
const val HIGH_RAM_BYTES = 4L * 1024L * 1024L * 1024L

/** GB com 1 casa decimal ("3,5 GB"), p/ os textos da sugestão e do diagnóstico. */
fun ramGb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / (1024.0 * 1024.0 * 1024.0)).replace('.', ',')

/** Mede o aparelho agora. Tudo com fallback: o que não medir vira 0/"sem rede", nunca um chute. */
fun measureDeviceFacts(context: Context): DeviceFacts {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo()
    runCatching { am.getMemoryInfo(mem) }
    val config = context.resources.configuration
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val metrics = context.resources.displayMetrics
    val refreshRate = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f
    }.getOrDefault(0f)
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
    val networkKind = when {
        caps == null -> "sem rede"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "dados móveis"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "cabo"
        else -> "outra rede"
    }
    val metered = runCatching { cm.isActiveNetworkMetered }.getOrDefault(false)
    return DeviceFacts(
        totalRamBytes = mem.totalMem,
        availRamBytes = mem.availMem,
        isTv = isTv,
        screenWidthDp = (metrics.widthPixels / metrics.density).toInt(),
        densityDpi = metrics.densityDpi,
        refreshRateHz = refreshRate,
        networkKind = networkKind,
        meteredNetwork = metered,
        model = Build.MODEL ?: "aparelho",
        androidRelease = Build.VERSION.RELEASE ?: "",
        androidSdk = Build.VERSION.SDK_INT,
    )
}

// ── Sugestão ────────────────────────────────────────────────────────────────

/**
 * Sugestão de ajuste. [buffer] é "low"/"normal"/"high" (null = o atual já
 * está certo) e [wifiOnly] é o "Só Wi-Fi" dos downloads (null = nada a
 * sugerir). [reasons] explica cada sugestão com o valor MEDIDO.
 */
data class TuningSuggestion(
    val buffer: String?,
    val wifiOnly: Boolean?,
    val reasons: List<String>,
)

/**
 * Sugere buffer e rede a partir do que foi medido. null = nada a sugerir
 * (o atual já é o sugerido). Regras, sem exceção fora destas:
 * - RAM total < 2 GB → buffer Baixo (pouca memória: guarda menos vídeo na
 *   frente p/ não travar o aparelho).
 * - RAM total >= 4 GB → buffer Alto (memória de sobra: guarda até 3 min e
 *   aguenta a internet oscilar).
 * - Entre 2 e 4 GB → Normal (o padrão do ExoPlayer).
 * - Rede medida é móvel (metered) e o "Só Wi-Fi" está desligado → sugerir
 *   ligar (os downloads não gastam o pacote).
 * A TV não muda a sugestão sozinha: sem dado medido que justifique, não há
 * regra p/ ela.
 */
fun suggestTuning(facts: DeviceFacts, currentBuffer: String, currentWifiOnly: Boolean): TuningSuggestion? {
    val suggestedBuffer = when {
        facts.totalRamBytes in 1 until LOW_RAM_BYTES -> "low"
        facts.totalRamBytes >= HIGH_RAM_BYTES -> "high"
        else -> "normal"
    }
    val buffer = suggestedBuffer.takeIf { it != currentBuffer }
    val wifiOnly = if (facts.meteredNetwork && !currentWifiOnly) true else null
    if (buffer == null && wifiOnly == null) return null
    val reasons = buildList {
        if (buffer == "low") add("Memória total de ${ramGb(facts.totalRamBytes)} GB — buffer Baixo guarda menos vídeo na frente e evita travadas.")
        if (buffer == "high") add("Memória total de ${ramGb(facts.totalRamBytes)} GB — buffer Alto guarda até 3 minutos e aguenta a internet oscilar.")
        if (wifiOnly == true) add("Você está em ${facts.networkKind} agora — baixar só no Wi-Fi evita gastar o pacote de dados.")
    }
    return TuningSuggestion(buffer, wifiOnly, reasons)
}

/** Rótulo do perfil de buffer, igual ao da página Reprodução. */
fun bufferLabel(buffer: String): String = when (buffer) {
    "low" -> "Baixo"
    "high" -> "Alto"
    else -> "Normal"
}
