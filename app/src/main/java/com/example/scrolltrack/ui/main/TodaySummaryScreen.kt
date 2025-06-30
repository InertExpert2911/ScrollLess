package com.example.scrolltrack.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.scrolltrack.ui.theme.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
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
    topWeeklyApp: AppUsageUiItem?,
    totalScrollUnits: Long,
    scrollDistanceMeters: String,
    totalUnlocks: Int,
    totalNotifications: Int,
    onNavigateToHistoricalUsage: () -> Unit,
    onNavigateToUnlocks: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    onThemePaletteChange: (AppTheme) -> Unit,
    currentThemePalette: AppTheme,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeSelectorDialog by remember { mutableStateOf(false) }

    if (showThemeSelectorDialog) {
        ThemeSelectorDialog(
            currentTheme = currentThemePalette,
            onThemeSelected = {
                onThemePaletteChange(it)
                showThemeSelectorDialog = false
            },
            onDismissRequest = { showThemeSelectorDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showThemeSelectorDialog = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Select Theme Palette")
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onDarkModeChange,
                    thumbContent = {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.Brightness2 else Icons.Filled.WbSunny,
                            contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                )
            }
        }

        Text(
            text = stringResource(id = R.string.greeting_manage_habits),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Permissions Section
        val permissionsNeeded = !isAccessibilityServiceEnabled || !isUsageStatsPermissionGranted || !isNotificationListenerEnabled
        AnimatedVisibility(visible = permissionsNeeded) {
            Column {
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Stats Grid
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Phone Usage Today",
                value = totalUsageTime,
                icon = Icons.Outlined.PhoneAndroid,
                subValue = "View Details",
                onCardClick = onNavigateToHistoricalUsage
            )
            TopAppCard(
                modifier = Modifier.weight(1f),
                topApp = topWeeklyApp,
                onClick = onNavigateToAppDetail
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Screen Unlocks",
                value = totalUnlocks.toString(),
                icon = Icons.Filled.LockOpen,
                onCardClick = onNavigateToUnlocks
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Notifications",
                value = totalNotifications.toString(),
                icon = Icons.Filled.Notifications,
                onCardClick = onNavigateToNotifications
            )
        }

        ScrollStatsCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            scrollDistanceMeters = scrollDistanceMeters,
            totalScrollUnits = totalScrollUnits,
            onClick = {
                val todayDate = DateUtil.getCurrentLocalDateString()
                navController.navigate(ScreenRoutes.ScrollDetailRoute.createRoute(todayDate))
            }
        )

        // New Theme Selector
        ThemePaletteSelector(
            currentPalette = currentThemePalette,
            onPaletteSelected = onThemePaletteChange
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ThemePaletteSelector(
    modifier: Modifier = Modifier,
    currentPalette: AppTheme,
    onPaletteSelected: (AppTheme) -> Unit
) {
    val palettes = AppTheme.entries
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Theme Palette",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(palettes) { palette ->
                val colors = themeColors(palette = palette)
                val isSelected = currentPalette == palette

                val border = if (isSelected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                }

                Card(
                    modifier = Modifier
                        .size(width = 100.dp, height = 60.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = border,
                    onClick = { onPaletteSelected(palette) }
                ) {
                    Row(Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).background(colors.primary))
                        Box(modifier = Modifier.weight(1f).background(colors.secondary))
                        Box(modifier = Modifier.weight(1f).background(colors.tertiary))
                    }
                }
            }
        }
    }
}

@Composable
private fun themeColors(palette: AppTheme): ThemeColors {
    return when (palette) {
        AppTheme.ClarityTeal -> ThemeColors(ClarityTeal.primaryLight, ClarityTeal.secondaryLight, ClarityTeal.tertiaryLight)
        AppTheme.FocusBlue -> ThemeColors(FocusBlue.primaryLight, FocusBlue.secondaryLight, FocusBlue.tertiaryLight)
        AppTheme.CalmLavender -> ThemeColors(CalmLavender.primaryLight, CalmLavender.secondaryLight, CalmLavender.tertiaryLight)
        AppTheme.VitalityOrange -> ThemeColors(VitalityOrange.primaryLight, VitalityOrange.secondaryLight, VitalityOrange.tertiaryLight)
        AppTheme.UpliftingGreen -> ThemeColors(UpliftingGreen.primaryLight, UpliftingGreen.secondaryLight, UpliftingGreen.tertiaryLight)
        AppTheme.OptimisticYellow -> ThemeColors(OptimisticYellow.primaryLight, OptimisticYellow.secondaryLight, OptimisticYellow.tertiaryLight)
    }
}

private data class ThemeColors(val primary: Color, val secondary: Color, val tertiary: Color)

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
            LazyColumn {
                items(AppTheme.entries) { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(theme.visibleName, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    subValue: String? = null,
    onCardClick: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier
            .height(160.dp)
            .clickable(enabled = onCardClick != null) { onCardClick?.invoke() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .padding(bottom = 8.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                modifier = Modifier.padding(bottom = 4.dp),
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            if (subValue != null) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TopAppCard(
    modifier: Modifier = Modifier,
    topApp: AppUsageUiItem?,
    onClick: (String) -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .height(160.dp)
            .clickable(enabled = topApp != null) { topApp?.packageName?.let(onClick) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (topApp != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = topApp.icon ?: R.mipmap.ic_launcher_round),
                    contentDescription = "${topApp.appName} icon",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .padding(bottom = 8.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = topApp.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${DateUtil.formatDuration(topApp.usageTimeMillis)} ${stringResource(id = R.string.suffix_last_7_days)}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = "No top app data",
                    modifier = Modifier
                        .size(32.dp)
                        .padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(id = R.string.card_title_top_weekly_app),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.text_no_data_yet),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScrollStatsCard(
    modifier: Modifier = Modifier,
    scrollDistanceMeters: String,
    totalScrollUnits: Long,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Scroll Stats",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 16.dp)
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.card_title_scroll_stats_today),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$totalScrollUnits units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = scrollDistanceMeters,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
