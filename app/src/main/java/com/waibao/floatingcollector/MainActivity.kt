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

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val primaryColor = 0xFF5B6AE0.toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "价格采集器"
            textSize = 26f
            setTextColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 40)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        val btnAccessibility = Button(this).apply {
            text = "开启无障碍服务"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 32 }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccessibility)

        // 采集区域标题
        val collectLabel = TextView(this).apply {
            text = "一键采集"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER
        }
        layout.addView(collectLabel)

        // 3个App采集按钮
        val apps = listOf(
            "食行生鲜" to "shixing",
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
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = density * 12
                gd.setColor(primaryColor)
                background = gd
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
            ).apply { topMargin = 24 }
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
