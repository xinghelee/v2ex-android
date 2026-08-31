package com.vibe.v2ex.data.remote

import com.vibe.v2ex.data.model.Member
import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.model.Notification
import com.vibe.v2ex.data.model.Reply
import com.vibe.v2ex.data.model.Topic
import kotlinx.serialization.Serializable
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** V2EX API 1.0 — public, unauthenticated, rate limited to 600 req/hour/IP. Raw JSON, not enveloped. */
interface V2exApiV1 {
    @GET("api/topics/latest.json")
    suspend fun latestTopics(): List<Topic>

    @GET("api/topics/hot.json")
    suspend fun hotTopics(): List<Topic>

    @GET("api/topics/show.json")
    suspend fun topicsInNode(@Query("node_name") nodeName: String): List<Topic>

    @GET("api/topics/show.json")
    suspend fun topicsByMember(@Query("username") username: String): List<Topic>

    @GET("api/topics/show.json")
    suspend fun topic(@Query("id") id: Long): List<Topic>

    @GET("api/nodes/show.json")
    suspend fun showNode(@Query("name") name: String): Node

    @GET("api/nodes/all.json")
    suspend fun allNodes(): List<Node>

    @GET("api/members/show.json")
    suspend fun showMember(@Query("username") username: String): Member

    /** Unpaginated, documented upstream as unreliable/empty for very recent threads. */
    @GET("api/replies/show.json")
    suspend fun repliesForTopic(@Query("topic_id") topicId: Long): List<Reply>
}

/** Every API 2.0 payload is enveloped: `{success, message, result}`. */
@Serializable
data class V2Envelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val result: T? = null,
)

/** V2EX API 2.0 — Personal Access Token (Bearer) required, enveloped responses. */
interface V2exApiV2 {
    @GET("api/v2/member")
    suspend fun me(@Header("Authorization") authorization: String? = null): V2Envelope<Member>

    /** Only page 1 is ever actually used by the UI — the API supports more but nothing paginates it. */
    @GET("api/v2/notifications")
    suspend fun notifications(@Query("p") page: Int = 1): V2Envelope<List<Notification>>

    @DELETE("api/v2/notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: Long): V2Envelope<Unit>

    @GET("api/v2/nodes/{name}/topics")
    suspend fun topicsForNode(@Path("name") name: String, @Query("p") page: Int = 1): V2Envelope<List<Topic>>

    @GET("api/v2/topics/{id}")
    suspend fun topic(@Path("id") id: Long): V2Envelope<Topic>

    /** 20 replies/page (empirically verified — not the documented 100). */
    @GET("api/v2/topics/{id}/replies")
    suspend fun repliesForTopic(@Path("id") id: Long, @Query("p") page: Int = 1): V2Envelope<List<Reply>>
}
