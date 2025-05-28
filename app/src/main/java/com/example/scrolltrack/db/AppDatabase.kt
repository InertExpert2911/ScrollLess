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
    entities = [ScrollSessionRecord::class, DailyAppUsageRecord::class, RawAppEvent::class],
    version = 2, // Keep version as is, or increment if schema actually changed since last successful build
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao // Added DAO for new entity
    abstract fun rawAppEventDao(): RawAppEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Removed MIGRATION_3_4 object

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scroll_track_database"
                )
                // Using fallbackToDestructiveMigration during overhaul.
                // Proper migrations can be added later.
                .fallbackToDestructiveMigration()
                // Removed .addMigrations(...) calls
                .build()
                INSTANCE = instance
                instance
            }
        }

        // Removed example MIGRATION_1_2 comment block
    }
}
