package com.omni.wifi.audit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.ui.DetailScreen
import com.omni.wifi.audit.ui.MainScreen
import com.omni.wifi.audit.ui.theme.OmniWifiAuditTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Sample data for demonstration
        val sampleResults = listOf(
            AccessPointInfo(
                ssid = "TP-Link_Guest",
                bssid = "00:0C:42:11:22:33",
                signalLevel = -45,
                frequency = 2412,
                capabilities = "[WPA2-PSK-CCMP][WPS][ESS]",
                wifiStandard = "Wi-Fi 4",
                encryptionType = "WPA2",
                vendor = "TP-Link",
                potentialVulnerabilities = listOf("Default admin:admin credentials", "WPS Enabled", "CVE-2023-1234: High")
            ),
            AccessPointInfo(
                ssid = "Keenetic-7788",
                bssid = "28:28:5D:AA:BB:CC",
                signalLevel = -60,
                frequency = 5180,
                capabilities = "[WPA3-SAE-CCMP][ESS]",
                wifiStandard = "Wi-Fi 6",
                encryptionType = "WPA3",
                vendor = "Keenetic",
                potentialVulnerabilities = emptyList()
            ),
            AccessPointInfo(
                ssid = "D-Link_Old",
                bssid = "00:1E:58:FF:EE:DD",
                signalLevel = -80,
                frequency = 2437,
                capabilities = "[WEP][ESS]",
                wifiStandard = "Older",
                encryptionType = "WEP",
                vendor = "D-Link",
                potentialVulnerabilities = listOf("WEP is insecure", "CVE-2017-14491: High", "Default admin:password")
            )
        )

        setContent {
            OmniWifiAuditTheme {
                var selectedAp by remember { mutableStateOf<AccessPointInfo?>(null) }

                if (selectedAp == null) {
                    MainScreen(
                        scanResults = sampleResults,
                        onAccessPointClick = { ap -> selectedAp = ap }
                    )
                } else {
                    DetailScreen(
                        accessPoint = selectedAp!!,
                        onBack = { selectedAp = null }
                    )
                }
            }
        }
    }
}
