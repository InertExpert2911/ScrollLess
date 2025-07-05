package com.example.scrolltrack.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.content.ComponentName
import android.util.Log
import com.example.scrolltrack.ScrollTrackService
import com.example.scrolltrack.services.NotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

object PermissionUtils {

    fun isNotificationListenerEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val componentName = ComponentName(context, serviceClass)
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabledListeners?.split(":")?.mapNotNull {
            ComponentName.unflattenFromString(it)
        }?.any { it == componentName } ?: false
    }

    fun isAccessibilityServiceEnabledFlow(context: Context): Flow<Boolean> = flow {
        while (true) {
            emit(isAccessibilityServiceEnabled(context, ScrollTrackService::class.java))
            delay(1000) // Check every second
        }
    }.distinctUntilChanged()

    fun isNotificationListenerEnabledFlow(context: Context): Flow<Boolean> = flow {
        while (true) {
            emit(isNotificationListenerEnabled(context, NotificationListener::class.java))
            delay(1000)
        }
    }.distinctUntilChanged()

    fun isUsageStatsPermissionGrantedFlow(context: Context): Flow<Boolean> = flow {
        while (true) {
            emit(hasUsageStatsPermission(context))
            delay(1000)
        }
    }.distinctUntilChanged()

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?: return false

        val serviceClassName = serviceClass.name
        Log.d("PermissionUtils", "Checking for running service class: $serviceClassName")

        for (service in runningServices) {
            val serviceComponent = ComponentName.unflattenFromString(service.id)
            if (serviceComponent != null &&
                serviceComponent.packageName == context.packageName &&
                serviceComponent.className == serviceClassName) {
                Log.i("PermissionUtils", "Match found for ${service.id}. Service is enabled.")
                return true
            }
        }
        Log.w("PermissionUtils", "No running service matched ${context.packageName}/$serviceClassName.")
        return false
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        return if (appOpsManager != null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } else false
    }
} 