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
    version = 3, // Incremented version due to schema change (new table)
    exportSchema = false // Set to true and specify schema location for production
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao // Added DAO for new entity

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Example of a simple migration (not strictly needed if using fallbackToDestructiveMigration for dev)
        // val MIGRATION_2_3 = object : Migration(2, 3) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         // SQL to create the new daily_app_usage table (Room handles this if it's a new table)
        //         // If you were altering an existing table, you'd put ALTER TABLE statements here.
        //         // For adding a new table, Room handles it if it's part of the entities list
        //         // and the version is incremented.
        //         // However, if you want to be explicit or if Room has issues:
        //         db.execSQL(
        //             "CREATE TABLE IF NOT EXISTS `daily_app_usage` (" +
        //                     "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        //                     "`package_name` TEXT NOT NULL, " +
        //                     "`date_string` TEXT NOT NULL, " +
        //                     "`usage_time_millis` INTEGER NOT NULL, " +
        //                     "`last_updated_timestamp` INTEGER NOT NULL, " +
        //                     "UNIQUE (`package_name`, `date_string`))"
        //         )
        //     }
        // }


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scroll_track_database"
                )
                    // For development, fallbackToDestructiveMigration is often easiest.
                    // For production, you MUST provide proper Migration objects.
                    .fallbackToDestructiveMigration(dropAllTables = true) // This will wipe data on version upgrade if no migration
                    // .addMigrations(MIGRATION_2_3) // Example if you were providing a specific migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
