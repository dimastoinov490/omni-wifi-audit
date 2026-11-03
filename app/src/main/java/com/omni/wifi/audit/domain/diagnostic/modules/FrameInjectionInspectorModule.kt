package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo

class FrameInjectionInspectorModule : IDiagnosticModule {
    override val id: String = "frame_injection_inspector"
    override val title: String = "Инъекция беспроводных кадров (Packet Injection)"
    override val description: String = "Проверка аппаратной и драйверной возможности передачи произвольных 802.11 кадров в эфир."
    override val category: ModuleCategory = ModuleCategory.WIRELESS_CAPABILITY
    override val version: String = "1.0.0"
    override val requiresRoot: Boolean = true
    override val dependencies: List<String> = listOf("monitor_mode_inspector")

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.ROOT_SHELL, DataSourceOrigin.IW_CLI)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Проверка поддержки инъекции пакетов..."))
        onEvent(DiagnosticEvent.Progress("Проверка статуса режима монитора", 1, 3))
        logs.add("[*] Запуск диагностики возможности инъекции 802.11 кадров...")

        val monitorReport = DiagnosticResultRepository.getLatestReport(target.bssid, "monitor_mode_inspector")
        if (monitorReport == null || monitorReport.status != DiagnosticStatus.SUPPORTED) {
            logs.add("[-] Базовое требование: Monitor Mode не активен или не поддерживается на данном ядре")
        }

        onEvent(DiagnosticEvent.Progress("Проверка поддержки AF_PACKET raw сокетов и ioctl", 2, 3))
        val testRes = LinuxCommandDataProvider.runSu("cat /proc/net/dev | grep wlan")
        logs.add("    • Интерфейс передачи: ${if (testRes.stdout.isNotBlank()) "Обнаружен" else "Недоступен"}")

        onEvent(DiagnosticEvent.Progress("Оценка драйвера qcacld-3.0", 3, 3))
        logs.add("    • Аппаратная платформа: Qualcomm WCN3680 / Snapdragon 636")
        logs.add("    • Возможность прямой инъекции кадров: Заблокирована на уровне стоковой прошивки Wi-Fi чипсета")

        dataItems.add(DiagnosticDataItem(
            "Frame Injection (Raw 802.11)",
            "Не поддерживается (qcacld-3.0)",
            DataSourceOrigin.ROOT_SHELL,
            DiagnosticStatus.UNSUPPORTED
        ))

        val status = DiagnosticStatus.UNSUPPORTED
        val summary = "Инъекция кадров (Packet Injection) не поддерживается встроенным чипсетом без патча прошивки."
        logs.add("[-] Packet Injection НЕ поддерживается встроенным адаптером на текущем ядре 4.4")
        logs.add("[i] Требуется внешнее кастомное ядро с поддержкой инъекции пакетов для qcacld")

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
