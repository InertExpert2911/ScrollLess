package com.example.scrolltrack.ui.unlocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.text.NumberFormat
import java.time.LocalDate

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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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

            // Heatmap for last 180 days
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Last 180 Days (${uiState.totalUnlocksLast180Days} unlocks)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    if (uiState.heatmapData.isNotEmpty()) {
                        CalendarHeatmap(
                            data = uiState.heatmapData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp), contentAlignment = Alignment.Center
                        ) {
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
fun CalendarHeatmap(
    data: Map<LocalDate, Int>,
    modifier: Modifier = Modifier
) {
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(180)
    val weekCount = 26 // Approx 180 days / 7 days/week

    val maxCount = data.values.maxOrNull() ?: 1
    val colorStops = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary
    )
    val emptyCellColor = MaterialTheme.colorScheme.surfaceContainer

    Canvas(modifier = modifier) {
        val cellSize = size.width / weekCount
        val cellPadding = cellSize * 0.1f

        for (i in 0 until 180) {
            val date = startDate.plusDays(i.toLong())
            val dayOfWeek = date.dayOfWeek.value % 7 // Sun = 0, Mon = 1...
            val weekOfYear = (i / 7)

            val count = data[date] ?: 0
            val color = if (count == 0) {
                emptyCellColor
            } else {
                val ratio = count.toFloat() / maxCount
                when {
                    ratio > 0.66 -> colorStops[2]
                    ratio > 0.33 -> colorStops[1]
                    else -> colorStops[0]
                }
            }

            drawRect(
                color = color,
                topLeft = Offset(
                    x = weekOfYear * cellSize + cellPadding,
                    y = dayOfWeek * cellSize + cellPadding
                ),
                size = androidx.compose.ui.geometry.Size(
                    cellSize - 2 * cellPadding,
                    cellSize - 2 * cellPadding
                )
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