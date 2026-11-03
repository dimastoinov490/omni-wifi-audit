package com.omni.wifi.audit.domain.diagnostic.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class CommandExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
)

object LinuxCommandDataProvider {

    suspend fun runSh(command: String, timeoutSeconds: Long = 3): CommandExecutionResult =
        runProcess(arrayOf("sh", "-c", command), timeoutSeconds)

    suspend fun runSu(command: String, timeoutSeconds: Long = 4): CommandExecutionResult =
        runProcess(arrayOf("su", "-c", command), timeoutSeconds)

    private suspend fun runProcess(cmdArray: Array<String>, timeoutSeconds: Long): CommandExecutionResult =
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(cmdArray)
                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext CommandExecutionResult(
                        exitCode = -1,
                        stdout = "",
                        stderr = "Превышено время ожидания (${timeoutSeconds}с)",
                        timedOut = true
                    )
                }

                val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
                val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }

                CommandExecutionResult(
                    exitCode = process.exitValue(),
                    stdout = stdout,
                    stderr = stderr
                )
            } catch (e: Exception) {
                CommandExecutionResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Ошибка выполнения: ${e.message}"
                )
            }
        }
}
