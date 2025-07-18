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
import com.example.scrolltrack.ui.components.DashboardCard

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
                text = "Insights for you",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Text(
                text = "Unlock Insights",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Meaningful Unlocks",
                    value = intentionalUnlocks.toString(),
                    unit = "times",
                    comparison = null
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Quick Glances",
                    value = glanceUnlocks.toString(),
                    unit = "times",
                    comparison = null
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "First Unlock",
                    value = firstUnlockTime,
                    unit = "",
                    comparison = null
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Last Unlock",
                    value = lastUnlockTime,
                    unit = "",
                    comparison = null
                )
            }
        }
    }
}
