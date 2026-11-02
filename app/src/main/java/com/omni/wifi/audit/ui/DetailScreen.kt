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
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.theme.AuditGradient

@Composable
fun DetailScreen(
    accessPoint: AccessPointInfo,
    allAccessPoints: List<AccessPointInfo> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val taskState by DiagnosticEngine.taskState.collectAsState()
    val modules = remember { DiagnosticModuleRegistry.getModules() }

    DisposableEffect(Unit) {
        onDispose {
            DiagnosticEngine.cancelCurrentTask()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                DiagnosticEngine.cancelCurrentTask()
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

        // Target Access Point Card
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
                Text(text = "Оборудование: ${accessPoint.vendor ?: "Не определено"}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF74C0FC))
                Text(
                    text = "Диапазон: ${accessPoint.band} • Канал ${accessPoint.channel} (${accessPoint.frequency} МГц)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "Защита: ${accessPoint.encryptionType} (${accessPoint.wifiStandard})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "Параметры WPS: ${accessPoint.wpsDetails}",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Active Diagnostic Task View (if running or completed)
        if (taskState.status != DiagnosticStatus.IDLE) {
            ActiveTaskConsole(
                taskState = taskState,
                onStop = { DiagnosticEngine.cancelCurrentTask() },
                onDismiss = { DiagnosticEngine.reset() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section Title
        Text(
            text = "ДИАГНОСТИЧЕСКИЕ МОДУЛИ (${modules.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // List of Diagnostic Modules
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            modules.forEach { module ->
                val isThisRunning = taskState.isRunning && taskState.moduleId == module.id
                val anyRunning = taskState.isRunning

                ModuleExecutionCard(
                    module = module,
                    isRunning = isThisRunning,
                    isBlocked = anyRunning && !isThisRunning,
                    onLaunch = {
                        DiagnosticEngine.runTask(
                            context = context,
                            module = module,
                            target = accessPoint,
                            allAccessPoints = allAccessPoints
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
            Text(text = module.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = module.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            // Badges for requirements
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RequirementBadge(
                    label = if (module.requiresRoot) "Root (su): Да" else "Без Root",
                    isWarning = module.requiresRoot
                )
                RequirementBadge(
                    label = if (module.requiresConnection) "Сеть: Подключение" else "Сеть: Радиоэфир",
                    isWarning = module.requiresConnection
                )
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
                    Text(text = "Запустить диагностику", color = Color.White)
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
fun StatusBadge(status: DiagnosticStatus) {
    val (bgColor, textColor, label) = when (status) {
        DiagnosticStatus.IDLE -> Triple(Color.DarkGray, Color.White, "IDLE")
        DiagnosticStatus.RUNNING -> Triple(Color(0xFF1971C2), Color.White, "В ПРОЦЕССЕ")
        DiagnosticStatus.SUCCESS -> Triple(Color(0xFF2B8A3E), Color.White, "SUCCESS")
        DiagnosticStatus.FAILED -> Triple(Color(0xFFC92A2A), Color.White, "FAILED")
        DiagnosticStatus.UNSUPPORTED -> Triple(Color(0xFFE8590C), Color.White, "UNSUPPORTED")
        DiagnosticStatus.PERMISSION_DENIED -> Triple(Color(0xFFD6336C), Color.White, "DENIED")
        DiagnosticStatus.TIMEOUT -> Triple(Color(0xFFE03131), Color.White, "TIMEOUT")
        DiagnosticStatus.CANCELLED -> Triple(Color(0xFF495057), Color.White, "CANCELLED")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
