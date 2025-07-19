package com.example.scrolltrack.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.scrolltrack.R
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.ui.theme.ScrollTrackTheme
import com.example.scrolltrack.ui.theme.getThemeColors
import androidx.compose.material3.HorizontalDivider
import com.example.scrolltrack.ui.components.SettingCard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ColorLens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val calibrationStatus by viewModel.calibrationStatusText.collectAsStateWithLifecycle()
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
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionTitle(title = stringResource(R.string.settings_section_appearance))
            Spacer(modifier = Modifier.height(8.dp))

            SettingCard(
                title = stringResource(R.string.settings_dark_mode),
                subtitle = stringResource(if (isDarkMode) R.string.settings_dark_mode_subtitle_on else R.string.settings_dark_mode_subtitle_off),
                icon = if (isDarkMode) Icons.Filled.Brightness2 else Icons.Filled.WbSunny,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = viewModel::setIsDarkMode,
                    thumbContent = {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.Brightness2 else Icons.Filled.WbSunny,
                            contentDescription = if (isDarkMode) stringResource(R.string.settings_dark_mode_off) else stringResource(R.string.settings_dark_mode_on),
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                )
            }
            HorizontalDivider()
            SettingCard(
                title = stringResource(R.string.settings_theme_palette),
                subtitle = selectedTheme.name,
                icon = Icons.Filled.ColorLens,
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

            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionTitle(title = stringResource(R.string.settings_section_general))
            Spacer(modifier = Modifier.height(8.dp))

            SettingCard(
                title = stringResource(R.string.settings_scroll_calibration),
                subtitle = calibrationStatus,
                icon = Icons.Filled.Straighten,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(ScreenRoutes.CalibrationRoute.route) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.settings_navigate_to_calibration)
                )
            }
            HorizontalDivider()
            SettingCard(
                title = stringResource(R.string.settings_manage_app_visibility),
                subtitle = stringResource(R.string.settings_manage_app_visibility_subtitle),
                icon = Icons.Filled.Visibility,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(ScreenRoutes.AppVisibility.route) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.settings_navigate_to_app_visibility)
                )
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
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
        title = { Text(stringResource(R.string.settings_theme_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
