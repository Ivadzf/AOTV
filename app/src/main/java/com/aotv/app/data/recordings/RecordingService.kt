package com.aotv.app.data.recordings

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AppDns
import com.aotv.app.data.sports.SportsGame
import com.aotv.app.data.sports.normalizeSportsName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Gravação de stream ao vivo (Lote 8, v1.18.0; disparo com o app fechado no Lote 13, v1.23.0).
 *
 * ForegroundService com notificação persistente: a gravação continua com o
 * app em 2º plano enquanto o serviço vive. MPEG-TS direto grava os bytes do
 * stream; HLS baixa os segmentos da playlist em sequência e concatena num
 * só .ts (funciona quando os segmentos usam o mesmo codec — não há remux).
 *
 * DISPARO: com o app aberto, [checkDue] (chamado quando a Início abre e a
 * cada minuto com ela aberta, ver MoviesHomeScreen); com o app fechado, o
 * WorkManager dispara o serviço na janela ([RecordingWorker], agendado pelo
 * [WorkScheduler] ao criar a gravação — ver [RecordingStore.schedule]).
 * Gravação agendada p/ quando o app estava fechado começa assim que o app
 * abre (via [checkDue]) ou pelo worker, se ainda estiver dentro da janela
 * (início − antecedência até fim + tolerância). Fora da janela, fica
 * "Agendada" para sempre — a tela mostra e permite apagar.
 *
 * Erros: a falha grava a mensagem REAL da exceção no registro (nunca
 * genérico) e notifica com ela. Espaço: StatFs no filesDir; com pasta SAF
 * o StatFs é do armazenamento interno (proxy — a SAF não expõe o espaço
 * livre da pasta), e a gravação pára quando o aparelho fica abaixo do
 * limite configurado.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private lateinit var store: RecordingStore
    private lateinit var prefs: PlatformPrefs

    private val httpClient: OkHttpClient by lazy {
        AppDns.mediaClient.newBuilder()
            .callTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    override fun onCreate() {
        super.onCreate()
        store = RecordingStore(applicationContext)
        prefs = PlatformPrefs(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_RECORDING_ID).orEmpty()
        val rec = store.list().firstOrNull { it.id == id }
        if (id.isBlank() || rec == null || rec.status == ScheduledRecording.STATUS_CANCELLED) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Só uma gravação por vez: a segunda fica agendada p/ a próxima checagem.
        if (currentJob?.isActive == true) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        store.update(id) { it.copy(status = ScheduledRecording.STATUS_RECORDING) }
        val notification = LocalNotifications.ongoingNotification(
            applicationContext, "Gravando: ${rec.programTitle}", rec.channelName,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
        currentJob = scope.launch { runRecording(rec) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runRecording(rec: ScheduledRecording) {
        val fileName = store.fileNameFor(rec)
        store.update(rec.id) { it.copy(fileName = fileName) }
        if (prefs.recordNotifications) {
            LocalNotifications.notify(
                applicationContext, LocalNotifications.CHANNEL_RECORDINGS,
                rec.id.hashCode(), "Gravando: ${rec.programTitle}", rec.channelName,
            )
        }
        var out: OutputStream? = null
        var localFile: File? = null
        var safDocUri: Uri? = null
        try {
            // Espera a janela abrir (programa − antecedência). O serviço já
            // está em 1º plano: esperar aqui é o que segura a gravação.
            var remaining = rec.windowStart(prefs.recordStartEarlyMin) - System.currentTimeMillis()
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                delay(minOf(remaining, 5_000L))
                val current = store.list().firstOrNull { it.id == rec.id }
                if (current?.status == ScheduledRecording.STATUS_CANCELLED) return
                remaining = rec.windowStart(prefs.recordStartEarlyMin) - System.currentTimeMillis()
            }
            val opened = openOutput(rec, fileName)
            out = opened.first
            localFile = opened.second
            safDocUri = opened.third
            checkLowSpace()
            val endMs = rec.windowEnd(prefs.recordRunLateMin)
            if (isHls(rec.channelUrl)) downloadHls(rec.channelUrl, rec.channelHeaders, out, endMs)
            else downloadTs(rec.channelUrl, rec.channelHeaders, out, endMs)
            out.flush()
            store.update(rec.id) { it.copy(status = ScheduledRecording.STATUS_DONE) }
            // Lote 20 (v1.30.0): gravou local — move p/ o destino (rede/USB)
            // agora que o arquivo está completo. Falha ao mover NÃO perde a
            // gravação: ela fica local e o erro vai no registro.
            moveToDestination(rec, localFile)
            if (prefs.recordNotifications) {
                LocalNotifications.notify(
                    applicationContext, LocalNotifications.CHANNEL_RECORDINGS,
                    rec.id.hashCode(), "Gravação concluída: ${rec.programTitle}", rec.channelName,
                )
            }
            pruneRetention()
        } catch (e: CancellationException) {
            // Serviço destruído no meio da gravação (sistema matou o app):
            // marca a falha com o motivo real e NÃO relança — o serviço já
            // está indo embora e o registro precisa ficar consistente.
            runCatching { out?.close() }
            deletePartial(localFile, safDocUri)
            store.update(rec.id) { it.copy(status = ScheduledRecording.STATUS_FAILED, error = "Gravação interrompida (o app foi fechado)") }
        } catch (e: Exception) {
            // Erro REAL na tela e na notificação — nunca genérico.
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            runCatching { out?.close() }
            deletePartial(localFile, safDocUri)
            store.update(rec.id) { it.copy(status = ScheduledRecording.STATUS_FAILED, error = msg) }
            if (prefs.recordNotifications) {
                LocalNotifications.notify(
                    applicationContext, LocalNotifications.CHANNEL_RECORDINGS,
                    rec.id.hashCode(), "Falha na gravação: ${rec.programTitle}", msg,
                )
            }
        } finally {
            runCatching { out?.close() }
            stopSelf()
        }
    }

    /**
     * Abre o arquivo de saída — SEMPRE na pasta local do app (Lote 20,
     * v1.30.0). Gravar direto na pasta SAF (rede/USB) é lento e falha no
     * meio; o arquivo é movido p/ o destino ao concluir (ver
     * [moveToDestination]). Lote 12 — Frente 2: gravação de série vai p/
     * a subpasta da série (ver [RecordingStore.folderNameFor]); sem série,
     * vai na raiz. */
    private fun openOutput(rec: ScheduledRecording, fileName: String): Triple<OutputStream, File?, Uri?> {
        val folder = store.folderNameFor(rec)
        val dir = File(applicationContext.filesDir, "recordings").let {
            if (folder.isBlank()) it else File(it, folder)
        }.apply { mkdirs() }
        val file = File(dir, fileName)
        return Triple(FileOutputStream(file), file, null)
    }

    /**
     * Move a gravação concluída p/ a pasta de destino (SAF — rede/USB).
     * Sem pasta configurada ou com o mover desligado, não faz nada. Em
     * falha, o arquivo fica local e o erro vai no registro (a gravação
     * NÃO se perde). Devolve true quando moveu.
     */
    private fun moveToDestination(rec: ScheduledRecording, localFile: File?): Boolean {
        if (localFile == null || !localFile.exists()) return false
        if (!prefs.recordMoveToDestination) return false
        val folderUri = prefs.recordingsFolderUri
        if (folderUri.isBlank()) return false
        return try {
            moveFileToDestination(applicationContext, rec, localFile, folderUri, store)
        } catch (e: Exception) {
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            store.update(rec.id) {
                it.copy(error = "Gravada localmente — não consegui mover p/ o destino: $msg")
            }
            if (prefs.recordNotifications) {
                LocalNotifications.notify(
                    applicationContext, LocalNotifications.CHANNEL_RECORDINGS,
                    rec.id.hashCode(), "Gravação não moveu: ${rec.programTitle}", msg,
                )
            }
            false
        }
    }

    /** Apaga o arquivo parcial de uma gravação que falhou. */
    private fun deletePartial(localFile: File?, safDocUri: Uri?) {
        runCatching { localFile?.delete() }
        if (safDocUri != null) {
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, safDocUri) }
        }
    }

    /** Espaço livre em bytes (StatFs do armazenamento interno; proxy p/ pasta SAF). */
    private fun freeBytes(): Long = runCatching {
        StatFs(applicationContext.filesDir.path).availableBytes
    }.getOrDefault(0L)

    /** Pára a gravação quando o espaço livre fica abaixo do limite configurado. */
    private fun checkLowSpace() {
        val limit = prefs.recordLowSpaceMb
        if (limit <= 0) return
        val freeMb = freeBytes() / (1024L * 1024L)
        if (freeMb < limit) throw IOException("Espaço livre abaixo de $limit MB ($freeMb MB livres)")
    }

    private fun isHls(url: String): Boolean =
        url.substringBefore('?').lowercase().endsWith(".m3u8")

    /** Baixa um texto por HTTP (playlist). Erro traz o código HTTP real. */
    private fun fetchText(url: String, headers: Map<String, String>): String {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Playlist inacessível (${resp.code}): ${resp.message}")
            return resp.body?.string() ?: throw IOException("Playlist vazia")
        }
    }

    /** Baixa os bytes de uma URL p/ o arquivo (um segmento HLS). */
    private fun downloadBytes(url: String, headers: Map<String, String>, out: OutputStream) {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Segmento inacessível (${resp.code}): ${resp.message}")
            val body = resp.body ?: throw IOException("Segmento vazio")
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var n = input.read(buf)
                while (n >= 0) {
                    out.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
    }

    /** Resolve uma URL relativa contra a URL da playlist. */
    private fun resolveUrl(base: String, ref: String): String {
        if (ref.startsWith("http", true)) return ref
        return base.toHttpUrlOrNull()?.resolve(ref)?.toString() ?: ref
    }

    /** Variante de maior banda da playlist mestre. Sem variante, volta a própria URL. */
    private fun pickVariant(master: String, baseUrl: String): String {
        val lines = master.lines()
        var bestUrl: String? = null
        var bestBw = -1L
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val next = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (next.isNotBlank() && !next.startsWith("#") && bw >= bestBw) {
                    bestBw = bw
                    bestUrl = resolveUrl(baseUrl, next)
                }
            }
            i += 1
        }
        return bestUrl ?: baseUrl
    }

    /**
     * HLS ao vivo: relê a playlist a cada 5 s e baixa só os segmentos novos,
     * até o fim da janela. Playlist mestre vira a variante de maior banda.
     * #EXT-X-ENDLIST com nenhum segmento novo = o VOD terminou.
     */
    private suspend fun downloadHls(url: String, headers: Map<String, String>, out: OutputStream, endMs: Long) {
        var playlistUrl = url
        val seen = HashSet<String>()
        var consecutiveErrors = 0
        while (System.currentTimeMillis() < endMs) {
            currentCoroutineContext().ensureActive()
            val text = try {
                fetchText(playlistUrl, headers)
            } catch (e: Exception) {
                consecutiveErrors += 1
                if (consecutiveErrors >= 3) throw IOException("Playlist HLS inacessível: ${e.message}", e)
                delay(5_000L)
                null
            }
            if (text == null) continue
            if (text.contains("#EXT-X-STREAM-INF")) {
                playlistUrl = pickVariant(text, playlistUrl)
            } else {
                consecutiveErrors = 0
                val base = playlistUrl.substringBeforeLast('/') + "/"
                val segments = text.lines().map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                var downloaded = 0
                for (seg in segments) {
                    currentCoroutineContext().ensureActive()
                    val segUrl = resolveUrl(base, seg)
                    if (seen.add(segUrl)) {
                        downloadBytes(segUrl, headers, out)
                        downloaded += 1
                    }
                }
                if (text.contains("#EXT-X-ENDLIST") && downloaded == 0) return
                checkLowSpace()
                delay(5_000L)
            }
        }
    }

    /**
     * MPEG-TS direto: grava os bytes do stream até o fim da janela (ou até
     * o servidor fechar o stream). Sem timeout de leitura: stream ao vivo
     * pode ficar quieto por minutos sem ser erro.
     */
    private fun downloadTs(url: String, headers: Map<String, String>, out: OutputStream, endMs: Long) {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        val client = httpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Stream inacessível (${resp.code}): ${resp.message}")
            val body = resp.body ?: throw IOException("Stream vazio")
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var writtenSinceCheck = 0L
                var done = false
                var n = input.read(buf)
                while (n >= 0 && !done) {
                    if (System.currentTimeMillis() >= endMs) {
                        done = true
                    } else {
                        out.write(buf, 0, n)
                        writtenSinceCheck += n
                        if (writtenSinceCheck >= 10 * 1024 * 1024L) {
                            writtenSinceCheck = 0L
                            checkLowSpace()
                        }
                        n = input.read(buf)
                    }
                }
            }
        }
    }

    /** Mantém só as N gravações mais recentes (a mais antiga sai).
     * Lote 12 — Frente 2: olha também as subpastas de séries (1 nível).
     * Lote 20 (v1.30.0): a gravação agora nasce local e move p/ o destino
     * ao concluir — a retenção olha os DOIS lugares (local + SAF). */
    private fun pruneRetention() {
        val keep = prefs.recordKeepEpisodes
        runCatching {
            val dir = File(applicationContext.filesDir, "recordings")
            val files = (dir.listFiles()?.toList().orEmpty())
                .flatMap { f -> if (f.isDirectory) f.listFiles()?.toList().orEmpty() else listOf(f) }
                .filter { it.name.startsWith("aotv-rec-") }
                .sortedByDescending { it.lastModified() }
            files.drop(keep).forEach { it.delete() }
        }
        runCatching {
            val folderUri = prefs.recordingsFolderUri
            if (folderUri.isBlank()) return@runCatching
            val treeUri = Uri.parse(folderUri)
            val dirDoc = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return@runCatching
            val docs = dirDoc.listFiles().flatMap { d ->
                if (d.isDirectory) d.listFiles().toList() else listOf(d)
            }
                .filter { (it.name.orEmpty()).startsWith("aotv-rec-") }
                .sortedByDescending { it.lastModified() }
            docs.drop(keep).forEach { it.delete() }
        }
    }

    companion object {
        private const val EXTRA_RECORDING_ID = "recording_id"
        private const val FOREGROUND_ID = 9101

        /** Inicia o serviço p/ gravar este registro. */
        fun start(context: Context, recordingId: String) {
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_RECORDING_ID, recordingId)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Dispara a gravação agendada cuja janela já abriu (início − antecedência)
         * e ainda não fechou (fim + tolerância). Uma por vez. false = nada a fazer.
         * Caminho com o app aberto — com ele fechado, o WorkManager dispara
         * (ver [RecordingWorker]); os dois usam a mesma janela e o mesmo serviço.
         */
        fun checkDue(context: Context): Boolean {
            val store = RecordingStore(context)
            val prefs = PlatformPrefs(context)
            val now = System.currentTimeMillis()
            if (store.list().any { it.status == ScheduledRecording.STATUS_RECORDING }) return false
            val due = store.list().firstOrNull { rec ->
                rec.status == ScheduledRecording.STATUS_SCHEDULED &&
                    now >= rec.windowStart(prefs.recordStartEarlyMin) &&
                    now < rec.windowEnd(prefs.recordRunLateMin)
            } ?: return false
            start(context, due.id)
            return true
        }

        /**
         * Agenda sozinhas as gravações dos jogos dos times com gravação
         * ligada (Lote 20, v1.30.0 — pref `record_team_games_v1`). [games]
         * é a mesma lista de `upcomingGames` da Início (jogos do EPG, com
         * canal e horários). Agenda o jogo quando ele começa nas próximas
         * 24 h (ou começou há menos de 30 min) e ainda não há gravação p/
         * o mesmo canal + início (qualquer status, menos cancelada). A
         * checagem roda a cada minuto com a Início aberta — sem duplicar.
         */
        fun checkTeamGames(context: Context, games: List<SportsGame>) {
            val prefs = PlatformPrefs(context)
            val teams = prefs.recordTeamGames
            if (teams.isEmpty()) return
            val store = RecordingStore(context)
            val now = System.currentTimeMillis()
            val existing = store.list()
            games.forEach { game ->
                if (teams.none { team -> teamMentions(game.title, team) || teamMentions(game.desc, team) }) return@forEach
                if (game.startMs < now - 30 * 60_000L) return@forEach
                if (game.startMs > now + 24 * 3_600_000L) return@forEach
                if (existing.any {
                        it.channelId == game.channelId && it.startMs == game.startMs &&
                            it.status != ScheduledRecording.STATUS_CANCELLED
                    }
                ) return@forEach
                store.schedule(
                    game.channelId, game.channelName, game.channelUrl, game.channelHeaders,
                    game.title, game.startMs, game.stopMs,
                )
            }
        }

        /** O texto do programa menciona o time (normalizado: acento/maiúscula fora). */
        private fun teamMentions(text: String, team: String): Boolean {
            val normTeam = normalizeSportsName(team.trim())
            if (normTeam.isBlank()) return false
            return normTeam in normalizeSportsName(text)
        }

        /** Arquivo local da gravação (null quando não existe ou já moveu). */
        fun localFileFor(context: Context, rec: ScheduledRecording): File? {
            if (rec.fileName.isBlank() || rec.movedToDestination) return null
            val store = RecordingStore(context)
            val folder = store.folderNameFor(rec)
            val base = File(context.filesDir, "recordings")
            val dir = if (folder.isBlank()) base else File(base, folder)
            return File(dir, rec.fileName).takeIf { it.exists() }
        }

        /**
         * Copia o arquivo local p/ a pasta de destino (SAF) e apaga o
         * local. A subpasta da série é a mesma dos dois lados. Se o arquivo
         * já está no destino (retry), só apaga o local. Lança IOException
         * com o motivo real — quem chama decide o que mostrar.
         */
        fun moveFileToDestination(
            context: Context,
            rec: ScheduledRecording,
            localFile: File,
            folderUri: String,
            store: RecordingStore,
        ): Boolean {
            val treeUri = Uri.parse(folderUri)
            var dirDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("Pasta de destino sem acesso — escolha a pasta de novo em Downloads")
            val folder = store.folderNameFor(rec)
            if (folder.isNotBlank()) {
                dirDoc = dirDoc.findFile(folder)?.takeIf { it.isDirectory }
                    ?: dirDoc.createDirectory(folder)
                    ?: throw IOException("Não consegui criar a pasta da série no destino")
            }
            val existing = dirDoc.findFile(rec.fileName)
            if (existing != null && existing.isFile) {
                localFile.delete()
                store.update(rec.id) { it.copy(movedToDestination = true, error = "") }
                return true
            }
            val doc = dirDoc.createFile("video/mp2t", rec.fileName)
                ?: throw IOException("Não consegui criar o arquivo no destino")
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                FileInputStream(localFile).use { input -> input.copyTo(out) }
            } ?: throw IOException("Não consegui abrir o arquivo no destino")
            localFile.delete()
            store.update(rec.id) { it.copy(movedToDestination = true, error = "") }
            return true
        }

        /**
         * Move manual p/ o destino (botão "Mover p/ destino" na lista de
         * gravações). Devolve true quando moveu; failure traz o motivo.
         */
        fun moveNow(context: Context, recordingId: String): Result<Boolean> = runCatching {
            val store = RecordingStore(context)
            val prefs = PlatformPrefs(context)
            val rec = store.list().firstOrNull { it.id == recordingId }
                ?: throw IllegalArgumentException("Gravação não encontrada")
            if (rec.movedToDestination) return@runCatching true
            val localFile = localFileFor(context, rec)
                ?: throw IllegalStateException("Arquivo local não encontrado")
            if (prefs.recordingsFolderUri.isBlank()) {
                throw IllegalStateException("Escolha a pasta de destino em Downloads → Gravações")
            }
            moveFileToDestination(context, rec, localFile, prefs.recordingsFolderUri, store)
        }
    }
}
