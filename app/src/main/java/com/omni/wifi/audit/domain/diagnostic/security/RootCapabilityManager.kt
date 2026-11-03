package com.omni.wifi.audit.domain.diagnostic.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class RootStatus {
    ROOT_GRANTED,
    ROOT_DENIED_BY_USER,
    NO_SU_BINARY,
    ERROR
}

data class RootCheckResult(
    val status: RootStatus,
    val uid: String? = null,
    val suPath: String? = null,
    val availableCommands: Map<String, Boolean> = emptyMap(),
    val details: String
)

object RootCapabilityManager {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su"
    )

    fun hasSuBinary(): Boolean {
        return SU_PATHS.any { File(it).exists() } || checkWhichSu() != null
    }

    private fun checkWhichSu(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor(1, TimeUnit.SECONDS)
            if (!line.isNullOrBlank() && File(line.trim()).exists()) line.trim() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun inspectRoot(): RootCheckResult = withContext(Dispatchers.IO) {
        val suFound = SU_PATHS.firstOrNull { File(it).exists() } ?: checkWhichSu()

        if (suFound == null) {
            return@withContext RootCheckResult(
                status = RootStatus.NO_SU_BINARY,
                details = "Бинарный файл 'su' не найден в файловой системе устройства."
            )
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext RootCheckResult(
                    status = RootStatus.ROOT_DENIED_BY_USER,
                    suPath = suFound,
                    details = "Таймаут ожидания подтверждения Root-прав (запрос Magisk/KernelSU был отклонён или проигнорирован)."
                )
            }

            val exitCode = process.exitValue()
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()

            if (exitCode == 0 && stdout.contains("uid=0(root)")) {
                // Root is verified! Check standard networking binaries
                val commandsToCheck = listOf("iw", "ip", "ifconfig", "iwconfig", "getprop", "lsmod")
                val commandMap = mutableMapOf<String, Boolean>()
                for (cmd in commandsToCheck) {
                    commandMap[cmd] = checkToolAvailableWithRoot(cmd)
                }

                RootCheckResult(
                    status = RootStatus.ROOT_GRANTED,
                    uid = stdout,
                    suPath = suFound,
                    availableCommands = commandMap,
                    details = "Root-доступ успешно подтверждён: $stdout"
                )
            } else {
                RootCheckResult(
                    status = RootStatus.ROOT_DENIED_BY_USER,
                    suPath = suFound,
                    details = "Root-запрос отклонён менеджером суперпользователя. Код: $exitCode, вывод: $stderr"
                )
            }
        } catch (e: Exception) {
            RootCheckResult(
                status = RootStatus.ERROR,
                suPath = suFound,
                details = "Ошибка при проверке Root-доступа: ${e.message}"
            )
        }
    }

    private fun checkToolAvailableWithRoot(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "which $cmd"))
            val finished = process.waitFor(1, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
