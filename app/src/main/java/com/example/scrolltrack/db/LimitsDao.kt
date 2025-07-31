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

    @Query("SELECT * FROM limit_groups WHERE is_user_visible = 1 ORDER BY name ASC")
    fun getAllVisibleGroups(): Flow<List<LimitGroup>>

    @Transaction
    @Query("SELECT * FROM limit_groups WHERE id = :groupId")
    fun getGroupWithApps(groupId: Long): Flow<GroupWithApps?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLimitedApp(app: LimitedApp)

    @Query("DELETE FROM limited_apps WHERE package_name = :packageName")
    suspend fun deleteLimitedApp(packageName: String)
    
    @Query("SELECT * FROM limited_apps WHERE package_name = :packageName")
    fun getLimitedApp(packageName: String): Flow<LimitedApp?>

    @Query("SELECT EXISTS(SELECT 1 FROM limit_groups WHERE name = :name LIMIT 1)")
    suspend fun groupExists(name: String): Boolean
}