# 知识管理

## 架构决策
- 使用 TYPE_ACCESSIBILITY_OVERLAY 创建悬浮窗，无需 SYSTEM_ALERT_WINDOW 权限
- 使用纯 Android View（非 Compose），减少依赖
- 包名 com.waibao.floatingcollector

## 调试经验
- adb shell input text 不支持中文，需用 AccessibilityService ACTION_SET_TEXT 输入中文
- adb shell settings put secure enabled_accessibility_services 可通过 adb 开启无障碍服务
- uiautomator dump 在有动画/overlay 时可能失败，用 --compressed 参数或 logcat 替代
- 大润发优鲜包名 com.rt.market.fresh，搜索按钮 resource-id: com.rt.market.fresh:id/tv_search_btn
- 大润发优鲜价格在 text 节点中含「¥」符号，价格文本可能含额外信息需用正则清洗
- 菜亿萝包名 com.caiyiluo.nip

## 已知陷阱
- 无障碍服务重装后需重新通过 adb settings 开启
- rootInActiveWindow 返回的是前台 App 的窗口，不包含 overlay
- 大润发优鲜 home Activity 不可直接 am start（not exported），需用 monkey 启动
