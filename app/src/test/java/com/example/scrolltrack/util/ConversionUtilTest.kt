package com.example.scrolltrack.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ConversionUtilTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var conversionUtil: ConversionUtil

    // Assume a standard screen density for predictable test results
    private val standardDpi = 160f

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use Robolectric to manipulate display metrics for testing
        val displayMetrics = context.resources.displayMetrics
        displayMetrics.xdpi = standardDpi
        displayMetrics.ydpi = standardDpi

        settingsRepository = mockk()
        conversionUtil = ConversionUtil(settingsRepository, context)
    }

    @Test
    fun `formatScrollDistanceSync - no calibration - returns correct meters`() = runBlocking {
        // 160 pixels = 1 inch. 1 inch = 0.0254 meters
        val scrollUnits = 160L
        val (value, unit) = conversionUtil.formatScrollDistanceSync(scrollUnits)
        assertThat(unit).isEqualTo("m")
        // Expected: (160 / 160) / 39.3701 = 0.0254
        assertThat(value.toDouble()).isWithin(0.01).of(0.03) // Generous rounding for float precision
    }

    @Test
    fun `formatScrollDistance - no calibration - returns correct distance`() = runBlocking {
        coEvery { settingsRepository.calibrationFactorX } returns flowOf(null)
        coEvery { settingsRepository.calibrationFactorY } returns flowOf(null)

        // 80px X + 80px Y = 160px total = 1 inch
        val (value, unit) = conversionUtil.formatScrollDistance(80, 80)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }

    @Test
    fun `formatScrollDistance - with calibration - returns correct distance`() = runBlocking {
        // Let's say user scrolled 1000px on screen for a 5cm physical distance
        val calibratedPixelsFor5cm = 1000f
        coEvery { settingsRepository.calibrationFactorX } returns flowOf(calibratedPixelsFor5cm)
        coEvery { settingsRepository.calibrationFactorY } returns flowOf(calibratedPixelsFor5cm)

        // The new "dpi" is effectively (1000px / 5cm) * 2.54cm/inch = 508 dpi
        // So 508px should now equal 1 inch.
        // We scroll 254px in each direction, total 508px.
        val (value, unit) = conversionUtil.formatScrollDistance(254, 254)
        assertThat(unit).isEqualTo("m")
        // Expected: (508 / 508) / 39.3701 = 0.0254
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }

    @Test
    fun `formatScrollDistance - handles zero scroll`() = runBlocking {
        coEvery { settingsRepository.calibrationFactorX } returns flowOf(null)
        coEvery { settingsRepository.calibrationFactorY } returns flowOf(null)
        val (value, unit) = conversionUtil.formatScrollDistance(0, 0)
        assertThat(value).isEqualTo("0")
        assertThat(unit).isEqualTo("m")
    }

    @Test
    fun `formatScrollDistance - handles kilometers`() = runBlocking {
        coEvery { settingsRepository.calibrationFactorX } returns flowOf(null)
        coEvery { settingsRepository.calibrationFactorY } returns flowOf(null)

        // 1km = 39370.1 inches. 39370.1 inches * 160 dpi = 6,299,216 pixels
        val scrollForOneKm = 6300000L
        val (value, unit) = conversionUtil.formatScrollDistance(scrollForOneKm, 0)

        assertThat(unit).isEqualTo("km")
        assertThat(value.replace(",", "").toDouble()).isWithin(0.01).of(1.0)
    }
}
