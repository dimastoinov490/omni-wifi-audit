package com.omni.wifi.audit.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.ui.theme.*

val ShieldShape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w * 0.96f, h * 0.18f)
    lineTo(w * 0.90f, h * 0.68f)
    lineTo(w * 0.50f, h * 1.0f)
    lineTo(w * 0.10f, h * 0.68f)
    lineTo(w * 0.04f, h * 0.18f)
    close()
}

/**
 * Shield Logo matching HTML prototype:
 * Clip-path polygon(50% 0, 96% 18%, 90% 68%, 50% 100%, 10% 68%, 4% 18%)
 * Border #378aff, linear-gradient(#5835bd66, #087edc22), center lightning icon.
 */
@Composable
fun OmniShieldLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(ShieldShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0x995835BD), Color(0x33087EDC))
                )
            )
            .border(2.dp, Brush.linearGradient(listOf(BlueAccent, CyanAccent)), ShieldShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⌁",
            color = CyanAccent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Radar scanner with circular concentric glow rings and rotating scan sweep.
 */
@Composable
fun RadarScannerView(
    isScanning: Boolean,
    networkCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) * 0.92f

                // Outer faint glows
                drawCircle(
                    color = Color(0x2249338B),
                    radius = radius,
                    style = Stroke(width = 30.dp.toPx())
                )
                drawCircle(
                    color = Color(0x33352568),
                    radius = radius * 0.85f,
                    style = Stroke(width = 16.dp.toPx())
                )

                // Main ring
                drawCircle(
                    color = Color(0xFF51449C),
                    radius = radius * 0.80f,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner ring
                drawCircle(
                    color = Color(0xFF4A3D9B),
                    radius = radius * 0.55f,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Center point
                drawCircle(
                    color = PurplePrimary,
                    radius = 4.dp.toPx()
                )

                // Rotating radar sweep when scanning
                if (isScanning) {
                    val sweepBrush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x11793CFF),
                            Color(0x4421CAFF),
                            CyanAccent
                        ),
                        center = center
                    )
                    drawArc(
                        brush = sweepBrush,
                        startAngle = angle,
                        sweepAngle = 90f,
                        useCenter = true,
                        size = Size(radius * 1.6f, radius * 1.6f),
                        topLeft = Offset(center.x - radius * 0.8f, center.y - radius * 0.8f)
                    )
                }

                // Decorative arcs (cyan and magenta) matching prototype .ring:before
                drawArc(
                    color = Color(0xFFD34CFF),
                    startAngle = -150f,
                    sweepAngle = 75f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.72f),
                    size = Size(radius * 1.44f, radius * 1.44f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawArc(
                    color = CyanAccent,
                    startAngle = 30f,
                    sweepAngle = 75f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.72f),
                    size = Size(radius * 1.44f, radius * 1.44f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Center Wi-Fi icon with pulse
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        if (isScanning) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◔",
                    color = PurplePrimary,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Caption & networks found count
        Text(
            text = if (isScanning) "Сканирование сетей..." else "Сканирование завершено",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Найдено сетей: $networkCount",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

/**
 * Visual signal bars on Detail Screen (gradient purple to cyan/green).
 */
@Composable
fun VisualSignalBars(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val normalized = (rssi + 100).coerceIn(10, 100) / 100f
    val barHeights = listOf(9.dp, 16.dp, 24.dp, 32.dp, 40.dp, 26.dp)

    Row(
        modifier = modifier.height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEachIndexed { index, height ->
            val isActive = (index + 1).toFloat() / barHeights.size <= (normalized + 0.15f)
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(height)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) SignalBarBrush
                        else Brush.verticalGradient(listOf(Color(0xFF2E2F45), Color(0xFF1E2034)))
                    )
            )
        }
    }
}

/**
 * Bottom Navigation Bar with 4 tabs:
 * Главная, Сети, Диагностика, История
 */
enum class NavigationTab(val title: String, val icon: String) {
    HOME("Главная", "⌂"),
    NETWORKS("Сети", "◉"),
    DIAGNOSTICS("Диагностика", "⚙"),
    HISTORY("История", "▤")
}

@Composable
fun PrototypeBottomNavBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color(0xF20C0D20),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF393264))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = NavigationTab.values()
            for (tab in tabs) {
                val isSelected = selectedTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tab.icon,
                        fontSize = 20.sp,
                        color = if (isSelected) PurpleLight else TextMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        color = if (isSelected) PurpleLight else TextMuted,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
