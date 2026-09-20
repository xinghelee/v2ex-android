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
    version = 4,
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

        /** 1.2.3 -> 1.2.4：新增用户标记表，纯新增，无数据迁移。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `member_tags` (" +
                        "`usernameKey` TEXT NOT NULL, `username` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, " +
                        "`avatarUrl` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`usernameKey`))",
                )
            }
        }

        /** 1.2.4 -> 1.2.5：offline_topics 增加列表摘要列并回填（issue #5，见 [OfflineTopicSummary]）。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `offline_topics` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `offline_topics` ADD COLUMN `nodeTitle` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `offline_topics` ADD COLUMN `authorName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `offline_topics` ADD COLUMN `authorId` INTEGER")
                db.execSQL("ALTER TABLE `offline_topics` ADD COLUMN `replyCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `offline_topics` ADD COLUMN `byteSize` INTEGER NOT NULL DEFAULT 0")

                // 回填先只取 id，再逐篇读正文：整批 SELECT * 正是这次要消灭的读法，迁移自己不能再踩一次。
                val ids = ArrayList<Long>()
                db.query("SELECT topicId FROM offline_topics").use { cursor ->
                    while (cursor.moveToNext()) ids += cursor.getLong(0)
                }
                for (id in ids) {
                    val row = db.query(
                        "SELECT topicJson, LENGTH(topicJson) + LENGTH(repliesJson) FROM offline_topics WHERE topicId = ?",
                        arrayOf<Any>(id),
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) to cursor.getInt(1) else null
                    } ?: continue
                    val summary = OfflineTopicSummary.fromTopicJson(row.first)
                    db.execSQL(
                        "UPDATE offline_topics SET title = ?, nodeTitle = ?, authorName = ?, authorId = ?, " +
                            "replyCount = ?, byteSize = ? WHERE topicId = ?",
                        arrayOf<Any?>(
                            summary.title, summary.nodeTitle, summary.authorName, summary.authorId,
                            summary.replyCount, row.second, id,
                        ),
                    )
                }
            }
        }
    }
}
