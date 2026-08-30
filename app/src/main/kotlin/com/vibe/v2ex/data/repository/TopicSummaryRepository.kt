package com.vibe.v2ex.data.repository

import android.content.Context
import com.vibe.v2ex.data.datastore.SecureStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class DeepSeekChatRequest(
    val model: String = "deepseek-v4-flash",
    val messages: List<Message>,
    val thinking: Thinking = Thinking(),
    @SerialName("max_tokens") val maxTokens: Int = 700,
) {
    @Serializable data class Message(val role: String, val content: String)
    @Serializable data class Thinking(val type: String = "disabled")
}

@Serializable
private data class DeepSeekChatResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null,
) {
    @Serializable data class Choice(val message: Message)
    @Serializable data class Message(val content: String? = null)
    @Serializable data class ApiError(val message: String? = null)
}

@Serializable
private data class CachedTopicSummary(
    val signature: String,
    val text: String,
    val generatedAt: Long,
)

/** Manual, privacy-explicit DeepSeek summaries with a per-discussion disk cache. */
@Singleton
class TopicSummaryRepository @Inject constructor(
    @ApplicationContext context: Context,
    client: OkHttpClient,
    private val json: Json,
    private val secureStore: SecureStore,
) {
    private val directory = File(context.cacheDir, "topic-summaries")
    // App 级 Json 为减小普通 API payload 关闭了 encodeDefaults；但 DeepSeek 的
    // model / thinking / max_tokens 都是有默认值的构造参数，关闭后会被整个省略。
    // DeepSeek 因此会把请求判为 `missing field model`。
    private val requestJson = Json(json) { encodeDefaults = true }
    private val aiClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = secureStore.isDeepSeekConfigured

    suspend fun cached(topicId: Long, source: String): String? = withContext(Dispatchers.IO) {
        val cached = runCatching {
            json.decodeFromString<CachedTopicSummary>(cacheFile(topicId).readText())
        }.getOrNull() ?: return@withContext null
        cached.text.takeIf { cached.signature == signature(source) }
    }

    suspend fun generate(topicId: Long, source: String): String = withContext(Dispatchers.IO) {
        val key = secureStore.deepSeekApiKey ?: error("请先在设置中配置 DeepSeek API Key")
        val payload = DeepSeekChatRequest(
            messages = listOf(
                DeepSeekChatRequest.Message(
                    role = "system",
                    content = """
                        你是论坛阅读助手。只根据用户提供的帖子和回复总结，不补充外部事实，不猜测作者意图。
                        使用简体中文，保持中立、准确、紧凑，保留关键数字、结论与明显分歧。
                        可以使用简短 Markdown，控制在 350 字以内，结构为：核心内容、主要观点、讨论分歧。
                        如果没有明显分歧，明确写“暂无明显分歧”。
                    """.trimIndent(),
                ),
                DeepSeekChatRequest.Message(
                    role = "user",
                    content = "请总结下面这段 V2EX 讨论：\n\n$source",
                ),
            ),
        )
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(requestJson.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = aiClient.newCall(request).execute()
        val body = response.body.string()
        val decoded = runCatching { json.decodeFromString<DeepSeekChatResponse>(body) }.getOrNull()
        if (!response.isSuccessful) {
            error(deepSeekErrorMessage(response.code, decoded?.error?.message))
        }
        val summary = decoded?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
        if (summary.isBlank()) error("DeepSeek 没有返回摘要")

        directory.mkdirs()
        cacheFile(topicId).writeText(
            json.encodeToString(CachedTopicSummary(signature(source), summary, System.currentTimeMillis())),
        )
        summary
    }

    private fun cacheFile(topicId: Long) = File(directory, "$topicId.json")

    private fun signature(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun deepSeekErrorMessage(code: Int, detail: String?): String = when (code) {
        400 -> if (detail?.contains("missing field", ignoreCase = true) == true) {
            "DeepSeek 请求格式不兼容，请更新应用后重试"
        } else {
            "DeepSeek 无法处理本次请求，请稍后重试"
        }
        401, 403 -> "DeepSeek API Key 无效或没有模型权限"
        402 -> "DeepSeek 账户余额不足"
        429 -> "DeepSeek 请求过于频繁，请稍后重试"
        in 500..599 -> "DeepSeek 服务暂时不可用，请稍后重试"
        else -> "DeepSeek 请求失败（HTTP $code）"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
