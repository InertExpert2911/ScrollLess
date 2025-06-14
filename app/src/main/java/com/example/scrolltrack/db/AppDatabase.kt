package com.example.scrolltrack.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main database class for the application.
 * Includes tables for ScrollSessionRecord and DailyAppUsageRecord.
 */
@Database(
    entities = [ScrollSessionRecord::class, DailyAppUsageRecord::class, RawAppEvent::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollSessionDao(): ScrollSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun rawAppEventDao(): RawAppEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scroll_track_database"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
