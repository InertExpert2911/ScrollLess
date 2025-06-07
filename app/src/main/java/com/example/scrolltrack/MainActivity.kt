package com.example.scrolltrack

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
// import android.graphics.drawable.Drawable // Retaining for now, might be used by App*UiItem previews or direct composable later
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
// import androidx.compose.foundation.shape.RoundedCornerShape // Not directly used by name in this file after changes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Used in PermissionRow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
// import androidx.lifecycle.compose.collectAsStateWithLifecycle // Duplicate import
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.ScrollDataRepositoryImpl
import com.example.scrolltrack.ui.main.AppScrollUiItem
import com.example.scrolltrack.ui.main.AppUsageUiItem
import com.example.scrolltrack.db.* // Wildcard for db package
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.detail.AppDetailScreen
import com.example.scrolltrack.ui.detail.ScrollDetailScreen
import com.example.scrolltrack.ui.historical.HistoricalUsageScreen
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.ui.main.MainViewModelFactory
import com.example.scrolltrack.ui.theme.* // Wildcard for theme package
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import java.util.*

// Constants for theme variants to be used by the Switch logic
// Moved to top level for accessibility by ThemeModeSwitch
private const val THEME_LIGHT = "light"
private const val THEME_OLED_DARK = "oled_dark"

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var isAccessibilityEnabledState by mutableStateOf(false)
    private var isUsageStatsGrantedState by mutableStateOf(false)
    private lateinit var viewModel: MainViewModel
    private lateinit var appPrefs: SharedPreferences

    private companion object {
        const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
        const val KEY_HISTORICAL_BACKFILL_DONE = "historical_backfill_done"
        const val KEY_SELECTED_THEME = "selected_theme_variant"
        const val DEFAULT_THEME = THEME_OLED_DARK
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPrefs = getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)

        val viewModelFactory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        // Request notification permission if on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        setContent {
            val selectedTheme by viewModel.selectedThemeVariant.collectAsStateWithLifecycle()
            ScrollTrackTheme(themeVariant = selectedTheme, dynamicColor = false) {
                val navController = rememberNavController()
                AppNavigationHost(
                    navController = navController,
                    viewModel = viewModel,
                    isAccessibilityEnabledState = isAccessibilityEnabledState,
                    isUsageStatsGrantedState = isUsageStatsGrantedState,
                    onEnableAccessibilityClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onEnableUsageStatsClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening usage access settings", e)
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityServiceStatus()
        updateUsageStatsPermissionStatus()
        // Check notification permission status on resume as well, in case it changed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Optionally, update UI or state based on notification permission status here
            // For now, just logging if it's granted or not.
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Notification permission is granted.")
            } else {
                Log.i(TAG, "Notification permission is NOT granted.")
            }
        }
    }

    private fun updateAccessibilityServiceStatus() {
        isAccessibilityEnabledState = isAccessibilityServiceEnabled(this, ScrollTrackService::class.java)
    }

    private fun updateUsageStatsPermissionStatus() {
        isUsageStatsGrantedState = hasUsageStatsPermission(this)
        if (isUsageStatsGrantedState) {
            Log.i(TAG, "Usage stats permission is granted.")
            if (::viewModel.isInitialized) {
                viewModel.refreshDataForToday()
                val backfillDone = appPrefs.getBoolean(KEY_HISTORICAL_BACKFILL_DONE, false)
                if (!backfillDone) {
                    Log.i(TAG, "Performing initial historical data backfill.")
                    viewModel.performHistoricalUsageDataBackfill(30)
                    appPrefs.edit().putBoolean(KEY_HISTORICAL_BACKFILL_DONE, true).apply()
                }
            }
        } else {
            Log.i(TAG, "Usage stats permission is NOT granted.")
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (accessibilityEnabled == 0) return false
        val settingValue = Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (settingValue != null) {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(settingValue)
            val serviceSimpleName = serviceClass.simpleName ?: return false // Handle null simpleName gracefully
            val serviceNameToCheckShort = "." + serviceSimpleName
            val serviceNameToCheckFull = context.packageName + "/" + serviceClass.name
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(serviceNameToCheckFull, ignoreCase = true) ||
                    componentName.equals(context.packageName + serviceNameToCheckShort, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(APP_OPS_SERVICE) as? AppOpsManager
        return if (appOpsManager != null) {
            val mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } else false
    }

    // --- Notification Permission Handling (Android 13+) ---
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it.
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            } else {
                // Permission has already been granted
                Log.i(TAG, "Notification permission already granted.")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Notification Permission Granted
                    Log.i(TAG, "Notification permission granted by user.")
                } else {
                    // Notification Permission Denied
                    Log.w(TAG, "Notification permission denied by user.")
                    // Optionally, show a rationale or guide the user to settings
                }
                return
            }
            // Handle other permission requests if any
        }
    }
    // --- End Notification Permission Handling ---
}

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

            TodaySummaryScreen(
                navController = navController,
                viewModel = viewModel,
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
                }
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
                AppDetailScreen(
                    navController = navController,
                    viewModel = viewModel,
                    packageName = packageName
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
                ScrollDetailScreen(
                    navController = navController,
                    initialSelectedDate = date
                )
            } else {
                Text("Error: Date not found for Scroll Detail.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySummaryScreen(
    navController: NavHostController,
    viewModel: MainViewModel,
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
    appScrollData: List<AppScrollUiItem>,
    onNavigateToHistoricalUsage: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
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
                currentThemeVariant = viewModel.selectedThemeVariant.collectAsStateWithLifecycle().value,
                onThemeChange = viewModel::updateThemeVariant
            )
        }

        Text(
            text = "Manage your daily digital habits.",
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
                title = "Enable Accessibility Service",
                description = "Required to track scrolls & calculate distance in other apps.",
                buttonText = "Open Settings",
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
                title = "Grant Usage Access",
                description = "Needed to show your phone usage time and app breakdown.",
                buttonText = "Grant Access",
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
                text = "PHONE USAGE (Today)",
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
                text = "View Details",
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
                    text = "${topApp.formattedUsageTime} (Last 7 Days)",
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
                    text = "Top Weekly App",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                     textAlign = TextAlign.Center
                )
                 Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No data yet",
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
                text = "SCROLL STATS (Today)",
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

