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
import com.example.scrolltrack.ui.detail.ChartPeriodType
import com.example.scrolltrack.ui.main.ComparisonColorType
import com.example.scrolltrack.ui.main.ComparisonIconType
import com.example.scrolltrack.ui.detail.AppDetailViewModel
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import com.example.scrolltrack.util.ConversionUtil
import java.text.NumberFormat
import kotlin.math.roundToInt
import android.content.Context
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import com.example.scrolltrack.ui.detail.ChartState
import com.example.scrolltrack.ui.detail.rememberChartState
import androidx.compose.ui.unit.toSize

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
    viewModel: AppDetailViewModel,
    packageName: String,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(packageName) {
        // viewModel.loadAppDetailsInfo(packageName) // This is now handled in the ViewModel's init block
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
    val focusedOpenCount by viewModel.appDetailFocusedOpenCount.collectAsStateWithLifecycle()

    val chartState = rememberChartState(data = chartData, periodType = currentPeriodType)

    val canNavigateForward by remember(currentPeriodType, currentReferenceDateStr) {
        derivedStateOf {
            val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val refDateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = DateUtil.parseLocalDateString(currentReferenceDateStr) ?: Date()
            }

            when (currentPeriodType) {
                ChartPeriodType.DAILY -> !DateUtils.isToday(refDateCal.timeInMillis)
                ChartPeriodType.WEEKLY -> {
                    val endOfWeekForRef = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        time = refDateCal.time
                        // Assuming week ends on the reference date for simplicity of check
                    }
                    // If the end of the current week view is before today, allow moving forward.
                    // This logic assumes referenceDate is the *end* of the period.
                    // If refDateCal is already today or in future, can't go further.
                    !DateUtils.isToday(endOfWeekForRef.timeInMillis) && endOfWeekForRef.before(today)

                }
                ChartPeriodType.MONTHLY -> {
                    val endOfMonthForRef = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                        onClick = { viewModel.changeChartPeriod(period) },
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
                IconButton(onClick = { viewModel.navigateChartDate(-1) }) {
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
                            color = MaterialTheme.colorScheme.tertiary
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
                            ComparisonColorType.GREEN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            ComparisonColorType.RED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            ComparisonColorType.GREY -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val iconVector = when (comparisonIconType) {
                            ComparisonIconType.UP -> Icons.Filled.ArrowUpward
                            ComparisonIconType.DOWN -> Icons.Filled.ArrowDownward
                            ComparisonIconType.NEUTRAL -> Icons.Filled.HorizontalRule
                            ComparisonIconType.NONE -> null
                        }

                        Card(
                            modifier = Modifier.padding(top = 8.dp),
                            shape = MaterialTheme.shapes.large,
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
                    onClick = { viewModel.navigateChartDate(1) },
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
                                swipeOffsetX += dragAmount
                            },
                            onDragEnd = {
                                val swipeThresholdPx = SWIPE_THRESHOLD_DP.dp.toPx()
                                if (swipeOffsetX > swipeThresholdPx) {
                                    // Swiped Right (older data)
                                    viewModel.navigateChartDate(-1)
                                } else if (swipeOffsetX < -swipeThresholdPx) {
                                    // Swiped Left (newer data)
                                    if (canNavigateForward) {
                                        viewModel.navigateChartDate(1)
                                    }
                                }
                                swipeOffsetX = 0f // Reset for next gesture
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (chartState.hasData) {
                    UsageBarScrollLineChart(
                        modifier = Modifier.fillMaxSize(),
                        chartState = chartState
                    )
                } else {
                    Text("Loading chart data...", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (focusedOpenCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Times Opened",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$focusedOpenCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
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
    chartState: ChartState
) {
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current

    // Hoisted Paint objects for performance
    val axisLabelPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val legendPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val tooltipTextPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.LEFT } }
    val chartAreaErrorPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }

    if (!chartState.hasData) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data to display.", color = chartState.colors.labelColor)
        }
        return
    }

    Canvas(
        modifier = modifier.pointerInput(chartState) {
            detectTapGestures(
                onTap = { tapOffset ->
                    chartState.handleTap(tapOffset, size.toSize())
                }
            )
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val chartWidth = canvasWidth - chartState.leftMargin - chartState.rightMargin
        val chartHeight = canvasHeight - chartState.bottomMargin - chartState.topMargin

        if (chartWidth <= 0 || chartHeight <= 0) {
            drawIntoCanvas { canvas ->
                chartAreaErrorPaint.apply {
                    textSize = 14.sp.toPx()
                    color = chartState.colors.labelColor.toArgb()
                }
                canvas.nativeCanvas.drawText("Chart area too small", center.x, center.y, chartAreaErrorPaint)
            }
            return@Canvas
        }

        // --- AXIS & GRID LINES ---
        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val ratio = i.toFloat() / yLabelCount
            val yPos = chartState.topMargin + chartHeight - (ratio * chartHeight)

            if (yPos >= chartState.topMargin && yPos <= chartState.topMargin + chartHeight) {
                // Grid line
                drawLine(
                    color = chartState.colors.faintAxisColor,
                    start = Offset(chartState.leftMargin, yPos),
                    end = Offset(chartState.leftMargin + chartWidth, yPos),
                    strokeWidth = 1f
                )

                // Left Y-axis (Usage Time)
                val usageValue = chartState.minUsageTime + ratio * (chartState.maxUsageTime - chartState.minUsageTime)
                val usageLabelText = formatMillisToHoursOrMinutes(usageValue.toLong())
                val usageTextLayout = textMeasurer.measure(buildAnnotatedString { append(usageLabelText) }, style = chartState.styles.axisLabelTextStyle)
                axisLabelPaint.color = chartState.colors.labelColor.toArgb()
                axisLabelPaint.textSize = chartState.styles.axisLabelTextStyle.fontSize.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    usageLabelText,
                    chartState.leftMargin - usageTextLayout.size.width - 10f,
                    yPos + usageTextLayout.size.height / 2f,
                    axisLabelPaint
                )

                // Right Y-axis (Scroll Distance)
                val scrollValue = chartState.minScrollUnits + ratio * (chartState.maxScrollUnitsForAxis - chartState.minScrollUnits)
                val scrollLabelText = formatScrollForAxis(scrollValue.toFloat(), chartState.maxScrollUnits, context)
                val scrollTextLayout = textMeasurer.measure(buildAnnotatedString { append(scrollLabelText) }, style = chartState.styles.axisLabelTextStyle)
                drawContext.canvas.nativeCanvas.drawText(
                    scrollLabelText,
                    chartState.leftMargin + chartWidth + 10f,
                    yPos + scrollTextLayout.size.height / 2,
                    axisLabelPaint
                )
            }
        }
        // Base X-Axis Line
        drawLine(
            color = chartState.colors.faintAxisColor,
            start = Offset(chartState.leftMargin, chartState.topMargin + chartHeight),
            end = Offset(chartState.leftMargin + chartWidth, chartState.topMargin + chartHeight),
            strokeWidth = 1f
        )

        val barCount = chartState.data.size
        val totalBarWidthFactor = when (chartState.periodType) {
            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 0.6f
            ChartPeriodType.MONTHLY -> 0.7f
        }
        val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
        val barWidth = (totalBarLayoutWidth / barCount).coerceAtLeast(2f)
        val barSpacing = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)

        chartState.data.forEachIndexed { index, detail ->
            if (detail.usageTimeMillis > 0) {
                val barHeightNorm = ((detail.usageTimeMillis - chartState.minUsageTime).toFloat() / (chartState.maxUsageTime - chartState.minUsageTime).coerceAtLeast(1L).toFloat())
                val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())

                val barLeft = chartState.leftMargin + barSpacing + index * (barWidth + barSpacing)
                val barTop = chartState.topMargin + chartHeight - barActualHeight
                val barRight = barLeft + barWidth

                val currentBarColor = when {
                    chartState.periodType == ChartPeriodType.DAILY -> chartState.colors.barColor
                    chartState.selectedBarIndex == null -> chartState.colors.barColor
                    chartState.selectedBarIndex == index -> chartState.colors.selectedBarColor
                    else -> chartState.colors.barColor.copy(alpha = 0.4f)
                }

                val dynamicCornerRadius = when (chartState.periodType) {
                    ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 24f
                    ChartPeriodType.MONTHLY -> 8f
                }
                val path = Path().apply {
                    moveTo(barLeft, chartState.topMargin + chartHeight)
                    lineTo(barLeft, barTop + dynamicCornerRadius)
                    arcTo(
                        rect = Rect(left = barLeft, top = barTop, right = barLeft + 2 * dynamicCornerRadius, bottom = barTop + 2 * dynamicCornerRadius),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(barRight - dynamicCornerRadius, barTop)
                    arcTo(
                        rect = Rect(left = barRight - 2 * dynamicCornerRadius, top = barTop, right = barRight, bottom = barTop + 2 * dynamicCornerRadius),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(barRight, chartState.topMargin + chartHeight)
                    close()
                }
                drawPath(path, color = currentBarColor)

                if (detail.activeTimeMillis > 0) {
                    val activeBarHeightNorm = (detail.activeTimeMillis.toFloat() / chartState.maxUsageTime.toFloat()).coerceIn(0f, 1f)
                    val activeBarActualHeight = (activeBarHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx())
                    val activeBarTop = chartState.topMargin + chartHeight - activeBarActualHeight
                    val activeBarWidth = barWidth * 0.6f
                    val activeBarLeft = barLeft + (barWidth - activeBarWidth) / 2

                    drawRect(
                        color = chartState.colors.activeBarColor,
                        topLeft = Offset(activeBarLeft, activeBarTop),
                        size = androidx.compose.ui.geometry.Size(activeBarWidth, activeBarActualHeight)
                    )
                }
            }
        }

        val scrollDataPoints = chartState.data.mapIndexedNotNull { index, detail ->
            if (detail.scrollUnits <= 0) return@mapIndexedNotNull null
            val barLeft = chartState.leftMargin + barSpacing + index * (barWidth + barSpacing)
            val barCenterX = barLeft + barWidth / 2
            val scrollRange = (chartState.maxScrollUnits - chartState.minScrollUnits).coerceAtLeast(1L)
            val yNorm = if (scrollRange == 0L) 0f else ((detail.scrollUnits - chartState.minScrollUnits).toFloat() / scrollRange.toFloat())
            val y = chartState.topMargin + chartHeight - (yNorm * chartHeight)
            Offset(barCenterX, y.coerceIn(chartState.topMargin, chartState.topMargin + chartHeight))
        }

        if (scrollDataPoints.size >= 2) {
            val linePath = Path()
            val splinePoints = mutableListOf<Offset>().apply {
                if (scrollDataPoints.isNotEmpty()) {
                    add(scrollDataPoints.first())
                    addAll(scrollDataPoints)
                    add(scrollDataPoints.last())
                }
            }
            if (splinePoints.isNotEmpty()) {
                linePath.moveTo(splinePoints[1].x, splinePoints[1].y)
                for (i in 1 until splinePoints.size - 2) {
                    val p0 = splinePoints[i - 1]
                    val p1 = splinePoints[i]
                    val p2 = splinePoints[i + 1]
                    val p3 = splinePoints[i + 2]
                    val cp1x = p1.x + (p2.x - p0.x) / 6.0f
                    val cp1y = p1.y + (p2.y - p0.y) / 6.0f
                    val cp2x = p2.x - (p3.x - p1.x) / 6.0f
                    val cp2y = p2.y - (p3.y - p1.y) / 6.0f
                    linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
                drawPath(path = linePath, color = chartState.colors.scrollLineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }

        scrollDataPoints.forEach { point ->
            drawCircle(color = chartState.colors.scrollLineColor, radius = 4.dp.toPx(), center = point)
        }

        chartState.data.forEachIndexed { index, dailyData ->
            val formattedDate = when (chartState.periodType) {
                ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> getDayOfWeekLetter(dailyData.date)
                ChartPeriodType.MONTHLY -> {
                    try {
                        val dayOfMonth = dailyData.date.substringAfterLast('-').toInt()
                        if (dayOfMonth == 1 || index == chartState.data.size - 1 || (dayOfMonth % 5 == 0 && !(dayOfMonth == 30 && chartState.data.size == 31))) {
                            dayOfMonth.toString()
                        } else ""
                    } catch (e: Exception) { "" }
                }
            }
            if (formattedDate.isNotEmpty()) {
                val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(formattedDate) }, style = chartState.styles.axisLabelTextStyle)
                val labelX = chartState.leftMargin + barSpacing + index * (barWidth + barSpacing) + barWidth / 2 - textLayoutResult.size.width / 2
                axisLabelPaint.color = chartState.colors.labelColor.toArgb()
                axisLabelPaint.textSize = chartState.styles.axisLabelTextStyle.fontSize.toPx()
                drawContext.canvas.nativeCanvas.drawText(formattedDate, labelX, chartState.topMargin + chartHeight + 5f + textLayoutResult.size.height, axisLabelPaint)
            }
        }

        val legendStartY = chartState.topMargin + chartHeight + chartState.xAxisLabelHeight + 20f
        val legendItemSize = 10.dp.toPx()
        val legendTextPadding = 8.dp.toPx()

        drawRect(color = chartState.colors.barColor, topLeft = Offset(chartState.leftMargin, legendStartY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        legendPaint.color = chartState.styles.legendTextStyle.color.toArgb()
        legendPaint.textSize = chartState.styles.legendTextStyle.fontSize.toPx()
        val usageLegendLayout = textMeasurer.measure(buildAnnotatedString { append("Usage") }, style = chartState.styles.legendTextStyle)
        drawContext.canvas.nativeCanvas.drawText("Usage", chartState.leftMargin + legendItemSize + legendTextPadding, legendStartY + usageLegendLayout.size.height / 2f, legendPaint)

        val secondLegendItemY = legendStartY + chartState.legendItemHeight
        drawRect(color = chartState.colors.scrollLineColor, topLeft = Offset(chartState.leftMargin, secondLegendItemY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        val scrollLegendLayout = textMeasurer.measure(buildAnnotatedString { append("Scroll") }, style = chartState.styles.legendTextStyle)
        drawContext.canvas.nativeCanvas.drawText("Scroll", chartState.leftMargin + legendItemSize + legendTextPadding, secondLegendItemY + scrollLegendLayout.size.height / 2f, legendPaint)

        chartState.selectedBarIndex?.let { index ->
            val selectedData = chartState.data[index]
            val usageText = formatMillisToHoursMinutesSeconds(selectedData.usageTimeMillis)
            val scrollText = chartState.getTooltipScrollText(index)
            val usageTextLayout = textMeasurer.measure(buildAnnotatedString { append(usageText) }, style = chartState.styles.tooltipTextStyle)
            val scrollTextLayout = textMeasurer.measure(buildAnnotatedString { append(scrollText) }, style = chartState.styles.tooltipTextStyle)

            val tooltipPaddingHorizontal = 8.dp.toPx()
            val tooltipPaddingVertical = 4.dp.toPx()
            val tooltipTextSpacing = 2.dp.toPx()

            val tooltipContentWidth = maxOf(usageTextLayout.size.width, scrollTextLayout.size.width).toFloat()
            val tooltipContentHeight = usageTextLayout.size.height + scrollTextLayout.size.height + tooltipTextSpacing
            val tooltipTotalWidth = tooltipContentWidth + 2 * tooltipPaddingHorizontal
            val tooltipTotalHeight = tooltipContentHeight + 2 * tooltipPaddingVertical

            val selectedBarLeft = chartState.leftMargin + barSpacing + index * (barWidth + barSpacing)
            val selectedBarCenterX = selectedBarLeft + barWidth / 2
            val selectedBarUsageNorm = ((selectedData.usageTimeMillis - chartState.minUsageTime).toFloat() / (chartState.maxUsageTime - chartState.minUsageTime).coerceAtLeast(1L).toFloat())
            val selectedBarActualHeight = (selectedBarUsageNorm * chartHeight).coerceAtLeast(0f)
            val selectedBarTopCanvas = chartState.topMargin + chartHeight - selectedBarActualHeight

            val tooltipGap = 8.dp.toPx()
            var tooltipX = selectedBarCenterX - tooltipTotalWidth / 2
            var tooltipY = selectedBarTopCanvas - tooltipTotalHeight - tooltipGap

            if (tooltipX < chartState.leftMargin) tooltipX = chartState.leftMargin
            if (tooltipX + tooltipTotalWidth > chartState.leftMargin + chartWidth) tooltipX = chartState.leftMargin + chartWidth - tooltipTotalWidth
            if (tooltipY < chartState.topMargin) tooltipY = chartState.topMargin

            drawRoundRect(
                color = chartState.colors.tooltipBackgroundColor,
                topLeft = Offset(tooltipX, tooltipY),
                size = androidx.compose.ui.geometry.Size(tooltipTotalWidth, tooltipTotalHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
            tooltipTextPaint.apply {
                color = chartState.colors.tooltipTextColor.toArgb()
                textSize = chartState.styles.tooltipTextStyle.fontSize.toPx()
            }
            drawContext.canvas.nativeCanvas.drawText(
                usageText,
                tooltipX + tooltipPaddingHorizontal,
                tooltipY + tooltipPaddingVertical + usageTextLayout.size.height,
                tooltipTextPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                scrollText,
                tooltipX + tooltipPaddingHorizontal,
                tooltipY + tooltipPaddingVertical + usageTextLayout.size.height + tooltipTextSpacing + scrollTextLayout.size.height,
                tooltipTextPaint
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