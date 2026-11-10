# Android 应用编译环境配置指南

## 项目信息
- **项目名称**: Android BLE 应用（蓝牙低功耗示例）
- **应用ID**: com.example.android.bluetoothadvertisements
- **目标API级别**: 37
- **最低API级别**: 21
- **Java版本**: 1.8

## 系统环境

### 已安装工具
| 工具 | 版本 | 位置 |
|------|------|------|
| Java | OpenJDK 25.0.5 LTS | `/usr/bin/java` |
| Gradle | 9.7.1 (Wrapper) | `./gradlew` |
| Android SDK | - | `/home/codespace/Android/Sdk` |

### Android SDK 组件

#### 已安装的平台和工具
- **Android SDK Platform 37** - `/home/codespace/Android/Sdk/platforms/android-37.0`
- **Android SDK Build-Tools 36.0.0** - `/home/codespace/Android/Sdk/build-tools/36.0.0`
- **Android SDK Build-Tools 37.0.0** - `/home/codespace/Android/Sdk/build-tools/37.0.0`
- **Platform Tools** - `/home/codespace/Android/Sdk/platform-tools`
  - `adb` (Android Debug Bridge)
  - `fastboot`
  - 其他调试工具

## 环境变量

```bash
export ANDROID_SDK_ROOT=/home/codespace/Android/Sdk
export ANDROID_HOME=/home/codespace/Android/Sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH
```

### 配置文件

#### local.properties
位置: `/workspaces/andriod-apk-ble/local.properties`

```properties
sdk.dir=/home/codespace/Android/Sdk
android.useAndroidX=true
```

**说明**: 此文件包含本地Android SDK路径，应不纳入版本控制。

#### gradle.properties
位置: `/workspaces/andriod-apk-ble/gradle.properties`

优化配置包括:
- **Java编译优化**: 禁用Java源/目标版本弃用警告
- **构建缓存**: 启用Gradle Configuration Cache加速构建
- **并行编译**: 启用项目并行执行
- **JVM内存**: 配置为2GB堆大小

## 编译指令

### 编译项目

#### 生成Debug APK
```bash
cd /workspaces/andriod-apk-ble
./gradlew assembleDebug
```
- 输出位置: `Application/build/outputs/apk/debug/Application-debug.apk`

#### 生成Release APK (需配置签名)
```bash
./gradlew assembleRelease
```

### 常用Gradle任务

```bash
# 查看所有可用任务
./gradlew tasks

# 清理构建
./gradlew clean

# 检查构建
./gradlew check

# 只编译Java代码
./gradlew compileDebugJava

# 运行单元测试
./gradlew testDebug

# 运行instrumentation测试
./gradlew connectedAndroidTest

# 生成APK并在连接的设备上安装运行
./gradlew installDebug
./gradlew installDebugAndroidTest
```

## 最新编译结果

### 编译状态: ✅ 成功
- 编译命令: `./gradlew assembleDebug`
- 编译时间: ~28秒 (首次构建)
- 任务执行: 33个任务成功
- APK大小: (见build输出)

### 注意事项
1. Java 25对源/目标版本8的弃用警告已被抑制
2. 第一次编译会下载Gradle wrapper和依赖项
3. 后续编译会因为启用的Configuration Cache而更快

## 故障排除

### 问题: Gradle同步失败
**解决方案**: 
```bash
./gradlew clean --refresh-dependencies
./gradlew assembleDebug
```

### 问题: SDK not found
**解决方案**: 确保`local.properties`中的`sdk.dir`路径正确
```bash
cat local.properties
```

### 问题: Java版本不兼容
**解决方案**: build.gradle已配置使用Java 1.8编译源代码，现有配置兼容Java 25

### 问题: 内存不足
**解决方案**: 在`gradle.properties`中调整JVM参数
```properties
org.gradle.jvmargs=-Xmx4096m
```

## 相关文件

- [Application/build.gradle](/workspaces/andriod-apk-ble/Application/build.gradle) - 应用模块编译配置
- [build.gradle](/workspaces/andriod-apk-ble/build.gradle) - 项目根编译配置
- [settings.gradle](/workspaces/andriod-apk-ble/settings.gradle) - Gradle项目结构
- [local.properties](/workspaces/andriod-apk-ble/local.properties) - 本地SDK配置
- [gradle.properties](/workspaces/andriod-apk-ble/gradle.properties) - Gradle全局配置

## 快速开始

```bash
# 1. 进入项目目录
cd /workspaces/andriod-apk-ble

# 2. 构建Debug APK
./gradlew assembleDebug

# 3. 生成的APK可在以下位置找到
# Application/build/outputs/apk/debug/Application-debug.apk
```

## 参考文档
- [Android官方编译文档](https://developer.android.com/studio/build)
- [Gradle官方文档](https://gradle.org/docs/)
- [Android Gradle Plugin文档](https://developer.android.com/studio/releases/gradle-plugin)
