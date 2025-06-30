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
import com.airbnb.lottie.model.KeyPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

@Composable
fun AppNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Insights,
        BottomNavItem.Settings
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(item.animResId))
            var isPlaying by remember { mutableStateOf(false) }

            // Trigger the animation and let it run once to completion.
            val progress by animateLottieCompositionAsState(
                composition = composition,
                isPlaying = isPlaying,
                iterations = 1
            )

            // When the animation is complete, reset the trigger.
            LaunchedEffect(progress) {
                if (progress == 1f) {
                    isPlaying = false
                }
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    isPlaying = true
                    navController.navigate(item.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item.
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item.
                        restoreState = true
                    }
                },
                label = { Text(item.title) },
                icon = {
                    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    val dynamicProperties = rememberLottieDynamicProperties(
                        rememberLottieDynamicProperty(
                            property = LottieProperty.COLOR_FILTER,
                            value = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN),
                            keyPath = arrayOf("**")
                        )
                    )

                    LottieAnimation(
                        composition = composition,
                        // This lambda controls the exact frame of the animation.
                        // - If isPlaying, it follows the animation's progress.
                        // - If not playing but is selected, it stays at the end frame (1f).
                        // - If not playing and not selected, it stays at the start frame (0f).
                        progress = {
                            if (isPlaying) progress
                            else if (isSelected) 1f
                            else 0f
                        },
                        dynamicProperties = dynamicProperties,
                        modifier = Modifier.size(28.dp)
                    )
                }
            )
        }
    }
} 