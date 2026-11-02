package com.omni.wifi.audit.domain.diagnostic

import android.content.Context
import com.omni.wifi.audit.domain.model.AccessPointInfo

interface IDiagnosticModule {
    val id: String
    val title: String
    val description: String
    val requiredPermissions: List<String>
    val requiresRoot: Boolean
    val requiresConnection: Boolean

    suspend fun checkAvailability(context: Context, target: AccessPointInfo): ModuleAvailability

    suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport
}
