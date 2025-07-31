package com.example.scrolltrack.services

import android.content.Context
import android.content.Intent
import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.GroupWithApps
import com.example.scrolltrack.db.LimitGroup
import com.example.scrolltrack.db.LimitedApp
import com.example.scrolltrack.ui.limit.BlockingActivity
import com.example.scrolltrack.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class LimitMonitorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var limitsRepository: LimitsRepository

    @MockK
    private lateinit var dailyAppUsageDao: DailyAppUsageDao

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var clock: Clock

    private val testPackage = "com.test.app"
    private val todayString = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private val testGroup = LimitGroup(id = 1, name = "Test Group", time_limit_minutes = 10, is_user_visible = true, is_enabled = true)
    private val testLimitedApp = LimitedApp(package_name = testPackage, group_id = 1)
    private val testGroupWithApps = GroupWithApps(group = testGroup, apps = listOf(testLimitedApp))

    @Test
    fun `onForegroundAppChanged does nothing for app with no limit`() = runTest {
        val limitMonitor = LimitMonitor(limitsRepository, dailyAppUsageDao, context, this, clock)
        coEvery { limitsRepository.getLimitedApp(testPackage) } returns flowOf(null)

        limitMonitor.onForegroundAppChanged(testPackage)
        advanceTimeBy(10000)

        coVerify(exactly = 0) { dailyAppUsageDao.getSpecificAppUsageForDate(any(), any()) }
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `monitor does not fire intent when usage is under limit`() = runTest {
        val limitMonitor = LimitMonitor(limitsRepository, dailyAppUsageDao, context, this, clock)
        // Arrange: Usage is 9 minutes, limit is 10 minutes
        val nineMinutesMs = TimeUnit.MINUTES.toMillis(9)
        coEvery { limitsRepository.getLimitedApp(testPackage) } returns flowOf(testLimitedApp)
        coEvery { limitsRepository.getGroupWithApps(1) } returns flowOf(testGroupWithApps)
        coEvery { clock.currentTimeMillis() } returns 0L
        coEvery { dailyAppUsageDao.getSpecificAppUsageForDate(testPackage, todayString) } returns DailyAppUsageRecord(packageName = testPackage, dateString = todayString, usageTimeMillis = nineMinutesMs)

        // Act
        limitMonitor.onForegroundAppChanged(testPackage)
        advanceTimeBy(10000) // Advance time, but not enough to exceed limit

        // Assert
        verify(exactly = 0) { context.startActivity(any()) }

        // Cleanup
        limitMonitor.onAppStopped(testPackage)
    }

    @Test
    fun `monitor fires intent when usage exceeds limit`() = runTest {
        val limitMonitor = LimitMonitor(limitsRepository, dailyAppUsageDao, context, this, clock)
        // Arrange: Usage is 11 minutes, limit is 10 minutes
        val elevenMinutesMs = TimeUnit.MINUTES.toMillis(11)
        val intentSlot = slot<Intent>()
        coEvery { limitsRepository.getLimitedApp(testPackage) } returns flowOf(testLimitedApp)
        coEvery { limitsRepository.getGroupWithApps(1) } returns flowOf(testGroupWithApps)
        coEvery { clock.currentTimeMillis() } returns 0L
        coEvery { dailyAppUsageDao.getSpecificAppUsageForDate(testPackage, todayString) } returns DailyAppUsageRecord(packageName = testPackage, dateString = todayString, usageTimeMillis = elevenMinutesMs)
        coEvery { context.startActivity(capture(intentSlot)) } returns Unit

        // Act
        limitMonitor.onForegroundAppChanged(testPackage)
        advanceTimeBy(6000) // Let one check cycle run

        // Assert
        verify(exactly = 1) { context.startActivity(any()) }
        assertEquals(BlockingActivity::class.java.name, intentSlot.captured.component?.className)
        assertEquals(testPackage, intentSlot.captured.getStringExtra(BlockingActivity.EXTRA_PACKAGE_NAME))
        assertEquals(1L, intentSlot.captured.getLongExtra(BlockingActivity.EXTRA_GROUP_ID, -1L))
    }

    @Test
    fun `monitor stops when app is stopped`() = runTest {
        val limitMonitor = LimitMonitor(limitsRepository, dailyAppUsageDao, context, this, clock)
        // Arrange
        coEvery { limitsRepository.getLimitedApp(testPackage) } returns flowOf(testLimitedApp)
        coEvery { limitsRepository.getGroupWithApps(1) } returns flowOf(testGroupWithApps)
        coEvery { clock.currentTimeMillis() } returnsMany listOf(0L, 1000L)
        coEvery { dailyAppUsageDao.getSpecificAppUsageForDate(any(), any()) } returns null

        // Act
        limitMonitor.onForegroundAppChanged(testPackage)
        advanceTimeBy(1000)
        limitMonitor.onAppStopped(testPackage)
        advanceTimeBy(10000) // Advance time to see if checks continue

        // Assert: Verify checkUsage was called but then stopped.
        // The check happens once on start, but not again after stop.
        coVerify(exactly = 1) { dailyAppUsageDao.getSpecificAppUsageForDate(any(), any()) }
    }
}