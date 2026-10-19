package com.omni.wifi.audit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vendor_oui")
data class VendorOUI(
    @PrimaryKey val oui: String, // MAC Prefix (e.g., "00:0C:42")
    val vendor: String
)

@Entity(tableName = "wps_algorithms")
data class WpsAlgorithm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val signature: String // Regex or pattern to match BSSID/SSID
)

@Entity(tableName = "default_credentials")
data class DefaultCredential(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendor: String,
    val model: String,
    val username: String,
    val password: String
)

@Entity(tableName = "cve_vulnerabilities")
data class CveVulnerability(
    @PrimaryKey val cveId: String,
    val targetModel: String,
    val description: String,
    val severity: String, // Low, Medium, High, Critical
    val affectedVersions: String
)
