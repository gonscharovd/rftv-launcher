package com.rftv.launcher

/**
 * Conservative, evidence-based package explanation.
 *
 * This class classifies observable metadata only. It never labels a package as
 * spyware and never grants permission to disable or uninstall anything.
 */
internal object PackageAnalysisPolicy {
    enum class Severity { CRITICAL, REVIEW, INFORMATIONAL }
    enum class Confidence { HIGH, MEDIUM, LOW }

    data class Signals(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
        val enabled: Boolean,
        val requestedPermissions: List<String> = emptyList(),
        val serviceCount: Int = 0,
        val receiverCount: Int = 0,
        val activityCount: Int = 0,
        val profilePurpose: String? = null,
        val profileRisk: String? = null,
        val isProtected: Boolean = false,
        val sharedUserId: String? = null,
        val isPersistent: Boolean = false,
        val providesTvInputService: Boolean = false
    )

    data class Report(
        val purpose: String,
        val category: String,
        val severity: Severity,
        val confidence: Confidence,
        val evidence: List<String>,
        val limitation: String
    )

    private val criticalPackageIds = setOf(
        "android",
        "com.android.systemui",
        "com.android.shell",
        "com.android.phone",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.networkstack",
        "com.android.bluetooth",
        "com.android.ext.services",
        "com.android.ext.shared",
        "android.ext.services",
        "android.ext.shared",
        "com.android.modulemetadata",
        "com.google.android.modulemetadata",
        "com.android.sdksandbox",
        "com.android.location.fused",
        "com.mediatek.tis",
        "com.mediatek.tv.service",
        "com.mediatek.tv.agent",
        "com.cltv.mal",
        "ru.sberdevices.services",
        "com.sberdevices.services",
        "ru.sberdevices.app.starkeyboard",
        "ru.sberdevices.tvinputs",
        "ru.sberdevices.webview",
        "ru.sberdevices.starlauncher"
    )
    private val criticalPackagePrefixes = listOf(
        "com.android.providers.",
        "com.google.android.overlay.modules.",
        "com.android.inputmethod.",
        "com.google.android.inputmethod.",
        "com.android.tv.",
        "com.mediatek.",
        "com.cvte.",
        "com.droidlogic.",
        "ru.sberdevices.services.",
        "com.sberdevices.services.",
        "ru.sberdevices.tvinputs.",
        "ru.sberdevices.gamepad."
    )

