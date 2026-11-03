package com.omni.wifi.audit.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.ActiveTaskConsole
import com.omni.wifi.audit.ui.ModuleExecutionCard
import com.omni.wifi.audit.ui.StatusBadge
import com.omni.wifi.audit.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsTabScreen(
    currentTarget: AccessPointInfo?,
    allAccessPoints: List<AccessPointInfo>,
    onSelectTarget: (AccessPointInfo) -> Unit
) {
    val context = LocalContext.current
    val taskState by DiagnosticCore.taskState.collectAsState()
    val allModules = remember { DiagnosticModuleRegistry.getModules() }
    var selectedCategory by remember { mutableStateOf<ModuleCategory?>(null) }
    var showTargetDropdown by remember { mutableStateOf(false) }

    val filteredModules = remember(selectedCategory, allModules) {
        if (selectedCategory == null) allModules
        else allModules.filter { it.category == selectedCategory }
    }

    // Fallback target if none selected: first scanned AP or a placeholder target for local hardware modules
    val activeTarget = currentTarget ?: allAccessPoints.firstOrNull() ?: AccessPointInfo(
        bssid = "00:00:00:00:00:00",
        ssid = "Локальное устройство",
        capabilities = "[LOCAL]",
        frequency = 2412,
        signalLevel = -50,
        channel = 1,
        band = "2.4 GHz",
        encryptionType = "OPEN",
        isWpsSupported = false,
        wpsDetails = "N/A",
        wifiStandard = "802.11",
        vendor = "Локальный адаптер"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Heading
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "⚙",
                    color = Color(0xFFA65AFF),
                    fontSize = 22.sp
                )
                Text(
                    text = "Центр диагностики",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active Target Network Selector Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBrush)
                .border(1.dp, CardBorderColor, RoundedCornerShape(16.dp))
                .clickable {
                    if (allAccessPoints.isNotEmpty()) {
                        showTargetDropdown = !showTargetDropdown
                    }
                }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Целевая сеть для диагностики:", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeTarget.ssid.ifBlank { activeTarget.bssid },
                        color = CyanAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (showTargetDropdown) "▲" else "▼",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }

            if (showTargetDropdown && allAccessPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allAccessPoints.take(8).forEach { ap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (ap.bssid == activeTarget.bssid) Color(0xFF29204F) else Color(0xFF15162C))
                                .clickable {
                                    onSelectTarget(ap)
                                    showTargetDropdown = false
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = ap.ssid.ifBlank { ap.bssid }, color = TextPrimary, fontSize = 12.sp)
                            Text(text = "${ap.signalLevel} dBm", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Task Console (if running or completed report available)
        if (taskState.status != DiagnosticStatus.NOT_TESTED) {
            ActiveTaskConsole(
                taskState = taskState,
                onStop = { DiagnosticCore.cancelCurrentTask() },
                onDismiss = { DiagnosticCore.reset() }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("Все (${allModules.size})", fontSize = 11.sp) }
            )
            ModuleCategory.values().forEach { category ->
                val count = allModules.count { it.category == category }
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text("${category.displayName} ($count)", fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Modules List
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            filteredModules.forEach { module ->
                val isThisRunning = taskState.isRunning && taskState.moduleId == module.id
                val anyRunning = taskState.isRunning
                val latestReport = DiagnosticResultRepository.getLatestReport(activeTarget.bssid, module.id)

                ModuleExecutionCard(
                    module = module,
                    latestReport = latestReport,
                    isRunning = isThisRunning,
                    isBlocked = anyRunning && !isThisRunning,
                    onLaunch = {
                        DiagnosticCore.runTask(
                            context = context,
                            module = module,
                            target = activeTarget,
                            allAccessPoints = allAccessPoints
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
