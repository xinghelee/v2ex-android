package com.vibe.v2ex.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/** 上一次崩溃留下的报告：正文是纯文本，直接可复制 / 分享。 */
data class CrashReport(val text: String, val capturedAt: Long)

/**
 * 崩溃日志落盘。没有任何第三方上报：日志只写在 App 私有目录，
 * 由用户自己决定要不要从「设置 → 关于 → 崩溃日志」复制或分享出去。
 *
 * 只能接住 Java/Kotlin 层的未捕获异常；native 崩溃（SIGSEGV 等）不经过这里。
 */
object CrashLog {
    private const val DIR = "crash"
    private const val REPORT = "last_crash.txt"
    private const val SEEN = "last_crash.seen"
    private const val LOGCAT_LINES = 200
    private const val MAX_LOGCAT_BYTES = 64 * 1024

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** 只看文件是否存在，给设置页这种不需要内容、又跑在主线程的地方用。 */
    fun hasReport(context: Context): Boolean = reportFile(context).isFile

    fun latest(context: Context): CrashReport? {
        val file = reportFile(context)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return CrashReport(text, file.lastModified())
    }

    /** 有新报告且用户还没在启动提示里看过。 */
    fun hasUnseen(context: Context): Boolean =
        reportFile(context).isFile && !seenFile(context).exists()

    fun markSeen(context: Context) {
        runCatching { seenFile(context).apply { parentFile?.mkdirs() }.writeText("") }
    }

    fun clear(context: Context) {
        reportFile(context).delete()
        seenFile(context).delete()
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val temp = File(dir, "$REPORT.tmp")
        temp.writeText(buildReport(context, thread, error))
        val target = File(dir, REPORT)
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
        File(dir, SEEN).delete()
    }

    private fun buildReport(context: Context, thread: Thread, error: Throwable): String {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            "${info.versionName} (${info.versionCode})"
        }.getOrDefault("未知")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("V2EX Android 崩溃日志")
            appendLine("时间: $time")
            appendLine("版本: $version")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
            appendLine("线程: ${thread.name}")
            appendLine()
            appendLine("--- 异常 ---")
            appendLine(stackTraceOf(error))
            appendLine("--- 最近日志（本进程） ---")
            appendLine(recentLogcat())
        }
    }

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        val text = writer.toString()
        // 异常链极长（互相引用）时截断，避免报告膨胀到无法分享。
        val lines = text.lines()
        return if (lines.size > 400) lines.take(400).joinToString("\n") + "\n…（已截断）" else text
    }

    /** 本进程自己的 logcat 是允许读的；任何一步失败都只是少一段上下文。 */
    private fun recentLogcat(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "time", "-t", LOGCAT_LINES.toString(), "--pid=${Process.myPid()}",
        ).redirectErrorStream(true).start()
        val bytes = process.inputStream.use { it.readBytes() }
        process.waitFor()
        val trimmed = if (bytes.size > MAX_LOGCAT_BYTES) bytes.copyOfRange(bytes.size - MAX_LOGCAT_BYTES, bytes.size) else bytes
        String(trimmed, Charsets.UTF_8)
    }.getOrDefault("（读取 logcat 失败）")

    private fun reportFile(context: Context) = File(File(context.filesDir, DIR), REPORT)
    private fun seenFile(context: Context) = File(File(context.filesDir, DIR), SEEN)
}
