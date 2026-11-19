package com.aotv.app.ui.movies

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

// Governador de bateria/qualidade (Lote 25 item 8, v1.35.0 — MKCinema).
// Reimplementação da ideia, sem copiar código (MKCinema é GPL-3.0).
//
// Com bateria ≤ 20% e sem carregar, sugere o modo economia UMA vez por
// sessão (diálogo, com "não perguntar de novo"). Economia = teto de
// qualidade (preferir fontes ≤ 720p na escolha de fonte, via o
// downgrade que já existe) + desliga a prévia do herói/trailer autoplay
// na sessão. Toggle em Configurações → Reprodução liga/desliga o
// governador (pref `pref_battery_governor_v1`, por perfil, ligada por
// padrão). O "não perguntar de novo" desliga o governador (o toggle
// mostra desligado e o usuário pode religar).
//
// O nível vem do BatteryManager via o intent ACTION_BATTERY_CHANGED
// (sem permissão extra): nível/escala p/ a porcentagem e
// EXTRA_STATUS p/ saber se está carregando (CHARGING ou FULL).
// Sem leitura (aparelho sem bateria reportada), não sugere nada —
// nunca inventar nível. O nível atual também aparece no painel de
// infos técnicas do player (ver [TechInfo.batteryLevelPercent]).

/** Bateria em 20% ou menos, sem carregar: sugerir o modo economia. */
const val BATTERY_ECONOMY_THRESHOLD_PERCENT = 20

/** Teto de qualidade do modo economia, em px (720p). */
const val BATTERY_ECONOMY_QUALITY_CAP_PX = 720

/** Nível da bateria (0–100%) + se está carregando. */
data class BatteryStatus(val levelPercent: Int, val isCharging: Boolean)

/** Lê o nível e o carregamento da bateria, ou null quando não dá p/ saber. */
fun readBatteryStatus(context: Context): BatteryStatus? {
    val intent = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull() ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryStatus(
        levelPercent = (level * 100 / scale).coerceIn(0, 100),
        isCharging = charging,
    )
}

/** true = bateria fraca e sem carregar: vale sugerir o modo economia. */
fun shouldSuggestEconomy(status: BatteryStatus): Boolean =
    !status.isCharging && status.levelPercent in 0..BATTERY_ECONOMY_THRESHOLD_PERCENT

/**
 * Modo economia ativo NESTA sessão (processo). A Início lê daqui p/
 * desligar a prévia do herói/trailer enquanto a economia durar; fechar
 * o app zera (a economia é por sessão, não persiste).
 */
object BatteryGovernorSession {
    @Volatile
    var economyActive: Boolean = false
}

/**
 * Diálogo do governador (1x por sessão): "Ativar economia" liga o
 * teto de 720p + desliga a prévia do herói; "Agora não" só adia (não
 * pergunta de novo nesta sessão); "Não perguntar de novo" desliga o
 * governador (religável em Configurações → Reprodução). Fechar pelo
 * Voltar equivale a "Agora não".
 */
@Composable
fun BatteryGovernorDialog(
    status: BatteryStatus?,
    onEnable: () -> Unit,
    onLater: () -> Unit,
    onNeverAgain: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Bateria fraca — ativar o modo economia?") },
        text = {
            Text(
                "Bateria em ${status?.levelPercent?.let { "$it%" } ?: "nível desconhecido"} " +
                    "e sem carregar. O modo economia prefere fontes até 720p " +
                    "e desliga a prévia do herói/trailer nesta sessão."
            )
        },
        confirmButton = {
            TextButton(onClick = onEnable) { Text("Ativar economia") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLater) { Text("Agora não") }
                TextButton(onClick = onNeverAgain) { Text("Não perguntar de novo") }
            }
        },
    )
}
