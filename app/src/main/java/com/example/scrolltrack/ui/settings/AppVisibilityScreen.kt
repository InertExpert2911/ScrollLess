package com.example.scrolltrack.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppVisibilityScreen(
    navController: NavController,
    viewModel: AppVisibilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Visibility", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if ((uiState as? AppVisibilityUiState.Success)?.showNonInteractiveApps == true) "Hide non-interactive apps" else "Show non-interactive apps") },
                                onClick = {
                                    viewModel.toggleShowNonInteractiveApps()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is AppVisibilityUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is AppVisibilityUiState.Error -> {
                    Text(text = state.message)
                }
                is AppVisibilityUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VisibilityFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.visibilityFilter == filter,
                                    onClick = { viewModel.setVisibilityFilter(filter) },
                                    label = { Text(filter.name.lowercase().replaceFirstChar { it.titlecase() }) }
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.apps, key = { it.packageName }) { app ->
                                AppVisibilityRow(
                                    app = app,
                                    onVisibilityChange = { newVisibility ->
                                        viewModel.setAppVisibility(app.packageName, newVisibility)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppVisibilityRow(
    app: AppVisibilityItem,
    onVisibilityChange: (VisibilityState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon ?: R.mipmap.ic_launcher_round),
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val states = if (app.visibilityState == VisibilityState.DEFAULT) {
                if (app.isDefaultVisible) {
                    listOf(VisibilityState.DEFAULT, VisibilityState.HIDDEN)
                } else {
                    listOf(VisibilityState.DEFAULT, VisibilityState.VISIBLE)
                }
            } else {
                // If an override is active, show Default and the active override
                listOf(VisibilityState.DEFAULT, app.visibilityState).sortedBy { it.ordinal }
            }

            states.forEachIndexed { index, state ->
                ToggleButton(
                    checked = app.visibilityState == state,
                    onCheckedChange = { onVisibilityChange(state) },
                    modifier = Modifier.weight(1f),
                    shapes = if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    }
                ) {
                    if (app.visibilityState == state) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    val buttonText = when (state) {
                        VisibilityState.DEFAULT -> if (app.isDefaultVisible) "Default (Visible)" else "Default (Hidden)"
                        VisibilityState.VISIBLE -> "Visible"
                        VisibilityState.HIDDEN -> "Hidden"
                    }
                    Text(
                        text = buttonText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
