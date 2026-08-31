package com.vibe.v2ex.di

import android.content.Context
import androidx.room.Room
import com.vibe.v2ex.data.local.AppDatabase
import com.vibe.v2ex.data.local.BlockListDao
import com.vibe.v2ex.data.local.DraftDao
import com.vibe.v2ex.data.local.FavoriteTopicDao
import com.vibe.v2ex.data.local.FeedCacheDao
import com.vibe.v2ex.data.local.HistoryDao
import com.vibe.v2ex.data.local.ModerationVisibilityDao
import com.vibe.v2ex.data.local.OfflineTopicDao
import com.vibe.v2ex.data.local.ReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "v2ex.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideOfflineTopicDao(db: AppDatabase): OfflineTopicDao = db.offlineTopicDao()

    @Provides
    fun provideFeedCacheDao(db: AppDatabase): FeedCacheDao = db.feedCacheDao()

    @Provides
    fun provideDraftDao(db: AppDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideBlockListDao(db: AppDatabase): BlockListDao = db.blockListDao()

    @Provides
    fun provideModerationVisibilityDao(db: AppDatabase): ModerationVisibilityDao = db.moderationVisibilityDao()

    @Provides
    fun provideFavoriteTopicDao(db: AppDatabase): FavoriteTopicDao = db.favoriteTopicDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()
}
