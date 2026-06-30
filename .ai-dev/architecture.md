# 架构设计

## 系统架构
```
┌─────────────────────────────────────────┐
│           MainActivity (引导页)          │
│  [选择App] [开始自动采集] [查看结果]      │
└──────────────┬──────────────────────────┘
               │ 启动服务
┌──────────────▼──────────────────────────┐
│     CollectorAccessibilityService        │
│  ┌─────────────────────────────────┐    │
│  │     AutoCollectEngine           │    │
│  │  1. 启动目标App                  │    │
│  │  2. 找搜索框→输入关键词          │    │
│  │  3. 点搜索→等结果                │    │
│  │  4. 采集价格→滚动→再采集         │    │
│  │  5. 返回→下一个关键词            │    │
│  │  6. 保存结果                     │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │     PriceExtractor              │    │
│  │  遍历节点→找¥价格→找商品名       │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │     FloatingWindow              │    │
│  │  显示进度+结果                   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

## 数据流
```
预设关键词列表 → AutoCollectEngine
  → 打开目标App (am start)
  → 找搜索框 (findAccessibilityNodeInfosByText/ById)
  → ACTION_SET_TEXT 输入关键词
  → ACTION_CLICK 搜索按钮
  → 等待 3s (TYPE_WINDOW_CONTENT_CHANGED)
  → PriceExtractor.collectPrices(rootInActiveWindow)
  → 滚动 (GestureDescription 或 input swipe)
  → 再采集
  → GLOBAL_ACTION_BACK
  → 下一个关键词
  → 全部完成 → 保存到 /sdcard/采集结果_<app>.txt
```

## 关键设计决策
1. **自动操作通过无障碍服务**：AccessibilityService 可执行 ACTION_SET_TEXT、ACTION_CLICK、GLOBAL_ACTION_BACK，实现全自动操作
2. **搜索框定位**：通过 resource-id 或 text="搜索" 查找搜索入口，再找 EditText
3. **关键词列表**：硬编码 10 个常见生鲜商品关键词
4. **结果保存**：写入 /sdcard/采集结果_<app名>.txt，同时通过 logcat 输出
5. **悬浮窗**：显示当前进度（第几个关键词、采集到几条）
6. **App 适配**：大润发优鲜和菜亿萝的搜索框 resource-id 不同，需分别适配
