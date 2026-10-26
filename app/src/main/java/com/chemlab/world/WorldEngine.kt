package com.chemlab.world

import com.chemlab.game.Engine
import com.chemlab.game.Reaction
import kotlin.math.abs

/**
 * 联动引擎：把「连接图」翻译成实际的物质与能量流动，再驱动原有的单容器反应引擎。
 *
 * 每 tick 的职责严格分为两步，顺序不可颠倒：
 *  1. 传输：遍历所有打开的连接，按端口种类搬运气体/液体/热量/电流；
 *  2. 反应：对每个容器调用一次 [Engine.resolve]。
 *
 * 传输阶段**不**调用 resolve，避免同一 tick 内重复结算（反复 resolve 既慢又会让
 * 日志与成就重复触发）。
 */
object WorldEngine {

    /** 气体每 tick 转移源容器内各气体总量的比例 */
    private const val GAS_TRANSFER_RATIO = 0.5

    /** 液体每 tick 最多转移的水量（mL） */
    private const val LIQUID_TRANSFER_ML = 20.0

    /** 热量每 tick 收敛温差的比例（两容器温度向中间靠拢） */
    private const val HEAT_CONVERGE_RATIO = 0.25

    /** 浮点零阈值：小于该值视为「没有」 */
    private const val EPS = 1e-9

    /**
     * 清空上一 tick 由连线传导的供热 / 供电标记。
     *
     * 这两个标记是「本 tick 有效」的瞬时状态：断开连线、关掉酒精灯或电源后，本 tick 就
     * 不会再被置位，目标容器立刻停止加热 / 电解，不会像以前那样把状态残留进
     * [Container.heating] / [Container.electrolyzing] 变成「自己一直加热」。
     *
     * 供电标记会体现在电源 / 电极的位图高亮里，所以清除时一并标脏触发重绘。
     * 由 [WorldState.tick] 在判断「世界是否活跃」之前调用，保证断开后第一拍就停。
     */
    fun resetLinkState(world: World) {
        for (a in world.apparatus.values) {
            if (a.container.linkPowered) a.markDirty()
            a.container.linkHeated = false
            a.container.linkPowered = false
        }
        // 流量同样是「本 tick」的瞬时量：先清零，再由搬运按实际搬运量重新置位。
        // 于是断开连线、或源容器没料时流量自然为 0，导管里的流动动画随之停住。
        for (l in world.links) l.flow = 0f
    }

    /**
     * 执行一个时间步。
     *
     * @return apparatusId → 该容器本 tick 内发生的反应列表（无反应则不出现该 key）
     */
    fun tick(world: World): Map<String, List<Reaction>> {
        resetLinkState(world)
        transferAll(world)

        val result = LinkedHashMap<String, List<Reaction>>()
        for ((id, a) in world.apparatus) {
            val happened = Engine.resolve(a.container)
            if (happened.isNotEmpty()) {
                result[id] = happened
                a.markDirty()
            }
        }
        return result
    }

    // ==================== 传输 ====================

    /**
     * 一次遍历所有打开的连接。
     *
     * [filled] 记录本 tick 已经被「实际充入物质」的容器：一个容器每 tick 最多被充一次，
     * 避免同源多线时被重复灌入。注意只有**真的搬了东西**才占用名额 —— 否则一条空管
     * （例如挂在容器口上但还没倒料的漏斗）会白占名额，把同一容器上其它管线的输送全挡掉。
     */
    private fun transferAll(world: World) {
        val filled = HashSet<String>()
        for (link in world.links) {
            if (!link.open) continue
            val flow = flowOf(world, link) ?: continue
            when (flow.srcPort.kind) {
                PortKind.GAS ->
                    if (flow.dst.id !in filled && transferGas(flow, link)) filled.add(flow.dst.id)

                PortKind.LIQUID ->
                    if (flow.dst.id !in filled && transferLiquid(flow, link)) filled.add(flow.dst.id)

                // 热与电是状态传导而非物质搬运，不受「每 tick 只充一次」的限制
                PortKind.HEAT -> transferHeat(flow)
                PortKind.ELECTRIC -> transferElectric(flow)
            }
        }
    }

    /** 一条连接的实际流向；[forward] 表示解析后源端就是 link.fromId（否则源端是 toId） */
    private class Flow(
        val src: Apparatus, val srcPort: Port,
        val dst: Apparatus, val dstPort: Port,
        val forward: Boolean
    )

