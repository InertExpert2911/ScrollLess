package com.example.scrolltrack.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.scrolltrack.ui.detail.AppDetailScreen
import com.example.scrolltrack.ui.detail.AppDetailViewModel
import com.example.scrolltrack.ui.detail.ScrollDetailScreen
import com.example.scrolltrack.ui.detail.ScrollDetailViewModel
import com.example.scrolltrack.ui.historical.HistoricalUsageScreen
import com.example.scrolltrack.ui.historical.HistoricalViewModel
import com.example.scrolltrack.ui.main.TodaySummaryScreen
import com.example.scrolltrack.ui.main.TodaySummaryViewModel
import com.example.scrolltrack.ui.main.StatComparison
import com.example.scrolltrack.ui.insights.InsightsScreen
import com.example.scrolltrack.ui.unlocks.UnlocksScreen
import com.example.scrolltrack.ui.unlocks.UnlocksViewModel
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.ui.notifications.NotificationsViewModel
import com.example.scrolltrack.ui.notifications.NotificationsScreen
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import com.example.scrolltrack.ui.settings.SettingsViewModel
import com.example.scrolltrack.ui.settings.SettingsScreen
import com.example.scrolltrack.ui.settings.AppVisibilityScreen
import com.example.scrolltrack.ui.settings.CalibrationScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

@Composable
fun AppNavigationHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    isAccessibilityEnabledState: Boolean,
    isUsageStatsGrantedState: Boolean,
    isNotificationListenerEnabledState: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit,
    onEnableNotificationListenerClick: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = DASHBOARD_GRAPH_ROUTE,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        addDashboardGraph(
            navController = navController,
            isAccessibilityEnabledState = isAccessibilityEnabledState,
            isUsageStatsGrantedState = isUsageStatsGrantedState,
            isNotificationListenerEnabledState = isNotificationListenerEnabledState,
            onEnableAccessibilityClick = onEnableAccessibilityClick,
            onEnableUsageStatsClick = onEnableUsageStatsClick,
            onEnableNotificationListenerClick = onEnableNotificationListenerClick
        )
        addInsightsGraph(navController)
        addSettingsGraph(navController)
    }
}

