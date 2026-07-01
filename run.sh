#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "=========================================="
echo "  价格采集器 - 一键运行"
echo "=========================================="
echo ""

echo "[1/5] 编译 APK..."
./gradlew assembleDebug 2>&1 | tail -3
echo ""

echo "[2/5] 安装 APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tail -2
echo ""

echo "[3/5] 重启无障碍服务..."
adb shell am force-stop com.waibao.floatingcollector || true
sleep 1
adb shell settings put secure enabled_accessibility_services '""'
sleep 1
adb shell settings put secure enabled_accessibility_services com.waibao.floatingcollector/.CollectorAccessibilityService
adb shell settings put secure accessibility_enabled 1
sleep 1
echo "    无障碍服务已启用"
echo ""

echo "[4/5] 启动 tap_helper（菜亿萝采集需要）..."
if pgrep -f "tap_helper.sh" > /dev/null; then
    echo "    tap_helper 已在运行"
else
    bash tap_helper.sh &
    echo "    tap_helper 已启动"
fi
echo ""

echo "[5/5] 启动 App..."
adb shell am start -n com.waibao.floatingcollector/.MainActivity > /dev/null
echo "    App 已启动"
echo ""

echo "=========================================="
echo "  ✅ 完成！"
echo "  📱 查看手机，选择 App 按钮开始采集"
echo "=========================================="
