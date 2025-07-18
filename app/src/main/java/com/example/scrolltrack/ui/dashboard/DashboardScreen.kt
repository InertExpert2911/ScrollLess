package com.example.scrolltrack.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.detail.ScrollDetailScreen
import com.example.scrolltrack.ui.notifications.NotificationsScreen
import com.example.scrolltrack.ui.phoneusage.PhoneUsageScreen
import com.example.scrolltrack.ui.unlocks.UnlocksScreen
import kotlinx.coroutines.launch

enum class DashboardScreen(
    val icon: Int
) {
    PhoneUsage(R.drawable.ic_mobile_duotone),
    Unlocks(R.drawable.ic_lock_duotone),
    ScrollDistance(R.drawable.ic_ruler_triangle_duotone),
    Notifications(R.drawable.ic_notificaiton_bell_duotone)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardTabs(
    navController: NavController,
    selectedTab: String?,
    modifier: Modifier = Modifier
) {
    val initialPageIndex = remember {
        DashboardScreen.valueOf(selectedTab ?: DashboardScreen.PhoneUsage.name).ordinal
    }
    val pagerState = rememberPagerState(initialPage = initialPageIndex) {
        DashboardScreen.entries.size
    }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text(DashboardScreen.entries[pagerState.currentPage].name.let {
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
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                DashboardScreen.entries.forEachIndexed { index, destination ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = destination.icon),
                                contentDescription = destination.name,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    DashboardScreen.PhoneUsage.ordinal -> PhoneUsageScreen(navController = navController, viewModel = hiltViewModel())
                    DashboardScreen.Unlocks.ordinal -> UnlocksScreen(navController = navController, viewModel = hiltViewModel())
                    DashboardScreen.ScrollDistance.ordinal -> ScrollDetailScreen(navController = navController, viewModel = hiltViewModel())
                    DashboardScreen.Notifications.ordinal -> NotificationsScreen(navController = navController, viewModel = hiltViewModel())
                }
            }
        }
    }
}
