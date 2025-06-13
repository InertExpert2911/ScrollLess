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
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.navigation.AppNavigationHost
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.ui.main.MainViewModelFactory
import com.example.scrolltrack.ui.theme.*
import com.example.scrolltrack.util.PermissionUtils

// Constants for theme variants to be used by the Switch logic
private const val THEME_LIGHT = "light"
private const val THEME_OLED_DARK = "oled_dark"

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var isAccessibilityEnabledState by mutableStateOf(false)
    private var isUsageStatsGrantedState by mutableStateOf(false)
    private lateinit var viewModel: MainViewModel

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModelFactory = MainViewModelFactory(application, null, null)
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
        isAccessibilityEnabledState = PermissionUtils.isAccessibilityServiceEnabled(this, ScrollTrackService::class.java)
    }

    private fun updateUsageStatsPermissionStatus() {
        isUsageStatsGrantedState = PermissionUtils.hasUsageStatsPermission(this)
        if (isUsageStatsGrantedState) {
            Log.i(TAG, "Usage stats permission is granted.")
            if (::viewModel.isInitialized) {
                viewModel.refreshDataForToday()
            }
        } else {
            Log.i(TAG, "Usage stats permission is NOT granted.")
        }
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