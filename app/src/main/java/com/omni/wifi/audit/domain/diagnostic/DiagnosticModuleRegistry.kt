package com.omni.wifi.audit.domain.diagnostic

import com.omni.wifi.audit.domain.diagnostic.modules.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object DiagnosticModuleRegistry {

    private val modules = CopyOnWriteArrayList<IDiagnosticModule>()
    private val disabledModuleIds = ConcurrentHashMap.newKeySet<String>()

    init {
        registerDefaults()
    }

    fun registerDefaults() {
        modules.clear()
        disabledModuleIds.clear()

        registerModule(HardwareInspectorModule())
        registerModule(KernelInspectorModule())
        registerModule(WifiDriverInspectorModule())
        registerModule(MonitorModeInspectorModule())
        registerModule(FrameInjectionInspectorModule())
        registerModule(RouterIdentificationModule())
        registerModule(WpsInformationModule())
        registerModule(WifiChannelAnalysisModule())
        registerModule(GatewayAuditModule())
        registerModule(CveAdvisoryModule())
        registerModule(PixieDustDiagnosticsModule())
    }

    fun registerModule(module: IDiagnosticModule): Boolean {
        if (modules.any { it.id == module.id }) {
            return false // Duplicate rejected
        }
        return modules.add(module)
    }

    fun unregisterModule(moduleId: String): Boolean {
        return modules.removeIf { it.id == moduleId }
    }

    fun getModules(): List<IDiagnosticModule> {
        return modules.toList()
    }

    fun getEnabledModules(): List<IDiagnosticModule> {
        return modules.filter { !disabledModuleIds.contains(it.id) }
    }

    fun getModuleById(moduleId: String): IDiagnosticModule? {
        return modules.firstOrNull { it.id == moduleId }
    }

    fun getModulesByCategory(category: ModuleCategory): List<IDiagnosticModule> {
        return modules.filter { it.category == category }
    }

    fun isModuleEnabled(moduleId: String): Boolean {
        return !disabledModuleIds.contains(moduleId)
    }

    fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        if (enabled) {
            disabledModuleIds.remove(moduleId)
        } else {
            disabledModuleIds.add(moduleId)
        }
    }

    fun clear() {
        modules.clear()
        disabledModuleIds.clear()
    }
}
