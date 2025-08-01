package com.example.scrolltrack.data

import androidx.room.withTransaction
import com.example.scrolltrack.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitsRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val limitsDao: LimitsDao
) : LimitsRepository {

    override fun getCustomGroups(): Flow<List<LimitGroup>> {
        return limitsDao.getCustomGroups()
    }

    override fun getQuickLimitedGroups(): Flow<List<LimitGroup>> {
        return limitsDao.getQuickLimitedGroups()
    }

    override fun getGroupWithApps(groupId: Long): Flow<GroupWithApps?> {
        return limitsDao.getGroupWithApps(groupId)
    }

    override fun getAllLimitedApps(): Flow<List<LimitedApp>> {
        return limitsDao.getAllLimitedApps()
    }

    override fun getLimitedApp(packageName: String): Flow<LimitedApp?> {
        return limitsDao.getLimitedApp(packageName)
    }

    override fun getLimitedApps(packageNames: List<String>): Flow<List<LimitedApp>> {
        return limitsDao.getLimitedApps(packageNames)
    }

    override suspend fun getLimitsForApps(packageNames: List<String>): Map<String, LimitGroup> {
        val limitedApps = limitsDao.getLimitedApps(packageNames).firstOrNull() ?: return emptyMap()
        return limitedApps.mapNotNull { limitedApp ->
            val group = limitsDao.getGroupWithApps(limitedApp.group_id).firstOrNull()?.group
            if (group != null) {
                limitedApp.package_name to group
            } else {
                null
            }
        }.toMap()
    }

    override suspend fun groupExists(name: String): Boolean {
        return limitsDao.groupExists(name)
    }

    override suspend fun createGroup(name: String, timeLimitMinutes: Int): Long {
        val group = LimitGroup(
            name = name,
            time_limit_minutes = timeLimitMinutes,
            group_type = LimitGroupType.CUSTOM_GROUP
        )
        return limitsDao.insertGroup(group)
    }

    override suspend fun updateGroup(group: LimitGroup) {
        limitsDao.updateGroup(group)
    }

    override suspend fun deleteGroup(group: LimitGroup) {
        limitsDao.deleteGroup(group)
    }

    override suspend fun addAppToGroup(packageName: String, groupId: Long) {
        appDatabase.withTransaction {
            removeAppFromAnyGroup(packageName)
            limitsDao.insertOrUpdateLimitedApp(LimitedApp(packageName, groupId))
        }
    }

    override suspend fun setAppLimit(packageName: String, limitMinutes: Int) {
        appDatabase.withTransaction {
            removeAppFromAnyGroup(packageName)

            val group = LimitGroup(
                name = "invisible_group_for_$packageName",
                time_limit_minutes = limitMinutes,
                group_type = LimitGroupType.QUICK_LIMIT
            )
            val newGroupId = limitsDao.insertGroup(group)
            limitsDao.insertOrUpdateLimitedApp(LimitedApp(packageName, newGroupId))
        }
    }

    override suspend fun removeAppLimit(packageName: String) {
        appDatabase.withTransaction {
            removeAppFromAnyGroup(packageName)
        }
    }

    private suspend fun removeAppFromAnyGroup(packageName: String) {
        val limitedApp = limitsDao.getLimitedApp(packageName).firstOrNull()
        if (limitedApp != null) {
            val group = limitsDao.getGroupWithApps(limitedApp.group_id).firstOrNull()?.group

            limitsDao.deleteLimitedApp(packageName)

            if (group != null && group.group_type == LimitGroupType.QUICK_LIMIT) {
                val remainingApps = limitsDao.getGroupWithApps(group.id).firstOrNull()?.apps
                if (remainingApps.isNullOrEmpty()) {
                    limitsDao.deleteGroup(group)
                }
            }
        }
    }
}
