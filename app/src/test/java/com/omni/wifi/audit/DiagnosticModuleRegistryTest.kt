package com.omni.wifi.audit

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.model.AccessPointInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DiagnosticModuleRegistryTest {

    @Before
    fun setUp() {
        DiagnosticModuleRegistry.registerDefaults()
    }

    @Test
    fun testDefaultModulesRegistered() {
        val modules = DiagnosticModuleRegistry.getModules()
        assertEquals("Should register exactly 11 modules by default", 11, modules.size)
        assertNotNull(DiagnosticModuleRegistry.getModuleById("hw_inspector"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("kernel_inspector"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("driver_inspector"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("monitor_mode_inspector"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("frame_injection_inspector"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("router_id_module"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("wps_info_module"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("channel_analysis_module"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("gateway_audit_module"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("cve_advisory_module"))
        assertNotNull(DiagnosticModuleRegistry.getModuleById("pixie_dust_diagnostics"))
    }

    @Test
    fun testDuplicateRegistrationRejected() {
        val countBefore = DiagnosticModuleRegistry.getModules().size
        val duplicateModule = object : IDiagnosticModule {
            override val id: String = "hw_inspector"
            override val title: String = "Duplicate"
            override val description: String = "Test"
            override val category: ModuleCategory = ModuleCategory.HARDWARE
            override suspend fun execute(
                context: Context,
                target: AccessPointInfo,
                allAccessPoints: List<AccessPointInfo>,
                onEvent: suspend (DiagnosticEvent) -> Unit
            ): DiagnosticReport {
                throw UnsupportedOperationException()
            }
        }
        val registered = DiagnosticModuleRegistry.registerModule(duplicateModule)
        assertFalse("Duplicate module ID should not be accepted", registered)
        assertEquals(countBefore, DiagnosticModuleRegistry.getModules().size)
    }

    @Test
    fun testCategoryFiltering() {
        val hwModules = DiagnosticModuleRegistry.getModulesByCategory(ModuleCategory.HARDWARE)
        assertTrue(hwModules.any { it.id == "hw_inspector" })

        val kernelModules = DiagnosticModuleRegistry.getModulesByCategory(ModuleCategory.KERNEL_DRIVER)
        assertTrue(kernelModules.any { it.id == "kernel_inspector" })
        assertTrue(kernelModules.any { it.id == "driver_inspector" })

        val vulnModules = DiagnosticModuleRegistry.getModulesByCategory(ModuleCategory.VULNERABILITY)
        assertTrue(vulnModules.any { it.id == "cve_advisory_module" })
        assertTrue(vulnModules.any { it.id == "pixie_dust_diagnostics" })
    }

    @Test
    fun testEnableDisableModule() {
        assertTrue(DiagnosticModuleRegistry.isModuleEnabled("hw_inspector"))

        DiagnosticModuleRegistry.setModuleEnabled("hw_inspector", false)
        assertFalse(DiagnosticModuleRegistry.isModuleEnabled("hw_inspector"))
        assertFalse(DiagnosticModuleRegistry.getEnabledModules().any { it.id == "hw_inspector" })

        DiagnosticModuleRegistry.setModuleEnabled("hw_inspector", true)
        assertTrue(DiagnosticModuleRegistry.isModuleEnabled("hw_inspector"))
        assertTrue(DiagnosticModuleRegistry.getEnabledModules().any { it.id == "hw_inspector" })
    }

    @Test
    fun testUnregisterModule() {
        assertTrue(DiagnosticModuleRegistry.unregisterModule("hw_inspector"))
        assertNull(DiagnosticModuleRegistry.getModuleById("hw_inspector"))
    }
}
