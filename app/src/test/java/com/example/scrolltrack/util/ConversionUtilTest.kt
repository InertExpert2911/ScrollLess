package com.example.scrolltrack.util

import android.content.Context
import android.util.DisplayMetrics
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ConversionUtilTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var conversionUtil: ConversionUtil
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Mock the display metrics to ensure consistent test results
        val displayMetrics = DisplayMetrics().apply {
            xdpi = 160f
            ydpi = 160f
        }
        val resources = mockk<android.content.res.Resources>()
        every { resources.displayMetrics } returns displayMetrics
        val mockContext = mockk<Context>()
        every { mockContext.resources } returns resources
        every { mockContext.applicationContext } returns context
        
        mockSettingsRepository = mockk()
        conversionUtil = ConversionUtil(mockSettingsRepository, mockContext)
    }

    @Test
    fun `formatScrollDistance with no calibration returns correct values`() = runTest {
        coEvery { mockSettingsRepository.calibrationFactorX } returns flowOf(null)
        coEvery { mockSettingsRepository.calibrationFactorY } returns flowOf(null)

        // 1 inch of scroll
        val (value, unit) = conversionUtil.formatScrollDistance(160, 0) 
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.0254)
    }

    @Test
    fun `formatScrollDistance with calibration returns correct values`() = runTest {
        // Assume 1000 pixels / 5cm 
        coEvery { mockSettingsRepository.calibrationFactorX } returns flowOf(1000f)
        coEvery { mockSettingsRepository.calibrationFactorY } returns flowOf(1000f)

        // 1000 pixels scroll should be 5 cm = 0.05 m
        val (value, unit) = conversionUtil.formatScrollDistance(1000, 0)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.05)
    }

    @Test
    fun `formatScrollDistance handles combined X and Y scroll`() = runTest {
        coEvery { mockSettingsRepository.calibrationFactorX } returns flowOf(null)
        coEvery { mockSettingsRepository.calibrationFactorY } returns flowOf(null)

        // 1 inch scroll X, 1 inch scroll Y
        val (value, unit) = conversionUtil.formatScrollDistance(160, 160)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.0508) // 2 * 0.0254
    }

    @Test
    fun `formatScrollDistance handles zero scroll`() = runTest {
        coEvery { mockSettingsRepository.calibrationFactorX } returns flowOf(null)
        coEvery { mockSettingsRepository.calibrationFactorY } returns flowOf(null)
        val (value, unit) = conversionUtil.formatScrollDistance(0, 0)
        assertThat(unit).isEqualTo("m")
        assertThat(value).isEqualTo("0")
    }
}
