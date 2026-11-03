package com.omni.wifi.audit.domain.diagnostic.providers

import android.content.Context
import com.omni.wifi.audit.data.AuditDatabase
import com.omni.wifi.audit.data.DatabaseCacheManager
import com.omni.wifi.audit.data.entity.CveVulnerability
import com.omni.wifi.audit.data.entity.DefaultCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseDataProvider {

    suspend fun lookupVendor(context: Context, macAddress: String): String? = withContext(Dispatchers.IO) {
        val cached = DatabaseCacheManager.getVendor(macAddress)
        if (cached != null) return@withContext cached

        try {
            val db = AuditDatabase.getDatabase(context)
            val cleanMac = macAddress.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
            if (cleanMac.length >= 6) {
                val prefix = cleanMac.substring(0, 6)
                db.auditDao().getVendorByOui(prefix)?.vendor
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findCves(context: Context, query: String): List<CveVulnerability> = withContext(Dispatchers.IO) {
        val cached = DatabaseCacheManager.getCves(query)
        if (cached.isNotEmpty()) return@withContext cached

        try {
            val db = AuditDatabase.getDatabase(context)
            db.auditDao().findCvesForModel(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun findCredentials(context: Context, vendor: String): List<DefaultCredential> = withContext(Dispatchers.IO) {
        val cached = DatabaseCacheManager.getCredentials(vendor)
        if (cached.isNotEmpty()) return@withContext cached

        try {
            val db = AuditDatabase.getDatabase(context)
            db.auditDao().findCredentials(vendor)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
