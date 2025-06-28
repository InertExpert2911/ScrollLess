package com.example.scrolltrack.ui.unlocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.scrolltrack.util.DateUtil
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlocksScreen(
    navController: NavController,
    viewModel: UnlocksViewModel,
    modifier: Modifier = Modifier
) {
    val heatmapData by viewModel.heatmapData.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlocks & App Opens") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Heatmap Section
            item {
                HeatmapSection(
                    heatmapData = heatmapData,
                    onDateSelected = viewModel::setSelectedDate
                )
            }

            // App Opens Section
            item {
                when (val state = uiState) {
                    is UnlocksUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UnlocksUiState.Success -> {
                        Column {
                            PeriodSelector(
                                selectedPeriod = state.selectedPeriod,
                                onPeriodSelected = viewModel::selectPeriod
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HeatmapLegend()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = state.periodTitle,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.appOpens.isEmpty()) {
                                Text(
                                    text = "No app opens recorded for this period.",
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                // This Column is not lazy, which is fine for the items inside the LazyColumn item
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.appOpens.forEach { appOpen ->
                                        AppOpenRow(
                                            appItem = appOpen,
                                            onAppClick = { packageName ->
                                                navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(packageName))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthView(
    yearMonth: YearMonth,
    data: Map<String, Int>,
    maxCount: Float,
    onDateSelected: (String) -> Unit
) {
    val firstDayOfMonth = yearMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sun = 0, Mon = 1..
    val daysInMonth = yearMonth.lengthOfMonth()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }

    val colorStops = listOf(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.tertiary
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${yearMonth.year}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Day of week labels
        Row {
            val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")
            weekdays.forEach { day ->
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val allCellsInGrid = List(firstDayOfWeek) { null } + (1..daysInMonth).toList()
        val weeks = allCellsInGrid.chunked(7)

        weeks.forEach { weekDays ->
            Row {
                weekDays.forEach { dayOfMonth ->
                    if (dayOfMonth == null) {
                        Spacer(modifier = Modifier.size(32.dp))
                        return@forEach
                    }

                    val date = yearMonth.atDay(dayOfMonth)
                    val dateString = date.format(dateFormatter)
                    val isFutureDate = date.isAfter(today)
                    val count = if (!isFutureDate) data[dateString] ?: 0 else 0
                    val ratio = if(maxCount > 0) (count / maxCount).coerceIn(0f, 1f) else 0f

                    val cellColor = when {
                        ratio <= 0f -> colorStops[0]
                        ratio < 0.5f -> lerp(colorStops[0], colorStops[1], ratio * 2)
                        else -> lerp(colorStops[1], colorStops[2], (ratio - 0.5f) * 2)
                    }

                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .padding(2.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (!isFutureDate) cellColor else Color.Transparent,
                        tonalElevation = if (!isFutureDate) 1.dp else 0.dp
                    ) {
                        if (!isFutureDate) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .clickable { onDateSelected(dateString) })
                        }
                    }
                }
                // Add spacers to fill out the last row if it's not a full week
                if (weekDays.size < 7) {
                    repeat(7 - weekDays.size) {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        (0..4).forEach { i ->
            val ratio = i / 4f
            val colorStops = listOf(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.tertiary
            )
            val cellColor = when {
                ratio < 0.5f -> lerp(colorStops[0], colorStops[1], ratio * 2)
                else -> lerp(colorStops[1], colorStops[2], (ratio - 0.5f) * 2)
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(cellColor, RoundedCornerShape(3.dp))
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(
    selectedPeriod: UnlockPeriod,
    onPeriodSelected: (UnlockPeriod) -> Unit
) {
    val options = UnlockPeriod.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onPeriodSelected(period) },
                selected = period == selectedPeriod
            ) {
                Text(period.name)
            }
        }
    }
}

@Composable
fun HeatmapSection(
    heatmapData: Map<YearMonth, List<HeatmapCell>>,
    onDateSelected: (String) -> Unit
) {
    val sortedMonths = heatmapData.keys.sortedDescending()

    if (heatmapData.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Unlock data is being gathered...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        reverseLayout = true
    ) {
        items(sortedMonths, key = { it.toString() }) { month ->
            val monthData = heatmapData[month] ?: emptyList()
            MonthView(
                yearMonth = month,
                data = monthData.associate { it.date to it.count },
                maxCount = (heatmapData.values.flatten().maxOfOrNull { it.count } ?: 1).toFloat(),
                onDateSelected = onDateSelected
            )
        }
    }
}

@Composable
fun AppOpenRow(
    appItem: AppOpenUiItem,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onAppClick(appItem.packageName) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = appItem.icon,
                    fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                ),
                contentDescription = "${appItem.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appItem.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = appItem.openCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
} 