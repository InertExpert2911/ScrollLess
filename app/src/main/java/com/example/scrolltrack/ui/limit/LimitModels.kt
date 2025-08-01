package com.example.scrolltrack.ui.limit

import com.example.scrolltrack.ui.model.AppUsageUiItem
import java.io.File

// Represents a single group shown on the "Limits Hub" screen
data class LimitGroupUiModel(
    val groupId: Long,
    val name: String,
    val timeLimitFormatted: String,
    val apps: List<AppUsageUiItem> // Use the correct UI model
)

data class IndividualLimitUiModel(
    val packageName: String,
    val appName: String,
    val icon: File?,
    val timeLimitFormatted: String
)

data class LimitsScreenUiState(
    val individualLimits: List<IndividualLimitUiModel> = emptyList(),
    val customLimitGroups: List<LimitGroupUiModel> = emptyList()
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

data class LimitInfo(
    val timeLimitMillis: Long,
    val timeRemainingMillis: Long
)