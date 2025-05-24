package com.example.scrolltrack

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
// import android.content.pm.PackageManager // Re-evaluate if needed elsewhere, not directly by this file's Composables
// import android.graphics.drawable.Drawable // Re-evaluate if needed by AppScrollUiItem if icon was handled here
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
// import androidx.compose.foundation.shape.RoundedCornerShape // Not directly used by name in this file after changes
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.automirrored.filled.ArrowBack // Not used
// import androidx.compose.material.icons.filled.BarChart // Not used
// import androidx.compose.material.icons.filled.CalendarToday // Not used
import androidx.compose.material.icons.filled.CheckCircle
// import androidx.compose.material.icons.filled.Insights // Not used
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.graphics.vector.ImageVector // Not used
import androidx.compose.ui.layout.ContentScale
// import androidx.compose.ui.platform.LocalContext // Not used
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Used in PermissionRow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// import androidx.navigation.NavController // NavHostController is used
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.historical.HistoricalUsageScreen
import com.example.scrolltrack.ui.main.AppScrollUiItem
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.ui.main.MainViewModelFactory
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import com.example.scrolltrack.ui.theme.UsageStatusGreen
import com.example.scrolltrack.ui.theme.UsageStatusOrange
import com.example.scrolltrack.ui.theme.UsageStatusRed
// import com.example.scrolltrack.util.DateUtil // Not directly used in this file's composables after changes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TrendingUp

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var isAccessibilityEnabledState by mutableStateOf(false)
    private var isUsageStatsGrantedState by mutableStateOf(false)
    private lateinit var viewModel: MainViewModel
    private lateinit var appPrefs: SharedPreferences

    private companion object {
        const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
        const val KEY_HISTORICAL_BACKFILL_DONE = "historical_backfill_done"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPrefs = getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)

        val viewModelFactory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        setContent {
            ScrollTrackTheme(darkThemeUserPreference = true, dynamicColor = true) {
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
                    viewModel.performHistoricalUsageDataBackfill(10)
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
    NavHost(navController = navController, startDestination = ScreenRoutes.TODAY_SUMMARY) {
        composable(ScreenRoutes.TODAY_SUMMARY) {
            val greeting by viewModel.greeting.collectAsStateWithLifecycle()
            val appScrollItems by viewModel.aggregatedScrollDataToday.collectAsStateWithLifecycle()
            val totalScrollUnits by viewModel.totalScrollToday.collectAsStateWithLifecycle()
            val totalUsageTimeFormatted by viewModel.totalPhoneUsageTodayFormatted.collectAsStateWithLifecycle()
            val totalUsageTimeMillis: Long by viewModel.totalPhoneUsageTodayMillis.collectAsStateWithLifecycle()
            val scrollDistance by viewModel.scrollDistanceTodayFormatted.collectAsStateWithLifecycle()

            TodaySummaryScreen(
                greeting = greeting,
                isAccessibilityServiceEnabled = isAccessibilityEnabledState,
                onEnableAccessibilityClick = onEnableAccessibilityClick,
                isUsageStatsPermissionGranted = isUsageStatsGrantedState,
                onEnableUsageStatsClick = onEnableUsageStatsClick,
                totalUsageTime = totalUsageTimeFormatted,
                totalUsageTimeMillis = totalUsageTimeMillis,
                totalScrollUnits = totalScrollUnits,
                scrollDistanceKm = scrollDistance.first,
                scrollDistanceMiles = scrollDistance.second,
                appScrollData = appScrollItems,
                onNavigateToHistoricalUsage = {
                    viewModel.resetSelectedDateToToday()
                    navController.navigate(ScreenRoutes.HISTORICAL_USAGE)
                }
            )
        }
        composable(ScreenRoutes.HISTORICAL_USAGE) {
            HistoricalUsageScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySummaryScreen(
    greeting: String,
    isAccessibilityServiceEnabled: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    isUsageStatsPermissionGranted: Boolean,
    onEnableUsageStatsClick: () -> Unit,
    totalUsageTime: String,
    totalUsageTimeMillis: Long,
    totalScrollUnits: Long,
    scrollDistanceKm: String,
    scrollDistanceMiles: String,
    appScrollData: List<AppScrollUiItem>,
    onNavigateToHistoricalUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityStatusColor = if (isAccessibilityServiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val accessibilityStatusTextDisplay = "Accessibility: ${if (isAccessibilityServiceEnabled) "Enabled" else "Disabled"}"

    val usageStatsStatusColor = if (isUsageStatsPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val usageStatsStatusTextDisplay = "Usage Access: ${if (isUsageStatsPermissionGranted) "Granted" else "Denied"}"

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
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            text = "Manage your daily digital habits.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 20.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                PermissionRow(
                    statusText = accessibilityStatusTextDisplay,
                    statusColor = accessibilityStatusColor,
                    buttonText = "Settings",
                    onButtonClick = onEnableAccessibilityClick,
                    showButton = !isAccessibilityServiceEnabled
                )
                Divider(Modifier.padding(vertical = 8.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
                PermissionRow(
                    statusText = usageStatsStatusTextDisplay,
                    statusColor = usageStatsStatusColor,
                    buttonText = "Settings",
                    onButtonClick = onEnableUsageStatsClick,
                    showButton = !isUsageStatsPermissionGranted
                )
            }
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
            ScrollStatsCard(
                modifier = Modifier.weight(1f),
                scrollDistanceKm = scrollDistanceKm,
                scrollDistanceMiles = scrollDistanceMiles,
                totalScrollUnits = totalScrollUnits
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Today's App Scroll Breakdown:",
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(start = 8.dp)
        )
        Divider(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )

        if (appScrollData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No scroll data recorded today.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = appScrollData, key = { it.id }) { appItem ->
                    AppScrollItemEntry(appItem)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                text = "PHONE USAGE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ScrollStatsCard(
    modifier: Modifier = Modifier,
    scrollDistanceKm: String,
    scrollDistanceMiles: String,
    totalScrollUnits: Long
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.TrendingUp, contentDescription = "Scroll Stats", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
            Text(
                text = "SCROLL STATS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$scrollDistanceKm / $scrollDistanceMiles",
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$totalScrollUnits units",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionRow(
    statusText: String,
    statusColor: Color,
    buttonText: String,
    onButtonClick: () -> Unit,
    showButton: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = statusColor,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        if (showButton) {
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(buttonText, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Permission Granted",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun AppScrollItemEntry(appData: AppScrollUiItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* TODO: Handle item click */ }
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

@Preview(showBackground = true, name = "Today Summary Screen - Dark - Vibrant")
@Composable
fun TodaySummaryScreenDarkPreviewVibrant() {
    ScrollTrackTheme(darkThemeUserPreference = true, dynamicColor = false) {
        val exampleTimeMillis = (7 * 60 * 60 * 1000 + 18 * 60 * 1000).toLong()
        TodaySummaryScreen(
            greeting = "Good Evening ✨",
            isAccessibilityServiceEnabled = false,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = true,
            onEnableUsageStatsClick = {},
            totalUsageTime = "7h 18m",
            totalUsageTimeMillis = exampleTimeMillis,
            totalScrollUnits = 249194L,
            scrollDistanceKm = "2.45 km",
            scrollDistanceMiles = "1.52 miles",
            appScrollData = listOf(
                AppScrollUiItem("yt", "YouTube", null, 149234, "com.google.android.youtube"),
                AppScrollUiItem("chrome", "Chrome", null, 82351, "com.android.chrome"),
                AppScrollUiItem("settings", "Settings", null, 15621, "com.android.settings")
            ),
            onNavigateToHistoricalUsage = {}
        )
    }
}
