package com.example.scrolltrack.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import com.example.scrolltrack.R

private sealed class NavigationItem(val route: String, val animResId: Int) {
    object Dashboard : NavigationItem(ScreenRoutes.Dashboard.route, R.raw.home_nav_bar_anim_icon)
    object Insights : NavigationItem(ScreenRoutes.Insights.route, R.raw.bolt_nav_bar_anim_icon)
    object Settings : NavigationItem(ScreenRoutes.Settings.route, R.raw.settings_nav_bar_anim_icon)
    object Limits : NavigationItem(LIMITS_GRAPH_ROUTE, R.raw.bolt_nav_bar_anim_icon) // Re-using insights icon for now
}

@Composable
fun AppNavigationBar(navController: NavController) {
    val items = listOf(
        NavigationItem.Dashboard,
        NavigationItem.Insights,
        NavigationItem.Limits,
        NavigationItem.Settings
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(item.animResId))

            val progress by animateLottieCompositionAsState(
                composition = composition,
                isPlaying = isSelected,
                restartOnPlay = true,
                speed = 0.8f,
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        // When re-selecting the current tab, we want to pop the back stack to the start destination.
                        // This gives the "reset" behavior.
                        navController.popBackStack(item.route, inclusive = false)
                    } else {
                        // When selecting a new tab, we use the standard navigation pattern
                        // which saves and restores the back stack.
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    val dynamicProperties = rememberLottieDynamicProperties(
                        rememberLottieDynamicProperty(
                            property = LottieProperty.STROKE_COLOR,
                            value = color.toArgb(),
                            keyPath = arrayOf("**")
                        ),
                        rememberLottieDynamicProperty(
                            property = LottieProperty.COLOR,
                            value = color.toArgb(),
                            keyPath = arrayOf("**")
                        )
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { if (isSelected) progress else 0f },
                        dynamicProperties = dynamicProperties,
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
        }
    }
}
