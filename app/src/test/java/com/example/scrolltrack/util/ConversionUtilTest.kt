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

    private val standardDpi = 160f

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val displayMetrics = context.resources.displayMetrics
        displayMetrics.xdpi = standardDpi
        displayMetrics.ydpi = standardDpi

        settingsRepository = mockk()
        conversionUtil = ConversionUtil(settingsRepository, context)
    }

    @Test
    fun `formatScrollDistanceSync - no calibration - returns correct meters`() = runBlocking {
        val scrollUnits = 160L
        val (value, unit) = conversionUtil.formatScrollDistanceSync(scrollUnits)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }

    @Test
    fun `formatScrollDistance - with dpi from settings - returns correct distance`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(160)

        val (value, unit) = conversionUtil.formatScrollDistance(80, 80)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }

    @Test
    fun `formatScrollDistance - handles zero scroll`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(160)
        val (value, unit) = conversionUtil.formatScrollDistance(0, 0)
        assertThat(value).isEqualTo("0")
        assertThat(unit).isEqualTo("m")
    }

    @Test
    fun `formatScrollDistance - handles kilometers`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(160)

        val scrollForOneKm = 6300000L
        val (value, unit) = conversionUtil.formatScrollDistance(scrollForOneKm, 0)

        assertThat(unit).isEqualTo("km")
        assertThat(value.replace(",", "").toDouble()).isWithin(0.01).of(1.0)
    }

    @Test
    fun `formatScrollDistance with high DPI returns smaller distance`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(480) // High DPI

        val (value, unit) = conversionUtil.formatScrollDistance(160, 0)

        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.01) // Smaller distance
    }

    @Test
    fun `formatScrollDistance with low DPI returns larger distance`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(120) // Low DPI

        val (value, unit) = conversionUtil.formatScrollDistance(160, 0)

        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03) // Larger distance
    }

    @Test
    fun `formatScrollDistance with zero DPI falls back to default`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(0) // Fallback DPI

        val (value, unit) = conversionUtil.formatScrollDistance(160, 0)

        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }

    @Test
    fun `formatUnits below 1000 returns number`() {
        val result = conversionUtil.formatUnits(999)
        assertThat(result).isEqualTo("999")
    }

    @Test
    fun `formatUnits for thousands returns K`() {
        val result = conversionUtil.formatUnits(12345)
        assertThat(result).isEqualTo("12.3K")
    }

    @Test
    fun `formatUnits for millions returns M`() {
        val result = conversionUtil.formatUnits(1234567)
        assertThat(result).isEqualTo("1.2M")
    }

    @Test
    fun `formatUnits with 1000 returns 1K`() {
        val result = conversionUtil.formatUnits(1000)
        assertThat(result).isEqualTo("1.0K")
    }

    @Test
    fun `formatUnits with 0 returns 0`() {
        val result = conversionUtil.formatUnits(0)
        assertThat(result).isEqualTo("0")
    }

    @Test
    fun `formatScrollDistance with X-only scroll`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(160)
        val (value, unit) = conversionUtil.formatScrollDistance(160, 0)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }

    @Test
    fun `formatScrollDistance with Y-only scroll`() = runBlocking {
        coEvery { settingsRepository.screenDpi } returns flowOf(160)
        val (value, unit) = conversionUtil.formatScrollDistance(0, 160)
        assertThat(unit).isEqualTo("m")
        assertThat(value.toDouble()).isWithin(0.01).of(0.03)
    }
}
