package com.rftv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Narrow Shizuku integration; it never runs arbitrary shell commands. */
internal object TvManagerShizukuBridge {
    const val REQUEST_CODE = 7401
    private const val USER_SERVICE_TAG = "com.rftv.launcher.tvmanager"
    private const val USER_SERVICE_VERSION = 2

    fun isBinderReady(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun isSupportedServer(): Boolean = try {
        isBinderReady() && !Shizuku.isPreV11() && Shizuku.getVersion() >= 11
    } catch (_: Throwable) {
        false
    }

    fun serverUid(): Int = try {
        if (isBinderReady()) Shizuku.getUid() else -1
    } catch (_: Throwable) {
        -1
    }

    fun hasPermission(): Boolean = try {
        isSupportedServer() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        check(isSupportedServer()) { "Shizuku server is not ready or its API is unsupported" }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun bind(context: Context, connection: ServiceConnection): Boolean {
        if (!isSupportedServer() || !hasPermission()) return false
        val args = Shizuku.UserServiceArgs(
            ComponentName(context, TvManagerShellService::class.java)
        ).daemon(false)
            .tag(USER_SERVICE_TAG)
            .processNameSuffix("tvmanager")
            .debuggable(false)
            .version(USER_SERVICE_VERSION)
        Shizuku.bindUserService(args, connection)
        return true
    }

    fun unbind(context: Context, connection: ServiceConnection) {
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, TvManagerShellService::class.java)
            ).daemon(false)
                .processNameSuffix("tvmanager")
                .debuggable(false)
                .version(1)
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Throwable) {
            // Service may already have died or never been bound.
        }
    }
}
