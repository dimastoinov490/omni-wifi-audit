package com.omni.wifi.audit.domain.hardware

import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.SysFsDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class QualcommQcacldDriverHardware : IWifiDriverHardware {
    override val hardwareName: String = "Qualcomm Wi-Fi (detected from device data)"

    override suspend fun probe(hasRoot: Boolean): DriverCapabilityInfo = withContext(Dispatchers.IO) {
        val interfaces = SysFsDataProvider.listNetworkInterfaces()
        val wifi = interfaces.firstOrNull { it.isWireless }
        val iface = wifi?.name
        val driverPath = iface?.let { File("/sys/class/net", it).resolve("device/driver").canonicalFile }
        val detectedDriver = driverPath?.name?.takeIf { it.isNotBlank() }
        val moduleName = detectedDriver ?: "unknown"
        val command = if (hasRoot) LinuxCommandDataProvider.runSu("iw list") else LinuxCommandDataProvider.runSh("iw list")
        val modes = parseSupportedModes(command.stdout)
        val monitorSupported = modes.any { it.contains("monitor", ignoreCase = true) }
        val notes = buildList {
            add("Определение по текущим интерфейсам и sysfs; режим интерфейса не изменялся.")
            add(if (iface == null) "Беспроводной интерфейс не найден в /sys/class/net." else "Найден интерфейс $iface (${wifi?.operstate ?: "состояние недоступно"}).")
            add(if (detectedDriver == null) "Имя драйвера через sysfs не определено." else "Драйвер из sysfs: $detectedDriver.")
            add(if (command.exitCode == 0) "Поддерживаемые режимы прочитаны из вывода iw list." else "Не удалось получить возможности через iw: ${command.stderr.ifBlank { "команда отсутствует или запрещена" }}")
            add("Возможность передачи произвольных кадров не проверялась: активные передающие тесты не запускаются.")
        }
        DriverCapabilityInfo(
            driverName = detectedDriver ?: "Не определен",
            chipset = listOf(Build.HARDWARE, Build.BOARD).filter { it.isNotBlank() }.distinct().joinToString(" / ").ifBlank { "Не определен" },
            interfaceName = iface ?: "Не обнаружен",
            isInterfaceUp = wifi?.operstate.equals("up", ignoreCase = true),
            supportedModes = modes,
            isMonitorModeSupported = when {
                command.exitCode != 0 -> false
                modes.isEmpty() -> false
                else -> monitorSupported
            },
            isPacketInjectionSupported = false,
            kernelModule = moduleName,
            notes = notes
        )
    }

    private fun parseSupportedModes(output: String): List<String> {
        val result = mutableListOf<String>()
        var inModes = false
        output.lineSequence().forEach { line ->
            if (line.contains("Supported interface modes", ignoreCase = true)) {
                inModes = true
                return@forEach
            }
            if (inModes) {
                val trimmed = line.trim()
                if (trimmed.startsWith("*")) result += trimmed.removePrefix("*").trim()
                else if (trimmed.isNotEmpty() && !line.startsWith(" ")) inModes = false
                else if (trimmed.startsWith("Band ") || trimmed.startsWith(" valid interface combinations")) inModes = false
            }
        }
        return result.distinct()
    }
}
