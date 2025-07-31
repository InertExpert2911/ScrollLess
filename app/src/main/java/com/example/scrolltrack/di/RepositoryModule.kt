package com.example.scrolltrack.di

import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppMetadataRepositoryImpl
import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.data.LimitsRepositoryImpl
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.data.SettingsRepositoryImpl
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.ScrollDataRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLimitsRepository(
        limitsRepositoryImpl: LimitsRepositoryImpl
    ): LimitsRepository

    @Binds
    @Singleton
    abstract fun bindAppMetadataRepository(
        appMetadataRepositoryImpl: AppMetadataRepositoryImpl
    ): AppMetadataRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindScrollDataRepository(
        scrollDataRepositoryImpl: ScrollDataRepositoryImpl
    ): ScrollDataRepository
}