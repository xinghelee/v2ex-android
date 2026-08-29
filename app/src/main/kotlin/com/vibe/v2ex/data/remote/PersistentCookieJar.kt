package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.datastore.SecureStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the v2ex.com session cookie only in [SecureStore] — never on disk unencrypted. */
@Singleton
class PersistentCookieJar @Inject constructor(
    private val secureStore: SecureStore,
) : CookieJar {
    private val memoryCookies = mutableMapOf<String, Cookie>()

    init {
        secureStore.sessionCookieHeader?.let(::absorbHeader)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { memoryCookies[it.name] = it }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        memoryCookies.values.filter { it.matches(url) }

    /** Cookies were captured outside OkHttp (e.g. an Android WebView login) — merge them in. */
    fun setCookieHeader(header: String) {
        absorbHeader(header)
        persist()
    }

    fun clear() {
        memoryCookies.clear()
        persist()
    }

    private fun absorbHeader(header: String) {
        header.split("; ").forEach { pair ->
            val (name, value) = pair.split("=", limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            if (name.isNotBlank()) {
                memoryCookies[name] = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("v2ex.com")
                    .build()
            }
        }
    }

    private fun persist() {
        secureStore.sessionCookieHeader = memoryCookies.values
            .joinToString("; ") { "${it.name}=${it.value}" }
            .ifBlank { null }
    }
}
