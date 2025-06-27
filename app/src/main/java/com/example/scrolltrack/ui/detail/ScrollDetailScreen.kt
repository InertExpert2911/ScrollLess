package com.example.scrolltrack.ui.detail

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R // For fallback icon
import com.example.scrolltrack.navigation.ScreenRoutes // For potential navigation from item
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.main.MainViewModel
import com.example.scrolltrack.ui.main.MainViewModelFactory
import com.example.scrolltrack.ui.main.FakeScrollDataRepository
import com.example.scrolltrack.ui.main.FakeSettingsRepository
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import java.util.Calendar
import java.util.Date
import androidx.compose.ui.tooling.preview.Preview
import android.text.format.DateUtils as AndroidDateUtils
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollDetailScreen(
    navController: NavController,
    viewModel: MainViewModel,
    selectedDateString: String,
    scrollData: List<AppScrollUiItem>,
    onDateSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val selectableDatesMillis by viewModel.selectableDatesForScrollDetail.collectAsStateWithLifecycle()

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
                title = { Text("Scroll Breakdown", style = MaterialTheme.typography.titleLarge) }, // Use Pixelify Sans
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    TextButton(onClick = { showDatePickerDialog = true }) {
                        Text(selectedDateString, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) // Themed text
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CalendarToday, "Select Date", tint = MaterialTheme.colorScheme.primary) // Themed icon
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Use primary surface
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary // Action items often use primary
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        modifier = Modifier.navigationBarsPadding().background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply scaffold padding
                .padding(horizontal = 16.dp, vertical = 12.dp) // Consistent screen padding
        ) {
            if (scrollData.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No scroll data recorded for $selectedDateString.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Add spacing between items
                ) {
                    items(items = scrollData, key = { it.id }) { appItem ->
                        AppScrollDetailItemEntry(
                            appItem = appItem,
                            formattedDistance = ConversionUtil.formatScrollDistance(appItem.totalScroll, context).first,
                            onClick = { navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(appItem.packageName)) }
                        )
                        // Consider removing HorizontalDivider if Card elevation provides enough separation
                        // HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }

        if (showDatePickerDialog) {
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    Button(onClick = { // Use standard Button
                        datePickerState.selectedDateMillis?.let(onDateSelected)
                        showDatePickerDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
                },
                colors = DatePickerDefaults.colors( // Apply theme colors to DatePicker
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

// Updated AppScrollDetailItemEntry with Card styling
@Composable
fun AppScrollDetailItemEntry(
    appItem: AppScrollUiItem,
    formattedDistance: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card( // Wrap item in a Card
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Use a subtle background
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
                painter = rememberAsyncImagePainter(model = appItem.icon ?: R.mipmap.ic_launcher_round),
                contentDescription = "${appItem.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appName,
                    style = MaterialTheme.typography.titleSmall, // Consistent typography
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Ensure text color
                )
                Text(
                    text = "${appItem.totalScroll} units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), // Subtler text
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp)) // Increased spacing
            Text(
                text = formattedDistance,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary // Highlight with primary color
                ),
                textAlign = TextAlign.End
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScrollDetailScreenPreview() {
    val context = LocalContext.current
    val fakeViewModel = MainViewModel(FakeScrollDataRepository(), FakeSettingsRepository(), context.applicationContext as android.app.Application)
    ScrollTrackTheme(darkTheme = true) { // Updated call
        ScrollDetailScreen(
            navController = rememberNavController(),
            viewModel = fakeViewModel,
            selectedDateString = "2023-10-27",
            scrollData = listOf(
                AppScrollUiItem("app1", "App One", null, 1234, "com.example.app1"),
                AppScrollUiItem("app2", "App Two", null, 5678, "com.example.app2")
            ),
            onDateSelected = {}
        )
    }
} 