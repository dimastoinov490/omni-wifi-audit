package com.omni.wifi.audit.domain.router

import com.omni.wifi.audit.data.DatabaseCacheManager
import com.omni.wifi.audit.domain.wps.WpsDeviceInfo

enum class ConfidenceLevel {
    HIGH,     // Verified from WPS Information Elements (Manufacturer + Model)
    MEDIUM,   // Verified from IEEE OUI database match
    LOW,      // Fallback OUI table prefix
    UNKNOWN
}

data class RouterIdentificationResult(
    val vendor: String,
    val model: String?,
    val modelNumber: String?,
    val deviceName: String?,
    val confidence: ConfidenceLevel,
    val source: String,
    val evidence: List<String>
)

object RouterIdentificationEngine {

    // Expanded built-in IEEE OUI database
    private val BUILTIN_OUIS = mapOf(
        // TP-Link
        "000C42" to "TP-Link", "18E829" to "TP-Link", "50C7BF" to "TP-Link", "CC2DE0" to "TP-Link",
        "60E327" to "TP-Link", "E848B8" to "TP-Link", "B43A28" to "TP-Link", "AC84C6" to "TP-Link",
        "D807B6" to "TP-Link", "F4F26D" to "TP-Link", "54AF97" to "TP-Link", "003192" to "TP-Link",
        // Keenetic
        "28285D" to "Keenetic", "647002" to "Keenetic", "107B44" to "Keenetic", "EE43F6" to "Keenetic",
        "50FF20" to "Keenetic",
        // D-Link
        "001E58" to "D-Link", "002401" to "D-Link", "1C7EE5" to "D-Link", "B8A386" to "D-Link",
        "28107B" to "D-Link", "14D64D" to "D-Link", "C8D3A3" to "D-Link", "78321B" to "D-Link",
        // ASUS
        "B06EBF" to "ASUS", "9C934E" to "ASUS", "049226" to "ASUS", "F832E4" to "ASUS",
        "2C4D54" to "ASUS", "40167E" to "ASUS", "704D7B" to "ASUS", "107B44" to "ASUS",
        // Huawei
        "E460E2" to "Huawei", "00E0FC" to "Huawei", "4869CC" to "Huawei", "88366C" to "Huawei",
        "00464B" to "Huawei", "200889" to "Huawei", "7054F5" to "Huawei", "CC3D82" to "Huawei",
        // ZTE
        "001E73" to "ZTE", "002293" to "ZTE", "709F2D" to "ZTE", "909F43" to "ZTE",
        "34E0CF" to "ZTE", "DC028E" to "ZTE", "6C8B2F" to "ZTE", "485702" to "ZTE",
        // Xiaomi
        "BC3F8F" to "Xiaomi", "286C07" to "Xiaomi", "640980" to "Xiaomi", "7811DC" to "Xiaomi",
        "508F4C" to "Xiaomi", "8CBEBE" to "Xiaomi", "ACF1DF" to "Xiaomi",
        // MikroTik
        "488F5A" to "MikroTik", "64D154" to "MikroTik", "B869F4" to "MikroTik", "CC2DE0" to "MikroTik",
        "D4CA6D" to "MikroTik", "000C42" to "MikroTik", "E48D8C" to "MikroTik",
        // Tenda
        "C83A35" to "Tenda", "74DADA" to "Tenda", "502B73" to "Tenda", "0495E6" to "Tenda",
        "CC3429" to "Tenda", "E865D4" to "Tenda",
        // Netgear
        "001B2F" to "Netgear", "9CD36D" to "Netgear", "C40415" to "Netgear", "20E52A" to "Netgear",
        "2C3033" to "Netgear", "A00460" to "Netgear",
        // Ubiquiti
        "002722" to "Ubiquiti", "0418D6" to "Ubiquiti", "24A43C" to "Ubiquiti", "F09FC2" to "Ubiquiti",
        // Cisco
        "00000C" to "Cisco", "000142" to "Cisco", "000143" to "Cisco", "001B53" to "Cisco"
    )

    fun identify(bssid: String, wpsInfo: WpsDeviceInfo?): RouterIdentificationResult {
        val evidence = mutableListOf<String>()

        // 1. High-confidence identification: WPS Information Elements (Beacon/Probe Response)
        if (wpsInfo != null && wpsInfo.isSupported) {
            val wpsVendor = wpsInfo.manufacturer
            val wpsModel = wpsInfo.modelName
            val wpsModelNum = wpsInfo.modelNumber
            val wpsDevice = wpsInfo.deviceName

            if (!wpsVendor.isNullOrBlank() || !wpsModel.isNullOrBlank()) {
                val resolvedVendor = wpsVendor ?: resolveOUI(bssid) ?: "Неизвестный производитель"
                evidence.add("Подтверждено через WPS Information Elements (802.11 Beacon)")
                wpsVendor?.let { evidence.add("WPS Manufacturer: $it") }
                wpsModel?.let { evidence.add("WPS Model Name: $it") }
                wpsModelNum?.let { evidence.add("WPS Model Number: $it") }
                wpsDevice?.let { evidence.add("WPS Device Name: $it") }

                return RouterIdentificationResult(
                    vendor = resolvedVendor,
                    model = wpsModel,
                    modelNumber = wpsModelNum,
                    deviceName = wpsDevice,
                    confidence = ConfidenceLevel.HIGH,
                    source = "WPS_INFORMATION_ELEMENT",
                    evidence = evidence
                )
            }
        }

        // 2. Medium-confidence: IEEE OUI Database lookup
        val dbVendor = DatabaseCacheManager.getVendor(bssid)
        if (dbVendor != null && dbVendor != "Неизвестный") {
            evidence.add("Определено по базе OUI IEEE (MAC-префикс): $dbVendor")
            return RouterIdentificationResult(
                vendor = dbVendor,
                model = null,
                modelNumber = null,
                deviceName = null,
                confidence = ConfidenceLevel.MEDIUM,
                source = "IEEE_OUI_DATABASE",
                evidence = evidence
            )
        }

        // 3. Built-in OUI fallback table
        val builtin = resolveOUI(bssid)
        if (builtin != null) {
            evidence.add("Определено по встроенному OUI-словарю: $builtin")
            return RouterIdentificationResult(
                vendor = builtin,
                model = null,
                modelNumber = null,
                deviceName = null,
                confidence = ConfidenceLevel.LOW,
                source = "IEEE_OUI_BUILTIN",
                evidence = evidence
            )
        }

        evidence.add("Производитель не зарегистрирован в локальной базе OUI")
        return RouterIdentificationResult(
            vendor = "Неизвестный производитель",
            model = null,
            modelNumber = null,
            deviceName = null,
            confidence = ConfidenceLevel.UNKNOWN,
            source = "NONE",
            evidence = evidence
        )
    }

    private fun resolveOUI(bssid: String): String? {
        val clean = bssid.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        if (clean.length >= 6) {
            val prefix = clean.substring(0, 6)
            return BUILTIN_OUIS[prefix]
        }
        return null
    }
}
