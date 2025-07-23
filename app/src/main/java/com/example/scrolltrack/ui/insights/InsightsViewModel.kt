package com.example.scrolltrack.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.db.DailyDeviceSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val scrollDataRepository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository
) : ViewModel() {

    private val _insights = MutableStateFlow<List<InsightCardUiModel>>(emptyList())
    val insights: StateFlow<List<InsightCardUiModel>> = _insights.asStateFlow()

    private val todaySummary: StateFlow<DailyDeviceSummary?> =
        scrollDataRepository.getDeviceSummaryForDate(DateUtil.getCurrentLocalDateString())
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    val intentionalUnlocks: StateFlow<Int> = todaySummary.map { it?.intentionalUnlockCount ?: 0 }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0)

    val glanceUnlocks: StateFlow<Int> = todaySummary.map { it?.glanceUnlockCount ?: 0 }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0)

    val firstUnlockTime: StateFlow<String> = todaySummary.map {
        it?.firstUnlockTimestampUtc?.let { ts ->
            DateUtil.formatUtcTimestampToTimeString(ts)
        } ?: "N/A"
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "N/A")

    val lastUnlockTime: StateFlow<String> = todaySummary.map {
        it?.lastUnlockTimestampUtc?.let { ts ->
            DateUtil.formatUtcTimestampToTimeString(ts)
        } ?: "N/A"
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "N/A")


    init {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            // Set a loading state initially for all insights
            _insights.value = listOf(
                InsightCardUiModel.Loading("first_app"),
                InsightCardUiModel.Loading("last_app"),
                InsightCardUiModel.Loading("compulsive_check"),
                InsightCardUiModel.Loading("notification_leader"),
                InsightCardUiModel.Loading("time_pattern")
            )

            // Fetch all insights in parallel
            val firstAppInsightDeferred = async { loadFirstAppInsight() }
            val lastAppInsightDeferred = async { loadLastAppInsight() }
            val compulsiveCheckInsightDeferred = async { loadCompulsiveCheckInsight() }
            val notificationLeaderInsightDeferred = async { loadNotificationLeaderInsight() }
            val timePatternInsightDeferred = async { loadTimePatternInsight() }


            // Await all results and filter out any nulls (if an insight can't be generated)
            val loadedInsights = listOfNotNull(
                firstAppInsightDeferred.await(),
                lastAppInsightDeferred.await(),
                compulsiveCheckInsightDeferred.await(),
                notificationLeaderInsightDeferred.await(),
                timePatternInsightDeferred.await()
            )

            _insights.value = loadedInsights.ifEmpty {
                // Handle case where no insights could be generated
                // You could show a "Not enough data yet" message here
                emptyList()
            }
        }
    }

    private suspend fun loadFirstAppInsight(): InsightCardUiModel.FirstApp? {
        // Use the start of the user's local day (12:00 AM)
        val startOfTodayTimestamp = DateUtil.getStartOfDayUtcMillis(DateUtil.getCurrentLocalDateString())
        val firstAppEvent = scrollDataRepository.getFirstAppUsedAfter(startOfTodayTimestamp) ?: return null

        val appMetadata = appMetadataRepository.getAppMetadata(firstAppEvent.packageName)
        val iconFile = appMetadataRepository.getIconFile(firstAppEvent.packageName)

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val usageTime = DateUtil.formatUtcTimestampToLocalDateTime(firstAppEvent.eventTimestamp)
            .format(timeFormatter)

        return InsightCardUiModel.FirstApp(
            appName = appMetadata?.appName ?: firstAppEvent.packageName,
            icon = iconFile,
            time = usageTime
        )
    }

    private suspend fun loadLastAppInsight(): InsightCardUiModel.LastApp? {
        val yesterday = DateUtil.getYesterdayDateString()
        val lastAppEvent = scrollDataRepository.getLastAppUsedOn(yesterday) ?: return null

        val appMetadata = appMetadataRepository.getAppMetadata(lastAppEvent.packageName)
        val iconFile = appMetadataRepository.getIconFile(lastAppEvent.packageName)

        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val usageTime = DateUtil.formatUtcTimestampToLocalDateTime(lastAppEvent.eventTimestamp)
            .format(timeFormatter)

        return InsightCardUiModel.LastApp(
            appName = appMetadata?.appName ?: lastAppEvent.packageName,
            icon = iconFile,
            time = usageTime
        )
    }

    private suspend fun loadCompulsiveCheckInsight(): InsightCardUiModel.CompulsiveCheck? {
        val today = DateUtil.getCurrentLocalDateString()
        val topCompulsiveApp = scrollDataRepository.getCompulsiveCheckCounts(today, today)
            .first()
            .maxByOrNull { it.count } ?: return null

        // Only show if the count is somewhat significant
        if (topCompulsiveApp.count < 3) return null

        val appMetadata = appMetadataRepository.getAppMetadata(topCompulsiveApp.packageName)
        val iconFile = appMetadataRepository.getIconFile(topCompulsiveApp.packageName)

        return InsightCardUiModel.CompulsiveCheck(
            appName = appMetadata?.appName ?: topCompulsiveApp.packageName,
            icon = iconFile,
            count = topCompulsiveApp.count
        )
    }

    private suspend fun loadNotificationLeaderInsight(): InsightCardUiModel.NotificationLeader? {
        val today = DateUtil.getCurrentLocalDateString()
        val notificationCounts = scrollDataRepository.getNotificationDrivenUnlockCounts(today, today).first()
        val topApp = notificationCounts.maxByOrNull { it.count } ?: return null

        // Fetch today's summary directly and wait for it if needed. This is more reliable.
        val todaysSummary = scrollDataRepository.getDeviceSummaryForDate(today).first() ?: return null
        val totalUnlocks = todaysSummary.totalUnlockCount

        if (totalUnlocks == 0) return null

        val percentage = (topApp.count.toDouble() / totalUnlocks.toDouble() * 100).toInt()
        if (percentage < 10) return null // Only show if significant

        val appMetadata = appMetadataRepository.getAppMetadata(topApp.packageName)
        val iconFile = appMetadataRepository.getIconFile(topApp.packageName)

        return InsightCardUiModel.NotificationLeader(
            appName = appMetadata?.appName ?: topApp.packageName,
            icon = iconFile,
            percentage = percentage
        )
    }

    private suspend fun loadTimePatternInsight(): InsightCardUiModel.TimePattern? {
        val today = DateUtil.getCurrentLocalDateString()
        val sessions = scrollDataRepository.getUnlockSessionsForDateRange(today, today).first()
        if (sessions.size < 10) return null // Need enough data

        val hourlyCounts = sessions
            .map { DateUtil.formatUtcTimestampToLocalDateTime(it.unlockTimestamp).hour }
            .groupingBy { it }
            .eachCount()

        val busiestHour = hourlyCounts.maxByOrNull { it.value }?.key ?: return null

        val (timeOfDay, period) = when (busiestHour) {
            in 5..8 -> "Morning" to "5 AM - 9 AM"
            in 9..11 -> "Late Morning" to "9 AM - 12 PM"
            in 12..13 -> "Lunchtime" to "12 PM - 2 PM"
            in 14..16 -> "Afternoon" to "2 PM - 5 PM"
            in 17..20 -> "Evening" to "5 PM - 9 PM"
            in 21..23 -> "Late Night" to "9 PM - 12 AM"
            else -> "Night" to "12 AM - 5 AM"
        }

        return InsightCardUiModel.TimePattern(
            timeOfDay = timeOfDay,
            metric = "unlocks",
            period = period
        )
    }
}
