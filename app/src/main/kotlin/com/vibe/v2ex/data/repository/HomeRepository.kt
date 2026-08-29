package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.model.Topic
import com.vibe.v2ex.data.remote.V2exApiV1
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val apiV1: V2exApiV1,
) {
    suspend fun hotTopics(): Result<List<Topic>> = runCatching { apiV1.hotTopics() }
}
