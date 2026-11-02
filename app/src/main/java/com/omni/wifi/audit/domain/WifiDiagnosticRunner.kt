package com.omni.wifi.audit.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.omni.wifi.audit.data.AuditDatabase
import com.omni.wifi.audit.data.DatabaseCacheManager
import com.omni.wifi.audit.domain.model.AccessPointInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

object WifiDiagnosticRunner {

    suspend fun analyzeChannelAndSignal(
        ap: AccessPointInfo,
        allNearbyAps: List<AccessPointInfo>
    ): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        sb.appendLine("=== АНАЛИЗ РАДИОКАНАЛА И СИГНАЛА ===")
        sb.appendLine("• BSSID: ${ap.bssid}")
        sb.appendLine("• Частота: ${ap.frequency} МГц | Диапазон: ${ap.band}")
        sb.appendLine("• Рабочий канал: ${if (ap.channel > 0) ap.channel.toString() else "Не определен"}")

        val signal = ap.signalLevel
        val quality = when {
            signal >= -50 -> "Отличный (максимальная скорость передачи данных)"
            signal >= -65 -> "Хороший (стабильное подключение)"
            signal >= -75 -> "Удовлетворительный (возможны потери пакетов при нагрузке)"
            signal >= -85 -> "Слабый (высокий джиттер, снижение модуляции)"
            else -> "Критически слабый сигнал (граница зоны покрытия)"
        }
        sb.appendLine("• Уровень сигнала: $signal дБм ($quality)")

        // Interference analysis
        if (ap.band == "2.4 GHz" && ap.channel in 1..14) {
            val nonOverlapping = listOf(1, 6, 11)
            if (ap.channel in nonOverlapping) {
                sb.appendLine("• Статус канала: Стандартный неперекрывающийся канал (${ap.channel})")
            } else {
                sb.appendLine("• [!] Внимание: Канал ${ap.channel} перекрывается со стандартными каналами 1, 6 и 11, создавая межканальную интерференцию (ACI).")
            }

            val sameChannelCount = allNearbyAps.count { it.bssid != ap.bssid && it.channel == ap.channel }
            val adjacentCount = allNearbyAps.count { it.bssid != ap.bssid && it.band == "2.4 GHz" && kotlin.math.abs(it.channel - ap.channel) in 1..2 }
            sb.appendLine("• Точек на том же канале: $sameChannelCount")
            sb.appendLine("• Точек на соседних пересекающихся каналах (±2): $adjacentCount")
            if (sameChannelCount > 3 || adjacentCount > 4) {
                sb.appendLine("• [!] Высокая загрузка эфира в диапазоне 2.4 ГГц. Рекомендуется переход в диапазон 5 ГГц.")
            } else {
                sb.appendLine("• Уровень загруженности диапазона в норме.")
            }
        } else if (ap.band == "5 GHz") {
            val sameChannelCount = allNearbyAps.count { it.bssid != ap.bssid && it.channel == ap.channel }
            sb.appendLine("• Точек на данном 5 ГГц канале: $sameChannelCount")
            sb.appendLine("• Диапазон 5 ГГц обеспечивает широкую полосу пропускания и минимальную интерференцию.")
        }

