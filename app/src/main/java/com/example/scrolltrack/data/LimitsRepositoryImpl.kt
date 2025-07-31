package com.example.scrolltrack.data

import com.example.scrolltrack.db.GroupWithApps
import com.example.scrolltrack.db.LimitGroup
import com.example.scrolltrack.db.LimitedApp
import com.example.scrolltrack.db.LimitsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitsRepositoryImpl @Inject constructor(
    private val limitsDao: LimitsDao
) : LimitsRepository {

    override fun getAllVisibleGroups(): Flow<List<LimitGroup>> {
        return limitsDao.getAllVisibleGroups()
    }

    override fun getGroupWithApps(groupId: Long): Flow<GroupWithApps?> {
        return limitsDao.getGroupWithApps(groupId)
    }
    
    override fun getLimitedApp(packageName: String): Flow<LimitedApp?> {
        return limitsDao.getLimitedApp(packageName)
    }

    override suspend fun groupExists(name: String): Boolean {
        return limitsDao.groupExists(name)
    }

    override suspend fun createGroup(name: String, timeLimitMinutes: Int): Long {
        val group = LimitGroup(
            name = name,
            time_limit_minutes = timeLimitMinutes,
            is_user_visible = true
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
        removeAppFromAnyGroup(packageName)
        limitsDao.insertOrUpdateLimitedApp(LimitedApp(packageName, groupId))
    }

    override suspend fun setAppLimit(packageName: String, limitMinutes: Int) {
        removeAppFromAnyGroup(packageName)

        val group = LimitGroup(
            name = "invisible_group_for_$packageName",
            time_limit_minutes = limitMinutes,
            is_user_visible = false,
        )
        val newGroupId = limitsDao.insertGroup(group)
        limitsDao.insertOrUpdateLimitedApp(LimitedApp(packageName, newGroupId))
    }

    override suspend fun removeAppLimit(packageName: String) {
        removeAppFromAnyGroup(packageName)
    }

    private suspend fun removeAppFromAnyGroup(packageName: String) {
        val limitedApp = limitsDao.getLimitedApp(packageName).firstOrNull()
        if (limitedApp != null) {
            val group = limitsDao.getGroupWithApps(limitedApp.group_id).firstOrNull()?.group

            limitsDao.deleteLimitedApp(packageName)

            if (group != null && !group.is_user_visible) {
                val remainingApps = limitsDao.getGroupWithApps(group.id).firstOrNull()?.apps
                if (remainingApps.isNullOrEmpty()) {
                    limitsDao.deleteGroup(group)
                }
            }
        }
    }
}