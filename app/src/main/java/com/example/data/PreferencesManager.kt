package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppConfig(
    val delaySeconds: Int = 0, // Instant by default, or configurable
    val fiveFingerWake: Boolean = true,
    val powerKeyWake: Boolean = true,
    val emergencyTapWake: Boolean = true,
    val hapticFeedback: Boolean = true,
    val cpuWakeLock: Boolean = true,
    val oledPureBlack: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val timerEnabled: Boolean = false, // Timer switch
    val timerMinutes: Int = 120 // 1-12 hours in 30-min steps (60 to 720 mins)
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fake_standby_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private fun loadConfig(): AppConfig {
        return AppConfig(
            delaySeconds = prefs.getInt(KEY_DELAY_SECONDS, 0),
            fiveFingerWake = prefs.getBoolean(KEY_FIVE_FINGER_WAKE, true),
            powerKeyWake = prefs.getBoolean(KEY_POWER_KEY_WAKE, true),
            emergencyTapWake = prefs.getBoolean(KEY_EMERGENCY_TAP_WAKE, true),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
            cpuWakeLock = prefs.getBoolean(KEY_CPU_WAKE_LOCK, true),
            oledPureBlack = prefs.getBoolean(KEY_OLED_PURE_BLACK, true),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_BOOT, false),
            timerEnabled = prefs.getBoolean(KEY_TIMER_ENABLED, false),
            timerMinutes = prefs.getInt(KEY_TIMER_MINUTES, 120)
        )
    }

    fun setDelaySeconds(seconds: Int) {
        prefs.edit().putInt(KEY_DELAY_SECONDS, seconds).apply()
        _config.value = _config.value.copy(delaySeconds = seconds)
    }

    fun setFiveFingerWake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FIVE_FINGER_WAKE, enabled).apply()
        _config.value = _config.value.copy(fiveFingerWake = enabled)
    }

    fun setPowerKeyWake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POWER_KEY_WAKE, enabled).apply()
        _config.value = _config.value.copy(powerKeyWake = enabled)
    }

    fun setEmergencyTapWake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMERGENCY_TAP_WAKE, enabled).apply()
        _config.value = _config.value.copy(emergencyTapWake = enabled)
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _config.value = _config.value.copy(hapticFeedback = enabled)
    }

    fun setCpuWakeLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CPU_WAKE_LOCK, enabled).apply()
        _config.value = _config.value.copy(cpuWakeLock = enabled)
    }

    fun setOledPureBlack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OLED_PURE_BLACK, enabled).apply()
        _config.value = _config.value.copy(oledPureBlack = enabled)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_BOOT, enabled).apply()
        _config.value = _config.value.copy(autoStartOnBoot = enabled)
    }

    fun setTimerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIMER_ENABLED, enabled).apply()
        _config.value = _config.value.copy(timerEnabled = enabled)
    }

    fun setTimerMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_TIMER_MINUTES, minutes).apply()
        _config.value = _config.value.copy(timerMinutes = minutes)
    }

    companion object {
        private const val KEY_DELAY_SECONDS = "delay_seconds"
        private const val KEY_FIVE_FINGER_WAKE = "five_finger_wake"
        private const val KEY_POWER_KEY_WAKE = "power_key_wake"
        private const val KEY_EMERGENCY_TAP_WAKE = "emergency_tap_wake"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_CPU_WAKE_LOCK = "cpu_wake_lock"
        private const val KEY_OLED_PURE_BLACK = "oled_pure_black"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_TIMER_ENABLED = "timer_enabled"
        private const val KEY_TIMER_MINUTES = "timer_minutes"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
