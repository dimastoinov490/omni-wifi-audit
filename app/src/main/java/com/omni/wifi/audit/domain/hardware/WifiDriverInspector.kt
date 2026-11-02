package com.omni.wifi.audit.domain.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FullDriverInspectionResult(
    val system: SystemCapabilities,
    val driver: DriverCapabilityInfo
)

object WifiDriverInspector {

    private val driverBackends: List<IWifiDriverHardware> = listOf(
        QualcommQcacldDriverHardware(),
        GenericAndroidWifiHardware()
    )

    suspend fun inspect(): FullDriverInspectionResult = withContext(Dispatchers.IO) {
        val system = DeviceCapabilityManager.inspect()

        // Choose appropriate hardware backend
        val isQualcommDevice = system.deviceModel.contains("whyred", ignoreCase = true) ||
                system.deviceModel.contains("Redmi Note 5", ignoreCase = true) ||
                system.kernelFull.contains("whyred", ignoreCase = true) ||
                system.kernelFull.contains("qcom", ignoreCase = true)

        val backend = if (isQualcommDevice) {
            driverBackends.first { it is QualcommQcacldDriverHardware }
        } else {
            driverBackends.first { it is GenericAndroidWifiHardware }
        }

        val driverInfo = backend.probe(system.isRootGranted)
        FullDriverInspectionResult(system, driverInfo)
    }
}
