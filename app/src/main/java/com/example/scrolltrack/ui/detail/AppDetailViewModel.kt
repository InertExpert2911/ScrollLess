package com.example.scrolltrack.ui.detail

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.main.ComparisonColorType
import com.example.scrolltrack.ui.main.ComparisonIconType
import com.example.scrolltrack.ui.model.AppDailyDetailData
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlinx.coroutines.async
import java.time.LocalDate
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs


enum class ChartPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY
}
data class CombinedAppDailyData(
    val date: String,
    val usageTimeMillis: Long,
    val activeTimeMillis: Long,
    val scrollUnits: Long,
    val openCount: Int
)
@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository,
    internal val conversionUtil: ConversionUtil,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>("packageName")!!

    // --- App Detail Screen Specific States ---
    private val _appDetailAppName = MutableStateFlow<String?>(null)
    val appDetailAppName: StateFlow<String?> = _appDetailAppName.asStateFlow()

    private val _appDetailAppIcon = MutableStateFlow<Drawable?>(null)
    val appDetailAppIcon: StateFlow<Drawable?> = _appDetailAppIcon.asStateFlow()

    private val _appDetailChartData = MutableStateFlow<List<CombinedAppDailyData>>(emptyList())
    val appDetailChartData: StateFlow<List<CombinedAppDailyData>> = _appDetailChartData.asStateFlow()

    private val _currentChartPeriodType = MutableStateFlow(ChartPeriodType.DAILY)
    val currentChartPeriodType: StateFlow<ChartPeriodType> = _currentChartPeriodType.asStateFlow()

    // Represents the anchor date for the current chart view.
    private val _currentChartReferenceDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val currentChartReferenceDate: StateFlow<String> = _currentChartReferenceDate.asStateFlow()

    // --- New StateFlows for App Detail Summary ---
    private val _appDetailFocusedUsageDisplay = MutableStateFlow("0m")
    val appDetailFocusedUsageDisplay: StateFlow<String> = _appDetailFocusedUsageDisplay.asStateFlow()

    private val _appDetailFocusedActiveUsageDisplay = MutableStateFlow("0m")
    val appDetailFocusedActiveUsageDisplay: StateFlow<String> = _appDetailFocusedActiveUsageDisplay.asStateFlow()

    private val _appDetailPeriodDescriptionText = MutableStateFlow<String?>(null)
    val appDetailPeriodDescriptionText: StateFlow<String?> = _appDetailPeriodDescriptionText.asStateFlow()

    private val _appDetailFocusedPeriodDisplay = MutableStateFlow("")
    val appDetailFocusedPeriodDisplay: StateFlow<String> = _appDetailFocusedPeriodDisplay.asStateFlow()

    private val _appDetailComparisonText = MutableStateFlow<String?>(null)
    val appDetailComparisonText: StateFlow<String?> = _appDetailComparisonText.asStateFlow()

    private val _appDetailComparisonIconType = MutableStateFlow(ComparisonIconType.NONE)
    val appDetailComparisonIconType: StateFlow<ComparisonIconType> = _appDetailComparisonIconType.asStateFlow()

    private val _appDetailComparisonColorType = MutableStateFlow(ComparisonColorType.GREY)
    val appDetailComparisonColorType: StateFlow<ComparisonColorType> = _appDetailComparisonColorType.asStateFlow()

    private val _appDetailWeekNumberDisplay = MutableStateFlow<String?>(null)
    val appDetailWeekNumberDisplay: StateFlow<String?> = _appDetailWeekNumberDisplay.asStateFlow()

    private val _appDetailFocusedScrollDisplay = MutableStateFlow("0 m")
    val appDetailFocusedScrollDisplay: StateFlow<String> = _appDetailFocusedScrollDisplay.asStateFlow()

    private val _appDetailFocusedOpenCount = MutableStateFlow(0)
    val appDetailFocusedOpenCount: StateFlow<Int> = _appDetailFocusedOpenCount.asStateFlow()

    private val _appDetailFocusedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val appDetailFocusedDate: StateFlow<String> = _appDetailFocusedDate.asStateFlow()

    init {
        loadAppDetailsInfo()
    }

    // Methods for App Detail Screen
    private fun loadAppDetailsInfo() {
        // Since packageName is from SavedStateHandle, it's final. No need to check for changes.
        _currentChartPeriodType.value = ChartPeriodType.DAILY
        _currentChartReferenceDate.value = DateUtil.getCurrentLocalDateString()

        viewModelScope.launch(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(packageName)
            if (metadata != null) {
                _appDetailAppName.value = metadata.appName
                val iconFile = appMetadataRepository.getIconFile(packageName)
                _appDetailAppIcon.value = iconFile?.let { Drawable.createFromPath(it.absolutePath) }
            } else {
                Log.w("AppDetailViewModel", "App info not found for $packageName in AppDetailScreen")
                _appDetailAppName.value = packageName.substringAfterLast('.', packageName)
                _appDetailAppIcon.value = null
            }
        }
        loadAppDetailChartData(packageName, _currentChartPeriodType.value, _currentChartReferenceDate.value)
    }

    private fun loadAppDetailChartData(packageName: String, period: ChartPeriodType, referenceDateStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _appDetailChartData.value = emptyList()
            _appDetailFocusedUsageDisplay.value = "..."
            _appDetailFocusedPeriodDisplay.value = "..."
            _appDetailFocusedScrollDisplay.value = "..."
            _appDetailFocusedActiveUsageDisplay.value = "..."
            _appDetailComparisonText.value = null
            _appDetailWeekNumberDisplay.value = null
            _appDetailPeriodDescriptionText.value = null
            _appDetailFocusedOpenCount.value = 0

            Log.d("AppDetailViewModel", "Loading chart data for $packageName, Period: $period, RefDate: $referenceDateStr")

            val referenceDate = DateUtil.parseLocalDate(referenceDateStr)
            if (referenceDate == null) {
                Log.e("AppDetailViewModel", "Could not parse reference date: $referenceDateStr")
                // Handle error state appropriately, maybe clear the chart
                _appDetailChartData.value = emptyList()
                return@launch
            }

            val dateStringsForCurrentPeriod = calculateDateStringsForPeriod(period, referenceDate)
            if (dateStringsForCurrentPeriod.isEmpty()) {
                Log.w("AppDetailViewModel", "Date strings for current period resulted in empty list.")
                _appDetailChartData.value = emptyList()
                _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedPeriodDisplay.value = ""
                _appDetailFocusedScrollDisplay.value = conversionUtil.formatScrollDistance(0L, context).first
                return@launch
            }

            val usageDataDeferred = async { repository.getUsageForPackageAndDates(packageName, dateStringsForCurrentPeriod) }
            val scrollDataDeferred = async { repository.getAggregatedScrollForPackageAndDates(packageName, dateStringsForCurrentPeriod) }

            val currentPeriodUsageRecords = usageDataDeferred.await()
            val currentPeriodScrollRecords = scrollDataDeferred.await()

            val totalOpens = currentPeriodUsageRecords.sumOf { it.appOpenCount }
            _appDetailFocusedOpenCount.value = totalOpens

            val currentUsageMap = currentPeriodUsageRecords.associateBy { it.dateString }
            val currentScrollMap = currentPeriodScrollRecords.associateBy { it.dateString }

            val allDates = dateStringsForCurrentPeriod.toSet()

            val chartEntries = allDates.map { dateString ->
                val usage = currentUsageMap[dateString]
                val scroll = currentScrollMap[dateString]
                CombinedAppDailyData(
                    date = dateString,
                    usageTimeMillis = usage?.usageTimeMillis ?: 0L,
                    activeTimeMillis = usage?.activeTimeMillis ?: 0L,
                    scrollUnits = scroll?.totalScroll ?: 0L,
                    openCount = usage?.appOpenCount ?: 0
                )
            }.sortedBy { it.date }

            _appDetailChartData.value = chartEntries
            Log.d("AppDetailViewModel", "Chart data loaded: ${chartEntries.size} points for current period.")

            val sdfDisplay = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            val sdfMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

            when (period) {
                ChartPeriodType.DAILY -> {
                    val focusedDayData = chartEntries.firstOrNull { it.date == referenceDateStr }
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.usageTimeMillis ?: 0L)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.activeTimeMillis ?: 0L)
                    val scrollDistance = conversionUtil.formatScrollDistance(focusedDayData?.scrollUnits ?: 0L, context)
                    _appDetailFocusedScrollDisplay.value = "${scrollDistance.first} ${scrollDistance.second}"
                    _appDetailPeriodDescriptionText.value = "Daily Summary"
                    _appDetailFocusedPeriodDisplay.value = referenceDate.format(sdfDisplay)
                    _appDetailWeekNumberDisplay.value = null
                    _appDetailComparisonText.value = null
                }
                ChartPeriodType.WEEKLY -> {
                    val currentPeriodAverageUsage = calculateAverageUsage(chartEntries)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageActiveUsage = calculateAverageActiveUsage(chartEntries)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageActiveUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(chartEntries)
                    val scrollDistance = conversionUtil.formatScrollDistance(currentPeriodAverageScroll, context)
                    _appDetailFocusedScrollDisplay.value = "${scrollDistance.first} ${scrollDistance.second}"
                    _appDetailPeriodDescriptionText.value = "Weekly Average"
                    val (startOfWeek, endOfWeek) = getPeriodDisplayStrings(period, referenceDate)
                    _appDetailFocusedPeriodDisplay.value = "$startOfWeek - $endOfWeek"
                    _appDetailWeekNumberDisplay.value = "Week ${DateUtil.getWeekOfYear(referenceDate)}"

                    val (prevWeekDateStrings, _) = calculatePreviousPeriodDateStrings(period, referenceDate)
                    if (prevWeekDateStrings.isNotEmpty()) {
                        val prevWeekUsageRecords = repository.getUsageForPackageAndDates(packageName, prevWeekDateStrings)
                        val prevWeekAverageUsage = calculateAverageUsageFromRecords(prevWeekUsageRecords)
                        updateComparisonText(currentPeriodAverageUsage, prevWeekAverageUsage, "week")
                    } else {
                        _appDetailComparisonText.value = "No data for comparison"
                    }
                }
                ChartPeriodType.MONTHLY -> {
                    val currentPeriodAverageUsage = calculateAverageUsage(chartEntries)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageActiveUsage = calculateAverageActiveUsage(chartEntries)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageActiveUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(chartEntries)
                    val scrollDistance = conversionUtil.formatScrollDistance(currentPeriodAverageScroll, context)
                    _appDetailFocusedScrollDisplay.value = "${scrollDistance.first} ${scrollDistance.second}"

                    _appDetailPeriodDescriptionText.value = "Monthly Average"
                    _appDetailFocusedPeriodDisplay.value = referenceDate.format(sdfMonth)
                    _appDetailWeekNumberDisplay.value = null

                    val (prevMonthDateStrings, _) = calculatePreviousPeriodDateStrings(period, referenceDate)
                    if (prevMonthDateStrings.isNotEmpty()) {
                        val prevMonthUsageRecords = repository.getUsageForPackageAndDates(packageName, prevMonthDateStrings)
                        val prevMonthAverageUsage = calculateAverageUsageFromRecords(prevMonthUsageRecords)
                        updateComparisonText(currentPeriodAverageUsage, prevMonthAverageUsage, "month")
                    } else {
                        _appDetailComparisonText.value = "No data for comparison"
                    }
                }
            }
        }
    }

    private fun getWeekOfYear(date: LocalDate): Int {
        return DateUtil.getWeekOfYear(date)
    }

    private fun calculateAverageUsage(data: List<CombinedAppDailyData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun calculateAverageActiveUsage(data: List<CombinedAppDailyData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.activeTimeMillis } / data.size
    }

    private fun calculateAverageScroll(data: List<CombinedAppDailyData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.scrollUnits } / data.size
    }

    private fun calculateAverageUsageFromRecords(data: List<DailyAppUsageRecord>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun updateComparisonText(currentAvg: Long, previousAvg: Long, periodName: String) {
        val currentAvgFormatted = DateUtil.formatDuration(currentAvg)

        if (previousAvg == 0L) {
            if (currentAvg == 0L) {
                _appDetailComparisonText.value = "Still no usage"
                _appDetailComparisonIconType.value = ComparisonIconType.NEUTRAL
                _appDetailComparisonColorType.value = ComparisonColorType.GREY
            } else {
                _appDetailComparisonText.value = "$currentAvgFormatted from no usage"
                _appDetailComparisonIconType.value = ComparisonIconType.UP
                _appDetailComparisonColorType.value = ComparisonColorType.RED
            }
            return
        }

        if (currentAvg == previousAvg) {
            _appDetailComparisonText.value = "Same as last $periodName"
            _appDetailComparisonIconType.value = ComparisonIconType.NEUTRAL
            _appDetailComparisonColorType.value = ComparisonColorType.GREY
            return
        }

        val difference = currentAvg - previousAvg
        val percentageChange = abs((difference.toDouble() / previousAvg.toDouble()) * 100)
        val percentageString = String.format(Locale.US, "%.0f%%", percentageChange)

        if (currentAvg < previousAvg) {
            _appDetailComparisonText.value = "${percentageString} less vs last ${periodName}"
            _appDetailComparisonIconType.value = ComparisonIconType.DOWN
            _appDetailComparisonColorType.value = ComparisonColorType.GREEN
        } else {
            _appDetailComparisonText.value = "${percentageString} more vs last ${periodName}"
            _appDetailComparisonIconType.value = ComparisonIconType.UP
            _appDetailComparisonColorType.value = ComparisonColorType.RED
        }
    }

    private fun calculatePreviousPeriodDateStrings(period: ChartPeriodType, currentReferenceDate: LocalDate): Pair<List<String>, LocalDate> {
        val previousReferenceDate = when (period) {
            ChartPeriodType.DAILY -> currentReferenceDate.minusWeeks(1) // Not used but for completeness
            ChartPeriodType.WEEKLY -> currentReferenceDate.minusWeeks(1)
            ChartPeriodType.MONTHLY -> currentReferenceDate.minusMonths(1)
        }
        val previousPeriodDates = calculateDateStringsForPeriod(period, previousReferenceDate)
        return Pair(previousPeriodDates, previousReferenceDate)
    }

    private fun getPeriodDisplayStrings(period: ChartPeriodType, referenceDate: LocalDate, includeYear: Boolean = true): Pair<String, String> {
        val monthDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val monthDayYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        val dayOnlyFormat = DateTimeFormatter.ofPattern("d", Locale.getDefault())

        return when (period) {
            ChartPeriodType.DAILY -> {
                val format = if (includeYear) DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                Pair(referenceDate.format(format), "")
            }
            ChartPeriodType.WEEKLY -> {
                val startOfWeek = DateUtil.getStartOfWeek(referenceDate)
                val endOfWeek = DateUtil.getEndOfWeek(referenceDate)

                val startFormat = monthDayFormat
                val endFormat = if (startOfWeek.year != endOfWeek.year || !includeYear) {
                    if (includeYear) monthDayYearFormat else monthDayFormat
                } else if (startOfWeek.month != endOfWeek.month) {
                    monthDayFormat
                } else {
                    dayOnlyFormat
                }
                val yearSuffix = if (includeYear && startOfWeek.year == endOfWeek.year) ", ${startOfWeek.year}" else ""

                if (!includeYear){
                    return Pair(startOfWeek.format(monthDayFormat), endOfWeek.format(monthDayFormat))
                }

                Pair(startOfWeek.format(startFormat), "${endOfWeek.format(endFormat)}$yearSuffix")
            }
            ChartPeriodType.MONTHLY -> {
                val format = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                Pair(referenceDate.format(format), "")
            }
        }
    }


    private fun calculateDateStringsForPeriod(period: ChartPeriodType, referenceDate: LocalDate): List<String> {
        return when (period) {
            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> {
                val startOfWeek = DateUtil.getStartOfWeek(referenceDate)
                (0..6).map {
                    startOfWeek.plusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
                }
            }
            ChartPeriodType.MONTHLY -> {
                val startOfMonth = DateUtil.getStartOfMonth(referenceDate)
                val daysInMonth = startOfMonth.lengthOfMonth()
                (0 until daysInMonth).map {
                    startOfMonth.plusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
                }
            }
        }
    }

    fun changeChartPeriod(newPeriod: ChartPeriodType) {
        if (newPeriod != _currentChartPeriodType.value) {
            _currentChartPeriodType.value = newPeriod
            // When period changes, we reload the data for the same reference date
            loadAppDetailChartData(packageName, newPeriod, _currentChartReferenceDate.value)
        }
    }

    fun navigateChartDate(direction: Int) {
        val currentRefDate = DateUtil.parseLocalDate(_currentChartReferenceDate.value) ?: return
        val newDate = when (_currentChartPeriodType.value) {
            ChartPeriodType.DAILY -> currentRefDate.plusDays(direction.toLong())
            ChartPeriodType.WEEKLY -> currentRefDate.plusWeeks(direction.toLong())
            ChartPeriodType.MONTHLY -> currentRefDate.plusMonths(direction.toLong())
        }
        val newDateStr = DateUtil.formatDateToYyyyMmDdString(newDate)
        _currentChartReferenceDate.value = newDateStr
        loadAppDetailChartData(packageName, _currentChartPeriodType.value, newDateStr)
    }

    fun setFocusedDate(newDate: String) {
        if (_currentChartReferenceDate.value != newDate) {
            _currentChartReferenceDate.value = newDate
            if (_currentChartPeriodType.value == ChartPeriodType.DAILY) {
                loadAppDetailChartData(packageName, ChartPeriodType.DAILY, newDate)
            } else {
                Log.w("AppDetailViewModel", "setFocusedDate called with period type ${_currentChartPeriodType.value}. Expected DAILY.")
                loadAppDetailChartData(packageName, _currentChartPeriodType.value, newDate)
            }
        }
    }
} 