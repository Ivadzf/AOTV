package com.aotv.app.ui.movies

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Gate de dados móveis ao dar play (Lote 25 item 7, v1.35.0 — ArcTV:
// "Use mobile data this once"). Reimplementação da ideia, sem copiar
// código (ArcTV é GPL-3.0).
//
// O app JÁ tinha "Só Wi-Fi" SÓ p/ downloads (PlatformPrefs, "Só Wi-Fi"
// nos downloads + contadores de dados móveis) — este gate NÃO mexe
// nisso. O novo é o gate NA REPRODUÇÃO: ao dar play em dados móveis
// (sem Wi-Fi), com o gate em "perguntar sempre", o player segura a
// preparação do stream e mostra este diálogo.
//
// Modos (pref `pref_mobile_data_gate_v1`, por perfil):
// - ASK ("ask", padrão): pergunta toda vez que um título começa em dados móveis.
// - ALLOW ("allow", "liberar sempre"): nunca pergunta, libera sempre.
// - BLOCK ("block", "só Wi-Fi"): nunca pergunta, nunca libera — o play é
//   cancelado e a tela volta (com um toast explicando).
// A "liberação desta vez" do diálogo é EFÊMERA: só vale p/ este título
// nesta sessão (em memória, na sessão do player) e NÃO persiste. O
// "não perguntar de novo" do diálogo persiste a escolha feita nele:
// "usar desta vez" + não perguntar = ALLOW; "Só Wi-Fi" + não
// perguntar = BLOCK.
// A detecção de rede reusa [com.aotv.app.data.local.MobileDataUsage]
// (ConnectivityManager + NetworkCapabilities, já usada pelos contadores).

/** Modo do gate de dados móveis na reprodução. */
enum class MobileDataGateMode(val id: String, val label: String) {
    ASK("ask", "Perguntar sempre"),
    ALLOW("allow", "Liberar sempre"),
    BLOCK("block", "Só Wi-Fi");

    companion object {
        fun fromId(id: String): MobileDataGateMode =
            entries.firstOrNull { it.id == id } ?: ASK
    }
}

/**
 * Diálogo do gate: "Usar dados móveis desta vez" (libera só este
 * título nesta sessão) ou "Só Wi-Fi" (cancela o play e volta). O
 * checkbox "Não perguntar de novo" persiste a escolha feita ([onAllowOnce]
 * / [onWifiOnly] recebem o valor dele). Fechar pelo Voltar equivale a
 * "Só Wi-Fi" sem persistir — nunca gastar dados sem uma escolha explícita.
 */
@Composable
fun MobileDataGateDialog(
    title: String,
    onAllowOnce: (dontAskAgain: Boolean) -> Unit,
    onWifiOnly: (dontAskAgain: Boolean) -> Unit,
) {
    var dontAskAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onWifiOnly(false) },
        title = { Text("Assistir com dados móveis?") },
        text = {
            Column {
                Text(
                    "Você está em dados móveis (sem Wi-Fi). " +
                        "“$title” vai gastar o seu pacote de dados."
                )
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { checked -> dontAskAgain = checked },
                    )
                    Text("Não perguntar de novo")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAllowOnce(dontAskAgain) }) {
                Text("Usar dados móveis desta vez")
            }
        },
        dismissButton = {
            TextButton(onClick = { onWifiOnly(dontAskAgain) }) {
                Text("Só Wi-Fi")
            }
        },
    )
}
