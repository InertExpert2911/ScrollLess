package com.example.scrolltrack.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.SessionManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    @Binds
    @Singleton
    abstract fun bindAppMetadataRepository(impl: AppMetadataRepositoryImpl): AppMetadataRepository

    companion object {
        @Provides
        @Singleton
        fun provideScrollDataRepository(
            appDatabase: AppDatabase,
            appMetadataRepository: AppMetadataRepository,
            scrollSessionDao: ScrollSessionDao,
            dailyAppUsageDao: DailyAppUsageDao,
            rawAppEventDao: RawAppEventDao,
            notificationDao: NotificationDao,
            dailyDeviceSummaryDao: DailyDeviceSummaryDao,
            @ApplicationContext context: Context
        ): ScrollDataRepository {
            return ScrollDataRepositoryImpl(
                appDatabase,
                appMetadataRepository,
                scrollSessionDao,
                dailyAppUsageDao,
                rawAppEventDao,
                notificationDao,
                dailyDeviceSummaryDao,
                context
            )
        }

        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "scroll_track_database"
            ).fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        @Singleton
        fun provideScrollSessionDao(db: AppDatabase): ScrollSessionDao = db.scrollSessionDao()

        @Provides
        @Singleton
        fun provideDailyAppUsageDao(db: AppDatabase): DailyAppUsageDao = db.dailyAppUsageDao()

        @Provides
        @Singleton
        fun provideRawAppEventDao(db: AppDatabase): RawAppEventDao = db.rawAppEventDao()

        @Provides
        @Singleton
        fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()

        @Provides
        @Singleton
        fun provideDailyDeviceSummaryDao(db: AppDatabase): DailyDeviceSummaryDao = db.dailyDeviceSummaryDao()

        @Provides
        @Singleton
        fun provideAppMetadataDao(db: AppDatabase): AppMetadataDao = db.appMetadataDao()

        @Provides
        @Singleton
        fun provideSessionManager(
            draftRepository: DraftRepository,
            scrollSessionDao: ScrollSessionDao
        ): SessionManager {
            return SessionManager(
                draftRepository = draftRepository,
                scrollSessionDao = scrollSessionDao
            )
        }
    }
}