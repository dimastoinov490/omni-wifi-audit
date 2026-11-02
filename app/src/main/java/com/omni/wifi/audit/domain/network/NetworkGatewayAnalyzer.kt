package com.omni.wifi.audit.domain.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.vulnerability.VulnerabilityDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

data class GatewayPortStatus(
    val port: Int,
    val service: String,
    val isOpen: Boolean,
    val latencyMs: Long
)

data class GatewayAuditReport(
    val isConnectedToTarget: Boolean,
    val targetBssid: String,
    val connectedBssid: String?,
    val gatewayIp: String?,
    val portStatuses: List<GatewayPortStatus>,
    val defaultCredentialsAdvisory: List<String>,
    val summary: String
)

class NetworkGatewayAnalyzer(private val context: Context) {
    private val vulnDb = VulnerabilityDatabase(context)

    suspend fun audit(
        target: AccessPointInfo,
        onLog: suspend (String) -> Unit
    ): GatewayAuditReport = withContext(Dispatchers.IO) {
        onLog("[*] Запуск NetworkGatewayAnalyzer...")

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifi) {
            onLog("[-] Устройство не подключено к Wi-Fi сети.")
            return@withContext GatewayAuditReport(
                isConnectedToTarget = false,
                targetBssid = target.bssid,
                connectedBssid = null,
                gatewayIp = null,
                portStatuses = emptyList(),
                defaultCredentialsAdvisory = emptyList(),
                summary = "Устройство не подключено к Wi-Fi сети. Сетевой аудит шлюза невозможен."
            )
        }

        val connInfo = wm?.connectionInfo
        val connectedBssid = connInfo?.bssid?.uppercase()
        val targetBssid = target.bssid.uppercase()

        val isTargetMatch = connectedBssid != null && (
                connectedBssid == targetBssid ||
                connectedBssid.replace(":", "") == targetBssid.replace(":", "")
        )

        if (!isTargetMatch) {
            onLog("[-] Устройство подключено к сети с BSSID: ${connectedBssid ?: "Неизвестен"}")
            onLog("[-] Целевой BSSID: $targetBssid. Подключение не к целевой точке доступа.")
            return@withContext GatewayAuditReport(
                isConnectedToTarget = false,
                targetBssid = target.bssid,
                connectedBssid = connectedBssid,
                gatewayIp = null,
                portStatuses = emptyList(),
                defaultCredentialsAdvisory = emptyList(),
                summary = "Устройство подключено к другой сети (${connectedBssid ?: "неизвестно"}). Требуется прямое подключение к ${target.ssid}."
            )
        }

        onLog("[+] Устройство активно подключено к целевой сети: ${target.ssid}")

        // Discover IPv4 Gateway
        var gatewayIp: String? = null
        try {
            val linkProps = cm.getLinkProperties(activeNet)
            linkProps?.routes?.forEach { route ->
                if (route.isDefaultRoute && route.gateway is Inet4Address) {
                    gatewayIp = route.gateway?.hostAddress
                }
            }
        } catch (_: Exception) {}

        if (gatewayIp == null) {
            val dhcp = wm.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                gatewayIp = String.format(
                    "%d.%d.%d.%d",
                    dhcp.gateway and 0xff,
                    dhcp.gateway shr 8 and 0xff,
                    dhcp.gateway shr 16 and 0xff,
                    dhcp.gateway shr 24 and 0xff
                )
            }
        }

        if (gatewayIp == null) {
            onLog("[-] Не удалось извлечь адрес основного шлюза (Default Gateway) из сетевого интерфейса.")
            return@withContext GatewayAuditReport(
                isConnectedToTarget = true,
                targetBssid = target.bssid,
                connectedBssid = connectedBssid,
                gatewayIp = null,
                portStatuses = emptyList(),
                defaultCredentialsAdvisory = emptyList(),
                summary = "IP-адрес шлюза не определен."
            )
        }

        onLog("[+] Основной шлюз обнаружен: $gatewayIp")

        // Probe real web management ports
        val portsToCheck = listOf(
            80 to "HTTP (Web Admin)",
            443 to "HTTPS (Web SSL)",
            8080 to "HTTP-Alt / Proxy",
            8000 to "HTTP-Management"
        )

        val portResults = mutableListOf<GatewayPortStatus>()
        for ((port, label) in portsToCheck) {
            onLog("[*] Проверка сокета: $gatewayIp:$port ($label)...")
            val startTime = System.currentTimeMillis()
            var isOpen = false
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(gatewayIp, port), 1200)
                    isOpen = true
                }
            } catch (_: Exception) {
                isOpen = false
            }
            val latency = System.currentTimeMillis() - startTime
            portResults.add(GatewayPortStatus(port, label, isOpen, latency))
            if (isOpen) {
                onLog("[+] ПОРТ ОТКРЫТ: $port ($label) — время отклика: ${latency}мс")
            } else {
                onLog("[-] Порт $port ($label) закрыт или фильтруется")
            }
        }

        // Default credentials from VulnerabilityDatabase
        val rawVendor = target.vendor?.split(" ")?.firstOrNull() ?: ""
        val creds = vulnDb.getCredentialsForVendor(rawVendor)
        val credStrings = creds.map { "${it.vendor} ${it.model}: ${it.username} / ${if (it.password.isEmpty()) "<пустой>" else it.password}" }

        if (credStrings.isNotEmpty()) {
            onLog("[!] База данных: Для производителя '$rawVendor' зарегистрированы стандартные учетные данные:")
            credStrings.forEach { onLog("    • $it") }
        }

        val openCount = portResults.count { it.isOpen }
        val summary = if (openCount > 0) {
            "Шлюз доступен по адресу $gatewayIp. Обнаружено открытых портов управления: $openCount."
        } else {
            "Шлюз доступен по адресу $gatewayIp, стандартные веб-порты закрыты."
        }

        GatewayAuditReport(
            isConnectedToTarget = true,
            targetBssid = target.bssid,
            connectedBssid = connectedBssid,
            gatewayIp = gatewayIp,
            portStatuses = portResults,
            defaultCredentialsAdvisory = credStrings,
            summary = summary
        )
    }
}
