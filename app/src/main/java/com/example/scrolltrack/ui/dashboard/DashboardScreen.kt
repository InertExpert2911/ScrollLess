package com.example.scrolltrack.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.scrolltrack.ui.phoneusage.PhoneUsageScreen
import com.example.scrolltrack.ui.unlocks.UnlocksScreen
import com.example.scrolltrack.ui.detail.ScrollDetailScreen
import com.example.scrolltrack.ui.notifications.NotificationsScreen

enum class DashboardScreen(
    val icon: ImageVector
) {
    PhoneUsage(Icons.Default.Phone),
    Unlocks(Icons.Default.Lock),
    ScrollDistance(Icons.Default.Home),
    Notifications(Icons.Default.Notifications)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTabs(
    navController: NavController,
    selectedTab: String?,
    modifier: Modifier = Modifier
) {
    val tabNavController = rememberNavController()
    var selectedDestination by rememberSaveable {
        mutableIntStateOf(
            DashboardScreen.valueOf(selectedTab ?: DashboardScreen.PhoneUsage.name).ordinal
        )
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text(DashboardScreen.entries[selectedDestination].name.let {
                    when(it) {
                        "PhoneUsage" -> "Phone Usage"
                        "ScrollDistance" -> "Scroll Distance"
                        else -> it
                    }
                }, style = MaterialTheme.typography.headlineMedium) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedDestination) {
                DashboardScreen.entries.forEachIndexed { index, destination ->
                    Tab(
                        selected = selectedDestination == index,
                        onClick = {
                            selectedDestination = index
                            tabNavController.navigate(destination.name) {
                                popUpTo(0)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(destination.icon, contentDescription = destination.name)
                        }
                    )
                }
            }
            NavHost(
                navController = tabNavController,
                startDestination = selectedTab ?: DashboardScreen.PhoneUsage.name
            ) {
                composable(DashboardScreen.PhoneUsage.name) {
                    PhoneUsageScreen(navController = navController, viewModel = hiltViewModel())
                }
                composable(DashboardScreen.Unlocks.name) {
                    UnlocksScreen(navController = navController, viewModel = hiltViewModel())
                }
                composable(DashboardScreen.ScrollDistance.name) {
                    ScrollDetailScreen(navController = navController, viewModel = hiltViewModel())
                }
                composable(DashboardScreen.Notifications.name) {
                    NotificationsScreen(navController = navController, viewModel = hiltViewModel())
                }
            }
        }
    }
}
