package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "limit_groups",
    indices = [Index(value = ["name"], unique = true)] // <-- ADD THIS
)
data class LimitGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val time_limit_minutes: Int,
    val is_enabled: Boolean = true,
    val is_user_visible: Boolean, // To distinguish "invisible" groups
    val creation_timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "limited_apps",
    foreignKeys = [ForeignKey(
        entity = LimitGroup::class,
        parentColumns = ["id"],
        childColumns = ["group_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["group_id"])]
)
data class LimitedApp(
    @PrimaryKey
    val package_name: String,
    val group_id: Long
)

data class GroupWithApps(
    @Embedded
    val group: LimitGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "group_id"
    )
    val apps: List<LimitedApp>
)