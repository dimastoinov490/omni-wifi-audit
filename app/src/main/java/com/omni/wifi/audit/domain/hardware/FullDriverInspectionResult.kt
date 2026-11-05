package com.omni.wifi.audit.domain.hardware

/**
 * Result of a real Wi‑Fi driver inspection based on Android/Linux capabilities and sysfs data.
 */
data class FullDriverInspectionResult(
    val system: SystemCapabilities,
    val driver: DriverCapabilityInfo
)
