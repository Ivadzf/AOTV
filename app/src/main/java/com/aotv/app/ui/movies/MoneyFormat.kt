package com.aotv.app.ui.movies

import java.text.NumberFormat
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

/**
 * Formato legível em pt-BR p/ orçamento/receita do TMDB (Lote 3 item 7, v1.11.0).
 * Valores em dólares americanos, como o TMDB devolve (budget/revenue de /movie/{id}).
 *
 * - >= US$ 1 bi: "US$ 1,2 bi" (1 casa decimal, sem ",0": 2 bi exatos vira "US$ 2 bi")
 * - >= US$ 1 mi: "US$ 185 mi" (inteiro quando exato, senão 1 casa: "US$ 1,5 mi")
 * - < US$ 1 mi: "US$ 50.000" (separador de milhar pt-BR, sem centavos)
 * - <= 0: "" (sem dado — a tela NÃO mostra a linha, nunca inventa valor)
 *
 * Exemplos: 185_000_000 -> "US$ 185 mi"; 1_200_000_000 -> "US$ 1,2 bi";
 * 2_000_000_000 -> "US$ 2 bi"; 50_000 -> "US$ 50.000"; 0 -> "".
 */
fun formatMoneyUsdBr(value: Long): String {
    if (value <= 0) return ""
    if (value >= 1_000_000_000L) {
        val bi = value / 1_000_000_000.0
        val s = String.format(PT_BR, "%.1f", bi).removeSuffix(",0")
        return "US$ $s bi"
    }
    if (value >= 1_000_000L) {
        val mi = value / 1_000_000.0
        val s = String.format(PT_BR, "%.1f", mi).removeSuffix(",0")
        return "US$ $s mi"
    }
    return "US$ " + NumberFormat.getIntegerInstance(PT_BR).format(value)
}
