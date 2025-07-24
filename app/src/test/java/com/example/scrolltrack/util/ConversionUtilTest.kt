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
}