@Preview(showBackground = true, name = "Today Summary - Permissions Needed")
@Composable
fun TodaySummaryScreenPermissionsNeededPreview() {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    // Create dummy DAOs for preview
    val dummyScrollSessionDao = object : ScrollSessionDao {
        override fun getAllSessionsFlow(): Flow<List<ScrollSessionRecord>> = flowOf(emptyList())
        override suspend fun insertSession(session: ScrollSessionRecord) {}
        override fun getTotalScrollForDate(dateString: String): Flow<Long?> = flowOf(0L)
        override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> = flowOf(emptyList())
        override suspend fun getAllSessions(): List<ScrollSessionRecord> = emptyList()
        override suspend fun getTotalScrollForAppOnDate(pkgName: String, dateString: String): Long? = null
        override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> = emptyList<AppScrollDataPerDate>()
    }
    val dummyDailyAppUsageDao = object : DailyAppUsageDao {
        override fun getUsageForDate(dateString: String): Flow<List<DailyAppUsageRecord>> = flowOf(emptyList())
        override fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>> = flowOf(emptyList())
        override suspend fun insertOrUpdateUsage(dailyAppUsageRecord: DailyAppUsageRecord) {}
        override suspend fun insertAllUsage(records: List<DailyAppUsageRecord>) {}
        override suspend fun clearAllUsageData() {}
        override suspend fun getSpecificAppUsageForDate(packageName: String, dateString: String): DailyAppUsageRecord? = null
        override fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?> = flowOf(0L)
        override suspend fun deleteOldUsageData(timestampMillis: Long): Int = 0
        override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> = emptyList()
        override suspend fun deleteUsageForDate(dateString: String) {}
        override suspend fun getUsageCountForDateString(dateString: String): Int = 0
    }

    val dummyRawAppEventDao = object : com.example.scrolltrack.db.RawAppEventDao {
        override suspend fun insertEvent(event: com.example.scrolltrack.db.RawAppEvent) {}
        override suspend fun insertEvents(events: List<com.example.scrolltrack.db.RawAppEvent>) {}
        override fun getEventsForDate(dateString: String): Flow<List<com.example.scrolltrack.db.RawAppEvent>> = flowOf(emptyList())
        override suspend fun getEventsForPeriod(startTime: Long, endTime: Long): List<com.example.scrolltrack.db.RawAppEvent> = emptyList()
        override suspend fun getEventsForPackageNameInPeriod(packageName: String, startTime: Long, endTime: Long): List<com.example.scrolltrack.db.RawAppEvent> = emptyList()
        override suspend fun deleteOldEvents(cutoffTimestamp: Long) {}
        override suspend fun getFirstEventTimestamp(): Long? = null
        override suspend fun getLastEventTimestamp(): Long? = null
        override suspend fun deleteEventsForDateString(dateString: String) {}
        override suspend fun getLatestEventTimestampForDate(dateString: String): Long? = null
    }

    val fakeRepo = ScrollDataRepositoryImpl(dummyScrollSessionDao, dummyDailyAppUsageDao, dummyRawAppEventDao, app)
    val fakeViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application = app, repositoryOverride = fakeRepo))
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) { 
        TodaySummaryScreen(
            navController = rememberNavController(),
            viewModel = fakeViewModel, 
            greeting = "Good Morning! ☀️",
            isAccessibilityServiceEnabled = false,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = false,
            onEnableUsageStatsClick = {},
            totalUsageTime = "0m",
            totalUsageTimeMillis = 0L,
            topWeeklyApp = null,
            totalScrollUnits = 0L,
            scrollDistanceMeters = "0 m",
            appScrollData = emptyList(),
            onNavigateToHistoricalUsage = {},
            onNavigateToAppDetail = {}
        )
    }
}

