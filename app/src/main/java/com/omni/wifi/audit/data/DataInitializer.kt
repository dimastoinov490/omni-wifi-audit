package com.omni.wifi.audit.data

import com.omni.wifi.audit.data.dao.AuditDao
import com.omni.wifi.audit.data.entity.*

object DataInitializer {
    suspend fun populate(dao: AuditDao) {
        dao.insertOuis(listOf(
            VendorOUI("00:0C:42", "TP-Link"),
            VendorOUI("18:E8:29", "TP-Link"),
            VendorOUI("50:C7:BF", "TP-Link"),
            VendorOUI("CC:2D:E0", "TP-Link"),
            VendorOUI("60:E3:27", "TP-Link"),
            VendorOUI("E8:48:B8", "TP-Link"),
            VendorOUI("B4:3A:28", "TP-Link"),
            
            VendorOUI("28:28:5D", "Keenetic"),
            VendorOUI("64:70:02", "Keenetic"),
            VendorOUI("10:7B:44", "Keenetic"),

            VendorOUI("00:1E:58", "D-Link"),
            VendorOUI("00:24:01", "D-Link"),
            VendorOUI("1C:7E:E5", "D-Link"),
            VendorOUI("B8:A3:86", "D-Link"),

            VendorOUI("C8:3A:35", "Tenda"),
            VendorOUI("74:DA:DA", "Tenda"),
            VendorOUI("50:2B:73", "Tenda"),

            VendorOUI("BC:3F:8F", "Xiaomi"),
            VendorOUI("28:6C:07", "Xiaomi"),
            VendorOUI("64:09:80", "Xiaomi"),
            VendorOUI("78:11:DC", "Xiaomi"),

            VendorOUI("E4:60:E2", "Huawei"),
            VendorOUI("00:E0:FC", "Huawei"),
            VendorOUI("48:69:CC", "Huawei"),
            VendorOUI("88:36:6C", "Huawei"),

            VendorOUI("48:8F:5A", "Mikrotik"),
            VendorOUI("64:D1:54", "Mikrotik"),
            VendorOUI("B8:69:F4", "Mikrotik"),

            VendorOUI("B0:6E:BF", "ASUS"),
            VendorOUI("9C:93:4E", "ASUS"),
            VendorOUI("AC:84:C6", "ASUS"),
            VendorOUI("04:92:26", "ASUS"),
            VendorOUI("F8:32:E4", "ASUS"),

            VendorOUI("00:1B:2F", "Netgear"),
            VendorOUI("9C:D3:6D", "Netgear"),
            VendorOUI("C4:04:15", "Netgear")
        ))

        dao.insertCredentials(listOf(
            DefaultCredential(vendor = "TP-Link", model = "Archer", username = "admin", password = "admin"),
            DefaultCredential(vendor = "D-Link", model = "DIR", username = "admin", password = ""),
            DefaultCredential(vendor = "Tenda", model = "F3", username = "admin", password = "admin"),
            DefaultCredential(vendor = "Xiaomi", model = "MiRouter", username = "admin", password = "password123"),
            DefaultCredential(vendor = "ASUS", model = "RT", username = "admin", password = "admin"),
            DefaultCredential(vendor = "Keenetic", model = "Giga", username = "admin", password = "admin"),
            DefaultCredential(vendor = "Huawei", model = "HG", username = "admin", password = "admin")
        ))

        dao.insertCves(listOf(
            CveVulnerability("CVE-2017-14491", "D-Link", "Remote Code Execution in DNSmasq", "Critical", "< 2.78"),
            CveVulnerability("CVE-2021-27101", "TP-Link", "Command Injection in web interface", "High", "Archer C50 v3"),
            CveVulnerability("CVE-2022-35805", "ASUS", "Remote Code Execution", "Critical", "RT-AC68U < 3.0.0.4"),
            CveVulnerability("CVE-2023-1380", "TP-Link", "Command Injection", "Critical", "Archer AX21")
        ))
    }
}
