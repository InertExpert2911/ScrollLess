package com.example.scrolltrack.ui.main

data class SetLimitSheetState(
    val packageName: String,
    val appName: String,
    val existingLimitMinutes: Int?,
    val averageUsageMillis: Long?,
    val suggestedLimitMinutes: Int?
)