package com.example.scrolltrack.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.ScrollDataRepositoryImpl
import com.example.scrolltrack.db.AppDatabase

class MainViewModelFactory(
    private val application: Application,
    private val repositoryOverride: ScrollDataRepository? = null // Optional repository override
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val repository = repositoryOverride ?: run {
                val database = AppDatabase.getDatabase(application)
                ScrollDataRepositoryImpl(database.scrollSessionDao(), database.dailyAppUsageDao(), application)
            }
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
