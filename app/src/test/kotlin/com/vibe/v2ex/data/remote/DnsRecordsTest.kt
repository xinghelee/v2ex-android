package com.vibe.v2ex.data.remote

import okhttp3.Dns
import okhttp3.Protocol
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.InetAddress

class DnsRecordsTest {

    private val host = "www.v2ex.com"
    private val ech = Dns.Record.ServiceMetadata(host, listOf(Protocol.HTTP_2), 443, emptyList(), "ech".encodeUtf8())

    /** 模拟 DnsOverHttps：newCall 给出 HTTPS 记录 + IP。 */
    private class RecordsDns(private val records: (Dns.Request) -> List<Dns.Record>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            records(Dns.Request(hostname)).filterIsInstance<Dns.Record.IpAddress>().map { it.address }

        override fun newCall(request: Dns.Request): Dns.Call = BlockingRecordsCall(request) { records(request) }
    }

    private fun ip(value: String, hostname: String = host): Dns.Record = Dns.Record.IpAddress(hostname, InetAddress.getByName(value))

    @Test
    fun `newCall keeps the ech metadata from doh`() {
        val upstream = RecordsDns { listOf(ech, ip("1.2.3.4")) }
        val dns = FallbackDns(upstreams = listOf(upstream), enabled = { true }, system = Dns { error("unused") }, onFailure = {})

        val records = dns.newCall(Dns.Request(host)).collect()

        assertTrue(ech in records)
        assertTrue(records.hasIpAddress())
    }

    @Test
    fun `newCall falls back to system ips when every upstream fails`() {
        val upstream = RecordsDns { throw ConnectException("blocked") }
        val system = Dns { listOf(InetAddress.getByName("5.6.7.8")) }
        val dns = FallbackDns(upstreams = listOf(upstream), enabled = { true }, system = system, onFailure = {})

        assertEquals(listOf(ip("5.6.7.8")), dns.newCall(Dns.Request(host)).collect())
    }

    @Test
    fun `ip ordering keeps metadata and honours the ipv6 preference`() {
        val records = listOf(ip("2606:4700::1"), ech, ip("1.2.3.4"))
        assertEquals(listOf(ech, ip("1.2.3.4"), ip("2606:4700::1")), records.orderedIpsForConnect(preferIpv6 = false))
        assertEquals(listOf(ech, ip("2606:4700::1"), ip("1.2.3.4")), records.orderedIpsForConnect(preferIpv6 = true))
    }

    @Test
    fun `preferred ips only accept literals and only apply to the v2ex site`() {
        assertEquals(
            listOf("172.66.133.207", "2606:4700:10::ac42:85cf").map(InetAddress::getByName),
            parsePreferredIps("172.66.133.207, bad.host 2606:4700:10::ac42:85cf,172.66.133.207"),
        )
        assertTrue(isV2exHost("www.v2ex.com"))
        assertTrue(isV2exHost("V2EX.com"))
        assertFalse(isV2exHost("cdn.v2ex.com"))
        assertFalse(isV2exHost("i.imgur.com"))
    }

    @Test
    fun `trace parsing reads sni and colo`() {
        val trace = parseTrace("fl=1\nh=www.v2ex.com\nsni=encrypted\ncolo=HKG\n")
        assertEquals("encrypted", trace["sni"])
        assertEquals("HKG", trace["colo"])
    }
}
