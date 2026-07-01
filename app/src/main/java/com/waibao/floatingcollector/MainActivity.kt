package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.view.View

class MainActivity : Activity() {

    private lateinit var statusCard: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusSubText: TextView
    private lateinit var btnAccessibility: Button
    private val density by lazy { resources.displayMetrics.density }
    private val primaryColor = 0xFF5B4FE8.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 标题
        val title = TextView(this).apply {
            text = "价格采集器"
            textSize = 28f
            setTextColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        // 无障碍状态卡片（醒目提示）
        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((density * 16).toInt(), (density * 14).toInt(), (density * 16).toInt(), (density * 14).toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (density * 28).toInt()
            layoutParams = lp
        }
        statusText = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (density * 4).toInt())
        }
        statusSubText = TextView(this).apply {
            textSize = 13f
        }
        statusCard.addView(statusText)
        statusCard.addView(statusSubText)
        layout.addView(statusCard)

        // 开启无障碍服务按钮
        btnAccessibility = Button(this).apply {
            text = "开启无障碍服务"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (density * 32).toInt() }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccessibility)

        // 采集区域标题
        val collectLabel = TextView(this).apply {
            text = "一键采集"
            textSize = 17f
            setTextColor(0xFF444444.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (density * 16).toInt())
            gravity = Gravity.CENTER
        }
        layout.addView(collectLabel)

        // App采集按钮（食行生鲜已隐藏，保留代码以便后续启用）
        val apps = listOf(
            // "食行生鲜" to "shixing",  // 暂时隐藏，需要时取消注释即可恢复
            "大润发优鲜" to "darunfa",
            "菜亿萝" to "caiyiluo"
        )
        for ((label, appKey) in apps) {
            val btn = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, (density * 14).toInt(), 0, (density * 14).toInt())
                val normalGd = GradientDrawable()
                normalGd.shape = GradientDrawable.RECTANGLE
                normalGd.cornerRadius = density * 12
                normalGd.setColor(primaryColor)
                val pressedGd = GradientDrawable()
                pressedGd.shape = GradientDrawable.RECTANGLE
                pressedGd.cornerRadius = density * 12
                pressedGd.setColor(0xFF4A3FD0.toInt())
                val sld = android.graphics.drawable.StateListDrawable()
                sld.addState(intArrayOf(android.R.attr.state_pressed), pressedGd)
                sld.addState(intArrayOf(), normalGd)
                background = sld
                isClickable = true
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (density * 12).toInt()
                layoutParams = lp
                setOnClickListener {
                    if (isAccessibilityEnabled()) {
                        val intent = Intent("com.waibao.floatingcollector.AUTO_COLLECT").apply {
                            putExtra("app", appKey)
                            setPackage("com.waibao.floatingcollector")
                        }
                        sendBroadcast(intent)
                        Toast.makeText(this@MainActivity, "开始采集: $label", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@MainActivity, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
                    }
                }
            }
            layout.addView(btn)
        }

        val btnStart = Button(this).apply {
            text = "启动悬浮窗（手动采集）"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (density * 24).toInt() }
            setOnClickListener {
                if (isAccessibilityEnabled()) {
                    Toast.makeText(this@MainActivity, "悬浮窗已随服务启动", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(btnStart)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        val cardGd = GradientDrawable()
        cardGd.shape = GradientDrawable.RECTANGLE
        cardGd.cornerRadius = density * 12

        if (enabled) {
            // 已启用：绿色柔和提示
            cardGd.setColor(0xFFECFDF5.toInt())
            cardGd.setStroke((density * 4).toInt(), 0xFF10B981.toInt())
            statusCard.background = cardGd
            statusText.text = "✓  无障碍服务已启用"
            statusText.setTextColor(0xFF059669.toInt())
            statusSubText.text = "可以选择下方App开始采集"
            statusSubText.setTextColor(0xFF059669.toInt())
            btnAccessibility.visibility = View.GONE
        } else {
            // 未启用：红色醒目警告
            cardGd.setColor(0xFFFEF3F2.toInt())
            cardGd.setStroke((density * 4).toInt(), 0xFFFE5C5C.toInt())
            statusCard.background = cardGd
            statusText.text = "⚠  无障碍服务未启用"
            statusText.setTextColor(0xFFE74C3C.toInt())
            statusSubText.text = "请点击下方按钮开启，否则无法采集"
            statusSubText.setTextColor(0xFFC0392B.toInt())
            btnAccessibility.visibility = View.VISIBLE
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
