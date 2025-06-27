package com.example.scrolltrack.ui.detail

import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape // Ensure CircleShape is imported
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
// import androidx.compose.material3.FilledTonalButton // Replaced with Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // Ensure clip is imported
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.model.AppDailyDetailData
import com.example.scrolltrack.ui.main.ChartPeriodType
import com.example.scrolltrack.ui.main.ComparisonColorType
import com.example.scrolltrack.ui.main.ComparisonIconType
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.util.DateUtil
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
// import androidx.compose.ui.draw.alpha // Not needed
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.LocalContentColor
// import androidx.compose.material.ContentAlpha // M2, use LocalContentColor.current.copy(alpha = ...) for M3 if direct alpha needed on content. Or directly on tint.
import androidx.compose.ui.graphics.compositeOver
import android.text.format.DateUtils
import kotlin.math.abs
import kotlin.math.round
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import com.example.scrolltrack.util.ConversionUtil
import java.text.NumberFormat
import kotlin.math.roundToInt
import android.content.Context
import androidx.compose.ui.graphics.Path

// Helper functions for date formatting specific to this screen
private fun formatDateToDayOfWeek(dateString: String): String {
    val date = DateUtil.parseLocalDateString(dateString) ?: return ""
    return SimpleDateFormat("E", Locale.getDefault()).format(date)
}

private fun formatDateToDayOfMonth(dateString: String): String {
    val date = DateUtil.parseLocalDateString(dateString) ?: return ""
    val cal = Calendar.getInstance()
    cal.time = date
    return cal.get(Calendar.DAY_OF_MONTH).toString()
}

