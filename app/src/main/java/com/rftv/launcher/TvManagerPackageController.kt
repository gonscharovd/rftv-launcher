package com.rftv.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Executes one package-state transition at a time.
 *
 * The caller must show the preview and obtain confirmation before calling disable().
 * Every mutation is preceded by a synchronous snapshot commit and followed by a
 * PackageManager read-back. A failed verification triggers an immediate rollback.
 */
internal class TvManagerPackageController(
    context: Context,
    private val serviceProvider: () -> ITvManagerShellService?,
    private val serviceUidProvider: () -> Int
) {
    data class Preview(
        val packageName: String,
        val label: String,
        val enabledSetting: Int,
        val isSystemApp: Boolean,
        val isProtected: Boolean,
        val profileMatched: Boolean,
        val verifiedOptional: Boolean,
        val profileNote: String?,
        val profileRisk: String?,
        val confidence: PackageOperationPolicy.Confidence,
        val dependenciesVerified: Boolean,
        val decision: PackageOperationPolicy.Decision
    )

    data class OperationResult(
        val success: Boolean,
        val message: String,
        val snapshot: TvManagerOperationStore.Snapshot? = null
    )

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val store = TvManagerOperationStore(appContext)
    private val matchingProfiles by lazy(LazyThreadSafetyMode.NONE) {
        TvManagerProfiles.matchingProfiles()
    }
    private val profileEntries by lazy(LazyThreadSafetyMode.NONE) {
        matchingProfiles.flatMap { it.packages }.associateBy { it.packageId }
    }
    private val dynamicProtectedPackages by lazy(LazyThreadSafetyMode.NONE) {
        val protected = mutableSetOf<String>()
        try {
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNullTo(protected) { it.activityInfo?.packageName }
        } catch (_: Exception) { }
        try {
            packageManager.resolveActivity(
                android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName?.let { protected += it }
        } catch (_: Exception) { }
        try {
            val resolver = appContext.contentResolver
            val inputMethods = listOf(
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
                android.provider.Settings.Secure.ENABLED_INPUT_METHODS
            )
            inputMethods.forEach { key ->
                android.provider.Settings.Secure.getString(resolver, key)
                    ?.split(':')
                    ?.mapNotNull { android.content.ComponentName.unflattenFromString(it)?.packageName }
                    ?.let(protected::addAll)
            }
            android.provider.Settings.Secure.getString(
                resolver,
                "screensaver_components"
            )?.split(':')
                ?.mapNotNull { android.content.ComponentName.unflattenFromString(it)?.packageName }
                ?.let(protected::addAll)
        } catch (_: Exception) { }
        protected
    }

    fun preview(packageName: String, mode: PackageOperationPolicy.Mode): Preview? {
        // The shell bridge intentionally targets only Android's primary user.
        if (android.os.Process.myUid() / 100000 != 0) return null
        if (!PACKAGE_NAME.matches(packageName) || packageName == appContext.packageName) return null
        val app = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            return null
        }
        val enabledSetting = try {
            packageManager.getApplicationEnabledSetting(packageName)
        } catch (_: Exception) {
            return null
        }
        val disabled = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
            (enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && !app.enabled)
        val profileEntry = profileEntries[packageName]
        val exactProfile = profileEntry != null
        val protected = isHardProtected(packageName)
        val uid = serviceUidProvider()
        val accessMode = when (uid) {
            0 -> PackageOperationPolicy.AccessMode.SHIZUKU_ROOT
            2000 -> PackageOperationPolicy.AccessMode.SHIZUKU_ADB
            else -> PackageOperationPolicy.AccessMode.NONE
        }
        val access = PackageOperationPolicy.Access(
            mode = accessMode,
            packageStateChange = uid == 0 || uid == 2000,
            hidePackage = false,
            verified = serviceProvider() != null && (uid == 0 || uid == 2000)
        )
        val state = PackageOperationPolicy.EnabledSetting.from(enabledSetting)
        val original = state?.let {
            PackageOperationPolicy.OriginalState(
                enabledSetting = it,
                applicationEnabled = app.enabled,
                hiddenByDevicePolicy = false
            )
        }
        val policyDecision = PackageOperationPolicy.canDisable(
            PackageOperationPolicy.Candidate(
                packageName = packageName,
                isOwnPackage = packageName == appContext.packageName,
                isProtected = protected,
                exactDeviceProfileMatch = exactProfile,
                explicitlyVerifiedOptional = profileEntry?.verifiedOptional == true,
                confidence = if (profileEntry?.confidenceHigh == true) {
                    PackageOperationPolicy.Confidence.HIGH
                } else {
                    PackageOperationPolicy.Confidence.LOW
                },
                dependenciesChecked = profileEntry?.dependenciesVerified == true,
                hasUnresolvedDependencies = profileEntry?.dependenciesVerified != true,
                currentlyEnabled = !disabled,
                originalState = original
            ),
            access,
            mode
        )
        val decision = if (store.activeSnapshot(packageName) != null) {
            PackageOperationPolicy.Decision(
                allowed = false,
                reason = "Для пакета уже существует сохранённая операция RFTV. Сначала проверь и заверши восстановление."
            )
        } else {
            policyDecision
        }
        return Preview(
            packageName = packageName,
            label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { packageName },
            enabledSetting = enabledSetting,
            isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isProtected = protected,
            profileMatched = exactProfile && profileEntry != null,
            verifiedOptional = profileEntry?.verifiedOptional == true,
            profileNote = profileEntry?.note,
            profileRisk = profileEntry?.risk,
            confidence = if (profileEntry?.confidenceHigh == true) {
                PackageOperationPolicy.Confidence.HIGH
            } else {
                PackageOperationPolicy.Confidence.LOW
            },
            dependenciesVerified = profileEntry?.dependenciesVerified == true,
            decision = decision
        )
    }

    data class BatchResult(
        val success: Boolean,
        val appliedCount: Int,
        val message: String,
        val rollbackMessages: List<String> = emptyList()
    )

    fun automaticCandidates(): List<Preview> {
        return profileEntries.values
            .filter { it.verifiedOptional && it.confidenceHigh && it.dependenciesVerified }
            .mapNotNull { preview(it.packageId, PackageOperationPolicy.Mode.AUTOMATIC) }
            .filter { it.decision.allowed }
    }

    /** Applies a fully previewed automatic batch; any failure rolls back in reverse order. */
    fun disableAutomaticBatch(confirmed: Boolean): BatchResult {
        if (!confirmed) return BatchResult(false, 0, "Не получено подтверждение владельца.")
        val candidates = automaticCandidates()
        if (candidates.isEmpty()) {
            return BatchResult(false, 0, "Нет пакетов, прошедших строгую проверку автоматического режима.")
        }
        val applied = mutableListOf<String>()
        for (candidate in candidates) {
            val result = disable(
                candidate.packageName,
                PackageOperationPolicy.Mode.AUTOMATIC,
                warningConfirmed = false
            )
            if (!result.success) {
                val rollbackTargets = buildList {
                    result.snapshot?.packageName
                        ?.takeIf { store.activeSnapshot(it) != null }
                        ?.let(::add)
                    addAll(applied.asReversed())
                }
                val rollback = rollbackTargets.map { packageId ->
                    val restored = restore(packageId)
                    "$packageId: ${restored.message}"
                }
                return BatchResult(
                    false,
                    applied.size,
                    "Пакет ${candidate.packageName} не прошёл операцию: ${result.message}",
                    rollback
                )
            }
            applied += candidate.packageName
        }
        return BatchResult(true, applied.size, "Автоматическая очистка завершена. Проверено отключённых пакетов: ${applied.size}.")
    }

    fun disable(
        packageName: String,
        mode: PackageOperationPolicy.Mode,
        warningConfirmed: Boolean
    ): OperationResult {
        val preview = preview(packageName, mode)
            ?: return OperationResult(false, "Пакет не найден или его идентификатор недопустим.")
        if (!preview.decision.allowed) return OperationResult(false, preview.decision.reason)
        if (preview.decision.requiresExplicitWarning && !warningConfirmed) {
            return OperationResult(false, "Для ручного отключения требуется отдельное подтверждение предупреждения.")
        }
        val app = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            return OperationResult(false, "Пакет больше не установлен.")
        }
        val service = serviceProvider()
            ?: return OperationResult(false, "Привилегированный канал отключился до сохранения snapshot.")
        val uid = try {
            service.uid
        } catch (_: Exception) {
            return OperationResult(false, "Не удалось повторно проверить UID привилегированного сервиса.")
        }
        if (uid != 0 && uid != 2000) {
            return OperationResult(false, "UID сервиса изменился или не является shell/root.")
        }
        val originalState = preview.enabledSetting
        val operationId = TvManagerOperationStore.newOperationId()
        val snapshot = TvManagerOperationStore.Snapshot(
            operationId = operationId,
            packageName = packageName,
            userId = 0,
            originalEnabledSetting = originalState,
            applicationEnabled = app.enabled,
            originalHidden = false,
            provider = if (uid == 0) "Shizuku root" else "Shizuku ADB shell",
            deviceFingerprint = Build.FINGERPRINT,
            createdAt = System.currentTimeMillis()
        )
        if (!store.saveSnapshotBeforeChange(snapshot)) {
            return OperationResult(false, "Не удалось надёжно сохранить исходное состояние. Изменение не выполнялось.")
        }

        val commandResult = try {
            service.setEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)
        } catch (error: Exception) {
            store.recordFailure(snapshot, error.message ?: "Ошибка Binder")
            return OperationResult(false, "Ошибка канала управления: ${error.message ?: "Binder"}", snapshot)
        }
        val verified = commandResult.startsWith("OK:") &&
            verifyState(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
        if (!verified) {
            val rollback = restoreState(service, snapshot)
            store.recordRollback(snapshot, rollback, "Отключение не подтверждено. Ответ: $commandResult")
            if (rollback) store.recordRestored(snapshot, snapshot.originalEnabledSetting, true)
            return OperationResult(
                false,
                if (rollback) "Отключение не подтверждено; исходное состояние восстановлено."
                else "КРИТИЧЕСКАЯ ОШИБКА: отключение и автоматический откат не подтверждены. Не перезагружайте ТВ; используйте журнал и повторное восстановление.",
                snapshot
            )
        }
        if (!store.recordApplied(snapshot, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, true)) {
            val rollback = restoreState(service, snapshot)
            store.recordRollback(snapshot, rollback, "Не удалось записать результат операции в журнал.")
            if (rollback) store.recordRestored(snapshot, snapshot.originalEnabledSetting, true)
            return OperationResult(
                false,
                if (rollback) "Не удалось сохранить журнал. Пакет возвращён в исходное состояние."
                else "КРИТИЧЕСКАЯ ОШИБКА: не удалось сохранить журнал и подтвердить откат.",
                snapshot
            )
        }
        return OperationResult(true, "Пакет отключён; новое состояние подтверждено.", snapshot)
    }

    fun restore(packageName: String): OperationResult {
        val snapshot = store.activeSnapshot(packageName)
            ?: return OperationResult(false, "Нет сохранённого исходного состояния, изменённого RFTV.")
        val service = serviceProvider()
            ?: return OperationResult(false, "Привилегированный канал недоступен.", snapshot)
        val uid = try {
            service.uid
        } catch (_: Exception) {
            return OperationResult(false, "Не удалось проверить UID привилегированного сервиса.", snapshot)
        }
        if (uid != 0 && uid != 2000) {
            return OperationResult(false, "UID сервиса изменился или не является shell/root.", snapshot)
        }
        val accessMode = when (uid) {
            0 -> PackageOperationPolicy.AccessMode.SHIZUKU_ROOT
            2000 -> PackageOperationPolicy.AccessMode.SHIZUKU_ADB
            else -> PackageOperationPolicy.AccessMode.NONE
        }
        val decision = PackageOperationPolicy.canRestore(
            packageName = packageName,
            isOwnPackage = packageName == appContext.packageName,
            isProtected = false,
            originalState = PackageOperationPolicy.EnabledSetting.from(snapshot.originalEnabledSetting)?.let {
                PackageOperationPolicy.OriginalState(it, snapshot.applicationEnabled, snapshot.originalHidden)
            },
            changedByRftv = true,
            access = PackageOperationPolicy.Access(
                mode = accessMode,
                packageStateChange = uid == 0 || uid == 2000,
                hidePackage = false,
                verified = uid == 0 || uid == 2000
            )
        )
        if (!decision.allowed) return OperationResult(false, decision.reason, snapshot)
        val result = restoreState(service, snapshot)
        val verified = result && verifyState(packageName, snapshot.originalEnabledSetting)
        if (!verified) {
            store.recordRestored(snapshot, currentState(packageName), false)
            return OperationResult(false, "Исходное состояние не подтверждено. Запись сохранена для повторной попытки.", snapshot)
        }
        if (!store.recordRestored(snapshot, snapshot.originalEnabledSetting, true)) {
            return OperationResult(
                false,
                "Состояние восстановлено, но журнал не удалось обновить. Snapshot сохранён; повтори проверку.",
                snapshot
            )
        }
        return OperationResult(true, "Исходное состояние пакета восстановлено и проверено.")
    }

    fun activeSnapshots(): List<TvManagerOperationStore.Snapshot> =
        store.activeSnapshots().sortedByDescending { it.createdAt }

    fun restoreAll(): List<OperationResult> =
        activeSnapshots().map { snapshot -> restore(snapshot.packageName) }

    fun journal(): List<TvManagerOperationStore.JournalEntry> = store.journal()

    private fun restoreState(
        service: ITvManagerShellService,
        snapshot: TvManagerOperationStore.Snapshot
    ): Boolean {
        return try {
            val response = service.setEnabledSetting(snapshot.packageName, snapshot.originalEnabledSetting, snapshot.userId)
            response.startsWith("OK:") && verifyState(snapshot.packageName, snapshot.originalEnabledSetting)
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyState(packageName: String, expected: Int): Boolean {
        repeat(4) { attempt ->
            if (currentState(packageName) == expected) return true
            if (attempt < 3) Thread.sleep(150L)
        }
        return false
    }

    private fun currentState(packageName: String): Int = try {
        packageManager.getApplicationEnabledSetting(packageName)
    } catch (_: Exception) {
        Int.MIN_VALUE
    }

    private fun isHardProtected(packageName: String): Boolean {
        val id = packageName.lowercase()
        return id == appContext.packageName ||
            PackageAnalysisPolicy.isProtectedPackage(id) ||
            packageName in dynamicProtectedPackages
    }

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
