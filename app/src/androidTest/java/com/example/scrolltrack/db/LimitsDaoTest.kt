package com.example.scrolltrack.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LimitsDaoTest {

    private lateinit var limitsDao: LimitsDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        limitsDao = db.limitsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertGroupAndGetIt() = runBlocking {
        val group = LimitGroup(name = "Social Media", time_limit_minutes = 60, is_user_visible = true)
        val groupId = limitsDao.insertOrUpdateGroup(group)

        val groupWithApps = limitsDao.getGroupWithApps(groupId).first()
        assertThat(groupWithApps).isNotNull()
        assertThat(groupWithApps?.group?.name).isEqualTo("Social Media")
    }

    @Test
    @Throws(Exception::class)
    fun deletingGroup_cascadesDelete_toLimitedApps() = runBlocking {
        // Arrange
        val group = LimitGroup(name = "Games", time_limit_minutes = 30, is_user_visible = true)
        val groupId = limitsDao.insertOrUpdateGroup(group)
        val app1 = LimitedApp(package_name = "com.game.one", group_id = groupId)
        val app2 = LimitedApp(package_name = "com.game.two", group_id = groupId)
        limitsDao.insertOrUpdateLimitedApp(app1)
        limitsDao.insertOrUpdateLimitedApp(app2)
        assertThat(limitsDao.getGroupWithApps(groupId).first()?.apps).hasSize(2)

        // Act
        val groupToDelete = limitsDao.getGroupWithApps(groupId).first()!!.group
        limitsDao.deleteGroup(groupToDelete)

        // Assert
        assertThat(limitsDao.getGroupWithApps(groupId).first()).isNull()
        assertThat(limitsDao.getLimitedApp("com.game.one").first()).isNull()
        assertThat(limitsDao.getLimitedApp("com.game.two").first()).isNull()
    }
}