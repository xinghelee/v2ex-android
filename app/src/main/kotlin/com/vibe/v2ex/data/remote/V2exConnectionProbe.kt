package com.vibe.v2ex.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConnectionProbeResult {
    data class Success(
        val millis: Long,
        /** 实际连上的服务器 IP。 */
        val serverIp: String?,
        /** Cloudflare 节点代号，如 HKG、LAX。 */
        val colo: String?,
        /** true = Cloudflare 报告 `sni=encrypted`，即这次握手用上了 ECH。 */
        val echActive: Boolean,
    ) : ConnectionProbeResult

    data class Failed(val reason: String) : ConnectionProbeResult
}

/**
 * 「连接测试」：按 App 当前的网络配置（或指定的优选 IP）请求 `www.v2ex.com/cdn-cgi/trace`。
 * Cloudflare 在这个端点里直接回报 SNI 是否加密，是确认 ECH 是否生效最可靠的办法。
 *
 * 用独立连接池，保证每次都是新握手：复用旧连接既测不出耗时，也看不出 ECH。
 * 不带 cookie：测速没理由把会话发出去。
 */
@Singleton
class V2exConnectionProbe @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dohDns: DohDns,
) {
    suspend fun probe(pinnedIps: List<InetAddress>? = null): ConnectionProbeResult = withContext(Dispatchers.IO) {
        var serverIp: String? = null
        val client = okHttpClient.newBuilder()
            .connectionPool(ConnectionPool())
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(pinnedIps?.let(dohDns::pinnedTo) ?: dohDns)
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                serverIp = chain.connection()?.route()?.socketAddress?.address?.hostAddress
                chain.proceed(chain.request())
            }
            .build()
        val request = Request.Builder().url(TRACE_URL).build()
        val start = System.nanoTime()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val trace = parseTrace(response.body.string())
                ConnectionProbeResult.Success(
                    millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                    serverIp = serverIp,
                    colo = trace["colo"],
                    echActive = trace["sni"] == "encrypted",
                )
            }
        }.getOrElse { e ->
            ConnectionProbeResult.Failed(e.message?.takeIf(String::isNotBlank) ?: e.javaClass.simpleName)
        }.also {
            client.connectionPool.evictAll()
        }
    }

    private companion object {
        const val TRACE_URL = "https://www.v2ex.com/cdn-cgi/trace"
        const val PROBE_TIMEOUT_SECONDS = 10L
    }
}

/** `key=value` 每行一条。 */
internal fun parseTrace(body: String): Map<String, String> =
    body.lineSequence()
        .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key.trim() to value.trim() }
