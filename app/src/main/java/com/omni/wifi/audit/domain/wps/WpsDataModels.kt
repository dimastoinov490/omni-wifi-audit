package com.omni.wifi.audit.domain.wps

data class WpsDeviceInfo(
    val isSupported: Boolean = false,
    val version: String = "Unknown",
    val state: WpsState = WpsState.UNKNOWN,
    val isLocked: Boolean = false,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    val deviceName: String? = null,
    val serialNumber: String? = null,
    val configMethods: List<String> = emptyList(),
    val rawTlvCount: Int = 0,
    val source: String = "NONE" // "INFORMATION_ELEMENT" or "CAPABILITIES_FALLBACK"
)

enum class WpsState {
    NOT_CONFIGURED,
    CONFIGURED,
    UNKNOWN
}
