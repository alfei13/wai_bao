#!/bin/bash
# 监听logcat中的TAP_REQUEST和SWIPE_REQUEST标记，自动执行adb shell input命令
# 用法: ./tap_helper.sh
# TAP_REQUEST:x:y      → adb shell input tap x y
# SWIPE_REQUEST:x1:y1:x2:y2:duration → adb shell input swipe x1 y1 x2 y2 duration

echo "=== Tap Helper 启动 ==="
echo "监听 TAP_REQUEST / SWIPE_REQUEST 标记..."

adb logcat -s PriceCollector:I | while read line; do
    if [[ "$line" == *"TAP_REQUEST:"* ]]; then
        coords=$(echo "$line" | grep -o 'TAP_REQUEST:[0-9]*:[0-9]*' | sed 's/TAP_REQUEST://')
        x=$(echo "$coords" | cut -d: -f1)
        y=$(echo "$coords" | cut -d: -f2)
        echo "[$(date '+%H:%M:%S')] TAP: ($x, $y)"
        adb shell input tap "$x" "$y"
    elif [[ "$line" == *"SWIPE_REQUEST:"* ]]; then
        coords=$(echo "$line" | grep -o 'SWIPE_REQUEST:[0-9]*:[0-9]*:[0-9]*:[0-9]*:[0-9]*' | sed 's/SWIPE_REQUEST://')
        x1=$(echo "$coords" | cut -d: -f1)
        y1=$(echo "$coords" | cut -d: -f2)
        x2=$(echo "$coords" | cut -d: -f3)
        y2=$(echo "$coords" | cut -d: -f4)
        dur=$(echo "$coords" | cut -d: -f5)
        echo "[$(date '+%H:%M:%S')] SWIPE: ($x1,$y1)→($x2,$y2) ${dur}ms"
        adb shell input swipe "$x1" "$y1" "$x2" "$y2" "$dur"
    fi
done
