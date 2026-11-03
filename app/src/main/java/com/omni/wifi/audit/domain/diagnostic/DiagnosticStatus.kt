package com.omni.wifi.audit.domain.diagnostic

enum class DiagnosticStatus(val label: String) {
    SUPPORTED("SUPPORTED"),         // Подтверждённая поддержка (Зелёный)
    UNSUPPORTED("UNSUPPORTED"),     // Подтверждённое отсутствие поддержки (Красный)
    UNKNOWN("UNKNOWN"),             // Невозможно определить (Серый)
    PARTIAL("PARTIAL"),             // Частичная поддержка (Жёлтый)
    WARNING("WARNING"),             // Обнаружено ограничение (Оранжевый)
    ERROR("ERROR"),                 // Ошибка выполнения (Красный)
    RUNNING("RUNNING"),             // Проверка выполняется (Синий)
    NOT_TESTED("NOT_TESTED")        // Проверка ещё не выполнялась (Нейтральный)
}

enum class ModuleCategory(val displayName: String) {
    HARDWARE("Аппаратное обеспечение"),
    KERNEL_DRIVER("Ядро и Драйвер"),
    WIRELESS_CAPABILITY("Беспроводные режимы"),
    ROUTER_INSPECTION("Инспекция маршрутизатора"),
    NETWORK_GATEWAY("Сетевой шлюз"),
    VULNERABILITY("Уязвимости и Аудит")
}

enum class DataSourceOrigin(val title: String) {
    ANDROID_WIFI_API("Android Wi-Fi API"),
    SCAN_RESULT("802.11 ScanResult"),
    WPS_IE("WPS Information Elements (00:50:f2:04)"),
    LINUX_PROCFS("Linux procfs (/proc)"),
    LINUX_SYSFS("Linux sysfs (/sys)"),
    KERNEL_MODULE("Загруженные модули ядра (lsmod/kmod)"),
    IW_CLI("Беспроводная утилита nl80211 (iw)"),
    DUMPSYS("Android dumpsys wifi"),
    ANDROID_HAL("Android Wi-Fi HAL"),
    ROOT_SHELL("Root-интерфейс (su/sh)"),
    LOCAL_DATABASE("Локальная база данных (OUI/CVE/WPS)")
}
