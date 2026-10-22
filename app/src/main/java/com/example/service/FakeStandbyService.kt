package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesManager
import com.example.receiver.FakeStandbyAdminReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeStandbyService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefsManager: PreferencesManager
    private var overlayView: View? = null
    private var countdownOverlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var powerKeyReceiver: BroadcastReceiver? = null
    private var countDownTimer: CountDownTimer? = null
    private var lockTimer: CountDownTimer? = null

    // Touch gesture tracking for emergency wake
    private var tapCount = 0
    private var lastTapTime = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefsManager = PreferencesManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopStandby()
                stopSelf()
            }
            ACTION_TOGGLE -> {
                if (_isStandbyActive.value) {
                    stopStandby()
                    stopSelf()
                } else {
                    initiateStandby()
                }
            }
            ACTION_START -> {
                initiateStandby()
            }
        }

        // Return START_NOT_STICKY: When idle, process smoothly enters Android cached state
        return START_NOT_STICKY
    }

    private fun initiateStandby() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(mainIntent)
            stopSelf()
            return
        }

        val config = prefsManager.config.value
        val delay = config.delaySeconds

        startForeground(NOTIFICATION_ID, buildNotification(isStandby = false, countingDown = delay > 0, remainingMillis = null))

        if (delay > 0) {
            startCountdown(delay)
        } else {
            activateOverlay()
        }
    }

    private fun startCountdown(seconds: Int) {
        removeCountdownOverlay()
        removeOverlay()

        val countdownView = FrameLayout(this).apply {
            setBackgroundColor(0xEE000000.toInt())
            isClickable = true
            isFocusable = true
        }

        val textView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            text = "将在 $seconds 秒后进入息屏挂机\n点击屏幕任意位置可取消"
        }

        countdownView.addView(
            textView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        countdownView.setOnClickListener {
            cancelCountdown()
            Toast.makeText(this, "息屏挂机已取消", Toast.LENGTH_SHORT).show()
            stopStandby()
            stopSelf()
        }

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(countdownView, windowParams)
            countdownOverlayView = countdownView
        } catch (e: Exception) {
            Log.e(TAG, "Error showing countdown overlay", e)
        }

        countDownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000) + 1
                textView.text = "将在 $remaining 秒后进入息屏挂机\n点击屏幕任意位置可取消"
            }

            override fun onFinish() {
                removeCountdownOverlay()
                activateOverlay()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        removeCountdownOverlay()
    }

    private fun removeCountdownOverlay() {
        countdownOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing countdown overlay", e)
            }
            countdownOverlayView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun activateOverlay() {
        removeOverlay()

        val config = prefsManager.config.value

        // Acquire WakeLock if enabled
        if (config.cpuWakeLock) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FakeStandby:GameAfkLock").apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L)
            }
        }

        // Register power button / lock key receiver
        if (config.powerKeyWake) {
            registerPowerKeyReceiver()
        }

        // Create AMOLED Black Overlay View
        val blackView = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            fitsSystemWindows = false
        }

        // Hide Android Status Bar, Navigation Bar, and 3-Button Nav
        blackView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                hideSystemBars(v)
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        // Intercept all touches: underlying game cannot receive any clicks
        blackView.setOnTouchListener { _, event ->
            handleStandbyTouchEvent(event)
            true
        }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF // Minimum backlight
        }

        try {
            windowManager.addView(blackView, overlayParams)
            overlayView = blackView
            _isStandbyActive.value = true

            // Hide system bars. Note: Back simulation is ONLY performed by FakeStandbyTileService,
            // NOT here, so in-app toggle never triggers the back key!
            hideSystemBars(blackView)

            // Start Timer if enabled (1-12 hours in 30-min steps)
            if (config.timerEnabled && config.timerMinutes > 0) {
                startLockTimer(config.timerMinutes)
            } else {
                updateNotificationState(remainingMillis = null)
            }

            // Haptic feedback
            if (config.hapticFeedback) {
                performHapticPulse(short = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach standby overlay", e)
            Toast.makeText(this, "悬浮窗加载失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            stopStandby()
            stopSelf()
        }
    }

    private fun hideSystemBars(view: View) {
        view.windowInsetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startLockTimer(minutes: Int) {
        lockTimer?.cancel()
        val totalMillis = minutes * 60 * 1000L

        updateNotificationState(remainingMillis = totalMillis)

        lockTimer = object : CountDownTimer(totalMillis, 60000L) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotificationState(remainingMillis = millisUntilFinished)
            }

            override fun onFinish() {
                handleTimerLock()
            }
        }.start()
    }

    private fun handleTimerLock() {
        Log.d(TAG, "Lock timer finished: locking phone screen")
        Toast.makeText(applicationContext, "定时挂机结束，正在锁定手机", Toast.LENGTH_LONG).show()

        stopStandby()

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, FakeStandbyAdminReceiver::class.java)

        if (dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                Log.e(TAG, "DevicePolicyManager lockNow failed", e)
            }
        }
        stopSelf()
    }

    private fun handleStandbyTouchEvent(event: MotionEvent) {
        val config = prefsManager.config.value

        // Method 1: Five-finger touch wake (五指触摸唤醒)
        if (config.fiveFingerWake && event.pointerCount >= 5) {
            triggerWakeUp("五指触控唤醒成功")
            return
        }

        // Method 2: Emergency 5 rapid taps
        if (config.emergencyTapWake && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 500) {
                tapCount++
                if (tapCount >= 5) {
                    tapCount = 0
                    triggerWakeUp("应急五连击唤醒成功")
                    return
                }
            } else {
                tapCount = 1
            }
            lastTapTime = now
        }
    }

    private fun triggerWakeUp(reason: String) {
        if (prefsManager.config.value.hapticFeedback) {
            performHapticPulse(short = false)
        }
        Toast.makeText(applicationContext, reason, Toast.LENGTH_SHORT).show()
        stopStandby()
        stopSelf()
    }

    private fun registerPowerKeyReceiver() {
        if (powerKeyReceiver != null) return

        powerKeyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> {
                        stopStandby()
                        stopSelf()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerKeyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerKeyReceiver, filter)
        }
    }

    private fun unregisterPowerKeyReceiver() {
        powerKeyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            powerKeyReceiver = null
        }
    }

    private fun performHapticPulse(short: Boolean) {
        try {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            if (short) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val timings = longArrayOf(0, 70, 70, 100)
                val amplitudes = intArrayOf(0, 200, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic vibration failed", e)
        }
    }

    private fun updateNotificationState(remainingMillis: Long?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(isStandby = true, countingDown = false, remainingMillis = remainingMillis)
        )
    }

    private fun stopStandby() {
        cancelCountdown()
        lockTimer?.cancel()
        lockTimer = null

        removeOverlay()
        unregisterPowerKeyReceiver()

        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing wakeLock", e)
                }
            }
            wakeLock = null
        }

        _isStandbyActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlayView", e)
            }
            overlayView = null
        }
    }

    private fun buildNotification(isStandby: Boolean, countingDown: Boolean, remainingMillis: Long?): Notification {
        val stopIntent = Intent(this, FakeStandbyService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 101, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val appIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val appPendingIntent = PendingIntent.getActivity(this, 102, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = when {
            isStandby -> {
                if (remainingMillis != null) {
                    val remainingMins = (remainingMillis / 60000L).toInt()
                    val hrs = remainingMins / 60
                    val mins = remainingMins % 60
                    if (hrs > 0) "息屏挂机中 · 剩余 ${hrs}小时${mins}分锁屏" else "息屏挂机中 · 剩余 ${mins}分钟锁屏"
                } else {
                    getString(R.string.notification_title)
                }
            }
            countingDown -> "FakeStandby 准备息屏中..."
            else -> getString(R.string.notification_ready_title)
        }

        val content = if (isStandby) "状态栏已收起，亮度已降至最低。五指触摸或按锁屏键恢复"
        else if (countingDown) "倒计时结束将隐藏状态栏并覆盖全黑，阻断误触"
        else getString(R.string.notification_ready_content)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_fakestandby)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(appPendingIntent)
            .setOngoing(isStandby)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isStandby || countingDown) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_action_stop), stopPendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopStandby()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FakeStandbyService"
        const val ACTION_START = "com.example.action.START_STANDBY"
        const val ACTION_STOP = "com.example.action.STOP_STANDBY"
        const val ACTION_TOGGLE = "com.example.action.TOGGLE_STANDBY"
        private const val NOTIFICATION_ID = 20261
        private const val CHANNEL_ID = "fake_standby_running_channel"

        private val _isStandbyActive = MutableStateFlow(false)
        val isStandbyActive: StateFlow<Boolean> = _isStandbyActive.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FakeStandbyService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FakeStandbyService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
