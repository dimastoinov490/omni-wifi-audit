package com.omni.wifi.audit.domain.diagnostic.providers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CurrentWifiConnectionInfo(
    val isConnected: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
    val ipAddress: String? = null,
    val gatewayAddress: String? = null,
    val linkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0
)

object AndroidWifiDataProvider {

    fun isWifiEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.isWifiEnabled == true
    }

    @Suppress("DEPRECATION")
    fun getConnectionInfo(context: Context): CurrentWifiConnectionInfo {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return CurrentWifiConnectionInfo(isConnected = false)

        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val hasWifiCap = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!hasWifiCap) {
            return CurrentWifiConnectionInfo(isConnected = false)
        }

        val info: WifiInfo? = wm.connectionInfo
        val dhcp = wm.dhcpInfo

        val ipStr = if (dhcp != null && dhcp.ipAddress != 0) intToIp(dhcp.ipAddress) else null
        val gatewayStr = if (dhcp != null && dhcp.gateway != 0) intToIp(dhcp.gateway) else null

        val ssid = info?.ssid?.removePrefix("\"")?.removeSuffix("\"")
        val bssid = info?.bssid

        return CurrentWifiConnectionInfo(
            isConnected = true,
            ssid = ssid,
            bssid = bssid,
            ipAddress = ipStr,
            gatewayAddress = gatewayStr,
            linkSpeedMbps = info?.linkSpeed ?: 0,
            frequencyMhz = info?.frequency ?: 0
        )
    }

    private fun intToIp(ip: Int): String {
        return try {
            val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ip).array()
            InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }
}
