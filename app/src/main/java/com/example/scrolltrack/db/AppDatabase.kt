package com.example.scrolltrack.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main database class for the application.
 * Includes tables for ScrollSessionRecord and DailyAppUsageRecord.
 */
@Database(
    entities = [ScrollSessionRecord::class, DailyAppUsageRecord::class, RawAppEvent::class, NotificationRecord::class, DailyDeviceSummary::class, AppMetadata::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun rawAppEventDao(): RawAppEventDao
    abstract fun notificationDao(): NotificationDao
    abstract fun dailyDeviceSummaryDao(): DailyDeviceSummaryDao
    abstract fun appMetadataDao(): AppMetadataDao

    // The companion object getDatabase() is now obsolete as Hilt provides the database.
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notification_records ADD COLUMN category TEXT")
    }
}
