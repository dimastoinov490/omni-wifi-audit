package com.omni.wifi.audit.domain.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
    object LocationDisabled : ScanTriggerResult()
    data class PermissionRequired(val permissions: List<String>) : ScanTriggerResult()
    data class Error(val message: String) : ScanTriggerResult()
}

class WifiScannerEngine(private val context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val cveEngine = CveMatchingEngine(appContext)

    /** Runtime permission needed for obtaining nearby scan results. */
    fun checkPermissions(): List<String> = buildList {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun isWifiEnabled(): Boolean = try {
        wifiManager?.isWifiEnabled == true
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    fun triggerScan(): ScanTriggerResult {
        if (wifiManager == null) return ScanTriggerResult.Error("Wi-Fi service is unavailable")
        if (!isWifiEnabled()) return ScanTriggerResult.WifiDisabled
        val missingPermissions = checkPermissions()
        if (missingPermissions.isNotEmpty()) return ScanTriggerResult.PermissionRequired(missingPermissions)
        if (!isLocationEnabled()) return ScanTriggerResult.LocationDisabled

        return try {
            if (wifiManager.startScan()) ScanTriggerResult.Started else ScanTriggerResult.Throttled
        } catch (e: SecurityException) {
            ScanTriggerResult.PermissionRequired(checkPermissions().ifEmpty { listOf(Manifest.permission.ACCESS_FINE_LOCATION) })
        } catch (e: RuntimeException) {
            ScanTriggerResult.Error(e.localizedMessage ?: "Android could not start a Wi-Fi scan")
        }
    }

    suspend fun getScanResults(): List<AccessPointInfo> = withContext(Dispatchers.IO) {
        val manager = wifiManager ?: return@withContext emptyList()
        if (checkPermissions().isNotEmpty()) return@withContext emptyList()
        val rawResults = try {
            manager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }

        rawResults.mapNotNull { result ->
            try {
                parseScanResult(result)
            } catch (_: RuntimeException) {
                null
            }
        }.distinctBy { it.bssid.lowercase() }.sortedByDescending { it.signalLevel }
    }

    private fun parseScanResult(scanResult: ScanResult): AccessPointInfo {
        val rawBssid = (scanResult.BSSID ?: "00:00:00:00:00:00").uppercase()
        val freq = scanResult.frequency
        val channel = WifiChannelAnalyzer.calculateChannel(freq)
        val band = WifiChannelAnalyzer.determineBand(freq)
        val wpsInfo = WpsInformationElementParser.parseFromScanResult(scanResult)
        val caps = scanResult.capabilities.orEmpty()
        val hasWps = wpsInfo?.isSupported ?: caps.contains("WPS", ignoreCase = true)
        val routerId = RouterIdentificationEngine.identify(rawBssid, wpsInfo)
        val encryption = when {
            caps.contains("SAE", true) && caps.contains("WPA2", true) -> "WPA2/WPA3 Transition"
            caps.contains("SAE", true) || caps.contains("WPA3", true) -> "WPA3-Personal"
            caps.contains("WPA2", true) && caps.contains("EAP", true) -> "WPA2-Enterprise"
            caps.contains("WPA2", true) -> "WPA2-PSK"
            caps.contains("WPA", true) -> "WPA-PSK"
            caps.contains("WEP", true) -> "WEP"
            caps.contains("OWE", true) -> "OWE (Enhanced Open)"
            else -> "Open"
        }
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                when (scanResult.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                    ScanResult.WIFI_STANDARD_LEGACY -> "Legacy 802.11a/b/g"
                    else -> "802.11 (не определен)"
                }
            } catch (_: RuntimeException) { "802.11 (не определен)" }
        } else if (freq >= 5000) "802.11a/ac (оценка по частоте)" else "802.11b/g/n (оценка по частоте)"

        val wpsDetails = when {
            !hasWps -> "WPS отключен или не объявлен в данных сканирования"
            wpsInfo?.isLocked == true -> "WPS объявлен; точка сообщает блокировку настройки"
            wpsInfo != null -> "WPS v${wpsInfo.version} (${wpsInfo.configMethods.joinToString("/")})"
            else -> "WPS объявлен; детали информационного элемента недоступны"
        }
        val vulnerabilities = buildList {
            if (encryption == "Open") add("Открытая сеть: точка не объявляет шифрование; это не подтверждает доступность сети или уязвимость клиента")
            if (encryption == "WEP") add("Объявлен устаревший WEP; рекомендуется отключить его")
            if (hasWps) add("Точка объявляет поддержку WPS; активность протокола и эксплуатируемость этим сканированием не проверяются")
            cveEngine.matchCves(routerId.vendor, routerId.model).take(3).forEach { cve ->
                val matchLabel = if (cve.isExactModelMatch) "Совпадение модели" else "Совпадение вендора; модель/версия не подтверждены"
                add("[$matchLabel] ${cve.cveId} (${cve.severity}): ${cve.description}")
            }
        }
        val displayName = routerId.model?.let { "${routerId.vendor} $it" } ?: routerId.vendor

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
            potentialVulnerabilities = vulnerabilities
        )
    }
}
