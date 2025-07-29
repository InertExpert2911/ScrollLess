package com.example.scrolltrack.ui.settings

import android.content.Context
import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.db.AppMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AppVisibilityViewModelTest {

    private lateinit var viewModel: AppVisibilityViewModel
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val allAppsFlow = MutableStateFlow<List<AppMetadata>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { appMetadataRepository.getAllMetadata() } returns allAppsFlow
        val context: Context = mockk(relaxed = true)
        viewModel = AppVisibilityViewModel(appMetadataRepository, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestApp(pkg: String, name: String, isVisible: Boolean, override: Boolean?): AppMetadata {
        return AppMetadata(
            packageName = pkg,
            appName = name,
            isUserVisible = isVisible,
            userHidesOverride = override,
            isSystemApp = false,
            installTimestamp = 0L,
            lastUpdateTimestamp = 0L,
            versionName = "1.0",
            versionCode = 1L
        )
    }

    @Test
    fun `filters by visible and sorts alphabetically`() = runTest {
        val apps = listOf(
            createTestApp("com.c", "C App", true, false),
            createTestApp("com.a", "A App", true, null), // Default visible
            createTestApp("com.b", "B App", false, true) // Hidden
        )

        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(AppVisibilityUiState.Loading::class.java)
            allAppsFlow.value = apps
            
            viewModel.setVisibilityFilter(VisibilityFilter.VISIBLE)
            advanceUntilIdle()
            
            // It will emit loading, then success from the initial load, then loading, then success from the filter change
            awaitItem() // initial success
            awaitItem() // loading
            val state = awaitItem() as AppVisibilityUiState.Success

            assertThat(state.apps.map { it.appName }).containsExactly("A App", "C App").inOrder()
        }
    }

    @Test
    fun `setAppVisibility updates state and persists to repository`() = runTest {
        val app = createTestApp("com.app", "Test App", true, null)
        allAppsFlow.value = listOf(app)

        viewModel.uiState.test {
            awaitItem() // Loading
            val initialState = awaitItem() as AppVisibilityUiState.Success
            assertThat(initialState.apps.first().visibilityState).isEqualTo(VisibilityState.DEFAULT)

            viewModel.setAppVisibility("com.app", VisibilityState.HIDDEN)
            
            val updatedState = awaitItem() as AppVisibilityUiState.Success
            assertThat(updatedState.apps.first().visibilityState).isEqualTo(VisibilityState.HIDDEN)

            coVerify { appMetadataRepository.updateUserHidesOverride("com.app", true) }
        }
    }

    @Test
    fun `error state is emitted when repository throws exception`() = runTest {
        coEvery { appMetadataRepository.getAllMetadata() } returns flow { throw RuntimeException("DB error") }
        
        val errorViewModel = AppVisibilityViewModel(appMetadataRepository, mockk(relaxed = true), testDispatcher)

        errorViewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(AppVisibilityUiState.Loading::class.java)
            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(AppVisibilityUiState.Error::class.java)
        }
    }
   @Test
   fun `toggleShowNonInteractiveApps updates filter`() = runTest {
       val interactiveApp = createTestApp("com.interactive", "Interactive App", true, null)
       val nonInteractiveApp = createTestApp("com.noninteractive", "Non-Interactive App", false, null)
       allAppsFlow.value = listOf(interactiveApp, nonInteractiveApp)

       viewModel.uiState.test {
           awaitItem() // Loading
           val initialState = awaitItem() as AppVisibilityUiState.Success
           assertThat(initialState.apps.map { it.packageName }).containsExactly("com.interactive")

           viewModel.toggleShowNonInteractiveApps()
           awaitItem() // Loading
           val updatedState = awaitItem() as AppVisibilityUiState.Success
           assertThat(updatedState.apps.map { it.packageName }).containsExactly("com.interactive", "com.noninteractive")
       }
   }
}