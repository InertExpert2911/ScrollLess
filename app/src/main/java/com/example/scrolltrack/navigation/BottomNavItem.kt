package com.example.scrolltrack.navigation

import androidx.annotation.RawRes
import com.example.scrolltrack.R

sealed class BottomNavItem(
    val route: String,
    val title: String,
    @RawRes val animResId: Int
) {
    object Dashboard : BottomNavItem(
        route = DASHBOARD_GRAPH_ROUTE,
        title = "Dashboard",
        animResId = R.raw.home_nav_bar_anim_icon
    )

    object Insights : BottomNavItem(
        route = INSIGHTS_GRAPH_ROUTE,
        title = "Insights",
        animResId = R.raw.bolt_nav_bar_anim_icon
    )

    object Settings : BottomNavItem(
        route = SETTINGS_GRAPH_ROUTE,
        title = "Settings",
        animResId = R.raw.settings_nav_bar_anim_icon
    )
} 