package com.omni.wifi.audit.domain.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenericAndroidWifiHardware : IWifiDriverHardware {
    override val hardwareName: String = "Generic Android Wi-Fi HAL"

    override suspend fun probe(hasRoot: Boolean): DriverCapabilityInfo = withContext(Dispatchers.IO) {
        DriverCapabilityInfo(
            driverName = "Android Wi-Fi HAL",
            chipset = "Internal WLAN",
            interfaceName = "wlan0",
            isInterfaceUp = true,
            supportedModes = listOf("managed (STA)", "AP", "P2P"),
            isMonitorModeSupported = false,
            isPacketInjectionSupported = false,
            kernelModule = "wlan",
            notes = listOf("Штатный Android Wi-Fi интерфейс: поддержка стандартных функций клиента и точки доступа")
        )
    }
}
