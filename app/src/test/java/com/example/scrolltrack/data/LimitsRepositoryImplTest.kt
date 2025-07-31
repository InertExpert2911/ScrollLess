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

    @Test
    fun `createGroup - creates a visible group`() = runTest {
        val groupName = "Social Media"
        val limit = 60
        val groupId = repository.createGroup(groupName, limit)

        val group = fakeDao.getGroupWithApps(groupId).first()
        assertThat(group).isNotNull()
        assertThat(group?.group?.name).isEqualTo(groupName)
        assertThat(group?.group?.time_limit_minutes).isEqualTo(limit)
        assertThat(group?.group?.is_user_visible).isTrue()
        assertThat(group?.apps).isEmpty()
    }

    @Test
    fun `updateGroup - updates group name and limit`() = runTest {
        val groupId = repository.createGroup("Initial Name", 30)
        val group = fakeDao.getGroupWithApps(groupId).first()!!.group
        
        val updatedGroup = group.copy(name = "Updated Name", time_limit_minutes = 90)
        repository.updateGroup(updatedGroup)

        val fetchedGroup = fakeDao.getGroupWithApps(groupId).first()
        assertThat(fetchedGroup?.group?.name).isEqualTo("Updated Name")
        assertThat(fetchedGroup?.group?.time_limit_minutes).isEqualTo(90)
    }

    @Test
    fun `deleteGroup - deletes the group`() = runTest {
        val groupId = repository.createGroup("To Be Deleted", 30)
        val group = fakeDao.getGroupWithApps(groupId).first()!!.group
        
        repository.deleteGroup(group)

        val fetchedGroup = fakeDao.getGroupWithApps(groupId).first()
        assertThat(fetchedGroup).isNull()
    }
    @Test
    fun `addAppToGroup - adds app to an existing group`() = runTest {
        val groupId = repository.createGroup("Social", 60)
        val packageName = "com.example.app"

        repository.addAppToGroup(packageName, groupId)

        val group = fakeDao.getGroupWithApps(groupId).first()
        assertThat(group?.apps).hasSize(1)
        assertThat(group?.apps?.first()?.package_name).isEqualTo(packageName)
    }

    @Test
    fun `addAppToGroup - moves app from one group to another`() = runTest {
        val groupId1 = repository.createGroup("Group 1", 30)
        val groupId2 = repository.createGroup("Group 2", 60)
        val packageName = "com.example.app"

        repository.addAppToGroup(packageName, groupId1)
        val group1Before = fakeDao.getGroupWithApps(groupId1).first()
        assertThat(group1Before?.apps).hasSize(1)

        repository.addAppToGroup(packageName, groupId2)
        val group1After = fakeDao.getGroupWithApps(groupId1).first()
        val group2After = fakeDao.getGroupWithApps(groupId2).first()

        assertThat(group1After?.apps).isEmpty()
        assertThat(group2After?.apps).hasSize(1)
        assertThat(group2After?.apps?.first()?.package_name).isEqualTo(packageName)
    }
    @Test
    fun `removeAppLimit - last app in user group - does not delete group`() = runTest {
        val groupId = repository.createGroup("My Group", 60)
        val packageName = "com.example.app"
        repository.addAppToGroup(packageName, groupId)

        repository.removeAppLimit(packageName)

        val group = fakeDao.getGroupWithApps(groupId).first()
        assertThat(group).isNotNull()
        assertThat(group?.apps).isEmpty()
    }

    @Test
    fun `removeAppLimit - app in user group - removes app but not group`() = runTest {
        val groupId = repository.createGroup("My Group", 60)
        val packageName = "com.example.app"
        repository.addAppToGroup(packageName, groupId)

        repository.removeAppLimit(packageName)

        val group = fakeDao.getGroupWithApps(groupId).first()
        assertThat(group).isNotNull()
        assertThat(group?.apps).isEmpty()
        
        val limitedApp = fakeDao.getLimitedApp(packageName).first()
        assertThat(limitedApp).isNull()
    }
}