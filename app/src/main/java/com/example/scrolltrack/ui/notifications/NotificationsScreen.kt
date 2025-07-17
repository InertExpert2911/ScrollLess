package com.example.scrolltrack.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.scrolltrack.ui.model.NotificationTreemapItem
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Color
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.example.scrolltrack.R
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.db.AppMetadata
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.scrolltrack.ui.components.InteractiveCalendarHeatmap
import com.example.scrolltrack.ui.components.HeatmapLegend

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.navigationBarsPadding()
    ) { innerPadding ->
        when (val state = uiState) {
            is NotificationsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is NotificationsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        InteractiveCalendarHeatmap(
                            heatmapData = state.heatmapData,
                            selectedDate = state.selectedDate,
                            onDateSelected = viewModel::onDateSelected,
                            monthsWithData = state.heatmapData.keys.map { java.time.YearMonth.from(it) }.distinct().sorted(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .padding(horizontal = 16.dp)
                        )
                        HeatmapLegend(modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            val options = NotificationPeriod.entries
                            options.forEachIndexed { index, period ->
                                ToggleButton(
                                    checked = state.selectedPeriod == period,
                                    onCheckedChange = { viewModel.selectPeriod(period) },
                                    modifier = Modifier.weight(1f),
                                    shapes =
                                    when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                ) {
                                    if (state.selectedPeriod == period) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.periodTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val totalLabel = when (state.selectedPeriod) {
                                NotificationPeriod.Daily -> "Total Notifications"
                                else -> "Avg. Daily Notifications"
                            }
                            Text(
                                text = "$totalLabel: ${state.totalCount}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (state.notificationCounts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No notifications for this period.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.notificationCounts, key = { it.first.packageName }) { (metadata, count) ->
                            var icon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                            LaunchedEffect(metadata.packageName) {
                                icon = viewModel.getIcon(metadata.packageName)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = icon ?: R.mipmap.ic_launcher_round
                                        ),
                                        contentDescription = "${metadata.appName} icon",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = metadata.appName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val countText = when (state.selectedPeriod) {
                                            NotificationPeriod.Daily -> if (count == 1) "1 notification" else "$count notifications"
                                            else -> "$count notifications on average"
                                        }
                                        Text(
                                            text = countText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}


/**
 * A simplified placeholder for the Treemap that arranges items in a FlowRow.
 * A proper squarified treemap algorithm is more complex and will be implemented next.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimpleTreemap(
    items: List<NotificationTreemapItem>,
    modifier: Modifier = Modifier
) {
    val totalCount = items.sumOf { it.count }.toFloat()
    if (totalCount == 0f) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val weight = item.count / totalCount
            Card(
                modifier = Modifier
                    .fillMaxWidth(fraction = (weight * 2).coerceIn(0.15f, 0.8f)) // Basic sizing
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = item.color.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.icon?.let {
                        Image(
                            painter = rememberAsyncImagePainter(model = it),
                            contentDescription = item.appName,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.appName}: ${item.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
