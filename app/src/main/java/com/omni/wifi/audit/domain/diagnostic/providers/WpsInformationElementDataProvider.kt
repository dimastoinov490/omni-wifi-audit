package com.omni.wifi.audit.domain.diagnostic.providers

import android.content.Context
import com.omni.wifi.audit.domain.model.AccessPointInfo
import com.omni.wifi.audit.domain.wps.WpsDeviceInfo
import com.omni.wifi.audit.domain.wps.WpsInformationElementParser
import com.omni.wifi.audit.domain.wps.WpsProtocolAnalyzer

object WpsInformationElementDataProvider {

    fun parseWps(context: Context, target: AccessPointInfo): WpsDeviceInfo {
        val scanResult = ScanResultDataProvider.findSystemScanResult(context, target.bssid)
        if (scanResult != null) {
            val parsed = WpsInformationElementParser.parseFromScanResult(scanResult)
            if (parsed != null) return parsed
        }

        val report = WpsProtocolAnalyzer.analyze(scanResult, target)
        return WpsDeviceInfo(
            isSupported = report.isWpsSupported,
            version = report.wpsVersion,
            state = report.state,
            isLocked = report.isLocked,
            manufacturer = report.manufacturer,
            modelName = report.modelName,
            modelNumber = report.modelNumber,
            deviceName = report.deviceName,
            configMethods = report.configMethods,
            source = "ANALYZER_FALLBACK"
        )
    }
}
