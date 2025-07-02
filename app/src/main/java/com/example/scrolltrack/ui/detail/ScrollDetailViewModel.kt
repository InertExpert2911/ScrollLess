package com.example.scrolltrack.ui.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.util.ConversionUtil
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
class ScrollDetailViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository,
    private val mapper: AppUiModelMapper,
    internal val conversionUtil: ConversionUtil,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialDate: String = savedStateHandle.get<String>("date") ?: DateUtil.getCurrentLocalDateString()

    private val _selectedDateForScrollDetail = MutableStateFlow(initialDate)
    val selectedDateForScrollDetail: StateFlow<String> = _selectedDateForScrollDetail.asStateFlow()

    val aggregatedScrollDataForSelectedDate: StateFlow<List<AppScrollUiItem>> =
        _selectedDateForScrollDetail.flatMapLatest { dateString ->
            Log.d("ScrollDetailViewModel", "Loading aggregated scroll data for date: $dateString")
            repository.getAggregatedScrollDataForDate(dateString)
                .map { appScrollDataList ->
                    Log.d("ScrollDetailViewModel", "Received ${appScrollDataList.size} raw scroll items for $dateString")
                    val uiItems = appScrollDataList.map { mapper.mapToAppScrollUiItem(it) }
                    Log.d("ScrollDetailViewModel", "Mapped to ${uiItems.size} UI scroll items for $dateString")
                    uiItems
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectableDatesForScrollDetail: StateFlow<Set<Long>> =
        repository.getAllDistinctScrollDateStrings()
            .map { dateStrings ->
                dateStrings.mapNotNull { DateUtil.parseLocalDateString(it)?.time }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun updateSelectedDateForScrollDetail(dateMillis: Long) {
        _selectedDateForScrollDetail.value = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
    }
} 