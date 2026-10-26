package com.chemlab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chemlab.game.Container
import com.chemlab.game.Equipment
import com.chemlab.game.Substances
import com.chemlab.world.Apparatus
import com.chemlab.world.PortKind
import kotlin.math.min
import kotlin.math.roundToInt

// ==================== 尺寸常量（dp） ====================

/** 器材的绘制/触摸区域边长；>= 80dp 满足小屏最小触摸区域要求 */
const val APPARATUS_SIZE_DP = 88f

/** 端口视觉直径 */
const val PORT_VISUAL_DP = 16f

/** 端口触摸命中区边长（48dp ≈ 9mm，符合可点击控件最小尺寸） */
const val PORT_HIT_DP = 48f

/** 磁吸拖拽的吸附半径 */
const val PORT_MAGNET_DP = 60f

/** 磁吸命中时端口放大到的直径 */
const val PORT_MAGNET_GROW_DP = 32f

/** 连线中点断开热区边长 */
const val LINK_HIT_DP = 24f

/** 导管线宽相对器材边长的比例 */
private const val TUBE_WIDTH_RATIO = 0.055f

/**
 * 酒精灯吸附到反应容器下方时的中心纵向偏移（dp）。
 * 取 0.6 倍器材边长，让灯焰恰好贴在容器底部。
 */
const val HEAT_UNIT_OFFSET_DP = 52f

/**
 * 漏斗插到容器口上时的中心纵向偏移（dp）。
 * 取 0.64 倍器材边长，让漏斗的下管伸进容器口内一段。
 */
const val FUNNEL_UNIT_OFFSET_DP = 56f

/**
 * 端口相对器材中心的位置（单位：dp 偏移）。
 *
 * 布局约束：同一器材上任意两个端口的中心距都必须 > [PORT_HIT_DP]（48dp），
 * 否则触摸命中区会互相重叠，小屏上必然误触。
 * 取 half = [APPARATUS_SIZE_DP] / 2 = 44dp 校验：
 *  · 上下端口间距 = 2 × 0.95 × 44 = 83.6dp ✓
 *  · 顶部端口 → 右侧端口 = √(41.8² + 46.2²) ≈ 62.3dp ✓（试管/锥形瓶）
 *  · 底部端口 → 右侧端口 = √(41.8² + 37.4²) ≈ 56.1dp ✓
 */
fun portLocalOffsetDp(portId: String, halfDp: Float = APPARATUS_SIZE_DP / 2f): Offset = when (portId) {
    // 顶部：进料口 / 瓶口
    "top", "mouth", "gas_in", "liquid_in" -> Offset(0f, -0.95f * halfDp)
    // 底部：加热位 / 出液口
    "heat_in", "heat_out", "liquid_out" -> Offset(0f, 0.95f * halfDp)
    // 右侧：出气口
    "gas_out" -> Offset(0.95f * halfDp, 0.10f * halfDp)
    // 右侧：排水口
    "water_out" -> Offset(0.95f * halfDp, 0.25f * halfDp)
    // 左右：电极
    "elec_in" -> Offset(-0.95f * halfDp, 0f)
    "elec_out" -> Offset(0.95f * halfDp, 0f)
    else -> Offset(0f, 0f)
}

/**
 * 端口在器材「实体」上的接头位置（相对器材中心的 dp 偏移）。
 *
 * 与 [portLocalOffsetDp] 分工不同：后者是触摸命中用的抽象端口点 —— 为了满足
 * 48dp 最小命中区，一律贴在器材格子边缘，于是试管这类细长器材的端口点会落在
 * 玻璃外面的空气里；这里给出的是真正该长橡胶塞、该接管子的地方（试管口、瓶口、
 * 侧壁出气口）。两者分开，才能既点得准、又接得像真的。
 *
 * 未列出的器材退回 [portLocalOffsetDp]，即接头与端口点重合。
 */
fun portJointLocalDp(
    equipmentId: String,
    portId: String,
    halfDp: Float = APPARATUS_SIZE_DP / 2f
): Offset {
    // 比例以器材格子边长（= 2 × halfDp）为基准：0.5f 表示正中
    fun f(fx: Float, fy: Float) = Offset((fx - 0.5f) * halfDp * 2f, (fy - 0.5f) * halfDp * 2f)
    return when (equipmentId) {
        // 试管：玻璃体 x 0.36~0.64，y 0.14~0.88
        Equipment.TEST_TUBE -> when (portId) {
            "mouth" -> f(0.50f, 0.15f)
            "gas_out" -> f(0.63f, 0.27f)
            "heat_in" -> f(0.50f, 0.86f)
            "elec_in" -> f(0.37f, 0.62f)
            else -> portLocalOffsetDp(portId, halfDp)
        }
        // 锥形瓶：颈部 x 0.42~0.58（y 0.14~0.46），瓶身最宽 0.22~0.78
        Equipment.FLASK -> when (portId) {
            "mouth" -> f(0.50f, 0.15f)
            "gas_out" -> f(0.57f, 0.30f)
            "heat_in" -> f(0.50f, 0.87f)
            "elec_in" -> f(0.30f, 0.72f)
            else -> portLocalOffsetDp(portId, halfDp)
        }
        // 烧杯：杯身 x 0.24~0.76，y 0.24~0.86
        Equipment.BEAKER -> when (portId) {
            "top" -> f(0.50f, 0.25f)
            "heat_in" -> f(0.50f, 0.86f)
            "elec_in" -> f(0.25f, 0.60f)
            else -> portLocalOffsetDp(portId, halfDp)
        }
        // 集气瓶：瓶身 x 0.26~0.74，y 0.26~0.86
        Equipment.GAS_COLLECTOR -> when (portId) {
            "gas_in" -> f(0.50f, 0.27f)
            "water_out" -> f(0.73f, 0.60f)
            else -> portLocalOffsetDp(portId, halfDp)
        }
        // 漏斗：斗口 y 0.24，细管 x 0.45~0.55
        Equipment.FUNNEL -> when (portId) {
            "liquid_in" -> f(0.50f, 0.25f)
            "liquid_out" -> f(0.50f, 0.86f)
            else -> portLocalOffsetDp(portId, halfDp)
        }
        // 电源：正极接线柱在 (0.62, 0.42)，与 drawBattery 的绘制一致
        Equipment.BATTERY -> when (portId) {
            "elec_out" -> f(0.62f, 0.42f)
            else -> portLocalOffsetDp(portId, halfDp)
        }
        else -> portLocalOffsetDp(portId, halfDp)
    }
}

