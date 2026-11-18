package com.aotv.app.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Send to TV de verdade (Lote 22, v1.32.0, item 2) — SEM o Google Cast SDK.
 *
 * Descoberta na LAN (SSDP + mDNS, deduplicada por host+tipo):
 * - Chromecast: DIAL (SSDP `urn:dial-multiscreen-org:service:dial:1` +
 *   HTTP em :8008) p/ lançar o receiver + protocolo CastV2 (TLS em :8009,
 *   protobuf mínimo escrito à mão, sem biblioteca) p/ carregar e controlar
 *   a mídia. O Cast SDK que já existe no app (Lote 4) continua lá p/ o
 *   botão antigo; este caminho novo não depende dele.
 * - DLNA/UPnP: SSDP `MediaRenderer` + AVTransport por SOAP (SetAVTransportURI
 *   / Play / Pause / Stop / GetPositionInfo).
 * - AirPlay: descoberto por mDNS (`_airplay._tcp.local`) e LISTADO, mas o
 *   envio de VÍDEO por AirPlay NÃO foi implementado: AirPlay 2 exige
 *   FairPlay (criptografia da Apple, só aparelhos MFi) p/ vídeo — não há
 *   biblioteca aberta que faça um sender de vídeo AirPlay. O código de 4
 *   dígitos da 1ª vez faz parte desse pareamento FairPlay/SRP e por isso
 *   também não existe aqui. O aparelho aparece na lista com o motivo.
 *
 * O celular vira o controle: Pause/Stop na notificação (ações que chamam
 * a sessão ativa). A notificação só aparece com POST_NOTIFICATIONS
 * concedida (já pedida em runtime p/ as gravações).
 */

/** Tipo de alvo na TV. */
enum class TvTargetKind { CHROMECAST, DLNA, AIRPLAY }

/** Uma TV encontrada no Wi-Fi. */
data class TvDevice(
    val name: String,
    val host: String,
    val port: Int = 8008,
    val kind: TvTargetKind,
    val model: String = "",
    val uuid: String = "",
    /** Base DIAL (Chromecast), ex. http://192.168.1.5:8008 — vazio se não for DIAL. */
    val dialBaseUrl: String = "",
    /** URL de controle AVTransport (DLNA) — vazio se não for DLNA. */
    val avTransportUrl: String = "",
    /** Vídeo suportado por este caminho (AirPlay = false, ver motivo). */
    val videoSupported: Boolean = true,
    /** Motivo quando [videoSupported] é false. */
    val unsupportedReason: String = "",
)

/** Transporte de mídia p/ um alvo (Chromecast via CastV2 ou DLNA via SOAP). */
interface TvTransport {
    suspend fun load(url: String, title: String, mime: String, positionMs: Long): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun positionMs(): Result<Long>
    fun close()
}

/** Sessão ativa de envio p/ TV (um aparelho por vez). */
class SendToTvSession(
    val device: TvDevice,
    private val transport: TvTransport,
    val title: String,
) {
    suspend fun pause(): Result<Unit> = transport.pause()
    suspend fun resume(): Result<Unit> = transport.resume()
    suspend fun stop(): Result<Unit> = transport.stop()
    suspend fun positionMs(): Result<Long> = transport.positionMs()
    fun close() = transport.close()
}

/** Guarda a sessão ativa (p/ a notificação e o receiver das ações). */
object SendToTvSessionManager {
    @Volatile var active: SendToTvSession? = null
        private set

    fun set(session: SendToTvSession?) {
        active?.takeIf { it !== session }?.close()
        active = session
    }

    fun clear() {
        active?.close()
        active = null
    }
}

/** Ações da notificação do controle (Pause/Stop). */
object SendToTvActions {
    const val ACTION_PAUSE = "com.aotv.app.SEND_TO_TV_PAUSE"
    const val ACTION_RESUME = "com.aotv.app.SEND_TO_TV_RESUME"
    const val ACTION_STOP = "com.aotv.app.SEND_TO_TV_STOP"
}

