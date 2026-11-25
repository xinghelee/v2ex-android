package com.vibe.v2ex.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.remote.ConnectionProbeResult
import com.vibe.v2ex.data.remote.CustomDohParse
import com.vibe.v2ex.data.remote.DohProbe
import com.vibe.v2ex.data.remote.DohProbeResult
import com.vibe.v2ex.data.remote.DohResolver
import com.vibe.v2ex.data.remote.ECH_MIN_SDK
import com.vibe.v2ex.data.remote.V2exConnectionProbe
import com.vibe.v2ex.data.remote.endpoints
import com.vibe.v2ex.data.remote.isIpLiteral
import com.vibe.v2ex.data.remote.parseCustomDoh
import com.vibe.v2ex.data.remote.parsePreferredIps
import com.vibe.v2ex.data.remote.splitBootstrapIps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProbeState {
    data object Running : ProbeState
    data class Done(val result: DohProbeResult) : ProbeState
}

sealed interface ConnectionProbeState {
    data object Running : ConnectionProbeState
    data class Done(val result: ConnectionProbeResult) : ConnectionProbeState
}

data class EncryptedDnsUiState(
    val enabled: Boolean = false,
    val resolver: DohResolver = DohResolver.Auto,
    val customUrl: String = "",
    val customBootstrap: String = "",
    val preferIpv6: Boolean = false,
    /** 按 `DohResolver.key` 存的测试结果；没测过的线路不在里面。 */
    val probes: Map<String, ProbeState> = emptyMap(),
    /** 原样保存的优选 IP 文本；解析后的列表见 [preferredIpList]。 */
    val preferredIps: String = "",
    /** 当前配置下的整体连接测试。 */
    val connectionProbe: ConnectionProbeState? = null,
    /** 按 IP 字符串存的单个优选 IP 测速结果。 */
    val ipProbes: Map<String, ConnectionProbeState> = emptyMap(),
    /** 系统 TLS 是否支持 ECH（Android 17+）。 */
    val echSupported: Boolean = Build.VERSION.SDK_INT >= ECH_MIN_SDK,
) {
    val preferredIpList: List<String> get() = splitBootstrapIps(preferredIps).filter(::isIpLiteral).distinct()

    val customValid: Boolean get() = parseCustomDoh(customUrl, customBootstrap) is CustomDohParse.Valid
}

@HiltViewModel
class EncryptedDnsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val probe: DohProbe,
    private val connectionProbe: V2exConnectionProbe,
) : ViewModel() {
    private val probes = MutableStateFlow<Map<String, ProbeState>>(emptyMap())
    private val connection = MutableStateFlow<ConnectionProbeState?>(null)
    private val ipProbes = MutableStateFlow<Map<String, ConnectionProbeState>>(emptyMap())

    val uiState: StateFlow<EncryptedDnsUiState> =
        combine(settings.encryptedDns, probes, connection, ipProbes) { prefs, probes, connection, ipProbes ->
            EncryptedDnsUiState(
                enabled = prefs.enabled,
                resolver = DohResolver.fromKey(prefs.resolverKey),
                customUrl = prefs.customUrl,
                customBootstrap = prefs.customBootstrap,
                preferIpv6 = prefs.preferIpv6,
                probes = probes,
                preferredIps = prefs.preferredIps,
                connectionProbe = connection,
                ipProbes = ipProbes,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EncryptedDnsUiState())

    /** 只写偏好；DohDns 自己订阅这个 Flow，切换后立刻重建解析链并清连接池，不需要重启 App。 */
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { settings.setEncryptedDnsEnabled(enabled) }

    fun select(resolver: DohResolver) = viewModelScope.launch { settings.setEncryptedDnsResolver(resolver.key) }

    fun setPreferIpv6(prefer: Boolean) = viewModelScope.launch { settings.setEncryptedDnsPreferIpv6(prefer) }

    /** 校验通过才落盘并选中自定义线路；返回的错误文案由对话框内联显示。 */
    fun saveCustom(url: String, bootstrap: String): String? {
        val parsed = parseCustomDoh(url, bootstrap)
        if (parsed is CustomDohParse.Invalid) return parsed.message
        viewModelScope.launch {
            settings.setEncryptedDnsCustom(url.trim(), bootstrap.trim(), DohResolver.Custom.key)
        }
        return null
    }

    fun probe(resolver: DohResolver) {
        viewModelScope.launch {
            val prefs = settings.encryptedDns.first()
            val endpoints = resolver.endpoints(prefs.customUrl, prefs.customBootstrap)
            if (endpoints.isEmpty()) {
                probes.update { it + (resolver.key to ProbeState.Done(DohProbeResult.Failed("未填写有效地址"))) }
                return@launch
            }
            probes.update { it + (resolver.key to ProbeState.Running) }
            val result = probe.probe(endpoints, preferIpv6 = prefs.preferIpv6)
            probes.update { it + (resolver.key to ProbeState.Done(result)) }
        }
    }

    fun probeAll() = DohResolver.ALL.forEach(::probe)

    /** 校验通过才落盘；空字符串 = 清除优选 IP。返回的错误文案由对话框内联显示。 */
    fun savePreferredIps(raw: String): String? {
        val entries = splitBootstrapIps(raw)
        val invalid = entries.filterNot(::isIpLiteral)
        if (invalid.isNotEmpty()) return "不是有效的 IP 地址：${invalid.joinToString("、")}"
        if (entries.distinct().size > MAX_PREFERRED_IPS) return "最多填写 $MAX_PREFERRED_IPS 个 IP"
        ipProbes.value = emptyMap()
        connection.value = null
        viewModelScope.launch { settings.setEncryptedDnsPreferredIps(entries.distinct().joinToString(", ")) }
        return null
    }

    /** 按当前生效的配置连一次 V2EX，看耗时、落到哪个节点、ECH 是否生效。 */
    fun probeConnection() {
        if (connection.value is ConnectionProbeState.Running) return
        connection.value = ConnectionProbeState.Running
        viewModelScope.launch {
            connection.value = ConnectionProbeState.Done(connectionProbe.probe())
        }
    }

    /** 逐个测优选 IP，互不影响；结果按 IP 显示在各自那一行。 */
    fun probePreferredIps() {
        uiState.value.preferredIpList.forEach { ip ->
            if (ipProbes.value[ip] is ConnectionProbeState.Running) return@forEach
            ipProbes.update { it + (ip to ConnectionProbeState.Running) }
            viewModelScope.launch {
                val result = connectionProbe.probe(pinnedIps = parsePreferredIps(ip))
                ipProbes.update { it + (ip to ConnectionProbeState.Done(result)) }
            }
        }
    }

    private companion object {
        const val MAX_PREFERRED_IPS = 8
    }
}
