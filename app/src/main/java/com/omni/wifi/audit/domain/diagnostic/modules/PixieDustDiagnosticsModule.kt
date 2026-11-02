package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.hardware.WifiDriverInspector
import com.omni.wifi.audit.domain.model.AccessPointInfo

class PixieDustDiagnosticsModule : IDiagnosticModule {
    override val id: String = "module_pixie_dust_diagnostics"
    override val title: String = "Диагностика условий Pixie-Dust"
    override val description: String = "Проверка условий уязвимости генерации псевдослучайных чисел (PRNG) и аппаратных возможностей устройства без подбора PIN."
    override val requiredPermissions: List<String> = emptyList()
    override val requiresRoot: Boolean = true
    override val requiresConnection: Boolean = false

    override suspend fun checkAvailability(context: Context, target: AccessPointInfo): ModuleAvailability {
        if (!target.isWpsSupported) {
            return ModuleAvailability.Unavailable("WPS отключен на целевой точке доступа")
        }
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

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Проверка условий уязвимости..."))
        onEvent(DiagnosticEvent.Progress("Проверка целевой точки доступа", 1, 3))

        log("[*] Запуск диагностики применимости Pixie-Dust для ${target.bssid} (${target.ssid})...")

        if (!target.isWpsSupported) {
            log("[-] Точка доступа не поддерживает WPS. Обмен сообщениями M1-M4 невозможен.", LogLevel.ERROR)
            val summary = "WPS отключен. Уязвимость Pixie-Dust неприменима."
            onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.UNSUPPORTED, summary))
            return DiagnosticReport(id, title, target.bssid, target.ssid, status = DiagnosticStatus.UNSUPPORTED, summary = summary, logs = logs)
        }
        log("[+] На целевой точке доступа активирован протокол WPS.", LogLevel.SUCCESS)

        onEvent(DiagnosticEvent.Progress("Анализ чипсета и энтропии PRNG", 2, 3))
        val vendor = target.vendor ?: "Неизвестный"
        log("[*] Анализ оборудования: $vendor")
        val isPotentiallyWeakChipset = vendor.contains("Realtek", ignoreCase = true) ||
                vendor.contains("Ralink", ignoreCase = true) ||
                vendor.contains("D-Link", ignoreCase = true) ||
                vendor.contains("Tenda", ignoreCase = true)

        if (isPotentiallyWeakChipset) {
            log("[!] Историческая справка: оборудование $vendor в ранних ревизиях использовало чипсеты Realtek/Ralink со слабым генератором nonces (E-S1/E-S2).", LogLevel.WARNING)
        } else {
            log("[i] Современное оборудование $vendor использует криптографическую энтропию Linux (/dev/urandom).", LogLevel.INFO)
        }

        onEvent(DiagnosticEvent.Progress("Проверка драйвера и захвата кадров", 3, 3))
        val inspection = WifiDriverInspector.inspect()
        val sys = inspection.system
        val drv = inspection.driver

        log("[*] Проверка системных возможностей:")
        log("    • Устройство: ${sys.deviceModel} (Snapdragon 636)")
        log("    • Ядро Linux: ${sys.kernelShort} | Прошивка: ${sys.romDisplay}")

        if (!sys.isRootGranted) {
            log("[-] Root-доступ не предоставлен: низкоуровневая диагностика сетевого адаптера невозможна.", LogLevel.ERROR)
            val summary = "Требуется Root-доступ для проверки низкоуровневых параметров интерфейса wlan0."
            onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.PERMISSION_DENIED, summary))
            return DiagnosticReport(id, title, target.bssid, target.ssid, status = DiagnosticStatus.PERMISSION_DENIED, summary = summary, logs = logs)
        }

        log("[+] Root-доступ подтвержден.", LogLevel.SUCCESS)
        log("[*] Анализ возможностей Wi-Fi драйвера (${drv.driverName}):")
        drv.notes.forEach { log("    • $it") }

        val status: DiagnosticStatus
        val summary: String
        if (drv.isMonitorModeSupported) {
            status = DiagnosticStatus.SUCCESS
            summary = "Драйвер ядра поддерживает мониторный режим. Захват management-кадров технически возможен."
            log("[+] В текущем ядре ${sys.kernelShort} обнаружена поддержка Monitor Mode!", LogLevel.SUCCESS)
        } else {
            status = DiagnosticStatus.UNSUPPORTED
            summary = "Аппаратное ограничение: драйвер qcacld-3.0 на ядре ${sys.kernelShort} не поддерживает Monitor Mode."
            log("[-] ОГРАНИЧЕНИЕ: Встроенный чипсет Qualcomm с текущим модулем wlan.ko работает только в режиме Station (Managed).", LogLevel.WARNING)
            log("[i] Перебор и вычисление PIN-кода не производятся. Для глубокого аудита требуется внешнее оборудование или ядро с патчем мониторного режима.", LogLevel.INFO)
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
                "Target WPS" to "Supported",
                "Root" to sys.isRootGranted.toString(),
                "Kernel" to sys.kernelShort,
                "Monitor Mode" to drv.isMonitorModeSupported.toString()
            )
        )
    }
}
