package com.omni.wifi.audit.domain.diagnostic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory report index backed by app-private preferences so diagnostic history
 * survives process death and device restarts. No external storage permission is needed.
 */
object DiagnosticResultRepository {
    private const val PREFERENCES_NAME = "diagnostic_reports"
    private const val REPORTS_KEY = "reports_json"
    private const val MAX_REPORTS_PER_NETWORK = 50

    private val lock = Any()
    private val reports = ConcurrentHashMap<String, MutableList<DiagnosticReport>>()
    private val _reportsFlow = MutableStateFlow<List<DiagnosticReport>>(emptyList())
    val reportsFlow: StateFlow<List<DiagnosticReport>> = _reportsFlow.asStateFlow()

    @Volatile
    private var preferences: android.content.SharedPreferences? = null

    /** Load persisted history once; safe to call again after Activity recreation. */
    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) return
            val prefs = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            preferences = prefs
            val saved = prefs.getString(REPORTS_KEY, null)
            if (!saved.isNullOrBlank()) {
                try {
                    val array = JSONArray(saved)
                    for (index in 0 until array.length()) {
                        decodeReport(array.optJSONObject(index))?.let(::addReportToIndex)
                    }
                } catch (_: Exception) {
                    // Corrupt/old persisted data must not prevent the rest of the app from starting.
                    reports.clear()
                }
            }
            publishReports()
        }
    }

    fun saveReport(report: DiagnosticReport) {
        synchronized(lock) {
            addReportToIndex(report)
            publishReports()
            persistReports()
        }
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

    fun getAllReports(): List<DiagnosticReport> = synchronized(lock) { allReports() }

    fun clear() {
        synchronized(lock) {
            reports.clear()
            _reportsFlow.value = emptyList()
            persistReports()
        }
    }

    private fun addReportToIndex(report: DiagnosticReport) {
        val list = reports.getOrPut(report.targetBssid) { mutableListOf() }
        synchronized(list) {
            list.add(0, report)
            if (list.size > MAX_REPORTS_PER_NETWORK) {
                list.subList(MAX_REPORTS_PER_NETWORK, list.size).clear()
            }
        }
    }

    private fun allReports(): List<DiagnosticReport> =
        reports.values.flatMap { list -> synchronized(list) { list.toList() } }
            .sortedByDescending { it.timestamp }

    private fun publishReports() {
        _reportsFlow.value = allReports()
    }

    private fun persistReports() {
        val prefs = preferences ?: return
        try {
            val json = JSONArray()
            allReports().forEach { json.put(encodeReport(it)) }
            // commit is intentional: report history should not be lost if the process is killed immediately.
            prefs.edit().putString(REPORTS_KEY, json.toString()).commit()
        } catch (_: Exception) {
            // Keep the in-memory history usable if storage is temporarily unavailable.
        }
    }

    private fun encodeReport(report: DiagnosticReport): JSONObject = JSONObject().apply {
        put("moduleId", report.moduleId)
        put("moduleTitle", report.moduleTitle)
        put("category", report.category.name)
        put("targetBssid", report.targetBssid)
        put("targetSsid", report.targetSsid)
        put("timestamp", report.timestamp)
        put("status", report.status.name)
        put("summary", report.summary)
        put("logs", JSONArray(report.logs))
        put("dataItems", JSONArray().apply {
            report.dataItems.forEach { item ->
                put(JSONObject().apply {
                    put("key", item.key)
                    put("value", item.value)
                    put("origin", item.origin.name)
                    put("status", item.status.name)
                })
            }
        })
        put("sources", JSONArray().apply { report.sources.forEach { put(it.name) } })
        put("details", JSONObject().apply { report.details.forEach { (key, value) -> put(key, value) } })
    }

    private fun decodeReport(value: JSONObject?): DiagnosticReport? {
        value ?: return null
        return try {
            val logsJson = value.optJSONArray("logs") ?: JSONArray()
            val itemsJson = value.optJSONArray("dataItems") ?: JSONArray()
            val items = buildList {
                for (index in 0 until itemsJson.length()) {
                    val item = itemsJson.optJSONObject(index) ?: continue
                    add(
                        DiagnosticDataItem(
                            key = item.optString("key"),
                            value = item.optString("value"),
                            origin = enumOrDefault(item.optString("origin"), DataSourceOrigin.ANDROID_HAL),
                            status = enumOrDefault(item.optString("status"), DiagnosticStatus.UNKNOWN)
                        )
                    )
                }
            }
            val sourcesJson = value.optJSONArray("sources") ?: JSONArray()
            val sources = buildList {
                for (index in 0 until sourcesJson.length()) {
                    enumOrNull<DataSourceOrigin>(sourcesJson.optString(index))?.let(::add)
                }
            }
            val detailsJson = value.optJSONObject("details") ?: JSONObject()
            val details = buildMap {
                val keys = detailsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, detailsJson.optString(key))
                }
            }
            val category = enumOrDefault(value.optString("category"), ModuleCategory.HARDWARE)
            DiagnosticReport(
                moduleId = value.getString("moduleId"),
                moduleTitle = value.optString("moduleTitle"),
                category = category,
                targetBssid = value.getString("targetBssid"),
                targetSsid = value.optString("targetSsid"),
                timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                status = enumOrDefault(value.optString("status"), DiagnosticStatus.UNKNOWN),
                summary = value.optString("summary"),
                logs = buildList { for (index in 0 until logsJson.length()) add(logsJson.optString(index)) },
                dataItems = items,
                sources = sources,
                details = details
            )
        } catch (_: Exception) {
            null
        }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        try { enumValueOf<T>(value) } catch (_: IllegalArgumentException) { null }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        enumOrNull<T>(value) ?: fallback
}
