package com.vibe.v2ex.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs two unrelated credential systems with an Android Keystore-wrapped
 * SharedPreferences file — never written to cloud backup (see backup_rules.xml /
 * data_extraction_rules.xml), mirroring the iOS Keychain-only session storage:
 *  - the API 2.0 Personal Access Token (gates Notifications, Profile, pagination)
 *  - the web session cookie + username, captured from the WebView login (gates
 *    in-app reply/publish/favorite-sync). Password is never persisted anywhere.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "v2ex_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** 入库前剔除所有空白（换行/空格）— 粘贴来的 token 常带尾部换行，进请求头会让 OkHttp 抛异常。 */
    var personalAccessToken: String?
        get() = prefs.getString(KEY_PAT, null)?.filterNot(Char::isWhitespace)?.ifBlank { null }
        set(value) {
            val cleaned = value?.filterNot(Char::isWhitespace)?.ifBlank { null }
            prefs.edit().putString(KEY_PAT, cleaned).apply()
        }

    /** DeepSeek key is device-only and protected by Android Keystore, just like the V2EX token. */
    var deepSeekApiKey: String?
        get() = prefs.getString(KEY_DEEPSEEK_API_KEY, null)?.filterNot(Char::isWhitespace)?.ifBlank { null }
        set(value) {
            val cleaned = value?.filterNot(Char::isWhitespace)?.ifBlank { null }
            prefs.edit().putString(KEY_DEEPSEEK_API_KEY, cleaned).apply()
        }

    var sessionCookieHeader: String?
        get() = prefs.getString(KEY_COOKIES, null)
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    var sessionUsername: String?
        get() = prefs.getString(KEY_SESSION_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_SESSION_USERNAME, value).apply()

    /**
     * 显式登录标记，只在 WebView 登录确认成功时置位。V2EX 对匿名访客也下发
     * cookie（cookie jar 会原样持久化），所以「有 cookie」不能当成「已登录」。
     */
    var webLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_WEB_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_WEB_LOGGED_IN, value).apply()

    val isTokenSet: Boolean get() = !personalAccessToken.isNullOrBlank()
    val isWebSessionActive: Boolean get() = webLoggedIn && !sessionCookieHeader.isNullOrBlank()
    val isSignedIn: Boolean get() = isTokenSet || isWebSessionActive
    val isDeepSeekConfigured: Boolean get() = !deepSeekApiKey.isNullOrBlank()

    fun clearWebSession() {
        prefs.edit()
            .remove(KEY_COOKIES)
            .remove(KEY_SESSION_USERNAME)
            .remove(KEY_WEB_LOGGED_IN)
            .apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_PAT).apply()
    }

    fun clearDeepSeekApiKey() {
        prefs.edit().remove(KEY_DEEPSEEK_API_KEY).apply()
    }

    private companion object {
        const val KEY_PAT = "personal_access_token"
        const val KEY_COOKIES = "session_cookie_header"
        const val KEY_SESSION_USERNAME = "session_username"
        const val KEY_WEB_LOGGED_IN = "web_logged_in"
        const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
    }
}
