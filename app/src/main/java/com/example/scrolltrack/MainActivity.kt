package com.example.scrolltrack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.scrolltrack.ui.main.MainScreen
import com.example.scrolltrack.ui.main.TodaySummaryViewModel
import com.example.scrolltrack.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts

// Constants for theme variants to be used by the Switch logic
private const val THEME_LIGHT = "light"
private const val THEME_OLED_DARK = "oled_dark"
private const val PREFS_GLOBAL = "ScrollTrackGlobalPrefs"
private const val KEY_BACKFILL_DONE = "historical_backfill_done"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private val viewModel: TodaySummaryViewModel by viewModels()
    private lateinit var globalPrefs: SharedPreferences

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i(TAG, "Notification permission granted by user.")
            } else {
                Log.w(TAG, "Notification permission denied by user.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display and predictive back gestures.
        enableEdgeToEdge()
        globalPrefs = getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE)

        // The ViewModelFactory and ViewModelProvider are no longer needed.
        // Hilt will automatically provide the MainViewModel.

        // --- One-Time Historical Backfill (Now permission-aware) ---
        lifecycleScope.launch {
            // Wait until the permission is granted.
            viewModel.isUsagePermissionGranted.first { it }

            // Once permission is confirmed, check if backfill has already been done.
            val isBackfillDone = globalPrefs.getBoolean(KEY_BACKFILL_DONE, false)
            if (!isBackfillDone) {
                Log.i(TAG, "Usage permission is granted and backfill has not been run. Triggering now.")
                viewModel.performHistoricalUsageDataBackfill(10) { success ->
                    if (success) {
                        globalPrefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
                        Log.i(TAG, "Historical backfill completed successfully and flag was set.")
                    } else {
                        Log.e(TAG, "Historical backfill failed. Flag will not be set.")
                    }
                }
            } else {
                Log.d(TAG, "Historical backfill flag is already set. Skipping check after permission grant.")
            }
        }

        // Request notification permission if on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        setContent {
            val selectedPalette by viewModel.selectedThemePalette.collectAsState()
            val useDarkTheme by viewModel.isDarkMode.collectAsState()
            val isAccessibilityEnabled by viewModel.isAccessibilityServiceEnabled.collectAsStateWithLifecycle()
            val isUsageStatsGranted by viewModel.isUsagePermissionGranted.collectAsStateWithLifecycle()
            val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled.collectAsStateWithLifecycle()

            ScrollTrackTheme(
                appTheme = selectedPalette,
                darkTheme = useDarkTheme,
                dynamicColor = false
            ) {
                MainScreen(
                    isAccessibilityEnabledState = isAccessibilityEnabled,
                    isUsageStatsGrantedState = isUsageStatsGranted,
                    isNotificationListenerEnabledState = isNotificationListenerEnabled,
                    onEnableAccessibilityClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onEnableUsageStatsClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening usage access settings", e)
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onEnableNotificationListenerClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening notification listener settings", e)
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // This new function handles re-checking permissions and triggering a data refresh if needed.
        viewModel.onAppResumed()
    }

    // --- Notification Permission Handling (Android 13+) ---
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i(TAG, "Notification permission already granted.")
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // In a real app, you might show a custom dialog explaining why the permission is needed
                    Log.w(TAG, "Showing rationale for notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.i(TAG, "Requesting notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    // --- End Notification Permission Handling ---
}
