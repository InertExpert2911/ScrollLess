package com.example.scrolltrack.services

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.scrolltrack.db.AppDatabase
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.sqrt

// Custom WorkerFactory for testing
class FlushInferredScrollWorkerFactory(
    private val prefs: SharedPreferences,
    private val dao: RawAppEventDao
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker {
        return FlushInferredScrollWorker(appContext, workerParameters, prefs, dao)
    }
}

@RunWith(RobolectricTestRunner::class)
class FlushInferredScrollWorkerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var db: AppDatabase
    private lateinit var rawAppEventDao: RawAppEventDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("TestInferredScrollPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rawAppEventDao = db.rawAppEventDao()
    }

    @After
    fun tearDown() {
        db.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun `doWork - flushes counters to db and clears prefs`() = runBlocking {
        // Arrange
        val count1 = 100
        val count2 = 25
        prefs.edit()
            .putInt("com.app.one", count1)
            .putInt("com.app.two", count2)
            .commit()

        val worker = TestListenableWorkerBuilder<FlushInferredScrollWorker>(context)
            .setWorkerFactory(FlushInferredScrollWorkerFactory(prefs, rawAppEventDao))
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertThat(result).isEqualTo(ListenableWorker.Result.success())

        // Check DB
        val events = rawAppEventDao.getEventsForDate(DateUtil.getCurrentLocalDateString())
        assertThat(events).hasSize(2)
        val eventOne = events.find { it.packageName == "com.app.one" }
        val eventTwo = events.find { it.packageName == "com.app.two" }
        assertThat(eventOne).isNotNull()
        assertThat(eventTwo).isNotNull()
        assertThat(eventOne!!.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_SCROLL_INFERRED)
        val expectedValue1 = (sqrt(count1.toDouble()) * AppConstants.INFERRED_SCROLL_MULTIPLIER).toLong()
        val expectedValue2 = (sqrt(count2.toDouble()) * AppConstants.INFERRED_SCROLL_MULTIPLIER).toLong()
        assertThat(eventOne.value).isEqualTo(expectedValue1)
        assertThat(eventTwo!!.value).isEqualTo(expectedValue2)


        // Check Prefs are cleared
        assertThat(prefs.all).isEmpty()
    }

    @Test
    fun `doWork - succeeds when no counters to flush`() = runBlocking {
        val worker = TestListenableWorkerBuilder<FlushInferredScrollWorker>(context)
            .setWorkerFactory(FlushInferredScrollWorkerFactory(prefs, rawAppEventDao))
            .build()

        val result = worker.doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val events = rawAppEventDao.getEventsForDate(DateUtil.getCurrentLocalDateString())
        assertThat(events).isEmpty()
    }
} 