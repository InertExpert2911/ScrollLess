package com.example.scrolltrack.ui.insights

import java.io.File

sealed class InsightCardUiModel(val id: String) {
    data class FirstApp(
        val appName: String?,
        val icon: File?,
        val time: String
    ) : InsightCardUiModel("first_app")

    data class LastApp(
        val appName: String?,
        val icon: File?,
        val time: String
    ) : InsightCardUiModel("last_app")

    data class CompulsiveCheck(
        val appName: String?,
        val icon: File?,
        val count: Int
    ) : InsightCardUiModel("compulsive_check")

    data class NotificationLeader(
        val appName: String?,
        val icon: File?,
        val percentage: Int
    ) : InsightCardUiModel("notification_leader")

    data class TimePattern(
        val timeOfDay: String, // e.g., "Evening", "Lunchtime"
        val metric: String, // e.g., "Screen Time", "Unlocks"
        val period: String // e.g., "7 PM - 9 PM"
    ) : InsightCardUiModel("time_pattern")

    data class Loading(val placeholderId: String) : InsightCardUiModel("loading_$placeholderId")
}
