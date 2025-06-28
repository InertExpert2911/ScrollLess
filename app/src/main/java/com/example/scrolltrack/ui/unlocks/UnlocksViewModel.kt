package com.example.scrolltrack.ui.unlocks

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class UnlockPeriod {
    Daily, Weekly, Monthly
}

// UI Model for the list item
data class AppOpenUiItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val openCount: Int
)

// UI Model for the heatmap cells
data class HeatmapCell(
    val date: String, // "YYYY-MM-DD"
    val count: Int
)

// UI State for the entire screen
sealed interface UnlocksUiState {
    object Loading : UnlocksUiState
    data class Success(
        val appOpens: List<AppOpenUiItem>,
        val selectedPeriod: UnlockPeriod,
        val selectedDateString: String,
        val periodTitle: String
    ) : UnlocksUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class UnlocksViewModel(
    private val repository: ScrollDataRepository,
    private val application: Application
) : ViewModel() {
    private val packageManager: PackageManager = application.packageManager

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    private val _selectedPeriod = MutableStateFlow(UnlockPeriod.Daily)
    val selectedPeriod: StateFlow<UnlockPeriod> = _selectedPeriod.asStateFlow()

    val heatmapData: StateFlow<Map<YearMonth, List<HeatmapCell>>> = repository.getAllDeviceSummaries()
        .map { summaries ->
            summaries.map { HeatmapCell(it.dateString, it.totalUnlockCount) }
                .groupBy { YearMonth.from(LocalDate.parse(it.date)) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<UnlocksUiState> = combine(
        _selectedDate,
        _selectedPeriod
    ) { date, period ->
        date to period
    }.flatMapLatest { (date, period) ->
        flow {
            emit(UnlocksUiState.Loading)
            val localDate = LocalDate.parse(date)
            val (startDate, endDate, title) = when (period) {
                UnlockPeriod.Daily -> Triple(date, date, "For $date")
                UnlockPeriod.Weekly -> {
                    val start = DateUtil.getStartOfWeek(localDate)
                    val end = DateUtil.getEndOfWeek(localDate)
                    Triple(start.toString(), end.toString(), "Week of ${start.format(DateTimeFormatter.ofPattern("MMM d"))}")
                }
                UnlockPeriod.Monthly -> {
                    val start = DateUtil.getStartOfMonth(localDate)
                    val end = DateUtil.getEndOfMonth(localDate)
                    Triple(start.toString(), end.toString(), localDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                }
            }

            val records = repository.getUsageRecordsForDateRange(startDate, endDate).first()
            val appOpens = withContext(Dispatchers.Default) {
                records
                    .groupBy { it.packageName }
                    .mapValues { entry -> entry.value.sumOf { it.appOpenCount } }
                    .map { (pkg, count) ->
                        Triple(pkg, count, getAppName(pkg))
                    }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .mapNotNull { (pkg, count, name) ->
                        try {
                            AppOpenUiItem(
                                packageName = pkg,
                                appName = name,
                                icon = packageManager.getApplicationIcon(pkg),
                                openCount = count
                            )
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
            }
            emit(UnlocksUiState.Success(appOpens, period, date, title))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UnlocksUiState.Loading
    )

    fun selectPeriod(period: UnlockPeriod) {
        _selectedPeriod.value = period
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
        // When a new date is selected from heatmap, default to Daily view
        _selectedPeriod.value = UnlockPeriod.Daily
    }

    private suspend fun getAppName(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
} 