/** Recebe as ações da notificação (declarado no AndroidManifest, exported=false). */
class SendToTvActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = SendToTvSessionManager.active ?: run {
            SendToTvNotification.cancel(context)
            return
        }
        // O receiver roda na main; o transporte faz rede — despacha p/ IO.
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        when (intent.action) {
            SendToTvActions.ACTION_PAUSE -> scope.launch {
                session.pause()
                SendToTvNotification.show(context, session.title, isPlaying = false)
            }
            SendToTvActions.ACTION_RESUME -> scope.launch {
                session.resume()
                SendToTvNotification.show(context, session.title, isPlaying = true)
            }
            SendToTvActions.ACTION_STOP -> scope.launch {
                session.stop()
                SendToTvSessionManager.clear()
                SendToTvNotification.cancel(context)
            }
        }
    }
}

/** Notificação do controle (Pause/Stop) enquanto há envio ativo. */
object SendToTvNotification {
    private const val CHANNEL_ID = "aotv_send_to_tv"
    private const val NOTIF_ID = 42022

    fun show(context: Context, title: String, isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Enviar para TV", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val pauseResume = PendingIntent.getBroadcast(
            context, 1,
            Intent(if (isPlaying) SendToTvActions.ACTION_PAUSE else SendToTvActions.ACTION_RESUME)
                .setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, 2,
            Intent(SendToTvActions.ACTION_STOP).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openApp = PendingIntent.getActivity(
            context, 3,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Tocando na TV: $title")
            .setContentText(if (isPlaying) "Tocando — toque para pausar" else "Pausado")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPlaying) "Pausar" else "Continuar",
                pauseResume,
            )
            .addAction(android.R.drawable.ic_media_play, "Parar", stop)
            .build()
        runCatching { manager.notify(NOTIF_ID, notif) }
    }

    fun cancel(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        }
    }
}

