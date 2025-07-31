package com.example.scrolltrack.ui.limit

import com.example.scrolltrack.ui.model.AppUsageUiItem

// Represents a single group shown on the "Limits Hub" screen
data class LimitGroupUiModel(
    val groupId: Long,
    val name: String,
    val timeLimitFormatted: String,
    val apps: List<AppUsageUiItem> // Use the correct UI model
)

// Represents the state of the "Create/Edit" screen
data class CreateEditGroupUiState(
    val groupId: Long? = null,
    val groupName: String = "",
    val timeLimitMinutes: Int = 30,
    val allApps: List<SelectableAppUiModel> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null
)

data class SelectableAppUiModel(
    val app: AppUsageUiItem, // Use the correct UI model here too
    val isSelected: Boolean
)