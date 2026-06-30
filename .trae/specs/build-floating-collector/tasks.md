# Tasks

- [x] Task 1: 搭建极简 Android 工程骨架
  - [x] SubTask 1.1: 创建 Gradle 工程结构（build.gradle.kts、settings.gradle.kts、gradle wrapper、local.properties 指向 ~/Library/Android/sdk）
  - [x] SubTask 1.2: 配置 AndroidManifest.xml（声明无障碍服务、悬浮窗权限 SYSTEM_ALERT_WINDOW、MainActivity）
  - [x] SubTask 1.3: 编写无障碍服务配置 XML（accessibility_service_config.xml，监听全包，typeWindowContentChanged|typeWindowStateChanged）
  - [x] SubTask 1.4: 执行 `./gradlew assembleDebug` 成功产出 APK

- [x] Task 2: 实现无障碍服务与屏幕节点采集
  - [x] SubTask 2.1: 实现 CollectorAccessibilityService，在 onAccessibilityEvent 中缓存 rootInActiveWindow
  - [x] SubTask 2.2: 实现 collectPrices() 遍历节点树，找出含「¥」的文本节点为价格，从兄弟/父级节点提取商品名称
  - [x] SubTask 2.3: 提供静态方法供悬浮窗调用触发采集并返回结果列表

- [x] Task 3: 实现悬浮窗 UI
  - [x] SubTask 3.1: 实现 FloatingWindowManager，用 WindowManager 添加悬浮 View（标题栏 + 采集按钮 + 结果 TextView + 隐藏/关闭按钮）
  - [x] SubTask 3.2: 实现拖动逻辑（OnTouchListener 处理 MOVE 事件更新 LayoutParams）
  - [x] SubTask 3.3: 实现「采集」按钮点击 → 调用无障碍服务采集 → 结果展示到 TextView
  - [x] SubTask 3.4: 实现最小化/关闭逻辑

- [x] Task 4: 实现 MainActivity 引导页
  - [x] SubTask 4.1: 简单 UI：无障碍状态检测 + 「开启无障碍服务」按钮 + 「启动悬浮窗」按钮
  - [x] SubTask 4.2: 检测无障碍服务是否已启用，未启用时引导跳转系统设置
  - [x] SubTask 4.3: 申请悬浮窗权限 Settings.canDrawOverlays，未授权跳转 ACTION_MANAGE_OVERLAY_PERMISSION

- [x] Task 5: 真机安装与无障碍/悬浮窗权限配置
  - [x] SubTask 5.1: adb install -r 安装 APK 到小米手机
  - [x] SubTask 5.2: 引导/adb 自动开启无障碍服务（am start 跳转无障碍设置）
  - [x] SubTask 5.3: 授予悬浮窗权限（使用 TYPE_ACCESSIBILITY_OVERLAY 无需此权限）
  - [x] SubTask 5.4: 启动应用，确认悬浮窗可弹出

- [x] Task 6: 大润发优鲜真机采集验证（核心验收点）
  - [x] SubTask 6.1: adb 启动大润发优鲜 com.rt.market.fresh
  - [x] SubTask 6.2: 在 App 内进入「肉禽蛋」分类页
  - [x] SubTask 6.3: 通过广播触发采集，确认采集到真实商品名称 + ¥价格
  - [x] SubTask 6.4: 通过 logcat 分析节点特征，优化 collectPrices() 提取逻辑（价格清洗），重新编译安装验证
  - [x] SubTask 6.5: 采集成功后截屏保存证据，记录采集到的数据

# Task Dependencies
- Task 2, Task 3, Task 4 依赖 Task 1
- Task 5 依赖 Task 1-4（编译产出 APK）
- Task 6 依赖 Task 5（已安装并授权）
