package com.example.scrolltrack.navigation

import androidx.annotation.RawRes
import com.example.scrolltrack.R

sealed class BottomNavItem(
    val route: String,
    val title: String,
    @RawRes val animResId: Int
) {
    object Dashboard : BottomNavItem(
        route = "dashboard",
        title = "Dashboard",
        animResId = R.raw.home_nav_bar_anim_icon
    )

    object Insights : BottomNavItem(
        route = "insights",
        title = "Insights",
        animResId = R.raw.bolt_nav_bar_anim_icon
    )

    object Settings : BottomNavItem(
        route = "settings",
        title = "Settings",
        animResId = R.raw.settings_nav_bar_anim_icon
    )
} 