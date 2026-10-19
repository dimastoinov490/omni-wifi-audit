package com.omni.wifi.audit.data

import com.omni.wifi.audit.data.dao.AuditDao
import com.omni.wifi.audit.data.entity.*

object DataInitializer {
    suspend fun populate(dao: AuditDao) {
        dao.insertOuis(listOf(
            VendorOUI("00:0C:42", "TP-Link"),
            VendorOUI("28:28:5D", "Keenetic"),
            VendorOUI("00:1E:58", "D-Link"),
            VendorOUI("C8:3A:35", "Tenda"),
            VendorOUI("BC:3F:8F", "Xiaomi"),
            VendorOUI("E4:60:E2", "Huawei"),
            VendorOUI("48:8F:5A", "Mikrotik"),
            VendorOUI("B0:6E:BF", "ASUS")
        ))

        dao.insertCredentials(listOf(
            DefaultCredential(vendor = "TP-Link", model = "Archer", username = "admin", password = "admin"),
            DefaultCredential(vendor = "D-Link", model = "DIR", username = "admin", password = ""),
            DefaultCredential(vendor = "Tenda", model = "F3", username = "admin", password = "admin"),
            DefaultCredential(vendor = "Xiaomi", model = "MiRouter", username = "admin", password = "password123")
        ))

        dao.insertCves(listOf(
            CveVulnerability("CVE-2017-14491", "D-Link", "Remote Code Execution in DNSmasq", "Critical", "< 2.78"),
            CveVulnerability("CVE-2021-27101", "TP-Link", "Command Injection in web interface", "High", "Archer C50 v3")
        ))
    }
}
