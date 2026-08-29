package com.vibe.v2ex.feature.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
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
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = MOBILE_UA
                    webViewClient = object : WebViewClient() {
                        @SuppressLint("SetJavaScriptEnabled")
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            val path = url?.toUri()?.path.orEmpty()
                            if (path == "/signin" || path == "/2fa") return
                            val cookie = CookieManager.getInstance().getCookie(V2EX_BASE)
                            if (cookie.isNullOrBlank()) return
                            view.evaluateJavascript(SCRAPE_USERNAME_JS) { rawResult ->
                                val username = rawResult?.trim('"').orEmpty()
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
