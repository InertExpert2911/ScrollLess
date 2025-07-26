package com.example.scrolltrack.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.scrolltrack.data.AppMetadataRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppMetadataSyncWorkerTest {

    private lateinit var worker: AppMetadataSyncWorker
    private lateinit var appMetadataRepository: AppMetadataRepository
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setUp() {
        appMetadataRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
    }

    @Test
    fun `test install or update action calls repository`() = runBlocking {
        val inputData = workDataOf(
            AppMetadataSyncWorker.KEY_PACKAGE_NAME to "com.example.app",
            AppMetadataSyncWorker.KEY_ACTION to AppMetadataSyncWorker.ACTION_INSTALL_OR_UPDATE
        )
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns inputData
        worker = AppMetadataSyncWorker(context, workerParams, appMetadataRepository)

        val result = worker.doWork()

        coVerify { appMetadataRepository.handleAppInstalledOrUpdated("com.example.app") }
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `test uninstall action calls repository`() = runBlocking {
        val inputData = workDataOf(
            AppMetadataSyncWorker.KEY_PACKAGE_NAME to "com.example.app",
            AppMetadataSyncWorker.KEY_ACTION to AppMetadataSyncWorker.ACTION_UNINSTALL
        )
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns inputData
        worker = AppMetadataSyncWorker(context, workerParams, appMetadataRepository)

        val result = worker.doWork()

        coVerify { appMetadataRepository.handleAppUninstalled("com.example.app") }
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `test repository failure returns retry`() = runBlocking {
        coEvery { appMetadataRepository.handleAppInstalledOrUpdated(any()) } throws Exception()
        val inputData = workDataOf(
            AppMetadataSyncWorker.KEY_PACKAGE_NAME to "com.example.app",
            AppMetadataSyncWorker.KEY_ACTION to AppMetadataSyncWorker.ACTION_INSTALL_OR_UPDATE
        )
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns inputData
        worker = AppMetadataSyncWorker(context, workerParams, appMetadataRepository)

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun `test invalid input returns failure`() = runBlocking {
        val inputData = workDataOf(AppMetadataSyncWorker.KEY_PACKAGE_NAME to "com.example.app")
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns inputData
        worker = AppMetadataSyncWorker(context, workerParams, appMetadataRepository)

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
    }
}