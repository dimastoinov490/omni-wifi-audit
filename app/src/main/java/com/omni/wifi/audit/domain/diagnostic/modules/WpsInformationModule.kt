package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.ScanResultDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.WpsInformationElementDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.wps.WpsProtocolAnalyzer

class WpsInformationModule : IDiagnosticModule {
    override val id: String = "wps_info_module"
    override val title: String = "Параметры протокола WPS"
    override val description: String = "Парсинг TLV-атрибутов WPS: версия (WPS 1.0/2.0), статус блокировки (WPS Lockout), поддерживаемые методы сопряжения (PBC, PIN, Display)."
    override val category: ModuleCategory = ModuleCategory.ROUTER_INSPECTION
    override val version: String = "1.0.0"

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.SCAN_RESULT, DataSourceOrigin.WPS_IE)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Анализ WPS Information Elements..."))
        onEvent(DiagnosticEvent.Progress("Проверка наличия WPS в радиокадрах", 1, 3))
        logs.add("[*] Запуск детального анализа параметров протокола WPS...")

        val scanResult = ScanResultDataProvider.findSystemScanResult(context, target.bssid)
        val analysis = WpsProtocolAnalyzer.analyze(scanResult, target)
        val wpsData = WpsInformationElementDataProvider.parseWps(context, target)

        logs.add("    • Поддержка WPS точкой: ${if (analysis.isWpsSupported) "Да" else "Нет"}")
        dataItems.add(DiagnosticDataItem(
            "Поддержка WPS",
            if (analysis.isWpsSupported) "Да" else "Нет",
            DataSourceOrigin.SCAN_RESULT,
            if (analysis.isWpsSupported) DiagnosticStatus.SUPPORTED else DiagnosticStatus.UNSUPPORTED
        ))

        if (!analysis.isWpsSupported) {
            logs.add("[i] В радиокадрах Beacon/Probe Response отсутствует тег WPS (00:50:f2:04)")
            val summary = "WPS не поддерживается или отключен в настройках маршрутизатора."
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
                dataItems = dataItems,
                sources = sources
            )
        }

        onEvent(DiagnosticEvent.Progress("Парсинг TLV-атрибутов WPS", 2, 3))
        val wpsVersion = wpsData.version
        logs.add("    • Версия спецификации WPS: $wpsVersion")
        dataItems.add(DiagnosticDataItem("Версия WPS", wpsVersion, DataSourceOrigin.WPS_IE))

        val isLocked = wpsData.isLocked
        logs.add("    • Статус AP Setup Locked: ${if (isLocked) "ЗАБЛОКИРОВАНО (Lockout)" else "Разблокировано"}")
        dataItems.add(DiagnosticDataItem(
            "Статус блокировки WPS",
            if (isLocked) "Заблокировано (WPS Lock)" else "Разблокировано",
            DataSourceOrigin.WPS_IE,
            if (isLocked) DiagnosticStatus.WARNING else DiagnosticStatus.SUPPORTED
        ))

        val configMethodsStr = wpsData.configMethods.joinToString(", ").ifEmpty { "PBC, PIN" }
        logs.add("    • Метод конфигурирования: $configMethodsStr")
        dataItems.add(DiagnosticDataItem("Методы сопряжения", configMethodsStr, DataSourceOrigin.WPS_IE))

        onEvent(DiagnosticEvent.Progress("Оценка безопасности WPS", 3, 3))
        analysis.confirmedIndicators.forEach { ind ->
            logs.add("    • $ind")
        }
        analysis.protocolQuirks.forEach { quirk ->
            logs.add("    [!] $quirk")
        }

        val status = if (isLocked) DiagnosticStatus.WARNING else DiagnosticStatus.SUPPORTED
        val summary = "WPS $wpsVersion активен. Блокировка: ${if (isLocked) "ДА" else "НЕТ"}. Методы: $configMethodsStr."
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
