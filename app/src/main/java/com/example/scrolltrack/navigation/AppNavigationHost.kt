package com.example.scrolltrack.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.scrolltrack.util.DateUtil

@Composable
fun AppNavigationHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    isAccessibilityEnabledState: Boolean,
    isUsageStatsGrantedState: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit
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

            TodaySummaryScreen(
                navController = navController,
                greeting = greeting,
                isAccessibilityServiceEnabled = isAccessibilityEnabledState,
                onEnableAccessibilityClick = onEnableAccessibilityClick,
                isUsageStatsPermissionGranted = isUsageStatsGrantedState,
                onEnableUsageStatsClick = onEnableUsageStatsClick,
                totalUsageTime = totalUsageTimeFormatted,
                totalUsageTimeMillis = totalUsageTimeMillis,
                topWeeklyApp = topWeeklyApp,
                totalScrollUnits = totalScrollUnits,
                scrollDistanceMeters = scrollDistance.first,
                appScrollData = appScrollItems,
                onNavigateToHistoricalUsage = {
                    viewModel.resetSelectedDateToToday()
                    navController.navigate(ScreenRoutes.HistoricalUsageRoute.route)
                },
                onNavigateToAppDetail = { packageName ->
                    navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(packageName))
                },
                onThemeChange = viewModel::updateThemeVariant,
                currentThemeVariant = selectedTheme
            )
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
                val appName by viewModel.appDetailAppName.collectAsStateWithLifecycle()
                val appIcon by viewModel.appDetailAppIcon.collectAsStateWithLifecycle()
                val chartData by viewModel.appDetailChartData.collectAsStateWithLifecycle()
                val currentPeriodType by viewModel.currentChartPeriodType.collectAsStateWithLifecycle()
                val focusedUsageDisplay by viewModel.appDetailFocusedUsageDisplay.collectAsStateWithLifecycle()
                val focusedPeriodDisplay by viewModel.appDetailFocusedPeriodDisplay.collectAsStateWithLifecycle()
                val comparisonText by viewModel.appDetailComparisonText.collectAsStateWithLifecycle()
                val comparisonIconType by viewModel.appDetailComparisonIconType.collectAsStateWithLifecycle()
                val comparisonColorType by viewModel.appDetailComparisonColorType.collectAsStateWithLifecycle()
                val weekNumberDisplay by viewModel.appDetailWeekNumberDisplay.collectAsStateWithLifecycle()
                val periodDescriptionText by viewModel.appDetailPeriodDescriptionText.collectAsStateWithLifecycle()
                val focusedScrollDisplay by viewModel.appDetailFocusedScrollDisplay.collectAsStateWithLifecycle()
                val canNavigateForward by viewModel.canNavigateChartForward.collectAsStateWithLifecycle()
                val focusedDate by viewModel.appDetailFocusedDate.collectAsStateWithLifecycle()

                LaunchedEffect(packageName) {
                    viewModel.loadAppDetailsInfo(packageName)
                }

                AppDetailScreen(
                    navController = navController,
                    appName = appName,
                    appIcon = appIcon,
                    chartData = chartData,
                    currentPeriodType = currentPeriodType,
                    focusedUsageDisplay = focusedUsageDisplay,
                    focusedPeriodDisplay = focusedPeriodDisplay,
                    comparisonText = comparisonText,
                    comparisonIconType = comparisonIconType,
                    comparisonColorType = comparisonColorType,
                    weekNumberDisplay = weekNumberDisplay,
                    periodDescriptionText = periodDescriptionText,
                    focusedScrollDisplay = focusedScrollDisplay,
                    canNavigateForward = canNavigateForward,
                    focusedDate = focusedDate,
                    onPeriodChange = { viewModel.changeChartPeriod(packageName, it) },
                    onNavigateDate = { viewModel.navigateChartDate(packageName, it) },
                    onSetFocusedDate = { viewModel.setFocusedDate(packageName, it) }
                )
            } else {
                // Handle error or navigate back
                Text("Error: Package name not found.")
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