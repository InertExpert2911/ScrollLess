package com.example.scrolltrack.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyProcessingWorkerTest {

    private lateinit var worker: DailyProcessingWorker
    private lateinit var scrollDataRepository: ScrollDataRepository
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setUp() {
        scrollDataRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        worker = DailyProcessingWorker(context, workerParams, scrollDataRepository)
    }

    @Test
    fun `test worker processes today and yesterday`() = runBlocking {
        val today = DateUtil.getCurrentLocalDateString()
        val yesterday = DateUtil.getPastDateString(1)
        val fakeForegroundApp = "com.fake.app"
        coEvery { scrollDataRepository.getCurrentForegroundApp() } returns fakeForegroundApp

        val result = worker.doWork()

        coVerify { scrollDataRepository.processAndSummarizeDate(yesterday) }
        coVerify { scrollDataRepository.processAndSummarizeDate(today, fakeForegroundApp) }
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `test repository failure returns retry`() = runBlocking {
        coEvery { scrollDataRepository.processAndSummarizeDate(any()) } throws Exception()

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }
    
    @Test
    fun `test worker processes today with null foreground app`() = runBlocking {
        val today = DateUtil.getCurrentLocalDateString()
        val yesterday = DateUtil.getPastDateString(1)
        coEvery { scrollDataRepository.getCurrentForegroundApp() } returns null

        val result = worker.doWork()

        coVerify { scrollDataRepository.processAndSummarizeDate(yesterday) }
        coVerify { scrollDataRepository.processAndSummarizeDate(today, null) }
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
}