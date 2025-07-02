package com.example.scrolltrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.scrolltrack.R
import kotlinx.coroutines.launch
import java.text.NumberFormat

private const val CALIBRATION_DISTANCE_CM = 10f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    navController: NavController,
    viewModel: CalibrationViewModel = hiltViewModel()
) {
    var isCalibrating by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var startScrollPixels by remember { mutableStateOf(0) }

    val currentFactor by viewModel.calibrationFactor.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scroll Calibration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Current Calibration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val factorText = if (currentFactor != null) {
                        "Custom (${NumberFormat.getNumberInstance().format(currentFactor)} pixels/meter)"
                    } else {
                        "Using device default (DPI)"
                    }
                    Text(factorText, style = MaterialTheme.typography.bodyLarge)

                    if (currentFactor != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.resetCalibration() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset to Default")
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.calibration_instructions, CALIBRATION_DISTANCE_CM),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual guide for scrolling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("START", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray))
                Spacer(modifier = Modifier.height(400.dp)) // Increased spacer for more scroll room
                Text(
                    text = "SCROLL DOWN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(400.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray))
                Spacer(modifier = Modifier.height(8.dp))
                Text("END", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            if (!isCalibrating) {
                Button(
                    onClick = {
                        startScrollPixels = scrollState.value
                        isCalibrating = true
                        coroutineScope.launch {
                            scrollState.animateScrollTo(0) // Scroll to top to start
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Begin Calibration")
                }
            } else {
                Button(
                    onClick = {
                        val endScrollPixels = scrollState.value
                        val pixelsScrolled = endScrollPixels.toFloat() - startScrollPixels.toFloat()
                        if (pixelsScrolled > 0) {
                            viewModel.saveCalibrationFactor(pixelsScrolled, CALIBRATION_DISTANCE_CM)
                        }
                        isCalibrating = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Save Calibration (${CALIBRATION_DISTANCE_CM}cm)")
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes content up if screen is large
        }
    }
} 