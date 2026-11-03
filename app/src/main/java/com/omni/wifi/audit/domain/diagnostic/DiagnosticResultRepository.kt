package com.omni.wifi.audit.domain.diagnostic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object DiagnosticResultRepository {
    private val reports = ConcurrentHashMap<String, MutableList<DiagnosticReport>>()
    private val _reportsFlow = MutableStateFlow<List<DiagnosticReport>>(emptyList())
    val reportsFlow: StateFlow<List<DiagnosticReport>> = _reportsFlow.asStateFlow()

    fun saveReport(report: DiagnosticReport) {
        val list = reports.getOrPut(report.targetBssid) { mutableListOf() }
        synchronized(list) {
            // Keep the latest report at index 0, and keep history up to 50 entries
            list.add(0, report)
            if (list.size > 50) {
                list.removeAt(list.lastIndex)
            }
        }
        val allReports = reports.values.flatten().sortedByDescending { it.timestamp }
        _reportsFlow.value = allReports
    }

    fun getReportsForBssid(bssid: String): List<DiagnosticReport> {
        val list = reports[bssid] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun getLatestReport(bssid: String, moduleId: String): DiagnosticReport? {
        val list = reports[bssid] ?: return null
        return synchronized(list) { list.firstOrNull { it.moduleId == moduleId } }
    }

    fun getReportHistory(bssid: String, moduleId: String): List<DiagnosticReport> {
        val list = reports[bssid] ?: return emptyList()
        return synchronized(list) { list.filter { it.moduleId == moduleId } }
    }

    fun getAllReports(): List<DiagnosticReport> {
        return reports.values.flatten().sortedByDescending { it.timestamp }
    }

    fun clear() {
        reports.clear()
        _reportsFlow.value = emptyList()
    }
}