/**
 * 容器内腔（可容纳气泡的区域）相对器材边长的比例：left, top, right, bottom。
 *
 * 气泡必须限制在这里面：早先按「整个格子」撒气泡，试管、锥形瓶这类细长器材
 * 会有大量气泡飘在玻璃外面。
 */
private fun cavityFraction(e: Equipment): FloatArray = when (e.id) {
    Equipment.TEST_TUBE -> floatArrayOf(0.38f, 0.19f, 0.62f, 0.82f)
    Equipment.FLASK -> floatArrayOf(0.30f, 0.52f, 0.70f, 0.84f)
    Equipment.BEAKER -> floatArrayOf(0.27f, 0.28f, 0.73f, 0.84f)
    Equipment.GAS_COLLECTOR -> floatArrayOf(0.29f, 0.30f, 0.71f, 0.84f)
    Equipment.FUNNEL -> floatArrayOf(0.46f, 0.60f, 0.54f, 0.84f)
    else -> floatArrayOf(0.28f, 0.28f, 0.72f, 0.84f)
}

// ==================== 相位驱动的动效（沿用旧版方案） ====================

/**
 * 器材的动态部分：火焰 / 气体气泡 / 电解气泡 / 温度计。
 *
 * 与旧版 [com.chemlab.game.Icons] 的做法完全一致 —— 每颗粒子的位置是相位 [phase]
 * 的纯函数，不保存粒子状态、每帧不新建对象，也不回写模型。
 *
 * 上一版把气泡放进对象池并每 tick 标脏重绘整张器材位图，等于每秒 10 次「重新分配
 * 位图 + 全量重绘」，低端机上必然卡顿。现在拆成两层：
 *  · 静态内容（玻璃体/液体/沉淀/选中框）进离屏缓存，只在内容变化时重建；
 *  · 动态部分在这里每帧直接画，开销只有几个 drawCircle/drawOval。
 *
 * @param cx,cy 器材中心（屏幕坐标）
 * @param size  器材在屏幕上的边长（已含画布缩放）
 * @param phase 0→1 的循环相位
 */
fun DrawScope.drawApparatusEffects(a: Apparatus, cx: Float, cy: Float, size: Float, phase: Float) {
    val c = a.container
    if (c.isHeating) {
        // 酒精灯的火焰在灯芯处，其余器材的火焰在器底
        if (a.equipment.id == Equipment.ALCOHOL_LAMP) {
            drawFlameLive(cx, cy - size * 0.10f, size * 0.10f, phase)
        } else {
            drawFlameLive(cx, cy + size * 0.44f, size * 0.09f, phase)
        }
    }
    if (c.isElectrolyzing) drawElectrolysisLive(cx, cy, size, phase)
    if (c.gases.isNotEmpty()) drawBubblesLive(a, cx, cy, size, phase)
    drawThermometerLive(c, cx, cy, size)
}

/** 气体气泡颗数 */
private const val BUBBLE_COUNT = 9

/** 电解气泡颗数 */
private const val ELECTRO_BUBBLE_COUNT = 8

/**
 * 0..1 的确定性伪随机：同一 seed 恒返回同一值。
 *
 * 用哈希代替固定数组来打散粒子，既能消除「等距、同速、同步」的机械感，
 * 又不需要保存任何粒子状态 —— 位置依旧是相位 [phase] 的纯函数，
 * 满足低配机「不分配对象、不存粒子」的约束。
 */
private fun hash01(seed: Int): Float {
    val x = kotlin.math.sin(seed * 12.9898f) * 43758.547f
    return x - kotlin.math.floor(x)
}

/**
 * 气体气泡：颗粒数、横向位置、半径、上升速度、初相位全部由哈希打散。
 *
 * 关键约束：整簇气泡被限制在 [cavityFraction] 给出的容器内腔里 —— 试管、锥形瓶
 * 这类细长器材的内腔远小于器材格子，早先按整格撒气泡会让大量气泡飘在玻璃外面。
 * 容器里有液体时还进一步限制在液柱内（液面高度与 drawGlass 的算法保持一致）。
 */
private fun DrawScope.drawBubblesLive(a: Apparatus, cx: Float, cy: Float, size: Float, phase: Float) {
    val cav = cavityFraction(a.equipment)
    val left = cx + (cav[0] - 0.5f) * size
    val right = cx + (cav[2] - 0.5f) * size
    val cavBottom = cy + (cav[3] - 0.5f) * size
    val level = (a.container.waterMl / 120.0).coerceIn(0.0, 1.0).toFloat()
    // 有液体 → 从腔底冒到液面；干态 → 在整个内腔里飘
    val cavTop = if (level > 0.02f) {
        maxOf(cy + (0.86f - 0.58f * level - 0.5f) * size, cy + (cav[1] - 0.5f) * size)
    } else {
        cy + (cav[1] - 0.5f) * size
    }
    val cavW = (right - left).coerceAtLeast(size * 0.06f)
    val span = (cavBottom - cavTop).coerceAtLeast(size * 0.05f)
    for (i in 0 until BUBBLE_COUNT) {
        val s = i * 5
        val hx = hash01(s + 1)
        val hr = hash01(s + 2)
        val hs = hash01(s + 3)
        val hp = hash01(s + 4)
        val hw = hash01(s + 5)
        // 半径随内腔宽度缩放：细管里自然冒小泡，烧杯里冒大泡
        val r = cavW * (0.06f + hr * 0.09f)
        val t = (phase * (0.55f + hs * 0.85f) + hp) % 1f
        val wob = kotlin.math.sin((t * 3f + hp * 5f) * 6.2831855f) * cavW * 0.08f * (0.4f + hw)
        val x = left + cavW * hx + wob
        val y = cavBottom - t * span
        drawCircle(
            color = Color(0xAAEAF6FB).copy(alpha = 0.35f + 0.65f * (1f - t)),
            radius = r,
            center = Offset(x, y)
        )
    }
}