    /**
     * 解析连接的方向：OUT 端必为源，IN 端必为目标；两端都双向时按 from → to。
     */
    private fun flowOf(world: World, link: Link): Flow? {
        val a = world.apparatus[link.fromId] ?: return null
        val b = world.apparatus[link.toId] ?: return null
        val pa = a.port(link.fromPort) ?: return null
        val pb = b.port(link.toPort) ?: return null
        return when {
            pa.dir == Direction.OUT || pb.dir == Direction.IN -> Flow(a, pa, b, pb, true)
            pb.dir == Direction.OUT || pa.dir == Direction.IN -> Flow(b, pb, a, pa, false)
            else -> Flow(a, pa, b, pb, true)
        }
    }

    /**
     * 气体：源容器每种气体按 50% 转移到目标容器。
     * @return 是否真的搬了东西（源容器没有气体时为 false，不占用本 tick 的充入名额）
     */
    private fun transferGas(f: Flow, link: Link): Boolean {
        val from = f.src.container
        val to = f.dst.container
        val moves = ArrayList<Pair<String, Double>>(from.gases.size)
        for ((id, amount) in from.gases) {
            val mv = amount * GAS_TRANSFER_RATIO
            if (mv > EPS) moves.add(id to mv)
        }
        if (moves.isEmpty()) return false
        for ((id, mv) in moves) {
            from.gases[id] = (from.gases[id] ?: 0.0) - mv
            to.gases[id] = (to.gases[id] ?: 0.0) + mv
        }
        // 抽干到接近 0 的气体从表里清掉，避免残留 1e-15 量级的数据污染 UI
        from.gases.entries.removeAll { it.value <= EPS }
        // 气体量不参与器材位图缓存（气泡在实时层绘制），因此不标脏：
        // 否则一旦有气体在相连容器间来回扩散，每 100ms 都会重建两张位图。
        link.flow = 1f
        link.flowForward = f.forward
        return true
    }

    /**
     * 液体：每 tick 最多移走 20 mL 水，溶质按移走水量的比例同步带走（固体留在原容器）。
     * @return 是否真的搬了东西（源容器没有水时为 false，不占用本 tick 的充入名额）
     */
    private fun transferLiquid(f: Flow, link: Link): Boolean {
        val from = f.src.container
        val to = f.dst.container
        if (from.waterMl <= EPS) return false
        val moveMl = minOf(from.waterMl, LIQUID_TRANSFER_ML)
        if (moveMl <= EPS) return false
        val ratio = moveMl / from.waterMl
        from.waterMl -= moveMl
        to.waterMl += moveMl

        for ((id, mol) in ArrayList(from.solutes.entries)) {
            val take = mol * ratio
            if (take <= EPS) continue
            from.solutes[id] = mol - take
            to.solutes[id] = (to.solutes[id] ?: 0.0) + take
        }
        from.solutes.entries.removeAll { it.value <= EPS }

        // 流量按本 tick 实际搬运的水量归一化：水流弱时导管里的粒子也更稀更慢
        link.flow = (moveMl / LIQUID_TRANSFER_ML).toFloat()
        link.flowForward = f.forward

        f.src.markDirty()
        f.dst.markDirty()
        return true
    }

    /**
     * 热量：两容器温度按 25% 的比例向均值收敛（温差越大会传得越快）。
     *
     * 「正在加热」只作为本 tick 的供热标记传给目标（[Container.linkHeated]），
     * 不写进目标自身的 [Container.heating]：写进去的话，熄灭酒精灯或断开连线后
     * 目标容器会残留 heating 状态自行加热，而且连线还会把目标自己点的火一并扑灭。
     * 标记每 tick 由 [tick] 重置，因此供热是「连着才有」的。
     */
    private fun transferHeat(f: Flow) {
        val from = f.src.container
        val to = f.dst.container
        if (from.isHeating) to.linkHeated = true
        val diff = from.temperature - to.temperature
        // 温度与「加热中」都只在实时层体现（火焰/温度计），不影响器材位图，
        // 因此这里不标脏 —— 否则热量每 tick 都在两容器间收敛，位图会被无限重建。
        if (abs(diff) > 1e-6) {
            val move = diff * HEAT_CONVERGE_RATIO
            from.temperature -= move
            to.temperature += move
        }
    }

    /** 电流：源容器通电时，目标容器本 tick 标记为被供电；断电或断开连线后标记自然消失 */
    private fun transferElectric(f: Flow) {
        val to = f.dst.container
        if (f.src.container.isElectrolyzing && !to.linkPowered) {
            to.linkPowered = true
            f.dst.markDirty()
        }
    }
}