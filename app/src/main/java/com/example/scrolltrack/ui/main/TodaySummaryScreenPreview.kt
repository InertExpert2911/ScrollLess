package com.example.scrolltrack.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.ScrollTrackTheme

@Preview(
    name = "Today Summary (Light)",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun TodaySummaryScreenPreview() {
    val dummyTopApp = AppUsageUiItem("id_preview", "Top Weekly App", null, 3600000L * 5, "com.top.app")
    ScrollTrackTheme(darkTheme = false) {
        TodaySummaryScreen(
            navController = rememberNavController(),
            greeting = "Good Afternoon",
            isAccessibilityServiceEnabled = true,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = true,
            onEnableUsageStatsClick = {},
            isNotificationListenerEnabled = true,
            onEnableNotificationListenerClick = {},
            totalUsageTime = "3h 15m",
            totalUsageTimeMillis = 11700000L,
            todaysAppUsage = emptyList(),
            topWeeklyApp = dummyTopApp,
            totalScrollUnits = 12345L,
            scrollDistanceMeters = "31.4 m",
            totalUnlocks = 42,
            totalNotifications = 128,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToNotifications = {},
            onNavigateToScrollDetail = {},
            onNavigateToAppDetail = { _ -> },
            onSetLimit = { _, _ -> },
            isRefreshing = false,
            onRefresh = {},
            snackbarMessage = null,
            onSnackbarDismiss = {},
            screenTimeComparison = null,
            unlocksComparison = null,
            notificationsComparison = null,
            scrollComparison = null
        )
    }
}

@Preview(
    name = "Today Summary (Dark)",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun TodaySummaryScreenDarkPreview() {
    val dummyTopApp = AppUsageUiItem("id_preview", "Top Weekly App", null, 3600000L * 5, "com.top.app")
    ScrollTrackTheme(darkTheme = true) {
        TodaySummaryScreen(
            navController = rememberNavController(),
            greeting = "Good Evening",
            isAccessibilityServiceEnabled = true,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = true,
            onEnableUsageStatsClick = {},
            isNotificationListenerEnabled = true,
            onEnableNotificationListenerClick = {},
            totalUsageTime = "3h 15m",
            totalUsageTimeMillis = 11700000L,
            todaysAppUsage = emptyList(),
            topWeeklyApp = dummyTopApp,
            totalScrollUnits = 12345L,
            scrollDistanceMeters = "31.4 m",
            totalUnlocks = 42,
            totalNotifications = 128,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToNotifications = {},
            onNavigateToScrollDetail = {},
            onNavigateToAppDetail = { _ -> },
            onSetLimit = { _, _ -> },
            isRefreshing = false,
            onRefresh = {},
            snackbarMessage = null,
            onSnackbarDismiss = {},
            screenTimeComparison = null,
            unlocksComparison = null,
            notificationsComparison = null,
            scrollComparison = null
        )
    }
}

@Preview(
    name = "Today Summary With Permissions Needed",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun TodaySummaryScreenWithPermissionsPreview() {
    ScrollTrackTheme(darkTheme = true) {
        TodaySummaryScreen(
            navController = rememberNavController(),
            greeting = "Good Morning",
            isAccessibilityServiceEnabled = false,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = false,
            onEnableUsageStatsClick = {},
            isNotificationListenerEnabled = false,
            onEnableNotificationListenerClick = {},
            totalUsageTime = "...",
            totalUsageTimeMillis = 0L,
            todaysAppUsage = emptyList(),
            topWeeklyApp = null,
            totalScrollUnits = 0L,
            scrollDistanceMeters = "0 m",
            totalUnlocks = 0,
            totalNotifications = 0,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToNotifications = {},
            onNavigateToScrollDetail = {},
            onNavigateToAppDetail = { _ -> },
            onSetLimit = { _, _ -> },
            isRefreshing = false,
            onRefresh = {},
            snackbarMessage = null,
            onSnackbarDismiss = {},
            screenTimeComparison = null,
            unlocksComparison = null,
            notificationsComparison = null,
            scrollComparison = null
        )
    }
}
