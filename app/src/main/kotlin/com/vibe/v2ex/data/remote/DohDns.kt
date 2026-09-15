package com.vibe.v2ex.data.remote

import android.util.Log
import com.vibe.v2ex.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "V2exDoh"

/** DoH 查询独立超时，不复用主 client 的 15s —— 解析卡住 15s 比解析失败更难受。 */
private const val TIMEOUT_SECONDS = 5L

/** 某一级失败后断路 60s，避免每个请求都去撞同一堵墙。 */
private const val BREAKER_MILLIS = 60_000L

/**
 * 端点顺序即优先级。实测国内 DoH（DNSPod/AliDNS）对 v2ex.com 会返回被污染的 IP
 * （Dropbox/Twitter/Facebook 段，且两轮之间随机变化），所以境外端点必须排在前面；
 * AliDNS 留作第二级，是因为它对 i.v2ex.co、www.sov2ex.com 解析正确且大陆可达，
 * 在 Cloudflare 不通时仍能挡住本地/运营商层的 DNS 劫持。
 *
 * bootstrap IP 硬编码：端点自身的可达性不能反过来依赖系统 DNS。
 */
private val ENDPOINTS = listOf(
    "https://cloudflare-dns.com/dns-query" to listOf("1.1.1.1", "1.0.0.1"),
    "https://dns.alidns.com/dns-query" to listOf("223.5.5.5", "223.6.6.6"),
)

/**
 * 按顺序尝试若干上游 DNS，全挂则回退系统 DNS。失败一律静默（只写 logcat），
 * 因为解析失败时用户看到的已经是请求层的错误，再弹一次没有新信息。
 *
 * [enabled] 在生产里是二道保险而非承重墙：约束 1「关闭时零开销」由 [DohDns.lookup]
 * 承担，它在开关关闭时连 `delegate` 都不会构造，根本走不到这里。保留这个参数是为了
 * 让 FallbackDns 自身可独立测试，也防止将来有人直接复用它时漏掉开关。
 */
internal class FallbackDns(
    private val upstreams: List<Dns>,
    private val enabled: () -> Boolean,
    private val system: Dns = Dns.SYSTEM,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onFailure: (String) -> Unit = { Log.w(TAG, it) },
) : Dns {
    /** 每个上游最近一次失败的时刻；0 = 闭合（哨兵值，见 [lookup] 里的显式判断）。下标与 [upstreams] 对齐。 */
    private val breakerOpenedAt = AtomicLongArray(upstreams.size)

    override fun lookup(hostname: String): List<InetAddress> {
        if (!enabled()) return system.lookup(hostname)
        val now = nowMillis()
        for (i in upstreams.indices) {
            val openedAt = breakerOpenedAt.get(i)
            // 0 必须显式当哨兵判掉，不能指望 `now - 0 >= BREAKER_MILLIS` 碰巧成立：那只在
            // nowMillis 是墙钟（≈1.7e12）时才对，一旦换成 SystemClock.elapsedRealtime()
            // （这正是治时钟回拨的正确方向），开机后头 60s 会把所有上游全部跳过。
            // 上界用 `in 0 until` 而不是 `< BREAKER_MILLIS`：NTP 回拨会让差值变负，
            // 负数同样小于 60_000，会把这一级一直断路到墙钟追回来。
            if (openedAt != 0L && now - openedAt in 0 until BREAKER_MILLIS) continue
            try {
                val addresses = upstreams[i].lookup(hostname)
                if (addresses.isNotEmpty()) {
                    // 归零是时钟回拨的保险：墙钟单调不减时这一步不可观测（成功只可能发生在
                    // now ≥ 断路时刻 + 60s 之后），但 now 一旦变小，没归零的旧时间戳会把
                    // 一个已经恢复的上游重新判成断路。
                    breakerOpenedAt.set(i, 0L)
                    return addresses
                }
                // 空结果按失败处理，但不开断路器：可能只是这一个域名没有 A 记录。
                // 仍然记一行——否则一个对所有域名都返回空的坏端点在 logcat 里毫无痕迹。
                onFailure("DoH #$i returned no address for $hostname")
            } catch (e: Exception) {
                // 刻意捕到 Exception 而非 IOException：畸形的 wire-format 响应会在
                // 解码阶段抛 RuntimeException，那同样只是「这一级不可用」，不该崩掉请求。
                //
                // 但 UnknownHostException 不能开断路器：DnsOverHttps 用它统一表达「这个主机名
                // 本身给不出地址」——NXDOMAIN、SERVFAIL、私有单标签主机名被本地短路（零 IO）、
                // 所有 query 都没产出地址。这是上游给出的权威否定答案，说明这一级是通的，只是
                // 这个域名没有记录。真正的端点故障（连接超时/拒绝、HTTP 非 200、响应体畸形）
                // 全都是别的 IOException 子类，这个二分在类型层面稳固，不需要匹配 message。
                // 若不区分：V2EX 十年老帖里失效图床遍地，滑到一张挂掉的图就会把两级 DoH 一起
                // 断路 60s，此后全 App 静默绕过 DoH，每遇到一个新死域名还会续期。
                if (e !is UnknownHostException) breakerOpenedAt.set(i, now)
                onFailure("DoH #$i failed for $hostname: $e")
            }
        }
        return system.lookup(hostname)
    }
}

