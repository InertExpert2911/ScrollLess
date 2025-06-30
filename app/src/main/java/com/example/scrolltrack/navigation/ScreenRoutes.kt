package com.example.scrolltrack.navigation

// Make ScreenRoutes a sealed class
sealed class ScreenRoutes(val route: String) {
    // --- Main destinations for the Bottom Navigation Bar ---
    object Dashboard : ScreenRoutes("dashboard")
    object Insights : ScreenRoutes("insights")
    object Settings : ScreenRoutes("settings")

    // --- Other screens navigated to from the main destinations ---
    object HistoricalUsageRoute : ScreenRoutes("historical_usage")
    object UnlocksRoute : ScreenRoutes("unlocks_route")
    object NotificationsRoute : ScreenRoutes("notifications_route")
    object AppDetailRoute : ScreenRoutes("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    object ScrollDetailRoute : ScreenRoutes("scroll_detail/{date}") {
        fun createRoute(date: String) = "scroll_detail/$date"
    }
}