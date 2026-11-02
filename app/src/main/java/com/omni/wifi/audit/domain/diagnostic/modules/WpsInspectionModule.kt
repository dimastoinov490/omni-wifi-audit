package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.hardware.DeviceCapabilityManager
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.wps.WpsProtocolAnalyzer

class WpsInspectionModule : IDiagnosticModule {
    override val id: String = "module_wps_inspector"
    override val title: String = "WPS Протокольный Инспектор"
    override val description: String = "Глубокий разбор параметров WPS из 802.11 Beacon: версия, состояние, статус блокировки (Lockout), методы аутентификации."
    override val requiredPermissions: List<String> = emptyList()
    override val requiresRoot: Boolean = false
    override val requiresConnection: Boolean = false

    override suspend fun checkAvailability(context: Context, target: AccessPointInfo): ModuleAvailability {
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

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Анализ WPS параметров..."))
        onEvent(DiagnosticEvent.Progress("Парсинг Information Elements", 1, 3))

        log("[*] Запуск WPS Protocol Analyzer для ${target.bssid} (${target.ssid})...")
        log("    • Анонсированные возможности: ${target.capabilities}")

        val report = WpsProtocolAnalyzer.analyze(null, target)

        onEvent(DiagnosticEvent.Progress("Интерпретация протокола", 2, 3))
        log("[*] Подтвержденные признаки:")
        report.confirmedIndicators.forEach { log("    [+] $it", LogLevel.SUCCESS) }

        if (report.unconfirmedIndicators.isNotEmpty()) {
            log("[*] Неподтвержденные признаки:")
            report.unconfirmedIndicators.forEach { log("    [-] $it", LogLevel.INFO) }
        }

        if (report.protocolQuirks.isNotEmpty()) {
            log("[*] Особенности реализации в оборудовании:")
            report.protocolQuirks.forEach { log("    [i] $it", LogLevel.WARNING) }
        }

        onEvent(DiagnosticEvent.Progress("Проверка системных ограничений", 3, 3))
        val sys = DeviceCapabilityManager.inspect()
        log("[*] Проверка системного стека устройства:")
        log("    • Android ${sys.androidVersion} (API ${sys.apiLevel}) | Ядро: ${sys.kernelShort}")
        if (sys.isSupplicantSocketRestricted) {
            log("    [i] Ограничение Android 9+: Прямой доступ к сокету wpa_supplicant заблокирован SELinux.")
        }
        if (sys.isRootGranted) {
            log("    [+] Доступ к root-шеллу подтвержден.")
        } else {
            log("    [-] Root-доступ отсутствует.")
        }

        val status = if (report.isWpsSupported) DiagnosticStatus.SUCCESS else DiagnosticStatus.UNSUPPORTED
        val summary = if (report.isWpsSupported) {
            "WPS активен. Версия: ${report.wpsVersion}. Lockout: ${if (report.isLocked) "Заблокирован" else "Открыт"}."
        } else {
            "WPS отключен на целевой точке доступа."
        }

        onEvent(DiagnosticEvent.StatusChange(status, summary))

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = status,
            summary = summary,
            logs = logs,
            details = mapOf(
                "WPS Supported" to report.isWpsSupported.toString(),
                "WPS Version" to report.wpsVersion,
                "Is Locked" to report.isLocked.toString(),
                "State" to report.state.name
            )
        )
    }
}
