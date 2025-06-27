package com.example.scrolltrack.ui.main

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
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.ScrollTrackTheme

@Preview(showBackground = true, name = "Today Summary - Permissions Needed")
@Composable
fun TodaySummaryScreenPermissionsNeededPreview() {
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) {
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
            onNavigateToAppDetail = {},
            onThemeChange = {},
            currentThemeVariant = "oled_dark"
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

    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) {
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
            onNavigateToAppDetail = {},
            onThemeChange = {},
            currentThemeVariant = "oled_dark"
        )
    }
}

@Preview(showBackground = true, name = "Top Weekly App Card - With Data")
@Composable
fun TopWeeklyAppCardPreview() {
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) {
        val topAppExample = AppUsageUiItem(
            id = "com.example.topapp",
            appName = "Social Media Pro",
            icon = null,
            usageTimeMillis = (15 * 60 * 60 * 1000).toLong(),
            packageName = "com.example.topapp"
        )
        Box(modifier = Modifier.padding(16.dp).width(200.dp).height(180.dp)) {
            TopWeeklyAppCard(topApp = topAppExample, modifier = Modifier.fillMaxSize(), onClick = {})
        }
    }
}

@Preview(showBackground = true, name = "Top Weekly App Card - No Data")
@Composable
fun TopWeeklyAppCardNoDataPreview() {
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp).width(200.dp).height(180.dp)) {
            TopWeeklyAppCard(topApp = null, modifier = Modifier.fillMaxSize(), onClick = {})
        }
    }
}
