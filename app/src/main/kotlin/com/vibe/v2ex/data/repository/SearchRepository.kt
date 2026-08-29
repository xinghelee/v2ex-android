package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.remote.SoV2exApi
import com.vibe.v2ex.data.remote.SoV2exHit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val api: SoV2exApi,
) {
    /** [sort] `sumup` for topics (default), `created` for replies — same endpoint/shape either way. */
    suspend fun search(query: String, sort: String = "sumup"): Result<List<SoV2exHit>> = runCatching {
        api.search(query = query, sort = sort).hits
    }
}
