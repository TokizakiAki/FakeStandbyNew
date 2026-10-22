package com.example

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.PreferencesManager
import com.example.receiver.FakeStandbyAdminReceiver
import com.example.service.FakeStandbyAccessibilityService
import com.example.service.FakeStandbyService
import com.example.ui.theme.FakeStandbyTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FakeStandbyTheme {
                FakeStandbyMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeStandbyMainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val config by prefsManager.config.collectAsState()
    val isStandbyActive by FakeStandbyService.isStandbyActive.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryIgnoring by remember {
        mutableStateOf(valPowerManager(context).isIgnoringBatteryOptimizations(context.packageName))
    }
    var isDeviceAdminActive by remember {
        mutableStateOf(isDeviceAdminEnabled(context))
    }
    var isAccessibilityActive by remember {
        mutableStateOf(isAccessibilityEnabled(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isBatteryIgnoring = valPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)
                isDeviceAdminActive = isDeviceAdminEnabled(context)
                isAccessibilityActive = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "FakeStandby",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master Action Hero Card
            item(key = "hero_card") {
                MasterStatusHeroCard(
                    isStandbyActive = isStandbyActive,
                    hasOverlayPermission = hasOverlayPermission,
                    onToggleStandby = {
                        if (!hasOverlayPermission) {
                            requestOverlayPermission(context)
                        } else {
                            if (isStandbyActive) {
                                FakeStandbyService.stop(context)
                            } else {
                                FakeStandbyService.start(context)
                            }
                        }
                    }
                )
            }

            // Permissions Section
            item(key = "permissions_card") {
                PermissionStatusSection(
                    hasOverlayPermission = hasOverlayPermission,
                    isBatteryIgnoring = isBatteryIgnoring,
                    isAccessibilityActive = isAccessibilityActive,
                    onRequestOverlay = { requestOverlayPermission(context) },
                    onRequestAccessibility = { requestAccessibilityPermission(context) },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestBatteryExemption = { requestBatteryOptimizationExemption(context) }
                )
            }

            // Timer Configuration Section (1-12 hours in 30-min steps)
            item(key = "timer_card") {
                TimerSettingsSection(
                    timerEnabled = config.timerEnabled,
                    timerMinutes = config.timerMinutes,
                    isDeviceAdminActive = isDeviceAdminActive,
                    onToggleTimer = { prefsManager.setTimerEnabled(it) },
                    onUpdateMinutes = { prefsManager.setTimerMinutes(it) },
                    onRequestAdmin = { requestDeviceAdminPermission(context) }
                )
            }

            // Wake-Up Mechanisms Section
            item(key = "wakeup_card") {
                WakeUpSettingsSection(
                    config = config,
                    onUpdateFourFinger = { prefsManager.setFourFingerWake(it) },
                    onUpdatePowerKey = { prefsManager.setPowerKeyWake(it) },
                    onUpdateEmergencyTap = { prefsManager.setEmergencyTapWake(it) },
                    onUpdateHaptic = { prefsManager.setHapticFeedback(it) }
                )
            }

            // Standby & Power Configuration
            item(key = "power_card") {
                StandbyConfigSection(
                    config = config,
                    onUpdateDelay = { prefsManager.setDelaySeconds(it) },
                    onUpdateCpuLock = { prefsManager.setCpuWakeLock(it) },
                    onUpdateOledPureBlack = { prefsManager.setOledPureBlack(it) },
                    onUpdateAutoBoot = { prefsManager.setAutoStartOnBoot(it) }
                )
            }

            // Open Source Acknowledgment
            item(key = "acknowledgment_card") {
                OpenSourceAcknowledgmentCard(
                    onOpenProject = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JonasBernard/FakeStandby"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开浏览器: https://github.com/JonasBernard/FakeStandby", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item(key = "bottom_space") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MasterStatusHeroCard(
    isStandbyActive: Boolean,
    hasOverlayPermission: Boolean,
    onToggleStandby: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_status_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isStandbyActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStandbyActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        !hasOverlayPermission -> "需要悬浮窗权限"
                        isStandbyActive -> "息屏挂机中 · 状态栏已隐藏"
                        else -> "就绪待命"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isStandbyActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isStandbyActive) "息屏挂机中" else "一键息屏挂机",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleStandby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("toggle_standby_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStandbyActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (isStandbyActive) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isStandbyActive) Icons.Default.Close else Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !hasOverlayPermission -> "前往授予悬浮窗权限"
                        isStandbyActive -> "退出息屏挂机"
                        else -> "立即开启息屏挂机"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun TimerSettingsSection(
    timerEnabled: Boolean,
    timerMinutes: Int,
    isDeviceAdminActive: Boolean,
    onToggleTimer: (Boolean) -> Unit,
    onUpdateMinutes: (Int) -> Unit,
    onRequestAdmin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "定时自动锁屏",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = if (timerEnabled) "已开启定时：达到指定时长后锁定手机"
                        else "已关闭定时：无限时间息屏挂机，直到手动唤醒",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    )
                }
                Switch(
                    checked = timerEnabled,
                    onCheckedChange = onToggleTimer,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            AnimatedVisibility(visible = timerEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hours = timerMinutes / 60
                    val minutes = timerMinutes % 60
                    val durationText = if (minutes == 0) "$hours 小时" else "$hours 小时 $minutes 分钟"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "定时挂机时长",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "$durationText (${timerMinutes}分钟)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    var sliderPos by remember(timerMinutes) {
                        mutableFloatStateOf(timerMinutes.toFloat())
                    }

                    Slider(
                        value = sliderPos,
                        onValueChange = { newVal ->
                            val snapped = (newVal / 30f).roundToInt() * 30
                            sliderPos = snapped.toFloat().coerceIn(60f, 720f)
                        },
                        onValueChangeFinished = {
                            onUpdateMinutes(sliderPos.toInt())
                        },
                        valueRange = 60f..720f,
                        steps = 21,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 小时", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                        Text("刻度: 30分钟", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                        Text("12 小时", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                    }

                    if (!isDeviceAdminActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("系统锁屏管理授权", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
                                Text("授权后定时结束可直接锁定手机屏幕", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                            OutlinedButton(
                                onClick = onRequestAdmin,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("去授权", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusSection(
    hasOverlayPermission: Boolean,
    isBatteryIgnoring: Boolean,
    isAccessibilityActive: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "系统权限与后台规范",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            PermissionItemRow(
                title = "显示在其他应用上层 (悬浮窗)",
                subtitle = "必需权限，用于在游戏上方显示纯黑屏并收起系统导航栏",
                isGranted = hasOverlayPermission,
                actionLabel = "去授权",
                onClick = onRequestOverlay
            )

            PermissionItemRow(
                title = "无障碍服务 (模拟返回键收起控制中心)",
                subtitle = "仅点击磁贴时自动执行返回键收起控制中心，彻底杜绝画中画",
                isGranted = isAccessibilityActive,
                actionLabel = "去开启",
                onClick = onRequestAccessibility
            )

            PermissionItemRow(
                title = "电池不受限制 (后台防杀)",
                subtitle = "不用时符合规范自动进入系统缓存，挂机时不被杀死",
                isGranted = isBatteryIgnoring,
                actionLabel = "去设置",
                onClick = onRequestBatteryExemption
            )

            PermissionItemRow(
                title = "通知权限 (前台服务)",
                subtitle = "支持下拉通知栏随时退出挂机",
                isGranted = true,
                actionLabel = "检查",
                onClick = onRequestNotification
            )
        }
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }

        if (!isGranted) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun WakeUpSettingsSection(
    config: com.example.data.AppConfig,
    onUpdateFourFinger: (Boolean) -> Unit,
    onUpdatePowerKey: (Boolean) -> Unit,
    onUpdateEmergencyTap: (Boolean) -> Unit,
    onUpdateHaptic: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "唤醒与防误触",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            SettingToggleRow(
                icon = Icons.Default.TouchApp,
                title = "四指触摸唤醒",
                desc = "息屏期间所有触控完全阻断。任意四根手指同时贴在屏幕上即可解锁，符合人体工程学，防止误触游戏。",
                checked = config.fourFingerWake,
                onCheckedChange = onUpdateFourFinger
            )

            SettingToggleRow(
                icon = Icons.Default.PowerSettingsNew,
                title = "锁屏键 / 电源键开启",
                desc = "按下机身侧边电源/锁屏键时立即退出挂机并恢复常规屏幕。与四指触摸并列生效、互不干扰。",
                checked = config.powerKeyWake,
                onCheckedChange = onUpdatePowerKey
            )

            SettingToggleRow(
                icon = Icons.Default.DirectionsRun,
                title = "应急五连击唤醒",
                desc = "单手不便使用五指时，快速连续轻触屏幕 5 次亦可紧急恢复屏幕显示。",
                checked = config.emergencyTapWake,
                onCheckedChange = onUpdateEmergencyTap
            )

            SettingToggleRow(
                icon = Icons.Default.Vibration,
                title = "震动反馈提示",
                desc = "成功唤醒或进入挂机时触发震动确认。",
                checked = config.hapticFeedback,
                onCheckedChange = onUpdateHaptic
            )
        }
    }
}

@Composable
fun StandbyConfigSection(
    config: com.example.data.AppConfig,
    onUpdateDelay: (Int) -> Unit,
    onUpdateCpuLock: (Boolean) -> Unit,
    onUpdateOledPureBlack: (Boolean) -> Unit,
    onUpdateAutoBoot: (Boolean) -> Unit
) {
    val delayOptions = remember { listOf(0 to "即时", 3 to "3秒", 5 to "5秒", 10 to "10秒") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "全屏与电源设置",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Column {
                Text(
                    text = "启动延迟倒计时",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    delayOptions.forEach { (seconds, label) ->
                        val selected = config.delaySeconds == seconds
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateDelay(seconds) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            SettingToggleRow(
                icon = Icons.Default.Visibility,
                title = "OLED 纯黑全屏防烧屏",
                desc = "使用纯黑(#000000)关闭全部像素发光，并将屏幕背光降至最低，隐藏全部状态栏与导航键。",
                checked = config.oledPureBlack,
                onCheckedChange = onUpdateOledPureBlack
            )

            SettingToggleRow(
                icon = Icons.Default.BatteryAlert,
                title = "CPU 挂机防休眠锁 (WakeLock)",
                desc = "挂机期间保持 CPU 运转，防止大型游戏后台被系统暂停或被休眠杀掉。",
                checked = config.cpuWakeLock,
                onCheckedChange = onUpdateCpuLock
            )

            SettingToggleRow(
                icon = Icons.Default.PowerSettingsNew,
                title = "开机自动就绪",
                desc = "设备重启后自动待命，快捷磁贴立即可用。",
                checked = config.autoStartOnBoot,
                onCheckedChange = onUpdateAutoBoot
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun OpenSourceAcknowledgmentCard(onOpenProject: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenProject() }
            .testTag("acknowledgment_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开源致谢 · 致敬原作者",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "基于 JonasBernard/FakeStandby 开源项目",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = "点击在默认浏览器中打开原项目主页",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                )
            }
            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = "Open link",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun valPowerManager(context: Context): PowerManager {
    return context.getSystemService(Context.POWER_SERVICE) as PowerManager
}

private fun isDeviceAdminEnabled(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, FakeStandbyAdminReceiver::class.java)
    return dpm.isAdminActive(adminComponent)
}

private fun requestOverlayPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
}

private fun requestBatteryOptimizationExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "请在「设置 - 应用程序 - FakeStandby - 电池」中选择「无限制」", Toast.LENGTH_LONG).show()
    }
}

private fun requestDeviceAdminPermission(context: Context) {
    try {
        val adminComponent = ComponentName(context, FakeStandbyAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "FakeStandby 需要设备管理器权限以在定时挂机结束时自动锁定手机屏幕。")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开设备管理授权设置", Toast.LENGTH_SHORT).show()
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    if (FakeStandbyAccessibilityService.isEnabled) return true
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expected = ComponentName(context, FakeStandbyAccessibilityService::class.java).flattenToString()
    return enabledServices.split(":").any { it.equals(expected, ignoreCase = true) }
}

private fun requestAccessibilityPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
        Toast.makeText(context, "请在「已安装的应用程序」列表中开启 FakeStandby", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "无法直接打开无障碍设置，请在系统设置中手动开启", Toast.LENGTH_SHORT).show()
    }
}
