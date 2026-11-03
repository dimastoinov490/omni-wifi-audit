package com.omni.wifi.audit.domain.diagnostic

import android.content.Context
import com.omni.wifi.audit.domain.model.AccessPointInfo

interface IDiagnosticModule {
    val id: String
    val title: String
    val description: String
    val category: ModuleCategory
    val version: String
        get() = "1.0.0"
    val requiredPermissions: List<String>
        get() = emptyList()
    val requiresRoot: Boolean
        get() = false
    val requiresConnection: Boolean
        get() = false
    val dependencies: List<String>
        get() = emptyList()

    suspend fun checkCompatibility(
        context: Context,
        target: AccessPointInfo,
        registry: DiagnosticModuleRegistry
    ): ModuleCompatibility {
        val missing = dependencies.filter { depId -> registry.getModuleById(depId) == null }
        return if (missing.isNotEmpty()) {
            ModuleCompatibility(
                isCompatible = false,
                reason = "Отсутствуют необходимые модули: ${missing.joinToString(", ")}",
                missingDependencies = missing
            )
        } else {
            ModuleCompatibility(isCompatible = true)
        }
    }

    suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport

    fun cancel() {}
}
