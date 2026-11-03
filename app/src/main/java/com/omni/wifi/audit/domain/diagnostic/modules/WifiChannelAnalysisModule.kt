package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.scanner.CongestionLevel
import com.omni.wifi.audit.domain.scanner.WifiChannelAnalyzer

class WifiChannelAnalysisModule : IDiagnosticModule {
    override val id: String = "channel_analysis_module"
    override val title: String = "Анализ радиоканала и эфира"
    override val description: String = "Анализ частотного диапазона, рабочего радиоканала, межканальной интерференции (ACI/CCI) и уровня сигнала."
    override val category: ModuleCategory = ModuleCategory.WIRELESS_CAPABILITY
    override val version: String = "1.0.0"

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.SCAN_RESULT, DataSourceOrigin.ANDROID_WIFI_API)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Выполняется анализ эфира..."))
        onEvent(DiagnosticEvent.Progress("Расчет характеристик радиоканала", 1, 3))

        logs.add("[*] Запуск модуля анализа радиоканала...")
        logs.add("    • BSSID: ${target.bssid} | SSID: ${target.ssid}")
        logs.add("    • Частота: ${target.frequency} МГц | Диапазон: ${target.band}")
        logs.add("    • Канал: ${target.channel}")

        dataItems.add(DiagnosticDataItem("Частота", "${target.frequency} МГц", DataSourceOrigin.SCAN_RESULT))
        dataItems.add(DiagnosticDataItem("Диапазон", target.band, DataSourceOrigin.SCAN_RESULT))
        dataItems.add(DiagnosticDataItem("Канал", target.channel.toString(), DataSourceOrigin.SCAN_RESULT))

        onEvent(DiagnosticEvent.Progress("Оценка интерференции", 2, 3))
        val result = WifiChannelAnalyzer.analyze(target, allAccessPoints)

        logs.add("    • Качество сигнала: ${target.signalLevel} дБм (${result.signalQuality})")
        logs.add("    • Точек на том же канале (${result.channel}): ${result.sameChannelApCount}")
        dataItems.add(DiagnosticDataItem("Уровень сигнала", "${target.signalLevel} дБм (${result.signalQuality})", DataSourceOrigin.SCAN_RESULT))
        dataItems.add(DiagnosticDataItem("Ко-канальных сетей (CCI)", result.sameChannelApCount.toString(), DataSourceOrigin.SCAN_RESULT))

        if (result.band == "2.4 GHz") {
            logs.add("    • Точек на соседних пересекающихся каналах: ${result.adjacentChannelApCount}")
            dataItems.add(DiagnosticDataItem("Смежных сетей (ACI)", result.adjacentChannelApCount.toString(), DataSourceOrigin.SCAN_RESULT))
            if (result.isNonOverlapping24) {
                logs.add("[+] Точка настроена на стандартный непересекающийся канал (${result.channel})")
            } else {
                logs.add("[!] ВНИМАНИЕ: Канал ${result.channel} вызывает межканальную интерференцию (ACI) с каналами 1, 6 и 11")
            }
        }

        result.recommendations.forEach { rec ->
            logs.add("[i] Рекомендация: $rec")
        }

        onEvent(DiagnosticEvent.Progress("Формирование отчета", 3, 3))
        val summary = "Канал ${result.channel} (${result.band}). Загрузка: ${result.congestionLevel.name}. Сигнал: ${target.signalLevel} дБм."
        val isCongested = result.congestionLevel == CongestionLevel.HIGH || result.congestionLevel == CongestionLevel.SEVERE
        val status = if (isCongested || (!result.isNonOverlapping24 && result.band == "2.4 GHz")) {
            DiagnosticStatus.WARNING
        } else {
            DiagnosticStatus.SUPPORTED
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
