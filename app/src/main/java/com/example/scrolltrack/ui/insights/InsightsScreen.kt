package com.example.scrolltrack.ui.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@Composable
fun InsightsScreen(
    navController: NavHostController,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val intentionalUnlocks by viewModel.intentionalUnlocks.collectAsState()
    val glanceUnlocks by viewModel.glanceUnlocks.collectAsState()
    val firstUnlockTime by viewModel.firstUnlockTime.collectAsState()
    val lastUnlockTime by viewModel.lastUnlockTime.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Unlock Insights",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Meaningful Unlocks",
                    value = intentionalUnlocks.toString(),
                    icon = Icons.Filled.LockOpen
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Quick Glances",
                    value = glanceUnlocks.toString(),
                    icon = Icons.Filled.WbSunny
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "First Unlock",
                    value = firstUnlockTime,
                    icon = Icons.Filled.HourglassTop
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Last Unlock",
                    value = lastUnlockTime,
                    icon = Icons.Filled.PhoneLocked
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    ElevatedCard(
        modifier = modifier
            .height(160.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .padding(bottom = 8.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                modifier = Modifier.padding(bottom = 4.dp),
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
