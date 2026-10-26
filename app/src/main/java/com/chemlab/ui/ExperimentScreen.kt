package com.chemlab.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chemlab.game.Category
import com.chemlab.game.Equipment
import com.chemlab.game.Equipments
import com.chemlab.game.Phase
import com.chemlab.game.Substance
import com.chemlab.game.Substances
import com.chemlab.game.WorldState
import com.chemlab.world.Apparatus
import com.chemlab.world.Direction
import com.chemlab.world.Link
import com.chemlab.world.PortKind
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** 主循环周期：100ms（10Hz），与低端小屏的刷新能力匹配 */
const val TICK_MS = 100L

/** 画布缩放范围 */
private const val MIN_SCALE = 0.55f
private const val MAX_SCALE = 2.5f

/** 动效相位周期（tick 数）：9 拍 ≈ 900ms，与旧版 GameView 的相位节奏一致 */
private const val PHASE_TICKS = 9

/** 反应闪光持续 tick 数 */
private const val FLASH_TICKS = 7

/**
 * 流动动画的每帧时间步长。
 * 主循环 100ms 一拍，0.05 表示管内粒子约 20 拍（2 秒）走完一趟。
 */
private const val FLOW_TIME_STEP = 0.05f

// ==================== 界面配色（与旧版 GameView 完全一致） ====================

private val BG = Color(0xFF10151C)
private val BG_TOP = Color(0xFF16222D)
private val BG_BOTTOM = Color(0xFF0B1117)
private val PANEL = Color(0xFF1C2430)
private val BTN = Color(0xFF2A3542)
private val BTN_ACTIVE = Color(0xFF3A4A5C)
private val BTN_ON = Color(0xFF3E5E6E)
private val ACCENT = Color(0xFF4FC3F7)
private val TEXT_MAIN = Color(0xFFECEFF4)
private val TEXT_DIM = Color(0xFF9AA7B4)
private val FLAME = Color(0xFFFFB74D)
private val HAIRLINE = Color(0x33FFFFFF)

/** 端口引用（器材 id + 端口 id） */
private data class PortRef(val apparatusId: String, val portId: String)

/** 端口的中文短名，用于提示文案 */
private fun portName(portId: String): String = when (portId) {
    "top" -> "上口"
    "mouth" -> "瓶口"
    "gas_in" -> "进气口"
    "gas_out" -> "出气口"
    "water_out" -> "排水口"
    "liquid_in" -> "进液口"
    "liquid_out" -> "出液口"
    "heat_in" -> "受热口"
    "heat_out" -> "供热口"
    "elec_in" -> "接线端"
    "elec_out" -> "输出端"
    else -> portId
}

/** 背景渐变的画笔缓存：shader 只在尺寸变化时重建，避免每帧新建对象 */
private class BackgroundCache {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var height = 0f
}

/**
 * 「器材联动实验」主界面。
 *
 * 视觉与旧版 [com.chemlab.game.GameView] 对齐：同一套配色、同样按系统安全区内缩、
 * 同样的圆角面板与加大的操作按钮。性能上针对低配手表做了三件事：
 *  1. 主循环只在 [WorldState.tick] 返回 true 时推进 UI 帧，静止时不跑模拟也不重绘；
 *  2. 器材位图只在「内容变化」时重建（见 [ApparatusCache]），升温和冒泡不触发重建；
 *  3. 温度计、火焰、气泡等每帧变化的绘制走实时层，沿用旧版相位驱动方案，不分配对象。
 *
 * @param tick       主循环帧计数（State 形式），仅在绘制阶段读取，用于驱动重绘与特效相位
 * @param onReaction 反应回调（成就、日志）
 * @param onAction   操作回调
 * @param onExit     返回主页面
 */
