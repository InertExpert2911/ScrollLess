package com.example.scrolltrack.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.AppMetadataDao
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDrawable
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppMetadataRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var mockDao: AppMetadataDao
    private lateinit var repository: AppMetadataRepositoryImpl
    private lateinit var packageManager: PackageManager
    private lateinit var shadowPackageManager: org.robolectric.shadows.ShadowPackageManager

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Correctly initialize real objects, spies, and shadows to avoid conflicts.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val realPackageManager = appContext.packageManager
        shadowPackageManager = Shadows.shadowOf(realPackageManager)
        packageManager = spyk(realPackageManager) // Spy and Shadow are on the same real object

        // The context given to the repository must return our spied package manager.
        context = spyk(appContext) {
            every { packageManager } returns this@AppMetadataRepositoryImplTest.packageManager
        }

        mockDao = mockk(relaxUnitFun = true)
        repository = AppMetadataRepositoryImpl(context, mockDao)

        // Clean up icon directory before each test
        val iconDir = File(context.filesDir, "app_icons")
        if (iconDir.exists()) {
            iconDir.deleteRecursively()
        }
        iconDir.mkdirs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    ): PackageInfo {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.name = appName
            this.flags = if (isSystem) ApplicationInfo.FLAG_SYSTEM else 0
            this.sourceDir = "/dev/null"
            this.uid = 12345
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

        shadowPackageManager.installPackage(packageInfo)

        // Mock getApplicationLabel and getApplicationIcon using the spy
        every { packageManager.getApplicationLabel(appInfo) } returns appName
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(context.resources, bitmap)
        every { packageManager.getApplicationIcon(appInfo) } returns drawable

        // Bypassing delegation issues by mocking queryIntentActivities directly.
        val resolveInfoList = if (hasLauncher) {
            listOf(ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    this.packageName = packageName
                    this.name = "TestActivity"
                }
            })
        } else {
            emptyList()
        }

        // Mock for all Android versions by matching the intent's characteristics.
        val mainLaunchIntentMatcher: (Intent) -> Boolean = {
            it.action == Intent.ACTION_MAIN && it.hasCategory(Intent.CATEGORY_LAUNCHER)
        }
        every { packageManager.queryIntentActivities(match(mainLaunchIntentMatcher), any<Int>()) } returns resolveInfoList
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { packageManager.queryIntentActivities(match(mainLaunchIntentMatcher), any<PackageManager.ResolveInfoFlags>()) } returns resolveInfoList
        }

        // Mock getLaunchIntentForPackage for any other tests that might need it.
        if (hasLauncher) {
            val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
            every { packageManager.getLaunchIntentForPackage(packageName) } returns launchIntent
        } else {
            every { packageManager.getLaunchIntentForPackage(packageName) } returns null
        }

        return packageInfo
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
    fun `getAppMetadata - in DB, installed, version matches - returns DB data`() = runTest(testDispatcher) {
        val pkg = "com.test.app1"
        val dbMeta = createAppMetadata(pkg, versionCode = 2L)
        coEvery { mockDao.getByPackageName(pkg) } returns dbMeta
        mockPmApp(pkg, versionCode = 2L)

        val result = repository.getAppMetadata(pkg)
        assertThat(result).isEqualTo(dbMeta)
        coVerify(exactly = 0) { mockDao.insertOrUpdate(any()) } // No update
    }

    @Test
    fun `getAppMetadata - in DB, installed, version mismatch - fetches from PM, updates DB`() = runTest(testDispatcher) {
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
    fun `getAppMetadata - in DB, installed, but not in PM (uninstalled) - marks uninstalled`() = runTest(testDispatcher) {
        val pkg = "com.test.app1"
        val dbMetaInstalled = createAppMetadata(pkg, isInstalled = true)
        val dbMetaUninstalled = createAppMetadata(pkg, isInstalled = false)

        // GIVEN: The DAO first returns the installed state, then the uninstalled state after the update.
        coEvery { mockDao.getByPackageName(pkg) } returns dbMetaInstalled andThen dbMetaUninstalled
        // GIVEN: The call to mark as uninstalled runs successfully.
        coEvery { mockDao.markAsUninstalled(pkg, any()) } just Runs

        // GIVEN: The app is not actually present in the package manager.
        Shadows.shadowOf(packageManager).removePackage(pkg)

        // WHEN: We try to get the metadata
        val result = repository.getAppMetadata(pkg)

        // THEN: The repository should mark the app as uninstalled in the DAO.
        coVerify { mockDao.markAsUninstalled(pkg, any()) }

        // AND THEN: The final returned metadata should reflect the uninstalled state.
        assertThat(result).isEqualTo(dbMetaUninstalled)
        assertThat(result?.isInstalled).isFalse()
    }

    @Test
    fun `getAppMetadata - not in DB, in PM - fetches from PM, caches`() = runTest(testDispatcher) {
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
    fun `getAppMetadata - not in DB, not in PM - returns null`() = runTest(testDispatcher) {
        val pkg = "com.nonexistent"
        coEvery { mockDao.getByPackageName(pkg) } returns null
        // Not mocking in PM
        val result = repository.getAppMetadata(pkg)
        assertThat(result).isNull()
    }

    // --- getIconFile Tests ---
    @Test
    fun `getIconFile - icon exists - returns File`() = runTest(testDispatcher) {
        val pkg = "com.test.app.icon"
        // Simulate icon saving by fetching it first (which saves it)
        coEvery { mockDao.getByPackageName(pkg) } returns null
        mockPmApp(pkg)
        repository.getAppMetadata(pkg) // This should save the icon

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
    fun `syncAllInstalledApps - new apps in PM - adds to DB`() = runTest(testDispatcher) {
        val pkg1 = "com.new.app1"
        val pkg2 = "com.new.app2"
        // Still need to mock the individual app lookups that happen inside the sync
        val appInfo1 = mockPmApp(pkg1, appName = "New App 1").applicationInfo
        val appInfo2 = mockPmApp(pkg2, appName = "New App 2").applicationInfo

        // GIVEN: The package manager reports two installed apps
        every { packageManager.getInstalledApplications(any<Int>()) } returns listOf(appInfo1, appInfo2)

        // GIVEN: The database is empty
        coEvery { mockDao.getAllKnownPackageNames() } returns emptyList()
        coEvery { mockDao.getByPackageName(any()) } returns null // For the subsequent fetch call
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        // WHEN: We run the sync
        repository.syncAllInstalledApps()

        // THEN: Both new apps should be inserted into the database
        val capturedMetas = mutableListOf<AppMetadata>()
        coVerify(exactly = 2) { mockDao.insertOrUpdate(capture(capturedMetas)) }
        assertThat(capturedMetas.map { it.packageName }).containsExactlyElementsIn(listOf(pkg1, pkg2))
    }

    @Test
    fun `syncAllInstalledApps - apps in DB not in PM - marks uninstalled`() = runTest(testDispatcher) {
        val pkgUninstalled = "com.dbonly.app"
        val dbMetaInstalled = createAppMetadata(pkgUninstalled, isInstalled = true)

        // GIVEN: The package manager reports no installed apps
        every { packageManager.getInstalledApplications(any<Int>()) } returns emptyList()

        // GIVEN: The database knows about one installed app
        coEvery { mockDao.getAllKnownPackageNames() } returns listOf(pkgUninstalled)
        coEvery { mockDao.getByPackageName(pkgUninstalled) } returns dbMetaInstalled
        coEvery { mockDao.markAsUninstalled(pkgUninstalled, any()) } just Runs

        // WHEN: We run the sync
        repository.syncAllInstalledApps()

        // THEN: The app should be marked as uninstalled
        coVerify { mockDao.markAsUninstalled(pkgUninstalled, any()) }
    }

    // --- isUserVisible Heuristic (tested via fetchFromPackageManagerAndCache indirectly) ---
    @Test
    fun `fetchFromPackageManagerAndCache - non-system app - isUserVisible true`() = runTest(testDispatcher) {
        val pkg = "com.non.system"
        mockPmApp(pkg, isSystem = false, hasLauncher = true) // Launcher status doesn't matter for non-system
        coEvery { mockDao.getByPackageName(pkg) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.handleAppInstalledOrUpdated(pkg) // Triggers fetch

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.isUserVisible).isTrue()
    }

    @Test
    fun `fetchFromPackageManagerAndCache - system app with launcher - isUserVisible true`() = runTest(testDispatcher) {
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
    fun `fetchFromPackageManagerAndCache - system app without launcher - isUserVisible false`() = runTest(testDispatcher) {
        val pkg = "com.system.nolauncher"
        mockPmApp(pkg, isSystem = true, hasLauncher = false)
        coEvery { mockDao.getByPackageName(pkg) } returns null
        coEvery { mockDao.insertOrUpdate(any()) } just Runs

        repository.handleAppInstalledOrUpdated(pkg)

        val slot = slot<AppMetadata>()
        coVerify { mockDao.insertOrUpdate(capture(slot)) }
        assertThat(slot.captured.isUserVisible).isFalse()
    }
}
