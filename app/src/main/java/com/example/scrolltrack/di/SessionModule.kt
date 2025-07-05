package com.example.scrolltrack.di

import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.util.ScrollSessionAggregator
import com.example.scrolltrack.util.Clock
import com.example.scrolltrack.util.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    
    @Provides
    @Singleton
    fun provideSessionManager(
        draftRepository: DraftRepository,
        scrollSessionAggregator: ScrollSessionAggregator,
        clock: Clock
    ): SessionManager {
        return SessionManager(
            draftRepository = draftRepository,
            scrollSessionAggregator = scrollSessionAggregator,
            clock = clock
        )
    }
}
