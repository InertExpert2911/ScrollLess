package com.example.scrolltrack.ui.model

import java.io.File

data class AppOpenUiItem(
    val packageName: String,
    val appName: String,
    val icon: File?,
    val openCount: Int
) 