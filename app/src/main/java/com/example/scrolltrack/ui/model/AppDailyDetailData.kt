package com.example.scrolltrack.ui.model

/**
 * Data class for holding daily detail data for the chart.
 */
data class AppDailyDetailData(
    val date: String,
    val usageTimeMillis: Long,
    val activeTimeMillis: Long,
    val scrollUnits: Long
) 