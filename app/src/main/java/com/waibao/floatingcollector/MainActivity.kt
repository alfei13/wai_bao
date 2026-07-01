package com.waibao.floatingcollector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Activity

class MainActivity : Activity() {

    private lateinit var statusCard: LinearLayout
    private lateinit var statusIcon: TextView
    private lateinit var statusText: TextView
    private lateinit var statusSubtext: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var appCardsContainer: LinearLayout
    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusCard = findViewById(R.id.status_card)
        statusIcon = findViewById(R.id.status_icon)
        statusText = findViewById(R.id.status_text)
        statusSubtext = findViewById(R.id.status_subtext)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        appCardsContainer = findViewById(R.id.app_cards_container)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_floating).setOnClickListener {
            if (isAccessibilityEnabled()) {
                Toast.makeText(this, "悬浮窗已随服务启动", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            }
        }

        buildAppCards()
    }

    private fun buildAppCards() {
        // 食行生鲜已隐藏，保留代码以便后续启用
        val apps = listOf(
            AppInfo("大润发优鲜", "darunfa", "大润发优鲜在线商城", 0xFFFF9F43.toInt()),
            AppInfo("菜亿萝", "caiyiluo", "菜亿萝生鲜B2B平台", 0xFF00CEC9.toInt())
            // AppInfo("食行生鲜", "shixing", "食行生鲜社区团购", 0xFF6C5CE7.toInt()),
        )

        for ((index, app) in apps.withIndex()) {
            val card = createAppCard(app)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) lp.topMargin = (density * 12).toInt()
            card.layoutParams = lp
            appCardsContainer.addView(card)
        }
    }

    private fun createAppCard(app: AppInfo): View {
        val ctx = this
        val dp14 = (density * 14).toInt()
        val dp16 = (density * 16).toInt()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp16, dp14, dp16, dp14)
            setBackgroundResource(R.drawable.bg_app_card)
            isClickable = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                elevation = density * 2
            }
        }

        // 左侧圆形头像
        val avatar = TextView(ctx).apply {
            text = app.label.first().toString()
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.OVAL
            gd.setColor(app.color)
            background = gd
            val size = (density * 44).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        card.addView(avatar)

        // 中间文字区
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp16
            layoutParams = lp
        }
        val name = TextView(ctx).apply {
            text = app.label
            textSize = 16f
            setTextColor(0xFF2D3436.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        val desc = TextView(ctx).apply {
            text = app.desc
            textSize = 12.5f
            setTextColor(0xFFB2BEC3.toInt())
        }
        textCol.addView(name)
        textCol.addView(desc)
        card.addView(textCol)

        // 右侧采集提示
        val action = TextView(ctx).apply {
            text = "采集"
            textSize = 13f
            setTextColor(0xFF6C5CE7.toInt())
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = (density * 4).toInt()
            layoutParams = lp
        }
        card.addView(action)

        val arrow = TextView(ctx).apply {
            text = "›"
            textSize = 20f
            setTextColor(0xFFB2BEC3.toInt())
        }
        card.addView(arrow)

        card.setOnClickListener {
            if (isAccessibilityEnabled()) {
                val intent = Intent("com.waibao.floatingcollector.AUTO_COLLECT").apply {
                    putExtra("app", app.appKey)
                    setPackage("com.waibao.floatingcollector")
                }
                sendBroadcast(intent)
                Toast.makeText(this, "开始采集: ${app.label}", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            }
        }

        return card
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = isAccessibilityEnabled()
        if (enabled) {
            statusCard.setBackgroundResource(R.drawable.bg_status_enabled)
            statusIcon.text = "✓"
            statusIcon.setTextColor(0xFF00B894.toInt())
            statusText.text = "无障碍服务已启用"
            statusText.setTextColor(0xFF059669.toInt())
            statusSubtext.text = "选择下方App开始采集"
            statusSubtext.setTextColor(0xFF059669.toInt())
            btnAccessibility.visibility = View.GONE
        } else {
            statusCard.setBackgroundResource(R.drawable.bg_status_disabled)
            statusIcon.text = "⚠"
            statusIcon.setTextColor(0xFFFF6B6B.toInt())
            statusText.text = "无障碍服务未启用"
            statusText.setTextColor(0xFFE74C3C.toInt())
            statusSubtext.text = "请点击下方按钮开启，否则无法采集"
            statusSubtext.setTextColor(0xFFC0392B.toInt())
            btnAccessibility.visibility = View.VISIBLE
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private data class AppInfo(val label: String, val appKey: String, val desc: String, val color: Int)
}
