package com.example.scrolltrack.navigation

// Make ScreenRoutes a sealed class
sealed class ScreenRoutes(val route: String) {
    object TodaySummary : ScreenRoutes("today_summary")
    object HistoricalUsageRoute : ScreenRoutes("historical_usage")
    object UnlocksRoute : ScreenRoutes("unlocks_route")
    object AppDetailRoute : ScreenRoutes("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    object ScrollDetailRoute : ScreenRoutes("scroll_detail/{date}") {
        fun createRoute(date: String) = "scroll_detail/$date"
    }

    // Companion object for constants for simpler access in NavHost if preferred,
    // though direct use of object.route is also fine.
    companion object {
        const val TODAY_SUMMARY_ROUTE = "today_summary"
        const val HISTORICAL_USAGE_ROUTE = "historical_usage"
        const val APP_DETAIL_ROUTE_PATTERN = "app_detail/{packageName}"
        const val SCROLL_DETAIL_ROUTE_PATTERN = "scroll_detail/{date}"
    }
}