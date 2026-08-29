package com.vibe.v2ex.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Community-run full-text index — V2EX itself has no official search endpoint.
 * Topics and replies share this identical endpoint/shape, differentiated only by [sort]
 * (`sumup` for topics, `created` for replies) — sov2ex has no separate "reply" hit type.
 */
interface SoV2exApi {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("sort") sort: String = "sumup",
        @Query("from") from: Int = 0,
        @Query("size") size: Int = 20,
    ): SoV2exResponse
}

@Serializable
data class SoV2exResponse(
    val took: Int = 0,
    val total: Int = 0,
    val hits: List<SoV2exHit> = emptyList(),
)

@Serializable
data class SoV2exHit(
    @SerialName("_source") val source: SoV2exSource = SoV2exSource(),
    val highlight: SoV2exHighlight? = null,
)

@Serializable
data class SoV2exSource(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    /** 可能是节点名字符串，也可能是数字 node id — 用 JsonPrimitive 兼容两种。 */
    val node: kotlinx.serialization.json.JsonPrimitive? = null,
    val member: String? = null,
    val replies: Int = 0,
    /** ISO8601 字符串（非 epoch）。 */
    val created: String? = null,
) {
    /** 仅当 node 是字符串名时返回，数字 id 无法本地映射则为 null。 */
    val nodeName: String?
        get() = node?.takeUnless { it.isString.not() }?.content
}

@Serializable
data class SoV2exHighlight(
    val title: List<String>? = null,
    val content: List<String>? = null,
)
