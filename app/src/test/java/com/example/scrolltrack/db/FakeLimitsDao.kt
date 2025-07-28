package com.example.scrolltrack.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLimitsDao : LimitsDao {

    private val groups = MutableStateFlow<MutableMap<Long, LimitGroup>>(mutableMapOf())
    private val apps = MutableStateFlow<MutableMap<String, LimitedApp>>(mutableMapOf())
    private var nextGroupId = 1L

    override suspend fun insertOrUpdateGroup(group: LimitGroup): Long {
        val groupId = if (group.id == 0L) nextGroupId++ else group.id
        val newGroup = group.copy(id = groupId)
        groups.value[groupId] = newGroup
        groups.value = groups.value // Trigger flow update
        return groupId
    }

    override suspend fun deleteGroup(group: LimitGroup) {
        groups.value.remove(group.id)
        groups.value = groups.value // Trigger flow update
    }

    override fun getAllVisibleGroups(): Flow<List<LimitGroup>> {
        return groups.map { it.values.filter { group -> group.is_user_visible }.sortedBy { it.name } }
    }

    override fun getGroupWithApps(groupId: Long): Flow<GroupWithApps?> {
        return groups.map { groupMap ->
            groupMap[groupId]?.let { group ->
                val associatedApps = apps.value.values.filter { it.group_id == groupId }
                GroupWithApps(group, associatedApps)
            }
        }
    }

    override suspend fun insertOrUpdateLimitedApp(app: LimitedApp) {
        apps.value[app.package_name] = app
        apps.value = apps.value
    }

    override suspend fun deleteLimitedApp(packageName: String) {
        apps.value.remove(packageName)
        apps.value = apps.value
    }

    override fun getLimitedApp(packageName: String): Flow<LimitedApp?> {
        return apps.map { it[packageName] }
    }
}