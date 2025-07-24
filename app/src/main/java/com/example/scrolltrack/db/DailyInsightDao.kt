package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyInsightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsights(insights: List<DailyInsight>)

    @Query("SELECT * FROM daily_insights WHERE date_string = :dateString")
    fun getInsightsForDate(dateString: String): Flow<List<DailyInsight>>

    @Query("SELECT * FROM daily_insights WHERE date_string IN (:dateStrings)")
    fun getInsightsForDates(dateStrings: List<String>): Flow<List<DailyInsight>>

    @Query("DELETE FROM daily_insights WHERE date_string = :dateString")
    suspend fun deleteInsightsForDate(dateString: String)
}
