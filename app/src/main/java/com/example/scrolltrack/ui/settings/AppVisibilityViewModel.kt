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
import kotlinx.coroutines.withContext

sealed interface AppVisibilityUiState {
    object Loading : AppVisibilityUiState
    data class Success(val apps: List<AppVisibilityItem>) : AppVisibilityUiState
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

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = AppVisibilityUiState.Loading
            try {
                val appVisibilityItems = withContext(ioDispatcher) {
                    val allMetadata = appMetadataRepository.getAllMetadata()
                    allMetadata.mapNotNull { metadata ->
                        try {
                            val iconPath = appMetadataRepository.getIconFile(metadata.packageName)?.path
                            val icon = if (iconPath != null) Drawable.createFromPath(iconPath) else null
                            mapMetadataToVisibilityItem(metadata, icon)
                        } catch (e: Exception) {
                            Log.e("AppVisibilityViewModel", "Failed to map metadata for ${metadata.packageName}", e)
                            null
                        }
                    }.sortedBy { it.appName }
                }
                _uiState.value = AppVisibilityUiState.Success(appVisibilityItems)
            } catch (e: Exception) {
                _uiState.value = AppVisibilityUiState.Error("Failed to load apps")
            }
        }
    }

    private fun mapMetadataToVisibilityItem(metadata: AppMetadata, icon: Drawable?): AppVisibilityItem {
        val state = when {
            metadata.userHidesOverride == true -> VisibilityState.HIDDEN
            metadata.userHidesOverride == false -> VisibilityState.VISIBLE
            !metadata.isUserVisible -> VisibilityState.HIDDEN
            else -> VisibilityState.DEFAULT
        }
        return AppVisibilityItem(
            packageName = metadata.packageName,
            appName = metadata.appName,
            icon = icon,
            visibilityState = state
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
            loadApps() // Refresh the list
        }
    }
}
