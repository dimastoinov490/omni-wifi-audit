package com.omni.wifi.audit.data

import android.content.Context
import com.omni.wifi.audit.data.dao.AuditDao
import com.omni.wifi.audit.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object DatabaseCacheManager {
    private val ouiMap = ConcurrentHashMap<String, String>()
    private val credentialsMap = ConcurrentHashMap<String, List<DefaultCredential>>()
    private val cveMap = ConcurrentHashMap<String, List<CveVulnerability>>()
    private val wpsAlgorithmsList = mutableListOf<WpsAlgorithm>()
    private var isInitialized = false

    suspend fun initialize(context: Context, dao: AuditDao) {
        if (isInitialized) return
        withContext(Dispatchers.IO) {
            try {
                // Ensure default snapshot & data exists safely
                try {
                    DatabaseManager.saveDefaultDatabaseSnapshot(context, dao)
                    DataInitializer.populate(dao)
                } catch (_: Exception) {}

                val ouis = try { dao.getAllOuis() } catch (_: Exception) { emptyList() }
                ouiMap.clear()
                for (oui in ouis) {
                    val cleanOui = oui.oui.replace(Regex("[^0-9A-F]"), "").uppercase()
                    ouiMap[cleanOui] = oui.vendor
                    ouiMap[oui.oui.uppercase()] = oui.vendor
                }

                val creds = try { dao.findCredentials("") } catch (_: Exception) { emptyList() }
                credentialsMap.clear()
                for (cred in creds) {
                    val key = cred.vendor.lowercase()
                    val list = credentialsMap.getOrPut(key) { mutableListOf() } as MutableList
                    list.add(cred)
                }

                val cves = try { dao.findCvesForModel("") } catch (_: Exception) { emptyList() }
                cveMap.clear()
                for (cve in cves) {
                    val key = cve.targetModel.lowercase()
                    val list = cveMap.getOrPut(key) { mutableListOf() } as MutableList
                    list.add(cve)
                }

                if (ouiMap.isEmpty()) {
                    loadBuiltinFallbackCache()
                }

                isInitialized = true
            } catch (e: Exception) {
                // Fail-safe: fallback to hardcoded built-in cache if DB or disk fails
                loadBuiltinFallbackCache()
                isInitialized = true
            }
        }
    }

    private fun loadBuiltinFallbackCache() {
        val defaultOuis = mapOf(
            "000C42" to "TP-Link", "18E829" to "TP-Link", "50C7BF" to "TP-Link", "CC2DE0" to "TP-Link",
            "60E327" to "TP-Link", "E848B8" to "TP-Link", "B43A28" to "TP-Link",
            "28285D" to "Keenetic", "647002" to "Keenetic", "107B44" to "Keenetic",
            "001E58" to "D-Link", "002401" to "D-Link", "1C7EE5" to "D-Link", "B8A386" to "D-Link",
            "C83A35" to "Tenda", "74DADA" to "Tenda", "502B73" to "Tenda",
            "BC3F8F" to "Xiaomi", "286C07" to "Xiaomi", "640980" to "Xiaomi", "7811DC" to "Xiaomi",
            "E460E2" to "Huawei", "00E0FC" to "Huawei", "4869CC" to "Huawei", "88366C" to "Huawei",
            "488F5A" to "Mikrotik", "64D154" to "Mikrotik", "B869F4" to "Mikrotik",
            "B06EBF" to "ASUS", "9C934E" to "ASUS", "AC84C6" to "ASUS", "049226" to "ASUS", "F832E4" to "ASUS",
            "001B2F" to "Netgear", "9CD36D" to "Netgear", "C40415" to "Netgear"
        )
        ouiMap.putAll(defaultOuis)
    }

    fun getVendor(bssid: String): String? {
        try {
            val cleanBssid = bssid.replace(Regex("[^0-9A-F]"), "").uppercase()
            if (cleanBssid.length >= 8) {
                val prefix8 = cleanBssid.take(8)
                if (ouiMap.containsKey(prefix8)) return ouiMap[prefix8]
            }
            if (cleanBssid.length >= 6) {
                val prefix6 = cleanBssid.take(6)
                if (ouiMap.containsKey(prefix6)) return ouiMap[prefix6]
            }
            for ((oui, vendor) in ouiMap) {
                val cleanOui = oui.replace(Regex("[^0-9A-F]"), "").uppercase()
                if (cleanBssid.startsWith(cleanOui) || cleanOui.startsWith(cleanBssid)) {
                    return vendor
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getCredentials(vendor: String): List<DefaultCredential> {
        try {
            val key = vendor.lowercase()
            for ((v, creds) in credentialsMap) {
                if (key.contains(v) || v.contains(key)) {
                    return creds
                }
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    fun getCves(vendor: String): List<CveVulnerability> {
        try {
            val key = vendor.lowercase()
            val result = mutableListOf<CveVulnerability>()
            for ((target, cves) in cveMap) {
                if (key.contains(target) || target.contains(key)) {
                    result.addAll(cves)
                }
            }
            return result
        } catch (_: Exception) {}
        return emptyList()
    }

    suspend fun refreshCache(context: Context, dao: AuditDao) {
        withContext(Dispatchers.IO) {
            try {
                isInitialized = false
                initialize(context, dao)
            } catch (_: Exception) {}
        }
    }
}
