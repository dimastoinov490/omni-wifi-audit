package com.omni.wifi.audit.domain.diagnostic

import com.omni.wifi.audit.domain.diagnostic.modules.*
import java.util.concurrent.CopyOnWriteArrayList

object DiagnosticModuleRegistry {

    private val modules = CopyOnWriteArrayList<IDiagnosticModule>()

    init {
        // Register default production diagnostic modules
        registerModule(ChannelAnalysisModule())
        registerModule(WpsInspectionModule())
        registerModule(PixieDustDiagnosticsModule())
        registerModule(GatewayAuditModule())
        registerModule(CveAdvisoryModule())
        registerModule(WifiDriverInspectionModule())
    }

    fun registerModule(module: IDiagnosticModule) {
        if (modules.none { it.id == module.id }) {
            modules.add(module)
        }
    }

    fun unregisterModule(moduleId: String) {
        modules.removeIf { it.id == moduleId }
    }

    fun getModules(): List<IDiagnosticModule> {
        return modules.toList()
    }

    fun getModuleById(moduleId: String): IDiagnosticModule? {
        return modules.firstOrNull { it.id == moduleId }
    }
}
