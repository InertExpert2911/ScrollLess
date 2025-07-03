package com.example.scrolltrack.util

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import com.example.scrolltrack.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.text.NumberFormat
import java.util.Locale

class ConversionUtilTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    @Mock
    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    private lateinit var mockSettingsRepository: SettingsRepository

    private lateinit var conversionUtil: ConversionUtil

    private var originalDefaultLocale: Locale? = null

    // Constants for calculation
    private val INCHES_PER_METER = 39.3701
    private val METERS_PER_KILOMETER = 1000.0

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        originalDefaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US) // For consistent number formatting

        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)

        conversionUtil = ConversionUtil(mockSettingsRepository)
    }

    @After
    fun tearDown() {
        originalDefaultLocale?.let { Locale.setDefault(it) }
    }

    private fun mockDeviceDpi(ydpi: Float) {
        `when`(mockDisplayMetrics.ydpi).thenReturn(ydpi)
    }

    private fun mockCalibrationFactor(factor: Float?) {
        `when`(mockSettingsRepository.calibrationFactor).thenReturn(flowOf(factor))
    }

    // Helper to format numbers like the main code for assertion consistency
    private fun formatValue(value: Double, maxFractionDigits: Int): String {
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.maximumFractionDigits = maxFractionDigits
        return nf.format(value)
    }

    // Tests for formatScrollDistance (suspend function)
    @Test
    fun `formatScrollDistance - zero scroll units - returns 0 m`() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null) // No custom calibration
        val result = conversionUtil.formatScrollDistance(0L, mockContext)
        assertThat(result).isEqualTo("0" to "m")
    }

    @Test
    fun `formatScrollDistance - default DPI - less than 1 meter`() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        // 1000 pixels / 160 pixels/inch = 6.25 inches
        // 6.25 inches / 39.3701 inches/meter = 0.15875 meters
        val scrollUnits = 1000L
        val expectedMeters = (scrollUnits / deviceDpi) / INCHES_PER_METER
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(expectedMeters, 2) to "m") // "0.16" to "m"
    }

    @Test
    fun `formatScrollDistance - default DPI - exact 1 meter`() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        // scrollUnits for 1 meter = 1 meter * 39.3701 inches/meter * 160 pixels/inch
        val scrollUnits = (1.0 * INCHES_PER_METER * deviceDpi).toLong() // Approx 6299
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        // Calculation might lead to tiny fractions, check formatting
        assertThat(result.first.toDouble()).isWithin(0.01).of(1.0)
        assertThat(result.second).isEqualTo("m")
    }


    @Test
    fun `formatScrollDistance - default DPI - multiple meters`() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val meters = 123.0
        val scrollUnits = (meters * INCHES_PER_METER * deviceDpi).toLong()
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(meters, 0) to "m") // "123" to "m"
    }

    @Test
    fun `formatScrollDistance - default DPI - converts to kilometers`() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val kilometers = 1.5
        val scrollUnits = (kilometers * METERS_PER_KILOMETER * INCHES_PER_METER * deviceDpi).toLong() // Approx 9,448,824
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(kilometers, 2) to "km") // "1.5" to "km" (or "1.50")
    }

    @Test
    fun `formatScrollDistance - with custom calibration factor (pixels per meter)`() = runTest {
        val customPixelsPerMeter = 6000f // User calibrated: 6000 pixels = 1 meter
        mockCalibrationFactor(customPixelsPerMeter)
        // mockDeviceDpi is not used when calibrationFactor is present

        // scrollUnits for 0.5 meter = 0.5 * 6000 = 3000
        var scrollUnits = 3000L
        var result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(0.5, 2) to "m") // "0.50" to "m"

        // scrollUnits for 2 kilometers = 2 * 1000 * 6000 = 12,000,000
        scrollUnits = 12_000_000L
        result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(2.0, 2) to "km") // "2.00" to "km"
    }

    @Test
    fun `formatScrollDistance - invalid DPI (device ydpi is 0) - no calibration - returns error`() = runTest {
        mockDeviceDpi(0f)
        mockCalibrationFactor(null)
        val result = conversionUtil.formatScrollDistance(1000L, mockContext)
        assertThat(result).isEqualTo("N/A" to "error")
    }

    @Test
    fun `formatScrollDistance - invalid calibration factor (0) - uses device DPI`() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(0f) // Invalid calibration factor

        val scrollUnits = (1.0 * INCHES_PER_METER * deviceDpi).toLong() // Approx 6299 (1 meter)
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.toDouble()).isWithin(0.01).of(1.0)
        assertThat(result.second).isEqualTo("m")
    }


    // Tests for formatScrollDistanceSync (synchronous function)
    @Test
    fun `formatScrollDistanceSync - zero scroll units - returns 0 m`() {
        mockDeviceDpi(160f)
        val result = conversionUtil.formatScrollDistanceSync(0L, mockContext)
        assertThat(result).isEqualTo("0" to "m")
    }

    @Test
    fun `formatScrollDistanceSync - less than 1 meter`() {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        val scrollUnits = 1000L
        val expectedMeters = (scrollUnits / deviceDpi) / INCHES_PER_METER
        val result = conversionUtil.formatScrollDistanceSync(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(expectedMeters, 2) to "m")
    }

    @Test
    fun `formatScrollDistanceSync - converts to kilometers`() {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        val kilometers = 2.5
        val scrollUnits = (kilometers * METERS_PER_KILOMETER * INCHES_PER_METER * deviceDpi).toLong()
        val result = conversionUtil.formatScrollDistanceSync(scrollUnits, mockContext)
        assertThat(result).isEqualTo(formatValue(kilometers, 2) to "km")
    }

    @Test
    fun `formatScrollDistanceSync - invalid DPI (device ydpi is 0) - returns error`() {
        mockDeviceDpi(0f)
        val result = conversionUtil.formatScrollDistanceSync(1000L, mockContext)
        assertThat(result).isEqualTo("N/A" to "error")
    }

    // Tests for scrollUnitsToKilometersValue
    @Test
    fun `scrollUnitsToKilometersValue - zero scroll units`() {
        assertThat(conversionUtil.scrollUnitsToKilometersValue(0L, 160f)).isEqualTo(0.0f)
    }

    @Test
    fun `scrollUnitsToKilometersValue - with explicit DPI`() {
        val dpi = 160f
        // For 1 km: 1000m * 39.3701 in/m * 160 px/in = 6,299,216 pixels
        val scrollUnitsOneKm = (METERS_PER_KILOMETER * INCHES_PER_METER * dpi).toLong()
        assertThat(conversionUtil.scrollUnitsToKilometersValue(scrollUnitsOneKm, dpi))
            .isWithin(0.001f).of(1.0f)

        assertThat(conversionUtil.scrollUnitsToKilometersValue(scrollUnitsOneKm / 2, dpi))
            .isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `scrollUnitsToKilometersValue - with null DPI (uses default 160f)`() {
        val defaultDpi = 160f
        val scrollUnitsOneKmDefault = (METERS_PER_KILOMETER * INCHES_PER_METER * defaultDpi).toLong()
        assertThat(conversionUtil.scrollUnitsToKilometersValue(scrollUnitsOneKmDefault, null))
            .isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `scrollUnitsToKilometersValue - with zero DPI`() {
        assertThat(conversionUtil.scrollUnitsToKilometersValue(1000L, 0f)).isEqualTo(0.0f)
    }

    @Test
    fun `formatScrollDistance - number formatting for meters less than 1`() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null)
        // Approx 0.015875 meters for 100 scroll units at 160 DPI
        val scrollUnits = 100L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo("0.02" to "m") // Should be formatted to two decimal places
    }

    @Test
    fun `formatScrollDistance - number formatting for meters greater than or equal to 1 but less than 1km`() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null)
        // Approx 1.5875 meters for 10000 scroll units at 160 DPI
        var scrollUnits = 10000L
        var result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo("2" to "m") // Should be formatted to zero decimal places for whole number like meters

        // Approx 15.875 meters for 100000 scroll units at 160 DPI
        scrollUnits = 100000L
        result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result).isEqualTo("16" to "m") // Should be formatted to zero decimal places
    }

    @Test
    fun `formatScrollDistanceSync - number formatting for meters less than 1`() {
        mockDeviceDpi(160f)
        // Approx 0.015875 meters for 100 scroll units at 160 DPI
        val scrollUnits = 100L
        val result = conversionUtil.formatScrollDistanceSync(scrollUnits, mockContext)
        assertThat(result).isEqualTo("0.02" to "m")
    }

    @Test
    fun `formatScrollDistanceSync - number formatting for meters greater than or equal to 1 but less than 1km`() {
        mockDeviceDpi(160f)
        // Approx 1.5875 meters for 10000 scroll units at 160 DPI
        var scrollUnits = 10000L
        var result = conversionUtil.formatScrollDistanceSync(scrollUnits, mockContext)
        assertThat(result).isEqualTo("2" to "m")

        // Approx 15.875 meters for 100000 scroll units at 160 DPI
        scrollUnits = 100000L
        result = conversionUtil.formatScrollDistanceSync(scrollUnits, mockContext)
        assertThat(result).isEqualTo("16" to "m")
    }
}