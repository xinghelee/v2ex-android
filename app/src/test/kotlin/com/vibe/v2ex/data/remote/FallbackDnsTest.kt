package com.vibe.v2ex.data.remote

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * `Dns` 是 fun interface，手写 fake 就够 —— 不引入 mockwebserver/mockk/robolectric。
 * 所有用例显式传 `onFailure`，否则会撞上单测环境里 `android.util.Log` 的 "not mocked"。
 */
class FallbackDnsTest {

    private class CountingDns(private val behavior: (String) -> List<InetAddress>) : Dns {
        var calls = 0
            private set

        override fun lookup(hostname: String): List<InetAddress> {
            calls++
            return behavior(hostname)
        }
    }

    private fun dnsOf(vararg ips: String) = CountingDns { ips.map(InetAddress::getByName) }
    private fun failingDns(e: Exception) = CountingDns { throw e }
    private fun emptyDns() = CountingDns { emptyList() }

    private fun addressesOf(vararg ips: String) = ips.map(InetAddress::getByName)

    @Test
    fun `disabled bypasses upstreams entirely`() {
        val upstream = dnsOf("1.2.3.4")
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(upstream),
            enabled = { false },
            system = system,
            onFailure = {},
        )

        assertEquals(addressesOf("5.6.7.8"), dns.lookup("v2ex.com"))
        assertEquals(0, upstream.calls)
        assertEquals(1, system.calls)
    }

    @Test
    fun `enabled uses first upstream and never touches system`() {
        val upstream = dnsOf("1.2.3.4")
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(upstream),
            enabled = { true },
            system = system,
            onFailure = {},
        )

        assertEquals(addressesOf("1.2.3.4"), dns.lookup("v2ex.com"))
        assertEquals(1, upstream.calls)
        assertEquals(0, system.calls)
    }

    @Test
    fun `falls through to second upstream when first throws`() {
        val first = failingDns(UnknownHostException("boom"))
        val second = dnsOf("9.9.9.9")
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(first, second),
            enabled = { true },
            system = system,
            onFailure = {},
        )

        assertEquals(addressesOf("9.9.9.9"), dns.lookup("v2ex.com"))
        assertEquals(1, second.calls)
        assertEquals(0, system.calls)
    }

    @Test
    fun `all upstream failure kinds fall back to system without throwing`() {
        val first = failingDns(SocketTimeoutException("timeout"))
        val second = failingDns(IOException("io"))
        val third = failingDns(IllegalStateException("malformed wire format"))
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(first, second, third),
            enabled = { true },
            system = system,
            onFailure = {},
        )

        assertEquals(addressesOf("5.6.7.8"), dns.lookup("v2ex.com"))
        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
        assertEquals(1, third.calls)
    }

    @Test
    fun `empty result continues to next level without opening the breaker`() {
        val first = emptyDns()
        val second = dnsOf("9.9.9.9")
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(first, second),
            enabled = { true },
            system = system,
            nowMillis = { 1_000_000L },
            onFailure = {},
        )

        assertEquals(addressesOf("9.9.9.9"), dns.lookup("v2ex.com"))
        assertEquals(1, first.calls)
        assertEquals(0, system.calls)

        // 时间不推进也应再次尝试第一级：空结果只说明这个域名没记录，不是端点故障。
        assertEquals(addressesOf("9.9.9.9"), dns.lookup("v2ex.com"))
        assertEquals(2, first.calls)
    }

    @Test
    fun `all upstreams empty falls back to system`() {
        val first = emptyDns()
        val second = emptyDns()
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(first, second),
            enabled = { true },
            system = system,
            onFailure = {},
        )

        assertEquals(addressesOf("5.6.7.8"), dns.lookup("v2ex.com"))
        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
        assertEquals(1, system.calls)
    }

    @Test
    fun `unknown host does not open the breaker`() {
        // NXDOMAIN / SERVFAIL 都被 DnsOverHttps 编码成 UnknownHostException：这是端点给出的
        // 权威否定答案，不是端点故障。一个失效图床域名不该把 DoH 静默关掉 60s。
        val first = failingDns(UnknownHostException("nxdomain"))
        val second = dnsOf("9.9.9.9")
        val dns = FallbackDns(
            upstreams = listOf(first, second),
            enabled = { true },
            system = dnsOf("5.6.7.8"),
            nowMillis = { 1_000_000L },
            onFailure = {},
        )

        dns.lookup("dead-imagehost.example")
        assertEquals(1, first.calls)

        // 时间不推进，第一级仍应被尝试。
        dns.lookup("v2ex.com")
        assertEquals(2, first.calls)
    }

    @Test
    fun `hot switching enabled takes effect on the next lookup`() {
        var on = false
        val upstream = dnsOf("1.2.3.4")
        val system = dnsOf("5.6.7.8")
        val dns = FallbackDns(
            upstreams = listOf(upstream),
            enabled = { on },
            system = system,
            onFailure = {},
        )

        assertEquals(addressesOf("5.6.7.8"), dns.lookup("v2ex.com"))
        assertEquals(0, upstream.calls)

        on = true
        assertEquals(addressesOf("1.2.3.4"), dns.lookup("v2ex.com"))
        assertEquals(1, upstream.calls)
        assertEquals(1, system.calls)
    }

    @Test
    fun `breaker skips failed upstream for 60s then lets it back in`() {
        val first = failingDns(IOException("down"))
        val second = dnsOf("9.9.9.9")
        var now = 1_000_000L
        val dns = FallbackDns(
            upstreams = listOf(first, second),
            enabled = { true },
            system = dnsOf("5.6.7.8"),
            nowMillis = { now },
            onFailure = {},
        )

        dns.lookup("v2ex.com")
        assertEquals(1, first.calls)

        // 59s：仍在断路窗口内，第一级不该被再次调用。
        now += 59_000L
        dns.lookup("v2ex.com")
        assertEquals(1, first.calls)

        // 再过 2s（共 61s）：窗口过期，第一级恢复尝试。
        now += 2_000L
        dns.lookup("v2ex.com")
        assertEquals(2, first.calls)
        assertEquals(3, second.calls)
    }

    @Test
    fun `successful retry closes the breaker`() {
        var shouldFail = true
        val first = CountingDns {
            if (shouldFail) throw IOException("down") else addressesOf("1.2.3.4")
        }
        var now = 1_000_000L
        val dns = FallbackDns(
            upstreams = listOf(first, dnsOf("9.9.9.9")),
            enabled = { true },
            system = dnsOf("5.6.7.8"),
            nowMillis = { now },
            onFailure = {},
        )

        dns.lookup("v2ex.com")
        now += 61_000L
        shouldFail = false
        assertEquals(addressesOf("1.2.3.4"), dns.lookup("v2ex.com"))

        // breaker 已归零：时间不推进也还能立刻再用第一级。
        assertEquals(addressesOf("1.2.3.4"), dns.lookup("v2ex.com"))
        assertEquals(3, first.calls)
    }

    @Test
    fun `breaker stays closed after a clock rollback`() {
        // 归零那行在单调不减的时钟下永远不可观测（成功只发生在 T+60s 之后，之后任意
        // now 的差值都 ≥ 60s）。真正能区分「归零 / 不归零」的只有时钟回拨：NTP 校时把
        // 墙钟往回拨 30s 后，没归零的旧时间戳会让一个已恢复的上游重新落进断路窗口。
        var shouldFail = true
        val first = CountingDns {
            if (shouldFail) throw IOException("down") else addressesOf("1.2.3.4")
        }
        var now = 1_000_000L
        val dns = FallbackDns(
            upstreams = listOf(first, dnsOf("9.9.9.9")),
            enabled = { true },
            system = dnsOf("5.6.7.8"),
            nowMillis = { now },
            onFailure = {},
        )

        dns.lookup("v2ex.com")
        assertEquals(1, first.calls)

        now += 61_000L
        shouldFail = false
        assertEquals(addressesOf("1.2.3.4"), dns.lookup("v2ex.com"))
        assertEquals(2, first.calls)

        // NTP 回拨 30s：此时 now 落在「旧失败时刻 + 60s」窗口内。
        now -= 30_000L
        assertEquals(addressesOf("1.2.3.4"), dns.lookup("v2ex.com"))
        assertEquals(3, first.calls)
    }

    @Test
    fun `breaker ignores a clock rollback past the failure timestamp`() {
        // 回拨幅度大到让 now 早于失败时刻时，差值为负；`< BREAKER_MILLIS` 会把负数也
        // 判成「仍在窗口内」，把这一级断路到墙钟追回来为止。
        val first = failingDns(IOException("down"))
        val second = dnsOf("9.9.9.9")
        var now = 1_000_000L
        val dns = FallbackDns(
            upstreams = listOf(first, second),
            enabled = { true },
            system = dnsOf("5.6.7.8"),
            nowMillis = { now },
            onFailure = {},
        )

        dns.lookup("v2ex.com")
        assertEquals(1, first.calls)

        now -= 500_000L
        dns.lookup("v2ex.com")
        assertEquals(2, first.calls)
    }

    @Test
    fun `failure is reported with the hostname`() {
        val messages = mutableListOf<String>()
        val dns = FallbackDns(
            upstreams = listOf(failingDns(IOException("down"))),
            enabled = { true },
            system = dnsOf("5.6.7.8"),
            onFailure = { messages += it },
        )

        dns.lookup("i.v2ex.co")

        assertEquals(1, messages.size)
        assertTrue(messages.single(), messages.single().contains("i.v2ex.co"))
    }
}
