package com.example.scrolltrack.di

import com.example.scrolltrack.ui.limit.LimitViewModelDelegate
import com.example.scrolltrack.ui.limit.LimitViewModelDelegateImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class DelegateModule {

    @Binds
    abstract fun bindLimitViewModelDelegate(
        impl: LimitViewModelDelegateImpl
    ): LimitViewModelDelegate
}