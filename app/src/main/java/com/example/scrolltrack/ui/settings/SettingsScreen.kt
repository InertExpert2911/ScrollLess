package com.example.scrolltrack.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.ui.theme.getThemeColors
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSelectorDialog(
            currentTheme = selectedTheme,
            onThemeSelected = {
                viewModel.setSelectedTheme(it)
                showThemeDialog = false
            },
            onDismissRequest = { showThemeDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SettingRow(
                title = "Dark Mode",
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = viewModel::setIsDarkMode,
                    thumbContent = {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.Brightness2 else Icons.Filled.WbSunny,
                            contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingRow(
                title = "Theme Palette",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeDialog = true }
            ) {
                val currentThemeColors = getThemeColors(theme = selectedTheme, darkTheme = isDarkMode)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(currentThemeColors.primary)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingRow(
                title = "Scroll Calibration",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(ScreenRoutes.CalibrationRoute.route) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Navigate to Scroll Calibration"
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingRow(
                title = "Manage App Visibility",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(ScreenRoutes.AppVisibility.route) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Navigate to App Visibility Settings"
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        content()
    }
}

@Composable
fun ThemeSelectorDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismissRequest: () -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select a Theme") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Theme Palette", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(AppTheme.entries) { theme ->
                        val isSelected = theme == currentTheme
                        val themeColors = getThemeColors(theme = theme, darkTheme = isDarkMode)
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(themeColors.primary)
                                .border(BorderStroke(3.dp, borderColor), CircleShape)
                                .clickable { onThemeSelected(theme) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
} 