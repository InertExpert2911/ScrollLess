package com.example.scrolltrack.ui.detail

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
            override fun isSelectableYear(year: Int): Boolean {
                return true // Allow all years for now
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scroll Breakdown") }, // Simplified title
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant, // Match HistoricalUsageScreen
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp) // Adjusted padding
        ) {
            if (scrollData.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scroll data recorded for $selectedDateString.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = scrollData, key = { it.id }) { appItem ->
                        AppScrollDetailItemEntry(
                            appItem = appItem,
                            // Pass context for ConversionUtil
                            formattedDistance = ConversionUtil.formatScrollDistance(appItem.totalScroll, context).first,
                            onClick = { navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(appItem.packageName)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        if (showDatePickerDialog) {
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            onDateSelected(it)
                        }
                        showDatePickerDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun AppScrollDetailItemEntry(
    appItem: AppScrollUiItem,
    formattedDistance: String, // Now passed in
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = appItem.icon ?: R.mipmap.ic_launcher_round // Fallback icon
            ),
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
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${appItem.totalScroll} units", // Display raw units
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formattedDistance, // Display formatted distance (e.g., "123 m")
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ScrollDetailScreenPreview() {
    val context = LocalContext.current
    val fakeViewModel = MainViewModel(FakeScrollDataRepository(), FakeSettingsRepository(), context.applicationContext as android.app.Application)
    ScrollTrackTheme(themeVariant = "oled_dark") {
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