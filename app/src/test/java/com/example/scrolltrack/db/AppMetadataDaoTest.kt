package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppMetadataDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: AppMetadataDao

    private val app1 = AppMetadata(packageName = "com.app.one", appName = "App One", versionCode = 1, versionName = "1.0", isSystemApp = false, isInstalled = true, isUserVisible = true, installTimestamp = 1000L, lastUpdateTimestamp = 1000L)
    private val app2 = AppMetadata(packageName = "com.app.two", appName = "App Two", versionCode = 1, versionName = "1.0", isSystemApp = true, isInstalled = true, isUserVisible = false, installTimestamp = 2000L, lastUpdateTimestamp = 2000L)
    private val app3 = AppMetadata(packageName = "com.app.three", appName = "App Three", versionCode = 1, versionName = "1.0", isSystemApp = false, isInstalled = true, isUserVisible = true, installTimestamp = 3000L, lastUpdateTimestamp = 3000L)


    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.appMetadataDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insertOrUpdate inserts a new record correctly`() = runTest {
        dao.insertOrUpdate(app1)
        val retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved).isEqualTo(app1)
    }

    @Test
    fun `insertOrUpdate updates an existing record correctly`() = runTest {
        dao.insertOrUpdate(app1)
        val updatedApp1 = app1.copy(
            appName = "App One Updated",
            versionCode = 2,
            versionName = "1.1",
            isSystemApp = true, // This shouldn't typically change, but we test it.
            isInstalled = true,
            isUserVisible = false,
            lastUpdateTimestamp = 5000L,
            isIconCached = true
        )
        dao.insertOrUpdate(updatedApp1)
        val retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved).isEqualTo(updatedApp1)
    }

    @Test
    fun `getByPackageName returns null for non-existent package`() = runTest {
        val retrieved = dao.getByPackageName("com.app.nonexistent")
        assertThat(retrieved).isNull()
    }


    @Test
    fun `markAsUninstalled updates flags and timestamp`() = runTest {
        dao.insertOrUpdate(app1)
        val uninstallTimestamp = 9999L
        dao.markAsUninstalled("com.app.one", uninstallTimestamp)

        val retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.isInstalled).isFalse()
        assertThat(retrieved?.isIconCached).isFalse()
        assertThat(retrieved?.lastUpdateTimestamp).isEqualTo(uninstallTimestamp)
    }

    @Test
    fun `markAsUninstalled does nothing for non-existent package`() = runTest {
        dao.markAsUninstalled("com.app.nonexistent", 9999L)
        val allApps = dao.getAll().first()
        assertThat(allApps).isEmpty()
    }

    @Test
    fun `getAllKnownPackageNames returns all package names`() = runTest {
        dao.insertOrUpdate(app1)
        dao.insertOrUpdate(app2)

        val names = dao.getAllKnownPackageNames()
        assertThat(names).hasSize(2)
        assertThat(names).containsExactly("com.app.one", "com.app.two")
    }

    @Test
    fun `getNonVisiblePackageNames returns correct packages based on both flags`() = runTest {
        // app1 is visible by default
        dao.insertOrUpdate(app1)
        // app2 is not visible by its property
        dao.insertOrUpdate(app2)
        // app3 is visible by its property, but we will hide it with the override
        dao.insertOrUpdate(app3)
        dao.updateUserHidesOverride(app3.packageName, true)

        val nonVisibleNames = dao.getNonVisiblePackageNames().first()
        assertThat(nonVisibleNames).hasSize(2)
        assertThat(nonVisibleNames).containsExactly(app2.packageName, "com.app.three")
    }

    @Test
    fun `getAll returns all metadata records as flow`() = runTest {
        dao.insertOrUpdate(app1)
        dao.insertOrUpdate(app2)
        dao.insertOrUpdate(app3)

        dao.getAll().test {
            val all = awaitItem()
            assertThat(all).hasSize(3)
            assertThat(all).containsExactly(app1, app2, app3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserHidesOverride correctly sets the override`() = runTest {
        dao.insertOrUpdate(app1)

        // Set override to true
        dao.updateUserHidesOverride("com.app.one", true)
        var retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved?.userHidesOverride).isTrue()

        // Set override to false
        dao.updateUserHidesOverride("com.app.one", false)
        retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved?.userHidesOverride).isFalse()

        // Set override to null
        dao.updateUserHidesOverride("com.app.one", null)
        retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved?.userHidesOverride).isNull()
    }
}
