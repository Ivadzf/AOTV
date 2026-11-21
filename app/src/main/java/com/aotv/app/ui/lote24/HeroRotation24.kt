package com.aotv.app.ui.lote24

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Lote 24 (v1.34.0), item 2 — herói auto-rotativo + prévia de cena ao focar.
 *
 * Regras:
 * - O herói rotaciona sozinho a cada [AUTO_ROTATE_INTERVAL_MS] (8 s).
 * - Pausa quando o usuário interage (arrastar o pager).
 * - Default: ligado no celular; na TV, desligado quando a TV é fraca
 *   (pouca RAM ou Android TV de baixo desempenho). Quando não dá p/ detectar
 *   de forma confiável, o default na TV é DESLIGADO e no celular LIGADO.
 *
 * Remodel v1.44.0 (Frente 3): o trailer do herói NUNCA toca sozinho —
 * sem autoplay por tempo parado nem prévia ao focar/toque longo (a
 * prévia de cena do item 2 foi removida; o trailer só abre pelo botão
 * "Trailer" do herói). A rotação do pager continua valendo: este arquivo
 * guarda as constantes, a detecção de TV fraca e a regra do default
 * efetivo (pref "auto" | "on" | "off").
 *
 * A rotação em si vive no [HeroCarousel] (MoviesHomeScreen.kt).
 */
object HeroAutoRotate {
    /** Intervalo da rotação automática do herói. */
    const val INTERVAL_MS = 8000L
}

/**
 * TV fraca? Null = não deu p/ detectar de forma confiável (sem
 * ActivityManager ou sem o total de RAM). Considera fraca: aparelho
 * low-RAM ([ActivityManager.isLowRamDevice]) ou menos de 2 GB de RAM total.
 * Só vale p/ TV ([UiModeManager.UI_MODE_TYPE_TELEVISION]); fora da TV
 * devolve false (o default do celular não depende disso).
 */
fun detectLowEndTv(context: Context): Boolean? {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ?: return null
    val isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (!isTv) return false
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return null
    val memInfo = ActivityManager.MemoryInfo()
    runCatching { activityManager.getMemoryInfo(memInfo) }.getOrNull() ?: return null
    if (memInfo.totalMem <= 0L) return null
    val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return activityManager.isLowRamDevice || totalGb < 2.0
}

/**
 * Default da rotação automática quando a pref está em "auto" (o default da
 * pref [Lote24PrefKeys.HERO_AUTO_ROTATE]): ligado no celular; na TV, só
 * quando a TV NÃO é fraca — e quando a detecção falha, desligado na TV.
 */
fun heroAutoRotateDefault(context: Context, isTv: Boolean): Boolean {
    if (!isTv) return true
    return detectLowEndTv(context) == false
}

/**
 * Rotação efetiva a partir da pref guardada ("auto" | "on" | "off").
 * "auto" usa [heroAutoRotateDefault]; valor desconhecido vira "auto".
 */
fun heroAutoRotateEffective(setting: String, context: Context, isTv: Boolean): Boolean =
    when (setting) {
        "on" -> true
        "off" -> false
        else -> heroAutoRotateDefault(context, isTv)
    }
