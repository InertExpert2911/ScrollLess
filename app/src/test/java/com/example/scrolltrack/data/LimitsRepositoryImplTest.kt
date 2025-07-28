package com.example.scrolltrack.data

import com.example.scrolltrack.db.FakeLimitsDao
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LimitsRepositoryImplTest {

    private lateinit var repository: LimitsRepository
    private lateinit var fakeDao: FakeLimitsDao

    @Before
    fun setUp() {
        fakeDao = FakeLimitsDao()
        repository = LimitsRepositoryImpl(fakeDao)
    }

    @Test
    fun `setAppLimit creates an invisible group and adds app`() = runTest {
        // Arrange
        val packageName = "com.example.app"
        val limit = 30

        // Act
        repository.setAppLimit(packageName, limit)

        // Assert
        val limitedApp = fakeDao.getLimitedApp(packageName).first()
        assertThat(limitedApp).isNotNull()
        
        val group = fakeDao.getGroupWithApps(limitedApp!!.group_id).first()
        assertThat(group).isNotNull()
        assertThat(group?.group?.is_user_visible).isFalse()
        assertThat(group?.group?.time_limit_minutes).isEqualTo(limit)
        assertThat(group?.apps).hasSize(1)
    }

    @Test
    fun `removeAppLimit deletes app and cleans up invisible group`() = runTest {
        // Arrange: First, set a limit
        val packageName = "com.example.app"
        repository.setAppLimit(packageName, 30)
        val limitedAppBefore = fakeDao.getLimitedApp(packageName).first()
        assertThat(limitedAppBefore).isNotNull()

        // Act
        repository.removeAppLimit(packageName)

        // Assert
        val limitedAppAfter = fakeDao.getLimitedApp(packageName).first()
        assertThat(limitedAppAfter).isNull()

        // Check that the group was also deleted
        val groupAfter = fakeDao.getGroupWithApps(limitedAppBefore!!.group_id).first()
        assertThat(groupAfter).isNull()
    }
}