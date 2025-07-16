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
import com.example.scrolltrack.ui.components.AppLoadingScreen
import com.example.scrolltrack.ui.main.MainScreen
import com.example.scrolltrack.ui.main.TodaySummaryViewModel
import com.example.scrolltrack.ui.main.UiState
import com.example.scrolltrack.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.navigation.compose.rememberNavController
import timber.log.Timber

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
                Timber.tag(TAG).i("Notification permission granted by user.")
            } else {
                Timber.tag(TAG).w("Notification permission denied by user.")
            }
        }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                Timber.tag(TAG)
                    .i("Usage permission is granted and backfill has not been run. Triggering now.")
                viewModel.performHistoricalUsageDataBackfill(10) { success ->
                    if (success) {
                        globalPrefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
                        Timber.tag(TAG)
                            .i("Historical backfill completed successfully and flag was set.")
                    } else {
                        Timber.tag(TAG).e("Historical backfill failed. Flag will not be set.")
                    }
                }
            } else {
                Timber.tag(TAG)
                    .d("Historical backfill flag is already set. Skipping check after permission grant.")
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
            val navController = rememberNavController()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            ScrollTrackTheme(
                appTheme = selectedPalette,
                darkTheme = useDarkTheme,
                dynamicColor = false
            ) {
                when (uiState) {
                    is UiState.InitialLoading -> {
                        AppLoadingScreen()
                    }
                    is UiState.Ready, is UiState.Refreshing, is UiState.Error -> {
                        MainScreen(
                            navController = navController,
                            isAccessibilityEnabledState = isAccessibilityEnabled,
                            isUsageStatsGrantedState = isUsageStatsGranted,
                            isNotificationListenerEnabledState = isNotificationListenerEnabled,
                            onEnableAccessibilityClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            onEnableUsageStatsClick = {
                                try {
                                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (e: Exception) {
                                    Timber.tag(TAG).e(e, "Error opening usage access settings")
                                    startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            onEnableNotificationListenerClick = {
                                try {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } catch (e: Exception) {
                                    Timber.tag(TAG)
                                        .e(e, "Error opening notification listener settings")
                                    startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        )
                    }
                }
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
                    Timber.tag(TAG).i("Notification permission already granted.")
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // In a real app, you might show a custom dialog explaining why the permission is needed
                    Timber.tag(TAG).w("Showing rationale for notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Timber.tag(TAG).i("Requesting notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    // --- End Notification Permission Handling ---
}
