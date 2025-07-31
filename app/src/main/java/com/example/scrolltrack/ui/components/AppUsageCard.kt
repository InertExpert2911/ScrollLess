package com.example.scrolltrack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.DateUtil
import java.io.File

@Composable
fun AppUsageCard(
    apps: List<AppUsageUiItem>,
    totalUsageTimeMillis: Long,
    onAppClick: (String) -> Unit,
    onSetLimitClick: (AppUsageUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Apps Today",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No usage data yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { app ->
                        AppUsageRow(
                            app = app,
                            totalUsageMillis = totalUsageTimeMillis,
                            onClick = { onAppClick(app.packageName) },
                            onSetLimitClick = { onSetLimitClick(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    app: AppUsageUiItem,
    totalUsageMillis: Long,
    onClick: () -> Unit,
    onSetLimitClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = app.icon ?: R.mipmap.ic_launcher_round
            ),
            contentDescription = "${app.appName} icon",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateUtil.formatDuration(app.usageTimeMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val percentage = if (totalUsageMillis > 0) {
                (app.usageTimeMillis.toFloat() / totalUsageMillis.toFloat())
            } else 0f
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
        IconButton(onClick = onSetLimitClick) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_lock_duotone),
                contentDescription = "Set Limit"
            )
        }
    }
}
