package com.vibe.v2ex.di

import com.vibe.v2ex.data.datastore.SecureStore
import com.vibe.v2ex.data.remote.DohDns
import com.vibe.v2ex.data.remote.PersistentCookieJar
import com.vibe.v2ex.data.remote.SoV2exApi
import com.vibe.v2ex.data.remote.V2exApiV1
import com.vibe.v2ex.data.remote.V2exApiV2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val V2EX_BASE_URL = "https://www.v2ex.com/"
private const val SOV2EX_BASE_URL = "https://www.sov2ex.com/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar,
        secureStore: SecureStore,
        dohDns: DohDns,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        // 开关关闭时 DohDns 逐字转发给 Dns.SYSTEM，所以这一行常态下没有任何行为变化。
        .dns(dohDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            // 粘贴的 token 可能夹带换行/空格 — 头部值里出现 0x0a 会让 OkHttp 直接抛异常
            val token = secureStore.personalAccessToken?.filterNot(Char::isWhitespace)
            val authorized = if (
                request.header("Authorization") == null &&
                !token.isNullOrBlank() &&
                request.url.encodedPath.contains("/api/v2/")
            ) {
                request.newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                request
            }
            chain.proceed(authorized)
        }
        // Cloudflare fronts /api/*.json with a 5-day max-age and ignores no-cache headers —
        // a genuinely novel URL is the only reliable way to force a fresh response.
        .addInterceptor { chain ->
            val request = chain.request()
            val request2 = if (request.url.encodedPath.contains("/api/")) {
                val bustedUrl = request.url.newBuilder()
                    .addQueryParameter("_", System.currentTimeMillis().toString())
                    .build()
                request.newBuilder().url(bustedUrl).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).build()
            } else {
                request
            }
            chain.proceed(request2)
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // BASIC 当前不输出请求头；仍显式脱敏，避免未来提高日志级别时泄露 PAT。
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    /**
     * 图片专用 client：共享上面这个 client 的 DNS（含 DoH）、Dispatcher 与 ConnectionPool，
     * 但必须剥掉两样东西 —— CookieJar（图床没有理由收到 V2EX 会话 cookie）和全部
     * interceptor（PAT 注入、`/api/` cache-busting + FORCE_NETWORK 对图片只有害处）。
     *
     * `newBuilder()` 把 interceptors 拷进新的可变列表，`clear()` 不会动到原 client。
     */
    @Provides
    @Singleton
    @Named("image")
    fun provideImageOkHttpClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .also { it.interceptors().clear() }
        .build()

    @Provides
    @Singleton
    @Named("v2ex")
    fun provideV2exRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(V2EX_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @Named("sov2ex")
    fun provideSoV2exRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(SOV2EX_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideV2exApiV1(@Named("v2ex") retrofit: Retrofit): V2exApiV1 = retrofit.create(V2exApiV1::class.java)

    @Provides
    @Singleton
    fun provideV2exApiV2(@Named("v2ex") retrofit: Retrofit): V2exApiV2 = retrofit.create(V2exApiV2::class.java)

    @Provides
    @Singleton
    fun provideSoV2exApi(@Named("sov2ex") retrofit: Retrofit): SoV2exApi = retrofit.create(SoV2exApi::class.java)
}
