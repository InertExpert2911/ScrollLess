package com.example.scrolltrack.ui.detail

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
import com.example.scrolltrack.ui.main.AppDailyDetailData
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
import android.text.format.DateUtils
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.scrolltrack.util.ConversionUtil
import java.text.NumberFormat
import android.content.Context
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path

// Top-level constants for formatters to avoid recreation
private val DATE_FORMAT_YYYY_MM_DD = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val DATE_FORMAT_E = SimpleDateFormat("E", Locale.getDefault())
private val NUMBER_FORMAT_INSTANCE = NumberFormat.getNumberInstance(Locale.getDefault())

// Chart visual constants
private const val CHART_AXIS_LABEL_TEXT_SIZE_SP = 10
private const val CHART_LEGEND_TEXT_SIZE_SP = 11
private const val CHART_LEGEND_ITEM_SIZE_DP = 12
private const val CHART_LEGEND_TEXT_PADDING_DP = 8
private const val CHART_TOOLTIP_PADDING_HORIZONTAL_DP = 8
private const val CHART_TOOLTIP_PADDING_VERTICAL_DP = 4
private const val CHART_TOOLTIP_TEXT_SPACING_DP = 2
private const val CHART_TOOLTIP_POINTER_SIZE_DP = 8
private const val CHART_TOOLTIP_CORNER_RADIUS_DP = 8
private const val CHART_TOOLTIP_GAP_DP = 8

