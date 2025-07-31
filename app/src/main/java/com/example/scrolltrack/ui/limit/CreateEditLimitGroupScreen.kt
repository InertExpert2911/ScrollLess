package com.example.scrolltrack.ui.limit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.ui.model.AppUsageUiItem
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditLimitGroupScreen(
    navController: NavController,
    viewModel: LimitsViewModel = hiltViewModel(),
    groupId: Long?
) {
    val uiState by viewModel.createEditUiState.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = Unit) {
        viewModel.loadGroupDetails(groupId)
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.navigateBackEvent.collectLatest {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (groupId == null) "Create Limit Group" else "Edit Limit Group") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.saveGroup() }) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Icon(Icons.Default.Done, contentDescription = "Save Limit Group")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.groupName,
                    onValueChange = { viewModel.onGroupNameChange(it) },
                    label = { Text("Group Name") },
                    isError = uiState.error != null,
                    supportingText = { if (uiState.error != null) Text(uiState.error!!) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Time Limit: ${uiState.timeLimitMinutes} minutes")
                Slider(
                    value = uiState.timeLimitMinutes.toFloat(),
                    onValueChange = { viewModel.onTimeLimitChange(it.toInt()) },
                    valueRange = 10f..240f,
                    steps = 22
                )
            }

            item {
                Text("Select Apps", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.allApps) { selectableApp ->
                AppCheckboxRow(
                    app = selectableApp.app,
                    isSelected = selectableApp.isSelected,
                    onCheckedChange = { isSelected ->
                        viewModel.onAppSelectionChange(selectableApp.app.packageName, isSelected)
                    }
                )
            }
        }
    }
}

@Composable
fun AppCheckboxRow(
    app: AppUsageUiItem,
    isSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = app.appName,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = app.appName, modifier = Modifier.weight(1f))
        Checkbox(checked = isSelected, onCheckedChange = onCheckedChange)
    }
}