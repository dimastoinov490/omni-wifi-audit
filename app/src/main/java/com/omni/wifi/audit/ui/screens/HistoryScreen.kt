package com.omni.wifi.audit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.diagnostic.DiagnosticReport
import com.omni.wifi.audit.domain.diagnostic.DiagnosticResultRepository
import com.omni.wifi.audit.ui.OriginChip
import com.omni.wifi.audit.ui.StatusBadge
import com.omni.wifi.audit.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onSelectReport: (DiagnosticReport) -> Unit = {}
) {
    val reports by DiagnosticResultRepository.reportsFlow.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    text = "▤",
                    color = Color(0xFFA65AFF),
                    fontSize = 22.sp
                )
                Text(
                    text = "История проверок",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (reports.isNotEmpty()) {
                Text(
                    text = "Очистить",
                    color = Color(0xFFFF8787),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { DiagnosticResultRepository.clear() }
                )
            }
        }

        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "▤",
                        color = Color(0xFF3B3472),
                        fontSize = 48.sp
                    )
                    Text(
                        text = "История диагностик пуста",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Запустите проверку модулей на экране сети\nили во вкладке 'Диагностика'",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports) { report ->
                    HistoryReportCard(
                        report = report,
                        formattedTime = dateFormat.format(Date(report.timestamp)),
                        onClick = { onSelectReport(report) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun HistoryReportCard(
    report: DiagnosticReport,
    formattedTime: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBrush)
            .border(1.dp, CardBorderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.moduleTitle,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Сеть: ${report.targetSsid} (${report.targetBssid})",
                    color = CyanAccent,
                    fontSize = 11.sp
                )
            }
            StatusBadge(status = report.status)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = report.summary,
            color = Color(0xFFC0BFE0),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        // Data Items with Origin badges
        if (report.dataItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                report.dataItems.take(3).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "• ${item.key}: ${item.value}", color = TextMuted, fontSize = 10.sp)
                        OriginChip(origin = item.origin)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = formattedTime,
            color = Color(0xFF6F718F),
            fontSize = 10.sp
        )
    }
}
