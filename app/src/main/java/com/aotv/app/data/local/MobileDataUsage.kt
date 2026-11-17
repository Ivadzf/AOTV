package com.aotv.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rede e contagem de dados móveis (Lote 5, v1.14.0; streaming/torrent no Lote 18, v1.28.0).
 *
 * REGRA DE HONESTIDADE — o que entra na conta e o que NÃO entra:
 * - ENTRA: bytes de downloads .mp4 medidos pelo DownloadManager
 *   ([com.aotv.app.data.repository.DownloadRepository] lê
 *   COLUMN_BYTES_DOWNLOADED_SO_FAR e soma só o delta observado enquanto a
 *   rede ativa é móvel). É um número medido, não estimado.
 * - ENTRA (Lote 18, v1.28.0): bytes de STREAMING do player, medidos pelo
 *   TransferListener do Media3 ([StreamingDataMeter]), e bytes de TORRENT,
 *   medidos pelo [com.aotv.app.data.torrent.TorrentStreamer] via
 *   `totalDownload()` do libtorrent — separados por tipo nos contadores
 *   de [PlatformPrefs] e somados no mesmo total mensal que o limite olha.
 * - NÃO ENTRA: pôsteres/backdrops (Coil), catálogos e chamadas de API.
 *   O que não é mensurável fica FORA da conta e documentado aqui, em vez
 *   de somar estimativa.
 *
 * A contagem de downloads só observa enquanto o app está vivo: deltas são
 * somados quando o [com.aotv.app.data.repository.DownloadRepository]
 * consulta o DownloadManager (tela de Downloads aberta ou retomada da fila).
 * Um download concluído com o app fechado entra na conta no momento em que
 * o app volta a observá-lo, com o total real do DownloadManager, atribuído
 * à rede daquele momento — o valor em bytes é real; só a atribuição à rede
 * é pela observação. Ver [com.aotv.app.data.repository.DownloadRepository.countMobileBytes].
 * O mesmo vale p/ streaming/torrent: o medidor só soma enquanto o app está
 * vivo, e a atribuição à rede é a do momento do flush.
 */
object MobileDataUsage {

    /** Rede ativa é Wi-Fi? */
    fun isWifiActive(context: Context): Boolean =
        hasTransport(context, NetworkCapabilities.TRANSPORT_WIFI)

    /** Rede ativa é móvel (3G/4G/5G)? */
    fun isCellularActive(context: Context): Boolean =
        hasTransport(context, NetworkCapabilities.TRANSPORT_CELLULAR)

    private fun hasTransport(context: Context, transport: Int): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(transport)
    }

    /** Chave do dia ("yyyy-MM-dd") p/ o contador de hoje. */
    fun dayKey(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    /** Chave do mês ("yyyy-MM") p/ o contador do mês e o limite. */
    fun monthKey(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(date)

    /** "120 B", "850 KB", "1,2 MB", "2,4 GB" — pt-BR, 1 casa decimal. */
    fun formatBytes(bytes: Long): String {
        val br = Locale("pt", "BR")
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(br, "%.1f KB", bytes / 1024.0).replace(",0", "")
            bytes < 1024L * 1024L * 1024L ->
                String.format(br, "%.1f MB", bytes / (1024.0 * 1024.0)).replace(",0", "")
            else -> String.format(br, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0)).replace(",0", "")
        }
    }
}
