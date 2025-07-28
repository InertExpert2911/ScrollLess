package com.example.scrolltrack.di

import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.data.LimitsRepositoryImpl
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
}