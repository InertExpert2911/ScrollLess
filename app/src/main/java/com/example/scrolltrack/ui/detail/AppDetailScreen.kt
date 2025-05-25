package com.example.scrolltrack.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.main.AppDailyDetailData
import com.example.scrolltrack.ui.main.ChartPeriodType
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

    // Collect new summary states
    val focusedUsageDisplay by viewModel.appDetailFocusedUsageDisplay.collectAsStateWithLifecycle()
    val focusedPeriodDisplay by viewModel.appDetailFocusedPeriodDisplay.collectAsStateWithLifecycle()
    val comparisonText by viewModel.appDetailComparisonText.collectAsStateWithLifecycle()
    val comparisonIsPositive by viewModel.appDetailComparisonIsPositive.collectAsStateWithLifecycle()
    val weekNumberDisplay by viewModel.appDetailWeekNumberDisplay.collectAsStateWithLifecycle()
    val periodDescriptionText by viewModel.appDetailPeriodDescriptionText.collectAsStateWithLifecycle()

    val canNavigateForward by remember(currentPeriodType, currentReferenceDateStr) {
        derivedStateOf {
            val today = Calendar.getInstance()
            val refDateCal = Calendar.getInstance().apply {
                time = DateUtil.parseDateString(currentReferenceDateStr) ?: Date()
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
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = appName ?: packageName)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier
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
                        Card(
                            modifier = Modifier.padding(top = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                                contentColor = if (comparisonIsPositive) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
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
                    .pointerInput(currentPeriodType, chartData) { // Add pointerInput for swipe gestures
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
                    Text("Loading chart data...")
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

    Canvas(
        modifier = modifier.pointerInput(periodType, data) { // Include periodType in key
            detectTapGestures(
                onTap = { tapOffset ->
                    val barCount = data.size
                    if (barCount == 0) return@detectTapGestures

                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()

                    val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
                    val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = axisLabelTextStyle).size.width.toFloat()
                    val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
                    val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 10f
                    val legendTotalHeight = legendItemHeight * 2

                    val leftMargin = yAxisLabelUsageTimeWidth + 20f
                    val bottomMargin = xAxisLabelHeight + 20f + legendTotalHeight
                    val topMargin = 20f
                    val rightMargin = yAxisLabelScrollDistWidth + 20f

                    val chartWidth = canvasWidth - leftMargin - rightMargin
                    val chartHeight = canvasHeight - bottomMargin - topMargin

                    if (chartWidth <= 0 || chartHeight <= 0) return@detectTapGestures

                    val totalBarWidthFactor = 0.8f
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
                            // For DAILY view, on tap, we might want to update the focused date in ViewModel
                            if (periodType == ChartPeriodType.DAILY && selectedBarIndex != null && selectedBarIndex!! < data.size) {
                                val selectedDate = data[selectedBarIndex!!].date
                                viewModel.setFocusedDateFromChart(selectedDate) 
                            }
                            break
                        }
                    }
                    if (!tappedBarFound) {
                        selectedBarIndex = null // Clicked outside any bar
                    }
                }
            )
        }
    ) { // End of Canvas modifier, start of Canvas draw scope
        val canvasWidth = size.width
        val canvasHeight = size.height

        val yAxisLabelUsageTimeWidth = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = axisLabelTextStyle).size.width.toFloat()
        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()

        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 10f
        val legendTotalHeight = legendItemHeight * 2

        val leftMargin = yAxisLabelUsageTimeWidth + 20f
        val bottomMargin = xAxisLabelHeight + 20f + legendTotalHeight
        val topMargin = 20f
        val rightMargin = yAxisLabelScrollDistWidth + 20f

        val chartWidth = canvasWidth - leftMargin - rightMargin
        var chartHeight = canvasHeight - bottomMargin - topMargin

        if (chartWidth <= 0 || chartHeight <= 0) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 14.sp.toPx()
                    color = labelColor.toArgb()
                }
                canvas.nativeCanvas.drawText("Chart area too small", center.x, center.y, paint)
            }
            return@Canvas
        }

        drawLine(color = faintAxisColor, start = Offset(leftMargin, topMargin), end = Offset(leftMargin, topMargin + chartHeight), strokeWidth = 1f)
        drawLine(color = faintAxisColor, start = Offset(leftMargin, topMargin + chartHeight), end = Offset(leftMargin + chartWidth, topMargin + chartHeight), strokeWidth = 1f)
        drawLine(color = faintAxisColor, start = Offset(leftMargin + chartWidth, topMargin), end = Offset(leftMargin + chartWidth, topMargin + chartHeight), strokeWidth = 1f)

        val usageTimes = data.map { it.usageTimeMillis }
        val maxUsageTime = usageTimes.maxOrNull() ?: 1L
        val minUsageTime = 0L

        val scrollUnitsList = data.map { it.scrollUnits }
        val maxScrollUnits = scrollUnitsList.maxOrNull() ?: 1L
        val minScrollUnits = 0L

        val barCount = data.size
        if (barCount > 0) {
            val totalBarWidth = chartWidth * 0.8f
            val barWidth = (totalBarWidth / barCount).coerceAtLeast(2f)
            val barSpacing = (chartWidth - totalBarWidth) / (barCount + 1).coerceAtLeast(1)

            data.forEachIndexed { index, detail ->
                val barHeightNorm = ((detail.usageTimeMillis - minUsageTime).toFloat() / (maxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
                val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(0f)

                val barLeft = leftMargin + barSpacing + index * (barWidth + barSpacing)
                val barTop = topMargin + chartHeight - barActualHeight
                val barRight = barLeft + barWidth
                val barBottom = topMargin + chartHeight
                
                val currentBarColor = when {
                    periodType == ChartPeriodType.DAILY -> barColor // Daily, always normal color, no interaction
                    selectedBarIndex == null -> barColor // Weekly/Monthly, No selection, normal color
                    selectedBarIndex == index -> selectedBarColor // Weekly/Monthly, This bar is selected
                    else -> barColor.copy(alpha = 0.4f) // Weekly/Monthly, Another bar is selected, fade this one
                }

                drawRoundRect(
                    color = currentBarColor,
                    topLeft = Offset(barLeft, barTop),
                    size = androidx.compose.ui.geometry.Size(barRight - barLeft, barBottom - barTop),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(x = 8f, y = 8f)
                )
            }
        }
        
        // Calculate bar centers for accurate scroll dot placement
        val barCentersX = data.mapIndexed { index, _ ->
            val barCount = data.size
            if (barCount > 0) {
                val totalBarWidthFactor = 0.8f // Ensure this matches bar drawing logic
                val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
                val barWidth = (totalBarLayoutWidth / barCount).coerceAtLeast(2f)
                val barSpacing = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)
                leftMargin + barSpacing + index * (barWidth + barSpacing) + barWidth / 2
            } else {
                0f // Should not happen if data is not empty
            }
        }

        val scrollDistancePoints = data.mapIndexed { index, detail ->
            // Use pre-calculated barCenterX if available and valid
            val x = if (index < barCentersX.size) barCentersX[index] else leftMargin + (chartWidth / (data.size -1 ).coerceAtLeast(1)) * index
            val scrollRange = (maxScrollUnits - minScrollUnits).coerceAtLeast(1L)
            val yNorm = if (scrollRange == 0L) 0f else ((detail.scrollUnits - minScrollUnits).toFloat() / scrollRange.toFloat())
            val y = topMargin + chartHeight - (yNorm * chartHeight)
            Offset(x, y.coerceIn(topMargin, topMargin + chartHeight))
        }
        
        // Draw scroll data as circles instead of a line
        scrollDistancePoints.forEach { point ->
            drawCircle(
                color = scrollDistanceColor, // Use the existing scrollDistanceColor
                radius = 4.dp.toPx(), // Fixed radius for the circles
                center = point
            )
        }

        val xLabelCount = data.size
        if (barCount > 0) {
            val totalBarWidth = chartWidth * 0.8f
            val barWidth = (totalBarWidth / barCount).coerceAtLeast(2f)
            val barSpacing = (chartWidth - totalBarWidth) / (barCount + 1).coerceAtLeast(1)

            data.forEachIndexed { index, dailyData ->
                val dateStr = dailyData.date
                val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val calendar = Calendar.getInstance()

                val formattedDate = when (periodType) {
                    ChartPeriodType.DAILY -> getDayOfWeekLetter(dateStr)
                    ChartPeriodType.WEEKLY -> getDayOfWeekLetter(dateStr)
                    ChartPeriodType.MONTHLY -> {
                        try {
                            calendar.time = dateParser.parse(dateStr)!!
                            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                            if (dayOfMonth == 1 || dayOfMonth % 5 == 0 || dayOfMonth == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                                SimpleDateFormat("d", Locale.getDefault()).format(calendar.time) // Day number
                            } else {
                                ""
                            }
                        } catch (e: Exception) { "" }
                    }
                }
                if (formattedDate.isNotEmpty()){
                    val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(formattedDate) }, style = axisLabelTextStyle)
                    val labelX = leftMargin + barSpacing + index * (barWidth + barSpacing) + barWidth / 2 - textLayoutResult.size.width / 2
                    
                    drawContext.canvas.nativeCanvas.drawText(formattedDate, labelX, topMargin + chartHeight + 5f + textLayoutResult.size.height, android.graphics.Paint().apply { this.color = labelColor.toArgb(); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT })
                }
            }
        }

        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val value = minUsageTime + (i.toFloat() / yLabelCount) * (maxUsageTime - minUsageTime)
            val labelText = formatMillisToHoursOrMinutes(value.toLong())
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            // Draw grid line
            drawLine(color = faintAxisColor.copy(alpha = 0.5f), start = Offset(leftMargin, labelY), end = Offset(leftMargin + chartWidth, labelY), strokeWidth = 0.5f)
            // Draw tick mark
            drawLine(color = faintAxisColor, start = Offset(leftMargin - 5f, labelY), end = Offset(leftMargin, labelY), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin - textLayoutResult.size.width - 10f, labelY + textLayoutResult.size.height / 2 - 5f, android.graphics.Paint().apply { this.color = labelColor.toArgb(); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT })
        }

        for (i in 0..yLabelCount) {
            val value = minScrollUnits + (i.toFloat() / yLabelCount) * (maxScrollUnits - minScrollUnits)
            val labelText = formatScrollForAxis(value, maxScrollUnits)
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            // Draw grid line
            drawLine(color = faintAxisColor.copy(alpha = 0.5f), start = Offset(leftMargin, labelY), end = Offset(leftMargin + chartWidth, labelY), strokeWidth = 0.5f)
            // Draw tick mark
            drawLine(color = faintAxisColor, start = Offset(leftMargin + chartWidth, labelY), end = Offset(leftMargin + chartWidth + 5f, labelY), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin + chartWidth + 10f, labelY + textLayoutResult.size.height / 2 - 5f, android.graphics.Paint().apply { this.color = labelColor.toArgb(); textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT })
        }

        val legendStartY = topMargin + chartHeight + xAxisLabelHeight + 20f
        val legendItemSize = 12.sp.toPx()
        val legendTextPadding = 8.dp.toPx()

        drawRect(color = barColor, topLeft = Offset(leftMargin, legendStartY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        drawContext.canvas.nativeCanvas.drawText("Usage", leftMargin + legendItemSize + legendTextPadding, legendStartY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Usage") }, style = axisLabelTextStyle).size.height / 4, android.graphics.Paint().apply { this.color = labelColor.toArgb(); textSize = 11.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT })

        val secondLegendItemY = legendStartY + legendItemHeight
        drawRect(color = scrollDistanceColor, topLeft = Offset(leftMargin, secondLegendItemY), size = androidx.compose.ui.geometry.Size(legendItemSize, legendItemSize))
        drawContext.canvas.nativeCanvas.drawText("Scroll", leftMargin + legendItemSize + legendTextPadding, secondLegendItemY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Scroll") }, style = axisLabelTextStyle).size.height / 4, android.graphics.Paint().apply { this.color = labelColor.toArgb(); textSize = 11.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT })

        // --- Draw Tooltip if a bar is selected (Only for WEEKLY/MONTHLY) ---
        if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
            val selectedData = data[selectedBarIndex!!]

            val usageText = formatMillisToHoursMinutesSeconds(selectedData.usageTimeMillis)
            val scrollText = formatScrollForAxis(selectedData.scrollUnits.toFloat(), selectedData.scrollUnits) 

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
                android.graphics.Paint().apply { 
                    this.color = tooltipActualTextColor.toArgb() // Use resolved color
                    textSize = tooltipTextStyle.fontSize.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                    // Optionally set typeface if MaterialTheme.typography.bodySmall has a specific font
                }
            )
            // Draw scroll text
            drawContext.canvas.nativeCanvas.drawText(
                scrollText,
                tooltipX + tooltipPaddingHorizontal,
                tooltipY + tooltipPaddingVertical + usageTextLayout.size.height + tooltipTextSpacing + scrollTextLayout.size.height, // Y is baseline
                android.graphics.Paint().apply { 
                    this.color = tooltipActualTextColor.toArgb() // Use resolved color
                    textSize = tooltipTextStyle.fontSize.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
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

fun formatScrollForAxis(value: Float, maxValueHint: Long): String {
    if (abs(value) < 0.1f) return "0 px" // Handle near-zero values more robustly
    // If the value is small (e.g. < 10) and has a significant fractional part, show one decimal.
    // Otherwise, round to the nearest whole number.
    return if (value != 0f && abs(value) < 10f && (value % 1.0f != 0.0f && abs(value % 1.0f) > 0.05f) ) {
        String.format(Locale.US, "%.1f px", value)
    } else {
        String.format(Locale.US, "%.0f px", round(value))
    }
}

fun formatMillisToHoursOrMinutes(millis: Long): String {
    if (millis < 0) return "0m"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

    return when {
        hours > 0 -> String.format("%dh", hours)
        minutes > 0 -> String.format("%dm", minutes)
        else -> "0m"
    }
}

fun formatMillisToHoursMinutesSeconds(millis: Long): String {
    if (millis < 0) return "0s"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return when {
        hours > 0 -> String.format("%dh %dm", hours, minutes)
        minutes > 0 -> String.format("%dm %ds", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
} 