@Composable
fun ExperimentScreen(
    state: WorldState,
    tick: State<Int>,
    onReaction: (String, com.chemlab.game.Reaction) -> Unit = { _, _ -> },
    onAction: (String) -> Unit = {},
    onExit: () -> Unit
) {
    val density = LocalDensity.current.density
    val cellPx = APPARATUS_SIZE_DP * density
    val hitHalf = (PORT_HIT_DP * density) / 2f
    val magnetPx = PORT_MAGNET_DP * density
    val safe = rememberSafeAreaPadding()

    /**
     * 由帧计数推导的动效相位。
     *
     * 刻意做成 lambda 而不是直接算好一个 Float：只有在「绘制阶段」调用它才会读取帧计数，
     * 于是 tick 变化只让画布/端口重绘，不会把帧计数读进组合从而触发整屏重组。
     */
    val phaseOf: () -> Float = { (tick.value % PHASE_TICKS) / PHASE_TICKS.toFloat() }

    // ---- 交互状态 ----
    var mode by remember { mutableStateOf(InteractionMode.OPERATE) }
    var selectedPort by remember { mutableStateOf<PortRef?>(null) }
    var dragPort by remember { mutableStateOf<PortRef?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var dragOver by remember { mutableStateOf<PortRef?>(null) }
    /** 正在被长按拖动的器材 id 及其世界坐标中心 */
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableStateOf(Offset.Zero) }
    /** 底部操作栏：仅点击器材后弹出 */
    var showBar by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    var confirmLink by remember { mutableStateOf<Link?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var areaSize by remember { mutableStateOf(Offset.Zero) }
    var seeded by remember { mutableStateOf(false) }

    /**
     * 交互版本号。
     *
     * [WorldState.activeId] 与器材内容是普通可变字段（不参与快照系统），
     * 点击换选器材时只有它变化 —— 若不自增一个快照状态，界面不会重组，
     * 底部操作栏会停留在上一件器材上。所有交互后自增即可强制整屏刷新。
     */
    var uiRev by remember { mutableStateOf(0) }
    fun refresh() {
        uiRev++
    }

    /** 当前选中器材：随交互重算，保证操作栏指向正确对象 */
    val active = remember(uiRev) { state.active }

    // ---- 画布视图变换 ----
    var scale by remember { mutableStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }

    // ---- 特效计时（按 tick 计，避免依赖系统时钟） ----
    var flashUntil by remember { mutableStateOf(0) }

    val caches = remember { mutableMapOf<String, ApparatusCache>() }
    val linkBorn = remember { mutableMapOf<String, Int>() }
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val bgCache = remember { BackgroundCache() }

    // 反应/操作回调接到外部（成就持久化）
    LaunchedEffect(state) {
        state.onReaction = { id, r ->
            flashUntil = tick.value + FLASH_TICKS
            onReaction(id, r)
        }
        state.onAction = { tag -> onAction(tag) }
    }

    // ---- 视图变换辅助 ----
    fun toScreen(w: Offset) = Offset(w.x * scale + viewOffset.x, w.y * scale + viewOffset.y)

    /** 自由平移：不限制边界，画布可以任意移动 */
    fun moveView(delta: Offset) {
        viewOffset += delta
    }

    /** 以 [focus]（屏幕坐标）为锚点缩放，保证锚点下的内容不跑偏 */
    fun applyZoom(factor: Float, focus: Offset) {
        val ns = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (ns == scale) return
        val world = Offset((focus.x - viewOffset.x) / scale, (focus.y - viewOffset.y) / scale)
        scale = ns
        viewOffset = Offset(focus.x - world.x * ns, focus.y - world.y * ns)
    }

    // ---- 几何辅助 ----

    /**
     * 器材中心（世界坐标）。
     *
     * 拖动中的器材用实时位置 [dragCenter]；若它与另一件器材组成整体（酒精灯吸附在容器
     * 下方、漏斗插在容器口上），伙伴按「拖动开始时记录的相对位移」跟随 ——
     * 于是整个整体一起走，反应容器无法被单独拖离它的配件。
     */
    fun centerWorld(a: Apparatus): Offset {
        val dragged = draggingId
        if (dragged == null) return Offset(a.x, a.y)
        if (a.id == dragged) return dragCenter
        val host = state.world.apparatus[dragged] ?: return Offset(a.x, a.y)
        return if (state.world.unitPartner(dragged) == a.id) {
            Offset(dragCenter.x + (a.x - host.x), dragCenter.y + (a.y - host.y))
        } else {
            Offset(a.x, a.y)
        }
    }

    /**
     * 器材最上方端口点所需的顶部余量（density px）。
     *
     * 器材顶部的端口点位于中心上方 0.95×半格处，它的触摸命中区还要再往外半个
     * [PORT_HIT_DP]；实验区是带圆角裁切的，命中区一旦被裁掉就点不到端口了。
     */
    fun topPortPad(): Float = cellPx * 0.475f + PORT_HIT_DP * density * 0.5f

    /**
     * 配件吸附到容器上：酒精灯固定在容器下方（灯焰对着器底），
     * 漏斗插在容器口上（下管伸进容器）。吸附后两者以整体形式选中、拖动。
     *
     * 吸附后会把整体一起平移回实验区内：漏斗的进液口在它自己上方，容器若在第一行，
     * 硬吸附会把漏斗顶到实验区外，端口点跟着被裁掉 —— 看上去就是「漏斗端口用不了」。
     */
    fun snapAccessory(accessory: Apparatus, vessel: Apparatus) {
        accessory.x = vessel.x
        accessory.y = if (accessory.equipment.id == Equipment.FUNNEL) {
            vessel.y - FUNNEL_UNIT_OFFSET_DP * density
        } else {
            vessel.y + HEAT_UNIT_OFFSET_DP * density
        }
        if (areaSize.y > 0f) {
            // 上边界按「端口命中区」留余量，下边界只需整体不出界
            val need = minOf(accessory.y, vessel.y) - topPortPad() - density * 6f
            var delta = if (need < 0f) -need else 0f
            val hi = maxOf(accessory.y, vessel.y) + cellPx / 2f
            if (hi + delta > areaSize.y) delta -= (hi + delta - areaSize.y)
            if (delta != 0f) {
                accessory.y += delta
                vessel.y += delta
            }
        }
    }

    fun portCenterWorld(a: Apparatus, portId: String): Offset {
        val o = portLocalOffsetDp(portId)
        return centerWorld(a) + Offset(o.x * density, o.y * density)
    }

    /**
     * 端口接头在器材实体上的位置（世界坐标）。
     * 连接件（导管/导线）从这里出发并落在这里 —— 试管口、瓶口、接线柱，
     * 而不是贴在器材格子边缘的触摸端口点。
     */
    fun portJointWorld(a: Apparatus, portId: String): Offset {
        val j = portJointLocalDp(a.equipment.id, portId)
        return centerWorld(a) + Offset(j.x * density, j.y * density)
    }

    /**
     * 器材已接线端口的位掩码（第 i 位对应 a.ports[i]）。
     * 用位掩码而不是集合：Canvas 每帧都要算一次，这样可以零分配地直接参与
     * 器材位图缓存的失效判定，接头装置随接线状态自动重绘。
     */
    fun wiredMask(a: Apparatus): Int {
        var m = 0
        a.ports.forEachIndexed { i, p ->
            if (state.world.isPortOpenLinked(a.id, p.id)) m = m or (1 shl i)
        }
        return m
    }

    /** 命中器材（屏幕坐标）。命中判定与绘制层级一致：配件（酒精灯/漏斗）压在容器之上时优先选中 */
    fun hitApparatus(pos: Offset): Apparatus? {
        val half = cellPx * scale / 2f
        var hit: Apparatus? = null
        for (a in state.world.apparatus.values) {
            val c = toScreen(centerWorld(a))
            if (pos.x < c.x - half || pos.x > c.x + half) continue
            if (pos.y < c.y - half || pos.y > c.y + half) continue
            // 同层内取后出现的（与绘制顺序一致）；配件层始终压过容器层
            if (hit == null || state.world.layerOf(a) >= state.world.layerOf(hit)) hit = a
        }
        return hit
    }

    fun portVisual(apparatusId: String, portId: String): PortVisualState {
        val sel = selectedPort
        if (sel == null) {
            return if (state.world.isPortConnected(apparatusId, portId)) {
                PortVisualState.Connected
            } else {
                PortVisualState.Idle
            }
        }
        if (sel.apparatusId == apparatusId && sel.portId == portId) return PortVisualState.Selected
        return if (state.world.canConnect(sel.apparatusId, sel.portId, apparatusId, portId)) {
            PortVisualState.Compatible
        } else {
            PortVisualState.Invalid
        }
    }

    /** 建立连接并生成提示文案（失败时说明原因，而不是静默无反应） */
    fun tryConnect(src: PortRef, dst: PortRef): String {
        if (src.apparatusId == dst.apparatusId && src.portId == dst.portId) return "不能连接到自身"
        val a = state.world.apparatus[src.apparatusId] ?: return "器材不存在"
        val b = state.world.apparatus[dst.apparatusId] ?: return "器材不存在"
        val pa = a.port(src.portId) ?: return "端口不存在"
        val pb = b.port(dst.portId) ?: return "端口不存在"
        if (pa.kind != pb.kind) {
            return "端口类型不同（${portKindLabel(pa.kind)} ≠ ${portKindLabel(pb.kind)}），无法连接"
        }
        if (pa.dir == Direction.OUT && pb.dir == Direction.OUT) return "两个端口都是输出端，无法连接"
        if (pa.dir == Direction.IN && pb.dir == Direction.IN) return "两个端口都是输入端，无法连接"
        // 配件（酒精灯 / 漏斗）用**自己的出口**接到容器上才会合成整体，这是一对一关系：
        // 一个配件只服务一个容器，否则「吸附 + 整体拖动」无从谈起。
        // 注意「从别处往配件里送料」（如集气瓶排水口 → 漏斗进液口）不算整体，
        // 不受这条限制 —— 否则漏斗自己的进液口就永远接不上东西了。
        val willFormUnit = state.world.willFormUnit(src.apparatusId, src.portId, dst.apparatusId, dst.portId)
        if (willFormUnit) {
            listOf(a, b).firstOrNull { state.world.unitPartner(it.id) != null }?.let {
                return "${it.equipment.cn} 已与另一件器材组成整体，请先分离"
            }
        }
        // 已经组成整体的两件器材之间不再接受第二条连线：例如「漏斗进液口 → 它自己插着的
        // 容器瓶口」会形成容器 ↔ 漏斗的死循环（液体在两者之间来回倒），没有任何意义。
        if (state.world.unitPartner(a.id) == b.id || state.world.unitPartner(b.id) == a.id) {
            return "${a.equipment.cn} 与 ${b.equipment.cn} 已组成整体，无需重复连接"
        }
        val link = state.connect(src.apparatusId, src.portId, dst.apparatusId, dst.portId)
            ?: return "连接失败：该连接已存在"
        linkBorn[link.id] = tick.value
        // 配件接上容器后吸附上去，两者合成整体
        if (willFormUnit) {
            val accessory = if (state.world.isAccessory(a)) a else b
            val vessel = if (accessory === a) b else a
            snapAccessory(accessory, vessel)
            // 绘制层级由 state.world.layerOf 派生（配件压在容器之上），这里不再改列表顺序
            return "已把 ${accessory.equipment.cn} 固定在 ${vessel.equipment.cn} 上（可整体拖动，操作栏可分离）"
        }
        val kind = portKindLabel(pa.kind)
        return "已连接：${a.equipment.cn}·${portName(pa.id)} → ${b.equipment.cn}·${portName(pb.id)}（$kind）"
    }

    /** 摆放第 index 件器材的默认位置（两列错落，保证端口命中区不重叠） */
    fun slotCenter(index: Int, w: Float, h: Float): Offset {
        val cols = 2
        val col = index % cols
        val row = index / cols
        val marginX = cellPx * 0.75f
        val marginY = cellPx * 0.75f
        val usableW = (w - marginX * 2f).coerceAtLeast(cellPx)
        val x = marginX + usableW * col
        val y = marginY + cellPx * 1.55f * row
        // 顶行同样要给端口命中区留余量（见 topPortPad），否则第一行器材的瓶口点被裁掉
        val minY = cellPx * 0.5f + (topPortPad() - cellPx * 0.5f).coerceAtLeast(0f)
        return Offset(
            x.coerceIn(cellPx / 2f, (w - cellPx / 2f).coerceAtLeast(cellPx / 2f)),
            y.coerceIn(minY, (h - cellPx / 2f).coerceAtLeast(minY))
        )
    }

    fun addApparatus(e: Equipment) {
        val id = state.nextId()
        val idx = state.world.apparatus.size
        val pos = slotCenter(idx, areaSize.x, areaSize.y)
        val a = Apparatus(id, e, pos.x, pos.y)
        state.addApparatus(a)
        state.select(id)
        showBar = true
        showPalette = false
        message = "已放置 ${e.cn}"
        refresh()
    }

    // 背景铺满全屏（含非安全区），内容再按安全区内缩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (bgCache.paint.shader == null || bgCache.height != size.height) {
                    bgCache.paint.shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, size.height,
                        intArrayOf(BG_TOP.toArgb(), BG.toArgb(), BG_BOTTOM.toArgb()),
                        floatArrayOf(0f, 0.5f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    bgCache.height = size.height
                }
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, bgCache.paint) }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(safe)) {

            // ==================== 顶部提示横幅 ====================
            TopBanner(
                mode = mode,
                message = message,
                onMode = { m ->
                    mode = m
                    selectedPort = null
                    draggingId = null
                    dragPort = null
                    message = m.hint
                    refresh()
                },
                onAdd = { showPalette = true; refresh() },
                onExit = onExit
            )

            // ==================== 实验台 ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PANEL)
                    .onSizeChanged { size ->
                        areaSize = Offset(size.width.toFloat(), size.height.toFloat())
                        // 首次拿到尺寸后再摆放初始器材（位置依赖可用区域）
                        if (!seeded && size.width > 0) {
                            seeded = true
                            val lamp = Apparatus(state.nextId(), Equipments.ALCOHOL_LAMP)
                            slotCenter(0, areaSize.x, areaSize.y).let { lamp.x = it.x; lamp.y = it.y }
                            val tube = Apparatus(state.nextId(), Equipments.TEST_TUBE)
                            slotCenter(1, areaSize.x, areaSize.y).let { tube.x = it.x; tube.y = it.y }
                            val bottle = Apparatus(state.nextId(), Equipments.GAS_COLLECTOR)
                            slotCenter(2, areaSize.x, areaSize.y).let { bottle.x = it.x; bottle.y = it.y }
                            state.addApparatus(lamp)
                            state.addApparatus(tube)
                            state.addApparatus(bottle)
                            state.select(tube.id)
                            message = "已放置酒精灯 / 试管 / 集气瓶"
                        }
                    }
                    // 统一手势：单指=点选/平移画布，长按=拖动器材，双指=缩放
                    .pointerInput(mode) {
                        val longPressMs = viewConfiguration.longPressTimeoutMillis
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            awaitPointerEventScope {
                                // 端口等子节点已 consume 的按下事件不参与画布手势
                                val down = awaitFirstDown(requireUnconsumed = true)
                                val t0 = SystemClock.uptimeMillis()
                                var moved = false
                                var longPressed = false
                                var multi = false
                                var appId: String? = null
                                var last = down.position
                                var finished = false

                                while (!finished) {
                                    val remain = longPressMs - (SystemClock.uptimeMillis() - t0)
                                    val ev = if (!moved && !longPressed && !multi && remain > 0) {
                                        withTimeoutOrNull(remain) { awaitPointerEvent() }
                                    } else {
                                        awaitPointerEvent()
                                    }

                                    if (ev == null) {
                                        // 长按超时：按住的是器材则进入拖动（拖动不弹出操作栏）
                                        val hit = hitApparatus(down.position)
                                        if (hit != null) {
                                            longPressed = true
                                            appId = hit.id
                                            dragCenter = Offset(hit.x, hit.y)
                                            draggingId = hit.id
                                            state.select(hit.id)
                                            message = "拖动中：${hit.equipment.cn}"
                                            refresh()
                                        }
                                        continue
                                    }

                                    if (ev.changes.count { it.pressed } >= 2) {
                                        // 双指：缩放 + 平移
                                        multi = true
                                        if (appId != null) {
                                            draggingId = null
                                            appId = null
                                        }
                                        val zoom = ev.calculateZoom()
                                        val pan = ev.calculatePan()
                                        val centroid = ev.calculateCentroid(useCurrent = true)
                                        if (zoom != 1f) applyZoom(zoom, centroid)
                                        if (pan != Offset.Zero) moveView(pan)
                                        ev.changes.forEach { it.consume() }
                                        last = centroid
                                        continue
                                    }

                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                    if (ch == null || !ch.pressed) {
                                        when {
                                            longPressed && appId != null -> {
                                                val host = state.world.apparatus[appId]
                                                if (host != null) {
                                                    // 整体落位：伙伴（酒精灯 / 漏斗）保持与拖动前的相对位移
                                                    val partner = state.world.unitPartner(host.id)
                                                        ?.let { state.world.apparatus[it] }
                                                    val dx = if (partner != null) partner.x - host.x else 0f
                                                    val dy = if (partner != null) partner.y - host.y else 0f
                                                    host.x = dragCenter.x
                                                    host.y = dragCenter.y
                                                    partner?.let {
                                                        it.x = dragCenter.x + dx
                                                        it.y = dragCenter.y + dy
                                                    }
                                                    message = if (partner != null) {
                                                        "已移动 ${host.equipment.cn}（连同 ${partner.equipment.cn}）"
                                                    } else {
                                                        "已移动 ${host.equipment.cn}"
                                                    }
                                                }
                                                draggingId = null
                                                refresh()
                                            }

                                            !moved && !multi -> {
                                                // 点击：命中器材则选中并弹出操作栏，点空白则收起
                                                val hit = hitApparatus(down.position)
                                                if (hit != null) {
                                                    state.select(hit.id)
                                                    showBar = true
                                                    message = "已选中 ${hit.equipment.cn}"
                                                } else {
                                                    showBar = false
                                                    selectedPort = null
                                                    message = null
                                                }
                                                refresh()
                                            }
                                        }
                                        finished = true
                                    } else if (longPressed && appId != null) {
                                        val d = ch.position - last
                                        // 自由摆放：不限制器材位置
                                        dragCenter = Offset(
                                            dragCenter.x + d.x / scale,
                                            dragCenter.y + d.y / scale
                                        )
                                        ch.consume()
                                        last = ch.position
                                    } else if (!multi) {
                                        if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                                        if (moved) {
                                            moveView(ch.position - last)
                                            ch.consume()
                                        }
                                        last = ch.position
                                    }
                                }
                            }
                        }
                    }
            ) {
                // ---- 连线 + 器材 + 实时动效 ----
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 只在绘制阶段读取帧计数：tick 变化仅让本 Canvas 重绘，不触发整屏重组
                    val t = tick.value
                    val phase = (t % PHASE_TICKS) / PHASE_TICKS.toFloat()
                    val flash = ((flashUntil - t) / FLASH_TICKS.toFloat()).coerceIn(0f, 1f)
                    // 流动用的连续时间基：帧计数 × 固定步长，不回卷。
                    // 不能复用上面的 phase（9 拍锯齿波）—— 乘上速度系数后会在回卷处跳变。
                    val flowTime = t * FLOW_TIME_STEP

                    // 1) 先画连接件，让器具压在它们之上
                    //    · 气体 / 液体：导管 + 器口橡胶塞（从器口伸出后弯向对方，随位置实时跟随）
                    //    · 电学：电源到容器接线端的导线
                    //    · 热学：酒精灯已物理吸附在容器下方，相邻本身就是连接，不画线
                    for (link in state.world.links) {
                        val a = state.world.apparatus[link.fromId] ?: continue
                        val b = state.world.apparatus[link.toId] ?: continue
                        val pa = a.port(link.fromPort) ?: continue
                        // 构成整体的连线不画连接件：酒精灯是物理贴在容器下方、
                        // 漏斗下管是直接插在容器口里，都不该再画一根管子出来。
                        if (state.world.isUnitLink(link)) continue
                        // 连接件走「实体接头」而不是抽象端口点：管子两端落在容器真实的开口上
                        val from = toScreen(portJointWorld(a, link.fromPort))
                        val to = toScreen(portJointWorld(b, link.toPort))
                        val born = linkBorn[link.id]
                        // 200ms 生长动画：以 100ms 主循环为节拍，2 拍走完
                        val progress = if (born == null) 1f else ((t - born) / 2f).coerceIn(0f, 1f)
                        val vs = when {
                            link.id == confirmLink?.id -> LinkVisualState.Highlighted
                            !link.open -> LinkVisualState.Closed
                            else -> LinkVisualState.Open
                        }
                        val unitPx = cellPx * scale
                        val outFrom = portAxisOut(link.fromPort)
                        val outTo = portAxisOut(link.toPort)
                        when (pa.kind) {
                            PortKind.HEAT -> Unit

                            PortKind.ELECTRIC -> drawWire(
                                from = from, outFrom = outFrom,
                                to = to, outTo = outTo,
                                state = vs, progress = progress, unit = unitPx
                            )

                            else -> drawTube(
                                from = from, outFrom = outFrom,
                                to = to, outTo = outTo,
                                state = vs, progress = progress, unit = unitPx,
                                // 流量与流向由引擎按本 tick 实际搬运量给出
                                flow = link.flow, forward = link.flowForward,
                                flowTime = flowTime, gas = pa.kind == PortKind.GAS
                            )
                        }
                    }

                    // 2) 器材静态内容（命中缓存时只是一次 drawImage）
                    //    分两遍画：容器在下、配件（酒精灯/漏斗）在上 —— 插进容器口的漏斗
                    //    下管要能看见。层级由 layerOf 派生，不改动器材列表本身的顺序。
                    //    整体的成员不各自画选中框，由下面的整体框统一表示。
                    for (layer in 0..1) {
                        for (a in state.world.apparatus.values) {
                            if (state.world.layerOf(a) != layer) continue
                            val cache = caches.getOrPut(a.id) { ApparatusCache() }
                            val sc = toScreen(centerWorld(a))
                            val size = cellPx * scale
                            drawApparatus(
                                a = a,
                                cache = cache,
                                cellPx = cellPx.roundToInt(),
                                selected = a.id == state.activeId && state.world.unitPartner(a.id) == null,
                                // 哪些端口接了线：决定容器上要不要画橡胶塞 / 容器内要不要画电极
                                wiredMask = wiredMask(a),
                                topLeft = Offset(sc.x - size / 2f, sc.y - size / 2f),
                                dstSize = size
                            )
                        }
                    }

                    // 2.5) 整体的选中框：包住配件（酒精灯 / 漏斗）与容器，读作一个器材
                    state.activeId?.let { aid ->
                        val partnerId = state.world.unitPartner(aid)
                        val a1 = state.world.apparatus[aid]
                        val a2 = partnerId?.let { state.world.apparatus[it] }
                        if (a1 != null && a2 != null) {
                            val s1 = toScreen(centerWorld(a1))
                            val s2 = toScreen(centerWorld(a2))
                            val size = cellPx * scale
                            val half = size / 2f
                            val inset = size * 0.06f
                            val tl = Offset(
                                minOf(s1.x, s2.x) - half + inset,
                                minOf(s1.y, s2.y) - half + inset
                            )
                            val boxSize = Size(
                                kotlin.math.abs(s1.x - s2.x) + size - inset * 2f,
                                kotlin.math.abs(s1.y - s2.y) + size - inset * 2f
                            )
                            val corner = CornerRadius(size * 0.14f, size * 0.14f)
                            drawRoundRect(
                                color = Color(0x332196F3),
                                topLeft = tl, size = boxSize, cornerRadius = corner
                            )
                            drawRoundRect(
                                color = Color(0xFF2196F3),
                                topLeft = tl, size = boxSize, cornerRadius = corner,
                                style = Stroke(width = size * 0.02f)
                            )
                        }
                    }

                    // 3) 实时动效：火焰 / 气泡 / 电解 / 温度计（旧版相位驱动方案）
                    for (a in state.world.apparatus.values) {
                        val sc = toScreen(centerWorld(a))
                        val size = cellPx * scale
                        drawApparatusEffects(a, sc.x, sc.y, size, phase)
                        if (flash > 0f && a.id == state.activeId) {
                            drawCircle(
                                color = Color(0xFFFFD766).copy(alpha = flash * 0.30f),
                                radius = size * (0.62f + (1f - flash) * 0.45f),
                                center = sc
                            )
                        }
                    }

                    // 3.5) 入料动画：物质进入容器后的延续（液体下滴 / 气体上浮）。
                    //      必须画在器材位图之上，否则「容器里也在流动」会被容器盖住看不见。
                    for (link in state.world.links) {
                        if (!link.open || link.flow <= 0.04f) continue
                        val a = state.world.apparatus[link.fromId] ?: continue
                        val b = state.world.apparatus[link.toId] ?: continue
                        val pa = a.port(link.fromPort) ?: continue
                        if (pa.kind != PortKind.GAS && pa.kind != PortKind.LIQUID) continue
                        // 下游（入料端）随流动方向而定，不一定就是 toId
                        val dst = if (link.flowForward) b else a
                        val dstPort = if (link.flowForward) link.toPort else link.fromPort
                        val at = toScreen(portJointWorld(dst, dstPort))
                        val out = portAxisOut(dstPort)
                        drawInflow(
                            at = at,
                            inward = Offset(-out.x, -out.y),
                            unit = cellPx * scale,
                            flow = link.flow,
                            flowTime = flowTime,
                            gas = pa.kind == PortKind.GAS
                        )
                    }

                    // 4) 磁吸拖拽时的手指跟随点
                    if (dragPort != null) {
                        drawCircle(Color(0x66EAF6FB), radius = cellPx * 0.16f, center = dragPoint)
                        drawCircle(Color(0xCC4FA3D1), radius = cellPx * 0.06f, center = dragPoint)
                    }

                    // 5) 器材名称 + 温度（原生文字，避免每件器材一个 Text 组合项）
                    drawIntoCanvas { canvas ->
                        val np = canvas.nativeCanvas
                        labelPaint.textSize = 10f * density
                        for (a in state.world.apparatus.values) {
                            val sc = toScreen(centerWorld(a))
                            val on = a.id == state.activeId
                            labelPaint.color = if (on) ACCENT.toArgb() else TEXT_DIM.toArgb()
                            labelPaint.isFakeBoldText = on
                            val text = "${a.equipment.cn} ${a.container.temperature.roundToInt()}℃"
                            np.drawText(
                                text,
                                sc.x - labelPaint.measureText(text) / 2f,
                                sc.y + cellPx * scale * 0.42f + labelPaint.textSize,
                                labelPaint
                            )
                        }
                    }
                }

                // ---- 端口（仅连接模式：48dp 命中区，拖拽磁吸） ----
                if (mode == InteractionMode.CONNECT) {
                    for (a in state.world.apparatus.values) {
                        for (p in a.ports) {
                            // 配件装上后不再显示它自己的挂载端口点（灯焰口 / 漏斗下管口）：
                            // 那是整体内部的接口，解除要用操作栏的「分离」；更要紧的是，
                            // 它 48dp 的命中区会盖住容器自己的瓶口，让容器再也没法进液。
                            if (state.world.isAccessory(a) && state.world.isUnitPort(a.id, p.id)) continue
                            val isDragSource = dragPort?.apparatusId == a.id && dragPort?.portId == p.id
                            val isHover = dragOver?.apparatusId == a.id && dragOver?.portId == p.id
                            PortDot(
                                state = if (isDragSource) PortVisualState.Selected else portVisual(a.id, p.id),
                                enabled = true,
                                grow = isHover,
                                phase = phaseOf,
                                // 端口位置在「布局阶段」按当前视图变换求值：
                                // 平移/缩放只让端口重新布局，不会触发整屏重组
                                modifier = Modifier.offset {
                                    val c = toScreen(portCenterWorld(a, p.id))
                                    IntOffset((c.x - hitHalf).roundToInt(), (c.y - hitHalf).roundToInt())
                                },
                                onClick = {
                                    val ref = PortRef(a.id, p.id)
                                    val sel = selectedPort
                                    message = if (sel == null) {
                                        selectedPort = ref
                                        "已选中 ${a.equipment.cn}·${portName(p.id)}（${portKindLabel(p.kind)}），请选择要连接到的端口"
                                    } else if (sel == ref) {
                                        selectedPort = null
                                        "已取消选择"
                                    } else {
                                        val msg = tryConnect(sel, ref)
                                        selectedPort = null
                                        msg
                                    }
                                    refresh()
                                },
                                onDragStart = {
                                    val ref = PortRef(a.id, p.id)
                                    dragPort = ref
                                    selectedPort = ref
                                    dragPoint = toScreen(portCenterWorld(a, p.id))
                                    dragOver = null
                                    refresh()
                                },
                                onDrag = { local ->
                                    val c = toScreen(portCenterWorld(a, p.id))
                                    val global = Offset(c.x - hitHalf + local.x, c.y - hitHalf + local.y)
                                    dragPoint = global
                                    // 磁吸：半径 60dp 内最近的兼容端口
                                    var best: PortRef? = null
                                    var bestDist = magnetPx
                                    val src = dragPort
                                    if (src != null) {
                                        for (oa in state.world.apparatus.values) {
                                            for (op in oa.ports) {
                                                if (oa.id == src.apparatusId && op.id == src.portId) continue
                                                if (!state.world.canConnect(src.apparatusId, src.portId, oa.id, op.id)) continue
                                                val pc = toScreen(portCenterWorld(oa, op.id))
                                                val d = (pc - global).getDistance()
                                                if (d <= bestDist) {
                                                    bestDist = d
                                                    best = PortRef(oa.id, op.id)
                                                }
                                            }
                                        }
                                    }
                                    dragOver = best
                                },
                                onDragEnd = {
                                    val src = dragPort
                                    val dst = dragOver
                                    if (src != null && dst != null) {
                                        message = tryConnect(src, dst)
                                    } else if (src != null) {
                                        message = "松开位置附近没有可连接的端口"
                                    }
                                    dragPort = null
                                    dragOver = null
                                    selectedPort = null
                                    refresh()
                                }
                            )
                        }
                    }

                    // ---- 连线中点：24dp 断开热区（构成整体的连线除外，它用操作栏的「分离」解除） ----
                    for (link in state.world.links) {
                        val a = state.world.apparatus[link.fromId] ?: continue
                        val b = state.world.apparatus[link.toId] ?: continue
                        if (state.world.isUnitLink(link)) continue
                        val half = (LINK_HIT_DP * density) / 2f
                        LinkHitTarget(
                            modifier = Modifier.offset {
                                // 热区锚在**实际走线的中点**上，参数与 drawTube / drawWire 保持一致。
                                // 取两端口连线的中点会让热区飘到管子外面，点上去毫无反应。
                                val pa = a.port(link.fromPort)
                                val stub = cellPx * (if (pa?.kind == PortKind.ELECTRIC) 0.26f else 0.45f)
                                val mid = linkMidPoint(
                                    portJointWorld(a, link.fromPort), portAxisOut(link.fromPort),
                                    portJointWorld(b, link.toPort), portAxisOut(link.toPort),
                                    stub
                                )
                                val midScreen = toScreen(mid)
                                IntOffset(
                                    (midScreen.x - half).roundToInt(),
                                    (midScreen.y - half).roundToInt()
                                )
                            },
                            onClick = {
                                confirmLink = link
                                message = null
                                refresh()
                            }
                        )
                    }
                }
            }

            // ==================== 底部操作栏（点击器材后才弹出） ====================
            if (showBar) {
                active?.let { a ->
                    ApparatusBar(
                        a = a,
                        tick = tick,
                        detachable = state.world.unitPartner(a.id) != null,
                        onClose = { showBar = false; refresh() },
                        onPick = { showPicker = true; refresh() },
                        onStir = { state.stir(); showBar = true; message = "已搅拌"; refresh() },
                        onHeat = {
                            val on = a.container.heating
                            state.toggleHeat()
                            message = if (on) "已熄灭" else "已点燃"
                            refresh()
                        },
                        onIgnite = { state.ignite(); message = "已点燃"; refresh() },
                        onElec = {
                            val on = a.container.electrolyzing
                            state.toggleElectrolysis()
                            message = if (on) "已断电" else "已通电"
                            refresh()
                        },
                        onDetach = {
                            // 分离后把配件（酒精灯 / 漏斗）从容器上挪开一段，让「已分离」一眼可见
                            val partner = state.world.unitPartner(a.id)
                                ?.let { state.world.apparatus[it] }
                            val accessory = if (state.world.isAccessory(a)) a else partner
                            if (state.world.detachUnit(a.id)) {
                                accessory?.let {
                                    it.y += if (it.equipment.id == Equipment.FUNNEL) {
                                        -FUNNEL_UNIT_OFFSET_DP * density * 0.45f
                                    } else {
                                        HEAT_UNIT_OFFSET_DP * density * 0.55f
                                    }
                                }
                                message = "已分离：${accessory?.equipment?.cn ?: "配件"}现在可单独摆放"
                            } else {
                                message = "没有可分离的整体"
                            }
                            refresh()
                        },
                        onVent = { state.removeGases(); message = "已排气"; refresh() },
                        onClean = { state.clean(); message = "已清洗"; refresh() },
                        onRemove = {
                            caches.remove(a.id)
                            state.world.unitPartner(a.id)?.let { caches.remove(it) }
                            state.removeApparatus(a.id)
                            showBar = false
                            message = "已移走 ${a.equipment.cn}"
                            refresh()
                        }
                    )
                }
            }
        }

        // ==================== 试剂选择页 ====================
        if (showPicker) {
            Box(modifier = Modifier.fillMaxSize().background(BG).padding(safe)) {
                SubstancePickerPage(
                    onPick = { s ->
                        state.addSubstance(s.id)
                        message = "已向 ${state.active?.equipment?.cn ?: "器材"} 加入 ${s.cn}"
                        showPicker = false
                        refresh()
                    },
                    onDismiss = { showPicker = false; refresh() }
                )
            }
        }

        // ==================== 器材面板 ====================
        if (showPalette) {
            Box(modifier = Modifier.fillMaxSize().padding(safe)) {
                ApparatusPalette(
                    onPick = { addApparatus(it) },
                    onDismiss = { showPalette = false; refresh() }
                )
            }
        }

        // ==================== 断开确认面板 ====================
        confirmLink?.let { link ->
            Box(modifier = Modifier.fillMaxSize().padding(safe)) {
                DisconnectConfirm(
                    text = describeLink(state, link),
                    onCancel = { confirmLink = null; refresh() },
                    onConfirm = {
                        state.world.disconnect(link.id)
                        linkBorn.remove(link.id)
                        confirmLink = null
                        message = "已断开该连接，物质不再转移"
                        refresh()
                    }
                )
            }
        }
    }
}

