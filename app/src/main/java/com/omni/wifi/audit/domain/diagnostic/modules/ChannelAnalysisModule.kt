package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.scanner.WifiChannelAnalyzer

class ChannelAnalysisModule : IDiagnosticModule {
    override val id: String = "module_channel_analysis"
    override val title: String = "Анализ радиоканала и эфира"
    override val description: String = "Анализ частотного диапазона, рабочего радиоканала, межканальной интерференции (ACI) и уровня сигнала."
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

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Выполняется анализ эфира..."))
        onEvent(DiagnosticEvent.Progress("Расчет характеристик канала", 1, 3))

        log("[*] Запуск модуля анализа радиоканала...")
        log("    • BSSID: ${target.bssid} | SSID: ${target.ssid}")
        log("    • Частота: ${target.frequency} МГц | Диапазон: ${target.band}")
        log("    • Канал: ${target.channel}")

        onEvent(DiagnosticEvent.Progress("Оценка интерференции", 2, 3))
        val result = WifiChannelAnalyzer.analyze(target, allAccessPoints)

        log("    • Качество сигнала: ${target.signalLevel} дБм (${result.signalQuality})")
        log("    • Точек на том же канале (${result.channel}): ${result.sameChannelApCount}")
        if (result.band == "2.4 GHz") {
            log("    • Точек на соседних пересекающихся каналах: ${result.adjacentChannelApCount}")
            if (result.isNonOverlapping24) {
                log("[+] Точка настроена на стандартный непересекающийся канал (${result.channel})", LogLevel.SUCCESS)
            } else {
                log("[!] ВНИМАНИЕ: Канал ${result.channel} вызывает межканальную интерференцию (ACI) с каналами 1, 6 и 11", LogLevel.WARNING)
            }
        }

        result.recommendations.forEach { rec ->
            log("[i] Рекомендация: $rec")
        }

        onEvent(DiagnosticEvent.Progress("Формирование отчета", 3, 3))
        val summary = "Канал ${result.channel} (${result.band}). Загрузка: ${result.congestionLevel}. Сигнал: ${target.signalLevel} дБм."
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
                "Channel" to result.channel.toString(),
                "Band" to result.band,
                "Signal" to "${result.signalLevel} dBm",
                "Congestion" to result.congestionLevel.name
            )
        )
    }
}
