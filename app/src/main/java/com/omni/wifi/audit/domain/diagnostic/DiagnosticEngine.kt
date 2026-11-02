package com.omni.wifi.audit.domain.diagnostic

import android.content.Context
import com.omni.wifi.audit.domain.hardware.DeviceCapabilityManager
import com.omni.wifi.audit.domain.model.AccessPointInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticTaskState(
    val moduleId: String? = null,
    val moduleTitle: String = "",
    val status: DiagnosticStatus = DiagnosticStatus.IDLE,
    val currentStage: String = "",
    val progress: Pair<Int, Int>? = null, // (current, total)
    val logs: List<String> = emptyList(),
    val report: DiagnosticReport? = null,
    val isRunning: Boolean = false
)

object DiagnosticEngine {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null

    private val _taskState = MutableStateFlow(DiagnosticTaskState())
    val taskState: StateFlow<DiagnosticTaskState> = _taskState.asStateFlow()

    fun runTask(
        context: Context,
        module: IDiagnosticModule,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>
    ) {
        cancelCurrentTask()

        currentJob = scope.launch {
            _taskState.value = DiagnosticTaskState(
                moduleId = module.id,
                moduleTitle = module.title,
                status = DiagnosticStatus.RUNNING,
                currentStage = "Инициализация задачи",
                progress = Pair(0, 1),
                logs = listOf("[*] Запуск модуля: ${module.title}..."),
                isRunning = true
            )

            // 1. Check Root requirement if necessary
            if (module.requiresRoot) {
                val sys = DeviceCapabilityManager.inspect()
                if (!sys.isRootGranted) {
                    val deniedReport = DiagnosticReport(
                        moduleId = module.id,
                        moduleTitle = module.title,
                        targetBssid = target.bssid,
                        targetSsid = target.ssid,
                        status = DiagnosticStatus.PERMISSION_DENIED,
                        summary = "Для работы данного модуля требуются Root-права (su).",
                        logs = _taskState.value.logs + "[-] Ошибка: Root-доступ не предоставлен пользователем."
                    )
                    DiagnosticResultRepository.saveReport(deniedReport)
                    _taskState.value = _taskState.value.copy(
                        status = DiagnosticStatus.PERMISSION_DENIED,
                        currentStage = "Отклонено: нет Root-прав",
                        logs = deniedReport.logs,
                        report = deniedReport,
                        isRunning = false
                    )
                    return@launch
                }
            }

            // 2. Check general availability
            val avail = module.checkAvailability(context, target)
            if (avail is ModuleAvailability.Unavailable) {
                val unavailReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.UNSUPPORTED,
                    summary = avail.reason,
                    logs = _taskState.value.logs + "[-] Модуль недоступен: ${avail.reason}"
                )
                DiagnosticResultRepository.saveReport(unavailReport)
                _taskState.value = _taskState.value.copy(
                    status = DiagnosticStatus.UNSUPPORTED,
                    currentStage = "Не поддерживается",
                    logs = unavailReport.logs,
                    report = unavailReport,
                    isRunning = false
                )
                return@launch
            }

            // 3. Execute with progress and cancellation support
            try {
                val report = module.execute(context, target, allAccessPoints) { event ->
                    withContext(Dispatchers.Main) {
                        when (event) {
                            is DiagnosticEvent.Log -> {
                                _taskState.value = _taskState.value.copy(
                                    logs = _taskState.value.logs + event.message
                                )
                            }
                            is DiagnosticEvent.Progress -> {
                                _taskState.value = _taskState.value.copy(
                                    currentStage = event.stage,
                                    progress = Pair(event.current, event.total)
                                )
                            }
                            is DiagnosticEvent.StatusChange -> {
                                _taskState.value = _taskState.value.copy(
                                    status = event.status
                                )
                            }
                        }
                    }
                }

                DiagnosticResultRepository.saveReport(report)
                _taskState.value = _taskState.value.copy(
                    status = report.status,
                    currentStage = "Завершено",
                    logs = report.logs,
                    report = report,
                    isRunning = false
                )
            } catch (e: CancellationException) {
                val cancelReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.CANCELLED,
                    summary = "Операция отменена пользователем.",
                    logs = _taskState.value.logs + "[!] Операция прервана пользователем."
                )
                DiagnosticResultRepository.saveReport(cancelReport)
                _taskState.value = _taskState.value.copy(
                    status = DiagnosticStatus.CANCELLED,
                    currentStage = "Отменено",
                    logs = cancelReport.logs,
                    report = cancelReport,
                    isRunning = false
                )
            } catch (e: Exception) {
                val failReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.FAILED,
                    summary = "Ошибка выполнения: ${e.localizedMessage ?: "Сбой"}",
                    logs = _taskState.value.logs + "[-] Исключение: ${e.localizedMessage}"
                )
                DiagnosticResultRepository.saveReport(failReport)
                _taskState.value = _taskState.value.copy(
                    status = DiagnosticStatus.FAILED,
                    currentStage = "Ошибка",
                    logs = failReport.logs,
                    report = failReport,
                    isRunning = false
                )
            }
        }
    }

    fun cancelCurrentTask() {
        currentJob?.cancel()
        currentJob = null
        if (_taskState.value.isRunning) {
            _taskState.value = _taskState.value.copy(
                status = DiagnosticStatus.CANCELLED,
                currentStage = "Отменено",
                isRunning = false
            )
        }
    }

    fun reset() {
        cancelCurrentTask()
        _taskState.value = DiagnosticTaskState()
    }
}
