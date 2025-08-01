package com.example.scrolltrack.ui.limit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.navigation.ScreenRoutes

@Composable
fun LimitsScreen(
    viewModel: LimitsViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(ScreenRoutes.CreateEditLimitGroupRoute.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "Create Limit Group")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Text(
                text = "App Limits",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Individual Limits",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (uiState.individualLimits.isEmpty()) {
                    item { Text("No individual app limits set yet.") }
                } else {
                    items(uiState.individualLimits) { limit ->
                        IndividualLimitCard(limit = limit, onClick = { /*TODO*/ })
                    }
                }

                item {
                    Text(
                        text = "Custom App Groups",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                if (uiState.customLimitGroups.isEmpty()) {
                    item { Text("No limit groups created yet.") }
                } else {
                    items(uiState.customLimitGroups) { group ->
                        LimitGroupCard(
                            group = group,
                            onClick = { navController.navigate(ScreenRoutes.CreateEditLimitGroupRoute.createRoute(group.groupId)) },
                            onDelete = { viewModel.deleteGroup(group.groupId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IndividualLimitCard(
    limit: IndividualLimitUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberAsyncImagePainter(model = limit.icon),
                    contentDescription = limit.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column {
                    Text(
                        text = limit.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Limit: ${limit.timeLimitFormatted}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Edit Limit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LimitGroupCard(
    group: LimitGroupUiModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Limit: ${group.timeLimitFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    group.apps.take(5).forEach { app ->
                        Box(modifier = Modifier.padding(end = 4.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(model = app.icon),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Edit Limit Group",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}