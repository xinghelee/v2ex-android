package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.datastore.EncryptedDnsSettings
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * 内置 DoH 线路。bootstrap IP 硬编码：端点自身的可达性不能反过来依赖系统 DNS，
 * 否则在最需要它的网络里会自我失效。
 */
enum class DohPreset(val label: String, val url: String, val bootstrapIps: List<String>) {
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    ALIDNS("阿里 DNS", "https://dns.alidns.com/dns-query", listOf("223.5.5.5", "223.6.6.6")),
    DNSPOD("腾讯 DNSPod", "https://doh.pub/dns-query", listOf("1.12.12.12", "120.53.53.53")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ;

    // 字面 IP 不走网络解析，这里不会递归回到系统 DNS。
    val endpoint: DohEndpoint
        get() = DohEndpoint(url.toHttpUrl(), bootstrapIps.map(InetAddress::getByName))
}

/**
 * 「自动」线路的顺序即优先级。实测国内 DoH（DNSPod/AliDNS）对 v2ex.com 会返回被污染的 IP
 * （Dropbox/Twitter/Facebook 段，且两轮之间随机变化），所以境外端点必须排在前面；
 * AliDNS 留作第二级，是因为它对 i.v2ex.co、www.sov2ex.com 解析正确且大陆可达，
 * 在 Cloudflare 不通时仍能挡住本地/运营商层的 DNS 劫持。
 */
val AUTO_DOH_CHAIN: List<DohPreset> = listOf(DohPreset.CLOUDFLARE, DohPreset.ALIDNS)

/** 一条可用的 DoH 端点。[bootstrap] 为空表示端点主机名交给系统 DNS 解析（IP 字面量主机不需要）。 */
data class DohEndpoint(val url: HttpUrl, val bootstrap: List<InetAddress>)

/** 用户在「解析线路」里选的项。持久化用 [key]，未知值一律回落 [Auto]。 */
sealed interface DohResolver {
    val key: String
    val label: String

    data object Auto : DohResolver {
        override val key = "auto"
        override val label = "自动"
    }

    data class Preset(val preset: DohPreset) : DohResolver {
        override val key get() = preset.name.lowercase()
        override val label get() = preset.label
    }

    data object Custom : DohResolver {
        override val key = "custom"
        override val label = "自定义 DoH"
    }

    companion object {
        val ALL: List<DohResolver> = listOf(Auto) + DohPreset.entries.map(::Preset) + Custom

        fun fromKey(key: String?): DohResolver = ALL.firstOrNull { it.key == key } ?: Auto
    }
}

/** 自定义 DoH 的校验结果：要么是可用端点，要么是给用户看的一句错误。 */
sealed interface CustomDohParse {
    data class Valid(val endpoint: DohEndpoint) : CustomDohParse
    data class Invalid(val message: String) : CustomDohParse
}

/**
 * 校验用户填的地址与 bootstrap IP。主机是 IP 字面量时 bootstrap 无意义，直接丢弃；
 * 主机是域名而没填 bootstrap 时，端点本身会先经系统 DNS 解析 —— 这是用户的选择，只在 UI 里提示。
 */
fun parseCustomDoh(url: String, bootstrap: String): CustomDohParse {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return CustomDohParse.Invalid("请填写 DoH 地址")
    val httpUrl = trimmed.toHttpUrlOrNull()
        ?: return CustomDohParse.Invalid("地址无法解析，示例：https://223.5.5.5/dns-query")
    if (httpUrl.scheme != "https") return CustomDohParse.Invalid("DoH 地址必须以 https:// 开头")
    val ips = splitBootstrapIps(bootstrap)
    ips.firstOrNull { !isIpLiteral(it) }?.let { return CustomDohParse.Invalid("bootstrap IP 格式不对：$it") }
    if (isIpLiteral(httpUrl.host)) return CustomDohParse.Valid(DohEndpoint(httpUrl, emptyList()))
    // 上面已按字面量校验，这里不该发起网络解析；但平台对个别写法仍可能拒绝（抛 UnknownHostException），
    // 这个函数会在点击「保存」时于主线程调用，任何异常都只能变成一句错误提示。
    val bootstrapAddresses = runCatching { ips.map(InetAddress::getByName) }
        .getOrElse { return CustomDohParse.Invalid("bootstrap IP 无法识别，请检查写法") }
    return CustomDohParse.Valid(DohEndpoint(httpUrl, bootstrapAddresses))
}

/** 按设置算出生效的线路链；关闭、或自定义地址非法时为空 —— 空链等价于直接用系统 DNS。 */
fun EncryptedDnsSettings.dohEndpoints(): List<DohEndpoint> {
    if (!enabled) return emptyList()
    return DohResolver.fromKey(resolverKey).endpoints(customUrl, customBootstrap)
}

/** 某条线路实际包含的端点；「自动」是两级链，其余单级。 */
fun DohResolver.endpoints(customUrl: String, customBootstrap: String): List<DohEndpoint> = when (this) {
    DohResolver.Auto -> AUTO_DOH_CHAIN.map { it.endpoint }
    is DohResolver.Preset -> listOf(preset.endpoint)
    DohResolver.Custom -> (parseCustomDoh(customUrl, customBootstrap) as? CustomDohParse.Valid)
        ?.let { listOf(it.endpoint) }.orEmpty()
}

internal fun DohEndpoint.toDns(client: OkHttpClient): DnsOverHttps = DnsOverHttps.Builder()
    .client(client)
    .url(url)
    .apply { if (bootstrap.isNotEmpty()) bootstrapDnsHosts(bootstrap) }
    .build()

internal fun splitBootstrapIps(raw: String): List<String> =
    raw.split(',', ';', ' ', '\n', '\t').map(String::trim).filter(String::isNotEmpty)

/**
 * 只认 IP 字面量。校验必须比 `InetAddress.getByName` 严：它对解析失败的字符串会退回主机名查询，
 * 一个手滑的「1:2」就会在主线程外发起一次真实 DNS 请求。
 */
internal fun isIpLiteral(value: String): Boolean = isIpv4Literal(value) || isIpv6Literal(value)

/** 只认 ASCII 数字且不接受前导零：`Char.isDigit` 会放过全角/阿拉伯-印度数字，`01.1.1.1` 则会被平台按过时写法拒绝。 */
private fun isIpv4Literal(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
            (part.length == 1 || part[0] != '0') && part.toInt() in 0..255
    }
}

private fun isIpv6Literal(value: String): Boolean {
    if (value.count { it == ':' } < 2) return false
    if (!value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) return false
    val halves = value.split("::")
    if (halves.size > 2) return false
    val groups = halves.flatMap { if (it.isEmpty()) emptyList() else it.split(':') }
    var count = 0
    groups.forEachIndexed { index, group ->
        when {
            group.isEmpty() -> return false
            '.' in group -> {
                if (index != groups.lastIndex || !isIpv4Literal(group)) return false
                count += 2
            }
            group.length > 4 -> return false
            else -> count += 1
        }
    }
    return if (halves.size == 2) count < 8 else count == 8
}
