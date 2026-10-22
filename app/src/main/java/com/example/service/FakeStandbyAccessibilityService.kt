package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FakeStandbyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "FakeStandbyAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception required, solely used to dispatch global BACK / DISMISS actions
    }

    override fun onInterrupt() {
        Log.d(TAG, "FakeStandbyAccessibilityService interrupted")
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FakeStandbyA11y"

        @Volatile
        var instance: FakeStandbyAccessibilityService? = null
            private set

        val isEnabled: Boolean
            get() = instance != null

        /**
         * Simulates the Back button (三大金刚返回键) / Dismiss notification shade.
         * This collapses the Quick Settings control center WITHOUT switching tasks or launching activities,
         * thereby completely avoiding triggering Picture-in-Picture (PiP) or windowed mode in games!
         */
        fun performBackOrDismissShade(): Boolean {
            val service = instance ?: return false
            return try {
                var handled = false
                // On Android 12+ (API 31+), try official GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE first
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    handled = service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }
                // Fall back to simulating the physical/gesture Back button
                if (!handled) {
                    handled = service.performGlobalAction(GLOBAL_ACTION_BACK)
                }
                handled
            } catch (e: Exception) {
                Log.e(TAG, "Error performing global back action", e)
                false
            }
        }
    }
}
