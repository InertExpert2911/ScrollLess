package com.example.scrolltrack.services

import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class LimitMonitorTest {

    private lateinit var limitsRepository: LimitsRepository
    private lateinit var dailyAppUsageDao: DailyAppUsageDao
    private lateinit var limitMonitor: LimitMonitor

    @Before
    fun setUp() {
        limitsRepository = mockk(relaxed = true)
        dailyAppUsageDao = mockk(relaxed = true)
        limitMonitor = LimitMonitor(limitsRepository, dailyAppUsageDao)
        mockkObject(DateUtil)
    }

    @After
    fun tearDown() {
        limitMonitor.stopMonitoring()
        unmockkObject(DateUtil)
    }

    @Test
    fun `startMonitoring stops when app is not limited`() = runTest {
        // Arrange
        val packageName = "com.unlimited.app"
        coEvery { limitsRepository.getLimitedApp(packageName) } returns flowOf(null)

        // Act
        val job = launch { limitMonitor.startMonitoring(this, packageName) }
        advanceUntilIdle()

        // Assert
        coVerify { limitsRepository.getLimitedApp(packageName) }
        coVerify(exactly = 0) { dailyAppUsageDao.getUsageForAppsOnDate(any(), any()) }
        job.cancel()
    }

//    @Test
//    fun `startMonitoring fetches usage and does not block when limit is not reached`() = runTest {
//        // Arrange
//        val packageName = "com.limited.app"
//        val groupId = 1L
//        val limitedApp = LimitedApp(package_name = packageName, group_id = groupId)
//        val group = LimitGroup(id = groupId, name = "Social Media", time_limit_minutes = 60, is_user_visible = true)
//        val groupWithApps = GroupWithApps(group, listOf(limitedApp))
//        val usageRecord = DailyAppUsageRecord(dateString = "2023-10-27", packageName = packageName, usageTimeMillis = 1000, appOpenCount = 1)
//        val today = "2023-10-27"
//
//        every { DateUtil.getCurrentLocalDateString() } returns today
//        coEvery { limitsRepository.getLimitedApp(packageName) } returns flowOf(limitedApp)
//        coEvery { limitsRepository.getGroupWithApps(groupId) } returns flowOf(groupWithApps)
//        coEvery { dailyAppUsageDao.getUsageForAppsOnDate(listOf(packageName), today) } returns flowOf(listOf(usageRecord))
//
//        // Act
//        val job = launch { limitMonitor.startMonitoring(this, packageName) }
//        advanceUntilIdle()
//
//        // Assert
//        coVerify { dailyAppUsageDao.getUsageForAppsOnDate(listOf(packageName), today) }
//
//        job.cancel()
//    }

    @Test
    fun `startMonitoring triggers blocking when limit is breached`() = runTest {
        // Arrange
        val packageName = "com.limited.app"
        val groupId = 1L
        val limitMinutes = 1
        val limitMillis = TimeUnit.MINUTES.toMillis(limitMinutes.toLong())
        val limitedApp = LimitedApp(package_name = packageName, group_id = groupId)
        val group = LimitGroup(id = groupId, name = "Games", time_limit_minutes = limitMinutes, is_user_visible = true)
        val groupWithApps = GroupWithApps(group, listOf(limitedApp))
        // Usage is exactly at the limit
        val usageRecord = DailyAppUsageRecord(dateString = "2023-10-27", packageName = packageName, usageTimeMillis = limitMillis, appOpenCount = 1)
        val today = "2023-10-27"

        every { DateUtil.getCurrentLocalDateString() } returns today
        coEvery { limitsRepository.getLimitedApp(packageName) } returns flowOf(limitedApp)
        coEvery { limitsRepository.getGroupWithApps(groupId) } returns flowOf(groupWithApps)
        coEvery { dailyAppUsageDao.getUsageForAppsOnDate(listOf(packageName), today) } returns flowOf(listOf(usageRecord))

        // Act
        val job = launch { limitMonitor.startMonitoring(this, packageName) }
        advanceUntilIdle() // Run the first iteration where the breach happens.

        // Assert
        // 1. Verify the group was added to the blocked set, the primary side-effect.
        assertThat(limitMonitor.blockedGroups).contains(groupId)

        // 2. Verify the monitor stopped and did not continue checking usage.
        advanceTimeBy(120_000) // Advance time well past the 60-second delay.
        advanceUntilIdle()

        // The DAO should have only been called once, in the initial iteration before it stopped.
        coVerify(exactly = 1) { dailyAppUsageDao.getUsageForAppsOnDate(any(), any()) }
        job.cancel()
    }

    @Test
    fun `startMonitoring does not trigger blocking if group is already blocked`() = runTest {
        // Arrange
        val packageName = "com.limited.app"
        val groupId = 1L
        val limitedApp = LimitedApp(package_name = packageName, group_id = groupId)
        coEvery { limitsRepository.getLimitedApp(packageName) } returns flowOf(limitedApp)

        // Manually block the group
        limitMonitor.blockedGroups.add(groupId)

        // Act
        val job = launch { limitMonitor.startMonitoring(this, packageName) }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { limitsRepository.getGroupWithApps(any()) }
        job.cancel()
    }
}