package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.vulnerability.CveMatchingEngine

class CveAdvisoryModule : IDiagnosticModule {
    override val id: String = "module_cve_advisory"
    override val title: String = "База уязвимостей и CVE оборудования"
    override val description: String = "Сопоставление производителя и модели роутера с базой известных критических бюллетеней безопасности (NVD/CVE)."
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

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Поиск CVE записей в базе..."))
        onEvent(DiagnosticEvent.Progress("Запрос к локальной базе CVE", 1, 2))

        val vendor = target.vendor?.split(" ")?.firstOrNull() ?: ""
        val model = target.vendor?.split(" ")?.drop(1)?.joinToString(" ")

        log("[*] Поиск CVE уязвимостей для производителя: '$vendor'...")
        if (!model.isNullOrBlank()) {
            log("    • Модель устройства: '$model'")
        }

        val cveEngine = CveMatchingEngine(context)
        val matched = cveEngine.matchCves(vendor, model)

        onEvent(DiagnosticEvent.Progress("Анализ результатов", 2, 2))

        val status: DiagnosticStatus
        val summary: String

        if (matched.isNotEmpty()) {
            status = DiagnosticStatus.SUCCESS
            log("[!] Обнаружено зарегистрированных CVE записей: ${matched.size}", LogLevel.WARNING)
            matched.forEach { cve ->
                val matchType = if (cve.isExactModelMatch) "[ПОДТВЕРЖДЕНО ДЛЯ МОДЕЛИ]" else "[ОБОРУДОВАНИЕ ВЕНДОРА]"
                log("    • $matchType ${cve.cveId} (${cve.severity}): ${cve.description}")
                log("      Уязвимые версии: ${cve.affectedVersions}")
            }
            log("[i] Примечание: Уязвимость подтверждается при совпадении ревизии оборудования и версии прошивки.", LogLevel.INFO)
            summary = "Найдено записей CVE: ${matched.size} для вендора '$vendor'."
        } else {
            status = DiagnosticStatus.SUCCESS
            log("[OK] Критические зарегистрированные CVE для '$vendor' отсутствуют в текущей базе.", LogLevel.SUCCESS)
            summary = "Критических известных CVE для данного производителя в базе не обнаружено."
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
                "Vendor" to vendor,
                "CVE Count" to matched.size.toString()
            )
        )
    }
}
