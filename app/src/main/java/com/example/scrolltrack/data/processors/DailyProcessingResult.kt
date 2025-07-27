package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.*

data class DailyProcessingResult(
    val unlockSessions: List<UnlockSessionRecord>,
    val scrollSessions: List<ScrollSessionRecord>,
    val usageRecords: List<DailyAppUsageRecord>,
    val deviceSummary: DailyDeviceSummary?,
    val insights: List<DailyInsight>
)