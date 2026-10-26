package com.chemlab.game

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GameView(context: Context, val achievements: AchievementManager) : View(context) {

    enum class Screen { TITLE, LAB, PICKER, GUIDE, SETTINGS, ABOUT }

    private val game = GameState()
    private var screen = Screen.TITLE

    // 滚动偏移
    private var titleScroll = 0f
    private var statusScroll = 0f
    private var pickerScroll = 0f
    private var achScroll = 0f
    private var catalogScroll = 0f
    private var aboutScroll = 0f

    // 成就·图鉴合并界面：0=成就，1=图鉴
    private var guideTab = 0
    private val guideTabs = ArrayList<Pair<Int, RectF>>()

    // 实验台操作栏（横向可滑动）
    private var labBarScrollX = 0f
    private var labBarScrollStart = 0f
    private var labBarMaxScroll = 0f
    private var labBarRect = RectF()

    // 底部操作按钮（LAB）
    private class Btn(val label: String, val rect: RectF, val action: () -> Unit)
    private val buttons = ArrayList<Btn>()

    // 实验状态旁的「常用」侧栏按钮（排气 / 清洗 / 换容器）
    private val sideButtons = ArrayList<Btn>()

    // 列表项点击热区（按屏幕缓存）
    private val pickerItems = ArrayList<Pair<Substance, RectF>>()
    private val categoryTabs = ArrayList<Pair<Category, RectF>>()
    private val achItems = ArrayList<Pair<AchievementManager.Def, RectF>>()
    private val catalogItems = ArrayList<Pair<Substance, RectF>>()
    private val settingsRows = ArrayList<Pair<String, RectF>>()

    // 可滚动区域
    private var scrollRect = RectF()

    // 当前列表的最大可滚动距离（每次绘制时按内容高度更新）
    private var scrollMax = 0f

    // 布局关键值（供触摸判定）
    private var headerH = 0f

    // 安全显示区 insets（避开系统圆角/刘海，物理 px）
    private var safeLeft = 0f
    private var safeTop = 0f
    private var safeRight = 0f
    private var safeBottom = 0f

    // 调校系数：越小越充分利用屏幕（越小安全边距越小）
    private val SAFE_CORNER_SCALE = 0.45f     // 系统圆角半径 × 该系数后作为内缩量
    private val SAFE_FALLBACK_FRACTION = 0.06f // 系统无数据时的兜底内缩比例

    // 触摸
    private var downX = 0f
    private var downY = 0f
    private var scrollStart = 0f
    private var dragging = false

    // 成就提示
    private var toast: String? = null
    private var toastUntil = 0L

    // 设置项状态
    private var soundEnabled = true
    private var resetArmed = false
    private var resetArmedUntil = 0L

    // UI 颜色
    private val C_BG = 0xFF10151C.toInt()
    private val C_PANEL = 0xFF1C2430.toInt()
    private val C_BTN = 0xFF2A3542.toInt()
    private val C_BTN_ACTIVE = 0xFF3A4A5C.toInt()
    private val C_BTN_PRESS = 0xFF5A6C80.toInt()
    private val C_ACCENT = 0xFF4FC3F7.toInt()
    private val C_TEXT = 0xFFECEFF4.toInt()
    private val C_DIM = 0xFF9AA7B4.toInt()
    private val C_FLAME = 0xFFFFB74D.toInt()

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_TEXT }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    // 高温/反应时的徽标柔光
    private val badgeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // 背景渐变（程序化"素材"渲染：暗色渐变 + 顶部光晕呼吸）
    private val bgShaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // 动画 / 动效时间戳（用于闪光、溶解、反应辉光等）
    private var lastReactMs = 0L
    private var lastDissolveMs = 0L
    private var lastTickMs = 0L
    private var pressedLabel: String? = null

    init {
        soundEnabled = context.getSharedPreferences("chemlab", Context.MODE_PRIVATE)
            .getBoolean("sound_enabled", true)
        game.onReaction = { r ->
            lastReactMs = SystemClock.uptimeMillis()
            achievements.onReaction(r)
        }
        game.onAction = { tag ->
            if (tag == "stir") lastDissolveMs = SystemClock.uptimeMillis()
            playActionSound(tag)
            achievements.onAction(tag)
        }
        achievements.onUnlock = { showToast("解锁成就：${it.name}") }
        // 监听系统 insets（圆角 / 刘海安全区），仅用于绘制时取安全区，不动窗口行为
        if (Build.VERSION.SDK_INT >= 20) {
            setOnApplyWindowInsetsListener { _, insets ->
                updateSafeInsets(insets)
                insets
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            // 游戏逻辑/温度以 600ms 节拍推进
            if (now - lastTickMs >= 600) {
                lastTickMs = now
                if (screen == Screen.LAB) game.tick()
            }
            // 有动效、提示或标题页呼吸光晕时以 ~50ms 刷新实现平滑动画；空闲时不做无谓重绘以省电
            if ((screen == Screen.LAB && wantsAnimation()) || screen == Screen.TITLE || isToastActive()) invalidate()
            postDelayed(this, 50)
        }
    }

    /** 判定当前是否有需要在实验台播放的动效 */
    private fun wantsAnimation(): Boolean {
        val c = game.container
        val now = SystemClock.uptimeMillis()
        return c.heating || c.electrolyzing || c.gases.isNotEmpty() || c.ignitionArmed ||
            (c.temperature > 25.05f && !c.heating) ||
            now - lastReactMs < 900 || now - lastDissolveMs < 1100
    }

    fun start() { removeCallbacks(ticker); postDelayed(ticker, 600) }
    fun stop() { removeCallbacks(ticker) }

    /** 页面返回逻辑：返回上一级页面，逐级回退；在 TITLE 返回 false 交由系统处理退出 */
    fun back(): Boolean {
        return when (screen) {
            Screen.LAB -> { screen = Screen.TITLE; onLeave() }
            Screen.PICKER -> { screen = Screen.LAB; onLeave() }
            Screen.GUIDE, Screen.SETTINGS, Screen.ABOUT -> { screen = Screen.TITLE; onLeave() }
            Screen.TITLE -> false
        }
    }

    private fun onLeave(): Boolean {
        buttons.clear()
        invalidate()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 尺寸变化时刷新安全区（insets 可能随窗口/旋转变化；兜底估算需真实宽高）
        if (Build.VERSION.SDK_INT >= 20) updateSafeInsets(rootWindowInsets) else updateSafeInsets(null)
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /**
     * 计算当前"安全显示矩形"的四边 insets（物理 px）：
     * 优先取系统 insets（圆角半径 + 刘海 safeInset 的最大值），
     * 全部为 0 或拿不到系统值时，按屏幕比例兜底（圆角/圆形屏：四周统一 W*0.14）。
     */
    private fun updateSafeInsets(insets: WindowInsets?) {
        var l = 0f; var t = 0f; var r = 0f; var b = 0f

        if (insets != null) {
            // 刘海屏 safeInset（API 28+）
            val cutout = insets.displayCutout
            if (Build.VERSION.SDK_INT >= 28 && cutout != null) {
                l = max(l, cutout.safeInsetLeft.toFloat())
                t = max(t, cutout.safeInsetTop.toFloat())
                r = max(r, cutout.safeInsetRight.toFloat())
                b = max(b, cutout.safeInsetBottom.toFloat())
            }

            // 系统圆角半径（API 30+）
            if (Build.VERSION.SDK_INT >= 30) {
                fun corner(which: Int): Float = insets.getRoundedCorner(which)?.radius?.toFloat() ?: 0f
                val tl = corner(RoundedCorner.POSITION_TOP_LEFT) * SAFE_CORNER_SCALE
                val tr = corner(RoundedCorner.POSITION_TOP_RIGHT) * SAFE_CORNER_SCALE
                val bl = corner(RoundedCorner.POSITION_BOTTOM_LEFT) * SAFE_CORNER_SCALE
                val br = corner(RoundedCorner.POSITION_BOTTOM_RIGHT) * SAFE_CORNER_SCALE
                l = max(l, max(tl, bl))
                r = max(r, max(tr, br))
                t = max(t, max(tl, tr))
                b = max(b, max(bl, br))
            }
        }

        // 兜底：系统 insets 全为 0 / 拿不到时，按屏幕比例估算（圆角/圆形屏）
        if (l <= 0f && t <= 0f && r <= 0f && b <= 0f) {
            val w = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
            val fallback = w * SAFE_FALLBACK_FRACTION
            l = fallback; t = fallback; r = fallback; b = fallback
        }

        // l/t/r/b 都是"距各边的内缩量"，需转成实际矩形坐标：
        // 左/上就是坐标起点；右/下 = 屏幕宽高 − 内缩量
        val wPx = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        val hPx = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
        safeLeft = l
        safeTop = t
        safeRight = wPx - r
        safeBottom = hPx - b
    }

    private fun showToast(s: String) { toast = s; toastUntil = SystemClock.uptimeMillis() + 2500 }
    private fun isToastActive() = toast != null && SystemClock.uptimeMillis() < toastUntil

    /** 根据操作事件播放对应音效 */
    private fun playActionSound(tag: String) {
        when {
            tag.startsWith("add:") -> {
                val s = Substances.get(tag.removePrefix("add:"))
                val kind = when {
                    s.isGas -> SoundEffects.Kind.GAS
                    s.phase == Phase.AQUEOUS || s.phase == Phase.LIQUID -> SoundEffects.Kind.LIQUID
                    else -> SoundEffects.Kind.SOLID
                }
                SoundEffects.play(kind, soundEnabled)
            }
            tag == "heat" && game.container.heating -> SoundEffects.play(SoundEffects.Kind.FLAME, soundEnabled)
            tag == "ignite" -> SoundEffects.play(SoundEffects.Kind.FLAME, soundEnabled)
        }
    }

    // ================= 绘制 =================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        // 尚未获得安全区（例如个别设备 insets 回调滞后）时先补齐，保证 availW/availH > 0
        if (safeRight <= safeLeft || safeBottom <= safeTop) {
            updateSafeInsets(if (Build.VERSION.SDK_INT >= 20) rootWindowInsets else null)
        }
        // 背景：暗色纵向渐变 + 顶部呼吸光晕（性能足够，采用程序化高质渲染）
        if (bgShaderPaint.shader == null) {
            bgShaderPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF16222D.toInt(), C_BG, 0xFF0B1117.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            glowPaint.shader = RadialGradient(
                width / 2f, 0f, width.toFloat(),
                intArrayOf(0x55FFFFFF.toInt(), 0x44FFB74D.toInt(), 0x00FFFFFF.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgShaderPaint)
        // 顶部光晕随相位呼吸（alpha 脉动），为各页带来柔和动效
        val ph = (SystemClock.uptimeMillis() % 3000L).toFloat() / 3000f
        glowPaint.alpha = (28 + 30 * (0.5f - 0.5f * kotlin.math.cos(ph * 6.283f)).toFloat()).toInt()
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height * 0.28f), glowPaint)

        buttons.clear(); pickerItems.clear(); categoryTabs.clear(); achItems.clear(); catalogItems.clear(); settingsRows.clear(); guideTabs.clear(); pickerSubTabs.clear()

        when (screen) {
            Screen.TITLE -> drawTitle(canvas)
            Screen.LAB -> drawLab(canvas)
            Screen.PICKER -> drawPicker(canvas)
            Screen.GUIDE -> drawGuide(canvas)
            Screen.SETTINGS -> drawSettings(canvas)
            Screen.ABOUT -> drawAbout(canvas)
        }

        if (isToastActive() && toast != null) drawToast(canvas, toast!!)
    }

    private val insetX get() = availW * 0.06f
    private val insetTop get() = availH * 0.05f
    private val availW get() = safeRight - safeLeft
    private val availH get() = safeBottom - safeTop
    private val mainLeft get() = safeLeft + insetX
    private val mainRight get() = safeRight - insetX

    private fun drawTitle(canvas: Canvas) {
        val cx = safeLeft + availW / 2f
        // 顶部强调标线（横贯安全区）
        pFill.color = C_ACCENT
        canvas.drawRect(safeLeft, safeTop, safeRight, safeTop + dp(4f), pFill)
        // 标题（固定区）
        drawText(canvas, "化学实验室", cx, safeTop + availH * 0.14f, C_ACCENT, dp(30f), centerX = true)
        pFill.color = C_BTN
        canvas.drawRoundRect(RectF(cx - availW * 0.18f, safeTop + availH * 0.14f + dp(6f), cx + availW * 0.18f, safeTop + availH * 0.14f + dp(8f)), dp(2f), dp(2f), pFill)

        // 可滚动按钮区
        val contentTop = safeTop + availH * 0.14f + dp(24f)
        val listRect = RectF(mainLeft, contentTop, mainRight, safeBottom - dp(4f))
        scrollRect = listRect
        canvas.save(); canvas.clipRect(listRect)

        val labels = listOf("开始实验", "器材联动实验", "成就 · 图鉴", "设置", "关于")
        val actions = listOf<() -> Unit>(
            { screen = Screen.LAB },
            { openWorldMode() },
            { guideTab = 0; screen = Screen.GUIDE },
            { screen = Screen.SETTINGS },
            { screen = Screen.ABOUT },
        )
        val itemH = dp(52f)
        val gap = dp(10f)
        val contentH = labels.size * itemH + (labels.size - 1) * gap + dp(46f)
        scrollMax = (contentH - listRect.height()).coerceAtLeast(0f)

        var y = listRect.top - titleScroll
        for ((i, lb) in labels.withIndex()) {
            val r = RectF(cx - availW * 0.38f, y, cx + availW * 0.38f, y + itemH)
            pFill.color = when {
                lb == pressedLabel -> C_BTN_PRESS
                i == 0 -> C_BTN_ACTIVE
                else -> C_BTN
            }
            canvas.drawRoundRect(r, dp(14f), dp(14f), pFill)
            pFill.color = 0x33FFFFFF
            canvas.drawRoundRect(RectF(r.left + dp(10f), y + dp(4f), r.right - dp(10f), y + dp(9f)), dp(5f), dp(5f), pFill)
            drawText(canvas, if (lb == pressedLabel) "▸ $lb" else lb, r.centerX(), r.centerY(), C_TEXT, dp(17f), centerX = true)
            buttons.add(Btn(lb, r, actions[i]))
            y += itemH + gap
        }

        canvas.restore()
    }

    /**
     * 打开「器材联动实验」模式。
     *
     * 该模式用 Jetpack Compose 实现，单独做成一个 Activity，
     * 这样本类的 Canvas 渲染路径完全不受影响；返回键即可回到首页。
     */
    private fun openWorldMode() {
        val intent = Intent(context, com.chemlab.ui.ExperimentActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun drawLab(canvas: Canvas) {
        val h = availH
        val headerH = h * 0.075f
        this.headerH = headerH
        val c = game.container

        // 计算动画状态（时间驱动）
        val now = SystemClock.uptimeMillis()
        val phase = (now % 900).toFloat() / 900f
        val flash = (1f - (now - lastReactMs) / 700f).coerceIn(0f, 1f)
        val dissolve = (1f - (now - lastDissolveMs) / 1100f).coerceIn(0f, 1f)

        val hTop = safeTop
        val hBottom = safeTop + headerH
        // —— 顶部标题栏 ——
        pFill.color = C_PANEL
        canvas.drawRoundRect(RectF(safeLeft, hTop, safeRight, hBottom), 0f, 0f, pFill)
        // 标题栏下方强调线（高温时偏暖色，正常偏主题蓝）
        val tempHot = c.temperature >= 60f
        pFill.color = when {
            tempHot -> 0xFFB0743A.toInt()
            else -> C_ACCENT
        }
        canvas.drawRect(safeLeft, hBottom - dp(2f), safeRight, hBottom, pFill)
        drawText(canvas, "‹", safeLeft + dp(6f), hTop + headerH / 2f, C_DIM, dp(18f), centerX = true)
        drawText(canvas, c.equipment.cn, safeLeft + dp(30f), hTop + headerH / 2f, C_TEXT, dp(15f))

        // 顶部温度徽标：高温变色 + 柔和光晕 + 呼吸脉动
        val flag = buildString {
            if (c.heating) append("🔥")
            if (c.electrolyzing) append("⚡")
            if (c.ignitionArmed) append("✨")
        }
        val badgeText = "%.0f℃ %s".format(c.temperature, flag)
        val tempColor = when {
            c.temperature >= 300f -> 0xFFFF7A5C.toInt()
            tempHot -> C_FLAME
            else -> C_ACCENT
        }
        val badgeW = dp(76f)
        val badgeRect = RectF(safeRight - dp(8f) - badgeW, hTop + headerH / 2f - dp(10f), safeRight - dp(8f), hTop + headerH / 2f + dp(10f))
        // 徽标背景胶囊
        pFill.color = if (tempHot) 0x66C87334.toInt() else 0x22000000.toInt()
        canvas.drawRoundRect(badgeRect, dp(9f), dp(9f), pFill)
        if (tempHot || flash > 0f) {
            // 高温/反应中：给徽标一个暖色呼吸光晕
            val pulse = 0.5f + 0.5f * kotlin.math.sin(phase * 6.283f).toFloat()
            val ga = if (tempHot) (0x38 + 0x28 * pulse).toInt() else (0x30 + 0x24 * flash).toInt()
            val glowColor = (ga shl 24) or (tempColor and 0x00FFFFFF)
            badgeGlow.shader = RadialGradient(
                badgeRect.centerX(), badgeRect.centerY(), badgeW * 1.3f,
                intArrayOf(glowColor, tempColor and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(badgeRect.centerX(), badgeRect.centerY(), badgeW * 1.15f, badgeGlow)
        }
        drawText(canvas, badgeText, badgeRect.centerX(), badgeRect.centerY(), tempColor, dp(13f), centerX = true)

        // —— 主体：装置在上 → 实验状态居中 → 操作按钮（横向滑动）在下 ——
        val bodyTop = hBottom + dp(6f)
        val bodyBottom = safeBottom - dp(4f)
        val bodyH = (bodyBottom - bodyTop).coerceAtLeast(0f)
        val gap = dp(6f)
        val btnH = min(dp(60f), bodyH * 0.34f)          // 单行操作栏高度
        val remainH = (bodyH - btnH - gap * 2).coerceAtLeast(0f)
        val appH = remainH * 0.42f
        val statusH = remainH - appH

        // 1) 装置区（圆角卡片面板）
        val appRect = RectF(safeLeft + dp(6f), bodyTop, safeRight - dp(6f), bodyTop + appH)
        pFill.color = C_PANEL
        canvas.drawRoundRect(appRect, dp(16f), dp(16f), pFill)
        pFill.color = 0x14FFFFFF
        canvas.drawRoundRect(RectF(appRect.left + dp(8f), appRect.top + dp(3f), appRect.right - dp(8f), appRect.top + dp(7f)), dp(4f), dp(4f), pFill)
        if (flash > 0f || tempHot) {
            pStroke.strokeWidth = dp(2f)
            val ga = ((70 + 80 * (if (flash > 0f) flash else 0.35f)).toInt() and 0xFF)
            pStroke.color = (ga shl 24) or (tempColor and 0x00FFFFFF)
            canvas.drawRoundRect(appRect, dp(16f), dp(16f), pStroke)
        }
        val cyApp = appRect.centerY()
        val dw = min(appRect.width() * 0.86f, appRect.height() * 0.95f)
        val dh = appRect.height() * 0.78f
        Icons.drawContainer(canvas, appRect.centerX(), cyApp, dw, dh, c, Icons.Fx(phase, flash, dissolve))

        // 2) 实验状态区（左） + 常用侧栏（右）
        val statusTop = appRect.bottom + gap
        val statusBottom = statusTop + statusH
        val sideW = dp(64f)
        val statusRight = (safeRight - sideW - gap).coerceAtLeast(safeLeft + dp(80f))
        val statusRect = RectF(safeLeft, statusTop, statusRight, statusBottom)
        pFill.color = C_PANEL
        canvas.drawRoundRect(statusRect, dp(16f), dp(16f), pFill)
        if (flash > 0f || tempHot) {
            pStroke.strokeWidth = dp(2f)
            val ga = ((70 + 80 * (if (flash > 0f) flash else 0.35f)).toInt() and 0xFF)
            pStroke.color = (ga shl 24) or (tempColor and 0x00FFFFFF)
            canvas.drawRoundRect(statusRect, dp(16f), dp(16f), pStroke)
        }
        drawText(canvas, "实验日志", statusRect.left + dp(10f), statusTop + dp(15f), if (tempHot) C_FLAME else C_ACCENT, dp(12f))
        val contentRect = RectF(statusRect.left + dp(8f), statusTop + dp(27f), statusRect.right - dp(8f), statusBottom - dp(3f))
        scrollRect = contentRect
        canvas.save()
        canvas.clipRect(contentRect)
        val lines = buildStatusLines(c)
        val lineH = dp(18f)
        scrollMax = (lines.size * lineH - contentRect.height()).coerceAtLeast(0f)
        var y = contentRect.top - statusScroll
        for ((text, color) in lines) {
            if (y + lineH < contentRect.top) { y += lineH; continue }
            if (y > contentRect.bottom) break
            drawText(canvas, text, contentRect.left, y + lineH / 2f, color, dp(12f))
            y += lineH
        }
        canvas.restore()

        // 2b) 常用侧栏（排气 / 清洗 / 换容器）
        val sideRect = RectF(statusRight + gap, statusTop, safeRight, statusBottom)
        if (sideRect.width() > dp(30f)) {
            pFill.color = C_PANEL
            canvas.drawRoundRect(sideRect, dp(14f), dp(14f), pFill)
            drawText(canvas, "常用", sideRect.centerX(), statusTop + dp(15f), C_ACCENT, dp(12f), centerX = true)
            buildSideButtons(sideRect)
            for (b in sideButtons) drawLabButton(canvas, b)
        } else {
            sideButtons.clear()
        }

        // 3) 操作按钮区（底部，横向可滑动）
        val btnTop = statusBottom + gap
        labBarRect.set(safeLeft, btnTop, safeRight, btnTop + btnH)
        buildLabButtons()
        for (b in buttons) drawLabButton(canvas, b)
        // 右缘滑动提示
        if (labBarMaxScroll > 0f) {
            val hintW = dp(16f)
            pFill.color = 0x6610151C.toInt()
            canvas.drawRect(safeRight - hintW, btnTop, safeRight, btnTop + btnH, pFill)
            drawText(canvas, "›", safeRight - hintW / 2f, btnTop + btnH / 2f, C_DIM, dp(16f), centerX = true)
        }
    }

    /** 主操作与次要操作按钮，用于配色主次区分 */
    private fun isPrimaryButton(label: String): Boolean =
        label == "加物质" ||
            label == "加热" || label == "熄灭" || label == "点燃" ||
            label == "通电" || label == "断电" || label == "搅拌溶解"

    private fun drawLabButton(canvas: Canvas, b: Btn) {
        val press = b.label == pressedLabel
        val primary = isPrimaryButton(b.label)
        val on = b.label == "熄灭" || b.label == "断电" // 当前处于开启态（加热/通电）
        val fill = when {
            press -> C_BTN_PRESS
            on -> 0xFF3E5E6E.toInt()         // 开启态：偏青色调强调
            primary -> C_BTN_ACTIVE
            else -> C_BTN
        }
        val radius = dp(14f)
        pFill.color = fill
        canvas.drawRoundRect(b.rect, radius, radius, pFill)
        // 顶部内高光
        pFill.color = 0x22FFFFFF
        canvas.drawRoundRect(RectF(b.rect.left + dp(6f), b.rect.top + dp(3f), b.rect.right - dp(6f), b.rect.top + dp(7f)), dp(3f), dp(3f), pFill)
        // 文字颜色：开启态用强调色
        val tc = when { press -> C_TEXT; on -> C_ACCENT; else -> C_TEXT }
        val ts = if (primary) dp(15f) else dp(13f)
        if (on) {
            // 开启态：主题描边 + 顶部小圆点“电力/热度指示”
            pStroke.color = C_ACCENT
            pStroke.strokeWidth = dp(2f)
            canvas.drawRoundRect(b.rect, radius, radius, pStroke)
            drawText(canvas, "●", b.rect.centerX(), b.rect.top + dp(10f), C_ACCENT, dp(7f), centerX = true)
        } else if (primary) {
            pStroke.color = 0x44FFFFFF
            pStroke.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(RectF(b.rect.left + dp(1f), b.rect.top + dp(1f), b.rect.right - dp(1f), b.rect.bottom - dp(1f)), radius, radius, pStroke)
        }
        drawText(canvas, b.label, b.rect.centerX(), b.rect.centerY(), tc, ts, centerX = true)
    }

    /** 实验台操作：横向一排，可左右滑动；内容总宽超出可视区时启用横向滚动 */
    private fun buildLabButtons() {
        buttons.clear()
        val c = game.container
        val pad = dp(4f)
        val itemW = dp(96f)
        // 横向顺序：加物质入口 → 条件开关 → 单键操作
        val spec = ArrayList<Pair<String, () -> Unit>>()
        spec += "加物质" to { screen = Screen.PICKER }
        spec += (if (c.heating) "熄灭" else "加热") to { game.toggleHeat() }
        spec += (if (c.electrolyzing) "断电" else "通电") to { game.toggleElectrolysis() }
        spec += "点燃" to { game.ignite() }
        spec += "搅拌溶解" to { game.stir() }
        spec += "加水" to { game.addSubstance("h2o") }
        spec += "冷却" to { game.cool() }
        spec += "蒸发结晶" to { game.evaporate() }
        val top = labBarRect.top + pad
        val bh = (labBarRect.height() - pad * 2).coerceAtLeast(0f)
        var x = labBarRect.left + pad - labBarScrollX
        for ((label, action) in spec) {
            buttons.add(Btn(label, RectF(x, top, x + itemW, top + bh), action))
            x += itemW + pad
        }
        val totalW = spec.size * itemW + (spec.size + 1) * pad
        labBarMaxScroll = (totalW - labBarRect.width()).coerceAtLeast(0f)
        labBarScrollX = labBarScrollX.coerceIn(0f, labBarMaxScroll)
    }

    /** 「常用」侧栏：排气 / 清洗 / 换容器，纵向排列在实验日志右侧 */
    private fun buildSideButtons(panel: RectF) {
        sideButtons.clear()
        val pad = dp(5f)
        val top = panel.top + dp(24f)
        val bh = ((panel.bottom - dp(5f) - top - pad * 2) / 3f).coerceIn(dp(28f), dp(54f))
        val spec = listOf<Pair<String, () -> Unit>>(
            "排气" to { game.removeGases() },
            "清洗" to { game.clean() },
            "换容器" to { cycleEquipment() }
        )
        val blockH = bh * spec.size + pad * (spec.size - 1)
        var y = top + ((panel.bottom - dp(5f) - top - blockH) / 2f).coerceAtLeast(0f)
        for ((label, action) in spec) {
            sideButtons.add(Btn(label, RectF(panel.left + pad, y, panel.right - pad, y + bh), action))
            y += bh + pad
        }
    }

    private fun cycleEquipment() {
        val all = Equipments.ALL
        val idx = all.indexOfFirst { it.id == game.container.equipment.id }
        game.switchEquipment(all[(idx + 1) % all.size])
    }

    private fun buildStatusLines(c: Container): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        out.add("器材：${c.equipment.cn}" to C_TEXT)
        out.add("温度：%.0f℃　水量：%.0f mL".format(c.temperature, c.waterMl) to C_ACCENT)
        if (c.solids.isNotEmpty()) out.add("固体：" + describe(c.solids) to C_TEXT)
        if (c.solutes.isNotEmpty()) out.add("溶液：" + describe(c.solutes) to C_TEXT)
        if (c.gases.isNotEmpty()) out.add("气体：" + describe(c.gases) to C_TEXT)
        out.add("—— 反应记录 ——" to C_DIM)
        for (l in c.log) out.add(l to C_DIM)
        return out
    }

    private fun describe(map: Map<String, Double>): String =
        map.entries.joinToString("　") { (id, mol) ->
            val s = Substances.get(id)
            "%s %.1f".format(s.cn, mol)
        }

    private fun drawPicker(canvas: Canvas) {
        val w = availW; val top = safeTop + insetTop
        val cx = safeLeft + w / 2f
        drawText(canvas, "选择物质（点击加入）", cx, top, C_ACCENT, dp(15f), centerX = true)

        val cats = Category.values()
        val tabH = dp(30f)
        var y = top + dp(20f)
        // 一级分类标签
        val tabW = (w - insetX * 2 - dp(4f) * 4) / 5f
        var x = mainLeft
        for (cat in cats) {
            val r = RectF(x, y, x + tabW, y + tabH)
            pFill.color = if (someCat == cat) C_BTN_ACTIVE else C_BTN
            canvas.drawRoundRect(r, dp(8f), dp(8f), pFill)
            drawText(canvas, cat.label, r.centerX(), r.centerY(), C_TEXT, dp(11f), centerX = true)
            categoryTabs.add(cat to r)
            x += tabW + dp(4f)
        }

        // 二级分类标签（横向可滑动，位于一级分类下方）
        val subs = Substances.subGroups(someCat)
        if (pickerSubSel !in subs) pickerSubSel = Substances.SUB_ALL
        val subH = dp(28f)
        val subTop = y + tabH + dp(6f)
        pickerSubRect.set(mainLeft, subTop, mainRight, subTop + subH)
        val subPad = dp(4f)
        val subItemW = dp(58f)
        var sx = mainLeft + subPad - pickerSubScrollX
        for (sub in subs) {
            val r = RectF(sx, subTop + subPad, sx + subItemW, subTop + subH - subPad)
            val on = sub == pickerSubSel
            pFill.color = if (on) C_BTN_ACTIVE else C_PANEL
            canvas.drawRoundRect(r, dp(10f), dp(10f), pFill)
            if (on) {
                pStroke.color = C_ACCENT; pStroke.strokeWidth = dp(1.5f)
                canvas.drawRoundRect(r, dp(10f), dp(10f), pStroke)
            }
            drawText(canvas, sub, r.centerX(), r.centerY(), if (on) C_ACCENT else C_DIM, dp(11f), centerX = true)
            pickerSubTabs.add(sub to r)
            sx += subItemW + subPad
        }
        val subTotalW = subs.size * subItemW + (subs.size + 1) * subPad
        pickerSubMaxScroll = (subTotalW - pickerSubRect.width()).coerceAtLeast(0f)
        pickerSubScrollX = pickerSubScrollX.coerceIn(0f, pickerSubMaxScroll)
        // 右缘滑动提示
        if (pickerSubMaxScroll > 0f) {
            val hintW = dp(14f)
            pFill.color = 0x6610151C.toInt()
            canvas.drawRect(mainRight - hintW, subTop, mainRight, subTop + subH, pFill)
            drawText(canvas, "›", mainRight - hintW / 2f, subTop + subH / 2f, C_DIM, dp(13f), centerX = true)
        }

        val listTop = subTop + subH + dp(8f)
        val listRect = RectF(mainLeft, listTop, mainRight, safeBottom - insetTop)
        scrollRect = listRect
        canvas.save(); canvas.clipRect(listRect)

        val items = Substances.byCategory(someCat).filter { Substances.inSubGroup(it, pickerSubSel) }
        val itemH = dp(34f)
        scrollMax = (items.size * (itemH + dp(4f)) - listRect.height()).coerceAtLeast(0f)
        if (items.isEmpty()) {
            drawText(canvas, "该分类下暂无物质", cx, listTop + dp(24f), C_DIM, dp(13f), centerX = true)
        }
        var iy = listTop - pickerScroll
        for (s in items) {
            if (iy + itemH < listTop) { iy += itemH; continue }
            if (iy > listRect.bottom) break
            val r = RectF(mainLeft, iy, mainRight, iy + itemH)
            pFill.color = C_PANEL
            canvas.drawRoundRect(r, dp(6f), dp(6f), pFill)
            Icons.drawSubstanceDot(canvas, mainLeft + dp(16f), iy + itemH / 2f, dp(9f), s)
            drawText(canvas, s.cn, mainLeft + dp(30f), iy + itemH / 2f, C_TEXT, dp(13f))
            drawText(canvas, s.formula, mainRight - dp(10f), iy + itemH / 2f, C_DIM, dp(11f), centerX = true)
            pickerItems.add(s to r)
            iy += itemH + dp(4f)
        }
        canvas.restore()

        drawText(canvas, "‹ 返回", mainLeft, safeBottom - insetTop, C_DIM, dp(13f))
    }

    private var someCat = Category.ELEMENT

    // 二级分类：当前选中的子分类、横向滚动与点击热区
    private var pickerSubSel = Substances.SUB_ALL
    private var pickerSubScrollX = 0f
    private var pickerSubScrollStart = 0f
    private var pickerSubMaxScroll = 0f
    private val pickerSubRect = RectF()
    private val pickerSubTabs = ArrayList<Pair<String, RectF>>()

    /** 成就 · 图鉴 合并界面：顶部切换标签，共用一个列表区域 */
    private fun drawGuide(canvas: Canvas) {
        val w = availW; val top = safeTop + insetTop
        val cx = safeLeft + w / 2f
        val achN = achievements.unlockedCount()
        val achTotal = achievements.defs.size
        val colN = achievements.collectedCount()
        val colTotal = achievements.catalogTotal()
        drawText(canvas, "成就 · 图鉴", cx, top, C_ACCENT, dp(15f), centerX = true)

        // 顶部两个切换标签
        val tabH = dp(32f)
        var y = top + dp(20f)
        val tabW = (w - insetX * 2 - dp(6f)) / 2f
        val tabLabels = listOf("成就（$achN/$achTotal）", "图鉴（$colN/$colTotal）")
        var tx = mainLeft
        for (i in tabLabels.indices) {
            val r = RectF(tx, y, tx + tabW, y + tabH)
            pFill.color = if (guideTab == i) C_BTN_ACTIVE else C_BTN
            canvas.drawRoundRect(r, dp(8f), dp(8f), pFill)
            if (guideTab == i) {
                pStroke.color = C_ACCENT; pStroke.strokeWidth = dp(1.5f)
                canvas.drawRoundRect(r, dp(8f), dp(8f), pStroke)
            }
            drawText(canvas, tabLabels[i], r.centerX(), r.centerY(), if (guideTab == i) C_ACCENT else C_TEXT, dp(12f), centerX = true)
            guideTabs.add(i to r)
            tx += tabW + dp(6f)
        }

        val listRect = RectF(mainLeft, y + tabH + dp(8f), mainRight, safeBottom - insetTop)
        scrollRect = listRect
        canvas.save(); canvas.clipRect(listRect)

        if (guideTab == 0) {
            val itemH = dp(40f)
            scrollMax = (achTotal * (itemH + dp(4f)) - listRect.height()).coerceAtLeast(0f)
            var iy = listRect.top - achScroll
            for (d in achievements.defs) {
                if (iy + itemH < listRect.top) { iy += itemH; continue }
                if (iy > listRect.bottom) break
                val unlocked = achievements.isUnlocked(d.id)
                pFill.color = C_PANEL
                val r = RectF(mainLeft, iy, mainRight, iy + itemH)
                canvas.drawRoundRect(r, dp(6f), dp(6f), pFill)
                drawText(canvas, if (unlocked) "✓" else "○", mainLeft + dp(16f), iy + itemH / 2f,
                    if (unlocked) C_ACCENT else C_DIM, dp(16f), centerX = true)
                drawText(canvas, d.name, mainLeft + dp(34f), iy + itemH / 2f - dp(5f), if (unlocked) C_TEXT else C_DIM, dp(13f))
                drawText(canvas, d.desc, mainLeft + dp(34f), iy + itemH / 2f + dp(15f), C_DIM, dp(11f))
                achItems.add(d to r)
                iy += itemH + dp(4f)
            }
        } else {
            val list = achievements.catalogSubstances()
            if (list.isEmpty()) {
                scrollMax = 0f
                drawText(canvas, "还没有收集任何物质，快去实验吧！", cx, listRect.top + dp(30f), C_DIM, dp(13f), centerX = true)
            } else {
                val itemH = dp(32f)
                scrollMax = (list.size * (itemH + dp(4f)) - listRect.height()).coerceAtLeast(0f)
                var iy = listRect.top - catalogScroll
                for (s in list) {
                    if (iy + itemH < listRect.top) { iy += itemH; continue }
                    if (iy > listRect.bottom) break
                    pFill.color = C_PANEL
                    val r = RectF(mainLeft, iy, mainRight, iy + itemH)
                    canvas.drawRoundRect(r, dp(6f), dp(6f), pFill)
                    Icons.drawSubstanceDot(canvas, mainLeft + dp(16f), iy + itemH / 2f, dp(8f), s)
                    drawText(canvas, s.cn, mainLeft + dp(30f), iy + itemH / 2f, C_TEXT, dp(13f))
                    drawText(canvas, "${s.formula}", mainRight - dp(8f), iy + itemH / 2f, C_DIM, dp(11f), centerX = true)
                    catalogItems.add(s to r)
                    iy += itemH + dp(4f)
                }
            }
        }
        canvas.restore()
        drawText(canvas, "‹ 返回", mainLeft, safeBottom - insetTop, C_DIM, dp(13f))
    }

    private fun drawSettings(canvas: Canvas) {
        val w = availW; val top = safeTop + insetTop
        val cx = safeLeft + w / 2f
        drawText(canvas, "设置", cx, top, C_ACCENT, dp(15f), centerX = true)
        var y = top + dp(22f)
        val rowH = dp(46f)
        val gap = dp(8f)

        var r = RectF(mainLeft, y, mainRight, y + rowH)
        drawSettingRow(canvas, r, "音效", if (soundEnabled) "开" else "关")
        settingsRows.add("sound" to r)
        y += rowH + gap

        r = RectF(mainLeft, y, mainRight, y + rowH)
        drawSettingRow(canvas, r, "重置进度", if (resetArmed) "再点一次" else "重置")
        settingsRows.add("reset" to r)
        y += rowH + gap

        drawText(canvas, "‹ 返回", mainLeft, safeBottom - insetTop, C_DIM, dp(13f))
    }

    private fun drawSettingRow(canvas: Canvas, r: RectF, label: String, value: String) {
        pFill.color = C_PANEL
        canvas.drawRoundRect(r, dp(8f), dp(8f), pFill)
        drawText(canvas, label, r.left + dp(14f), r.centerY(), C_TEXT, dp(14f))
        drawText(canvas, value, r.right - dp(14f), r.centerY(), C_DIM, dp(13f), centerX = true)
    }

    private fun drawAbout(canvas: Canvas) {
        val w = availW; val top = safeTop + insetTop
        val cx = safeLeft + w / 2f
        drawText(canvas, "关于", cx, top, C_ACCENT, dp(15f), centerX = true)

        val listRect = RectF(mainLeft, top + dp(20f), mainRight, safeBottom - insetTop)
        scrollRect = listRect
        canvas.save(); canvas.clipRect(listRect)

        val lines = ArrayList<Triple<String, Int, Float>>()
        lines += Triple("化学实验室", C_ACCENT, dp(20f))
        lines += Triple("", C_DIM, dp(10f))
        lines += Triple("一款运行于安卓与 Wear OS 手表的", C_TEXT, dp(12f))
        lines += Triple("虚拟化学实验模拟器，在方寸之间", C_TEXT, dp(12f))
        lines += Triple("体验加热、混合、溶解与燃烧的乐趣。", C_TEXT, dp(12f))
        lines += Triple("", C_DIM, dp(10f))
        lines += Triple("作者：东山de字幕", C_FLAME, dp(14f))

        val lineH = dp(22f)
        scrollMax = (lines.size * lineH - listRect.height()).coerceAtLeast(0f)
        var y = listRect.top - aboutScroll
        for ((text, color, size) in lines) {
            if (text.isEmpty()) { y += lineH * 0.6f; continue }
            if (y + lineH < listRect.top) { y += lineH; continue }
            if (y > listRect.bottom) break
            drawText(canvas, text, cx, y + lineH / 2f, color, size, centerX = true)
            y += lineH
        }
        canvas.restore()
        drawText(canvas, "‹ 返回", mainLeft, safeBottom - insetTop, C_DIM, dp(13f))
    }

    private fun drawToast(canvas: Canvas, msg: String) {
        val bw = min(availW * 0.9f, dp(300f))
        val toastTop = safeTop + dp(6f)
        val r = RectF(safeLeft + (availW - bw) / 2f, toastTop, safeLeft + (availW + bw) / 2f, toastTop + dp(40f))
        pFill.color = 0xE62A3542.toInt()
        canvas.drawRoundRect(r, dp(12f), dp(12f), pFill)
        drawText(canvas, msg, r.centerX(), r.centerY(), C_ACCENT, dp(13f), centerX = true)
    }

    private fun drawText(canvas: Canvas, s: String, x: Float, cy: Float, color: Int, size: Float, centerX: Boolean = false) {
        pText.color = color
        pText.textSize = size
        pText.textAlign = if (centerX) Paint.Align.CENTER else Paint.Align.LEFT
        val fm = pText.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(s, if (centerX) x else x, baseline, pText)
    }

    // ================= 触摸 =================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y
                scrollStart = currentScroll()
                labBarScrollStart = labBarScrollX
                pickerSubScrollStart = pickerSubScrollX
                dragging = false
                pressedLabel = if (screen == Screen.TITLE || screen == Screen.LAB) findButton(x, y) else null
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = dp(8f)
                if (abs(y - downY) > slop || abs(x - downX) > slop) {
                    dragging = true
                    pressedLabel = null
                }
                if (dragging) {
                    // 操作栏内横向滑动；否则按按下位置纵向滚动列表
                    if (screen == Screen.LAB && labBarRect.contains(downX, downY)) {
                        labBarScrollX = (labBarScrollStart - (x - downX)).coerceIn(0f, labBarMaxScroll)
                    } else if (screen == Screen.PICKER && pickerSubRect.contains(downX, downY)) {
                        pickerSubScrollX = (pickerSubScrollStart - (x - downX)).coerceIn(0f, pickerSubMaxScroll)
                    } else if (scrollRect.contains(downX, downY)) {
                        setScroll(scrollStart + (downY - y))
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) handleTap(x, y)
                pressedLabel = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun currentScroll(): Float = when (screen) {
        Screen.TITLE -> titleScroll
        Screen.LAB -> statusScroll
        Screen.PICKER -> pickerScroll
        Screen.GUIDE -> if (guideTab == 0) achScroll else catalogScroll
        Screen.ABOUT -> aboutScroll
        else -> 0f
    }

    /** 命中按钮时返回其标签，用于按压高亮；未命中返回 null */
    private fun findButton(x: Float, y: Float): String? {
        for (b in buttons) if (b.rect.contains(x, y)) return b.label
        for (b in sideButtons) if (b.rect.contains(x, y)) return b.label
        return null
    }

    private fun setScroll(v: Float) {
        val clamped = v.coerceIn(0f, scrollMax)
        when (screen) {
            Screen.TITLE -> titleScroll = clamped
            Screen.LAB -> statusScroll = clamped
            Screen.PICKER -> pickerScroll = clamped
            Screen.GUIDE -> if (guideTab == 0) achScroll = clamped else catalogScroll = clamped
            Screen.ABOUT -> aboutScroll = clamped
            else -> {}
        }
    }

    private fun handleTap(x: Float, y: Float) {
        when (screen) {
            Screen.TITLE -> { for (b in buttons) if (b.rect.contains(x, y)) { b.action(); invalidate(); return } }
            Screen.LAB -> {
                val backBottom = safeTop + headerH
                if (y < backBottom && x < safeLeft + dp(36f)) { screen = Screen.TITLE; return }
                for (b in sideButtons) if (b.rect.contains(x, y)) { b.action(); invalidate(); return }
                for (b in buttons) if (b.rect.contains(x, y)) { b.action(); invalidate(); return }
            }
            Screen.PICKER -> {
                val backY = safeBottom - insetTop
                if (y > backY) { screen = Screen.LAB; return }
                for ((cat, r) in categoryTabs) if (r.contains(x, y)) {
                    someCat = cat; pickerSubSel = Substances.SUB_ALL
                    pickerSubScrollX = 0f; pickerScroll = 0f; invalidate(); return
                }
                for ((sub, r) in pickerSubTabs) if (r.contains(x, y)) {
                    pickerSubSel = sub; pickerScroll = 0f; invalidate(); return
                }
                for ((s, r) in pickerItems) if (r.contains(x, y)) {
                    game.addSubstance(s.id); screen = Screen.LAB; invalidate(); return
                }
            }
            Screen.GUIDE -> {
                val backY = safeBottom - insetTop
                if (y > backY) { screen = Screen.TITLE; return }
                for ((idx, r) in guideTabs) if (r.contains(x, y)) { guideTab = idx; invalidate(); return }
            }
            Screen.SETTINGS -> {
                val backY = safeBottom - insetTop
                if (y > backY) { screen = Screen.TITLE; return }
                for ((key, r) in settingsRows) if (r.contains(x, y)) {
                    when (key) {
                        "sound" -> {
                            soundEnabled = !soundEnabled
                            context.getSharedPreferences("chemlab", Context.MODE_PRIVATE)
                                .edit().putBoolean("sound_enabled", soundEnabled).apply()
                            showToast(if (soundEnabled) "音效已开启" else "音效已关闭")
                        }
                        "reset" -> {
                            val armed = resetArmed && SystemClock.uptimeMillis() < resetArmedUntil
                            if (armed) {
                                achievements.reset()
                                resetArmed = false
                                showToast("已重置进度")
                            } else {
                                resetArmed = true
                                resetArmedUntil = SystemClock.uptimeMillis() + 3000
                                showToast("再次点击确认重置")
                            }
                        }
                    }
                    invalidate()
                    return
                }
            }
            Screen.ABOUT -> { val backY = safeBottom - insetTop; if (y > backY) { screen = Screen.TITLE; return } }
        }
    }
}