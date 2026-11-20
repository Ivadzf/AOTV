package com.aotv.app.ui.movies

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Últimos valores observados do mpv (Lote mpv 4, v1.39.0).
 *
 * O [MpvPlayer] observa as propriedades úteis p/ o app (`time-pos`,
 * `duration`, `pause`, `paused-for-cache`, `core-idle`) e joga os valores
 * aqui; o Diagnóstico os mostra (e o resto do app pode ler sem segurar
 * referência ao player). Os valores já vêm formatados p/ exibição
 * (tempo em mm:ss, booleanos em sim/não). Atualizado na thread de
 * eventos do mpv — o StateFlow é seguro p/ escrever de qualquer thread.
 */
object MpvObservedProperties {
    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    /** Propriedades observadas por [MpvPlayer.observePlaybackProperties]. */
    val OBSERVED = listOf("time-pos", "duration", "pause", "paused-for-cache", "core-idle")

    /** Registra um valor observado (ignora propriedades fora de [OBSERVED]). */
    fun update(property: String, value: Any?) {
        if (property !in OBSERVED) return
        val text = when (value) {
            is Double -> formatSeconds(value)
            is Boolean -> if (value) "sim" else "não"
            is Long -> value.toString()
            is String -> value
            null -> "—"
            else -> value.toString()
        }
        _values.value = _values.value + (property to text)
    }

    /** Limpa os valores (ex.: ao trocar de motor). */
    fun clear() {
        _values.value = emptyMap()
    }

    private fun formatSeconds(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
