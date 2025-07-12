package com.example.scrolltrack.services

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Custom WorkerFactory for testing
class InferredScrollWorkerFactory(
    private val prefs: SharedPreferences
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker {
        return InferredScrollWorker(appContext, workerParameters, prefs)
    }
}

@RunWith(RobolectricTestRunner::class)
class InferredScrollWorkerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use a real SharedPreferences instance for the test
        prefs = context.getSharedPreferences("TestInferredScrollPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `doWork increments scroll count for package`() = runBlocking {
        val packageName = "com.test.app"
        val inputData = workDataOf(InferredScrollWorker.KEY_PACKAGE_NAME to packageName)

        // First run
        val worker1 = TestListenableWorkerBuilder<InferredScrollWorker>(
            context = context,
            inputData = inputData
        ).setWorkerFactory(InferredScrollWorkerFactory(prefs)).build()
        val result1 = worker1.doWork()
        assertThat(result1).isEqualTo(ListenableWorker.Result.success())
        assertThat(prefs.getInt(packageName, 0)).isEqualTo(1)

        // Second run
        val worker2 = TestListenableWorkerBuilder<InferredScrollWorker>(
            context = context,
            inputData = inputData
        ).setWorkerFactory(InferredScrollWorkerFactory(prefs)).build()
        val result2 = worker2.doWork()
        assertThat(result2).isEqualTo(ListenableWorker.Result.success())
        assertThat(prefs.getInt(packageName, 0)).isEqualTo(2)
    }


    @Test
    fun `doWork fails if package name is missing`() = runBlocking {
        val worker = TestListenableWorkerBuilder<InferredScrollWorker>(context)
            .setWorkerFactory(InferredScrollWorkerFactory(prefs))
            .build()

        val result = worker.doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }
} 