package com.omni.wifi.audit.domain.diagnostic.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class NetworkInterfaceInfo(
    val name: String,
    val operstate: String,
    val macAddress: String,
    val isWireless: Boolean
)

object SysFsDataProvider {

    suspend fun listNetworkInterfaces(): List<NetworkInterfaceInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<NetworkInterfaceInfo>()
        val netDir = File("/sys/class/net")
        if (netDir.exists() && netDir.isDirectory) {
            netDir.listFiles()?.forEach { ifaceDir ->
                val name = ifaceDir.name
                val operstate = File(ifaceDir, "operstate").takeIf { it.exists() }?.readText()?.trim() ?: "unknown"
                val mac = File(ifaceDir, "address").takeIf { it.exists() }?.readText()?.trim() ?: ""
                val isWireless = File(ifaceDir, "wireless").exists() || File(ifaceDir, "phy80211").exists() || name.startsWith("wlan")
                list.add(NetworkInterfaceInfo(name, operstate, mac, isWireless))
            }
        }
        list
    }

    suspend fun getDriverModuleInfo(moduleName: String = "wlan"): Map<String, String> = withContext(Dispatchers.IO) {
        val info = mutableMapOf<String, String>()
        val moduleDir = File("/sys/module/$moduleName")
        if (moduleDir.exists()) {
            info["exists"] = "true"
            val versionFile = File(moduleDir, "version")
            if (versionFile.exists()) {
                info["version"] = versionFile.readText().trim()
            }
            val paramsDir = File(moduleDir, "parameters")
            if (paramsDir.exists() && paramsDir.isDirectory) {
                paramsDir.listFiles()?.take(10)?.forEach { param ->
                    try {
                        if (param.canRead() && param.isFile) {
                            info["param_${param.name}"] = param.readText().trim()
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            info["exists"] = "false"
        }
        info
    }
}
