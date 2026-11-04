package com.omni.wifi.audit.domain.hardware

import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.security.RootCapabilityManager
import com.omni.wifi.audit.domain.diagnostic.security.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Values gathered from Android system properties and readable kernel interfaces. */
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
    /** Android 9+ restricts access to several legacy Wi-Fi supplicant interfaces. */
    val isSupplicantSocketRestricted: Boolean
)

/** Collect only values reported by Android/Linux; never substitute example device values. */
object DeviceCapabilityManager {
    suspend fun inspect(): SystemCapabilities = withContext(Dispatchers.IO) {
        val kernel = readFirstLine(File("/proc/version"))
            ?: System.getProperty("os.version")?.takeIf(String::isNotBlank)
            ?: "Недоступно"
        val kernelShort = Regex("\\d+\\.\\d+(?:\\.\\d+)?").find(kernel)?.value ?: "Не определено"
        val rootResult = try { RootCapabilityManager.inspectRoot() } catch (_: Exception) { null }
        val selinux = try {
            File("/sys/fs/selinux/enforce").takeIf { it.canRead() }?.readText()?.trim()?.let {
                when (it) { "1" -> "Enforcing"; "0" -> "Permissive"; else -> "Не определено ($it)" }
            }
        } catch (_: Exception) { null } ?: "Недоступно"

        SystemCapabilities(
            deviceModel = Build.MODEL?.takeIf(String::isNotBlank) ?: "Не определено",
            manufacturer = Build.MANUFACTURER?.takeIf(String::isNotBlank) ?: "Не определен",
            androidVersion = Build.VERSION.RELEASE?.takeIf(String::isNotBlank) ?: "Не определена",
            apiLevel = Build.VERSION.SDK_INT,
            kernelFull = kernel,
            kernelShort = kernelShort,
            romDisplay = Build.DISPLAY?.takeIf(String::isNotBlank) ?: "Не определена",
            isRootGranted = rootResult?.status == RootStatus.ROOT_GRANTED,
            rootIdOutput = rootResult?.uid,
            selinuxMode = selinux,
            isSupplicantSocketRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        )
    }

    private fun readFirstLine(file: File): String? = try {
        file.takeIf { it.canRead() }?.useLines { lines -> lines.firstOrNull()?.trim()?.takeIf(String::isNotBlank) }
    } catch (_: Exception) { null }
}
