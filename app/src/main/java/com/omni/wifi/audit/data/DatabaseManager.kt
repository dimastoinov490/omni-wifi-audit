package com.omni.wifi.audit.data

import android.content.Context
import android.net.Uri
import com.omni.wifi.audit.data.dao.AuditDao
import com.omni.wifi.audit.data.entity.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class DatabaseExport(
    val ouis: List<VendorOUI> = emptyList(),
    val credentials: List<DefaultCredential> = emptyList(),
    val cves: List<CveVulnerability> = emptyList(),
    val wpsAlgorithms: List<WpsAlgorithm> = emptyList()
)

object DatabaseManager {
    private val client = HttpClient(Android)

    private fun getDbFile(context: Context, filename: String): File {
        return File(context.filesDir, filename)
    }

    suspend fun saveDefaultDatabaseSnapshot(context: Context, dao: AuditDao) {
        try {
            val file = getDbFile(context, "omni_audit_db.json")
            if (!file.exists()) {
                val initial = DatabaseExport(
                    ouis = listOf(
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
                    ),
                    credentials = listOf(
                        DefaultCredential(vendor = "TP-Link", model = "Archer", username = "admin", password = "admin"),
                        DefaultCredential(vendor = "D-Link", model = "DIR", username = "admin", password = ""),
                        DefaultCredential(vendor = "Tenda", model = "F3", username = "admin", password = "admin"),
                        DefaultCredential(vendor = "Xiaomi", model = "MiRouter", username = "admin", password = "password123"),
                        DefaultCredential(vendor = "ASUS", model = "RT", username = "admin", password = "admin"),
                        DefaultCredential(vendor = "Keenetic", model = "Giga", username = "admin", password = "admin"),
                        DefaultCredential(vendor = "Huawei", model = "HG", username = "admin", password = "admin")
                    ),
                    cves = listOf(
                        CveVulnerability("CVE-2017-14491", "D-Link", "Remote Code Execution in DNSmasq", "Critical", "< 2.78"),
                        CveVulnerability("CVE-2021-27101", "TP-Link", "Command Injection in web interface", "High", "Archer C50 v3"),
                        CveVulnerability("CVE-2022-35805", "ASUS", "Remote Code Execution", "Critical", "RT-AC68U < 3.0.0.4"),
                        CveVulnerability("CVE-2023-1380", "TP-Link", "Command Injection", "Critical", "Archer AX21")
                    )
                )

                val rootObj = JSONObject().apply {
                    put("ouis", JSONArray().apply {
                        initial.ouis.forEach { oui ->
                            put(JSONObject().apply {
                                put("oui", oui.oui)
                                put("vendor", oui.vendor)
                            })
                        }
                    })
                    put("credentials", JSONArray().apply {
                        initial.credentials.forEach { cred ->
                            put(JSONObject().apply {
                                put("vendor", cred.vendor)
                                put("model", cred.model)
                                put("username", cred.username)
                                put("password", cred.password)
                            })
                        }
                    })
                    put("cves", JSONArray().apply {
                        initial.cves.forEach { cve ->
                            put(JSONObject().apply {
                                put("cveId", cve.cveId)
                                put("targetModel", cve.targetModel)
                                put("description", cve.description)
                                put("severity", cve.severity)
                                put("affectedVersions", cve.affectedVersions)
                            })
                        }
                    })
                }
                file.writeText(rootObj.toString())
            }
        } catch (_: Exception) {}
    }

