package com.example.scrolltrack.ui.detail

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.scrolltrack.ui.model.AppDailyDetailData
import com.example.scrolltrack.util.ConversionUtil
import kotlinx.coroutines.runBlocking
import kotlin.math.max

private const val Y_AXIS_PADDING_FACTOR = 1.15f

@Immutable
class ChartColors(
    val barColor: Color,
    val selectedBarColor: Color,
    val activeBarColor: Color,
    val scrollLineColor: Color,
    val faintAxisColor: Color,
    val labelColor: Color,
    val tooltipBackgroundColor: Color,
    val tooltipTextColor: Color
)

@Immutable
class ChartStyles(
    val axisLabelTextStyle: TextStyle,
    val legendTextStyle: TextStyle,
    val tooltipTextStyle: TextStyle
)

class ChartState(
    val data: List<CombinedAppDailyData>,
    val periodType: ChartPeriodType,
    val colors: ChartColors,
    val styles: ChartStyles,
    private val textMeasurer: TextMeasurer,
    private val context: Context,
    private val conversionUtil: ConversionUtil
) {
    var selectedBarIndex by mutableStateOf<Int?>(null)
        private set

    val hasData = data.isNotEmpty()

    val maxUsageTime = data.maxOfOrNull { it.usageTimeMillis } ?: 1L
    val minUsageTime = 0L

    val maxScrollUnits = data.maxOfOrNull { it.scrollUnits } ?: 1L
    val minScrollUnits = 0L

    private val tickMarkAndLabelPadding = 20f
    private val yAxisLabelUsageTimeWidth: Float = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = styles.axisLabelTextStyle).size.width.toFloat()
    private val yAxisLabelScrollDistWidth: Float = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = styles.axisLabelTextStyle).size.width.toFloat()
    val xAxisLabelHeight: Float = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = styles.axisLabelTextStyle).size.height.toFloat()
    val legendItemHeight: Float = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = styles.axisLabelTextStyle).size.height.toFloat() + 10f
    val legendTotalHeight: Float = legendItemHeight * 2
    val topMargin: Float = tickMarkAndLabelPadding
    val leftMargin: Float = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
    val rightMargin: Float = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding
    val bottomMargin: Float = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight

    val maxUsageTimeForAxis = (maxUsageTime * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)
    val maxScrollUnitsForAxis = (maxScrollUnits * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)

    // This function is no longer responsible for the conversion calculation.
    // It is now a simple formatter for the pre-calculated scroll distance.
    // The final implementation will likely take a formatted string directly.
    // For now, we revert to a simpler state to remove the runBlocking hack.
    fun getTooltipScrollText(index: Int): String {
        if (index >= data.size) {
            return ""
        }
        val selectedData = data[index]
        val (value, unit) = conversionUtil.formatScrollDistanceSync(selectedData.scrollUnits)
        return "$value $unit"
    }

    fun handleTap(tapOffset: Offset, canvasSize: Size) {
        if (periodType == ChartPeriodType.DAILY || !hasData) return

        val chartWidth = canvasSize.width - leftMargin - rightMargin
        val chartHeight = canvasSize.height - bottomMargin - topMargin
        if (chartWidth <= 0 || chartHeight <= 0) return

        val barCount = data.size
        val totalBarWidthFactor = when (periodType) {
            ChartPeriodType.WEEKLY -> 0.6f
            ChartPeriodType.MONTHLY -> 0.7f
            else -> 0.6f
        }
        val totalBarWidth = chartWidth * totalBarWidthFactor
        val barWidth = (totalBarWidth / barCount).coerceAtLeast(2f)
        val barSpacing = (chartWidth - totalBarWidth) / (barCount + 1).coerceAtLeast(1)

        for (index in data.indices) {
            val detail = data[index]
            val barHeightNorm = ((detail.usageTimeMillis - minUsageTime).toFloat() / (maxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
            val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(0f)
            val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
            val barTopCanvas = topMargin + chartHeight - barActualHeight
            val barRight = barLeft + barWidth
            val barBottomCanvas = topMargin + chartHeight

            if (tapOffset.x >= barLeft && tapOffset.x <= barRight && tapOffset.y >= barTopCanvas && tapOffset.y <= barBottomCanvas) {
                selectedBarIndex = if (selectedBarIndex == index) null else index
                return
            }
        }
        selectedBarIndex = null // Tapped outside any bar
    }

    fun resetSelection() {
        selectedBarIndex = null
    }
}

@Composable
fun rememberChartState(
    data: List<CombinedAppDailyData>,
    periodType: ChartPeriodType,
    conversionUtil: ConversionUtil
): ChartState {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    val colors = ChartColors(
        barColor = MaterialTheme.colorScheme.primary,
        selectedBarColor = MaterialTheme.colorScheme.secondary,
        activeBarColor = MaterialTheme.colorScheme.primaryContainer,
        scrollLineColor = MaterialTheme.colorScheme.tertiary,
        faintAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tooltipBackgroundColor = MaterialTheme.colorScheme.inverseSurface,
        tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface
    )

    val styles = ChartStyles(
        axisLabelTextStyle = MaterialTheme.typography.labelSmall.copy(color = colors.labelColor),
        legendTextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        tooltipTextStyle = MaterialTheme.typography.bodySmall.copy(color = colors.tooltipTextColor)
    )

    val state = remember(data, periodType, colors, styles, textMeasurer, context, conversionUtil) {
        ChartState(data, periodType, colors, styles, textMeasurer, context, conversionUtil)
    }

    LaunchedEffect(periodType, data) {
        state.resetSelection()
    }

    return state
} 