package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var testResultText: TextView
    private lateinit var uploadJsonText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "价格采集器"
            textSize = 24f
            setPadding(0, 0, 0, 48)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        val btnAccessibility = Button(this).apply {
            text = "开启无障碍服务"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccessibility)

        // 3个App采集按钮（横向排列）
        val appButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        val btnShixing = Button(this).apply {
            text = "食行生鲜"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { sendCollectBroadcast("shixing") }
        }
        val btnDarunfa = Button(this).apply {
            text = "大润发"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { sendCollectBroadcast("darunfa") }
        }
        val btnCaiyiluo = Button(this).apply {
            text = "菜亿萝"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { sendCollectBroadcast("caiyiluo") }
        }
        appButtonRow.addView(btnShixing)
        appButtonRow.addView(btnDarunfa)
        appButtonRow.addView(btnCaiyiluo)
        layout.addView(appButtonRow)

        val btnStart = Button(this).apply {
            text = "启动悬浮窗（切到目标App即可采集）"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                if (isAccessibilityEnabled()) {
                    Toast.makeText(this@MainActivity, "悬浮窗已随服务启动，请切到目标App点击采集", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(btnStart)

        // 测试上报按钮
        val btnTestUpload = Button(this).apply {
            text = "测试上报（发送5条搜索数据）"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            setOnClickListener { testUpload() }
        }
        layout.addView(btnTestUpload)

        testResultText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 12, 0, 0)
            gravity = Gravity.CENTER
        }
        layout.addView(testResultText)

        // 显示上报请求 JSON（可复制）
        val jsonScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                400
            ).apply { topMargin = 24 }
        }
        uploadJsonText = TextView(this).apply {
            textSize = 12f
            setPadding(12, 12, 12, 12)
            setTextIsSelectable(true)
            text = "点击测试上报后，这里会显示发送的 JSON 参数"
        }
        jsonScroll.addView(uploadJsonText)
        layout.addView(jsonScroll)

        setContentView(layout)
    }

    private fun sendCollectBroadcast(app: String) {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent("com.waibao.floatingcollector.AUTO_COLLECT").apply {
            setPackage("com.waibao.floatingcollector")
            putExtra("app", app)
        }
        sendBroadcast(intent)
        Toast.makeText(this, "已触发${app}采集", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** 测试上报接口：发送5条正常搜索采集的数据 */
    private fun testUpload() {
        testResultText.text = "正在测试上报..."
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val batchId = "BATCH_TEST_${System.currentTimeMillis()}"
        val sourceCode = "rf_mart_fresh"
        val sourceName = "大润发优鲜"
        // 模拟大润发搜索到的5条真实商品数据
        val items = listOf(
            UploadItem(skuId = "SKU001", sourceSkuName = "精品大白菜", sourceId = sourceCode, priceValue = 2.50, priceUnit = "元/斤", priceDate = now, remark = ""),
            UploadItem(skuId = "SKU002", sourceSkuName = "黄心土豆", sourceId = sourceCode, priceValue = 3.80, priceUnit = "元/斤", priceDate = now, remark = ""),
            UploadItem(skuId = "SKU003", sourceSkuName = "普罗旺斯西红柿", sourceId = sourceCode, priceValue = 4.50, priceUnit = "元/斤", priceDate = now, remark = ""),
            UploadItem(skuId = "SKU004", sourceSkuName = "鲜鸡蛋 30枚装", sourceId = sourceCode, priceValue = 18.50, priceUnit = "元/盒", priceDate = now, remark = ""),
            UploadItem(skuId = "SKU005", sourceSkuName = "西兰花", sourceId = sourceCode, priceValue = 6.80, priceUnit = "元/斤", priceDate = now, remark = "")
        )
        // 先显示请求 JSON
        val requestJson = buildUploadJson(batchId, sourceCode, sourceName, now, items)
        uploadJsonText.text = requestJson
        Thread {
            val result = ApiClient.uploadCollectedData(
                batchId = batchId,
                sourceCode = sourceCode,
                sourceName = sourceName,
                fetchTime = now,
                items = items
            )
            Log.i("TestUpload", "success=${result.success}, accepted=${result.accepted}, rejected=${result.rejected}, msg=${result.message}")
            handler.post {
                testResultText.text = if (result.success) {
                    "上报结果: accepted=${result.accepted}, rejected=${result.rejected}\n${result.message}"
                } else {
                    "上报失败: ${result.message}"
                }
            }
        }.start()
    }

    /** 构造上报 JSON 字符串 */
    private fun buildUploadJson(
        batchId: String,
        sourceCode: String,
        sourceName: String,
        fetchTime: String,
        items: List<UploadItem>
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"batchId\": \"$batchId\",\n")
        sb.append("  \"sourceCode\": \"$sourceCode\",\n")
        sb.append("  \"sourceName\": \"$sourceName\",\n")
        sb.append("  \"fetchTime\": \"$fetchTime\",\n")
        sb.append("  \"totalCount\": ${items.size},\n")
        sb.append("  \"items\": [\n")
        for ((index, item) in items.withIndex()) {
            sb.append("    {\n")
            sb.append("      \"skuId\": \"${item.skuId}\",\n")
            sb.append("      \"sourceSkuName\": \"${item.sourceSkuName}\",\n")
            sb.append("      \"sourceId\": \"${item.sourceId}\",\n")
            sb.append("      \"priceValue\": ${item.priceValue},\n")
            sb.append("      \"priceUnit\": \"${item.priceUnit}\",\n")
            sb.append("      \"priceDate\": \"${item.priceDate}\",\n")
            sb.append("      \"remark\": \"${item.remark}\"\n")
            sb.append("    }")
            if (index < items.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    override fun onResume() {
        super.onResume()
        statusText.text = if (isAccessibilityEnabled()) {
            "无障碍服务：已启用 ✓"
        } else {
            "无障碍服务：未启用（请先开启）"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
