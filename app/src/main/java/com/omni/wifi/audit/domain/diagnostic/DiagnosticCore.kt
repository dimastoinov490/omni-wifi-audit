package com.omni.wifi.audit.domain.diagnostic

import android.content.Context
import com.omni.wifi.audit.domain.diagnostic.security.PermissionManager
import com.omni.wifi.audit.domain.diagnostic.security.RootCapabilityManager
import com.omni.wifi.audit.domain.diagnostic.security.RootStatus
import com.omni.wifi.audit.domain.model.AccessPointInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticTaskState(
    val moduleId: String? = null,
    val moduleTitle: String = "",
    val category: ModuleCategory? = null,
    val status: DiagnosticStatus = DiagnosticStatus.NOT_TESTED,
    val currentStage: String = "",
    val progress: Pair<Int, Int>? = null, // (current, total)
    val logs: List<String> = emptyList(),
    val report: DiagnosticReport? = null,
    val isRunning: Boolean = false
)

object DiagnosticCore {

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
                category = module.category,
                status = DiagnosticStatus.RUNNING,
                currentStage = "Инициализация и проверка требований",
                progress = Pair(0, 1),
                logs = listOf("[*] Запуск диагностического модуля: ${module.title}..."),
                isRunning = true
            )

            // 1. Check Missing Permissions
            val missingPermissions = PermissionManager.getMissingPermissions(context, module.requiredPermissions)
            if (missingPermissions.isNotEmpty()) {
                val deniedReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    category = module.category,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.ERROR,
                    summary = "Отсутствуют необходимые разрешения Android: ${missingPermissions.joinToString(", ")}",
                    logs = _taskState.value.logs + "[-] Ошибка: Отсутствуют разрешения: ${missingPermissions.joinToString(", ")}",
                    sources = listOf(DataSourceOrigin.ANDROID_HAL)
                )
                DiagnosticResultRepository.saveReport(deniedReport)
                _taskState.value = _taskState.value.copy(
                    status = DiagnosticStatus.ERROR,
                    currentStage = "Ошибка: нет разрешений",
                    logs = deniedReport.logs,
                    report = deniedReport,
                    isRunning = false
                )
                return@launch
            }

            // 2. Check Module Dependencies & Compatibility
            val compatibility = module.checkCompatibility(context, target, DiagnosticModuleRegistry)
            if (!compatibility.isCompatible) {
                val reason = compatibility.reason ?: "Модуль несовместим с текущей конфигурацией."
                val unavailReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    category = module.category,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.UNSUPPORTED,
                    summary = reason,
                    logs = _taskState.value.logs + "[-] Несовместимость: $reason",
                    sources = listOf(DataSourceOrigin.ANDROID_HAL)
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

            // 3. Check Root requirement if necessary
            if (module.requiresRoot) {
                _taskState.value = _taskState.value.copy(currentStage = "Проверка Root-доступа (su)")
                val rootCheck = RootCapabilityManager.inspectRoot()
                if (rootCheck.status != RootStatus.ROOT_GRANTED) {
                    val rootFailReason = rootCheck.details
                    val deniedReport = DiagnosticReport(
                        moduleId = module.id,
                        moduleTitle = module.title,
                        category = module.category,
                        targetBssid = target.bssid,
                        targetSsid = target.ssid,
                        status = DiagnosticStatus.ERROR,
                        summary = "Для работы модуля требуются Root-права: $rootFailReason",
                        logs = _taskState.value.logs + "[-] Ошибка Root: $rootFailReason",
                        sources = listOf(DataSourceOrigin.ROOT_SHELL)
                    )
                    DiagnosticResultRepository.saveReport(deniedReport)
                    _taskState.value = _taskState.value.copy(
                        status = DiagnosticStatus.ERROR,
                        currentStage = "Ошибка: нет Root-прав",
                        logs = deniedReport.logs,
                        report = deniedReport,
                        isRunning = false
                    )
                    return@launch
                }
            }

            // 4. Execute Module
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
                            is DiagnosticEvent.ItemDiscovered -> {
                                // Handled in logs and report
                            }
                        }
                    }
                }

                DiagnosticResultRepository.saveReport(report)
                _taskState.value = _taskState.value.copy(
                    status = report.status,
                    currentStage = "Диагностика завершена",
                    logs = report.logs,
                    report = report,
                    isRunning = false
                )
            } catch (e: CancellationException) {
                val cancelReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    category = module.category,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.NOT_TESTED,
                    summary = "Операция отменена пользователем.",
                    logs = _taskState.value.logs + "[!] Операция прервана пользователем.",
                    sources = listOf(DataSourceOrigin.ANDROID_HAL)
                )
                DiagnosticResultRepository.saveReport(cancelReport)
                _taskState.value = _taskState.value.copy(
                    status = DiagnosticStatus.NOT_TESTED,
                    currentStage = "Отменено",
                    logs = cancelReport.logs,
                    report = cancelReport,
                    isRunning = false
                )
            } catch (e: Exception) {
                val failReport = DiagnosticReport(
                    moduleId = module.id,
                    moduleTitle = module.title,
                    category = module.category,
                    targetBssid = target.bssid,
                    targetSsid = target.ssid,
                    status = DiagnosticStatus.ERROR,
                    summary = "Сбой выполнения модуля: ${e.localizedMessage ?: "Неизвестная ошибка"}",
                    logs = _taskState.value.logs + "[-] Исключение: ${e.localizedMessage}",
                    sources = listOf(DataSourceOrigin.ANDROID_HAL)
                )
                DiagnosticResultRepository.saveReport(failReport)
                _taskState.value = _taskState.value.copy(
                    status = DiagnosticStatus.ERROR,
                    currentStage = "Сбой",
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
                status = DiagnosticStatus.NOT_TESTED,
                currentStage = "Отменено пользователем",
                isRunning = false
            )
        }
    }

    fun reset() {
        cancelCurrentTask()
        _taskState.value = DiagnosticTaskState()
    }
}
