package com.vibe.v2ex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OfflineTopicEntity::class,
        FeedCacheEntity::class,
        DraftEntity::class,
        BlockedUserEntity::class,
        BlockedKeywordEntity::class,
        HiddenTopicEntity::class,
        HiddenReplyEntity::class,
        FavoriteTopicEntity::class,
        HistoryEntity::class,
        ReportEntity::class,
        MemberTagEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineTopicDao(): OfflineTopicDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun draftDao(): DraftDao
    abstract fun blockListDao(): BlockListDao
    abstract fun moderationVisibilityDao(): ModerationVisibilityDao
    abstract fun favoriteTopicDao(): FavoriteTopicDao
    abstract fun historyDao(): HistoryDao
    abstract fun reportDao(): ReportDao
    abstract fun memberTagDao(): MemberTagDao

    companion object {
        /** 1.0.2 -> 1.1.0：新增列表离线快照表，纯新增，无数据迁移。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feed_cache` (" +
                        "`feedKey` TEXT NOT NULL, `topicsJson` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`feedKey`))",
                )
            }
        }

        /** 1.2.3 -> 1.3.0：新增用户标记表，纯新增，无数据迁移。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `member_tags` (" +
                        "`usernameKey` TEXT NOT NULL, `username` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, " +
                        "`avatarUrl` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`usernameKey`))",
                )
            }
        }
    }
}
