package com.example.scrolltrack.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import com.example.scrolltrack.db.UnlockSessionRecord
import javax.inject.Inject
import com.example.scrolltrack.db.DailyInsight

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val scrollDataRepository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository
) : ViewModel() {

    // --- THIS IS THE NEW LIVE DATA SOURCE ---
    private val unlockSessionsToday: Flow<List<UnlockSessionRecord>> =
        scrollDataRepository.getUnlockSessionsForDateRange(
            DateUtil.getCurrentLocalDateString(),
            DateUtil.getCurrentLocalDateString()
        )

    private val todaysInsightsFlow: Flow<List<DailyInsight>> =
        scrollDataRepository.getInsightsForDate(DateUtil.getCurrentLocalDateString())

    private val yesterdaysInsightsFlow: Flow<List<DailyInsight>> =
        scrollDataRepository.getInsightsForDate(DateUtil.getPastDateString(1))

    val dailyInsights: StateFlow<DailyInsightsUiModel> = combine(
        todaysInsightsFlow,
        unlockSessionsToday
    ) { insights, sessions ->
            val glanceCount = sessions.count { it.sessionType == "Glance" }
            val meaningfulUnlocks = sessions.count { it.sessionType == "Intentional" || it.sessionEndReason == "INTERRUPTED" || it.sessionType == null }
            val firstUnlock = insights.find { it.insightKey == "first_unlock_time" }?.longValue?.let {
                DateUtil.formatUtcTimestampToTimeString(it)
            } ?: "N/A"
            val lastUnlock = insights.find { it.insightKey == "last_unlock_time" }?.longValue?.let {
                DateUtil.formatUtcTimestampToTimeString(it)
            } ?: "N/A"

            val firstAppUsedInsight = insights.find { it.insightKey == "first_app_used" }
            val firstUsedApp = firstAppUsedInsight?.let {
                val appMetadata = appMetadataRepository.getAppMetadata(it.stringValue!!)
                val time = it.longValue?.let { ts -> DateUtil.formatUtcTimestampToTimeString(ts) } ?: ""
                "${appMetadata?.appName ?: it.stringValue} at $time"
            } ?: "N/A"

            val lastAppUsedInsight = insights.find { it.insightKey == "last_app_used" }
            val lastUsedApp = lastAppUsedInsight?.let {
                val appMetadata = appMetadataRepository.getAppMetadata(it.stringValue!!)
                val time = it.longValue?.let { ts -> DateUtil.formatUtcTimestampToTimeString(ts) } ?: ""
                "${appMetadata?.appName ?: it.stringValue} at $time"
            } ?: "N/A"

            DailyInsightsUiModel(glanceCount, firstUnlock, lastUnlock, meaningfulUnlocks, firstUsedApp, lastUsedApp)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DailyInsightsUiModel(0, "N/A", "N/A", 0, "N/A", "N/A")
        )

    val insightCards: StateFlow<List<InsightCardUiModel>> = combine(
        todaysInsightsFlow,
        yesterdaysInsightsFlow
    ) { todaysInsights, yesterdaysInsights ->
        buildUiModelsFromInsights(todaysInsights, yesterdaysInsights)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private suspend fun buildUiModelsFromInsights(
        todaysInsights: List<DailyInsight>,
        yesterdaysInsights: List<DailyInsight>
    ): List<InsightCardUiModel> {
        val uiModels = mutableListOf<InsightCardUiModel>()

        // --- Cards using TODAY'S data ---
        todaysInsights.find { it.insightKey == "first_app_used" }?.let { insight ->
            val appName = appMetadataRepository.getAppMetadata(insight.stringValue!!)?.appName ?: insight.stringValue
            val icon = appMetadataRepository.getIconFile(insight.stringValue!!)
            val time = insight.longValue?.let { DateUtil.formatUtcTimestampToTimeString(it) } ?: ""
            uiModels.add(InsightCardUiModel.FirstApp(appName, icon, time))
        }
        
        todaysInsights.find { it.insightKey == "night_owl_last_app" }?.let { insight ->
            val appName = appMetadataRepository.getAppMetadata(insight.stringValue!!)?.appName ?: insight.stringValue
            val icon = appMetadataRepository.getIconFile(insight.stringValue!!)
            val time = insight.longValue?.let { DateUtil.formatUtcTimestampToTimeString(it) } ?: ""
            uiModels.add(InsightCardUiModel.NightOwl(appName, icon, time))
        }

        todaysInsights.find { it.insightKey == "top_compulsive_app" }?.let { insight ->
             if ((insight.longValue ?: 0) >= 3) {
                val appName = appMetadataRepository.getAppMetadata(insight.stringValue!!)?.appName ?: insight.stringValue
                val icon = appMetadataRepository.getIconFile(insight.stringValue!!)
                uiModels.add(InsightCardUiModel.CompulsiveCheck(appName, icon, insight.longValue!!.toInt()))
            }
        }

        todaysInsights.find { it.insightKey == "top_notification_unlock_app" }?.let { insight ->
            val totalUnlocks = (todaysInsights.find { it.insightKey == "glance_count" }?.longValue ?: 0) +
                               (todaysInsights.find { it.insightKey == "meaningful_unlock_count" }?.longValue ?: 0)
            if (totalUnlocks > 0) {
                val percentage = ((insight.longValue ?: 0).toDouble() / totalUnlocks.toDouble() * 100).toInt()
                if (percentage >= 10) {
                    val appName = appMetadataRepository.getAppMetadata(insight.stringValue!!)?.appName ?: insight.stringValue
                    val icon = appMetadataRepository.getIconFile(insight.stringValue!!)
                    uiModels.add(InsightCardUiModel.NotificationLeader(appName, icon, percentage))
                }
            }
        }

        todaysInsights.find { it.insightKey == "busiest_unlock_hour" }?.let { insight ->
            val busiestHour = insight.longValue!!.toInt()
            val (timeOfDay, period) = when (busiestHour) {
                in 5..8 -> "Morning" to "5 AM - 9 AM"
                in 9..11 -> "Late Morning" to "9 AM - 12 PM"
                in 12..13 -> "Lunchtime" to "12 PM - 2 PM"
                in 14..16 -> "Afternoon" to "2 PM - 5 PM"
                in 17..20 -> "Evening" to "5 PM - 9 PM"
                in 21..23 -> "Late Night" to "9 PM - 12 AM"
                else -> "Night" to "12 AM - 5 AM"
            }
            uiModels.add(InsightCardUiModel.TimePattern(timeOfDay, "unlocks", period))
        }

        // --- Cards using YESTERDAY'S data ---
        yesterdaysInsights.find { it.insightKey == "last_app_used" }?.let { insight ->
            val appName = appMetadataRepository.getAppMetadata(insight.stringValue!!)?.appName ?: insight.stringValue
            val icon = appMetadataRepository.getIconFile(insight.stringValue!!)
            val time = insight.longValue?.let { DateUtil.formatUtcTimestampToTimeString(it) } ?: ""
            uiModels.add(InsightCardUiModel.LastApp(appName, icon, time))
        }

        return uiModels.sortedBy { it.id } // Sort to maintain a consistent order
    }
}
