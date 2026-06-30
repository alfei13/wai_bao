package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PriceCollector"

class CollectorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var resultText: TextView? = null
    private var contentLayout: View? = null
    private var minimizedView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isMinimized = false

    private val collectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "收到广播触发采集")
            performCollect()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接 onServiceConnected")
        showFloatingWindow()
        val filter = IntentFilter("com.waibao.floatingcollector.COLLECT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(collectReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(collectReceiver, filter)
        }
        Log.i(TAG, "广播接收器已注册")
    }

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

        // 标题栏（可拖动）
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

        // 拖动逻辑
        titleBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                }
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

        // 采集按钮
        val btnCollect = Button(ctx).apply {
            text = "采集当前页面"
            setOnClickListener { performCollect() }
        }

        // 结果显示区
        val scrollView = ScrollView(ctx)
        resultText = TextView(ctx).apply {
            text = "点击「采集」按钮提取价格"
            textSize = 13f
            setPadding(8, 16, 8, 16)
            setTextIsSelectable(true)
        }
        scrollView.addView(resultText)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        scrollView.layoutParams = scrollParams

        contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        (contentLayout as LinearLayout).addView(btnCollect)
        (contentLayout as LinearLayout).addView(scrollView)

        root.addView(titleBar)
        root.addView(contentLayout)

        // 设置整体大小
        val overallParams = LinearLayout.LayoutParams(
            (resources.displayMetrics.density * 320).toInt(),
            (resources.displayMetrics.density * 360).toInt()
        )
        root.layoutParams = overallParams

        return root
    }

    private fun minimize() {
        isMinimized = true
        contentLayout?.visibility = View.GONE
        layoutParams?.let { lp ->
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowManager?.updateViewLayout(floatingView, lp)
        }
    }

    private fun removeFloatingWindow() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
        }
        floatingView = null
    }

    private fun performCollect() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "rootInActiveWindow 为空")
            resultText?.text = "未获取到页面内容，请确保目标App在前台后重试"
            return
        }

        Log.i(TAG, "=== 开始采集 ===")
        Log.i(TAG, "根节点包名: ${root.packageName}, childCount: ${root.childCount}")

        val items = collectPrices(root)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (items.isEmpty()) {
            // 调试：dump 所有文本节点
            val allText = mutableListOf<String>()
            dumpTexts(root, allText, 0)
            Log.i(TAG, "未找到价格，屏幕文本节点共 ${allText.size} 个:")
            for (t in allText) {
                Log.i(TAG, "  $t")
            }
            val debug = if (allText.isEmpty()) "（无任何文本节点）" else allText.take(30).joinToString("\n")
            resultText?.text = "未采集到价格数据（$time）\n\n---屏幕文本节选---\n$debug"
            return
        }

        Log.i(TAG, "采集到 ${items.size} 条价格:")
        val sb = StringBuilder()
        sb.append("采集时间：$time\n")
        sb.append("共 ${items.size} 条\n")
        sb.append("-------------------\n")
        for (item in items) {
            Log.i(TAG, "  商品: ${item.name}  价格: ${item.price}")
            sb.append("${item.name}\n${item.price}\n\n")
        }
        resultText?.text = sb.toString()
    }

    data class SkuItem(val name: String, val price: String)

    private fun collectPrices(root: AccessibilityNodeInfo): List<SkuItem> {
        val priceNodes = mutableListOf<AccessibilityNodeInfo>()
        findAllPriceNodes(root, priceNodes)

        val results = mutableListOf<SkuItem>()
        val usedNames = mutableSetOf<String>()

        for (priceNode in priceNodes) {
            val rawPrice = priceNode.text?.toString()?.trim() ?: continue
            // 从原始价格文本中提取干净的 ¥xxx/单位 部分
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
            // 匹配 ¥ 开头或包含 ¥ 且后面跟着数字
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
        // 策略1：看兄弟节点
        val parent = priceNode.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling === priceNode) continue
                val text = sibling.text?.toString()?.trim()
                if (isLikelyProductName(text)) {
                    return text!!
                }
                // 递归看一层子节点
                for (j in 0 until sibling.childCount) {
                    val child = sibling.getChild(j) ?: continue
                    val childText = child.text?.toString()?.trim()
                    if (isLikelyProductName(childText)) {
                        return childText!!
                    }
                }
            }
        }
        // 策略2：向上找到包含商品名的祖先容器
        var p = parent
        for (depth in 0 until 4) {
            p = p?.parent ?: return ""
            val text = p.text?.toString()?.trim()
            if (isLikelyProductName(text)) {
                return text!!
            }
            // 看祖先的第一个非价格子树
            for (i in 0 until p.childCount) {
                val child = p.getChild(i) ?: continue
                val childText = child.text?.toString()?.trim()
                if (isLikelyProductName(childText)) {
                    return childText!!
                }
            }
        }
        return ""
    }

    private fun isLikelyProductName(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        if (text.contains("¥") || text.contains("￥")) return false
        if (text.length < 2) return false
        // 排除常见按钮/标签文字
        val excludeKeywords = listOf("购物车", "购买", "立即", "加入", "确定", "取消", "搜索", "返回",
            "收藏", "分享", "评价", "评论", "规格", "数量", "配送", "运费", "已选", "选择",
            "立即购买", "加入购物车", "客服", "详情", "参数", "推荐", "热门", "首页", "分类", "我的")
        for (kw in excludeKeywords) {
            if (text == kw || text.startsWith(kw)) return false
        }
        // 纯数字或纯符号不算
        if (text.matches(Regex("[\\d.\\s/%gkgml两斤克]+"))) return false
        return true
    }

    private fun dumpTexts(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int) {
        val text = node.text
        if (text != null && text.toString().trim().isNotEmpty()) {
            list.add("[$depth] ${node.className}: ${text.toString().trim().take(40)}")
        }
        val desc = node.contentDescription
        if (desc != null && desc.toString().trim().isNotEmpty() && list.size < 60) {
            list.add("[$depth] desc: ${desc.toString().trim().take(40)}")
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

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(collectReceiver) } catch (_: Exception) {}
        removeFloatingWindow()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeFloatingWindow()
        return super.onUnbind(intent)
    }
}
