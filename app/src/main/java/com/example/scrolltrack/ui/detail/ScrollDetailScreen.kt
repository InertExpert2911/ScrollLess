package com.example.scrolltrack.ui.detail

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
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import android.text.format.DateUtils as AndroidDateUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.ZoneOffset

@Composable
fun ScrollDetailScreen(
    navController: NavController,
    viewModel: ScrollDetailViewModel
) {
    val selectedDateString by viewModel.selectedDateForScrollDetail.collectAsStateWithLifecycle()
    val scrollData by viewModel.aggregatedScrollDataForSelectedDate.collectAsStateWithLifecycle()
    val selectableDatesMillis by viewModel.selectableDatesForScrollDetail.collectAsStateWithLifecycle()
    val conversionUtil = viewModel.conversionUtil

    ScrollDetailScreenContent(
        navController = navController,
        selectedDateString = selectedDateString,
        scrollData = scrollData,
        selectableDatesMillis = selectableDatesMillis,
        onDateSelected = { dateMillis ->
            viewModel.updateSelectedDateForScrollDetail(dateMillis)
        },
        conversionUtil = conversionUtil
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollDetailScreenContent(
    navController: NavController,
    selectedDateString: String,
    scrollData: List<AppScrollUiItem>,
    selectableDatesMillis: Set<Long>,
    onDateSelected: (Long) -> Unit,
    conversionUtil: ConversionUtil
) {
    val context = LocalContext.current
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = remember(selectedDateString) {
            DateUtil.parseLocalDate(selectedDateString)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
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
                title = { Text("Scroll Breakdown") },
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
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        modifier = Modifier
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(items = scrollData, key = { it.id }) { appItem ->
                        var formattedDistance by remember { mutableStateOf("...") }
                        LaunchedEffect(appItem.totalScroll, conversionUtil) {
                            val (value, unit) = conversionUtil.formatScrollDistance(appItem.totalScrollX, appItem.totalScrollY)
                            formattedDistance = "$value $unit"
                        }

                        AppScrollDetailItemEntry(
                            appItem = appItem,
                            formattedDistance = formattedDistance,
                            onClick = { navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(appItem.packageName)) }
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
    formattedDistance: String,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = appItem.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "${appItem.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appItem.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val (icon, description, tint) = when (appItem.dataType) {
                        "MEASURED" -> Triple(
                            Icons.Filled.Straighten,
                            "Measured Data",
                            MaterialTheme.colorScheme.secondary
                        )
                        "INFERRED" -> Triple(
                            Icons.Filled.Waves,
                            "Inferred Data",
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        else -> Triple(null, null, Color.Unspecified)
                    }
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = description,
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "${appItem.totalScroll} units",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formattedDistance,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End
            )
        }
    }
}

private class FakeSettingsRepository : SettingsRepository {
    override val selectedTheme: Flow<AppTheme> = flowOf(AppTheme.CalmLavender)
    override suspend fun setSelectedTheme(theme: AppTheme) {}
    override val isDarkMode: Flow<Boolean> = flowOf(true)
    override suspend fun setIsDarkMode(isDark: Boolean) {}
    override val calibrationFactorX: Flow<Float?> = flowOf(null)
    override val calibrationFactorY: Flow<Float?> = flowOf(null)
    override suspend fun setCalibrationFactors(factorX: Float?, factorY: Float?) {}
}

@Preview(showBackground = true, name = "Scroll Detail Screen")
@Composable
fun ScrollDetailScreenPreview() {
    val dummyData = listOf(
        AppScrollUiItem("1", "App One", null, 12000, 6000, 6000, "com.app1", "MEASURED"),
        AppScrollUiItem("2", "App Two", null, 8500, 0, 8500, "com.app2", "INFERRED"),
        AppScrollUiItem("3", "Another Very Long App Name That Will Surely Overflow", null, 400, 300, 100, "com.app3", "MEASURED")
    )
    ScrollTrackTheme {
        ScrollDetailScreenContent(
            navController = rememberNavController(),
            selectedDateString = "2023-10-27",
            scrollData = dummyData,
            selectableDatesMillis = emptySet(),
            onDateSelected = {},
            conversionUtil = ConversionUtil(FakeSettingsRepository(), LocalContext.current)
        )
    }
} 