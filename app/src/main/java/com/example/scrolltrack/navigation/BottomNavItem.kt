package com.example.scrolltrack.navigation

import androidx.annotation.DrawableRes
import com.example.scrolltrack.R

sealed class BottomNavItem(var title: String, @DrawableRes var icon: Int, var screen_route: String) {
    object Locks : BottomNavItem("Unlocks", R.drawable.ic_lock_duotone, "unlocks")
    object Notifications : BottomNavItem("Notifications", R.drawable.ic_notificaiton_bell_duotone, "notifications")
    object Scroll : BottomNavItem("Scroll", R.drawable.ic_ruler_triangle_duotone, "scroll")
    object Limits : BottomNavItem("Limits", R.drawable.ic_lock_duotone, "limits")
}
