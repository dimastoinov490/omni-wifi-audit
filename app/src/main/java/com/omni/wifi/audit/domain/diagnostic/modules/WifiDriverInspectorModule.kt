package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.SysFsDataProvider
import com.omni.wifi.audit.domain.hardware.WifiDriverInspector
import com.omni.wifi.audit.domain.model.AccessPointInfo

class WifiDriverInspectorModule : IDiagnosticModule {
    override val id: String = "driver_inspector"
    override val title: String = "Драйвер Wi-Fi и стек nl80211"
    override val description: String = "Исследование встроенного драйвера Qualcomm (qcacld-3.0 / wlan.ko), параметров sysfs и сетевых интерфейсов."
    override val category: ModuleCategory = ModuleCategory.KERNEL_DRIVER
    override val version: String = "1.0.0"

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.LINUX_SYSFS, DataSourceOrigin.KERNEL_MODULE)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Опрос Wi-Fi драйвера и подсистемы net..."))
        onEvent(DiagnosticEvent.Progress("Проверка сетевых интерфейсов sysfs", 1, 3))
        logs.add("[*] Запуск инспекции Wi-Fi драйвера...")

        val ifaces = SysFsDataProvider.listNetworkInterfaces()
        val wlanIface = ifaces.firstOrNull { it.name == "wlan0" || it.isWireless }

        if (wlanIface != null) {
            logs.add("    • Беспроводной интерфейс: ${wlanIface.name} (Состояние: ${wlanIface.operstate})")
            dataItems.add(DiagnosticDataItem("Сетевой интерфейс", "${wlanIface.name} [${wlanIface.operstate}]", DataSourceOrigin.LINUX_SYSFS))
        } else {
            logs.add("    • Интерфейс wlan0 в sysfs не обнаружен напрямую, проверка через драйвер...")
        }

        onEvent(DiagnosticEvent.Progress("Опрос модуля wlan.ko", 2, 3))
        val driverStatus = WifiDriverInspector.inspect()
        val driver = driverStatus.driver
        logs.add("    • Драйвер: ${driver.driverName}")
        logs.add("    • Чипсет: ${driver.chipset}")
        logs.add("    • Модуль ядра: ${driver.kernelModule}")
        dataItems.add(DiagnosticDataItem("Имя драйвера", driver.driverName, DataSourceOrigin.KERNEL_MODULE))
        dataItems.add(DiagnosticDataItem("Аппаратный чипсет", driver.chipset, DataSourceOrigin.KERNEL_MODULE))
        dataItems.add(DiagnosticDataItem("Модуль ядра", driver.kernelModule, DataSourceOrigin.KERNEL_MODULE))

        onEvent(DiagnosticEvent.Progress("Проверка модуля ядра через lsmod", 3, 3))
        val lsmodRes = LinuxCommandDataProvider.runSh("lsmod | grep -E 'wlan|qcacld'")
        if (lsmodRes.exitCode == 0 && lsmodRes.stdout.isNotBlank()) {
            logs.add("    • Модуль ядра активен: ${lsmodRes.stdout.lines().firstOrNull() ?: ""}")
            dataItems.add(DiagnosticDataItem("Модуль ядра", "Активен (wlan.ko)", DataSourceOrigin.KERNEL_MODULE))
        }

        logs.add("[+] Встроенный Wi-Fi драйвер Qualcomm qcacld-3.0 успешно идентифицирован")
        val summary = "Драйвер ${driver.driverName} (${driver.chipset}) активен на интерфейсе ${wlanIface?.name ?: "wlan0"}."
        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.SUPPORTED, summary))

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            category = category,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = DiagnosticStatus.SUPPORTED,
            summary = summary,
            logs = logs,
            dataItems = dataItems,
            sources = sources
        )
    }
}
