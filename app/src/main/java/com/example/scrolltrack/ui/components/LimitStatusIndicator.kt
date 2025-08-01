package com.example.scrolltrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.scrolltrack.R
import com.example.scrolltrack.ui.limit.LimitInfo
import com.example.scrolltrack.util.DateUtil

@Composable
fun LimitStatusIndicator(
    limitInfo: LimitInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconRes = when {
            limitInfo == null -> R.drawable.ic_hour_glass_empty
            limitInfo.timeRemainingMillis <= 0 -> R.drawable.ic_hour_glass_end
            (limitInfo.timeRemainingMillis.toFloat() / limitInfo.timeLimitMillis) < 0.2f -> R.drawable.ic_hour_glass_end
            else -> R.drawable.ic_hour_glass_start
        }
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Time Limit Status",
            tint = LocalContentColor.current
        )
        if (limitInfo != null) {
            Text(
                text = DateUtil.formatDuration(limitInfo.timeRemainingMillis),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}