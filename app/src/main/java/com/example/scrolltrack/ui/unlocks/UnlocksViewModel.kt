package com.example.scrolltrack.ui.unlocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.di.IoDispatcher
import com.example.scrolltrack.ui.model.AppOpenUiItem
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt

enum class UnlockPeriod {
    Daily, Weekly, Monthly
}

data class UnlocksUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val period: UnlockPeriod = UnlockPeriod.Daily,
    val heatmapData: Map<LocalDate, Int> = emptyMap(),
    val unlockStat: Int = 0,
    val appOpens: List<AppOpenUiItem> = emptyList(),
    val periodDisplay: String = "",
    val currentMonth: YearMonth = YearMonth.now(),
    val monthsWithData: List<YearMonth> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UnlocksViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val mapper: AppUiModelMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _period = MutableStateFlow(UnlockPeriod.Daily)

    val uiState: StateFlow<UnlocksUiState> = combine(
        _selectedDate,
        _period,
        repository.getAllDeviceSummaries()
    ) { selectedDate: LocalDate, period: UnlockPeriod, allSummaries: List<DailyDeviceSummary> ->
        val heatmapData = allSummaries.associate { LocalDate.parse(it.dateString) to it.totalUnlockCount }
        val monthsWithData = heatmapData.keys.map { YearMonth.from(it) }.distinct().sorted()
        val currentMonth = monthsWithData.lastOrNull() ?: YearMonth.now()

        val (unlockStat, dateRange, periodDisplay) = when (period) {
            UnlockPeriod.Daily -> {
                val stat = allSummaries.find { it.dateString == selectedDate.toString() }?.totalUnlockCount ?: 0
                Triple(stat, listOf(selectedDate.toString()), selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
            }
            UnlockPeriod.Weekly -> {
                val startOfWeek = DateUtil.getStartOfWeek(selectedDate)
                val endOfWeek = startOfWeek.plusDays(6)
                val dateStrings = (0..6).map { startOfWeek.plusDays(it.toLong()).toString() }
                val weeklySummaries = allSummaries.filter { it.dateString in dateStrings }
                val avg = if (weeklySummaries.isNotEmpty()) {
                    weeklySummaries.sumOf { it.totalUnlockCount }.toDouble() / 7
                } else 0.0
                val display = "Week ${DateUtil.getWeekOfYear(selectedDate)} (${startOfWeek.format(DateTimeFormatter.ofPattern("MMM d"))} - ${endOfWeek.format(DateTimeFormatter.ofPattern("d, yyyy"))})"
                Triple(avg.roundToInt(), dateStrings, display)
            }
            UnlockPeriod.Monthly -> {
                val startOfMonth = DateUtil.getStartOfMonth(selectedDate)
                val dateStrings = (0 until startOfMonth.lengthOfMonth()).map { startOfMonth.plusDays(it.toLong()).toString() }
                val monthlySummaries = allSummaries.filter { it.dateString in dateStrings }
                val avg = if (monthlySummaries.isNotEmpty()) {
                    monthlySummaries.sumOf { it.totalUnlockCount }.toDouble() / startOfMonth.lengthOfMonth()
                } else 0.0
                Triple(avg.roundToInt(), dateStrings, selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
            }
        }

        Pair(
            UnlocksUiState(
                selectedDate = selectedDate,
                period = period,
                heatmapData = heatmapData,
                unlockStat = unlockStat,
                periodDisplay = periodDisplay,
                currentMonth = currentMonth,
                monthsWithData = monthsWithData
            ),
            dateRange
        )
    }.flatMapLatest { pair: Pair<UnlocksUiState, List<String>> ->
        val (state, dateRange) = pair
        if (dateRange.isEmpty()) {
            flowOf(state.copy(appOpens = emptyList()))
        } else {
            repository.getUsageRecordsForDateRange(dateRange.first(), dateRange.last())
                .map { usageRecords ->
                    val appOpens = if (usageRecords.isNotEmpty()) {
                        val groupedByPackage: Map<String, List<DailyAppUsageRecord>> = usageRecords
                            .filter { it.appOpenCount > 0 }
                            .groupBy { it.packageName }

                        val mappedItems: List<AppOpenUiItem> = withContext(Dispatchers.Default) {
                            val deferredItems = groupedByPackage.map { (_, records) ->
                                async {
                                    val firstRecord = records.first()
                                    val totalOpens = records.sumOf { it.appOpenCount }
                                    val avgOpens = when (state.period) {
                                        UnlockPeriod.Daily -> totalOpens
                                        UnlockPeriod.Weekly, UnlockPeriod.Monthly -> {
                                            (totalOpens.toDouble() / dateRange.size).roundToInt()
                                        }
                                    }
                                    if (avgOpens > 0) {
                                        mapper.mapToAppOpenUiItem(firstRecord).copy(openCount = avgOpens)
                                    } else null
                                }
                            }
                            deferredItems.awaitAll().filterNotNull()
                        }

                        mappedItems.sortedByDescending { item -> item.openCount }
                    } else emptyList()
                    state.copy(appOpens = appOpens)
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UnlocksUiState()
    )

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    fun onPeriodChanged(period: UnlockPeriod) {
        _period.value = period
    }

}
