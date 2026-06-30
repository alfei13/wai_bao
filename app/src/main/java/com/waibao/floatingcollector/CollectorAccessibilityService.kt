package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PriceCollector"

class CollectorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var resultText: TextView? = null
    private var contentLayout: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // ===== 自动采集引擎状态 =====
    private val keywords = listOf("鸡蛋", "猪肉", "白菜", "土豆", "番茄", "黄瓜", "牛奶", "面包", "苹果", "香蕉")
    private var autoCollecting = false
    private var currentKeywordIndex = 0
    private var currentApp = ""
    private var currentAppName = ""
    private var searchRetryCount = 0
    private val allResults = mutableListOf<Pair<String, SkuItem>>()
    private val handler = Handler(Looper.getMainLooper())

    private val collectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                "com.waibao.floatingcollector.COLLECT" -> {
                    Log.i(TAG, "收到手动采集广播")
                    performCollect()
                }
                "com.waibao.floatingcollector.AUTO_COLLECT" -> {
                    val app = intent.getStringExtra("app") ?: "darunfa"
                    Log.i(TAG, "收到自动采集广播, app=$app")
                    if (!autoCollecting) {
                        startAutoCollect(app)
                    } else {
                        Log.w(TAG, "自动采集进行中，忽略重复请求")
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接 onServiceConnected")
        showFloatingWindow()
        val filter = IntentFilter().apply {
            addAction("com.waibao.floatingcollector.COLLECT")
            addAction("com.waibao.floatingcollector.AUTO_COLLECT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(collectReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(collectReceiver, filter)
        }
        Log.i(TAG, "广播接收器已注册")
    }

    // ===== 悬浮窗 =====

    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200
        layoutParams = params
        floatingView = buildFloatingView()
        windowManager?.addView(floatingView, params)
    }

    private fun buildFloatingView(): View {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(24, 16, 24, 24)
        }

        val titleBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        val title = TextView(ctx).apply {
            text = "价格采集器"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnHide = Button(ctx).apply {
            text = "隐藏"
            textSize = 12f
            setOnClickListener { minimize() }
        }
        val btnClose = Button(ctx).apply {
            text = "关闭"
            textSize = 12f
            setOnClickListener { removeFloatingWindow() }
        }
        titleBar.addView(title)
        titleBar.addView(btnHide)
        titleBar.addView(btnClose)

        titleBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.let { lp ->
                        lp.x = event.rawX.toInt() - v.width / 2
                        lp.y = event.rawY.toInt() - v.height / 2
                        windowManager?.updateViewLayout(floatingView, lp)
                    }
                }
            }
            true
        }

        val btnCollect = Button(ctx).apply {
            text = "采集当前页面"
            setOnClickListener { performCollect() }
        }

        val scrollView = ScrollView(ctx)
        resultText = TextView(ctx).apply {
            text = "点击「采集」或通过广播触发自动采集"
            textSize = 13f
            setPadding(8, 16, 8, 16)
            setTextIsSelectable(true)
        }
        scrollView.addView(resultText)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        (contentLayout as LinearLayout).addView(btnCollect)
        (contentLayout as LinearLayout).addView(scrollView)

        root.addView(titleBar)
        root.addView(contentLayout)
        root.layoutParams = LinearLayout.LayoutParams(
            (resources.displayMetrics.density * 320).toInt(),
            (resources.displayMetrics.density * 360).toInt()
        )
        return root
    }

    private fun minimize() {
        contentLayout?.visibility = View.GONE
        layoutParams?.let { lp ->
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowManager?.updateViewLayout(floatingView, lp)
        }
    }

    private fun removeFloatingWindow() {
        try { floatingView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        floatingView = null
    }

    private fun updateResult(text: String) {
        handler.post { resultText?.text = text }
    }

    // ===== 手动采集 =====

    private fun performCollect() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "rootInActiveWindow 为空")
            resultText?.text = "未获取到页面内容"
            return
        }
        Log.i(TAG, "=== 开始采集 ===")
        Log.i(TAG, "根节点包名: ${root.packageName}, childCount: ${root.childCount}")
        val items = collectPrices(root)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        if (items.isEmpty()) {
            val allText = mutableListOf<String>()
            dumpTexts(root, allText, 0)
            Log.i(TAG, "未找到价格，屏幕文本节点共 ${allText.size} 个:")
            for (t in allText) Log.i(TAG, "  $t")
            val debug = if (allText.isEmpty()) "（无文本节点）" else allText.take(20).joinToString("\n")
            resultText?.text = "未采集到价格（$time）\n\n$debug"
            return
        }
        Log.i(TAG, "采集到 ${items.size} 条价格:")
        val sb = StringBuilder()
        sb.append("采集时间：$time\n共 ${items.size} 条\n---\n")
        for (item in items) {
            Log.i(TAG, "  ${item.name}  ${item.price}")
            sb.append("${item.name}\n${item.price}\n\n")
        }
        resultText?.text = sb.toString()
    }

    // ===== 自动采集引擎 =====

    private fun startAutoCollect(app: String) {
        currentApp = app
        currentAppName = if (app == "darunfa") "大润发优鲜" else "菜亿萝"
        currentKeywordIndex = 0
        searchRetryCount = 0
        allResults.clear()
        autoCollecting = true

        val packageName = if (app == "darunfa") "com.rt.market.fresh" else "com.caiyiluo.nip"
        Log.i(TAG, "=== 开始自动采集: $currentAppName ($packageName) ===")
        Log.i(TAG, "关键词列表: $keywords")
        updateResult("开始自动采集: $currentAppName\n正在启动目标App...")

        // 由App自己启动目标App（用户要求无需手动操作）
        launchTargetApp(packageName)
        handler.postDelayed({ autoCollectStepSearch() }, 6000)
    }

    private fun launchTargetApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.i(TAG, "已发送启动意图: $packageName")
            } else {
                Log.w(TAG, "无法获取启动意图(可能未安装): $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动App异常: ${e.message}")
        }
    }

    // 处理MIUI跨应用启动确认框，返回true表示已处理
    private fun handleMiuiConfirmIfPresent(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: ""
        if (pkg.contains("miui") || pkg.contains("securitycenter")) {
            Log.i(TAG, "检测到MIUI确认框: $pkg，尝试自动允许")
            // 找"允许"/"始终允许"/"确定"按钮并点击
            val allowBtn = findClickableNodeByText(root, "允许")
                ?: findClickableNodeByText(root, "始终允许")
                ?: findClickableNodeByText(root, "确定")
                ?: findClickableNodeByText(root, "同意")
            if (allowBtn != null) {
                Log.i(TAG, "点击允许按钮")
                allowBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    private fun autoCollectStepSearch() {
        if (!autoCollecting) return
        if (currentKeywordIndex >= keywords.size) {
            autoCollectFinish()
            return
        }
        val keyword = keywords[currentKeywordIndex]
        Log.i(TAG, "--- 关键词 ${currentKeywordIndex + 1}/${keywords.size}: $keyword (retry=$searchRetryCount) ---")
        updateResult("自动采集: $currentAppName\n正在搜索: $keyword (${currentKeywordIndex + 1}/${keywords.size})\n已采集: ${allResults.size}条")

        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "rootInActiveWindow 为空，等待重试")
            searchRetryCount++
            if (searchRetryCount > 8) {
                skipKeyword(keyword, "rootInActiveWindow持续为空")
                return
            }
            handler.postDelayed({ autoCollectStepSearch() }, 2000)
            return
        }

        // 先处理MIUI跨应用启动确认框
        if (handleMiuiConfirmIfPresent()) {
            handler.postDelayed({ autoCollectStepSearch() }, 1500)
            return
        }

        val pkg = root.packageName?.toString() ?: ""
        val targetPkg = if (currentApp == "darunfa") "com.rt.market.fresh" else "com.caiyiluo.nip"
        // 若前台不是目标App，尝试重新启动
        if (pkg != targetPkg) {
            Log.w(TAG, "当前前台: $pkg，非目标App: $targetPkg，尝试重新启动")
            searchRetryCount++
            if (searchRetryCount > 8) {
                skipKeyword(keyword, "无法进入目标App: $pkg")
                return
            }
            launchTargetApp(targetPkg)
            handler.postDelayed({ autoCollectStepSearch() }, 3000)
            return
        }

        if (currentApp == "darunfa") {
            // 优先找 EditText（已在搜索输入页 SearchActivity）
            val editText = findEditText(root)
            if (editText != null) {
                searchRetryCount = 0
                autoCollectStepInputDirectly(keyword, editText)
            } else {
                // 首页: 找"搜索"入口按钮; 结果页: 找搜索框区域 v_keyword_blank
                val entry = findClickableNodeByText(root, "搜索")
                    ?: findNodeById(root, "com.rt.market.fresh:id/v_keyword_blank")
                    ?: findNodeById(root, "com.rt.market.fresh:id/hsv_keyword")
                if (entry != null) {
                    Log.i(TAG, "点击搜索入口/搜索框区域进入搜索页")
                    entry.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    searchRetryCount = 0
                    handler.postDelayed({ autoCollectStepInput(keyword) }, 2000)
                } else {
                    searchRetryCount++
                    if (searchRetryCount > 5) {
                        skipKeyword(keyword, "未找到搜索入口")
                        return
                    }
                    Log.w(TAG, "未找到搜索入口，重试 $searchRetryCount/5")
                    handler.postDelayed({ autoCollectStepSearch() }, 2000)
                }
            }
        } else {
            // 菜亿萝: 搜索框直接在首页
            val editText = findEditText(root)
            if (editText != null) {
                searchRetryCount = 0
                autoCollectStepInputDirectly(keyword, editText)
            } else {
                searchRetryCount++
                if (searchRetryCount > 5) {
                    skipKeyword(keyword, "未找到搜索框")
                    return
                }
                Log.w(TAG, "未找到搜索框，重试 $searchRetryCount/5")
                handler.postDelayed({ autoCollectStepSearch() }, 2000)
            }
        }
    }

    private fun skipKeyword(keyword: String, reason: String) {
        Log.w(TAG, "跳过关键词 '$keyword': $reason")
        updateResult("自动采集: $currentAppName\n跳过: $keyword ($reason)\n已采集: ${allResults.size}条")
        searchRetryCount = 0
        currentKeywordIndex++
        handler.postDelayed({ autoCollectStepSearch() }, 1500)
    }

    private fun autoCollectStepInput(keyword: String) {
        if (!autoCollecting) return
        val root = rootInActiveWindow ?: run {
            searchRetryCount++
            if (searchRetryCount > 6) {
                skipKeyword(keyword, "input阶段root为空")
                return
            }
            handler.postDelayed({ autoCollectStepInput(keyword) }, 1500)
            return
        }
        val editText = findEditText(root)
        if (editText != null) {
            searchRetryCount = 0
            autoCollectStepInputDirectly(keyword, editText)
        } else {
            // 大润发: 在搜索结果页需点击搜索框区域进入搜索输入页
            if (currentApp == "darunfa") {
                val searchBox = findNodeById(root, "com.rt.market.fresh:id/v_keyword_blank")
                    ?: findNodeById(root, "com.rt.market.fresh:id/hsv_keyword")
                if (searchBox != null) {
                    Log.i(TAG, "点击搜索框区域进入搜索输入页")
                    searchBox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            searchRetryCount++
            if (searchRetryCount > 6) {
                skipKeyword(keyword, "搜索页未找到输入框")
                return
            }
            Log.w(TAG, "搜索页未找到输入框，重试 $searchRetryCount/6")
            handler.postDelayed({ autoCollectStepInput(keyword) }, 1800)
        }
    }

    private fun autoCollectStepInputDirectly(keyword: String, editText: AccessibilityNodeInfo) {
        if (!autoCollecting) return
        Log.i(TAG, "输入关键词: $keyword")
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        handler.postDelayed({ autoCollectStepTriggerSearch(keyword) }, 1000)
    }

    private fun autoCollectStepTriggerSearch(keyword: String) {
        if (!autoCollecting) return
        val root = rootInActiveWindow ?: run {
            handler.postDelayed({ autoCollectStepTriggerSearch(keyword) }, 1500)
            return
        }
        // 找"搜索"按钮点击
        val searchBtn = findClickableNodeByText(root, "搜索")
        if (searchBtn != null) {
            Log.i(TAG, "点击搜索按钮触发搜索")
            searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // 尝试按回车
            Log.i(TAG, "未找到搜索按钮，尝试IME enter")
        }
        // 等待搜索结果加载
        handler.postDelayed({ autoCollectStepCollect(keyword) }, 4000)
    }

    private fun autoCollectStepCollect(keyword: String) {
        if (!autoCollecting) return
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "采集时rootInActiveWindow为空")
            handler.postDelayed({ autoCollectStepBack(keyword, emptyList()) }, 1000)
            return
        }
        Log.i(TAG, "开始采集搜索结果, 根包名: ${root.packageName}")
        val items = collectPrices(root)
        Log.i(TAG, "关键词 '$keyword' 首屏采集到 ${items.size} 条")
        for (item in items) {
            Log.i(TAG, "  ${item.name} = ${item.price}")
            allResults.add(keyword to item)
        }

        // 滚动后采集更多
        handler.postDelayed({ autoCollectStepScrollAndCollect(keyword, items) }, 1500)
    }

    private fun autoCollectStepScrollAndCollect(keyword: String, firstBatch: List<SkuItem>) {
        if (!autoCollecting) return
        Log.i(TAG, "滚动页面采集更多...")
        scrollDown()

        handler.postDelayed({
            val root = rootInActiveWindow
            if (root != null) {
                val moreItems = collectPrices(root)
                val existingNames = firstBatch.map { it.name }.toSet()
                val newItems = moreItems.filter { it.name !in existingNames }
                Log.i(TAG, "关键词 '$keyword' 滚动后新增 ${newItems.size} 条")
                for (item in newItems) {
                    Log.i(TAG, "  ${item.name} = ${item.price}")
                    allResults.add(keyword to item)
                }
            }
            autoCollectStepBack(keyword, firstBatch)
        }, 2500)
    }

    private fun autoCollectStepBack(keyword: String, items: List<SkuItem>) {
        if (!autoCollecting) return
        if (items.isEmpty()) {
            Log.w(TAG, "关键词 '$keyword' 未采集到数据")
        }
        updateResult("自动采集: $currentAppName\n完成: $keyword (${currentKeywordIndex + 1}/${keywords.size})\n已采集: ${allResults.size}条")
        // 返回上一页
        performGlobalAction(GLOBAL_ACTION_BACK)
        currentKeywordIndex++
        searchRetryCount = 0
        // 等待返回后搜索下一个
        handler.postDelayed({ autoCollectStepSearch() }, 2500)
    }

    private fun autoCollectFinish() {
        autoCollecting = false
        Log.i(TAG, "=== 自动采集完成: $currentAppName ===")
        Log.i(TAG, "总共采集到 ${allResults.size} 条数据")
        for ((kw, item) in allResults) {
            Log.i(TAG, "  [$kw] ${item.name} = ${item.price}")
        }
        // 保存到文件
        saveResultsToFile()
        // 更新悬浮窗
        val sb = StringBuilder()
        sb.append("采集完成: $currentAppName\n")
        sb.append("总共 ${allResults.size} 条\n---\n")
        for ((kw, item) in allResults) {
            sb.append("[$kw] ${item.name}\n  ${item.price}\n\n")
        }
        updateResult(sb.toString())
    }

    private fun saveResultsToFile() {
        try {
            val fileName = if (currentApp == "darunfa") "采集结果_大润发优鲜.txt" else "采集结果_菜亿萝.txt"
            val file = File(getExternalFilesDir(null), fileName)
            val writer = FileWriter(file)
            writer.write("$currentAppName 自动采集结果\n")
            writer.write("采集时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.write("关键词: $keywords\n")
            writer.write("总条数: ${allResults.size}\n")
            writer.write("========================================\n\n")
            val byKeyword = allResults.groupBy { it.first }
            for ((kw, items) in byKeyword) {
                writer.write("【$kw】${items.size}条\n")
                for ((_, item) in items) {
                    writer.write("  ${item.name} | ${item.price}\n")
                }
                writer.write("\n")
            }
            writer.close()
            Log.i(TAG, "结果已保存到: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存结果失败: ${e.message}")
        }
    }

    // ===== 辅助方法 =====

    private fun findClickableNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) return node
            var p = node.parent
            while (p != null) {
                if (p.isClickable) return p
                p = p.parent
            }
        }
        return null
    }

    private fun findEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.className == "android.widget.EditText" && root.isVisibleToUser) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull { it.isVisibleToUser }
    }

    private fun findSiblingByViewId(priceNode: AccessibilityNodeInfo, viewId: String): String {
        val parent = priceNode.parent ?: return ""
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            if (child === priceNode) continue
            if (child.viewIdResourceName == viewId) {
                return child.text?.toString()?.trim() ?: ""
            }
        }
        return ""
    }

    private fun scrollDown() {
        val path = Path()
        path.moveTo(540f, 1800f)
        path.lineTo(540f, 400f)
        val stroke = GestureDescription.StrokeDescription(path, 0, 500)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // ===== 价格提取 =====

    data class SkuItem(val name: String, val price: String)

    private fun collectPrices(root: AccessibilityNodeInfo): List<SkuItem> {
        val results = mutableListOf<SkuItem>()
        val usedNames = mutableSetOf<String>()

        // 方式1: 大润发专用 - tv_price(content-desc为价格数字) + tv_name(同级商品名)
        val priceNodes = root.findAccessibilityNodeInfosByViewId("com.rt.market.fresh:id/tv_price")
        if (priceNodes.isNotEmpty()) {
            for (priceNode in priceNodes) {
                val price = priceNode.contentDescription?.toString()?.trim() ?: continue
                if (price.isNotEmpty() && price.matches(Regex("\\d+(\\.\\d+)?"))) {
                    val name = findSiblingByViewId(priceNode, "com.rt.market.fresh:id/tv_name")
                    if (name.isNotEmpty() && !usedNames.contains(name)) {
                        usedNames.add(name)
                        results.add(SkuItem(name, "¥$price"))
                    }
                }
            }
            if (results.isNotEmpty()) return results
        }

        // 方式2: 通用 - 找包含 ¥ 的文本节点
        val textPriceNodes = mutableListOf<AccessibilityNodeInfo>()
        findAllPriceNodes(root, textPriceNodes)
        for (priceNode in textPriceNodes) {
            val rawPrice = priceNode.text?.toString()?.trim() ?: continue
            val cleanPrice = Regex("¥\\s*[\\d.]+(?:/\\S+)?").find(rawPrice)?.value ?: rawPrice
            val name = findProductName(priceNode)
            if (name.isNotEmpty() && !usedNames.contains(name)) {
                usedNames.add(name)
                results.add(SkuItem(name, cleanPrice))
            }
        }
        return results
    }

    private fun findAllPriceNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val text = node.text
        if (text != null) {
            val s = text.toString().trim()
            if (s.contains("¥") || s.contains("￥")) {
                val cleaned = s.replace("￥", "¥")
                if (cleaned.matches(Regex(".*¥\\s*\\d+(\\.\\d+)?.*"))) {
                    list.add(node)
                }
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findAllPriceNodes(it, list) }
        }
    }

    private fun findProductName(priceNode: AccessibilityNodeInfo): String {
        val parent = priceNode.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling === priceNode) continue
                val text = sibling.text?.toString()?.trim()
                if (isLikelyProductName(text)) return text!!
                for (j in 0 until sibling.childCount) {
                    val child = sibling.getChild(j) ?: continue
                    val childText = child.text?.toString()?.trim()
                    if (isLikelyProductName(childText)) return childText!!
                }
            }
        }
        var p = parent
        for (depth in 0 until 4) {
            p = p?.parent ?: return ""
            val text = p.text?.toString()?.trim()
            if (isLikelyProductName(text)) return text!!
            for (i in 0 until p.childCount) {
                val child = p.getChild(i) ?: continue
                val childText = child.text?.toString()?.trim()
                if (isLikelyProductName(childText)) return childText!!
            }
        }
        return ""
    }

    private fun isLikelyProductName(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        if (text.contains("¥") || text.contains("￥")) return false
        if (text.length < 2) return false
        val excludeKeywords = listOf("购物车", "购买", "立即", "加入", "确定", "取消", "搜索", "返回",
            "收藏", "分享", "评价", "评论", "规格", "数量", "配送", "运费", "已选", "选择",
            "客服", "详情", "参数", "推荐", "热门", "首页", "分类", "我的", "促销", "优惠", "券",
            "新人", "专享", "福利", "领券", "满减", "活动", "查看", "更多", "全部", "领", "抢")
        for (kw in excludeKeywords) {
            if (text == kw || text.startsWith(kw)) return false
        }
        if (text.matches(Regex("[\\d.\\s/%gkgml两斤克元]+"))) return false
        return true
    }

    private fun dumpTexts(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int) {
        val text = node.text
        if (text != null && text.toString().trim().isNotEmpty()) {
            list.add("[$depth] ${node.className}: ${text.toString().trim().take(50)}")
        }
        val desc = node.contentDescription
        if (desc != null && desc.toString().trim().isNotEmpty() && list.size < 80) {
            list.add("[$depth] desc: ${desc.toString().trim().take(50)}")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpTexts(it, list, depth + 1) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: ""
        val type = event?.eventType ?: 0
        if (pkg.isNotEmpty() && pkg != "com.waibao.floatingcollector") {
            Log.d(TAG, "事件: pkg=$pkg type=$type")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        autoCollecting = false
        try { unregisterReceiver(collectReceiver) } catch (_: Exception) {}
        removeFloatingWindow()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeFloatingWindow()
        return super.onUnbind(intent)
    }
}
