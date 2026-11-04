package com.omni.wifi.audit.domain.hardware

import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.providers.SysFsDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WifiDriverInspector {
    suspend fun inspect(): FullDriverInspectionResult = withContext(Dispatchers.IO) {
        val system = DeviceCapabilityManager.inspect()
        val netIfaces = SysFsDataProvider.listNetworkInterfaces()
        val wirelessIface = netIfaces.firstOrNull { it.isWireless }
        val driverName = wirelessIface?.name?.let { name ->
            try { File("/sys/class/net/$name/device/driver").canonicalFile.name } catch (_: Exception) { null }
        }.orEmpty()
        val boardHint = listOf(Build.HARDWARE, Build.BOARD, driverName).joinToString(" ").lowercase()
        val backend: IWifiDriverHardware = if (boardHint.contains("qcom") || boardHint.contains("qualcomm") || boardHint.contains("qcacld")) {
            QualcommQcacldDriverHardware()
        } else {
            GenericAndroidWifiHardware()
        }
        FullDriverInspectionResult(system, backend.probe(system.isRootGranted))
    }
}