    private val locationPermissions = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    )
    private val mediaCapturePermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO"
    )
    private val personalDataPermissions = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.READ_CALENDAR"
    )

    private val additionalProtectedPackageIds = setOf(
        "com.android.launcher3",
        "com.google.android.tvlauncher",
        "com.android.tv.launcher",
        "com.android.providers.tv",
        "com.android.providers.media",
        "com.android.providers.settings",
        "com.android.inputdevices",
        "com.android.server.telecom",
        "com.android.providers.downloads",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.dreams.basic",
        "com.android.dreams.phototable",
        "com.android.externalstorage",
        "com.android.documentsui",
        "com.android.keychain",
        "com.android.certinstaller",
        "com.android.defcontainer",
        "com.android.sharedstoragebackup",
        "com.android.backupconfirm",
        "com.android.statementservice",
        "com.android.providers.downloads.ui",
        "com.android.media",
        "com.android.media.module",
        "com.android.server.media",
        "com.android.server.wifi",
        "com.android.dhcp",
        "com.android.wifi.resources",
        "com.android.wifi.dialog",
        "com.android.tv.tuner",
        "com.google.android.tv.remote.service",
        "com.google.android.tv.remote",
        "ru.sberdevices.rcfupdater",
        "ru.sberdevices.tvinputs",
        "ru.sberdevices.gamepad.service",
        "com.mediatek.androidbox"
    )

    /** Hard deny-list shared by the UI and operation controller. */
    fun isProtectedPackage(packageName: String): Boolean =
        isCriticalIdentity(packageName.lowercase())

    fun analyze(signals: Signals): Report {
        val id = signals.packageName.lowercase()
        val permissions = signals.requestedPermissions.toSet()
        val evidence = mutableListOf<String>()

        val hardPlatformProtection = signals.sharedUserId == "android.uid.system" ||
            signals.isPersistent || signals.providesTvInputService
        if (isCriticalIdentity(id) || signals.isProtected || hardPlatformProtection) {
            if (signals.sharedUserId == "android.uid.system") evidence += "Пакет использует защищённый sharedUserId android.uid.system."
            if (signals.isPersistent) evidence += "Пакет помечен ApplicationInfo.FLAG_PERSISTENT."
            if (signals.providesTvInputService) evidence += "Пакет объявляет сервис с action android.media.tv.TvInputService."
            if (evidence.isEmpty()) evidence += if (signals.isProtected && !isCriticalIdentity(id)) {
                "Пакет динамически защищён: он назначен системной ролью HOME, Settings, ввода или активной заставки."
            } else {
                "Идентификатор пакета совпадает с защищённой системной категорией."
            }
            return Report(
                purpose = "Системный компонент или компонент с потенциально критической зависимостью",
                category = "Система и безопасность",
                severity = Severity.CRITICAL,
                confidence = Confidence.MEDIUM,
                evidence = evidence,
                limitation = "Критичность требует проверки на конкретной прошивке; отключение не предлагается."
            )
        }

        if (!signals.profilePurpose.isNullOrBlank()) {
            evidence += "Пакет найден в профиле, совпавшем по точной модели/плате и API Android."
            signals.profileRisk?.let { evidence += "Оценка источника: $it." }
            evidence += "Профильное описание: ${signals.profilePurpose}"
            val high = signals.profileRisk.equals("Критический", true) ||
                signals.profileRisk.equals("Высокий", true)
            return Report(
                purpose = signals.profilePurpose,
                category = "Проверенный профиль устройства",
                severity = if (high) Severity.CRITICAL else Severity.REVIEW,
                confidence = Confidence.HIGH,
                evidence = evidence,
                limitation = "Это наблюдение из профильного теста, а не гарантия поведения другой версии прошивки."
            )
        }

        val category = when {
            id.contains("launcher") || id.contains("home") -> "Главный экран"
            id.contains("tv") || id.contains("iptv") || id.contains("live") -> "ТВ и видео"
            id.contains("media") || id.contains("player") || id.contains("music") -> "Медиа"
            id.contains("browser") || id.contains("webview") -> "Браузер и веб"
            id.contains("cast") || id.contains("dlna") || id.contains("share") -> "Трансляция"
            id.contains("update") || id.contains("ota") -> "Обновление системы/приложений"
            id.contains("voice") || id.contains("assistant") -> "Голосовые функции"
            signals.isSystemApp -> "Системное приложение"
            else -> "Пользовательское приложение"
        }

        if (signals.isSystemApp) evidence += "Android помечает приложение как системное."
        else evidence += "Android не помечает приложение как системное."

        if (signals.enabled) evidence += "Пакет включён."
        else evidence += "Пакет сейчас отключён."

        if (signals.serviceCount > 0) evidence += "Объявлено служб: ${signals.serviceCount}."
        if (signals.receiverCount > 0) evidence += "Объявлено broadcast-приёмников: ${signals.receiverCount}."
        if (signals.activityCount > 0) evidence += "Объявлено Activity: ${signals.activityCount}."

        val sensitive = permissions.intersect(locationPermissions + mediaCapturePermissions + personalDataPermissions)
        if (sensitive.isNotEmpty()) {
            evidence += "В манифесте запрошены чувствительные разрешения: ${sensitive.map { it.substringAfterLast('.') }.sorted().joinToString()}."
        }
        if ("android.permission.INTERNET" in permissions) {
            evidence += "Запрошен доступ к сети; сам по себе он не доказывает сбор или передачу персональных данных."
        }
        if (sensitive.isEmpty()) {
            evidence += "В проверенном списке нет разрешений на геолокацию, запись аудио/видео или чтение личных данных."
        }

        val sensitiveWithNetwork = sensitive.isNotEmpty() && "android.permission.INTERNET" in permissions
        return Report(
            purpose = when {
                category == "Главный экран" -> "Компонент домашнего экрана или запуска приложений"
                category == "ТВ и видео" -> "Приложение телевизионного контента или воспроизведения"
                category == "Медиа" -> "Медиа-приложение или служба воспроизведения"
                category == "Браузер и веб" -> "Браузерный или веб-компонент"
                category == "Трансляция" -> "Компонент передачи или приёма медиаконтента"
                category == "Обновление системы/приложений" -> "Компонент обновления; системную зависимость нужно проверить"
                category == "Голосовые функции" -> "Голосовой помощник или голосовая служба"
                signals.isSystemApp -> "Системное приложение; точная роль не подтверждена только метаданными"
                else -> "Пользовательское приложение; точная роль не подтверждена только метаданными"
            },
            category = category,
            severity = if (sensitiveWithNetwork) Severity.REVIEW else Severity.INFORMATIONAL,
            confidence = Confidence.LOW,
            evidence = evidence,
            limitation = if (sensitiveWithNetwork)
                "Низкая уверенность: PackageManager показывает только декларации разрешений. Это не доказывает использование микрофона/камеры/геолокации или передачу данных; AppOps history и сетевой трафик здесь недоступны."
            else
                "Назначение выведено из доступных метаданных и имени пакета; это не анализ сетевого трафика или поведения приложения."
        )
    }

    private fun isCriticalIdentity(packageName: String): Boolean =
        packageName in criticalPackageIds ||
            packageName in additionalProtectedPackageIds ||
            criticalPackagePrefixes.any { packageName.startsWith(it) }
}
