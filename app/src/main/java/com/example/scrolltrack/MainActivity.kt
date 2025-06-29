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
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.navigation.AppNavigationHost
import com.example.scrolltrack.ui.main.TodaySummaryViewModel
import com.example.scrolltrack.ui.theme.*
import com.example.scrolltrack.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val selectedTheme by viewModel.selectedThemeVariant.collectAsState()
            val isAccessibilityEnabled by viewModel.isAccessibilityServiceEnabled.collectAsStateWithLifecycle()
            val isUsageStatsGranted by viewModel.isUsagePermissionGranted.collectAsStateWithLifecycle()
            val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled.collectAsStateWithLifecycle()

            // Determine darkTheme based on selectedTheme. "light" is light, others are dark.
            val useDarkTheme = selectedTheme != THEME_LIGHT

            ScrollTrackTheme(darkTheme = useDarkTheme, dynamicColor = false) { // Updated call
                val navController = rememberNavController()
                AppNavigationHost(
                    navController = navController,
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
        // Always check permissions on resume and refresh data if needed
        viewModel.checkAllPermissions()
        viewModel.refreshDataForToday()
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