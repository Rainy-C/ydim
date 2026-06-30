package com.yucve.ydim.xposed

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.yucve.ydim.DimContract
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

class YDimXposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != DimContract.SYSTEM_UI_PACKAGE) return

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Application)?.let {
                            SystemUiBridge.install(it)
                        }
                    }
                },
            )
        } catch (error: Throwable) {
            XposedBridge.log("YDim: SystemUI hook failed: ${error.message}")
        }
    }
}

private object SystemUiBridge {
    private val installed = AtomicBoolean(false)

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        val filter = IntentFilter().apply {
            addAction(DimContract.ACTION_LSPOSED_SET)
            addAction(DimContract.ACTION_LSPOSED_PING)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val requestId = intent.getStringExtra(DimContract.EXTRA_REQUEST_ID) ?: return
                when (intent.action) {
                    DimContract.ACTION_LSPOSED_PING -> reply(context, requestId, true)
                    DimContract.ACTION_LSPOSED_SET -> {
                        val enabled = intent.getBooleanExtra(DimContract.EXTRA_ENABLED, false)
                        val success = try {
                            Settings.Secure.putInt(
                                context.contentResolver,
                                DimContract.SETTING_KEY,
                                if (enabled) 1 else 0,
                            )
                        } catch (error: Throwable) {
                            XposedBridge.log("YDim: SystemUI write failed: ${error.message}")
                            false
                        }
                        reply(context, requestId, success)
                    }
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(
                    receiver,
                    filter,
                    DimContract.BRIDGE_PERMISSION,
                    Handler(Looper.getMainLooper()),
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("DEPRECATION")
                application.registerReceiver(
                    receiver,
                    filter,
                    DimContract.BRIDGE_PERMISSION,
                    Handler(Looper.getMainLooper()),
                )
            }
            XposedBridge.log("YDim: SystemUI bridge ready")
        } catch (error: Throwable) {
            XposedBridge.log("YDim: bridge registration failed: ${error.message}")
        }
    }

    private fun reply(context: Context, requestId: String, success: Boolean) {
        context.sendBroadcast(
            Intent(DimContract.ACTION_LSPOSED_REPLY)
                .setPackage(DimContract.APP_PACKAGE)
                .putExtra(DimContract.EXTRA_REQUEST_ID, requestId)
                .putExtra(DimContract.EXTRA_SUCCESS, success),
        )
    }
}