private fun NavGraphBuilder.addDashboardGraph(
    navController: NavHostController,
    isAccessibilityEnabledState: Boolean,
    isUsageStatsGrantedState: Boolean,
    isNotificationListenerEnabledState: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit,
    onEnableNotificationListenerClick: () -> Unit
) {
    navigation(
        startDestination = ScreenRoutes.Dashboard.route,
        route = DASHBOARD_GRAPH_ROUTE
    ) {
        composable(ScreenRoutes.Dashboard.route) {
            val viewModel: TodaySummaryViewModel = hiltViewModel()
            val greeting by viewModel.greeting.collectAsStateWithLifecycle()
            val todaysAppUsage by viewModel.todaysAppUsageUiList.collectAsStateWithLifecycle()
            val aggregatedScrollData by viewModel.aggregatedScrollDataToday.collectAsStateWithLifecycle()
            val totalScrollUnits by viewModel.totalScrollToday.collectAsStateWithLifecycle()
            val totalUsageTimeFormatted by viewModel.totalPhoneUsageTodayFormatted.collectAsStateWithLifecycle()
            val totalUsageTimeMillis by viewModel.totalPhoneUsageTodayMillis.collectAsStateWithLifecycle()
            val scrollDistanceFormatted by viewModel.scrollDistanceTodayFormatted.collectAsStateWithLifecycle()
            val selectedTheme by viewModel.selectedThemePalette.collectAsStateWithLifecycle()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val totalUnlocks by viewModel.totalUnlocksToday.collectAsStateWithLifecycle()
            val totalNotifications by viewModel.totalNotificationsToday.collectAsStateWithLifecycle()
            val topWeeklyApp by viewModel.topWeeklyApp.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
            val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
            val screenTimeComparison by viewModel.screenTimeComparison.collectAsStateWithLifecycle()
            val unlocksComparison by viewModel.unlocksComparison.collectAsStateWithLifecycle()
            val notificationsComparison by viewModel.notificationsComparison.collectAsStateWithLifecycle()
            val scrollComparison by viewModel.scrollComparison.collectAsStateWithLifecycle()

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
                todaysAppUsage = todaysAppUsage,
                topWeeklyApp = topWeeklyApp,
                totalScrollUnits = totalScrollUnits,
                scrollDistanceMeters = "${scrollDistanceFormatted.first} ${scrollDistanceFormatted.second}",
                totalUnlocks = totalUnlocks,
                totalNotifications = totalNotifications,
                screenTimeComparison = screenTimeComparison,
                unlocksComparison = unlocksComparison,
                notificationsComparison = notificationsComparison,
                scrollComparison = scrollComparison,
                onNavigateToHistoricalUsage = { navController.navigate(ScreenRoutes.HistoricalUsageRoute.route) },
                onNavigateToUnlocks = { navController.navigate(ScreenRoutes.UnlocksRoute.route) },
                onNavigateToNotifications = { navController.navigate(ScreenRoutes.NotificationsRoute.route) },
                onNavigateToAppDetail = { packageName: String ->
                    navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(packageName))
                },
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.onPullToRefresh() },
                snackbarMessage = snackbarMessage,
                onSnackbarDismiss = { viewModel.dismissSnackbar() }
            )
        }
        composable(ScreenRoutes.NotificationsRoute.route) {
            val notificationsViewModel: NotificationsViewModel = hiltViewModel()
            NotificationsScreen(navController = navController, viewModel = notificationsViewModel)
        }
        composable(ScreenRoutes.UnlocksRoute.route) {
            val unlocksViewModel: UnlocksViewModel = hiltViewModel()
            UnlocksScreen(navController = navController, viewModel = unlocksViewModel)
        }
        composable(ScreenRoutes.HistoricalUsageRoute.route) {
            val historicalViewModel: HistoricalViewModel = hiltViewModel()
            HistoricalUsageScreen(
                navController = navController,
                viewModel = historicalViewModel
            )
        }
        composable(
            route = ScreenRoutes.AppDetailRoute.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
            if (packageName != null) {
                val appDetailViewModel: AppDetailViewModel = hiltViewModel()
                AppDetailScreen(
                    navController = navController,
                    viewModel = appDetailViewModel,
                    packageName = packageName
                )
            } else {
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
                val scrollDetailViewModel: ScrollDetailViewModel = hiltViewModel()
                val selectedDateString by scrollDetailViewModel.selectedDateForScrollDetail.collectAsStateWithLifecycle()
                val scrollData by scrollDetailViewModel.aggregatedScrollDataForSelectedDate.collectAsStateWithLifecycle()
                val selectableDates by scrollDetailViewModel.selectableDatesForScrollDetail.collectAsStateWithLifecycle()

                LaunchedEffect(date) {
                    DateUtil.parseLocalDate(date)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()?.let {
                        scrollDetailViewModel.updateSelectedDateForScrollDetail(it)
                    }
                }

                ScrollDetailScreen(
                    navController = navController,
                    viewModel = scrollDetailViewModel
                )
            } else {
                Text("Error: Date not found for Scroll Detail.")
            }
        }
    }
}

private fun NavGraphBuilder.addInsightsGraph(navController: NavHostController) {
    navigation(
        startDestination = ScreenRoutes.Insights.route,
        route = INSIGHTS_GRAPH_ROUTE
    ) {
        composable(ScreenRoutes.Insights.route) {
            InsightsScreen(navController = navController)
        }
        // Add other destinations for the insights tab here
    }
}

private fun NavGraphBuilder.addSettingsGraph(navController: NavHostController) {
    navigation(
        startDestination = ScreenRoutes.Settings.route,
        route = SETTINGS_GRAPH_ROUTE
    ) {
        composable(ScreenRoutes.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(navController = navController, viewModel = settingsViewModel)
        }
        composable(ScreenRoutes.AppVisibility.route) {
            AppVisibilityScreen(navController = navController)
        }
        composable(ScreenRoutes.CalibrationRoute.route) {
            CalibrationScreen(navController = navController)
        }
        // Add other destinations for the settings tab here
    }
}
