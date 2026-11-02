package com.omni.wifi.audit.domain.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class QualcommQcacldDriverHardware : IWifiDriverHardware {
    override val hardwareName: String = "Qualcomm Atheros qcacld-3.0 (WCNSS / SDM636)"

    override suspend fun probe(hasRoot: Boolean): DriverCapabilityInfo = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()
        val supportedModes = mutableListOf<String>()
        var isInterfaceUp = false
        var isMonitorSupported = false
        var isInjectionSupported = false

        notes.add("Аппаратная платформа: Qualcomm Snapdragon 636 (whyred)")
        notes.add("Драйвер ядра: qcacld-3.0 (модуль wlan.ko / встроенный драйвер)")

        if (hasRoot) {
            try {
                // 1. Check wlan0 status
                val linkProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip link show wlan0 2>&1"))
                val linkReader = BufferedReader(InputStreamReader(linkProc.inputStream))
                val linkOutput = linkReader.readText()
                linkProc.waitFor()
                if (linkOutput.contains("state UP", ignoreCase = true) || linkOutput.contains("<UP", ignoreCase = true)) {
                    isInterfaceUp = true
                    notes.add("Интерфейс wlan0 активен (UP)")
                } else if (linkOutput.contains("wlan0")) {
                    notes.add("Интерфейс wlan0 зарегистрирован в стеке ядра")
                }

                // 2. Query supported interface modes via iw
                val iwProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "iw phy phy0 info 2>&1 || iw list 2>&1"))
                val iwReader = BufferedReader(InputStreamReader(iwProc.inputStream))
                val iwOutput = iwReader.readText()
                iwProc.waitFor()

                if (iwOutput.contains("Supported interface modes", ignoreCase = true)) {
                    val lines = iwOutput.lines()
                    var inModes = false
                    for (line in lines) {
                        if (line.contains("Supported interface modes", ignoreCase = true)) {
                            inModes = true
                            continue
                        }
                        if (inModes) {
                            if (line.trim().startsWith("*")) {
                                val mode = line.trim().removePrefix("*").trim()
                                supportedModes.add(mode)
                            } else if (line.isNotBlank() && !line.startsWith(" ")) {
                                break
                            }
                        }
                    }
                }

                if (supportedModes.isEmpty()) {
                    // Standard Qualcomm qcacld modes
                    supportedModes.addAll(listOf("managed (STA)", "AP", "P2P-client", "P2P-GO"))
                }

                isMonitorSupported = supportedModes.any { it.contains("monitor", ignoreCase = true) }
                isInjectionSupported = isMonitorSupported // Packet injection requires monitor mode

                if (isMonitorSupported) {
                    notes.add("[+] В драйвере подтверждена поддержка Monitor Mode!")
                } else {
                    notes.add("[-] Режим Monitor Mode не заявлен в текущей сборке драйвера qcacld-3.0")
                    notes.add("[i] Стандартная прошивка WCNSS отбрасывает 802.11 management-кадры до попадания в userspace")
                }
            } catch (e: Exception) {
                notes.add("Ошибка выполнения команд через su: ${e.localizedMessage}")
                supportedModes.addAll(listOf("managed", "AP", "P2P"))
            }
        } else {
            notes.add("Проверка выполнена без root: детальный опрос ioctl/netlink ограничен правами Android SELinux")
            supportedModes.addAll(listOf("managed (клиент)"))
        }

        DriverCapabilityInfo(
            driverName = "qcacld-3.0",
            chipset = "Qualcomm WCN39xx / WCN36xx (Snapdragon 636)",
            interfaceName = "wlan0",
            isInterfaceUp = isInterfaceUp,
            supportedModes = supportedModes,
            isMonitorModeSupported = isMonitorSupported,
            isPacketInjectionSupported = isInjectionSupported,
            kernelModule = "wlan.ko",
            notes = notes
        )
    }
}