/** Descoberta de TVs na LAN (SSDP p/ DIAL/DLNA + mDNS p/ Cast/AirPlay). */
object SendToTvDiscovery {
    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val MDNS_ADDR = "224.0.0.251"
    private const val MDNS_PORT = 5353
    private const val TAG = "SendToTv"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Descobre tudo e deduplica por host+tipo. Roda em IO; pode demorar ~3 s. */
    suspend fun discover(context: Context, timeoutMs: Long = 3000): List<TvDevice> =
        withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, TvDevice>()
            runCatching { discoverSsdp(timeoutMs) }.getOrDefault(emptyList()).forEach { device ->
                found["${device.host}:${device.kind}"] = device
            }
            runCatching { discoverMdns(context, timeoutMs) }.getOrDefault(emptyList()).forEach { device ->
                val key = "${device.host}:${device.kind}"
                // mDNS traz o nome amigável do Cast/AirPlay; SSDP traz o DIAL.
                // Chromecast achado pelos dois vira UM, com o DIAL (p/ lançar).
                val prev = found[key]
                found[key] = if (prev != null && prev.kind == TvTargetKind.CHROMECAST) {
                    prev.copy(name = device.name.ifBlank { prev.name }, model = device.model.ifBlank { prev.model })
                } else {
                    device
                }
            }
            found.values.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
        }

    // ── SSDP ────────────────────────────────────────────────────────────────

    private fun discoverSsdp(timeoutMs: Long): List<TvDevice> {
        val out = mutableListOf<TvDevice>()
        val socket = DatagramSocket()
        socket.soTimeout = 1200
        try {
            val targets = listOf(
                "urn:dial-multiscreen-org:service:dial:1" to TvTargetKind.CHROMECAST,
                "urn:schemas-upnp-org:device:MediaRenderer:1" to TvTargetKind.DLNA,
            )
            for ((st, kind) in targets) {
                val msg = ("M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: $SSDP_ADDR:$SSDP_PORT\r\n" +
                    "MAN: \"ns=01\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: $st\r\n" +
                    "USER-AGENT: AOTV/1.32.0\r\n\r\n").toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(msg, msg.size, InetAddress.getByName(SSDP_ADDR), SSDP_PORT)
                runCatching { socket.send(packet) }
                val deadline = System.currentTimeMillis() + 1200
                while (System.currentTimeMillis() < deadline) {
                    val buf = ByteArray(4096)
                    val resp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(resp)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    parseSsdpResponse(String(buf, 0, resp.length, Charsets.UTF_8), kind)?.let { out.add(it) }
                }
            }
        } finally {
            runCatching { socket.close() }
        }
        return out
    }

    private fun parseSsdpResponse(text: String, kind: TvTargetKind): TvDevice? {
        val headers = text.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
        }.toMap()
        val location = headers["location"] ?: return null
        val host = runCatching { URL(location).host }.getOrNull() ?: return null
        val usn = headers["usn"].orEmpty()
        val uuid = usn.substringAfter("uuid:", "").substringBefore("::").takeIf { it.isNotBlank() }.orEmpty()
        return when (kind) {
            TvTargetKind.CHROMECAST -> {
                // Confirma DIAL: o endpoint :8008/apps responde no Chromecast.
                val dialBase = "http://$host:8008"
                val ok = runCatching {
                    val req = Request.Builder().url("$dialBase/apps").get().build()
                    http.newCall(req).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (!ok) return null
                val info = fetchDeviceDescription(location)
                TvDevice(
                    name = info["friendlyName"] ?: "Chromecast ($host)",
                    host = host, port = 8008, kind = TvTargetKind.CHROMECAST,
                    model = info["modelName"].orEmpty(), uuid = uuid, dialBaseUrl = dialBase,
                )
            }
            TvTargetKind.DLNA -> {
                val info = fetchDeviceDescription(location)
                val avUrl = resolveAvTransportUrl(location, info["avControlUrl"].orEmpty())
                if (avUrl.isBlank()) return null
                TvDevice(
                    name = info["friendlyName"] ?: "DLNA ($host)",
                    host = host, port = runCatching { URL(location).port }.getOrDefault(80),
                    kind = TvTargetKind.DLNA, model = info["modelName"].orEmpty(),
                    uuid = uuid, avTransportUrl = avUrl,
                )
            }
            TvTargetKind.AIRPLAY -> null
        }
    }

    /** Lê a descrição UPnP/DIAL (XML) e extrai nome/modelo + controlURL do AVTransport. */
    private fun fetchDeviceDescription(location: String): Map<String, String> {
        val xml = runCatching {
            val req = Request.Builder().url(location).get().build()
            http.newCall(req).execute().use { it.body?.string().orEmpty() }
        }.getOrNull().orEmpty()
        if (xml.isBlank()) return emptyMap()
        fun tag(name: String): String? =
            Regex("<$name>([^<]+)</$name>").find(xml)?.groupValues?.getOrNull(1)?.trim()
        val avControl = runCatching {
            val serviceBlock = Regex(
                "<service>\\s*<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>(.*?)</service>",
                RegexOption.DOT_MATCHES_ALL,
            ).find(xml)?.groupValues?.getOrNull(1).orEmpty()
            Regex("<controlURL>([^<]+)</controlURL>").find(serviceBlock)?.groupValues?.getOrNull(1)?.trim()
        }.getOrNull()
        return mapOf(
            "friendlyName" to (tag("friendlyName") ?: ""),
            "modelName" to (tag("modelName") ?: ""),
            "avControlUrl" to (avControl ?: ""),
        ).filterValues { it.isNotBlank() }
    }

    private fun resolveAvTransportUrl(location: String, controlUrl: String): String {
        if (controlUrl.isBlank()) return ""
        if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) return controlUrl
        return runCatching {
            val base = URL(location)
            val portPart = if (base.port == -1) "" else ":${base.port}"
            val path = if (controlUrl.startsWith("/")) controlUrl else "/$controlUrl"
            "${base.protocol}://${base.host}$portPart$path"
        }.getOrNull().orEmpty()
    }

    // ── mDNS (Cast + AirPlay) ───────────────────────────────────────────────

    private fun discoverMdns(context: Context, timeoutMs: Long): List<TvDevice> {
        val out = mutableListOf<TvDevice>()
        // MulticastLock: sem ele o Wi-Fi descarta o multicast e nada responde.
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching { wifi?.createMulticastLock("aotv-mdns")?.apply { setReferenceCounted(true) } }.getOrNull()
        runCatching { lock?.acquire() }
        val socket = MulticastSocket(MDNS_PORT)
        try {
            socket.reuseAddress = true
            socket.soTimeout = 1500
            runCatching { socket.joinGroup(InetAddress.getByName(MDNS_ADDR)) }
            val group = InetAddress.getByName(MDNS_ADDR)
            for (service in listOf("_googlecast._tcp.local", "_airplay._tcp.local")) {
                val query = buildMdnsQuery(service)
                runCatching { socket.send(DatagramPacket(query, query.size, group, MDNS_PORT)) }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val instances = mutableMapOf<String, MdnsInstance>()
            while (System.currentTimeMillis() < deadline) {
                val buf = ByteArray(8192)
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                parseMdnsResponse(buf.copyOf(packet.length), instances)
            }
            for ((_, inst) in instances) {
                val ip = inst.ip ?: continue
                when {
                    inst.service.contains("_googlecast") -> out.add(
                        TvDevice(
                            name = inst.friendlyName.ifBlank { inst.instance.substringBefore("._googlecast") },
                            host = ip, port = inst.port, kind = TvTargetKind.CHROMECAST,
                            model = inst.model, dialBaseUrl = "http://$ip:8008",
                        ),
                    )
                    inst.service.contains("_airplay") -> out.add(
                        TvDevice(
                            name = inst.friendlyName.ifBlank { inst.instance.substringBefore("._airplay") },
                            host = ip, port = inst.port, kind = TvTargetKind.AIRPLAY,
                            model = inst.model, videoSupported = false,
                            unsupportedReason = "AirPlay de vídeo precisa do FairPlay da Apple " +
                                "(só aparelhos MFi): o AOTV não pode enviar vídeo por AirPlay.",
                        ),
                    )
                }
            }
        } finally {
            runCatching { socket.leaveGroup(InetAddress.getByName(MDNS_ADDR)) }
            runCatching { socket.close() }
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
        return out
    }

    private data class MdnsInstance(
        var service: String = "",
        var instance: String = "",
        var friendlyName: String = "",
        var model: String = "",
        var port: Int = 0,
        var ip: String? = null,
        var targetHost: String = "",
    )

    private fun buildMdnsQuery(service: String): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeShort(0) // ID
        data.writeShort(0x0000) // flags: query padrão
        data.writeShort(1) // QDCOUNT
        data.writeShort(0) // ANCOUNT
        data.writeShort(0) // NSCOUNT
        data.writeShort(0) // ARCOUNT
        service.split(".").forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            data.writeByte(bytes.size)
            data.write(bytes)
        }
        data.writeByte(0)
        data.writeShort(12) // QTYPE PTR
        data.writeShort(1) // QCLASS IN
        data.flush()
        return out.toByteArray()
    }

    private fun parseMdnsResponse(bytes: ByteArray, instances: MutableMap<String, MdnsInstance>) {
        if (bytes.size < 12) return
        fun u16(off: Int): Int = ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)
        val qdCount = u16(4)
        val anCount = u16(6)
        var off = 12
        // Pula as perguntas.
        repeat(qdCount) {
            val (_, next) = readMdnsName(bytes, off)
            off = next + 4
        }
        repeat(anCount) {
            if (off + 10 > bytes.size) return
            val (name, afterName) = readMdnsName(bytes, off)
            off = afterName
            val type = u16(off)
            val rdLength = u16(off + 8)
            off += 10
            val rdataStart = off
            val rdataEnd = minOf(bytes.size, off + rdLength)
            when (type) {
                12 -> { // PTR: nome da instância
                    val (target, _) = readMdnsName(bytes, rdataStart)
                    val key = target
                    val inst = instances.getOrPut(key) { MdnsInstance(instance = target) }
                    inst.service = name
                }
                33 -> { // SRV: porta + host alvo (o dono do registro é a instância)
                    if (rdataEnd - rdataStart >= 6) {
                        val port = u16(rdataStart + 4)
                        val (target, _) = readMdnsName(bytes, rdataStart + 6)
                        val inst = instances[name]
                        inst?.let { it.port = port; it.targetHost = target }
                    }
                }
                16 -> { // TXT: fn= (nome amigável), md= (modelo)
                    var p = rdataStart
                    val fields = mutableMapOf<String, String>()
                    while (p < rdataEnd) {
                        val len = bytes[p].toInt() and 0xFF
                        if (len == 0 || p + 1 + len > rdataEnd) break
                        val kv = String(bytes, p + 1, len, Charsets.UTF_8)
                        val eq = kv.indexOf('=')
                        if (eq > 0) fields[kv.substring(0, eq)] = kv.substring(eq + 1)
                        p += 1 + len
                    }
                    val inst = instances[name]
                    inst?.let {
                        fields["fn"]?.takeIf { v -> v.isNotBlank() }?.let { v -> it.friendlyName = v }
                        fields["md"]?.takeIf { v -> v.isNotBlank() }?.let { v -> it.model = v }
                    }
                }
                1 -> { // A: IPv4 do alvo (o dono é o host alvo do SRV)
                    if (rdataEnd - rdataStart >= 4) {
                        val ip = (0 until 4).joinToString(".") { (bytes[rdataStart + it].toInt() and 0xFF).toString() }
                        val inst = instances.values.firstOrNull { it.targetHost == name }
                        inst?.let { it.ip = ip }
                    }
                }
            }
            off = rdataEnd
        }
    }

    /** Lê um nome DNS (com compressão por ponteiro 0xC0). Devolve (nome, próximo offset). */
    private fun readMdnsName(bytes: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var off = start
        var jumped = false
        var next = start
        var guard = 0
        while (off < bytes.size && guard++ < 64) {
            val len = bytes[off].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) next = off + 1
                break
            }
            if (len and 0xC0 == 0xC0) {
                if (off + 1 >= bytes.size) break
                val ptr = ((len and 0x3F) shl 8) or (bytes[off + 1].toInt() and 0xFF)
                if (!jumped) next = off + 2
                jumped = true
                off = ptr
                continue
            }
            if (off + 1 + len > bytes.size) break
            labels.add(String(bytes, off + 1, len, Charsets.UTF_8))
            off += 1 + len
            if (!jumped) next = off
        }
        return labels.joinToString(".") to next
    }
}

