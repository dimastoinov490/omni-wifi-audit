package com.omni.wifi.audit

import com.omni.wifi.audit.domain.diagnostic.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DiagnosticResultRepositoryTest {

    @Before
    fun setUp() {
        DiagnosticResultRepository.clear()
    }

    @Test
    fun testSaveAndRetrieveReport() {
        val bssid = "11:22:33:44:55:66"
        val report = DiagnosticReport(
            moduleId = "hw_inspector",
            moduleTitle = "Аппаратная платформа",
            category = ModuleCategory.HARDWARE,
            targetBssid = bssid,
            targetSsid = "TestAP",
            status = DiagnosticStatus.SUPPORTED,
            summary = "Платформа Qualcomm Snapdragon 636",
            logs = listOf("[*] Проверка hardware", "[+] Платформа whyred"),
            dataItems = listOf(
                DiagnosticDataItem("SoC", "SDM636", DataSourceOrigin.ANDROID_HAL, DiagnosticStatus.SUPPORTED)
            ),
            sources = listOf(DataSourceOrigin.ANDROID_HAL, DataSourceOrigin.LINUX_PROCFS)
        )

        DiagnosticResultRepository.saveReport(report)

        val retrievedList = DiagnosticResultRepository.getReportsForBssid(bssid)
        assertEquals(1, retrievedList.size)
        assertEquals("hw_inspector", retrievedList[0].moduleId)
        assertEquals(DiagnosticStatus.SUPPORTED, retrievedList[0].status)
        assertEquals(1, retrievedList[0].dataItems.size)
        assertEquals("SoC", retrievedList[0].dataItems[0].key)

        val latest = DiagnosticResultRepository.getLatestReport(bssid, "hw_inspector")
        assertNotNull(latest)
        assertEquals("Платформа Qualcomm Snapdragon 636", latest!!.summary)
    }

    @Test
    fun testReportHistory() {
        val bssid = "22:33:44:55:66:77"
        val report1 = DiagnosticReport(
            moduleId = "kernel_inspector",
            moduleTitle = "Ядро Linux",
            category = ModuleCategory.KERNEL_DRIVER,
            targetBssid = bssid,
            targetSsid = "TestAP",
            status = DiagnosticStatus.RUNNING,
            summary = "Запуск",
            logs = emptyList(),
            timestamp = 1000L
        )
        val report2 = DiagnosticReport(
            moduleId = "kernel_inspector",
            moduleTitle = "Ядро Linux",
            category = ModuleCategory.KERNEL_DRIVER,
            targetBssid = bssid,
            targetSsid = "TestAP",
            status = DiagnosticStatus.SUPPORTED,
            summary = "Завершено успешно",
            logs = emptyList(),
            timestamp = 2000L
        )

        DiagnosticResultRepository.saveReport(report1)
        DiagnosticResultRepository.saveReport(report2)

        val history = DiagnosticResultRepository.getReportHistory(bssid, "kernel_inspector")
        assertEquals(2, history.size)
        // Latest report is at index 0
        assertEquals(DiagnosticStatus.SUPPORTED, history[0].status)

        val latest = DiagnosticResultRepository.getLatestReport(bssid, "kernel_inspector")
        assertEquals(DiagnosticStatus.SUPPORTED, latest!!.status)
    }

    @Test
    fun testClearReports() {
        val report = DiagnosticReport(
            moduleId = "wps_info_module",
            moduleTitle = "WPS Info",
            category = ModuleCategory.ROUTER_INSPECTION,
            targetBssid = "33:44:55:66:77:88",
            targetSsid = "TestAP",
            status = DiagnosticStatus.WARNING,
            summary = "WPS Lockout",
            logs = emptyList()
        )
        DiagnosticResultRepository.saveReport(report)
        assertEquals(1, DiagnosticResultRepository.getAllReports().size)

        DiagnosticResultRepository.clear()
        assertEquals(0, DiagnosticResultRepository.getAllReports().size)
        assertEquals(0, DiagnosticResultRepository.reportsFlow.value.size)
    }
}
