package com.example.scrolltrack.ui.notifications

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.model.NotificationTreemapItem
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class NotificationPeriod {
    Daily, Weekly, Monthly
}

sealed interface NotificationsUiState {
    object Loading : NotificationsUiState
    data class Success(
        val treemapItems: List<NotificationTreemapItem>,
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

    // A simple color palette for the treemap items
    private val treemapColors = listOf(
        Color(0xFF00497D), Color(0xFFB4D173), Color(0xFF7C292F), Color(0xFF4E6813), Color(0xFF9A4045)
    )

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

            val treemapItems = withContext(Dispatchers.Default) {
                appNotificationCounts
                    .mapNotNull { countPerApp ->
                        appMetadataRepository.getAppMetadata(countPerApp.packageName)?.let { metadata ->
                            if (!metadata.isUserVisible || !metadata.isInstalled) return@mapNotNull null // Filter out non-visible and uninstalled apps
                            val iconFile = appMetadataRepository.getIconFile(countPerApp.packageName)
                            Triple(metadata, iconFile, countPerApp.count)
                        }
                    }
                    .mapIndexed { index, (metadata, iconFile, count) ->
                        NotificationTreemapItem(
                            packageName = metadata.packageName,
                            appName = metadata.appName,
                            count = count,
                            icon = iconFile?.let { android.graphics.drawable.Drawable.createFromPath(it.absolutePath) },
                            color = treemapColors[index % treemapColors.size]
                        )
                    }
            }

            emit(NotificationsUiState.Success(treemapItems, period, title, totalCount))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState.Loading
    )

    fun selectPeriod(period: NotificationPeriod) {
        _selectedPeriod.value = period
    }
} 