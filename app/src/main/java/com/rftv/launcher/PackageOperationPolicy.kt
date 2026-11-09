package com.rftv.launcher

/**
 * Pure safety rules for package state changes.
 *
 * This policy does not execute commands or infer that a package is safe from its
 * name. A privileged provider must independently confirm the requested action.
 */
internal object PackageOperationPolicy {
    enum class AccessMode {
        NONE,
        DEVICE_OWNER,
        SHIZUKU_ADB,
        SHIZUKU_ROOT,
        EXTERNAL_ADB,
        ROOT_SHELL
    }

    enum class Action { DISABLE, RESTORE }
    enum class Mode { AUTOMATIC, MANUAL }

    enum class Confidence { HIGH, MEDIUM, LOW }

    enum class EnabledSetting(val value: Int) {
        DEFAULT(0),
        ENABLED(1),
        DISABLED(2),
        DISABLED_USER(3),
        DISABLED_UNTIL_USED(4);

        companion object {
            fun from(value: Int): EnabledSetting? = entries.firstOrNull { it.value == value }
        }
    }

    /** Exact state captured before RFTV changes anything. */
    data class OriginalState(
        val enabledSetting: EnabledSetting,
        val manifestEnabled: Boolean,
        val hiddenByDevicePolicy: Boolean = false
    )

    data class Candidate(
        val packageName: String,
        val isOwnPackage: Boolean,
        val isProtected: Boolean,
        val exactDeviceProfileMatch: Boolean,
        val explicitlyVerifiedOptional: Boolean,
        val confidence: Confidence,
        val dependenciesChecked: Boolean,
        val hasUnresolvedDependencies: Boolean,
        val currentlyEnabled: Boolean,
        val originalState: OriginalState?
    )

    data class Access(
        val mode: AccessMode,
        val packageStateChange: Boolean,
        val hidePackage: Boolean,
        val verified: Boolean
    )

    data class Decision(
        val allowed: Boolean,
        val reason: String,
        val requiresExplicitWarning: Boolean = false
    )

    fun canDisable(candidate: Candidate, access: Access, mode: Mode = Mode.AUTOMATIC): Decision {
        if (candidate.packageName.isBlank()) return deny("Не указан идентификатор пакета.")
        if (candidate.isOwnPackage) return deny("Нельзя отключить сам RFTV Launcher.")
        if (candidate.isProtected) return deny("Пакет входит в защищённую системную категорию.")
        if (mode == Mode.AUTOMATIC) {
            if (!candidate.exactDeviceProfileMatch) return deny("Нет точного профиля этой модели, платы и версии Android.")
            if (!candidate.explicitlyVerifiedOptional) return deny("Пакет не подтверждён как необязательный для этой прошивки.")
            if (candidate.confidence != Confidence.HIGH) return deny("Уверенность анализа ниже высокой.")
            if (!candidate.dependenciesChecked || candidate.hasUnresolvedDependencies) {
                return deny("Зависимости пакета не проверены или остались неизвестные зависимости.")
            }
        }
        if (!candidate.currentlyEnabled) return deny("Пакет уже отключён; RFTV не должен перезаписывать его состояние.")
        if (candidate.originalState == null) return deny("Исходное состояние не сохранено.")
        if (!access.verified) return deny("Привилегированный канал не прошёл проверку.")
        if (!access.packageStateChange && !access.hidePackage) return deny("Нет подтверждённой возможности управлять пакетами.")
        return if (mode == Mode.AUTOMATIC) {
            allow("Пакет прошёл строгую проверку профиля; требуется отдельное подтверждение владельца.")
        } else {
            Decision(
                true,
                "Ручное отключение: профиль или анализ не подтверждает безопасность. Проверь назначение и возможные последствия.",
                requiresExplicitWarning = !candidate.exactDeviceProfileMatch ||
                    !candidate.explicitlyVerifiedOptional ||
                    candidate.confidence != Confidence.HIGH ||
                    !candidate.dependenciesChecked ||
                    candidate.hasUnresolvedDependencies
            )
        }
    }

    fun canRestore(
        packageName: String,
        isOwnPackage: Boolean,
        isProtected: Boolean,
        originalState: OriginalState?,
        changedByRftv: Boolean,
        access: Access
    ): Decision {
        if (packageName.isBlank()) return deny("Не указан идентификатор пакета.")
        if (isOwnPackage) return deny("Восстановление собственного пакета RFTV через TV Manager запрещено.")
        if (isProtected) return deny("Защищённый пакет нельзя изменять через общий механизм восстановления.")
        if (!changedByRftv) return deny("Нет подтверждённой записи, что пакет изменял RFTV.")
        if (originalState == null) return deny("Не найдено исходное состояние пакета.")
        if (!access.verified) return deny("Привилегированный канал не прошёл проверку.")
        if (!access.packageStateChange && !access.hidePackage) return deny("Нет подтверждённой возможности управлять пакетами.")
        return allow("Можно восстановить только сохранённое исходное состояние этого пакета.")
    }

    fun shouldRollbackBatch(appliedCount: Int, failed: Boolean): Boolean =
        appliedCount > 0 && failed

    private fun allow(reason: String) = Decision(true, reason)
    private fun deny(reason: String) = Decision(false, reason)
}