// Constant for swipe threshold
private const val SWIPE_THRESHOLD_DP = 50 // Adjust as needed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    navController: NavController,
    viewModel: MainViewModel,
    packageName: String,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(packageName) {
        viewModel.loadAppDetailsInfo(packageName)
    }

    val appName by viewModel.appDetailAppName.collectAsStateWithLifecycle()
    val appIcon by viewModel.appDetailAppIcon.collectAsStateWithLifecycle()
    val chartData by viewModel.appDetailChartData.collectAsStateWithLifecycle()
    val currentPeriodType by viewModel.currentChartPeriodType.collectAsStateWithLifecycle()
    val currentReferenceDateStr by viewModel.currentChartReferenceDate.collectAsStateWithLifecycle()

    // Collect palette colors - REMOVE THESE
    // val appBarColor by viewModel.appDetailAppBarColor.collectAsStateWithLifecycle()
    // val appBarContentColor by viewModel.appDetailAppBarContentColor.collectAsStateWithLifecycle()
    // val screenBackgroundColor by viewModel.appDetailBackgroundColor.collectAsStateWithLifecycle()

    // Collect new summary states
    val focusedUsageDisplay by viewModel.appDetailFocusedUsageDisplay.collectAsStateWithLifecycle()
    val focusedPeriodDisplay by viewModel.appDetailFocusedPeriodDisplay.collectAsStateWithLifecycle()
    val comparisonText by viewModel.appDetailComparisonText.collectAsStateWithLifecycle()
    val comparisonIconType by viewModel.appDetailComparisonIconType.collectAsStateWithLifecycle()
    val comparisonColorType by viewModel.appDetailComparisonColorType.collectAsStateWithLifecycle()
    val weekNumberDisplay by viewModel.appDetailWeekNumberDisplay.collectAsStateWithLifecycle()
    val periodDescriptionText by viewModel.appDetailPeriodDescriptionText.collectAsStateWithLifecycle()
    val focusedScrollDisplay by viewModel.appDetailFocusedScrollDisplay.collectAsStateWithLifecycle()
    val focusedActiveUsageDisplay by viewModel.appDetailFocusedActiveUsageDisplay.collectAsStateWithLifecycle()

    val canNavigateForward by remember(currentPeriodType, currentReferenceDateStr) {
        derivedStateOf {
            val today = Calendar.getInstance()
            val refDateCal = Calendar.getInstance().apply {
                time = DateUtil.parseLocalDateString(currentReferenceDateStr) ?: Date()
            }

            when (currentPeriodType) {
                ChartPeriodType.DAILY -> !DateUtils.isToday(refDateCal.timeInMillis)
                ChartPeriodType.WEEKLY -> {
                    val endOfWeekForRef = Calendar.getInstance().apply {
                        time = refDateCal.time
                        // Assuming week ends on the reference date for simplicity of check
                    }
                    // If the end of the current week view is before today, allow moving forward.
                    // This logic assumes referenceDate is the *end* of the period.
                    // If refDateCal is already today or in future, can't go further.
                    !DateUtils.isToday(endOfWeekForRef.timeInMillis) && endOfWeekForRef.before(today)

                }
                ChartPeriodType.MONTHLY -> {
                    val endOfMonthForRef = Calendar.getInstance().apply {
                        time = refDateCal.time
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    }
                    // If the end of the current month view is before today, allow moving forward.
                    !DateUtils.isToday(endOfMonthForRef.timeInMillis) && endOfMonthForRef.before(today)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = appIcon ?: R.mipmap.ic_launcher_round,
                            ),
                            contentDescription = "$appName icon",
                            modifier = Modifier.size(32.dp).clip(CircleShape) // Smaller, circular icon
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = appName ?: packageName,
                            style = MaterialTheme.typography.titleLarge, // Uses Pixelify Sans
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface // Ensure text color is from theme
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) // Use onSurface for primary action icons
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Use primary surface color
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding() // Add padding for status bar
            )
        },
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding() // Add padding for navigation bar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Apply inner padding from Scaffold
                .padding(horizontal = 16.dp, vertical = 12.dp), // Consistent screen padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Period Selector Buttons - Enhanced styling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp), // Increased spacing
                horizontalArrangement = Arrangement.spacedBy(8.dp), // Spacing between buttons
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartPeriodType.entries.forEach { period ->
                    val isSelected = currentPeriodType == period
                    Button( // Using standard Button for better visual hierarchy
                        onClick = { viewModel.changeChartPeriod(packageName, period) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium // Consistent shape
                    ) {
                        Text(
                            text = period.name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = MaterialTheme.typography.labelLarge // Using Inter
                        )
                    }
                }
            }

            // Date Navigation and Summary Information Area - Improved Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp), // Increased vertical padding
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.navigateChartDate(packageName, -1) },
                    modifier = Modifier.size(40.dp) // Ensure adequate touch target
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Period", tint = MaterialTheme.colorScheme.primary)
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp), // Increased padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    periodDescriptionText?.let {
                        Text(
                            text = it.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium, // Adjusted style
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = focusedUsageDisplay,
                        style = MaterialTheme.typography.displayMedium, // Prominent display
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary, // Use primary color
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row( // Active and Scroll time side-by-side
                        modifier = Modifier.padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active: $focusedActiveUsageDisplay",
                            style = MaterialTheme.typography.titleSmall, // Clearer sub-info
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Scroll: $focusedScrollDisplay",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = focusedPeriodDisplay,
                            style = MaterialTheme.typography.bodySmall, // Smaller for less emphasis
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentPeriodType == ChartPeriodType.WEEKLY && weekNumberDisplay != null) {
                            Text(
                                text = " • $weekNumberDisplay",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    comparisonText?.let { text ->
                        val (containerColor, contentColor) = when (comparisonColorType) {
                            ComparisonColorType.GREEN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            ComparisonColorType.RED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            ComparisonColorType.GREY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val iconVector = when (comparisonIconType) {
                            ComparisonIconType.UP -> Icons.Filled.ArrowUpward
                            ComparisonIconType.DOWN -> Icons.Filled.ArrowDownward
                            ComparisonIconType.NEUTRAL -> Icons.Filled.HorizontalRule
                            else -> null
                        }

                        Card(
                            modifier = Modifier.padding(top = 12.dp), // Increased spacing
                            shape = MaterialTheme.shapes.small, // Smaller shape for this element
                            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Subtle elevation
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), // Adjusted padding
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                iconVector?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = "Comparison",
                                        modifier = Modifier.size(18.dp).padding(end = 4.dp) // Adjusted size
                                    )
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), // Adjusted style
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.navigateChartDate(packageName, 1) },
                    enabled = canNavigateForward,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Next Period",
                        tint = if (canNavigateForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // Standard M3 disabled alpha
                    )
                }
            }

            // Chart Area - Increased padding and ensure it's visually appealing
            Card( // Wrap chart in a card for better visual separation and theming
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp) // Slightly increased height
                    .padding(vertical = 16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), // Padding inside the card for the chart
                    contentAlignment = Alignment.Center
                ) {
                    if (chartData.isEmpty()) {
                        Text("Loading chart data...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        UsageBarScrollLineChart(
                            modifier = Modifier.fillMaxSize(),
                            data = chartData,
                            periodType = currentPeriodType,
                            // viewModel and packageName are not directly used by the chart drawing logic itself after data is passed.
                            // Tap interactions inside chart for DAILY period were removed.
                            // Swipe gestures are on the parent Box.
                            // Tooltip logic is self-contained with data.
                            // If specific viewModel interactions are needed from within the chart for other reasons, they'd be passed.
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageBarScrollLineChart(
    modifier: Modifier = Modifier,
    data: List<AppDailyDetailData>,
    periodType: ChartPeriodType
    // viewModel and packageName are no longer passed here as swipe/tap interactions
    // that require them are handled in the parent or removed if purely visual.
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    val axisLabelPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val legendPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val tooltipTextPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val chartAreaErrorPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }

    LaunchedEffect(periodType, data) {
        if (periodType == ChartPeriodType.DAILY) {
            selectedBarIndex = null
        }
    }

    if (data.isEmpty()) {
        return
    }

    // Use theme colors for chart elements
    val barColor = MaterialTheme.colorScheme.primary
    val scrollDistanceColor = MaterialTheme.colorScheme.secondary
    val faintAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val axisLabelTextStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val selectedBarColor = MaterialTheme.colorScheme.tertiary

    val tooltipBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tooltipActualTextColor = MaterialTheme.colorScheme.onSurfaceContainerHighest
    val tooltipTextStyle = MaterialTheme.typography.bodySmall.copy(color = tooltipActualTextColor)

    val tooltipScrollText by remember(selectedBarIndex, data, periodType, context) {
        derivedStateOf {
            if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
                val selectedData = data[selectedBarIndex!!]
                ConversionUtil.formatScrollDistance(selectedData.scrollUnits, context).first
            } else {
                ""
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(periodType, data) { // Keep pointerInput for tap gestures on bars
            if (periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val barCount = data.size
                        if (barCount == 0) return@detectTapGestures

                        val canvasWidth = size.width.toFloat()
                        val canvasHeight = size.height.toFloat()

                        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
                        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99km") }, style = axisLabelTextStyle).size.width.toFloat()
                        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
                        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 8.dp.toPx()
                        val legendTotalHeight = legendItemHeight * 2

                        val tickMarkAndLabelPadding = 12.dp.toPx()
                        val leftMargin = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
                        val bottomMargin = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight
                        val topMargin = 12.dp.toPx()
                        val rightMargin = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding

                        val chartWidth = canvasWidth - leftMargin - rightMargin
                        val chartHeight = canvasHeight - bottomMargin - topMargin

                        if (chartWidth <= 0 || chartHeight <= 0) return@detectTapGestures

                        val totalBarWidthFactor = 0.7f
                        val totalBarWidth = chartWidth * totalBarWidthFactor
                        val barWidth = (totalBarWidth / barCount).coerceAtLeast(4.dp.toPx())
                        val barSpacing = (chartWidth - totalBarWidth) / (barCount + 1).coerceAtLeast(1)

                        var tappedBarFound = false
                        for (index in data.indices) {
                            val detail = data[index]
                            val usageTimesForHeight = data.map { it.usageTimeMillis }
                            val maxUsageTimeForHeight = usageTimesForHeight.maxOrNull() ?: 1L
                            val minUsageTimeForHeight = 0L

                            val barHeightNorm = ((detail.usageTimeMillis - minUsageTimeForHeight).toFloat() / (maxUsageTimeForHeight - minUsageTimeForHeight).coerceAtLeast(1L).toFloat())
                            val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(0f)

                            val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                            val barTopCanvas = topMargin + chartHeight - barActualHeight
                            val barRight = barLeft + barWidth
                            val barBottomCanvas = topMargin + chartHeight
                            val barRect = Rect(barLeft, barTopCanvas, barRight, barBottomCanvas)

                            if (barRect.contains(tapOffset)) {
                                selectedBarIndex = if (selectedBarIndex == index) null else index
                                tappedBarFound = true
                                break
                            }
                        }
                        if (!tappedBarFound) {
                            selectedBarIndex = null
                        }
                    }
                )
            }
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99km") }, style = axisLabelTextStyle).size.width.toFloat()
        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 8.dp.toPx()
        val legendTotalHeight = legendItemHeight * 2

        val tickMarkAndLabelPadding = 12.dp.toPx()
        val leftMargin = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
        val bottomMargin = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight
        val topMargin = 12.dp.toPx()
        val rightMargin = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding

        val chartWidth = canvasWidth - leftMargin - rightMargin
        val chartHeight = canvasHeight - bottomMargin - topMargin

        if (chartWidth <= 0 || chartHeight <= 0) {
            drawIntoCanvas { canvas ->
                chartAreaErrorPaint.apply {
                    textSize = MaterialTheme.typography.bodyMedium.fontSize.toPx()
                    color = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                }
                canvas.nativeCanvas.drawText("Chart area too small", center.x, center.y, chartAreaErrorPaint)
            }
            return@Canvas
        }

        drawLine(color = faintAxisColor, start = Offset(leftMargin, topMargin + chartHeight), end = Offset(leftMargin + chartWidth, topMargin + chartHeight), strokeWidth = 1.dp.toPx())

        val usageTimes = data.map { it.usageTimeMillis }
        val actualMaxUsageTime = usageTimes.maxOrNull() ?: 1L
        val minUsageTime = 0L
        val scrollUnitsList = data.map { it.scrollUnits }
        val actualMaxScrollUnits = scrollUnitsList.maxOrNull() ?: 1L
        val minScrollUnits = 0L

        val Y_AXIS_PADDING_FACTOR = 1.20f
        val maxUsageTimeForAxis = (actualMaxUsageTime * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)
        val maxScrollUnitsForAxis = (actualMaxScrollUnits * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)

        val barCount = data.size
        val barWidth: Float
        val barSpacing: Float
        if (barCount > 0) {
            val totalBarWidthFactor = 0.7f
            val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
            barWidth = (totalBarLayoutWidth / barCount).coerceAtLeast(4.dp.toPx())
            barSpacing = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)
        } else {
            barWidth = 0f; barSpacing = 0f
        }

        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.usageTimeMillis > 0) {
                    val barHeightNorm = ((detail.usageTimeMillis - minUsageTime).toFloat() / (actualMaxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
                    val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())

                    val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                    val barTop = topMargin + chartHeight - barActualHeight
                    val barRight = barLeft + barWidth
                    val barBottom = topMargin + chartHeight

                    val currentBarColor = when {
                        periodType == ChartPeriodType.DAILY -> barColor
                        selectedBarIndex == null -> barColor
                        selectedBarIndex == index -> selectedBarColor
                        else -> barColor.copy(alpha = 0.5f)
                    }

                    val dynamicCornerRadius = when (periodType) {
                        ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 6.dp.toPx()
                        ChartPeriodType.MONTHLY -> 4.dp.toPx()
                    }

                    if (barActualHeight > 0) {
                         val path = Path().apply {
                            moveTo(barLeft, barBottom)
                            lineTo(barLeft, barTop + dynamicCornerRadius)
                            arcTo(Rect(left = barLeft, top = barTop, right = barLeft + 2 * dynamicCornerRadius, bottom = barTop + 2 * dynamicCornerRadius), 180f, 90f, false)
                            lineTo(barRight - dynamicCornerRadius, barTop)
                            arcTo(Rect(left = barRight - 2 * dynamicCornerRadius, top = barTop, right = barRight, bottom = barTop + 2 * dynamicCornerRadius), 270f, 90f, false)
                            lineTo(barRight, barBottom)
                            close()
                        }
                        drawPath(path, color = currentBarColor)
                    }

                    if (detail.activeTimeMillis > 0) {
                        val activeBarHeightNorm = (detail.activeTimeMillis.toFloat() / actualMaxUsageTime.toFloat()).coerceIn(0f, 1f)
                        val activeBarActualHeight = (activeBarHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())
                        val activeBarTop = topMargin + chartHeight - activeBarActualHeight
                        val activeBarWidth = barWidth * 0.5f
                        val activeBarLeft = barLeft + (barWidth - activeBarWidth) / 2

                        drawRect(
                            color = currentBarColor.copy(alpha = 0.7f),
                            topLeft = Offset(activeBarLeft, activeBarTop),
                            size = androidx.compose.ui.geometry.Size(activeBarWidth, activeBarActualHeight)
                        )
                    }
                }
            }
        }

        val scrollDataPoints = mutableListOf<Offset>()
        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.scrollUnits > 0) {
                    val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                    val barCenterX = barLeft + barWidth / 2
                    val scrollRange = (actualMaxScrollUnits - minScrollUnits).coerceAtLeast(1L)
                    val yNorm = if (scrollRange == 0L) 0f else ((detail.scrollUnits - minScrollUnits).toFloat() / scrollRange.toFloat())
                    val y = topMargin + chartHeight - (yNorm * chartHeight)
                    scrollDataPoints.add(Offset(barCenterX, y.coerceIn(topMargin, topMargin + chartHeight)))
                }
            }
        }

        if (scrollDataPoints.size >= 2) {
            val linePath = Path()
            if (scrollDataPoints.size == 2) {
                linePath.moveTo(scrollDataPoints[0].x, scrollDataPoints[0].y)
                linePath.lineTo(scrollDataPoints[1].x, scrollDataPoints[1].y)
            } else {
                val splinePoints = mutableListOf<Offset>()
                splinePoints.add(scrollDataPoints.first())
                splinePoints.addAll(scrollDataPoints)
                splinePoints.add(scrollDataPoints.last())
                linePath.moveTo(splinePoints[1].x, splinePoints[1].y)
                for (i in 1 until splinePoints.size - 2) {
                    val p0 = splinePoints[i-1]; val p1 = splinePoints[i]; val p2 = splinePoints[i+1]; val p3 = splinePoints[i+2]
                    val cp1x = p1.x + (p2.x - p0.x) / 6.0f; val cp1y = p1.y + (p2.y - p0.y) / 6.0f
                    val cp2x = p2.x - (p3.x - p1.x) / 6.0f; val cp2y = p2.y - (p3.y - p1.y) / 6.0f
                    linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
            }
            drawPath(path = linePath, color = scrollDistanceColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }

        scrollDataPoints.forEach { point ->
            drawCircle(color = scrollDistanceColor, radius = 3.dp.toPx(), center = point)
            drawCircle(color = MaterialTheme.colorScheme.surfaceVariant, radius = 1.5.dp.toPx(), center = point)
        }

        if (barCount > 0) {
            val totalBarLayoutWidthForXLabels = chartWidth * 0.8f
            val barWidthForXLabels = (totalBarLayoutWidthForXLabels / barCount).coerceAtLeast(2f)
            val barSpacingForXLabels = (chartWidth - totalBarLayoutWidthForXLabels) / (barCount + 1).coerceAtLeast(1)
            data.forEachIndexed { index, dailyData ->
                val dateStr = dailyData.date
                val formattedDate = when (periodType) {
                    ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> getDayOfWeekLetter(dateStr)
                    ChartPeriodType.MONTHLY -> {
                        try {
                            val dayOfMonth = dateStr.substringAfterLast('-').toInt()
                            if (dayOfMonth == 1 || index == data.size -1 || dayOfMonth % 5 == 0 && !(dayOfMonth == 30 && data.size == 31)) dayOfMonth.toString() else ""
                        } catch (e: Exception) { "" }
                    }
                }
                if (formattedDate.isNotEmpty()){
                    val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(formattedDate) }, style = axisLabelTextStyle)
                    val labelX = leftMargin + barSpacingForXLabels + index * (barWidthForXLabels + barSpacingForXLabels) + barWidthForXLabels / 2 - textLayoutResult.size.width / 2
                    axisLabelPaint.apply { color = labelColor.toArgb(); textSize = axisLabelTextStyle.fontSize.toPx() }
                    drawContext.canvas.nativeCanvas.drawText(formattedDate, labelX, topMargin + chartHeight + 5f + textLayoutResult.size.height, axisLabelPaint)
                }
            }
        }

        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val value = minUsageTime + (i.toFloat() / yLabelCount) * (maxUsageTimeForAxis - minUsageTime)
            val labelText = formatMillisToHoursOrMinutes(value.toLong())
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            if (labelY >= topMargin && labelY <= topMargin + chartHeight) {
                drawLine(color = faintAxisColor.copy(alpha = 0.3f), start = Offset(leftMargin, labelY), end = Offset(leftMargin + chartWidth, labelY), strokeWidth = 0.5.dp.toPx())
                drawLine(color = faintAxisColor, start = Offset(leftMargin - 4.dp.toPx(), labelY), end = Offset(leftMargin, labelY), strokeWidth = 1.dp.toPx())
                axisLabelPaint.apply { color = labelColor.toArgb(); textSize = axisLabelTextStyle.fontSize.toPx() }
                drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin - textLayoutResult.size.width - 6.dp.toPx(), labelY + textLayoutResult.size.height / 2f - 2.dp.toPx(), axisLabelPaint)
            }
        }

        val yScrollLabelCount = 4
        for (i in 0..yScrollLabelCount) {
            val value = minScrollUnits + (i.toFloat() / yScrollLabelCount) * (maxScrollUnitsForAxis - minScrollUnits)
            val labelText = formatScrollForAxis(value.toFloat(), actualMaxScrollUnits, context)
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yScrollLabelCount) * chartHeight
            drawLine(color = faintAxisColor, start = Offset(leftMargin + chartWidth, labelY), end = Offset(leftMargin + chartWidth + 4.dp.toPx(), labelY), strokeWidth = 1.dp.toPx())
            axisLabelPaint.apply { color = labelColor.toArgb(); textSize = axisLabelTextStyle.fontSize.toPx() }
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin + chartWidth + 6.dp.toPx(), labelY + textLayoutResult.size.height / 2 - 2.dp.toPx(), axisLabelPaint)
        }

        val legendStartY = topMargin + chartHeight + xAxisLabelHeight + 16.dp.toPx()
        val legendItemSize = 10.dp.toPx()
        val legendTextPadding = 6.dp.toPx()

        drawRect(color = barColor, topLeft = Offset(leftMargin, legendStartY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        legendPaint.apply { color = labelColor.toArgb(); textSize = MaterialTheme.typography.labelSmall.fontSize.toPx() }
        drawContext.canvas.nativeCanvas.drawText("Usage", leftMargin + legendItemSize + legendTextPadding, legendStartY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Usage") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        val secondLegendItemY = legendStartY + legendItemHeight
        drawRect(color = scrollDistanceColor, topLeft = Offset(leftMargin, secondLegendItemY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        drawContext.canvas.nativeCanvas.drawText("Scroll", leftMargin + legendItemSize + legendTextPadding, secondLegendItemY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Scroll") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
            val selectedData = data[selectedBarIndex!!]
            val usageText = formatMillisToHoursMinutesSeconds(selectedData.usageTimeMillis)
            val scrollText = tooltipScrollText
            val usageTextLayout = textMeasurer.measure(buildAnnotatedString{ append(usageText) }, style = tooltipTextStyle)
            val scrollTextLayout = textMeasurer.measure(buildAnnotatedString{ append(scrollText) }, style = tooltipTextStyle)

            val tooltipPaddingHorizontal = 10.dp.toPx()
            val tooltipPaddingVertical = 6.dp.toPx()
            val tooltipTextSpacing = 4.dp.toPx()

            val tooltipContentWidth = maxOf(usageTextLayout.size.width, scrollTextLayout.size.width).toFloat()
            val tooltipContentHeight = usageTextLayout.size.height + scrollTextLayout.size.height + tooltipTextSpacing
            val tooltipTotalWidth = tooltipContentWidth + 2 * tooltipPaddingHorizontal
            val tooltipTotalHeight = tooltipContentHeight + 2 * tooltipPaddingVertical

            val selectedBarLeft = leftMargin + barSpacing + selectedBarIndex!! * (barWidth + barSpacing)
            val selectedBarCenterX = selectedBarLeft + barWidth / 2
            val selectedBarUsageNorm = ((selectedData.usageTimeMillis - minUsageTime).toFloat() / (actualMaxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
            val selectedBarActualHeight = (selectedBarUsageNorm * chartHeight).coerceAtLeast(0f)
            val selectedBarTopCanvas = topMargin + chartHeight - selectedBarActualHeight

            val tooltipGap = 6.dp.toPx()
            var tooltipX = selectedBarCenterX - tooltipTotalWidth / 2
            var tooltipY = selectedBarTopCanvas - tooltipTotalHeight - tooltipGap
            if (tooltipX < leftMargin) tooltipX = leftMargin
            if (tooltipX + tooltipTotalWidth > leftMargin + chartWidth) tooltipX = leftMargin + chartWidth - tooltipTotalWidth
            if (tooltipY < topMargin) tooltipY = topMargin

            drawRoundRect(color = tooltipBackgroundColor, topLeft = Offset(tooltipX, tooltipY), size = androidx.compose.ui.geometry.Size(tooltipTotalWidth, tooltipTotalHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            tooltipTextPaint.apply { color = tooltipActualTextColor.toArgb(); textSize = tooltipTextStyle.fontSize.toPx() }
            drawContext.canvas.nativeCanvas.drawText(usageText, tooltipX + tooltipPaddingHorizontal, tooltipY + tooltipPaddingVertical + usageTextLayout.size.height, tooltipTextPaint)
            drawContext.canvas.nativeCanvas.drawText(scrollText, tooltipX + tooltipPaddingHorizontal, tooltipY + tooltipPaddingVertical + usageTextLayout.size.height + tooltipTextSpacing + scrollTextLayout.size.height, tooltipTextPaint)
        }
    }
}

fun getDayOfWeekLetter(dateString: String): String {
    packageName: String
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    val axisLabelPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val legendPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val tooltipTextPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val chartAreaErrorPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }

    LaunchedEffect(periodType, data) {
        if (periodType == ChartPeriodType.DAILY) {
            selectedBarIndex = null
        }
    }

    if (data.isEmpty()) {
        // Already handled by the caller (Box with "Loading chart data..." or "No data to display.")
        // This Composable might not need its own empty state if the parent handles it.
        // However, if called directly, this is a good fallback.
        // For this refactor, we assume the parent Card provides the context.
        return
    }

    // Use theme colors for chart elements
    val barColor = MaterialTheme.colorScheme.primary // Default bar color
    val scrollDistanceColor = MaterialTheme.colorScheme.secondary // Color for scroll line/dots
    val faintAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) // Lighter axis/grid lines
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // For axis labels
    val axisLabelTextStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor) // Use theme typography
    val selectedBarColor = MaterialTheme.colorScheme.tertiary // Color for selected bar

    val tooltipBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest // Darker surface for tooltip
    val tooltipActualTextColor = MaterialTheme.colorScheme.onSurfaceContainerHighest
    val tooltipTextStyle = MaterialTheme.typography.bodySmall.copy(color = tooltipActualTextColor)

    val tooltipScrollText by remember(selectedBarIndex, data, periodType, context) {
        derivedStateOf {
            if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
                val selectedData = data[selectedBarIndex!!]
                ConversionUtil.formatScrollDistance(selectedData.scrollUnits, context).first
            } else {
                ""
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(periodType, data) {
            if (periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val barCount = data.size
                        if (barCount == 0) return@detectTapGestures

                        val canvasWidth = size.width.toFloat()
                        val canvasHeight = size.height.toFloat()

                        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
                        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99km") }, style = axisLabelTextStyle).size.width.toFloat()
                        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
                        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 8.dp.toPx() // Adjusted padding
                        val legendTotalHeight = legendItemHeight * 2

                        val tickMarkAndLabelPadding = 12.dp.toPx() // Reduced padding
                        val leftMargin = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
                        val bottomMargin = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight
                        val topMargin = 12.dp.toPx() // Consistent top padding
                        val rightMargin = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding

                        val chartWidth = canvasWidth - leftMargin - rightMargin
                        val chartHeight = canvasHeight - bottomMargin - topMargin

                        if (chartWidth <= 0 || chartHeight <= 0) return@detectTapGestures

                        val totalBarWidthFactor = 0.7f // Slightly wider bars
                        val totalBarWidth = chartWidth * totalBarWidthFactor
                        val barWidth = (totalBarWidth / barCount).coerceAtLeast(4.dp.toPx()) // Min bar width
                        val barSpacing = (chartWidth - totalBarWidth) / (barCount + 1).coerceAtLeast(1)

                        var tappedBarFound = false
                        for (index in data.indices) {
                            val detail = data[index]
                            val usageTimesForHeight = data.map { it.usageTimeMillis }
                            val maxUsageTimeForHeight = usageTimesForHeight.maxOrNull() ?: 1L
                            val minUsageTimeForHeight = 0L

                            val barHeightNorm = ((detail.usageTimeMillis - minUsageTimeForHeight).toFloat() / (maxUsageTimeForHeight - minUsageTimeForHeight).coerceAtLeast(1L).toFloat())
                            val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(0f)

                            val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                            val barTopCanvas = topMargin + chartHeight - barActualHeight
                            val barRight = barLeft + barWidth
                            val barBottomCanvas = topMargin + chartHeight
                            val barRect = Rect(barLeft, barTopCanvas, barRight, barBottomCanvas)

                            if (barRect.contains(tapOffset)) {
                                selectedBarIndex = if (selectedBarIndex == index) null else index
                                tappedBarFound = true
                                break
                            }
                        }
                        if (!tappedBarFound) {
                            selectedBarIndex = null
                        }
                    }
                )
            }
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99km") }, style = axisLabelTextStyle).size.width.toFloat()
        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 8.dp.toPx()
        val legendTotalHeight = legendItemHeight * 2

        val tickMarkAndLabelPadding = 12.dp.toPx()
        val leftMargin = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
        val bottomMargin = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight
        val topMargin = 12.dp.toPx()
        val rightMargin = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding

        val chartWidth = canvasWidth - leftMargin - rightMargin
        val chartHeight = canvasHeight - bottomMargin - topMargin

        if (chartWidth <= 0 || chartHeight <= 0) {
            drawIntoCanvas { canvas ->
                chartAreaErrorPaint.apply {
                    textSize = MaterialTheme.typography.bodyMedium.fontSize.toPx()
                    color = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                }
                canvas.nativeCanvas.drawText("Chart area too small", center.x, center.y, chartAreaErrorPaint)
            }
            return@Canvas
        }

        drawLine(color = faintAxisColor, start = Offset(leftMargin, topMargin + chartHeight), end = Offset(leftMargin + chartWidth, topMargin + chartHeight), strokeWidth = 1.dp.toPx())

        val usageTimes = data.map { it.usageTimeMillis }
        val actualMaxUsageTime = usageTimes.maxOrNull() ?: 1L
        val minUsageTime = 0L
        val scrollUnitsList = data.map { it.scrollUnits }
        val actualMaxScrollUnits = scrollUnitsList.maxOrNull() ?: 1L
        val minScrollUnits = 0L

        val Y_AXIS_PADDING_FACTOR = 1.20f // Increased padding for better visual separation
        val maxUsageTimeForAxis = (actualMaxUsageTime * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)
        val maxScrollUnitsForAxis = (actualMaxScrollUnits * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)

        val barCount = data.size
        val barWidth: Float
        val barSpacing: Float
        if (barCount > 0) {
            val totalBarWidthFactor = 0.7f
            val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
            barWidth = (totalBarLayoutWidth / barCount).coerceAtLeast(4.dp.toPx())
            barSpacing = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)
        } else {
            barWidth = 0f; barSpacing = 0f
        }

        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.usageTimeMillis > 0) {
                    val barHeightNorm = ((detail.usageTimeMillis - minUsageTime).toFloat() / (actualMaxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
                    val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())

                    val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                    val barTop = topMargin + chartHeight - barActualHeight
                    val barRight = barLeft + barWidth
                    val barBottom = topMargin + chartHeight

                    val currentBarColor = when {
                        periodType == ChartPeriodType.DAILY -> barColor
                        selectedBarIndex == null -> barColor
                        selectedBarIndex == index -> selectedBarColor
                        else -> barColor.copy(alpha = 0.5f) // More pronounced alpha for non-selected
                    }

                    val dynamicCornerRadius = when (periodType) {
                        ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 6.dp.toPx() // Standardized corner radius
                        ChartPeriodType.MONTHLY -> 4.dp.toPx()
                    }

                    if (barActualHeight > 0) {
                         val path = Path().apply {
                            moveTo(barLeft, barBottom)
                            lineTo(barLeft, barTop + dynamicCornerRadius)
                            arcTo(Rect(left = barLeft, top = barTop, right = barLeft + 2 * dynamicCornerRadius, bottom = barTop + 2 * dynamicCornerRadius), 180f, 90f, false)
                            lineTo(barRight - dynamicCornerRadius, barTop)
                            arcTo(Rect(left = barRight - 2 * dynamicCornerRadius, top = barTop, right = barRight, bottom = barTop + 2 * dynamicCornerRadius), 270f, 90f, false)
                            lineTo(barRight, barBottom)
                            close()
                        }
                        drawPath(path, color = currentBarColor)
                    }

                    if (detail.activeTimeMillis > 0) {
                        val activeBarHeightNorm = (detail.activeTimeMillis.toFloat() / actualMaxUsageTime.toFloat()).coerceIn(0f, 1f)
                        val activeBarActualHeight = (activeBarHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())
                        val activeBarTop = topMargin + chartHeight - activeBarActualHeight
                        val activeBarWidth = barWidth * 0.5f // Thinner active time bar
                        val activeBarLeft = barLeft + (barWidth - activeBarWidth) / 2

                        drawRect(
                            color = currentBarColor.copy(alpha = 0.7f), // Slightly transparent overlay or different shade
                            topLeft = Offset(activeBarLeft, activeBarTop),
                            size = androidx.compose.ui.geometry.Size(activeBarWidth, activeBarActualHeight)
                        )
                    }
                }
            }
        }

        val scrollDataPoints = mutableListOf<Offset>()
        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.scrollUnits > 0) {
                    val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                    val barCenterX = barLeft + barWidth / 2
                    val scrollRange = (actualMaxScrollUnits - minScrollUnits).coerceAtLeast(1L)
                    val yNorm = if (scrollRange == 0L) 0f else ((detail.scrollUnits - minScrollUnits).toFloat() / scrollRange.toFloat())
                    val y = topMargin + chartHeight - (yNorm * chartHeight)
                    scrollDataPoints.add(Offset(barCenterX, y.coerceIn(topMargin, topMargin + chartHeight)))
                }
            }
        }

        if (scrollDataPoints.size >= 2) {
            val linePath = Path()
            if (scrollDataPoints.size == 2) {
                linePath.moveTo(scrollDataPoints[0].x, scrollDataPoints[0].y)
                linePath.lineTo(scrollDataPoints[1].x, scrollDataPoints[1].y)
            } else {
                val splinePoints = mutableListOf<Offset>()
                splinePoints.add(scrollDataPoints.first())
                splinePoints.addAll(scrollDataPoints)
                splinePoints.add(scrollDataPoints.last())
                linePath.moveTo(splinePoints[1].x, splinePoints[1].y)
                for (i in 1 until splinePoints.size - 2) {
                    val p0 = splinePoints[i-1]; val p1 = splinePoints[i]; val p2 = splinePoints[i+1]; val p3 = splinePoints[i+2]
                    val cp1x = p1.x + (p2.x - p0.x) / 6.0f; val cp1y = p1.y + (p2.y - p0.y) / 6.0f
                    val cp2x = p2.x - (p3.x - p1.x) / 6.0f; val cp2y = p2.y - (p3.y - p1.y) / 6.0f
                    linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
            }
            drawPath(path = linePath, color = scrollDistanceColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)) // Round cap
        }

        scrollDataPoints.forEach { point ->
            drawCircle(color = scrollDistanceColor, radius = 3.dp.toPx(), center = point) // Smaller dots
            drawCircle(color = MaterialTheme.colorScheme.surfaceVariant, radius = 1.5.dp.toPx(), center = point) // Inner dot for contrast
        }

        if (barCount > 0) {
            val totalBarLayoutWidthForXLabels = chartWidth * 0.8f
            val barWidthForXLabels = (totalBarLayoutWidthForXLabels / barCount).coerceAtLeast(2f)
            val barSpacingForXLabels = (chartWidth - totalBarLayoutWidthForXLabels) / (barCount + 1).coerceAtLeast(1)
            data.forEachIndexed { index, dailyData ->
                val dateStr = dailyData.date
                val formattedDate = when (periodType) {
                    ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> getDayOfWeekLetter(dateStr)
                    ChartPeriodType.MONTHLY -> {
                        try {
                            val dayOfMonth = dateStr.substringAfterLast('-').toInt()
                            if (dayOfMonth == 1 || index == data.size -1 || dayOfMonth % 5 == 0 && !(dayOfMonth == 30 && data.size == 31)) dayOfMonth.toString() else ""
                        } catch (e: Exception) { "" }
                    }
                }
                if (formattedDate.isNotEmpty()){
                    val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(formattedDate) }, style = axisLabelTextStyle)
                    val labelX = leftMargin + barSpacingForXLabels + index * (barWidthForXLabels + barSpacingForXLabels) + barWidthForXLabels / 2 - textLayoutResult.size.width / 2
                    axisLabelPaint.apply { color = labelColor.toArgb(); textSize = axisLabelTextStyle.fontSize.toPx() }
                    drawContext.canvas.nativeCanvas.drawText(formattedDate, labelX, topMargin + chartHeight + 5f + textLayoutResult.size.height, axisLabelPaint)
                }
            }
        }

        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val value = minUsageTime + (i.toFloat() / yLabelCount) * (maxUsageTimeForAxis - minUsageTime)
            val labelText = formatMillisToHoursOrMinutes(value.toLong())
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            if (labelY >= topMargin && labelY <= topMargin + chartHeight) {
                drawLine(color = faintAxisColor.copy(alpha = 0.3f), start = Offset(leftMargin, labelY), end = Offset(leftMargin + chartWidth, labelY), strokeWidth = 0.5.dp.toPx()) // Thinner grid lines
                drawLine(color = faintAxisColor, start = Offset(leftMargin - 4.dp.toPx(), labelY), end = Offset(leftMargin, labelY), strokeWidth = 1.dp.toPx())
                axisLabelPaint.apply { color = labelColor.toArgb(); textSize = axisLabelTextStyle.fontSize.toPx() }
                drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin - textLayoutResult.size.width - 6.dp.toPx(), labelY + textLayoutResult.size.height / 2f - 2.dp.toPx(), axisLabelPaint)
            }
        }

        val yScrollLabelCount = 4
        for (i in 0..yScrollLabelCount) {
            val value = minScrollUnits + (i.toFloat() / yScrollLabelCount) * (maxScrollUnitsForAxis - minScrollUnits)
            val labelText = formatScrollForAxis(value.toFloat(), actualMaxScrollUnits, context)
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yScrollLabelCount) * chartHeight
            drawLine(color = faintAxisColor, start = Offset(leftMargin + chartWidth, labelY), end = Offset(leftMargin + chartWidth + 4.dp.toPx(), labelY), strokeWidth = 1.dp.toPx())
            axisLabelPaint.apply { color = labelColor.toArgb(); textSize = axisLabelTextStyle.fontSize.toPx() }
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin + chartWidth + 6.dp.toPx(), labelY + textLayoutResult.size.height / 2 - 2.dp.toPx(), axisLabelPaint)
        }

        val legendStartY = topMargin + chartHeight + xAxisLabelHeight + 16.dp.toPx() // Adjusted spacing
        val legendItemSize = 10.dp.toPx() // Smaller legend items
        val legendTextPadding = 6.dp.toPx()

        drawRect(color = barColor, topLeft = Offset(leftMargin, legendStartY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        legendPaint.apply { color = labelColor.toArgb(); textSize = MaterialTheme.typography.labelSmall.fontSize.toPx() }
        drawContext.canvas.nativeCanvas.drawText("Usage", leftMargin + legendItemSize + legendTextPadding, legendStartY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Usage") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        val secondLegendItemY = legendStartY + legendItemHeight
        drawRect(color = scrollDistanceColor, topLeft = Offset(leftMargin, secondLegendItemY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        drawContext.canvas.nativeCanvas.drawText("Scroll", leftMargin + legendItemSize + legendTextPadding, secondLegendItemY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Scroll") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
            val selectedData = data[selectedBarIndex!!]
            val usageText = formatMillisToHoursMinutesSeconds(selectedData.usageTimeMillis)
            val scrollText = tooltipScrollText
            val usageTextLayout = textMeasurer.measure(buildAnnotatedString{ append(usageText) }, style = tooltipTextStyle)
            val scrollTextLayout = textMeasurer.measure(buildAnnotatedString{ append(scrollText) }, style = tooltipTextStyle)

            val tooltipPaddingHorizontal = 10.dp.toPx() // Increased padding
            val tooltipPaddingVertical = 6.dp.toPx()
            val tooltipTextSpacing = 4.dp.toPx()

            val tooltipContentWidth = maxOf(usageTextLayout.size.width, scrollTextLayout.size.width).toFloat()
            val tooltipContentHeight = usageTextLayout.size.height + scrollTextLayout.size.height + tooltipTextSpacing
            val tooltipTotalWidth = tooltipContentWidth + 2 * tooltipPaddingHorizontal
            val tooltipTotalHeight = tooltipContentHeight + 2 * tooltipPaddingVertical

            val selectedBarLeft = leftMargin + barSpacing + selectedBarIndex!! * (barWidth + barSpacing)
            val selectedBarCenterX = selectedBarLeft + barWidth / 2
            val selectedBarUsageNorm = ((selectedData.usageTimeMillis - minUsageTime).toFloat() / (actualMaxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
            val selectedBarActualHeight = (selectedBarUsageNorm * chartHeight).coerceAtLeast(0f)
            val selectedBarTopCanvas = topMargin + chartHeight - selectedBarActualHeight

            val tooltipGap = 6.dp.toPx()
            var tooltipX = selectedBarCenterX - tooltipTotalWidth / 2
            var tooltipY = selectedBarTopCanvas - tooltipTotalHeight - tooltipGap
            if (tooltipX < leftMargin) tooltipX = leftMargin
            if (tooltipX + tooltipTotalWidth > leftMargin + chartWidth) tooltipX = leftMargin + chartWidth - tooltipTotalWidth
            if (tooltipY < topMargin) tooltipY = topMargin

            drawRoundRect(color = tooltipBackgroundColor, topLeft = Offset(tooltipX, tooltipY), size = androidx.compose.ui.geometry.Size(tooltipTotalWidth, tooltipTotalHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())) // Softer corners
            tooltipTextPaint.apply { color = tooltipActualTextColor.toArgb(); textSize = tooltipTextStyle.fontSize.toPx() }
            drawContext.canvas.nativeCanvas.drawText(usageText, tooltipX + tooltipPaddingHorizontal, tooltipY + tooltipPaddingVertical + usageTextLayout.size.height, tooltipTextPaint)
            drawContext.canvas.nativeCanvas.drawText(scrollText, tooltipX + tooltipPaddingHorizontal, tooltipY + tooltipPaddingVertical + usageTextLayout.size.height + tooltipTextSpacing + scrollTextLayout.size.height, tooltipTextPaint)
        }
    }
}

fun getDayOfWeekLetter(dateString: String): String {
    return try {
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdfInput.parse(dateString)
        val sdfOutput = SimpleDateFormat("E", Locale.getDefault())
        sdfOutput.format(date!!).first().toString()
    } catch (e: Exception) {
        "?"
    }
}

fun formatScrollForAxis(value: Float, maxValueHint: Long, context: Context): String {
    if (value == 0f) return "0 m"

    val displayMetrics = context.resources.displayMetrics
    val dpi = displayMetrics.ydpi
    if (dpi <= 0f) return "N/A"

    val inchesScrolled = value.toDouble() / dpi
    val metersScrolled = inchesScrolled / 39.3701 // INCHES_PER_METER

    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

    return when {
        metersScrolled.compareTo(10000.0) >= 0 -> { // 10km+
            numberFormat.maximumFractionDigits = 1
            numberFormat.format(metersScrolled / 1000.0) + " km"
        }
        metersScrolled.compareTo(1000.0) >= 0 -> { // 1km - 9.999km
            numberFormat.maximumFractionDigits = 1
            numberFormat.format(metersScrolled / 1000.0) + " km"
        }
        metersScrolled.compareTo(1.0) >= 0 -> {
            numberFormat.maximumFractionDigits = 0
            numberFormat.format(metersScrolled) + " m"
        }
        metersScrolled.compareTo(0.0) > 0 -> { // Between 0 and 1 meter
            numberFormat.maximumFractionDigits = 1 // e.g., 0.5 m
            numberFormat.format(metersScrolled) + " m"
        }
        else -> "0 m" // For negative or very small values effectively zero
    }
}

fun formatMillisToHoursOrMinutes(millis: Long): String {
    if (millis < 0) return "0min"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

    return when {
        hours > 0 -> String.format("%dhr", hours)
        minutes > 0 -> String.format("%dmin", minutes)
        else -> "0min"
    }
}

fun formatMillisToHoursMinutesSeconds(millis: Long): String {
    if (millis < 0) return "0sec"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return when {
        hours > 0 -> String.format("%dhr %02dmin", hours, minutes)
        minutes > 0 -> String.format("%dmin %02dsec", minutes, seconds)
        else -> String.format("%dsec", seconds)
    }
}