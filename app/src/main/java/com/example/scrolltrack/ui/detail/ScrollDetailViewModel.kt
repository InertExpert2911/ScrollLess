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
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScrollDetailViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val mapper: AppUiModelMapper,
    val conversionUtil: ConversionUtil
) : ViewModel() {

    private val _selectedDateForScrollDetail = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val selectedDateForScrollDetail: StateFlow<String> = _selectedDateForScrollDetail.asStateFlow()

    val aggregatedScrollDataForSelectedDate: StateFlow<List<AppScrollUiItem>> =
        _selectedDateForScrollDetail.flatMapLatest { dateString ->
            repository.getScrollDataForDate(dateString)
                .map { scrollDataList ->
                    scrollDataList.map { mapper.mapToAppScrollUiItem(it) }
                        .sortedByDescending { it.totalScroll }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectableDatesForScrollDetail: StateFlow<Set<Long>> =
        repository.getAllDistinctUsageDateStrings()
            .map { dateStrings ->
                dateStrings.mapNotNull { DateUtil.parseLocalDateString(it)?.time }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun updateSelectedDateForScrollDetail(dateMillis: Long) {
        val newDateString = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
        if (_selectedDateForScrollDetail.value != newDateString) {
            _selectedDateForScrollDetail.value = newDateString
        }
    }
} 