package com.omni.wifi.audit

import android.content.Context
import android.content.ContextWrapper
import com.omni.wifi.audit.domain.diagnostic.DiagnosticModuleRegistry
import com.omni.wifi.audit.domain.model.AccessPointInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModuleDependencyTest {

    private lateinit var testContext: Context
    private val testTarget = AccessPointInfo(
        bssid = "AA:BB:CC:DD:EE:FF",
        ssid = "Test_Network",
        capabilities = "[WPA2-PSK-CCMP][WPS][ESS]",
        frequency = 2412,
        signalLevel = -55,
        channel = 1,
        band = "2.4 GHz",
        encryptionType = "WPA2",
        isWpsSupported = true,
        wpsDetails = "WPS 2.0 (Config: PBC, Display)",
        wifiStandard = "802.11n",
        vendor = "Qualcomm"
    )

    @Before
    fun setUp() {
        testContext = ContextWrapper(null)
        DiagnosticModuleRegistry.registerDefaults()
    }

    @Test
    fun testMonitorModeDependenciesSatisfiedWhenAllPresent() = runBlocking {
        val monitorModule = DiagnosticModuleRegistry.getModuleById("monitor_mode_inspector")
        assertNotNull("monitor_mode_inspector should exist", monitorModule)

        val compatibility = monitorModule!!.checkCompatibility(testContext, testTarget, DiagnosticModuleRegistry)
        assertTrue("All dependencies are registered by default, compatibility should be true", compatibility.isCompatible)
        assertTrue(compatibility.missingDependencies.isEmpty())
    }

    @Test
    fun testMonitorModeReportsMissingDependencyWhenDriverUnregistered() = runBlocking {
        val monitorModule = DiagnosticModuleRegistry.getModuleById("monitor_mode_inspector")
        assertNotNull(monitorModule)

        // Unregister one of its dependencies: driver_inspector
        DiagnosticModuleRegistry.unregisterModule("driver_inspector")

        val compatibility = monitorModule!!.checkCompatibility(testContext, testTarget, DiagnosticModuleRegistry)
        assertFalse("Should be incompatible when driver_inspector is missing", compatibility.isCompatible)
        assertTrue("missingDependencies should contain driver_inspector", compatibility.missingDependencies.contains("driver_inspector"))
        assertNotNull(compatibility.reason)
        assertTrue(compatibility.reason!!.contains("driver_inspector"))
    }

    @Test
    fun testFrameInjectionDependsOnMonitorMode() = runBlocking {
        val frameModule = DiagnosticModuleRegistry.getModuleById("frame_injection_inspector")
        assertNotNull(frameModule)
        assertTrue(frameModule!!.dependencies.contains("monitor_mode_inspector"))

        DiagnosticModuleRegistry.unregisterModule("monitor_mode_inspector")
        val compatibility = frameModule.checkCompatibility(testContext, testTarget, DiagnosticModuleRegistry)
        assertFalse(compatibility.isCompatible)
        assertTrue(compatibility.missingDependencies.contains("monitor_mode_inspector"))
    }

    @Test
    fun testCveAdvisoryDependsOnRouterIdModule() = runBlocking {
        val cveModule = DiagnosticModuleRegistry.getModuleById("cve_advisory_module")
        assertNotNull(cveModule)
        assertTrue(cveModule!!.dependencies.contains("router_id_module"))

        DiagnosticModuleRegistry.unregisterModule("router_id_module")
        val compatibility = cveModule.checkCompatibility(testContext, testTarget, DiagnosticModuleRegistry)
        assertFalse(compatibility.isCompatible)
        assertTrue(compatibility.missingDependencies.contains("router_id_module"))
    }

    @Test
    fun testPixieDustDiagnosticsDependencies() = runBlocking {
        val pixieModule = DiagnosticModuleRegistry.getModuleById("pixie_dust_diagnostics")
        assertNotNull(pixieModule)
        assertTrue(pixieModule!!.dependencies.contains("wps_info_module"))
        assertTrue(pixieModule.dependencies.contains("driver_inspector"))
    }
}
