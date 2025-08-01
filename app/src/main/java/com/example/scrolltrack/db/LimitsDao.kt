package com.example.scrolltrack.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LimitsDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: LimitGroup): Long

    @Update
    suspend fun updateGroup(group: LimitGroup)

    @Delete
    suspend fun deleteGroup(group: LimitGroup)

    @Query("SELECT * FROM limit_groups WHERE group_type = 'CUSTOM_GROUP' ORDER BY name ASC")
    fun getCustomGroups(): Flow<List<LimitGroup>>

    @Query("SELECT * FROM limit_groups WHERE group_type = 'QUICK_LIMIT'")
    fun getQuickLimitedGroups(): Flow<List<LimitGroup>>

    @Transaction
    @Query("SELECT * FROM limit_groups WHERE id = :groupId")
    fun getGroupWithApps(groupId: Long): Flow<GroupWithApps?>

    @Query("SELECT * FROM limited_apps")
    fun getAllLimitedApps(): Flow<List<LimitedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLimitedApp(app: LimitedApp)

    @Query("DELETE FROM limited_apps WHERE package_name = :packageName")
    suspend fun deleteLimitedApp(packageName: String)

    @Query("SELECT * FROM limited_apps WHERE package_name = :packageName")
    fun getLimitedApp(packageName: String): Flow<LimitedApp?>

    @Query("SELECT * FROM limited_apps WHERE package_name IN (:packageNames)")
    fun getLimitedApps(packageNames: List<String>): Flow<List<LimitedApp>>

    @Query("SELECT EXISTS(SELECT 1 FROM limit_groups WHERE name = :name LIMIT 1)")
    suspend fun groupExists(name: String): Boolean
}
