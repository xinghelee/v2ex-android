package com.vibe.v2ex.data.remote

import okhttp3.Dns
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/*
 * OkHttp 5.5 的 DNS 新接口：连接层调用 `Dns.newCall` 拿记录，除了 IP 还有 HTTPS 记录
 * （`ServiceMetadata`，里面带 ECH 配置）。只实现旧的 `lookup()` 等于把 ECH 配置丢掉，
 * Android 17 上就用不上 ECH。这里是在包装层之间传递完整记录的小工具。
 */

/** 记录收集的上限：DoH 同时查 A/AAAA/HTTPS，每个查询本身有 [DOH_TIMEOUT_SECONDS] 的超时。 */
private const val COLLECT_TIMEOUT_SECONDS = DOH_TIMEOUT_SECONDS * 2

/** 后台执行阻塞式解析的线程池；守护线程，不阻止进程退出。 */
internal val dnsExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "V2exDns").apply { isDaemon = true }
}

/** 同步跑完一个 [Dns.Call]，返回全部记录。回调里的布尔值为 true 表示这是最后一批。 */
internal fun Dns.Call.collect(timeoutSeconds: Long = COLLECT_TIMEOUT_SECONDS): List<Dns.Record> {
    val result = LinkedBlockingQueue<Result<List<Dns.Record>>>(1)
    val collected = mutableListOf<Dns.Record>()
    enqueue(object : Dns.Callback {
        override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) {
            synchronized(collected) {
                collected += records
                if (last) result.offer(Result.success(collected.toList()))
            }
        }

        override fun onFailure(call: Dns.Call, e: IOException) {
            result.offer(Result.failure(e))
        }
    })
    val outcome = try {
        result.poll(timeoutSeconds, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        cancel()
        Thread.currentThread().interrupt()
        throw InterruptedIOException("DNS lookup interrupted")
    }
    if (outcome == null) {
        cancel()
        throw InterruptedIOException("DNS lookup timed out for ${request.hostname}")
    }
    return outcome.getOrThrow()
}

/** 把一段阻塞的解析逻辑包装成 [Dns.Call]：在 [dnsExecutor] 上执行，一次性交付全部记录。 */
internal class BlockingRecordsCall(
    override val request: Dns.Request,
    private val resolve: () -> List<Dns.Record>,
) : Dns.Call {
    private val canceled = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    override fun enqueue(callback: Dns.Callback) {
        check(started.compareAndSet(false, true)) { "Already enqueued" }
        dnsExecutor.execute {
            if (canceled.get()) {
                callback.onFailure(this, IOException("Canceled"))
                return@execute
            }
            try {
                val records = resolve()
                if (canceled.get()) callback.onFailure(this, IOException("Canceled"))
                else callback.onRecords(this, true, records)
            } catch (e: IOException) {
                callback.onFailure(this, e)
            } catch (e: RuntimeException) {
                callback.onFailure(this, IOException(e))
            }
        }
    }

    override fun cancel() {
        canceled.set(true)
    }

    override fun isCanceled(): Boolean = canceled.get()
}

internal fun List<InetAddress>.toIpRecords(hostname: String): List<Dns.Record> =
    map { Dns.Record.IpAddress(hostname, it) }

/** 只重排 IP 记录（规则同 [orderedForConnect]），HTTPS 记录原样保留在前面。 */
internal fun List<Dns.Record>.orderedIpsForConnect(preferIpv6: Boolean): List<Dns.Record> {
    val (ips, others) = partition { it is Dns.Record.IpAddress }
    val sorted = ips.sortedBy { record ->
        val isV6 = (record as Dns.Record.IpAddress).address is Inet6Address
        if (preferIpv6) !isV6 else isV6
    }
    return others + sorted
}

internal fun List<Dns.Record>.hasIpAddress(): Boolean = any { it is Dns.Record.IpAddress }
