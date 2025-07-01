package com.example.scrolltrack.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.navigation.AppNavigationBar
import com.example.scrolltrack.navigation.AppNavigationHost
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    isAccessibilityEnabledState: Boolean,
    isUsageStatsGrantedState: Boolean,
    isNotificationListenerEnabledState: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit,
    onEnableNotificationListenerClick: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Let the content handle insets
        bottomBar = {
            AppNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        AppNavigationHost(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            isAccessibilityEnabledState = isAccessibilityEnabledState,
            isUsageStatsGrantedState = isUsageStatsGrantedState,
            isNotificationListenerEnabledState = isNotificationListenerEnabledState,
            onEnableAccessibilityClick = onEnableAccessibilityClick,
            onEnableUsageStatsClick = onEnableUsageStatsClick,
            onEnableNotificationListenerClick = onEnableNotificationListenerClick
        )
    }
} 