package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.AndroidWifiDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.network.NetworkGatewayAnalyzer

class GatewayAuditModule : IDiagnosticModule {
    override val id: String = "gateway_audit_module"
    override val title: String = "Аудит сетевого шлюза и маршрутизатора"
    override val description: String = "Определение маршрута по умолчанию к шлюзу, доступности IP-адреса, открытых портов управления (80 HTTP, 443 HTTPS, 8080)."
    override val category: ModuleCategory = ModuleCategory.NETWORK_GATEWAY
    override val version: String = "1.0.0"
    override val requiresConnection: Boolean = true

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.ANDROID_WIFI_API, DataSourceOrigin.LOCAL_DATABASE)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Проверка сетевого соединения..."))
        onEvent(DiagnosticEvent.Progress("Опрос интерфейса маршрутизации", 1, 3))
        logs.add("[*] Запуск аудита сетевого шлюза...")

        val conn = AndroidWifiDataProvider.getConnectionInfo(context)
        if (!conn.isConnected) {
            logs.add("[-] Устройство не подключено к Wi-Fi сети")
            val summary = "Для сетевого аудита шлюза требуется активное подключение к целевой сети."
            onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.UNSUPPORTED, summary))
            return DiagnosticReport(
                moduleId = id,
                moduleTitle = title,
                category = category,
                targetBssid = target.bssid,
                targetSsid = target.ssid,
                status = DiagnosticStatus.UNSUPPORTED,
                summary = summary,
                logs = logs,
                dataItems = listOf(DiagnosticDataItem("Статус сети", "Не подключено", DataSourceOrigin.ANDROID_WIFI_API, DiagnosticStatus.UNSUPPORTED)),
                sources = sources
            )
        }

        logs.add("    • IP устройства: ${conn.ipAddress ?: "Не определен"}")
        logs.add("    • IP шлюза (Gateway): ${conn.gatewayAddress ?: "Не определен"}")
        dataItems.add(DiagnosticDataItem("IP адрес устройства", conn.ipAddress ?: "0.0.0.0", DataSourceOrigin.ANDROID_WIFI_API))
        dataItems.add(DiagnosticDataItem("IP адрес шлюза", conn.gatewayAddress ?: "0.0.0.0", DataSourceOrigin.ANDROID_WIFI_API))

        val isTarget = conn.bssid.equals(target.bssid, ignoreCase = true)
        if (!isTarget) {
            logs.add("[!] Устройство подключено к сети с BSSID: ${conn.bssid}, целевой BSSID: ${target.bssid}")
        }

        onEvent(DiagnosticEvent.Progress("Сканирование портов администрирования", 2, 3))
        val analyzer = NetworkGatewayAnalyzer(context)
        val report = analyzer.audit(target) { logLine ->
            logs.add(logLine)
        }

        for (port in report.portStatuses) {
            val status = if (port.isOpen) DiagnosticStatus.WARNING else DiagnosticStatus.SUPPORTED
            dataItems.add(DiagnosticDataItem("Порт ${port.port} (${port.service})", if (port.isOpen) "ОТКРЫТ (${port.latencyMs}мс)" else "Закрыт", DataSourceOrigin.ANDROID_WIFI_API, status))
        }

        onEvent(DiagnosticEvent.Progress("Формирование отчета безопасности", 3, 3))
        val anyOpen = report.portStatuses.any { it.isOpen }
        val status = if (anyOpen) DiagnosticStatus.WARNING else DiagnosticStatus.SUPPORTED
        val summary = "Шлюз: ${conn.gatewayAddress ?: "Определен"}. Открытых портов веб-интерфейса: ${report.portStatuses.count { it.isOpen }}."

        onEvent(DiagnosticEvent.StatusChange(status, summary))

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            category = category,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = status,
            summary = summary,
            logs = logs,
            dataItems = dataItems,
            sources = sources
        )
    }
}