private const val CHART_Y_AXIS_LABEL_AREA_WIDTH_DP = 25 // Increased padding for Y-axis labels
private const val CHART_X_AXIS_LABEL_AREA_HEIGHT_DP = 25 // Increased padding for X-axis labels
private const val CHART_TOP_MARGIN_DP = 25

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

    // Collect palette colors
    val appBarColor by viewModel.appDetailAppBarColor.collectAsStateWithLifecycle()
    val appBarContentColor by viewModel.appDetailAppBarContentColor.collectAsStateWithLifecycle()
    val screenBackgroundColor by viewModel.appDetailBackgroundColor.collectAsStateWithLifecycle()

    // Collect new summary states
    val focusedUsageDisplay by viewModel.appDetailFocusedUsageDisplay.collectAsStateWithLifecycle()
    val focusedPeriodDisplay by viewModel.appDetailFocusedPeriodDisplay.collectAsStateWithLifecycle()
    val comparisonText by viewModel.appDetailComparisonText.collectAsStateWithLifecycle()
    val comparisonIconType by viewModel.appDetailComparisonIconType.collectAsStateWithLifecycle()
    val comparisonColorType by viewModel.appDetailComparisonColorType.collectAsStateWithLifecycle()
    val weekNumberDisplay by viewModel.appDetailWeekNumberDisplay.collectAsStateWithLifecycle()
    val periodDescriptionText by viewModel.appDetailPeriodDescriptionText.collectAsStateWithLifecycle()
    val focusedScrollDisplay by viewModel.appDetailFocusedScrollDisplay.collectAsStateWithLifecycle()

    val canNavigateForward by remember(currentPeriodType, currentReferenceDateStr) {
        derivedStateOf {
            val today = Calendar.getInstance()
            val refDateCal = Calendar.getInstance().apply {
                time = DateUtil.parseDateString(currentReferenceDateStr) ?: Date()
            }

            when (currentPeriodType) {
                ChartPeriodType.DAILY -> !DateUtils.isToday(refDateCal.timeInMillis)
                ChartPeriodType.WEEKLY -> {
                    // If refDateCal (end of week) is before today, we can navigate forward.
                    refDateCal.before(today) && !DateUtils.isSameDay(refDateCal, today)
                }
                ChartPeriodType.MONTHLY -> {
                    // If refDateCal (end of month) is before today, we can navigate forward.
                    refDateCal.before(today) && !DateUtils.isSameDay(refDateCal, today)
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
                    containerColor = appBarColor ?: MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = appBarContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = appBarContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = appBarContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier.background(screenBackgroundColor ?: MaterialTheme.colorScheme.background)
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
                    Text(
                        text = "Scroll: $focusedScrollDisplay",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary
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

    LaunchedEffect(periodType, data) {
        if (periodType == ChartPeriodType.DAILY) { 
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
    val axisLabelTextStyle = TextStyle(color = labelColor, fontSize = (CHART_AXIS_LABEL_TEXT_SIZE_SP - 1).sp) // Adjusted for smaller axis labels (was 11.sp directly)
    val selectedBarColor = MaterialTheme.colorScheme.secondary

    val tooltipBackgroundColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipActualTextColor = MaterialTheme.colorScheme.surface 
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

                        val yAxisLabelUsageTimeWidthPx = textMeasurer.measure(text = buildAnnotatedString { append("99h") }, style = axisLabelTextStyle).size.width.toFloat()
                        val yAxisLabelScrollDistWidthPx = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = axisLabelTextStyle).size.width.toFloat()
                        val xAxisLabelHeightPx = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()
                        val legendItemHeightPx = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 10f // 10f is an existing offset
                        val legendTotalHeightPx = legendItemHeightPx * 2

                        val leftMarginPx = yAxisLabelUsageTimeWidthPx + CHART_Y_AXIS_LABEL_AREA_WIDTH_DP.dp.toPx()
                        val bottomMarginPx = xAxisLabelHeightPx + CHART_X_AXIS_LABEL_AREA_HEIGHT_DP.dp.toPx() + legendTotalHeightPx
                        val topMarginPx = CHART_TOP_MARGIN_DP.dp.toPx()
                        val rightMarginPx = yAxisLabelScrollDistWidthPx + CHART_Y_AXIS_LABEL_AREA_WIDTH_DP.dp.toPx() // Use same area width for consistency

                        val chartWidthPx = canvasWidth - leftMarginPx - rightMarginPx
                        val chartHeightPx = canvasHeight - bottomMarginPx - topMarginPx

                        if (chartWidthPx <= 0 || chartHeightPx <= 0) return@detectTapGestures

                        val totalBarWidthFactor = when (periodType) { 
                            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 0.6f 
                            ChartPeriodType.MONTHLY -> 0.7f 
                        }
                        val totalBarLayoutWidthPx = chartWidthPx * totalBarWidthFactor
                        val currentBarWidthPx = (totalBarLayoutWidthPx / barCount).coerceAtLeast(2f)
                        val currentBarSpacingPx = (chartWidthPx - totalBarLayoutWidthPx) / (barCount + 1).coerceAtLeast(1)
                        
                        val usageTimesForHeight = data.map { it.usageTimeMillis } // Moved out of loop
                        val maxUsageTimeForHeight = usageTimesForHeight.maxOrNull() ?: 1L
                        val minUsageTimeForHeight = 0L

                        var tappedBarFound = false
                        for (index in data.indices) {
                            val detail = data[index]
                            val barHeightNorm = ((detail.usageTimeMillis - minUsageTimeForHeight).toFloat() / (maxUsageTimeForHeight - minUsageTimeForHeight).coerceAtLeast(1L).toFloat())
                            val barActualHeight = (barHeightNorm * chartHeightPx).coerceAtLeast(0f)

                            val barLeft = leftMarginPx + currentBarSpacingPx + index * (currentBarWidthPx + currentBarSpacingPx)
                            val barTopCanvas = topMarginPx + chartHeightPx - barActualHeight
                            val barRight = barLeft + currentBarWidthPx
                            val barBottomCanvas = topMarginPx + chartHeightPx

                            val barRect = Rect(barLeft, barTopCanvas, barRight, barBottomCanvas)

                            if (barRect.contains(tapOffset)) {
                                val tappedExistingSelection = selectedBarIndex == index
                                selectedBarIndex = if (tappedExistingSelection) null else index
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
        val yAxisLabelScrollDistWidth = textMeasurer.measure(text = buildAnnotatedString { append("99.9km") }, style = axisLabelTextStyle).size.width.toFloat()
        val xAxisLabelHeight = textMeasurer.measure(text = buildAnnotatedString { append("M") }, style = axisLabelTextStyle).size.height.toFloat()

        val legendItemHeight = textMeasurer.measure(text = buildAnnotatedString { append("Legend") }, style = axisLabelTextStyle).size.height.toFloat() + 10f // Existing offset
        val legendTotalHeight = legendItemHeight * 2

        val leftMargin = yAxisLabelUsageTimeWidth + CHART_Y_AXIS_LABEL_AREA_WIDTH_DP.dp.toPx()
        val bottomMargin = xAxisLabelHeight + CHART_X_AXIS_LABEL_AREA_HEIGHT_DP.dp.toPx() + legendTotalHeight
        val topMargin = CHART_TOP_MARGIN_DP.dp.toPx()
        val rightMargin = yAxisLabelScrollDistWidth + CHART_Y_AXIS_LABEL_AREA_WIDTH_DP.dp.toPx()

        val chartWidth = canvasWidth - leftMargin - rightMargin
        val chartHeight = canvasHeight - bottomMargin - topMargin

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
        
        val baseAxisPaint = remember(labelColor, CHART_AXIS_LABEL_TEXT_SIZE_SP) { // For X-axis labels & Y-axis tick labels
            android.graphics.Paint().apply {
                this.color = labelColor.toArgb()
                textSize = CHART_AXIS_LABEL_TEXT_SIZE_SP.sp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
        }
        val legendPaint = remember(labelColor, CHART_LEGEND_TEXT_SIZE_SP) { // For legend text
             android.graphics.Paint().apply {
                this.color = labelColor.toArgb()
                textSize = CHART_LEGEND_TEXT_SIZE_SP.sp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
        }

        drawLine(color = faintAxisColor.copy(alpha = 0.5f), start = Offset(leftMargin, topMargin + chartHeight), end = Offset(leftMargin + chartWidth, topMargin + chartHeight), strokeWidth = 1.5f)

        val usageTimes = data.map { it.usageTimeMillis }
        val maxUsageTime = usageTimes.maxOrNull() ?: 1L
        val minUsageTime = 0L

        val scrollUnitsList = data.map { it.scrollUnits }
        val maxScrollUnits = scrollUnitsList.maxOrNull() ?: 1L
        val minScrollUnits = 0L

        val barCount = data.size
        val barWidthPx: Float
        val barSpacingPx: Float

        if (barCount > 0) {
            val totalBarWidthFactor = when (periodType) { 
                ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> 0.6f 
                ChartPeriodType.MONTHLY -> 0.7f 
            }
            val totalBarLayoutWidth = chartWidth * totalBarWidthFactor
            barWidthPx = (totalBarLayoutWidth / barCount).coerceAtLeast(2f)
            barSpacingPx = (chartWidth - totalBarLayoutWidth) / (barCount + 1).coerceAtLeast(1)
        } else {
            barWidthPx = 0f
            barSpacingPx = 0f
        }

        if (barCount > 0) {
            data.forEachIndexed { index, detail ->
                if (detail.usageTimeMillis > 0) { 
                    val barHeightNorm = ((detail.usageTimeMillis - minUsageTime).toFloat() / (maxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
                    val barActualHeight = (barHeightNorm * chartHeight).coerceAtLeast(2.dp.toPx()) 

                    val barLeft = leftMargin + barSpacingPx + index * (barWidthPx + barSpacingPx)
                    val barTop = topMargin + chartHeight - barActualHeight
                    val barRight = barLeft + barWidthPx
                    val barBottom = topMargin + chartHeight
                    
                    val currentBarFillColor = when {
                        periodType == ChartPeriodType.DAILY -> barColor
                        selectedBarIndex == null -> barColor
                        selectedBarIndex == index -> selectedBarColor
                        else -> barColor.copy(alpha = 0.4f)
                    }

                    val dynamicCornerRadius = if (periodType == ChartPeriodType.MONTHLY) 8f else 16f

                    if (barActualHeight >= 2 * dynamicCornerRadius) { 
                        val path = Path().apply {
                            moveTo(barLeft, barBottom) 
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
                            lineTo(barRight, barBottom) 
                            close() 
                        }
                        drawPath(path, color = currentBarFillColor)
                    } else { 
                        drawRect(
                            color = currentBarFillColor,
                            topLeft = Offset(barLeft, barTop),
                            size = androidx.compose.ui.geometry.Size(barRight - barLeft, barActualHeight)
                        )
                    }
                }
            }
        }
        
        if (barCount > 0) { 
            data.forEachIndexed { index, detail ->
                if (detail.scrollUnits > 0) { 
                    val barLeft = leftMargin + barSpacingPx + index * (barWidthPx + barSpacingPx) // Use calculated barSpacingPx and barWidthPx
                    val barCenterX = barLeft + barWidthPx / 2
    
                    val scrollRange = (maxScrollUnits - minScrollUnits).coerceAtLeast(1L)
                    val yNorm = if (scrollRange == 0L) 0f else ((detail.scrollUnits - minScrollUnits).toFloat() / scrollRange.toFloat())
                    val y = topMargin + chartHeight - (yNorm * chartHeight)
                    val point = Offset(barCenterX, y.coerceIn(topMargin, topMargin + chartHeight))
    
                    val dotBaseRadius = 5.dp.toPx()
                    val isSelectedForTooltip = selectedBarIndex == index && (periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY)
                    val currentDotRadius = if (isSelectedForTooltip) dotBaseRadius * 1.5f else dotBaseRadius
                    val currentDotColor = if (isSelectedForTooltip) scrollDistanceColor.copy(alpha = 1f) else scrollDistanceColor.copy(alpha = 0.8f)
    
                    drawCircle(
                        color = currentDotColor,
                        radius = currentDotRadius, 
                        center = point
                    )
                }
            }
        }

        if (barCount > 0) {
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
                    val labelX = leftMargin + barSpacingPx + index * (barWidthPx + barSpacingPx) + barWidthPx / 2 - textLayoutResult.size.width / 2
                    
                    drawContext.canvas.nativeCanvas.drawText(formattedDate, labelX, topMargin + chartHeight + 5f + textLayoutResult.size.height, baseAxisPaint)
                }
            }
        }

        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val value = minUsageTime + (i.toFloat() / yLabelCount) * (maxUsageTime - minUsageTime)
            val labelText = formatMillisToHoursOrMinutes(value.toLong())
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            if (value.toLong() != minUsageTime) { 
                drawLine(
                    color = faintAxisColor.copy(alpha = 0.5f), 
                    start = Offset(leftMargin, labelY), 
                    end = Offset(leftMargin + chartWidth, labelY), 
                    strokeWidth = 1.5f 
                )
            }
            drawLine(color = faintAxisColor, start = Offset(leftMargin - 5f, labelY), end = Offset(leftMargin, labelY), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin - textLayoutResult.size.width - 10f, labelY + textLayoutResult.size.height / 2 - 5f, baseAxisPaint)
        }

        for (i in 0..yLabelCount) {
            val value = minScrollUnits + (i.toFloat() / yLabelCount) * (maxScrollUnits - minScrollUnits)
            val labelText = formatScrollForAxis(value, maxScrollUnits, context)
            val textLayoutResult = textMeasurer.measure(buildAnnotatedString { append(labelText) }, style = axisLabelTextStyle)
            val labelY = topMargin + chartHeight - (i.toFloat() / yLabelCount) * chartHeight
            if (value.compareTo(minScrollUnits) != 0) { 
                 drawLine(
                    color = faintAxisColor.copy(alpha = 0.5f), 
                    start = Offset(leftMargin, labelY), 
                    end = Offset(leftMargin + chartWidth, labelY), 
                    strokeWidth = 1.5f 
                )
            }
            drawLine(color = faintAxisColor, start = Offset(leftMargin + chartWidth, labelY), end = Offset(leftMargin + chartWidth + 5f, labelY), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(labelText, leftMargin + chartWidth + 10f, labelY + textLayoutResult.size.height / 2 - 5f, baseAxisPaint)
        }

        val legendStartY = topMargin + chartHeight + xAxisLabelHeight + CHART_X_AXIS_LABEL_AREA_HEIGHT_DP.dp.toPx() / 2 // Adjusted for clarity
        val legendItemSizePx = CHART_LEGEND_ITEM_SIZE_DP.dp.toPx()
        val legendTextPaddingPx = CHART_LEGEND_TEXT_PADDING_DP.dp.toPx()

        drawRect(color = barColor, topLeft = Offset(leftMargin, legendStartY), size = androidx.compose.ui.geometry.Size(legendItemSizePx, legendItemSizePx))
        drawContext.canvas.nativeCanvas.drawText("Usage", leftMargin + legendItemSizePx + legendTextPaddingPx, legendStartY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Usage") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        val secondLegendItemY = legendStartY + legendItemHeight
        drawRect(color = scrollDistanceColor, topLeft = Offset(leftMargin, secondLegendItemY), size = androidx.compose.ui.geometry.Size(legendItemSizePx, legendItemSizePx))
        drawContext.canvas.nativeCanvas.drawText("Scroll", leftMargin + legendItemSizePx + legendTextPaddingPx, secondLegendItemY + legendItemHeight / 2 + textMeasurer.measure(buildAnnotatedString { append("Scroll") }, style = axisLabelTextStyle).size.height / 4, legendPaint)

        if ((periodType == ChartPeriodType.WEEKLY || periodType == ChartPeriodType.MONTHLY) && selectedBarIndex != null && selectedBarIndex!! < data.size) {
            val selectedData = data[selectedBarIndex!!]

            val usageText = formatMillisToHoursMinutesSeconds(selectedData.usageTimeMillis)
            val scrollText = tooltipScrollText // Already calculated using derivedStateOf

            val usageTextLayout = textMeasurer.measure(buildAnnotatedString{ append(usageText) }, style = tooltipTextStyle)
            val scrollTextLayout = textMeasurer.measure(buildAnnotatedString{ append(scrollText) }, style = tooltipTextStyle)

            val tooltipPaddingHorizontal = CHART_TOOLTIP_PADDING_HORIZONTAL_DP.dp.toPx()
            val tooltipPaddingVertical = CHART_TOOLTIP_PADDING_VERTICAL_DP.dp.toPx()
            val tooltipTextSpacing = CHART_TOOLTIP_TEXT_SPACING_DP.dp.toPx()

            val tooltipContentWidth = maxOf(usageTextLayout.size.width, scrollTextLayout.size.width).toFloat()
            val tooltipContentHeight = usageTextLayout.size.height + scrollTextLayout.size.height + tooltipTextSpacing
            
            val tooltipTotalWidth = tooltipContentWidth + 2 * tooltipPaddingHorizontal
            val tooltipTotalHeight = tooltipContentHeight + 2 * tooltipPaddingVertical
            
            // Use the same barWidthPx and barSpacingPx from main chart drawing phase
            val selectedBarLeft = leftMargin + barSpacingPx + selectedBarIndex!! * (barWidthPx + barSpacingPx)
            val selectedBarCenterX = selectedBarLeft + barWidthPx / 2

            val selectedBarUsageNorm = ((selectedData.usageTimeMillis - minUsageTime).toFloat() / (maxUsageTime - minUsageTime).coerceAtLeast(1L).toFloat())
            val selectedBarActualHeight = (selectedBarUsageNorm * chartHeight).coerceAtLeast(0f)
            val selectedBarTopCanvas = topMargin + chartHeight - selectedBarActualHeight

            val tooltipGap = CHART_TOOLTIP_GAP_DP.dp.toPx()
            var tooltipX = selectedBarCenterX - tooltipTotalWidth / 2
            var tooltipY = selectedBarTopCanvas - tooltipTotalHeight - tooltipGap

            if (tooltipX < leftMargin) tooltipX = leftMargin
            if (tooltipX + tooltipTotalWidth > leftMargin + chartWidth) tooltipX = leftMargin + chartWidth - tooltipTotalWidth
            if (tooltipY < topMargin) tooltipY = topMargin

            drawRoundRect(
                color = tooltipBackgroundColor, 
                topLeft = Offset(tooltipX, tooltipY),
                size = androidx.compose.ui.geometry.Size(tooltipTotalWidth, tooltipTotalHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(CHART_TOOLTIP_CORNER_RADIUS_DP.dp.toPx())
            )

            val pointerSize = CHART_TOOLTIP_POINTER_SIZE_DP.dp.toPx()
            val pointerPath = Path().apply {
                moveTo(tooltipX + tooltipTotalWidth / 2 - pointerSize / 2, tooltipY + tooltipTotalHeight) 
                lineTo(tooltipX + tooltipTotalWidth / 2 + pointerSize / 2, tooltipY + tooltipTotalHeight) 
                lineTo(tooltipX + tooltipTotalWidth / 2, tooltipY + tooltipTotalHeight + pointerSize) 
                close()
            }
            drawPath(path = pointerPath, color = tooltipBackgroundColor)

            // Re-create Paint objects for tooltip text to ensure correct styling from tooltipTextStyle
            val tooltipTextPaint = remember(tooltipActualTextColor, tooltipTextStyle.fontSize) {
                android.graphics.Paint().apply { 
                    this.color = tooltipActualTextColor.toArgb()
                    textSize = tooltipTextStyle.fontSize.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
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
        val date = DATE_FORMAT_YYYY_MM_DD.parse(dateString)
        DATE_FORMAT_E.format(date!!).first().toString()
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
    val metersScrolled = inchesScrolled / 39.3701 

    val numberFormat = NUMBER_FORMAT_INSTANCE

    return when {
        metersScrolled.compareTo(10000.0) >= 0 -> { 
            numberFormat.maximumFractionDigits = 1
            numberFormat.format(metersScrolled / 1000.0) + " km"
        }
        metersScrolled.compareTo(1000.0) >= 0 -> { 
            numberFormat.maximumFractionDigits = 1
            numberFormat.format(metersScrolled / 1000.0) + " km"
        }
        metersScrolled.compareTo(1.0) >= 0 -> {
            numberFormat.maximumFractionDigits = 0
            numberFormat.format(metersScrolled) + " m"
        }
        metersScrolled.compareTo(0.0) > 0 -> { 
            numberFormat.maximumFractionDigits = 1 
            numberFormat.format(metersScrolled) + " m"
        }
        else -> "0 m" 
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