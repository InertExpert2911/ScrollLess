package com.example.scrolltrack.di

import com.example.scrolltrack.util.Clock
import com.example.scrolltrack.util.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}
