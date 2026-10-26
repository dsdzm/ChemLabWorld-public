package com.chemlab.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.max

/**
 * 示意图标渲染器：用 Canvas 绘制实验器材、内容物、火焰、气泡与物质圆点。
 * 纯矢量绘制，无图片资源，契合低功耗设备。
 */
object Icons {

    /** 动画状态：phase 为 0..1 的循环相位（时间驱动），flash 为反应闪光强度，dissolve 为溶解旋涡强度 */
    data class Fx(val phase: Float, val flash: Float, val dissolve: Float)

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF9FB6C9.toInt()
    }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x165E7C8E
    }
    private val liquid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x88B9E3EE.toInt()
    }
    private val flame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAA8EDBFF.toInt()
    }

    // 装置背景柔光（径向渐变）
    private val glowBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    // 液体渐变与液面高光（独立画笔，避免污染共享 liquid 画笔）
    private val liqGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val liqLip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** 颜色向白色靠拢 amount（0..1）用于玻璃液面渐变的高光端 */
    private fun lift(c: Int, amount: Float): Int {
        val a = (c shr 24) and 0xFF
        val r = ((c shr 16) and 0xFF) + ((255 - ((c shr 16) and 0xFF)) * amount).toInt()
        val g = ((c shr 8) and 0xFF) + ((255 - ((c shr 8) and 0xFF)) * amount).toInt()
        val b = (c and 0xFF) + ((255 - (c and 0xFF)) * amount).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 溶液主色：混合有色溶质，无色则为水的淡蓝 */
    fun liquidColor(c: Container): Int {
        val colored = c.solutes.keys
            .mapNotNull { Substances.get(it).solutionColor }
        if (colored.isEmpty()) return 0x33549FC9.toInt()
        var r = 0; var g = 0; var b = 0
        for (col in colored) {
            r += (col shr 16) and 0xFF
            g += (col shr 8) and 0xFF
            b += col and 0xFF
        }
        val n = colored.size
        val a = 0xAA
        return (a shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** 液面比例 0..1 */
    fun fillFraction(c: Container): Float {
        val soluteVol = c.solutes.values.sum() * 40.0
        val vol = c.waterMl + soluteVol
        return min(1.0f, (vol / 240.0).toFloat()).coerceIn(0f, 1f)
    }

    fun drawContainer(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, c: Container, fx: Fx) {
        // 反应闪光（背景辉光）
        if (fx.flash > 0f) drawFlash(canvas, cx, cy, w, h, fx.flash)
        // 装置下的柔和背光，让玻璃器皿浮现于面板之上
        glowBack.shader = RadialGradient(
            cx, cy, max(w, h) * 0.75f,
            intArrayOf(0x2E4FC3F7.toInt(), 0x004FC3F7.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(cx - w * 0.7f, cy - h * 0.72f, cx + w * 0.7f, cy + h * 0.72f), glowBack)
        when (c.equipment.id) {
            Equipment.BEAKER -> drawBeaker(canvas, cx, cy, w, h, c, fx)
            Equipment.TEST_TUBE -> drawTestTube(canvas, cx, cy, w, h, c, fx)
            Equipment.FLASK -> drawFlask(canvas, cx, cy, w, h, c, fx)
            Equipment.GAS_BOTTLE -> drawGasBottle(canvas, cx, cy, w, h, c, fx)
            Equipment.EVAP_DISH -> drawEvapDish(canvas, cx, cy, w, h, c, fx)
        }
        // 被热连线供热的容器同样显示火焰（isHeating = 自身点火 或 外部供热）
        if (c.isHeating) drawFlame(canvas, cx, cy + h / 2f, fx.phase)
        // 气泡不在这里按整格画：由各容器在自己的内腔里绘制（见 drawBubbles 的 basin 参数），
        // 否则试管、锥形瓶这类细长容器会有一半气泡飘在玻璃外面。
    }

    private fun fillLiquid(canvas: Canvas, basin: RectF, c: Container, fx: Fx) {
        // 干态（没有水、也没有溶质）时液体不画，但底部固体必须照画：
        // 固体绘制在下面，若在这里直接 return，空容器里加入的固体试剂就会完全看不见。
        if (c.waterMl <= 0 && c.solutes.isEmpty()) {
            drawSolids(canvas, basin, c)
            return
        }
        val frac = fillFraction(c)
        if (frac <= 0f) return
        val level = basin.bottom - basin.height() * frac
        val liq = RectF(basin.left + 3f, level, basin.right - 3f, basin.bottom - 2f)
        val base = liquidColor(c)
        // 液体纵向渐变：顶部偏亮、底部更深，模拟玻璃透视与液面受光
        liqGrad.shader = LinearGradient(
            liq.left, liq.top, liq.left, liq.bottom,
            lift(base, 0.30f), base, Shader.TileMode.CLAMP
        )
        liqGrad.color = base
        canvas.drawRect(liq, liqGrad)
        // 液面高光（细亮线），增强器皿质感
        liqLip.color = 0x66FFFFFF
        canvas.drawRect(RectF(liq.left, liq.top, liq.right, liq.top + 1.5f), liqLip)
        // 溶解旋涡（搅拌时）
        if (fx.dissolve > 0f) drawSwirl(canvas, liq, c, fx)
        // 电解气泡（通电时从底部上升）
        if (c.isElectrolyzing) drawElectrolysis(canvas, liq, fx.phase, fx.dissolve)
        // 底部固体
        drawSolids(canvas, basin, c)
    }

    private fun drawSolids(canvas: Canvas, basin: RectF, c: Container) {
        val items = c.solids.entries.take(8)
        if (items.isEmpty()) return
        val dot = basin.width() * 0.09f
        val step = (basin.width() - dot * 2) / 7f
        var i = 0
        for ((id, _) in items) {
            val s = Substances.get(id)
            solid.color = s.solidColor
            val x = basin.left + dot + i * step
            val y = basin.bottom - dot
            canvas.drawOval(RectF(x, y - dot, x + dot, y), solid)
            i++
        }
    }

    /** 反应闪光：向外扩散、渐隐的光圈 */
    private fun drawFlash(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, strength: Float) {
        val rad = (w * (0.5f + (1f - strength) * 0.5f)).coerceIn(w * 0.4f, w * 1.1f)
        flash.color = (0xE6 shl 24) or (((120 + 120 * strength).toInt() and 0xFF) shl 16)
        canvas.drawCircle(cx, cy, rad, flash)
    }

    /** 电解：从液底上升的蓝色小气泡 */
    private fun drawElectrolysis(canvas: Canvas, liq: RectF, phase: Float, dissolve: Float) {
        for (i in 0 until 6) {
            val t = ((phase + i * 0.16f) % 1f)
            val x = liq.left + liq.width() * ((i * 0.37f) % 1f)
            val y = liq.bottom - t * liq.height()
            spark.alpha = ((1f - t) * 200).toInt().coerceIn(40, 200)
            canvas.drawCircle(x, y, 2f + (i % 3) * 1f, spark)
        }
    }

    /** 溶解旋涡：液体中的旋转圆弧与上浮粒子（强度随搅拌衰减） */
    private fun drawSwirl(canvas: Canvas, liq: RectF, c: Container, fx: Fx) {
        liquid.color = (liquidColor(c) and 0x00FFFFFF) or (0x77 shl 24)
        val cyc = liq.centerY() + liq.height() * 0.3f
        val rx = liq.width() * 0.42f
        val ry = liq.height() * 0.22f
        canvas.drawOval(RectF(liq.centerX() - rx, cyc - ry, liq.centerX() + rx, cyc + ry), liquid)
        // 上浮的溶解粒子
        for (i in 0 until 6) {
            val t = ((fx.phase + i * 0.17f) % 1f)
            solid.color = (0xA0 shl 24) or 0xFFFFFF
            val x = liq.centerX() + (liq.width() * 0.3f) * (if ((i % 2) == 0) t - 0.5f else 1f - t - 0.5f)
            val y = liq.bottom - t * liq.height() * 0.6f
            canvas.drawCircle(x, y, 2f, solid)
        }
    }

    /** 气体气泡颗数 */
    private const val BUBBLE_COUNT = 9

    /** 气体气泡基色（含 alpha） */
    private const val BUBBLE_COLOR = 0x88B9E3EE.toInt()

    /**
     * 0..1 的确定性伪随机：同一 seed 恒返回同一值。
     * 用哈希打散粒子，代替固定数组，消除「等距同速」的机械感，且无需保存粒子状态。
     */
    private fun hash01(seed: Int): Float {
        val x = kotlin.math.sin(seed * 12.9898f) * 43758.547f
        return x - kotlin.math.floor(x)
    }

    /**
     * 气体气泡：位置 / 半径 / 上升速度 / 初相位由哈希打散，避免「排队上升」的机械感。
     *
     * 整簇气泡被限制在 [basin]（该容器自己的内腔）之内 —— 试管、锥形瓶的内腔远小于
     * 器材格子，按整格撒气泡会飘到玻璃外面。容器里有液体时进一步限制在液柱内，
     * 液面高度与 [fillLiquid] 的算法保持一致。
     */
    private fun drawBubbles(canvas: Canvas, basin: RectF, c: Container, phase: Float) {
        bubble.color = BUBBLE_COLOR
        val baseAlpha = (BUBBLE_COLOR ushr 24) and 0xFF
        val cavW = basin.width().coerceAtLeast(1f)
        val frac = fillFraction(c).coerceIn(0f, 1f)
        val top = if (frac > 0.02f) basin.bottom - basin.height() * frac else basin.top
        val span = (basin.bottom - top).coerceAtLeast(basin.height() * 0.08f)
        for (i in 0 until BUBBLE_COUNT) {
            val s = i * 5
            val hx = hash01(s + 1)
            val hr = hash01(s + 2)
            val hs = hash01(s + 3)
            val hp = hash01(s + 4)
            val hw = hash01(s + 5)
            val r = (cavW * (0.05f + hr * 0.075f)).coerceAtLeast(1.2f)
            val t = (phase * (0.55f + hs * 0.85f) + hp) % 1f
            val wob = kotlin.math.sin((t * 3f + hp * 5f) * 6.2831855f) * cavW * 0.06f * (0.4f + hw)
            val x = basin.left + cavW * (0.06f + 0.88f * hx) + wob
            val y = basin.bottom - t * span
            bubble.alpha = (baseAlpha * (0.35f + 0.65f * (1f - t))).toInt().coerceIn(30, baseAlpha)
            canvas.drawCircle(x, y, r, bubble)
        }
        bubble.color = BUBBLE_COLOR // 复位共享画笔
    }

    private fun drawFlame(canvas: Canvas, cx: Float, baseY: Float, phase: Float) {
        // 火苗随相位伸缩跳动
        val flick = 1f + 0.18f * (kotlin.math.sin(phase * 3.14f * 2f).toFloat())
        flame.color = 0xCCFF9A2A.toInt()
        canvas.drawOval(RectF(cx - 10f, baseY - 2f, cx + 10f, baseY + 7f), flame)
        val h1 = (12f * flick).coerceAtLeast(8f)
        flame.color = 0xCCFFD23F.toInt()
        canvas.drawOval(RectF(cx - 6f, baseY - h1 - 2f, cx + 6f, baseY + 2f), flame)
        val h2 = (18f * (1f + 0.15f * (1f - phase))).coerceAtLeast(12f)
        flame.color = 0xCC4FC3F7.toInt()
        canvas.drawOval(RectF(cx - 3f, baseY - h2 - 2f, cx + 3f, baseY - 3f), flame)
    }

    private fun drawBeaker(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, c: Container, fx: Fx) {
        val left = cx - w * 0.34f
        val right = cx + w * 0.34f
        val top = cy - h * 0.42f
        val bottom = cy + h * 0.44f
        canvas.drawRect(RectF(left, top, right, top + 4f), glass)          // 顶部沿口
        canvas.drawLine(left, top, left, bottom, outline)                   // 左壁
        canvas.drawLine(right, top, right, bottom, outline)                 // 右壁
        canvas.drawLine(left, bottom, left + w * 0.34f, bottom, outline)    // 底
        canvas.drawLine(right, bottom, right - w * 0.34f, bottom, outline)
        val basin = RectF(left + 2f, top + 4f, right - 2f, bottom)
        fillLiquid(canvas, basin, c, fx)
        if (c.gases.isNotEmpty()) drawBubbles(canvas, basin, c, fx.phase)
        // 刻度
        for (i in 1..3) {
            val gy = bottom - basin.height() * i / 4f
            canvas.drawLine(left - 6f, gy, left, gy, outline)
        }
    }

    private fun drawTestTube(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, c: Container, fx: Fx) {
        val rw = w * 0.16f
        val left = cx - rw
        val right = cx + rw
        val top = cy - h * 0.42f
        val bottom = cy + h * 0.44f
        val body = RectF(left, top, right, bottom)
        canvas.drawRoundRect(body, rw, rw, glass)
        canvas.drawRoundRect(body, rw, rw, outline)
        val basin = RectF(left + 2f, top + rw, right - 2f, bottom - 2f)
        fillLiquid(canvas, basin, c, fx)
        if (c.gases.isNotEmpty()) drawBubbles(canvas, basin, c, fx.phase)
    }

    private fun drawFlask(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, c: Container, fx: Fx) {
        val top = cy - h * 0.42f
        val bottom = cy + h * 0.44f
        val neckTop = top
        val neckBottom = top + h * 0.16f
        val neckW = w * 0.14f
        val bodyTop = neckBottom
        val bodyW = w * 0.40f
        // 瓶身（梯形近似）
        val path = android.graphics.Path().apply {
            moveTo(cx - neckW, neckTop)
            lineTo(cx + neckW, neckTop)
            lineTo(cx + neckW, neckBottom)
            lineTo(cx + bodyW, bodyTop)
            lineTo(cx + bodyW, bottom)
            lineTo(cx - bodyW, bottom)
            lineTo(cx - bodyW, bodyTop)
            close()
        }
        canvas.drawPath(path, glass)
        canvas.drawPath(path, outline)
        val basin = RectF(cx - bodyW + 3f, bodyTop + 2f, cx + bodyW - 3f, bottom - 2f)
        fillLiquid(canvas, basin, c, fx)
        if (c.gases.isNotEmpty()) drawBubbles(canvas, basin, c, fx.phase)
    }

    private fun drawGasBottle(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, c: Container, fx: Fx) {
        val top = cy - h * 0.40f
        val bottom = cy + h * 0.46f
        val bodyW = w * 0.34f
        val body = RectF(cx - bodyW, top + h * 0.06f, cx + bodyW, bottom)
        canvas.drawRoundRect(body, 6f, 6f, glass)
        canvas.drawRoundRect(body, 6f, 6f, outline)
        // 瓶口
        canvas.drawLine(cx - w * 0.08f, top, cx - w * 0.08f, top + h * 0.06f, outline)
        canvas.drawLine(cx + w * 0.08f, top, cx + w * 0.08f, top + h * 0.06f, outline)
        canvas.drawLine(cx - w * 0.10f, top, cx + w * 0.10f, top, outline)
        // 收集到气体时给瓶内淡淡上色（用后恢复玻璃色，避免污染其它器材绘制）
        if (c.gases.isNotEmpty()) {
            val g = Substances.get(c.gases.keys.first())
            val tint = g.solidColor
            val prev = glass.color
            glass.color = (tint and 0x00FFFFFF) or 0x44000000.toInt()
            canvas.drawRoundRect(RectF(cx - bodyW + 4f, top + h * 0.08f, cx + bodyW - 4f, bottom - 4f), 4f, 4f, glass)
            glass.color = prev
        }
        // 集气瓶同样可以盛放固体试剂，底部固体照常绘制
        drawSolids(canvas, RectF(cx - bodyW + 4f, top + h * 0.08f, cx + bodyW - 4f, bottom - 4f), c)
        if (c.gases.isNotEmpty()) {
            drawBubbles(canvas, RectF(cx - bodyW + 6f, top + h * 0.10f, cx + bodyW - 6f, bottom - 6f), c, fx.phase)
        }
    }

    private fun drawEvapDish(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, c: Container, fx: Fx) {
        val rw = w * 0.40f
        val ry = h * 0.26f
        val top = cy - 0f
        val body = RectF(cx - rw, top - ry, cx + rw, top + ry)
        canvas.drawOval(body, glass)
        canvas.drawOval(body, outline)
        // 蒸发皿同理：干态（无液体）时也要把皿底的固体画出来
        val dish = RectF(cx - rw + 4f, top - ry + 4f, cx + rw - 4f, top + ry - 2f)
        if (fillFraction(c) > 0f) {
            val f = fillFraction(c)
            val liq = RectF(cx - rw + 3f, top - ry * f * 0.7f, cx + rw - 3f, top + ry * 0.6f)
            liquid.color = liquidColor(c)
            canvas.drawOval(liq, liquid)
        }
        drawSolids(canvas, dish, c)
        if (c.gases.isNotEmpty()) drawBubbles(canvas, dish, c, fx.phase)
    }

    /** 物质图标（用于选择面板与图鉴）：按相态绘制成小容器造型 */
    fun drawSubstanceDot(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Substance) {
        when (s.phase) {
            Phase.GAS -> {
                // 气泡：内含一圈表示气体
                bubble.color = s.solidColor
                canvas.drawCircle(cx, cy, r, bubble)
                outline.color = 0x66FFFFFF
                outline.strokeWidth = 2f
                canvas.drawCircle(cx, cy, r + 2f, outline)
                outline.strokeWidth = 2f
                canvas.drawCircle(cx, cy, r * 0.45f, outline)
            }
            Phase.AQUEOUS -> {
                // 小烧杯 + 有色溶液
                val w = r * 2.2f; val h = r * 2.4f
                val left = cx - w / 2f; val right = cx + w / 2f
                val top = cy - h / 2f; val bottom = cy + h / 2f
                val col = s.solutionColor ?: C.COLORLESS
                liquid.color = col
                canvas.drawRect(RectF(left + 1f, cy, right - 1f, bottom - 1f), liquid)
                glass.color = 0x2E9FB6C9.toInt()
                canvas.drawRoundRect(RectF(left, top, right, bottom), 2f, 2f, glass)
                outline.color = 0x66FFFFFF
                outline.strokeWidth = 2f
                canvas.drawRoundRect(RectF(left, top, right, bottom), 2f, 2f, outline)
                outline.strokeWidth = 2f
                canvas.drawLine(left, cy - 1f, right, cy - 1f, outline) // 液面线
            }
            Phase.LIQUID -> {
                // 液滴
                liquid.color = s.solidColor
                canvas.drawOval(RectF(cx - r, cy - r * 0.5f, cx + r, cy + r * 1.4f), liquid)
                canvas.drawCircle(cx, cy - r * 0.6f, r * 0.45f, liquid)
            }
            else -> {
                // 晶体：六边形棱晶
                val col = s.solidColor
                solid.color = col
                outline.color = 0x66FFFFFF
                val path = android.graphics.Path()
                path.moveTo(cx, cy - r)
                path.lineTo(cx + r * 0.95f, cy - r * 0.35f)
                path.lineTo(cx + r * 0.55f, cy + r * 0.9f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.9f)
                path.lineTo(cx - r * 0.95f, cy - r * 0.35f)
                path.close()
                outline.strokeWidth = 2f
                canvas.drawPath(path, solid)
                canvas.drawPath(path, outline)
            }
        }
        // 恢复共享画笔默认状态，避免污染后续器材/容器绘制
        outline.strokeWidth = 3f
        glass.color = 0x165E7C8E.toInt()
        liquid.color = 0
        solid.color = 0
        bubble.color = BUBBLE_COLOR
    }
}