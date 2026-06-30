package com.yucve.ydim

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.concurrent.Executors

class ExtremeDimTileService : TileService() {
    companion object {
        private const val ACTION_REDUCE_BRIGHT_COLORS_SETTINGS =
            "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS"

        fun requestRefresh(context: android.content.Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, ExtremeDimTileService::class.java),
                )
            } catch (_: Throwable) {
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerStateObserver()
        refreshTile()
    }

    override fun onStopListening() {
        unregisterStateObserver()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            updateTile()
        }

        executor.execute {
            val result = PrivilegeController.toggle(this)
            Handler(Looper.getMainLooper()).post {
                refreshTile()
                if (!result.success) {
                    openExtremeDimSettings()
                }
            }
        }
    }

    private fun registerStateObserver() {
        if (observerRegistered) return

        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(DimContract.SETTING_KEY),
            false,
            observer,
        )
        observerRegistered = true
    }

    private fun unregisterStateObserver() {
        if (!observerRegistered) return
        contentResolver.unregisterContentObserver(observer)
        observerRegistered = false
    }

    private fun refreshTile() {
        val enabled = PrivilegeController.isEnabled(this)

        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_name)
            contentDescription = getString(
                if (enabled) R.string.tile_enabled else R.string.tile_disabled,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (enabled) R.string.tile_enabled else R.string.tile_disabled,
                )
            }

            updateTile()
        }
    }

    private fun openExtremeDimSettings() {
        val intent = Intent(ACTION_REDUCE_BRIGHT_COLORS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { candidate ->
                if (candidate.resolveActivity(packageManager) != null) {
                    candidate
                } else {
                    Intent(Settings.ACTION_DISPLAY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
