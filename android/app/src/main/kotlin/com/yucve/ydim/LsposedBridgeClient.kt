package com.yucve.ydim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LsposedBridgeClient {
    private const val TIMEOUT_MS = 650L

    fun ping(context: Context): Boolean {
        return request(context, DimContract.ACTION_LSPOSED_PING, null)
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        return request(context, DimContract.ACTION_LSPOSED_SET, enabled)
    }

    private fun request(context: Context, action: String, enabled: Boolean?): Boolean {
        val requestId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        var success = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != DimContract.ACTION_LSPOSED_REPLY) return
                if (intent.getStringExtra(DimContract.EXTRA_REQUEST_ID) != requestId) return
                success = intent.getBooleanExtra(DimContract.EXTRA_SUCCESS, false)
                latch.countDown()
            }
        }

        try {
            val filter = IntentFilter(DimContract.ACTION_LSPOSED_REPLY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }

            val intent = Intent(action)
                .setPackage(DimContract.SYSTEM_UI_PACKAGE)
                .putExtra(DimContract.EXTRA_REQUEST_ID, requestId)

            if (enabled != null) {
                intent.putExtra(DimContract.EXTRA_ENABLED, enabled)
            }

            context.sendBroadcast(intent)
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            success = false
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }

        return success
    }
}
