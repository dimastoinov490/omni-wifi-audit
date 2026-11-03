package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.vulnerability.CveMatchingEngine

class CveAdvisoryModule : IDiagnosticModule {
    override val id: String = "cve_advisory_module"
    override val title: String = "Анализ уязвимостей CVE"
    override val description: String = "Сопоставление идентифицированного производителя и модели роутера с базой известных уязвимостей безопасности (NVD / CVE)."
    override val category: ModuleCategory = ModuleCategory.VULNERABILITY
    override val version: String = "1.0.0"
    override val dependencies: List<String> = listOf("router_id_module")

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.LOCAL_DATABASE)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Поиск известных CVE уязвимостей..."))
        onEvent(DiagnosticEvent.Progress("Определение модели маршрутизатора", 1, 3))
        logs.add("[*] Запуск модуля проверки базы CVE...")

        val vendor = target.vendor ?: "Неизвестный вендор"
        logs.add("    • Целевой вендор: $vendor | SSID: ${target.ssid}")
        dataItems.add(DiagnosticDataItem("Целевой вендор", vendor, DataSourceOrigin.LOCAL_DATABASE))

        onEvent(DiagnosticEvent.Progress("Запрос локальной базы CVE", 2, 3))
        val engine = CveMatchingEngine(context)
        val matchedCves = engine.matchCves(vendor, null)

        logs.add("    • Найдено зарегистрированных CVE: ${matchedCves.size}")
        dataItems.add(DiagnosticDataItem("Количество найденных CVE", matchedCves.size.toString(), DataSourceOrigin.LOCAL_DATABASE))

        onEvent(DiagnosticEvent.Progress("Оценка уровней критичности CVSS", 3, 3))
        for (cve in matchedCves.take(5)) {
            val prefix = if (cve.severity.contains("CRITICAL", ignoreCase = true) || cve.severity.contains("HIGH", ignoreCase = true)) "[!]" else "[i]"
            logs.add("$prefix ${cve.cveId} [${cve.severity}]: ${cve.description.take(80)}...")
            dataItems.add(DiagnosticDataItem(
                cve.cveId,
                cve.severity,
                DataSourceOrigin.LOCAL_DATABASE,
                if (cve.severity.contains("HIGH", ignoreCase = true) || cve.severity.contains("CRITICAL", ignoreCase = true)) DiagnosticStatus.WARNING else DiagnosticStatus.SUPPORTED
            ))
        }

        val hasHighOrCritical = matchedCves.any { it.severity.contains("HIGH", ignoreCase = true) || it.severity.contains("CRITICAL", ignoreCase = true) }
        val status = if (hasHighOrCritical) {
            DiagnosticStatus.WARNING
        } else if (matchedCves.isNotEmpty()) {
            DiagnosticStatus.PARTIAL
        } else {
            DiagnosticStatus.SUPPORTED
        }

        val summary = if (matchedCves.isNotEmpty()) {
            "Обнаружено ${matchedCves.size} потенциальных уязвимостей CVE для $vendor."
        } else {
            "В базе данных не обнаружено известных критических CVE для $vendor."
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
