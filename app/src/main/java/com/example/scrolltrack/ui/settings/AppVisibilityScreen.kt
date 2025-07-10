package com.example.scrolltrack.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVisibilityScreen(
    navController: NavController,
    viewModel: AppVisibilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Visibility") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVisibilityRow(
    app: AppVisibilityItem,
    onVisibilityChange: (VisibilityState) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(16.dp))

            SingleChoiceSegmentedButtonRow {
                val states = VisibilityState.entries
                states.forEachIndexed { index, state ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = states.size),
                        onClick = { onVisibilityChange(state) },
                        selected = app.visibilityState == state
                    ) {
                        Text(state.name.lowercase().replaceFirstChar { it.titlecase() })
                    }
                }
            }
        }
    }
} 