/** 电解气泡：蓝色小点自液底上升，位置 / 半径 / 速度同样由哈希打散 */
private fun DrawScope.drawElectrolysisLive(cx: Float, cy: Float, size: Float, phase: Float) {
    val top = cy - size * 0.10f
    val bottom = cy + size * 0.36f
    for (i in 0 until ELECTRO_BUBBLE_COUNT) {
        val s = i * 5 + 101
        val hx = hash01(s + 1)
        val hr = hash01(s + 2)
        val hs = hash01(s + 3)
        val hp = hash01(s + 4)
        val t = (phase * (0.65f + hs * 0.90f) + hp) % 1f
        val x = cx + size * (hx * 0.52f - 0.26f)
        val y = bottom - t * (bottom - top)
        drawCircle(
            color = Color(0xFF4FC3F7).copy(alpha = (1f - t).coerceIn(0.12f, 0.85f)),
            radius = size * (0.016f + hr * 0.012f),
            center = Offset(x, y)
        )
    }
}

/** 火焰：外焰橙 + 内焰黄 + 焰心蓝，随相位轻微跳动 */
private fun DrawScope.drawFlameLive(cx: Float, baseY: Float, r: Float, phase: Float) {
    val flick = 1f + 0.18f * kotlin.math.sin(phase * 6.2831855f)
    drawOval(
        color = Color(0xCCFF9A2A),
        topLeft = Offset(cx - r, baseY - r * 0.3f),
        size = Size(r * 2f, r * 1.2f)
    )
    val h1 = r * 2.2f * flick
    drawOval(
        color = Color(0xCCFFD23F),
        topLeft = Offset(cx - r * 0.62f, baseY - h1),
        size = Size(r * 1.24f, h1)
    )
    val h2 = r * 3.2f * (1f + 0.15f * (1f - phase))
    drawOval(
        color = Color(0xCC4FC3F7),
        topLeft = Offset(cx - r * 0.32f, baseY - h2),
        size = Size(r * 0.64f, h2)
    )
}

/** 温度计：25℃～800℃ 满量程，颜色随区间变化 */
private fun DrawScope.drawThermometerLive(c: Container, cx: Float, cy: Float, size: Float) {
    val x = cx + size * 0.40f
    val top = cy - size * 0.26f
    val bottom = cy + size * 0.34f
    drawRoundRect(
        color = Color(0x33FFFFFF),
        topLeft = Offset(x - size * 0.028f, top),
        size = Size(size * 0.056f, bottom - top),
        cornerRadius = CornerRadius(size * 0.028f, size * 0.028f)
    )
    val frac = ((c.temperature - 25.0) / 775.0).coerceIn(0.0, 1.0).toFloat()
    val col = when {
        c.temperature >= 300 -> Color(0xFFFF5A3C)
        c.temperature >= 100 -> Color(0xFFFFB300)
        else -> Color(0xFF4FC3F7)
    }
    if (frac > 0.002f) {
        val merTop = bottom - (bottom - top) * frac
        drawRoundRect(
            color = col,
            topLeft = Offset(x - size * 0.020f, merTop),
            size = Size(size * 0.040f, bottom - merTop),
            cornerRadius = CornerRadius(size * 0.020f, size * 0.020f)
        )
    }
    drawCircle(color = col, radius = size * 0.045f, center = Offset(x, bottom))
}

// ==================== 器材离屏缓存（脏标记落点） ====================

/**
 * 单件器材的离屏位图缓存。
 *
 * 只缓存「结构 + 内容」：玻璃体、液体、沉淀、选中框。
 * 温度计 / 火焰 / 气泡一律画在实时层，所以升温、加热、冒泡、电解都不会把缓存打脏 ——
 * 10Hz 主循环下绝大多数帧只是一次 drawImage。
 */
class ApparatusCache {
    var bitmap: ImageBitmap? = null
    var revision: Long = Long.MIN_VALUE
    var selected: Boolean = false
    /** 已接线端口的位掩码（第 i 位对应 Apparatus.ports[i]）—— 与前两者一样参与缓存失效判定 */
    var wiredMask: Int = 0

    /** 复用的离屏绘制作用域，避免每次重建位图都新建一个对象 */
    val drawScope = CanvasDrawScope()
}

/**
 * 把一件器材的静态内容贴到画布上（命中缓存时只是一次 drawImage）。
 *
 * 位置由调用方传入，而不是读 [Apparatus.x]/[Apparatus.y] —— 这样长按拖动时
 * 示意图与手指位置天然同步。
 *
 * @param cellPx  缓存位图边长（density px，与画布缩放无关）
 * @param topLeft 目标左上角（屏幕坐标）
 * @param dstSize 目标边长（屏幕 px，含画布缩放）
 */
fun DrawScope.drawApparatus(
    a: Apparatus,
    cache: ApparatusCache,
    cellPx: Int,
    selected: Boolean,
    wiredMask: Int,
    topLeft: Offset,
    dstSize: Float
) {
    val needRedraw = cache.bitmap == null || cache.revision != a.revision ||
        cache.selected != selected || cache.wiredMask != wiredMask
    if (needRedraw) {
        // 重建时重新分配位图：跨驱动兼容性最好，不会因 BlendMode.Clear 语义差异留下残影；
        // 重建只发生在内容真的变化时，非每帧，开销可控。
        val bmp = ImageBitmap(cellPx, cellPx)
        val size = Size(cellPx.toFloat(), cellPx.toFloat())
        cache.drawScope.draw(this, this.layoutDirection, ComposeCanvas(bmp), size) {
            drawApparatusContent(a, size, selected, wiredMask)
        }
        cache.bitmap = bmp
        cache.revision = a.revision
        cache.selected = selected
        cache.wiredMask = wiredMask
    }
    val bmp = cache.bitmap ?: return
    val d = dstSize.roundToInt().coerceAtLeast(1)
    drawImage(
        image = bmp,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(cellPx, cellPx),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(d, d),
        filterQuality = FilterQuality.Low
    )
}

