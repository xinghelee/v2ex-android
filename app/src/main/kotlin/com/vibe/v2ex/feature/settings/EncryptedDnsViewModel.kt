package com.vibe.v2ex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.data.remote.CustomDohParse
import com.vibe.v2ex.data.remote.DohProbe
import com.vibe.v2ex.data.remote.DohProbeResult
import com.vibe.v2ex.data.remote.DohResolver
import com.vibe.v2ex.data.remote.endpoints
import com.vibe.v2ex.data.remote.parseCustomDoh
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

data class EncryptedDnsUiState(
    val enabled: Boolean = false,
    val resolver: DohResolver = DohResolver.Auto,
    val customUrl: String = "",
    val customBootstrap: String = "",
    /** 按 `DohResolver.key` 存的测试结果；没测过的线路不在里面。 */
    val probes: Map<String, ProbeState> = emptyMap(),
) {
    val customValid: Boolean get() = parseCustomDoh(customUrl, customBootstrap) is CustomDohParse.Valid
}

@HiltViewModel
class EncryptedDnsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val probe: DohProbe,
) : ViewModel() {
    private val probes = MutableStateFlow<Map<String, ProbeState>>(emptyMap())

    val uiState: StateFlow<EncryptedDnsUiState> = combine(settings.encryptedDns, probes) { prefs, probes ->
        EncryptedDnsUiState(
            enabled = prefs.enabled,
            resolver = DohResolver.fromKey(prefs.resolverKey),
            customUrl = prefs.customUrl,
            customBootstrap = prefs.customBootstrap,
            probes = probes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EncryptedDnsUiState())

    /** 只写偏好；DohDns 自己订阅这个 Flow，切换后立刻重建解析链并清连接池，不需要重启 App。 */
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { settings.setEncryptedDnsEnabled(enabled) }

    fun select(resolver: DohResolver) = viewModelScope.launch { settings.setEncryptedDnsResolver(resolver.key) }

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
            val result = probe.probe(endpoints)
            probes.update { it + (resolver.key to ProbeState.Done(result)) }
        }
    }

    fun probeAll() = DohResolver.ALL.forEach(::probe)
}
