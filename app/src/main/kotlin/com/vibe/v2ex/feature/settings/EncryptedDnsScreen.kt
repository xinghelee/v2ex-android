package com.vibe.v2ex.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.v2ex.data.remote.ConnectionProbeResult
import com.vibe.v2ex.data.remote.DohProbeResult
import com.vibe.v2ex.data.remote.DohResolver
import com.vibe.v2ex.designsystem.SectionHeader
import com.vibe.v2ex.designsystem.V2Card

/** 设置 → 加密 DNS 解析：开关、线路单选、逐条测速、自定义端点。 */
@Composable
fun EncryptedDnsScreen(
    onBack: () -> Unit,
    viewModel: EncryptedDnsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCustomEditor by rememberSaveable { mutableStateOf(false) }
    var showPreferredIpEditor by rememberSaveable { mutableStateOf(false) }

    if (showPreferredIpEditor) {
        PreferredIpDialog(
            initial = uiState.preferredIps,
            onDismiss = { showPreferredIpEditor = false },
            onSave = viewModel::savePreferredIps,
        )
    }

    if (showCustomEditor) {
        CustomDohDialog(
            initialUrl = uiState.customUrl,
            initialBootstrap = uiState.customBootstrap,
            onDismiss = { showCustomEditor = false },
            onSave = viewModel::saveCustom,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        BackRow(onBack)
        Text(
            text = "加密 DNS 解析",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 14.dp),
        )

        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            SwitchRow(
                label = "启用加密 DNS",
                subtitle = if (uiState.enabled) "域名解析走所选线路，不通时回退系统 DNS" else "关闭时使用系统 DNS",
                checked = uiState.enabled,
                onCheckedChange = viewModel::setEnabled,
            )
            InsetDivider()
            SwitchRow(
                label = "优先使用 IPv6 地址",
                subtitle = "关闭时先连 IPv4；没有 IPv6 路由的网络打开后每个域名首次请求都会先等超时",
                checked = uiState.preferIpv6,
                onCheckedChange = viewModel::setPreferIpv6,
                enabled = uiState.enabled,
            )
            Text(
                "开启后，你打开的网址所属域名（含帖子里的第三方图床）会发给所选线路的 DNS 服务商解析。" +
                    "这能绕开本地网络的 DNS 劫持与广告注入，但不解决 IP 层封锁。" +
                    "网页登录走系统 WebView，不受本设置影响。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(
            title = "解析线路",
            trailing = {
                Text(
                    text = "测试全部",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = viewModel::probeAll)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            },
        )
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            DohResolver.ALL.forEachIndexed { index, resolver ->
                if (index > 0) InsetDivider()
                val isCustom = resolver is DohResolver.Custom
                ResolverRow(
                    label = resolver.label,
                    subtitle = when (resolver) {
                        DohResolver.Auto -> "Cloudflare → 阿里云，两家都不通时回退系统 DNS"
                        is DohResolver.Preset -> resolver.preset.url
                        DohResolver.Custom -> uiState.customUrl.ifBlank { "填写你的 HTTPS DoH 地址" } +
                            uiState.customBootstrap.takeIf { it.isNotBlank() }?.let { " · bootstrap $it" }.orEmpty()
                    },
                    selected = resolver.key == uiState.resolver.key,
                    probe = uiState.probes[resolver.key],
                    onSelect = {
                        // 自定义线路没填地址时，选中它等于什么都没选；直接带去填。
                        if (isCustom && !uiState.customValid) showCustomEditor = true else viewModel.select(resolver)
                    },
                    onProbe = { viewModel.probe(resolver) },
                    onEdit = if (isCustom) ({ showCustomEditor = true }) else null,
                )
            }
        }
        Text(
            text = "测试会通过该线路解析 www.v2ex.com 并计时。自定义地址必须是 HTTPS；" +
                "主机名形式的地址若不填 bootstrap IP，端点本身会先经系统 DNS 解析。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        )

        SectionHeader(title = "V2EX 连接")
        V2Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            ActionRow(
                label = "加密域名（ECH）",
                subtitle = when {
                    !uiState.echSupported -> "当前系统不支持，需要 Android 17 及以上"
                    !uiState.enabled -> "开启加密 DNS 后自动使用"
                    else -> "已启用：连接 V2EX 时隐藏域名，以连接测试结果为准"
                },
            )
            InsetDivider()
            ActionRow(
                label = "连接测试",
                subtitle = "按当前设置连一次 www.v2ex.com",
                result = uiState.connectionProbe,
                onProbe = viewModel::probeConnection,
            )
            InsetDivider()
            ActionRow(
                label = "优选 IP",
                subtitle = uiState.preferredIpList.takeIf { it.isNotEmpty() }
                    ?.let { "V2EX 只连这 ${it.size} 个 IP" } ?: "未设置，按 DNS 结果连接",
                onEdit = { showPreferredIpEditor = true },
                onProbe = viewModel::probePreferredIps.takeIf { uiState.preferredIpList.isNotEmpty() },
                running = uiState.ipProbes.values.any { it is ConnectionProbeState.Running },
            )
            uiState.preferredIpList.forEach { ip ->
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text(text = ip, style = MaterialTheme.typography.labelMedium)
                    ConnectionProbeLine(uiState.ipProbes[ip])
                }
            }
        }
        Text(
            text = "国内直连 V2EX 的障碍有两层：DNS 污染（加密 DNS 可解决）和明文域名（SNI）被识别后重置连接。" +
                "ECH 把域名加密，需要 Android 17 及以上并开启加密 DNS；网页登录走系统 WebView，暂不支持。" +
                "优选 IP 只作用于 V2EX 主站，填 Cloudflare 节点 IP，可先逐个测试再保留最快的。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBackIos,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = "设置",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 一条线路：单选 + 名称/地址 + 测速结果，右侧「编辑」（仅自定义）和「测试」。 */
@Composable
private fun ResolverRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    probe: ProbeState?,
    onSelect: () -> Unit,
    onProbe: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .heightIn(min = 56.dp)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            when (val result = (probe as? ProbeState.Done)?.result) {
                is DohProbeResult.Success -> ProbeLine("${result.millis} ms · ${result.address}", MaterialTheme.colorScheme.primary)
                is DohProbeResult.Failed -> ProbeLine("失败：${result.reason}", MaterialTheme.colorScheme.error)
                null -> Unit
            }
        }
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onProbe, enabled = probe !is ProbeState.Running) {
            if (probe is ProbeState.Running) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "测试", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProbeLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun CustomDohDialog(
    initialUrl: String,
    initialBootstrap: String,
    onDismiss: () -> Unit,
    /** 返回 null 表示已保存；否则是要内联显示的错误。 */
    onSave: (url: String, bootstrap: String) -> String?,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var bootstrap by remember { mutableStateOf(initialBootstrap) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义 DoH") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("DoH 地址") },
                    placeholder = { Text("https://223.5.5.5/dns-query") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bootstrap,
                    onValueChange = { bootstrap = it; error = null },
                    label = { Text("bootstrap IP（可选，逗号分隔）") },
                    placeholder = { Text("1.1.1.1, 1.0.0.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = "地址主机是域名时，bootstrap IP 用来绕开系统 DNS 解析端点本身；主机是 IP 时不需要填。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(url, bootstrap)
                if (error == null) onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一行：标题/说明，可选的测试结果，右侧可选「编辑」「测试」。 */
@Composable
private fun ActionRow(
    label: String,
    subtitle: String,
    result: ConnectionProbeState? = null,
    onEdit: (() -> Unit)? = null,
    onProbe: (() -> Unit)? = null,
    running: Boolean = result is ConnectionProbeState.Running,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
            ConnectionProbeLine(result)
        }
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
            }
        }
        onProbe?.let {
            IconButton(onClick = it, enabled = !running) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "测试", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ConnectionProbeLine(state: ConnectionProbeState?) {
    when (val result = (state as? ConnectionProbeState.Done)?.result) {
        is ConnectionProbeResult.Success -> ProbeLine(
            listOfNotNull(
                "${result.millis} ms",
                result.colo,
                result.serverIp,
                if (result.echActive) "ECH 已生效" else "ECH 未生效",
            ).joinToString(" · "),
            MaterialTheme.colorScheme.primary,
        )
        is ConnectionProbeResult.Failed -> ProbeLine("失败：${result.reason}", MaterialTheme.colorScheme.error)
        null -> Unit
    }
}

@Composable
private fun PreferredIpDialog(
    initial: String,
    onDismiss: () -> Unit,
    /** 返回 null 表示已保存；否则是要内联显示的错误。 */
    onSave: (String) -> String?,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("V2EX 优选 IP") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("IP 地址（逗号分隔，最多 8 个）") },
                    placeholder = { Text("172.66.133.207, 104.16.1.1") },
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = "只对 www.v2ex.com 生效。留空保存即恢复按 DNS 结果连接。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(value)
                if (error == null) onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
