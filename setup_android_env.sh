#!/bin/bash

# Android 编译环境快速设置脚本
# 使用说明: source setup_android_env.sh 或 bash setup_android_env.sh

set -e

echo "================================"
echo "Android 编译环境快速设置"
echo "================================"
echo ""

# 检查Android SDK目录
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"

if [ ! -d "$SDK_DIR" ]; then
    echo "✗ 错误: Android SDK 未安装在 $SDK_DIR"
    echo "  请运行: mkdir -p ~/Android/Sdk"
    exit 1
fi

# 设置环境变量
export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

echo "✓ 环境变量已设置:"
echo "  ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo "  ANDROID_HOME=$ANDROID_HOME"
echo ""

# 验证关键工具
echo "✓ 检查工具可用性:"

if command -v java &> /dev/null; then
    echo "  Java: $(java -version 2>&1 | head -1 | awk '{print $1, $3}')"
else
    echo "  ✗ Java 未找到"
    exit 1
fi

if command -v sdkmanager &> /dev/null; then
    echo "  ✓ Android SDK Manager: 可用"
else
    echo "  ✗ Android SDK Manager 未找到"
fi

if [ -f "gradlew" ]; then
    echo "  ✓ Gradle Wrapper: 可用"
else
    echo "  ⚠ Gradle Wrapper 未找到 (gradlew)"
fi

echo ""
echo "✓ 安装的SDK组件:"
[ -d "$SDK_DIR/platforms" ] && echo "  $(ls -d $SDK_DIR/platforms/android-* 2>/dev/null | wc -l) 个 Android SDK Platform"
[ -d "$SDK_DIR/build-tools" ] && echo "  $(ls -d $SDK_DIR/build-tools/* 2>/dev/null | wc -l) 个 Build Tools"
[ -d "$SDK_DIR/platform-tools" ] && echo "  ✓ Platform Tools"
echo ""

# 检查项目配置
if [ -f "local.properties" ]; then
    echo "✓ 项目配置文件已就位:"
    echo "  - local.properties"
fi

if [ -f "gradle.properties" ]; then
    echo "  - gradle.properties"
fi

echo ""
echo "================================"
echo "✓ 环境配置完成！"
echo "================================"
echo ""
echo "快速开始:"
echo "  1. 构建Debug APK: ./gradlew assembleDebug"
echo "  2. 清理: ./gradlew clean"
echo "  3. 查看任务: ./gradlew tasks"
echo ""
