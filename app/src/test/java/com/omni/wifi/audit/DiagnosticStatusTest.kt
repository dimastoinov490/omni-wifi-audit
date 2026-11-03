package com.omni.wifi.audit

import com.omni.wifi.audit.domain.diagnostic.DataSourceOrigin
import com.omni.wifi.audit.domain.diagnostic.DiagnosticStatus
import com.omni.wifi.audit.domain.diagnostic.ModuleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiagnosticStatusTest {

    @Test
    fun testAllEightDiagnosticStatusesExist() {
        val statuses = DiagnosticStatus.values()
        assertEquals(8, statuses.size)
        assertNotNull(DiagnosticStatus.valueOf("SUPPORTED"))
        assertNotNull(DiagnosticStatus.valueOf("UNSUPPORTED"))
        assertNotNull(DiagnosticStatus.valueOf("UNKNOWN"))
        assertNotNull(DiagnosticStatus.valueOf("PARTIAL"))
        assertNotNull(DiagnosticStatus.valueOf("WARNING"))
        assertNotNull(DiagnosticStatus.valueOf("ERROR"))
        assertNotNull(DiagnosticStatus.valueOf("RUNNING"))
        assertNotNull(DiagnosticStatus.valueOf("NOT_TESTED"))
    }

    @Test
    fun testModuleCategories() {
        val categories = ModuleCategory.values()
        assertEquals(6, categories.size)
        assertEquals("HARDWARE", ModuleCategory.HARDWARE.name)
        assertEquals("KERNEL_DRIVER", ModuleCategory.KERNEL_DRIVER.name)
        assertEquals("WIRELESS_CAPABILITY", ModuleCategory.WIRELESS_CAPABILITY.name)
        assertEquals("ROUTER_INSPECTION", ModuleCategory.ROUTER_INSPECTION.name)
        assertEquals("NETWORK_GATEWAY", ModuleCategory.NETWORK_GATEWAY.name)
        assertEquals("VULNERABILITY", ModuleCategory.VULNERABILITY.name)
    }

    @Test
    fun testDataSourceOrigins() {
        val origins = DataSourceOrigin.values()
        assertNotNull(DataSourceOrigin.valueOf("ANDROID_WIFI_API"))
        assertNotNull(DataSourceOrigin.valueOf("SCAN_RESULT"))
        assertNotNull(DataSourceOrigin.valueOf("WPS_IE"))
        assertNotNull(DataSourceOrigin.valueOf("LINUX_PROCFS"))
        assertNotNull(DataSourceOrigin.valueOf("LINUX_SYSFS"))
        assertNotNull(DataSourceOrigin.valueOf("KERNEL_MODULE"))
        assertNotNull(DataSourceOrigin.valueOf("IW_CLI"))
        assertNotNull(DataSourceOrigin.valueOf("ROOT_SHELL"))
        assertNotNull(DataSourceOrigin.valueOf("LOCAL_DATABASE"))
    }
}
