package com.vibe.v2ex.feature.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginScreen(onCancel: () -> Unit, onSuccess: (cookieHeader: String, username: String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网页登录") },
                actions = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                        private var reported = false

                        @SuppressLint("SetJavaScriptEnabled")
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            if (reported) return
                            val path = url?.toUri()?.path.orEmpty()
                            if (path == "/signin" || path == "/2fa") return
                            val cookie = CookieManager.getInstance().getCookie(V2EX_BASE)
                            if (cookie.isNullOrBlank()) return
                            view.evaluateJavascript(SCRAPE_USERNAME_JS) { rawResult ->
                                val username = rawResult?.trim('"').orEmpty()
                                // V2EX 对匿名访客也下发 cookie；页面上出现自己的
                                // /member/ 链接（能刮到用户名）才是真登录，否则
                                // 只是没登录乱逛 —— 千万不能当成功。
                                if (username.isBlank()) return@evaluateJavascript
                                reported = true
                                onSuccess(cookie, username)
                            }
                        }
                    }
                    loadUrl("$V2EX_BASE/signin")
                }
            },
            update = { webView -> if (webView.url == null) webView.loadUrl("$V2EX_BASE/signin") },
            onRelease = { it.destroy() },
        )
    }
}
