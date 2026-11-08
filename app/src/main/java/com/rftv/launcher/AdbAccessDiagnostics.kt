package com.rftv.launcher

import android.os.Process

/**
 * Reports the privilege level of the current application process.
 *
 * ADB debugging being enabled does not grant an installed application ADB shell.
 * This diagnostic intentionally does not spawn shell commands, change system
 * settings, connect to adbd, or attempt privilege escalation.
 */
internal object AdbAccessDiagnostics {
    internal data class Result(
        val shellAvailable: Boolean,
        val systemInfoAvailable: Boolean,
        val packageManagementAvailable: Boolean,
        val rootAvailable: Boolean,
        val portLabel: String,
        val details: String
    )

    fun inspect(): Result {
        val uid = Process.myUid()
        val isRoot = uid == 0
        val isShell = uid == 2000
        val hasPrivilegedPackageControl = isRoot || isShell

        val details = when {
            isRoot -> "Процесс RFTV работает с UID 0 (root). Это фактический уровень привилегий процесса."
            isShell -> "Процесс RFTV работает с UID 2000 (shell). Доступность конкретных команд всё равно требует отдельной проверки."
            else -> "Приложение работает с обычным UID $uid. Включённый системный ADB Debugging не передаёт приложению ADB Shell."
        }

        return Result(
            shellAvailable = isRoot || isShell,
            systemInfoAvailable = true,
            packageManagementAvailable = hasPrivilegedPackageControl,
            rootAvailable = isRoot,
            portLabel = "Не определён",
            details = details
        )
    }
}