/** DIAL: lança um app no Chromecast (ex. o receiver de mídia padrão CC1AD845). */
object DialClient {
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** POST /apps/{appId}. 201 = lançado (ou já rodando). */
    fun launchApp(dialBaseUrl: String, appId: String): Result<Unit> = runCatching {
        val body = "".toRequestBody("text/plain".toMediaType())
        val req = Request.Builder().url("$dialBaseUrl/apps/$appId").post(body).build()
        http.newCall(req).execute().use { resp ->
            if (resp.code != 201 && resp.code != 200) {
                throw IllegalStateException("O Chromecast respondeu HTTP ${resp.code} ao lançar o app.")
            }
        }
    }
}

/**
 * CastV2 mínimo (Chromecast, sem o Cast SDK): TLS em :8009 + protobuf
 * escrito à mão (só os campos da CastMessage) + JSON nos payloads.
 *
 * O certificado do Chromecast é autoassinado: o trust-all aqui vale SÓ
 * p/ a conexão com o aparelho na LAN (porta 8009), nunca p/ HTTPS de
 * internet (que continua no OkHttp com o trust store do sistema).
 */
class CastV2Client(private val host: String) : TvTransport {
    private var socket: SSLSocket? = null
    private var out: java.io.OutputStream? = null
    private var readerThread: Thread? = null
    @Volatile private var mediaSessionId: Int = -1
    @Volatile private var transportId: String? = null
    @Volatile private var requestId = 1
    private val lock = Any()

