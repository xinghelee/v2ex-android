package com.vibe.v2ex

import android.app.ActivityManager
import android.app.Application
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class V2exApplication : Application(), SingletonImageLoader.Factory {

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
            .crossfade(true)
            .build()
    }

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 192L * 1024 * 1024
    }
}
