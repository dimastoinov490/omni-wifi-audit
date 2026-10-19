package com.omni.wifi.audit.domain.model

data class AccessPointInfo(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val frequency: Int,
    val capabilities: String,
    val wifiStandard: String, // Wi-Fi 4/5/6/7
    val encryptionType: String, // WEP, WPA2, WPA3, Transition
    val vendor: String? = null,
    val wpsAlgorithms: List<String> = emptyList(),
    val potentialVulnerabilities: List<String> = emptyList()
)
