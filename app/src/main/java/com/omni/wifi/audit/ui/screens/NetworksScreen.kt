package com.omni.wifi.audit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NetworksScreen(
    scanResults: List<AccessPointInfo>,
    isScanning: Boolean,
    selectedAp: AccessPointInfo?,
    onAccessPointClick: (AccessPointInfo) -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentTime = remember(scanResults) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    val filteredList = remember(scanResults, searchQuery) {
        if (searchQuery.isBlank()) {
            scanResults
        } else {
            scanResults.filter {
                it.ssid.contains(searchQuery, ignoreCase = true) ||
                        it.bssid.contains(searchQuery, ignoreCase = true) ||
                        (it.vendor?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Heading matching HTML: ☰ Доступные сети  ⌕ ☷
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
                    text = "☰",
                    color = Color(0xFFC9C6ED),
                    fontSize = 22.sp
                )
                Text(
                    text = "Доступные сети",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isScanning) "⟳" else "☷",
                    color = if (isScanning) CyanAccent else Color(0xFFC9C6ED),
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { onRefresh() }
                )
            }
        }

        // Search bar: ⌕ Поиск сетей...
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF15162C))
                .border(1.dp, Color(0xFF34315B), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⌕　",
                    color = Color(0xFF9D9DBB),
                    fontSize = 15.sp
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(PurpleLight),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Поиск сетей...",
                                color = Color(0xFF9D9DBB),
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { searchQuery = "" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Networks List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (scanResults.isEmpty()) "Поиск доступных Wi-Fi сетей...\nНажмите кнопку обновления" else "Сети не найдены по запросу '$searchQuery'",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            items(filteredList, key = { it.bssid }) { ap ->
                val isSelected = selectedAp?.bssid == ap.bssid

                NetworkListItem(
                    accessPoint = ap,
                    isSelected = isSelected,
                    onClick = { onAccessPointClick(ap) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Note Bar: Найдено сетей: X | Обновлено: HH:mm ⟳
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF24233F))
                .border(1.dp, LineColor, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Найдено сетей: ${scanResults.size}",
                color = Color(0xFFDEDDF5),
                fontSize = 11.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { onRefresh() }
            ) {
                Text(
                    text = "Обновлено: $currentTime　⟳",
                    color = Color(0xFFDEDDF5),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun NetworkListItem(
    accessPoint: AccessPointInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isGoodSignal = accessPoint.signalLevel >= -65
    val signalColor = if (isGoodSignal) Color(0xFF3FE6B1) else Color(0xFFA58CFF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) SelectedCardBrush else CardBrush)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CardBorderActive else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // Signal Icon ◉
        Text(
            text = "◉",
            color = if (isSelected) Color(0xFFA88BFF) else signalColor,
            fontSize = 24.sp
        )

        // Network Information
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = accessPoint.ssid.ifBlank { "Скрытая сеть (${accessPoint.bssid.take(8)}..)" },
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "▣ ${accessPoint.signalLevel} dBm · ${accessPoint.band} · Канал ${accessPoint.channel}",
                color = Color(0xFFB0B1CE),
                fontSize = 11.sp
            )
        }

        // WPS Badge
        if (accessPoint.isWpsSupported) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, Color(0xFF1BE98C), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "WPS",
                    color = Color(0xFF31F49B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Selection / Action indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8C43F6)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "›",
                color = Color(0xFFB8B5D9),
                fontSize = 22.sp
            )
        }
    }
}
