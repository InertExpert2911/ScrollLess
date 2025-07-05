package com.example.scrolltrack.ui.unlocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.text.NumberFormat
import kotlin.math.roundToInt
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat

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
                title = { Text("Unlocks Analysis") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.daysTracked == 0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not enough data to analyze unlocks.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                            Text(
                "Unlock Patterns",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

            // Stats Grid
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UnlockStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Daily Average",
                    value = formatDouble(uiState.dailyAverage),
                    icon = Icons.Default.Today
                )
                UnlockStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Weekly Average",
                    value = formatDouble(uiState.weeklyAverage),
                    icon = Icons.Default.CalendarToday
                )
                UnlockStatCard(
                    modifier = Modifier.weight(1f),
                                    label = "Total Unlocks",
                    value = uiState.totalUnlocks.toString(),
                    icon = Icons.Default.PhoneAndroid
                )
            }

            // Bar Chart for last 7 days
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 7 Days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    if (uiState.barChartData.isNotEmpty()) {
                        SimpleBarChart(
                            data = uiState.barChartData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                )
                            } else {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                           Text("No recent data.")
                        }
                    }
                }
            }

            // First/Last Unlock
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UnlockStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Today's First Unlock",
                    value = uiState.firstUnlockTime ?: "N/A",
                    icon = Icons.Default.HourglassTop
                )
                UnlockStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Today's Last Unlock",
                    value = uiState.lastUnlockTime ?: "N/A",
                    icon = Icons.Default.AvTimer
                )
            }
        }
    }
}

@Composable
fun SimpleBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val maxVal = data.maxOfOrNull { it.second } ?: 0
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = labelColor)
    val axisLabelPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }


    Canvas(modifier = modifier) {
        val barCount = data.size
        if (barCount == 0) return@Canvas

        val barWidth = size.width / (barCount * 2)
        val spaceBetweenBars = barWidth

        data.forEachIndexed { index, pair ->
            val barHeight = if (maxVal > 0) (pair.second.toFloat() / maxVal.toFloat()) * size.height * 0.9f else 0f
            val left = (index * (barWidth + spaceBetweenBars)) + spaceBetweenBars / 2
            val top = size.height - barHeight - (size.height * 0.1f) // Leave space at bottom for labels
            drawRect(
                color = primaryColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )

            // Draw labels
            val textLayoutResult = textMeasurer.measure(
                text = pair.first,
                style = labelStyle
            )
            axisLabelPaint.color = labelColor.toArgb()
            axisLabelPaint.textSize = labelStyle.fontSize.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                pair.first,
                left + barWidth / 2,
                size.height,
                axisLabelPaint
            )
        }
    }
}

@Composable
private fun UnlockStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
                Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatDouble(value: Double): String {
    return if (value.isNaN()) "0.0"
    else NumberFormat.getInstance().apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
    }.format(value)
} 