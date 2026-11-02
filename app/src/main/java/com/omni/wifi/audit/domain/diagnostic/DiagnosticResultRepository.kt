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
            list.add(0, report)
        }
        val allReports = reports.values.flatten().sortedByDescending { it.timestamp }
        _reportsFlow.value = allReports
    }

    fun getReportsForBssid(bssid: String): List<DiagnosticReport> {
        return reports[bssid]?.toList() ?: emptyList()
    }

    fun getLatestReport(bssid: String, moduleId: String): DiagnosticReport? {
        return reports[bssid]?.firstOrNull { it.moduleId == moduleId }
    }

    fun clear() {
        reports.clear()
        _reportsFlow.value = emptyList()
    }
}