    suspend fun updateFromGithub(context: Context, dao: AuditDao, url: String = "https://raw.githubusercontent.com/omni-wifi/audit-db/main/db.json"): Result<Int> {
        return try {
            val response: HttpResponse = client.get(url)
            val content = response.bodyAsText()
            parseAndApply(context, dao, content)
        } catch (e: Exception) {
            try {
                val file = getDbFile(context, "omni_audit_db.json")
                if (file.exists()) {
                    val content = file.readText()
                    parseAndApply(context, dao, content)
                } else {
                    Result.failure(e)
                }
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        }
    }

    suspend fun updateFromUri(context: Context, dao: AuditDao, uri: Uri): Result<Int> {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader().use { it?.readText() } ?: throw Exception("Не удалось прочитать файл")
            parseAndApply(context, dao, content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun parseAndApply(context: Context, dao: AuditDao, content: String): Result<Int> {
        return try {
            val file = getDbFile(context, "omni_audit_db.json")
            file.writeText(content)

            val rootObj = JSONObject(content)
            
            val ouisList = mutableListOf<VendorOUI>()
            val ouisJson = rootObj.optJSONArray("ouis")
            if (ouisJson != null) {
                for (i in 0 until ouisJson.length()) {
                    val obj = ouisJson.getJSONObject(i)
                    ouisList.add(VendorOUI(
                        oui = obj.optString("oui"),
                        vendor = obj.optString("vendor")
                    ))
                }
            }

            val credsList = mutableListOf<DefaultCredential>()
            val credsJson = rootObj.optJSONArray("credentials")
            if (credsJson != null) {
                for (i in 0 until credsJson.length()) {
                    val obj = credsJson.getJSONObject(i)
                    credsList.add(DefaultCredential(
                        vendor = obj.optString("vendor"),
                        model = obj.optString("model"),
                        username = obj.optString("username"),
                        password = obj.optString("password")
                    ))
                }
            }

            val cvesList = mutableListOf<CveVulnerability>()
            val cvesJson = rootObj.optJSONArray("cves")
            if (cvesJson != null) {
                for (i in 0 until cvesJson.length()) {
                    val obj = cvesJson.getJSONObject(i)
                    cvesList.add(CveVulnerability(
                        cveId = obj.optString("cveId"),
                        targetModel = obj.optString("targetModel"),
                        description = obj.optString("description"),
                        severity = obj.optString("severity"),
                        affectedVersions = obj.optString("affectedVersions")
                    ))
                }
            }

            val wpsList = mutableListOf<WpsAlgorithm>()
            val wpsJson = rootObj.optJSONArray("wpsAlgorithms")
            if (wpsJson != null) {
                for (i in 0 until wpsJson.length()) {
                    val obj = wpsJson.getJSONObject(i)
                    wpsList.add(WpsAlgorithm(
                        name = obj.optString("name"),
                        description = obj.optString("description"),
                        signature = obj.optString("signature")
                    ))
                }
            }

            val exportData = DatabaseExport(
                ouis = ouisList,
                credentials = credsList,
                cves = cvesList,
                wpsAlgorithms = wpsList
            )

            var count = 0
            if (exportData.ouis.isNotEmpty()) {
                dao.insertOuis(exportData.ouis)
                count += exportData.ouis.size
            }
            if (exportData.credentials.isNotEmpty()) {
                dao.insertCredentials(exportData.credentials)
                count += exportData.credentials.size
            }
            if (exportData.cves.isNotEmpty()) {
                dao.insertCves(exportData.cves)
                count += exportData.cves.size
            }
            if (exportData.wpsAlgorithms.isNotEmpty()) {
                dao.insertWpsAlgorithms(exportData.wpsAlgorithms)
                count += exportData.wpsAlgorithms.size
            }
            Result.success(count)
        } catch (e: Exception) {
            try {
                val lines = content.lines()
                val ouis = mutableListOf<VendorOUI>()
                for (line in lines) {
                    val parts = line.split(Regex("[,\\t\\s]+"))
                    if (parts.size >= 2) {
                        val oui = parts[0].trim().uppercase()
                        val vendor = parts.drop(1).joinToString(" ").trim()
                        if (oui.length >= 6 && vendor.isNotBlank()) {
                            ouis.add(VendorOUI(oui, vendor))
                        }
                    }
                }
                if (ouis.isNotEmpty()) {
                    dao.insertOuis(ouis)
                    return Result.success(ouis.size)
                }
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}
