package com.example.scrolltrack.data

import android.content.Context
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

    override suspend fun getAppMetadata(packageName: String): AppMetadata? = withContext(Dispatchers.IO) {
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
                    return@withContext fetchFromPackageManagerAndCache(packageName)
                } else {
                    return@withContext fromDb // Version is the same, return cached data.
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App was marked as installed, but is no longer on device. Correct this.
                Log.w(TAG, "Correcting record: $packageName was marked installed but not found.")
                handleAppUninstalled(packageName)
                return@withContext appMetadataDao.getByPackageName(packageName) // Return the updated (uninstalled) record
            }
        }

        // Case 2: App is in our DB but marked as uninstalled.
        if (fromDb != null && !fromDb.isInstalled) {
            return@withContext fromDb
        }

        // Case 3: App is not in our DB at all.
        return@withContext fetchFromPackageManagerAndCache(packageName)
    }

    override fun getIconFile(packageName: String): File? {
        val iconFile = File(iconDir, "$packageName.png")
        if (iconFile.exists()) {
            return iconFile
        }
        return null // We don't fallback to PackageManager here to enforce using our cache.
    }

    override suspend fun syncAllInstalledApps(): Unit = withContext(Dispatchers.IO) {
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

    override suspend fun handleAppUninstalled(packageName: String): Unit = withContext(Dispatchers.IO) {
        appMetadataDao.markAsUninstalled(packageName, System.currentTimeMillis())
        deleteIconFile(packageName)
        Log.d(TAG, "Handled app uninstallation for $packageName.")
    }

    override suspend fun handleAppInstalledOrUpdated(packageName: String): Unit = withContext(Dispatchers.IO) {
        fetchFromPackageManagerAndCache(packageName)
        Log.d(TAG, "Handled app installation/update for $packageName.")
    }

    private suspend fun fetchFromPackageManagerAndCache(packageName: String): AppMetadata? = withContext(Dispatchers.IO) {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val appInfo = packageInfo.applicationInfo
            if (appInfo == null) {
                Log.e(TAG, "ApplicationInfo is null for $packageName. Cannot cache.")
                return@withContext null
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

            val metadata = AppMetadata(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                isSystemApp = isSystem,
                isInstalled = true,
                isIconCached = iconCached,
                installTimestamp = packageInfo.firstInstallTime,
                lastUpdateTimestamp = System.currentTimeMillis()
            )

            appMetadataDao.insertOrUpdate(metadata)
            Log.d(TAG, "Fetched and cached new metadata for $packageName.")
            return@withContext metadata
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not fetch metadata for $packageName, it may have been uninstalled quickly.", e)
            return@withContext null
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