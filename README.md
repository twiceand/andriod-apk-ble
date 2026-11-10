# 蓝牙扫描

这是 Swift `BluetoothScanner` 应用的 Android 移植版，使用 BLE 扫描和 GATT 连接：

- 手动开始或停止持续扫描，显示设备名称、蓝牙地址、RSSI 和最近发现时间。
- 点击设备建立或断开 GATT 连接；Android 不主动请求配对，连接后等待设备端发起安全请求，再由 Android 蓝牙栈处理配对和加密。
- 显示带时间戳的蓝牙事件日志，最多保留 300 条，并支持清空。
- 对名称包含 `Zephyr` 的设备查找自定义安全状态服务并订阅状态特征；报告等级 1、错误 4 时断开并提示用户。
- 应用进入后台时停止扫描；回到前台后可手动重新开始。

首次使用时，Android 6–11 需要授予定位权限并开启系统定位服务；Android 12 及以上需要授予附近设备权限。当前 Android 工程仍使用旧版 target SDK，因此 Android 12 及以上首次使用也会请求定位权限以兼容 BLE 扫描。连接和配对能力取决于设备与外设支持情况；GATT 连接成功不代表已配对或已加密。

## 构建

在 Android Studio 中打开项目并运行 `Application` 配置，或在 Windows 命令行执行：

```powershell
.\gradlew.bat :Application:assembleDebug
```

应用使用 Android SDK 37 和 Java 8 编译，最低支持 Android 5.0（API 21）。构建需要 JDK 17 或更高版本（Gradle 9.7.1 与 AGP 9.5.0）。
