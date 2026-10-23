package com.omni.wifi.audit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omni.wifi.audit.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Query("SELECT * FROM vendor_oui WHERE oui = :prefix LIMIT 1")
    suspend fun getVendorByOui(prefix: String): VendorOUI?

    @Query("SELECT * FROM vendor_oui")
    suspend fun getAllOuis(): List<VendorOUI>

    @Query("SELECT * FROM wps_algorithms")
    fun getAllWpsAlgorithms(): Flow<List<WpsAlgorithm>>

    @Query("SELECT * FROM default_credentials WHERE vendor LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%'")
    suspend fun findCredentials(query: String): List<DefaultCredential>

    @Query("SELECT * FROM cve_vulnerabilities WHERE targetModel LIKE '%' || :model || '%'")
    suspend fun findCvesForModel(model: String): List<CveVulnerability>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOuis(ouis: List<VendorOUI>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWpsAlgorithms(algorithms: List<WpsAlgorithm>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredentials(creds: List<DefaultCredential>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCves(cves: List<CveVulnerability>)
}
