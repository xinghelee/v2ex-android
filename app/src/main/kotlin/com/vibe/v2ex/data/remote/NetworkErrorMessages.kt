package com.vibe.v2ex.data.remote

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * 把网络异常翻译成用户看得懂的一句话。只在加密 DNS 生效时介入：那种场景下
 * 「Failed to connect to /2606:…:443」会让用户以为 DoH 没起作用，其实域名已经解析对了，
 * 是连接被网络阻断（issue #1）。其余情况保持原样，不改既有文案。
 */
@Singleton
class NetworkErrorMessages @Inject constructor(private val dohDns: DohDns) {
    fun describe(error: Throwable?, fallback: String): String =
        describeNetworkError(error, fallback, dohActive = dohDns.isActive)
}

internal const val DOH_BLOCKED_MESSAGE =
    "域名已通过加密 DNS 解析，但连接被当前网络阻断。DoH 只能绕过 DNS 污染，绕不过 IP / SNI 层封锁，需要系统代理或 VPN。"
internal const val DOH_RESOLVE_FAILED_MESSAGE =
    "加密 DNS 线路解析失败，回退系统 DNS 后仍不可用。可在 设置 → 解析线路 里测试并更换线路。"

internal fun describeNetworkError(error: Throwable?, fallback: String, dohActive: Boolean): String {
    val raw = error?.message?.trim().orEmpty().ifEmpty { fallback }
    if (!dohActive || error == null) return raw
    val chain = generateSequence(error) { it.cause }.take(4).toList()
    return when {
        chain.any { it is UnknownHostException } -> DOH_RESOLVE_FAILED_MESSAGE
        chain.any { it.isConnectionBlocked() } -> DOH_BLOCKED_MESSAGE
        else -> raw
    }
}

private fun Throwable.isConnectionBlocked(): Boolean = when (this) {
    is ConnectException, is NoRouteToHostException, is SocketTimeoutException, is SSLException -> true
    is SocketException -> message?.contains("reset", ignoreCase = true) == true ||
        message?.contains("broken pipe", ignoreCase = true) == true
    else -> false
}
