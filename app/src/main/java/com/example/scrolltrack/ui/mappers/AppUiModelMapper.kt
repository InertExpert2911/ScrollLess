package com.example.scrolltrack.ui.mappers

import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.limit.LimitInfo
import com.example.scrolltrack.ui.model.AppOpenUiItem
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.ConversionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUiModelMapper @Inject constructor(
    private val conversionUtil: ConversionUtil,
    private val appMetadataRepository: AppMetadataRepository,
    private val limitsRepository: LimitsRepository
) {

    suspend fun mapToAppUsageUiItem(record: DailyAppUsageRecord): AppUsageUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(record.packageName)
            val iconFile = appMetadataRepository.getIconFile(record.packageName)
            val limitGroup = limitsRepository.getLimitsForApps(listOf(record.packageName))[record.packageName]
            val limitInfo = if (limitGroup != null) {
                LimitInfo(
                    timeLimitMillis = limitGroup.time_limit_minutes * 60 * 1000L,
                    timeRemainingMillis = (limitGroup.time_limit_minutes * 60 * 1000L) - record.usageTimeMillis
                )
            } else {
                null
            }

            if (metadata != null) {
                AppUsageUiItem(
                    id = record.packageName,
                    appName = metadata.appName,
                    icon = iconFile,
                    usageTimeMillis = record.usageTimeMillis,
                    packageName = record.packageName,
                    limitInfo = limitInfo
                )
            } else {
                Timber.tag("AppUiModelMapper").w("No metadata found for ${record.packageName}, creating fallback UI item.")
                val fallbackAppName = record.packageName.substringAfterLast('.', record.packageName)
                AppUsageUiItem(record.packageName, fallbackAppName, null, record.usageTimeMillis, record.packageName, limitInfo)
            }
        }
    }

    suspend fun mapToAppUsageUiItems(
        records: List<DailyAppUsageRecord>,
        days: Int = 1
    ): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            val packageNames = records.map { it.packageName }.distinct()
            if (packageNames.isEmpty()) return@withContext emptyList()

            val limitsMap = limitsRepository.getLimitsForApps(packageNames)

            records.map { record ->
                val metadata = appMetadataRepository.getAppMetadata(record.packageName)
                val iconFile = appMetadataRepository.getIconFile(record.packageName)
                val limitGroup = limitsMap[record.packageName]
                val limitInfo = if (limitGroup != null) {
                    LimitInfo(
                        timeLimitMillis = limitGroup.time_limit_minutes * 60 * 1000L,
                        timeRemainingMillis = (limitGroup.time_limit_minutes * 60 * 1000L) - record.usageTimeMillis
                    )
                } else {
                    null
                }

                AppUsageUiItem(
                    id = record.packageName,
                    appName = metadata?.appName ?: record.packageName.substringAfterLast('.', record.packageName),
                    icon = iconFile,
                    usageTimeMillis = record.usageTimeMillis / days,
                    packageName = record.packageName,
                    limitInfo = limitInfo
                )
            }.sortedByDescending { it.usageTimeMillis }
        }
    }

    suspend fun mapToAppOpenUiItem(record: DailyAppUsageRecord): AppOpenUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(record.packageName)
            val iconFile = appMetadataRepository.getIconFile(record.packageName)
            val limitsMap = limitsRepository.getLimitsForApps(listOf(record.packageName))
            val limitGroup = limitsMap[record.packageName]
            val limitInfo = if (limitGroup != null) {
                LimitInfo(
                    timeLimitMillis = limitGroup.time_limit_minutes * 60 * 1000L,
                    timeRemainingMillis = (limitGroup.time_limit_minutes * 60 * 1000L) - record.usageTimeMillis
                )
            } else {
                null
            }
            AppOpenUiItem(
                packageName = record.packageName,
                appName = metadata?.appName ?: record.packageName.substringAfterLast('.', record.packageName),
                icon = iconFile,
                openCount = record.appOpenCount,
                limitInfo = limitInfo
            )
        }
    }

    suspend fun mapToAppScrollUiItem(appData: AppScrollData, usageRecord: DailyAppUsageRecord?): AppScrollUiItem {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(appData.packageName)
            val iconFile = appMetadataRepository.getIconFile(appData.packageName)
            val limitsMap = limitsRepository.getLimitsForApps(listOf(appData.packageName))
            val limitGroup = limitsMap[appData.packageName]
            val limitInfo = if (limitGroup != null) {
                LimitInfo(
                    timeLimitMillis = limitGroup.time_limit_minutes * 60 * 1000L,
                    timeRemainingMillis = (limitGroup.time_limit_minutes * 60 * 1000L) - (usageRecord?.usageTimeMillis ?: 0L)
                )
            } else {
                null
            }

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
                    dataType = appData.dataType,
                    limitInfo = limitInfo
                )
            } else {
                Timber.tag("AppUiModelMapper")
                    .w("No metadata found for scroll item ${appData.packageName}, creating fallback UI item.")
                val fallbackAppName = appData.packageName.substringAfterLast('.', appData.packageName)
                AppScrollUiItem(uniqueId, fallbackAppName, null, appData.totalScroll, 0, 0, appData.packageName, appData.dataType, limitInfo)
            }
        }
    }
}
