package com.omni.wifi.audit.domain.scanner

import com.omni.wifi.audit.domain.model.AccessPointInfo

data class ChannelAnalysisResult(
    val channel: Int,
    val band: String,
    val frequency: Int,
    val signalLevel: Int,
    val signalQuality: String,
    val isNonOverlapping24: Boolean,
    val sameChannelApCount: Int,
    val adjacentChannelApCount: Int,
    val congestionLevel: CongestionLevel,
    val recommendations: List<String>
)

enum class CongestionLevel {
    LOW,
    MODERATE,
    HIGH,
    SEVERE
}

object WifiChannelAnalyzer {

    fun calculateChannel(frequency: Int): Int {
        return when {
            frequency in 2412..2472 -> (frequency - 2407) / 5
            frequency == 2484 -> 14
            frequency in 5170..5825 -> (frequency - 5000) / 5
            frequency in 5945..7105 -> (frequency - 5940) / 5
            else -> 0
        }
    }

    fun determineBand(frequency: Int): String {
        return when {
            frequency in 2400..2499 -> "2.4 GHz"
            frequency in 4900..5899 -> "5 GHz"
            frequency in 5925..7125 -> "6 GHz"
            else -> "N/A"
        }
    }

    fun analyze(target: AccessPointInfo, allAps: List<AccessPointInfo>): ChannelAnalysisResult {
        val channel = if (target.channel > 0) target.channel else calculateChannel(target.frequency)
        val band = if (target.band != "N/A" && target.band.isNotEmpty()) target.band else determineBand(target.frequency)

        val signal = target.signalLevel
        val quality = when {
            signal >= -50 -> "Отличный (высокая скорость, min задержка)"
            signal >= -65 -> "Хороший (стабильное подключение)"
            signal >= -75 -> "Удовлетворительный (возможны просадки)"
            signal >= -85 -> "Слабый (высокий джиттер)"
            else -> "Критически слабый (граница зоны)"
        }

        val isNonOverlapping24 = band == "2.4 GHz" && channel in listOf(1, 6, 11)

        val sameCount = allAps.count { it.bssid != target.bssid && it.channel == channel && it.band == band }
        val adjacentCount = if (band == "2.4 GHz") {
            allAps.count { it.bssid != target.bssid && it.band == "2.4 GHz" && kotlin.math.abs(it.channel - channel) in 1..2 }
        } else {
            0
        }

        val congestion = when {
            sameCount >= 5 || adjacentCount >= 6 -> CongestionLevel.SEVERE
            sameCount >= 3 || adjacentCount >= 3 -> CongestionLevel.HIGH
            sameCount >= 1 || adjacentCount >= 1 -> CongestionLevel.MODERATE
            else -> CongestionLevel.LOW
        }

        val recs = mutableListOf<String>()
        if (band == "2.4 GHz") {
            if (!isNonOverlapping24 && channel in 1..14) {
                recs.add("Точка работает на канале $channel, перекрывающем стандартные каналы 1, 6, 11 (межканальная интерференция ACI). Рекомендуется переключить роутер на каналы 1, 6 или 11.")
            }
            if (congestion == CongestionLevel.HIGH || congestion == CongestionLevel.SEVERE) {
                recs.add("Высокая плотность сетей в диапазоне 2.4 ГГц ($sameCount на одном канале, $adjacentCount на соседних). Рекомендуется включить вещание в 5 ГГц.")
            }
        } else if (band == "5 GHz") {
            recs.add("Диапазон 5 ГГц обеспечивает свободный радиоэфир и поддержку каналов 40/80/160 МГц без взаимных помех.")
        }

        return ChannelAnalysisResult(
            channel = channel,
            band = band,
            frequency = target.frequency,
            signalLevel = signal,
            signalQuality = quality,
            isNonOverlapping24 = isNonOverlapping24,
            sameChannelApCount = sameCount,
            adjacentChannelApCount = adjacentCount,
            congestionLevel = congestion,
            recommendations = recs
        )
    }
}
