package com.example.scrolltrack.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    navController: NavController,
    viewModel: CalibrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accumulatedX by viewModel.accumulatedScrollX.collectAsStateWithLifecycle()
    val accumulatedY by viewModel.accumulatedScrollY.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    // This will hold the measured pixel size of our target Composables
    var verticalLinePixelHeight by remember { mutableStateOf(0f) }
    var horizontalLinePixelWidth by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scroll Calibration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveCalibration(verticalLinePixelHeight, horizontalLinePixelWidth)
                        navController.popBackStack()
                    }) {
                        Text("Done")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "To improve accuracy, scroll over the lines below using a real-world reference, like a ruler.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            // Vertical Calibration
            CalibrationCard(
                title = "Vertical Calibration",
                instruction = "Scroll Down 5cm",
                accumulatedScroll = accumulatedY,
                onReset = { viewModel.resetScroll("Y") }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp) // Fixed height for calibration target
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.addScrollDelta(0, abs(dragAmount.y.toInt()))
                            }
                        }
                        .onGloballyPositioned {
                            verticalLinePixelHeight = it.size.height.toFloat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.Red,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Horizontal Calibration
            CalibrationCard(
                title = "Horizontal Calibration",
                instruction = "Swipe Right 5cm",
                accumulatedScroll = accumulatedX,
                onReset = { viewModel.resetScroll("X") }
            ) {
                Box(
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.addScrollDelta(abs(dragAmount.x.toInt()), 0)
                            }
                        }
                        .onGloballyPositioned {
                            horizontalLinePixelWidth = it.size.width.toFloat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.Blue,
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Current Status: ${uiState.statusText}",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.isCalibrated) {
                Text("Vertical: ${uiState.verticalDpi} | Horizontal: ${uiState.horizontalDpi}")
            }
        }
    }
}

@Composable
fun CalibrationCard(
    title: String,
    instruction: String,
    accumulatedScroll: Int,
    onReset: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = instruction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            content()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Scrolled: $accumulatedScroll px",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onReset) {
                    Text("Reset")
                }
            }
        }
    }
}

@Preview
@Composable
private fun CalibrationScreenPreview() {
    ScrollTrackTheme {
        CalibrationScreen(navController = rememberNavController())
    }
} 