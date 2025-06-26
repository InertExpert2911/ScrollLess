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
import androidx.compose.material3.*
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
import com.example.scrolltrack.ui.theme.CaveatFontFamily
import com.example.scrolltrack.ui.theme.UsageStatusGreen
import com.example.scrolltrack.ui.theme.UsageStatusOrange
import com.example.scrolltrack.ui.theme.UsageStatusRed
import com.example.scrolltrack.util.DateUtil
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySummaryScreen(
    navController: NavHostController,
    greeting: String,
    isAccessibilityServiceEnabled: Boolean,
    isUsageStatsPermissionGranted: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    onEnableUsageStatsClick: () -> Unit,
    totalUsageTime: String,
    totalUsageTimeMillis: Long,
    topWeeklyApp: AppUsageUiItem?,
    totalScrollUnits: Long,
    scrollDistanceMeters: String,
    onNavigateToHistoricalUsage: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    currentThemeVariant: String,
    modifier: Modifier = Modifier
) {
    val phoneUsageColor = when {
        totalUsageTimeMillis <= 2.5 * 60 * 60 * 1000 -> UsageStatusGreen
        totalUsageTimeMillis <= 5 * 60 * 60 * 1000 -> UsageStatusOrange
        else -> UsageStatusRed
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = CaveatFontFamily),
                color = MaterialTheme.colorScheme.onBackground,
            )
            ThemeModeSwitch(
                currentThemeVariant = currentThemeVariant,
                onThemeChange = onThemeChange
            )
        }

        Text(
            text = stringResource(id = R.string.greeting_manage_habits), // Replaced
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )

        AnimatedVisibility(
            visible = !isAccessibilityServiceEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PermissionRequestCard(
                leadingIcon = { Icon(Icons.Filled.AccessibilityNew, contentDescription = "Accessibility Service Icon", modifier = Modifier.size(28.dp)) },
                title = stringResource(id = R.string.permission_accessibility_title), // Replaced
                description = stringResource(id = R.string.permission_accessibility_description), // Replaced
                buttonText = stringResource(id = R.string.permission_button_open_settings), // Replaced
                onButtonClick = onEnableAccessibilityClick,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = !isUsageStatsPermissionGranted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PermissionRequestCard(
                leadingIcon = { Icon(Icons.Filled.QueryStats, contentDescription = "Usage Access Icon", modifier = Modifier.size(28.dp)) },
                title = stringResource(id = R.string.permission_usage_stats_title), // Replaced
                description = stringResource(id = R.string.permission_usage_stats_description), // Replaced
                buttonText = stringResource(id = R.string.permission_button_grant_access), // Replaced
                onButtonClick = onEnableUsageStatsClick,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        if (!isAccessibilityServiceEnabled || !isUsageStatsPermissionGranted) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhoneUsageCard(
                modifier = Modifier.weight(1f),
                totalUsageTime = totalUsageTime,
                usageTimeColor = phoneUsageColor,
                onNavigateToHistoricalUsage = onNavigateToHistoricalUsage
            )
            TopWeeklyAppCard(
                modifier = Modifier.weight(1f),
                topApp = topWeeklyApp,
                onClick = { packageName ->
                    onNavigateToAppDetail(packageName)
                }
            )
        }

        ScrollStatsCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            scrollDistanceMeters = scrollDistanceMeters,
            totalScrollUnits = totalScrollUnits,
            onClick = {
                val todayDate = DateUtil.getCurrentLocalDateString()
                navController.navigate(ScreenRoutes.ScrollDetailRoute.createRoute(todayDate))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PermissionRequestCard(
    leadingIcon: (@Composable () -> Unit)?,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.padding(end = 16.dp)) {
                    leadingIcon()
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Information",
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp).size(16.dp),
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onButtonClick,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun PhoneUsageCard(
    modifier: Modifier = Modifier,
    totalUsageTime: String,
    usageTimeColor: Color,
    onNavigateToHistoricalUsage: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onNavigateToHistoricalUsage() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.PhoneAndroid, contentDescription = "Phone Usage", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
            Text(
                text = stringResource(id = R.string.card_title_phone_usage_today), // Replaced
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = totalUsageTime,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = usageTimeColor
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.card_button_view_details), // Replaced
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun TopWeeklyAppCard(
    modifier: Modifier = Modifier,
    topApp: AppUsageUiItem?,
    onClick: (String) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = topApp != null) {
                topApp?.packageName?.let { onClick(it) }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    painter = rememberAsyncImagePainter(
                        model = topApp.icon ?: R.mipmap.ic_launcher_round
                    ),
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
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${DateUtil.formatDuration(topApp.usageTimeMillis)} ${stringResource(id = R.string.suffix_last_7_days)}", // Replaced
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = "No top app data",
                    modifier = Modifier.size(36.dp).padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(id = R.string.card_title_top_weekly_app), // Replaced
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.text_no_data_yet), // Replaced
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Scroll Stats", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
            Text(
                text = stringResource(id = R.string.card_title_scroll_stats_today), // Replaced
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = scrollDistanceMeters,
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$totalScrollUnits units",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AppScrollItemEntry(
    appData: AppScrollUiItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = appData.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "${appData.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appData.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = appData.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${appData.totalScroll} units",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ThemeModeSwitch(
    currentThemeVariant: String,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = currentThemeVariant != "light"

    Switch(
        modifier = modifier,
        checked = isDarkMode,
        onCheckedChange = {
            val newTheme = if (it) "oled_dark" else "light"
            onThemeChange(newTheme)
        },
        thumbContent = {
            Icon(
                imageVector = if (isDarkMode) Icons.Filled.Brightness2 else Icons.Filled.WbSunny,
                contentDescription = if (isDarkMode) "Dark Mode" else "Light Mode",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}
