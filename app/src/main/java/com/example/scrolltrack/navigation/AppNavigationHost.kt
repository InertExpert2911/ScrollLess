package com.example.scrolltrack.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.scrolltrack.ui.detail.AppDetailScreen
import com.example.scrolltrack.ui.detail.ScrollDetailScreen
import com.example.scrolltrack.ui.historical.HistoricalUsageScreen
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.ui.main.TodaySummaryScreen
import com.example.scrolltrack.ui.unlocks.UnlocksScreen
import com.example.scrolltrack.ui.unlocks.UnlocksViewModel
import com.example.scrolltrack.ui.unlocks.UnlocksViewModelFactory
import com.example.scrolltrack.util.DateUtil

@Composable
fun AppNavigationHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    isAccessibilityEnabledState: Boolean,
    isUsageStatsGrantedState: Boolean,
    isNotificationListenerEnabledState: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit,
    onEnableNotificationListenerClick: () -> Unit
) {
    NavHost(navController = navController, startDestination = ScreenRoutes.TodaySummary.route) {
        composable(ScreenRoutes.TodaySummary.route) {
            val greeting by viewModel.greeting.collectAsStateWithLifecycle()
            val appScrollItems by viewModel.aggregatedScrollDataToday.collectAsStateWithLifecycle()
            val totalScrollUnits by viewModel.totalScrollToday.collectAsStateWithLifecycle()
            val totalUsageTimeFormatted by viewModel.totalPhoneUsageTodayFormatted.collectAsStateWithLifecycle()
            val totalUsageTimeMillis: Long by viewModel.totalPhoneUsageTodayMillis.collectAsStateWithLifecycle()
            val scrollDistance by viewModel.scrollDistanceTodayFormatted.collectAsStateWithLifecycle()
            val topWeeklyApp by viewModel.topUsedAppLast7Days.collectAsStateWithLifecycle()
            val selectedTheme by viewModel.selectedThemeVariant.collectAsStateWithLifecycle()
            val totalUnlocks by viewModel.totalUnlocksToday.collectAsStateWithLifecycle()
            val totalNotifications by viewModel.totalNotificationsToday.collectAsStateWithLifecycle()

            TodaySummaryScreen(
                navController = navController,
                greeting = greeting,
                isAccessibilityServiceEnabled = isAccessibilityEnabledState,
                onEnableAccessibilityClick = onEnableAccessibilityClick,
                isUsageStatsPermissionGranted = isUsageStatsGrantedState,
                onEnableUsageStatsClick = onEnableUsageStatsClick,
                isNotificationListenerEnabled = isNotificationListenerEnabledState,
                onEnableNotificationListenerClick = onEnableNotificationListenerClick,
                totalUsageTime = totalUsageTimeFormatted,
                totalUsageTimeMillis = totalUsageTimeMillis,
                topWeeklyApp = topWeeklyApp,
                totalScrollUnits = totalScrollUnits,
                scrollDistanceMeters = scrollDistance.first,
                totalUnlocks = totalUnlocks,
                totalNotifications = totalNotifications,
                // appScrollData = appScrollItems, // Removed: Not directly displayed on TodaySummaryScreen
                onNavigateToHistoricalUsage = {
                    viewModel.resetSelectedDateToToday()
                    navController.navigate(ScreenRoutes.HistoricalUsageRoute.route)
                },
                onNavigateToUnlocks = {
                    navController.navigate(ScreenRoutes.UnlocksRoute.route)
                },
                onNavigateToAppDetail = { packageName ->
                    navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(packageName))
                },
                onThemeChange = viewModel::updateThemeVariant,
                currentThemeVariant = selectedTheme
            )
        }
        composable(ScreenRoutes.UnlocksRoute.route) {
            val unlocksViewModel: UnlocksViewModel = viewModel(
                factory = UnlocksViewModelFactory(navController.context.applicationContext as Application)
            )
            UnlocksScreen(navController = navController, viewModel = unlocksViewModel)
        }
        composable(ScreenRoutes.HistoricalUsageRoute.route) {
            HistoricalUsageScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable(
            route = ScreenRoutes.AppDetailRoute.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
            if (packageName != null) {
                AppDetailScreen(
                    navController = navController,
                    viewModel = viewModel,
                    packageName = packageName
                )
            } else {
                // Handle error: navigate back or show an error message
                navController.popBackStack()
            }
        }
        composable(
            route = ScreenRoutes.ScrollDetailRoute.route,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) {
                backStackEntry ->
            val date = backStackEntry.arguments?.getString("date")
            if (date != null) {
                val selectedDateString by viewModel.selectedDateForScrollDetail.collectAsState()
                val scrollData by viewModel.aggregatedScrollDataForSelectedDate.collectAsState()

                LaunchedEffect(date) {
                    DateUtil.parseLocalDateString(date)?.time?.let {
                        viewModel.updateSelectedDateForScrollDetail(it)
                    }
                }

                ScrollDetailScreen(
                    navController = navController,
                    viewModel = viewModel,
                    selectedDateString = selectedDateString,
                    scrollData = scrollData,
                    onDateSelected = { viewModel.updateSelectedDateForScrollDetail(it) }
                )
            } else {
                Text("Error: Date not found for Scroll Detail.")
            }
        }
    }
} 