@Preview(showBackground = true, name = "Today Summary - All Granted - Top App")
@Composable
fun TodaySummaryScreenAllGrantedWithTopAppPreview() {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    // Create dummy DAOs for preview (can share the same dummy implementations)
    val dummyScrollSessionDao = object : ScrollSessionDao {
        override fun getAllSessionsFlow(): Flow<List<ScrollSessionRecord>> = flowOf(emptyList())
        override suspend fun insertSession(session: ScrollSessionRecord) {}
        override fun getTotalScrollForDate(dateString: String): Flow<Long?> = flowOf(0L)
        override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> = flowOf(emptyList())
        override suspend fun getAllSessions(): List<ScrollSessionRecord> = emptyList()
        override suspend fun getTotalScrollForAppOnDate(pkgName: String, dateString: String): Long? = null
        override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> = emptyList<AppScrollDataPerDate>()
    }
    val dummyDailyAppUsageDao = object : DailyAppUsageDao {
        override fun getUsageForDate(dateString: String): Flow<List<DailyAppUsageRecord>> = flowOf(emptyList())
        override fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>> = flowOf(emptyList())
        override suspend fun insertOrUpdateUsage(dailyAppUsageRecord: DailyAppUsageRecord) {}
        override suspend fun insertAllUsage(records: List<DailyAppUsageRecord>) {}
        override suspend fun clearAllUsageData() {}
        override suspend fun getSpecificAppUsageForDate(packageName: String, dateString: String): DailyAppUsageRecord? = null
        override fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?> = flowOf(0L)
        override suspend fun deleteOldUsageData(timestampMillis: Long): Int = 0
        override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> = emptyList()
        override suspend fun deleteUsageForDate(dateString: String) {}
        override suspend fun getUsageCountForDateString(dateString: String): Int = 0
    }

    val dummyRawAppEventDao = object : com.example.scrolltrack.db.RawAppEventDao {
        override suspend fun insertEvent(event: com.example.scrolltrack.db.RawAppEvent) {}
        override suspend fun insertEvents(events: List<com.example.scrolltrack.db.RawAppEvent>) {}
        override fun getEventsForDate(dateString: String): Flow<List<com.example.scrolltrack.db.RawAppEvent>> = flowOf(emptyList())
        override suspend fun getEventsForPeriod(startTime: Long, endTime: Long): List<com.example.scrolltrack.db.RawAppEvent> = emptyList()
        override suspend fun getEventsForPackageNameInPeriod(packageName: String, startTime: Long, endTime: Long): List<com.example.scrolltrack.db.RawAppEvent> = emptyList()
        override suspend fun deleteOldEvents(cutoffTimestamp: Long) {}
        override suspend fun getFirstEventTimestamp(): Long? = null
        override suspend fun getLastEventTimestamp(): Long? = null
        override suspend fun deleteEventsForDateString(dateString: String) {}
        override suspend fun getLatestEventTimestampForDate(dateString: String): Long? = null
    }

    val fakeRepo = ScrollDataRepositoryImpl(dummyScrollSessionDao, dummyDailyAppUsageDao, dummyRawAppEventDao, app)
    val fakeViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application = app, repositoryOverride = fakeRepo))
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) { 
        val exampleTimeMillis = (2.75 * 60 * 60 * 1000).toLong()
        val topAppExample = AppUsageUiItem(
            id = "com.example.topapp",
            appName = "Social Butterfly",
            icon = null,
            usageTimeMillis = (10 * 60 * 60 * 1000).toLong(),
            formattedUsageTime = "10h 0m",
            packageName = "com.example.topapp"
        )
        TodaySummaryScreen(
            navController = rememberNavController(),
            viewModel = fakeViewModel,
            greeting = "Good Evening 👍",
            isAccessibilityServiceEnabled = true,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = true,
            onEnableUsageStatsClick = {},
            totalUsageTime = "2h 45m",
            totalUsageTimeMillis = exampleTimeMillis,
            topWeeklyApp = topAppExample,
            totalScrollUnits = 13106L,
            scrollDistanceMeters = "1,230 m",
            appScrollData = listOf(
                AppScrollUiItem("settings", "Settings", null, 7294, "com.android.settings")
            ),
            onNavigateToHistoricalUsage = {},
            onNavigateToAppDetail = {}
        )
    }
}

