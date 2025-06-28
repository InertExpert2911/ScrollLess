package com.example.scrolltrack.data

import android.graphics.drawable.Drawable
import com.example.scrolltrack.db.AppMetadata
import java.io.File

interface AppMetadataRepository {
    suspend fun getAppMetadata(packageName: String): AppMetadata?
    fun getIconFile(packageName: String): File?
    suspend fun syncAllInstalledApps()
    suspend fun handleAppUninstalled(packageName: String)
    suspend fun handleAppInstalledOrUpdated(packageName: String)
} 