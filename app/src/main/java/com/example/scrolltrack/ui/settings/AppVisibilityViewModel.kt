package com.example.scrolltrack.ui.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppVisibilityViewModel @Inject constructor(
    private val appMetadataRepository: AppMetadataRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppVisibilityItem>>(emptyList())
    val apps: StateFlow<List<AppVisibilityItem>> = _apps.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            try {
                val metadataList = appMetadataRepository.getAllMetadata()
                    .filter { it.packageName != context.packageName } // Filter out our own app
                    .sortedBy { it.appName.lowercase() }

                _apps.value = metadataList.map { metadata ->
                    val icon = appMetadataRepository.getIconFile(metadata.packageName)?.let {
                        Drawable.createFromPath(it.absolutePath)
                    }
                    AppVisibilityItem(
                        packageName = metadata.packageName,
                        appName = metadata.appName,
                        icon = icon,
                        visibilityState = when (metadata.userHidesOverride) {
                            true -> VisibilityState.HIDDEN
                            false -> VisibilityState.VISIBLE
                            null -> VisibilityState.DEFAULT
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("AppVisibilityViewModel", "Error loading apps", e)
                // Handle error state if necessary
            }
        }
    }

    fun setAppVisibility(packageName: String, newState: VisibilityState) {
        viewModelScope.launch {
            try {
                val newOverrideState = when (newState) {
                    VisibilityState.HIDDEN -> true
                    VisibilityState.VISIBLE -> false
                    VisibilityState.DEFAULT -> null
                }
                appMetadataRepository.updateUserHidesOverride(packageName, newOverrideState)

                // Update the state locally for a more responsive UI
                _apps.update { currentList ->
                    currentList.map {
                        if (it.packageName == packageName) {
                            it.copy(visibilityState = newState)
                        } else {
                            it
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppVisibilityViewModel", "Error setting app visibility for $packageName", e)
                // Optionally reload all apps to ensure consistency on error
                loadApps()
            }
        }
    }
}
