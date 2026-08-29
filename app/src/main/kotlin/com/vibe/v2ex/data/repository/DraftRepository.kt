package com.vibe.v2ex.data.repository

import com.vibe.v2ex.data.local.DraftDao
import com.vibe.v2ex.data.local.DraftEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepository @Inject constructor(
    private val draftDao: DraftDao,
) {
    fun observeAll(): Flow<List<DraftEntity>> = draftDao.observeAll()

    suspend fun forTopic(topicId: Long?): DraftEntity? = draftDao.forTopic(topicId)

    /** Pass [draftId] from a previous [save] call to update that same row instead of inserting a new one. */
    suspend fun save(draftId: Long?, topicId: Long?, title: String, content: String, nodeName: String?): Long =
        draftDao.upsert(
            DraftEntity(
                id = draftId ?: 0,
                topicId = topicId,
                title = title,
                content = content,
                nodeName = nodeName,
                updatedAt = System.currentTimeMillis(),
            ),
        )

    suspend fun delete(entity: DraftEntity) = draftDao.delete(entity)
}
