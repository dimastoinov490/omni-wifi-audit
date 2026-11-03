package com.omni.wifi.audit.domain.diagnostic.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ProcFsDataProvider {

    suspend fun readKernelVersion(): String = withContext(Dispatchers.IO) {
        try {
            val file = File("/proc/version")
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else {
                System.getProperty("os.version") ?: "Недоступно"
            }
        } catch (e: Exception) {
            "Ошибка чтения /proc/version: ${e.message}"
        }
    }

    suspend fun readCpuInfo(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val file = File("/proc/cpuinfo")
            if (file.exists() && file.canRead()) {
                file.forEachLine { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        if (key in listOf("Hardware", "Processor", "model name", "Features", "CPU architecture") && !result.containsKey(key)) {
                            result[key] = value
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        result
    }

    suspend fun readWirelessStats(): String? = withContext(Dispatchers.IO) {
        try {
            val file = File("/proc/net/wireless")
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
