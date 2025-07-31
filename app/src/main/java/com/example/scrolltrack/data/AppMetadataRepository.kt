package com.example.scrolltrack.data

import android.graphics.drawable.Drawable
import com.example.scrolltrack.db.AppMetadata
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AppMetadataRepository {
    suspend fun getAppMetadata(packageName: String): AppMetadata?
    fun getIconFile(packageName: String): File?
    suspend fun syncAllInstalledApps()
    suspend fun handleAppUninstalled(packageName: String): AppMetadata?
    suspend fun handleAppInstalledOrUpdated(packageName: String)
    suspend fun getNonVisiblePackageNames(): List<String>
    suspend fun updateUserHidesOverride(packageName: String, userHidesOverride: Boolean?)
    fun getAllMetadata(): Flow<List<AppMetadata>>
    fun getVisibleApps(): Flow<List<AppMetadata>>
}
