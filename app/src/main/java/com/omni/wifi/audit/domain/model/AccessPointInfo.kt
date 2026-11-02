package com.omni.wifi.audit.domain.model

data class AccessPointInfo(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val frequency: Int,
    val channel: Int,
    val band: String, // 2.4 GHz, 5 GHz, 6 GHz
    val capabilities: String,
    val wifiStandard: String, // Wi-Fi 4/5/6/7
    val encryptionType: String, // Open, WEP, WPA, WPA2, WPA3
    val isWpsSupported: Boolean = false,
    val wpsDetails: String = "",
    val vendor: String? = null,
    val wpsAlgorithms: List<String> = emptyList(),
    val potentialVulnerabilities: List<String> = emptyList()
)