// ==================== 顶部横幅 ====================

@Composable
private fun TopBanner(
    mode: InteractionMode,
    message: String?,
    onMode: (InteractionMode) -> Unit,
    onAdd: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallButton(label = "返回", selected = false, onClick = onExit)
            Spacer(Modifier.width(6.dp))
            for (m in InteractionMode.values()) {
                SmallButton(
                    label = m.label,
                    selected = m == mode,
                    primary = m == InteractionMode.OPERATE,
                    onClick = { onMode(m) }
                )
                Spacer(Modifier.width(6.dp))
            }
            SmallButton(label = "＋器材", selected = false, primary = true, onClick = onAdd)
        }
        Text(
            text = message ?: mode.hint,
            color = if (message != null) ACCENT else TEXT_DIM,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
        // 旧版标题栏下方的强调线
        Box(Modifier.fillMaxWidth().height(2.dp).background(ACCENT))
    }
}

// ==================== 底部操作栏 ====================

/**
 * 点击器材后弹出的操作栏。
 *
 * 除操作按钮外，还直接展示该器材的当前状态与最近日志 —— 小屏上容器内部「看不见」，
 * 状态日志是理解联动效果的主要途径。
 */
/**
 * 是否为反应容器。
 *
 * 只有反应容器才提供「加试剂 / 搅拌 / 加热 / 点燃 / 通电 / 排气 / 清洗」这类容器专属操作：
 * 酒精灯、电源、电极、漏斗自身没有容器内容物，对它们显示「加试剂」等按钮既没有意义，
 * 也容易被误触。集气瓶虽是联动模式新增器材，但确实用于盛放物质，仍按反应容器处理。
 */
