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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.main.ChartPeriodType
import com.example.scrolltrack.ui.main.ComparisonColorType
import com.example.scrolltrack.ui.main.ComparisonIconType
import com.example.scrolltrack.ui.model.AppDailyDetailData
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import com.example.scrolltrack.util.DateUtil
import java.text.SimpleDateFormat
import java.util.*

// Constant for swipe threshold
private const val SWIPE_THRESHOLD_DP = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    // State
    appName: String?,
    appIcon: Any?, // Can be Drawable or other models Coil supports
    chartData: List<AppDailyDetailData>,
    currentPeriodType: ChartPeriodType,
    focusedUsageDisplay: String,
    focusedPeriodDisplay: String,
    comparisonText: String?,
    comparisonIconType: ComparisonIconType,
    comparisonColorType: ComparisonColorType,
    weekNumberDisplay: String?,
    periodDescriptionText: String?,
    focusedScrollDisplay: String,
    canNavigateForward: Boolean,
    focusedDate: String,
    // Events
    onPeriodChange: (ChartPeriodType) -> Unit,
    onNavigateDate: (Int) -> Unit,
    onSetFocusedDate: (String) -> Unit
) {
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
                            text = appName ?: "App Details",
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
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PeriodSelector(
                currentPeriod = currentPeriodType,
                onPeriodSelected = onPeriodChange
            )

            DateNavigationAndSummary(
                periodDescriptionText = periodDescriptionText,
                focusedUsageDisplay = focusedUsageDisplay,
                focusedScrollDisplay = focusedScrollDisplay,
                focusedPeriodDisplay = focusedPeriodDisplay,
                currentPeriodType = currentPeriodType,
                weekNumberDisplay = weekNumberDisplay,
                comparisonText = comparisonText,
                comparisonColorType = comparisonColorType,
                comparisonIconType = comparisonIconType,
                canNavigateForward = canNavigateForward,
                onNavigateDate = onNavigateDate
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppUsageChart(
                data = chartData,
                periodType = currentPeriodType,
                onBarClick = { date -> onSetFocusedDate(date) },
                onSwipe = onNavigateDate,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                focusedDate = focusedDate
            )
        }
    }
}

@Composable
private fun PeriodSelector(
    currentPeriod: ChartPeriodType,
    onPeriodSelected: (ChartPeriodType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val buttonModifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)

        ChartPeriodType.entries.forEach { period ->
            val isSelected = currentPeriod == period
            FilledTonalButton(
                onClick = { onPeriodSelected(period) },
                modifier = buttonModifier,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(period.name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) })
            }
        }
    }
}

@Composable
private fun DateNavigationAndSummary(
    periodDescriptionText: String?,
    focusedUsageDisplay: String,
    focusedScrollDisplay: String,
    focusedPeriodDisplay: String,
    currentPeriodType: ChartPeriodType,
    weekNumberDisplay: String?,
    comparisonText: String?,
    comparisonColorType: ComparisonColorType,
    comparisonIconType: ComparisonIconType,
    canNavigateForward: Boolean,
    onNavigateDate: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onNavigateDate(-1) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Period")
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = focusedPeriodDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (currentPeriodType == ChartPeriodType.WEEKLY && weekNumberDisplay != null) {
                    Text(
                        text = " (W$weekNumberDisplay)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            comparisonText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                ComparisonChip(
                    text = it,
                    iconType = comparisonIconType,
                    colorType = comparisonColorType
                )
            }
        }

        IconButton(onClick = { onNavigateDate(1) }, enabled = canNavigateForward) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next Period")
        }
    }
}

