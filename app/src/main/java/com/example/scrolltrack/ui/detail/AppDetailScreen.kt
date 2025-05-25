package com.example.scrolltrack.ui.detail

import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.main.ChartPeriodType
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    navController: NavController,
    viewModel: MainViewModel,
    packageName: String,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(packageName) {
        viewModel.loadAppDetailsInfo(packageName) // This will also trigger initial chart load
    }

    val appName by viewModel.appDetailAppName.collectAsStateWithLifecycle()
    val appIcon by viewModel.appDetailAppIcon.collectAsStateWithLifecycle()
    val chartData by viewModel.appDetailChartData.collectAsStateWithLifecycle()
    val currentPeriodType by viewModel.currentChartPeriodType.collectAsStateWithLifecycle()
    val currentReferenceDate by viewModel.currentChartReferenceDate.collectAsStateWithLifecycle()

    val displayedDateRange = calculateDisplayedDateRange(currentPeriodType, currentReferenceDate, chartData.map { it.date })

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
            // Period Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = currentPeriodType == ChartPeriodType.WEEKLY,
                    onClick = { viewModel.changeChartPeriod(packageName, ChartPeriodType.WEEKLY) },
                    label = { Text("Weekly") },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                FilterChip(
                    selected = currentPeriodType == ChartPeriodType.MONTHLY,
                    onClick = { viewModel.changeChartPeriod(packageName, ChartPeriodType.MONTHLY) },
                    label = { Text("Monthly") },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Date Navigation
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
                Text(
                    text = displayedDateRange,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { viewModel.navigateChartDate(packageName, 1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next Period")
                }
            }

            // Placeholder for the graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (chartData.isEmpty()) {
                    Text("Loading chart data...")
                } else {
                    Text("Graph Placeholder: ${chartData.size} data points for $packageName. First date: ${chartData.firstOrNull()?.date}, Last date: ${chartData.lastOrNull()?.date}")
                }
            }
        }
    }
}

// Helper function to create a displayable date range string
private fun calculateDisplayedDateRange(periodType: ChartPeriodType, referenceDateStr: String, actualDatesInChart: List<String>): String {
    if (actualDatesInChart.isEmpty()) return referenceDateStr // Fallback if no data yet

    val firstDateInChart = DateUtil.parseDateString(actualDatesInChart.first())
    val lastDateInChart = DateUtil.parseDateString(actualDatesInChart.last())

    val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    return when (periodType) {
        ChartPeriodType.WEEKLY -> {
            if (firstDateInChart != null && lastDateInChart != null) {
                val calFirst = Calendar.getInstance().apply { time = firstDateInChart }
                val calLast = Calendar.getInstance().apply { time = lastDateInChart }
                if (calFirst.get(Calendar.YEAR) == calLast.get(Calendar.YEAR)) {
                    if (calFirst.get(Calendar.MONTH) == calLast.get(Calendar.MONTH)) {
                        "${monthDayFormat.format(firstDateInChart)} - ${SimpleDateFormat("d, yyyy", Locale.getDefault()).format(lastDateInChart)}" // e.g. Mar 1 - 7, 2023
                    } else {
                        "${monthDayFormat.format(firstDateInChart)} - ${monthDayFormat.format(lastDateInChart)}, ${calLast.get(Calendar.YEAR)}" // e.g. Mar 28 - Apr 3, 2023
                    }
                } else {
                    "${fullDateFormat.format(firstDateInChart)} - ${fullDateFormat.format(lastDateInChart)}" // e.g. Dec 28, 2023 - Jan 3, 2024
                }
            } else {
                referenceDateStr
            }
        }
        ChartPeriodType.MONTHLY -> {
            DateUtil.parseDateString(referenceDateStr)?.let {
                monthYearFormat.format(it) // e.g. March 2023
            } ?: referenceDateStr
        }
    }
}

// TopAppBar can be extracted if it becomes complex, for now, keep it inline within Scaffold
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// fun AppDetailTopAppBar(...) { ... } 