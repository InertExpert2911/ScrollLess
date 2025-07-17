package com.example.scrolltrack.ui.unlocks

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.components.HeatmapLegend
import com.example.scrolltrack.ui.components.InteractiveCalendarHeatmap
import com.example.scrolltrack.ui.model.AppOpenUiItem
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnlocksScreen(
    navController: NavController,
    viewModel: UnlocksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
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
                    val options = UnlockPeriod.entries
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
                    val unlockLabel = when (uiState.period) {
                        UnlockPeriod.Daily -> "Total Unlocks"
                        UnlockPeriod.Weekly -> "Avg. Unlocks per Day"
                        UnlockPeriod.Monthly -> "Avg. Unlocks per Day"
                    }
                    Text(
                        text = "$unlockLabel: ${uiState.unlockStat}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }


            if (uiState.appOpens.isNotEmpty()) {
                items(uiState.appOpens, key = { it.packageName }) { app ->
                    AppOpenRow(
                        app = app,
                        period = uiState.period,
                        onClick = {
                            navController.navigate(ScreenRoutes.AppDetailRoute.createRoute(app.packageName))
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
fun AppOpenRow(app: AppOpenUiItem, period: UnlockPeriod, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon ?: R.mipmap.ic_launcher_round),
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                val openText = when (period) {
                    UnlockPeriod.Daily -> if (app.openCount == 1) "Opened 1 time" else "Opened ${app.openCount} times"
                    UnlockPeriod.Weekly -> "Opened ${app.openCount} times on average"
                    UnlockPeriod.Monthly -> "Opened ${app.openCount} times on average"
                }
                Text(
                    text = openText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
