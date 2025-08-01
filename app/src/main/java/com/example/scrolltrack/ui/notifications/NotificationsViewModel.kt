package com.example.scrolltrack.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.di.IoDispatcher
import com.example.scrolltrack.ui.limit.LimitViewModelDelegate
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import android.graphics.drawable.Drawable

enum class NotificationPeriod {
    Daily, Weekly, Monthly
}

sealed interface NotificationsUiState {
    object Loading : NotificationsUiState
    data class Success(
        val notificationCounts: List<Pair<AppMetadata, Int>>,
        val selectedPeriod: NotificationPeriod,
        val periodTitle: String,
        val totalCount: Int,
        val heatmapData: Map<LocalDate, Int> = emptyMap(),
        val selectedDate: LocalDate = LocalDate.now()
    ) : NotificationsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val limitViewModelDelegate: LimitViewModelDelegate
) : ViewModel() {

    private data class PeriodDetails(val startDate: String, val endDate: String, val title: String, val dateRange: List<String>)
    val setLimitSheetState = limitViewModelDelegate.setLimitSheetState

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _selectedPeriod = MutableStateFlow(NotificationPeriod.Daily)

    val uiState: StateFlow<NotificationsUiState> = combine(
        _selectedDate,
        _selectedPeriod,
        repository.getAllNotificationSummaries()
    ) { localDate, period, allSummaries ->
        val heatmapData = allSummaries
            .mapNotNull { summary -> summary.date?.let { LocalDate.parse(it) to summary.count } }
            .toMap()

        val periodDetails = when (period) {
            NotificationPeriod.Daily -> {
                val date = localDate.toString()
                PeriodDetails(date, date, localDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), listOf(date))
            }
            NotificationPeriod.Weekly -> {
                val start = DateUtil.getStartOfWeek(localDate)
                val end = DateUtil.getEndOfWeek(localDate)
                val dates = (0..6).map { start.plusDays(it.toLong()).toString() }
                PeriodDetails(start.toString(), end.toString(), "Week ${DateUtil.getWeekOfYear(localDate)} (${start.format(DateTimeFormatter.ofPattern("MMM d"))} - ${end.format(DateTimeFormatter.ofPattern("d, yyyy"))})", dates)
            }
            NotificationPeriod.Monthly -> {
                val start = DateUtil.getStartOfMonth(localDate)
                val dates = (0 until start.lengthOfMonth()).map { start.plusDays(it.toLong()).toString() }
                PeriodDetails(start.toString(), dates.last(), localDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")), dates)
            }
        }

        val appNotificationCounts = repository.getNotificationCountPerAppForPeriod(periodDetails.startDate, periodDetails.endDate).first()

        val notificationItems = withContext(ioDispatcher) {
            appNotificationCounts
                .mapNotNull { countPerApp ->
                    appMetadataRepository.getAppMetadata(countPerApp.packageName)?.let { metadata ->
                        if (!metadata.isUserVisible || !metadata.isInstalled) return@mapNotNull null
                        val avgCount = when (period) {
                            NotificationPeriod.Daily -> countPerApp.count
                            else -> (countPerApp.count.toDouble() / periodDetails.dateRange.size).toInt()
                        }
                        if (avgCount > 0) {
                            metadata to avgCount
                        } else {
                            null
                        }
                    }
                }
                .sortedByDescending { it.second }
        }

        val totalCount = when (period) {
            NotificationPeriod.Daily -> notificationItems.sumOf { it.second }
            else -> {
                val total = appNotificationCounts.sumOf { it.count }
                if (periodDetails.dateRange.isNotEmpty()) (total.toDouble() / periodDetails.dateRange.size).toInt() else 0
            }
        }

        NotificationsUiState.Success(
            notificationCounts = notificationItems,
            selectedPeriod = period,
            periodTitle = periodDetails.title,
            totalCount = totalCount,
            heatmapData = heatmapData,
            selectedDate = localDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState.Loading
    )

fun selectPeriod(period: NotificationPeriod) {
    _selectedPeriod.value = period
}

fun onDateSelected(date: LocalDate) {
    _selectedDate.value = date
}

    suspend fun getIcon(packageName: String): Drawable? = withContext(ioDispatcher) {
        appMetadataRepository.getIconFile(packageName)?.let {
            Drawable.createFromPath(it.absolutePath)
        }
    }

suspend fun getIconFile(packageName: String): java.io.File? = withContext(ioDispatcher) {
        appMetadataRepository.getIconFile(packageName)
    }

    fun onQuickLimitIconClicked(packageName: String, appName: String) {
        limitViewModelDelegate.onQuickLimitIconClicked(viewModelScope, packageName, appName)
    }

    fun onSetLimit(packageName: String, limitMinutes: Int) {
        limitViewModelDelegate.onSetLimit(viewModelScope, packageName, limitMinutes)
    }

    fun onDeleteLimit(packageName: String) {
        limitViewModelDelegate.onDeleteLimit(viewModelScope, packageName)
    }

    fun dismissSetLimitSheet() {
        limitViewModelDelegate.dismissSetLimitSheet()
    }
}
