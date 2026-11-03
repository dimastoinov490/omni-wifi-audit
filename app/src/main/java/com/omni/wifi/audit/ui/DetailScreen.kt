package com.omni.wifi.audit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.AndroidWifiDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.WpsInformationElementDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.scanner.CongestionLevel
import com.omni.wifi.audit.domain.scanner.WifiChannelAnalyzer
import com.omni.wifi.audit.domain.wps.WpsState
import com.omni.wifi.audit.ui.components.VisualSignalBars
import com.omni.wifi.audit.ui.theme.*

/**
 * Screen 3 from HTML Prototype: Экран выбранной сети (DetailScreen)
 */
@Composable
fun DetailScreen(
    accessPoint: AccessPointInfo,
    allAccessPoints: List<AccessPointInfo> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isFavorite by remember { mutableStateOf(false) }
    var showFullWpsDetails by remember { mutableStateOf(false) }

    val taskState by DiagnosticCore.taskState.collectAsState()

    BackHandler {
        DiagnosticCore.cancelCurrentTask()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            DiagnosticCore.cancelCurrentTask()
        }
    }

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
            .background(BackgroundBrush)
            .statusBarsPadding()
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
                    .clickable {
                        DiagnosticCore.cancelCurrentTask()
                        onBack()
                    },
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
                    .clip(CircleShape),
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

        // Live Diagnostic Console if a diagnostic task was started
        if (taskState.status != DiagnosticStatus.NOT_TESTED) {
            ActiveTaskConsole(
                taskState = taskState,
                onStop = { DiagnosticCore.cancelCurrentTask() },
                onDismiss = { DiagnosticCore.reset() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

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
                .clickable {
                    val targetModule = DiagnosticModuleRegistry.getModuleById("wps_info_module")
                        ?: DiagnosticModuleRegistry.getModuleById("channel_analysis_module")
                    if (targetModule != null) {
                        DiagnosticCore.runTask(context, targetModule, accessPoint, allAccessPoints)
                    }
                }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (taskState.isRunning) "▥　Выполняется диагностика..." else "▥　Запустить диагностику",
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

@Composable
fun ActiveTaskConsole(
    taskState: DiagnosticTaskState,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1117)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = taskState.moduleTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Этап: ${taskState.currentStage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF74C0FC)
                    )
                }
                StatusBadge(status = taskState.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (taskState.isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF1E222D)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Terminal Logs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .background(Color(0xFF08090C), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(taskState.logs.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    taskState.logs.forEach { logLine ->
                        Text(
                            text = logLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = when {
                                logLine.contains("[-]") || logLine.contains("[!]") || logLine.contains("Ошибка") -> Color(0xFFFF8787)
                                logLine.contains("[+]") || logLine.contains("[OK]") -> Color(0xFF69DB7C)
                                logLine.contains("[i]") -> Color(0xFF74C0FC)
                                else -> Color(0xFFD0D7DE)
                            }
                        )
                    }
                }
            }

            // Discovered Data Items
            taskState.report?.dataItems?.let { items ->
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Обнаруженные параметры:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "• ${item.key}:", fontSize = 11.sp, color = Color.LightGray)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = item.value, fontSize = 11.sp, color = Color(0xFF74C0FC), fontWeight = FontWeight.Medium)
                                    OriginChip(origin = item.origin)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (taskState.isRunning) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE03131))
                    ) {
                        Text(text = "Остановить", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text(text = "Скрыть панель", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleExecutionCard(
    module: IDiagnosticModule,
    latestReport: DiagnosticReport?,
    isRunning: Boolean,
    isBlocked: Boolean,
    onLaunch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = module.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    Text(text = "Категория: ${module.category.displayName} • v${module.version}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF74C0FC))
                }
                if (latestReport != null) {
                    StatusBadge(status = latestReport.status)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = module.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            // Badges for requirements
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RequirementBadge(
                    label = if (module.requiresRoot) "Root (su): Да" else "Без Root",
                    isWarning = module.requiresRoot
                )
                RequirementBadge(
                    label = if (module.requiresConnection) "Сеть: Подключение" else "Сеть: Радиоэфир",
                    isWarning = module.requiresConnection
                )
                if (module.dependencies.isNotEmpty()) {
                    RequirementBadge(
                        label = "Зависимости: ${module.dependencies.size}",
                        isWarning = false
                    )
                }
            }

            // Summary of latest report if present
            if (latestReport != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF141720),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Отчет: ${latestReport.summary}",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onLaunch,
                enabled = !isRunning && !isBlocked,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Выполняется...", color = Color.White)
                } else {
                    Text(text = if (latestReport == null) "Запустить диагностику" else "Повторить проверку", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun RequirementBadge(label: String, isWarning: Boolean) {
    Surface(
        color = if (isWarning) Color(0xFF2C1B14) else Color(0xFF16231C),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            color = if (isWarning) Color(0xFFFFA94D) else Color(0xFF69DB7C),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun OriginChip(origin: DataSourceOrigin) {
    Surface(
        color = Color(0xFF182234),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = origin.name,
            color = Color(0xFF74C0FC),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun StatusBadge(status: DiagnosticStatus) {
    val (bgColor, textColor, label) = when (status) {
        DiagnosticStatus.SUPPORTED -> Triple(Color(0xFF2B8A3E), Color.White, "SUPPORTED")
        DiagnosticStatus.UNSUPPORTED -> Triple(Color(0xFFC92A2A), Color.White, "UNSUPPORTED")
        DiagnosticStatus.UNKNOWN -> Triple(Color(0xFF868E96), Color.White, "UNKNOWN")
        DiagnosticStatus.PARTIAL -> Triple(Color(0xFFFAB005), Color.Black, "PARTIAL")
        DiagnosticStatus.WARNING -> Triple(Color(0xFFFD7E14), Color.White, "WARNING")
        DiagnosticStatus.ERROR -> Triple(Color(0xFFFA5252), Color.White, "ERROR")
        DiagnosticStatus.RUNNING -> Triple(Color(0xFF1971C2), Color.White, "RUNNING")
        DiagnosticStatus.NOT_TESTED -> Triple(Color(0xFF495057), Color.White, "NOT TESTED")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
