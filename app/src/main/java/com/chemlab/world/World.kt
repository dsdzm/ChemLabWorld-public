package com.chemlab.world

import com.chemlab.game.Equipment

/**
 * 一条连接：把「源端口」连到「目标端口」。
 *
 * [open] 为 false 时表示已断开（保留记录便于恢复），引擎只处理 open == true 的连接。
 */
data class Link(
    val id: String,
    val fromId: String,
    val fromPort: String,
    val toId: String,
    val toPort: String,
    var open: Boolean = true
) {
    /**
     * 本 tick 的物质流量（0..1，0 = 没有转移）。
     *
     * 由 [WorldEngine] 在搬运后按实际搬运量置位、每 tick 先清零；UI 据此在导管里
     * 绘制流动的粒子。流量不存在连线本身，而是「这一拍搬了多少」的瞬时快照。
     */
    var flow: Float = 0f

    /**
     * 流动方向：true 表示 from → to。
     * 连接的方向解析可能把 to 当成源（见 [WorldEngine.flowOf]），所以方向要单独记，
     * 不能假定总是 from → to。
     */
    var flowForward: Boolean = true
}

/**
 * 配件「装到容器上」用的接口：酒精灯的供热口、漏斗的下管口。
 * 只有配件用自己的出口接到容器上，才算「装上去」成为一个整体。
 */
private val MOUNT_PORTS = setOf("heat_out", "liquid_out")

/**
 * 连接图：持有实验台上所有器材与连线。
 *
 * 只负责「结构」（谁能连谁、怎么连），不负责物质传输 —— 传输在 [WorldEngine] 中。
 */
class World {

    val apparatus = LinkedHashMap<String, Apparatus>()
    val links = mutableListOf<Link>()

    private var linkSeq = 0

    fun add(a: Apparatus) {
        apparatus[a.id] = a
    }

    /** 移除器材，并级联删除挂在它上面的所有连线 */
    fun remove(id: String) {
        apparatus.remove(id)
        links.removeAll { it.fromId == id || it.toId == id }
    }

    /**
     * 建立连接。失败返回 null（不抛异常，方便 UI 直接判空）。
     *
     * 校验顺序：
     *  1. 两端器材与端口都必须存在；
     *  2. 端口种类必须一致（液体只能连液体…）；
     *  3. 不允许 OUT→OUT / IN→IN（没有明确流向）；
     *  4. 不允许两端口之间的重复连接。
     */
    fun connect(fromId: String, fromPort: String, toId: String, toPort: String): Link? {
        if (fromId == toId) return null
        val a = apparatus[fromId] ?: return null
        val b = apparatus[toId] ?: return null
        val pa = a.port(fromPort) ?: return null
        val pb = b.port(toPort) ?: return null
        if (pa.kind != pb.kind) return null
        if (pa.dir == Direction.OUT && pb.dir == Direction.OUT) return null
        if (pa.dir == Direction.IN && pb.dir == Direction.IN) return null
        val duplicated = links.any {
            (it.fromId == fromId && it.fromPort == fromPort && it.toId == toId && it.toPort == toPort) ||
                (it.fromId == toId && it.fromPort == toPort && it.toId == fromId && it.toPort == fromPort)
        }
        if (duplicated) return null

        val link = Link("link_${++linkSeq}", fromId, fromPort, toId, toPort)
        links.add(link)
        return link
    }

    fun disconnect(linkId: String) {
        links.removeAll { it.id == linkId }
    }

    /** 与某器材相关的全部连线（用于断开器材时清理、UI 高亮） */
    fun linksOf(id: String): List<Link> = links.filter { it.fromId == id || it.toId == id }

    /**
     * 是否是「配件」：能吸附到容器上、与容器合成一个整体的器材。
     * 酒精灯吸附在容器下方供热，漏斗插在容器口上加液。
     */
    fun isAccessory(a: Apparatus?): Boolean = a != null &&
        (a.equipment.id == Equipment.ALCOHOL_LAMP || a.equipment.id == Equipment.FUNNEL)

