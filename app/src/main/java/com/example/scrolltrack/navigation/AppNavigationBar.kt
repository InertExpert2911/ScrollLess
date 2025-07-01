package com.example.scrolltrack.navigation

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

            val lottieProgress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(durationMillis = 400),
                label = "NavBarIconAnimation"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = !isSelected
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
                        progress = { lottieProgress },
                        dynamicProperties = dynamicProperties,
                        modifier = Modifier.size(28.dp)
                    )
                }
            )
        }
    }
} 