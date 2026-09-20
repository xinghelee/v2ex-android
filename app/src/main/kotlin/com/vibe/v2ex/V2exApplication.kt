package com.vibe.v2ex

import android.app.ActivityManager
import android.app.Application
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import com.vibe.v2ex.diagnostics.CrashLog
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class V2exApplication : Application(), SingletonImageLoader.Factory {

    /**
     * 用 dagger.Lazy 而不是直接注入 OkHttpClient：后者会让 Hilt 在 onCreate 就把
     * 整张网络图（CookieJar、SecureStore、DohDns）构建出来，白白给冷启动加开销。
     */
    @Inject
    @Named("image")
    lateinit var imageClient: Lazy<OkHttpClient>

    override fun onCreate() {
        super.onCreate()
        // 未捕获异常先落盘再交给系统处理，用户下次启动能从「设置 → 关于 → 崩溃日志」拿到堆栈。
        CrashLog.install(this)
    }

    /**
     * Coil 的出厂默认允许 4096×4096 的位图 —— 单张就能吃掉 64MB 堆，而 V2EX 帖子里
     * 动辄一张几千像素高的长截图，这是「内存不足崩掉」的主要来源。这里把上限压到
     * 屏幕尺度，并显式声明磁盘缓存：看过的图在飞机上也还能打开。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val metrics = resources.displayMetrics
        return ImageLoader.Builder(context)
            .maxBitmapSize(Size(metrics.widthPixels, metrics.heightPixels * 2))
            // RGB_565 省一半内存，但不透明照片会有色带 —— 只在低内存机型上换这个折中。
            .allowRgb565(getSystemService<ActivityManager>()?.isLowRamDevice == true)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(this, 0.15).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            // 显式接到 App 自己的 OkHttp 上，图片链路才能一起吃到 DoH；
            // 不指定的话 Coil 会用 ServiceLoader 自动注册一个它自己的 client。
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient.get() })) }
            .crossfade(true)
            .build()
    }

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 192L * 1024 * 1024
    }
}
