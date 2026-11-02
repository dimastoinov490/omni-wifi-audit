package com.omni.wifi.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.WifiDiagnosticRunner
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.theme.AuditGradient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    accessPoint: AccessPointInfo,
    allAccessPoints: List<AccessPointInfo> = emptyList(),
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Module 1: Channel & Interference
    var channelResult by remember { mutableStateOf<String?>(null) }
    var isChannelRunning by remember { mutableStateOf(false) }
    var channelJob by remember { mutableStateOf<Job?>(null) }

    // Module 2: WPS Inspector & Driver
    var wpsLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isWpsRunning by remember { mutableStateOf(false) }
    var wpsJob by remember { mutableStateOf<Job?>(null) }

    // Module 3: Pixie-Dust Feasibility
    var pixieLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPixieRunning by remember { mutableStateOf(false) }
    var pixieJob by remember { mutableStateOf<Job?>(null) }

    // Module 4: Gateway & Web Admin Audit
    var gatewayLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGatewayRunning by remember { mutableStateOf(false) }
    var gatewayJob by remember { mutableStateOf<Job?>(null) }

    // Module 5: Router Database CVEs
    var cveResult by remember { mutableStateOf<String?>(null) }
    var isCveRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                channelJob?.cancel()
                wpsJob?.cancel()
                pixieJob?.cancel()
                gatewayJob?.cancel()
                onBack()
            }) {
                Text(text = "← Назад", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            }
            Text(
                text = "ДИАГНОСТИКА СЕТИ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                color = Color.White
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(AuditGradient)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AP Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = accessPoint.ssid,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "BSSID: ${accessPoint.bssid}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(text = "Производитель: ${accessPoint.vendor ?: "Не определен"}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    text = "Диапазон: ${accessPoint.band} | Канал: ${accessPoint.channel} (${accessPoint.frequency} МГц)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "Стандарт: ${accessPoint.wifiStandard} | Защита: ${accessPoint.encryptionType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "WPS: ${accessPoint.wpsDetails}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accessPoint.isWpsSupported) Color(0xFFFFA94D) else Color.Gray
                )
                Text(
                    text = "Уровень сигнала: ${accessPoint.signalLevel} дБм",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ДИАГНОСТИЧЕСКИЕ ТЕСТЫ",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Channel and Interference Diagnostic
        DiagnosticTextCard(
            title = "Анализ радиоканала и эфира",
            description = "Реальный расчет параметров радиочастотного канала, межканальной интерференции (ACI/CCI) и качества сигнала.",
            isRunning = isChannelRunning,
            result = channelResult,
            onStart = {
                channelJob?.cancel()
                isChannelRunning = true
                channelResult = null
                channelJob = coroutineScope.launch {
                    try {
                        val result = WifiDiagnosticRunner.analyzeChannelAndSignal(accessPoint, allAccessPoints)
                        channelResult = result
                    } catch (e: Exception) {
                        channelResult = "[-] Ошибка выполнения анализа: ${e.localizedMessage}"
                    } finally {
                        isChannelRunning = false
                    }
                }
            },
            onStop = {
                channelJob?.cancel()
                isChannelRunning = false
                channelResult = "[!] Анализ остановлен пользователем."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. WPS Inspector & Driver Check
        DiagnosticTerminalCard(
            title = "WPS Инспектор и аудит драйвера",
            description = "Определение параметров WPS из пакетов Beacon/Probe Response, проверка root-прав и ограничений Qualcomm qcacld-3.0.",
            isRunning = isWpsRunning,
            logs = wpsLogs,
            onStart = {
                wpsJob?.cancel()
                isWpsRunning = true
                wpsLogs = emptyList()
                wpsJob = coroutineScope.launch {
                    try {
                        WifiDiagnosticRunner.inspectWpsAndDriver(context, accessPoint) { line ->
                            wpsLogs = wpsLogs + line
                        }
                    } catch (e: Exception) {
                        wpsLogs = wpsLogs + "[-] Ошибка выполнения: ${e.localizedMessage}"
                    } finally {
                        isWpsRunning = false
                    }
                }
            },
            onStop = {
                wpsJob?.cancel()
                isWpsRunning = false
                wpsLogs = wpsLogs + "[!] Проверка прервана пользователем."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Pixie-Dust Feasibility Diagnostics
        DiagnosticTerminalCard(
            title = "Диагностика условий Pixie-Dust",
            description = "Анализ готовности устройства и совместимости чипсета. Не производит перебор или подбор PIN-кода.",
            isRunning = isPixieRunning,
            logs = pixieLogs,
            onStart = {
                pixieJob?.cancel()
                isPixieRunning = true
                pixieLogs = emptyList()
                pixieJob = coroutineScope.launch {
                    try {
                        WifiDiagnosticRunner.checkPixieDustFeasibility(context, accessPoint) { line ->
                            pixieLogs = pixieLogs + line
                        }
                    } catch (e: Exception) {
                        pixieLogs = pixieLogs + "[-] Ошибка диагностики: ${e.localizedMessage}"
                    } finally {
                        isPixieRunning = false
                    }
                }
            },
            onStop = {
                pixieJob?.cancel()
                isPixieRunning = false
                pixieLogs = pixieLogs + "[!] Проверка отменена пользователем."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Gateway & Web Admin Audit
        DiagnosticTerminalCard(
            title = "Аудит сетевого шлюза и веб-админки",
            description = "Реальное сканирование портов 80/443 шлюза при активном подключении к сети. Проверка заводских учетных записей.",
            isRunning = isGatewayRunning,
            logs = gatewayLogs,
            onStart = {
                gatewayJob?.cancel()
                isGatewayRunning = true
                gatewayLogs = emptyList()
                gatewayJob = coroutineScope.launch {
                    try {
                        WifiDiagnosticRunner.auditGatewayAndWebAdmin(context, accessPoint) { line ->
                            gatewayLogs = gatewayLogs + line
                        }
                    } catch (e: Exception) {
                        gatewayLogs = gatewayLogs + "[-] Ошибка сетевого запроса: ${e.localizedMessage}"
                    } finally {
                        isGatewayRunning = false
                    }
                }
            },
            onStop = {
                gatewayJob?.cancel()
                isGatewayRunning = false
                gatewayLogs = gatewayLogs + "[!] Аудит шлюза прерван пользователем."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Router Database & CVE
        DiagnosticTextCard(
            title = "База уязвимостей и CVE оборудования",
            description = "Поиск зарегистрированных уязвимостей и бюллетеней безопасности для производителя ${accessPoint.vendor ?: "устройства"}.",
            isRunning = isCveRunning,
            result = cveResult,
            onStart = {
                isCveRunning = true
                cveResult = null
                coroutineScope.launch {
                    val matchingCves = accessPoint.potentialVulnerabilities.filter { it.contains("CVE") }
                    cveResult = if (matchingCves.isNotEmpty()) {
                        "=== ПОДТВЕРЖДЕННЫЕ CVE ДЛЯ ВЕНДОРА '${accessPoint.vendor}' ===\n" +
                                matchingCves.joinToString("\n\n") { "• $it" } +
                                "\n\n[i] Примечание: Уязвимость подтверждается при совпадении точной модели и версии прошивки устройства."
                    } else {
                        "[OK] Критические зарегистрированные CVE для '${accessPoint.vendor}' отсутствуют в локальной базе."
                    }
                    isCveRunning = false
                }
            },
            onStop = {
                isCveRunning = false
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DiagnosticTextCard(
    title: String,
    description: String,
    isRunning: Boolean,
    result: String?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Выполняется...", color = Color.White)
                    } else {
                        Text(text = "Запустить тест", color = Color.White)
                    }
                }

                if (isRunning) {
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Text(text = "Остановить")
                    }
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101014), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.contains("[!]") || result.contains("Критическ")) Color(0xFFFF8787) else Color(0xFF69DB7C),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticTerminalCard(
    title: String,
    description: String,
    isRunning: Boolean,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Выполняется...", color = Color.White)
                    } else {
                        Text(text = "Запустить диагностику", color = Color.White)
                    }
                }

                if (isRunning) {
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Text(text = "Остановить")
                    }
                }
            }

            if (logs.isNotEmpty() || isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 260.dp)
                        .background(Color(0xFF0C0C10), shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(logs.size) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        logs.forEach { logLine ->
                            Text(
                                text = logLine,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = when {
                                    logLine.contains("[-]") || logLine.contains("[!]") -> Color(0xFFFF8787)
                                    logLine.contains("[+]") || logLine.contains("[OK]") -> Color(0xFF69DB7C)
                                    logLine.contains("[i]") -> Color(0xFF74C0FC)
                                    else -> Color(0xFFE2E8F0)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
