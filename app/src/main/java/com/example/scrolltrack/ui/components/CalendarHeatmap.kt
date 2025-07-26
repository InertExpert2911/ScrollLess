package com.example.scrolltrack.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InteractiveCalendarHeatmap(
    heatmapData: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    monthsWithData: List<YearMonth>,
    modifier: Modifier = Modifier
) {
    if (monthsWithData.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 56.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet. Check back soon!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val scrollState = rememberLazyListState()

    LaunchedEffect(monthsWithData.size) {
        if (monthsWithData.isNotEmpty()) {
            scrollState.scrollToItem(monthsWithData.size - 1)
        }
    }

    LazyRow(
        state = scrollState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(monthsWithData, key = { it.toString() }) { month ->
            MonthView(
                month = month,
                heatmapData = heatmapData,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                modifier = Modifier.width(300.dp)
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
    val textColorOnPrimary = MaterialTheme.colorScheme.onPrimary
    val textColorOnSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily
            ),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val daysOfWeek = (0..6).map {
                        DayOfWeek.SUNDAY.plus(it.toLong())
                            .getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    }
                    daysOfWeek.forEach {
                        Text(
                            text = it,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                        val textColor = if (color == emptyColor) {
                            textColorOnSurface
                        } else {
                            textColorOnPrimary
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            day.toString(),
                            (dayOfWeek * cellSize) + (cellSize / 2),
                            (weekOfMonth * cellSize) + (cellSize / 2) + 5.dp.toPx(),
                            Paint().apply {
                                this.color = textColor.toArgb()
                                this.textSize = 12.sp.toPx()
                                this.textAlign = Paint.Align.CENTER
                            }
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
