package com.example.scrolltrack.ui.phoneusage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

enum class PhoneUsagePeriod {
    Daily,
    Weekly,
    Monthly
}

data class PhoneUsageUiState(
    val heatmapData: Map<LocalDate, Int> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthsWithData: List<YearMonth> = emptyList(),
    val period: PhoneUsagePeriod = PhoneUsagePeriod.Daily,
    val periodDisplay: String = "",
    val usageStat: String = "",
    val appUsage: List<AppUsageUiItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhoneUsageViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val mapper: AppUiModelMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneUsageUiState())
    val uiState: StateFlow<PhoneUsageUiState> = _uiState.asStateFlow()

    init {
        observeHeatmapData()
        observeSelectedDateChanges()
    }

    private fun observeHeatmapData() {
        repository.getTotalUsageTimePerDay()
            .onEach { heatmapData ->
                val monthsWithData = heatmapData.keys
                    .map { YearMonth.from(it) }
                    .distinct()
                    .sorted()
                _uiState.update {
                    it.copy(
                        heatmapData = heatmapData,
                        monthsWithData = monthsWithData
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSelectedDateChanges() {
        _uiState.map { it.selectedDate to it.period }
            .distinctUntilChanged()
            .flatMapLatest { (date, period) ->
                when (period) {
                    PhoneUsagePeriod.Daily -> getDailyUsage(date)
                    PhoneUsagePeriod.Weekly -> getWeeklyUsage(date)
                    PhoneUsagePeriod.Monthly -> getMonthlyUsage(date)
                }
            }
            .onEach { newState ->
                _uiState.update {
                    it.copy(
                        periodDisplay = newState.periodDisplay,
                        usageStat = newState.usageStat,
                        appUsage = newState.appUsage
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun getDailyUsage(date: LocalDate): Flow<PhoneUsageUiState> {
        return repository.getAppUsageForDate(DateUtil.formatLocalDateToString(date)).map { usageRecords ->
            val totalUsage = usageRecords.sumOf { it.usageTimeMillis }
            _uiState.value.copy(
                periodDisplay = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                usageStat = DateUtil.formatDuration(totalUsage),
                appUsage = mapper.mapToAppUsageUiItems(usageRecords)
            )
        }
    }

    private fun getWeeklyUsage(date: LocalDate): Flow<PhoneUsageUiState> {
        val weekRange = DateUtil.getWeekRange(date)
        return repository.getAppUsageForDateRange(weekRange.first, weekRange.second).map { usageRecords ->
            val appUsage = mapper.mapToAppUsageUiItems(usageRecords, 7)
            val totalUsage = appUsage.sumOf { it.usageTimeMillis }
            _uiState.value.copy(
                periodDisplay = "Week ${DateUtil.getWeekOfYear(date)} (${weekRange.first.format(DateTimeFormatter.ofPattern("MMM d"))} - ${weekRange.second.format(DateTimeFormatter.ofPattern("d, yyyy"))})",
                usageStat = DateUtil.formatDuration(totalUsage),
                appUsage = appUsage
            )
        }
    }

    private fun getMonthlyUsage(date: LocalDate): Flow<PhoneUsageUiState> {
        val monthRange = DateUtil.getMonthRange(date)
        return repository.getAppUsageForDateRange(monthRange.first, monthRange.second).map { usageRecords ->
            val daysInMonth = YearMonth.from(date).lengthOfMonth()
            val appUsage = mapper.mapToAppUsageUiItems(usageRecords, daysInMonth)
            val totalUsage = appUsage.sumOf { it.usageTimeMillis }
            _uiState.value.copy(
                periodDisplay = YearMonth.from(date).format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                usageStat = DateUtil.formatDuration(totalUsage),
                appUsage = appUsage
            )
        }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onPeriodChanged(period: PhoneUsagePeriod) {
        _uiState.update { it.copy(period = period) }
    }

}
