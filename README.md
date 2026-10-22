AndroidManifest.xml 中完全不声明 INTERNET 与网络状态权限，应用在物理层面上没有任何网络通信能力。

在 accessibility_service_config.xml 中严格配置：
canRetrieveWindowContent="false"（禁止获取/读取任何窗口与界面内容）；
canPerformGestures="false"（禁止手势监听与操作）；
canRequestFilterKeyEvents="false"（禁止截获键盘输入）；
accessibilityEventTypes=""（不订阅任何系统无障碍事件）。
该服务唯一且仅具备的功能就是在点击磁贴时派发全局动作 GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE / GLOBAL_ACTION_BACK，安全纯净。

待机结束或停止时，及时解除所有 WakeLock 并调用 stopForeground(STOP_FOREGROUND_REMOVE) + stopSelf()；
未在息屏挂机时，进程无任何后台常驻轮询，符合 Android 标准 Cached Apps 规范，不占用 CPU 与电池。
