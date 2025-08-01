package com.example.scrolltrack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scrolltrack.ui.model.AppUsageUiItem

import com.example.scrolltrack.ui.main.SetLimitSheetState
import com.example.scrolltrack.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLimitBottomSheet(
    onDismissRequest: () -> Unit,
    onSetLimit: (String, Int) -> Unit,
    onDeleteLimit: (String) -> Unit,
    sheetState: SetLimitSheetState
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        var sliderPosition by remember { mutableFloatStateOf(sheetState.existingLimitMinutes?.toFloat() ?: 30f) }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Control your time on ${sheetState.appName}",
                style = MaterialTheme.typography.headlineSmall
            )
            sheetState.averageUsageMillis?.let {
                Text(
                    text = "Your average daily use is ${DateUtil.formatDuration(it)}. Try setting a limit just below that.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sheetState.existingLimitMinutes != null) {
                    OutlinedButton(
                        onClick = {
                            onDeleteLimit(sheetState.packageName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete Limit")
                    }
                }
                Button(
                    onClick = {
                        onSetLimit(sheetState.packageName, sliderPosition.toInt())
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (sheetState.existingLimitMinutes != null) "Update Limit" else "Set Limit")
                }
            }
        }
    }
}