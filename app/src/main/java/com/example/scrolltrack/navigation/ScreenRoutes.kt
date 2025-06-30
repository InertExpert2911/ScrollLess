package com.example.scrolltrack.navigation

// Define graph routes
const val DASHBOARD_GRAPH_ROUTE = "dashboard_graph"
const val INSIGHTS_GRAPH_ROUTE = "insights_graph"
const val SETTINGS_GRAPH_ROUTE = "settings_graph"

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