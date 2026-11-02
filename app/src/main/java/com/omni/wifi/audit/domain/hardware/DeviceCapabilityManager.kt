package com.omni.wifi.audit.domain.hardware

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class SystemCapabilities(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val kernelFull: String,
    val kernelShort: String,
    val romDisplay: String,
    val isRootGranted: Boolean,
    val rootIdOutput: String?,
    val selinuxMode: String,
    val isSupplicantSocketRestricted: Boolean
)

object DeviceCapabilityManager {

    suspend fun inspect(): SystemCapabilities = withContext(Dispatchers.IO) {
        val kernelFull = try {
            File("/proc/version").readText().lines().firstOrNull()?.trim()
                ?: (System.getProperty("os.version") ?: "Linux 4.4")
        } catch (_: Exception) {
            System.getProperty("os.version") ?: "Linux 4.4"
        }

        val kernelShort = when {
            kernelFull.contains("4.4") -> "4.4"
            kernelFull.contains("4.19") -> "4.19"
            else -> kernelFull.split(" ").firstOrNull { it.contains(".") } ?: "4.4"
        }

        val rom = Build.DISPLAY ?: "Pixel Experience (AOSP)"
        val device = Build.MODEL ?: "Redmi Note 5"
        val mfg = Build.MANUFACTURER ?: "Xiaomi"

        // Check Root via su
        var isRoot = false
        var rootId: String? = null
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val line = r.readLine()
            p.waitFor()
            if (line != null && line.contains("uid=0")) {
                isRoot = true
                rootId = line
            }
        } catch (_: Exception) {}

        // Check SELinux
        var selinux = "Enforcing"
        if (isRoot) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "getenforce"))
                val r = BufferedReader(InputStreamReader(p.inputStream))
                val mode = r.readLine()?.trim()
                p.waitFor()
                if (!mode.isNullOrBlank()) {
                    selinux = mode
                }
            } catch (_: Exception) {}
        }

        val isSupplicantRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        SystemCapabilities(
            deviceModel = device,
            manufacturer = mfg,
            androidVersion = Build.VERSION.RELEASE ?: "13",
            apiLevel = Build.VERSION.SDK_INT,
            kernelFull = kernelFull,
            kernelShort = kernelShort,
            romDisplay = rom,
            isRootGranted = isRoot,
            rootIdOutput = rootId,
            selinuxMode = selinux,
            isSupplicantSocketRestricted = isSupplicantRestricted
        )
    }
}
