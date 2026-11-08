package com.rftv.launcher

import android.os.Process

/**
 * Conservative diagnostics for the current application process.
 *
 * ADB debugging being enabled does not grant an installed application ADB shell.
 * UID 2000/root can describe the process privilege level, but does not prove an
 * ADB transport or that package-management commands are available. This check
 * therefore never reports ADB shell or package management as granted.
 *
 * No shell commands are spawned and no device state is changed.
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
        val isShellUid = uid == 2000

        val details = when {
            isRoot -> "Процесс RFTV работает с UID 0 (root), но подключение к ADB transport и доступность команд отдельно не проверялись."
            isShellUid -> "Обнаружен UID 2000 (shell). Это не подтверждает ADB transport или доступность конкретных команд."
            else -> "Приложение работает с обычным UID $uid. Включённый системный ADB Debugging не передаёт приложению ADB Shell."
        }

        return Result(
            shellAvailable = false,
            systemInfoAvailable = true,
            packageManagementAvailable = false,
            rootAvailable = isRoot,
            portLabel = "Не определён",
            details = details
        )
    }
}
