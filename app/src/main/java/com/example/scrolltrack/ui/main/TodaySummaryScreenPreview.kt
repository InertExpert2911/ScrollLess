package com.example.scrolltrack.ui.main

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.AppTheme
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
            topWeeklyApp = dummyTopApp,
            totalScrollUnits = 12345L,
            scrollDistanceMeters = "31.4 m",
            totalUnlocks = 42,
            totalNotifications = 128,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToNotifications = {},
            onNavigateToAppDetail = {},
            onThemePaletteChange = {},
            currentThemePalette = AppTheme.CalmLavender,
            isDarkMode = false,
            onDarkModeChange = {}
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
            topWeeklyApp = dummyTopApp,
            totalScrollUnits = 12345L,
            scrollDistanceMeters = "31.4 m",
            totalUnlocks = 42,
            totalNotifications = 128,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToNotifications = {},
            onNavigateToAppDetail = {},
            onThemePaletteChange = {},
            currentThemePalette = AppTheme.CalmLavender,
            isDarkMode = true,
            onDarkModeChange = {}
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
            topWeeklyApp = null,
            totalScrollUnits = 0L,
            scrollDistanceMeters = "0 m",
            totalUnlocks = 0,
            totalNotifications = 0,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToNotifications = {},
            onNavigateToAppDetail = {},
            onThemePaletteChange = {},
            currentThemePalette = AppTheme.FocusBlue,
            isDarkMode = true,
            onDarkModeChange = {}
        )
    }
}

@Preview(showBackground = true, name = "Today Summary - Permissions Needed")
@Composable
fun TodaySummaryScreenPermissionsNeededPreview() {
    ScrollTrackTheme(darkTheme = true, dynamicColor = false) { // Updated call
        TodaySummaryScreen(
            navController = rememberNavController(),
            greeting = "Good Morning! ☀️",
            isAccessibilityServiceEnabled = false,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = false,
            onEnableUsageStatsClick = {},
            isNotificationListenerEnabled = false,
            onEnableNotificationListenerClick = {},
            totalUsageTime = "0m",
            totalUsageTimeMillis = 0L,
            topWeeklyApp = null,
            totalScrollUnits = 0L,
            scrollDistanceMeters = "0 m",
            totalUnlocks = 0,
            totalNotifications = 0,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToAppDetail = {},
            onNavigateToNotifications = {},
            onThemePaletteChange = {},
            currentThemePalette = AppTheme.FocusBlue,
            isDarkMode = true,
            onDarkModeChange = {}
        )
    }
}

@Preview(showBackground = true, name = "Today Summary - All Granted - Top App")
@Composable
fun TodaySummaryScreenAllGrantedWithTopAppPreview() {
    val topAppExample = AppUsageUiItem(
        id = "com.example.topapp",
        appName = "Social Butterfly",
        icon = null,
        usageTimeMillis = (10 * 60 * 60 * 1000).toLong(),
        packageName = "com.example.topapp"
    )

    ScrollTrackTheme(darkTheme = true, dynamicColor = false) { // Updated call
        val exampleTimeMillis = (2.75 * 60 * 60 * 1000).toLong()
        TodaySummaryScreen(
            navController = rememberNavController(),
            greeting = "Good Evening 👍",
            isAccessibilityServiceEnabled = true,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = true,
            onEnableUsageStatsClick = {},
            isNotificationListenerEnabled = true,
            onEnableNotificationListenerClick = {},
            totalUsageTime = "2h 45m",
            totalUsageTimeMillis = exampleTimeMillis,
            topWeeklyApp = topAppExample,
            totalScrollUnits = 13106L,
            scrollDistanceMeters = "1,230 m",
            totalUnlocks = 24,
            totalNotifications = 128,
            onNavigateToHistoricalUsage = {},
            onNavigateToUnlocks = {},
            onNavigateToAppDetail = {},
            onNavigateToNotifications = {},
            onThemePaletteChange = {},
            currentThemePalette = AppTheme.CalmLavender,
            isDarkMode = true,
            onDarkModeChange = {}
        )
    }
}

@Preview(showBackground = true, name = "Top Weekly App Card - With Data")
@Composable
fun TopWeeklyAppCardPreview() {
    ScrollTrackTheme(darkTheme = true, dynamicColor = false) { // Updated call
        val topAppExample = AppUsageUiItem(
            id = "com.example.topapp",
            appName = "Social Media Pro",
            icon = null,
            usageTimeMillis = (15 * 60 * 60 * 1000).toLong(),
            packageName = "com.example.topapp"
        )
        Box(modifier = Modifier.padding(16.dp).width(200.dp).height(180.dp)) {
            TopAppCard(topApp = topAppExample, modifier = Modifier.fillMaxSize(), onClick = {})
        }
    }
}

@Preview(showBackground = true, name = "Top Weekly App Card - No Data")
@Composable
fun TopWeeklyAppCardNoDataPreview() {
    ScrollTrackTheme(darkTheme = true, dynamicColor = false) { // Updated call
        Box(modifier = Modifier.padding(16.dp).width(200.dp).height(180.dp)) {
            TopAppCard(topApp = null, modifier = Modifier.fillMaxSize(), onClick = {})
        }
    }
}
