# 需求分析

## 项目背景
业主需要从市场端 App 采集 SKU 价格数据，用于供应商报价谈判和向客户报价。已有基础采集器（悬浮窗+无障碍服务），但需手动点击采集按钮。现需实现自动化采集，无需人工操作。

## 核心需求

### 用户故事 1：自动采集
As a 业主，I want 打开采集器后自动打开目标 App、搜索商品、采集价格，So that 无需手动操作即可获取数据。

### 用户故事 2：多 App 支持
As a 业主，I want 采集器支持大润发优鲜和菜亿萝两个 App，So that 获取多渠道价格对比。

### 用户故事 3：批量商品采集
As a 业主，I want 预设 10 个商品关键词自动逐一搜索采集，So that 一次性获取完整数据。

## 功能范围
- IN SCOPE：自动打开目标 App、自动搜索商品、自动采集价格、结果保存、支持大润发优鲜+菜亿萝
- OUT OF SCOPE：数据库存储、定时任务、接口上传（后续）

## 验收标准
1. 点击「自动采集」后，App 自动打开目标 App 并完成搜索采集，全程无需人工干预
2. 预设 10 个商品关键词，每个都能采集到至少 1 条「商品名+¥价格」数据
3. 大润发优鲜（com.rt.market.fresh）采集成功
4. 菜亿萝（com.caiyiluo.nip）采集成功
5. 采集结果保存到文件

## 技术约束
- Android 无障碍服务（AccessibilityService）实现自动操作
- 使用 ACTION_SET_TEXT 输入中文搜索词（绕过 adb input text 不支持中文的限制）
- 使用 ACTION_CLICK 点击搜索按钮
- 使用 rootInActiveWindow 读取结果
- 使用 performGlobalAction(GLOBAL_ACTION_BACK) 返回
