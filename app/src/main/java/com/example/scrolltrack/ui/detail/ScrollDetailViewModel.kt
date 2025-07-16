package com.example.scrolltrack.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class ScrollDetailPeriod {
    Daily,
    Weekly,
    Monthly
}

data class ScrollDetailUiState(
    val heatmapData: Map<LocalDate, Int> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthsWithData: List<YearMonth> = emptyList(),
    val period: ScrollDetailPeriod = ScrollDetailPeriod.Daily,
    val periodDisplay: String = "",
    val scrollStat: String = "",
    val appScrolls: List<AppScrollUiItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScrollDetailViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val mapper: AppUiModelMapper,
    val conversionUtil: ConversionUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScrollDetailUiState())
    val uiState: StateFlow<ScrollDetailUiState> = _uiState.asStateFlow()

    init {
        observeHeatmapData()
        observeSelectedDateChanges()
    }

    private fun observeHeatmapData() {
        repository.getTotalScrollPerDay()
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
                    ScrollDetailPeriod.Daily -> getDailyScroll(date)
                    ScrollDetailPeriod.Weekly -> getWeeklyScroll(date)
                    ScrollDetailPeriod.Monthly -> getMonthlyScroll(date)
                }
            }
            .onEach { newState ->
                _uiState.update {
                    it.copy(
                        periodDisplay = newState.periodDisplay,
                        scrollStat = newState.scrollStat,
                        appScrolls = newState.appScrolls
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun getDailyScroll(date: LocalDate): Flow<ScrollDetailUiState> {
        return repository.getScrollDataForDate(DateUtil.formatLocalDateToString(date)).map { scrollData ->
            val totalScroll = scrollData.sumOf { it.totalScroll }
            _uiState.value.copy(
                periodDisplay = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                scrollStat = "${conversionUtil.formatScrollDistance(totalScroll, 0).first} ${conversionUtil.formatScrollDistance(totalScroll, 0).second}",
                appScrolls = scrollData.sortedByDescending { it.totalScroll }.map { mapper.mapToAppScrollUiItem(it) }
            )
        }
    }

    private fun getWeeklyScroll(date: LocalDate): Flow<ScrollDetailUiState> {
        val weekRange = DateUtil.getWeekRange(date)
        val daysInPeriod = 7
        return repository.getScrollDataForDateRange(weekRange.first, weekRange.second).map { scrollData ->
            val totalScroll = scrollData.sumOf { it.totalScroll } / daysInPeriod
            val averagedAppScrolls = scrollData
                .groupBy { it.packageName }
                .map { (packageName, data) ->
                    val total = data.sumOf { it.totalScroll }
                    val totalX = data.sumOf { it.totalScrollX }
                    val totalY = data.sumOf { it.totalScrollY }
                    data.first().copy(
                        totalScroll = total / daysInPeriod,
                        totalScrollX = totalX / daysInPeriod,
                        totalScrollY = totalY / daysInPeriod
                    )
                }

            _uiState.value.copy(
                periodDisplay = "Week ${DateUtil.getWeekOfYear(date)} (${weekRange.first.format(DateTimeFormatter.ofPattern("MMM d"))} - ${weekRange.second.format(DateTimeFormatter.ofPattern("d, yyyy"))})",
                scrollStat = "${conversionUtil.formatScrollDistance(totalScroll, 0).first}${conversionUtil.formatScrollDistance(totalScroll, 0).second}",
                appScrolls = averagedAppScrolls.sortedByDescending { it.totalScroll }.map { mapper.mapToAppScrollUiItem(it) }
            )
        }
    }

    private fun getMonthlyScroll(date: LocalDate): Flow<ScrollDetailUiState> {
        val monthRange = DateUtil.getMonthRange(date)
        val daysInPeriod = YearMonth.from(date).lengthOfMonth()
        return repository.getScrollDataForDateRange(monthRange.first, monthRange.second).map { scrollData ->
            val totalScroll = scrollData.sumOf { it.totalScroll } / daysInPeriod
            val averagedAppScrolls = scrollData
                .groupBy { it.packageName }
                .map { (packageName, data) ->
                    val total = data.sumOf { it.totalScroll }
                    val totalX = data.sumOf { it.totalScrollX }
                    val totalY = data.sumOf { it.totalScrollY }
                    data.first().copy(
                        totalScroll = total / daysInPeriod,
                        totalScrollX = totalX / daysInPeriod,
                        totalScrollY = totalY / daysInPeriod
                    )
                }

            _uiState.value.copy(
                periodDisplay = YearMonth.from(date).format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                scrollStat = "${conversionUtil.formatScrollDistance(totalScroll, 0).first}${conversionUtil.formatScrollDistance(totalScroll, 0).second}",
                appScrolls = averagedAppScrolls.sortedByDescending { it.totalScroll }.map { mapper.mapToAppScrollUiItem(it) }
            )
        }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onPeriodChanged(period: ScrollDetailPeriod) {
        _uiState.update { it.copy(period = period) }
    }
}