private fun isReactionVessel(e: Equipment): Boolean =
    e in Equipments.ALL || e == Equipments.GAS_COLLECTOR

@Composable
private fun ApparatusBar(
    a: Apparatus,
    tick: State<Int>,
    detachable: Boolean,
    onClose: () -> Unit,
    onPick: () -> Unit,
    onStir: () -> Unit,
    onHeat: () -> Unit,
    onIgnite: () -> Unit,
    onElec: () -> Unit,
    onDetach: () -> Unit,
    onVent: () -> Unit,
    onClean: () -> Unit,
    onRemove: () -> Unit
) {
    // 展开期间按主循环刷新状态文本（温度实时变化、日志追加）。
    // 只读在这里，重组范围仅限本操作栏，不会波及整屏与画布。
    tick.value
    val c = a.container
    // 按钮反映「自身」开关：连线外部供热/供电不由这里控制，只在状态行里说明
    val heating = c.heating
    val elec = c.electrolyzing
    val vessel = isReactionVessel(a.equipment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL)
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${a.equipment.cn} · ${if (vessel) "状态" else "说明"}",
                color = ACCENT,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SmallButton(label = "收起", selected = false, onClick = onClose)
        }

        Spacer(Modifier.height(4.dp))
        // 非反应容器没有容器内容物，显示器材用途说明而不是「空容器」这类状态
        Text(
            text = if (vessel) summaryOf(a) else a.equipment.note,
            color = TEXT_DIM, fontSize = 10.sp, lineHeight = 13.sp
        )

        // 实验状态日志：整体可上下滚动，回看一整轮实验的反应记录。
        // 限高避免日志多时把操作按钮挤出屏幕；滚动条区域独立，不影响画布手势。
        val logs = c.log
        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 84.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (l in logs) {
                    Text(
                        text = l,
                        color = TEXT_MAIN,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (vessel) {
                // 反应容器不提供「加热 / 通电」：加热只能由连上的酒精灯提供，
                // 电解只能由连上的电源提供 —— 器材之间的联动才是这两种操作的唯一来源。
                BarButton(label = "加试剂", onClick = onPick, primary = true, width = 88.dp)
                Spacer(Modifier.width(6.dp))
                BarButton(label = "搅拌", onClick = onStir, width = 78.dp)
                Spacer(Modifier.width(6.dp))
                BarButton(label = "点燃", onClick = onIgnite, width = 78.dp)
                Spacer(Modifier.width(6.dp))
                BarButton(label = "排气", onClick = onVent, width = 78.dp)
                Spacer(Modifier.width(6.dp))
                BarButton(label = "清洗", onClick = onClean, width = 78.dp)
                Spacer(Modifier.width(6.dp))
            } else {
                // 非反应容器只保留各自真正的功能开关
                when (a.equipment.id) {
                    Equipment.ALCOHOL_LAMP -> {
                        BarButton(label = if (heating) "熄灭" else "点燃", on = heating, onClick = onHeat, primary = true, width = 88.dp)
                        Spacer(Modifier.width(6.dp))
                    }

                    Equipment.BATTERY -> {
                        BarButton(label = if (elec) "断电" else "通电", on = elec, onClick = onElec, primary = true, width = 88.dp)
                        Spacer(Modifier.width(6.dp))
                    }

                    // 漏斗等通路器件自身没有可操作的开关
                    else -> {}
                }
            }
            if (detachable) {
                // 加热单元（酒精灯 + 容器）的解除入口
                BarButton(label = "分离", onClick = onDetach, primary = true, width = 78.dp)
                Spacer(Modifier.width(6.dp))
            }
            BarButton(label = "移走", onClick = onRemove, width = 78.dp)
        }
    }
}

