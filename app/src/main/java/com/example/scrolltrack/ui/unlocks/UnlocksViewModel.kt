package com.example.scrolltrack.ui.unlocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class UnlocksUiState(
    val weeklyAverage: Double = 0.0,
    val dailyAverage: Double = 0.0,
    val totalUnlocks: Int = 0,
    val daysTracked: Int = 0,
    val barChartData: List<Pair<String, Int>> = emptyList(),
    val firstUnlockTime: String? = null,
    val lastUnlockTime: String? = null
)

@HiltViewModel
class UnlocksViewModel @Inject constructor(
    private val repository: ScrollDataRepository
) : ViewModel() {

    val uiState: StateFlow<UnlocksUiState> = repository.getAllDeviceSummaries()
        .map { summaries ->
            if (summaries.isEmpty()) return@map UnlocksUiState()

            val totalUnlocks = summaries.sumOf { it.totalUnlockCount }
            val daysTracked = summaries.size

            val dailyAverage = if (daysTracked > 0) totalUnlocks.toDouble() / daysTracked else 0.0

            val weeklyAverage = summaries
                .groupBy { LocalDate.parse(it.dateString).year * 100 + LocalDate.parse(it.dateString).get(java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR) }
                .map { it.value.sumOf { s -> s.totalUnlockCount } }
                .average()

            val recentSummaries = summaries.takeLast(7)
            val barChartData = recentSummaries.map {
                val date = LocalDate.parse(it.dateString)
                val dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                dayLabel to it.totalUnlockCount
            }

            val todaySummary = summaries.find { it.dateString == DateUtil.getCurrentLocalDateString() }
            val firstUnlockTime = todaySummary?.firstUnlockTimestampUtc?.let {
                DateUtil.formatUtcTimestampToTimeString(it)
            }
            val lastUnlockTime = todaySummary?.lastUnlockTimestampUtc?.let {
                DateUtil.formatUtcTimestampToTimeString(it)
            }

            UnlocksUiState(
                weeklyAverage = weeklyAverage,
                dailyAverage = dailyAverage,
                totalUnlocks = totalUnlocks,
                daysTracked = daysTracked,
                barChartData = barChartData,
                firstUnlockTime = firstUnlockTime,
                lastUnlockTime = lastUnlockTime
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UnlocksUiState()
        )
} 