package com.aotv.app.data.local

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Medição de streaming e torrent em dados móveis (Lote 18 item 7,
 * v1.28.0 — fecha a pendência do Lote 5).
 *
 * REGRA DE HONESTIDADE — o que entra na conta e o que NÃO entra:
 * - ENTRA (novo): bytes de STREAMING do player, medidos pelo
 *   [TransferListener] do Media3 no `OkHttpDataSource` do player
 *   ([meteredFactory]) — é o que o ExoPlayer baixou de fato (HLS,
 *   .mp4, legendas externas que passam pela mesma fonte). E bytes de
 *   TORRENT, medidos pelo [com.aotv.app.data.torrent.TorrentStreamer]
 *   via `totalDownload()` do libtorrent ([recordTorrentBytes]) — é o
 *   que o motor baixou de fato, incluindo o pré-download à frente da
 *   reprodução.
 * - ENTRA (Lote 5, mantido): downloads .mp4 via DownloadManager.
 * - NÃO ENTRA: pôsteres/backdrops (Coil, outro cliente), catálogos e
 *   chamadas de API/TMDB, e o que o app baixa com o app fechado sem
 *   observar (mesma limitação do contador de downloads).
 *
 * Separado por tipo: cada tipo tem o próprio contador (hoje + mês) em
 * [PlatformPrefs] e TODOS somam no total do mês/hoje que o limite
 * mensal olha — o limite que o usuário configurou passa a cobrir
 * streaming e torrent também. Mesmo tratamento de "Só Wi-Fi": os
 * bytes só entram na conta quando a rede ativa é MÓVEL (verificado
 * aqui, no flush — igual ao contador de downloads, que verifica na
 * observação); em Wi-Fi, nada é somado. O contador NÃO bloqueia nada:
 * é medição + aviso, como o de downloads.
 *
 * Detalhes que evitam contar dobrado ou contar o que não é rede:
 * - `onBytesTransferred` é ACUMULADO por transferência: o delta é a
 *   diferença p/ o último valor visto daquela fonte (mapa por
 *   instância, limpo no `onTransferEnd`).
 * - O servidor local do torrent (127.0.0.1) passa pelo MESMO
 *   OkHttpDataSource do player — esses bytes são do disco local, não
 *   da rede, e o torrent já é contado por [recordTorrentBytes]: por
 *   isso transferências p/ 127.0.0.1 são ignoradas aqui.
 * - Arquivo local (download concluído, file:// ou content://) NÃO
 *   passa pelo OkHttpDataSource: não entra na conta de streaming.
 * - O flush soma em blocos (>= 1 MB, ou no fim da transferência, ou
 *   no `flush()` explícito): sem isso, cada pedaço de HLS faria um
 *   `apply()` no SharedPreferences.
 */
@OptIn(UnstableApi::class)
class StreamingDataMeter(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = PlatformPrefs(appContext)

    /** Último acumulado visto por fonte (p/ o delta de cada transferência). */
    private val lastSeenBytes = ConcurrentHashMap<DataSource, Int>()

    /** Fontes que servem o torrent local (127.0.0.1) — bytes de disco, não de rede. */
    private val localSources = ConcurrentHashMap<DataSource, Boolean>()

    /** Bytes de streaming acumulados, aguardando o flush. */
    private val streamingPending = AtomicLong(0L)

    /** Bytes de torrent acumulados, aguardando o flush. */
    private val torrentPending = AtomicLong(0L)

    /** Listener p/ plugar no DataSource do player (ver [meteredFactory]). */
    val transferListener: TransferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            // Nada a contar antes de começar.
        }

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            lastSeenBytes[source] = 0
            localSources[source] = dataSpec.uri.host == "127.0.0.1"
        }

        override fun onBytesTransferred(source: DataSource, bytesTransferred: Int) {
            if (localSources[source] == true) return
            val prev = lastSeenBytes[source] ?: 0
            val delta = (bytesTransferred - prev).coerceAtLeast(0)
            lastSeenBytes[source] = bytesTransferred
            if (delta > 0) {
                val pending = streamingPending.addAndGet(delta.toLong())
                if (pending >= FLUSH_BYTES) flushStreaming()
            }
        }

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            lastSeenBytes.remove(source)
            localSources.remove(source)
            flushStreaming()
        }
    }

    /**
     * Envolve a fábrica de DataSource do player p/ medir o streaming.
     * A fábrica devolvida cria a mesma fonte de antes, só com o
     * [transferListener] plugado — nada muda no comportamento de rede.
     */
    fun meteredFactory(base: DataSource.Factory): DataSource.Factory =
        DataSource.Factory {
            base.createDataSource().also { it.addTransferListener(transferListener) }
        }

    /** Bytes baixados pelo motor de torrent (chamado pelo [TorrentStreamer]). */
    fun recordTorrentBytes(bytes: Long) {
        if (bytes <= 0L) return
        val pending = torrentPending.addAndGet(bytes)
        if (pending >= FLUSH_BYTES) flushTorrent()
    }

    /** Soma o acumulado de streaming (só em dados móveis — ver [PlatformPrefs.recordStreamingMobileBytes]). */
    fun flushStreaming() {
        val bytes = streamingPending.getAndSet(0L)
        if (bytes > 0L) prefs.recordStreamingMobileBytes(bytes)
    }

    /** Soma o acumulado de torrent (só em dados móveis — ver [PlatformPrefs.recordTorrentMobileBytes]). */
    fun flushTorrent() {
        val bytes = torrentPending.getAndSet(0L)
        if (bytes > 0L) prefs.recordTorrentMobileBytes(bytes)
    }

    /** Soma os dois acumulados (p/ chamar ao parar o torrent / fechar o player). */
    fun flush() {
        flushStreaming()
        flushTorrent()
    }

    private companion object {
        /** Acumula até 1 MB antes de somar (evita um apply() por pedaço de HLS). */
        const val FLUSH_BYTES = 1024L * 1024L
    }
}