/** 主溶质→水溶液色，否则主固体色，用于液体/沉淀着色 */
private fun liquidColor(c: Container): Color {
    c.solutes.entries.maxByOrNull { it.value }?.let { (id, _) ->
        Substances.get(id).solutionColor?.let { return Color(it) }
    }
    return Color(0x99B9E3EE)
}

/** 主固体颜色，用于沉淀层 */
private fun precipitateColor(c: Container): Color {
    val top = c.solids.entries.maxByOrNull { it.value } ?: return Color(0xFF6E6E6E)
    return Color(Substances.get(top.key).solidColor)
}

private fun DrawScope.drawApparatusContent(
    a: Apparatus,
    s: Size,
    selected: Boolean,
    wiredMask: Int
) {
    val w = s.width
    val h = s.height

    if (selected) {
        val corner = CornerRadius(w * 0.14f, w * 0.14f)
        drawRoundRect(
            color = Color(0x332196F3),
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.88f),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Color(0xFF2196F3),
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.88f),
            cornerRadius = corner,
            style = Stroke(width = w * 0.02f)
        )
    }

    when (a.equipment.id) {
        Equipment.BEAKER -> drawGlass(a, s, beakerPath(s))
        Equipment.TEST_TUBE -> drawGlass(a, s, testTubePath(s))
        Equipment.FLASK -> drawGlass(a, s, flaskPath(s))
        Equipment.GAS_COLLECTOR -> drawGlass(a, s, gasCollectorPath(s))
        Equipment.FUNNEL -> drawLiquidFunnel(a, s, funnelPath(s))
        Equipment.ALCOHOL_LAMP -> drawLamp(s)
        Equipment.BATTERY -> drawBattery(a, s)
        else -> drawGlass(a, s, beakerPath(s))
    }
    // 已接线端口的接头装置（橡胶塞 / 容器内电极）画在容器实体上
    drawPortFittings(a, s, wiredMask)
    // 温度计 / 火焰 / 气泡属于每帧变化的动态部分，见 drawApparatusEffects
}

/**
 * 已接线端口的接头装置：气体/液体端口补画橡胶塞，电学端口在容器内画电极。
 *
 * 画在容器自己的位图里（而不是连线层）：接头因此「长在容器上」，
 * 连线只负责从接头走到对面接头 —— 这才像真实实验里塞上塞子、插上导管的样子。
 */
private fun DrawScope.drawPortFittings(a: Apparatus, s: Size, wiredMask: Int) {
    val cell = s.width
    val half = cell / 2f
    var hasElectrode = false
    a.ports.forEachIndexed { i, p ->
        if (wiredMask and (1 shl i) == 0) return@forEachIndexed
        // 接头位置取容器实体上的真实开口，不是贴在格子边缘的触摸端口点
        val joint = portJointLocalDp(a.equipment.id, p.id, half)
        val at = Offset(half + joint.x, half + joint.y)
        // 朝向仍取端口法向（格子坐标）：塞子沿器口朝外塞入
        val dir = portLocalOffsetDp(p.id, half)
        val len = dir.getDistance()
        val out = if (len < 1e-4f) Offset(0f, -1f) else dir / len
        when (p.kind) {
            // 只有玻璃容器的器口才塞橡胶塞：漏斗的下管口是倒液口，电源的接线柱更不该有塞子
            PortKind.GAS, PortKind.LIQUID -> if (hasStopper(a.equipment)) drawStopper(at, out, cell)
            PortKind.ELECTRIC -> hasElectrode = true
            // 受热口在器底，被灯焰遮住，不需要接头装置
            PortKind.HEAT -> Unit
        }
    }
    if (hasElectrode) drawElectrodesInVessel(a.equipment, s)
}

/** 该器材的器口是否该长橡胶塞（玻璃容器：需要塞紧后插导管） */
private fun hasStopper(e: Equipment): Boolean = when (e.id) {
    Equipment.BEAKER, Equipment.TEST_TUBE, Equipment.FLASK,
    Equipment.GAS_COLLECTOR, Equipment.GAS_BOTTLE, Equipment.EVAP_DISH -> true
    else -> false
}

/**
 * 容器内的简易电极：两根石墨棒插在液面以下，顶端带金属接线夹。
 *
 * 位置按 [cavityFraction] 的内腔推算而不是写死的比例 —— 锥形瓶的瓶颈比瓶身窄得多，
 * 固定比例会让电极从瓶颈里穿出去。画在液体之上，读作「插进容器里的电极」。
 */
private fun DrawScope.drawElectrodesInVessel(e: Equipment, s: Size) {
    val cell = s.width
    val cav = cavityFraction(e)
    val cavW = cav[2] - cav[0]
    // 两根电极贴内腔两侧，纵向从腔顶到腔底稍上
    val top = cav[1] * cell
    val bottom = (cav[3] - 0.03f) * cell
    val rodW = (cavW * 0.12f).coerceAtMost(cell * 0.06f)
    for (fx in floatArrayOf(cav[0] + cavW * 0.07f, cav[2] - cavW * 0.07f)) {
        val cx = fx * cell
        drawRoundRect(
            color = Color(0xFF3C444C),
            topLeft = Offset(cx - rodW / 2f, top),
            size = Size(rodW, bottom - top),
            cornerRadius = CornerRadius(rodW / 2f, rodW / 2f)
        )
        // 顶端接线夹
        drawRoundRect(
            color = Color(0xFFB0BEC5),
            topLeft = Offset(cx - rodW * 0.7f, top),
            size = Size(rodW * 1.4f, (bottom - top) * 0.13f),
            cornerRadius = CornerRadius(rodW * 0.4f, rodW * 0.4f)
        )
    }
}

