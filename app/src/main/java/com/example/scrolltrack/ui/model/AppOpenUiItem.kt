package com.example.scrolltrack.ui.model

import com.example.scrolltrack.ui.limit.LimitInfo
import java.io.File

data class AppOpenUiItem(
    val packageName: String,
    val appName: String,
    val icon: File?,
    val openCount: Int,
    val limitInfo: LimitInfo? = null
)