package com.example.scrolltrack

import android.app.AppOpsManager
// import android.app.Application // Not strictly needed here if MainViewModelFactory handles it
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences // Import SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.ui.main.AppScrollUiItem
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.ui.main.MainViewModelFactory
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import com.example.scrolltrack.util.DateUtil

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
                val greeting by viewModel.greeting.collectAsStateWithLifecycle()
                val selectedDateString by viewModel.selectedDate.collectAsStateWithLifecycle()
                val appScrollItems by viewModel.aggregatedScrollDataForSelectedDate.collectAsStateWithLifecycle()
                val totalScrollUnits by viewModel.totalScrollForSelectedDate.collectAsStateWithLifecycle()
                val totalUsageTimeFormatted by viewModel.totalUsageTimeFormatted.collectAsStateWithLifecycle()
                val scrollDistance by viewModel.scrollDistanceFormatted.collectAsStateWithLifecycle()

                MainScreenLayout(
                    greeting = greeting,
                    selectedDate = selectedDateString,
                    onDateSelected = { dateMillis -> viewModel.updateSelectedDate(dateMillis) },
                    isAccessibilityServiceEnabled = isAccessibilityEnabledState,
                    onEnableAccessibilityClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    isUsageStatsPermissionGranted = isUsageStatsGrantedState,
                    onEnableUsageStatsClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening usage access settings", e)
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    totalUsageTime = totalUsageTimeFormatted,
                    totalScrollUnits = totalScrollUnits,
                    scrollDistanceKm = scrollDistance.first,
                    scrollDistanceMiles = scrollDistance.second,
                    appScrollData = appScrollItems
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
                viewModel.refreshUsageTimeForCurrentDate() // Refresh current day's usage time

                // Check if historical backfill has been done
                val backfillDone = appPrefs.getBoolean(KEY_HISTORICAL_BACKFILL_DONE, false)
                if (!backfillDone) {
                    Log.i(TAG, "Performing initial historical data backfill.")
                    viewModel.performHistoricalUsageDataBackfill(10) // Fetch last 10 days
                    appPrefs.edit().putBoolean(KEY_HISTORICAL_BACKFILL_DONE, true).apply()
                }
            }
        } else {
            Log.i(TAG, "Usage stats permission is NOT granted.")
        }
    }

    // ... (isAccessibilityServiceEnabled and hasUsageStatsPermission functions remain the same) ...
    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (accessibilityEnabled == 0) return false
        val settingValue = Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (settingValue != null) {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(settingValue)
            val serviceNameToCheckShort = "." + serviceClass.simpleName
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

// --- Composable Functions (MainScreenLayout, SummaryMetricCard, PermissionRow, AppScrollItemEntry, Previews) ---
// These remain the same as in the previous response (main_activity_kt_v11_ui_polish)
// Ensure they are included below this MainActivity class.
// For brevity, I'm not repeating them here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenLayout(
    greeting: String,
    selectedDate: String,
    onDateSelected: (Long) -> Unit,
    isAccessibilityServiceEnabled: Boolean,
    onEnableAccessibilityClick: () -> Unit,
    isUsageStatsPermissionGranted: Boolean,
    onEnableUsageStatsClick: () -> Unit,
    totalUsageTime: String,
    totalScrollUnits: Long,
    scrollDistanceKm: String,
    scrollDistanceMiles: String,
    appScrollData: List<AppScrollUiItem>,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = remember(selectedDate) {
            DateUtil.parseDateString(selectedDate)?.time ?: System.currentTimeMillis()
        },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )

    val accessibilityStatusColor = if (isAccessibilityServiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val accessibilityStatusTextDisplay = "Accessibility: ${if (isAccessibilityServiceEnabled) "Enabled" else "Disabled"}"

    val usageStatsStatusColor = if (isUsageStatsPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val usageStatsStatusTextDisplay = "Usage Access: ${if (isUsageStatsPermissionGranted) "Granted" else "Denied"}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(greeting, style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    TextButton(onClick = { showDatePickerDialog = true }) {
                        Text(selectedDate, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CalendarToday, contentDescription = "Select Date")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PermissionRow(
                        statusText = accessibilityStatusTextDisplay,
                        statusColor = accessibilityStatusColor,
                        buttonText = "Settings",
                        onButtonClick = onEnableAccessibilityClick,
                        showButton = !isAccessibilityServiceEnabled
                    )
                    Divider(Modifier.padding(vertical = 8.dp))
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryMetricCard(
                    title = "Phone Usage",
                    value = totalUsageTime,
                    icon = Icons.Filled.BarChart,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Digital Scroll",
                    value = "$scrollDistanceKm\n$scrollDistanceMiles",
                    icon = Icons.Filled.Insights,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    isDistance = true
                )
            }
            if (totalScrollUnits > 0) {
                Text(
                    text = "Scroll Units: $totalScrollUnits",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("App Breakdown for $selectedDate", style = MaterialTheme.typography.titleMedium)
            Divider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            if (appScrollData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No scroll data recorded for $selectedDate.\nTry scrolling in some apps!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items = appScrollData, key = { it.id }) { appItem ->
                        AppScrollItemEntry(appItem)
                    }
                }
            }
        }

        if (showDatePickerDialog) {
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        showDatePickerDialog = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    isDistance: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).defaultMinSize(minHeight = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            val valueTextStyle = if (isDistance) {
                MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp)
            } else {
                MaterialTheme.typography.headlineMedium
            }
            Text(
                text = value,
                style = valueTextStyle,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
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
    showButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusText,
            fontSize = 14.sp,
            color = statusColor,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        if (showButton) {
            FilledTonalButton(
                onClick = onButtonClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(buttonText, fontSize = 12.sp)
            }
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Permission Granted",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AppScrollItemEntry(appData: AppScrollUiItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* TODO: Handle item click for more details later */ }
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
                    style = MaterialTheme.typography.titleMedium, // Prominent app name
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

@Preview(showBackground = true, name = "Main Screen - Dark Theme")
@Composable
fun MainScreenLayoutDarkPreview() {
    ScrollTrackTheme(darkThemeUserPreference = true, dynamicColor = false) {
        MainScreenLayout(
            greeting = "Good Evening ✨",
            selectedDate = "2025-05-23",
            onDateSelected = {},
            isAccessibilityServiceEnabled = true,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = true,
            onEnableUsageStatsClick = {},
            totalUsageTime = "4h 30m",
            totalScrollUnits = 249194L,
            scrollDistanceKm = "2.45 km",
            scrollDistanceMiles = "1.52 miles",
            appScrollData = listOf(
                AppScrollUiItem("yt", "YouTube", null, 149234, "com.google.android.youtube"),
                AppScrollUiItem("chrome", "Chrome", null, 82351, "com.android.chrome"),
                AppScrollUiItem("settings", "Settings", null, 15621, "com.android.settings")
            )
        )
    }
}

@Preview(showBackground = true, name = "Main Screen - Light Theme (Dynamic Off)")
@Composable
fun MainScreenLayoutLightPreview() {
    ScrollTrackTheme(darkThemeUserPreference = false, dynamicColor = false) {
        MainScreenLayout(
            greeting = "Good Morning ☀️",
            selectedDate = "2025-05-23",
            onDateSelected = {},
            isAccessibilityServiceEnabled = false,
            onEnableAccessibilityClick = {},
            isUsageStatsPermissionGranted = false,
            onEnableUsageStatsClick = {},
            totalUsageTime = "N/A",
            totalScrollUnits = 0L,
            scrollDistanceKm = "0.00 km",
            scrollDistanceMiles = "0.00 miles",
            appScrollData = emptyList()
        )
    }
}
