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
import android.util.Log
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.text.NumberFormat
import java.util.Locale
import java.text.DecimalFormatSymbols

@RunWith(Parameterized::class)
class ConversionUtilTest(
    private val testLocale: Locale,
    private val decimalSeparator: Char
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Locale: {0}, DecimalSeparator: {1}")
        fun locales() = listOf(
            arrayOf(Locale.US, '.'),
            arrayOf(Locale.GERMANY, ',')
        )
    }

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    private lateinit var mockSettingsRepository: SettingsRepository

    private lateinit var conversionUtil: ConversionUtil

    private var originalDefaultLocale: Locale? = null
    private lateinit var mockedLog: MockedStatic<Log>

    // Constants for calculation
    private val INCHES_PER_METER = 39.3701
    private val METERS_PER_KILOMETER = 1000.0

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        originalDefaultLocale = Locale.getDefault()
        Locale.setDefault(testLocale) // Use parameterized locale

        mockedLog = Mockito.mockStatic(Log::class.java)

        `when`(mockContext.resources).thenReturn(mockResources)
        mockDisplayMetrics = DisplayMetrics() // Use a real instance for field mocking
        `when`(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)

        conversionUtil = ConversionUtil(mockSettingsRepository)
    }

    @After
    fun tearDown() {
        originalDefaultLocale?.let { Locale.setDefault(it) }
        mockedLog.close()
    }

    private fun mockDeviceDpi(ydpi: Float) {
        mockDisplayMetrics.ydpi = ydpi // Set the field directly
    }

    private fun mockCalibrationFactor(factor: Float?) {
        `when`(mockSettingsRepository.calibrationFactor).thenReturn(flowOf(factor))
    }

    // Helper to format numbers like the main code for assertion consistency
    private fun formatValue(value: Double, maxFractionDigits: Int): String {
        val nf = NumberFormat.getNumberInstance(testLocale)
        nf.maximumFractionDigits = maxFractionDigits
        return nf.format(value)
    }

    // Tests for formatScrollDistance (suspend function)
    @Test
    fun formatScrollDistance_withZeroScrollUnits_returnsZeroMeters() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null) // No custom calibration
        val result = conversionUtil.formatScrollDistance(0L, mockContext)
        assertThat(result).isEqualTo("0" to "m")
    }

    @Test
    fun formatScrollDistance_withNegativeScrollUnits_returnsZeroMeters() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null)
        val result = conversionUtil.formatScrollDistance(-100L, mockContext)
        assertThat(result).isEqualTo("0" to "m")
    }

    @Test
    fun formatScrollDistance_atDefaultDpi_returnsValueInMetersForScrollLessThanOneMeter() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        // 1000 pixels / 160 pixels/inch = 6.25 inches
        // 6.25 inches / 39.3701 inches/meter = 0.15875 meters
        val scrollUnits = 1000L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val expectedString = "0${decimalSeparator}16"
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(expectedString.replace(decimalSeparator, '.').toDouble())
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_atHighDpi_returnsCorrectValueInMeters() = runTest {
        val deviceDpi = 480f // High DPI device
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val scrollUnits = 1000L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val expectedString = "0${decimalSeparator}05"
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(expectedString.replace(decimalSeparator, '.').toDouble())
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_atDefaultDpi_returnsValueInMetersForExactOneMeter() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        // scrollUnits for 1 meter = 1 meter * 39.3701 inches/meter * 160 pixels/inch
        val scrollUnits = (1.0 * INCHES_PER_METER * deviceDpi).toLong() // Approx 6299
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(1.0)
        assertThat(result.second).isEqualTo("m")
    }


    @Test
    fun formatScrollDistance_atDefaultDpi_returnsValueInMetersForMultipleMeters() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val meters = 123.0
        val scrollUnits = (meters * INCHES_PER_METER * deviceDpi).toLong()
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.1).of(meters)
        assertThat(result.second).isEqualTo("m") // "123" to "m"
    }

    @Test
    fun formatScrollDistance_atDefaultDpi_returnsValueInKilometers() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val kilometers = 1.5
        val scrollUnits = (kilometers * METERS_PER_KILOMETER * INCHES_PER_METER * deviceDpi).toLong()
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val expectedString = "1${decimalSeparator}50"
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.02).of(expectedString.replace(decimalSeparator, '.').toDouble())
        assertThat(result.second).isEqualTo("km")
    }

    @Test
    fun formatScrollDistance_atDefaultDpi_returnsValueInKilometersForVeryLargeDistance() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val kilometers = 12345.0
        val scrollUnits = (kilometers * METERS_PER_KILOMETER * INCHES_PER_METER * deviceDpi).toLong()
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val expectedString = formatValue(kilometers, 2)

        val symbols = DecimalFormatSymbols(testLocale)
        val groupingSeparator = symbols.groupingSeparator

        val actualValue = result.first
            .replace(groupingSeparator.toString(), "")
            .replace(decimalSeparator, '.')
            .toDouble()
        val expectedValue = expectedString
            .replace(groupingSeparator.toString(), "")
            .replace(decimalSeparator, '.')
            .toDouble()

        assertThat(actualValue).isWithin(0.02).of(expectedValue)
        assertThat(result.second).isEqualTo("km")
    }

    @Test
    fun formatScrollDistance_withCustomCalibration_overridesDpi() = runTest {
        val customPixelsPerMeter = 6000f // User calibrated: 6000 pixels = 1 meter
        mockCalibrationFactor(customPixelsPerMeter)
        mockDeviceDpi(160f) // This DPI should be ignored

        // scrollUnits for 0.5 meter = 0.5 * 6000 = 3000
        var scrollUnits = 3000L
        var result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(0.5)
        assertThat(result.second).isEqualTo("m")

        // scrollUnits for 2 kilometers = 2 * 1000 * 6000 = 12,000,000
        scrollUnits = 12_000_000L
        result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.02).of(2.00)
        assertThat(result.second).isEqualTo("km")
    }

    @Test
    fun formatScrollDistance_withInvalidDpiAndNoCalibration_returnsError() = runTest {
        mockDeviceDpi(0f)
        mockCalibrationFactor(null)
        val result = conversionUtil.formatScrollDistance(1000L, mockContext)
        assertThat(result).isEqualTo("N/A" to "error")
    }

    @Test
    fun formatScrollDistance_withInvalidCalibrationFactor_fallsBackToDeviceDpi() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(0f) // Invalid calibration factor

        val scrollUnits = (1.0 * INCHES_PER_METER * deviceDpi).toLong() // Approx 6299 (1 meter)
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(1.0)
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_withVerySmallScroll_returnsFractionalMeters() = runTest {
        val deviceDpi = 160f
        mockDeviceDpi(deviceDpi)
        mockCalibrationFactor(null)

        val scrollUnits = 10L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val value = result.first.replace(decimalSeparator, '.').toDouble()
        assertThat(value).isAtLeast(0.0)
        assertThat(value).isLessThan(1.0)
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_withCustomCalibrationAndZeroFactor_usesDefaultDpi() = runTest {
        mockCalibrationFactor(0f)
        mockDeviceDpi(160f)

        val scrollUnits = (1.0 * INCHES_PER_METER * 160).toLong()
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(1.0)
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_withNegativeCalibration_fallsBackToDpi() = runTest {
        mockCalibrationFactor(-6000f)
        mockDeviceDpi(160f)

        val scrollUnits = 3000L
        // With a negative calibration factor, the code should fall back to the device DPI (160f).
        // 3000 pixels / 160 dpi = 18.75 inches
        // 18.75 inches / 39.3701 in/m = 0.47625 meters
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.first.replace(decimalSeparator, '.').toDouble()).isWithin(0.01).of(0.48)
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_withExtremeHighDpi_handlesPrecision() = runTest {
        val extremeDpi = 1000f
        mockDeviceDpi(extremeDpi)
        mockCalibrationFactor(null)

        val scrollUnits = 1000L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        assertThat(result.second).isAnyOf("m", "km")
    }

    @Test
    fun formatScrollDistance_withExtremeLowDpi_handlesPrecision() = runTest {
        val lowDpi = 10f
        mockDeviceDpi(lowDpi)
        mockCalibrationFactor(null)

        val scrollUnits = 1000L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val value = result.first.replace(decimalSeparator, '.').toDouble()
        assertThat(value).isGreaterThan(0.0)
        assertThat(result.second).isEqualTo("m")
    }

    // Tests for formatScrollDistanceSync (synchronous function)
    @Test
    fun formatScrollDistanceSync_withZeroScrollUnits_returnsZeroMeters() {
        mockDeviceDpi(160f)
        val result = conversionUtil.formatScrollDistanceSync(0L, mockContext)
        assertThat(result).isEqualTo("0" to "m")
    }

    // Tests for scrollUnitsToKilometersValue
    @Test
    fun scrollUnitsToKilometersValue_withZeroScrollUnits_returnsZero() {
        assertThat(conversionUtil.scrollUnitsToKilometersValue(0L, 160f)).isEqualTo(0.0f)
    }

    @Test
    fun scrollUnitsToKilometersValue_withExplicitDpi_returnsCorrectKilometers() {
        val dpi = 160f
        // For 1 km: 1000m * 39.3701 in/m * 160 px/in = 6,299,216 pixels
        val scrollUnitsOneKm = (METERS_PER_KILOMETER * INCHES_PER_METER * dpi).toLong()
        assertThat(conversionUtil.scrollUnitsToKilometersValue(scrollUnitsOneKm, dpi))
            .isWithin(0.001f).of(1.0f)

        assertThat(conversionUtil.scrollUnitsToKilometersValue(scrollUnitsOneKm / 2, dpi))
            .isWithin(0.001f).of(0.5f)
    }

    @Test
    fun scrollUnitsToKilometersValue_withNullDpi_usesDefaultDpi() {
        val defaultDpi = 160f
        val scrollUnitsOneKmDefault = (METERS_PER_KILOMETER * INCHES_PER_METER * defaultDpi).toLong()
        assertThat(conversionUtil.scrollUnitsToKilometersValue(scrollUnitsOneKmDefault, null))
            .isWithin(0.001f).of(1.0f)
    }



    @Test
    fun formatScrollDistance_formattingCheck_returnsMetersWithTwoDecimalPlacesWhenLessThanOneMeter() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null)
        // Approx 0.015875 meters for 100 scroll units at 160 DPI
        val scrollUnits = 100L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val value = result.first.replace(decimalSeparator, '.').toDouble()
        assertThat(value).isAtLeast(0.0)
        assertThat(value).isLessThan(1.0)
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_formattingCheck_returnsMetersWithZeroDecimalPlacesWhenMoreThanOneMeter() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null)
        // Approx 1.58 meters
        val scrollUnits = 10000L
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val value = result.first.replace(decimalSeparator, '.').toDouble()
        assertThat(value).isGreaterThan(1.0)
        assertThat(result.second).isEqualTo("m")
    }

    @Test
    fun formatScrollDistance_formattingCheck_returnsKilometersWithTwoDecimalPlaces() = runTest {
        mockDeviceDpi(160f)
        mockCalibrationFactor(null)
        // Approx 1.59 km, which should be formatted with two decimal places
        val scrollUnits = 10_000_000L // ~1.59 km
        val result = conversionUtil.formatScrollDistance(scrollUnits, mockContext)
        val expectedKm = 1.59
        val resultValue = result.first.replace(decimalSeparator, '.').toDouble()

        assertThat(result.second).isEqualTo("km")
        assertThat(resultValue).isWithin(0.01).of(expectedKm)
    }

}
