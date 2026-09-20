package com.vibe.v2ex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorMessagesTest {

    @Test
    fun `without doh the raw message is kept`() {
        val error = ConnectException("Failed to connect to /2606:4700:10::ac42:85cf:443")
        assertEquals(error.message, describeNetworkError(error, "加载失败", dohActive = false))
        assertEquals("加载失败", describeNetworkError(IOException(), "加载失败", dohActive = false))
        assertEquals("加载失败", describeNetworkError(null, "加载失败", dohActive = true))
    }

    @Test
    fun `with doh a blocked connection is explained instead of showing the last ip`() {
        val connect = ConnectException("Failed to connect to /2606:4700:10::ac42:85cf:443")
        assertEquals(DOH_BLOCKED_MESSAGE, describeNetworkError(connect, "加载失败", dohActive = true))
        assertEquals(DOH_BLOCKED_MESSAGE, describeNetworkError(SocketTimeoutException("timeout"), "加载失败", dohActive = true))
        // 包在别的异常里也认得出来。
        val wrapped = IllegalStateException("boom", ConnectException("refused"))
        assertEquals(DOH_BLOCKED_MESSAGE, describeNetworkError(wrapped, "加载失败", dohActive = true))
    }

    @Test
    fun `with doh an unresolved host points at the line settings`() {
        val error = UnknownHostException("Unable to resolve host www.v2ex.com")
        assertEquals(DOH_RESOLVE_FAILED_MESSAGE, describeNetworkError(error, "加载失败", dohActive = true))
    }

    @Test
    fun `with doh unrelated errors keep their message`() {
        val error = IOException("HTTP 500")
        assertEquals("HTTP 500", describeNetworkError(error, "加载失败", dohActive = true))
    }
}
