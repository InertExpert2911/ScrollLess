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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlin.math.abs


enum class ChartPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY
}

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>("packageName")!!

    // --- App Detail Screen Specific States ---
    private val _appDetailAppName = MutableStateFlow<String?>(null)
    val appDetailAppName: StateFlow<String?> = _appDetailAppName.asStateFlow()

    private val _appDetailAppIcon = MutableStateFlow<Drawable?>(null)
    val appDetailAppIcon: StateFlow<Drawable?> = _appDetailAppIcon.asStateFlow()

    private val _appDetailChartData = MutableStateFlow<List<AppDailyDetailData>>(emptyList())
    val appDetailChartData: StateFlow<List<AppDailyDetailData>> = _appDetailChartData.asStateFlow()

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

    private fun loadAppDetailChartData(packageName: String, period: ChartPeriodType, referenceDate: String) {
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

            Log.d("AppDetailViewModel", "Loading chart data for $packageName, Period: $period, RefDate: $referenceDate")

            val dateStringsForCurrentPeriod = calculateDateStringsForPeriod(period, referenceDate)
            if (dateStringsForCurrentPeriod.isEmpty()) {
                Log.w("AppDetailViewModel", "Date strings for current period resulted in empty list.")
                _appDetailChartData.value = emptyList()
                _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedPeriodDisplay.value = ""
                _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(0L, context).first
                return@launch
            }

            val usageDataDeferred = async { repository.getUsageForPackageAndDates(packageName, dateStringsForCurrentPeriod) }
            val scrollDataDeferred = async { repository.getAggregatedScrollForPackageAndDates(packageName, dateStringsForCurrentPeriod) }

            val currentPeriodUsageRecords = usageDataDeferred.await()
            val currentPeriodScrollRecords = scrollDataDeferred.await()

            val totalOpens = currentPeriodUsageRecords.sumOf { it.appOpenCount }
            _appDetailFocusedOpenCount.value = totalOpens

            val currentUsageMap = currentPeriodUsageRecords.associateBy { it.dateString }
            val currentScrollMap = currentPeriodScrollRecords.associateBy { it.date }

            val currentCombinedData = dateStringsForCurrentPeriod.map { dateStr ->
                AppDailyDetailData(
                    date = dateStr,
                    usageTimeMillis = currentUsageMap[dateStr]?.usageTimeMillis ?: 0L,
                    activeTimeMillis = currentUsageMap[dateStr]?.activeTimeMillis ?: 0L,
                    scrollUnits = currentScrollMap[dateStr]?.totalScroll ?: 0L
                )
            }
            _appDetailChartData.value = currentCombinedData
            Log.d("AppDetailViewModel", "Chart data loaded: ${currentCombinedData.size} points for current period.")

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            DateUtil.parseLocalDateString(referenceDate)?.let { calendar.time = it }
            val sdfDisplay = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())


            when (period) {
                ChartPeriodType.DAILY -> {
                    val focusedDayData = currentCombinedData.firstOrNull { it.date == referenceDate }
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.usageTimeMillis ?: 0L)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.activeTimeMillis ?: 0L)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(focusedDayData?.scrollUnits ?: 0L, context).first
                    _appDetailPeriodDescriptionText.value = "Daily Summary"
                    _appDetailFocusedPeriodDisplay.value = sdfDisplay.format(calendar.time)
                    _appDetailWeekNumberDisplay.value = null
                    _appDetailComparisonText.value = null
                }
                ChartPeriodType.WEEKLY -> {
                    val currentPeriodAverageUsage = calculateAverageUsage(currentCombinedData)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageActiveUsage = calculateAverageActiveUsage(currentCombinedData)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageActiveUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(currentCombinedData)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(currentPeriodAverageScroll, context).first
                    _appDetailPeriodDescriptionText.value = "Weekly Average"
                    val (startOfWeekStr, endOfWeekStr) = getPeriodDisplayStrings(period, referenceDate)
                    _appDetailFocusedPeriodDisplay.value = "$startOfWeekStr - $endOfWeekStr"
                    _appDetailWeekNumberDisplay.value = "Week ${getWeekOfYear(calendar)}"

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
                    val currentPeriodAverageUsage = calculateAverageUsage(currentCombinedData)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageActiveUsage = calculateAverageActiveUsage(currentCombinedData)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageActiveUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(currentCombinedData)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(currentPeriodAverageScroll, context).first

                    _appDetailPeriodDescriptionText.value = "Monthly Average"
                    _appDetailFocusedPeriodDisplay.value = sdfMonth.format(calendar.time)
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

    private fun getWeekOfYear(calendar: Calendar): Int {
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    private fun calculateAverageUsage(data: List<AppDailyDetailData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun calculateAverageActiveUsage(data: List<AppDailyDetailData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.activeTimeMillis } / data.size
    }

    private fun calculateAverageScroll(data: List<AppDailyDetailData>): Long {
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

    private fun calculatePreviousPeriodDateStrings(period: ChartPeriodType, currentReferenceDateStr: String): Pair<List<String>, String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        DateUtil.parseLocalDateString(currentReferenceDateStr)?.let { calendar.time = it }
        lateinit var newReferenceDateStr: String

        val previousPeriodDates = when (period) {
            ChartPeriodType.DAILY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr)
            }
            ChartPeriodType.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr)
            }
            ChartPeriodType.MONTHLY -> {
                calendar.add(Calendar.MONTH, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.MONTHLY, newReferenceDateStr)
            }
        }
        return Pair(previousPeriodDates, newReferenceDateStr)
    }

    private fun getPeriodDisplayStrings(period: ChartPeriodType, referenceDateStr: String, includeYear: Boolean = true): Pair<String, String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }

        val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        return when (period) {
            ChartPeriodType.DAILY -> {
                val sdf = if (includeYear) SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) else SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                Pair(sdf.format(calendar.time), "")
            }
            ChartPeriodType.WEEKLY -> {
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)
                val startOfWeek = calendar.time

                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val endOfWeek = calendar.time

                val startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = startOfWeek }
                val endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = endOfWeek }

                val startFormat = monthDayFormat
                val endFormat = if (startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) || !includeYear) {
                    if (includeYear) fullDateFormat else monthDayFormat
                } else if (startCal.get(Calendar.MONTH) != endCal.get(Calendar.MONTH)) {
                    monthDayFormat
                } else {
                    SimpleDateFormat("d", Locale.getDefault())
                }
                val yearSuffix = if (includeYear && startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) ", ${startCal.get(Calendar.YEAR)}" else ""

                if (!includeYear){
                    return Pair(startFormat.format(startOfWeek), SimpleDateFormat("MMM d", Locale.getDefault()).format(endOfWeek))
                }

                Pair(startFormat.format(startOfWeek), "${endFormat.format(endOfWeek)}$yearSuffix")
            }
            ChartPeriodType.MONTHLY -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                Pair(sdf.format(calendar.time), "")
            }
        }
    }


    private fun calculateDateStringsForPeriod(period: ChartPeriodType, referenceDateStr: String): List<String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }

        return when (period) {
            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> {
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }

                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)

                (0..6).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_YEAR, it)
                    DateUtil.formatDateToYyyyMmDdString(dayCal.time)
                }
            }
            ChartPeriodType.MONTHLY -> {
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                (0 until daysInMonth).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_MONTH, it)
                    DateUtil.formatDateToYyyyMmDdString(dayCal.time)
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
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = DateUtil.parseLocalDateString(_currentChartReferenceDate.value) ?: Date()
        }
        when (_currentChartPeriodType.value) {
            ChartPeriodType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, direction)
            ChartPeriodType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, direction)
            ChartPeriodType.MONTHLY -> cal.add(Calendar.MONTH, direction)
        }
        val newDateStr = DateUtil.formatDateToYyyyMmDdString(cal.time)
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