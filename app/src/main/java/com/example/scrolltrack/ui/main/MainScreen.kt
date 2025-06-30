package com.example.scrolltrack.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.navigation.AppNavigationBar
import com.example.scrolltrack.navigation.AppNavigationHost

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
        bottomBar = {
            AppNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        AppNavigationHost(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
            isAccessibilityEnabledState = isAccessibilityEnabledState,
            isUsageStatsGrantedState = isUsageStatsGrantedState,
            isNotificationListenerEnabledState = isNotificationListenerEnabledState,
            onEnableAccessibilityClick = onEnableAccessibilityClick,
            onEnableUsageStatsClick = onEnableUsageStatsClick,
            onEnableNotificationListenerClick = onEnableNotificationListenerClick
        )
    }
} 