// ---------- 器材外形路径 ----------

private fun beakerPath(s: Size): Path = Path().apply {
    val w = s.width; val h = s.height
    moveTo(w * 0.28f, h * 0.24f)
    lineTo(w * 0.24f, h * 0.86f)
    lineTo(w * 0.76f, h * 0.86f)
    lineTo(w * 0.72f, h * 0.24f)
}

private fun testTubePath(s: Size): Path = Path().apply {
    val w = s.width; val h = s.height
    moveTo(w * 0.36f, h * 0.14f)
    lineTo(w * 0.36f, h * 0.74f)
    quadraticBezierTo(w * 0.36f, h * 0.88f, w * 0.50f, h * 0.88f)
    quadraticBezierTo(w * 0.64f, h * 0.88f, w * 0.64f, h * 0.74f)
    lineTo(w * 0.64f, h * 0.14f)
}

private fun flaskPath(s: Size): Path = Path().apply {
    val w = s.width; val h = s.height
    moveTo(w * 0.42f, h * 0.14f)
    lineTo(w * 0.40f, h * 0.46f)
    lineTo(w * 0.22f, h * 0.82f)
    quadraticBezierTo(w * 0.20f, h * 0.88f, w * 0.28f, h * 0.88f)
    lineTo(w * 0.72f, h * 0.88f)
    quadraticBezierTo(w * 0.80f, h * 0.88f, w * 0.78f, h * 0.82f)
    lineTo(w * 0.60f, h * 0.46f)
    lineTo(w * 0.58f, h * 0.14f)
}

private fun gasCollectorPath(s: Size): Path = Path().apply {
    val w = s.width; val h = s.height
    moveTo(w * 0.26f, h * 0.26f)
    lineTo(w * 0.26f, h * 0.86f)
    lineTo(w * 0.74f, h * 0.86f)
    lineTo(w * 0.74f, h * 0.26f)
    close()
}

private fun funnelPath(s: Size): Path = Path().apply {
    val w = s.width; val h = s.height
    moveTo(w * 0.20f, h * 0.24f)
    lineTo(w * 0.80f, h * 0.24f)
    lineTo(w * 0.55f, h * 0.58f)
    lineTo(w * 0.55f, h * 0.86f)
    lineTo(w * 0.45f, h * 0.86f)
    lineTo(w * 0.45f, h * 0.58f)
    close()
}

// ---------- 各类器材的绘制 ----------

/**
 * 通用玻璃容器：玻璃体 + 液体（按水量映射液面）+ 沉淀 + 描边高光。
 * 溶液颜色取主溶质色，沉淀取主固体色。
 */
private fun DrawScope.drawGlass(a: Apparatus, s: Size, body: Path) {
    val w = s.width
    val h = s.height
    val c = a.container

    drawPath(body, color = Color(0x2A8FC3D6))

    val level = (c.waterMl / 120.0).coerceIn(0.0, 1.0).toFloat()
    if (level > 0.001f) {
        val top = h * (0.86f - 0.58f * level)
        clipPath(body) {
            drawRect(
                color = liquidColor(c).copy(alpha = 0.78f),
                topLeft = Offset(0f, top),
                size = Size(w, h - top)
            )
        }
    }

    if (c.solids.isNotEmpty()) {
        val bottom = h * 0.86f
        val thick = h * min(0.10f, 0.03f + c.solids.values.sum().toFloat() * 0.02f)
        clipPath(body) {
            drawRect(
                color = precipitateColor(c),
                topLeft = Offset(w * 0.20f, bottom - thick),
                size = Size(w * 0.60f, thick)
            )
        }
    }

    drawPath(body, color = Color(0xFFB7D8E6), style = Stroke(width = w * 0.028f))
    drawPath(body, color = Color(0x66FFFFFF), style = Stroke(width = w * 0.012f))
}

/** 漏斗：只显示残留液体，不画液面柱 */
private fun DrawScope.drawLiquidFunnel(a: Apparatus, s: Size, body: Path) {
    val w = s.width
    val c = a.container
    drawPath(body, color = Color(0x2A8FC3D6))
    if (c.waterMl > 0.5) {
        clipPath(body) {
            drawRect(
                color = liquidColor(c).copy(alpha = 0.75f),
                topLeft = Offset(w * 0.30f, s.height * 0.44f),
                size = Size(w * 0.40f, s.height * 0.16f)
            )
        }
    }
    drawPath(body, color = Color(0xFFB7D8E6), style = Stroke(width = w * 0.028f))
}

