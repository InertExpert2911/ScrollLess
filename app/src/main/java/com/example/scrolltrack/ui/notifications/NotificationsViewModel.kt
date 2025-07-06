package com.example.scrolltrack.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
        val totalCount: Int
    ) : NotificationsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    private val _selectedPeriod = MutableStateFlow(NotificationPeriod.Weekly)

    val uiState: StateFlow<NotificationsUiState> = combine(
        _selectedDate,
        _selectedPeriod
    ) { date, period ->
        date to period
    }.flatMapLatest { (date, period) ->
        flow {
            emit(NotificationsUiState.Loading)

            val localDate = LocalDate.parse(date)
            val (startDate, endDate, title) = when (period) {
                NotificationPeriod.Daily -> Triple(date, date, "For $date")
                NotificationPeriod.Weekly -> {
                    val start = DateUtil.getStartOfWeek(localDate)
                    val end = DateUtil.getEndOfWeek(localDate)
                    Triple(start.toString(), end.toString(), "Week of ${start.format(DateTimeFormatter.ofPattern("MMM d"))}")
                }
                NotificationPeriod.Monthly -> {
                    val start = DateUtil.getStartOfMonth(localDate)
                    val end = DateUtil.getEndOfMonth(localDate)
                    Triple(start.toString(), end.toString(), localDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                }
            }

            val appNotificationCounts = repository.getNotificationCountPerAppForPeriod(startDate, endDate).first()
            val totalCount = appNotificationCounts.sumOf { it.count }

            val notificationItems = withContext(Dispatchers.Default) {
                appNotificationCounts
                    .mapNotNull { countPerApp ->
                        appMetadataRepository.getAppMetadata(countPerApp.packageName)?.let { metadata ->
                            if (!metadata.isUserVisible || !metadata.isInstalled) return@mapNotNull null
                            metadata to countPerApp.count
                        }
                    }
                    .sortedByDescending { it.second }
            }

            emit(NotificationsUiState.Success(notificationItems, period, title, totalCount))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState.Loading
    )

    fun selectPeriod(period: NotificationPeriod) {
        _selectedPeriod.value = period
    }

    suspend fun getIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        appMetadataRepository.getIconFile(packageName)?.let {
            Drawable.createFromPath(it.absolutePath)
        }
    }
} 