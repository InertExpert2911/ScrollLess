package com.example.scrolltrack.services

import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LimitMonitor @Inject constructor(
    private val limitsRepository: LimitsRepository,
    private val dailyAppUsageDao: DailyAppUsageDao
) {
    private var monitoringJob: Job? = null
    val blockedGroups = mutableSetOf<Long>()

    fun startMonitoring(scope: CoroutineScope, packageName: String) {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            val limitedApp = limitsRepository.getLimitedApp(packageName).firstOrNull()

            if (limitedApp == null) {
                blockedGroups.clear()
                return@launch
            }

            val groupId = limitedApp.group_id
            if (groupId in blockedGroups) {
                Timber.tag("LimitMonitor").d("Group $groupId is already blocked, not monitoring.")
                return@launch
            }

            val groupWithApps = limitsRepository.getGroupWithApps(groupId).firstOrNull()
            if (groupWithApps == null) {
                Timber.tag("LimitMonitor").w("Could not find group for ID: $groupId")
                return@launch
            }

            val packageNamesInGroup = groupWithApps.apps.map { it.package_name }
            val todayDateString = DateUtil.getCurrentLocalDateString()
            val limitMillis = TimeUnit.MINUTES.toMillis(groupWithApps.group.time_limit_minutes.toLong())

            while (currentCoroutineContext().isActive) {
                val usageRecords = dailyAppUsageDao.getUsageForAppsOnDate(packageNamesInGroup, todayDateString).first()
                val totalUsage = usageRecords.sumOf { it.usageTimeMillis }

                if (totalUsage >= limitMillis) {
                    Timber.tag("LimitMonitor")
                        .i("Limit breached for group $groupId. Total usage: $totalUsage ms. Firing Blocking Intent.")
                    blockedGroups.add(groupId)
                    // NOTE: The actual intent firing is handled by the service/activity context
                    // This monitor's job is just to detect the breach.
                    stopMonitoring() // Stop after blocking
                } else {
                    Timber.tag("LimitMonitor").v("Group $groupId usage: $totalUsage ms / $limitMillis ms")
                }

                delay(60000L) // Check every minute
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
    }
}