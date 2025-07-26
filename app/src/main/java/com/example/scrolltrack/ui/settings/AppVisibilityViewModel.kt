package com.example.scrolltrack.ui.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.scrolltrack.db.AppMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

enum class VisibilityFilter {
    ALL,
    VISIBLE,
    HIDDEN
}

sealed interface AppVisibilityUiState {
    object Loading : AppVisibilityUiState
    data class Success(
        val apps: List<AppVisibilityItem>,
        val showNonInteractiveApps: Boolean,
        val visibilityFilter: VisibilityFilter
    ) : AppVisibilityUiState
    data class Error(val message: String) : AppVisibilityUiState
}

@HiltViewModel
class AppVisibilityViewModel @Inject constructor(
    private val appMetadataRepository: AppMetadataRepository,
    @param:ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppVisibilityUiState>(AppVisibilityUiState.Loading)
    val uiState: StateFlow<AppVisibilityUiState> = _uiState.asStateFlow()

    private val _showNonInteractiveApps = MutableStateFlow(false)
    private val _visibilityFilter = MutableStateFlow(VisibilityFilter.ALL)

    init {
        loadApps()
    }

    fun toggleShowNonInteractiveApps() {
        _showNonInteractiveApps.value = !_showNonInteractiveApps.value
        loadApps()
    }

    fun setVisibilityFilter(filter: VisibilityFilter) {
        _visibilityFilter.value = filter
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = AppVisibilityUiState.Loading
            try {
                val appVisibilityItems = withContext(ioDispatcher) {
                    appMetadataRepository.getAllMetadata().first()
                        .mapNotNull { metadata ->
                            try {
                                val iconPath = appMetadataRepository.getIconFile(metadata.packageName)?.path
                                val icon = if (iconPath != null) Drawable.createFromPath(iconPath) else null
                                mapMetadataToVisibilityItem(metadata, icon)
                            } catch (e: Exception) {
                                Log.e("AppVisibilityViewModel", "Failed to map metadata for ${metadata.packageName}", e)
                                null
                            }
                        }
                        .filter { item ->
                            val visibilityFilterResult = when (_visibilityFilter.value) {
                                VisibilityFilter.ALL -> true
                                VisibilityFilter.VISIBLE -> item.visibilityState == VisibilityState.VISIBLE || (item.visibilityState == VisibilityState.DEFAULT && item.isDefaultVisible)
                                VisibilityFilter.HIDDEN -> item.visibilityState == VisibilityState.HIDDEN || (item.visibilityState == VisibilityState.DEFAULT && !item.isDefaultVisible)
                            }
                            val interactiveFilterResult = if (_showNonInteractiveApps.value) true else item.isDefaultVisible

                            if (_visibilityFilter.value == VisibilityFilter.HIDDEN) {
                                visibilityFilterResult
                            } else {
                                visibilityFilterResult && interactiveFilterResult
                            }
                        }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
                }
                _uiState.value = AppVisibilityUiState.Success(appVisibilityItems, _showNonInteractiveApps.value, _visibilityFilter.value)
            } catch (e: Exception) {
                _uiState.value = AppVisibilityUiState.Error("Failed to load apps")
            }
        }
    }

    private fun mapMetadataToVisibilityItem(metadata: AppMetadata, icon: Drawable?): AppVisibilityItem {
        val state = when (metadata.userHidesOverride) {
            true -> VisibilityState.HIDDEN
            false -> VisibilityState.VISIBLE
            null -> VisibilityState.DEFAULT
        }
        return AppVisibilityItem(
            packageName = metadata.packageName,
            appName = metadata.appName,
            icon = icon,
            visibilityState = state,
            isSystemApp = metadata.isSystemApp,
            isDefaultVisible = metadata.isUserVisible
        )
    }

    fun setAppVisibility(packageName: String, visibility: VisibilityState) {
        viewModelScope.launch {
            val userHidesOverride = when (visibility) {
                VisibilityState.VISIBLE -> false
                VisibilityState.HIDDEN -> true
                VisibilityState.DEFAULT -> null
            }
            appMetadataRepository.updateUserHidesOverride(packageName, userHidesOverride)

            // Update UI locally to feel faster
            val currentState = _uiState.value
            if (currentState is AppVisibilityUiState.Success) {
                val updatedApps = currentState.apps.map {
                    if (it.packageName == packageName) {
                        it.copy(visibilityState = visibility)
                    } else {
                        it
                    }
                }
                _uiState.value = currentState.copy(
                    apps = updatedApps.filter { app ->
                        when (_visibilityFilter.value) {
                            VisibilityFilter.ALL -> true
                            VisibilityFilter.VISIBLE -> app.visibilityState == VisibilityState.VISIBLE || (app.visibilityState == VisibilityState.DEFAULT && app.isDefaultVisible)
                            VisibilityFilter.HIDDEN -> app.visibilityState == VisibilityState.HIDDEN || (app.visibilityState == VisibilityState.DEFAULT && !app.isDefaultVisible)
                        }
                    }
                )
            }
        }
    }
}