    fun connect(): Result<Unit> = runCatching {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        val factory = sslContext.socketFactory
        val sock = factory.createSocket(host, 8009) as SSLSocket
        sock.soTimeout = 10000
        sock.startHandshake()
        socket = sock
        out = sock.getOutputStream()
        readerThread = Thread({ readLoop(sock) }, "aotv-castv2").apply { isDaemon = true; start() }
        send("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.connection", """{"type":"CONNECT"}""")
    }

    /** TvTransport: conecta, lança o receiver padrão e carrega a mídia. */
    override suspend fun load(url: String, title: String, mime: String, positionMs: Long): Result<Unit> =
        launchAndLoad(url, title, mime, positionMs)

    /** Lança o receiver padrão e carrega a mídia. */
    suspend fun launchAndLoad(url: String, title: String, mime: String, positionMs: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                connect().getOrThrow()
                send("sender-0", "receiver-0", "urn:x-cast:com.google.cast.receiver",
                    """{"type":"LAUNCH","appId":"CC1AD845","requestId":${nextRequestId()}}""")
                val deadline = System.currentTimeMillis() + 10000
                while (transportId == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100)
                }
                val transport = transportId
                    ?: throw IllegalStateException("O Chromecast não respondeu ao lançamento do receiver.")
                send("sender-0", transport, "urn:x-cast:com.google.cast.tp.connection", """{"type":"CONNECT"}""")
                val media = JSONObject()
                    .put("contentId", url)
                    .put("streamType", "BUFFERED")
                    .put("contentType", mime.ifBlank { "video/mp4" })
                    .put("metadata", JSONObject().put("title", title).put("metadataType", 0))
                val load = JSONObject()
                    .put("type", "LOAD")
                    .put("requestId", nextRequestId())
                    .put("media", media)
                    .put("autoplay", true)
                    .put("currentTime", (positionMs / 1000.0))
                send("sender-0", transport, "urn:x-cast:com.google.cast.media", load.toString())
            }
        }

    override suspend fun pause(): Result<Unit> = mediaCommand("PAUSE")
    override suspend fun resume(): Result<Unit> = mediaCommand("PLAY")
    override suspend fun stop(): Result<Unit> = mediaCommand("STOP")

    override suspend fun positionMs(): Result<Long> = runCatching {
        // Sem GET_STATUS síncrono aqui: a última posição vista no status.
        lastPositionMs
    }

    @Volatile private var lastPositionMs: Long = 0

    private fun mediaCommand(type: String): Result<Unit> = runCatching {
        val transport = transportId ?: throw IllegalStateException("Sem sessão de mídia ativa.")
        val sessionId = mediaSessionId
        if (sessionId < 0) throw IllegalStateException("O Chromecast ainda não confirmou a mídia.")
        send("sender-0", transport, "urn:x-cast:com.google.cast.media",
            JSONObject().put("type", type).put("requestId", nextRequestId())
                .put("mediaSessionId", sessionId).toString())
    }

    override fun close() {
        runCatching { readerThread?.interrupt() }
        runCatching { socket?.close() }
        socket = null
        out = null
        transportId = null
        mediaSessionId = -1
    }

    private fun nextRequestId(): Int = synchronized(lock) { requestId++ }

    private fun send(sourceId: String, destinationId: String, namespace: String, payload: String) {
        val body = castMessage(sourceId, destinationId, namespace, payload)
        synchronized(lock) {
            val stream = out ?: throw IllegalStateException("Sem conexão CastV2.")
            stream.write(byteArrayOf(
                ((body.size ushr 24) and 0xFF).toByte(),
                ((body.size ushr 16) and 0xFF).toByte(),
                ((body.size ushr 8) and 0xFF).toByte(),
                (body.size and 0xFF).toByte(),
            ))
            stream.write(body)
            stream.flush()
        }
    }

    /** CastMessage protobuf: 1=protocol_version, 2=source_id, 3=destination_id,
     * 4=namespace, 5=payload_type (0=STRING), 6=payload_utf8. */
    private fun castMessage(sourceId: String, destinationId: String, namespace: String, payload: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun varint(value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
        fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())
        fun stringField(field: Int, value: String) {
            tag(field, 2)
            val bytes = value.toByteArray(Charsets.UTF_8)
            varint(bytes.size.toLong())
            out.write(bytes)
        }
        tag(1, 0); varint(0)
        stringField(2, sourceId)
        stringField(3, destinationId)
        stringField(4, namespace)
        tag(5, 0); varint(0)
        stringField(6, payload)
        return out.toByteArray()
    }

    private fun readLoop(sock: SSLSocket) {
        val input = runCatching { sock.getInputStream() }.getOrNull() ?: return
        val lenBuf = ByteArray(4)
        try {
            while (!Thread.currentThread().isInterrupted) {
                var read = 0
                while (read < 4) {
                    val n = input.read(lenBuf, read, 4 - read)
                    if (n < 0) return
                    read += n
                }
                val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                    ((lenBuf[1].toInt() and 0xFF) shl 16) or
                    ((lenBuf[2].toInt() and 0xFF) shl 8) or
                    (lenBuf[3].toInt() and 0xFF)
                if (len <= 0 || len > 1024 * 1024) return
                val payload = ByteArray(len)
                var got = 0
                while (got < len) {
                    val n = input.read(payload, got, len - got)
                    if (n < 0) return
                    got += n
                }
                onCastMessage(payload)
            }
        } catch (_: Exception) {
            // Socket fechado ou timeout: a sessão acabou.
        }
    }

    /** Extrai o payload_utf8 (campo 6) e lê o JSON de status do receiver/mídia. */
    private fun onCastMessage(bytes: ByteArray) {
        val payload = extractPayloadUtf8(bytes) ?: return
        runCatching {
            val json = JSONObject(payload)
            val status = json.optJSONObject("status") ?: return
            val apps = status.optJSONArray("applications")
            if (apps != null && apps.length() > 0) {
                apps.optJSONObject(0)?.optString("transportId")?.takeIf { it.isNotBlank() }?.let {
                    transportId = it
                }
            }
            val mediaStatus = status.optJSONArray("status")
            if (mediaStatus != null && mediaStatus.length() > 0) {
                val first = mediaStatus.optJSONObject(0) ?: return
                if (first.has("mediaSessionId")) mediaSessionId = first.optInt("mediaSessionId", -1)
                if (first.has("currentTime")) lastPositionMs = (first.optDouble("currentTime", 0.0) * 1000).toLong()
            }
        }
    }

    private fun extractPayloadUtf8(bytes: ByteArray): String? {
        var off = 0
        fun varint(): Long? {
            var result = 0L
            var shift = 0
            while (off < bytes.size && shift < 64) {
                val b = bytes[off++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return null
        }
        while (off < bytes.size) {
            val tag = varint() ?: return null
            val field = (tag ushr 3).toInt()
            val wire = (tag and 0x7).toInt()
            when (wire) {
                0 -> varint() ?: return null
                2 -> {
                    val len = varint()?.toInt() ?: return null
                    if (off + len > bytes.size) return null
                    if (field == 6) return String(bytes, off, len, Charsets.UTF_8)
                    off += len
                }
                else -> return null
            }
        }
        return null
    }
}

/** DLNA/UPnP: AVTransport por SOAP. */
class DlnaTransport(private val controlUrl: String) : TvTransport {
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun soap(action: String, body: String): Result<String> = runCatching {
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$body</u:$action></s:Body></s:Envelope>"""
        val req = Request.Builder().url(controlUrl)
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .header("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("O aparelho DLNA respondeu HTTP ${resp.code}.")
            text
        }
    }

    override suspend fun load(url: String, title: String, mime: String, positionMs: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            soap("SetAVTransportURI",
                "<InstanceID>0</InstanceID><CurrentURI>${escapeXml(url)}</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>")
                .mapCatching {
                    playInternal().getOrThrow()
                    // Retoma de onde parou (o Chromecast recebe currentTime
                    // no LOAD; o DLNA precisa do Seek depois do Play).
                    // Best-effort: se o aparelho recusar o Seek, o vídeo
                    // continua do início sem falhar o envio.
                    if (positionMs > 0) {
                        val hms = formatMsToHms(positionMs)
                        soap("Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>$hms</Target>").getOrNull()
                    }
                }
        }

    override suspend fun pause(): Result<Unit> = withContext(Dispatchers.IO) {
        soap("Pause", "<InstanceID>0</InstanceID><Speed>1</Speed>").map { }
    }

    override suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) {
        playInternal()
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        soap("Stop", "<InstanceID>0</InstanceID>").map { }
    }

    override suspend fun positionMs(): Result<Long> = withContext(Dispatchers.IO) {
        soap("GetPositionInfo", "<InstanceID>0</InstanceID>").map { xml ->
            val rel = Regex("<RelTime>([^<]+)</RelTime>").find(xml)?.groupValues?.getOrNull(1).orEmpty()
            parseHmsToMs(rel)
        }
    }

    override fun close() = Unit

    private fun playInternal(): Result<Unit> =
        soap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>").map { }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun parseHmsToMs(hms: String): Long {
        val parts = hms.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.size != 3) return 0
        return ((parts[0] * 3600) + (parts[1] * 60) + parts[2]) * 1000
    }

    private fun formatMsToHms(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(totalSec / 3600, (totalSec / 60) % 60, totalSec % 60)
    }
}

/** Conecta e envia uma mídia p/ o aparelho. AirPlay devolve erro (ver [TvDevice.unsupportedReason]). */
object SendToTv {
    fun mimeFor(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
        url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
        url.contains(".webm", ignoreCase = true) -> "video/webm"
        else -> "video/mp4"
    }

    suspend fun connectAndPlay(
        device: TvDevice,
        url: String,
        title: String,
        positionMs: Long = 0,
    ): Result<SendToTvSession> = withContext(Dispatchers.IO) {
        runCatching {
            if (!device.videoSupported) {
                throw IllegalStateException(device.unsupportedReason.ifBlank { "Este aparelho não aceita vídeo por aqui." })
            }
            val transport: TvTransport = when (device.kind) {
                TvTargetKind.CHROMECAST -> {
                    val dialBase = device.dialBaseUrl.ifBlank { "http://${device.host}:8008" }
                    DialClient.launchApp(dialBase, "CC1AD845").getOrThrow()
                    CastV2Client(device.host)
                }
                TvTargetKind.DLNA -> {
                    if (device.avTransportUrl.isBlank()) {
                        throw IllegalStateException("Este aparelho DLNA não expôs o controle AVTransport.")
                    }
                    DlnaTransport(device.avTransportUrl)
                }
                TvTargetKind.AIRPLAY -> throw IllegalStateException(device.unsupportedReason)
            }
            val session = SendToTvSession(device, transport, title)
            transport.load(url, title, mimeFor(url), positionMs).getOrThrow()
            SendToTvSessionManager.set(session)
            session
        }
    }
}
