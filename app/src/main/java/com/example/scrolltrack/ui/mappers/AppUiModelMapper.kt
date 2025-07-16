package com.example.scrolltrack.ui.mappers

import android.graphics.drawable.Drawable
import android.util.Log
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.model.AppOpenUiItem
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
            val iconFile = appMetadataRepository.getIconFile(record.packageName)

            if (metadata != null) {
                AppUsageUiItem(
                    id = record.packageName,
                    appName = metadata.appName,
                    icon = iconFile,
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

    suspend fun mapToAppUsageUiItems(records: List<DailyAppUsageRecord>, days: Int = 1): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            records.groupBy { it.packageName }
                .map { (packageName, userRecords) ->
                    val totalUsage = userRecords.sumOf { it.usageTimeMillis }
                    val metadata = appMetadataRepository.getAppMetadata(packageName)
                    val iconFile = appMetadataRepository.getIconFile(packageName)
                    AppUsageUiItem(
                        id = packageName,
                        appName = metadata?.appName ?: packageName.substringAfterLast('.', packageName),
                        icon = iconFile,
                        usageTimeMillis = totalUsage / days,
                        packageName = packageName
                    )
                }
                .sortedByDescending { it.usageTimeMillis }
        }
    }

    suspend fun mapToAppOpenUiItem(record: DailyAppUsageRecord): AppOpenUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(record.packageName)
            val iconFile = appMetadataRepository.getIconFile(record.packageName)
            AppOpenUiItem(
                packageName = record.packageName,
                appName = metadata?.appName ?: record.packageName.substringAfterLast('.', record.packageName),
                icon = iconFile,
                openCount = record.appOpenCount
            )
        }
    }

    suspend fun mapToAppScrollUiItem(appData: AppScrollData): AppScrollUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(appData.packageName)
            val iconFile = appMetadataRepository.getIconFile(appData.packageName)

            // Create a unique ID by combining package name and data type
            val uniqueId = "${appData.packageName}-${appData.dataType}"

            if (metadata != null) {
                AppScrollUiItem(
                    id = uniqueId,
                    appName = metadata.appName,
                    icon = iconFile,
                    totalScroll = appData.totalScroll,
                    totalScrollX = appData.totalScrollX,
                    totalScrollY = appData.totalScrollY,
                    packageName = appData.packageName,
                    dataType = appData.dataType
                )
            } else {
                Log.w("AppUiModelMapper", "No metadata found for scroll item ${appData.packageName}, creating fallback UI item.")
                val fallbackAppName = appData.packageName.substringAfterLast('.', appData.packageName)
                AppScrollUiItem(uniqueId, fallbackAppName, null, appData.totalScroll, 0, 0, appData.packageName, appData.dataType)
            }
        }
    }
}
