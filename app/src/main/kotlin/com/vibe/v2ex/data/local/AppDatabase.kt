package com.vibe.v2ex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OfflineTopicEntity::class,
        DraftEntity::class,
        BlockedUserEntity::class,
        BlockedKeywordEntity::class,
        HiddenTopicEntity::class,
        HiddenReplyEntity::class,
        FavoriteTopicEntity::class,
        HistoryEntity::class,
        ReportEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineTopicDao(): OfflineTopicDao
    abstract fun draftDao(): DraftDao
    abstract fun blockListDao(): BlockListDao
    abstract fun moderationVisibilityDao(): ModerationVisibilityDao
    abstract fun favoriteTopicDao(): FavoriteTopicDao
    abstract fun historyDao(): HistoryDao
    abstract fun reportDao(): ReportDao
}
