package com.omni.wifi.audit.domain.diagnostic

import android.content.Context
import com.omni.wifi.audit.domain.model.AccessPointInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * DiagnosticEngine delegates to DiagnosticCore for unified task execution.
 */
object DiagnosticEngine {

    val taskState: StateFlow<DiagnosticTaskState>
        get() = DiagnosticCore.taskState

    fun runTask(
        context: Context,
        module: IDiagnosticModule,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>
    ) {
        DiagnosticCore.runTask(context, module, target, allAccessPoints)
    }

    fun cancelCurrentTask() {
        DiagnosticCore.cancelCurrentTask()
    }

    fun reset() {
        DiagnosticCore.reset()
    }
}
