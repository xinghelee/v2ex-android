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

    var personalAccessToken: String?
        get() = prefs.getString(KEY_PAT, null)
        set(value) = prefs.edit().putString(KEY_PAT, value).apply()

    var sessionCookieHeader: String?
        get() = prefs.getString(KEY_COOKIES, null)
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    var sessionUsername: String?
        get() = prefs.getString(KEY_SESSION_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_SESSION_USERNAME, value).apply()

    val isTokenSet: Boolean get() = !personalAccessToken.isNullOrBlank()
    val isWebSessionActive: Boolean get() = !sessionCookieHeader.isNullOrBlank()
    val isSignedIn: Boolean get() = isTokenSet || isWebSessionActive

    fun clearWebSession() {
        prefs.edit().remove(KEY_COOKIES).remove(KEY_SESSION_USERNAME).apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_PAT).apply()
    }

    private companion object {
        const val KEY_PAT = "personal_access_token"
        const val KEY_COOKIES = "session_cookie_header"
        const val KEY_SESSION_USERNAME = "session_username"
    }
}
