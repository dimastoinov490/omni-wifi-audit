package com.omni.wifi.audit.domain.hardware

import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.security.RootCapabilityManager
import com.omni.wifi.audit.domain.diagnostic.security.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Collect only values reported by Android/Linux; never substitute example device values. */
object DeviceCapabilityManager {
    suspend fun inspect(): SystemCapabilities = withContext(Dispatchers.IO) {
        val kernel = readFirstLine(File("/proc/version"))
            ?: System.getProperty("os.version")?.takeIf(String::isNotBlank)
            ?: "Недоступно"
        val kernelShort = Regex("\\d+\\.\\d+(?:\\.\\d+)?").find(kernel)?.value ?: "Не определено"
        val rootResult = try { RootCapabilityManager.inspectRoot() } catch (_: Exception) { null }
        val rootGranted = rootResult?.status == RootStatus.ROOT_GRANTED
        val selinux = try {
            File("/sys/fs/selinux/enforce").takeIf { it.canRead() }?.readText()?.trim()?.let {
                when (it) { "1" -> "Enforcing"; "0" -> "Permissive"; else -> "Не определено ($it)" }
            }
        } catch (_: Exception) { null } ?: "Недоступно"
        val interfaces = try { File("/sys/class/net").list()?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        SystemCapabilities(
            deviceModel = Build.MODEL?.takeIf(String::isNotBlank) ?: "Не определено",
            manufacturer = Build.MANUFACTURER?.takeIf(String::isNotBlank) ?: "Не определен",
            androidVersion = Build.VERSION.RELEASE?.takeIf(String::isNotBlank) ?: "Не определена",
            apiLevel = Build.VERSION.SDK_INT,
            kernelFull = kernel,
            kernelShort = kernelShort,
            romDisplay = Build.DISPLAY?.takeIf(String::isNotBlank) ?: "Не определена",
            isRootGranted = rootGranted,
            rootIdOutput = rootResult?.uid,
            selinuxMode = selinux,
            isSupplicantSocketRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && interfaces.isEmpty()
        )
    }

    private fun readFirstLine(file: File): String? = try {
        file.takeIf { it.canRead() }?.useLines { lines -> lines.firstOrNull()?.trim()?.takeIf(String::isNotBlank) }
    } catch (_: Exception) { null }
}
