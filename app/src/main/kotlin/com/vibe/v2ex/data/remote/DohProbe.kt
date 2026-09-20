package com.vibe.v2ex.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DohProbeResult {
    data class Success(val millis: Long, val address: String) : DohProbeResult
    data class Failed(val reason: String) : DohProbeResult
}

/**
 * 「测试线路」：用一次性的 DnsOverHttps 解析 www.v2ex.com 并计时。
 * 刻意不经过 App 自己的 OkHttp / DohDns —— 那条路挂着 DnsCache，测一下就会把结果灌进
 * 正式解析缓存，用户测完线路 A 却切到线路 B 时，头 300s 用的还是 A 的答案。
 */
@Singleton
class DohProbe @Inject constructor() {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 多级链按顺序试，第一个成功的即为结果 —— 与 [FallbackDns] 的实际行为一致。 */
    suspend fun probe(
        endpoints: List<DohEndpoint>,
        preferIpv6: Boolean = false,
        hostname: String = "www.v2ex.com",
    ): DohProbeResult = withContext(Dispatchers.IO) {
        var last: DohProbeResult = DohProbeResult.Failed("没有可用端点")
        for (endpoint in endpoints) {
            last = probeOne(endpoint, preferIpv6, hostname)
            if (last is DohProbeResult.Success) break
        }
        last
    }

    /** 显示的是排序后的第一个地址，即正式解析时会先连的那个。 */
    private fun probeOne(endpoint: DohEndpoint, preferIpv6: Boolean, hostname: String): DohProbeResult {
        val dns = endpoint.toDns(client, preferIpv6)
        val start = System.nanoTime()
        return runCatching { dns.lookup(hostname) }.fold(
            onSuccess = { addresses ->
                val address = addresses.firstOrNull()?.hostAddress
                    ?: return DohProbeResult.Failed("没有返回地址")
                DohProbeResult.Success(
                    millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                    address = address,
                )
            },
            onFailure = { e ->
                DohProbeResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
            },
        )
    }
}
