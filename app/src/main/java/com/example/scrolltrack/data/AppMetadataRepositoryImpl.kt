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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
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
                    Timber.tag(TAG).d("App update detected for $packageName. Re-fetching metadata.")
                    return fetchFromPackageManagerAndCache(packageName)
                } else {
                    return fromDb // Version is the same, return cached data.
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App was marked as installed, but is no longer on device. Correct this.
                Timber.tag(TAG)
                    .w("Correcting record: $packageName was marked installed but not found.")
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
        Timber.tag(TAG).d("Starting full sync of installed apps...")
        val allPmApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val allDbPackageNames = appMetadataDao.getAllKnownPackageNames().toSet()
        val allPmPackageNames = allPmApps.map { it.packageName }.toSet()

        // Add new apps
        val newApps = allPmPackageNames - allDbPackageNames
        for (pkgName in newApps) {
            fetchFromPackageManagerAndCache(pkgName)
        }
        Timber.tag(TAG).d("Found and cached ${newApps.size} new apps.")

        // Mark uninstalled apps
        val uninstalledApps = allDbPackageNames - allPmPackageNames
        for (pkgName in uninstalledApps) {
            val record = appMetadataDao.getByPackageName(pkgName)
            if (record != null && record.isInstalled) {
                handleAppUninstalled(pkgName)
            }
        }
        Timber.tag(TAG).d("Marked ${uninstalledApps.size} apps as uninstalled.")
        Timber.tag(TAG).d("Full sync complete.")
    }

    override suspend fun handleAppUninstalled(packageName: String): AppMetadata? {
        appMetadataDao.markAsUninstalled(packageName, System.currentTimeMillis())
        deleteIconFile(packageName)
        Timber.tag(TAG).d("Handled app uninstallation for $packageName.")
        return appMetadataDao.getByPackageName(packageName)
    }

    override suspend fun handleAppInstalledOrUpdated(packageName: String) {
        fetchFromPackageManagerAndCache(packageName)
        Timber.tag(TAG).d("Handled app installation/update for $packageName.")
    }

    override suspend fun getNonVisiblePackageNames(): List<String> {
        return appMetadataDao.getNonVisiblePackageNames()
    }

    override suspend fun updateUserHidesOverride(packageName: String, userHidesOverride: Boolean?) {
        appMetadataDao.updateUserHidesOverride(packageName, userHidesOverride)
        Timber.tag(TAG).d("User override for $packageName set to $userHidesOverride")
    }

    override fun getAllMetadata(): Flow<List<AppMetadata>> {
        return appMetadataDao.getAll()
    }

    private suspend fun fetchFromPackageManagerAndCache(packageName: String): AppMetadata? {
        try {
            val existingMetadata = appMetadataDao.getByPackageName(packageName)
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val appInfo = packageInfo.applicationInfo
            if (appInfo == null) {
                Timber.tag(TAG).e("ApplicationInfo is null for $packageName. Cannot cache.")
                return null
            }
            val appName = packageManager.getApplicationLabel(appInfo).toString().trim()
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
            // The most reliable way to determine if an app is "user-facing" is to check
            // if it has an activity that can be launched from the main app drawer.
            val launchIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            launchIntent.setPackage(packageName)
            val resolveInfoList = packageManager.queryIntentActivities(launchIntent, 0)
            val isUserVisible = resolveInfoList.isNotEmpty()
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
            Timber.tag(TAG)
                .d("Fetched and cached new metadata for $packageName. IsUserVisible: $isUserVisible")
            return metadata
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.tag(TAG).w(
                e,
                "Could not fetch metadata for $packageName, it may have been uninstalled quickly."
            )
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
            Timber.tag(TAG).e(e, "Failed to save icon to file for $packageName")
            return false
        }
    }

    private fun deleteIconFile(packageName: String) {
        val iconFile = File(iconDir, "$packageName.png")
        if (iconFile.exists()) {
            if (iconFile.delete()) {
                Timber.tag(TAG).d("Deleted cached icon for $packageName")
            } else {
                Timber.tag(TAG).e("Failed to delete cached icon for $packageName")
            }
        }
    }
}
