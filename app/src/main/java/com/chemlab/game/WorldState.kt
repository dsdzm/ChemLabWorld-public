package com.chemlab.game

import com.chemlab.world.Apparatus
import com.chemlab.world.Link
import com.chemlab.world.World
import com.chemlab.world.WorldEngine

/**
 * 「器材联动实验」的会话状态。
 *
 * 与 [GameState] 的关系：GameState 管「一个容器」，WorldState 管「一张连接图上的多个容器」。
 * 单容器内的反应逻辑完全复用 [Engine]，本类只负责：
 *  · 器材的增删与选中；
 *  · 把用户操作转发到当前选中器材的 [Container]；
 *  · 每 tick 驱动 [WorldEngine]（传输 + 反应）并把结果回调出去。
 */
class WorldState {

    val world = World()

    var activeId: String? = null
        private set

    /** 反应发生回调（用于成就与日志） */
    var onReaction: ((apparatusId: String, r: Reaction) -> Unit)? = null

    /** 操作事件回调，tag 形如 "heat"/"stir"/"add:<物质id>" */
    var onAction: ((tag: String) -> Unit)? = null

    /** 当前选中的器材 */
    val active: Apparatus? get() = activeId?.let { world.apparatus[it] }

    private var seq = 0

    /** 剩余「必须推进 UI 帧」的 tick 数：反应闪光、操作反馈等一次性动效靠它保证帧数 */
    private var animTicks = 0

    // ==================== 节能：按需出帧 ====================

    /**
     * 请求额外推进若干 UI 帧。
     *
     * 交互或反应后主动申请几十帧，让一次性动效（闪光、连线生长）播完就停下来，
     * 而不是让界面无脑地每 100ms 重组一次。
     */
    fun requestFrames(n: Int = 8) {
        if (n > animTicks) animTicks = n
    }

    /**
     * 世界是否处于「活跃」状态。
     *
     * 只要有连接、加热、电解、气体，或温度还没回到室温，就可能发生变化；
     * 反之为静止 —— 室温下能发生的反应在操作瞬间已由 [settle] 结算过，
     * 因此静止时不推进模拟是安全且必要的。
     */
    private fun anyActive(): Boolean {
        if (world.links.any { it.open }) return true
        for (a in world.apparatus.values) {
            val c = a.container
            if (c.isHeating || c.isElectrolyzing || c.gases.isNotEmpty() || c.temperature > 25.05) return true
        }
        return false
    }

    // ==================== 器材管理 ====================

    fun addApparatus(a: Apparatus) {
        world.add(a)
        if (activeId == null) activeId = a.id
        requestFrames()
    }

    fun removeApparatus(id: String) {
        world.remove(id)
        if (activeId == id) activeId = world.apparatus.keys.firstOrNull()
        requestFrames()
    }

    fun select(id: String) {
        if (world.apparatus.containsKey(id)) activeId = id
    }

    fun nextId(prefix: String = "app"): String = "${prefix}_${++seq}"

    fun connect(fromId: String, fromPort: String, toId: String, toPort: String): Link? =
        world.connect(fromId, fromPort, toId, toPort)?.also { requestFrames(4) }

    // ==================== 操作（转发到 active 的容器） ====================

    /** 加入一种物质：分相态投料，与 GameState.addSubstance 的规则保持一致 */
    fun addSubstance(id: String) {
        val a = active ?: return
        val c = a.container
        val s = Substances.get(id)
        when {
            id == "h2o" -> {
                c.waterMl += Dose.WATER_ML
                c.addLog("加入 ${s.cn}（+${Dose.WATER_ML.toInt()} mL）")
            }

            s.phase == Phase.GAS -> {
                c.gases[id] = (c.gases[id] ?: 0.0) + Dose.GAS
                c.addLog("通入气体 ${s.cn}")
            }

            s.phase == Phase.AQUEOUS -> {
                c.solutes[id] = (c.solutes[id] ?: 0.0) + Dose.AQUEOUS
                c.waterMl += 20.0 // 溶液自带溶剂
                c.addLog("加入 ${s.cn} 溶液")
            }

            s.phase == Phase.LIQUID -> {
                c.solutes[id] = (c.solutes[id] ?: 0.0) + Dose.LIQUID
                c.addLog("加入 ${s.cn}")
            }

            else -> {
                c.solids[id] = (c.solids[id] ?: 0.0) + Dose.SOLID
                c.addLog("加入 ${s.cn}")
            }
        }
        c.touch(id)
        onAction?.invoke("add:$id")
        settle(a, focus = id)
        requestFrames()
    }

    /** 搅拌溶解：可溶固体进入水中，并让反应物充分接触 */
    fun stir() {
        val a = active ?: return
        val c = a.container
        var dissolved = false
        if (c.waterMl > 0.0) {
            for (id in c.solids.keys.toList()) {
                val s = Substances.get(id)
                if (s.isSoluble && !s.isGas) {
                    val amount = c.solids.remove(id) ?: 0.0
                    c.solutes[id] = (c.solutes[id] ?: 0.0) + amount
                    c.touch(id)
                    dissolved = true
                }
            }
        }
        c.addLog(if (dissolved) "搅拌并溶解固体" else "搅拌（使反应物充分混合）")
        onAction?.invoke("stir")
        settle(a)
        requestFrames()
    }