/** 器材内部状态摘要 */
private fun summaryOf(a: Apparatus): String {
    val c = a.container
    val sb = StringBuilder()
    sb.append("温度 ").append(c.temperature.roundToInt()).append("℃")
    if (c.waterMl > 0) sb.append(" · 水 ").append(c.waterMl.roundToInt()).append("mL")
    if (c.solids.isNotEmpty()) {
        sb.append(" · 固体 ")
        sb.append(c.solids.entries.joinToString("、") { (id, v) -> "${Substances.get(id).formula}${fmt(v)}" })
    }
    if (c.solutes.isNotEmpty()) {
        sb.append(" · 溶液 ")
        sb.append(c.solutes.entries.joinToString("、") { (id, v) -> "${Substances.get(id).formula}${fmt(v)}" })
    }
    if (c.gases.isNotEmpty()) {
        sb.append(" · 气体 ")
        sb.append(c.gases.entries.joinToString("、") { (id, v) -> "${Substances.get(id).formula}${fmt(v)}" })
    }
    // 区分「自身加热」与「被连线供热」：后者在断开连线后会立即消失
    if (c.heating) sb.append(" · 加热中") else if (c.linkHeated) sb.append(" · 外部供热中")
    if (c.electrolyzing) sb.append(" · 通电中") else if (c.linkPowered) sb.append(" · 外部供电中")
    if (c.solids.isEmpty() && c.solutes.isEmpty() && c.gases.isEmpty() && c.waterMl <= 0) sb.append(" · 空容器")
    return sb.toString()
}

