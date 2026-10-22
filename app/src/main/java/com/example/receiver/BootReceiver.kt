package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device boot completed, checking auto-start preferences")
            val prefs = PreferencesManager.getInstance(context)
            if (prefs.config.value.autoStartOnBoot) {
                Log.d("BootReceiver", "Auto-start is enabled, readying FakeStandby")
            }
        }
    }
}
