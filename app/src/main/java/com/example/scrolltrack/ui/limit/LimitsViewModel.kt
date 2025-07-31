package com.example.scrolltrack.ui.limit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.db.LimitGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LimitsViewModel @Inject constructor(
    private val limitsRepository: LimitsRepository,
    private val appMetadataRepository: AppMetadataRepository
) : ViewModel() {

    private val _createEditUiState = MutableStateFlow(CreateEditGroupUiState())
    val createEditUiState: StateFlow<CreateEditGroupUiState> = _createEditUiState.asStateFlow()

    private val _navigateBackEvent = MutableSharedFlow<Unit>()
    val navigateBackEvent = _navigateBackEvent.asSharedFlow()

    private var originalSelectedApps: Set<String> = emptySet()

    val limitGroups: StateFlow<List<LimitGroupUiModel>> =
        limitsRepository.getAllVisibleGroups()
            .combine(appMetadataRepository.getAllMetadata()) { groups, metadataList ->
                val metadataMap = metadataList.associateBy { it.packageName }
                groups.map { group ->
                    val groupWithApps = limitsRepository.getGroupWithApps(group.id).firstOrNull()
                    val appModels = groupWithApps?.apps?.mapNotNull { limitedApp ->
                        metadataMap[limitedApp.package_name]?.let { metadata ->
                            AppUsageUiItem(
                                id = metadata.packageName,
                                appName = metadata.appName,
                                icon = appMetadataRepository.getIconFile(metadata.packageName),
                                usageTimeMillis = 0L, // Not needed for this display
                                packageName = metadata.packageName
                            )
                        }
                    } ?: emptyList()

                    LimitGroupUiModel(
                        groupId = group.id,
                        name = group.name,
                        timeLimitFormatted = "${group.time_limit_minutes} min",
                        apps = appModels
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    fun loadGroupDetails(groupId: Long?) {
        viewModelScope.launch {
            val allAppsMetadata = appMetadataRepository.getVisibleApps().first()
            val allAppUiItems = allAppsMetadata.map { metadata ->
                AppUsageUiItem(
                    id = metadata.packageName,
                    appName = metadata.appName,
                    icon = appMetadataRepository.getIconFile(metadata.packageName),
                    usageTimeMillis = 0L,
                    packageName = metadata.packageName
                )
            }.sortedBy { it.appName.lowercase() }

            if (groupId == null) {
                // CREATE mode
                originalSelectedApps = emptySet()
                _createEditUiState.value = CreateEditGroupUiState(
                    allApps = allAppUiItems.map { SelectableAppUiModel(it, false) }
                )
            } else {
                // EDIT mode
                val groupWithApps = limitsRepository.getGroupWithApps(groupId).firstOrNull() ?: return@launch
                val selectedPackageNames = groupWithApps.apps.map { it.package_name }.toSet()
                originalSelectedApps = selectedPackageNames

                _createEditUiState.value = CreateEditGroupUiState(
                    groupId = groupId,
                    groupName = groupWithApps.group.name,
                    timeLimitMinutes = groupWithApps.group.time_limit_minutes,
                    allApps = allAppUiItems.map { SelectableAppUiModel(it, selectedPackageNames.contains(it.packageName)) }
                )
            }
        }
    }

    fun onGroupNameChange(name: String) {
        _createEditUiState.update { it.copy(groupName = name) }
    }

    fun onTimeLimitChange(minutes: Int) {
        _createEditUiState.update { it.copy(timeLimitMinutes = minutes) }
    }

    fun onAppSelectionChange(packageName: String, isSelected: Boolean) {
        _createEditUiState.update { currentState ->
            val updatedApps = currentState.allApps.map {
                if (it.app.packageName == packageName) {
                    it.copy(isSelected = isSelected)
                } else {
                    it
                }
            }
            currentState.copy(allApps = updatedApps)
        }
    }

    fun saveGroup() {
        viewModelScope.launch {
            val currentState = _createEditUiState.value
            if (currentState.groupName.isBlank()) {
                _createEditUiState.update { it.copy(error = "Group name cannot be empty") }
                return@launch
            }
            _createEditUiState.update { it.copy(isSaving = true, error = null) }

            val currentlySelectedApps = currentState.allApps.filter { it.isSelected }.map { it.app.packageName }.toSet()

            if (currentState.groupId == null) {
                // CREATE new group
                // CREATE new group
                if (limitsRepository.groupExists(currentState.groupName)) {
                    _createEditUiState.update { it.copy(error = "Group name already exists", isSaving = false) }
                    return@launch
                }
                val newGroupId = limitsRepository.createGroup(currentState.groupName, currentState.timeLimitMinutes)
                currentlySelectedApps.forEach { packageName ->
                    limitsRepository.addAppToGroup(packageName, newGroupId)
                }
            } else {
                // EDIT existing group
                val groupWithApps = limitsRepository.getGroupWithApps(currentState.groupId).firstOrNull() ?: return@launch
                val groupToUpdate = groupWithApps.group

                val updatedGroup = groupToUpdate.copy(
                    name = currentState.groupName,
                    time_limit_minutes = currentState.timeLimitMinutes
                )
                limitsRepository.updateGroup(updatedGroup)

                val appsToAdd = currentlySelectedApps - originalSelectedApps
                val appsToRemove = originalSelectedApps - currentlySelectedApps

                appsToAdd.forEach { limitsRepository.addAppToGroup(it, currentState.groupId) }
                appsToRemove.forEach { limitsRepository.removeAppLimit(it) }
            }
            _navigateBackEvent.emit(Unit)
        }
    }

    fun setQuickLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            limitsRepository.setAppLimit(packageName, limitMinutes)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            val groupWithApps = limitsRepository.getGroupWithApps(groupId).firstOrNull()
            groupWithApps?.group?.let {
                limitsRepository.deleteGroup(it)
            }
        }
    }
}