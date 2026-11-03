package com.omni.wifi.audit.domain.diagnostic.providers

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.omni.wifi.audit.domain.model.AccessPointInfo

data class ApRadioInspection(
    val bssid: String,
    val ssid: String,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val rssiDbm: Int,
    val capabilities: String,
    val standard: String
)

object ScanResultDataProvider {

    fun inspectAccessPoint(target: AccessPointInfo): ApRadioInspection {
        return ApRadioInspection(
            bssid = target.bssid,
            ssid = target.ssid,
            frequencyMhz = target.frequency,
            channel = target.channel,
            band = target.band,
            rssiDbm = target.signalLevel,
            capabilities = target.capabilities,
            standard = target.wifiStandard
        )
    }

    @Suppress("DEPRECATION")
    fun findSystemScanResult(context: Context, bssid: String): ScanResult? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        return try {
            wm.scanResults?.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
    }
}
