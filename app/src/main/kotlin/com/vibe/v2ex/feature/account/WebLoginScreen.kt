package com.vibe.v2ex.feature.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

private const val V2EX_BASE = "https://www.v2ex.com"
private const val MOBILE_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"

private const val SCRAPE_USERNAME_JS = """
(function() {
  var el = document.querySelector('.site-nav .top[href^="/member/"]')
    || document.querySelector('#site-header-menu .avatar')
    || document.querySelector('a.avatar[href^="/member/"]')
    || document.querySelector('a[href^="/member/"]');
  if (!el) return '';
  var alt = el.getAttribute('alt');
  if (alt) return alt;
  var href = el.getAttribute('href') || '';
  return href.replace('/member/', '').split('?')[0].split('/')[0];
})()
"""

/**
 * 网页登录容器：内嵌 WebView 加载 /signin，验证码和两步验证都按浏览器的方式走。
 *
 * 页面上刮到用户名只当成「候选会话」，[onConfirm] 拿这份 cookie 请求 `/settings`
 * 确认可用才算登录成功 —— 两步验证没走完时页面同样是登录态的样子，未登录时逛列表页
 * 也刮得到楼主的用户名，直接认成功就会存下一份根本用不了的会话。没通过就清空候选，
 * 等用户走完流程、页面再次加载时重试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginScreen(
    onCancel: () -> Unit,
    onConfirm: suspend (cookieHeader: String, username: String) -> WebLoginConfirmation,
) {
    // WebViewClient 不是 composable，用一个共享的 state 把候选会话交回 Compose 侧。
    val candidate = remember { mutableStateOf<Pair<String, String>?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var unverified by remember { mutableStateOf(false) }

    LaunchedEffect(candidate.value) {
        val (cookie, username) = candidate.value ?: return@LaunchedEffect
        verifying = true
        unverified = false
        val result = onConfirm(cookie, username)
        verifying = false
        if (result != WebLoginConfirmation.ACCEPTED) {
            // 网络没结论时给一句提示，否则用户只会看到「登录了但没反应」。
            unverified = result == WebLoginConfirmation.UNVERIFIED
            candidate.value = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (verifying) "正在确认登录…" else "网页登录") },
                actions = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        // 残留的旧会话 cookie 会让 V2EX 拒发验证码图（/_captcha 被重定向回首页），
                        // 登录必然失败。开始登录 = 全新会话，先清干净。
                        removeAllCookies(null)
                        flush()
                    }
                    // debug 包开启 chrome://inspect 远程调试，方便排查登录页行为。
                    val debuggable = (context.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = MOBILE_UA
                        webViewClient = object : WebViewClient() {
                            @SuppressLint("SetJavaScriptEnabled")
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                // 已有候选在验证中就别再报一次。
                                if (candidate.value != null) return
                                val path = url?.toUri()?.path.orEmpty()
                                if (path == "/signin" || path == "/2fa") return
                                val cookie = CookieManager.getInstance().getCookie(V2EX_BASE)
                                if (cookie.isNullOrBlank()) return
                                view.evaluateJavascript(SCRAPE_USERNAME_JS) { rawResult ->
                                    val username = rawResult?.trim('"').orEmpty()
                                    // V2EX 对匿名访客也下发 cookie；页面上出现自己的
                                    // /member/ 链接（能刮到用户名）才值得拿去验证，否则
                                    // 只是没登录乱逛。
                                    if (username.isBlank()) return@evaluateJavascript
                                    candidate.value = cookie to username
                                }
                            }
                        }
                        loadUrl("$V2EX_BASE/signin")
                    }
                },
                update = { webView -> if (webView.url == null) webView.loadUrl("$V2EX_BASE/signin") },
                onRelease = { it.destroy() },
            )
            if (verifying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            if (unverified) {
                Text(
                    text = "无法确认登录状态，请检查网络后重新加载页面",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
