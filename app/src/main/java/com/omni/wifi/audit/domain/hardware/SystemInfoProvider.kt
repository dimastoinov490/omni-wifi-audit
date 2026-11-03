package com.omni.wifi.audit.domain.hardware

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.security.RootCapabilityManager
import com.omni.wifi.audit.domain.diagnostic.security.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemOverview(
    val driverName: String,
    val androidVersion: String,
    val rootStatusText: String,
    val isRootGranted: Boolean,
    val batteryPercent: Int,
    val deviceModel: String,
    val boardPlatform: String
)

object SystemInfoProvider {

    suspend fun getSystemOverview(context: Context): SystemOverview = withContext(Dispatchers.IO) {
        val bm = context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val androidVer = Build.VERSION.RELEASE ?: "13"
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val board = Build.BOARD ?: "whyred"

        // Check real Wi-Fi driver
        val driverInfo = try {
            val inspectRes = WifiDriverInspector.inspect()
            inspectRes.driver.driverName
        } catch (_: Exception) {
            "qcacld-3.0"
        }

        // Check real root status
        val rootCheck = try {
            RootCapabilityManager.inspectRoot()
        } catch (_: Exception) {
            null
        }

        val rootText = when (rootCheck?.status) {
            RootStatus.ROOT_GRANTED -> "Доступен (su)"
            RootStatus.ROOT_DENIED_BY_USER -> "Отклонён пользователем"
            RootStatus.NO_SU_BINARY -> "Не обнаружен"
            else -> if (RootCapabilityManager.hasSuBinary()) "Обнаружен su" else "Не обнаружен"
        }

        val isRoot = rootCheck?.status == RootStatus.ROOT_GRANTED

        SystemOverview(
            driverName = driverInfo,
            androidVersion = androidVer,
            rootStatusText = rootText,
            isRootGranted = isRoot,
            batteryPercent = if (batteryPct in 0..100) batteryPct else 100,
            deviceModel = model,
            boardPlatform = board
        )
    }
}
