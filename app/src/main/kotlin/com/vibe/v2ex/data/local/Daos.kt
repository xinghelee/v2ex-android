package com.vibe.v2ex.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTopicDao {
    @Query("SELECT * FROM offline_topics WHERE topicId = :topicId")
    suspend fun get(topicId: Long): OfflineTopicEntity?

    @Query("SELECT * FROM offline_topics ORDER BY cachedAt DESC")
    fun observeAll(): Flow<List<OfflineTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineTopicEntity)

    @Query("UPDATE offline_topics SET readingProgress = :progress WHERE topicId = :topicId")
    suspend fun updateProgress(topicId: Long, progress: Int)

    @Query("DELETE FROM offline_topics WHERE topicId = :topicId")
    suspend fun delete(topicId: Long)

    @Query("DELETE FROM offline_topics")
    suspend fun clear()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE topicId = :topicId LIMIT 1")
    suspend fun forTopic(topicId: Long?): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity): Long

    @Delete
    suspend fun delete(entity: DraftEntity)
}

@Dao
interface BlockListDao {
    @Query("SELECT username FROM blocked_users")
    fun observeBlockedUsers(): Flow<List<String>>

    @Query("SELECT keyword FROM blocked_keywords")
    fun observeBlockedKeywords(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUser(entity: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE username = :username")
    suspend fun unblockUser(username: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockKeyword(entity: BlockedKeywordEntity)

    @Query("DELETE FROM blocked_keywords WHERE keyword = :keyword")
    suspend fun unblockKeyword(keyword: String)
}

@Dao
interface ModerationVisibilityDao {
    @Query("SELECT topicId FROM hidden_topics")
    fun observeHiddenTopicIds(): Flow<List<Long>>

    @Query("SELECT replyId FROM hidden_replies")
    fun observeHiddenReplyIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideTopic(entity: HiddenTopicEntity)

    @Query("DELETE FROM hidden_topics WHERE topicId = :topicId")
    suspend fun unhideTopic(topicId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideReply(entity: HiddenReplyEntity)

    @Query("DELETE FROM hidden_replies WHERE replyId = :replyId")
    suspend fun unhideReply(replyId: Long)
}

@Dao
interface FavoriteTopicDao {
    @Query("SELECT * FROM favorite_topics ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavoriteTopicEntity>>

    @Query("SELECT topicId FROM favorite_topics")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteTopicEntity)

    @Query("DELETE FROM favorite_topics WHERE topicId = :topicId")
    suspend fun remove(topicId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY viewedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE topicId = :topicId")
    suspend fun remove(topicId: Long)

    @Query("DELETE FROM history WHERE viewedAt < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReportEntity)

    @Query("SELECT * FROM reports WHERE deliveredAt IS NULL")
    suspend fun pending(): List<ReportEntity>

    @Update
    suspend fun update(entity: ReportEntity)
}
