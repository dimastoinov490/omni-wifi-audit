package com.omni.wifi.audit.domain.diagnostic

data class DiagnosticDataItem(
    val key: String,
    val value: String,
    val origin: DataSourceOrigin,
    val status: DiagnosticStatus = DiagnosticStatus.SUPPORTED
)

data class ModuleCompatibility(
    val isCompatible: Boolean,
    val reason: String? = null,
    val missingDependencies: List<String> = emptyList()
)

sealed class DiagnosticEvent {
    data class Log(val message: String, val level: LogLevel = LogLevel.INFO) : DiagnosticEvent()
    data class Progress(val stage: String, val current: Int, val total: Int) : DiagnosticEvent()
    data class StatusChange(val status: DiagnosticStatus, val summary: String) : DiagnosticEvent()
    data class ItemDiscovered(val item: DiagnosticDataItem) : DiagnosticEvent()
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
    val category: ModuleCategory = ModuleCategory.HARDWARE,
    val targetBssid: String,
    val targetSsid: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DiagnosticStatus,
    val summary: String,
    val logs: List<String>,
    val dataItems: List<DiagnosticDataItem> = emptyList(),
    val sources: List<DataSourceOrigin> = emptyList(),
    val details: Map<String, String> = emptyMap()
)
