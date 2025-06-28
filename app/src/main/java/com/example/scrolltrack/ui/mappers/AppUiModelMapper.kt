package com.example.scrolltrack.ui.mappers

import android.graphics.drawable.Drawable
import android.util.Log
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUiModelMapper @Inject constructor(
    private val appMetadataRepository: AppMetadataRepository
) {

    suspend fun mapToAppUsageUiItem(record: DailyAppUsageRecord): AppUsageUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(record.packageName)
            val icon = getDrawableFromIconFile(appMetadataRepository.getIconFile(record.packageName))

            if (metadata != null) {
                AppUsageUiItem(
                    id = record.packageName,
                    appName = metadata.appName,
                    icon = icon,
                    usageTimeMillis = record.usageTimeMillis,
                    packageName = record.packageName
                )
            } else {
                Log.w("AppUiModelMapper", "No metadata found for ${record.packageName}, creating fallback UI item.")
                val fallbackAppName = record.packageName.substringAfterLast('.', record.packageName)
                AppUsageUiItem(record.packageName, fallbackAppName, null, record.usageTimeMillis, record.packageName)
            }
        }
    }

    suspend fun mapToAppScrollUiItem(appData: AppScrollData): AppScrollUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(appData.packageName)
            val icon = getDrawableFromIconFile(appMetadataRepository.getIconFile(appData.packageName))

            if (metadata != null) {
                AppScrollUiItem(
                    id = appData.packageName,
                    appName = metadata.appName,
                    icon = icon,
                    totalScroll = appData.totalScroll,
                    packageName = appData.packageName
                )
            } else {
                Log.w("AppUiModelMapper", "No metadata found for scroll item ${appData.packageName}, creating fallback UI item.")
                val fallbackAppName = appData.packageName.substringAfterLast('.', appData.packageName)
                AppScrollUiItem(appData.packageName, fallbackAppName, null, appData.totalScroll, appData.packageName)
            }
        }
    }

    private fun getDrawableFromIconFile(iconFile: File?): Drawable? {
        if (iconFile != null && iconFile.exists()) {
            return Drawable.createFromPath(iconFile.absolutePath)
        }
        return null
    }
} 