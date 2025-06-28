package com.example.scrolltrack.ui.historical

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoricalViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository
) : ViewModel() {

    private val _todayDateString = DateUtil.getCurrentLocalDateString()

    private val _selectedDateForHistory = MutableStateFlow(_todayDateString)
    val selectedDateForHistory: StateFlow<String> = _selectedDateForHistory.asStateFlow()

    val isTodaySelectedForHistory: StateFlow<Boolean> = _selectedDateForHistory.map { it == _todayDateString }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val selectableDatesForHistoricalUsage: StateFlow<Set<Long>> =
        repository.getAllDistinctUsageDateStrings()
            .map { dateStrings ->
                dateStrings.mapNotNull { DateUtil.parseLocalDateString(it)?.time }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val dailyAppUsageForSelectedDateHistory: StateFlow<List<AppUsageUiItem>> =
        selectedDateForHistory.flatMapLatest { dateString ->
            repository.getDailyUsageRecordsForDate(dateString)
                .map { dailyUsageRecords -> processUsageRecords(dailyUsageRecords) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalUsageTimeForSelectedDateHistoryFormatted: StateFlow<String> =
        dailyAppUsageForSelectedDateHistory.map { appUsageList ->
            val totalMillis = appUsageList.sumOf { it.usageTimeMillis }
            DateUtil.formatDuration(totalMillis)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "Loading...")


    fun updateSelectedDateForHistory(dateMillis: Long) {
        val newDateString = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
        if (_selectedDateForHistory.value != newDateString) {
            _selectedDateForHistory.value = newDateString
        }
    }

    fun resetSelectedDateToToday() {
        _selectedDateForHistory.value = _todayDateString
    }

    private suspend fun processUsageRecords(records: List<DailyAppUsageRecord>): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            records.mapNotNull { record ->
                processSingleUsageRecordToUiItem(record)
            }.sortedByDescending { it.usageTimeMillis }
        }
    }

    private suspend fun processSingleUsageRecordToUiItem(record: DailyAppUsageRecord): AppUsageUiItem? {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(record.packageName)
            val icon = appMetadataRepository.getIconDrawable(record.packageName)

            if (metadata != null) {
                AppUsageUiItem(
                    id = record.packageName,
                    appName = metadata.appName,
                    icon = icon,
                    usageTimeMillis = record.usageTimeMillis,
                    packageName = record.packageName
                )
            } else {
                Log.w("HistoricalViewModel", "No metadata found for ${record.packageName}, creating fallback UI item.")
                val fallbackAppName = record.packageName.substringAfterLast('.', record.packageName)
                AppUsageUiItem(record.packageName, fallbackAppName, null, record.usageTimeMillis, record.packageName)
            }
        }
    }
} 