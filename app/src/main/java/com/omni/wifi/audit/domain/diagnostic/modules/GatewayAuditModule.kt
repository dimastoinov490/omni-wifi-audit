package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.network.NetworkGatewayAnalyzer

class GatewayAuditModule : IDiagnosticModule {
    override val id: String = "module_gateway_audit"
    override val title: String = "Аудит сетевого шлюза и веб-админки"
    override val description: String = "Определение IP-адреса шлюза, сканирование портов управления (80, 443, 8080) и сопоставление со стандартными учетными записями."
    override val requiredPermissions: List<String> = emptyList()
    override val requiresRoot: Boolean = false
    override val requiresConnection: Boolean = true

    override suspend fun checkAvailability(context: Context, target: AccessPointInfo): ModuleAvailability {
        val analyzer = NetworkGatewayAnalyzer(context)
        return ModuleAvailability.Available
    }

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()

        fun log(msg: String, level: LogLevel = LogLevel.INFO) {
            logs.add(msg)
        }

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Проверка сетевого подключения..."))
        onEvent(DiagnosticEvent.Progress("Проверка подключения к шлюзу", 1, 3))

        val analyzer = NetworkGatewayAnalyzer(context)
        val report = analyzer.audit(target) { line ->
            log(line)
        }

        onEvent(DiagnosticEvent.Progress("Анализ портов управления", 2, 3))

        val status: DiagnosticStatus
        if (!report.isConnectedToTarget) {
            status = DiagnosticStatus.UNSUPPORTED
            log("[-] Ограничение: Устройство не подключено к целевой сети ${target.ssid}.", LogLevel.WARNING)
        } else if (report.gatewayIp == null) {
            status = DiagnosticStatus.FAILED
            log("[-] IP-адрес шлюза не определен.", LogLevel.ERROR)
        } else {
            status = DiagnosticStatus.SUCCESS
            log("[+] Аудит шлюза ${report.gatewayIp} успешно завершен.", LogLevel.SUCCESS)
        }

        onEvent(DiagnosticEvent.Progress("Завершение аудита", 3, 3))
        onEvent(DiagnosticEvent.StatusChange(status, report.summary))

        val details = mutableMapOf<String, String>()
        report.gatewayIp?.let { details["Gateway IP"] = it }
        details["Connected"] = report.isConnectedToTarget.toString()
        val openPorts = report.portStatuses.filter { it.isOpen }.map { it.port }
        details["Open Ports"] = if (openPorts.isNotEmpty()) openPorts.joinToString(", ") else "None"

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = status,
            summary = report.summary,
            logs = logs,
            details = details
        )
    }
}