/**
 * 全局 DNS 解析器。开关关闭时逐字等价于 `Dns.SYSTEM`：不构建任何 DoH 对象、
 * 不发任何额外请求，所以「默认关闭」真的等于零开销。
 *
 * 缓存完全交给 OkHttp 5 内置的 `DnsCache`（遵守 TTL，10s–300s 钳制、失败缓存 10s、
 * 并发合并），这里不写一行缓存代码。
 */
@Singleton
class DohDns @Inject constructor(
    settings: SettingsDataStore,
    // 用 dagger.Lazy 打破 OkHttpClient ↔ DohDns 的构造环；顺带避免 Hilt 在
    // Application.onCreate 就把整张网络图建起来。
    private val client: dagger.Lazy<OkHttpClient>,
) : Dns {
    // 声明顺序是隐式契约：下面 init 块里的 launchIn 让 this 在构造完成前就逃逸到
    // IO 线程，onEach 会读 enabled、写 delegate。Kotlin 按声明顺序初始化，所以
    // enabled / delegate / scope 三个字段必须留在 init 块之前，不要往下挪。
    @Volatile
    private var enabled = false

    private val delegate: Dns by lazy {
        // bootstrap client 刻意不挂 Cache：DoH 响应的 HTTP 缓存语义由 DnsCache 负责，
        // 再套一层磁盘缓存只会把过期结果引回来。
        val bootstrap = OkHttpClient.Builder()
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        FallbackDns(
            upstreams = ENDPOINTS.map { (url, ips) ->
                DnsOverHttps.Builder()
                    .client(bootstrap)
                    .url(url.toHttpUrl())
                    // 字面 IP 不走网络解析，这里不会递归回到自己。
                    .bootstrapDnsHosts(ips.map(InetAddress::getByName))
                    .build()
            },
            enabled = { enabled },
        )
    }

    // 开关订阅必须活过任何 ViewModel：它是进程级单例的状态，挂在页面生命周期上会
    // 在用户离开设置页后停止接收变更。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun lookup(hostname: String): List<InetAddress> =
        if (enabled) delegate.lookup(hostname) else Dns.SYSTEM.lookup(hostname)

    init {
        settings.encryptedDnsEnabled
            .distinctUntilChanged()
            .onEach { value ->
                // 这行不是 distinctUntilChanged 的重复，别删：DataStore 首次发射的 false
                // 与初值相同但会穿过 distinctUntilChanged，放行的话 client.get() 会在冷启动
                // 就把整张 Hilt 网络图建起来，直接破坏约束 1（关闭时零开销）。
                if (value == enabled) return@onEach
                enabled = value
                // 切换要立即生效：连接池里握好的连接是用旧解析结果建的。
                runCatching { client.get().connectionPool.evictAll() }
                    .onFailure { Log.w(TAG, "evictAll failed after toggling DoH to $value", it) }
            }
            .launchIn(scope)
    }
}
