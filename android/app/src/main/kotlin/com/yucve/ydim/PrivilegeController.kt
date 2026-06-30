package com.yucve.ydim

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ToggleResult(
    val success: Boolean,
    val enabled: Boolean,
    val backend: String,
    val message: String,
) {
    fun asMap(): Map<String, Any> = mapOf(
        "success" to success,
        "enabled" to enabled,
        "backend" to backend,
        "message" to message,
    )
}

object PrivilegeController {
    private val rootPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
    )

    fun status(context: Context): Map<String, Any> {
        val adbGranted = hasWriteSecureSettings(context)
        val shizuku = shizukuStatus()
        val lsposedActive = LsposedBridgeClient.ping(context)
        val rootPresent = rootPaths.any { File(it).canExecute() }

        return mapOf(
            "enabled" to isEnabled(context),
            "adb" to if (adbGranted) "已授权" else "未授权",
            "shizuku" to shizuku,
            "lsposed" to if (lsposedActive) "已连接" else "未启用",
            "root" to if (rootPresent) "可尝试使用" else "未检测到",
            "directAvailable" to (adbGranted || shizuku.startsWith("已授权") || lsposedActive || rootPresent),
        )
    }

    fun isEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                DimContract.SETTING_KEY,
                0,
            ) == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun toggle(context: Context): ToggleResult {
        val target = !isEnabled(context)

        if (hasWriteSecureSettings(context) && writeDirect(context, target) && verify(context, target)) {
            return success(target, "ADB 授权")
        }

        if (canUseShizuku() && writeByShizuku(context, target) && verify(context, target)) {
            return success(target, "Shizuku")
        }

        if (LsposedBridgeClient.setEnabled(context, target) && verify(context, target)) {
            return success(target, "LSPosed")
        }

        if (rootPaths.any { File(it).canExecute() } && writeByRoot(target) && verify(context, target)) {
            return success(target, "Root")
        }

        return ToggleResult(
            success = false,
            enabled = isEnabled(context),
            backend = "无可用授权",
            message = "没有可用的直切权限",
        )
    }

    private fun success(enabled: Boolean, backend: String): ToggleResult {
        return ToggleResult(
            success = true,
            enabled = enabled,
            backend = backend,
            message = if (enabled) "已开启极暗模式" else "已关闭极暗模式",
        )
    }

    private fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun writeDirect(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.Secure.putInt(
                context.contentResolver,
                DimContract.SETTING_KEY,
                if (enabled) 1 else 0,
            )
        } catch (_: Throwable) {
            false
        }
    }

    private fun shizukuStatus(): String {
        return try {
            if (!Shizuku.pingBinder()) {
                "未运行"
            } else if (Shizuku.isPreV11()) {
                "版本过旧"
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                when (Shizuku.getUid()) {
                    Process.ROOT_UID -> "已授权（Root）"
                    Process.SHELL_UID -> "已授权（ADB）"
                    else -> "已授权"
                }
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                "已拒绝"
            } else {
                "等待授权"
            }
        } catch (_: Throwable) {
            "未运行"
        }
    }

    private fun canUseShizuku(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeByShizuku(context: Context, enabled: Boolean): Boolean {
        val connected = CountDownLatch(1)
        var service: IExtremeDimService? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = IExtremeDimService.Stub.asInterface(binder)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ExtremeDimUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("ydim")
            .version(1)

        return try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(2, TimeUnit.SECONDS)) {
                false
            } else {
                service?.setEnabled(enabled) == true
            }
        } catch (_: Throwable) {
            false
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (_: Throwable) {
            }
        }
    }

    private fun writeByRoot(enabled: Boolean): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "su",
                    "-c",
                    "settings put secure ${DimContract.SETTING_KEY} ${if (enabled) 1 else 0}",
                ),
            )
            process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun verify(context: Context, expected: Boolean): Boolean {
        repeat(5) {
            if (isEnabled(context) == expected) return true
            Thread.sleep(120)
        }
        return false
    }
}
