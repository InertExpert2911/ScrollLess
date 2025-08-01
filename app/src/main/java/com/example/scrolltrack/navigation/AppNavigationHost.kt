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
import com.example.scrolltrack.ui.phoneusage.PhoneUsageScreen
import com.example.scrolltrack.ui.phoneusage.PhoneUsageViewModel
import com.example.scrolltrack.ui.main.TodaySummaryScreen
import com.example.scrolltrack.ui.main.TodaySummaryViewModel
import com.example.scrolltrack.ui.main.StatComparison
import com.example.scrolltrack.ui.insights.InsightsScreen
import com.example.scrolltrack.ui.unlocks.UnlocksScreen
import com.example.scrolltrack.ui.unlocks.UnlocksViewModel
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.ui.notifications.NotificationsViewModel
import com.example.scrolltrack.ui.notifications.NotificationsScreen
import com.example.scrolltrack.ui.dashboard.DashboardTabs
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
import com.example.scrolltrack.ui.limit.CreateEditLimitGroupScreen
import com.example.scrolltrack.ui.limit.LimitsScreen
import com.example.scrolltrack.ui.limit.LimitsViewModel

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
        addLimitsGraph(navController)
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
            val uiState by viewModel.todaySummaryUiState.collectAsStateWithLifecycle()
            val limitsViewModel: LimitsViewModel = hiltViewModel()

            TodaySummaryScreen(
                navController = navController,
                greeting = uiState.greeting,
                isAccessibilityServiceEnabled = isAccessibilityEnabledState,
                onEnableAccessibilityClick = onEnableAccessibilityClick,
                isUsageStatsPermissionGranted = isUsageStatsGrantedState,
                onEnableUsageStatsClick = onEnableUsageStatsClick,
                isNotificationListenerEnabled = isNotificationListenerEnabledState,
                onEnableNotificationListenerClick = onEnableNotificationListenerClick,
                totalUsageTime = uiState.totalUsageTimeFormatted,
                totalUsageTimeMillis = uiState.totalUsageTimeMillis,
                todaysAppUsage = uiState.todaysAppUsageUiList,
                topWeeklyApp = uiState.topWeeklyApp,
                totalScrollUnits = uiState.totalScrollToday,
                scrollDistanceMeters = "${uiState.scrollDistanceTodayFormatted.first} ${uiState.scrollDistanceTodayFormatted.second}",
                totalUnlocks = uiState.totalUnlocksToday,
                totalNotifications = uiState.totalNotificationsToday,
                screenTimeComparison = uiState.screenTimeComparison,
                unlocksComparison = uiState.unlocksComparison,
                notificationsComparison = uiState.notificationsComparison,
                scrollComparison = uiState.scrollComparison,
                onNavigateToHistoricalUsage = { navController.navigate(ScreenRoutes.DashboardTabs.createRoute("PhoneUsage")) },
                onNavigateToUnlocks = { navController.navigate(ScreenRoutes.DashboardTabs.createRoute("Unlocks")) },
                onNavigateToNotifications = { navController.navigate(ScreenRoutes.DashboardTabs.createRoute("Notifications")) },
                onNavigateToScrollDetail = { navController.navigate(ScreenRoutes.DashboardTabs.createRoute("ScrollDistance")) },
                onNavigateToAppDetail = { packageName: String ->
                    navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(packageName))
                },
                onNavigateToLimits = { navController.navigate(LIMITS_GRAPH_ROUTE) },
                onSetLimit = viewModel::onSetLimit,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onPullToRefresh() },
                snackbarMessage = uiState.snackbarMessage,
                onSnackbarDismiss = { viewModel.dismissSnackbar() },
                limitsCount = uiState.limitsCount,
                setLimitSheetState = uiState.setLimitSheetState,
                onQuickLimitIconClicked = viewModel::onQuickLimitIconClicked,
                onDismissSetLimitSheet = viewModel::dismissSetLimitSheet,
                onDeleteLimit = viewModel::onDeleteLimit
            )
        }
        composable(
            route = ScreenRoutes.DashboardTabs.route,
            arguments = listOf(navArgument("tab") { type = NavType.StringType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab")
            val todaySummaryViewModel: TodaySummaryViewModel = hiltViewModel()
            DashboardTabs(
                navController = navController,
                selectedTab = tab,
                onSetLimit = todaySummaryViewModel::onSetLimit,
                onDeleteLimit = todaySummaryViewModel::onDeleteLimit,
                onQuickLimitIconClicked = todaySummaryViewModel::onQuickLimitIconClicked
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
                    packageName = packageName,
                    onSetLimit = { _, _ -> },
                    onDeleteLimit = { }
                )
            } else {
                navController.popBackStack()
            }
        }
        composable(
            route = ScreenRoutes.ScrollDetailRoute.route,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date")
            if (date != null) {
                val scrollDetailViewModel: ScrollDetailViewModel = hiltViewModel()
                LaunchedEffect(date) {
                    DateUtil.parseLocalDate(date)?.let {
                        scrollDetailViewModel.onDateSelected(it)
                    }
                }
                ScrollDetailScreen(
                    navController = navController,
                    viewModel = scrollDetailViewModel,
                    onSetLimit = { _, _ -> },
                    onDeleteLimit = { },
                    onQuickLimitIconClicked = { _, _ -> }
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

private fun NavGraphBuilder.addLimitsGraph(navController: NavHostController) {
    navigation(
        startDestination = ScreenRoutes.Limits.route,
        route = LIMITS_GRAPH_ROUTE
    ) {
        composable(ScreenRoutes.Limits.route) {
            LimitsScreen(
                viewModel = hiltViewModel(),
                navController = navController
            )
        }
        composable(
            route = ScreenRoutes.CreateEditLimitGroupRoute.route,
            arguments = listOf(navArgument("groupId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getLong("groupId")
            CreateEditLimitGroupScreen(
                navController = navController,
                groupId = if (groupId == -1L) null else groupId
            )
        }
    }
}

