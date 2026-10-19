package com.omni.wifi.audit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.omni.wifi.audit.ui.theme.AuditGradient

@Composable
fun SignalStrengthGraph(level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(40.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = width / 5
        val normalizedLevel = (level + 100).coerceIn(0, 100) / 25 // 0 to 4

        for (i in 0 until 4) {
            val barHeight = (height / 4) * (i + 1)
            val color = if (i <= normalizedLevel) Color.Cyan else Color.DarkGray
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(i * (barWidth + 2.dp.toPx()), height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
fun ShieldIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2, 2.dp.toPx())
            lineTo(2.dp.toPx(), 6.dp.toPx())
            lineTo(2.dp.toPx(), 12.dp.toPx())
            quadraticBezierTo(2.dp.toPx(), 20.dp.toPx(), size.width / 2, 22.dp.toPx())
            quadraticBezierTo(size.width - 2.dp.toPx(), 20.dp.toPx(), size.width - 2.dp.toPx(), 12.dp.toPx())
            lineTo(size.width - 2.dp.toPx(), 6.dp.toPx())
            close()
        }
        drawPath(path, brush = AuditGradient, style = Stroke(width = 2.dp.toPx()))
    }
}
