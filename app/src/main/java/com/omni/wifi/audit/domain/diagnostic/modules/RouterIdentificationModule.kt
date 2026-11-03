package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.DatabaseDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.WpsInformationElementDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.router.RouterIdentificationEngine

class RouterIdentificationModule : IDiagnosticModule {
    override val id: String = "router_id_module"
    override val title: String = "Идентификация производителя и модели"
    override val description: String = "Многоуровневая детекция вендора, семейства и модели маршрутизатора по WPS Information Elements (0xDD) и OUI MAC-адреса."
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
        val sources = mutableListOf<DataSourceOrigin>()

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Определение оборудования роутера..."))
        onEvent(DiagnosticEvent.Progress("Анализ WPS Information Elements", 1, 3))
        logs.add("[*] Запуск идентификации оборудования точки доступа...")
        logs.add("    • BSSID: ${target.bssid} | SSID: ${target.ssid}")

        // 1. Check WPS IE data
        val wpsData = WpsInformationElementDataProvider.parseWps(context, target)
        if (wpsData.manufacturer != null || wpsData.modelName != null) {
            sources.add(DataSourceOrigin.WPS_IE)
            logs.add("[+] Обнаружены данные WPS Information Element (00:50:f2:04):")
            wpsData.manufacturer?.let {
                logs.add("    • Производитель (Manufacturer): $it")
                dataItems.add(DiagnosticDataItem("Производитель (WPS)", it, DataSourceOrigin.WPS_IE))
            }
            wpsData.modelName?.let {
                logs.add("    • Название модели (Model Name): $it")
                dataItems.add(DiagnosticDataItem("Модель (WPS)", it, DataSourceOrigin.WPS_IE))
            }
            wpsData.modelNumber?.let {
                logs.add("    • Номер модели (Model Number): $it")
                dataItems.add(DiagnosticDataItem("Номер модели (WPS)", it, DataSourceOrigin.WPS_IE))
            }
            wpsData.deviceName?.let {
                logs.add("    • Имя устройства (Device Name): $it")
                dataItems.add(DiagnosticDataItem("Имя устройства", it, DataSourceOrigin.WPS_IE))
            }
        }

        onEvent(DiagnosticEvent.Progress("Поиск по IEEE OUI базе данных", 2, 3))
        val ouiVendor = DatabaseDataProvider.lookupVendor(context, target.bssid)
        if (ouiVendor != null) {
            sources.add(DataSourceOrigin.LOCAL_DATABASE)
            logs.add("[+] Поиск по базе OUI: $ouiVendor")
            dataItems.add(DiagnosticDataItem("Вендор по OUI", ouiVendor, DataSourceOrigin.LOCAL_DATABASE))
        } else {
            logs.add("    • OUI вендор не найден в локальной базе данных")
        }

        onEvent(DiagnosticEvent.Progress("Сопоставление сигнатур оборудования", 3, 3))
        val engineMatch = RouterIdentificationEngine.identify(target.bssid, wpsData)
        logs.add("    • Достоверность детекции: ${engineMatch.confidence}")
        logs.add("    • Итоговое определение: ${engineMatch.vendor} ${engineMatch.model ?: ""}")
        dataItems.add(DiagnosticDataItem("Итоговый вендор", engineMatch.vendor, DataSourceOrigin.LOCAL_DATABASE))
        engineMatch.model?.let {
            dataItems.add(DiagnosticDataItem("Итоговая модель", it, DataSourceOrigin.LOCAL_DATABASE))
        }
        dataItems.add(DiagnosticDataItem("Уровень достоверности", engineMatch.confidence.name, DataSourceOrigin.LOCAL_DATABASE))

        val isIdentified = engineMatch.vendor != "Неизвестный вендор" && engineMatch.vendor.isNotBlank()
        val status = if (isIdentified) DiagnosticStatus.SUPPORTED else DiagnosticStatus.UNKNOWN
        val summary = if (isIdentified) {
            "Оборудование: ${engineMatch.vendor} ${engineMatch.model ?: ""} (${engineMatch.confidence})."
        } else {
            "Оборудование не удалось однозначно идентифицировать по сигнатурам."
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
            sources = sources.distinct()
        )
    }
}
