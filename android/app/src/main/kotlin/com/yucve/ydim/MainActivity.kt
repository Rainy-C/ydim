package com.yucve.ydim

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.yucve.ydim/quick_settings"
        private const val SHIZUKU_REQUEST_CODE = 0x5944
        private const val ACTION_REDUCE_BRIGHT_COLORS_SETTINGS =
            "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var pendingShizukuResult: MethodChannel.Result? = null

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != SHIZUKU_REQUEST_CODE) return@OnRequestPermissionResultListener
            val result = pendingShizukuResult ?: return@OnRequestPermissionResultListener
            pendingShizukuResult = null
            result.success(
                if (grantResult == PackageManager.PERMISSION_GRANTED) "granted" else "denied",
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "requestAddTile" -> requestAddTile(result)
                    "openExtremeDimSettings" -> openExtremeDimSettings(result)
                    "getStatus" -> runAsync(result) { PrivilegeController.status(this) }
                    "toggleExtremeDim" -> runAsync(result) {
                        val toggle = PrivilegeController.toggle(this)
                        ExtremeDimTileService.requestRefresh(this)
                        toggle.asMap()
                    }
                    "requestShizukuPermission" -> requestShizukuPermission(result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun runAsync(result: MethodChannel.Result, task: () -> Any) {
        executor.execute {
            try {
                val value = task()
                runOnUiThread { result.success(value) }
            } catch (error: Throwable) {
                runOnUiThread {
                    result.error("native_failed", error.message, null)
                }
            }
        }
    }

    private fun requestShizukuPermission(result: MethodChannel.Result) {
        try {
            if (!Shizuku.pingBinder()) {
                result.success("not_running")
                return
            }
            if (Shizuku.isPreV11()) {
                result.success("unsupported")
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                result.success("granted")
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                result.success("denied")
                return
            }

            pendingShizukuResult = result
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (_: Throwable) {
            result.success("not_running")
        }
    }

    private fun requestAddTile(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result.success("manual")
            return
        }
        requestAddTileApi33(result)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTileApi33(result: MethodChannel.Result) {
        val statusBarManager = getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            result.success("unavailable")
            return
        }

        val componentName = ComponentName(this, ExtremeDimTileService::class.java)
        val icon = Icon.createWithResource(this, R.drawable.ic_tile_dim)

        statusBarManager.requestAddTileService(
            componentName,
            getString(R.string.tile_name),
            icon,
            mainExecutor,
        ) { status ->
            val response = when (status) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "added"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "already_added"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "not_added"
                else -> if (status >= 1000) "unavailable" else "not_added"
            }
            result.success(response)
        }
    }

    private fun openExtremeDimSettings(result: MethodChannel.Result) {
        try {
            val intent = Intent(ACTION_REDUCE_BRIGHT_COLORS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                result.success("opened")
            } else {
                startActivity(
                    Intent(Settings.ACTION_DISPLAY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                result.success("fallback")
            }
        } catch (error: Throwable) {
            result.error("open_settings_failed", error.message, null)
        }
    }
}
