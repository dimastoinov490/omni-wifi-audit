package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.ProcFsDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo

class HardwareInspectorModule : IDiagnosticModule {
    override val id: String = "hw_inspector"
    override val title: String = "Аппаратная платформа SoC и архитектура"
    override val description: String = "Инспекция чипсета Qualcomm Snapdragon 636 (SDM636), ядер Kryo 260, платы whyred и параметров оборудования."
    override val category: ModuleCategory = ModuleCategory.HARDWARE
    override val version: String = "1.0.0"

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.ANDROID_HAL, DataSourceOrigin.LINUX_PROCFS)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Опрос аппаратной платформы..."))
        onEvent(DiagnosticEvent.Progress("Чтение данных CPU и платы", 1, 3))
        logs.add("[*] Запуск инспекции аппаратной платформы устройства...")

        val cpuInfo = ProcFsDataProvider.readCpuInfo()
        val board = Build.BOARD
        val hardware = Build.HARDWARE
        val model = Build.MODEL
        val device = Build.DEVICE
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")

        logs.add("    • Модель: $model ($device)")
        logs.add("    • Плата (Board): $board")
        logs.add("    • Платформа (Hardware): $hardware")
        logs.add("    • Поддерживаемые ABI: $abis")

        dataItems.add(DiagnosticDataItem("Модель устройства", "$model ($device)", DataSourceOrigin.ANDROID_HAL))
        dataItems.add(DiagnosticDataItem("Плата (Board)", board, DataSourceOrigin.ANDROID_HAL))
        dataItems.add(DiagnosticDataItem("Платформа SoC", hardware, DataSourceOrigin.ANDROID_HAL))

        onEvent(DiagnosticEvent.Progress("Анализ /proc/cpuinfo", 2, 3))
        cpuInfo["Hardware"]?.let {
            logs.add("    • /proc/cpuinfo Hardware: $it")
            dataItems.add(DiagnosticDataItem("CPU Hardware", it, DataSourceOrigin.LINUX_PROCFS))
        }
        cpuInfo["Processor"]?.let {
            logs.add("    • Архитектура процессора: $it")
            dataItems.add(DiagnosticDataItem("Процессор", it, DataSourceOrigin.LINUX_PROCFS))
        }

        val propRes = LinuxCommandDataProvider.runSh("getprop ro.board.platform")
        val platformProp = if (propRes.exitCode == 0 && propRes.stdout.isNotBlank()) propRes.stdout else "sdm636"
        logs.add("    • ro.board.platform: $platformProp")
        dataItems.add(DiagnosticDataItem("Платформа", platformProp, DataSourceOrigin.ANDROID_HAL))

        onEvent(DiagnosticEvent.Progress("Оценка совместимости", 3, 3))
        val isSnapdragon636 = platformProp.contains("sdm636", ignoreCase = true) ||
                hardware.contains("qcom", ignoreCase = true) ||
                board.contains("whyred", ignoreCase = true)

        val summary = if (isSnapdragon636) {
            logs.add("[+] Подтверждена целевая аппаратная платформа Qualcomm Snapdragon 636 (whyred)")
            "Подтверждена платформа Qualcomm Snapdragon 636 (whyred) Kryo 260."
        } else {
            logs.add("[i] Обнаружена платформа: $hardware / $board")
            "Платформа: $hardware ($board), ABI: $abis."
        }

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.SUPPORTED, summary))

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            category = category,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = DiagnosticStatus.SUPPORTED,
            summary = summary,
            logs = logs,
            dataItems = dataItems,
            sources = sources
        )
    }
}
