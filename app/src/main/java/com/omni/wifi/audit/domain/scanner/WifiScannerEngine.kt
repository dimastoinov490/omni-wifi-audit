package com.omni.wifi.audit.domain.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.router.RouterIdentificationEngine
import com.omni.wifi.audit.domain.vulnerability.CveMatchingEngine
import com.omni.wifi.audit.domain.wps.WpsInformationElementParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ScanTriggerResult {
    object Started : ScanTriggerResult()
    object Throttled : ScanTriggerResult()
    object WifiDisabled : ScanTriggerResult()
    data class PermissionRequired(val permissions: List<String>) : ScanTriggerResult()
    data class Error(val message: String) : ScanTriggerResult()
}

class WifiScannerEngine(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cveEngine = CveMatchingEngine(context)

    fun checkPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_WIFI_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        return missing
    }

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager.isWifiEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun triggerScan(): ScanTriggerResult {
        if (!isWifiEnabled()) {
            return ScanTriggerResult.WifiDisabled
        }

        val missingPermissions = checkPermissions()
        if (missingPermissions.isNotEmpty()) {
            return ScanTriggerResult.PermissionRequired(missingPermissions)
        }

        return try {
            val started = wifiManager.startScan()
            if (started) {
                ScanTriggerResult.Started
            } else {
                ScanTriggerResult.Throttled
            }
        } catch (e: Exception) {
            ScanTriggerResult.Error(e.localizedMessage ?: "Unknown scan error")
        }
    }

    suspend fun getScanResults(): List<AccessPointInfo> = withContext(Dispatchers.IO) {
        val rawResults = try {
            if (checkPermissions().isEmpty()) {
                wifiManager.scanResults ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        rawResults.map { scanResult ->
            parseScanResult(scanResult)
        }.sortedByDescending { it.signalLevel }
    }

    private suspend fun parseScanResult(scanResult: ScanResult): AccessPointInfo {
        val rawBssid = (scanResult.BSSID ?: "00:00:00:00:00:00").uppercase()
        val freq = scanResult.frequency
        val channel = WifiChannelAnalyzer.calculateChannel(freq)
        val band = WifiChannelAnalyzer.determineBand(freq)

        // 1. Parse WPS Information Elements
        val wpsInfo = WpsInformationElementParser.parseFromScanResult(scanResult)
        val hasWps = wpsInfo?.isSupported ?: scanResult.capabilities?.contains("WPS") == true

        // 2. Router Identification
        val routerId = RouterIdentificationEngine.identify(rawBssid, wpsInfo)

        // 3. Security & Encryption Type
        val caps = scanResult.capabilities ?: ""
        val encryption = when {
            caps.contains("SAE") && caps.contains("WPA2") -> "WPA2/WPA3 Transition"
            caps.contains("SAE") || caps.contains("WPA3") -> "WPA3-Personal"
            caps.contains("WPA2") && caps.contains("EAP") -> "WPA2-Enterprise"
            caps.contains("WPA2") -> "WPA2-PSK"
            caps.contains("WPA") -> "WPA-PSK"
            caps.contains("WEP") -> "WEP"
            caps.contains("OWE") -> "OWE (Enhanced Open)"
            else -> "Open"
        }

        // 4. Wi-Fi Generation Standard
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                when (scanResult.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                    ScanResult.WIFI_STANDARD_LEGACY -> "Legacy 802.11a/b/g"
                    else -> "802.11"
                }
            } catch (_: Exception) {
                "802.11"
            }
        } else {
            if (freq >= 5000) "Wi-Fi 5 (802.11ac)" else "Wi-Fi 4 (802.11n)"
        }

        // 5. Build WPS summary string
        val wpsDetails = if (hasWps) {
            when {
                wpsInfo?.isLocked == true -> "WPS активен [AP Setup Locked!]"
                wpsInfo != null -> "WPS v${wpsInfo.version} (${wpsInfo.configMethods.joinToString("/")})"
                caps.contains("WPS-PIN") -> "WPS (PIN-метод)"
                caps.contains("WPS-PBC") -> "WPS (Push Button)"
                else -> "WPS включен"
            }
        } else {
            "WPS отключен"
        }

        // 6. Potential vulnerabilities
        val vulns = mutableListOf<String>()
        if (encryption == "Open") {
            vulns.add("Открытая сеть: трафик не шифруется")
        } else if (encryption == "WEP") {
            vulns.add("Устаревший протокол WEP: критическая уязвимость RC4")
        }

        if (hasWps) {
            if (wpsInfo?.isLocked == true) {
                vulns.add("WPS: заблокирован роутером (AP Locked)")
            } else {
                vulns.add("WPS: активен (потенциальный вектор атак на согласование)")
            }
        }

        // Check matching CVEs
        val matchedCves = cveEngine.matchCves(routerId.vendor, routerId.model)
        matchedCves.take(3).forEach { cve ->
            val mark = if (cve.isExactModelMatch) "[Подтверждено]" else "[Вендор]"
            vulns.add("$mark ${cve.cveId} (${cve.severity}): ${cve.description}")
        }

        val displayName = if (routerId.model != null) {
            "${routerId.vendor} ${routerId.model}"
        } else {
            routerId.vendor
        }

        return AccessPointInfo(
            ssid = scanResult.SSID?.ifEmpty { "<Скрытая сеть>" } ?: "<Скрытая сеть>",
            bssid = rawBssid,
            signalLevel = scanResult.level,
            frequency = freq,
            channel = channel,
            band = band,
            capabilities = caps,
            wifiStandard = standard,
            encryptionType = encryption,
            isWpsSupported = hasWps,
            wpsDetails = wpsDetails,
            vendor = displayName,
            potentialVulnerabilities = vulns
        )
    }
}
