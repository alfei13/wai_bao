package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
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
