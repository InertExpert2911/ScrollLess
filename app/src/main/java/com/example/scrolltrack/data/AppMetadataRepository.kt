package com.example.scrolltrack.data

import android.graphics.drawable.Drawable
import com.example.scrolltrack.db.AppMetadata

interface AppMetadataRepository {
    suspend fun getAppMetadata(packageName: String): AppMetadata?
    fun getIconDrawable(packageName: String): Drawable?
    suspend fun syncAllInstalledApps()
    suspend fun handleAppUninstalled(packageName: String)
    suspend fun handleAppInstalledOrUpdated(packageName: String)
} 