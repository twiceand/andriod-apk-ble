# 蓝牙设备助手

这是一个简单的 Android BLE（低功耗蓝牙）示例应用，包含：

- 扫描附近的 BLE 设备，显示设备名称、蓝牙地址、信号强度和最近发现时间。
- 点击右上角按钮重新扫描（每次扫描约 5 秒）。
- 开启或关闭本机 BLE 广播，其他设备可以扫描到本机。

首次扫描时需要授予定位权限，这是旧版 Android 蓝牙扫描 API 的系统要求。要验证本机广播，请使用另一台设备；设备通常无法扫描到自己的广播。

## 构建

在 Android Studio 中打开项目并运行 `Application` 配置，或在 Windows 命令行执行：

```powershell
.\gradlew.bat :Application:assembleDebug
```

应用使用 Android SDK 28 和 Java 8 编译，最低支持 Android 5.0（API 21）。构建需要 JDK 11 或 17（Gradle 7.3.3 与 AGP 7.2.0）。
