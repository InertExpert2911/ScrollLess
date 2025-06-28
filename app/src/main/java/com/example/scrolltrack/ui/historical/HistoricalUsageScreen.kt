package com.example.scrolltrack.ui.historical

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.historical.HistoricalViewModel
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.DateUtil
import android.text.format.DateUtils as AndroidDateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalUsageScreen(
    navController: NavController,
    viewModel: HistoricalViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDateString by viewModel.selectedDateForHistory.collectAsStateWithLifecycle()
    val totalUsageTimeForSelectedDate by viewModel.totalUsageTimeForSelectedDateHistoryFormatted.collectAsStateWithLifecycle()
    val appUsageListForSelectedDate by viewModel.dailyAppUsageForSelectedDateHistory.collectAsStateWithLifecycle()

    var showDatePickerDialog by remember { mutableStateOf(false) }
    val selectableDatesMillis by viewModel.selectableDatesForHistoricalUsage.collectAsStateWithLifecycle()

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = remember(selectedDateString) {
            DateUtil.parseLocalDateString(selectedDateString)?.time ?: System.currentTimeMillis()
        },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val dateToCompare = DateUtil.getStartOfDayUtcMillis(DateUtil.formatUtcTimestampToLocalDateString(utcTimeMillis))
                return utcTimeMillis <= System.currentTimeMillis() &&
                        (selectableDatesMillis.contains(dateToCompare) || AndroidDateUtils.isToday(utcTimeMillis))
            }
            override fun isSelectableYear(year: Int): Boolean = true
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historical Usage") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showDatePickerDialog = true }) {
                        Text(selectedDateString)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CalendarToday, "Select Date")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer, // M3 behavior
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        modifier = modifier.navigationBarsPadding().background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Usage: $totalUsageTimeForSelectedDate",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary, // Use primary color for key metric
                modifier = Modifier.padding(bottom = 20.dp)
            )

            if (appUsageListForSelectedDate.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No usage data found for $selectedDateString.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = appUsageListForSelectedDate, key = { it.id }) { usageItem ->
                        AppUsageRowItem(
                            usageItem = usageItem,
                            onClick = { navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(usageItem.packageName)) }
                        )
                    }
                }
            }
        }

        if (showDatePickerDialog) {
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    Button(onClick = {
                        datePickerState.selectedDateMillis?.let(viewModel::updateSelectedDateForHistory)
                        showDatePickerDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
                }
                // DatePickerDialog will automatically use colors from the MaterialTheme
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun AppUsageRowItem(
    usageItem: AppUsageUiItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Subtle elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = usageItem.icon ?: R.mipmap.ic_launcher_round),
                contentDescription = "${usageItem.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usageItem.appName,
                    style = MaterialTheme.typography.titleMedium, // Adjusted for hierarchy
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface // Main text color
                )
                Text(
                    text = usageItem.packageName,
                    style = MaterialTheme.typography.bodyMedium, // Adjusted for hierarchy
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Secondary text color
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = DateUtil.formatDuration(usageItem.usageTimeMillis),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, // Main text color for value
                textAlign = TextAlign.End
            )
        }
    }
}
