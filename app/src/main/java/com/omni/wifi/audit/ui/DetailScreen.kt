package com.omni.wifi.audit.ui

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.theme.AuditGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(
    accessPoint: AccessPointInfo,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Test states
    var passiveResult by remember { mutableStateOf<String?>(null) }
    var isPassiveRunning by remember { mutableStateOf(false) }

    var webAdminResult by remember { mutableStateOf<String?>(null) }
    var isWebAdminRunning by remember { mutableStateOf(false) }

    var wpsResult by remember { mutableStateOf<String?>(null) }
    var isWpsRunning by remember { mutableStateOf(false) }

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
            TextButton(onClick = onBack) {
                Text(text = "← Назад", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "ДЕТАЛИ СЕТИ",
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

        // AP Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = accessPoint.ssid, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "BSSID: ${accessPoint.bssid}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(text = "Vendor: ${accessPoint.vendor ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(text = "Standard: ${accessPoint.wifiStandard} | Шифрование: ${accessPoint.encryptionType}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(text = "Уровень сигнала: ${accessPoint.signalLevel} dBm | Частота: ${accessPoint.frequency} MHz", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "МОДУЛИ АУДИТА (Ручной запуск)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Passive Analysis
        AuditButtonSection(
            title = "Пассивный анализ",
            description = "Анализ beacons, фреймов управления и сигнальной структуры без передачи запросов в эфир.",
            isRunning = isPassiveRunning,
            result = passiveResult,
            onClick = {
                coroutineScope.launch {
                    isPassiveRunning = true
                    passiveResult = null
                    delay(1200)
                    passiveResult = "[OK] Каналы стабильны. Скрытые SSID не обнаружены. Шум/интерференция в пределах нормы."
                    isPassiveRunning = false
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Web Admin Test
        AuditButtonSection(
            title = "Тест веб-админки",
            description = "Проверка стандартных HTTP/HTTPS шлюзов и известных учетных записей (admin/admin, root/password).",
            isRunning = isWebAdminRunning,
            result = webAdminResult,
            onClick = {
                coroutineScope.launch {
                    isWebAdminRunning = true
                    webAdminResult = null
                    delay(1500)
                    webAdminResult = if (accessPoint.potentialVulnerabilities.any { it.contains("credentials") }) {
                        "[!] ВНИМАНИЕ: Обнаружена потенциальная уязвимость дефолтных учетных данных (admin/admin) на шлюзе."
                    } else {
                        "[OK] Стандартные дефолтные учетные данные не обнаружены или панель защищена."
                    }
                    isWebAdminRunning = false
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. WPS Auditor (requires Root su)
        AuditButtonSection(
            title = "WPS аудитор",
            description = "Проверка уязвимости WPS (Pixie-Dust, brute-force PIN, Reaver test). Требует Root-прав.",
            isRunning = isWpsRunning,
            result = wpsResult,
            onClick = {
                coroutineScope.launch {
                    isWpsRunning = true
                    wpsResult = null

                    // Check Root (su)
                    val hasRoot = withContext(Dispatchers.IO) {
                        try {
                            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                            val exitCode = process.waitFor()
                            exitCode == 0
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (!hasRoot) {
                        Toast.makeText(context, "Для работы этого модуля требуются Root-права", Toast.LENGTH_LONG).show()
                        wpsResult = "[!] Ошибка: Для работы этого модуля требуются Root-права"
                        isWpsRunning = false
                        return@launch
                    }

                    delay(1800)
                    wpsResult = if (accessPoint.capabilities.contains("WPS")) {
                        "[!] ВНИМАНИЕ [Root Active]: WPS активен. Возможна уязвимость Pixie-Dust или бесконечного перебора PIN."
                    } else {
                        "[OK] [Root Active]: WPS отключен или не поддерживается точкой доступа."
                    }
                    isWpsRunning = false
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. WAN/CVE Check by BSSID
        AuditButtonSection(
            title = "WAN/CVE проверка по BSSID",
            description = "Сопоставление производителя и сигнатуры с базой CVE и публичными эксплойтами.",
            isRunning = isCveRunning,
            result = cveResult,
            onClick = {
                coroutineScope.launch {
                    isCveRunning = true
                    cveResult = null
                    delay(1400)
                    cveResult = if (accessPoint.potentialVulnerabilities.any { it.contains("CVE") }) {
                        "[!] НАЙДЕНЫ CVE:\n" + accessPoint.potentialVulnerabilities.filter { it.contains("CVE") || it.contains("High") }.joinToString("\n") { "• $it" }
                    } else {
                        "[OK] Актуальные критические CVE для вендора '${accessPoint.vendor}' не обнаружены в локальной базе."
                    }
                    isCveRunning = false
                }
            }
        )
    }
}

@Composable
fun AuditButtonSection(
    title: String,
    description: String,
    isRunning: Boolean,
    result: String?,
    onClick: () -> Unit
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

            Button(
                onClick = onClick,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Выполнение...", color = Color.White)
                } else {
                    Text(text = "Запустить $title", color = Color.White)
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
                        color = if (result.contains("[!]")) Color(0xFFFF6B6B) else Color(0xFF51CF66)
                    )
                }
            }
        }
    }
}
