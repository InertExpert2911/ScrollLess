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

object PermissionUtils {

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

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