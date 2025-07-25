package com.example.scrolltrack.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.AppMetadataDao
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.test.core.app.ApplicationProvider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppMetadataRepositoryImplTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var mockDao: AppMetadataDao
    private lateinit var repository: AppMetadataRepositoryImpl
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockDao = mockk(relaxUnitFun = true)
        mockPackageManager = mockk(relaxed = true)

        val spiedContext = spyk(context)
        every { spiedContext.packageManager } returns mockPackageManager
        every { spiedContext.filesDir } returns File("build/tmp/test-files")

        repository = AppMetadataRepositoryImpl(spiedContext, mockDao)

        val iconDir = File(spiedContext.filesDir, "app_icons")
        if (iconDir.exists()) {
            iconDir.deleteRecursively()
        }
        iconDir.mkdirs()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockPmApp(
        packageName: String,
        appName: String = "Test App",
        versionCode: Long = 1L,
        versionName: String = "1.0",
        isSystem: Boolean = false,
        hasLauncher: Boolean = true,
        firstInstallTime: Long = 1000L
    ) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.name = appName
            flags = if (isSystem) ApplicationInfo.FLAG_SYSTEM else 0
        }

        val packageInfo = PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = appInfo
            this.versionName = versionName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                this.longVersionCode = versionCode
            } else {
                @Suppress("DEPRECATION")
                this.versionCode = versionCode.toInt()
            }
            this.firstInstallTime = firstInstallTime
        }

        every { mockPackageManager.getPackageInfo(packageName, 0) } returns packageInfo
        every { mockPackageManager.getApplicationLabel(appInfo) } returns appName
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val drawable = mockk<BitmapDrawable>(relaxed = true)
        every { drawable.bitmap } returns bitmap
        every { mockPackageManager.getApplicationIcon(any<ApplicationInfo>()) } returns drawable

        val resolveInfoList = if (hasLauncher) {
            listOf(ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    this.packageName = packageName
                }
            })
        } else {
            emptyList()
        }
        
        every { mockPackageManager.queryIntentActivities(any(), any<Int>()) } returns resolveInfoList
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { mockPackageManager.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>()) } returns resolveInfoList
        }
    }

    private fun createAppMetadata(
        packageName: String, appName: String = "Test App", versionCode: Long = 1L,
        isInstalled: Boolean = true, isSystem: Boolean = false, isUserVisible: Boolean = true,
        isIconCached: Boolean = false, userHidesOverride: Boolean? = null
    ): AppMetadata {
        return AppMetadata(packageName, appName, "1.0", versionCode, isSystem, isInstalled, isIconCached, 0, isUserVisible, userHidesOverride, 1000L, 2000L)
    }

    // --- getAppMetadata Tests ---
    @Test
    fun `getAppMetadata - in DB, installed, version matches - returns DB data`() = runTest {
        val pkg = "com.test.app1"
        val dbMeta = createAppMetadata(pkg, versionCode = 2L)
        coEvery { mockDao.getByPackageName(pkg) } returns dbMeta
        mockPmApp(pkg, versionCode = 2L)

        val result = repository.getAppMetadata(pkg)
        assertThat(result).isEqualTo(dbMeta)
        coVerify(exactly = 0) { mockDao.insertOrUpdate(any()) } // No update
    }

    @Test
    fun `getAppMetadata - in DB, installed, version mismatch - fetches from PM, updates DB`() = runTest {
        val pkg = "com.test.app1"
        val oldDbMeta = createAppMetadata(pkg, appName = "Old Name", versionCode = 1L)
        coEvery { mockDao.getByPackageName(pkg) } returns oldDbMeta

        mockPmApp(pkg, appName = "New Name", versionCode = 2L) // PM has newer version
        coEvery { mockDao.insertOrUpdate(any()) } just Runs // For the update call

        val result = repository.getAppMetadata(pkg)
        assertThat(result).isNotNull()
        assertThat(result!!.appName).isEqualTo("New Name")
        assertThat(result.versionCode).isEqualTo(2L)

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.appName).isEqualTo("New Name")
        assertThat(slot.captured.versionCode).isEqualTo(2L)
    }

    @Test
    fun `getAppMetadata - in DB, installed, but not in PM (uninstalled) - marks uninstalled`() = runTest {
        val pkg = "com.test.app1"
        val dbMetaInstalled = createAppMetadata(pkg, isInstalled = true)
        val dbMetaUninstalled = createAppMetadata(pkg, isInstalled = false)

        coEvery { mockDao.getByPackageName(pkg) } returns dbMetaInstalled andThen dbMetaUninstalled
        coEvery { mockDao.markAsUninstalled(pkg, any()) } just Runs
        every { mockPackageManager.getPackageInfo(pkg, 0) } throws PackageManager.NameNotFoundException()

        val result = repository.getAppMetadata(pkg)

        coVerify { mockDao.markAsUninstalled(pkg, any()) }
        assertThat(result).isEqualTo(dbMetaUninstalled)
        assertThat(result?.isInstalled).isFalse()
    }

    @Test
    fun `getAppMetadata - not in DB, in PM - fetches from PM, caches`() = runTest {
        val pkg = "com.test.newapp"
        coEvery { mockDao.getByPackageName(pkg) } returns null // Not in DB
        mockPmApp(pkg, appName = "New App", versionCode = 1L)
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        val result = repository.getAppMetadata(pkg)
        assertThat(result).isNotNull()
        assertThat(result!!.packageName).isEqualTo(pkg)
        assertThat(result.appName).isEqualTo("New App")

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.packageName).isEqualTo(pkg)
    }

    @Test
    fun `getAppMetadata - not in DB, not in PM - returns null`() = runTest {
        val pkg = "com.nonexistent"
        coEvery { mockDao.getByPackageName(pkg) } returns null
        every { mockPackageManager.getPackageInfo(pkg, 0) } throws PackageManager.NameNotFoundException()
        val result = repository.getAppMetadata(pkg)
        assertThat(result).isNull()
    }

    // --- getIconFile Tests ---
    @Test
    fun `getIconFile - icon exists - returns File`() = runTest {
        val pkg = "com.test.app.icon"
        coEvery { mockDao.getByPackageName(pkg) } returns null
        mockPmApp(pkg)
        repository.getAppMetadata(pkg)

        val iconFile = repository.getIconFile(pkg)
        assertThat(iconFile).isNotNull()
        assertThat(iconFile!!.exists()).isTrue()
        assertThat(iconFile.name).isEqualTo("$pkg.png")
    }

    @Test
    fun `getIconFile - icon does not exist - returns null`() {
        val pkg = "com.test.app.noicon"
        val iconFile = repository.getIconFile(pkg)
        assertThat(iconFile).isNull()
    }

    // --- syncAllInstalledApps Tests ---
    @Test
    fun `syncAllInstalledApps - new apps in PM - caches new apps`() = runTest {
        val pkg1 = "com.new.app1"
        val pkg2 = "com.new.app2"
        mockPmApp(pkg1, appName = "New App 1")
        mockPmApp(pkg2, appName = "New App 2")

        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns listOf(
            ApplicationInfo().apply { packageName = pkg1 },
            ApplicationInfo().apply { packageName = pkg2 }
        )
        coEvery { mockDao.getAllKnownPackageNames() } returns emptyList()
        coEvery { mockDao.getByPackageName(any()) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.syncAllInstalledApps()

        val capturedList = mutableListOf<AppMetadata>()
        coVerify(exactly = 2) { mockDao.insertOrUpdate(capture(capturedList)) }
        val capturedApps = capturedList.map { it.packageName }.toSet()
        assertThat(capturedApps).containsExactly(pkg1, pkg2)
    }

    @Test
    fun `syncAllInstalledApps - apps in DB not in PM - marks as uninstalled`() = runTest {
        val pkgToUninstall = "com.old.app"
        val dbMeta = createAppMetadata(pkgToUninstall, isInstalled = true)

        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns emptyList()
        coEvery { mockDao.getAllKnownPackageNames() } returns listOf(pkgToUninstall)
        coEvery { mockDao.getByPackageName(pkgToUninstall) } returns dbMeta
        coEvery { mockDao.markAsUninstalled(pkgToUninstall, any()) } just Runs

        repository.syncAllInstalledApps()

        coVerify { mockDao.markAsUninstalled(pkgToUninstall, any()) }
    }

    // --- isUserVisible Heuristic (tested via fetchFromPackageManagerAndCache indirectly) ---
    @Test
    fun `fetchFromPackageManagerAndCache - non-system app - isUserVisible true`() = runTest {
        val pkg = "com.non.system"
        mockPmApp(pkg, isSystem = false, hasLauncher = true)
        coEvery { mockDao.getByPackageName(pkg) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.handleAppInstalledOrUpdated(pkg)

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.isUserVisible).isTrue()
    }

    @Test
    fun `fetchFromPackageManagerAndCache - system app with launcher - isUserVisible true`() = runTest {
        val pkg = "com.system.launcher"
        mockPmApp(pkg, isSystem = true, hasLauncher = true)
        coEvery { mockDao.getByPackageName(pkg) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.handleAppInstalledOrUpdated(pkg)

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.isUserVisible).isTrue()
    }

    @Test
    fun `fetchFromPackageManagerAndCache - system app without launcher - isUserVisible false`() = runTest {
        val pkg = "com.system.nolauncher"
        mockPmApp(pkg, isSystem = true, hasLauncher = false)
        coEvery { mockDao.getByPackageName(pkg) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.handleAppInstalledOrUpdated(pkg)

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.isUserVisible).isFalse()
    }

    // --- handleAppUninstalled Tests ---
    @Test
    fun `handleAppUninstalled - icon file is deleted`() = runTest {
        val pkg = "com.test.app.to.uninstall"
        coEvery { mockDao.getByPackageName(pkg) } returns null
        mockPmApp(pkg)
        repository.getAppMetadata(pkg)

        val iconFile = repository.getIconFile(pkg)
        assertThat(iconFile).isNotNull()
        assertThat(iconFile!!.exists()).isTrue()

        repository.handleAppUninstalled(pkg)

        assertThat(iconFile.exists()).isFalse()
        coVerify { mockDao.markAsUninstalled(pkg, any()) }
    }

    @Test
    fun `fetchFromPackageManagerAndCache - icon save fails - isIconCached is false`() = runTest {
        val pkg = "com.test.icon.fail"
        mockPmApp(pkg)
        coEvery { mockDao.getByPackageName(pkg) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        val spiedRepository = spyk(repository, recordPrivateCalls = true)
        every { spiedRepository["saveIconToFile"](any<String>(), any<android.graphics.drawable.Drawable>()) } returns false

        spiedRepository.handleAppInstalledOrUpdated(pkg)

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.isIconCached).isFalse()
    }

    @Test
    fun `getAppMetadata - app uninstalled during fetch - returns null`() = runTest {
        val pkg = "com.test.ghost.app"
        coEvery { mockDao.getByPackageName(pkg) } returns null
        every { mockPackageManager.getPackageInfo(pkg, 0) } throws PackageManager.NameNotFoundException()

        val result = repository.getAppMetadata(pkg)

        assertThat(result).isNull()
        coVerify(exactly = 0) { mockDao.insertOrUpdate(any()) }
    }

    @Test
    fun `syncAllInstalledApps - handles null ApplicationInfo from PM gracefully`() = runTest {
        val pkg1 = "com.good.app"
        val goodAppInfo = ApplicationInfo().apply { packageName = pkg1 }
        val badAppInfo = ApplicationInfo().apply { packageName = null }

        mockPmApp(pkg1)
        every { mockPackageManager.getInstalledApplications(any<Int>()) } returns listOf(goodAppInfo, badAppInfo)
        coEvery { mockDao.getAllKnownPackageNames() } returns emptyList()
        coEvery { mockDao.getByPackageName(any()) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.syncAllInstalledApps()

        coVerify(exactly = 1) { mockDao.insertOrUpdate(match { it.packageName == pkg1 }) }
        coVerify(exactly = 0) { mockDao.insertOrUpdate(match { it.packageName == null }) }
    }
}
