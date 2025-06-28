package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata")
data class AppMetadata(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    var appName: String,

    @ColumnInfo(name = "version_name")
    var versionName: String?,

    @ColumnInfo(name = "version_code")
    var versionCode: Long,

    @ColumnInfo(name = "is_system_app")
    var isSystemApp: Boolean,

    @ColumnInfo(name = "is_installed")
    var isInstalled: Boolean = true,

    @ColumnInfo(name = "is_icon_cached")
    var isIconCached: Boolean = false,

    @ColumnInfo(name = "install_timestamp")
    var installTimestamp: Long,

    @ColumnInfo(name = "last_update_timestamp")
    var lastUpdateTimestamp: Long
) 