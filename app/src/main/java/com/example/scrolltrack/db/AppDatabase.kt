package com.example.scrolltrack.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.db.ScrollSessionDao

/**
 * Main database class for the application.
 * Includes tables for ScrollSessionRecord and DailyAppUsageRecord.
 */
@Database(
    entities = [
        ScrollSessionRecord::class,
        RawAppEvent::class,
        AppMetadata::class,
        DailyDeviceSummary::class,
        DailyAppUsageRecord::class,
        NotificationRecord::class,
        UnlockSessionRecord::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun rawAppEventDao(): RawAppEventDao
    abstract fun notificationDao(): NotificationDao
    abstract fun dailyDeviceSummaryDao(): DailyDeviceSummaryDao
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun unlockSessionDao(): UnlockSessionDao

    // The companion object getDatabase() is now obsolete as Hilt provides the database.
}
