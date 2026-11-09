package com.rftv.launcher

import android.os.Build
import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * Runs inside Shizuku's isolated user-service process (ADB shell or root UID).
 * The exposed Binder API is intentionally limited to package enabled-state changes.
 */
class TvManagerShellService : ITvManagerShellService.Stub() {

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }

    override fun getUid(): Int = Process.myUid()

    override fun setEnabledSetting(packageName: String, enabledState: Int, userId: Int): String {
        if (Process.myUid() != 0 && Process.myUid() != 2000) {
            return "ERROR: неподдерживаемый UID привилегированного процесса"
        }
        if (!PACKAGE_NAME.matches(packageName) || packageName == OWN_PACKAGE) {
            return "ERROR: недопустимый идентификатор пакета"
        }
        if (userId != 0) return "ERROR: поддерживается только основной пользователь Android (0)"
        val command = when (enabledState) {
            0 -> "default-state"
            1 -> "enable"
            2 -> "disable"
            3 -> "disable-user"
            4 -> "disable-until-used"
            else -> return "ERROR: неизвестное состояние пакета"
        }

        val cmd = runCommand(
            listOf("/system/bin/cmd", "package", command, "--user", userId.toString(), packageName)
        ) ?: runCommand(
            listOf("/system/bin/pm", command, "--user", userId.toString(), packageName)
        ) ?: return "ERROR: не удалось запустить системную команду package manager"

        return if (cmd.exitCode == 0) {
            "OK: ${cmd.output.trim()}"
        } else {
            "ERROR (exit ${cmd.exitCode}): ${cmd.output.trim()}"
        }
    }

    private data class CommandResult(val exitCode: Int, val output: String)

    private fun runCommand(arguments: List<String>): CommandResult? {
        val executable = arguments.first()
        if (!java.io.File(executable).canExecute()) return null
        return try {
            val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(20, TimeUnit.SECONDS)
            } else {
                process.waitFor()
                true
            }
            if (!completed) {
                process.destroy()
                return CommandResult(-1, "timeout")
            }
            CommandResult(process.exitValue(), output)
        } catch (error: Exception) {
            CommandResult(-1, error.message ?: error.javaClass.simpleName)
        }
    }

    private companion object {
        const val OWN_PACKAGE = "com.rftv.launcher"
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
