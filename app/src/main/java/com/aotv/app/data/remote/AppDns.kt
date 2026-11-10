package com.aotv.app.data.remote

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS via Cloudflare (1.1.1.1). Algumas fontes de stream/canais são
 * bloqueadas no DNS do provedor (ISP) — resolvendo por DoH o app contorna o bloqueio.
 *
 * Bootstrap: o cliente que faz a query DoH precisa resolver "cloudflare-dns.com"
 * SEM DoH (senão é circular) → fixamos os IPs 1.1.1.1 / 1.0.0.1 via bootstrapDnsHosts.
 */
object AppDns {

    // Cliente mínimo só p/ as queries DoH. Timeouts CURTOS: se o DoH travar (ex.: TV box
    // Android 9 com TLS antigo), não pendura o load — cai rápido pro DNS do sistema.
    private val bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val doh: DnsOverHttps? by lazy {
        runCatching {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    InetAddress.getByName("2606:4700:4700::1111"),
                    InetAddress.getByName("2606:4700:4700::1001"),
                )
                .includeIPv6(false) // muitas redes BR sem IPv6 → evita timeouts
                .build()
        }.getOrNull()
    }

    /**
     * Resolver: tenta DoH; se falhar/travar (comum em Android 9), cai PARA O DNS DO SISTEMA
     * naquela query (antes não caía — DnsOverHttps lança e o app pendurava/não abria canal).
     */
    val cloudflare: Dns by lazy {
        val d = doh ?: return@lazy Dns.SYSTEM
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                runCatching { d.lookup(hostname) }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: Dns.SYSTEM.lookup(hostname)
        }
    }

    /** Aplica o DNS DoH a um builder OkHttp (use em todos os clients do app). */
    fun OkHttpClient.Builder.withDoh(): OkHttpClient.Builder = dns(cloudflare)

    /**
     * Cliente OkHttp para o ExoPlayer (via OkHttpDataSource) — usa o MESMO DoH dos streams.
     * followRedirects/followSslRedirects (padrão true) cobrem redirecionamentos cross-protocol.
     */
    val mediaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(cloudflare)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
