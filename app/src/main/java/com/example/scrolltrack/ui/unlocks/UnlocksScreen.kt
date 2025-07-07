package com.example.scrolltrack.ui.unlocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.model.AppOpenUiItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlocksScreen(
    navController: NavController,
    viewModel: UnlocksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlocks & App Opens") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                InteractiveCalendarHeatmap(
                    heatmapData = uiState.heatmapData,
                    selectedDate = uiState.selectedDate,
                    onDateSelected = viewModel::onDateSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                HeatmapLegend(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    UnlockPeriod.entries.forEach { period ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = period.ordinal, count = UnlockPeriod.entries.size),
                            onClick = { viewModel.onPeriodChanged(period) },
                            selected = uiState.period == period,
                        ) {
                            Text(period.name)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "For ${uiState.periodDisplay}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                val unlockLabel = when (uiState.period) {
                    UnlockPeriod.Daily -> "Total Unlocks"
                    UnlockPeriod.Weekly -> "Weekly Average"
                    UnlockPeriod.Monthly -> "Monthly Average"
                }
                Text(
                    text = "$unlockLabel: ${uiState.unlockStat}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            if (uiState.appOpens.isNotEmpty()) {
                items(uiState.appOpens, key = { it.packageName }) { app ->
                    AppOpenRow(
                        app = app,
                        onClick = {
                            navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(app.packageName))
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No app opens recorded for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveCalendarHeatmap(
    heatmapData: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    val today = LocalDate.now()

    val firstDateWithData = heatmapData.keys.minOrNull() ?: today
    val startMonth = YearMonth.from(firstDateWithData)
    val endMonth = YearMonth.from(today)
    val months = remember(startMonth, endMonth) {
        val list = mutableListOf<YearMonth>()
        var current = startMonth
        while (!current.isAfter(endMonth)) {
            list.add(current)
            current = current.plusMonths(1)
        }
        list
    }

    LaunchedEffect(months.size) {
        if (months.isNotEmpty()) {
            scrollState.scrollToItem(months.size - 1)
        }
    }

    LazyRow(
        state = scrollState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(months, key = { it.toString() }) { month ->
            MonthView(
                month = month,
                heatmapData = heatmapData,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                modifier = Modifier.width(250.dp) // Reduced width
            )
        }
    }
}

@Composable
fun MonthView(
    month: YearMonth,
    heatmapData: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxCount = (heatmapData.values.maxOrNull() ?: 1).toFloat()
    val heatMapStartColor = MaterialTheme.colorScheme.secondaryContainer
    val heatMapEndColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainer
    val selectedBorderColor = MaterialTheme.colorScheme.secondary

    Column(modifier) {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMM yyyy")),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            val daysOfWeek = (0..6).map { DayOfWeek.SUNDAY.plus(it.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
            daysOfWeek.forEach {
                Text(text = it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val cellSize = size.width / 7f
                        val col = (offset.x / cellSize)
                            .toInt()
                            .coerceIn(0, 6)
                        val row = (offset.y / cellSize)
                            .toInt()
                            .coerceIn(0, 5)

                        val firstDayOfMonth = month.atDay(1)
                        val firstDayOfWeekOfMonth = firstDayOfMonth.dayOfWeek.value % 7
                        val dayIndex = row * 7 + col - firstDayOfWeekOfMonth

                        if (dayIndex >= 0 && dayIndex < month.lengthOfMonth()) {
                            onDateSelected(month.atDay(dayIndex + 1))
                        }
                    }
                }
        ) {
            val cellSize = size.width / 7f
            val firstDayOfMonth = month.atDay(1)
            val firstDayOfWeekOfMonth = firstDayOfMonth.dayOfWeek.value % 7

            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val dayOfWeek = date.dayOfWeek.value % 7
                val weekOfMonth = (day + firstDayOfWeekOfMonth - 1) / 7

                val count = heatmapData[date] ?: 0
                val color = when {
                    count <= 0 -> emptyColor
                    else -> {
                        val ratio = (count / maxCount).coerceIn(0f, 1f)
                        lerp(heatMapStartColor, heatMapEndColor, ratio)
                    }
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(dayOfWeek * cellSize, weekOfMonth * cellSize),
                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()) // More rounded
                )
                if (date == selectedDate) {
                    drawRoundRect(
                        color = selectedBorderColor,
                        topLeft = Offset(dayOfWeek * cellSize, weekOfMonth * cellSize),
                        size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun AppOpenRow(app: AppOpenUiItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon ?: R.mipmap.ic_launcher_round),
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(app.openCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val startColor = MaterialTheme.colorScheme.secondaryContainer
    val endColor = MaterialTheme.colorScheme.primary
    val colors = remember(startColor, endColor) {
        (0..4).map { lerp(startColor, endColor, it / 4f) }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color, RoundedCornerShape(4.dp)) // More rounded
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
} 