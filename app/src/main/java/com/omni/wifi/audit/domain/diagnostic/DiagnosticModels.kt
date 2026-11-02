package com.omni.wifi.audit.domain.diagnostic

import java.time.Instant

enum class DiagnosticStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    UNSUPPORTED,
    PERMISSION_DENIED,
    TIMEOUT,
    CANCELLED
}

sealed class DiagnosticEvent {
    data class Log(val message: String, val level: LogLevel = LogLevel.INFO) : DiagnosticEvent()
    data class Progress(val stage: String, val current: Int, val total: Int) : DiagnosticEvent()
    data class StatusChange(val status: DiagnosticStatus, val summary: String) : DiagnosticEvent()
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

sealed class ModuleAvailability {
    object Available : ModuleAvailability()
    data class Unavailable(val reason: String) : ModuleAvailability()
}

data class DiagnosticReport(
    val moduleId: String,
    val moduleTitle: String,
    val targetBssid: String,
    val targetSsid: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DiagnosticStatus,
    val summary: String,
    val logs: List<String>,
    val details: Map<String, String> = emptyMap()
)
