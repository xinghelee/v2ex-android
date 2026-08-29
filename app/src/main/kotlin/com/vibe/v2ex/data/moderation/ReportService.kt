package com.vibe.v2ex.data.moderation

import com.vibe.v2ex.data.local.ReportEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val REPORT_ENDPOINT = "https://reports.xinghelee.com/report"

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/**
 * Fire-and-forget delivery for [ModerationStore]'s local-first reports — matches the
 * server contract in the iOS project's docs/report-worker/worker.js exactly: no auth
 * header on submission (the worker's admin read endpoint is separately key-gated),
 * strict field whitelist, dedup by [ReportEntity.id] server-side.
 */
@Singleton
class ReportService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /** true iff delivered (any 2xx) — the caller keeps retrying an undelivered report indefinitely. */
    suspend fun submit(report: ReportEntity): Boolean = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("id", report.id)
            put("kind", report.kind)
            put("targetType", report.targetType)
            put("targetID", report.targetId)
            report.topicId?.let { put("topicID", it) }
            report.author?.let { put("author", it) }
            report.excerpt?.let { put("excerpt", it) }
            put("reason", report.reason)
            put("reasonTitle", report.reasonTitle)
            report.note?.let { put("note", it) }
            put("createdAt", isoFormat.format(Date(report.createdAt)))
            report.topicId?.let { put("url", "https://www.v2ex.com/t/$it") }
            put("appVersion", "1.0.0")
            put("platform", "Android")
        }
        val body = Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(REPORT_ENDPOINT).post(body).build()
        runCatching {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
