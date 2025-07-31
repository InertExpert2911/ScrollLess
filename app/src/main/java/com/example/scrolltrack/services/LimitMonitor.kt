package com.example.scrolltrack.services

import android.content.Context
import android.content.Intent
import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.LimitGroup
import com.example.scrolltrack.db.LimitedApp
import com.example.scrolltrack.ui.limit.BlockingActivity
import com.example.scrolltrack.util.Clock
import com.example.scrolltrack.util.SnoozeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitMonitor @Inject constructor(
    private val limitsRepository: LimitsRepository,
    private val dailyAppUsageDao: DailyAppUsageDao,
    @ApplicationContext private val applicationContext: Context,
    private val externalScope: CoroutineScope,
    private val clock: Clock,
    private val snoozeManager: SnoozeManager
) {
    private var monitoringJob: Job? = null
    private var currentSessionStart: Long = 0
    private var currentPackage: String? = null

    companion object {
        private const val CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE // YYYY-MM-DD
    }

    fun onForegroundAppChanged(packageName: String) {
        if (packageName == currentPackage) {
            return
        }

        monitoringJob?.cancel()
        currentPackage = packageName

        monitoringJob = externalScope.launch {
            val limitedApp = limitsRepository.getLimitedApp(packageName).firstOrNull() ?: return@launch
            val groupWithApps = limitsRepository.getGroupWithApps(limitedApp.group_id).firstOrNull() ?: return@launch
            val group = groupWithApps.group

            if (!group.is_enabled) return@launch

            currentSessionStart = clock.currentTimeMillis()

            while (isActive) {
                checkUsage(packageName, group, groupWithApps.apps)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkUsage(packageName: String, group: LimitGroup, appsInGroup: List<LimitedApp>) {
        val todayString = LocalDate.now().format(DATE_FORMATTER)

        val historicalUsageMs = appsInGroup.sumOf { app ->
            dailyAppUsageDao.getSpecificAppUsageForDate(app.package_name, todayString)?.usageTimeMillis ?: 0L
        }

        val currentSessionMs = clock.currentTimeMillis() - currentSessionStart
        val totalUsageMs = historicalUsageMs + currentSessionMs
        val limitMs = TimeUnit.MINUTES.toMillis(group.time_limit_minutes.toLong())

        if (totalUsageMs > limitMs && !snoozeManager.isGroupSnoozed(group.id)) {
            fireBlockingIntent(packageName, group.id)
            monitoringJob?.cancel()
        }
    }

    private fun fireBlockingIntent(packageName: String, groupId: Long) {
        val intent = Intent(applicationContext, BlockingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BlockingActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockingActivity.EXTRA_GROUP_ID, groupId)
        }
        applicationContext.startActivity(intent)
    }

    fun onAppStopped(packageName: String) {
        if (packageName == currentPackage) {
            monitoringJob?.cancel()
            currentPackage = null
        }
    }
}