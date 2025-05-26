package com.example.scrolltrack.util // Updated package name

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import kotlin.math.roundToInt
import java.text.NumberFormat // Added for comma separation
import java.util.Locale // Added for Locale

object ConversionUtil {
    // Constants for conversion
    private const val INCHES_PER_METER = 39.3701
    private const val METERS_PER_KILOMETER = 1000.0
    private const val INCHES_PER_MILE = 63360.0

    /**
     * Formats a distance in meters into a string with comma separation and " m" suffix.
     * If the distance is >= 10,000m, it formats it as kilometers (e.g., "12.34 km").
     */
    private fun formatMeters(meters: Double): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        return if (meters >= METERS_PER_KILOMETER * 10) { // 10,000 meters
            numberFormat.maximumFractionDigits = 2
            numberFormat.format(meters / METERS_PER_KILOMETER) + " km"
        } else {
            numberFormat.maximumFractionDigits = 0 // No decimals for meters usually, unless very small
            if (meters < 1 && meters > 0) {
                 numberFormat.maximumFractionDigits = 2 // Show decimals for less than 1 meter
            }
            numberFormat.format(meters) + " m"
        }
    }

    /**
     * Converts scroll units (approximated as pixels) to estimated physical distance.
     * @param scrollUnits The total accumulated scroll units.
     * @param context Context to access display metrics.
     * @return Pair<Formatted Meters String, Formatted Miles String> e.g., ("1,234 m", "0.76 miles")
     */
    fun formatScrollDistance(scrollUnits: Long, context: Context): Pair<String, String> {
        if (scrollUnits <= 0) return "0 m" to "0.00 miles"

        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        // Using ydpi as a primary reference for vertical scrolling,
        // can be refined if xdpi and ydpi differ significantly and both scrolls are common.
        val dpi = displayMetrics.ydpi
        if (dpi <= 0f) { // ydpi is float
            Log.w("ConversionUtil", "Invalid DPI detected: $dpi. Cannot calculate distance.")
            return "N/A m" to "N/A miles"
        }

        val inchesScrolled = scrollUnits.toDouble() / dpi
        val metersScrolled = inchesScrolled / INCHES_PER_METER
        val miles = inchesScrolled / INCHES_PER_MILE

        val formattedMeters = formatMeters(metersScrolled)
        val formattedMiles = String.format(Locale.getDefault(), "%.2f miles", miles)

        return formattedMeters to formattedMiles
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
