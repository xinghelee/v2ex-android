package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.model.Node
import com.vibe.v2ex.data.remote.V2exApiV1
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodesRepository @Inject constructor(
    private val apiV1: V2exApiV1,
) {
    private var cachedNodes: List<Node>? = null

    suspend fun allNodes(forceRefresh: Boolean = false): Result<List<Node>> = runCatching {
        if (!forceRefresh) cachedNodes?.let { return@runCatching it }
        apiV1.allNodes().also { cachedNodes = it }
    }

    suspend fun node(name: String): Result<Node> = runCatching { apiV1.showNode(name) }
}
