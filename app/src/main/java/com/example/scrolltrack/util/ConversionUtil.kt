package com.example.scrolltrack.util

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import kotlin.math.roundToInt
import java.text.NumberFormat
import java.util.Locale
import com.example.scrolltrack.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversionUtil @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val INCHES_PER_METER = 39.3701
        private const val METERS_PER_KILOMETER = 1000.0
        private const val INCHES_PER_MILE = 63360.0
        private const val METERS_PER_MILE = 1609.34
    }

    /**
     * Converts scroll units (approximated as pixels) to estimated physical distance.
     * This is the main suspend function that accounts for user calibration.
     * @param scrollUnits The total accumulated scroll units.
     * @param context Context to access display metrics.
     * @return Pair<Formatted String, Unit String> e.g., ("1.23", "km")
     */
    suspend fun formatScrollDistance(scrollUnits: Long, context: Context): Pair<String, String> {
        if (scrollUnits <= 0) return "0" to "m"

        val customFactor = settingsRepository.calibrationFactor.first()
        val pixelsPerInch = if (customFactor != null && customFactor > 0) {
            // The customFactor is stored as pixels per meter. We need pixels per inch.
            customFactor / INCHES_PER_METER.toFloat()
        } else {
            context.resources.displayMetrics.ydpi
        }

        if (pixelsPerInch <= 0f) return "N/A" to "error"

        return scrollUnitsToFormattedPair(scrollUnits, pixelsPerInch)
    }

    /**
     * A synchronous version of formatScrollDistance that uses a default DPI.
     * This is suitable for UI components like charts where context might not be available
     * and a suspend function can't be used.
     * @param scrollUnits The total accumulated scroll units.
     * @param context Context to get resources.
     * @return Pair<Formatted String, Unit String> e.g., ("1.23", "km")
     */
    fun formatScrollDistanceSync(scrollUnits: Long, context: Context): Pair<String, String> {
        if (scrollUnits <= 0) return "0" to "m"
        val pixelsPerInch = context.resources.displayMetrics.ydpi
        if (pixelsPerInch <= 0f) return "N/A" to "error"
        return scrollUnitsToFormattedPair(scrollUnits, pixelsPerInch)
    }

    /**
     * Core conversion and formatting logic, shared by both sync and async functions.
     */
    private fun scrollUnitsToFormattedPair(scrollUnits: Long, pixelsPerInch: Float): Pair<String, String> {
        val inchesScrolled = scrollUnits.toDouble() / pixelsPerInch
        val metersScrolled = inchesScrolled / INCHES_PER_METER

        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        return when {
            metersScrolled >= METERS_PER_KILOMETER -> {
                numberFormat.maximumFractionDigits = 2
                val km = metersScrolled / METERS_PER_KILOMETER
                numberFormat.format(km) to "km"
            }
            else -> { // Less than 1 km, always show in meters
                if (metersScrolled < 1.0 && metersScrolled > 0) {
                    numberFormat.maximumFractionDigits = 2 // e.g., "0.75 m"
                } else {
                    numberFormat.maximumFractionDigits = 0 // e.g., "123 m"
                }
                numberFormat.format(metersScrolled) to "m"
            }
        }
    }

    /**
     * Converts scroll units (approximated as pixels) to estimated physical distance in kilometers.
     * This version attempts to use a 'standard' or 'average' DPI if context is not available
     * or as a fallback, though using actual DPI from context is preferred for accuracy.
     * For chart data, we might not have context readily available for every data point processing.
     *
     * @param scrollUnits The total accumulated scroll units.
     * @param dpi Device's Dots Per Inch (preferably ydpi for vertical scroll). If null, a default is used.
     * @return Kilometers as Float.
     */
    fun scrollUnitsToKilometersValue(scrollUnits: Long, dpi: Float? = null): Float {
        if (scrollUnits <= 0) return 0.0f

        val effectiveDpi = dpi ?: 160f // Default DPI (mdpi), adjust as needed or make it a settable default

        if (effectiveDpi <= 0f) {
            Log.w("ConversionUtil", "Invalid DPI ($effectiveDpi) for scrollUnitsToKilometersValue. Returning 0km.")
            return 0.0f
        }

        val inchesScrolled = scrollUnits.toDouble() / effectiveDpi
        val metersScrolled = inchesScrolled / INCHES_PER_METER
        val kilometers = metersScrolled / METERS_PER_KILOMETER

        return kilometers.toFloat()
    }
}
