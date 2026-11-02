package com.omni.wifi.audit.domain.wps

import android.net.wifi.ScanResult
import com.omni.wifi.audit.domain.model.AccessPointInfo

data class WpsAnalysisReport(
    val isWpsSupported: Boolean,
    val wpsVersion: String,
    val isLocked: Boolean,
    val state: WpsState,
    val manufacturer: String?,
    val modelName: String?,
    val modelNumber: String?,
    val deviceName: String?,
    val configMethods: List<String>,
    val confirmedIndicators: List<String>,
    val unconfirmedIndicators: List<String>,
    val protocolQuirks: List<String>
)

object WpsProtocolAnalyzer {

    fun analyze(scanResult: ScanResult?, ap: AccessPointInfo): WpsAnalysisReport {
        val parsedIe = scanResult?.let { WpsInformationElementParser.parseFromScanResult(it) }

        val confirmed = mutableListOf<String>()
        val unconfirmed = mutableListOf<String>()
        val quirks = mutableListOf<String>()

        val isSupported: Boolean
        val version: String
        val isLocked: Boolean
        val state: WpsState
        val manufacturer: String?
        val modelName: String?
        val modelNumber: String?
        val deviceName: String?
        val configMethods: List<String>

        if (parsedIe != null && parsedIe.isSupported) {
            isSupported = true
            version = parsedIe.version
            isLocked = parsedIe.isLocked
            state = parsedIe.state
            manufacturer = parsedIe.manufacturer
            modelName = parsedIe.modelName
            modelNumber = parsedIe.modelNumber
            deviceName = parsedIe.deviceName
            configMethods = parsedIe.configMethods

            confirmed.add("WPS Information Element (00:50:f2:04) подтвержден в кадре Beacon")
            confirmed.add("Версия протокола WPS: $version")
            confirmed.add("Состояние WPS: ${if (state == WpsState.CONFIGURED) "Configured" else "Unconfigured"}")
            if (isLocked) {
                confirmed.add("AP Setup Locked (0x1057): АКТИВЕН (роутер временно или постоянно блокирует регистрацию PIN)")
                quirks.add("Обнаружен флаг AP Setup Locked: Точка доступа блокирует входящие запросы M1/M4")
            } else {
                confirmed.add("AP Setup Locked (0x1057): НЕ активен (WPS открыт для согласования)")
            }

            if (manufacturer != null) confirmed.add("WPS Manufacturer (0x1021): $manufacturer")
            if (modelName != null) confirmed.add("WPS Model Name (0x1023): $modelName")
            if (modelNumber != null) confirmed.add("WPS Model Number (0x1024): $modelNumber")
            if (deviceName != null) confirmed.add("WPS Device Name (0x1011): $deviceName")

            if (configMethods.isNotEmpty()) {
                confirmed.add("Методы конфигурации: ${configMethods.joinToString(", ")}")
            }
        } else {
            // Fallback from scan capabilities string
            val hasWpsInCaps = ap.capabilities.contains("WPS")
            isSupported = hasWpsInCaps
            version = if (hasWpsInCaps) "1.0 / 2.0 (по Beacon)" else "Не поддерживается"
            isLocked = false
            state = WpsState.UNKNOWN
            manufacturer = null
            modelName = null
            modelNumber = null
            deviceName = null
            configMethods = when {
                ap.capabilities.contains("WPS-PBC") && ap.capabilities.contains("WPS-PIN") -> listOf("PushButton (PBC)", "Keypad (PIN)")
                ap.capabilities.contains("WPS-PBC") -> listOf("PushButton (PBC)")
                ap.capabilities.contains("WPS-PIN") -> listOf("Keypad (PIN)")
                else -> emptyList()
            }

            if (hasWpsInCaps) {
                confirmed.add("Флаг WPS присутствует в Capabilities")
                unconfirmed.add("Детальные Information Elements 00:50:f2:04 не были получены (возможно, отфильтрованы драйвером или не переданы в Probe Response)")
            } else {
                confirmed.add("WPS не анонсируется в радиоэфире")
            }
        }

        // Manufacturer-specific protocol behavior assessment
        val effectiveVendor = manufacturer ?: ap.vendor ?: ""
        if (effectiveVendor.contains("Keenetic", ignoreCase = true)) {
            quirks.add("Keenetic: Аппаратная защита WPS AP Lockout срабатывает после 3-5 попыток")
        } else if (effectiveVendor.contains("ASUS", ignoreCase = true)) {
            quirks.add("ASUS: Таймер блокировки WPS сбрасывается через 60-120 секунд")
        } else if (effectiveVendor.contains("TP-Link", ignoreCase = true)) {
            quirks.add("TP-Link: Новые ревизии Archer блокируют WPS на уровне чипсета после последовательных попыток")
        } else if (effectiveVendor.contains("MikroTik", ignoreCase = true)) {
            quirks.add("MikroTik: RouterOS поддерживает только виртуальный WPS (Virtual Push-button) по требованию")
        }

        return WpsAnalysisReport(
            isWpsSupported = isSupported,
            wpsVersion = version,
            isLocked = isLocked,
            state = state,
            manufacturer = manufacturer,
            modelName = modelName,
            modelNumber = modelNumber,
            deviceName = deviceName,
            configMethods = configMethods,
            confirmedIndicators = confirmed,
            unconfirmedIndicators = unconfirmed,
            protocolQuirks = quirks
        )
    }
}
