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
// Removed CaveatFontFamily import as PixelifySans and Inter are now primary fonts
// import com.example.scrolltrack.ui.theme.CaveatFontFamily
// UsageStatus colors will now be derived from the theme or defined more semantically
// import com.example.scrolltrack.ui.theme.UsageStatusGreen
// import com.example.scrolltrack.ui.theme.UsageStatusOrange
// import com.example.scrolltrack.ui.theme.UsageStatusRed
import com.example.scrolltrack.util.DateUtil
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp

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
    onNavigateToAppDetail: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    currentThemeVariant: String,
    modifier: Modifier = Modifier
) {
    // Determine phone usage color based on theme attributes or semantic colors
    val phoneUsageColor = when {
        totalUsageTimeMillis <= 2.5 * 60 * 60 * 1000 -> MaterialTheme.colorScheme.tertiary // Example: Use tertiary for "good"
        totalUsageTimeMillis <= 5 * 60 * 60 * 1000 -> MaterialTheme.colorScheme.secondary // Example: Use secondary for "warning"
        else -> MaterialTheme.colorScheme.error // Use error color for "high usage"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp) // Consistent padding
    ) {
        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), // Spacing after header
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineLarge, // Using Pixelify Sans
                color = MaterialTheme.colorScheme.onBackground,
            )
            ThemeModeSwitch( // Assuming ThemeModeSwitch is updated or uses appropriate tints
                currentThemeVariant = currentThemeVariant,
                onThemeChange = onThemeChange
            )
        }

        // Sub-greeting or motivational text
        Text(
            text = stringResource(id = R.string.greeting_manage_habits),
            style = MaterialTheme.typography.titleMedium, // Using Inter
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp) // More spacing
        )

        // Permissions Section - Using a consistent Card style
        val permissionsNeeded = !isAccessibilityServiceEnabled || !isUsageStatsPermissionGranted || !isNotificationListenerEnabled
        AnimatedVisibility(visible = permissionsNeeded) {
            Column {
                if (!isAccessibilityServiceEnabled) {
                    PermissionRequestCard(
                        leadingIcon = { Icon(Icons.Filled.AccessibilityNew, contentDescription = "Accessibility Service Icon", tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        title = stringResource(id = R.string.permission_accessibility_title),
                        description = stringResource(id = R.string.permission_accessibility_description),
                        buttonText = stringResource(id = R.string.permission_button_open_settings),
                        onButtonClick = onEnableAccessibilityClick,
                        modifier = Modifier.padding(bottom = 12.dp) // Spacing between permission cards
                    )
                }
                if (!isUsageStatsPermissionGranted) {
                    PermissionRequestCard(
                        leadingIcon = { Icon(Icons.Filled.QueryStats, contentDescription = "Usage Access Icon", tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        title = stringResource(id = R.string.permission_usage_stats_title),
                        description = stringResource(id = R.string.permission_usage_stats_description),
                        buttonText = stringResource(id = R.string.permission_button_grant_access),
                        onButtonClick = onEnableUsageStatsClick,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                if (!isNotificationListenerEnabled) {
                    PermissionRequestCard(
                        leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Notification Access Icon", tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        title = stringResource(id = R.string.permission_notification_listener_title),
                        description = stringResource(id = R.string.permission_notification_listener_description),
                        buttonText = stringResource(id = R.string.permission_button_grant_access),
                        onButtonClick = onEnableNotificationListenerClick,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp)) // Space after permissions block
            }
        }

        // Main Stats Grid - Using a 2x2 or similar layout for key metrics
        // Row 1 of Stats
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp), // Spacing between stat rows
            horizontalArrangement = Arrangement.spacedBy(12.dp) // Spacing between cards in a row
        ) {
            PhoneUsageCard(
                modifier = Modifier.weight(1f),
                totalUsageTime = totalUsageTime,
                usageTimeColor = phoneUsageColor, // Already determined based on theme/logic
                onNavigateToHistoricalUsage = onNavigateToHistoricalUsage
            )
            TopWeeklyAppCard(
                modifier = Modifier.weight(1f),
                topApp = topWeeklyApp,
                onClick = onNavigateToAppDetail // Simplified lambda
            )
        }

        // Row 2 of Stats (Unlocks & Notifications)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Screen Unlocks",
                value = totalUnlocks.toString(),
                icon = Icons.Filled.LockOpen,
                // iconTint is now handled by InfoCard's contentColor or can be explicitly set to a theme color
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Notifications",
                value = totalNotifications.toString(),
                icon = Icons.Filled.Notifications,
            )
        }

        // Scroll Stats Card - Prominent and clear
        ScrollStatsCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), // Space before next section or end
            scrollDistanceMeters = scrollDistanceMeters,
            totalScrollUnits = totalScrollUnits,
            onClick = {
                val todayDate = DateUtil.getCurrentLocalDateString()
                navController.navigate(ScreenRoutes.ScrollDetailRoute.createRoute(todayDate))
            }
        )

        // Optional: Add a section for "Quick Actions" or "Tips" if desired later

        Spacer(modifier = Modifier.height(16.dp)) // Final spacing at the bottom
    }
}