/** 酒精灯：灯体 + 灯芯 + 点燃后的火焰 */
private fun DrawScope.drawLamp(s: Size) {
    val w = s.width
    val h = s.height
    val cx = w / 2f
    val body = Path().apply {
        moveTo(w * 0.28f, h * 0.62f)
        lineTo(w * 0.22f, h * 0.88f)
        lineTo(w * 0.78f, h * 0.88f)
        lineTo(w * 0.72f, h * 0.62f)
        close()
    }
    drawPath(body, color = Color(0x33607D8B))
    drawPath(body, color = Color(0xFF8FB2C4), style = Stroke(width = w * 0.028f))
    drawRoundRect(
        color = Color(0xFF6E8A99),
        topLeft = Offset(cx - w * 0.10f, h * 0.50f),
        size = Size(w * 0.20f, h * 0.13f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
    )
    drawLine(
        color = Color(0xFF3A3A3A),
        start = Offset(cx, h * 0.50f),
        end = Offset(cx, h * 0.40f),
        strokeWidth = w * 0.03f
    )
    // 点燃后的火焰由 drawApparatusEffects 实时绘制
}

/** 电源：外壳 + 接线柱，通电时显示闪电 */
private fun DrawScope.drawBattery(a: Apparatus, s: Size) {
    val w = s.width
    val h = s.height
    val corner = CornerRadius(w * 0.06f, w * 0.06f)
    drawRoundRect(
        color = Color(0xFF37474F),
        topLeft = Offset(w * 0.22f, h * 0.44f),
        size = Size(w * 0.56f, h * 0.40f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color(0xFF90A4AE),
        topLeft = Offset(w * 0.22f, h * 0.44f),
        size = Size(w * 0.56f, h * 0.40f),
        cornerRadius = corner,
        style = Stroke(width = w * 0.025f)
    )
    drawCircle(Color(0xFFE34A44), radius = w * 0.045f, center = Offset(w * 0.38f, h * 0.42f))
    drawCircle(Color(0xFF2B2B2B), radius = w * 0.045f, center = Offset(w * 0.62f, h * 0.42f))
    if (a.container.isElectrolyzing) {
        drawCircle(Color(0x55FFD766), radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.64f))
        val bolt = Path().apply {
            moveTo(w * 0.52f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.66f)
            lineTo(w * 0.50f, h * 0.66f)
            lineTo(w * 0.46f, h * 0.80f)
            lineTo(w * 0.58f, h * 0.62f)
            lineTo(w * 0.51f, h * 0.62f)
            close()
        }
        drawPath(bolt, color = Color(0xFFFFD766))
    }
}

/** 电极已删除：电源直接接入反应容器的 elec_in，容器内由 drawElectrodesInVessel 绘制电极 */

// ==================== 连线绘制 ====================

/**
 * 端口的朝外方向，吸附到最近的坐标轴（横平竖直走线用）。
 * 端口本身是上下左右四向的，这里只是把 gas_out 这类带轻微斜率的法向拉直。
 */
fun portAxisOut(portId: String, halfDp: Float = APPARATUS_SIZE_DP / 2f): Offset {
    val o = portLocalOffsetDp(portId, halfDp)
    return if (kotlin.math.abs(o.x) >= kotlin.math.abs(o.y)) {
        Offset(if (o.x >= 0f) 1f else -1f, 0f)
    } else {
        Offset(0f, if (o.y >= 0f) 1f else -1f)
    }
}

/**
 * 横平竖直的走线：两端各沿端口法向伸出 [stub]（要足够长以完全离开器材格子），
 * 中间用直角折线连接。
 *
 * 两条硬性约束，都是为了管子不穿过器材、也不出现 180° 回折：
 *  1. 同轴端口（都朝上 / 都朝右…）的转折通道取**共同外侧**坐标，绝不在两件器材之间拐；
 *  2. 一横一竖时先沿**垂直于出口方向**的那条边走，保证管子离开器口后不会立刻折回，
 *     同时这条边贴着「对方接头所在的外侧」走，两边都不会压到器材本体上。
 *
 * 返回折线顶点列表，首尾即两端接头。
 */
private fun orthogonalRoute(
    from: Offset, dFrom: Offset,
    to: Offset, dTo: Offset,
    stub: Float
): List<Offset> {
    val p1 = Offset(from.x + dFrom.x * stub, from.y + dFrom.y * stub)
    val p2 = Offset(to.x + dTo.x * stub, to.y + dTo.y * stub)
    val raw = ArrayList<Offset>(6)
    raw.add(from)
    raw.add(p1)
    // 方向向量是 ±1/0，比较是精确的
    val fromH = dFrom.y == 0f
    val toH = dTo.y == 0f
    when {
        fromH && toH -> {
            val bothLeft = dFrom.x < 0f && dTo.x < 0f
            val bothRight = dFrom.x > 0f && dTo.x > 0f
            if (bothLeft || bothRight) {
                val c = if (bothLeft) minOf(p1.x, p2.x) else maxOf(p1.x, p2.x)
                raw.add(Offset(c, p1.y))
                raw.add(Offset(c, p2.y))
            } else {
                raw.add(Offset(p1.x, p2.y))
            }
        }

        !fromH && !toH -> {
            val bothUp = dFrom.y < 0f && dTo.y < 0f
            val bothDown = dFrom.y > 0f && dTo.y > 0f
            if (bothUp || bothDown) {
                val c = if (bothUp) minOf(p1.y, p2.y) else maxOf(p1.y, p2.y)
                raw.add(Offset(p1.x, c))
                raw.add(Offset(p2.x, c))
            } else {
                raw.add(Offset(p2.x, p1.y))
            }
        }

        // 一横一竖：先走垂直于出口方向的那条轴
        fromH -> raw.add(Offset(p1.x, p2.y))
        else -> raw.add(Offset(p2.x, p1.y))
    }
    raw.add(p2)
    raw.add(to)
    // 去掉重合点，避免零长度线段（会让按弧长取点出现抖动）
    val pts = ArrayList<Offset>(raw.size)
    for (p in raw) {
        if (pts.isEmpty() || (p - pts[pts.size - 1]).getDistance() > 0.01f) pts.add(p)
    }
    return pts
}

/** 折线总长 */
private fun routeLength(pts: List<Offset>): Float {
    var total = 0f
    for (i in 1 until pts.size) total += (pts[i] - pts[i - 1]).getDistance()
    return total
}

/** 折线上按弧长比例 [frac] 取点（0 = 起点，1 = 终点） */
private fun pointAt(pts: List<Offset>, total: Float, frac: Float): Offset {
    if (pts.isEmpty()) return Offset.Zero
    if (pts.size == 1 || total <= 1e-4f) return pts[0]
    var target = total * frac.coerceIn(0f, 1f)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val seg = (b - a).getDistance()
        if (seg <= 1e-5f) continue
        if (target <= seg) {
            val t = target / seg
            return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        target -= seg
    }
    return pts[pts.size - 1]
}

/** 按弧长比例截断折线（生长动画用，保证每一段都是横平竖直的） */
private fun polylinePath(pts: List<Offset>, progress: Float): Path {
    val path = Path()
    if (pts.size < 2) return path
    val total = routeLength(pts)
    val target = total * progress.coerceIn(0f, 1f)
    path.moveTo(pts[0].x, pts[0].y)
    var acc = 0f
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val seg = (b - a).getDistance()
        if (seg <= 1e-5f) continue
        if (acc + seg <= target) {
            path.lineTo(b.x, b.y)
            acc += seg
        } else {
            val t = ((target - acc) / seg).coerceIn(0f, 1f)
            path.lineTo(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            return path
        }
    }
    return path
}

/**
 * 流体导管（气体 / 液体）：横平竖直走线 + 管内流动的粒子。
 *
 * 橡胶塞由容器自己绘制（见 [drawPortFittings]），这里只负责管子本身，
 * 于是塞子和管子天然对齐，不会出现「管子接在半空中的端口点」。
 *
 * @param outFrom 起点端口的朝外方向（已吸附到坐标轴）
 * @param outTo   终点端口的朝外方向
 * @param progress 0→1 的生长进度，用于新建连接时的 200ms 动画
 * @param flow     本 tick 的流量（0..1），决定粒子密度；0 表示没有流动
 * @param forward  流动方向是否为 from → to
 * @param flowTime 连续推进的时间基（帧计数 × 步长，不回卷），粒子位置由它推演
 */
fun DrawScope.drawTube(
    from: Offset, outFrom: Offset,
    to: Offset, outTo: Offset,
    state: LinkVisualState, progress: Float, unit: Float,
    flow: Float, forward: Boolean, flowTime: Float, gas: Boolean
) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return
    // 外伸段取 0.45 倍器材边长：保证转折点完全落在器材格子之外，
    // 否则折线的竖直/水平段会贴着甚至穿过器材本体
    val pts = orthogonalRoute(from, outFrom, to, outTo, unit * 0.45f)
    val path = polylinePath(pts, p)
    val w = unit * TUBE_WIDTH_RATIO
    drawPath(
        path, color = state.color,
        style = Stroke(width = w * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path, color = Color(0xFF1B2229),
        style = Stroke(width = w * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    // 管子画完再画管内流动的粒子，于是粒子始终压在管子之上、像管里的料
    if (flow > 0.04f && p >= 1f) drawTubeFlow(pts, unit, flow, forward, flowTime, gas)
}

/**
 * 管内流动：粒子沿折线按弧长均匀分布、整体向前推进，形成连续输送的观感。
 *
 * 位置是 [flowTime] 的纯函数（不保存粒子状态）。这里刻意不用主循环那个 0..1 的
 * 锯齿相位：它的周期是固定的 9 拍，一旦乘以不等于 1 的速度系数，就会在相位回卷处
 * 出现可见的位置跳变。连续时间基没有这个问题 —— 粒子只是不停地向前走。
 * 流量越大，粒子越多、走得越快。
 */
private fun DrawScope.drawTubeFlow(
    pts: List<Offset>, unit: Float, flow: Float, forward: Boolean, flowTime: Float, gas: Boolean
) {
    val total = routeLength(pts)
    if (total <= 1e-3f) return
    val count = (6 + (flow * 8f).toInt()).coerceIn(6, 14)
    val r = unit * (if (gas) 0.026f else 0.023f)
    val col = if (gas) Color(0x99EAF6FB) else Color(0xB33FA9E0)
    val speed = 0.55f + flow * 0.85f
    for (i in 0 until count) {
        val f = (flowTime * speed + i.toFloat() / count) % 1f
        val t = if (forward) f else 1f - f
        // 头尾渐隐，粒子不会在接头处「突然出现/消失」
        val edge = minOf(t, 1f - t)
        val a = (edge / 0.18f).coerceIn(0.15f, 1f)
        drawCircle(color = col.copy(alpha = col.alpha * a), radius = r, center = pointAt(pts, total, t))
    }
}

/**
 * 入料动画：物质进入容器之后的延续 —— 液体成串下滴、气体成串上浮。
 *
 * 单独在器材之上绘制（不在连线层）：否则会被容器位图盖住，
 * 就看不出「容器里也在流动」了。与管内流动共用连续时间基，整条通路连成一体。
 *
 * @param at     目标容器上的接头位置
 * @param inward 进入容器的方向（= 该端口朝外方向的反方向）
 * @param flowTime 连续推进的时间基（与管内的同一份）
 */
fun DrawScope.drawInflow(
    at: Offset, inward: Offset, unit: Float, flow: Float, flowTime: Float, gas: Boolean
) {
    if (flow <= 0.04f) return
    // 液体受重力下坠、气体上浮；同时保留一点「沿管内方向继续前进」的惯性
    val bias = if (gas) Offset(0f, -1f) else Offset(0f, 1f)
    val dir = inward * 0.35f + bias
    val len = dir.getDistance()
    if (len < 1e-4f) return
    val u = dir / len
    val span = unit * 0.36f
    val count = (3 + (flow * 4f).toInt()).coerceIn(3, 7)
    val r = unit * (if (gas) 0.024f else 0.020f)
    val col = if (gas) Color(0x99EAF6FB) else Color(0xB33FA9E0)
    for (i in 0 until count) {
        val f = (flowTime * 0.85f + i.toFloat() / count) % 1f
        val p = Offset(at.x + u.x * span * f, at.y + u.y * span * f)
        drawCircle(
            color = col.copy(alpha = col.alpha * (1f - f) * 0.9f),
            radius = r * (1f - f * 0.35f),
            center = p
        )
    }
}

/** 器口橡胶塞：沿端口朝外方向的梯形塞子 */
private fun DrawScope.drawStopper(at: Offset, out: Offset, unit: Float) {
    val len = out.getDistance()
    val u = if (len < 1e-4f) Offset(0f, -1f) else out / len
    val n = Offset(-u.y, u.x)
    val half = unit * 0.055f
    val depth = unit * 0.070f
    val base = Offset(at.x - u.x * depth * 0.35f, at.y - u.y * depth * 0.35f)
    val tip = Offset(at.x + u.x * depth * 0.65f, at.y + u.y * depth * 0.65f)
    val path = Path().apply {
        moveTo(base.x - n.x * half, base.y - n.y * half)
        lineTo(base.x + n.x * half, base.y + n.y * half)
        lineTo(tip.x + n.x * half * 0.70f, tip.y + n.y * half * 0.70f)
        lineTo(tip.x - n.x * half * 0.70f, tip.y - n.y * half * 0.70f)
        close()
    }
    drawPath(path, color = Color(0xFF4A4640))
    drawPath(path, color = Color(0xFF7A7060), style = Stroke(width = unit * 0.008f))
}

/**
 * 导线（电源 → 容器接线端）：与导管同一套横平竖直走线，
 * 用深色外皮 + 亮色芯线区分于管路。
 */
fun DrawScope.drawWire(
    from: Offset, outFrom: Offset,
    to: Offset, outTo: Offset,
    state: LinkVisualState, progress: Float, unit: Float
) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return
    val pts = orthogonalRoute(from, outFrom, to, outTo, unit * 0.26f)
    val path = polylinePath(pts, p)
    val core = if (state == LinkVisualState.Highlighted) Color(0xFF4FC3F7) else Color(0xFFD8B25A)
    val w = unit * 0.030f
    drawPath(
        path, color = Color(0xFF23282E),
        style = Stroke(width = w * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path, color = core,
        style = Stroke(width = w * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * 连线折线的中点（屏幕坐标），用于放置「断开」热区。
 *
 * 必须按**实际走线**取中点，不能取两个端口连线的中点：横平竖直走线会先沿器口
 * 方向伸出去再绕，两者可能相差一整个器材的尺度，于是「点在管子上却没反应」。
 *
 * @param stub 外伸段长度，需与对应的 drawTube / drawWire 调用保持一致
 */
fun linkMidPoint(
    from: Offset, outFrom: Offset,
    to: Offset, outTo: Offset,
    stub: Float
): Offset {
    val pts = orthogonalRoute(from, outFrom, to, outTo, stub)
    return pointAt(pts, routeLength(pts), 0.5f)
}

// ==================== 端口组件 ====================

/**
 * 端口圆点。
 *
 * 结构上刻意分两层：外层 48dp 负责触摸，内层 Canvas 只画 16dp 视觉圆 ——
 * 视觉小巧不喧宾夺主，命中区却达到小屏推荐的 48dp。
 *
 * 手势：按下后位移超过 touchSlop 视为「磁吸拖拽」，否则抬起算「点击配对」。
 * 按下即 consume，避免父层空白区点击把刚选中的端口又清掉。
 *
 * 呼吸/脉冲由外部传入的相位 [phase] 驱动（而不是 rememberInfiniteTransition）：
 * 无限动画会让每个端口都常驻一个帧时钟，低配手表上会持续重组、非常耗电。
 * 相位以 lambda 形式传入并只在绘制阶段调用，因此帧计数变化只让端口重绘、不触发组合。
 *
 * @param phase 返回 0→1 循环相位的取值器，由主循环帧计数提供
 */
@Composable
fun PortDot(
    state: PortVisualState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    grow: Boolean = false,
    phase: () -> Float = { 0f },
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val visualDp = if (grow) PORT_MAGNET_GROW_DP else PORT_VISUAL_DP
    val alpha = if (state == PortVisualState.Invalid) 0.20f else 1f

    Box(
        modifier = modifier
            .size(PORT_HIT_DP.dp)
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    // 手动区分「点击配对」与「磁吸拖拽」：位移超过 touchSlop 才进入拖拽。
                    // 按下即 consume，拦下事件避免父层空白区点击把刚选中的端口清掉。
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val slop = viewConfiguration.touchSlop
                            val start = down.position
                            var dragging = false
                            var finished = false
                            while (!finished) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    finished = true
                                } else if (!change.pressed) {
                                    if (dragging) onDragEnd() else onClick()
                                    finished = true
                                } else {
                                    if (!dragging && (change.position - start).getDistance() > slop) {
                                        dragging = true
                                        onDragStart(change.position)
                                    }
                                    if (dragging) {
                                        change.consume()
                                        onDrag(change.position)
                                    }
                                }
                            }
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(visualDp.dp)) {
            val r = size.minDimension / 2f
            // 呼吸/脉冲强度在绘制阶段求值：Compatible 闪烁与 Selected 外圈脉冲共用同一条相位曲线
            val breath = 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(phase() * 6.2831855f))
            when (state) {
                PortVisualState.Selected ->
                    drawCircle(color = state.color.copy(alpha = 0.40f * breath), radius = r * 1.85f)

                PortVisualState.Compatible ->
                    drawCircle(color = state.color.copy(alpha = 0.35f * breath), radius = r * 1.55f)

                else -> Unit
            }
            drawCircle(color = Color(0xF2FFFFFF), radius = r)
            drawCircle(color = state.color.copy(alpha = alpha), radius = r * 0.70f)
        }
    }
}

/** 连线中点的断开热区：24dp，点击弹出确认面板 */
@Composable
fun LinkHitTarget(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(LINK_HIT_DP.dp)
            .pointerInput(Unit) {
                while (true) {
                    awaitPointerEventScope {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var moved = false
                        var finished = false
                        while (!finished) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                finished = true
                            } else if (!change.pressed) {
                                if (!moved) onClick()
                                finished = true
                            } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                        }
                    }
                }
            }
    )
}

/** 端口种类中文名，用于顶部提示 */
fun portKindLabel(kind: PortKind): String = when (kind) {
    PortKind.LIQUID -> "液体"
    PortKind.GAS -> "气体"
    PortKind.HEAT -> "热量"
    PortKind.ELECTRIC -> "电流"
}