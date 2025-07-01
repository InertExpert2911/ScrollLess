package com.example.scrolltrack.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.AppMetadataDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMetadataRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appMetadataDao: AppMetadataDao
) : AppMetadataRepository {

    private val TAG = "AppMetadataRepository"
    private val packageManager: PackageManager = context.packageManager
    private val iconDir = File(context.filesDir, "app_icons")

    init {
        if (!iconDir.exists()) {
            iconDir.mkdirs()
        }
    }

    override suspend fun getAppMetadata(packageName: String): AppMetadata? {
        val fromDb = appMetadataDao.getByPackageName(packageName)

        // Case 1: App is in our DB and marked as installed.
        if (fromDb != null && fromDb.isInstalled) {
            try {
                // Check if the app version has changed.
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                if (fromDb.versionCode != currentVersionCode) {
                    Log.d(TAG, "App update detected for $packageName. Re-fetching metadata.")
                    return fetchFromPackageManagerAndCache(packageName)
                } else {
                    return fromDb // Version is the same, return cached data.
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App was marked as installed, but is no longer on device. Correct this.
                Log.w(TAG, "Correcting record: $packageName was marked installed but not found.")
                return handleAppUninstalled(packageName)
            }
        }

        // Case 2: App is in our DB but marked as uninstalled.
        if (fromDb != null && !fromDb.isInstalled) {
            return fromDb
        }

        // Case 3: App is not in our DB at all.
        return fetchFromPackageManagerAndCache(packageName)
    }

    override fun getIconFile(packageName: String): File? {
        val iconFile = File(iconDir, "$packageName.png")
        if (iconFile.exists()) {
            return iconFile
        }
        return null // We don't fallback to PackageManager here to enforce using our cache.
    }

    override suspend fun syncAllInstalledApps() {
        Log.d(TAG, "Starting full sync of installed apps...")
        val allPmApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val allDbPackageNames = appMetadataDao.getAllKnownPackageNames().toSet()
        val allPmPackageNames = allPmApps.map { it.packageName }.toSet()

        // Add new apps
        val newApps = allPmPackageNames - allDbPackageNames
        for (pkgName in newApps) {
            fetchFromPackageManagerAndCache(pkgName)
        }
        Log.d(TAG, "Found and cached ${newApps.size} new apps.")

        // Mark uninstalled apps
        val uninstalledApps = allDbPackageNames - allPmPackageNames
        for (pkgName in uninstalledApps) {
            val record = appMetadataDao.getByPackageName(pkgName)
            if (record != null && record.isInstalled) {
                handleAppUninstalled(pkgName)
            }
        }
        Log.d(TAG, "Marked ${uninstalledApps.size} apps as uninstalled.")
        Log.d(TAG, "Full sync complete.")
    }

    override suspend fun handleAppUninstalled(packageName: String): AppMetadata? {
        appMetadataDao.markAsUninstalled(packageName, System.currentTimeMillis())
        deleteIconFile(packageName)
        Log.d(TAG, "Handled app uninstallation for $packageName.")
        return appMetadataDao.getByPackageName(packageName)
    }

    override suspend fun handleAppInstalledOrUpdated(packageName: String) {
        fetchFromPackageManagerAndCache(packageName)
        Log.d(TAG, "Handled app installation/update for $packageName.")
    }

    override suspend fun getNonVisiblePackageNames(): List<String> {
        return appMetadataDao.getNonVisiblePackageNames()
    }

    override suspend fun updateUserHidesOverride(packageName: String, userHidesOverride: Boolean?) {
        appMetadataDao.updateUserHidesOverride(packageName, userHidesOverride)
        Log.d(TAG, "User override for $packageName set to $userHidesOverride")
    }

    override suspend fun getAllMetadata(): List<AppMetadata> {
        return appMetadataDao.getAll()
    }

    private suspend fun fetchFromPackageManagerAndCache(packageName: String): AppMetadata? {
        try {
            val existingMetadata = appMetadataDao.getByPackageName(packageName)
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val appInfo = packageInfo.applicationInfo
            if (appInfo == null) {
                Log.e(TAG, "ApplicationInfo is null for $packageName. Cannot cache.")
                return null
            }
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val icon = packageManager.getApplicationIcon(appInfo)

            val iconCached = saveIconToFile(packageName, icon)

            val versionName = packageInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            // --- Heuristic for isUserVisible ---
            val hasLaunchIntent = packageManager.getLaunchIntentForPackage(packageName) != null

            // A more robust check for a main "launcher" intent.
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(mainIntent, 0)
            }
            val hasLauncherIcon = resolveInfoList.any { it.activityInfo.packageName == packageName }

            // An app is considered user-visible if it's NOT a system app,
            // OR if it IS a system app but it explicitly has a launcher icon.
            val isUserVisible = !isSystem || hasLauncherIcon
            // --- End of Heuristic ---

            val metadata = AppMetadata(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                isSystemApp = isSystem,
                isInstalled = true,
                isIconCached = iconCached,
                appCategory = -1, // Category is no longer needed for this heuristic
                isUserVisible = isUserVisible,
                userHidesOverride = existingMetadata?.userHidesOverride,
                installTimestamp = packageInfo.firstInstallTime,
                lastUpdateTimestamp = System.currentTimeMillis()
            )

            appMetadataDao.insertOrUpdate(metadata)
            Log.d(TAG, "Fetched and cached new metadata for $packageName. IsUserVisible: $isUserVisible")
            return metadata
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not fetch metadata for $packageName, it may have been uninstalled quickly.", e)
            return null
        }
    }

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }

        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            // Some drawables have no intrinsic size, so we'll create a default one
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }

        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveIconToFile(packageName: String, drawable: Drawable): Boolean {
        val iconFile = File(iconDir, "$packageName.png")
        try {
            val bitmap = getBitmapFromDrawable(drawable)
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save icon to file for $packageName", e)
            return false
        }
    }

    private fun deleteIconFile(packageName: String) {
        val iconFile = File(iconDir, "$packageName.png")
        if (iconFile.exists()) {
            if (iconFile.delete()) {
                Log.d(TAG, "Deleted cached icon for $packageName")
            } else {
                Log.e(TAG, "Failed to delete cached icon for $packageName")
            }
        }
    }
} 