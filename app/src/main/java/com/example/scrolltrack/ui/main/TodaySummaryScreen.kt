package com.example.scrolltrack.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.components.AppUsageCard
import com.example.scrolltrack.ui.components.DashboardCard
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.ui.theme.getThemeColors
import com.example.scrolltrack.util.DateUtil
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodaySummaryScreen(
    navController: NavHostController,
    greeting: String,
    isAccessibilityServiceEnabled: Boolean,
    isUsageStatsPermissionGranted: Boolean,
    isNotificationListenerEnabled: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit,
    onEnableNotificationListenerClick: () -> Unit,
    totalUsageTime: String,
    totalUsageTimeMillis: Long,
    todaysAppUsage: List<AppUsageUiItem>,
    topWeeklyApp: AppUsageUiItem?,
    totalScrollUnits: Long,
    scrollDistanceMeters: String,
    totalUnlocks: Int,
    totalNotifications: Int,
    screenTimeComparison: StatComparison?,
    unlocksComparison: StatComparison?,
    notificationsComparison: StatComparison?,
    scrollComparison: StatComparison?,
    onNavigateToHistoricalUsage: () -> Unit,
    onNavigateToUnlocks: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToScrollDetail: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    onSetLimit: (String, Int) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: String?,
    onSnackbarDismiss: () -> Unit
) {
    val state = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedAppForLimit by remember { mutableStateOf<AppUsageUiItem?>(null) }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = snackbarMessage,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed) {
                onSnackbarDismiss()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.padding(padding).fillMaxSize(),
            state = state,
            indicator = {
            // Correctly access distanceFraction from the 'state' variable
            val scale = state.distanceFraction.coerceIn(0f, 1.5f)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale
                    )
            ) {
                if (isRefreshing || state.distanceFraction > 0) {
                    LoadingIndicator()
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Header Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = DateUtil.getFormattedDate(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Permissions Section
            val permissionsNeeded = !isAccessibilityServiceEnabled || !isUsageStatsPermissionGranted || !isNotificationListenerEnabled
            if (permissionsNeeded) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                    ) {
                        if (!isAccessibilityServiceEnabled) {
                            PermissionRequestCard(
                                leadingIcon = { Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer) },
                                title = stringResource(id = R.string.permission_accessibility_title),
                                description = stringResource(id = R.string.permission_accessibility_description),
                                buttonText = stringResource(id = R.string.permission_button_open_settings),
                                onButtonClick = onEnableAccessibilityClick
                            )
                        }
                        if (!isUsageStatsPermissionGranted) {
                            PermissionRequestCard(
                                leadingIcon = { Icon(Icons.Filled.QueryStats, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer) },
                                title = stringResource(id = R.string.permission_usage_stats_title),
                                description = stringResource(id = R.string.permission_usage_stats_description),
                                buttonText = stringResource(id = R.string.permission_button_grant_access),
                                onButtonClick = onEnableUsageStatsClick
                            )
                        }
                        if (!isNotificationListenerEnabled) {
                            PermissionRequestCard(
                                leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer) },
                                title = stringResource(id = R.string.permission_notification_listener_title),
                                description = stringResource(id = R.string.permission_notification_listener_description),
                                buttonText = stringResource(id = R.string.permission_button_grant_access),
                                onButtonClick = onEnableNotificationListenerClick
                            )
                        }
                    }
                }
            }

            // Stats Grid
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Screen Time",
                        value = totalUsageTime,
                        unit = "",
                        comparison = screenTimeComparison,
                        showComparisonText = true,
                        onCardClick = onNavigateToHistoricalUsage
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Unlocks",
                        value = totalUnlocks.toString(),
                        unit = "times",
                        comparison = unlocksComparison,
                        showComparisonText = true,
                        onCardClick = onNavigateToUnlocks
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Notifications",
                        value = totalNotifications.toString(),
                        unit = "received",
                        comparison = notificationsComparison,
                        showComparisonText = true,
                        onCardClick = onNavigateToNotifications
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Scrolled",
                        value = scrollDistanceMeters.split(" ").firstOrNull() ?: "",
                        unit = "meters",
                        comparison = scrollComparison,
                        showComparisonText = true,
                        onCardClick = onNavigateToScrollDetail
                    )
                }
            }
            
            item {
                AppUsageCard(
                    apps = todaysAppUsage.take(3),
                    totalUsageTimeMillis = totalUsageTimeMillis,
                    onAppClick = onNavigateToAppDetail,
                    onSetLimitClick = { app ->
                        selectedAppForLimit = app
                        showBottomSheet = true
                    },
                    modifier = Modifier
                )
            }
        }
    }
    if (showBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
            var sliderPosition by remember { mutableFloatStateOf(30f) }

            Column(
                modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Set limit for ${selectedAppForLimit?.appName}",
                    style = MaterialTheme.typography.titleLarge
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 10f..180f, // 10 minutes to 3 hours
                    steps = 16 // Snap to 10-minute intervals
                )
                Text(
                    text = "${sliderPosition.toInt()} minutes",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = {
                        selectedAppForLimit?.let { app ->
                            onSetLimit(app.packageName, sliderPosition.toInt())
                        }
                        showBottomSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set Limit")
                }
            }
        }
    }
}
}

// ... (The rest of the file remains unchanged)
@Composable
fun ThemeSelectorDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select a Theme") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Theme Palette", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(AppTheme.entries) { theme ->
                        val isSelected = theme == currentTheme
                        val themeColors = getThemeColors(theme = theme, darkTheme = false)
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(themeColors.primary)
                                .border(BorderStroke(3.dp, borderColor), CircleShape)
                                .clickable { onThemeSelected(theme) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PermissionRequestCard(
    leadingIcon: @Composable () -> Unit,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.padding(end = 16.dp)) { leadingIcon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
