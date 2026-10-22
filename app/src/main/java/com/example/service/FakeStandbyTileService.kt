package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FakeStandbyTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            FakeStandbyService.isStandbyActive.collect { isActive ->
                updateTileState(isActive)
            }
        }
    }

    override fun onStopListening() {
        stateObserverJob?.cancel()
        stateObserverJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先在 FakeStandby 中开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (FakeStandbyService.isStandbyActive.value) {
            FakeStandbyService.stop(this)
            updateTileState(false)
        } else {
            // 1. Simulate Back button to collapse control center without starting any Activity (no PiP)
            val simulatedBack = FakeStandbyAccessibilityService.performBackOrDismissShade()
            if (!simulatedBack) {
                collapseNotificationShade(this)
            }

            // 2. Start foreground overlay service directly over the existing game
            FakeStandbyService.start(this)
            updateTileState(true)
        }
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "已全屏息屏" else "点击开启"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        super.onDestroy()
    }

    companion object {
        fun collapseNotificationShade(context: Context) {
            try {
                val statusBarService = context.getSystemService("statusbar")
                if (statusBarService != null) {
                    val statusBarClass = Class.forName("android.app.StatusBarManager")
                    val collapseMethod = statusBarClass.getMethod("collapsePanels")
                    collapseMethod.isAccessible = true
                    collapseMethod.invoke(statusBarService)
                    return
                }
            } catch (e: Throwable) {
                Log.d("TileService", "collapsePanels reflection failed, trying broadcast fallback", e)
            }

            try {
                @Suppress("DEPRECATION")
                val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                context.sendBroadcast(closeDialogs)
            } catch (ignored: Throwable) {}
        }
    }
}
