package com.chemlab.game

/**
 * 实验容器：记录盛放的物质、相态、温度与反应现象。
 * 物质 id 同一时间只存在于 solids / solutes / gases 三者之一；水以体积（mL）单独记录。
 */
class Container(val equipment: Equipment) {

    val solids = LinkedHashMap<String, Double>()   // 未溶解固体 / 金属 / 沉淀
    val solutes = LinkedHashMap<String, Double>()  // 溶液中的溶质
    val gases = LinkedHashMap<String, Double>()    // 容器内气体

    var waterMl = 0.0          // 溶剂水量
    var temperature = 25.0     // 摄氏温度
    var heating = false        // 酒精灯是否点燃（持续升温）
    var ignitionArmed = false  // 是否已点燃（单次引燃）
    var electrolyzing = false  // 是否通电

    /**
     * 由「器材联动」的连线传导而来的供热 / 供电标记。
     *
     * 必须与 [heating] / [electrolyzing] 分开：后两者是用户直接在本器材上操作的自身开关，
     * 而这两个标记每 tick 由 [com.chemlab.world.WorldEngine] 先清零、再按当前连接重新置位。
     * 若直接把连线源的状态写进自身开关，断开连线（或关掉酒精灯/电源）后状态会残留，
     * 器材就会「自己继续加热 / 继续电解」。
     */
    var linkHeated = false
    var linkPowered = false

    /** 有效加热：自身点火，或被热连线供热 */
    val isHeating: Boolean get() = heating || linkHeated

    /** 有效通电：自身通电，或被电源线供电 */
    val isElectrolyzing: Boolean get() = electrolyzing || linkPowered

    val log = ArrayList<String>()      // 最近发生的事件（新事件在前）

    /**
     * 加入次序记录：物质 id -> 加入序号。
     * 反应引擎据此优先触发"最近加入材料"参与的反应，避免刚倒入的试剂
     * 被容器内残留的其它物质抢先消耗掉。
     */
    val order = HashMap<String, Int>()
    var seq = 0

    /**
     * 本轮沉降中已经发生过的反应 id（含这些反应的互逆反应）。
     *
     * 反应物会被后续反应重新生成时，同一条反应就会反复发生，形成
     * 「分解出气体 → 气体被立刻重新吸收」这类无限循环。记录已发生的反应即可
     * 在第二次命中时终止循环。用户每次主动操作时清空，自动升温的 tick 之间保留。
     */
    val doneReactions = HashSet<String>()

    fun touch(id: String) {
        order[id] = ++seq
    }

    fun clear() {
        solids.clear(); solutes.clear(); gases.clear()
        waterMl = 0.0
        temperature = 25.0
        heating = false
        ignitionArmed = false
        electrolyzing = false
        linkHeated = false
        linkPowered = false
        log.clear()
        order.clear()
        seq = 0
        doneReactions.clear()
    }

    fun isEmpty(): Boolean = solids.isEmpty() && solutes.isEmpty() && gases.isEmpty() && waterMl <= 0.0

    /**
     * 记录条数上限。
     * 联动模式的器材状态栏可以上下滚动查看，所以这里保留足够多的历史，
     * 让用户能回看一整轮实验的反应记录，而不是只剩最近几条。
     */
    private val LOG_MAX = 60

    fun addLog(msg: String) {
        log.add(0, msg)
        while (log.size > LOG_MAX) log.removeAt(log.size - 1)
    }
}

/** 单次加入试剂的“一勺/一份”用量 */
object Dose {
    const val SOLID = 0.2      // mol
    const val AQUEOUS = 0.1    // mol
    const val LIQUID = 0.2     // mol
    const val GAS = 0.2        // mol
    const val WATER_ML = 60.0  // mL
}

/**
 * 游戏会话：持有容器，执行各项操作，并在反应发生后回调（供成就与日志使用）。
 */
class GameState {

    var container = Container(Equipments.BEAKER)
        private set

    /** 反应发生回调（用于成就解锁） */
    var onReaction: ((Reaction) -> Unit)? = null

    /** 操作事件回调，tag 形如 "heat"/"stir"/"ignite"/"add:<物质id>" */
    var onAction: ((String) -> Unit)? = null

    /** 加入一种物质 */
    fun addSubstance(id: String) {
        val c = container
        val s = Substances.get(id)
        when {
            id == "h2o" -> { c.waterMl += Dose.WATER_ML; c.addLog("加入 ${s.cn}（+${Dose.WATER_ML.toInt()} mL）") }
            s.phase == Phase.GAS -> { c.gases[id] = (c.gases[id] ?: 0.0) + Dose.GAS; c.addLog("通入气体 ${s.cn}") }
            s.phase == Phase.AQUEOUS -> {
                c.solutes[id] = (c.solutes[id] ?: 0.0) + Dose.AQUEOUS
                c.waterMl += 20.0 // 溶液自带溶剂
                c.addLog("加入 ${s.cn} 溶液")
            }
            s.phase == Phase.LIQUID -> { c.solutes[id] = (c.solutes[id] ?: 0.0) + Dose.LIQUID; c.addLog("加入 ${s.cn}") }
            else -> { c.solids[id] = (c.solids[id] ?: 0.0) + Dose.SOLID; c.addLog("加入 ${s.cn}") }
        }
        c.touch(id)
        onAction?.invoke("add:$id")
        resolve(focus = id)
    }

