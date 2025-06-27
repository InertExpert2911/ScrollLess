package com.example.scrolltrack.ui.historical

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.model.AppUsageUiItem // Ensure this is imported
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.util.DateUtil
import android.text.format.DateUtils as AndroidDateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalUsageScreen(
    navController: NavController,
    viewModel: MainViewModel,
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
                title = { Text("Historical Usage", style = MaterialTheme.typography.titleLarge) }, // Use Pixelify Sans
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    TextButton(onClick = { showDatePickerDialog = true }) {
                        Text(selectedDateString, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CalendarToday, "Select Date", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Use primary surface
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
                .padding(innerPadding) // Apply scaffold padding
                .padding(horizontal = 16.dp, vertical = 12.dp), // Consistent screen padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text( // More prominent display for total usage
                text = "Total Usage: $totalUsageTimeForSelectedDate",
                style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary), // Pixelify Sans, primary color
                modifier = Modifier.padding(bottom = 20.dp) // Increased spacing
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
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between items
                ) {
                    items(items = appUsageListForSelectedDate, key = { it.id }) { usageItem ->
                        AppUsageRowItem( // AppUsageRowItem will be styled with Card
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
                    Button(onClick = { // Standard Button
                        datePickerState.selectedDateMillis?.let(viewModel::updateSelectedDateForHistory)
                        showDatePickerDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
                },
                colors = DatePickerDefaults.colors( // Apply theme colors
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    headlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledSelectedDayContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                )
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors( // Apply same colors to DatePicker itself
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        headlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dayContentColor = MaterialTheme.colorScheme.onSurface,
                        disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledSelectedDayContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                        todayContentColor = MaterialTheme.colorScheme.primary,
                        todayDateBorderColor = MaterialTheme.colorScheme.primary,
                        yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                        selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                        currentYearContentColor = MaterialTheme.colorScheme.primary,
                    )
                )
            }
        }
    }
}

// Updated AppUsageRowItem with Card styling
@Composable
fun AppUsageRowItem(
    usageItem: AppUsageUiItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card( // Wrap item in a Card
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Subtle background
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp), // Standard padding
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
                    style = MaterialTheme.typography.titleSmall, // Consistent typography
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Ensure text color
                )
                Text(
                    text = usageItem.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), // Subtler text
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp)) // Increased spacing
            Text(
                text = DateUtil.formatDuration(usageItem.usageTimeMillis),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary // Highlight with primary color
                )
            )
        }
    }
}
