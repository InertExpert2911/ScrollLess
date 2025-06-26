package com.example.scrolltrack.ui.detail

import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.draw.alpha
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
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = appName ?: packageName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant, // Reverted to default
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant, // Reverted to default
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant, // Reverted to default
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant // Reverted to default
                )
            )
        },
        modifier = modifier.background(MaterialTheme.colorScheme.background) // Reverted to default
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Period Selector Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, // Distribute buttons evenly
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonModifier = Modifier.weight(1f).padding(horizontal = 4.dp)

                ChartPeriodType.entries.forEach { period -> // Iterate through all enum entries
                    val isSelected = currentPeriodType == period
                    FilledTonalButton(
                        onClick = { viewModel.changeChartPeriod(packageName, period) },
                        modifier = buttonModifier,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(period.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
                    }
                }
            }

            // Date Navigation and Relocated Summary Information Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.navigateChartDate(packageName, -1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Period")
                }

                // --- Relocated Summary Information ---
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    periodDescriptionText?.let {
                        Text(
                            text = it.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = focusedUsageDisplay,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active: $focusedActiveUsageDisplay",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Scroll: $focusedScrollDisplay",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = focusedPeriodDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentPeriodType == ChartPeriodType.WEEKLY && weekNumberDisplay != null) {
                            Text(
                                text = " • ${weekNumberDisplay}",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    comparisonText?.let {
                        val (cardContainerColor, cardContentColor) = when (comparisonColorType) {
                            ComparisonColorType.GREEN -> Color(0xFFC8E6C9) to Color(0xFF388E3C)
                            ComparisonColorType.RED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            ComparisonColorType.GREY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val iconVector = when (comparisonIconType) {
                            ComparisonIconType.UP -> Icons.Filled.ArrowUpward
                            ComparisonIconType.DOWN -> Icons.Filled.ArrowDownward
                            ComparisonIconType.NEUTRAL -> Icons.Filled.HorizontalRule
                            ComparisonIconType.NONE -> null
                        }

                        Card(
                            modifier = Modifier.padding(top = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = cardContainerColor,
                                contentColor = cardContentColor
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                iconVector?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = "Comparison direction", // More generic description
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), // Make text bold
                                )
                            }
                        }
                    }
                }
                // --- End of Relocated Summary ---

                IconButton(
                    onClick = { viewModel.navigateChartDate(packageName, 1) },
                    enabled = canNavigateForward
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Next Period",
                        tint = if (canNavigateForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }

            // Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(vertical = 8.dp)
                    .pointerInput(Unit) { // Add pointerInput for swipe gestures
                        var swipeOffsetX = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consumeAllChanges() // Consume the drag events
                                swipeOffsetX += dragAmount
                            },
                            onDragEnd = {
                                val swipeThresholdPx = SWIPE_THRESHOLD_DP.dp.toPx()
                                if (swipeOffsetX > swipeThresholdPx) {
                                    // Swiped Right (older data)
                                    viewModel.navigateChartDate(packageName, -1)
                                } else if (swipeOffsetX < -swipeThresholdPx) {
                                    // Swiped Left (newer data)
                                    if (canNavigateForward) {
                                        viewModel.navigateChartDate(packageName, 1)
                                    }
                                }
                                swipeOffsetX = 0f // Reset for next gesture
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (chartData.isEmpty()) {
                    Text("Loading chart data...", style = MaterialTheme.typography.bodyLarge)
                } else {
                    UsageBarScrollLineChart(
                        modifier = Modifier.fillMaxSize(),
                        data = chartData,
                        periodType = currentPeriodType,
                        viewModel = viewModel,
                        packageName = packageName
                    )
                }
            }
        }
    }
}

