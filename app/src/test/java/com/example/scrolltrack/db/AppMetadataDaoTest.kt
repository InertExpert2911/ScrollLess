package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val app1 = AppMetadata(packageName = "com.app.one", appName = "App One", versionCode = 1, versionName = "1.0", isSystemApp = false, isInstalled = true, installTimestamp = 1000L, lastUpdateTimestamp = 1000L)
    private val app2 = AppMetadata(packageName = "com.app.two", appName = "App Two", versionCode = 1, versionName = "1.0", isSystemApp = true, isInstalled = true, isUserVisible = false, installTimestamp = 2000L, lastUpdateTimestamp = 2000L)
    private val app3 = AppMetadata(packageName = "com.app.three", appName = "App Three", versionCode = 1, versionName = "1.0", isSystemApp = false, isInstalled = true, installTimestamp = 3000L, lastUpdateTimestamp = 3000L)

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
    fun `insertOrUpdate and getByPackageName work correctly`() = runTest {
        dao.insertOrUpdate(app1)
        val retrieved = dao.getByPackageName("com.app.one")
        assertThat(retrieved).isEqualTo(app1)

        val updatedApp1 = app1.copy(versionCode = 2, lastUpdateTimestamp = 5000L)
        dao.insertOrUpdate(updatedApp1)
        val updatedRetrieved = dao.getByPackageName("com.app.one")
        assertThat(updatedRetrieved?.versionCode).isEqualTo(2)
        assertThat(updatedRetrieved?.lastUpdateTimestamp).isEqualTo(5000L)
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
    fun `getAllKnownPackageNames returns all package names`() = runTest {
        dao.insertOrUpdate(app1)
        dao.insertOrUpdate(app2)

        val names = dao.getAllKnownPackageNames()
        assertThat(names).hasSize(2)
        assertThat(names).containsExactly("com.app.one", "com.app.two")
    }

    @Test
    fun `getNonVisiblePackageNames returns only non-visible apps`() = runTest {
        dao.insertOrUpdate(app1) // visible by default
        dao.insertOrUpdate(app2) // not visible
        dao.insertOrUpdate(app3.copy(isUserVisible = false)) // not visible

        val nonVisibleNames = dao.getNonVisiblePackageNames()
        assertThat(nonVisibleNames).hasSize(2)
        assertThat(nonVisibleNames).containsExactly("com.app.two", "com.app.three")
    }

    @Test
    fun `getAll returns all metadata records`() = runTest {
        dao.insertOrUpdate(app1)
        dao.insertOrUpdate(app2)
        dao.insertOrUpdate(app3)

        val all = dao.getAll()
        assertThat(all).hasSize(3)
        assertThat(all).containsExactly(app1, app2, app3)
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