@Preview(showBackground = true, name = "Top Weekly App Card - With Data")
@Composable
fun TopWeeklyAppCardPreview() {
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) {
        val topAppExample = AppUsageUiItem(
            id = "com.example.topapp",
            appName = "Social Media Pro",
            icon = null, 
            usageTimeMillis = (15 * 60 * 60 * 1000).toLong(),
            formattedUsageTime = "15h 0m",
            packageName = "com.example.topapp"
        )
        Box(modifier = Modifier.padding(16.dp).width(200.dp).height(180.dp)) {
             TopWeeklyAppCard(topApp = topAppExample, modifier = Modifier.fillMaxSize(), onClick = {})
        }
    }
}

@Preview(showBackground = true, name = "Top Weekly App Card - No Data")
@Composable
fun TopWeeklyAppCardNoDataPreview() {
    ScrollTrackTheme(themeVariant = "oled_dark", dynamicColor = false) {
         Box(modifier = Modifier.padding(16.dp).width(200.dp).height(180.dp)) {
            TopWeeklyAppCard(topApp = null, modifier = Modifier.fillMaxSize(), onClick = {})
        }
    }
}

@Composable
fun ThemeModeSwitch(
    currentThemeVariant: String,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = currentThemeVariant != THEME_LIGHT

    Switch(
        modifier = modifier,
        checked = isDarkMode,
        onCheckedChange = {
            val newTheme = if (it) THEME_OLED_DARK else THEME_LIGHT
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
