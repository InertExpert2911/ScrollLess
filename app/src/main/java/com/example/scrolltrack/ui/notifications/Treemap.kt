package com.example.scrolltrack.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.scrolltrack.ui.model.NotificationTreemapItem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class TreemapNode(val item: NotificationTreemapItem, val area: Double)

@Composable
fun Treemap(
    items: List<NotificationTreemapItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val totalValue = items.sumOf { it.count }.toDouble()
    if (totalValue == 0.0) return

    val nodes = items.map { TreemapNode(it, it.count.toDouble()) }

    Layout(
        content = {
            nodes.forEach { node ->
                Box(
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                ) {
                    TreemapTile(node = node)
                }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val area = width * height
        val normalizedNodes = nodes.map { it.copy(area = it.area / totalValue * area) }

        val rects = calculateSquarifiedRects(normalizedNodes, width, height)

        val placeables = measurables.mapIndexed { index, measurable ->
            val rect = rects[index]
            measurable.measure(
                Constraints.fixed(rect.width.roundToInt(), rect.height.roundToInt())
            )
        }

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val rect = rects[index]
                placeable.placeRelative(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}

@Composable
private fun TreemapTile(node: TreemapNode) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(node.item.color),
        contentAlignment = Alignment.Center
    ) {
        val boxWidth = this.maxWidth
        val boxHeight = this.maxHeight
        val isLandscape = boxWidth > boxHeight

        val content = @Composable {
            node.item.icon?.let {
                Image(
                    painter = rememberAsyncImagePainter(model = it),
                    contentDescription = node.item.appName,
                    modifier = Modifier.size(if (isLandscape) boxHeight * 0.4f else boxWidth * 0.4f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.item.appName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = (sqrt(boxWidth.value * boxHeight.value) / 10f).coerceAtMost(14f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.item.count.toString(),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontSize = (sqrt(boxWidth.value * boxHeight.value) / 14f).coerceAtMost(12f).sp
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) { content() }
        } else {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { content() }
        }
    }
}


private fun calculateSquarifiedRects(
    nodes: List<TreemapNode>,
    width: Int,
    height: Int
): List<Rect> {
    val resultRects = mutableListOf<Rect>()
    val remainingNodes = nodes.toMutableList()
    var currentRect = Rect(0, 0, width, height)

    while (remainingNodes.isNotEmpty()) {
        val row = mutableListOf<TreemapNode>()
        var rowLength = min(currentRect.width, currentRect.height).toDouble()

        // If the rectangle to place in is degenerate, we can't continue.
        if (rowLength <= 0) break

        var worstRatio = Double.MAX_VALUE
        var nodesInRow = 0

        for (i in remainingNodes.indices) {
            val node = remainingNodes[i]
            val newRow = row + node
            val newRatio = getWorstAspectRatio(newRow, rowLength)
            if (newRatio <= worstRatio) {
                row.add(node)
                worstRatio = newRatio
                nodesInRow++
            } else {
                break
            }
        }

        val totalAreaInRow = row.sumOf { it.area }
        var currentX = currentRect.left
        var currentY = currentRect.top

        if (currentRect.width >= currentRect.height) { // Horizontal layout
            val rowHeight = if (currentRect.width > 0) (totalAreaInRow / currentRect.width).toFloat() else 0f
            if (rowHeight <= 0) break // Stop if row has no height

            for (node in row) {
                val nodeWidth = if (rowHeight > 0) (node.area / rowHeight).toFloat() else 0f
                resultRects.add(Rect(currentX, currentY, currentX + nodeWidth, currentY + rowHeight))
                currentX += nodeWidth
            }
            currentRect = Rect(currentRect.left, currentRect.top + rowHeight, currentRect.right, currentRect.bottom)
        } else { // Vertical layout
            val rowWidth = if (currentRect.height > 0) (totalAreaInRow / currentRect.height).toFloat() else 0f
            if (rowWidth <= 0) break // Stop if row has no width

            for (node in row) {
                val nodeHeight = if (rowWidth > 0) (node.area / rowWidth).toFloat() else 0f
                resultRects.add(Rect(currentX, currentY, currentX + rowWidth, currentY + nodeHeight))
                currentY += nodeHeight
            }
            currentRect = Rect(currentRect.left + rowWidth, currentRect.top, currentRect.right, currentRect.bottom)
        }

        // Remove the processed nodes from the beginning of the list
        repeat(nodesInRow) { remainingNodes.removeAt(0) }
    }
    return resultRects
}

private fun squarify(
    nodes: List<TreemapNode>,
    rect: Rect,
    resultRects: MutableList<Rect>
) {
    if (nodes.isEmpty()) return

    // This function is now replaced by the iterative implementation in calculateSquarifiedRects
    // It is kept here to avoid breaking the call signature but is effectively unused.
    // The new iterative approach is safer against stack overflows.
}

private fun getWorstAspectRatio(row: List<TreemapNode>, length: Double): Double {
    if (row.isEmpty() || length == 0.0) return Double.MAX_VALUE

    val totalArea = row.sumOf { it.area }
    if (totalArea <= 0.0) return Double.MAX_VALUE

    val maxArea = row.maxOf { it.area }
    val minArea = row.minOf { it.area }
    if (minArea <= 0.0) return Double.MAX_VALUE


    val lengthSq = length * length
    val totalAreaSq = totalArea * totalArea

    return max(
        (lengthSq * maxArea) / totalAreaSq,
        totalAreaSq / (lengthSq * minArea)
    )
}

// A simple Rect class since Android's Rect is Int based
private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    constructor(left: Int, top: Int, right: Int, bottom: Int) : this(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
} 