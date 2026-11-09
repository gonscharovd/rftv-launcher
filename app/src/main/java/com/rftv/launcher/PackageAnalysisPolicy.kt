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
        val profileRisk: String? = null
    )

    data class Report(
        val purpose: String,
        val category: String,
        val severity: Severity,
        val confidence: Confidence,
        val evidence: List<String>,
        val limitation: String
    )

    private val criticalPackageTokens = listOf(
        "android", "systemui", "settings", "packageinstaller", "permissioncontroller",
        "inputmethod", "networkstack", "sberdevices.services", "bluetooth", "remotecontrol"
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

    fun analyze(signals: Signals): Report {
        val id = signals.packageName.lowercase()
        val permissions = signals.requestedPermissions.toSet()
        val evidence = mutableListOf<String>()

        if (isCriticalIdentity(id)) {
            evidence += "Идентификатор пакета совпадает с защищённой системной категорией."
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

        val privacyCombination = sensitive.isNotEmpty() && "android.permission.INTERNET" in permissions
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
            severity = if (privacyCombination) Severity.REVIEW else Severity.INFORMATIONAL,
            confidence = if (privacyCombination) Confidence.MEDIUM else Confidence.LOW,
            evidence = evidence,
            limitation = if (privacyCombination)
                "Запрошенные разрешения и доступ к сети не подтверждают фактическое использование микрофона/камеры/геолокации или передачу данных. Нужны дополнительные наблюдения."
            else
                "Назначение выведено из доступных метаданных и имени пакета; это не анализ сетевого трафика или поведения приложения."
        )
    }

    private fun isCriticalIdentity(packageName: String): Boolean =
        packageName == "android" ||
            criticalPackageTokens.any { token ->
                packageName == token || packageName.startsWith("$token.") ||
                    packageName.substringAfterLast('.') == token || packageName.contains(".$token.")
            }
}