@Composable
private fun ComparisonChip(text: String, iconType: ComparisonIconType, colorType: ComparisonColorType) {
    val contentColor = when (colorType) {
        ComparisonColorType.GREEN -> Color(0xFF388E3C)
        ComparisonColorType.RED -> MaterialTheme.colorScheme.error
        ComparisonColorType.GREY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (iconType) {
        ComparisonIconType.UP -> Icons.Filled.ArrowUpward
        ComparisonIconType.DOWN -> Icons.Filled.ArrowDownward
        ComparisonIconType.NEUTRAL -> Icons.Filled.HorizontalRule
        ComparisonIconType.NONE -> Icons.Filled.HorizontalRule
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = contentColor.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

@Composable
fun AppUsageChart(
    modifier: Modifier = Modifier,
    data: List<AppDailyDetailData>,
    periodType: ChartPeriodType,
    onBarClick: (String) -> Unit,
    onSwipe: (Int) -> Unit,
    focusedDate: String
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    // Moved these outside Canvas to be accessible by all drawing logic
    val labelTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val barColor = MaterialTheme.colorScheme.primary.toArgb()
    val focusedBarColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f).toArgb()

    val maxUsage = data.maxOfOrNull { it.usageTimeMillis } ?: 1L
    val labelStyle = TextStyle(
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Canvas(modifier = modifier
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val barWidthPx = size.width / data.size
                val index = (offset.x / barWidthPx).toInt()
                if (index in data.indices) {
                    onBarClick(data[index].date)
                }
            }
        }
        .pointerInput(Unit) {
            var dragAmount = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, drag ->
                    dragAmount += drag
                },
                onDragEnd = {
                    when {
                        dragAmount > swipeThresholdPx -> onSwipe(-1) // Swiped right
                        dragAmount < -swipeThresholdPx -> onSwipe(1) // Swiped left
                    }
                }
            )
        }
    ) {
        if (data.isEmpty()) {
            val emptyStateText = "No usage data available for this period."
            val measuredText = textMeasurer.measure(
                text = buildAnnotatedString { append(emptyStateText) },
                style = labelStyle.copy(textAlign = TextAlign.Center),
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = size.width.toInt())
            )
            drawText(
                textLayoutResult = measuredText,
                topLeft = Offset(
                    x = (size.width - measuredText.size.width) / 2f,
                    y = (size.height - measuredText.size.height) / 2f
                )
            )
            return@Canvas
        }

        val barCount = data.size
        val barWidth = size.width / (barCount * 1.5f)
        val spaceBetweenBars = barWidth * 0.5f
        val bottomPadding = 60.dp.toPx()
        val chartHeight = size.height - bottomPadding

        // Draw horizontal grid lines and labels
        val yAxisLabelCount = 5
        (0..yAxisLabelCount).forEach { i ->
            val y = chartHeight * (1 - i.toFloat() / yAxisLabelCount)
            drawLine(
                color = Color(gridLineColor),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )

            val labelMillis = maxUsage * (i.toFloat() / yAxisLabelCount)
            val labelText = DateUtil.formatDuration(labelMillis.toLong())
            val measuredText = textMeasurer.measure(
                text = buildAnnotatedString { append(labelText) },
                style = labelStyle
            )
            drawText(
                textLayoutResult = measuredText,
                topLeft = Offset(5.dp.toPx(), y - (measuredText.size.height / 2))
            )
        }

        // Draw bars and x-axis labels
        data.forEachIndexed { index, dailyData ->
            val barHeight = (dailyData.usageTimeMillis.toFloat() / maxUsage) * chartHeight
            val left = (index * (barWidth + spaceBetweenBars)) + spaceBetweenBars / 2
            val top = chartHeight - barHeight
            val right = left + barWidth
            val bottom = chartHeight

            drawRect(
                color = Color(if (dailyData.date == focusedDate) focusedBarColor else barColor),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
            )

            // Draw X-axis labels
            val labelText = when (periodType) {
                ChartPeriodType.DAILY -> SimpleDateFormat("ha", Locale.getDefault()).format(DateUtil.parseLocalDateString(dailyData.date) ?: Date())
                ChartPeriodType.WEEKLY -> SimpleDateFormat("E", Locale.getDefault()).format(DateUtil.parseLocalDateString(dailyData.date) ?: Date())
                ChartPeriodType.MONTHLY -> {
                    val cal = Calendar.getInstance()
                    cal.time = DateUtil.parseLocalDateString(dailyData.date) ?: Date()
                    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                    if (dayOfMonth % 5 == 1 || dayOfMonth == 1) dayOfMonth.toString() else ""
                }
            }

            if (labelText.isNotEmpty()) {
                val measuredText = textMeasurer.measure(
                    text = buildAnnotatedString { append(labelText) },
                    style = labelStyle
                )
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(
                        x = left + (barWidth / 2) - (measuredText.size.width / 2),
                        y = chartHeight + 10.dp.toPx()
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppDetailScreenPreview() {
    ScrollTrackTheme(themeVariant = "dark") {
        val navController = rememberNavController()
        val chartData = (0..6).map {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6 + it) }.time
            AppDailyDetailData(
                date = DateUtil.formatDateToYyyyMmDdString(date),
                usageTimeMillis = (1000 * 60 * (10 + Random().nextInt(120))).toLong(),
                scrollUnits = 0L // Add dummy value for scrollUnits
            )
        }
        AppDetailScreen(
            navController = navController,
            appName = "Social Media App",
            appIcon = R.mipmap.ic_launcher_round,
            chartData = chartData,
            currentPeriodType = ChartPeriodType.WEEKLY,
            focusedUsageDisplay = "2h 15m",
            focusedPeriodDisplay = "Today",
            comparisonText = "15% more than yesterday",
            comparisonIconType = ComparisonIconType.UP,
            comparisonColorType = ComparisonColorType.RED,
            weekNumberDisplay = "23",
            periodDescriptionText = "June 10 - June 16, 2024",
            focusedScrollDisplay = "120m",
            canNavigateForward = false,
            focusedDate = chartData.last().date,
            onPeriodChange = {},
            onNavigateDate = {},
            onSetFocusedDate = {}
        )
    }
}