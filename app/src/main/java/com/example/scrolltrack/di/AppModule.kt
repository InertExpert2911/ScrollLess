package com.example.scrolltrack.di

import android.content.Context
import androidx.room.Room
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton
import android.content.SharedPreferences

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppMetadataRepository(impl: AppMetadataRepositoryImpl): AppMetadataRepository

    @Binds
    @Singleton
    abstract fun bindScrollDataRepository(impl: ScrollDataRepositoryImpl): ScrollDataRepository

    companion object {
        private const val PREFS_INFERRED_SCROLL_COUNTERS = "InferredScrollCounters"

        @Provides
        @Singleton
        @InferredScrollPrefs
        fun provideInferredScrollPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_INFERRED_SCROLL_COUNTERS, Context.MODE_PRIVATE)
        }

        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "scroll_track_database"
            ).fallbackToDestructiveMigration(true)
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
        fun provideUnlockSessionDao(db: AppDatabase): UnlockSessionDao = db.unlockSessionDao()

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
