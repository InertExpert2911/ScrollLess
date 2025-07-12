package com.example.scrolltrack.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.GreetingUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideAppMetadataRepository(): AppMetadataRepository = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideScrollDataRepository(): ScrollDataRepository = mockk(relaxed = true)

    @Provides
    @Singleton
    @InferredScrollPrefs
    fun provideInferredScrollPreferences(): SharedPreferences = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideAppDatabase(): AppDatabase = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideScrollSessionDao(db: AppDatabase): ScrollSessionDao = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideDailyAppUsageDao(db: AppDatabase): DailyAppUsageDao = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideRawAppEventDao(db: AppDatabase): RawAppEventDao = mockk(relaxed = true)
    
    @Provides
    @Singleton
    fun provideNotificationDao(db: AppDatabase): NotificationDao = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideDailyDeviceSummaryDao(db: AppDatabase): DailyDeviceSummaryDao = mockk(relaxed = true)
    
    @Provides
    @Singleton
    fun provideAppMetadataDao(db: AppDatabase): AppMetadataDao = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideUnlockSessionDao(db: AppDatabase): UnlockSessionDao = mockk(relaxed = true)
    
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = mockk(relaxed = true)
} 