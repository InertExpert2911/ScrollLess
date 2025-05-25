package com.example.scrolltrack.navigation

object ScreenRoutes {
    const val TODAY_SUMMARY = "today_summary"
    const val HISTORICAL_USAGE = "historical_usage"
    const val APP_DETAIL = "app_detail/{packageName}"

    fun appDetailRoute(packageName: String) = "app_detail/$packageName"
}