private fun fmt(v: Double): String {
    val s = String.format("%.2f", v)
    return s.trimEnd('0').trimEnd('.')
}

private fun describeLink(state: WorldState, link: Link): String {
    val a = state.world.apparatus[link.fromId]
    val b = state.world.apparatus[link.toId]
    val pa = a?.port(link.fromPort)
    val from = "${a?.equipment?.cn ?: link.fromId}·${portName(link.fromPort)}"
    val to = "${b?.equipment?.cn ?: link.toId}·${portName(link.toPort)}"
    return "$from → $to（${pa?.let { portKindLabel(it.kind) } ?: "未知"}）"
}

/** 可放置的器材清单（电极已取消：电源的接线端直接接入反应容器） */
private val PALETTE = listOf(
    Equipments.ALCOHOL_LAMP,
    Equipments.TEST_TUBE,
    Equipments.FLASK,
    Equipments.BEAKER,
    Equipments.GAS_COLLECTOR,
    Equipments.FUNNEL,
    Equipments.BATTERY
)

// ==================== 通用小控件 ====================

/** 把 Compose 颜色转成原生 int，供 android.graphics.Paint / shader 使用 */
private fun Color.toArgb(): Int = (alpha * 255f + 0.5f).toInt() shl 24 or
    ((red * 255f + 0.5f).toInt() shl 16) or
    ((green * 255f + 0.5f).toInt() shl 8) or
    (blue * 255f + 0.5f).toInt()

