package com.example.scrolltrack.util

import android.content.Context
import android.util.Log
import com.example.scrolltrack.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ConversionUtil @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val INCHES_PER_METER = 39.3701
        private const val METERS_PER_KILOMETER = 1000.0
        private const val PHYSICAL_DISTANCE_CM = 5.0 // The reference distance used during calibration
    }

    suspend fun formatScrollDistance(
        scrollUnitsX: Long,
        scrollUnitsY: Long,
    ): Pair<String, String> {
        if (scrollUnitsX <= 0 && scrollUnitsY <= 0) return "0" to "m"

        val factorX = settingsRepository.calibrationFactorX.first()
        val factorY = settingsRepository.calibrationFactorY.first()

        val ydpi = context.resources.displayMetrics.ydpi
        val xdpi = context.resources.displayMetrics.xdpi

        val pixelsPerInchY = if (factorY != null && factorY > 0) {
            factorY / PHYSICAL_DISTANCE_CM.toFloat() * 2.54f // pixels/cm -> pixels/inch
        } else {
            ydpi
        }

        val pixelsPerInchX = if (factorX != null && factorX > 0) {
            factorX / PHYSICAL_DISTANCE_CM.toFloat() * 2.54f // pixels/cm -> pixels/inch
        } else {
            xdpi
        }

        if (pixelsPerInchX <= 0f || pixelsPerInchY <= 0f) return "N/A" to "error"

        val inchesScrolledY = scrollUnitsY.toDouble() / pixelsPerInchY
        val inchesScrolledX = scrollUnitsX.toDouble() / pixelsPerInchX

        // For now, we sum the distances. In the future, we could use hypotenuse if needed.
        val totalInchesScrolled = inchesScrolledX + inchesScrolledY
        val metersScrolled = totalInchesScrolled / INCHES_PER_METER

        return metersToFormattedPair(metersScrolled)
    }


    fun formatScrollDistanceSync(scrollUnits: Long): Pair<String, String> {
        if (scrollUnits <= 0) return "0" to "m"
        val ydpi = context.resources.displayMetrics.ydpi
        if (ydpi <= 0f) return "N/A" to "error"

        val inchesScrolled = scrollUnits.toDouble() / ydpi
        val metersScrolled = inchesScrolled / INCHES_PER_METER

        return metersToFormattedPair(metersScrolled)
    }


    private fun metersToFormattedPair(metersScrolled: Double): Pair<String, String> {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        return when {
            metersScrolled >= METERS_PER_KILOMETER -> {
                numberFormat.maximumFractionDigits = 2
                val km = metersScrolled / METERS_PER_KILOMETER
                numberFormat.format(km) to "km"
            }
            else -> {
                if (metersScrolled < 1.0 && metersScrolled > 0) {
                    numberFormat.maximumFractionDigits = 2
                } else {
                    numberFormat.maximumFractionDigits = 0
                }
                numberFormat.format(metersScrolled) to "m"
            }
        }
    }

    fun formatUnits(units: Long): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        return when {
            units >= 1_000_000 -> {
                numberFormat.maximumFractionDigits = 1
                val millions = units / 1_000_000.0
                "${numberFormat.format(millions)}M"
            }
            units >= 1_000 -> {
                numberFormat.maximumFractionDigits = 1
                val thousands = units / 1_000.0
                "${numberFormat.format(thousands)}K"
            }
            else -> {
                units.toString()
            }
        }
    }
}
