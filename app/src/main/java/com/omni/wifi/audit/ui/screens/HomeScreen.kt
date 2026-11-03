package com.omni.wifi.audit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.hardware.SystemOverview
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.components.OmniShieldLogo
import com.omni.wifi.audit.ui.components.RadarScannerView
import com.omni.wifi.audit.ui.theme.*

@Composable
fun HomeScreen(
    scanResults: List<AccessPointInfo>,
    isScanning: Boolean,
    systemInfo: SystemOverview,
    onRefresh: () -> Unit,
    onNavigateToNetworks: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Bar: Logo + Brand + Gear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OmniShieldLogo(size = 54.dp)
                Column {
                    Text(
                        text = "Omni WiFi Audit",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Профессиональный аудит\nвашей Wi-Fi сети",
                        color = Color(0xFFB9B7D9),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onNavigateToDiagnostics() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    color = Color(0xFFAAA7E8),
                    fontSize = 26.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Center Radar Scanner
        RadarScannerView(
            isScanning = isScanning,
            networkCount = scanResults.size,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // System Status Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(19.dp))
                .background(CardBrush)
                .border(1.dp, CardBorderColor, RoundedCornerShape(19.dp))
                .clickable { onNavigateToNetworks() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NeonGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color(0xFF06291B),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Статус системы",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (scanResults.isNotEmpty()) "Готово к работе" else "Инициализация сканера",
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "›",
                color = Color(0xFFAAA7DF),
                fontSize = 24.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2x2 Stats Grid from Real System Providers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            StatCard(
                icon = "▦",
                title = "Драйвер Wi-Fi",
                value = systemInfo.driverName,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = "♟",
                title = "Android",
                value = systemInfo.androidVersion,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            StatCard(
                icon = "⬟",
                title = "Root",
                value = systemInfo.rootStatusText,
                isSubtle = !systemInfo.isRootGranted,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = "▣",
                title = "Батарея",
                value = "${systemInfo.batteryPercent}%",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Action Button (⟳ Обновить)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(PrimaryButtonGradient)
                .clickable { onRefresh() }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isScanning) "⟳　Сканирование..." else "⟳　Обновить",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatCard(
    icon: String,
    title: String,
    value: String,
    isSubtle: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(CardBrush)
            .border(1.dp, CardBorderColor, RoundedCornerShape(19.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF29265B)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = Color(0xFF9285FF),
                fontSize = 20.sp
            )
        }

        Column {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = if (isSubtle) TextMuted else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
