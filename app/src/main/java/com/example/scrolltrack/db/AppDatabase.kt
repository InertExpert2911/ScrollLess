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
    entities = [ScrollSessionRecord::class, DailyAppUsageRecord::class], // Added DailyAppUsageRecord
    version = 4, // Current version
    exportSchema = true // Recommended to set to true and check schema into VCS
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao // Added DAO for new entity

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 3 to 4: Adds indices to scroll_sessions table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create indices for scroll_sessions table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scroll_sessions_date_string ON scroll_sessions(date_string)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scroll_sessions_package_name ON scroll_sessions(package_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scroll_sessions_date_string_package_name ON scroll_sessions(date_string, package_name)")
            }
        }

        // Example of a simple migration for future reference (e.g., MIGRATION_2_3 was an example)
        // val MIGRATION_2_3 = object : Migration(2, 3) { ... }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scroll_track_database"
                )
                    // Add migrations here instead of fallbackToDestructiveMigration for production
                    .addMigrations(MIGRATION_3_4)
                    // .fallbackToDestructiveMigration(dropAllTables = true) // Removed for robust migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