    /** 酒精灯点燃/熄灭开关 */
    fun toggleHeat() {
        val a = active ?: return
        val c = a.container
        c.heating = !c.heating
        c.addLog(if (c.heating) "点燃酒精灯，开始加热" else "熄灭酒精灯")
        onAction?.invoke("heat")
        settle(a)
        requestFrames()
    }

    /** 点燃（触发燃烧类反应） */
    fun ignite() {
        val a = active ?: return
        a.container.ignitionArmed = true
        a.container.addLog("点燃")
        onAction?.invoke("ignite")
        settle(a)
        requestFrames()
    }

    /** 通电开关（电解） */
    fun toggleElectrolysis() {
        val a = active ?: return
        val c = a.container
        c.electrolyzing = !c.electrolyzing
        c.addLog(if (c.electrolyzing) "接通电源（电解）" else "断开电源")
        onAction?.invoke("electrolysis")
        settle(a)
        requestFrames()
    }

    /** 排气：清空当前容器内的气体 */
    fun removeGases() {
        val a = active ?: return
        val g = a.container.gases
        if (g.isEmpty()) {
            a.container.addLog("容器内没有气体")
            onAction?.invoke("vent")
            return
        }
        val names = g.keys.joinToString("、") { Substances.get(it).cn }
        g.clear()
        a.container.addLog("排出气体：$names")
        onAction?.invoke("vent")
        a.markDirty()
        requestFrames()
    }

    /** 清洗当前容器（保留器材与连线） */
    fun clean() {
        val a = active ?: return
        a.container.clear()
        a.container.addLog("已清洗容器")
        onAction?.invoke("clean")
        a.markDirty()
        requestFrames()
    }

    // ==================== 时间步 ====================

    /**
     * 每 100ms 调用一次；返回本 tick 是否需要刷新 UI。
     *
     * 顺序：先让各容器自行升温/降温 → 再交给 [WorldEngine] 统一传输与结算反应。
     * 注意这里**不**清空 Container.doneReactions：跨 tick 保留才能阻止
     * 「分解放气 → 气体被立刻重新吸收」这类跨帧死循环（与 GameState.tick 一致）。
     *
     * 节能关键：世界静止时既不推进模拟、也不出帧。室温下能发生的反应在操作瞬间
     * 已由 [settle] 结算，跳过 `Engine.resolve` 省掉每 100ms 一次的全量反应扫描 ——
     * 这是低配手表上待机流畅度的关键（旧版主循环是 600ms，这里是 100ms，收益更明显）。
     */
    fun tick(): Boolean {
        // 先清掉上一 tick 由连线传导的供热 / 供电标记，再判断活跃度、再升温：
        // 断开连线或熄灭酒精灯后的第一拍就要停止加热，否则目标容器会多升温一拍。
        WorldEngine.resetLinkState(world)

        val active = anyActive()
        val animating = animTicks > 0
        if (animating) animTicks--
        if (!active && !animating) return false

        if (active) {
            for (a in world.apparatus.values) {
                val c = a.container
                val before = c.temperature
                when {
                    c.isHeating && c.temperature < 800.0 -> c.temperature = (c.temperature + 12.0).coerceAtMost(800.0)
                    !c.isHeating && c.temperature > 25.0 -> c.temperature = (c.temperature - 8.0).coerceAtLeast(25.0)
                }
                // 温度不参与器材位图缓存（温度计在实时层绘制），所以这里**不**标脏 ——
                // 否则升温/降温期间每 100ms 都会重建一次位图，是低配机上最大的卡顿来源。
                // 温度里程碑只在「跨过阈值」的那一拍触发，避免静止时每 tick 读一次 SharedPreferences。
                if (before < 300.0 && c.temperature >= 300.0) onAction?.invoke("temp300")
                if (before < 500.0 && c.temperature >= 500.0) onAction?.invoke("temp500")
            }

            val result = WorldEngine.tick(world)
            for ((id, list) in result) {
                val a = world.apparatus[id] ?: continue
                for (r in list) {
                    a.container.addLog("⚗ ${r.equation()}")
                    a.container.addLog("　🛈 ${r.effect.colorChange ?: "发生反应"}")
                    onReaction?.invoke(id, r)
                }
                a.markDirty()
            }
            if (result.isNotEmpty()) requestFrames(12)
        }
        return true
    }

    // ==================== 内部 ====================

    /**
     * 用户主动操作后的即时结算。
     *
     * 主动操作被视为「新一轮」，因此清空循环保护记录；随后的自动 tick 又会在
     * 本轮标记的基础上继续，从而兼顾「同一轮内不空转」与「跨帧不往复」。
     */
    private fun settle(a: Apparatus, focus: String? = null) {
        val c = a.container
        c.doneReactions.clear()
        val happened = Engine.resolve(c, focus)
        for (r in happened) {
            c.addLog("⚗ ${r.equation()}")
            c.addLog("　🛈 ${r.effect.colorChange ?: "发生反应"}")
            onReaction?.invoke(a.id, r)
        }
        if (happened.size >= 3) onAction?.invoke("chain:${happened.size}")
        a.markDirty()
        if (happened.isNotEmpty()) requestFrames(12)
    }
}