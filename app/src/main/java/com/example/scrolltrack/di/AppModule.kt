package com.example.scrolltrack.di

import android.content.Context
import androidx.room.Room
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.DateUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "scroll_track_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideScrollSessionDao(appDatabase: AppDatabase): ScrollSessionDao {
        return appDatabase.scrollSessionDao()
    }

    @Provides
    fun provideDailyAppUsageDao(appDatabase: AppDatabase): DailyAppUsageDao {
        return appDatabase.dailyAppUsageDao()
    }

    @Provides
    fun provideRawAppEventDao(appDatabase: AppDatabase): RawAppEventDao {
        return appDatabase.rawAppEventDao()
    }

    @Provides
    fun provideNotificationDao(appDatabase: AppDatabase): NotificationDao {
        return appDatabase.notificationDao()
    }

    @Provides
    fun provideDailyDeviceSummaryDao(appDatabase: AppDatabase): DailyDeviceSummaryDao {
        return appDatabase.dailyDeviceSummaryDao()
    }

    @Provides
    fun provideAppMetadataDao(appDatabase: AppDatabase): AppMetadataDao {
        return appDatabase.appMetadataDao()
    }

    @Provides
    fun provideUnlockSessionDao(appDatabase: AppDatabase): UnlockSessionDao {
        return appDatabase.unlockSessionDao()
    }

    @Provides
    fun provideDailyInsightDao(appDatabase: AppDatabase): DailyInsightDao {
        return appDatabase.dailyInsightDao()
    }

    @Provides
    fun provideLimitsDao(appDatabase: AppDatabase): LimitsDao {
        return appDatabase.limitsDao()
    }

    @Provides
    @Singleton
    fun provideDateUtil(): DateUtil {
        return DateUtil
    }
}
