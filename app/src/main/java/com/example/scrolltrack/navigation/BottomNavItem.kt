package com.example.scrolltrack.navigation

import androidx.annotation.DrawableRes
import com.example.scrolltrack.R

sealed class BottomNavItem(
    val route: String,
    val title: String,
    @param:DrawableRes val icon: Int
) {
    object Locks : BottomNavItem(
        route = ScreenRoutes.UnlocksRoute.route,
        title = "Unlocks",
        icon = R.drawable.ic_lock_duotone
    )

    object Notifications : BottomNavItem(
        route = ScreenRoutes.NotificationsRoute.route,
        title = "Notifications",
        icon = R.drawable.ic_notificaiton_bell_duotone
    )

    object Scroll : BottomNavItem(
        route = ScreenRoutes.ScrollDetailRoute.route,
        title = "Scroll",
        icon = R.drawable.ic_ruler_triangle_duotone
    )
}
