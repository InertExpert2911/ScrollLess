package com.example.scrolltrack.data

import com.example.scrolltrack.db.GroupWithApps
import com.example.scrolltrack.db.LimitGroup
import com.example.scrolltrack.db.LimitedApp
import kotlinx.coroutines.flow.Flow

interface LimitsRepository {
    fun getCustomGroups(): Flow<List<LimitGroup>>
    fun getQuickLimitedGroups(): Flow<List<LimitGroup>>
    fun getAllLimitedApps(): Flow<List<LimitedApp>>
    fun getGroupWithApps(groupId: Long): Flow<GroupWithApps?>
    fun getLimitedApp(packageName: String): Flow<LimitedApp?>
    suspend fun groupExists(name: String): Boolean
    suspend fun createGroup(name: String, timeLimitMinutes: Int): Long
    suspend fun updateGroup(group: LimitGroup)
    suspend fun deleteGroup(group: LimitGroup)
    suspend fun addAppToGroup(packageName: String, groupId: Long)
    suspend fun setAppLimit(packageName: String, limitMinutes: Int)
    suspend fun removeAppLimit(packageName: String)
}