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

## 调试经验（V2 自动采集引擎）
- **安装 -r 后无障碍服务会崩溃**：`adb install -r` 后服务虽然 settings 显示启用，但实际 Crashed。
  需先 `settings put secure enabled_accessibility_services '""'` 再设回，并打开 MainActivity 触发 onServiceConnected。
- **MIUI 跨应用 startActivity 确认框可自动处理**：无障碍服务检测到 `com.miui.securitycenter` 包名时，
  用 findClickableNodeByText 找"允许"按钮自动点击，无需用户操作。
- **大润发搜索流程是两步**：首页点"搜索"→KeyWordSearchListActivity（搜索结果页，无 EditText），
  顶部有可点击搜索框区域 `v_keyword_blank`（resource-id），点击它→进入 SearchActivity（有 `search_edit` EditText + `tv_toolbar_search` 搜索按钮）。
- **大润发搜索结果价格在 content-desc**：`tv_price` 节点 class 是 `android.view.View`，text 为空，
  价格数字在 `contentDescription`（如"2.42"），无 ¥ 符号。商品名在同级 `tv_name` 节点的 text。
  需用 `findAccessibilityNodeInfosByViewId("com.rt.market.fresh:id/tv_price")` + contentDescription 提取。
- **重试限制很重要**：每个步骤（search/input）必须有最大重试次数（5-8次），超过后 skipKeyword 跳过，
  避免无限循环卡死整个采集流程。

## 调试经验（菜亿萝采集适配）
- **菜亿萝是 WebView 应用**：io.dcloud.PandoraEntryActivity + android.webkit.WebView，搜索框是原生 EditText，
  但搜索按钮"搜索"是 WebView 内的 TextView，`findAccessibilityNodeInfosByText("搜索")` 在 WebView 中不可靠，
  需用 `findClickableNodeByTextTraversal` 手动遍历整个无障碍树查找。
- **搜索按钮坐标记录+dispatchGesture 点击兜底**：WebView 中 ACTION_SET_TEXT 后搜索按钮可能消失，
  需在输入前用 `preRoot` 记录搜索按钮的 `getBoundsInScreen` 到 `searchBtnBounds`，
  若输入后找不到按钮则用 `dispatchGesture` 在记录坐标处模拟点击。
- **菜亿萝搜索结果页价格结构**：uiautomator dump 看到 `text='￥1.58'`（￥+数字合并在一个 TextView），
  但无障碍服务看到的是分离的 `text='￥'` + `text='1.58'` 两个同级节点。
  采集走通用方式3（findAllPriceNodes 找含 ￥ 的节点），价格提取正常。
- **菜亿萝商品名提取陷阱**：搜索结果页每个商品卡片结构为：
  图片节点(含子节点"历史价格"标签) → 商品名TextView → ￥价格TextView → /单位TextView → "选规格"按钮。
  `findProductName` 先检查兄弟节点的子节点，"历史价格"作为图片节点的子节点比真实商品名更先被遍历到。
  加上 `usedNames` 去重，导致4个商品只采集到1个且名字全是"历史价格"。
  **修复**：在 `isLikelyProductName` 排除列表加入"历史价格""补货中""选规格"等 UI 标签，
  并过滤以"/"开头的单位文本（如"/120g/包"）。
- **菜亿萝首页 vs 搜索结果页结构不同**：首页价格是分离的 `￥` + `0.28` 节点（走方式2 findAllYenNodes）；
  搜索结果页价格是合并的 `￥1.58` 节点（走方式3 findAllPriceNodes）。两种结构都需要支持。

