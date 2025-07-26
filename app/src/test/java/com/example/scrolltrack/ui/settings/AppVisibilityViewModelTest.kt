package com.example.scrolltrack.ui.settings

import android.content.Context
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.db.AppMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AppVisibilityViewModelTest {

    private lateinit var viewModel: AppVisibilityViewModel
    private lateinit var appMetadataRepository: AppMetadataRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appMetadataRepository = mockk(relaxed = true)
        val context: Context = mockk(relaxed = true)
        viewModel = AppVisibilityViewModel(appMetadataRepository, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test filter by visible`() = runTest(testDispatcher) {
        val apps = listOf(
            AppMetadata("com.example.visible", "Visible App", isUserVisible = true, isSystemApp = false, userHidesOverride = false, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1),
            AppMetadata("com.example.hidden", "Hidden App", isUserVisible = true, isSystemApp = true, userHidesOverride = true, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1),
            AppMetadata("com.example.default_visible", "Default Visible App", isUserVisible = true, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1)
        )
        coEvery { appMetadataRepository.getAllMetadata() } returns flowOf(apps)

        viewModel.setVisibilityFilter(VisibilityFilter.VISIBLE)
        viewModel.loadApps()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value as AppVisibilityUiState.Success
        assertThat(uiState.apps.map { it.packageName }).containsExactly("com.example.visible", "com.example.default_visible")
    }

    @Test
    fun `test filter by hidden`() = runTest(testDispatcher) {
        val apps = listOf(
            AppMetadata("com.example.visible", "Visible App", isUserVisible = true, isSystemApp = false, userHidesOverride = false, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1),
            AppMetadata("com.example.hidden", "Hidden App", isUserVisible = true, isSystemApp = true, userHidesOverride = true, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1),
            AppMetadata("com.example.default_hidden", "Default Hidden App", isUserVisible = false, isSystemApp = true, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1)
        )
        coEvery { appMetadataRepository.getAllMetadata() } returns flowOf(apps)

        viewModel.setVisibilityFilter(VisibilityFilter.HIDDEN)
        viewModel.loadApps()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value as AppVisibilityUiState.Success
        assertThat(uiState.apps.map { it.packageName }).containsExactly("com.example.hidden", "com.example.default_hidden")
    }

    @Test
    fun `test toggle non-interactive apps`() = runTest(testDispatcher) {
        val apps = listOf(
            AppMetadata("com.example.interactive", "Interactive App", isUserVisible = true, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1),
            AppMetadata("com.example.non_interactive", "Non-Interactive App", isUserVisible = false, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1)
        )
        coEvery { appMetadataRepository.getAllMetadata() } returns flowOf(apps)

        viewModel.toggleShowNonInteractiveApps() // Set to true
        viewModel.loadApps()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value as AppVisibilityUiState.Success
        assertThat(uiState.apps.map { it.packageName }).containsExactly("com.example.interactive", "com.example.non_interactive")

        viewModel.toggleShowNonInteractiveApps() // Set to false
        viewModel.loadApps()
        advanceUntilIdle()

        val uiState2 = viewModel.uiState.value as AppVisibilityUiState.Success
        assertThat(uiState2.apps.map { it.packageName }).containsExactly("com.example.interactive")
    }

    @Test
    fun `test set app visibility`() = runTest(testDispatcher) {
        val apps = listOf(
            AppMetadata("com.example.app", "Test App", isUserVisible = true, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1)
        )
        coEvery { appMetadataRepository.getAllMetadata() } returns flowOf(apps)
        viewModel.loadApps()
        advanceUntilIdle()

        viewModel.setAppVisibility("com.example.app", VisibilityState.HIDDEN)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value as AppVisibilityUiState.Success
        assertThat(uiState.apps.find { it.packageName == "com.example.app" }?.visibilityState).isEqualTo(VisibilityState.HIDDEN)
    }
}