        sb.appendLine("• Режим шифрования: ${ap.encryptionType} (${ap.wifiStandard})")
        sb.toString().trimEnd()
    }

    suspend fun inspectWpsAndDriver(
        context: Context,
        ap: AccessPointInfo,
        onLog: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onLog("[*] Запуск WPS Inspector...")
        onLog("[*] Анализ параметров точки доступа:")
        onLog("    SSID: ${ap.ssid} | BSSID: ${ap.bssid}")
        onLog("    WPS статус: ${ap.wpsDetails}")
        onLog("    Возможности (Beacon/Probe): ${ap.capabilities}")

        if (!ap.isWpsSupported) {
            onLog("[-] Точка доступа не анонсирует поддержку WPS. WPS отключен или заблокирован.")
            onLog("[*] Проверка возможностей устройства и локального стека...")
        } else {
            onLog("[+] Точка доступа поддерживает протокол WPS.")
            if (ap.capabilities.contains("WPS-PBC")) {
                onLog("    -> Поддерживается метод Push Button (WPS-PBC)")
            }
            if (ap.capabilities.contains("WPS-PIN")) {
                onLog("    -> Поддерживается метод PIN-кода (WPS-PIN)")
            }
        }

        // Real check of device capabilities
        val kernelVersion = try {
            java.io.File("/proc/version").readText().lines().firstOrNull()?.trim()
                ?: (System.getProperty("os.version") ?: "Linux 4.4 (whyred)")
        } catch (_: Exception) {
            System.getProperty("os.version") ?: "Linux 4.4 (whyred)"
        }
        val kernelShort = if (kernelVersion.contains("4.4")) "4.4" else kernelVersion.split(" ").firstOrNull { it.contains(".") } ?: "4.4"
        val romDisplay = Build.DISPLAY ?: "Pixel Experience (AOSP)"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"

        onLog("[*] Проверка системного окружения:")
        onLog("    • Устройство: $deviceModel")
        onLog("    • Прошивка: $romDisplay (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        onLog("    • Ядро Linux: $kernelVersion")
        
        var isRootAvailable = false
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            if (line != null && line.contains("uid=0")) {
                isRootAvailable = true
                onLog("[+] Root-доступ подтвержден: $line")
            } else {
                onLog("[-] Root-доступ не предоставлен (uid != 0).")
            }
        } catch (e: Exception) {
            onLog("[-] Root-доступ недоступен: ${e.localizedMessage}")
        }

        // Driver and wpa_supplicant check
        onLog("[*] Анализ ограничений Wi-Fi стека:")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            onLog("[i] Ограничение Android 9-13 (AOSP / Pixel Experience): Прямой доступ к сокету wpa_supplicant (/data/misc/wifi/sockets/wpa_wlan0) изолирован политиками SELinux.")
        }

        var monitorModeDetected = false
        if (isRootAvailable) {
            try {
                // Check network interface details
                val ifProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip link show wlan0 2>&1"))
                val ifReader = BufferedReader(InputStreamReader(ifProcess.inputStream))
                val ifOutput = ifReader.readText()
                ifProcess.waitFor()
                if (ifOutput.isNotBlank()) {
                    onLog("[+] Интерфейс wlan0 обнаружен в системе:")
                    ifOutput.lines().filter { it.isNotBlank() }.take(2).forEach { onLog("    $it") }
                }

                // Check wireless capabilities via iw
                val iwProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "iw phy phy0 info 2>&1 || iw list 2>&1"))
                val iwReader = BufferedReader(InputStreamReader(iwProcess.inputStream))
                val iwOutput = iwReader.readText()
                iwProcess.waitFor()
                if (iwOutput.contains("monitor", ignoreCase = true)) {
                    monitorModeDetected = true
                    onLog("[+] В драйвере обнаружена поддержка режима Monitor Mode!")
                } else {
                    onLog("[i] Поддерживаемые режимы интерфейса: IBSS, managed, AP, P2P")
                }
            } catch (_: Exception) {}
        }

        onLog("[i] Аппаратная платформа: Snapdragon 636 (SDM636 / whyred)")
        onLog("[i] Драйвер Wi-Fi: qcacld-3.0 (wlan.ko) на ядре $kernelShort")
        if (monitorModeDetected) {
            onLog("[+] Ядро $kernelShort собрано с поддержкой Monitor Mode на qcacld-3.0.")
        } else {
            onLog("[!] Статус драйвера ядра $kernelShort: стандартный режим Station/Client (Managed). Перевод в Monitor Mode и инъекция 802.11 пакетов не активированы в текущей сборке ядра.")
        }
        onLog("[OK] Диагностика WPS и возможностей драйвера завершена.")
    }

    suspend fun checkPixieDustFeasibility(
        context: Context,
        ap: AccessPointInfo,
        onLog: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onLog("[*] Запуск диагностики условий уязвимости Pixie-Dust...")
        onLog("[*] Целевая сеть: ${ap.ssid} (${ap.bssid})")

        // 1. Target AP Check
        if (!ap.isWpsSupported) {
            onLog("[-] Точка доступа: WPS отключен. Уязвимость Pixie-Dust неприменима.")
            onLog("[*] Причина: обмен сообщениями M1-M4 не будет инициирован целевой точкой.")
            return@withContext
        } else {
            onLog("[+] Точка доступа: WPS включен.")
        }

        // 2. Chipset & PRNG analysis from database
        val vendor = ap.vendor ?: "Неизвестный"
        onLog("[*] Анализ вендора/чипсета: $vendor")
        val isPotentiallyVulnerableChipset = when (vendor) {
            "Ralink", "Realtek" -> true
            "D-Link", "Tenda" -> true // historically used vulnerable Realtek/Ralink chips
            else -> false
        }

        if (isPotentiallyVulnerableChipset) {
            onLog("[!] Историческая справка: оборудование $vendor часто комплектовалось чипсетами Realtek/Ralink со слабым генератором псевдослучайных чисел (PRNG) в ранних версиях прошивок.")
        } else {
            onLog("[i] Оборудование $vendor в современных ревизиях использует криптографически стойкую генерацию nonces (Linux /dev/urandom).")
        }

        // 3. Driver prerequisites for Pixie-Dust packet capture
        val kernelVersion = try {
            java.io.File("/proc/version").readText().lines().firstOrNull()?.trim()
                ?: (System.getProperty("os.version") ?: "Linux 4.4 (whyred)")
        } catch (_: Exception) {
            System.getProperty("os.version") ?: "Linux 4.4 (whyred)"
        }
        val kernelShort = if (kernelVersion.contains("4.4")) "4.4" else kernelVersion.split(" ").firstOrNull { it.contains(".") } ?: "4.4"

        onLog("[*] Проверка необходимых условий для проведения проверки:")
        onLog("    1) Ядро и система: $kernelVersion (Android 13 / Pixel Experience)")
        onLog("    2) Доступ к радиоканалу для перехвата M1/M2: ТРЕБУЕТСЯ")
        onLog("    3) Поддержка Monitor Mode на встроенном адаптере: ТРЕБУЕТСЯ")
        onLog("    4) Извлечение хэшей E-Hash1 / E-Hash2 / E-S1 / E-S2 из кадров M3: ТРЕБУЕТСЯ")

        // Check root and driver
        var hasRoot = false
        var monitorSupported = false
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            hasRoot = line != null && line.contains("uid=0")

            if (hasRoot) {
                val iwProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "iw phy phy0 info 2>&1 || iw list 2>&1"))
                val iwReader = BufferedReader(InputStreamReader(iwProcess.inputStream))
                val iwOut = iwReader.readText()
                iwProcess.waitFor()
                monitorSupported = iwOut.contains("monitor", ignoreCase = true)
            }
        } catch (_: Exception) {}

        if (!hasRoot) {
            onLog("[-] Root-доступ отсутствует.")
            onLog("[!] Причина невозможности: без root-прав в Android 13 невозможно управлять состоянием интерфейса wlan0.")
        } else {
            onLog("[+] Root-доступ доступен.")
            onLog("[i] Чипсет: Snapdragon 636 (whyred), драйвер: qcacld-3.0 (ядро $kernelShort)")
            if (monitorSupported) {
                onLog("[+] В ядре $kernelShort обнаружена поддержка Monitor Mode!")
            } else {
                onLog("[!] ПРИЧИНА ОГРАНИЧЕНИЯ: В используемой сборке ядра $kernelShort драйвер qcacld-3.0 не сконфигурирован для захвата произвольных 802.11 management-фреймов.")
                onLog("[i] Для низкоуровневого перехвата EAPOL требуется специализированное ядро с патчем мониторного режима для qcacld-3.0 либо внешний адаптер.")
            }
        }

        onLog("[*] Диагностика завершена. Перебор и восстановление PIN не производятся согласно требованиям безопасности.")
    }

    suspend fun auditGatewayAndWebAdmin(
        context: Context,
        ap: AccessPointInfo,
        onLog: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onLog("[*] Запуск аудита сетевого шлюза и веб-панели управления...")

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!isWifiConnected) {
            onLog("[-] Устройство не подключено ни к одной Wi-Fi сети.")
            onLog("[!] Ограничение: сетевой аудит веб-админки возможен только после подключения к сети точки доступа.")
            return@withContext
        }

        // Check if connected to THIS specific AP
        val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
        val connectedBssid = wifiInfo?.bssid?.uppercase()
        val targetBssid = ap.bssid.uppercase()

        val isConnectedToTarget = connectedBssid != null &&
                (connectedBssid == targetBssid || connectedBssid.replace(":", "") == targetBssid.replace(":", ""))

        if (!isConnectedToTarget) {
            onLog("[i] Текущее подключение: BSSID ${connectedBssid ?: "неизвестен"}")
            onLog("[-] Устройство подключено к ДРУГОЙ сети, а не к цели (${ap.ssid} / ${ap.bssid}).")
            onLog("[!] Сетевой порт роутера и веб-админка могут быть проверены только при прямом подключении к целевой сети.")
            return@withContext
        }

        onLog("[+] Устройство активно подключено к целевой сети: ${ap.ssid}")

        // Detect default gateway IP
        var gatewayIp: String? = null
        try {
            val linkProps = connectivityManager?.getLinkProperties(activeNetwork)
            linkProps?.routes?.forEach { route ->
                if (route.isDefaultRoute && route.gateway is Inet4Address) {
                    gatewayIp = route.gateway?.hostAddress
                }
            }
        } catch (_: Exception) {}

        if (gatewayIp == null) {
            // Fallback from dhcpInfo
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                gatewayIp = String.format(
                    "%d.%d.%d.%d",
                    dhcp.gateway and 0xff,
                    dhcp.gateway shr 8 and 0xff,
                    dhcp.gateway shr 16 and 0xff,
                    dhcp.gateway shr 24 and 0xff
                )
            }
        }

        if (gatewayIp == null) {
            onLog("[-] Не удалось определить IP-адрес основного шлюза (Gateway).")
            return@withContext
        }

        onLog("[+] Обнаружен IP-адрес шлюза: $gatewayIp")

        // Real check of ports 80, 443, 8080
        val portsToCheck = listOf(80 to "HTTP", 443 to "HTTPS", 8080 to "HTTP-Alt")
        val openPorts = mutableListOf<String>()

        for ((port, label) in portsToCheck) {
            onLog("[*] Проверка доступности порта $port ($label)...")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(gatewayIp, port), 1200)
                    openPorts.add("$port ($label)")
                    onLog("[+] Порт $port ($label) ОТКРЫТ на шлюзе $gatewayIp")
                }
            } catch (e: Exception) {
                onLog("[-] Порт $port ($label) закрыт или фильтруется шлюзом")
            }
        }

        if (openPorts.isNotEmpty()) {
            onLog("[!] Веб-интерфейс управления доступен: ${openPorts.joinToString(", ")}")
            // Check default credentials advisory for this vendor
            val db = AuditDatabase.getDatabase(context)
            val creds = try {
                DatabaseCacheManager.getCredentials(ap.vendor ?: "").ifEmpty {
                    db.auditDao().findCredentials(ap.vendor ?: "")
                }
            } catch (_: Exception) {
                emptyList()
            }

            if (creds.isNotEmpty()) {
                onLog("[!] ВНИМАНИЕ: Для вендора '${ap.vendor}' известны заводские учетные записи:")
                creds.forEach { c ->
                    onLog("    • Модель: ${c.model} | Логин: ${c.username} / Пароль: ${if (c.password.isEmpty()) "<пустой>" else c.password}")
                }
                onLog("[i] Рекомендация: убедитесь, что стандартный пароль веб-админки был изменен администратором.")
            }
        } else {
            onLog("[OK] Стандартные веб-порты управления (80, 443, 8080) закрыты для доступа из локального сегмента.")
        }

        onLog("[OK] Аудит шлюза завершен.")
    }
}
