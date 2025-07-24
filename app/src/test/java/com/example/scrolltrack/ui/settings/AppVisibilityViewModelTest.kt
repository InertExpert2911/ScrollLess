package com.example.scrolltrack.ui.settings

import android.content.Context
import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.db.AppMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class AppVisibilityViewModelTest {
    private lateinit var viewModel: AppVisibilityViewModel
    private val appMetadataRepository: AppMetadataRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AppVisibilityViewModel(appMetadataRepository, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel loads and maps app visibility states correctly`() = runTest {
        val mockMetadata = listOf(
            AppMetadata("pkg1", "App 1", "1.0", 1, false, true, true, -1, true, null, 0, 0),
            AppMetadata("pkg2", "App 2", "1.0", 1, true, true, true, -1, false, null, 0, 0),
            AppMetadata("pkg3", "App 3", "1.0", 1, false, true, true, -1, true, true, 0, 0),
            AppMetadata("pkg4", "App 4", "1.0", 1, false, true, true, -1, true, false, 0, 0)
        )
        coEvery { appMetadataRepository.getAllMetadata() } returns flowOf(mockMetadata)
        coEvery { appMetadataRepository.getIconFile(any()) } returns null

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val initialState = awaitItem()
            assertThat(initialState).isInstanceOf(AppVisibilityUiState.Loading::class.java)

            val state = awaitItem()
            assertThat(state).isInstanceOf(AppVisibilityUiState.Success::class.java)
            val successState = state as AppVisibilityUiState.Success
            assertThat(successState.apps).hasSize(4)

            // pkg1: userHidesOverride=null, isUserVisible=true -> DEFAULT
            assertThat(successState.apps.find { it.packageName == "pkg1" }?.visibilityState).isEqualTo(VisibilityState.DEFAULT)
            // pkg2: isUserVisible=false -> HIDDEN
            assertThat(successState.apps.find { it.packageName == "pkg2" }?.visibilityState).isEqualTo(VisibilityState.HIDDEN)
            // pkg3: userHidesOverride=true -> HIDDEN
            assertThat(successState.apps.find { it.packageName == "pkg3" }?.visibilityState).isEqualTo(VisibilityState.HIDDEN)
            // pkg4: userHidesOverride=false -> VISIBLE
            assertThat(successState.apps.find { it.packageName == "pkg4" }?.visibilityState).isEqualTo(VisibilityState.VISIBLE)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
