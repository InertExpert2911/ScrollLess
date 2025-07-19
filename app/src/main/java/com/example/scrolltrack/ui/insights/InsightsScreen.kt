package com.example.scrolltrack.ui.insights

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.components.DashboardCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    navController: NavController,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val intentionalUnlocks by viewModel.intentionalUnlocks.collectAsStateWithLifecycle()
    val glanceUnlocks by viewModel.glanceUnlocks.collectAsStateWithLifecycle()
    val firstUnlockTime by viewModel.firstUnlockTime.collectAsStateWithLifecycle()
    val lastUnlockTime by viewModel.lastUnlockTime.collectAsStateWithLifecycle()


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = MaterialTheme.typography.headlineLarge) }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Meaningful Unlocks",
                        value = intentionalUnlocks.toString(),
                        unit = "times",
                        comparison = null
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Quick Glances",
                        value = glanceUnlocks.toString(),
                        unit = "times",
                        comparison = null
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "First Unlock",
                        value = firstUnlockTime,
                        unit = "",
                        comparison = null
                    )
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        title = "Last Unlock",
                        value = lastUnlockTime,
                        unit = "",
                        comparison = null
                    )
                }
            }
            items(insights, key = { it.id }) { insight ->
                when (insight) {
                    is InsightCardUiModel.Loading -> LoadingInsightCard()
                    is InsightCardUiModel.FirstApp -> FirstAppInsightCard(insight)
                    is InsightCardUiModel.LastApp -> LastAppInsightCard(insight)
                    is InsightCardUiModel.CompulsiveCheck -> CompulsiveCheckInsightCard(insight)
                    is InsightCardUiModel.NotificationLeader -> NotificationLeaderInsightCard(insight)
                    is InsightCardUiModel.TimePattern -> TimePatternInsightCard(insight)
                }
            }
        }
    }
}

@Composable
fun FirstAppInsightCard(insight: InsightCardUiModel.FirstApp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = insight.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Rise and Shine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append("You started your day with ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(insight.appName ?: "an app")
                        }
                        append(" at ${insight.time}.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun LastAppInsightCard(insight: InsightCardUiModel.LastApp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = insight.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "App Icon",
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Last Call",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append("Yesterday, you ended your day with ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(insight.appName ?: "an app")
                        }
                        append(" at ${insight.time}.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun CompulsiveCheckInsightCard(insight: InsightCardUiModel.CompulsiveCheck) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = insight.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "App Icon",
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Quick Draw",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append("You had ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${insight.count} quick check")
                        }
                        if (insight.count > 1) append("s")
                        append(" on ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(insight.appName ?: "an app")
                        }
                        append(" today.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun NotificationLeaderInsightCard(insight: InsightCardUiModel.NotificationLeader) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = insight.icon ?: R.mipmap.ic_launcher_round
                ),
                contentDescription = "App Icon",
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Top Distraction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${insight.percentage}%")
                        }
                        append(" of your unlocks today were triggered by notifications from ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(insight.appName ?: "an app")
                        }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun TimePatternInsightCard(insight: InsightCardUiModel.TimePattern) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Digital Rush Hour",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    append("Your ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(insight.timeOfDay)
                    }
                    append(" seems to be your busiest time for ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(insight.metric)
                    }
                    append(", especially between ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(insight.period)
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LoadingInsightCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
