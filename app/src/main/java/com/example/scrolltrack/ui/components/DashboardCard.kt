package com.example.scrolltrack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.main.StatComparison
import kotlin.math.absoluteValue

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    comparison: StatComparison?,
    showComparisonText: Boolean = false,
    onCardClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.height(180.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        onClick = { onCardClick?.invoke() }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (comparison != null) {
                    val (tint, icon) = when {
                        comparison.percentageChange > 0 -> MaterialTheme.colorScheme.error to R.drawable.ic_chartline_up_duotone
                        comparison.percentageChange < 0 -> MaterialTheme.colorScheme.primary to R.drawable.ic_chartline_down_duotone
                        else -> MaterialTheme.colorScheme.onSurfaceVariant to R.drawable.ic_minus_duotone
                    }
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tint
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showComparisonText && comparison != null) {
                    val changeText = when {
                        comparison.percentageChange > 0 -> "+${"%.0f".format(comparison.percentageChange.absoluteValue)}% more"
                        comparison.percentageChange < 0 -> "-${"%.0f".format(comparison.percentageChange.absoluteValue)}% less"
                        else -> "Same as yesterday"
                    }
                    Text(
                        text = "$changeText than yesterday",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
