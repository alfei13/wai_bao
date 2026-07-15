package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
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
    private var titleText: TextView? = null
    private var contentLayout: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // ===== 自动采集引擎状态 =====
    private var keywords = listOf("鸡蛋", "猪肉", "白菜", "土豆")  // 默认关键词，接口拉取后替换
    private var skuConfigMap = emptyMap<String, SkuConfig>()  // skuName -> SkuConfig 映射，用于查找 skuId/unit
    private var autoCollecting = false
    private var currentKeywordIndex = 0
    private var currentApp = ""
    private var currentAppName = ""
    private var searchRetryCount = 0
    private var searchBtnBounds: Rect? = null
    private val allResults = mutableListOf<Pair<String, SkuItem>>()
    private val handler = Handler(Looper.getMainLooper())

    // unit 编码转中文显示
    private fun unitToDisplay(unit: String): String = when (unit) {
        "jin" -> "斤"
        "kg" -> "公斤"
        "box_small" -> "小盒"
        "box" -> "盒"
        "bag" -> "袋"
        "piece" -> "个"
        "pack" -> "包"
        else -> unit
    }

    // App -> sourceCode 映射
    private fun appToSourceCode(app: String): String = when (app) {
        "darunfa" -> "rf_mart_fresh"
        "shixing" -> "shihang_fresh"
        "caiyiluo" -> "caiyi_luo"
        else -> app
    }

    // App -> sourceName 映射
    private fun appToSourceName(app: String): String = when (app) {
        "darunfa" -> "大润发优鲜"
        "shixing" -> "食行生鲜"
        "caiyiluo" -> "蔡亿萝"
        else -> app
    }

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
        val density = resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(20, 12, 20, 16)
        }

        val titleBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 4, 4, 4)
        }
        titleText = TextView(ctx).apply {
            text = "价格采集器"
            textSize = 14f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = titleText!!
        val btnHide = Button(ctx).apply {
            text = "—"
            textSize = 12f
            setOnClickListener { toggleMinimize() }
        }
        val btnClose = Button(ctx).apply {
            text = "×"
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
            textSize = 12f
            setOnClickListener { performCollect() }
        }

        val scrollView = ScrollView(ctx)
        resultText = TextView(ctx).apply {
            text = "点「采集当前页面」手动采集\n或在主页选择App自动采集"
            textSize = 12f
            setPadding(4, 8, 4, 8)
            setTextIsSelectable(true)
        }
        scrollView.addView(resultText)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (density * 160).toInt()
        )

        contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        (contentLayout as LinearLayout).addView(btnCollect)
        (contentLayout as LinearLayout).addView(scrollView)

        root.addView(titleBar)
        root.addView(contentLayout)
        root.layoutParams = LinearLayout.LayoutParams(
            (density * 260).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return root
    }

    private var isMinimized = false

    private fun toggleMinimize() {
        val view = floatingView
        if (view == null) {
            isMinimized = false
            return
        }
        if (isMinimized) {
            contentLayout?.visibility = View.VISIBLE
            layoutParams?.let { lp ->
                lp.width = (resources.displayMetrics.density * 260).toInt()
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                windowManager?.updateViewLayout(view, lp)
            }
            isMinimized = false
        } else {
            contentLayout?.visibility = View.GONE
            layoutParams?.let { lp ->
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                windowManager?.updateViewLayout(view, lp)
            }
            isMinimized = true
        }
    }

    private fun removeFloatingWindow() {
        try { floatingView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        floatingView = null
        isMinimized = false
    }

    private fun updateResult(text: String) {
        handler.post {
            resultText?.text = text
            if (autoCollecting) {
                titleText?.text = "$currentAppName ${currentKeywordIndex}/${keywords.size}"
            }
        }
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
        currentAppName = when (app) {
            "darunfa" -> "大润发优鲜"
            "caiyiluo" -> "菜亿萝"
            "shixing" -> "食行生鲜"
            else -> app
        }
        currentKeywordIndex = 0
        searchRetryCount = 0
        allResults.clear()
        autoCollecting = true

        // 先在子线程拉取服务端 SKU 配置，替换硬编码关键词
        updateResult("正在获取采集配置...")
        Thread {
            val skuConfigs = ApiClient.fetchSkuConfig()
            handler.post {
                if (skuConfigs != null && skuConfigs.isNotEmpty()) {
                    keywords = skuConfigs.map { it.skuName }
                    skuConfigMap = skuConfigs.associateBy { it.skuName }
                    Log.i(TAG, "已从服务端获取 ${keywords.size} 个SKU配置: $keywords")
                    for ((name, cfg) in skuConfigMap) {
                        Log.i(TAG, "  $name -> skuId=${cfg.skuId}, unit=${cfg.unit}")
                    }
                } else {
                    Log.w(TAG, "接口拉取失败，使用默认关键词: $keywords")
                }
                // 配置加载完成，继续执行采集流程
                doStartAutoCollect(app)
            }
        }.start()
    }

    /** 实际执行采集（关键词已就绪） */
    private fun doStartAutoCollect(app: String) {
        val packageName = when (app) {
            "darunfa" -> "com.rt.market.fresh"
            "caiyiluo" -> "com.caiyiluo.nip"
            "shixing" -> "com.gem.tastyfood"
            else -> return
        }
        Log.i(TAG, "=== 开始自动采集: $currentAppName ($packageName) ===")
        Log.i(TAG, "关键词列表: $keywords")
        updateResult("开始自动采集: $currentAppName\n正在启动目标App...")
        // 悬浮窗可能已被关闭，先确保存在再最小化
        if (floatingView == null) showFloatingWindow()
        if (!isMinimized) toggleMinimize()
        handler.post { titleText?.text = "$currentAppName 0/${keywords.size}" }

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

    // 处理食行生鲜隐私协议弹窗和开屏广告，返回true表示已处理
    private fun handleShixingDialogsIfPresent(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: ""
        if (pkg != "com.gem.tastyfood") return false
        // 隐私协议弹窗: 点"我同意"
        val agreeBtn = findNodeById(root, "com.gem.tastyfood:id/btn_agree")
        if (agreeBtn != null) {
            Log.i(TAG, "食行生鲜: 点击隐私协议'我同意'")
            agreeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        // 开屏广告: 点"跳过"
        val skipBtn = findNodeById(root, "com.gem.tastyfood:id/tvTimer")
        if (skipBtn != null) {
            Log.i(TAG, "食行生鲜: 点击跳过开屏广告")
            skipBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

    private var splashRetryCount = 0

    // 处理通用开屏广告（"跳过"按钮），返回true表示已处理
    private fun handleSplashScreenIfPresent(): Boolean {
        val root = rootInActiveWindow ?: return false
        val skipNode = findNodeByTextTraversal(root, "跳过")
        if (skipNode != null) {
            splashRetryCount++
            if (splashRetryCount <= 3) {
                val rect = Rect()
                skipNode.getBoundsInScreen(rect)
                Log.i(TAG, "检测到开屏广告($splashRetryCount/3)，跳过位置: $rect")
                // 先尝试ACTION_CLICK（包括父节点链）
                var clicked = skipNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                    var p = skipNode.parent
                    while (p != null && !clicked) {
                        clicked = p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        p = p.parent
                    }
                }
                // dispatchGesture兜底（duration需较长，WebView需要更长触摸时间）
                val path = Path()
                path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                val stroke = GestureDescription.StrokeDescription(path, 0, 300)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
                Log.i(TAG, "开屏广告点击: ACTION_CLICK=$clicked, dispatchGesture已执行")
            } else {
                Log.i(TAG, "开屏广告未消失，等待自动关闭 (count=$splashRetryCount)")
            }
            return true
        }
        splashRetryCount = 0
        return false
    }

    private fun findNodeByTextTraversal(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val t = node.text?.toString() ?: ""
        if (t.contains(text)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextTraversal(child, text)
            if (result != null) return result
        }
        return null
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
        // 处理食行生鲜隐私协议弹窗和开屏广告
        if (handleShixingDialogsIfPresent()) {
            handler.postDelayed({ autoCollectStepSearch() }, 3000)
            return
        }
        // 处理通用开屏广告（"跳过"按钮）
        if (handleSplashScreenIfPresent()) {
            handler.postDelayed({ autoCollectStepSearch() }, 2000)
            return
        }

        val pkg = root.packageName?.toString() ?: ""
        val targetPkg = when (currentApp) {
            "darunfa" -> "com.rt.market.fresh"
            "caiyiluo" -> "com.caiyiluo.nip"
            "shixing" -> "com.gem.tastyfood"
            else -> ""
        }
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

        // ===== 各App独立搜索流程（避免逻辑互相干扰） =====
        when (currentApp) {
            "darunfa" -> {
                // 大润发优鲜: 首页点"搜索"→KeyWordSearchListActivity→点v_keyword_blank→SearchActivity(EditText)
                val editText = findEditText(root)
                if (editText != null) {
                    searchRetryCount = 0
                    autoCollectStepInputDirectly(keyword, editText)
                } else {
                    val entry = findClickableNodeByText(root, "搜索")
                        ?: findNodeById(root, "com.rt.market.fresh:id/v_keyword_blank")
                        ?: findNodeById(root, "com.rt.market.fresh:id/hsv_keyword")
                    if (entry != null) {
                        Log.i(TAG, "大润发: 点击搜索入口/搜索框区域进入搜索页")
                        entry.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        searchRetryCount = 0
                        handler.postDelayed({ autoCollectStepInput(keyword) }, 2000)
                    } else {
                        searchRetryCount++
                        if (searchRetryCount > 8) {
                            skipKeyword(keyword, "大润发: 未找到搜索入口")
                            return
                        }
                        Log.w(TAG, "大润发: 未找到搜索入口，重试 $searchRetryCount/8")
                        handler.postDelayed({ autoCollectStepSearch() }, 2000)
                    }
                }
            }
            "caiyiluo" -> {
                // 菜亿萝: WebView应用，dispatchGesture和ACTION_CLICK在WebView上不生效
                // 解决方案: 用ACTION_SET_TEXT输入关键词，通过TAP_REQUEST日志让主机脚本执行adb tap
                val editText = findEditText(root)
                if (editText != null) {
                    // 找到EditText，输入关键词并通过TAP_REQUEST触发搜索
                    val btnNode = findNodeByTextTraversal(root, "搜索")
                    if (btnNode != null) {
                        val btnRect = Rect()
                        btnNode.getBoundsInScreen(btnRect)
                        Log.i(TAG, "菜亿萝: 找到EditText和搜索按钮: $btnRect")
                        searchRetryCount = 0
                        autoCollectStepInputCaiyiluoWithTapRequest(keyword, editText, btnRect)
                    } else {
                        // 有EditText但没找到"搜索"文本，可能已在搜索结果页，直接输入
                        Log.i(TAG, "菜亿萝: 有EditText但无搜索按钮，直接输入并IME ENTER")
                        searchRetryCount = 0
                        autoCollectStepInputDirectlyCaiyiluo(keyword, editText)
                    }
                } else {
                    searchRetryCount++
                    if (searchRetryCount == 3) {
                        Log.w(TAG, "菜亿萝: 未找到EditText，dump节点:")
                        val texts = mutableListOf<String>()
                        dumpNodesForDebug(root, texts, 0)
                        for (t in texts.take(30)) Log.w(TAG, "  $t")
                    }
                    if (searchRetryCount > 8) {
                        skipKeyword(keyword, "菜亿萝: 未找到EditText")
                        return
                    }
                    Log.w(TAG, "菜亿萝: 未找到EditText，重试 $searchRetryCount/8")
                    handler.postDelayed({ autoCollectStepSearch() }, 2000)
                }
            }
            "shixing" -> {
                // 食行生鲜: 首页点searchTopView→搜索输入页(AutoCompleteTextView)
                val editText = findEditText(root)
                if (editText != null) {
                    searchRetryCount = 0
                    autoCollectStepInputDirectly(keyword, editText)
                } else {
                    val searchTopView = findNodeById(root, "com.gem.tastyfood:id/searchTopView")
                    if (searchTopView != null) {
                        Log.i(TAG, "食行生鲜: 点击搜索栏区域进入搜索输入页")
                        searchTopView.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        searchRetryCount = 0
                        handler.postDelayed({ autoCollectStepInput(keyword) }, 2000)
                    } else {
                        searchRetryCount++
                        if (searchRetryCount > 8) {
                            skipKeyword(keyword, "食行生鲜: 未找到搜索栏")
                            return
                        }
                        Log.w(TAG, "食行生鲜: 未找到搜索栏，重试 $searchRetryCount/8")
                        handler.postDelayed({ autoCollectStepSearch() }, 2000)
                    }
                }
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
                // debug: dump所有文本节点和类名
                Log.w(TAG, "搜索页未找到输入框，dump节点:")
                val texts = mutableListOf<String>()
                dumpNodesForDebug(root, texts, 0)
                for (t in texts.take(30)) Log.w(TAG, "  $t")
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
        // 输入前先记录"搜索"按钮位置（输入后可能消失，用坐标兜底）
        searchBtnBounds = null
        val preRoot = rootInActiveWindow
        if (preRoot != null) {
            // 先找可点击的"搜索"按钮（大润发/食行生鲜）
            val btn = findClickableNodeByText(preRoot, "搜索")
            if (btn != null) {
                val rect = Rect()
                btn.getBoundsInScreen(rect)
                searchBtnBounds = rect
                Log.i(TAG, "记录搜索按钮位置(可点击): $rect")
            } else {
                // 菜亿萝: "搜索"文本不可点击，用文本遍历查找其坐标
                val btnNode = findNodeByTextTraversal(preRoot, "搜索")
                if (btnNode != null) {
                    val rect = Rect()
                    btnNode.getBoundsInScreen(rect)
                    searchBtnBounds = rect
                    Log.i(TAG, "记录搜索按钮位置(文本遍历): $rect")
                }
            }
        }
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
        var triggered = false
        // 方式1: 找可点击的"搜索"按钮点击（大润发/食行生鲜）
        val searchBtn = findClickableNodeByText(root, "搜索")
        if (searchBtn != null) {
            Log.i(TAG, "点击搜索按钮触发搜索(可点击)")
            searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            triggered = true
        } else {
            // 菜亿萝: "搜索"文本不可点击，尝试多种方式
            val btnNode = findNodeByTextTraversal(root, "搜索")
            if (btnNode != null) {
                // 方式2: 直接对"搜索"节点执行ACTION_CLICK（即使isClickable=false，WebView有时也能响应）
                Log.i(TAG, "尝试对'搜索'文本节点执行ACTION_CLICK")
                val clicked = btnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "ACTION_CLICK结果: $clicked")
                if (clicked) triggered = true
            }
            if (!triggered) {
                // 方式3: 用dispatchGesture坐标点击（多次尝试，不同时长）
                val targetRect: Rect? = if (btnNode != null) {
                    Rect().also { btnNode.getBoundsInScreen(it) }
                } else searchBtnBounds
                if (targetRect != null) {
                    val cx = targetRect.centerX().toFloat()
                    val cy = targetRect.centerY().toFloat()
                    Log.i(TAG, "用坐标点击搜索: ($cx, $cy) rect=$targetRect")
                    val path = Path()
                    path.moveTo(cx, cy)
                    // 用较长的时间确保WebView接收事件
                    val stroke = GestureDescription.StrokeDescription(path, 0, 150)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    val result = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gesture: GestureDescription?) {
                            Log.i(TAG, "dispatchGesture onCompleted")
                        }
                        override fun onCancelled(gesture: GestureDescription?) {
                            Log.w(TAG, "dispatchGesture onCancelled")
                        }
                    }, null)
                    Log.i(TAG, "dispatchGesture返回: $result")
                    triggered = true
                }
            }
            if (!triggered && searchBtnBounds != null) {
                // 方式4: 用记录的坐标兜底
                val rect = searchBtnBounds!!
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()
                Log.i(TAG, "用记录坐标点击搜索: ($cx, $cy)")
                val path = Path()
                path.moveTo(cx, cy)
                val stroke = GestureDescription.StrokeDescription(path, 0, 150)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
                triggered = true
            }
        }
        if (!triggered) {
            // 方式5: 最后兜底 - 对输入框执行IME ENTER
            Log.i(TAG, "所有搜索触发方式失败，尝试IME ENTER")
            val editText = findEditText(root)
            if (editText != null) {
                editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)
            }
        }
        // 等待搜索结果加载
        handler.postDelayed({ autoCollectStepCollect(keyword) }, 4000)
    }

    // 菜亿萝专用: 输入关键词后用多种方式触发搜索（不依赖tap_helper）
    private fun autoCollectStepInputCaiyiluoWithTapRequest(keyword: String, editText: AccessibilityNodeInfo, searchBtnRect: Rect) {
        if (!autoCollecting) return
        Log.i(TAG, "菜亿萝: 输入关键词: $keyword")
        // 步骤1: 点击EditText激活输入框，弹出输入法
        editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        // 等待输入法弹出
        handler.postDelayed({
            if (!autoCollecting) return@postDelayed
            // 步骤2: 重新获取所有EditText，选择当前有焦点的那个
            val root = rootInActiveWindow
            if (root == null) {
                Log.w(TAG, "菜亿萝: root为空，重试")
                handler.postDelayed({ autoCollectStepSearch() }, 2000)
                return@postDelayed
            }
            val allEditTexts = mutableListOf<AccessibilityNodeInfo>()
            findAllEditTexts(root, allEditTexts)
            Log.i(TAG, "菜亿萝: 找到${allEditTexts.size}个EditText")
            for ((i, et) in allEditTexts.withIndex()) {
                val r = Rect()
                et.getBoundsInScreen(r)
                Log.i(TAG, "菜亿萝: EditText[$i] text='${et.text}' focused=${et.isFocused} bounds=$r")
            }
            // 优先选择有焦点的EditText，否则选择最后一个（通常是当前页面的）
            val freshEditText = allEditTexts.firstOrNull { it.isFocused }
                ?: allEditTexts.lastOrNull()
            if (freshEditText == null) {
                Log.w(TAG, "菜亿萝: 设置文本前EditText丢失，重试")
                handler.postDelayed({ autoCollectStepSearch() }, 2000)
                return@postDelayed
            }
            // 步骤3: 用剪贴板粘贴方式输入文本（SET_TEXT在WebView上不生效）
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("keyword", keyword))
            // 先清除旧文本
            val clearArgs = Bundle()
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            freshEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
            handler.postDelayed({
                if (!autoCollecting) return@postDelayed
                // 粘贴新文本
                val pasteResult = freshEditText.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                val textAfter = freshEditText.text?.toString() ?: ""
                Log.i(TAG, "菜亿萝: PASTE=$pasteResult 文本='$textAfter' 期望='$keyword'")
                // 如果粘贴不生效，尝试SET_TEXT
                if (textAfter != keyword) {
                    Log.i(TAG, "菜亿萝: PASTE未生效，尝试SET_TEXT")
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
                    freshEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    val textAfter2 = freshEditText.text?.toString() ?: ""
                    Log.i(TAG, "菜亿萝: SET_TEXT后 文本='$textAfter2'")
                }
                // 步骤4: 等待文本设置完成后，点击搜索
                handler.postDelayed({
                    if (!autoCollecting) return@postDelayed
                    triggerCaiyiluoSearch(keyword, searchBtnRect)
                }, 1500)
            }, 300)
        }, 2000)
    }

    // 菜亿萝搜索触发：点击输入法回车键触发搜索
    private fun triggerCaiyiluoSearch(keyword: String, searchBtnRect: Rect) {
        Log.i(TAG, "菜亿萝: 触发搜索 keyword=$keyword")

        // 确保EditText有焦点（输入法回车键需要焦点才能触发搜索）
        val root = rootInActiveWindow
        if (root != null) {
            val editText = findEditText(root)
            editText?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        // 点击输入法回车键
        val windows = windows
        var imeFound = false
        for (window in windows) {
            if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                imeFound = true
                val rect = Rect()
                window.getBoundsInScreen(rect)
                Log.i(TAG, "菜亿萝: 输入法窗口 bounds=$rect")

                // 尝试在输入法窗口中找到回车键节点
                val imeRoot = window.root
                var enterRect: Rect? = null
                if (imeRoot != null) {
                    enterRect = findImeEnterKey(imeRoot)
                }

                if (enterRect != null) {
                    Log.i(TAG, "菜亿萝: 找到回车键节点 bounds=$enterRect 点击它")
                    dispatchTap(enterRect.centerX().toFloat(), enterRect.centerY().toFloat(), 50)
                } else {
                    // 估算回车键位置（右下角，避开左下角剪贴板按钮）
                    Log.i(TAG, "菜亿萝: 未找到回车键节点，多次点击估算位置")
                    val enterX = rect.right - 60
                    val enterY1 = rect.bottom - 120
                    val enterY2 = rect.bottom - 80
                    val enterY3 = rect.bottom - 160
                    Log.i(TAG, "菜亿萝: 点击回车键估算位置: ($enterX, $enterY1) ($enterX, $enterY2) ($enterX, $enterY3)")
                    dispatchTap(enterX.toFloat(), enterY1.toFloat(), 50)
                    handler.postDelayed({ dispatchTap(enterX.toFloat(), enterY2.toFloat(), 50) }, 200)
                    handler.postDelayed({ dispatchTap(enterX.toFloat(), enterY3.toFloat(), 50) }, 400)
                }
                break
            }
        }
        if (!imeFound) {
            Log.w(TAG, "菜亿萝: 输入法未弹出！尝试重新点击EditText")
            if (root != null) {
                val editText = findEditText(root)
                editText?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed({
                    if (!autoCollecting) return@postDelayed
                    triggerCaiyiluoSearch(keyword, searchBtnRect)
                }, 2000)
                return
            }
        }

        // 等待搜索结果加载，然后验证
        handler.postDelayed({ autoCollectStepCollectWithVerify(keyword, searchBtnRect) }, 6000)
    }

    // 在输入法窗口节点树中搜索回车键/搜索键
    private fun findImeEnterKey(node: AccessibilityNodeInfo): Rect? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val keywords = listOf("搜索", "回车", "确认", "enter", "search", "send", "go", "完成", "前往")
        for (kw in keywords) {
            if (text.contains(kw) || desc.contains(kw)) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                Log.i(TAG, "菜亿萝: 输入法按键匹配 '$kw' text='$text' desc='$desc' bounds=$rect")
                return rect
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findImeEnterKey(child)
            if (result != null) return result
        }
        return null
    }

    // 采集并验证搜索结果（菜亿萝专用）
    private fun autoCollectStepCollectWithVerify(keyword: String, searchBtnRect: Rect) {
        if (!autoCollecting) return
        val root = rootInActiveWindow
        if (root == null) {
            handler.postDelayed({ autoCollectStepBack(keyword, emptyList()) }, 1000)
            return
        }
        val items = collectPrices(root)
        Log.i(TAG, "菜亿萝: 关键词 '$keyword' 采集到 ${items.size} 条，验证搜索结果...")

        // 验证：检查采集到的商品名是否包含关键词（至少1条匹配）
        val hasMatch = items.any { it.name.contains(keyword) }
        if (!hasMatch && items.isNotEmpty()) {
            // 搜索可能没触发，重试点击搜索按钮
            searchRetryCount++
            if (searchRetryCount <= 3) {
                Log.w(TAG, "菜亿萝: 搜索结果不匹配(期望含'$keyword')，重试搜索 $searchRetryCount/3")
                handler.postDelayed({
                    if (!autoCollecting) return@postDelayed
                    triggerCaiyiluoSearch(keyword, searchBtnRect)
                }, 500)
                return
            }
            Log.w(TAG, "菜亿萝: 搜索结果不匹配，重试已达上限，跳过")
        }
        searchRetryCount = 0
        // 搜索结果正确，只取前3条（附加 skuId 和 unit）
        val limitedItems = items.take(3)
        for (item in limitedItems) {
            val enriched = enrichWithSkuConfig(keyword, item)
            Log.i(TAG, "  ${item.name} = ${item.price} (skuId=${enriched.skuId}, unit=${enriched.unit})")
            allResults.add(keyword to enriched)
        }
        autoCollectStepBack(keyword, limitedItems)
    }

    // dispatchGesture点击（带回调日志）
    private fun dispatchTap(x: Float, y: Float, durationMs: Long) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val result = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) {
                Log.i(TAG, "dispatchGesture onCompleted ($x, $y)")
            }
            override fun onCancelled(gesture: GestureDescription?) {
                Log.w(TAG, "dispatchGesture onCancelled ($x, $y)")
            }
        }, null)
        Log.i(TAG, "dispatchGesture dispatchResult=$result")
    }

    // 通过shell执行input tap（不依赖tap_helper和root）
    private fun shellTap(x: Int, y: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "input tap $x $y"))
            val exitCode = process.waitFor()
            Log.i(TAG, "shellTap($x, $y) exitCode=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "shellTap异常: ${e.message}")
            false
        }
    }

    // 菜亿萝专用: 输入关键词后用ACTION_IME_ENTER触发搜索（避免点击WebView中的"搜索"文本）
    private fun autoCollectStepInputDirectlyCaiyiluo(keyword: String, editText: AccessibilityNodeInfo) {
        if (!autoCollecting) return
        Log.i(TAG, "菜亿萝: 输入关键词: $keyword")
        // 先聚焦EditText
        editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        // 设置文本
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
        val setResult = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.i(TAG, "菜亿萝: ACTION_SET_TEXT结果: $setResult")
        // 等待文本设置完成后触发IME ENTER
        handler.postDelayed({
            if (!autoCollecting) return@postDelayed
            // 尝试ACTION_IME_ENTER (API 30+, 值=0x400000)
            val ACTION_IME_ENTER = 0x400000
            val imeEnterResult = editText.performAction(ACTION_IME_ENTER)
            Log.i(TAG, "菜亿萝: ACTION_IME_ENTER结果: $imeEnterResult")
            // 也尝试点击"搜索"作为备份（虽然可能不生效）
            val root = rootInActiveWindow
            if (root != null) {
                val btnNode = findNodeByTextTraversal(root, "搜索")
                if (btnNode != null) {
                    val rect = Rect()
                    btnNode.getBoundsInScreen(rect)
                    Log.i(TAG, "菜亿萝: 也尝试点击搜索文本: $rect")
                    btnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    // dispatchGesture兜底
                    val path = Path()
                    path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                    val stroke = GestureDescription.StrokeDescription(path, 0, 150)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    dispatchGesture(gesture, null, null)
                }
            }
            // 等待搜索结果加载
            handler.postDelayed({ autoCollectStepCollect(keyword) }, 4000)
        }, 1500)
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
        val items = collectPrices(root).take(3)
        Log.i(TAG, "关键词 '$keyword' 首屏采集到 ${items.size} 条（最多取3条）")
        for (item in items) {
            val enriched = enrichWithSkuConfig(keyword, item)
            Log.i(TAG, "  ${item.name} = ${item.price} (skuId=${enriched.skuId}, unit=${enriched.unit})")
            allResults.add(keyword to enriched)
        }

        // 统一逻辑：只取前3条，不滚动，直接进行下一个关键词
        autoCollectStepBack(keyword, items)
    }

    private fun autoCollectStepBack(keyword: String, items: List<SkuItem>) {
        if (!autoCollecting) return
        if (items.isEmpty()) {
            Log.w(TAG, "关键词 '$keyword' 未采集到数据")
        }
        updateResult("自动采集: $currentAppName\n完成: $keyword (${currentKeywordIndex + 1}/${keywords.size})\n已采集: ${allResults.size}条")
        // 菜亿萝搜索结果就在当前分类页，不需要回退；其他app回退到搜索页
        if (currentApp != "caiyiluo") {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        currentKeywordIndex++
        searchRetryCount = 0
        // 等待后搜索下一个
        handler.postDelayed({ autoCollectStepSearch() }, 1500)
    }

    private fun autoCollectFinish() {
        autoCollecting = false
        Log.i(TAG, "=== 自动采集完成: $currentAppName ===")
        Log.i(TAG, "总共采集到 ${allResults.size} 条数据")
        for ((kw, item) in allResults) {
            Log.i(TAG, "  [$kw] skuId=${item.skuId} ${item.name} = ${item.price}/${item.unit}")
        }
        // 保存到文件
        saveResultsToFile()
        // 自动恢复悬浮窗显示结果
        if (floatingView == null) showFloatingWindow()
        if (isMinimized) toggleMinimize()
        handler.post { titleText?.text = "价格采集器" }
        // 更新悬浮窗
        val sb = StringBuilder()
        sb.append("采集完成: $currentAppName\n")
        sb.append("总共 ${allResults.size} 条\n---\n")
        for ((kw, item) in allResults) {
            sb.append("[$kw] ${item.name}\n  ${item.price}/${unitToDisplay(item.unit)} (skuId=${item.skuId})\n\n")
        }
        sb.append("\n正在上报数据...")
        updateResult(sb.toString())

        // 子线程上报数据
        val collectedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sourceCode = appToSourceCode(currentApp)
        val sourceName = appToSourceName(currentApp)
        val batchId = "BATCH_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"

        Thread {
            val uploadItems = allResults.map { (kw, item) ->
                val priceNum = item.price.replace(Regex("[^\\d.]"), "").toDoubleOrNull() ?: 0.0
                UploadItem(
                    skuId = item.skuId,
                    sourceSkuName = item.name,
                    sourceId = sourceCode,
                    priceValue = priceNum,
                    priceUnit = "元/${unitToDisplay(item.unit)}",
                    priceDate = collectedTime,
                    remark = ""
                )
            }
            val result = ApiClient.uploadCollectedData(batchId, sourceCode, sourceName, collectedTime, uploadItems)
            handler.post {
                val uploadMsg = if (result.success) {
                    "上报成功: accepted=${result.accepted}, rejected=${result.rejected}"
                } else {
                    "上报失败: ${result.message}"
                }
                Log.i(TAG, uploadMsg)
                // 在悬浮窗结果末尾追加上报状态
                val currentText = resultText?.text?.toString() ?: ""
                val updatedText = currentText.replace("正在上报数据...", uploadMsg)
                updateResult(updatedText)
            }
        }.start()
    }

    private fun saveResultsToFile() {
        try {
            val fileName = when (currentApp) {
                "darunfa" -> "采集结果_大润发优鲜.txt"
                "caiyiluo" -> "采集结果_菜亿萝.txt"
                "shixing" -> "采集结果_食行生鲜.txt"
                else -> "采集结果_未知.txt"
            }
            val file = File(getExternalFilesDir(null), fileName)
            val writer = FileWriter(file)
            writer.write("$currentAppName 自动采集结果\n")
            writer.write("采集时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.write("关键词: $keywords\n")
            writer.write("总条数: ${allResults.size}\n")
            writer.write("========================================\n\n")
            val byKeyword = allResults.groupBy { it.first }
            for ((kw, items) in byKeyword) {
                val skuId = items.firstOrNull()?.second?.skuId ?: ""
                val unit = items.firstOrNull()?.second?.unit ?: ""
                writer.write("【$kw】skuId=$skuId, unit=$unit, ${items.size}条\n")
                for ((_, item) in items) {
                    writer.write("  ${item.name} | ${item.price}/${unitToDisplay(item.unit)} | skuId=${item.skuId}\n")
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
        // WebView fallback: findAccessibilityNodeInfosByText在WebView中不可靠，遍历查找
        return findClickableNodeByTextTraversal(root, text)
    }

    private fun findClickableNodeByTextTraversal(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val t = node.text?.toString() ?: ""
        if (t.contains(text) && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findClickableNodeByTextTraversal(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun findEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isVisibleToUser) {
            val cls = root.className?.toString() ?: ""
            if (cls == "android.widget.EditText" || cls == "android.widget.AutoCompleteTextView") return root
            // WebView内的input元素: 检查是否支持ACTION_SET_TEXT
            val actions = root.actions
            if ((actions and AccessibilityNodeInfo.ACTION_SET_TEXT) != 0 && root.isEditable) return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    private fun findAllEditTexts(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isVisibleToUser) {
            val cls = node.className?.toString() ?: ""
            if (cls == "android.widget.EditText" || cls == "android.widget.AutoCompleteTextView") {
                list.add(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllEditTexts(child, list)
        }
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull { it.isVisibleToUser }
    }

    private fun dumpNodesForDebug(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int) {
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
        val editable = node.isEditable
        if (text.isNotEmpty() || desc.isNotEmpty() || editable) {
            list.add("[$depth] cls=$cls editable=$editable text='$text' desc='$desc'")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodesForDebug(child, list, depth + 1)
        }
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

    // ===== 价格提取 =====

    data class SkuItem(val name: String, val price: String, val skuId: String = "", val unit: String = "")

    /** 为采集到的商品附加 skuId 和 unit 信息 */
    private fun enrichWithSkuConfig(keyword: String, item: SkuItem): SkuItem {
        val config = skuConfigMap[keyword] ?: return item
        return item.copy(skuId = config.skuId, unit = config.unit)
    }

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

        // 方式2: 菜亿萝专用 - ￥节点 + 同级数字 + 兄弟级商品名（扁平结构）
        val yenNodes = mutableListOf<AccessibilityNodeInfo>()
        findAllYenNodes(root, yenNodes)
        Log.i(TAG, "采集: 找到 ${yenNodes.size} 个￥节点")
        if (yenNodes.isNotEmpty()) {
            for (yenNode in yenNodes) {
                val priceNum = findSiblingNumber(yenNode) ?: continue
                val name = findProductNameFromSiblings(yenNode)
                Log.i(TAG, "  ￥节点: price=$priceNum name='$name'")
                if (name.isNotEmpty() && !usedNames.contains(name)) {
                    usedNames.add(name)
                    results.add(SkuItem(name, "¥$priceNum"))
                }
            }
            if (results.isNotEmpty()) return results
        }

        // 方式3: 通用 - 找包含 ¥ 的文本节点
        val textPriceNodes = mutableListOf<AccessibilityNodeInfo>()
        findAllPriceNodes(root, textPriceNodes)
        Log.i(TAG, "采集: 找到 ${textPriceNodes.size} 个¥文本节点")
        for (priceNode in textPriceNodes) {
            val rawPrice = priceNode.text?.toString()?.trim() ?: continue
            val cleanPrice = Regex("¥\\s*[\\d.]+(?:/\\S+)?").find(rawPrice)?.value ?: rawPrice
            val name = findProductName(priceNode)
            Log.i(TAG, "  ¥文本: raw='$rawPrice' clean='$cleanPrice' name='$name'")
            if (name.isNotEmpty() && !usedNames.contains(name)) {
                usedNames.add(name)
                results.add(SkuItem(name, cleanPrice))
            }
        }
        return results
    }

    private fun findAllYenNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val text = node.text
        if (text != null) {
            val s = text.toString().trim()
            if (s == "￥" || s == "¥") list.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findAllYenNodes(it, list) }
        }
    }

    // 注意: AccessibilityNodeInfo.getChild(i) 每次返回新实例，
    // === 引用比较永远失败，必须用 Rect bounds 比较来定位节点索引。
    private fun findSiblingNumber(yenNode: AccessibilityNodeInfo): String? {
        val parent = yenNode.parent ?: return null
        val yenRect = Rect()
        yenNode.getBoundsInScreen(yenRect)
        var yenIndex = -1
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            val childRect = Rect()
            child.getBoundsInScreen(childRect)
            if (childRect == yenRect) { yenIndex = i; break }
        }
        if (yenIndex < 0) return null
        // 只看￥节点之后的兄弟节点，找到第一个数字（价格）
        for (i in yenIndex + 1 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            val text = child.text?.toString()?.trim() ?: continue
            if (text.matches(Regex("\\d+(\\.\\d+)?"))) return text
            if (text == "￥" || text == "¥") break
        }
        return null
    }

    // 菜亿萝搜索结果页是扁平结构：所有节点都是WebView的直接子节点
    // 商品名在￥节点之前，价格在￥节点之后
    private fun findProductNameFromSiblings(yenNode: AccessibilityNodeInfo): String {
        val parent = yenNode.parent ?: return ""
        val yenRect = Rect()
        yenNode.getBoundsInScreen(yenRect)
        var yenIndex = -1
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            val childRect = Rect()
            child.getBoundsInScreen(childRect)
            if (childRect == yenRect) { yenIndex = i; break }
        }
        if (yenIndex < 0) return ""
        // 从￥节点向前查找，找到第一个合法的商品名
        for (i in yenIndex - 1 downTo maxOf(0, yenIndex - 10)) {
            val child = parent.getChild(i) ?: continue
            val text = child.text?.toString()?.trim() ?: continue
            if (isCaiyiluoProductName(text)) return text
        }
        return ""
    }

    private fun findProductNameFromAncestors(node: AccessibilityNodeInfo, maxLevels: Int): String {
        var current = node
        for (level in 0 until maxLevels) {
            current = current.parent ?: return ""
            val name = findProductNameInSubtree(current, mutableSetOf())
            if (name.isNotEmpty()) return name
        }
        return ""
    }

    private fun findProductNameInSubtree(node: AccessibilityNodeInfo, visited: MutableSet<String>): String {
        val text = node.text?.toString()?.trim()
        if (isCaiyiluoProductName(text)) return text!!
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = child.text?.toString()?.trim()
            if (isCaiyiluoProductName(childText)) return childText!!
            val result = findProductNameInSubtree(child, visited)
            if (result.isNotEmpty()) return result
        }
        return ""
    }

    private fun isCaiyiluoProductName(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        if (text == "￥" || text == "¥") return false
        if (text.matches(Regex("\\d+(\\.\\d+)?"))) return false
        if (text.startsWith("pages/")) return false
        if (text.startsWith("库存")) return false
        if (text.startsWith("正价")) return false
        if (text == "add" || text == "cart") return false
        if (text.contains("?order=") || text.contains("_small")) return false
        if (text.length < 2) return false
        val exclude = listOf("搜索", "购物车", "首页", "我的", "分类", "昆山仓", "提醒",
            "新鲜蔬果", "肉类", "禽类", "烧腊类", "水产冻货", "豆品蛋类", "米油杂粮",
            "调料干货", "厨房用品", "饮品礼品", "谁在用菜亿箩",
            "历史价格", "补货中", "选规格", "特价专区", "新品专区", "近期上架新品",
            "廉洁协议", "价格透明", "走进菜亿箩", "招聘求职", "工厂招聘求职",
            "切换长辈模式", "个人中心", "账号管理", "财务管理", "常用工具", "客服中心",
            "公司信息", "仓库位置", "所有仓库电话", "下单时间", "菜亿箩平台", "新客户免",
            "大宗干货", "月采购金额", "鱼类免费", "专业服务", "当月购货", "20：30后",
            "菜亿箩最近发展动向", "常见问题", "待付款", "待发货", "待收货", "我的订单",
            "收货地址", "修改密码", "我的简历", "附近企业", "送货单", "对账单",
            "下单明细报表", "开票须知", "开票申请", "开票记录", "送货信息", "文件中心",
            "检测报告", "菜谱下单", "语音下单", "图片下单", "我的反馈", "新品需求",
            "豆腐板", "周转筐", "商户信息", "比价")
        if (text in exclude) return false
        return true
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
        if (text.startsWith("/")) return false
        if (text.startsWith("pages/")) return false
        if (text.length < 2) return false
        val excludeKeywords = listOf("购物车", "购买", "立即", "加入", "确定", "取消", "搜索", "返回",
            "收藏", "分享", "评价", "评论", "规格", "数量", "配送", "运费", "已选", "选择",
            "客服", "详情", "参数", "推荐", "热门", "首页", "分类", "我的", "促销", "优惠", "券",
            "新人", "专享", "福利", "领券", "满减", "活动", "查看", "更多", "全部", "领", "抢",
            "历史价格", "补货中", "选规格", "特价专区", "新品专区", "近期上架新品",
            "谁在用菜亿箩", "廉洁协议", "价格透明", "走进菜亿箩", "招聘求职", "工厂招聘求职",
            "切换长辈模式", "个人中心", "账号管理", "财务管理", "常用工具", "客服中心",
            "公司信息", "仓库位置", "所有仓库电话", "下单时间", "菜亿箩平台", "新客户免",
            "大宗干货", "月采购金额", "鱼类免费", "专业服务", "当月购货", "20：30后",
            "菜亿箩最近发展动向", "常见问题", "待付款", "待发货", "待收货", "我的订单",
            "收货地址", "修改密码", "我的简历", "附近企业", "送货单", "对账单",
            "下单明细报表", "开票须知", "开票申请", "开票记录", "送货信息", "文件中心",
            "检测报告", "菜谱下单", "语音下单", "图片下单", "我的反馈", "新品需求",
            "豆腐板", "周转筐", "商户信息", "比价")
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