/**
 * 标准面板按钮：与旧版按钮尺寸对齐（旧版约 44dp 高、圆角 14dp）。
 * [primary] 用于主要操作（加试剂 / ＋器材），[on] 用于「已开启」的高亮态。
 */
@Composable
private fun BarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    on: Boolean = false,
    width: androidx.compose.ui.unit.Dp? = null
) {
    val bg = when {
        on -> BTN_ON
        primary -> BTN_ACTIVE
        else -> BTN
    }
    val fg = if (on) ACCENT else TEXT_MAIN
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(
                1.dp,
                when {
                    on -> ACCENT
                    primary -> Color(0x44FFFFFF)
                    else -> HAIRLINE
                },
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = if (primary) 15.sp else 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 顶部横幅使用的紧凑按钮（同时是通用按钮实现） */
@Composable
private fun SmallButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = when {
        !enabled -> Color(0x1AFFFFFF)
        selected -> BTN_ON
        primary -> BTN_ACTIVE
        else -> BTN
    }
    val fg = when {
        !enabled -> Color(0x55FFFFFF)
        selected -> ACCENT
        else -> TEXT_MAIN
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (selected) ACCENT else HAIRLINE, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = if (primary) 15.sp else 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 物质色点：与旧版 Icons.drawSubstanceDot 的配色语义保持一致 */
@Composable
private fun SubstanceDot(s: Substance, d: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(d)) {
        val col = when (s.phase) {
            Phase.GAS, Phase.LIQUID, Phase.SOLID -> Color(s.solidColor)
            Phase.AQUEOUS -> Color(s.solutionColor ?: 0xFFB9E3EE.toInt())
        }
        val r = size.minDimension / 2f
        drawCircle(color = col, radius = r * 0.82f)
        drawCircle(color = Color(0x66FFFFFF), radius = r * 0.82f, style = Stroke(width = r * 0.18f))
    }
}

// ==================== 试剂选择页 ====================

/**
 * 试剂选择页：与旧版「选择物质」页面结构一致 ——
 * 一级分类（5 类）→ 二级分类（可横向滑动）→ 物质列表（色点 / 名称 / 化学式）。
 */
@Composable
private fun SubstancePickerPage(onPick: (Substance) -> Unit, onDismiss: () -> Unit) {
    var cat by remember { mutableStateOf(Category.values().first()) }
    var sub by remember { mutableStateOf(Substances.SUB_ALL) }
    val subs = remember(cat) { Substances.subGroups(cat) }
    if (sub !in subs) sub = Substances.SUB_ALL
    val list = remember(cat, sub) {
        Substances.byCategory(cat).filter { Substances.inSubGroup(it, sub) }
    }
    val subScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PANEL)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallButton(label = "返回", selected = false, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "选择物质（点击加入）",
                color = ACCENT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(text = "${list.size} 种", color = TEXT_DIM, fontSize = 11.sp)
        }

        // 一级分类
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (c in Category.values()) {
                val on = c == cat
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) BTN_ACTIVE else BTN)
                        .border(1.dp, if (on) ACCENT else HAIRLINE, RoundedCornerShape(10.dp))
                        .clickable {
                            cat = c
                            sub = Substances.SUB_ALL
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = c.label,
                        color = if (on) ACCENT else TEXT_MAIN,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 二级分类（横向滑动）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(subScroll)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (s in subs) {
                val on = s == sub
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (on) BTN_ACTIVE else PANEL)
                        .border(1.dp, if (on) ACCENT else HAIRLINE, RoundedCornerShape(17.dp))
                        .clickable { sub = s }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = s, color = if (on) ACCENT else TEXT_DIM, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 物质列表
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)
        ) {
            items(list, key = { it.id }) { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PANEL)
                        .clickable { onPick(s) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubstanceDot(s, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(text = s.cn, color = TEXT_MAIN, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(text = s.formula, color = TEXT_DIM, fontSize = 12.sp)
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        text = "该分类下暂无物质",
                        color = TEXT_DIM,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        }
    }
}

// ==================== 器材面板 ====================

@Composable
private fun ApparatusPalette(onPick: (Equipment) -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(PANEL)
                .clickable(enabled = false) {}
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "添加器材",
                    color = TEXT_MAIN,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SmallButton(label = "关闭", selected = false, onClick = onDismiss)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (e in PALETTE) {
                    BarButton(label = e.cn, onClick = { onPick(e) }, width = 84.dp)
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
    }
}

// ==================== 断开确认 ====================

@Composable
private fun DisconnectConfirm(text: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onCancel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(PANEL)
                .clickable(enabled = false) {}
                .padding(14.dp)
        ) {
            Text(
                text = "断开这条连接？",
                color = TEXT_MAIN,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(text = text, color = TEXT_DIM, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                BarButton(label = "取消", onClick = onCancel)
                Spacer(Modifier.width(8.dp))
                BarButton(label = "断开", onClick = onConfirm, on = true)
            }
        }
    }
}