    /** 溶解 / 搅拌：可溶固体进入水中形成溶液，并让反应物充分接触 */
    fun stir() {
        val c = container
        var dissolved = false
        if (c.waterMl > 0.0) {
            val ids = c.solids.keys.toList()
            for (id in ids) {
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
        resolve()
    }

    /** 酒精灯加热开关 */
    fun toggleHeat() {
        val c = container
        c.heating = !c.heating
        c.addLog(if (c.heating) "点燃酒精灯，开始加热" else "熄灭酒精灯")
        onAction?.invoke("heat")
        resolve()
    }

    /** 冷却至室温 */
    fun cool() {
        val c = container
        c.heating = false
        c.temperature = 25.0
        c.addLog("冷却至室温")
        onAction?.invoke("cool")
        resolve()
    }

    /** 点燃（触发燃烧类反应） */
    fun ignite() {
        val c = container
        c.ignitionArmed = true
        c.addLog("点燃")
        onAction?.invoke("ignite")
        resolve()
    }

    /** 通电开关（电解） */
    fun toggleElectrolysis() {
        val c = container
        c.electrolyzing = !c.electrolyzing
        c.addLog(if (c.electrolyzing) "接通电源（电解）" else "断开电源")
        onAction?.invoke("electrolysis")
        resolve()
    }

    /** 蒸发：蒸干水分，溶质析出结晶 */
    fun evaporate() {
        val c = container
        if (c.waterMl <= 0.0) { c.addLog("没有水可蒸发"); return }
        c.waterMl = 0.0
        val ids = c.solutes.keys.toList()
        var count = 0
        for (id in ids) {
            val amount = c.solutes.remove(id) ?: 0.0
            c.solids[id] = (c.solids[id] ?: 0.0) + amount
            count++
        }
        c.addLog(if (count > 0) "蒸发结晶，析出 ${count} 种晶体" else "蒸发水分")
        onAction?.invoke("evaporate")
        resolve()
    }

    /** 移除容器内的气体（排气） */
    fun removeGases() {
        val c = container
        if (c.gases.isEmpty()) {
            c.addLog("容器内没有气体")
            onAction?.invoke("vent")
            return
        }
        val names = c.gases.keys.joinToString("、") { Substances.get(it).cn }
        c.gases.clear()
        c.addLog("排出气体：$names")
        onAction?.invoke("vent")
    }

    /** 清洗容器 */
    fun clean() {
        container.clear()
        container.addLog("已清洗容器")
        onAction?.invoke("clean")
    }

    /** 切换器材 */
    fun switchEquipment(e: Equipment) {
        container = Container(e)
        container.addLog("换用：${e.cn}")
        onAction?.invoke("switch")
    }

    /** 每个时间步：温度爬升/回落 + 自动反应 */
    fun tick() {
        val c = container
        when {
            c.heating && c.temperature < 800.0 -> c.temperature = (c.temperature + 12.0).coerceAtMost(800.0)
            !c.heating && c.temperature > 25.0 -> c.temperature = (c.temperature - 8.0).coerceAtLeast(25.0)
        }
        // 温度里程碑（供成就使用，解锁是幂等的，重复触发无副作用）
        if (c.temperature >= 300.0) onAction?.invoke("temp300")
        if (c.temperature >= 500.0) onAction?.invoke("temp500")
        resolve(newAction = false)
    }

    /**
     * 结算容器内的反应。
     *
     * @param focus     本次操作刚加入的物质 id（其参与的反应优先发生）
     * @param newAction true 表示这是用户的主动操作，需要清空循环保护记录；
     *                  false 用于自动升温的 tick，需保留记录以阻止跨帧的往复死循环
     */
    private fun resolve(focus: String? = null, newAction: Boolean = true) {
        if (newAction) container.doneReactions.clear()
        val happened = Engine.resolve(container, focus)
        for (r in happened) {
            container.addLog("⚗ ${r.equation()}")
            container.addLog("　🛈 ${r.effect.colorChange ?: "发生反应"}")
            onReaction?.invoke(r)
        }
        // 连锁反应：一次操作触发 3 个及以上反应
        if (happened.size >= 3) onAction?.invoke("chain:${happened.size}")
    }
}