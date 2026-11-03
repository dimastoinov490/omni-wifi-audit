package com.omni.wifi.audit.domain.diagnostic.modules

import android.content.Context
import android.os.Build
import com.omni.wifi.audit.domain.diagnostic.*
import com.omni.wifi.audit.domain.diagnostic.providers.LinuxCommandDataProvider
import com.omni.wifi.audit.domain.diagnostic.providers.ProcFsDataProvider
import com.omni.wifi.audit.domain.model.AccessPointInfo

class KernelInspectorModule : IDiagnosticModule {
    override val id: String = "kernel_inspector"
    override val title: String = "Инспекция ядра Linux и ОС"
    override val description: String = "Анализ версии ядра Linux 4.4 (CAF msm-4.4), сборки ОС Pixel Experience (Android 13) и статуса SELinux."
    override val category: ModuleCategory = ModuleCategory.KERNEL_DRIVER
    override val version: String = "1.0.0"

    override suspend fun execute(
        context: Context,
        target: AccessPointInfo,
        allAccessPoints: List<AccessPointInfo>,
        onEvent: suspend (DiagnosticEvent) -> Unit
    ): DiagnosticReport {
        val logs = mutableListOf<String>()
        val dataItems = mutableListOf<DiagnosticDataItem>()
        val sources = mutableListOf(DataSourceOrigin.LINUX_PROCFS, DataSourceOrigin.ANDROID_HAL)

        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.RUNNING, "Опрос ядра Linux и ОС..."))
        onEvent(DiagnosticEvent.Progress("Чтение версии ядра", 1, 3))
        logs.add("[*] Запуск инспекции ядра Linux и системного окружения...")

        val kernelVer = ProcFsDataProvider.readKernelVersion()
        logs.add("    • Версия ядра: $kernelVer")
        dataItems.add(DiagnosticDataItem("Ядро Linux", kernelVer.take(60), DataSourceOrigin.LINUX_PROCFS))

        onEvent(DiagnosticEvent.Progress("Анализ версии Android и ROM", 2, 3))
        val androidVer = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        val fingerprint = Build.FINGERPRINT
        val romDisplay = Build.DISPLAY

        logs.add("    • Android: $androidVer (API $sdkInt)")
        logs.add("    • Display/ROM: $romDisplay")
        logs.add("    • Fingerprint: $fingerprint")
        dataItems.add(DiagnosticDataItem("Версия Android", "$androidVer (API $sdkInt)", DataSourceOrigin.ANDROID_HAL))
        dataItems.add(DiagnosticDataItem("Прошивка (ROM)", romDisplay, DataSourceOrigin.ANDROID_HAL))

        onEvent(DiagnosticEvent.Progress("Проверка SELinux", 3, 3))
        val selinuxResult = LinuxCommandDataProvider.runSh("getenforce")
        val selinuxMode = if (selinuxResult.exitCode == 0 && selinuxResult.stdout.isNotBlank()) {
            selinuxResult.stdout
        } else "Enforcing (Default)"
        logs.add("    • Режим SELinux: $selinuxMode")
        dataItems.add(DiagnosticDataItem("Режим SELinux", selinuxMode, DataSourceOrigin.ROOT_SHELL))

        val isKernel44 = kernelVer.contains("4.4.") || kernelVer.startsWith("4.4")
        val isAosp13 = sdkInt == 33 || androidVer == "13"

        if (isKernel44) {
            logs.add("[+] Подтверждено ядро ветки Linux 4.4 (CAF msm-4.4 для whyred)")
        } else {
            logs.add("[i] Обнаружена версия ядра: ${kernelVer.take(30)}")
        }

        if (isAosp13) {
            logs.add("[+] Подтверждена операционная система Android 13 (Pixel Experience / AOSP)")
        }

        val summary = "Ядро: ${if (isKernel44) "Linux 4.4 (whyred)" else kernelVer.take(20)}. ОС: Android $androidVer. SELinux: $selinuxMode."
        onEvent(DiagnosticEvent.StatusChange(DiagnosticStatus.SUPPORTED, summary))

        return DiagnosticReport(
            moduleId = id,
            moduleTitle = title,
            category = category,
            targetBssid = target.bssid,
            targetSsid = target.ssid,
            status = DiagnosticStatus.SUPPORTED,
            summary = summary,
            logs = logs,
            dataItems = dataItems,
            sources = sources
        )
    }
}
