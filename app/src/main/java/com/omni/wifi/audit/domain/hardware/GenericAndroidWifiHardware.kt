package com.omni.wifi.audit.domain.hardware

import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.SysFsDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenericAndroidWifiHardware : IWifiDriverHardware {
    override val hardwareName: String = "Android Wi-Fi (runtime inspection)"

    override suspend fun probe(hasRoot: Boolean): DriverCapabilityInfo = withContext(Dispatchers.IO) {
        val iface = SysFsDataProvider.listNetworkInterfaces().firstOrNull { it.isWireless }
        val command = if (hasRoot) LinuxCommandDataProvider.runSu("iw list") else LinuxCommandDataProvider.runSh("iw list")
        val modes = mutableListOf<String>()
        var inModes = false
        command.stdout.lineSequence().forEach { line ->
            if (line.contains("Supported interface modes", ignoreCase = true)) {
                inModes = true
                return@forEach
            }
            if (inModes) {
                val trimmed = line.trim()
                if (trimmed.startsWith("*")) modes += trimmed.removePrefix("*").trim()
                else if (trimmed.isNotEmpty() && !line.startsWith(" ")) inModes = false
            }
        }
        val driver = iface?.name?.let { name ->
            try {
                java.io.File("/sys/class/net/$name/device/driver").canonicalFile.name
            } catch (_: Exception) { null }
        }
        DriverCapabilityInfo(
            driverName = driver ?: "Не определен",
            chipset = listOf(Build.HARDWARE, Build.BOARD).filter(String::isNotBlank).distinct().joinToString(" / ").ifBlank { "Не определен" },
            interfaceName = iface?.name ?: "Не обнаружен",
            isInterfaceUp = iface?.operstate.equals("up", ignoreCase = true),
            supportedModes = modes.distinct(),
            isMonitorModeSupported = command.exitCode == 0 && modes.any { it.contains("monitor", true) },
            // Capability probing alone cannot demonstrate safe, functional arbitrary-frame transmission.
            isPacketInjectionSupported = false,
            kernelModule = driver ?: "Не определен",
            notes = buildList {
                add("Сведения получены из runtime sysfs и доступного вывода iw; предположительные значения не подставляются.")
                add(if (command.exitCode == 0) "iw list завершилась успешно." else "iw list недоступна: ${command.stderr.ifBlank { "нет утилиты или доступа" }}")
                add("Активные режимы Wi-Fi не менялись; произвольные кадры не передавались.")
            }
        )
    }
}
