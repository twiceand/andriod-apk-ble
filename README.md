# 蓝牙设备助手

这是一个简单的 Android BLE（低功耗蓝牙）示例应用，包含：

- 扫描附近的 BLE 设备，显示设备名称、蓝牙地址、信号强度和最近发现时间。
- 点击右上角按钮重新扫描（每次扫描约 5 秒）。
- 开启或关闭本机 BLE 广播，其他设备可以扫描到本机。
- 点击扫描结果会先建立 BLE GATT 连接，连接成功后再发起系统配对请求。

首次扫描时需要授予定位权限（Android 6–11）或附近设备权限（Android 12 及以上）。要验证本机广播，请使用另一台设备；设备通常无法扫描到自己的广播。并非所有 BLE 设备都支持配对；连接只建立 GATT 链路，不会自动订阅通知或读写特征值。

## 构建

在 Android Studio 中打开项目并运行 `Application` 配置，或在 Windows 命令行执行：

```powershell
.\gradlew.bat :Application:assembleDebug
```

应用使用 Android SDK 37 和 Java 8 编译，最低支持 Android 5.0（API 21）。构建需要 JDK 17 或更高版本（Gradle 9.7.1 与 AGP 9.5.0）。