    /**
     * 这条连接是否会构成「整体」：配件用**自己的出口**（灯焰口 / 漏斗下管口）接到容器上。
     *
     * 判定必须收紧到「用出口」这一点：从别处往配件里送料（例如集气瓶排水口 → 漏斗进液口）
     * 只是普通的物质转移，不该把漏斗也焊死在容器上、更不该占用配件的唯一整体名额。
     */
    fun willFormUnit(fromId: String, fromPort: String, toId: String, toPort: String): Boolean {
        val a = apparatus[fromId] ?: return false
        val b = apparatus[toId] ?: return false
        if (isAccessory(a) == isAccessory(b)) return false
        val port = if (isAccessory(a)) fromPort else toPort
        return port in MOUNT_PORTS
    }

    /** 这条连线是否构成一个「整体」—— 整体用操作栏的「分离」解除，不提供中点断开 */
    fun isUnitLink(l: Link): Boolean = willFormUnit(l.fromId, l.fromPort, l.toId, l.toPort)

    /**
     * 与某器材组成一个整体的伙伴（配件 ↔ 容器），没有则 null。
     * 连线一断，整体自然解散。
     */
    fun unitPartner(id: String): String? {
        for (l in links) {
            if (!l.open) continue
            if (l.fromId != id && l.toId != id) continue
            if (!isUnitLink(l)) continue
            return if (l.fromId == id) l.toId else l.fromId
        }
        return null
    }

    /**
     * 某端口是否被「整体」占用（即配件的挂载口，以及容器上对应的那个口）。
     * 这类端口属于整体内部，不参与手动连接；配件的挂载端口点在装上后也不再显示 ——
     * 否则它 48dp 的命中区会盖住容器自己的瓶口，让容器无法再进液。
     */
    fun isUnitPort(id: String, portId: String): Boolean = links.any {
        it.open && isUnitLink(it) &&
            ((it.fromId == id && it.fromPort == portId) || (it.toId == id && it.toPort == portId))
    }

    /** 断开与某器材相连的全部「整体」连线（「分离」按钮）；返回是否真的断开过 */
    fun detachUnit(id: String): Boolean {
        val unit = links.filter { (it.fromId == id || it.toId == id) && isUnitLink(it) }
        links.removeAll(unit)
        return unit.isNotEmpty()
    }

    /**
     * 某端口是否挂着「已导通」的连线。
     * 用于电极、导管这类结构可视化：断开连线后应立即消失。
     */
    fun isPortOpenLinked(id: String, portId: String): Boolean = links.any {
        it.open && ((it.fromId == id && it.fromPort == portId) ||
            (it.toId == id && it.toPort == portId))
    }

    /**
     * 按「该画在下面 → 该画在上面」排序用的层级：配件（酒精灯 / 漏斗）压容器之上。
     *
     * 之前是用 bringToFront 直接改这张表的顺序来达成同一目的，但端口点是按列表位置
     * 组合出来的，改动列表顺序会连带弄乱端口点的对应关系。改成绘制时按层级派生，
     * 列表本身始终不动。
     */
    fun layerOf(a: Apparatus): Int = if (isAccessory(a)) 1 else 0

    /** 查找连接图中某个端口当前是否已被占用 */
    fun isPortConnected(id: String, portId: String): Boolean = links.any {
        (it.fromId == id && it.fromPort == portId) || (it.toId == id && it.toPort == portId)
    }

    /**
     * 判断两个端口能否互连（不产生副作用，供 UI 计算 Compatible 状态）。
     * 与 [connect] 的校验规则保持一致。
     */
    fun canConnect(fromId: String, fromPort: String, toId: String, toPort: String): Boolean {
        if (fromId == toId) return false
        val pa = apparatus[fromId]?.port(fromPort) ?: return false
        val pb = apparatus[toId]?.port(toPort) ?: return false
        if (pa.kind != pb.kind) return false
        if (pa.dir == Direction.OUT && pb.dir == Direction.OUT) return false
        if (pa.dir == Direction.IN && pb.dir == Direction.IN) return false
        return true
    }
}