package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.datastore.EncryptedDnsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DohResolversTest {

    @Test
    fun `unknown or missing resolver key falls back to auto`() {
        assertEquals(DohResolver.Auto, DohResolver.fromKey(null))
        assertEquals(DohResolver.Auto, DohResolver.fromKey("nope"))
        assertEquals(DohResolver.Preset(DohPreset.ALIDNS), DohResolver.fromKey("alidns"))
        assertEquals(DohResolver.Custom, DohResolver.fromKey("custom"))
    }

    @Test
    fun `every resolver key round-trips`() {
        DohResolver.ALL.forEach { assertEquals(it, DohResolver.fromKey(it.key)) }
        assertEquals(DohResolver.ALL.size, DohResolver.ALL.map { it.key }.toSet().size)
    }

    @Test
    fun `disabled yields no endpoints regardless of resolver`() {
        assertTrue(EncryptedDnsSettings(enabled = false, resolverKey = "cloudflare").dohEndpoints().isEmpty())
    }

    @Test
    fun `auto is cloudflare then alidns`() {
        val endpoints = EncryptedDnsSettings(enabled = true, resolverKey = "auto").dohEndpoints()
        assertEquals(
            listOf("cloudflare-dns.com", "dns.alidns.com"),
            endpoints.map { it.url.host },
        )
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), endpoints.first().bootstrap.map { it.hostAddress })
    }

    @Test
    fun `preset is a single level with its bootstrap ips`() {
        val endpoints = EncryptedDnsSettings(enabled = true, resolverKey = "dnspod").dohEndpoints()
        assertEquals(1, endpoints.size)
        assertEquals("https://doh.pub/dns-query", endpoints.single().url.toString())
        assertEquals(listOf("1.12.12.12", "120.53.53.53"), endpoints.single().bootstrap.map { it.hostAddress })
    }

    @Test
    fun `custom with ip literal host needs no bootstrap`() {
        val parsed = parseCustomDoh("https://223.5.5.5/dns-query", "1.1.1.1")
        assertTrue(parsed is CustomDohParse.Valid)
        val endpoint = (parsed as CustomDohParse.Valid).endpoint
        assertEquals("223.5.5.5", endpoint.url.host)
        assertTrue(endpoint.bootstrap.isEmpty())
    }

    @Test
    fun `custom with hostname keeps bootstrap ips in order`() {
        val parsed = parseCustomDoh(" https://doh.example.com/dns-query ", "9.9.9.9, 149.112.112.112;2620:fe::fe")
        assertTrue(parsed is CustomDohParse.Valid)
        val endpoint = (parsed as CustomDohParse.Valid).endpoint
        assertEquals("doh.example.com", endpoint.url.host)
        assertEquals(listOf("9.9.9.9", "149.112.112.112", "2620:fe:0:0:0:0:0:fe"), endpoint.bootstrap.map { it.hostAddress })
    }

    @Test
    fun `custom rejects blank, non-https, unparsable and bad bootstrap`() {
        assertEquals("请填写 DoH 地址", (parseCustomDoh("  ", "") as CustomDohParse.Invalid).message)
        assertTrue((parseCustomDoh("http://doh.pub/dns-query", "") as CustomDohParse.Invalid).message.contains("https"))
        assertTrue(parseCustomDoh("doh.pub/dns-query", "") is CustomDohParse.Invalid)
        assertTrue(parseCustomDoh("https://[broken", "") is CustomDohParse.Invalid)
        assertEquals(
            "bootstrap IP 格式不对：1:2",
            (parseCustomDoh("https://doh.example.com/dns-query", "1.1.1.1, 1:2") as CustomDohParse.Invalid).message,
        )
        assertTrue(parseCustomDoh("https://doh.example.com/dns-query", "dns.google") is CustomDohParse.Invalid)
    }

    @Test
    fun `custom resolver with invalid url yields no endpoints`() {
        val settings = EncryptedDnsSettings(enabled = true, resolverKey = "custom", customUrl = "http://x/dns-query")
        assertTrue(settings.dohEndpoints().isEmpty())
    }

    @Test
    fun `ip literal check accepts v4 and v6 and rejects hostnames`() {
        assertTrue(isIpLiteral("1.1.1.1"))
        assertTrue(isIpLiteral("255.255.255.255"))
        assertTrue(isIpLiteral("2606:4700:4700::1111"))
        assertTrue(isIpLiteral("::1"))
        assertTrue(isIpLiteral("::ffff:1.2.3.4"))
        assertTrue(isIpLiteral("2001:db8:0:0:0:0:0:1"))
        assertFalse(isIpLiteral("256.1.1.1"))
        assertFalse(isIpLiteral("1.1.1"))
        // 前导零是平台会拒绝的过时写法；全角数字会被 Char.isDigit 放过、再触发真实 DNS 查询。
        assertFalse(isIpLiteral("01.1.1.1"))
        assertFalse(isIpLiteral("１.1.1.1"))
        assertFalse(isIpLiteral("::ffff:01.2.3.4"))
        assertTrue(isIpLiteral("0.0.0.0"))
        assertFalse(isIpLiteral("dns.google"))
        assertFalse(isIpLiteral("1:2"))
        assertFalse(isIpLiteral("1::2::3"))
        assertFalse(isIpLiteral("2001:db8:0:0:0:0:0:1:2"))
        assertFalse(isIpLiteral("12345::1"))
        assertFalse(isIpLiteral(""))
    }

    @Test
    fun `bootstrap list splits on common separators`() {
        assertEquals(listOf("1.1.1.1", "1.0.0.1", "8.8.8.8"), splitBootstrapIps("1.1.1.1, 1.0.0.1;8.8.8.8\n"))
        assertTrue(splitBootstrapIps("  ").isEmpty())
    }
}
