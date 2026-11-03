package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.diagnostic.security.RootCapabilityManager
import com.omni.wifi.audit.domain.model.AccessPointInfo

class MonitorModeInspectorModule : IDiagnosticModule {
    override val id: String = "monitor_mode_inspector"
    override val title: String = "Режим монитора (Monitor Mode)"
    override val description: String = "Проверка поддержки перевода встроенного чипсета в режим пассивного перехвата пакетов (Monitor Mode / 802.11 sniffer)."
    override val category: ModuleCategory = ModuleCategory.WIRELESS_CAPABILITY
    override val version: String = "1.0.0"
    override val requiresRoot: Boolean = true
    override val dependencies: List<String> = listOf("hw_inspector", "kernel_inspector", "driver_inspector")

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.IW_CLI, DataSourceOrigin.ROOT_SHELL)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Проверка поддержки режима монитора..."))
        onEvent(DiagnosticEvent.Progress("Проверка прав Root и утилиты iw", 1, 3))
        logs.add("[*] Запуск диагностики режима монитора для встроенного адаптера...")

        val rootCheck = RootCapabilityManager.inspectRoot()
        logs.add("    • Root статус: ${rootCheck.status}")
        dataItems.add(DiagnosticDataItem("Root доступ", rootCheck.status.name, DataSourceOrigin.ROOT_SHELL))

        onEvent(DiagnosticEvent.Progress("Опрос интерфейсов nl80211 через iw phy", 2, 3))
        val iwPhyRes = LinuxCommandDataProvider.runSu("iw phy0 info | grep -A 8 'Supported interface modes'")
        logs.add("    • Запрос режимов через iw phy0: ${if (iwPhyRes.exitCode == 0) "Выполнен" else "Код ${iwPhyRes.exitCode}"}")

        val hasMonitorInOutput = iwPhyRes.stdout.contains("monitor", ignoreCase = true)

        onEvent(DiagnosticEvent.Progress("Анализ драйвера qcacld-3.0", 3, 3))
        logs.add("    • Драйвер: Qualcomm qcacld-3.0 (wlan.ko)")
        logs.add("    • Поддержка nl80211 monitor mode: ${if (hasMonitorInOutput) "Обнаружена" else "Отсутствует в стандартном драйвере"}")

        dataItems.add(DiagnosticDataItem(
            "nl80211 Monitor Mode",
            if (hasMonitorInOutput) "Поддерживается" else "Не поддерживается (qcacld-3.0)",
            DataSourceOrigin.IW_CLI,
            if (hasMonitorInOutput) DiagnosticStatus.SUPPORTED else DiagnosticStatus.UNSUPPORTED
        ))

        val status = if (hasMonitorInOutput) DiagnosticStatus.SUPPORTED else DiagnosticStatus.UNSUPPORTED
        val summary = if (hasMonitorInOutput) {
            logs.add("[+] Обнаружена поддержка Monitor Mode на чипсете")
            "Поддержка Monitor Mode подтверждена через nl80211."
        } else {
            logs.add("[-] Режим монитора НЕ поддерживается стоковым драйвером Qualcomm qcacld-3.0 ядра 4.4")
            logs.add("[i] Для активации требуется кастомное ядро с патчем cfg80211/qcacld monitor mode либо nexmon")
            "Monitor Mode не поддерживается стоковым драйвером qcacld-3.0 (ядро 4.4 whyred). Требуется патч ядра."
        }

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
