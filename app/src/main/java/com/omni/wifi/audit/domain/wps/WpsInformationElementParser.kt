package com.omni.wifi.audit.domain.wps

import android.net.wifi.ScanResult
import android.os.Build
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object WpsInformationElementParser {

    private const val EID_VENDOR_SPECIFIC = 221 // 0xDD
    private val WPS_OUI = byteArrayOf(0x00.toByte(), 0x50.toByte(), 0xF2.toByte(), 0x04.toByte())

    // WPS TLV types
    private const val TLV_VERSION = 0x104A
    private const val TLV_WPS_STATE = 0x1044
    private const val TLV_AP_SETUP_LOCKED = 0x1057
    private const val TLV_CONFIG_METHODS = 0x1008
    private const val TLV_MANUFACTURER = 0x1021
    private const val TLV_MODEL_NAME = 0x1023
    private const val TLV_MODEL_NUMBER = 0x1024
    private const val TLV_DEVICE_NAME = 0x1011
    private const val TLV_SERIAL_NUMBER = 0x1042

    fun parseFromScanResult(scanResult: ScanResult): WpsDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val ies = scanResult.informationElements ?: return null
                for (ie in ies) {
                    if (ie.id == EID_VENDOR_SPECIFIC) {
                        val buffer = ie.bytes.duplicate()
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        if (isWpsElement(bytes)) {
                            return parseWpsTlvs(bytes)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun isWpsElement(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == WPS_OUI[0] &&
                bytes[1] == WPS_OUI[1] &&
                bytes[2] == WPS_OUI[2] &&
                bytes[3] == WPS_OUI[3]
    }

    private fun parseWpsTlvs(bytes: ByteArray): WpsDeviceInfo {
        var version = "1.0"
        var state = WpsState.UNKNOWN
        var isLocked = false
        var manufacturer: String? = null
        var modelName: String? = null
        var modelNumber: String? = null
        var deviceName: String? = null
        var serialNumber: String? = null
        val configMethods = mutableListOf<String>()
        var tlvCount = 0

        var offset = 4 // Skip 4 bytes of OUI
        while (offset + 4 <= bytes.size) {
            val type = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            offset += 4

            if (offset + length > bytes.size) {
                break
            }

            val value = bytes.copyOfRange(offset, offset + length)
            offset += length
            tlvCount++

            when (type) {
                TLV_VERSION -> {
                    if (value.isNotEmpty()) {
                        val v = value[0].toInt() and 0xFF
                        version = when (v) {
                            0x10 -> "1.0"
                            0x20 -> "2.0"
                            else -> String.format("%d.%d", (v shr 4) and 0x0F, v and 0x0F)
                        }
                    }
                }
                TLV_WPS_STATE -> {
                    if (value.isNotEmpty()) {
                        state = when (value[0].toInt()) {
                            1 -> WpsState.NOT_CONFIGURED
                            2 -> WpsState.CONFIGURED
                            else -> WpsState.UNKNOWN
                        }
                    }
                }
                TLV_AP_SETUP_LOCKED -> {
                    if (value.isNotEmpty()) {
                        isLocked = (value[0].toInt() and 0xFF) == 1
                    }
                }
                TLV_CONFIG_METHODS -> {
                    if (value.size >= 2) {
                        val mask = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                        if (mask and 0x0004 != 0) configMethods.add("Label")
                        if (mask and 0x0008 != 0) configMethods.add("Display")
                        if (mask and 0x0080 != 0) configMethods.add("PushButton (PBC)")
                        if (mask and 0x0100 != 0) configMethods.add("Keypad (PIN)")
                        if (mask and 0x0001 != 0) configMethods.add("USBA")
                        if (mask and 0x0002 != 0) configMethods.add("Ethernet")
                        if (mask and 0x0010 != 0) configMethods.add("Ext. NFC")
                        if (mask and 0x0020 != 0) configMethods.add("Int. NFC")
                    }
                }
                TLV_MANUFACTURER -> {
                    manufacturer = String(value, StandardCharsets.UTF_8).trim()
                }
                TLV_MODEL_NAME -> {
                    modelName = String(value, StandardCharsets.UTF_8).trim()
                }
                TLV_MODEL_NUMBER -> {
                    modelNumber = String(value, StandardCharsets.UTF_8).trim()
                }
                TLV_DEVICE_NAME -> {
                    deviceName = String(value, StandardCharsets.UTF_8).trim()
                }
                TLV_SERIAL_NUMBER -> {
                    serialNumber = String(value, StandardCharsets.UTF_8).trim()
                }
            }
        }

        return WpsDeviceInfo(
            isSupported = true,
            version = version,
            state = state,
            isLocked = isLocked,
            manufacturer = manufacturer?.ifEmpty { null },
            modelName = modelName?.ifEmpty { null },
            modelNumber = modelNumber?.ifEmpty { null },
            deviceName = deviceName?.ifEmpty { null },
            serialNumber = serialNumber?.ifEmpty { null },
            configMethods = configMethods,
            rawTlvCount = tlvCount,
            source = "INFORMATION_ELEMENT"
        )
    }
}
