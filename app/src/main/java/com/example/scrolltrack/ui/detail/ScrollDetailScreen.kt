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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import androidx.compose.material.icons.filled.Check
import com.example.scrolltrack.ui.components.HeatmapLegend
import com.example.scrolltrack.ui.components.InteractiveCalendarHeatmap
import com.example.scrolltrack.ui.components.SetLimitBottomSheet
import com.example.scrolltrack.ui.model.AppUsageUiItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScrollDetailScreen(
    navController: NavController,
    viewModel: ScrollDetailViewModel,
    onSetLimit: (String, Int) -> Unit,
    onDeleteLimit: (String) -> Unit,
    onQuickLimitIconClicked: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversionUtil = viewModel.conversionUtil
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedAppForLimit by remember { mutableStateOf<AppUsageUiItem?>(null) }
 
     Scaffold(
         modifier = Modifier.navigationBarsPadding()
     ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                InteractiveCalendarHeatmap(
                    heatmapData = uiState.heatmapData,
                    selectedDate = uiState.selectedDate,
                    onDateSelected = viewModel::onDateSelected,
                    monthsWithData = uiState.monthsWithData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                HeatmapLegend(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val options = ScrollDetailPeriod.entries
                    options.forEachIndexed { index, period ->
                        ToggleButton(
                            checked = uiState.period == period,
                            onCheckedChange = { viewModel.onPeriodChanged(period) },
                            modifier = Modifier.weight(1f),
                            shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            if (uiState.period == period) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(period.name)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.periodDisplay,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val scrollLabel = when (uiState.period) {
                        ScrollDetailPeriod.Daily -> "Total Scroll"
                        ScrollDetailPeriod.Weekly -> "Avg. Daily Scroll"
                        ScrollDetailPeriod.Monthly -> "Avg. Daily Scroll"
                    }
                    Text(
                        text = "$scrollLabel: ${uiState.scrollStat}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.appScrolls.isNotEmpty()) {
                items(uiState.appScrolls, key = { it.id }) { appItem ->
                    var formattedDistance by remember { mutableStateOf("...") }
                    var formattedUnits by remember { mutableStateOf("...") }
                    LaunchedEffect(appItem.totalScroll, conversionUtil) {
                        val (value, unit) = conversionUtil.formatScrollDistance(appItem.totalScrollX, appItem.totalScrollY)
                        formattedDistance = "$value$unit"
                        formattedUnits = conversionUtil.formatUnits(appItem.totalScroll)
                    }

                    AppScrollDetailItemEntry(
                        appItem = appItem,
                        period = uiState.period,
                        formattedDistance = formattedDistance,
                        formattedUnits = formattedUnits,
                        onClick = { navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(appItem.packageName)) },
                        onSetLimitClick = {
                            onQuickLimitIconClicked(it.packageName, it.appName)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppScrollDetailItemEntry(
    appItem: AppScrollUiItem,
    period: ScrollDetailPeriod,
    formattedDistance: String,
    formattedUnits: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSetLimitClick: (AppScrollUiItem) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val scrollText = when (period) {
                    ScrollDetailPeriod.Daily -> "Scrolled $formattedDistance ($formattedUnits Scroll Units)"
                    else -> "Scrolled $formattedDistance on average"
                }
                Text(
                    text = scrollText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
           IconButton(onClick = { onSetLimitClick(appItem) }) {
               Icon(
                   painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_hour_glass_duotone),
                   contentDescription = "Set Limit"
               )
           }
        }
    }
}

private class FakeSettingsRepository : SettingsRepository {
    override val selectedTheme: Flow<AppTheme> = flowOf(AppTheme.CalmLavender)
    override suspend fun setSelectedTheme(theme: AppTheme) {}
    override val isDarkMode: Flow<Boolean> = flowOf(true)
    override suspend fun setIsDarkMode(isDark: Boolean) {}
    override val screenDpi: Flow<Int> = flowOf(0)
    override suspend fun setScreenDpi(dpi: Int) {}
    override val calibrationSliderPosition: Flow<Float> = flowOf(0.5f)
    override suspend fun setCalibrationSliderPosition(position: Float) {}
    override val calibrationSliderHeight: Flow<Float> = flowOf(0f)
    override suspend fun setCalibrationSliderHeight(height: Float) {}
}
