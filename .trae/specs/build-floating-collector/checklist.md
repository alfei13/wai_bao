# Checklist

## 工程与编译
- [x] Gradle 工程结构完整，local.properties 正确指向 ~/Library/Android/sdk
- [x] AndroidManifest.xml 声明无障碍服务、SYSTEM_ALERT_WINDOW 权限、MainActivity
- [x] accessibility_service_config.xml 配置正确（监听全包，含 typeWindowContentChanged）
- [x] `./gradlew assembleDebug` 成功产出 debug APK

## 功能实现
- [x] CollectorAccessibilityService 能缓存 rootInActiveWindow
- [x] collectPrices() 能从节点树提取「¥价格 + 商品名称」
- [x] 悬浮窗含「采集」按钮、结果显示区、隐藏/关闭按钮
- [x] 悬浮窗可拖动
- [x] 悬浮窗可最小化/关闭
- [x] MainActivity 能检测无障碍状态并引导授权
- [x] MainActivity 能检测悬浮窗权限并引导授权（使用 TYPE_ACCESSIBILITY_OVERLAY 免权限）

## 真机验证
- [x] APK 成功安装到小米手机（device bf8593f2）
- [x] 无障碍服务已开启
- [x] 悬浮窗权限已授予（TYPE_ACCESSIBILITY_OVERLAY 无需单独授权）
- [x] 启动应用后悬浮窗可弹出并悬浮于其他 App 之上
- [x] 打开大润发优鲜 App 正常
- [x] 在分类页点击「采集」后，采集到真实「商品名称 + ¥价格」
- [x] 采集数据与大润发优鲜页面显示内容一致
- [x] 采集成功的截屏/日志证据已保存
