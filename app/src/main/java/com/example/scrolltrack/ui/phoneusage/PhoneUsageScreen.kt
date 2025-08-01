package com.example.scrolltrack.ui.phoneusage

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.example.scrolltrack.ui.components.HeatmapLegend
import com.example.scrolltrack.ui.components.InteractiveCalendarHeatmap
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.ui.phoneusage.PhoneUsagePeriod
import com.example.scrolltrack.ui.phoneusage.PhoneUsageViewModel
import com.example.scrolltrack.ui.components.SetLimitBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhoneUsageScreen(
    navController: NavController,
    viewModel: PhoneUsageViewModel,
    modifier: Modifier = Modifier,
    onSetLimit: (String, Int) -> Unit,
    onDeleteLimit: (String) -> Unit,
    onQuickLimitIconClicked: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedAppForLimit by remember { mutableStateOf<AppUsageUiItem?>(null) }
 
     Scaffold(
         modifier = modifier.navigationBarsPadding()
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
                    val options = PhoneUsagePeriod.entries
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
                    val usageLabel = when (uiState.period) {
                        PhoneUsagePeriod.Daily -> "Total Usage"
                        PhoneUsagePeriod.Weekly -> "Avg. Daily Usage"
                        PhoneUsagePeriod.Monthly -> "Avg. Daily Usage"
                    }
                    Text(
                        text = "$usageLabel: ${uiState.usageStat}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.appUsage.isNotEmpty()) {
                items(uiState.appUsage, key = { "${it.id}-${it.usageTimeMillis}" }) { usageItem ->
                    AppUsageRowItem(
                        usageItem = usageItem,
                        period = uiState.period,
                        onClick = { navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(usageItem.packageName)) },
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
fun AppUsageRowItem(
    usageItem: AppUsageUiItem,
    period: PhoneUsagePeriod,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSetLimitClick: (AppUsageUiItem) -> Unit
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
                    model = usageItem.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "${usageItem.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usageItem.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val usageText = when (period) {
                    PhoneUsagePeriod.Daily -> "Used for ${DateUtil.formatDuration(usageItem.usageTimeMillis)}"
                    else -> "Used for ${DateUtil.formatDuration(usageItem.usageTimeMillis)} on average"
                }
                Text(
                    text = usageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
           IconButton(onClick = { onSetLimitClick(usageItem) }) {
               Icon(
                   painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_hour_glass_duotone),
                   contentDescription = "Set Limit"
               )
           }
        }
    }
}
