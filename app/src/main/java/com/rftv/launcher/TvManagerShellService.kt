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
        if (!PACKAGE_NAME.matches(packageName) || isHardProtected(packageName)) {
            return "ERROR: пакет запрещён политикой защиты TV Manager"
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
            val output = StringBuilder()
            val exitCode = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().use { stream ->
                        var line = stream.readLine()
                        while (line != null) {
                            synchronized(output) { output.append(line).append('\n') }
                            line = stream.readLine()
                        }
                    }
                } catch (_: Exception) {
                    // The process may close its stream while being terminated.
                }
            }.apply { isDaemon = true; start() }
            val waiter = Thread {
                try {
                    exitCode.set(process.waitFor())
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }.apply { isDaemon = true; start() }
            waiter.join(TimeUnit.SECONDS.toMillis(20))
            if (waiter.isAlive) {
                process.destroy()
                waiter.join(500L)
                if (waiter.isAlive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                    waiter.join(500L)
                }
                reader.join(500L)
                return CommandResult(-1, "timeout")
            }
            reader.join(1_000L)
            CommandResult(exitCode.get(), synchronized(output) { output.toString() })
        } catch (error: Exception) {
            CommandResult(-1, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun isHardProtected(packageName: String): Boolean {
        val id = packageName.lowercase()
        if (id == OWN_PACKAGE) return true
        if (PackageAnalysisPolicy.isProtectedPackage(id)) return true
        return id.startsWith("com.android.providers.") ||
            id.startsWith("com.android.inputmethod.") ||
            id.startsWith("com.google.android.inputmethod.") ||
            id.startsWith("com.mediatek.") ||
            id.startsWith("com.cvte.") ||
            id.startsWith("com.droidlogic.")
    }

    private companion object {
        const val OWN_PACKAGE = "com.rftv.launcher"
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
