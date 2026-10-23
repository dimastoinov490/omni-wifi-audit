package com.omni.wifi.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.components.ShieldIcon
import com.omni.wifi.audit.ui.components.SignalStrengthGraph
import com.omni.wifi.audit.ui.theme.AuditGradient

@Composable
fun MainScreen(
    scanResults: List<AccessPointInfo>,
    isScanning: Boolean = false,
    onScanClick: () -> Unit = {},
    onUpdateGithub: () -> Unit = {},
    onUpdateLocalFile: () -> Unit = {},
    onAccessPointClick: (AccessPointInfo) -> Unit
) {
    var dragAmountSum by remember { mutableStateOf(0f) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Обновить базы PIN и уязвимостей", color = Color.White) },
            text = { Text("Выберите источник загрузки файлов баз данных (OUI, CVE, PIN):", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onUpdateGithub()
                }) {
                    Text("Репозиторий GitHub", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onUpdateLocalFile()
                }) {
                    Text("Локальный файл (.json / .txt)", color = Color.LightGray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .pointerInput(isScanning) {
                detectVerticalDragGestures(
                    onDragStart = { dragAmountSum = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAmountSum += dragAmount
                    },
                    onDragEnd = {
                        if (dragAmountSum > 80f && !isScanning) {
                            onScanClick()
                        }
                        dragAmountSum = 0f
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OMNI WIFI AUDIT",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showUpdateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(text = "Обновить базы", fontSize = 11.sp)
                }

                Button(
                    onClick = onScanClick,
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Поиск...", fontSize = 11.sp)
                    } else {
                        Text(text = "Сканировать", fontSize = 11.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(AuditGradient)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Обнаруженные сети (${scanResults.size})",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Gray
            )

            if (isScanning) {
                Text(
                    text = "Сканирование эфира...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (scanResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Сети не найдены. Нажмите «Сканировать».", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scanResults) { ap ->
                    AccessPointCard(ap = ap, onClick = { onAccessPointClick(ap) })
                }
            }
        }
    }
}

@Composable
fun AccessPointCard(ap: AccessPointInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalStrengthGraph(level = ap.signalLevel)
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = ap.ssid, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = ap.bssid, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(text = "${ap.vendor} | ${ap.wifiStandard} | ${ap.encryptionType}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            if (ap.potentialVulnerabilities.isNotEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    ShieldIcon()
                    Text(
                        text = "!",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
            }
        }
        
        if (ap.potentialVulnerabilities.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Divider(color = Color.DarkGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                ap.potentialVulnerabilities.take(2).forEach { vuln ->
                    Text(text = "• $vuln", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                }
            }
        }
    }
}
