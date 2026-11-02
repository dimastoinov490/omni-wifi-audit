package com.omni.wifi.audit.domain.hardware

data class DriverCapabilityInfo(
    val driverName: String,
    val chipset: String,
    val interfaceName: String,
    val isInterfaceUp: Boolean,
    val supportedModes: List<String>,
    val isMonitorModeSupported: Boolean,
    val isPacketInjectionSupported: Boolean,
    val kernelModule: String,
    val notes: List<String>
)

interface IWifiDriverHardware {
    val hardwareName: String
    suspend fun probe(hasRoot: Boolean): DriverCapabilityInfo
}