// Updated PermissionRequestCard for new styling
@Composable
fun PermissionRequestCard(
    leadingIcon: (@Composable () -> Unit)?,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card( // Using standard Card for a flatter, more integrated look
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // Slightly less rounded
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer, // Using a theme color
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Subtle elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let {
                Box(modifier = Modifier.padding(end = 16.dp)) { it() }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall, // Adjusted style
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { // Aligned icon with text
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Information",
                        modifier = Modifier.size(16.dp).padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer // Themed tint
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button( // Using standard Button for primary action emphasis
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


// Updated PhoneUsageCard with new styling
@Composable
fun PhoneUsageCard(
    modifier: Modifier = Modifier,
    totalUsageTime: String,
    usageTimeColor: Color, // This color is passed based on logic (good, warning, error)
    onNavigateToHistoricalUsage: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxHeight() // Ensure it fills height in a row
            .clickable { onNavigateToHistoricalUsage() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Neutral background
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content
        ) {
            Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = "Phone Usage",
                tint = MaterialTheme.colorScheme.primary, // Use primary color for icon
                modifier = Modifier.size(36.dp).padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(id = R.string.card_title_phone_usage_today),
                style = MaterialTheme.typography.titleMedium, // Clearer title
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = totalUsageTime,
                style = MaterialTheme.typography.headlineSmall.copy( // Slightly smaller headline
                    fontWeight = FontWeight.Bold,
                    color = usageTimeColor // Dynamic color based on usage
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(id = R.string.card_button_view_details),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), // Bolder label
                color = MaterialTheme.colorScheme.primary // Action text in primary color
            )
        }
    }
}

// Updated TopWeeklyAppCard with new styling
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
                topApp?.packageName?.let(onClick)
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(), // Fill available space
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content
        ) {
            if (topApp != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = topApp.icon ?: R.mipmap.ic_launcher_round),
                    contentDescription = "${topApp.appName} icon",
                    modifier = Modifier
                        .size(48.dp) // Larger icon
                        .clip(CircleShape)
                        .padding(bottom = 12.dp), // More space below icon
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = topApp.appName,
                    style = MaterialTheme.typography.titleSmall, // Adjusted style
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "${DateUtil.formatDuration(topApp.usageTimeMillis)} ${stringResource(id = R.string.suffix_last_7_days)}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = "No top app data",
                    modifier = Modifier.size(40.dp).padding(bottom = 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) // Slightly faded
                )
                Text(
                    text = stringResource(id = R.string.card_title_top_weekly_app),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = stringResource(id = R.string.text_no_data_yet),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}


// Updated ScrollStatsCard with new styling
@Composable
fun ScrollStatsCard(
    modifier: Modifier = Modifier,
    scrollDistanceMeters: String,
    totalScrollUnits: Long,
    onClick: () -> Unit
) {
    Card( // Changed from ElevatedCard for consistency
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Use a consistent container
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row( // Using Row for better horizontal layout of icon and text
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween // Space out elements
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Scroll Stats",
                    tint = MaterialTheme.colorScheme.primary, // Primary color for icon
                    modifier = Modifier.size(40.dp).padding(end = 12.dp) // Larger icon, padding
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.card_title_scroll_stats_today),
                        style = MaterialTheme.typography.titleMedium, // Clear title
                    )
                    Text( // Sub-text for units if desired
                        text = "$totalScrollUnits units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Text( // Distance on the right, more prominent
                text = scrollDistanceMeters,
                style = MaterialTheme.typography.headlineSmall.copy( // Prominent display
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary // Primary color for value
                ),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


// AppScrollItemEntry - Assuming this is for a list elsewhere, keeping its style for now
// If it appears on the dashboard, it would need similar styling updates.
@Composable
fun AppScrollItemEntry(
    appData: AppScrollUiItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp), // Add some vertical padding if it's in a list
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface, // Standard surface for list items
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Subtle elevation for list items
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp), // Standard padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = appData.icon ?: R.mipmap.ic_launcher_round),
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
                    style = MaterialTheme.typography.titleSmall, // Consistent typography
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appData.packageName, // If useful, otherwise can be removed for cleaner UI
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${appData.totalScroll} units",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.primary // Highlight primary stat
            )
        }
    }
}


// Updated ThemeModeSwitch
@Composable
fun ThemeModeSwitch(
    currentThemeVariant: String,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = currentThemeVariant != "light" // Assumes "light" is the only light variant

    IconButton( // Using IconButton for better touch target and visual integration
        onClick = {
            val newTheme = if (isDarkMode) "light" else "oled_dark" // Toggle between "light" and "oled_dark"
            onThemeChange(newTheme)
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isDarkMode) Icons.Filled.Brightness2 else Icons.Filled.WbSunny,
            contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
            tint = MaterialTheme.colorScheme.primary // Use primary color for the icon
        )
    }
}


// Updated InfoCard
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    // iconTint is removed, tint will be MaterialTheme.colorScheme.primary by default for the icon
) {
    Card(
        modifier = modifier.fillMaxHeight(), // Ensure it fills height in a row
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary, // Icon tinted with primary color
                modifier = Modifier.size(36.dp).padding(bottom = 8.dp) // Larger icon
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall, // Clear title
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
