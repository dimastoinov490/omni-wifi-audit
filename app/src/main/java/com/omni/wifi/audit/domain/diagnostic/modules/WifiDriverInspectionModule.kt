package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.hardware.WifiDriverInspector
import com.omni.wifi.audit.domain.model.AccessPointInfo

class WifiDriverInspectionModule : IDiagnosticModule {
    override val id: String = "module_wifi_driver_inspection"
    override val title: String = "Аудит Wi-Fi чипсета и драйвера"
    override val description: String = "Исследование аппаратной платформы, драйвера ядра qcacld-3.0, интерфейса wlan0 и поддерживаемых беспроводных режимов."
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

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Опрос аппаратной подсистемы..."))
        onEvent(DiagnosticEvent.Progress("Опрос ядра и системы", 1, 3))

        val result = WifiDriverInspector.inspect()
        val sys = result.system
        val drv = result.driver

        log("[*] Аппаратная платформа и операционная система:")
        log("    • Модель: ${sys.manufacturer} ${sys.deviceModel}")
        log("    • ОС: Android ${sys.androidVersion} (API ${sys.apiLevel}) | Прошивка: ${sys.romDisplay}")
        log("    • Ядро Linux: ${sys.kernelFull}")
        log("    • Режим SELinux: ${sys.selinuxMode}")

        onEvent(DiagnosticEvent.Progress("Проверка драйвера беспроводной связи", 2, 3))
        log("[*] Беспроводная подсистема:")
        log("    • Чипсет: ${drv.chipset}")
        log("    • Драйвер: ${drv.driverName} (модуль: ${drv.kernelModule})")
        log("    • Сетевой интерфейс: ${drv.interfaceName} (состояние: ${if (drv.isInterfaceUp) "UP" else "DOWN"})")
        log("    • Поддерживаемые режимы: ${drv.supportedModes.joinToString(", ")}")

        onEvent(DiagnosticEvent.Progress("Анализ специальных возможностей", 3, 3))
        drv.notes.forEach { note ->
            log("    • $note")
        }

        val summary = "Чипсет: ${drv.chipset}, Драйвер: ${drv.driverName}. Режимы: ${drv.supportedModes.joinToString(", ")}."
        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.SUCCESS, summary))

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = DiagnosticStatus.SUCCESS,
            summary = summary,
            logs = logs,
            details = mapOf(
                "Driver" to drv.driverName,
                "Chipset" to drv.chipset,
                "Interface" to drv.interfaceName,
                "Monitor Mode" to drv.isMonitorModeSupported.toString(),
                "Root" to sys.isRootGranted.toString()
            )
        )
    }
}
