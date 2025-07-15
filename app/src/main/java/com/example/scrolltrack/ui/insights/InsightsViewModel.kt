package com.example.scrolltrack.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: ScrollDataRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())

    private val summaryData: StateFlow<DailyDeviceSummary?> = _selectedDate.flatMapLatest { date ->
        repository.getDeviceSummaryForDate(date)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000L), null)

    val intentionalUnlocks: StateFlow<Int> = summaryData.map { it?.intentionalUnlockCount ?: 0 }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000L), 0)

    val glanceUnlocks: StateFlow<Int> = summaryData.map { it?.glanceUnlockCount ?: 0 }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000L), 0)

    val firstUnlockTime: StateFlow<String> = summaryData.map {
        it?.firstUnlockTimestampUtc?.let { ts -> DateUtil.formatUtcTimestampToTimeString(ts) } ?: "N/A"
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000L), "N/A")

    val lastUnlockTime: StateFlow<String> = summaryData.map {
        it?.lastUnlockTimestampUtc?.let { ts -> DateUtil.formatUtcTimestampToTimeString(ts) } ?: "N/A"
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000L), "N/A")
}
