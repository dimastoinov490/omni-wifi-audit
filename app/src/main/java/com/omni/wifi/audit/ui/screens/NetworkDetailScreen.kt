package com.omni.wifi.audit.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.diagnostic.providers.AndroidWifiDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.WpsInformationElementDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.scanner.CongestionLevel
import com.omni.wifi.audit.domain.scanner.WifiChannelAnalyzer
import com.omni.wifi.audit.domain.wps.WpsState
import com.omni.wifi.audit.ui.components.VisualSignalBars
import com.omni.wifi.audit.ui.theme.*

@Composable
fun NetworkDetailScreen(
    accessPoint: AccessPointInfo,
    allAccessPoints: List<AccessPointInfo> = emptyList(),
    onBack: () -> Unit,
    onLaunchDiagnostics: (AccessPointInfo) -> Unit
) {
    val context = LocalContext.current
    var isFavorite by remember { mutableStateOf(false) }
    var showFullWpsDetails by remember { mutableStateOf(false) }

    // Real system connection info
    val connInfo = remember(context, accessPoint) {
        AndroidWifiDataProvider.getConnectionInfo(context)
    }
    val isConnectedToThisAp = connInfo.isConnected &&
            (connInfo.bssid.equals(accessPoint.bssid, ignoreCase = true) ||
                    connInfo.ssid.equals(accessPoint.ssid, ignoreCase = true))

    // Real WPS Information Element extraction
    val wpsData = remember(context, accessPoint) {
        WpsInformationElementDataProvider.parseWps(context, accessPoint)
    }

    // Real Channel & Interference Analysis
    val channelAnalysis = remember(accessPoint, allAccessPoints) {
        WifiChannelAnalyzer.analyze(accessPoint, allAccessPoints)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Bar: ‹ Title ☆ ⋮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    color = Color(0xFFDDDDDD),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = accessPoint.ssid.ifBlank { "Скрытая сеть" },
                color = TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { isFavorite = !isFavorite },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFavorite) "★" else "☆",
                    color = Color(0xFFFFC84A),
                    fontSize = 24.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { /* options */ },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⋮",
                    color = Color(0xFFDDDDDD),
                    fontSize = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Hero Card matching HTML: .hero
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(HeroCardGradient)
                .border(1.dp, Color(0xFF4B3989), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "◔",
                color = Color(0xFF8757FF),
                fontSize = 58.sp,
                fontWeight = FontWeight.Bold
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = accessPoint.ssid.ifBlank { accessPoint.bssid },
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                // Connected badge
                Box(
                    modifier = Modifier
                        .padding(vertical = 7.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isConnectedToThisAp) NeonGreen else Color(0xFF262A45))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConnectedToThisAp) "Подключено" else "В зоне действия",
                        color = if (isConnectedToThisAp) Color(0xFF052B20) else Color(0xFFC0BFE0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${accessPoint.band}　·　Канал ${accessPoint.channel}　·　${accessPoint.signalLevel} dBm",
                    color = Color(0xFFC2C1E0),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                VisualSignalBars(rssi = accessPoint.signalLevel)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section Title: Информация о сети
        Text(
            text = "Информация о сети",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Info Table matching HTML: .info-table
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(CardBgAlt)
                .border(1.dp, Color(0xFF302D56), RoundedCornerShape(17.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoTableRow(label = "BSSID", value = accessPoint.bssid)
            InfoTableRow(
                label = "Производитель",
                value = accessPoint.vendor ?: wpsData.manufacturer ?: "Не определен"
            )
            InfoTableRow(
                label = "Модель роутера",
                value = wpsData.modelName ?: wpsData.modelNumber ?: "Не определена"
            )
            InfoTableRow(
                label = "Диапазон",
                value = "${accessPoint.band} (${accessPoint.wifiStandard})"
            )
            InfoTableRow(label = "Частота", value = "${accessPoint.frequency} МГц")
            InfoTableRow(label = "Канал", value = accessPoint.channel.toString())
            InfoTableRow(
                label = "Ширина канала",
                value = if (accessPoint.frequency > 5000) "80 МГц" else "20/40 МГц"
            )
            InfoTableRow(label = "Шифрование", value = accessPoint.encryptionType)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section Title: WPS информация
        Text(
            text = "WPS информация",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // WPS Card matching HTML: .wps-card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBrush)
                .border(1.dp, Color(0xFF51428A), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (accessPoint.isWpsSupported) "✓" else "✕",
                    color = if (accessPoint.isWpsSupported) NeonGreen else Color(0xFFFF8787),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (accessPoint.isWpsSupported) "WPS: Доступен" else "WPS: Отключен / Не поддерживается",
                    color = if (accessPoint.isWpsSupported) NeonGreen else Color(0xFFFF8787),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(start = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Версия: ${wpsData.version}",
                    color = Color(0xFFC6C4E2),
                    fontSize = 12.sp
                )
                Text(
                    text = "Состояние: ${if (wpsData.isLocked) "Заблокирован (Lockout)" else if (wpsData.state == WpsState.CONFIGURED) "Настроен (Configured)" else "Доступен"}",
                    color = Color(0xFFC6C4E2),
                    fontSize = 12.sp
                )
                Text(
                    text = "Конфигурационные методы: ${wpsData.configMethods.joinToString(", ").ifEmpty { "PBC, Display" }}",
                    color = Color(0xFFC6C4E2),
                    fontSize = 12.sp
                )

                if (showFullWpsDetails) {
                    wpsData.manufacturer?.let {
                        Text(text = "WPS Manufacturer: $it", color = Color(0xFF74C0FC), fontSize = 12.sp)
                    }
                    wpsData.modelName?.let {
                        Text(text = "WPS Model Name: $it", color = Color(0xFF74C0FC), fontSize = 12.sp)
                    }
                    wpsData.modelNumber?.let {
                        Text(text = "WPS Model Number: $it", color = Color(0xFF74C0FC), fontSize = 12.sp)
                    }
                    wpsData.deviceName?.let {
                        Text(text = "WPS Device Name: $it", color = Color(0xFF74C0FC), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF342476))
                    .clickable { showFullWpsDetails = !showFullWpsDetails }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showFullWpsDetails) "▲　Свернуть данные WPS" else "ⓘ　Подробнее о WPS",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section Title: Рекомендации
        Text(
            text = "Рекомендации",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Recommendations Card matching HTML: .recommend
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(CardBgAlt)
                .border(1.dp, Color(0xFF51477E), RoundedCornerShape(17.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "Результат анализа",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            val hasAciIssue = !channelAnalysis.isNonOverlapping24 && channelAnalysis.band == "2.4 GHz"
            val hasWpsLock = wpsData.isLocked
            val isHighCongestion = channelAnalysis.congestionLevel == CongestionLevel.HIGH ||
                    channelAnalysis.congestionLevel == CongestionLevel.SEVERE

            if (!hasAciIssue && !hasWpsLock && !isHighCongestion) {
                Text(
                    text = "✓　Сеть настроена корректно.",
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Критических проблем в радиоэфире не обнаружено.",
                    color = Color(0xFFBDBBD5),
                    fontSize = 12.sp
                )
            } else {
                if (hasAciIssue) {
                    Text(
                        text = "!　Межканальная интерференция (ACI)",
                        color = WarningOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Канал ${channelAnalysis.channel} пересекается со стандартными каналами 1, 6 и 11.",
                        color = Color(0xFFBDBBD5),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (hasWpsLock) {
                    Text(
                        text = "!　WPS Setup Locked активен",
                        color = WarningOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Маршрутизатор временно или аппаратно блокирует согласование WPS PIN.",
                        color = Color(0xFFBDBBD5),
                        fontSize = 12.sp
                    )
                }
            }

            channelAnalysis.recommendations.take(2).forEach { rec ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• $rec",
                    color = Color(0xFFC0BFD9),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Primary Button: ▥ Запустить диагностику
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(PrimaryButtonGradient)
                .clickable { onLaunchDiagnostics(accessPoint) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▥　Запустить диагностику",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun InfoTableRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFC0BFD9),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFFF6F4FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.25f),
            maxLines = 2
        )
    }
}
