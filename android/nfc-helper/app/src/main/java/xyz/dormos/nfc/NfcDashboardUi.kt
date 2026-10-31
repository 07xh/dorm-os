package xyz.dormos.nfc

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Zero-dependency native dashboard; the system NFC dispatcher remains the only trigger. */
class NfcDashboardUi(
    private val activity: Activity,
    private val onAction: (String) -> Unit
) {
    data class Content(val root: View, val status: TextView)

    private val ink = Color.rgb(25, 44, 79)
    private val blue = Color.rgb(28, 101, 224)
    private val muted = Color.rgb(94, 111, 139)
    private val border = Color.rgb(219, 230, 245)
    private val pale = Color.rgb(243, 247, 254)

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun background(color: Int, radius: Int = 20, outline: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (outline) setStroke(dp(1), border)
        }

    private fun text(
        value: String, size: Float, color: Int = ink, bold: Boolean = false
    ) = TextView(activity).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun params(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun button(
        label: String, action: String, strong: Boolean = false
    ) = Button(activity).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        minHeight = dp(46)
        minimumHeight = dp(46)
        setTextColor(if (strong) Color.WHITE else blue)
        backgroundTintList = ColorStateList.valueOf(if (strong) blue else Color.rgb(232, 241, 255))
        setOnClickListener { onAction(action) }
    }

    private fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = background(Color.WHITE, 18, outline = true)
    }

    private fun header(container: LinearLayout, title: String, subtitle: String) {
        container.addView(text(title, 18f, ink, bold = true), params(top = 22, bottom = 3))
        container.addView(text(subtitle, 12f, muted), params(bottom = 10))
    }

    fun build(nfcEnabled: Boolean, rootOption: Boolean, profileCount: Int): Content {
        activity.window.statusBarColor = Color.rgb(244, 248, 255)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            background = background(pale, radius = 0)
        }
        val base = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(28))
        }
        scroll.addView(base)

        val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("DORM OS", 23f, ink, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("NFC  ASSISTANT  ·  V0.6", 11f, blue, true))
        base.addView(top, params(bottom = 16))

        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(21))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(24, 80, 171), Color.rgb(31, 119, 226))
            ).apply { cornerRadius = dp(22).toFloat() }
        }
        hero.addView(text("NFC  ·  ONE TAP", 12f, Color.rgb(199, 226, 255), true))
        hero.addView(text("碰一下，执行一次", 24f, Color.WHITE, true), params(top = 8, bottom = 6))
        hero.addView(text(
            "不常驻后台，不自动轮询。\nNcwu 网络检查、快捷网页和可选 KernelSU 固定脚本。",
            13f, Color.rgb(233, 243, 255)
        ))
        base.addView(hero, params(bottom = 14))

        val metrics = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun metric(title: String, value: String, weight: Float) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(11), dp(9), dp(11))
                background = background(Color.WHITE, 14, outline = true)
            }
            item.addView(text(title, 11f, muted))
            item.addView(text(value, 14f, ink, true), params(top = 3))
            metrics.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply { rightMargin = dp(6) })
        }
        metric("NFC", if (nfcEnabled) "已开启" else "未开启", 1f)
        metric("我的标签", "$profileCount 个", 1f)
        metric("KernelSU", if (rootOption) "已启用" else "默认关闭", 1.2f)
        base.addView(metrics)

        header(base, "快速操作", "只在点击或扫描标签时工作")
        val quick = card()
        quick.addView(button("检查 Ncwu Wi-Fi", "diagnose", strong = true), params(bottom = 6))
        quick.addView(button("管理自定义 NFC 标签", "profiles"))
        base.addView(quick)

        header(base, "最近一次运行", "检查结果实时显示，不代表自动登录成功")
        val resultCard = card()
        val state = text("尚未运行 · 等待 NFC 标签", 14f, ink)
        state.setLineSpacing(dp(4).toFloat(), 1f)
        resultCard.addView(state)
        resultCard.addView(button("复制脱敏诊断结果", "copy"), params(top = 12))
        base.addView(resultCard)

        header(base, "标签和权限", "已有标签可继续使用；写入可能覆盖原内容")
        val settings = card()
        for ((title, action) in listOf(
            "写入 Ncwu 专用标签" to "write",
            "查看 NFC 启动权限" to "nfc-permission",
            "授权读取 Wi-Fi 名称" to "location",
            "打开 Wi-Fi 设置" to "wifi-settings",
            "取消 NFC 标签写入" to "cancel-write"
        )) {
            settings.addView(button(title, action), params(bottom = 4))
        }
        base.addView(settings)

        header(base, "高级功能", "Root 为可选权限，默认关闭")
        val advanced = card()
        advanced.addView(button("启用 / 关闭 KernelSU 固定 SH", "ksu"))
        advanced.addView(button("设备与权限自检", "self-check"), params(top = 5))
        advanced.addView(button("查看使用说明", "help"), params(top = 5))
        base.addView(advanced)

        base.addView(text(
            "安全说明：标签只保存动作编号。当前版本不提交 Ncwu 登录密码，也不支持让 NFC 执行任意脚本。",
            12f, muted
        ), params(top = 20))
        return Content(scroll, state)
    }
}