@Composable
fun UsageBarScrollLineChart(
    modifier: Modifier = Modifier,
    data: List<AppDailyDetailData>,
    periodType: ChartPeriodType,
    viewModel: MainViewModel,
    packageName: String
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    // Hoisted Paint objects
    val axisLabelPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val legendPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val tooltipTextPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val chartAreaErrorPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }

    // Reset selectedBarIndex if periodType is DAILY, or if data/period changes
    LaunchedEffect(periodType, data) {
        if (periodType == ChartPeriodType.DAILY) { // Reset for DAILY view
            selectedBarIndex = null
        }
    }

    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data to display.")
        }
        return
    }

    val barColor = MaterialTheme.colorScheme.primary
    val scrollDistanceColor = MaterialTheme.colorScheme.tertiary
    val faintAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val axisLabelTextStyle = TextStyle(color = labelColor, fontSize = 11.sp)
    val selectedBarColor = MaterialTheme.colorScheme.secondary

    // Resolve tooltip colors and style here, in @Composable scope
    val tooltipBackgroundColor = MaterialTheme.colorScheme.inverseSurface
    // Using MaterialTheme.colorScheme.surface as a fallback for onInverseSurface due to resolution issues.
    // This should provide a contrasting color for text on inverseSurface.
    val tooltipActualTextColor = MaterialTheme.colorScheme.surface
    val tooltipTextStyle = MaterialTheme.typography.bodySmall.copy(color = tooltipActualTextColor)

    // Calculate tooltip scroll text within composable scope
    val tooltipScrollText by remember(selectedBarIndex, data, periodType, context) {
        derivedStateOf {
            if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
                val selectedData = data[selectedBarIndex!!]
                ConversionUtil.formatScrollDistance(selectedData.scrollUnits, context).first
            } else {
                "" // Default empty string when no tooltip or not applicable
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(periodType, data) { // Include periodType in key
            if (periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) { // Only enable tap gestures for WEEKLY/MONTHLY view
                detectTapGestures(
                    onTap = { tapOffset ->
                        val barCount = data.size
                        if (barCount == 0) return@detectTapGestures

                        val canvasWidth = size.width.toFloat()
                        val canvasHeight = size.height.toFloat()

                        // Restore Y-axis label width calculations for margin
                        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
                        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = axisLabelTextStyle).size.width.toFloat()
                        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
                        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 10f
                        val legendTotalHeight = legendItemHeight * 2

                        val tickMarkAndLabelPadding = 20f // Combined padding for tick marks and labels from chart edge
                        val leftMargin = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
                        val bottomMargin = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight
                        val topMargin = tickMarkAndLabelPadding // General top padding
                        val rightMargin = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding

                        val chartWidth = canvasWidth - leftMargin - rightMargin
                        val chartHeight = canvasHeight - bottomMargin - topMargin

                        if (chartWidth <= 0 || chartHeight <= 0) return@detectTapGestures

                        val totalBarWidthFactor = when (periodType) {
                            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 0.6f
                            ChartPeriodType.MONTHLY -> 0.7f
                        }
                        val totalBarWidth = chartWidth * totalBarWidthFactor
                        val barWidth = (totalBarWidth / barCount).coerceAtLeast(2f)
                        val barSpacing = (chartWidth - totalBarWidth) / (barCount + 1).coerceAtLeast(1)
                        // val usageTimes = data.map { it.usageTimeMillis } // Not directly needed here for hit testing logic
                        // val maxUsageTime = usageTimes.maxOrNull() ?: 1L // Not directly needed here
                        // val minUsageTime = 0L // Not directly needed here


                        var tappedBarFound = false
                        for (index in data.indices) {
                            val detail = data[index]
                            // Need maxUsageTime for accurate bar height calculation if it's not uniform
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
                                val tappedExistingSelection = selectedBarIndex == index
                                selectedBarIndex = if (tappedExistingSelection) null else index
                                tappedBarFound = true
                                // DO NOT call viewModel.setFocusedDate for WEEKLY/MONTHLY taps.
                                // The main summary should remain weekly/monthly average.
                                break
                            }
                        }
                        if (!tappedBarFound) {
                            selectedBarIndex = null // Clicked outside any bar
                        }
                    }
                )
            }
        }
    ) { // End of Canvas modifier, start of Canvas draw scope
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Restore Y-axis label width calculations for margin
        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = axisLabelTextStyle).size.width.toFloat()
        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()

        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 10f
        val legendTotalHeight = legendItemHeight * 2
        
        val tickMarkAndLabelPadding = 20f // Combined padding for tick marks and labels from chart edge
        val leftMargin = yAxisLabelUsageTimeWidth + tickMarkAndLabelPadding
        val bottomMargin = xAxisLabelHeight + tickMarkAndLabelPadding + legendTotalHeight
        val topMargin = tickMarkAndLabelPadding // General top padding
        val rightMargin = yAxisLabelScrollDistWidth + tickMarkAndLabelPadding

        val chartWidth = canvasWidth - leftMargin - rightMargin
        var chartHeight = canvasHeight - bottomMargin - topMargin

        if (chartWidth <= 0 || chartHeight <= 0) {
            drawIntoCanvas { canvas ->
                chartAreaErrorPaint.apply {
                    textSize = 14.sp.toPx()
                    color = labelColor.toArgb()
                }
                canvas.nativeCanvas.drawText("Chart area too small", center.x, center.y, chartAreaErrorPaint)
            }
            return@Canvas
        }

        // Vertical Y-Axis lines are NOT drawn. Only X-axis base line.
        drawLine(color = faintAxisColor, start = Offset(leftMargin, topMargin + chartHeight), end = Offset(leftMargin + chartWidth, topMargin + chartHeight), strokeWidth = 1f) // Keep X-Axis Base Line

        val usageTimes = data.map { it.usageTimeMillis }
        val actualMaxUsageTime = usageTimes.maxOrNull() ?: 1L // Actual max for data scaling
        val minUsageTime = 0L

        val scrollUnitsList = data.map { it.scrollUnits }
        val actualMaxScrollUnits = scrollUnitsList.maxOrNull() ?: 1L // Actual max for data scaling
        val minScrollUnits = 0L

        // Add padding to the max values for Y-axis scale to prevent labels/ticks hitting the top
        val Y_AXIS_PADDING_FACTOR = 1.15f // Use 15% padding, adjust as needed (e.g., 1.20f for 20%)
        val maxUsageTimeForAxis = (actualMaxUsageTime * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L) 
        val maxScrollUnitsForAxis = (actualMaxScrollUnits * Y_AXIS_PADDING_FACTOR).toLong().coerceAtLeast(1L)

        val barCount = data.size
        // Calculate barWidth and barSpacing here, before they are used for dots
        val barWidth: Float
        val barSpacing: Float
        if (barCount > 0) {
            val totalBarWidthFactor = when (periodType) {
                ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 0.6f
                ChartPeriodType.MONTHLY -> 0.7f
            }
            val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
            barWidth = (totalBarLayoutWidth / barCount).coerceAtLeast(2f)
            barSpacing = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)
        } else {
            barWidth = 0f
            barSpacing = 0f
        }

        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.usageTimeMillis > 0) { // Ensure conditional drawing based on data
                    // Scale bars based on actualMaxUsageTime for correct representation of data
                    val barHeightNorm = ((detail.usageTimeMillis - minUsageTime).toFloat() / (actualMaxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
                    val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx()) // Min height for visibility

                    val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                    val barTop = topMargin + chartHeight - barActualHeight
                    val barRight = barLeft + barWidth
                    val barBottom = topMargin + chartHeight

                    val currentBarColor = when {
                        periodType == ChartPeriodType.DAILY -> barColor
                        selectedBarIndex == null -> barColor
                        selectedBarIndex == index -> selectedBarColor
                        else -> barColor.copy(alpha = 0.4f)
                    }

                    val dynamicCornerRadius = when (periodType) {
                        ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 24f // Increased rounding
                        ChartPeriodType.MONTHLY -> 8f
                    }
                    // Use dynamicCornerRadius directly for full rounding regardless of height
                    // val adjustedCornerRadius = minOf(dynamicCornerRadius, barActualHeight / 2f).coerceAtLeast(0f)

                    if (barActualHeight > 0) {
                         val path = Path().apply {
                            moveTo(barLeft, barBottom)
                            lineTo(barLeft, barTop + dynamicCornerRadius) // Use full radius
                            arcTo(
                                rect = Rect(left = barLeft, top = barTop, right = barLeft + 2 * dynamicCornerRadius, bottom = barTop + 2 * dynamicCornerRadius),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(barRight - dynamicCornerRadius, barTop) // Use full radius
                            arcTo(
                                rect = Rect(left = barRight - 2 * dynamicCornerRadius, top = barTop, right = barRight, bottom = barTop + 2 * dynamicCornerRadius),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(barRight, barBottom)
                            close()
                        }
                        drawPath(path, color = currentBarColor)
                    }

                    // --- Draw Active Time Bar ---
                    if (detail.activeTimeMillis > 0) {
                        val activeBarHeightNorm = (detail.activeTimeMillis.toFloat() / actualMaxUsageTime.toFloat()).coerceIn(0f, 1f)
                        val activeBarActualHeight = (activeBarHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())
                        val activeBarTop = topMargin + chartHeight - activeBarActualHeight
                        val activeBarWidth = barWidth * 0.6f // Make it slightly thinner
                        val activeBarLeft = barLeft + (barWidth - activeBarWidth) / 2

                        drawRect(
                            color = currentBarColor.copy(
                                red = currentBarColor.red * 0.75f,
                                green = currentBarColor.green * 0.75f,
                                blue = currentBarColor.blue * 0.75f
                            ), // A darker version of the main bar color
                            topLeft = Offset(activeBarLeft, activeBarTop),
                            size = androidx.compose.ui.geometry.Size(activeBarWidth, activeBarActualHeight)
                        )
                    }
                }
            }
        }

        // Collect scroll data points first
        val scrollDataPoints = mutableListOf<Offset>()
        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.scrollUnits > 0) { // Only consider points with scroll data
                    val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                    val barCenterX = barLeft + barWidth / 2

                    // Use actualMaxScrollUnits for scaling the data points themselves to the chart height
                    val scrollRange = (actualMaxScrollUnits - minScrollUnits).coerceAtLeast(1L)
                    val yNorm = if (scrollRange == 0L) 0f else ((detail.scrollUnits - minScrollUnits).toFloat() / scrollRange.toFloat())
                    val y = topMargin + chartHeight - (yNorm * chartHeight)
                    val point = Offset(barCenterX, y.coerceIn(topMargin, topMargin + chartHeight))
                    scrollDataPoints.add(point)
                }
            }
        }

        // Draw the scroll line (straight or curved)
        if (scrollDataPoints.size >= 2) {
            val linePath = Path()
            if (scrollDataPoints.size == 2) {
                // Straight line for two points
                linePath.moveTo(scrollDataPoints[0].x, scrollDataPoints[0].y)
                linePath.lineTo(scrollDataPoints[1].x, scrollDataPoints[1].y)
            } else {
                // Smooth curve for more than two points (Catmull-Rom to Bezier)
                val splinePoints = mutableListOf<Offset>()
                splinePoints.add(scrollDataPoints.first()) // Duplicate first point for boundary condition P[-1] = P[0]
                splinePoints.addAll(scrollDataPoints)
                splinePoints.add(scrollDataPoints.last())  // Duplicate last point for boundary condition P[n] = P[n-1]

                linePath.moveTo(splinePoints[1].x, splinePoints[1].y) // Move to the first actual data point

                for (i in 1 until splinePoints.size - 2) {
                    val p0 = splinePoints[i-1]
                    val p1 = splinePoints[i]   // Current start of segment
                    val p2 = splinePoints[i+1] // Current end of segment
                    val p3 = splinePoints[i+2]

                    // Calculate control points for cubic Bezier (Catmull-Rom to Bezier)
                    val cp1x = p1.x + (p2.x - p0.x) / 6.0f
                    val cp1y = p1.y + (p2.y - p0.y) / 6.0f
                    val cp2x = p2.x - (p3.x - p1.x) / 6.0f
                    val cp2y = p2.y - (p3.y - p1.y) / 6.0f

                    linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
            }
            drawPath(
                path = linePath,
                color = scrollDistanceColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }

        // Draw circles at each scroll data point (on top of the line)
        scrollDataPoints.forEach { point ->
            drawCircle(
                color = scrollDistanceColor,
                radius = 4.dp.toPx(),
                center = point
            )
        }

        val xLabelCount = data.size
        // X-axis label drawing logic - THIS MUST BE PRESERVED
        if (barCount > 0) {
            val totalBarLayoutWidthForXLabels = chartWidth * 0.8f // Factor for X-axis labels
            val barWidthForXLabels = (totalBarLayoutWidthForXLabels / barCount).coerceAtLeast(2f)
            val barSpacingForXLabels = (chartWidth - totalBarLayoutWidthForXLabels) / (barCount + 1).coerceAtLeast(1)

            data.forEachIndexed { index, dailyData ->
                val dateStr = dailyData.date
                val formattedDate = when (periodType) {
                    ChartPeriodType.DAILY -> getDayOfWeekLetter(dateStr)
                    ChartPeriodType.WEEKLY -> getDayOfWeekLetter(dateStr)
                    ChartPeriodType.MONTHLY -> {
                        try {
                            val dayOfMonth = dateStr.substringAfterLast('-').toInt()
                            val isLastDay = index == data.size - 1
                            val isFirstDay = dayOfMonth == 1
                            val isMultipleOf5 = dayOfMonth % 5 == 0
                            val isDay30In31DayMonth = dayOfMonth == 30 && data.size == 31

                            if (isFirstDay || isLastDay || (isMultipleOf5 && !isDay30In31DayMonth) ) {
                                dayOfMonth.toString()
                            } else {
                                ""
                            }
                        } catch (e: Exception) { "" }
                    }
                }
                if (formattedDate.isNotEmpty()){
                    val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(formattedDate) }, style = axisLabelTextStyle)
                    val labelX = leftMargin + barSpacingForXLabels + index * (barWidthForXLabels + barSpacingForXLabels) + barWidthForXLabels / 2 - textLayoutResult.size.width / 2
                    axisLabelPaint.apply {
                        color = labelColor.toArgb()
                        textSize = 10.sp.toPx()
                    }
                    drawContext.canvas.nativeCanvas.drawText(formattedDate, labelX, topMargin + chartHeight + 5f + textLayoutResult.size.height, axisLabelPaint)
                }
            }
        }
        // END OF X-AXIS LABEL LOGIC

        val yLabelCount = 4 // Restore yLabelCount for usage time ticks

        // Revert to linear interpolation for Y-axis labels and grid lines for usage time (left side)
        for (i in 0..yLabelCount) {
            // Use maxUsageTimeForAxis for creating the Y-axis scale labels
            val value = minUsageTime + (i.toFloat() / yLabelCount) * (maxUsageTimeForAxis - minUsageTime)
            val labelText = formatMillisToHoursOrMinutes(value.toLong())
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            
            if (labelY >= topMargin && labelY <= topMargin + chartHeight) {
                // Draw grid line
                drawLine(color = faintAxisColor.copy(alpha = 0.5f), start = Offset(leftMargin, labelY), end = Offset(leftMargin + chartWidth, labelY), strokeWidth = 0.5f)
                // Draw tick mark
                drawLine(color = faintAxisColor, start = Offset(leftMargin - 5f, labelY), end = Offset(leftMargin, labelY), strokeWidth = 1f)
                axisLabelPaint.apply {
                    color = labelColor.toArgb()
                    textSize = 10.sp.toPx()
                }
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    leftMargin - textLayoutResult.size.width - 10f, 
                    labelY + textLayoutResult.size.height / 2f - 5f, 
                    axisLabelPaint
                )
            }
        }

        // Scroll distance Y-axis ticks remain as they were (linear interpolation)
        val yScrollLabelCount = 4 // Use a distinct count for scroll if needed, or keep it 4
        for (i in 0..yScrollLabelCount) {
            // Use maxScrollUnitsForAxis for creating the Y-axis scale labels
            val value = minScrollUnits + (i.toFloat() / yScrollLabelCount) * (maxScrollUnitsForAxis - minScrollUnits)
            // Pass actualMaxScrollUnits to formatScrollForAxis for contextually correct unit formatting
            val labelText = formatScrollForAxis(value.toFloat(), actualMaxScrollUnits, context)
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yScrollLabelCount) * chartHeight
            // Draw grid line (already drawn by the loop above, but if we wanted separate control, it'd be here)
            // drawLine(color = faintAxisColor.copy(alpha = 0.5f), start = Offset(leftMargin, labelY), end = Offset(leftMargin + chartWidth, labelY), strokeWidth = 0.5f)
            // Draw tick mark (short line extending from where axis *would* be)
            drawLine(color = faintAxisColor, start = Offset(leftMargin + chartWidth, labelY), end = Offset(leftMargin + chartWidth + 5f, labelY), strokeWidth = 1f)
            axisLabelPaint.apply {
                color = labelColor.toArgb()
                textSize = 10.sp.toPx()
            }
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin + chartWidth + 10f, labelY + textLayoutResult.size.height / 2 - 5f, axisLabelPaint)
        }

        val legendStartY = topMargin + chartHeight + xAxisLabelHeight + 20f
        val legendItemSize = 12.sp.toPx()
        val legendTextPadding = 8.dp.toPx()

        drawRect(color = barColor, topLeft = Offset(leftMargin, legendStartY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        legendPaint.apply {
            color = labelColor.toArgb()
            textSize = 11.sp.toPx()
        }
        drawContext.canvas.nativeCanvas.drawText("Usage", leftMargin + legendItemSize + legendTextPadding, legendStartY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Usage") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        val secondLegendItemY = legendStartY + legendItemHeight
        drawRect(color = scrollDistanceColor, topLeft = Offset(leftMargin, secondLegendItemY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        legendPaint.apply {
            color = labelColor.toArgb()
            textSize = 11.sp.toPx()
        }
        drawContext.canvas.nativeCanvas.drawText("Scroll", leftMargin + legendItemSize + legendTextPadding, secondLegendItemY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Scroll") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        // --- Draw Tooltip if a bar is selected (Only for WEEKLY/MONTHLY) ---
        if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
            val selectedData = data[selectedBarIndex!!]

            val usageText = formatMillisToHoursMinutesSeconds(selectedData.usageTimeMillis)
            val scrollText = tooltipScrollText

            val usageTextLayout = textMeasurer.measure(buildAnnotatedString{ append(usageText) }, style = tooltipTextStyle)
            val scrollTextLayout = textMeasurer.measure(buildAnnotatedString{ append(scrollText) }, style = tooltipTextStyle)

            val tooltipPaddingHorizontal = 8.dp.toPx()
            val tooltipPaddingVertical = 4.dp.toPx()
            val tooltipTextSpacing = 2.dp.toPx() // Spacing between usage and scroll text lines

            val tooltipContentWidth = maxOf(usageTextLayout.size.width, scrollTextLayout.size.width).toFloat()
            val tooltipContentHeight = usageTextLayout.size.height + scrollTextLayout.size.height + tooltipTextSpacing

            val tooltipTotalWidth = tooltipContentWidth + 2 * tooltipPaddingHorizontal
            val tooltipTotalHeight = tooltipContentHeight + 2 * tooltipPaddingVertical

            // Calculate selected bar's center X and top Y
            val barCount = data.size
            val totalBarWidthFactor = 0.8f
            val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
            val barWidth = (totalBarLayoutWidth / barCount).coerceAtLeast(2f)
            val barSpacing = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)

            val selectedBarLeft = leftMargin + barSpacing + selectedBarIndex!! * (barWidth + barSpacing)
            val selectedBarCenterX = selectedBarLeft + barWidth / 2

            val usageTimes = data.map { it.usageTimeMillis }
            val maxUsageTime = usageTimes.maxOrNull() ?: 1L
            val minUsageTime = 0L
            val selectedBarUsageNorm = ((selectedData.usageTimeMillis - minUsageTime).toFloat() / (maxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
            val selectedBarActualHeight = (selectedBarUsageNorm * chartHeight).coerceAtLeast(0f)
            val selectedBarTopCanvas = topMargin + chartHeight - selectedBarActualHeight

            val tooltipGap = 8.dp.toPx()
            var tooltipX = selectedBarCenterX - tooltipTotalWidth / 2
            var tooltipY = selectedBarTopCanvas - tooltipTotalHeight - tooltipGap

            // Basic clamping to keep tooltip within chart horizontal bounds
            if (tooltipX < leftMargin) tooltipX = leftMargin
            if (tooltipX + tooltipTotalWidth > leftMargin + chartWidth) tooltipX = leftMargin + chartWidth - tooltipTotalWidth
            // Basic clamping for top
            if (tooltipY < topMargin) tooltipY = topMargin

            // Draw tooltip background
            drawRoundRect(
                color = tooltipBackgroundColor,
                topLeft = Offset(tooltipX, tooltipY),
                size = androidx.compose.ui.geometry.Size(tooltipTotalWidth, tooltipTotalHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )

            // Draw usage text
            drawContext.canvas.nativeCanvas.drawText(
                usageText,
                tooltipX + tooltipPaddingHorizontal,
                tooltipY + tooltipPaddingVertical + usageTextLayout.size.height, // Y is baseline for text
                tooltipTextPaint.apply {
                    color = tooltipActualTextColor.toArgb()
                    textSize = tooltipTextStyle.fontSize.toPx()
                }
            )
            // Draw scroll text
            drawContext.canvas.nativeCanvas.drawText(
                scrollText,
                tooltipX + tooltipPaddingHorizontal,
                tooltipY + tooltipPaddingVertical + usageTextLayout.size.height + tooltipTextSpacing + scrollTextLayout.size.height, // Y is baseline
                tooltipTextPaint.apply {
                    color = tooltipActualTextColor.toArgb()
                    textSize = tooltipTextStyle.fontSize.toPx()
                }
            )
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