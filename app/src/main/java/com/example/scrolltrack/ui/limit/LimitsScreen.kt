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
    val groups by viewModel.limitGroups.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(ScreenRoutes.CreateEditLimitGroupRoute.createRoute()) }) {
                Icon(Icons.Filled.Add, contentDescription = "Create Limit Group")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (groups.isEmpty()) {
                item { Text("No limit groups created yet.", modifier = Modifier.padding(paddingValues)) }
            } else {
                items(groups) { group ->
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