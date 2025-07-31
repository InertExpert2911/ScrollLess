package com.example.scrolltrack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scrolltrack.ui.model.AppUsageUiItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLimitBottomSheet(
    onDismissRequest: () -> Unit,
    onSetLimit: (String, Int) -> Unit,
    selectedApp: AppUsageUiItem?
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        var sliderPosition by remember { mutableFloatStateOf(30f) }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Set limit for ${selectedApp?.appName}",
                style = MaterialTheme.typography.titleLarge
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 10f..180f, // 10 minutes to 3 hours
                steps = 16 // Snap to 10-minute intervals
            )
            Text(
                text = "${sliderPosition.toInt()} minutes",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = {
                    selectedApp?.let { app ->
                        onSetLimit(app.packageName, sliderPosition.toInt())
                    }
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Limit")
            }
        }
    }
}